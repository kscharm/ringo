Names: Kenneth Scharm and Rudy Lowenstein
Email: kscharm3@gatech.edu and rlowenstein3@gatech.edu
CS3251 - Computer Networks I
March 16, 2018

Project Ringo: Milestone 2

1) Compiling and Running (IMPORTANT)

    i) Compiling Source Files
        To compile all source files, run the command:

            javac *.java

        in the directory where all the source files are stored.

ii) Running the Application

    To launch any given Ringo, use the command:

            java RingoApp <flag> <local-port> <PoC-name> <PoC-port> <N>

    <flag>: S if this Ringo peer is the Sender, R if it is the Receiver, and F if it is a Forwarder
    <local-port>: the UDP port number that this Ringo should use (at least for peer discovery)
    <PoC-name>: the host-name of the PoC for this Ringo. Set to 0 if this Ringo does not have a PoC.
    <PoC-port>: the UDP port number of the PoC for this Ringo. Set to 0 if this Ringo does not have a PoC.
    <N>: the total number of Ringos (when they are all active).

    Example Ringo launch where PoC is local machine:

        java RingoApp S 13001 localhost 13002 5

    Example Ringo launch where PoC is another machine:

        java RingoApp S 13001 networklab3.cc.gatech.edu 13002 5
