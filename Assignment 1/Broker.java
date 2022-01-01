
/** Broker class for custom Publish-Subscribe protocol.
  */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Broker extends Node {
    private Terminal terminal;
    /**
     * Map channel names to a list of its Subscribers.
     */
    static public Map<String, ArrayList<Pair<InetSocketAddress, Boolean>>> subscriberMap = new HashMap<String, ArrayList<Pair<InetSocketAddress, Boolean>>>();
    /** Map channel numbers to channel names. */
    static public Map<Integer, String> channelNumbers = new HashMap<Integer, String>();

    /*
     * Constructor of the Broker. Initialises the terminal, listener and hashmaps.
     */
    Broker(Terminal terminal) {
        this.terminal = terminal;
        try {
            socket = new DatagramSocket(BKR_PORT);
            listener.go();
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Mainline of the Broker. Initialises the terminal and calls the constructor
     * and start function.
     */
    public static void main(String[] args) {
        try {
            Terminal terminal = new Terminal("Broker");
            (new Broker(terminal)).start();
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Creates a channel given data from a creation packet. Returns true if the
     * channel is created, false otherwise (if the channel already exists).
     */
    private boolean createChannel(byte[] data) {
        ArrayList<Pair<InetSocketAddress, Boolean>> socketNumbers = new ArrayList<Pair<InetSocketAddress, Boolean>>();
        String channelName = getMessage(data);
        if (!subscriberMap.containsKey(channelName)) {
            subscriberMap.put(channelName, socketNumbers);
            int topicNumber = getChannelNumber(data);
            channelNumbers.put(topicNumber, channelName);
            terminal.println("Created a new channel: " + channelName);
            return true;
        }
        return false;
    }

    /*
     * Publishes a message for a topic given data from a publication packet. Returns
     * true if the message is published, false otherwise (the topic does not exist).
     */
    private boolean publish(byte[] data) {
        int channelNumber = getChannelNumber(data);
        boolean premium = getChannelPremium(data) != 0;
        setType(data, PUBLICATION);
        if (channelNumbers.containsKey(channelNumber)) {
            String channelName = channelNumbers.get(channelNumber);
            ArrayList<Pair<InetSocketAddress, Boolean>> dstAddresses = subscriberMap.get(channelName);
            if (!dstAddresses.isEmpty()) {
                if (!premium) {
                    for (int i = 0; i < dstAddresses.size(); i++) {
                        DatagramPacket publication = new DatagramPacket(data, data.length,
                                dstAddresses.get(i).getLeft());
                        try {
                            socket.send(publication);
                            terminal.println("A new message has been published in the channel: " + channelName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    for (int i = 0; i < dstAddresses.size(); i++) {
                        if (dstAddresses.get(i).getRight()) {
                            DatagramPacket publication = new DatagramPacket(data, data.length,
                                    dstAddresses.get(i).getLeft());
                            try {
                                socket.send(publication);
                                terminal.println(
                                        "A new premium message has been published in the channel: " + channelName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    /*
     * Subscribes a subscriber to a channel given data from a subscription packet
     * and the subscriber's address. Returns true if the subscriber is successfully
     * added to the subscription list, false otherwise (the channel does not exist).
     */
    private boolean subscribe(byte[] data, SocketAddress subscriberAddress) {
        String channelName = getMessage(data);
        boolean premium = getChannelPremium(data) != 0;
        if (subscriberMap.containsKey(channelName)) {
            ArrayList<Pair<InetSocketAddress, Boolean>> subscribers = subscriberMap.get(channelName);
            subscribers.add(new Pair<InetSocketAddress, Boolean>((InetSocketAddress) subscriberAddress, premium));
            subscriberMap.put(channelName, subscribers);
            terminal.println("A new subscriber has subscribed to the channel: " + channelName);
            return true;
        }
        return false;
    }

    /*
     * Unsubscribes a subscriber from a channel given data from an unsubscription
     * packet and the subscriber's address. Returns true if the subscriber is
     * successfully removed to the subscription list, false otherwise (the channel
     * does not exist).
     */
    private boolean unsubscribe(byte[] data, SocketAddress subscriberAddress) {
        boolean unsubscribed = false;
        String channelName = getMessage(data);
        if (subscriberMap.containsKey(channelName)) {
            ArrayList<Pair<InetSocketAddress, Boolean>> subscribers = subscriberMap.get(channelName);
            if (!subscribers.isEmpty()) {
                for (int i = 0; i < subscribers.size(); i++) {
                    if (subscribers.get(i).getLeft().equals(subscriberAddress)) {
                        subscribers.remove(i);
                        terminal.println("A subscriber unsubscribed from " + channelName + ".");
                        unsubscribed = true;
                    }
                }
            }
            subscriberMap.put(channelName, subscribers);
        }
        return unsubscribed;
    }

    /*
     * Upgrades a subscriber from a channel to a premium subscribe given data from
     * an ungrading packet and the subscriber's address. Returns true if the
     * subscriber is successfully upgraded, false otherwise (the user has not
     * subscribed to the channel).
     */
    private boolean upgrade(byte[] data, SocketAddress subscriberAddress) {
        boolean upgraded = false;
        String channelName = getMessage(data);
        if (subscriberMap.containsKey(channelName)) {
            ArrayList<Pair<InetSocketAddress, Boolean>> subscribers = subscriberMap.get(channelName);
            if (!subscribers.isEmpty()) {
                for (int i = 0; i < subscribers.size(); i++) {
                    if (subscribers.get(i).getLeft().equals(subscriberAddress)) {
                        subscribers.remove(i);
                        subscribers
                                .add(new Pair<InetSocketAddress, Boolean>((InetSocketAddress) subscriberAddress, true));
                        terminal.println("One more premium user at " + channelName + ".");
                        upgraded = true;
                    }
                }
            }
            subscriberMap.put(channelName, subscribers);
        }
        return upgraded;
    }

    /*
     * Cancels a subscriber's premium from a channel given data from an cancellation
     * packet and the subscriber's address. Returns true if the subscriber's premium
     * is successfully cancelled, false otherwise (the channel does not exist or the
     * subscriber was not a premium subscriber).
     */
    private boolean cancel(byte[] data, SocketAddress subscriberAddress) {
        boolean cancelled = false;
        String channelName = getMessage(data);
        if (subscriberMap.containsKey(channelName)) {
            ArrayList<Pair<InetSocketAddress, Boolean>> subscribers = subscriberMap.get(channelName);
            if (!subscribers.isEmpty()) {
                for (int i = 0; i < subscribers.size(); i++) {
                    if (subscribers.get(i).getLeft().equals(subscriberAddress)
                            && subscribers.get(i).getRight() == true) {
                        subscribers.remove(i);
                        subscribers.add(
                                new Pair<InetSocketAddress, Boolean>((InetSocketAddress) subscriberAddress, false));
                        terminal.println("One less premium user at " + channelName + ".");
                        cancelled = true;
                    }
                }
            }
            subscriberMap.put(channelName, subscribers);
        }
        return cancelled;
    }

    /*
     * Sends a message in a Datagram Packet given the message as a String and the
     * destination address.
     */
    private void sendMessage(String message, SocketAddress socketAddress) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        DatagramPacket packet = createPackets(MESSAGE, 0, false, message, inetSocketAddress)[0];
        try {
            socket.send(packet);
            terminal.println("Broker sent a message: " + message);
        } catch (IOException e) {
            e.printStackTrace();
            terminal.println("Broker failed to send a message: " + message);
        }
    }

    /*
     * Start function for the Broker. The Broker never initialises contact unless
     * contacted by another node first, so just waits.
     */
    public synchronized void start() throws Exception {
        terminal.println("Waiting for contact");
        while (true) {
            this.wait();
        }
    }

    /*
     * Implementation of the abstract function in Node.java to handle received
     * Datagram Packets.
     */
    public synchronized void onReceipt(DatagramPacket packet) {
        try {
            this.notify();
            byte[] data = packet.getData();
            switch (getType(data)) {
            case CREATION:
                terminal.println("Recieved the request to create a channel");
                if (!createChannel(data)) {
                    sendMessage("This is already a channel", packet.getSocketAddress());
                } else {
                    sendMessage("Channel creation successful", packet.getSocketAddress());
                }
                break;
            case PUBLICATION:
                terminal.println("Recieved the request to publish a message");
                if (!publish(data)) {
                    sendMessage("This channel does not exist", packet.getSocketAddress());
                } else {
                    sendMessage("Publication successful", packet.getSocketAddress());
                }
                break;
            case SUBSCRIPTION:
                terminal.println("Recieved the request to subscribe to a channel");
                if (!subscribe(data, packet.getSocketAddress())) {
                    sendMessage("This channel does not exist", packet.getSocketAddress());
                } else {
                    sendMessage("Subscription successful", packet.getSocketAddress());
                }
                break;
            case UNSUBSCRIPTION:
                terminal.println("Request recieved to unsubscribe from a channel");
                if (!unsubscribe(data, packet.getSocketAddress())) {
                    sendMessage("This channel does not exist", packet.getSocketAddress());
                } else {
                    sendMessage("Unsubscription successful", packet.getSocketAddress());
                }
                break;
            case UPGRADING:
                terminal.println("Request recieved to upgrade to premium user for a channel");
                if (!upgrade(data, packet.getSocketAddress())) {
                    sendMessage("This channel does not exist", packet.getSocketAddress());
                } else {
                    sendMessage("Upgrading successful", packet.getSocketAddress());
                }
                break;
            case CANCELLATION:
                terminal.println("Request recieved to cancel premium user for a channel");
                if (!cancel(data, packet.getSocketAddress())) {
                    sendMessage("This channel does not exist or the user was not a premium user",
                            packet.getSocketAddress());
                } else {
                    sendMessage("Cancellation successful", packet.getSocketAddress());
                }
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Pair<L, R> {

    private final L left;
    private final R right;

    public Pair(L left, R right) {
        assert left != null;
        assert right != null;

        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

}
