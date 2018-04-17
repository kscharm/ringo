import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive token-ring networking application.
 * Authors: Kenneth Scharm and Rudy Lowenstein
 */
public class RingoApp {
    // Data Structures
    private static Map<Node, NodeTime[]> globalRTT = Collections.synchronizedMap(new HashMap<Node, NodeTime[]>());
    private static List<Node> opt = new LinkedList<>();
    private static List<Node> currentPath = new LinkedList<>();
    private static List<Node> fPath = new LinkedList<>();
    private static List<Node> rPath = new LinkedList<>();
    private static DatagramSocket socket = null;
    private static InetAddress pocHost = null;
    private static Node id = null;
    private static Set<Node> alive = new HashSet<>();

    // Integer Variables
    private static int port = -1;
    private static int pocPort = -1;
    private static int numRingos = -1;
    private static int rttLength = 0;
    private static int sequenceNum = 0;

    // Constants
    private static final int MAX_PACKET_SIZE = 65500;
    private static final int NUM_ACKS = 100;
    private static final int KEEP_ALIVE_TIME = 15000;

    // String Declarations
    private static String ipaddr = null;
    private static String flag = null;
    private static String sourceFilePath = null;
    private static String fileName = null;

    // File Declarations
    private static File outFile = null;
    private static FileOutputStream fileOut = null;

    // Boolean Flags
    static volatile boolean discovery = true;
    static volatile boolean rttCalc = false;
    static volatile boolean rttTransfer = false;
    static volatile boolean calcRing = false;
    static volatile boolean pathTransfer = false;
    static volatile AtomicBoolean ackReceived = new AtomicBoolean(false);

    // Define Executor Services
    private static ExecutorService receiveThread = Executors.newSingleThreadExecutor();
    private static ExecutorService sendThread = Executors.newCachedThreadPool();
    private static ExecutorService keepAliveThread = Executors.newSingleThreadExecutor();
    private static ExecutorService sendFileThread = Executors.newSingleThreadExecutor();

    // Define global ringo object
    Ringo ringo = null;

    public static void main(String[] args) {
        RingoApp app = new RingoApp();
        app.start(args);
    }

