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
    boolean discovery = true;
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
                ipaddr = InetAddress.getLocalHost();
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
        // Add myself to the list of active ringos
        AddrPort id = new AddrPort(ipaddr, port);
        ringo.active.add(id);
        
        // Start threads
        thread1 = new ReceiveThread();
        thread2 = new SendThread();
        receiveThread = new Thread(thread1);
        sendThread = new Thread(thread2);
        sendThread.start();
        receiveThread.start();

        // Check to see if we know everyone
        if (ringo.active.size() == numRingos) {
            discovery = false;
            System.out.println("Active list: ");
            Iterator<AddrPort> it = ringo.active.iterator();
            while (it.hasNext()) {
                System.out.println(it.next().toString());
            }
        }
        // TODO: Start exchanging RTT
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
                System.out.println("--");
            } else if (input.equals("show-ring")) {
                // Show optimal ring formation
                System.out.println("#### Optimal ring ####");
                Iterator<AddrPort> it = ringo.active.iterator();
                while (it.hasNext()) {
                    System.out.println(it.next().toString());
                }
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
        message = message.replaceAll("[()]", ""); // Get rid of parenthesis
        message = message.replaceAll("\\s+", ""); // Get rid of white space
        System.out.println("Message: " + message);
        if (discovery) {
            String[] info = message.split(",");
            InetAddress recAddr = null;
            int recPort = 0;
            try {
                String ip = "";
                if (info[0].indexOf("/") != -1) {
                    ip = info[0].substring(info[0].indexOf("/") + 1, info[0].length());
                }
                recAddr = InetAddress.getByName(ip);
                recPort = Integer.parseInt(info[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid response: " + e);
            }
            if (recAddr != null && recPort != 0) {
                ringo.active.add(new AddrPort(recAddr, recPort));
            }
        }
    }
    class ReceiveThread implements Runnable {
        public void run() {
            receive();
        }

        public void receive() {
            DatagramPacket receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
            try {
                while (true) {
                    socket.receive(receivePacket);
                    if (receivePacket.getLength() != inFromRingo.length) {
                        receiveMessage(receivePacket);
                    }
                    if (discovery) {
                        Iterator<AddrPort> it = ringo.active.iterator();
                        while (it.hasNext()) {
                            String sendLoc = it.next().toString();
                            String s1 = sendLoc.substring(sendLoc.indexOf("/") + 1, sendLoc.indexOf(",") - 1);
                            InetAddress sendIp = InetAddress.getByName(s1);
                            String s2 = sendLoc.substring(sendLoc.indexOf(",") + 1, sendLoc.indexOf(")") - 1);
                            int sendPort = Integer.parseInt(s2);
                            try {
                                Iterator<AddrPort> it1 = ringo.active.iterator();
                                while(it1.hasNext()) {
                                    String payload = it1.next().toString();
                                    outToRingo = payload.getBytes();
                                    DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                                    socket.send(p);
                                }
                            } catch (IOException e) {
                                System.out.println("An I/O error has occurred while sending: " + e);
                            } 
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred while receiving: " + e);
                System.exit(0);
            }
        }
    }

    class SendThread implements Runnable {
        public void run() {
            send(ringo.pocHost, ringo.pocPort, ringo.active);
        }
        public void send(InetAddress pocHost, int pocPort, Set<AddrPort> active) {
            // Loop through active to send all
            if (discovery && !pocHost.toString().equals("0") && pocPort != 0) {
                Iterator<AddrPort> it = ringo.active.iterator();
                while (it.hasNext()) {
                    String s = it.next().toString();
                    System.out.println(s);
                    outToRingo = s.getBytes();
                    DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, pocHost, pocPort);
                    try {
                        socket.send(p);
                    } catch (IOException e) {
                        System.out.println("An I/O error has occurred while sending: " + e);
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

    @Override
    public String toString() {
        return "(" + addr + "," + p + ")";
    }

    @Override
    public int hashCode() {
        return addr.hashCode() * p;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AddrPort a = (AddrPort) obj;
        if (this.addr != a.addr || this.p != a.p) {
            return false;
        }
        return true;
    }
}