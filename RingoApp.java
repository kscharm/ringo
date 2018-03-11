import java.io.*;
import java.net.*;
import java.util.*;

public class RingoApp {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Insufficient arguments");
            System.exit(0);
        }
        System.out.println("Welcome to Ringo!");
        InetAddress ipaddr = null; 
        String flag = null;
        int port = -1;
        InetAddress pocHost = null;
        int pocPort = -1;
        int numRingos = -1;
        try {
            flag = args[0];
            pocHost = InetAddress.getByName(args[2]);
            try {
                port = Integer.parseInt(args[1]);
                pocPort = Integer.parseInt(args[3]);
            } catch(NumberFormatException e) {
                System.out.println("Invalid port number: " + e);
                System.exit(0);
            }
            try {
                numRingos = Integer.parseInt(args[4]);
            } catch(NumberFormatException e) {
                System.out.println("Invalid number of Ringos: " + e);
                System.exit(0);
            }
        } catch (IOException e) {
            System.out.println("An I/O error has occured: " + e);
            System.exit(0);
        }
        if (port != -1 && pocHost != null && pocPort != -1 && numRingos != -1) {
            if (flag.equals("R")) {
                Receiver r = new Receiver(port, pocHost, pocPort, numRingos);
            } else if (flag.equals("S")) {
                Sender s = new Sender(port, pocHost, pocPort, numRingos);
            } else if (flag.equals("F")) {
                Forwarder f = new Forwarder(port, pocHost, pocPort, numRingos);
            } else {
                System.out.println("Invalid flag");
                System.exit(0);
            }
        }
        // TODO: send RTT vectors 
        while(true) {
            // TODO: Ringo commands
        }
    }
}