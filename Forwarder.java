import java.util.*;
import java.io.*;
import java.net.*;

public class Forwarder extends Ringo {
    public Forwarder(int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
        this.localRTT = new NodeTime[numRingos];
    }

    public void forward(Packet p) {
        
    }
}