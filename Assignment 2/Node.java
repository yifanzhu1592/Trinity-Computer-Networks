import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public abstract class Node {

	/** Constants for messages and nodes. */
	protected static final byte HELLO = 0;
	protected static final byte PACKET_IN = 1;
	protected static final byte FLOW_REMOVED = 2;
	protected static final byte FLOW_MOD = 3;
	protected static final byte NODE_INITIALISE_ROUTER = 4;
	protected static final byte NODE_MESSAGE = 5;

	/** Other constants. */
	protected static final int PACKETSIZE = 100;
	protected static final int BASE_PORT_NUMBER = 51510;
	protected static final String LOCALHOST = "localhost";
	protected static final int CONTROLLER_PORT = 0;

	/** The index for the terms in preconfInfo */
	protected static final int SRC_INDEX = 0;
	protected static final int DST_INDEX = 1;
	protected static final int ROUTER_INDEX = 2;
	protected static final int INPUT_INDEX = 3;
	protected static final int OUTPUT_INDEX = 4;

	public static final byte NUM_ROUTERS = 8;
	public static final byte NUM_END_NODES = 4;
	DatagramSocket socket;
	Listener listener;
	CountDownLatch latch;

	Node() {
		latch = new CountDownLatch(1);
		listener = new Listener();
		listener.setDaemon(true);
		listener.start();
	}

	public abstract void onReceipt(DatagramPacket packet);

	protected byte getType(byte data[]) {
		return data[0];
	}
	
	protected void setType(byte[] data, byte type){
		data[0] = type;
	}
	
	protected byte getMessageLength(byte data[]){
		assert(getType(data)==NODE_MESSAGE);
		return data[1];
	}

	protected byte getMessageSource(byte data[]){
		assert(getType(data)==NODE_MESSAGE);
		return data[2];
	}
	
	protected byte getMessageDest(byte data[]){
		assert(getType(data)==NODE_MESSAGE);
		return data[3];
	}
	
	protected String getMessageContent(byte data[]){
		assert(getType(data)==NODE_MESSAGE);
		byte[] content = Arrays.copyOfRange(data, 3, data.length);
		String messageContent = new String(content).trim();
		return messageContent;
	}

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
		public synchronized void run() {
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
