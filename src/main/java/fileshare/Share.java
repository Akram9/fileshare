package fileshare;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class Log {
    static Logger logger = Logger.getLogger("Share");
    private static Log ins = new Log();

    public static Log getInstance() {
        return ins;
    }

    Log() {
        String logfile = "share.log";

        try {
            FileHandler fileHandler = new FileHandler(logfile, 1000000, 10, false);
            SimpleFormatter simpleFormatter = new SimpleFormatter();

            fileHandler.setFormatter(simpleFormatter);
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        }
        catch (IOException io) {
            logger.severe("Logger exception:\n"+io.toString());
        }
    }
}

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

    public static void main (String[] args) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ips = new ArrayList<>();
        AtomicBoolean quit = new AtomicBoolean(false);
        Property props = new Property();
        String c;
        Functions inet = new Functions();
        Scanner scan = new Scanner(System.in);
        OwnIP ownip = new OwnIP();
        String ip = ownip.getIP();
        String host;
        Log log = new Log();
        final String name = props.getProperty("name");
        int p, i = 0;

        System.out.println("Device: " + name + ", at: " + ip);

        Receiver receiver = new Receiver(names, ips, ip, quit);
        Sender sender = new Sender(name, ip, quit);

        loop: while (true) {
            System.out.print(i + ", Enter 'q' to quit or 's' to send: ");

            c = scan.nextLine();

            switch(c) {
                case "s":
                    System.out.print("Searching hosts...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        System.out.println("Sender thread interrupted");
                    }

                    if (names.isEmpty()) {
                        System.out.print("No connections available.");
                        System.out.println(" Try with another device connected to the network.");
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
                            names.clear();
                            ips.clear();
                        }

                        else {
                            host = ips.get(p - 1);
                            System.out.println("IP of receiver: " + host);
                            System.out.println("Enter path of files and dirs with ',': ");
                            String filepath = scan.nextLine();
                            System.out.println("Done taking input: " + filepath);
                            inet.send(host, filepath);
                        }
                        break;
                    }
                case "q":
                    break loop;
                default:
                    System.out.println("Your input is not satisfactory...\n");
            }
        }
        scan.close();
        quit.set(true);

        try {
            sender.t.join();
            receiver.t.join();
        }
        catch (InterruptedException ie) {
            System.out.println("Interrupted while joining threads:\n" + ie);

        }
    }
}