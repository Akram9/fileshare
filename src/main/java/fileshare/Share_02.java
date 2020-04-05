package fileshare;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

class Property {

    String getProperty(String property) {
        Properties properties = new Properties();

        try {
            properties.load(new FileInputStream("src/main/resources/config.properties"));
            return properties.getProperty(property);
        }
        catch (IOException e) {
            System.out.println("Error reading properties file:\n" + e);
        }
        return "<empty>";
    }
}


class Functions {
    
    void recv() {
        int port = 5001;
        try {
            System.out.println("\nThis is recv function\n");
            OwnIP ownip = new OwnIP();
            String addr = ownip.ipaddr();
            System.out.println("Local Address: " + addr);

            String fname, msg, path;
            Property props = new Property();
            final String root = props.getProperty("download_path");
            byte[] buffer = new byte[8192];
            //byte[] mess = new byte[2048];
            int len = 0;
            long fsize;

            ServerSocket server = new ServerSocket(port);
            Socket clientSocket = server.accept();

            System.out.println("connected");
            
            InputStream in = clientSocket.getInputStream();
            PrintWriter mout = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader min = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            
            while (true) {
                System.out.println("Waiting for message...");
                msg = min.readLine();
                System.out.println("Client: " + msg);

                if (msg.split("<SEP>").length == 3) {break;}

                else if (msg.split("<SEP>").length == 1) {
                    path = root + "/" + msg;
                    if (!(new File(path).mkdir())) {
                        if (new File(path).isDirectory()){
                            System.out.println(path + " already created. Using it.");
                        }
                        else {
                            System.out.println(path + " can't be created");
                            if (!(new File(path).canWrite())) {
                                System.out.println("No permission to write here");
                            }
                            else {
                                System.out.println("Has permission to write here");
                            }
                            //TODO exit this to either connection close or repeat till it is created
                            System.exit(1);
                        }
                    }
                    msg = "Created directory " + path;
                    mout.println(msg);
                    System.out.println("Server: " + msg);
                }

                else if (msg.split("<SEP>").length == 2) {
                    fname = msg.split("<SEP>")[0];
                    System.out.println(msg.split("<SEP>")[1].length() + ", " + len);
                    fsize = Long.parseLong(msg.split("<SEP>")[1]);
                    System.out.print("File to receive is: " + fname);
                    System.out.println(" with size: " + fsize);
                    msg = "Got metadata";
                    mout.println(msg);
                    System.out.println("Server: " + msg);
                    path = root + "/" + fname;

                    try {
                        FileOutputStream fin = new FileOutputStream(path);
                        try {
                            len = 0;
                            while(fsize > 0) {
                                len = in.read(buffer);
                                fin.write(buffer, 0, len);
                                fsize -= len;
                                //len = 0;
                            }
                        }
                        catch (IOException io) {
                            System.out.println("Error writing to file");
                            System.exit(1);
                        }
                        try {
                            fin.close();
                        }
                        catch (IOException io) {
                            System.out.println("Error closing file");
                            System.exit(1);
                        }
                    }
                    catch (IOException io) {
                        System.out.println("Error opening file");
                        System.exit(1);
                    }

                    msg = "Recieved file " + fname;
                    mout.println(msg);
                    System.out.println("Server: " + msg);
                }
            }

            try {
                min.close();
                mout.close();
                in.close();
                //out.close();
                clientSocket.close();
                server.close();
            }
            catch (IOException io) {
                System.out.println("Error closing something:\n" + io);
                System.exit(1);
            }
        }
        catch (IOException i) {
            System.out.println("IOError:\n" + i);
            System.exit(1);
        }
        System.out.println("Closed all");
    }
    
