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
            forwardSocket.setSoTimeout(2000);
            forwardSocket.send(p);
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
                int retryAttempts = 5;
                while (retryAttempts > 0) {
                    try {
                        forwardSocket.setSoTimeout(3000);
                        forwardSocket.send(p);
                        receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
                        forwardSocket.receive(receivePacket);
                        break;
                    } catch (IOException e) {
                        retryAttempts--;
                        if (e instanceof SocketTimeoutException) {
                            System.out.println("No response from POC. Retrying...");
                        }
                    } 
                }
                receiveMessage(receivePacket);
            } catch (IOException e) {
                if (e instanceof SocketTimeoutException) {
                    System.out.println("No response. Exiting...");
                }
                System.out.println("An I/O error has occurred: " + e);
                System.exit(0);
            }
        } 
        
    }

    private void receiveMessage(DatagramPacket receivePacket) throws IOException {
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
        System.out.println(neighbors.toString());
    }
    public void receiveRTT() {
    }
    public void sendRTT(int port, InetAddress host, double[] vector) {
    }
}