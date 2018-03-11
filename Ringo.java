import java.util.*;
import java.io.*;
import java.net.*;

public abstract class Ringo {
    public int numRingos;
    private int portNum;
    private String ipAddress;
    private boolean isAlive;
    public abstract void sendRTT(Ringo r, double[] vector);
}