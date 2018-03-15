import java.util.*;
import java.io.*;
import java.net.*;

public abstract class Ringo {
    public int numRingos;
    public int port;
    public InetAddress pocHost;
    public int pocPort;
    public boolean isAlive;
    public double[] localRTT;
    public static Set<AddrPort> active = new HashSet<AddrPort>();
}