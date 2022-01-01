import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

public abstract class Node {
	static final int PACKETSIZE = 1000;
	static final String DEFAULT_DST = "localhost";

	/** Port numbers for the Nodes in the system. */
	static final int BKR_PORT = 50001;
	static final int PUB_PORT = 50002;
	static final int SUB_PORT = 50003;

	/** Packet types */
	static final byte CREATION = 1;
	static final byte PUBLICATION = 2;
	static final byte SUBSCRIPTION = 3;
	static final byte UNSUBSCRIPTION = 4;
	static final byte UPGRADING = 5;
	static final byte CANCELLATION = 6;
	static final byte MESSAGE = 7;

	DatagramSocket socket;
	Listener listener;
	CountDownLatch latch;

	Node() {
		latch = new CountDownLatch(1);
		listener = new Listener();
		listener.setDaemon(true);
		listener.start();
	}

	/**
	 * Create an array of bytes for a DatagramPacket and returns it. Based on custom
	 * packet data layout; byte 0 = type, byte 1 = channel number, byte 2 = premium,
	 * remaining bytes = message.
	 */
	private byte[] createPacketData(int type, int channelNumber, boolean premium, byte[] message) {
		byte[] data = new byte[PACKETSIZE];
		data[0] = (byte) type;
		data[1] = (byte) channelNumber;
		data[2] = premium ? (byte) 1 : 0;
		for (int i = 0; i < message.length && i < PACKETSIZE; i++) {
			data[i + 3] = message[i];
		}
		return data;
	}

	/**
	 * Take the type of packet, channel number, premium, message and destination
	 * address and return an array of one or more packets. Assumes a message can be
	 * stored in multiple packets, but the actual program will only work if the
	 * message fits in one packet.
	 */
	protected DatagramPacket[] createPackets(int type, int channelNumber, boolean premium, String message,
			InetSocketAddress dstAddress) {
		int messageSize = PACKETSIZE - 3;
		byte[] tmpArray = message.getBytes();
		byte[] messageArray = new byte[tmpArray.length];
		for (int i = 0; i < tmpArray.length; i++) {
			messageArray[i] = tmpArray[i];
		}
		int numberOfPackets = 0;
		for (int messageLength = messageArray.length; messageLength > 0; messageLength -= messageSize) {
			numberOfPackets++;
		}
		DatagramPacket[] packets = new DatagramPacket[numberOfPackets];
		int offset = 0;
		for (int sequenceNumber = 0; sequenceNumber < numberOfPackets; sequenceNumber++) {
			byte[] dividedMessage = new byte[messageSize];
			for (int j = offset; j < offset + messageArray.length; j++) {
				dividedMessage[j] = messageArray[j + offset];
			}
			byte[] data = createPacketData(type, channelNumber, premium, dividedMessage);
			DatagramPacket packet = new DatagramPacket(data, data.length, dstAddress);
			packets[sequenceNumber] = packet;
			offset += messageSize;
		}
		return packets;
	}

	/**
	 * Return the type of packet.
	 */
	protected int getType(byte[] data) {
		return data[0];
	}

	/**
	 * Return the channel number.
	 */
	protected int getChannelNumber(byte[] data) {
		return data[1];
	}

	/**
	 * Return if the user is a premium user at the channel.
	 */
	protected int getChannelPremium(byte[] data) {
		return data[2];
	}

	/**
	 * Return the actual content of the message.
	 */
	protected String getMessage(byte[] data) {
		byte[] messageArray = new byte[data.length - 3];
		for (int i = 0; i < messageArray.length && data[i + 3] != 0; i++) {
			messageArray[i] = data[i + 3];
		}
		String message = new String(messageArray).trim();
		return message;
	}

	/**
	 * Set the type of packet.
	 */
	protected void setType(byte[] data, byte type) {
		data[0] = type;
	}

	public abstract void onReceipt(DatagramPacket packet);

	/**
	 *
	 * Listener thread
	 * 
	 * Listens for incoming packets on a datagram socket and informs registered
	 * receivers about incoming packets.
	 */
	class Listener extends Thread {

		/*
		 * Telling the listener that the socket has been initialized
		 */
		public void go() {
			latch.countDown();
		}

		/*
		 * Listen for incoming packets and inform receivers
		 */
		public void run() {
			try {
				latch.await();
				// Endless loop: attempt to receive packet, notify receivers,
				// etc
				while (true) {
					DatagramPacket packet = new DatagramPacket(new byte[PACKETSIZE], PACKETSIZE);
					socket.receive(packet);

					onReceipt(packet);
				}
			} catch (Exception e) {
				if (!(e instanceof SocketException))
					e.printStackTrace();
			}
		}
	}
}
