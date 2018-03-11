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
        // Initialize buffers, sockets and packets
        byte[] inFromServer  = null;
        byte[] outToServer = null;
        DatagramSocket clientSocket = null;
        DatagramPacket sendPacket = null;
        int port = 13001;
        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(args[0]);
            try {
                port = Integer.parseInt(args[1]);
            } catch(NumberFormatException e) {
                System.out.println("Invalid port number: " + e);
                System.exit(0);
            } 
            clientSocket = new DatagramSocket();
            outToServer = new byte[1024];
            inFromServer = new byte[1024];
        } catch (IOException e) {
            System.out.println("An I/O error has occured: " + e);
            System.exit(0);
        }
        String message = args[2];
        Stack<String> operationStack = new Stack<String>();
        System.out.println("Sending request to server to calculate: " + message);
        // Count variable to keep track of retry attempts
        int count = 0;
        // Check if everything has been initialized
        if (clientSocket != null && outToServer != null && inFromServer != null) {
            try {
                String packet = "";
                String answer = "";
                ArrayList<String> input = new ArrayList<String>();
                Collections.addAll(input, message.trim().split(" "));
                input.removeAll(Arrays.asList(null, ""));
                // No more than 25 operators or numbers in request
                if (input.size() >= 25) {
                    System.out.println("Too many operators and numbers. Please limit your query to 25 operators/numbers.");
                    clientSocket.close();
                    System.exit(0);
                }              
                for (int i = 0; i < input.size(); i++) {
                    // If there is an operator, pop the two pervious values and send to server with the operator
                    if (isOperator(input.get(i))) {
                        String op2 = "";
                        String op1 = "";
                        if (operationStack.size() > 1) {
                            op2 = operationStack.pop();
                            op1 = operationStack.pop();
                        }
                        packet = op1 + " " + op2 + " " + input.get(i);
                        try {
                            // Send the packet and set a timeout
                            outToServer = packet.getBytes();
                            sendPacket = new DatagramPacket(outToServer, outToServer.length, ipaddr, port);
                            clientSocket.send(sendPacket);
                            clientSocket.setSoTimeout(2000);
                            String recMessage;
                            DatagramPacket receivePacket;
                            // Receive the response
                            receivePacket = new DatagramPacket(inFromServer, inFromServer.length);
                            clientSocket.receive(receivePacket);
                            recMessage = new String(inFromServer, 0, receivePacket.getLength());
                            double responseDouble = 0.0;
                            try {
                                responseDouble = Double.parseDouble(recMessage);
                            } catch(NumberFormatException e) {
                                System.out.println("Invalid operation: " + e);
                            }
                            if (Double.isNaN(responseDouble)) {
                                System.exit(0);
                            }  
                            // If we are at the last element, save the response and break
                            if (i == input.size() - 1) {
                                answer = recMessage;
                                break;
                            }
                            // Push the intermediate calculation onto the operation stack for future calculations
                            operationStack.push(recMessage);
                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout exceeded. Retrying...");
                            count++;
                            // If we have attempted three times, print an error message and exit
                            if (count == 3) {
                                System.out.println("Number of attempts exceeded.");
                                clientSocket.close();
                                System.exit(0);
                            }
                            clientSocket.send(sendPacket);
                            clientSocket.setSoTimeout(2000);
                        }
                    } else {
                        try {
                            Integer.parseInt(input.get(i));
                            // Push the intermediate calculation onto the operation stack for future calculations
                            operationStack.push(input.get(i));
                        } catch(NumberFormatException e) {
                            System.out.println("Invalid input: " + e);
                            System.exit(0);
                        }
                    }
                }
                // Send terminating packet
                packet = "!";
                outToServer = packet.getBytes();
                sendPacket = new DatagramPacket(outToServer, outToServer.length, ipaddr, port);
                clientSocket.send(sendPacket);
                clientSocket.setSoTimeout(2000);
                String recMessage;
                DatagramPacket receivePacket;
                count = 0;
                try {
                    receivePacket = new DatagramPacket(inFromServer, inFromServer.length);
                    clientSocket.receive(receivePacket);
                    recMessage = new String(inFromServer, 0, receivePacket.getLength());
                    // If we receive an acknowledgement, print the answer and exit
                    if (recMessage.equals("!")) {
                        System.out.println("Server response: " + answer);
                        System.exit(0);
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout exceeded. Retrying...");
                    count++;
                    // If we have attempted three times, print an error message and exit
                    if (count == 3) {
                        System.out.println("Number of attempts exceeded. Please check your connection.");
                        clientSocket.close();
                        System.exit(0);
                    }
                    clientSocket.send(sendPacket);
                    clientSocket.setSoTimeout(2000);
                }
            } catch (IOException e) {
                System.err.println("An I/O error has occured: " + e);
                System.exit(0);
            }
        }
    }

    public static boolean isOperator(String s) {
        char c = s.charAt(0);
        return c == '+' || c == '-' || c == '/' || c == '*';
    }
}