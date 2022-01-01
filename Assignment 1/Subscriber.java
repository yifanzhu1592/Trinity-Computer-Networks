
/** Subscriber class for custom Publish-Subscribe protocol.
  */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Random;

public class Subscriber extends Node {
    /** Constant substrings to recognize user input. */
    private static final String SUBSCRIBE = "SUB";
    private static final String UNSUBSCRIBE = "UNSUB";
    private static final String UPGRADE = "UPG";
    private static final String CANCEL = "CAN";
    private static final String RECEIVE = "REC";

    private Terminal terminal;
    private InetSocketAddress dstAddress;

    /*
     * Subscriber constructor. Initialises the terminal, datagram socket and
     * listener.
     */
    Subscriber(Terminal terminal) {
        try {
            this.terminal = terminal;
            dstAddress = new InetSocketAddress(DEFAULT_DST, BKR_PORT);
            Random rand = new Random();
            socket = new DatagramSocket(SUB_PORT + rand.nextInt(1000));
            listener.go();
        } catch (java.lang.Exception e) {
        }
    }

    /*
     * Start method of subscriber. Takes user input to subscribe to a channel or
     * unsubscribe from a channel or upgrade to premium user or cancel premium or
     * receive messages, then waits for an acknowledgement and a reply message.
     */
    public synchronized void start() throws Exception {
        while (true) {
            String startingString = terminal.read(
                    "Enter sub to subscribe to a channel or enter unsub to unsubscribe from a channel or enter upg to upgrade to premium user or enter can to cancel premium or enter rec to receive messages: ");
            terminal.println(
                    "Enter sub to subscribe to a channel or enter unsub to unsubscribe from a channel or enter upg to upgrade to premium user or enter can to cancel premium or enter rec to receive messages: "
                            + startingString);
            if (startingString.toUpperCase().contains(UNSUBSCRIBE)) {
                unsubscribe();
                this.wait(); // wait for MESSAGE
            } else if (startingString.toUpperCase().contains(SUBSCRIBE)) {
                subscribe();
                this.wait(); // wait for MESSAGE
            } else if (startingString.toUpperCase().contains(UPGRADE)) {
                upgrade();
                this.wait(); // wait for MESSAGE
            } else if (startingString.toUpperCase().contains(CANCEL)) {
                cancel();
                this.wait(); // wait for MESSAGE
            } else if (startingString.toUpperCase().contains(RECEIVE)) {
                this.wait();
            } else {
                terminal.println("Invalid input");
            }
        }
    }

    /*
     * Takes user input about the name of the channel to subscribe to and sends a
     * subscription packet to the broker.
     */
    public synchronized void subscribe() {
        String data = terminal.read("Enter the channel to subscribe to: ");
        terminal.println("Enter the channel to subscribe to: " + data);
        String premium = terminal.read("Enter yes if you want to be a premium subscriber, otherwise enter no: ");
        terminal.println("Enter yes if you want to be a premium subscriber, otherwise enter no: " + premium);
        boolean isPremium = premium.toLowerCase().contains("yes");
        DatagramPacket packet = createPackets(SUBSCRIPTION, 0, isPremium, data, dstAddress)[0];
        try {
            socket.send(packet);
        } catch (IOException e) {
        }
        terminal.println("Subscription request has been sent to the broker");
    }

    /*
     * Takes user input about the name of the channel to unsubscribe from and sends an
     * unsubscription packet to the broker.
     */
    public synchronized void unsubscribe() {
        String data = terminal.read("Enter the topic to unsubscribe from: ");
        terminal.println("Enter the topic to unsubscribe from: " + data);
        DatagramPacket packet = createPackets(UNSUBSCRIPTION, 0, false, data, dstAddress)[0];
        try {
            socket.send(packet);
        } catch (IOException e) {
        }
        terminal.println("Subscription request has been sent to the broker");
    }

    /*
     * Takes user input about the name of the channel to upgrade subscribtion and sends an
     * upgrading packet to the broker.
     */
    public synchronized void upgrade() {
        String data = terminal.read("Enter the channel to upgrade: ");
        terminal.println("Enter the channel to ungrade: " + data);
        DatagramPacket packet = createPackets(UPGRADING, 0, false, data, dstAddress)[0];
        try {
            socket.send(packet);
        } catch (IOException e) {
        }
        terminal.println("Upgrading request has been sent to the broker");
    }

    /*
     * Takes user input about the name of the channel to cancel subscription and sends an
     * cancellation packet to the broker.
     */
    public synchronized void cancel() {
        String data = terminal.read("Enter the channel to cancel premium: ");
        terminal.println("Enter the channel to cancel premium: " + data);
        DatagramPacket packet = createPackets(CANCELLATION, 0, false, data, dstAddress)[0];
        try {
            socket.send(packet);
        } catch (IOException e) {
        }
        terminal.println("Cancellation request has been sent to the broker");
    }

    /*
     * Mainline for subscriber. Initialises the terminal, calls the constructor and
     * start method.
     */
    public static void main(String[] args) {
        try {
            Terminal terminal = new Terminal("Subscriber");
            (new Subscriber(terminal)).start();
        } catch (java.lang.Exception e) {
        }
    }

    /*
     * Implementation of the abstract method in Node.java to handle incoming
     * Datagram Packets.
     */
    @Override
    public synchronized void onReceipt(DatagramPacket packet) {
        try {
            this.notify();
            byte[] data = packet.getData();
            if (getType(data) == MESSAGE) {
                terminal.println("Got a new message from the broker: " + getMessage(data));
            } else if (getType(data) == PUBLICATION) {
                terminal.println("Got a new publication from the broker: " + getMessage(data));
            } else {
            }
        } catch (Exception e) {
        }
    }
}
