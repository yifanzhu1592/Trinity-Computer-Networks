
/** Publisher class for custom Publish-Subscribe protocol.
  */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class Publisher extends Node {

  /** Constant substrings to recognize user input. */
  private static final String CREATE = "CRE";
  private static final String PUBLISH = "PUB";

  Terminal terminal;
  InetSocketAddress dstAddress;

  /*
   * Constructor of the Publisher. Initialises the terminal, map of channel names
   * and numbers, the listener and the datagram socket.
   */
  Publisher(Terminal terminal) {
    try {
      this.terminal = terminal;
      dstAddress = new InetSocketAddress(DEFAULT_DST, BKR_PORT);
      socket = new DatagramSocket(PUB_PORT);
      listener.go();
    } catch (java.lang.Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * Mainline of the publisher. Initialises the terminal, calls the contructor and
   * the start method.
   */
  public static void main(String[] args) {
    try {
      Terminal terminal = new Terminal("Publisher");
      (new Publisher(terminal)).start();
      terminal.println("Program completed");
    } catch (java.lang.Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * Function to create a channel. Takes user input for the channel name and sends the
   * packet to the broker to create the packet.
   */
  private void createChannel() {
    String channel = terminal.read("Enter the channel to be created: ");
    terminal.println("Enter the channel to be created: " + channel);

    DatagramPacket[] packets = createPackets(CREATION, Broker.channelNumbers.size(), false, channel, dstAddress);
    Broker.channelNumbers.put(Broker.channelNumbers.size(), channel);
    try {
      socket.send(packets[0]);
    } catch (IOException e) {
      e.printStackTrace();
    }
    terminal.println("Channel creation request has been sent to the broker");
  }

  /*
   * Function to take user input and interact with the broker to publish a message
   * to subscribers of a particular channel.
   */
  private boolean publishMessage() {
    String channel = terminal.read("Enter the channel you want to publish a message under: ");
    terminal.println("Enter the channel you want to publish a message under: " + channel);
    String message = terminal.read("Enter the message to be published: ");
    terminal.println("Enter the message to be published: " + message);
    String premium = terminal.read("Enter yes if it is a premium subscribers only message, no if it isn't: ");
    terminal.println("Enter yes if it is a premium subscribers only message, no if it isn't: " + premium);
    boolean isPremium = premium.toLowerCase().contains("yes");
    int channelNumber = Integer.MAX_VALUE;
    for (int i = 0; i < Broker.channelNumbers.size(); i++) {
      if ((Broker.channelNumbers.get(i)).equals(channel)) {
        channelNumber = i;
      }
    }

    DatagramPacket[] packets = createPackets(PUBLICATION, channelNumber, isPremium, message, dstAddress);
    try {
      socket.send(packets[0]);
    } catch (IOException e) {
      e.printStackTrace();
    }
    terminal.println("Publication request has been sent to the broker");
    return true;
  }

  /*
   * Start function of the publisher. Takes user input to either create a channel or
   * publish a message, then waits for an acknowledgement and a reply message.
   */
  public synchronized void start() throws Exception {
    while (true) {
      String startingString = terminal.read("Enter cre to create a channel or enter pub to publish a message: ");
      terminal.println("Enter cre to create a channel or enter pub to publish a message: " + startingString);
      if (startingString.toUpperCase().contains(CREATE)) {
        createChannel();
        this.wait(); // wait for MESSAGE
      } else if (startingString.toUpperCase().contains(PUBLISH)) {
        if (publishMessage()) {
          this.wait(); // wait for MESSAGE
        }
      } else {
        terminal.println("Invalid input");
      }
    }
  }

  /*
   * Implementation of the abstract method in Node.java to handle Datagram
   * Packets. Prints either a message or an ack from the broker to the terminal,
   * no extra processing of received packets is required.
   */
  @Override
  public synchronized void onReceipt(DatagramPacket packet) {
    this.notify();
    byte[] data = packet.getData();
    terminal.println("Got a new message from the broker: " + getMessage(data));
  }

}
