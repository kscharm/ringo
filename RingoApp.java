import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Interactive token-ring networking application.
 * Authors: Kenneth Scharm and Rudy Lowenstein
 */
public class RingoApp {
    // Initialize data structures
    public static Map<Node, NodeTime[]> globalRTT = Collections.synchronizedMap(new HashMap<Node, NodeTime[]>());
    public static DatagramSocket socket = null;
    public static InetAddress pocHost = null;
    public static int port = -1;
    public static int pocPort = -1;
    public static int numRingos = -1;
    public static String ipaddr = null;

    // Flags
    volatile Boolean discovery = true;
    volatile Boolean rttCalc = false;
    volatile Boolean rttTransfer = false;
    volatile Boolean newData = false;

    // Define threads
    Runnable thread1, thread2, thread3;
    Thread receiveThread, sendThread, checkThread;

    // Define global ringo object
    Ringo ringo = null;

    public static void main(String[] args) {
        RingoApp app = new RingoApp();
        app.start(args);
    }
    /**
     * Launches the Ringo application and starts the command-line interface.
     */
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
                }
                port = Integer.parseInt(args[1]);
                pocPort = Integer.parseInt(args[3]);
                socket = new DatagramSocket(port);
            } catch(NumberFormatException e) {
                System.out.println("Invalid port number " + e.getMessage());
                System.exit(0);
            }
            try {
                numRingos = Integer.parseInt(args[4]);
            } catch(NumberFormatException e) {
                System.out.println("Invalid number of Ringos " + e.getMessage());
                System.exit(0);
            }
        } catch (IOException e) {
            System.out.println("An I/O error has occured " + e.getMessage());
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

        // Add myself to the list of active ringos
        Node id = new Node(ipaddr, port);
        ringo.active.add(id);
    
        // Start threads
        thread1 = new ReceiveThread();
        thread2 = new SendThread();
        receiveThread = new Thread(thread1);
        sendThread = new Thread(thread2);
        receiveThread.start();
        sendThread.start();

        // TODO: Find optimal ring
        Scanner scan = new Scanner(System.in);
        System.out.println("#### Ringo commands ####");
        System.out.println("1) send <filename>");
        System.out.println("2) show-matrix");
        System.out.println("3) show-ring");
        System.out.println("4) offline <seconds>");
        System.out.println("5) disconnect");
        // Start interactive interface
        while(true) {
            System.out.print("Ringo command: ");
            String input = scan.nextLine();
            // TODO: Implement send
            if (input.indexOf("send") != -1) {
                // Send file
                String filename = input.substring(input.indexOf(" ") + 1, input.length());
                System.out.println("Sending file: " + filename);
            } else if (input.equals("show-matrix")) {
                if (ringo.active.size() == numRingos) {
                    System.out.println("#### Global RTT Matrix ####");
                    printMap(globalRTT);
                } else {
                    System.out.println("Not all ringos have been discovered");
                }
            } else if (input.equals("show-ring")) {
                // Show optimal ring formation
                System.out.println("#### Optimal ring ####");
                for (int i = 0; i < ringo.active.size(); i++) {
                    if (i == ringo.active.size() - 1) {
                        System.out.print(ringo.active.toArray()[i].toString() + "\n");
                    } else {
                        System.out.print(ringo.active.toArray()[i].toString() + " <--> ");
                    }
                }
            } else if (input.indexOf("offline") != -1) {
                try {
                    String s = input.substring(input.indexOf(" ") + 1, input.length());
                    int seconds = Integer.parseInt(s);
                    System.out.println("Ringo going offline for " + seconds + " seconds");
                } catch (NumberFormatException e) {
                    System.out.println("Invalid response " + e.getMessage());
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
     * Thread for receiving packets in a stream.
     */
    class ReceiveThread implements Runnable {
        public void run() {
            receive();
        }
        /**
         * Receives incoming packets, parses each packet depending on the current flag, and broadcasts it to all other Ringos.
         * Currently supports peer discovery and RTT transfer.
         */
        public void receive() {
            byte[] outToRingo = new byte[2048];
            byte[] inFromRingo  = new byte[2048];
            DatagramPacket receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
            try {
                // Always be receiving incoming packets
                while (true) {
                    socket.receive(receivePacket);
                    // Check to see if the packet we have received is not empty
                    if (receivePacket.getLength() != inFromRingo.length) {
                        receiveMessage(inFromRingo, receivePacket);
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
                                            sendThread.sleep(100);
                                            socket.send(p);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.out.println("An I/O error has occurred while sending " + e.getMessage());
                            }
                        }
                        else if (rttTransfer) {
                            String payload = "";
                            synchronized(globalRTT) {
                                Iterator it = globalRTT.entrySet().iterator();
                                while (it.hasNext()) {
                                    Map.Entry pair = (Map.Entry)it.next();
                                    Node n = null;
                                    NodeTime[] nt =null;
                                    n = (Node)pair.getKey();
                                    nt = (NodeTime[])pair.getValue();
                                    // Attach parent to packet
                                    payload = n.toString() + "x";
                                    // Stringify all the local RTT entires
                                    for (int i = 0; i < nt.length; i++) {
                                        if (nt != null) {
                                            if (i == nt.length - 1) {
                                                payload += nt[i].toString();
                                            } else {
                                                payload += nt[i].toString() + "x";
                                            }
                                        }
                                    }
                                }
                                // Send the payload to every ringo
                                for (int j = 0; j < ringo.active.size(); j++) {
                                    Node neighbor = ringo.active.get(j);
                                    InetAddress sendIp = InetAddress.getByName(neighbor.addr);
                                    int sendPort = neighbor.port;
                                    outToRingo = payload.getBytes();
                                    DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                                    sendThread.sleep(100);
                                    socket.send(p);
                                }
                            }
                        }
                        newData = false;
                    }
                    // Check to see if we know everyone
                    if (discovery && ringo.active.size() == numRingos) {
                        discovery = false;
                        receiveThread.sleep(100);
                        rttCalc = true;
                        check();
                    }
                    
                    // Check to see if local RTT vector calculations have completed
                    if (!discovery && ringo.localRTT.length == numRingos) {
                        rttCalc = false;
                        receiveThread.sleep(100);
                        // rttTransfer = true;
                    }
                    // Check to see if we know all RTTs
                    if (rttTransfer && getSize() == numRingos) {
                        rttTransfer = false;
                    }
                }
            } catch (Exception e) {
                if (e instanceof IOException) {
                    System.out.println("An I/O error has occurred " + e.getMessage());
                }
                if (e instanceof InterruptedException) {
                    System.out.println("Thread interrupted " + e.getMessage());
                }
            }
        }
    }
    /**
     * Thread for sending packets in a stream.
     */
    class SendThread implements Runnable {
        public void run() {
            send();
        }
        /**
         * Creates a new packet and sends it to all other active Ringos.
         * Currently supports peer discovery and RTT transfer.
         */
        public void send() {
            byte[] sendData = new byte[2048];
            // Loop through active to send all
            while (true) {
                try {
                    if (!pocHost.toString().equals("0") && pocPort != 0) {
                        if (discovery) {
                            for (int i = 0; i < ringo.active.size(); i++) {
                                String s = ringo.active.get(i).toString();
                                sendData = s.getBytes();
                                DatagramPacket p = new DatagramPacket(sendData, sendData.length, pocHost, pocPort);
                                sendThread.sleep(100);
                                socket.send(p);
                            }
                        }
                        else if (rttTransfer) {
                            synchronized(globalRTT) {
                                // Iterate through global RTT matrix and send each entry to all other Ringos
                                Iterator it = globalRTT.entrySet().iterator();
                                while (it.hasNext()) {
                                    Map.Entry pair = (Map.Entry)it.next();
                                    Node n = (Node)pair.getKey();
                                    NodeTime[] nt = (NodeTime[])pair.getValue();
                                    // Attach parent to packet
                                    String payload = n.toString() + "x";
                                    // Stringify all the local RTT entires
                                    for (int i = 0; i < nt.length; i++) {
                                        if (nt != null) {
                                            if (i == nt.length - 1) {
                                                payload += nt[i].toString();
                                            } else {
                                                payload += nt[i].toString() + "x";
                                            }
                                        }
                                    }
                                    // Send the payload to every ringo
                                    for (int j = 0; j < ringo.active.size(); j++) {
                                        sendData = payload.getBytes();
                                        Node neighbor = ringo.active.get(j);
                                        InetAddress sendIp = InetAddress.getByName(neighbor.addr);
                                        int sendPort = neighbor.port;
                                        DatagramPacket p = new DatagramPacket(sendData, sendData.length, sendIp, sendPort);
                                        sendThread.sleep(100);
                                        socket.send(p);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof IOException) {
                        System.out.println("An I/O error has occurred " + e.getMessage());
                    }
                    if (e instanceof InterruptedException) {
                        System.out.println("Thread interrupted " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Parses a message and extracts important information, depending on the flag.
     */
    private void receiveMessage(byte[] buffer, DatagramPacket receivePacket) throws IOException {
        String message = new String(buffer, 0, receivePacket.getLength());
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
            String[] info = message.split("x");
            NodeTime[] ntArray = new NodeTime[numRingos];
            Node parent = null;
            for (int i = 0; i < info.length; i++) {
                String[] entry = info[i].split(",");
                try {
                    recAddr = entry[0];
                    recPort = Integer.parseInt(entry[1]);
                    Node n = new Node(recAddr, recPort);
                    if (i == 0) {
                        synchronized(globalRTT) {
                            if (globalRTT.containsKey(n)) {
                                break;
                            }
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
            synchronized(globalRTT) {
                if (!globalRTT.containsKey(parent)) {
                    globalRTT.put(parent, ntArray);
                    newData = true;
                }
            }   
        }
    }

    /**
     * Calculates the Round-Trip Time (RTT) by pinging a given IP address and port
     * and returning the amount of time it takes.
     */
    private static long calcRTT(String ip, int port) {
        try {
            InetAddress ipaddr = InetAddress.getByName(ip);
            long start = System.currentTimeMillis();
            long finish = 0;
            // Ping the IP and see if we get a response
            if (ipaddr.isReachable(5000)) {
                finish = System.currentTimeMillis();
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

    /**
     * Checks to see if we are in the calculate RTT state. If so,
     * add a new entry into the globalRTT matrix.
     */
    public void check() {
        if (rttCalc) {
            for (int i = 0; i < ringo.active.size(); i++) {
                Node n = ringo.active.get(i);
                if (n.addr.equals(ipaddr) && n.port == port) {
                    ringo.localRTT[i] = new NodeTime(n, 0);
                } else {
                    ringo.localRTT[i] = new NodeTime(n, calcRTT(n.addr, n.port));
                }
            }
            synchronized(globalRTT) {
                globalRTT.put(new Node(ipaddr, port), ringo.localRTT);
            }
            rttCalc = false;
        }
    }

    /**
     * Prints the global RTT matrix.
     */
    public static void printMap(Map mp) {
        synchronized(mp) {
            Iterator it = mp.entrySet().iterator();
            System.out.println("Format: A = (A, RTT) | (B, RTT) | (C,RTT)");
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                System.out.print(pair.getKey() + " = ");
                NodeTime[] ntArray = (NodeTime[])pair.getValue();
                for (int i = 0; i < ntArray.length; i++) {
                    if (ntArray[i] != null) {
                        if (i == ntArray.length - 1) {
                            System.out.print(ntArray[i].toString());
                        } else {
                            System.out.print(ntArray[i].toString() + "|");
                        }
                    }
                }
                System.out.println();
            }
        }
        
    }
    /**
     * Returns the size of the global RTT matrix.
     */
    public int getSize() {
        synchronized(this) {
            return globalRTT.size();
        }
    }
}


/**
 * Node class that represents a Ringo.
 */
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
        return addr.hashCode() + port;
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

/**
 * NodeTime class that represents a Ringo and its coresponding RTT.
 */
class NodeTime {
    Node n;
    long rtt;
    public NodeTime(Node n, long rtt) {
        this.n = n;
        this.rtt = rtt;
    }

    @Override
    public String toString() {
        return "(" + n.toString() + "," + rtt + ")";
    }

    @Override
    public int hashCode() {
        return n.hashCode() + Long.hashCode(rtt);
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