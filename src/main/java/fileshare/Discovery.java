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

class DiscoverySender implements Runnable {
    AtomicBoolean quit;
    String name, ip;
    Thread t;

    private Logger logger = LoggerFactory.getLogger("Sender");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();

    DiscoverySender(String name, String ip, AtomicBoolean quit) {
        this.name = name;
        this.ip = ip;
        this.quit = quit;
        t = new Thread(this, "DiscoverySender");
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

class DiscoveryReceiver implements Runnable {

    ArrayList<String> names, ips;
    AtomicBoolean quit;
    String ip;
    Thread t;

    private Logger logger = LoggerFactory.getLogger("Receiver");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();

    DiscoveryReceiver( ArrayList<String> names, ArrayList<String> ips, String ip, AtomicBoolean quit) {
        this.names = names;
        this.ips = ips;
        this.ip = ip;
        this.quit = quit;
        t = new Thread(this, "DiscoveryReceiver");
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
            sock.setSoTimeout(500);
            
            while (!quit.get()) {
                sock.receive(pack);
                message = new String(pack.getData(),
                        0, pack.getLength(), StandardCharsets.UTF_16);

                if (message.split("<SEP>")[0].equals("connection") &&
                        !ips.contains(message.split("<SEP>")[2])) {
                    logger.debug("Got connection.");
                    names.add(message.split("<SEP>")[1]);
                    ips.add(message.split("<SEP>")[2]);
                }

                /* ToDo: Delete the following
                else if (message.split("<SEP>")[0].equals("receive")) {
                    logger.debug("Entering receive function.");
                    Functions fn = new Functions();
                    fn.receive();
                }*/
            }
            sock.close();
        }
        catch (IOException e) {
            logger.error("Error somewhere in Receiver: " + e);
        }
    }
}