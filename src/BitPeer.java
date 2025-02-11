/* BitPeer.java: class for connection to a peer in BitTorrent */
/* Christopher Chute */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import util.lib.BitLibrary;

public class BitPeer {
    private static final int HANDSHAKE_SIZE = 68;
    private final InetSocketAddress peerAddrPort;
    // hex string of SHA1
    private final byte[] peerID;
    private boolean[] remoteBitfield = null;
    private Socket peerSocket = null;
    private BufferedOutputStream outToPeer = null;
    private BufferedInputStream inFromPeer = null;
    private Queue<BitMessage> messageQ = null;

    public HashSet<Integer> outstandingRequests;
    public boolean localIsChoked;       // peer is choking this client
    public boolean remoteIsChoked;      // this client is choking peer
    public boolean localIsInterested;   // this client is interested
    public boolean remoteIsInterested;  // peer is interested

    /* BitPeer(InetAddress, int): constructor for peer from command line/tracker */
    public BitPeer(InetAddress peerAddr, int peerPort) {
        this.peerAddrPort = new InetSocketAddress(peerAddr, peerPort);
        String stringToHash = getIP().toString() + getPort();
        peerID = BitLibrary.getSHA1(stringToHash);
        this.outstandingRequests = new HashSet<>();

        // peers start out choked and uninterested
        this.localIsChoked = true;
        this.localIsInterested = false;
        this.remoteIsChoked = true;
        this.remoteIsInterested = false;
    }

