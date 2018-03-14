import java.util.*;
import java.io.*;
import java.net.*;

public class Forwarder extends Ringo {
    public Forwarder(int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
        this.localRTT = new double[numRingos - 1];
        try {
            if (!pocHost.toString().equals("0") && pocPort != 0) {
                String hostIP = pocHost.getHostAddress();
                InetAddress ipaddr = InetAddress.getByName(hostIP);
                this.neighbors.add(new AddrPort(ipaddr, this.pocPort));
            }
        } catch (IOException e) {
            System.out.println("An I/O error has occurred: " + e);
            System.exit(0);
        }
    }

    public void forward(DatagramPacket p) {
        
    }
}