import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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
    public static int rttLength = 0;
    public static int sequenceNum = 0;
    public static String ipaddr = null;

    public static BlockingQueue<Packet> packetQueue;
    // Flags
    static volatile boolean discovery = true;
    static volatile boolean rttCalc = false;
    static volatile boolean rttTransfer = false;

    // Define threads
    //Runnable thread1, thread2, thread3;

    private static ExecutorService receiveThread = Executors.newSingleThreadExecutor();
    private static ExecutorService sendThread = Executors.newCachedThreadPool();
    private static ExecutorService keepAliveThread = Executors.newSingleThreadExecutor();

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

        if (!pocHost.equals(0) && pocPort != 0) {
            //System.out.println("sending to PoC");
            Packet first = new Packet("1:" + id.toString(), id, new Node(pocHost.getHostAddress(), pocPort));
            sendPacket(first);
        }


        // Start threads
        packetQueue = new LinkedBlockingQueue<>();

        receiveThread.submit(new ReceiveThread());

        //thread1 = new ReceiveThread();
        //thread2 = new SendThread();
        //thread3 = new KeepAliveThread();
        //receiveThread = new Thread(thread1);
        //sendThread = new Thread(thread2);
        //keepAliveThread = new Thread(thread3);
        //receiveThread.start();
        //sendThread.start();
        //keepAliveThread.start();

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
            try {
                // Always be receiving incoming packets
                while (true) {
                    byte[] inFromRingo  = new byte[2048];
                    DatagramPacket receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
                    socket.receive(receivePacket);

                    int port = receivePacket.getPort();
                    String address = receivePacket.getAddress().getHostAddress();

                    // Check to see if the packet we have received is not empty
                    if (receivePacket.getLength() != inFromRingo.length) {
                        receiveMessage(inFromRingo, address, port, receivePacket);
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

    public void sendPacket(Packet p) {
        sendThread.submit(new SendThread(p));
    }


    /**
     * Thread for sending packets in a stream.
     */
    class SendThread implements Runnable {

        Packet p;

        public SendThread(Packet p) {
            this.p = p;
        }
        public void run() {
            send();
        }
        /**
         * Creates a new packet and sends it to all other active Ringos.
         * Currently supports peer discovery and RTT transfer.
         */
        public void send() {
            String payload = p.getPayload();
            Node dest = p.getDestination();

            byte[] buffer = new byte[2048];
            buffer = payload.getBytes();
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(dest.getAddress()), dest.getPort());
                socket.send(packet);
            } catch (UnknownHostException e) {
                System.out.println("Unknown Host: " + e.getMessage());
            } catch (IOException f) {
                System.out.println(f.getMessage());
            }
        }
            /*
        System.out.println("Sender RTT Value: " + rttTransfer);
        byte[] sendData = new byte[2048];
        // Loop through active to send all
            try {
                if (discovery) {
                    if (!pocHost.toString().equals("0") && pocPort != 0) {
                        for (int i = 0; i < ringo.active.size(); i++) {
                            String s = ringo.active.get(i).toString();
                            String header = "1:";
                            String message = header + s;
                            sendData = message.getBytes();
                            DatagramPacket p = new DatagramPacket(sendData, sendData.length, pocHost, pocPort);
                            socket.send(p);
                        }
                    }
                }
                if (rttTransfer) {
                    System.out.println("DEBUG");
                    synchronized(globalRTT) {
                        // Iterate through global RTT matrix and send each entry to all other Ringos
                        Iterator it = globalRTT.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry pair = (Map.Entry)it.next();
                            Node n = (Node)pair.getKey();
                            NodeTime[] nt = (NodeTime[])pair.getValue();
                            String header = "2:";
                            // Attach parent to packet
                            String payload = header + n.toString() + "x";
                            System.out.println("Length: " + nt.length);
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
                            sendData = payload.getBytes();
                            for (int j = 0; j < ringo.active.size(); j++) {
                                Node neighbor = ringo.active.get(j);
                                InetAddress sendIp = InetAddress.getByName(neighbor.getAddress());
                                int sendPort = neighbor.getPort();
                                DatagramPacket p = new DatagramPacket(sendData, sendData.length, sendIp, sendPort);
                                socket.send(p);
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
            System.out.println("Setting flags...");
            setFlags();
        }
        */
    }

    class KeepAliveThread implements Runnable {
        public void run() {
            keepAlive();
        }
        /**
         * Periodically sends alive packet to all known nodes
         */
        public void keepAlive() {
            Timer timer = new Timer();
            if (!discovery) {
                // Once discovery is complete, schedule alive packets to be sent every minute
                timer.schedule(new SendAlivePackets(), 0, 60000);
            }
        }
    }
    
    /**
     * Parses a message and extracts important information, depending on the flag.
     */
    private void receiveMessage(byte[] buffer, String address, int port, DatagramPacket dp) throws IOException {
        String message = new String(buffer, 0, dp.getLength());
        message = message.replaceAll("[()]", ""); // Get rid of parenthesis
        message = message.replaceAll("\\s+", ""); // Get rid of white space
        String recAddr = null;
        int recPort = 0;
        int headerIndex = message.indexOf(":");
        String header = message.substring(0, headerIndex);
        message = message.substring(headerIndex + 1);
        byte[] outToRingo = new byte[2048];
        DatagramPacket p = null;
        /*
        // TODO: Send ACK back to source
        String ackString = "ACK:" + sequenceNum;
        outToRingo = ackString.getBytes();
        DatagramPacket p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
        socket.send(p);
        sequenceNum++;
        */
        if (header.equals("Alive")) {
            // TODO: determine if any Ringos went down
            String[] info = message.split(",");
        }
        // Header of 1 means peer discovery
        if (header.equals("1")) {
            String[] info = message.split(",");
            try {
                recAddr = info[0];
                recPort = Integer.parseInt(info[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid response: " + e);
            }
            Node tba = new Node(address, port);
            if (recAddr != null && recPort != 0 && !ringo.active.contains(tba)) {
                ringo.active.add(tba);
                for (int j = 0; j < ringo.active.size(); j++) {
                    Node n = ringo.active.get(j);
                    int sendPort = n.getPort();
                    if (sendPort != port) {
                        String payload = header + ":" + ringo.active.get(j).toString();
                        //outToRingo = payload.getBytes();
                        Packet packet = new Packet(payload, null , tba);
                        //p = new DatagramPacket(outToRingo, outToRingo.length, sendIp, sendPort);
                        //System.out.println("About to send...");
                        sendPacket(packet);
                        //packetQueue.put(packet);
                    }
                }
            }
        }
        // Header of 2 means RTT vector exchange
        if (header.equals("2")) {
            String[] info = message.split("x");
            NodeTime[] ntArray = new NodeTime[numRingos];
            Node parent = null;
            for (int i = 0; i < info.length; i++) {
                System.out.println("GlobalRTT entry: " + info[i]);
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
                    System.out.println("Putting " + parent + " into globalRTT");
                    globalRTT.put(parent, ntArray);
                }
            } 
        }

        setFlags();

    }

    public void setFlags() {
        // Check to see if we know everyone
        if (discovery && ringo.active.size() == numRingos) {
            System.out.println("Discovery complete");
            discovery = false;
            rttCalc = true;
            calcRTTvectors();
        }
        // Check to see if local RTT vector calculations have completed
        if (rttCalc && rttLength == numRingos) {
            System.out.println("Local RTT vector calculations complete");
            rttCalc = false;
            rttTransfer = true;
        }
        // Check to see if we know all RTTs
        if (rttTransfer && getSize() == numRingos) {
            System.out.println("RTT exchange complete");
            rttTransfer = false;
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
    public void calcRTTvectors() {
        synchronized(ringo.active) {
            for (int i = 0; i < ringo.active.size(); i++) {
                Node n = ringo.active.get(i);
                if (n.getAddress().equals(ipaddr) && n.getPort() == port) {
                    ringo.localRTT[i] = new NodeTime(n, 0);
                } else {
                    ringo.localRTT[i] = new NodeTime(n, calcRTT(n.getAddress(), n.getPort()));
                }
                rttLength++;
            }
            synchronized(globalRTT) {
                globalRTT.put(new Node(ipaddr, port), ringo.localRTT);
            }
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

    class SendAlivePackets extends TimerTask {
        public void run() {
            // Send the payload to every ringo
            byte[] sendData = new byte[2048];
            String payload = "Alive:" + new Node(ipaddr, port).toString();
            sendData = payload.getBytes();
            try {
                for (int j = 0; j < ringo.active.size(); j++) {
                    Node neighbor = ringo.active.get(j);
                    InetAddress sendIp = InetAddress.getByName(neighbor.getAddress());
                    int sendPort = neighbor.getPort();
                    if (sendPort != port) {
                        DatagramPacket p = new DatagramPacket(sendData, sendData.length, sendIp, sendPort);
                        socket.send(p);
                    }
                    
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred while sending alive packet " + e.getMessage());
            }
            
        }
    }
}

class Packet {

    String payload;
    Node source;
    Node destination;

    public Packet(String p, Node s, Node d) {
        payload = p;
        source = s;
        destination = d;
    }

    public String getPayload() {
        return payload;
    }

    public Node getSender() {
        return source;
    }

    public Node getDestination() {
        return destination;
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

    public String getAddress() {
        return addr;
    }

    public int getPort() {
        return port;
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

    public Node getNode() {
        return n;
    }

    public long getRTT() {
        return rtt;
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