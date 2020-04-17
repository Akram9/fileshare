package fileshare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


class Receiver implements Runnable {
    
    final int port = 5001;
    private Logger logger = LoggerFactory.getLogger("Functions");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();

    AtomicBoolean quit;
    Thread thread;
    
    Receiver(AtomicBoolean quit) {
        this.quit = quit;
        thread = new Thread(this, "Receiver");
        thread.start();
    }
    
    @Override
    public void run() {
        MDC.setContextMap(contextMap);
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            logger.error("Could not start a socket server: " + e);
        }
        try {
            assert serverSocket != null;
            serverSocket.setSoTimeout(3000);
        } catch (SocketException e) {
            logger.error("Could not set timeout to server socket: " + e);
        }
        while (!quit.get()) {
            logger.debug("Enter loop.");
            try {
                Socket clientSocket = serverSocket.accept();
                logger.debug(clientSocket.getInetAddress() + " connected.");
                ReceiverThread receiverThread = new ReceiverThread(clientSocket);

                while (receiverThread.thread.isAlive()) {
                    try {
                        receiverThread.thread.join();
                    }
                    catch (InterruptedException ie) {
                        logger.error("Expected error: " + ie);
                    }
                }
            } catch (IOException e) {
                logger.error("Error at Receiver: " + e);
            }
            logger.debug("End of loop.");
        }
        logger.debug("Quit loop.");
        try {
            serverSocket.close();
            logger.debug("Closed server socket.");
        } catch (IOException io) {
            logger.error("Error closing socket: " + io);
        }
    }
}

class ReceiverThread implements Runnable {
    
    Socket clientSocket;
    Thread thread;
    private Logger logger = LoggerFactory.getLogger("Functions");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();

    ReceiverThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
        thread = new Thread(this, "ReceiverThread");
        thread.start();
    }

    @Override
    public void run() {
        MDC.setContextMap(contextMap);
        try {

            logger.info("Entered receive function.");

            String fname, msg, path;
            Property props = new Property();
            final String root = props.getProperty("download_path");
            byte[] buffer = new byte[8192];
            //byte[] mess = new byte[2048];
            int len;
            long fsize;

            InputStream in = clientSocket.getInputStream();
            PrintWriter mout = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader min = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );

            while (true) {
                logger.debug("Waiting for message.");
                msg = min.readLine();
                logger.debug("Sender: " + msg);

                if (msg.split("<SEP>").length == 3) {
                    break;
                } else if (msg.split("<SEP>").length == 1) {
                    path = root + "/" + msg;
                    if (!(new File(path).mkdir())) {
                        if (new File(path).isDirectory()) {
                            logger.debug(path + " already created. Using it.");
                        } else {
                            logger.info(path + " can't be created");
                            if (!(new File(path).canWrite())) {
                                logger.info("No permission to write here");
                            } else {
                                logger.info("Has permission to write here");
                            }
                            //TODO exit this to either connection close or repeat till it is created
                            logger.error("File I/O error. Exit.");
                            System.exit(1);
                        }
                    }
                    msg = "Created directory " + path;
                    mout.println(msg);
                    logger.debug(msg);
                } else if (msg.split("<SEP>").length == 2) {
                    fname = msg.split("<SEP>")[0];
                    fsize = Long.parseLong(msg.split("<SEP>")[1]);
                    logger.debug(msg);
                    msg = "Got metadata";
                    mout.println(msg);
                    path = root + "/" + fname;

                    try {
                        FileOutputStream fin = new FileOutputStream(path);
                        logger.debug("Opened: " + path);
                        try {
                            while (fsize > 0) {
                                len = in.read(buffer);
                                fin.write(buffer, 0, len);
                                fsize -= len;
                            }
                        } catch (IOException io) {
                            logger.error("Error writing to file");
                            System.exit(1);
                        }
                        try {
                            fin.close();
                        } catch (IOException io) {
                            logger.error("Error closing file");
                            System.exit(1);
                        }
                    } catch (IOException io) {
                        logger.error("Error opening file");
                        System.exit(1);
                    }

                    msg = "Received file " + fname;
                    mout.println(msg);
                    logger.debug(msg);
                }
            }

            try {
                min.close();
                mout.close();
                in.close();
                //out.close();
                clientSocket.close();
            } catch (IOException io) {
                logger.error("Error closing something: " + io);
                System.exit(1);
            }
        } catch (IOException i) {
            logger.error("IOError: " + i);
            System.exit(1);
        }
        logger.info("Closed all");
    }
}
    
class Sender implements Runnable {
    
    String host, filepath;
    Thread thread;

