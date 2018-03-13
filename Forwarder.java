import java.util.*;
import java.io.*;
import java.net.*;

public class Forwarder extends Ringo {
    private DatagramSocket forwardSocket = null;
    private DatagramPacket receivePacket = null;
    byte[] outToRingo = null;
    byte[] inFromRingo  = null;
    public Forwarder(int port, InetAddress pocHost, int pocPort, int numRingos) {
        this.port = port;
        this.pocHost = pocHost;
        this.pocPort = pocPort;
        this.numRingos = numRingos;
        this.localRTT = new double[numRingos - 1];
        if (!pocHost.toString().equals("0") && pocPort != 0) {
            this.neighbors.add(new AddrPort(this.pocHost, this.pocPort));
        }
        try {
            forwardSocket = new DatagramSocket(port);
            outToRingo = new byte[1024];
            inFromRingo = new byte[1024];
        } catch (IOException e) {
            System.out.println("An I/O error has occurred: " + e);
            System.exit(0);
        }
        peerDiscovery();
    }

    public void forward(DatagramPacket p) {
        try {
            // Forward to next ringo
            forwardSocket.send(p);
            forwardSocket.setSoTimeout(5000);
        } catch (IOException e) {
            System.out.println("An I/O error has occured: " + e);
            if (e instanceof SocketTimeoutException) {
                System.out.println("Timeout exceeded: " + e);
                isAlive = false;
            }
        }
    }

    public void peerDiscovery() {
        if (!pocHost.toString().equals("0") && pocPort != 0) {
            try {
                String packet = InetAddress.getLocalHost().toString() + " " + Integer.toString(port);
                outToRingo = packet.getBytes();
                DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, pocHost, pocPort);
                try {
                    forwardSocket.send(p);
                    forwardSocket.setSoTimeout(2000);
                } catch (SocketTimeoutException e) {
                    System.out.println("No resposne from POC. Retrying...");
                    forwardSocket.send(p);
                    forwardSocket.setSoTimeout(2000);
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred: " + e);
            }
        } 
        try {
            receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
            forwardSocket.receive(receivePacket);
            String message = new String(inFromRingo, 0, receivePacket.getLength());
            System.out.println("Message: " + message);
            String[] info = message.split(" ");
            InetAddress ipaddr = null;
            int port = 0;
            try {
                ipaddr = InetAddress.getByName(info[0]);
                port = Integer.parseInt(info[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid response: " + e);
            }
            if (ipaddr != null && port != 0) {
                neighbors.add(new AddrPort(ipaddr, port));
            }
        } catch (IOException e) {
            System.out.println("An I/O error has occurred: " + e);
        }
        
    }
    public void receiveRTT() {
    }
    public void sendRTT(int port, InetAddress host, double[] vector) {
    }
}