/* BitReader.java:  runnable class to read from a connected BitPeer */
/* main purpose is to provide non-blocking reading of messages into messageQ */
/* Christopher Chute */

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Queue;

public class BitReader implements Runnable {
    private static final int INT_LEN = 4;
    private static final int MSG_BACKLOG = 10;    // max outstanding messages

    private final InputStream inFromPeer;        // incoming messages
    private final Queue<BitMessage> messageQ;    // queue of messages
    private volatile boolean isStopped = false;   // for killing thread

    public BitReader(final InputStream inp, final Queue<BitMessage> queue) {
        this.inFromPeer = inp;
        this.messageQ = queue;
    }

    public void stopThread() {
        this.isStopped = true;
    }

    public void run() {
        byte[] lenBuf = new byte[INT_LEN];
        while (!isStopped) {
            // read length of message
            int numRead = 0;
            try {
                numRead = inFromPeer.read(lenBuf, 0, INT_LEN);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (numRead != INT_LEN) {
                throw new RuntimeException("error: reader thread misaligned");
            }
            // NOTE: use ByteBuffer for integer encoding
            ByteBuffer buf = ByteBuffer.wrap(lenBuf);
            int msgLen = buf.getInt();

            // read rest of message
            byte[] rcvData = new byte[INT_LEN + msgLen];
            // (i) copy over the message length
            System.arraycopy(lenBuf, 0, rcvData, 0, INT_LEN);
            // (ii) read rest of message from inFromPeer
            try {
                // continue reading until entire message is read
                for (numRead = 0; numRead < msgLen; ) {
                    numRead += inFromPeer.read(rcvData, INT_LEN + numRead, msgLen - numRead);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (numRead != msgLen) {
                System.err.format("warning: msgLen was %d but only got %d\n", msgLen, numRead);
            }

            BitMessage msg = BitMessage.unpack(rcvData);

            // add message to the messageQ, wait if there's a backlog
            synchronized (messageQ) {
                while (messageQ.size() >= MSG_BACKLOG) {
                    try {
                        messageQ.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
                messageQ.offer(msg);
            }
        }
    }
}
