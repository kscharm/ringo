import java.io.*;
import java.net.*;
import java.util.*;

public class RingoApp {
    public static Map<InetAddress, double[]> globalRTT;
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
        Ringo ringo;
        globalRTT = new HashMap<InetAddress, double[]>(numRingos);
        if (port != -1 && pocHost != null && pocPort != -1 && numRingos != -1) {
            if (flag.equals("R")) {
                ringo = new Receiver(port, pocHost, pocPort, numRingos);
            } else if (flag.equals("S")) {
                ringo = new Sender(port, pocHost, pocPort, numRingos);
            } else if (flag.equals("F")) {
                ringo = new Forwarder(port, pocHost, pocPort, numRingos);
            } else {
                System.out.println("Invalid flag");
                System.exit(0);
            }
        }
        
        Scanner scan = new Scanner(System.in);
        System.out.println("#### Ringo commands ####");
        System.out.println("1) send <filename>");
        System.out.println("2) show-matrix");
        System.out.println("3) show-ring");
        System.out.println("4) disconnect");
        while(true) {
            System.out.print("Ringo command: ");
            String input = scan.nextLine();
            // TODO: Implement Ringo command functions
            if (input.indexOf("send") != -1) {
                // Send file
                String filename = input.substring(input.indexOf(" ") + 1, input.length());
                System.out.println("Sending file: " + filename);
            } else if (input.equals("show-matrix")) {
                // Show RTT vector matrix
                System.out.println("--");
            } else if (input.equals("show-ring")) {
                // Show optimal ring formation
                System.out.println("#### Optimal ring ####");
            } else if (input.equals("disconnect")) {
                // Terminate ringo process
                scan.close();
                System.exit(0);
            } else {
                System.out.println("Invalid input");
            }
        }
    }
}