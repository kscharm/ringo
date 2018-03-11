import java.util.*;
import java.io.*;
import java.net.*;

public class Sender extends Ringo {
    public Sender(int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
    }

    public void send(DatagramPacket p) {

    }

    public void sendRTT(Ringo r, double[] vector) {
        
    }
}