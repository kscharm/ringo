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
    public static Set<AddrPort> neighbors = new HashSet<AddrPort>();
    public abstract void sendRTT(int p, InetAddress h, double[] vector);
    public abstract void peerDiscovery();

    class AddrPort {
        InetAddress addr;
        int p;
        public AddrPort(InetAddress addr, int p) {
            this.addr = addr;
            this.p = p;
        }

        public String toString() {
            return "(" + addr + ", " + p + ")";
        }
    }
}