    void send(String host, String filepath) {
        
        int port = 5001;
        int len;

        try {
            String f1, fpath, fname, msg, root;
            byte[] buffer = new byte[8192];
            //byte[] mess = new byte[2048];
            ArrayList<String> dirs = new ArrayList<>();
            String[] a, paths;
            int i;
            long fsize;
            File file;
            boolean onlyfile = false;
            
            try {
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e) {
				System.out.println("Error while waiting to connect:\n");
				e.printStackTrace();
            }
            
            Socket sock = new Socket(host, port);
            OutputStream out = sock.getOutputStream();
            //InputStream in = sock.getInputStream();
            PrintWriter mout = new PrintWriter(sock.getOutputStream(), true);
            BufferedReader min = new BufferedReader(
                new InputStreamReader(sock.getInputStream()));
            
            System.out.println("\nThis is send function\n");
            paths = filepath.split(",");
            for (i = 0; i < paths.length; i++) {
                fpath = paths[i];
                file = new File(fpath);

                if (!file.isFile() && !file.isDirectory()) {
                    System.out.println(fpath + " is not valid file/dir. Skipping.");
                    continue;
                }

                if (file.isFile()) {
                    onlyfile = true;
                }

                root = String.join("/",
                    Arrays.copyOfRange(
                        fpath.split("/"), 0, (fpath.split("/").length - 1)));
                System.out.println("Root: " + root);
                f1 = fpath.split("/")[fpath.split("/").length - 1];
                dirs.add(f1);

                while (dirs.size() > 0) {
                    System.out.println("dirs: " + dirs);
                    file = new File(root + "/" + dirs.get(0));

                    if (onlyfile) {
                        System.out.println(fpath + " is a file.");
                        a = new String[] {f1};
                    }

                    else {
                        a = file.list();
                        msg = dirs.get(0);
                        mout.println(msg);
                        System.out.println("Client: " + msg);
                        System.out.println("Waiting for message...");
                        msg = min.readLine();
                        System.out.println("Server: " + msg);
                    }

                    assert a != null;
                    for (i = 0; i < a.length; i++) {

                        if (onlyfile) {
                            file = new File(root + "/" + a[i]);
                        }
                        else {
                            file = new File(root + "/" + dirs.get(0) + "/" + a[i]);
                        }

                        if (file.isDirectory()) {
                            dirs.add(dirs.get(0) + "/" + a[i]);
                        }

                        else if (file.isFile()) {
                            fsize = file.length();
                            fname = a[i];
                            System.out.println(fname);

                            if (onlyfile) {
                                msg = fname + "<SEP>" + fsize;
                            }
                            else {
                                msg = dirs.get(0) + "/" + fname + "<SEP>" + fsize;
                            }

                            mout.println(msg);
                            System.out.println("Client: " + msg);
                            System.out.println("Waiting for message...");

                            msg = min.readLine();
                            System.out.print("Server: ");
                            System.out.println(msg);
                            System.out.println("Sending file...");

                            try {
                                InputStream fin = new FileInputStream(file);
                                while ((len = fin.read(buffer)) > 0) {
                                    out.write(buffer, 0, len);
                                }
                                try {
                                    fin.close();
                                }
                                catch (IOException io) {
                                    System.out.println("Error closing file");
                                    System.exit(1);
                                }
                            }
                            catch (IOException io) {
                                System.out.println("Error opening file");
                                System.exit(1);
                            }
                            System.out.println("Waiting for message...");
                            msg = min.readLine();
                            System.out.println("Server: " + msg);
                        }
                    }
                    dirs.remove(0);
                    onlyfile = false;
                }
            }
            msg = "break<SEP>the<SEP>loop";
            System.out.println("Client: " + msg);
            mout.println(msg);

            try {
                min.close();
                mout.close();
                //in.close();
                out.close();
                sock.close();
            }
            catch (IOException io) {
                System.out.println("Error closing ssomething\n" + io);
                System.exit(1);
            }
        }
        catch (UnknownHostException u) {
            System.out.println("Unknown host\n" + u);
            System.exit(1);
        }
        catch (IOException i) {
            System.out.println("IOError\n" + i);
            System.exit(1);
        }
        System.out.println("Closed all");
    }
}

class Share_02 {

    public static void main (String[] args) {
        Property props = new Property();
        String c;
        Functions inet = new Functions();
        DiscoveryFunctions df = new DiscoveryFunctions();
        Scanner scan = new Scanner(System.in);
        final String name = props.getProperty("name");
        int i = 0;
        System.out.println("Device name: " + name);

        loop: while (true) {
            System.out.print(i + ", Enter 'q' to quit, 's' to send, 'r' to receive: ");
            c = scan.nextLine();

            if (c.equals("\n")) {
                System.out.println("newline character");
                continue;
            }
            switch(c) {
                case "r":
                    df.receive(name);
                    inet.recv();
                    break;
                case "w":
                    OwnIP ownip = new OwnIP();
                    String ip = ownip.ipaddr();
                    System.out.println("IP: " + ip);
                    break;
                case "s":
                    System.out.print("Searching hosts...");
                    String host = df.send(name, scan);
                    System.out.println("IP of reciever: " + host);
                    System.out.println("Enter path of files and dirs with ',': ");
                    String filepath = scan.nextLine();
                    System.out.println("Done taking input: " + filepath);
                    inet.send(host, filepath);
                    break;
                case "q":
                    break loop;
                default:
                    System.out.println("Your input is not satisfactory...\n");
            }
        }
        scan.close();
    }
}