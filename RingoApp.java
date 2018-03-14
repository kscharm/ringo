import java.io.*;
import java.net.*;
import java.util.*;

public class RingoApp {
    public static Map<InetAddress, double[]> globalRTT;
    public static DatagramSocket socket = null;
    public static InetAddress pocHost = null;
    public static int port = -1;
    public static int pocPort = -1;
    public static int numRingos = -1;
    public static InetAddress ipaddr = null;
    public static byte[] outToRingo = new byte[1024];
    public static byte[] inFromRingo  = new byte[1024];
    Runnable thread1, thread2;
    Thread receiveThread, sendThread;
    Ringo ringo = null;
    public static void main(String[] args) {
        RingoApp app = new RingoApp();
        app.start(args);
    }
    public void start(String[] args) {
        if (args.length < 5) {
            System.out.println("Insufficient arguments");
            System.exit(0);
        }
        System.out.println("Welcome to Ringo!");
        String flag = null;
        try {
            flag = args[0];
            pocHost = InetAddress.getByName(args[2]);
            try {
                port = Integer.parseInt(args[1]);
                pocPort = Integer.parseInt(args[3]);
                socket = new DatagramSocket(port);
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
        thread1 = new ReceiveThread();
        thread2 = new SendThread();
        receiveThread = new Thread(thread1);
        sendThread = new Thread(thread2);
        if (!ringo.pocHost.toString().equals("0") && ringo.pocPort != 0) {
                sendThread.start();
                receiveThread.start();
        } else {
                receiveThread.start();
        } 
        if (ringo.neighbors.size() != numRingos - 1) {
            System.out.println("Neighbor list: ");
            Iterator<AddrPort> it = ringo.neighbors.iterator();
            while (it.hasNext()) {
                System.out.println(it.next().toString());
            }
            // Start exchanging RTT
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

    private void receiveMessage(DatagramPacket receivePacket) throws IOException {
        String message = new String(inFromRingo, 0, receivePacket.getLength());
        String[] info = message.split(" ");
        InetAddress ipaddr = null;
        int port = 0;
        try {
            String ip = "";
            if (info[0].indexOf("/") != -1) {
                ip = info[0].substring(info[0].indexOf("/") + 1, info[0].length());
            }
            ipaddr = InetAddress.getByName(ip);
            port = Integer.parseInt(info[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid response: " + e);
        }
        if (ipaddr != null && port != 0) {
            ringo.neighbors.add(new AddrPort(ipaddr, port));
        }
    }
    class ReceiveThread implements Runnable {
        private SendThread send;
        public void run() {
            receive();
        }

        public void receive() {
            DatagramPacket receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
            try {
                socket.receive(receivePacket);
                if (receivePacket.getLength() != inFromRingo.length) {
                    receiveMessage(receivePacket);
                    // String packet1 = InetAddress.getLocalHost().toString() + " " + Integer.toString(port);
                    // outToRingo = packet1.getBytes();
                    // DatagramPacket p1 = new DatagramPacket(outToRingo, outToRingo.length, pocHost, pocPort);
                    // forwardSocket.send(p1);
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred: " + e);
                System.exit(0);
            }
        }
    }

    class SendThread implements Runnable {
        public void run() {
            send(ringo.pocHost, ringo.pocPort, ringo.neighbors);
        }
        public void send(InetAddress pocHost, int pocPort, Set<AddrPort> neighbors) {
            // Loop through neighbors to send all
            Iterator<AddrPort> it = ringo.neighbors.iterator();
            while (it.hasNext()) {
                String s = it.next().toString();
                outToRingo = s.getBytes();
                DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, pocHost, pocPort);
                int retryAttempts = 5;
                try {
                    socket.setSoTimeout(3000);
                    socket.send(p);
                } catch (IOException e) {
                    if (e instanceof SocketTimeoutException) {
                        System.out.println("No response from POC. Retrying...");
                    }
                } 
            }
        }
    }
}

class Timer {
    long timer;
    Timer() {
        timer = System.currentTimeMillis();
    }
}

class AddrPort {
    InetAddress addr;
    int p;
    public AddrPort(InetAddress addr, int p) {
        this.addr = addr;
        this.p = p;
    }

    public String toString() {
        return "(" + addr + ", " + p + ")";
    }
}