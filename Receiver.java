import java.util.*;
import java.io.*;
import java.net.*;

public class Receiver extends Ringo {
    public Receiver (int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
        this.localRTT = new double[numRingos - 1];
        sendRTT(pocPort, pocHost, localRTT);
    }

    public void receive(DatagramPacket p) {

    }

    public void sendRTT(int port, InetAddress host, double[] vector) {

    }
}