    private Logger logger = LoggerFactory.getLogger("Sender");
    private Map<String, String> contextMap = MDC.getCopyOfContextMap();
    
    Sender(String host, String filepath) {
        this.host = host;
        this.filepath = filepath;
        thread = new Thread(this, "Sender");
        thread.start();
    }
    
    @Override
    public void run() {
        MDC.setContextMap(contextMap);

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
                logger.error("Error while waiting to connect: " + e);
            }

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 2000);
            OutputStream out = socket.getOutputStream();
            //InputStream in = socket.getInputStream();
            PrintWriter mout = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader min = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            paths = filepath.split(",");
            for (i = 0; i < paths.length; i++) {
                fpath = paths[i];
                file = new File(fpath);

                if (!file.isFile() && !file.isDirectory()) {
                    System.out.println(fpath + " is not valid file/dir. Skipping.");
                    logger.debug(fpath + " is not valid path. Skipping.");
                    continue;
                }

                if (file.isFile()) {
                    onlyfile = true;
                }

                root = String.join("/",
                        Arrays.copyOfRange(
                                fpath.split("/"), 0, (fpath.split("/").length - 1)));
                System.out.println("Root: " + root);
                logger.debug("Root: " + root);
                f1 = fpath.split("/")[fpath.split("/").length - 1];
                dirs.add(f1);

                while (dirs.size() > 0) {
                    System.out.println("dirs: " + dirs);
                    logger.debug("dirs: " + dirs);
                    file = new File(root + "/" + dirs.get(0));

                    if (onlyfile) {
                        System.out.println(fpath + " is a file.");
                        logger.debug(fpath + " is a file.");
                        a = new String[] {f1};
                    }

                    else {
                        a = file.list();
                        msg = dirs.get(0);
                        mout.println(msg);
                        System.out.println("Me: " + msg);
                        logger.debug("Me: " + msg);
                        System.out.println("Waiting for message...");
                        msg = min.readLine();
                        System.out.println("Receiver: " + msg);
                        logger.debug("Me: " + msg);
                    }

                    assert a != null;
                    for (String s : a) {

                        if (onlyfile) {
                            file = new File(root + "/" + s);
                        } else {
                            file = new File(root + "/" + dirs.get(0) + "/" + s);
                        }

                        if (file.isDirectory()) {
                            dirs.add(dirs.get(0) + "/" + s);
                        } else if (file.isFile()) {
                            fsize = file.length();
                            fname = s;
                            System.out.println(fname);
                            logger.debug(fname);

                            if (onlyfile) {
                                msg = fname + "<SEP>" + fsize;
                            } else {
                                msg = dirs.get(0) + "/" + fname + "<SEP>" + fsize;
                            }

                            mout.println(msg);
                            System.out.println("Me: " +  msg);
                            logger.debug("Me: " + msg);
                            System.out.println("Waiting for message...");

                            msg = min.readLine();
                            System.out.print("Receiver: " + msg);
                            logger.debug("Receiver: " + msg);
                            System.out.println("Sending file...");
                            logger.debug("Sending file.");

                            try {
                                InputStream fin = new FileInputStream(file);
                                while ((len = fin.read(buffer)) > 0) {
                                    out.write(buffer, 0, len);
                                }
                                try {
                                    fin.close();
                                } catch (IOException io) {
                                    System.out.println("Error closing file");
                                    logger.error("Error closing file");
                                    System.exit(1);
                                }
                            } catch (IOException io) {
                                System.out.println("Error opening file");
                                logger.error("Error opening file");
                                System.exit(1);
                            }
                            System.out.println("Waiting for message...");
                            msg = min.readLine();
                            System.out.println("Receiver: " + msg);
                        }
                    }
                    dirs.remove(0);
                    onlyfile = false;
                }
            }
            msg = "break<SEP>the<SEP>loop";
            System.out.println("Me: " + msg);
            mout.println(msg);
            logger.info("Breaking connection.");

            try {
                min.close();
                mout.close();
                //in.close();
                out.close();
                socket.close();
            }
            catch (IOException io) {
                System.out.println("Error closing something\n" + io);
                logger.error("Error closing something: " + io);
                System.exit(1);
            }
        }
        catch (UnknownHostException u) {
            System.out.println("Unknown host\n" + u);
            logger.error("Unknown host: " + u);
            System.exit(1);
        }
        catch (IOException i) {
            System.out.println("IOError\n" + i);
            logger.error("IOError: " + i);
            System.exit(1);
        }
        System.out.println("Closed all");
        logger.info("Closed connection.");
    }
}