    /**
     * Starts the RingoApp Application
     * @param args parameters entered on the command line
     */
    public void start(String[] args) {
        // Parse arguments
        if (args.length < 5) {
            System.out.println("Insufficient arguments");
            System.exit(0);
        }
        System.out.println("Welcome to Ringo!");;
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
        id = new Node(ipaddr, port, flag);
        ringo.active.add(id);

        // Start receive thread
        receiveThread.submit(new ReceiveThread());

        if (!pocHost.equals(0) && pocPort != 0) {
            Packet first = new Packet("1:" + id.toString(), id, new Node(pocHost.getHostAddress(), pocPort));
            sendPacket(first);
        }


        Scanner scan = new Scanner(System.in);
        System.out.println("#### Ringo commands ####");
        System.out.println("1) send <filename>");
        System.out.println("2) show-matrix");
        System.out.println("3) show-ring");
        System.out.println("4) offline <seconds>");
        System.out.println("5) disconnect");

        // Start interactive interface
        while(true) {
            System.out.print("\n");
            System.out.print("Ringo command: ");
            String input = scan.nextLine();
            if (input.indexOf("send") != -1) {
                // Send file
                if (!flag.equals("S")) {
                    System.out.println("Can only send files from a Sender Ringo.");
                } else {
                    fileName = input.substring(input.indexOf("d") + 2, input.length());
                    sourceFilePath = System.getProperty("user.dir") + "/" + fileName;
                    if (!new File(sourceFilePath).exists())
                    {
                        System.out.println("File " + sourceFilePath + " does not exist!");
                    } else {
                        System.out.println("Sending file with path: " + sourceFilePath);
                        sendFileThread.submit(new SendFileThread());
                    } 
                }
            } else if (input.equals("show-matrix")) {
                if (ringo.active.size() == numRingos) {
                    System.out.println("#### Global RTT Matrix ####");
                    printMap(globalRTT);
                } else {
                    System.out.println("Not all ringos have been discovered.");
                }
            } else if (input.equals("show-ring")) {
                if (opt.size() == numRingos) {
                    // Show optimal ring formation
                    System.out.println("#### Optimal ring ####");
                    for (int i = 0; i < opt.size(); i++) {
                        if (i == opt.size() - 1) {
                            System.out.print(opt.toArray()[i].toString() + "\n");
                        } else {
                            System.out.print(opt.toArray()[i].toString() + " <--> ");
                        }
                    }
                } else {
                    System.out.println("Not all ringos have been discovered.");
                }
            } else if (input.indexOf("offline") != -1) {
                try {
                    String s = input.substring(input.indexOf(" ") + 1, input.length());
                    int seconds = Integer.parseInt(s);
                    System.out.println("Ringo going offline for " + seconds + " seconds");
                    try {
                        Thread.sleep(seconds * 1000);
                    } catch (InterruptedException e) {
                        System.out.println("Cant Sleep: " + e.getMessage());
                    }
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
     * Calculates the Round-Trip Time (RTT) by pinging a given IP address and port
     * and returning the amount of time it takes.
     * @param ip IP address to be pinged
     * @param port Port number of ip
     * @return RTT from current ringo to given IP/Port combination
     */
    private static long calcRTT(String ip, int port) {
        try {
            InetAddress ipaddr = InetAddress.getByName(ip);
            long start = System.currentTimeMillis();
            long finish = 0;
            // Ping the IP and see if we get a response
            if (ipaddr.isReachable(10000)) {
                finish = System.currentTimeMillis();
                return finish - start;
            } else {
                System.out.println(ipaddr + " is not reachable");
                long total = finish - start;
                if (total > 10000) {
                    total = 10000;
                }
                return total;
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
    public synchronized void calcRTTvectors() {
        for (int i = 0; i < ringo.active.size(); i++) {
            Node n = ringo.active.get(i);
            if (n.getAddress().equals(ipaddr) && n.getPort() == port) {
                ringo.localRTT[i] = new NodeTime(n, 0);
            } else {
                ringo.localRTT[i] = new NodeTime(n, calcRTT(n.getAddress(), n.getPort()));
            }
            rttLength++;
        }
        globalRTT.put(id, ringo.localRTT);
    }

    /**
     * Calculates the optimal ring formation by performing a greedy search algorithm
     * on the global RTT matrix. Starts with the first key in the global RTT matrix and
     * searches it's local RTT vector for the smallest RTT. It adds this node to the ring
     * and then looks at that node's local RTT vector to find the smallest. The algorithm
     * terminates when the ring has been calculated.
     */
    private synchronized void calculateOptimalRing(Node key) {
        if (opt.size() == numRingos) {
            for (int i = 0; i < opt.size(); i++) {
                System.out.print(opt.get(i).toString() + "|");
            }
            System.out.print("\n");
            return;
        }

        NodeTime[] nt = globalRTT.get(key);
        Node curr = nt[0].getNode();

        for (int i = 1; i < nt.length; i++) {
            long min = Long.MAX_VALUE;
            long time = nt[i].getRTT();

            if (time < min && !opt.contains(nt[i].getNode())) {
                min = time;
                curr = nt[i].getNode();
            }
        }
        opt.add(curr);
        calculateOptimalRing(curr);
    }

    /**
     * Calculates the path through the optimal ring to use
     */
    public void calculatePath() {
        List<Node> forwardPath = new LinkedList<>();
        List<Node> reversePath = new LinkedList<>();
        int sendIndex = -1;

        // Find the index in the optimal ring of the Sender Ringo
        for (int i = 0; i < opt.size(); i++) {
            if (opt.get(i).getFlag().equals("S")) {
                sendIndex = i;
            }
        }

        forwardPath.add(opt.get(sendIndex));
        reversePath.add(opt.get(sendIndex));

        // Sets the start index based on position of Sender Ringo in optimal ring
        int start = -1;
        if (sendIndex == opt.size() -1) {
            start = 0;
        } else {
            start = sendIndex + 1;
        }

        // Calculating the cost of the forward direction in the optimal ring
        int forwardCost = 0;
        Node prev = opt.get(sendIndex);
        for (int i = 0; i < opt.size() - 1; i++) {
            Node curr = opt.get(start);
            forwardCost += getCost(prev ,curr);
            prev = curr;
            if (start == opt.size() - 1) {
                start = 0;
            } else {
                start++;
            }

            forwardPath.add(curr);
            if (curr.getFlag().equals("R")) {
                break;
            }
        }


        if (sendIndex == 0) {
            start = opt.size() - 1;
        } else {
            start = sendIndex - 1;
        }
        // Calculating the cost of the reverse direction in the optimal ring
        int reverseCost = 0;
        prev = opt.get(sendIndex);
        for (int i = 0; i < opt.size() - 1; i++) {
            Node curr = opt.get(start);
            reverseCost += getCost(prev ,curr);
            prev = curr;
            if (start == 0) {
                start = opt.size() - 1;
            } else {
                start--;
            }

            reversePath.add(curr);
            if (curr.getFlag().equals("R")) {
                break;
            }
        }

        Node next = null;
        boolean isForward = true;
        if (forwardCost < reverseCost) {
            if (sendIndex != opt.size() - 1) {
                next = opt.get(sendIndex + 1);
            } else {
                next = opt.get(0);
            }
        } else {
            isForward = false;
            if (sendIndex != 0) {
                next = opt.get(sendIndex - 1);
            } else {
                next = opt.get(opt.size() - 1);
            }
        }

        String payload = "3:";
        if (isForward){
            currentPath = forwardPath;
            for (int i = 0; i < forwardPath.size(); i++) {
                if (i != forwardPath.size() - 1) {
                    payload += forwardPath.get(i).toString() + "x";
                } else {
                    payload += forwardPath.get(i).toString();
                }
            }
        } else {
            currentPath = reversePath;
            for (int i = 0; i < reversePath.size(); i++) {
                if (i != reversePath.size() - 1) {
                    payload += reversePath.get(i).toString() + "x";
                } else {
                    payload += reversePath.get(i).toString();
                }
            }
        }
        
        Packet p = new Packet(payload, id , next);
        sendPacket(p);

        fPath = forwardPath;
        rPath = reversePath;
    }

    /**
     * Checks the ackReceived variable to see if we can send again
     */
    private void checkAck() {
        synchronized (ackReceived) {
            try {
                while (!ackReceived.get()) {
                    ackReceived.wait();
                }
            } catch (InterruptedException e) {
                System.out.println("Interrupted thread: " + e.getMessage());
            }
            ackReceived.set(false);
        }
    }

    /**
     * Forwards a packet along the ring
     * @param p the packet being forwarded
     */
    private void forward(Packet p) {
        sendPacket(p);
    }

    /**
     * Checks to see if a Ringo is a Forwarder and if so, forwards a packet
     * @param message the message being forwarded
     */
    private void forwarderCheck(String message) {
        if (flag.equals("F")) {
            int forwarderIndex = -1;
            for (int i = 0; i < currentPath.size(); i++){
                if (currentPath.get(i).equals(id)) {
                    forwarderIndex = i;
                    break;
                }
            }
            Packet nextPacket = new Packet(message, id, currentPath.get(forwarderIndex + 1));
            forward(nextPacket);
        }
    }

    /**
     * Retrieves the cost between two nodes in the ring
     * @param key the starting point on the ring
     * @param dest the destination point on the ring
     * @return the RTT between key and dest
     */
    private synchronized long getCost(Node key, Node dest) {
        NodeTime[] costs = globalRTT.get(key);
        for (int i = 0; i < costs.length; i++) {
            Node curr = costs[i].getNode();
            long time = costs[i].getRTT();
            if (dest.equals(curr)) {
                return time;
            }
        }
        return -1;
    }


    /**
     * Returns the size of the global RTT matrix.
     * @return the size of the global RTT matrix.
     */
    private synchronized int getSize() {
        return globalRTT.size();
    }

    /**
     * Returns true is the cost from me to node 1 is greater than the cost
     * from me to node 2.
     */
    public Boolean isGreaterCost(Node n1, Node n2) {
        if (getCost(id, n1) > getCost(id, n2)) {
            return true;
        }
        return false;
    }

    /**
     * Prints the global RTT matrix.
     * @param mp the map data structure that holds the RTT matrix
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
     *
     * @param buffer data received by the socket
     * @param dp Packet received by the socket
     * @throws IOException
     */
    private synchronized void receiveMessage(byte[] buffer, DatagramPacket dp) {
        String message = new String(buffer, 0, dp.getLength());
        String oldMessage = message;
        //System.out.println(message);
        message = message.replaceAll("[()]", ""); // Get rid of parenthesis
        message = message.replaceAll("\\s+", ""); // Get rid of white space
        String recAddr = null;
        int recPort = 0;
        int headerIndex = message.indexOf(":");
        String header = null;
        if (headerIndex != -1 && headerIndex < 10) {
            header = message.substring(0, headerIndex);
            message = message.substring(headerIndex + 1);
        }
        DatagramPacket p = null;
        String f = null;
        if (header != null) {
            if (header.equals("Alive")) {
                String[] info = message.split(",");
                try {
                    recAddr = info[0];
                    recPort = Integer.parseInt(info[1]);
                    f = info[2];
                } catch (NumberFormatException e) {
                    System.out.println("Invalid response: " + e);
                }
                Node new_Alive = new Node(recAddr, recPort, f);
                alive.add(new_Alive);
            }

            if (header.equals("Down")) {
                String[] info = message.split(",");
                try {
                    recAddr = info[0];
                    recPort = Integer.parseInt(info[1]);
                    f = info[2];
                } catch (NumberFormatException e) {
                    System.out.println("Invalid response: " + e);
                }
                Node downed = new Node(recAddr, recPort, f);
                if (currentPath.contains(downed)) {
                    if (currentPath.equals(fPath)) {
                        currentPath = rPath;
                    } else if (currentPath.equals(rPath)) {
                        currentPath = fPath;
                    } else {}
                }
                System.out.println("Node " + downed.toString() + " is down.");
                System.out.println("Switching optimal path to use other path.");
            }

            // Header of 1 means peer discovery
            if (header.equals("1")) {
                String[] info = message.split(",");
                try {
                    recAddr = info[0];
                    recPort = Integer.parseInt(info[1]);
                    f = info[2];
                } catch (NumberFormatException e) {
                    System.out.println("Invalid response: " + e);
                }
                Node tba = new Node(recAddr, recPort, f);
                //sendAck(tba);
                if (recAddr != null && recPort != 0 && !ringo.active.contains(tba)) {
                    ringo.active.add(tba);
                    // Send each node in the active list to all neighbors
                    for (int i = 0; i < ringo.active.size(); i++) {
                        Node curr = ringo.active.get(i);
                        for (int j = 0; j < ringo.active.size(); j++) {
                            if (i != j) {
                                Node dest = ringo.active.get(j);
                                String payload = header + ":" + curr.toString();
                                Packet packet = new Packet(payload, curr, dest);
                                sendPacket(packet);
                            }
                        }
                    }
                }
            }
            // Header of 2 means RTT vector exchange
            else if (header.equals("2")) {
                String[] info = message.split("x");
                NodeTime[] ntArray = new NodeTime[numRingos];
                Node parent = null;
                String f1 = null;
                for (int i = 0; i < info.length; i++) {
                    String[] entry = info[i].split(",");
                    try {
                        recAddr = entry[0];
                        recPort = Integer.parseInt(entry[1]);
                        f1 = entry[2];
                        Node n = new Node(recAddr, recPort, f1);
                        if (i == 0) {
                            if (globalRTT.containsKey(n)) {
                                break;
                            }
                            parent = n;
                        } else {
                            int time = Integer.parseInt(entry[3]);
                            NodeTime nt = new NodeTime(n, time);
                            ntArray[i - 1] = nt;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid response: " + e);
                    }
                }
                if (parent != null) {
                    globalRTT.put(parent, ntArray);
                    sendRTT();
                }
            }

            // Header 3 means sending path information to all nodes in the optimal path
            else if (header.equals("3")) {
                String[] info = message.split("x");
                List<Node> temp = new LinkedList<>();
                try {
                    for (int i = 0; i < info.length; i++) {
                        String[] node = info[i].split(",");
                        String ip = node[0];
                        int port = Integer.parseInt(node[1]);
                        String f2 = node[2];

                        Node n = new Node(ip, port, f2);
                        temp.add(n);
                    }
                } catch (NumberFormatException e) {
                    System.out.println(e.getMessage());
                }

                currentPath = temp;
                forwarderCheck(oldMessage);
                if (flag.equals("R") && currentPath != null) {
                    pathTransfer = false;
                }
            }
            else if (header.equals("4")) {
                forwarderCheck(oldMessage);
                if (flag.equals("R")) {
                    sendAck(currentPath.get(currentPath.size() - 2));
                    // Construct destination file path (Note: change the name after the "/" to test sample output file)
                    String destFilePath = System.getProperty("user.dir") + "/" + message;
                    outFile = new File(destFilePath);
                    try {
                        fileOut = new FileOutputStream(outFile);
                    } catch(IOException e) {
                        System.out.println("Problem creating file output stream: " + e.getMessage());
                    }
                }
            }
            // ACK header means we got an ACK from the receiver
            else if (header.equals("ACK")) {
                int currentNumber = -1;
                oldMessage = oldMessage.substring(headerIndex + 1);
                System.out.println("ACK: " + oldMessage + " received");
                if (flag.equals("F")) {
                    int forwarderIndex = -1;
                    for (int i = 0; i < currentPath.size(); i++){
                        if (currentPath.get(i).getFlag().equals("F")) {
                            forwarderIndex = i;
                            break;
                        }
                    }
                    Packet nextPacket = new Packet(oldMessage, id, currentPath.get(forwarderIndex - 1));
                    System.out.println("Forwarding ACK...");
                    forward(nextPacket);
                }
                if (flag.equals("S")) {
                    try {
                        currentNumber = Integer.parseInt(oldMessage);
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing sequence number: " + e.getMessage());
                    }
                    synchronized (ackReceived) {
                        if (currentNumber == sequenceNum) {
                            sequenceNum++;
                            if (sequenceNum == NUM_ACKS) {
                                sequenceNum = 0;
                            }
                            ackReceived.set(true);
                            ackReceived.notify();
                        } else {
                            ackReceived.set(false);
                        }
                    }
                }
            }
            setFlags();
        } else {
            if (flag.equals("F")) {
                int forwarderIndex = -1;
                for (int i = 0; i < currentPath.size(); i++){
                    if (currentPath.get(i).getFlag().equals("F")) {
                        forwarderIndex = i;
                        break;
                    }
                }
                Packet nextPacket = new Packet(dp.getData(), id, currentPath.get(forwarderIndex + 1));
                forward(nextPacket);
            }
            if (flag.equals("R")) {
                if (oldMessage.equals("x")) {
                    System.out.println("Closing file output stream");
                    try {
                        fileOut.close();
                    } catch (IOException e) {
                        System.out.println("Problem closing file output stream: " + e.getMessage());
                    }
                } else {
                    try {
                        fileOut.write(dp.getData(), 0, dp.getLength());
                        System.out.println("Packet written to file");
                        fileOut.flush();
                    } catch (IOException e) {
                        System.out.println("Problem writing to file: " + e.getMessage());
                    }
                }
                sendAck(currentPath.get(currentPath.size() - 2));
            }
        }
    }


    /**
     * Sends an ACK packet
     */
    private synchronized void sendAck(Node dest) {
        String ackString = "ACK:" + sequenceNum;
        System.out.println(ackString);
        Packet p = new Packet(ackString, id, dest);
        sendPacket(p);
        sequenceNum++;
        if (sequenceNum == NUM_ACKS) {
            sequenceNum = 0;
        }
    }


    /**
     * Breaks a file into packets and sends them using Stop and Wait
     * @throws IOException
     */
    public void sendFile() throws IOException {
        byte[] sendData = new byte[MAX_PACKET_SIZE];
        File file = new File(sourceFilePath);
        FileInputStream fis = new FileInputStream(file);
        int totLength = 0;
        int count = 0;
        // Calculate total length of file
        while ((count = fis.read(sendData)) != -1)
        {
            totLength += count;
        }
        int noOfPackets = totLength / (MAX_PACKET_SIZE);
        int off = noOfPackets * (MAX_PACKET_SIZE);
        int lastPackLen = totLength - off;
        byte[] lastPack = new byte[lastPackLen - 1];
        fis.close();
        Node next = currentPath.get(1);
        Packet namePack = new Packet("4:" + fileName, id, next);
        sendPacket(namePack);
        checkAck();
        FileInputStream fis1 = new FileInputStream(file);
        while ((count = fis1.read(sendData, 0, MAX_PACKET_SIZE)) != -1 ) {
            if (noOfPackets <= 0) {
                break;
            }
            Packet p = new Packet(sendData, id, next);
            sendPacket(p);
            checkAck();
            noOfPackets--;
        }
        lastPack = Arrays.copyOf(sendData, lastPackLen);
        Packet p = new Packet(lastPack, id, next);
        sendPacket(p);
        checkAck();
        // Send terminating packet
        p = new Packet("x", id, next);
        sendPacket(p);
        checkAck();
        fis1.close();
    }

    /**
     * Submits a packet for the send thread to transmit
     * @param p the packet to be transmitted
     */
    public void sendPacket(Packet p) {
        sendThread.submit(new SendThread(p));
    }


    /**
     * Sends this node's global RTT matrix to all other known Ringos.
     */
    private synchronized void sendRTT() {
        // Send intial RTT vectors
        Iterator it = globalRTT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            Node n = (Node)pair.getKey();
            NodeTime[] nt = (NodeTime[])pair.getValue();
            // Attach parent to packet
            String payload = "2:"+ n.toString() + "x";
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
                Node neighbor = ringo.active.get(j);
                try {
                    InetAddress sendIp = InetAddress.getByName(neighbor.getAddress());
                    int sendPort = neighbor.getPort();
                    String f = neighbor.getFlag();
                    Packet pack = new Packet(payload, id, new Node(sendIp.toString(), sendPort, f));{
                        if (sendPort != port)
                            sendPacket(pack);
                    }
                } catch (UnknownHostException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }


    /**
     * Controls the boolean flags behind the Ringo State Machine
     */
    private synchronized void setFlags() {
        // Check to see if we know everyone
        if (discovery && ringo.active.size() == numRingos) {
            // System.out.println("Discovery complete");
            discovery = false;
            // Start keep alive thread
            keepAliveThread.submit(new KeepAliveThread());
            rttCalc = true;
            calcRTTvectors();
        }
        // Check to see if local RTT vector calculations have completed
        if (rttCalc && rttLength == numRingos) {
            // System.out.println("Local RTT vector calculations complete");
            rttCalc = false;
            rttTransfer = true;
            sendRTT();
        }
        // Check to see if we know all RTTs
        if (rttTransfer && getSize() == numRingos) {
            // System.out.println("RTT exchange complete");
            rttTransfer = false;
            calcRing = true;
            Node first = (Node)globalRTT.keySet().toArray()[0];
            opt.add(first);
            calculateOptimalRing(first);
        }

        if (calcRing && opt.size() == numRingos) {
            // System.out.println("Optimal ring calculation complete");
            calcRing = false;
            calculatePath();
            pathTransfer = true;
        }
    }

    //Inner Classes

    /**
     * Thread for sending Keep-Alive packets to other Ringos
     */
    class KeepAliveThread implements Runnable {
        public void run() {
            keepAlive();
        }
        /**
         * Periodically sends alive packet to all known nodes
         */
        public void keepAlive() {
            Timer timer = new Timer();
            // Once discovery is complete, schedule alive packets to be sent every 15 seconds
            timer.schedule(new SendAlivePackets(), 0, KEEP_ALIVE_TIME);

        }
    }



    /**
     * Class to send Keep Alive Packets to other ringos
     */
    class SendAlivePackets extends TimerTask {
        public void run() {
            // Send the payload to every ringo
            String payload = "Alive:" + id.toString();
            try {
                for (int j = 0; j < ringo.active.size(); j++) {
                    Node neighbor = ringo.active.get(j);
                    InetAddress sendIp = InetAddress.getByName(neighbor.getAddress());
                    String ip = sendIp.toString();
                    if (ip.indexOf("/") != -1) {
                        ip = ip.substring(ip.indexOf("/") + 1, ip.length());
                    }
                    if (!id.equals(neighbor)) {
                        Packet p = new Packet(payload, id, neighbor);
                        sendPacket(p);
                    }
                }
                try{
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }

                for (int i = 1; i < ringo.active.size(); i++) {
                    Node b = ringo.active.get(i);
                    if (!alive.contains(b)) {
                        String payload1 = "Down: " + b.toString();
                        for (int j = 0; j < ringo.active.size(); j++) {
                            Node neighbor = ringo.active.get(j);
                            InetAddress sendIp = InetAddress.getByName(neighbor.getAddress());
                            String ip = sendIp.toString();
                            if (ip.indexOf("/") != -1) {
                                ip = ip.substring(ip.indexOf("/") + 1, ip.length());
                            }
                            if (!id.equals(neighbor)) {
                                Packet p = new Packet(payload1, id, neighbor);
                                sendPacket(p);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("An I/O error has occurred while sending alive packet " + e.getMessage());
            }

            alive.clear();
        }
    }

    /**
     * Thread for sending file data in a stream
     */
    class SendFileThread implements Runnable {
        @Override
        public void run() {
            try {
                sendFile();
            } catch (IOException e) {
                System.out.println("Error sending file: " + e.getMessage());
            }

        }
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
         * Sends a packet with a given payload to a given destination.
         */
        public void send() {
            String payload = p.getPayload();
            Node dest = p.getDestination();
            String destAddress = dest.getAddress();
            if (destAddress.indexOf("/") != -1) {
                destAddress = destAddress.substring(destAddress.indexOf("/") + 1);
            }
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            if (p.getData() != null) {
                buffer = p.getData();
            } else {
                buffer = payload.getBytes();
            }
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(destAddress), dest.getPort());
                socket.send(packet);
            } catch (UnknownHostException e) {
                System.out.println("Unknown Host: " + e.getMessage());
            } catch (IOException f) {
                System.out.println(f.getMessage());
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
                    byte[] inFromRingo  = new byte[MAX_PACKET_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(inFromRingo, inFromRingo.length);
                    socket.receive(receivePacket);
                    receiveMessage(inFromRingo, receivePacket);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    System.out.println("Thread interrupted " + e.getMessage());
                }
            }
        }
    }


}

/**
 * Packet class that represents a packet with a source, destination, and payload.
 * Used to provide reliable file transfer.
 */
class Packet {

    String payload;
    Node source;
    Node destination;
    byte[] data;

    public Packet(String p, Node s, Node d) {
        payload = p;
        source = s;
        destination = d;
    }

    public Packet(byte[] data, Node s, Node d) {
        this.data = data;
        payload = null;
        source = s;
        destination = d;
    }

    public String getPayload() {
        return payload;
    }

    public byte[] getData() {
        return data;
    }

    public Node getSender() {
        return source;
    }

    public Node getDestination() {
        return destination;
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
        Packet a = (Packet) obj;
        if ((!this.source.equals(a.source)) || !this.destination.equals(a.destination) || !this.payload.equals(a.payload)) {
            return false;
        }
        return true;
    }

}

/**
 * Node class that represents a Ringo.
 */
class Node {
    String addr;
    int port;
    String flag;

    public Node(String addr, int port) {
        this.addr = addr;
        this.port = port;
        this.flag = null;
    }

    public Node (String addr, int port, String f) {
        this.addr = addr;
        this.port = port;
        this.flag = f;
    }

    public String getAddress() {
        return addr;
    }

    public int getPort() {
        return port;
    }

    public String getFlag() {
        return flag;
    }

    @Override
    public String toString() {
        String to = null;
        if (flag != (null) && !flag.isEmpty()) {
            to = "(" + addr + "," + port + "," + flag + ")";
        } else {
            to = "(" + addr + "," + port + ")";
        }
        return to;
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