import java.util.*;
import java.io.*;
import java.net.*;

public abstract class Ringo {
    public static int numRingos;
    public int port;
    public InetAddress pocHost;
    public int pocPort;
    public boolean isAlive;
    public long[] localRTT;
    public static List<Node> active = new ArrayList<>();
}