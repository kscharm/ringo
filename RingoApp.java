import java.io.*;
import java.net.*;
import java.util.*;

public class RingoApp {
    // Initialize data structures
    public static Map<Node, NodeTime[]> globalRTT;
    public static DatagramSocket socket = null;
    public static InetAddress pocHost = null;
    public static int port = -1;
    public static int pocPort = -1;
    public static int numRingos = -1;
    public static String ipaddr = null;
    public static byte[] outToRingo = new byte[2048];
    public static byte[] inFromRingo  = new byte[2048];

    // Flags
    boolean discovery = true;
    boolean rttCalc = false;
    boolean rttTransfer = false;
    boolean newData = false;

    // Define threads
    Runnable thread1, thread2, thread3;
    Thread receiveThread, sendThread, checkThread;

    // Define global ringo object
    Ringo ringo = null;

    public static void main(String[] args) {
        RingoApp app = new RingoApp();
        app.start(args);
    }
    public void start(String[] args) {
        // Parse arguments
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
                ipaddr = InetAddress.getLocalHost().toString();
                if (ipaddr.toString().indexOf("/") != -1) {
                    ipaddr = ipaddr.substring(ipaddr.indexOf("/") + 1, ipaddr.length());
                //    System.out.println(ip);
                }
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
        // Check to see if all arguments were set to a different value
        if (port != -1 && pocHost != null && pocPort != -1 && numRingos != -1) {
            // Create the corresponding ringo
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
        // Global and local RTT vectors
        globalRTT = new HashMap<Node, NodeTime[]>(numRingos);
        ringo.localRTT = new NodeTime[numRingos];

        // Add myself to the list of active ringos
        Node id = new Node(ipaddr, port);
        ringo.active.add(id);
    
        // Start threads
        thread1 = new ReceiveThread();
        thread2 = new SendThread();
        thread3 = new CheckThread();
        receiveThread = new Thread(thread1);
        sendThread = new Thread(thread2);
       // checkThread = new Thread(thread3);

        sendThread.start();
        receiveThread.start();
       // checkThread.start();


        // TODO: Start exchanging RTT
        // TODO: Find optimal ring
        Scanner scan = new Scanner(System.in);
        System.out.println("#### Ringo commands ####");
        System.out.println("1) send <filename>");
        System.out.println("2) show-matrix");
        System.out.println("3) show-ring");
        System.out.println("4) disconnect");
        // Start interactive interface
        while(true) {
            System.out.print("Ringo command: ");
            String input = scan.nextLine();
            // TODO: Implement Ringo command functions
            if (input.indexOf("send") != -1) {
                // Send file
                String filename = input.substring(input.indexOf(" ") + 1, input.length());
                System.out.println("Sending file: " + filename);
            } else if (input.equals("show-matrix")) {
                for (int i = 0; i < ringo.localRTT.length; i++) {
                    System.out.println("Time from: " + new Node(ipaddr, port).toString()
                        + " to " + ringo.active.get(i).toString() + " : " + ringo.localRTT[i]);
                }
            } else if (input.equals("show-ring")) {
                // Show optimal ring formation
                System.out.println("#### Optimal ring ####");
                for (int i = 0; i < ringo.active.size(); i++) {
                    System.out.println(ringo.active.toArray()[i].toString());
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

    /**
     * Thread for receiving packets in a stream
     */
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
                    if (newData) {
                        if (discovery) {
                            try {
                                for (int i = 0; i < ringo.active.size(); i++) {
                                    Node n = ringo.active.get(i);
                                    InetAddress sendIp = InetAddress.getByName(n.addr);
                                    int sendPort = n.port;
                                    for (int j = 0; j < ringo.active.size(); j++) {
                                        if (sendPort != port) {
                                            String payload = ringo.active.get(j).toString();
                                            outToRingo = payload.getBytes();
                                            DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                                            socket.send(p);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.out.println("An I/O error has occurred while sending: " + e);
                            }
                        }
                        if (rttTransfer) {
                            Iterator it = globalRTT.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry pair = (Map.Entry)it.next();
                                Node n = (Node)pair.getKey();
                                NodeTime[] nt = (NodeTime[])pair.getValue();
                                // Attach parent to packet
                                String payload = n.toString() + "|";
                                // Stringify all the local RTT entires
                                for (int i = 0; i < nt.length; i++) {
                                    if (i == nt.length - 1) {
                                        payload += nt[i].toString();
                                    } else {
                                        payload += nt[i].toString() + "|";
                                    }
                                    
                                }
                                // Send the payload to every ringo
                                for (int j = 0; j < ringo.active.size(); j++) {
                                    Node neighbor = ringo.active.get(j);
                                    InetAddress sendIp = InetAddress.getByName(neighbor.addr);
                                    int sendPort = neighbor.port;
                                    outToRingo = payload.getBytes();
                                    DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                                    socket.send(p);
                                }
                                it.remove();
                            }
                        }
                        newData = false;
                    }
                    // Check to see if we know everyone
                    if (ringo.active.size() == numRingos) {
                        discovery = false;
                        rttCalc = true;
                        check();
                        break;
                    }
                    // Check to see if we know all RTTs
                    if (globalRTT.size() == numRingos) {
                        rttTransfer = false;
                        System.out.println("Done");
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred while receiving: " + e);
                System.exit(0);
            }
        }
    }
    /**
     * Thread for sending packets in a stream
     */
    class SendThread implements Runnable {
        public void run() {
            send();
        }
        public void send() {
            // Loop through active to send all
            byte[] sendData = new byte[2048];
            while (true) {
                try {
                    if (!pocHost.toString().equals("0") && pocPort != 0) {
                        if (discovery) {
                            for (int i = 0; i < ringo.active.size(); i++) {
                                String s = ringo.active.get(i).toString();
                                sendData = s.getBytes();
                                DatagramPacket p = new DatagramPacket(sendData, sendData.length, pocHost, pocPort);
                                socket.send(p);
                            }
                        }
                        if (rttTransfer) {
                            System.out.println("RTT Transfer start");
                            Iterator it = globalRTT.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry pair = (Map.Entry)it.next();
                                Node n = (Node)pair.getKey();
                                NodeTime[] nt = (NodeTime[])pair.getValue();
                                // Attach parent to packet
                                String payload = n.toString() + "|";
                                // Stringify all the local RTT entires
                                for (int i = 0; i < nt.length; i++) {
                                    if (i == nt.length - 1) {
                                        payload += nt[i].toString();
                                    } else {
                                        payload += nt[i].toString() + "|";
                                    }
                                    
                                }
                                System.out.println("Sending message: " + payload);
                                // Send the payload to every ringo
                                for (int j = 0; j < ringo.active.size(); j++) {
                                    outToRingo = payload.getBytes();
                                    Node neighbor = ringo.active.get(j);
                                    InetAddress sendIp = InetAddress.getByName(neighbor.addr);
                                    int sendPort = neighbor.port;
                                    DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                                    socket.send(p);
                                }
                                it.remove();
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("An I/O error has occurred: " + e);
                }
            }
        }
    }
    class CheckThread implements Runnable {
        public void run() {
            check();
        }

        public void check() {

        }
    }

    private void receiveMessage(DatagramPacket receivePacket) throws IOException {
        String message = new String(inFromRingo, 0, receivePacket.getLength());
        message = message.replaceAll("[()]", ""); // Get rid of parenthesis
        message = message.replaceAll("\\s+", ""); // Get rid of white space
        String recAddr = null;
        int recPort = 0;
        if (discovery) {
            String[] info = message.split(",");
            try {
                recAddr = info[0];
                recPort = Integer.parseInt(info[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid response: " + e);
            }
            Node tba = new Node(recAddr, recPort);
            if (recAddr != null && recPort != 0 && !ringo.active.contains(tba)) {
                ringo.active.add(tba);
                newData = true;
            }
        }
        if (rttTransfer) {
            System.out.println("Receiving message");
            String[] info = message.split("|");
            NodeTime[] ntArray = new NodeTime[numRingos];
            Node parent = null;
            for (int i = 0; i < info.length; i++) {
                String[] entry = info[i].split(",");
                try {
                    recAddr = entry[0];
                    recPort = Integer.parseInt(entry[1]);
                    Node n = new Node(recAddr, recPort);
                    if (i == 0) {
                        if (globalRTT.containsKey(n)) {
                            break;
                        }
                        parent = n;
                    } else {
                        int time = Integer.parseInt(entry[2]);
                        NodeTime nt = new NodeTime(n, time);
                        ntArray[i - 1] = nt;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid response: " + e);
                }
            }
            if (!globalRTT.containsKey(parent)) {
                globalRTT.put(parent, ntArray);
                newData = true;
            }
        }
    }

    public void check() {
        while (true) {
            if (rttCalc) {
                System.out.println("Calculating RTT");
                for (int i = 0; i < ringo.active.size(); i++) {
                    Node n = ringo.active.get(i);
                    if (n.addr.equals(ipaddr) && n.port == port) {
                        ringo.localRTT[i] = new NodeTime(n, 0);
                    } else {
                        ringo.localRTT[i] = new NodeTime(n, calcRTT(n.addr, n.port));
                    }
                }
                rttCalc = false;
                rttTransfer = true;
                globalRTT.put(new Node(ipaddr, port), ringo.localRTT);
                break;
            }
        }
    }

    private static long calcRTT(String ip, int port) {
        try {
            InetAddress ipaddr = InetAddress.getByName(ip);
            long start = System.currentTimeMillis();
            System.out.println("Start: " + start);
            long finish = 0;
            if (ipaddr.isReachable(4000)) {
                finish = System.currentTimeMillis();
                System.out.println("Finish: " + finish);
                return finish - start;
            } else {
                System.out.println(ipaddr + " is not reachable");
                return Long.MAX_VALUE;
            }
        } catch (Exception e) {
            System.out.println("An exception has occurred: " + e);
            return Long.MAX_VALUE;
        }
    }
}

class Timer {
    long timer;
    Timer() {
        timer = System.currentTimeMillis();
    }
}

class Node {
    String addr;
    int port;
    public Node(String addr, int port) {
        this.addr = addr;
        this.port = port;
    }

    @Override
    public String toString() {
        return "(" + addr + "," + port + ")";
    }

    @Override
    public int hashCode() {
        return addr.hashCode() * port;
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
        Node a = (Node) obj;
        if ((!this.addr.equals(a.addr)) || this.port != a.port) {
            return false;
        }
        return true;
    }
}

class NodeTime {
    Node n;
    long rtt;
    public NodeTime(Node n, long rtt) {
        this.n = n;
        this.rtt = rtt;
    }

    @Override
    public String toString() {
        return  n.toString() + "," + rtt;
    }

    @Override
    public int hashCode() {
        return n.hashCode() * Long.hashCode(rtt);
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
        NodeTime a = (NodeTime) obj;
        if ((!this.n.equals(a.n)) || this.rtt != a.rtt) {
            return false;
        }
        return true;
    }
}