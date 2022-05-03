/* BitWelcomer.java:  runnable class to welcome incoming BitTorrent peers */
/* main purpose is to provide non-blocking acceptance of new peers */
/* Christopher Chute */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class BitWelcomer extends Thread {
    private ServerSocket welcomeSocket = null;  // welcome new peers
    private final LinkedList<Socket> welcomeQ; // pending new peers
    private volatile boolean isStopped = false;

    public BitWelcomer(int welcomePort, final LinkedList<Socket> welcomeQ) {
        this.welcomeQ = welcomeQ;

        try {
            welcomeSocket = new ServerSocket(welcomePort);
            welcomeSocket.setSoTimeout(5000);
            System.out.println("Client listening on port " + welcomePort);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void stopThread() {
        isStopped = true;
    }

    /* run:  continually loop to accept new BitTorrent peers */
    public void run() {
        while (!isStopped) {
            Socket peerSocket;
            try {
                peerSocket = welcomeSocket.accept();
            } catch (Exception ex) {
                continue;
            }
            if (peerSocket != null) {
                synchronized (welcomeQ) {
                    welcomeQ.offer(peerSocket);
                    welcomeQ.notifyAll();
                }
            }
        }
    }
}
