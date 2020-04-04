package fileshare;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class OwnIP {

    String ipaddr() {
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

class DiscoveryFunctions {

    OwnIP ipa = new OwnIP();

    protected void receive(String name) {

        AtomicBoolean done = new AtomicBoolean(false);
        String ownip = ipa.ipaddr();
        Reciever r1 = new Reciever(name, ownip, done);

        try {
            String msg;
            byte[] buff = new byte[1024];
            DatagramSocket sock = new DatagramSocket(5001);
            DatagramPacket pack = new DatagramPacket(buff, buff.length);
            System.out.println("Opened up a sock and pack at 5001");

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
                System.out.println("Error joing of Reciever thread:\n");
                e.printStackTrace();
            }
        }
        catch (IOException io) {
            System.out.println("Error occured while opening:\n" + io);
        }
    }

    protected String send(String name, Scanner scan) {

        ArrayList<String> recvs = new ArrayList<>();
        ArrayList<String> ips = new ArrayList<>();

        try {
            int p =0;
            boolean rep = true;
            //Scanner scan = new Scanner(System.in);
            String ownip = ipa.ipaddr();

            while (rep) {
                rep = false;
                Sender s1 = new Sender(recvs, ips);
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

                if (recvs.isEmpty()) {rep = true;}
                else {
                    System.out.println("\nConnect with one of the following:");
                    System.out.println("0: Retry");
                    for (int j = 0; j < recvs.size(); j++) {
                        System.out.println((j + 1) + ": " + recvs.get(j) + " at " + ips.get(j));
                    }
                    System.out.print("\nYour choice: ");
                    p = scan.nextInt();
                    scan.nextLine();
                    if (p == 0) {
                        recvs.clear();
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


class Reciever implements Runnable {
    AtomicBoolean done;
    String name, ip;
    Thread t;

    Reciever(String name, String ip, AtomicBoolean done) {
        this.name = name;
        this.ip = ip;
        this.done = done;
        t = new Thread(this, "Reciever");
        t.start();
    }

    public void run() {
        try {
            String msg;
            byte[] buff;
            DatagramSocket sock = new DatagramSocket(5003);
            InetAddress group = InetAddress.getByName("229.89.60.90");
            DatagramPacket pack;

            msg = "reciever<SEP>" + name + "<SEP>" + ip;
            buff = msg.getBytes(StandardCharsets.UTF_16);
            pack = new DatagramPacket(buff, buff.length, group, 5005);
            System.out.println("Pack to 229.89.60.90 at 5005");
            while (!done.get()) {
                for (int j = 0; j < 2; j++) {
                    System.out.println("Sending pack...");
                    sock.send(pack);
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e) {
                        System.out.println("Reciever thread interrupted");
                    }
                }
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    System.out.println("Reciever thread interrupted");
                }
            }
            sock.close();
        }
        catch (IOException e) {
            System.out.println("Error somewhere in Reciever:\n" + e);
        }
    }
}


class Sender implements Runnable{
    ArrayList<String> recvs;
    ArrayList<String> ips;
    Thread t;

    Sender(ArrayList<String> recvs, ArrayList<String> ips) {
        this.recvs = recvs;
        this.ips = ips;
        t = new Thread(this, "Sender");
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
                    try {
                        sock.receive(pack);
                    }
                    catch (SocketTimeoutException to) {
                        System.out.println("Timed out...");
                    }
                    msg = new String(pack.getData(),
                            0, pack.getLength(), StandardCharsets.UTF_16);
                    if (msg.split("<SEP>")[0].equals("reciever") &&
                            !recvs.contains(msg.split("<SEP>")[1])) {
                        recvs.add(msg.split("<SEP>")[1]);
                        ips.add(msg.split("<SEP>")[2]);
                    }
                }
            }
            sock.close();
        }
        catch (IOException e) {
            System.out.println("Error somewhere in Sender:\n" + e);
        }
    }
}