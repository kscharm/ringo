import java.util.*;
import java.io.*;
import java.net.*;

public class Forwarder extends Ringo {
    private DatagramSocket forwardSocket = null;
    public Forwarder(int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
        this.localRTT = new double[numRingos - 1];
        try {
            forwardSocket = new DatagramSocket();
            sendRTT(pocPort, pocHost, localRTT);
        } catch (IOException e) {
            System.out.println("An I/O error has occured: " + e);
            System.exit(0);
        }
    }

    public void forward(DatagramPacket p) {
        try {
            // Forward to next ringo
            forwardSocket.send(p);
        } catch (IOException e) {
            System.out.println("An I/O error has occured: " + e);
            isAlive = false;
        }
        
    }

    public void sendRTT(int port, InetAddress host, double[] vector) {

    }
}