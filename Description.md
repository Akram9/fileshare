I am not sure. The way I made it requires UDP/TCP sockets to function for now. If PWA can have sockets support, or somehow work with this logic, then fine.

Anyway, some conditions to be satisfied are - 

1. It should be able to create folders and write to files on the local device.

2. It should work totally offline. Going online is not at all considered, except, maybe for uploading logs. Online sharing apps already exist.

3. It should be able to communicate with other devices on the same network. If there are alternatives to sockets, then those can be considered. 

4. It should not disconnect from the network or hinder the device from using the network while sharing files. It means that if the local network is the local wifi, sharing files should not disconnect or stop the use of wifi. Like for example ShareIt satisfies most of the above, but last time I used it, it cut my devices from the LAN.

5. The software on all devices should prefarably be the same. Like if it is a PWA, it should not be that one is like a server while the others are like clients.

6. It should be as cross-platform as possible.

Now, the logic part (numbers as in the picture) - 

1. The program starts with three threads - the main thread, the UDP-reciever thread [1] and UDP-sender thread [2].

2. The UDP-sender thread broadcasts device information on a specific UDP port [3] at some time interval [4]. It stops when it recieves the stop signal from main thread [5].

3. The UDP-reciever thread opens a UDP port to recieve the messages in the broadcast port [6]. It keeps it open for some time interval and then stops and then restarts unless stop signal is sent by main thread [15]. The messages it recieves are from the UDP-sender of other devices in the network. If the message recieved is from a particular device to start recieving files from that device [7], it checks the availability of the device first [8] (I intend to allow only one pair of devices to share contents initially for simplicity) and then send appropriate message back to the device that initiated the request [9 & 10]. If the device is free, it starts another thread - the Reciever thread [11] which runs the Reciever function (TCP) [12] then sends signal to UDP-reciever thread of its ending [13 & 14] (the Reciever thread has not been implemented as intended).

4. The main thread gathers information from the UDP-reciever thread bout all devices available in the network and displays it to the user [16]. The user has to choose from two options - quit or send files [17 & 18].

5. If user chooses to send files, a message through UDP is sent to chosen device. Choosing to share file requires input from the user then the Send function (TCP) is executed from the same thread (as I have limited it to share in pairs only right now) [21].

6. Choosing to quit makes the main thread send quit messages to the two other threads [19]. It waits for the threads to join [20] and then program ends.

There are some features immediately missing - like a device cannot stop another from sending files. The default behaviour should be to allow the user to 

1. accept files from sender

2. always accept files from sender

3. do not accept files from sender

4. never accept files from sender (maybe not implemented)

This is basic security. It would require more security like encryption.
