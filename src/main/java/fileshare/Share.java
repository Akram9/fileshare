package fileshare;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class Property {

    String getProperty(String property) {
        Properties properties = new Properties();
        ClassLoader cl = this.getClass().getClassLoader();

        try {
            InputStream is = cl.getResourceAsStream("config.properties");
            assert is != null;
            properties.load(is);
            return properties.getProperty(property);
        }
        catch (IOException e) {
            System.out.println("Error reading properties file:\n" + e);
        }
        return "<empty>";
    }
}

class Share {

    private static Logger logger = LoggerFactory.getLogger("Share");

    public static void main (String[] args) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ips = new ArrayList<>();
        AtomicBoolean quit = new AtomicBoolean(false);
        Property props = new Property();
        String c, filepath;
        Functions inet = new Functions();
        Scanner scan = new Scanner(System.in);
        OwnIP ownip = new OwnIP();
        String ip = ownip.getIP();
        String host;
        final String name = props.getProperty("name");
        int p, i = 0;

        MDC.put("IP", ip);
        System.out.println("Device: " + name + ", at: " + ip);

        Receiver receiver = new Receiver(names, ips, ip, quit);
        Sender sender = new Sender(name, ip, quit);

        loop: while (true) {
            System.out.print(i + ", Enter 'q' to quit or 's' to send: ");

            c = scan.nextLine();

            switch(c) {
                case "s":
                    System.out.print("Searching hosts...");
                    logger.info("Enter 'send' fn.");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        System.out.println("Sender thread interrupted");
                        logger.error("Sender thread interrupted");
                    }

                    if (names.isEmpty()) {
                        System.out.print("No connections available.");
                        System.out.println(" Try with another device connected to the network.");
                        logger.info("No connections available.");
                    }
                    else {
                        System.out.println("\nConnect with one of the following:");
                        System.out.println("0: Retry");
                        for (int k = 0; k < names.size(); k++) {
                            System.out.println((k + 1) + ": " + names.get(k) + " at " + ips.get(k));
                        }
                        System.out.print("\nYour choice: ");
                        p = scan.nextInt();
                        scan.nextLine();

                        if (p == 0) {
                            System.out.println("You may retry after a while...");
                            logger.info("Choice - 0. Retry.");
                            names.clear();
                            ips.clear();
                        }

                        else {
                            logger.info("Choice (p) - " + p);
                            host = ips.get(p - 1);
                            logger.debug("Host: " + host);
                            System.out.println("IP of receiver: " + host);
                            System.out.println("Enter path of files and dirs with ',': ");
                            filepath = scan.nextLine();
                            logger.info("Filepath: " + filepath);
                            System.out.println("Done taking input: " + filepath);
                            inet.send(host, filepath);
                        }
                        break;
                    }
                case "q":
                    logger.info("Quit.");
                    break loop;
                default:
                    System.out.println("Your input is not satisfactory...\n");
                    logger.info("Unsatisfactory input: " + c);
            }
        }
        scan.close();
        quit.set(true);

        try {
            sender.t.join();
            receiver.t.join();
            logger.info("Joined child threads.");
        }
        catch (InterruptedException ie) {
            System.out.println("Interrupted while joining threads:\n" + ie);
            logger.error("Interrupted while joining threads: " + ie);
        }
    }
}