    /* BitPeer(Socket): constructor for receiving peer off welcome socket */
    public BitPeer(Socket peerSocket) {
        this.peerSocket = peerSocket;
        this.peerAddrPort = new InetSocketAddress(peerSocket.getInetAddress(), 
                                                  peerSocket.getPort());
        String stringToHash = getIP().toString() + getPort();
        peerID = BitLibrary.getSHA1(stringToHash);
        this.outstandingRequests = new HashSet<>();

        // peers start out choked and uninterested
        this.localIsChoked = true;
        this.localIsInterested = false;
        this.remoteIsChoked = true;
        this.remoteIsInterested = false;
        try {
            this.inFromPeer = new BufferedInputStream(
                              new DataInputStream(peerSocket.getInputStream()));
            this.outToPeer = new BufferedOutputStream(
                             new DataOutputStream(peerSocket.getOutputStream()));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        // only initialize reader once handshake is complete

    }

    /* connect:  connect to specified peer */
    public int connect() {
        // initialize input/output streams
        try {
            peerSocket = new Socket(getIP(), getPort());
            outToPeer = new BufferedOutputStream(
                        new DataOutputStream(peerSocket.getOutputStream()));
            inFromPeer = new BufferedInputStream(
                         new DataInputStream(peerSocket.getInputStream()));
        } catch (IOException ex) {
            System.err.println("error: failed to connect to peer at " + getIP());
            return -1;
        }

        return 0;
    }

    /* updateLastUsed: refresh the timestamp for when last used */
    public void updateLastUsed() {
    }

    public boolean[] getBitfield() {
        return this.remoteBitfield;
    }

    public void setBitfield(boolean[] remoteBitfield) {
        this.remoteBitfield = remoteBitfield;
    }

    public void addToBitfield(int index) {
        remoteBitfield[index] = true;
    }

    /* getNextMessage: return the next message off the messageQ */
    public BitMessage getNextMessage() {
        BitMessage msg;
        if (messageQ == null) {
            return null;
        }
        //noinspection SynchronizeOnNonFinalField
        synchronized (messageQ) {
            msg = messageQ.poll();
            messageQ.notifyAll();    // notify reader thread of new space
        }
        return msg;
    }

    /* getRarePiece: return index of a piece had by peer and not in */
    /* remoteBitfield do this randomly, and return -1 if no such piece exists */
    public int getRarePiece(boolean[] clientHas) {
        if (remoteBitfield == null || clientHas == null) {
            return -1;
        }

        int[] rarePieces = new int[clientHas.length];
        int j = 0;
        for (int i = 0; i < clientHas.length; ++i) {
            if (!clientHas[i] && remoteBitfield[i]) {
                rarePieces[j++] = i;
            }
        }
        if (j == 0) {
            return -1;
        }
        Random random = new Random(System.currentTimeMillis());
        return rarePieces[random.nextInt(j)];

    }

    /* write:  write bytes out to socket */
    public void write(byte[] sendData, int offset, int len) {
        if (outToPeer == null) {
            return;
        }

        try {
            outToPeer.write(sendData, offset, len);
            outToPeer.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /* sendHandshake: open socket to peer and send handshake message */
    /* return 0 on success, -1 on failure */
    public void sendHandshake(String encoded) {
        // open connection socket
        try {
            // send handshake
            byte[] handshakeMsg = generateHandshake(encoded);
            outToPeer.write(handshakeMsg, 0, handshakeMsg.length);
            outToPeer.flush();
        } catch (IOException ex) {
            System.err.println("error: could not initiate connection");
        }

    }

    /* receiveHandshake: receive, verify, respond to handshake pattern */
    /* return 0 on success, -1 on failure */
    public int receiveHandshake(String encoded) {
        if (inFromPeer == null || outToPeer == null) {
            System.err.println("error: receiveHandshake found null socket");
            return -1;
        }

        // read peer handshake message (blocking), compare to expected
        byte[] peerHandshakeMsg = new byte[HANDSHAKE_SIZE];
        try {
            // continue reading until full handshake is read;
            int numRead = 0;
            while (numRead < HANDSHAKE_SIZE) {
                numRead += inFromPeer.read(peerHandshakeMsg, numRead, 
                                           HANDSHAKE_SIZE - numRead);
            }
        } catch (IOException ex) {
            System.err.println("error: failed to read entire handshake");
            return -1;
        }

        byte[] myHandshakeMsg = generateHandshake(encoded);
        if (myHandshakeMsg.length != peerHandshakeMsg.length) {
            return -1;
        }
        for (int i = 0; i < myHandshakeMsg.length - 20; ++i) {
            // note: peerID is not being checked here (see "- 20" above)
            if (peerHandshakeMsg[i] != myHandshakeMsg[i]) {
                System.err.println("error: peer at " + getIP() 
                                   + " has wrong .torrent file");
                return -1;
            }
        }

        // initialize reader to read from socket
        this.messageQ = new LinkedList<>();
        BitReader reader = new BitReader(inFromPeer, messageQ);
        Thread t = new Thread(reader);
        t.start();

        return 0;
    }

    public byte[] generateHandshake(String encoded) {
        ByteBuffer handshakeMsg = ByteBuffer.allocate(HANDSHAKE_SIZE);

        // construct 48-byte handshake message
        // (i) byte=19 followed by "BitTorrent protocol"
        byte b = 19;
        handshakeMsg.put(b);
        handshakeMsg.put("BitTorrent protocol".getBytes(StandardCharsets.US_ASCII));
        // (ii) 8-byte extension (here we use all zeroes)
        byte[] pad = new byte[8];
        for (int i = 0; i < 8; ++i) {
            pad[i] = 0;
        }
        handshakeMsg.put(pad);
        // (iii) 20-byte SHA1 encoding of bencoded metainfo (diff. from protocol)
        handshakeMsg.put(Objects.requireNonNull(BitLibrary.getSHA1(encoded)));
        // (iv) 20-byte peer ID (SHA1 encoding of IP and port)
        handshakeMsg.put(peerID);
        handshakeMsg.flip();    // prepare for writing

        return handshakeMsg.array();
    }

    public InetAddress getIP() {
        return peerAddrPort.getAddress();
    }

    public int getPort() {
        return peerAddrPort.getPort();
    }

}
