package fileshare;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class OwnIP {

    String getIP() {
        String ip = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> ifaces =
                    NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface nwif = ifaces.nextElement();
                Enumeration<InetAddress> addresses =
                        nwif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr.isSiteLocalAddress()) {
                        ip = addr.getHostAddress();
                    }
                }
            }
        }
        catch (SocketException e) {
            System.out.println("Error getting ip of this device.");
        }
        return ip;
    }
}

/*
class DiscoveryFunctions {

    OwnIP ipa = new OwnIP();

    protected void receive(String name) {

        AtomicBoolean done = new AtomicBoolean(false);
        String ownip = ipa.getIP();
        Sender r1 = new Sender(name, ownip, done);

        try {
            String msg;
            byte[] buff = new byte[1024];
            DatagramSocket sock = new DatagramSocket(5001);
            DatagramPacket pack = new DatagramPacket(buff, buff.length);
            System.out.println("Opened up a sock at 5001");

            while (true) {
                sock.receive(pack);
                done.set(true);
                msg = new String(pack.getData(), 0, pack.getLength(), StandardCharsets.UTF_16);
                if (msg.split("<SEP>")[0].equals("connect")) {
                    System.out.println("\n" + msg.split("<SEP>")[1] + " wants to send files");
                    sock.close();
                    break;
                }
            }
            try {
                r1.t.join();
            } catch (InterruptedException e) {
                System.out.println("Error joing of Receiver thread:\n");
                e.printStackTrace();
            }
        }
        catch (IOException io) {
            System.out.println("Error occured while opening:\n" + io);
        }
    }

    protected String send(String name, Scanner scan) {

        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ips = new ArrayList<>();

        try {
            int p =0;
            boolean rep = true;
            String ownip = ipa.getIP();

            while (rep) {
                rep = false;
                Receiver s1 = new Receiver(names, ips);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    System.out.println("Error while waiting in send func.");
                    e.printStackTrace();
                }
                try {
                    s1.t.join();
                } catch (InterruptedException e) {
                    System.out.println("Error while joining of send thread:\n");
                    e.printStackTrace();
                }

                if (names.isEmpty()) {rep = true;}
                else {
                    System.out.println("\nConnect with one of the following:");
                    System.out.println("0: Retry");
                    for (int j = 0; j < names.size(); j++) {
                        System.out.println((j + 1) + ": " + names.get(j) + " at " + ips.get(j));
                    }
                    System.out.print("\nYour choice: ");
                    p = scan.nextInt();
                    scan.nextLine();
                    if (p == 0) {
                        names.clear();
                        ips.clear();
                        rep = true;
                    }
                }
            }

            byte[] buff;
            String msg, ip = ips.get(p - 1);
            InetAddress addr = InetAddress.getByName(ip);
            DatagramSocket sock = new DatagramSocket();
            DatagramPacket pack;

            msg = "connect<SEP>" + name + "<SEP>" + ownip;
            buff = msg.getBytes(StandardCharsets.UTF_16);
            pack = new DatagramPacket(buff, buff.length, addr, 5001);
            sock.send(pack);

            sock.close();
            //scan.close();
            return ip;
        }
        catch (IOException e) {
            System.out.println("Exception in send func:\n" + e);
        }
        return null;
    }
}
*/

class Sender implements Runnable {
    AtomicBoolean quit;
    String name, ip;
    Thread t;

    private Logger logger = LoggerFactory.getLogger("Sender");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();


    Sender(String name, String ip, AtomicBoolean quit) {
        this.name = name;
        this.ip = ip;
        this.quit = quit;
        t = new Thread(this, "Sender");
        t.start();
    }

    @Override
    public void run() {
        try {
            String msg;
            byte[] buff;
            DatagramSocket sock = new DatagramSocket(5003);
            InetAddress group = InetAddress.getByName("229.89.60.90");
            DatagramPacket pack;

            MDC.setContextMap(contextMap);
            msg = "connection<SEP>" + name + "<SEP>" + ip;
            buff = msg.getBytes(StandardCharsets.UTF_16);
            pack = new DatagramPacket(buff, buff.length, group, 5005);
            logger.info("Pack to 229.89.60.90 at 5005");
            while (!quit.get()) {
                for (int j = 0; j < 2; j++) {
                    logger.info("Sending pack...");
                    sock.send(pack);
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e) {
                        logger.error("Sender thread interrupted");
                    }
                }
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    logger.error("Sender thread interrupted");
                }
            }
            sock.close();
        }
        catch (IOException e) {
            logger.error("Error somewhere in Sender:" + e);
        }
    }
}

/*
class Receiver implements Runnable{
    ArrayList<String> names;
    ArrayList<String> ips;
    Thread t;

    Receiver(ArrayList<String> names, ArrayList<String> ips) {
        this.names = names;
        this.ips = ips;
        t = new Thread(this, "Receiver");
        t.start();
    }

    public void run() {
        try {
            String msg;
            byte[] buff = new byte[1024];
            MulticastSocket sock  = new MulticastSocket(5005);
            InetAddress group = InetAddress.getByName("229.89.60.90");
            DatagramPacket pack = new DatagramPacket(buff, buff.length);

            sock.joinGroup(group);
            sock.setSoTimeout(2000);

            for (long stop = System.nanoTime() +
                    TimeUnit.SECONDS.toNanos(3);
                 stop > System.nanoTime();) {

                while (true) {
                    sock.receive(pack);

                    msg = new String(pack.getData(),
                            0, pack.getLength(), StandardCharsets.UTF_16);
                    if (msg.split("<SEP>")[0].equals("receiver") &&
                            !names.contains(msg.split("<SEP>")[1])) {
                        names.add(msg.split("<SEP>")[1]);
                        ips.add(msg.split("<SEP>")[2]);
                    }
                }
            }
            sock.close();
        }
        catch (IOException e) {
            System.out.println("Error somewhere in Receiver:\n" + e);
        }
    }
}
*/

class Receiver implements Runnable {

    ArrayList<String> names, ips;
    AtomicBoolean quit;
    String ip;
    Thread t;

    private Logger logger = LoggerFactory.getLogger("Receiver");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();

    Receiver( ArrayList<String> names, ArrayList<String> ips, String ip, AtomicBoolean quit) {
        this.names = names;
        this.ips = ips;
        this.ip = ip;
        this.quit = quit;
        t = new Thread(this, "Discovery");
        t.start();
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[1024];
            String message;
            MDC.setContextMap(contextMap);
            MulticastSocket sock  = new MulticastSocket(5005);
            InetAddress group = InetAddress.getByName("229.89.60.90");
            DatagramPacket pack = new DatagramPacket(buffer, buffer.length);

            sock.joinGroup(group);
            sock.setSoTimeout(2000);
            
            while (!quit.get()) {
                sock.receive(pack);
                message = new String(pack.getData(),
                        0, pack.getLength(), StandardCharsets.UTF_16);

                if (message.split("<SEP>")[0].equals("connection") &&
                        !ips.contains(message.split("<SEP>")[2])) {
                    names.add(message.split("<SEP>")[1]);
                    ips.add(message.split("<SEP>")[2]);
                }
                
                else if (message.split("<SEP>")[0].equals("receive")) {
                    Functions fn = new Functions();
                    fn.receive();
                }
            }
            sock.close();
        }
        catch (IOException e) {
            logger.error("Error somewhere in Sender: " + e);
        }
    }
}