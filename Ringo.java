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
    public List<AddrPort> neighbors = new ArrayList<AddrPort>();
    public abstract void sendRTT(int p, InetAddress h, double[] vector);

    class AddrPort {
        InetAddress addr;
        int p;
        public AddrPort(InetAddress addr, int p) {
            this.addr = addr;
            this.p = p;
        }
    }
}