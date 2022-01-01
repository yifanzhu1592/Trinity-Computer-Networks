import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class EndUser extends Node {

	private Terminal terminal;
	private InetSocketAddress dstAddress;
	private final byte socketNumber;
	private static final String REC = "REC";
	private static final String SEND = "SEND";

	/** Initialises the terminal, datagram socket and listener of the end node.
	*/
	EndUser(byte socketNumber) throws SocketException {
		this.socketNumber = socketNumber;
		this.terminal = new Terminal("EndUser " + (socketNumber-NUM_ROUTERS));
		this.socket = new DatagramSocket(BASE_PORT_NUMBER + socketNumber);
		listener.go();
	}

	/** Implementation of the abstract onReceipt function in Node.java. If the datagram received is a
	  * message, it is printed to the terminal. Otherwise if it is an initialisation message from a
	  * router, that router's address is set as the destination address for all packets sent out.
	  */
	@Override
	public synchronized void onReceipt(DatagramPacket packet) {
		if (getType(packet.getData()) == NODE_INITIALISE_ROUTER) {
			dstAddress = (InetSocketAddress) packet.getSocketAddress();
			terminal.println("Connected to router " + (packet.getPort() - BASE_PORT_NUMBER) + ".");
		} else if (getType(packet.getData()) == NODE_MESSAGE) {
			byte[] data = packet.getData();
			byte src = getMessageSource(data);
			String message = getMessageContent(data);
			terminal.println("New message from end user " + src%NUM_ROUTERS + ": " + message);
		}
		this.start();
	}

	/* Start method of the end node which lets the user choose whether they would like to send a message
	 * or REC for a message.
	 */
	public synchronized void start() {
		while (true) {
			String chosenState = terminal.read("Enter SEND to send a message or enter REC to receive a message: ").toUpperCase();
			terminal.println("Enter SEND to send a message or enter REC to receive a message: " + chosenState);
			if (chosenState.contains(REC)) {
				terminal.println("Wating for messages.");
				return;
			} else if (chosenState.contains(SEND)) {
				sendMessage();
			} else {
				terminal.println("Invalid input.");
			}
		}
	}

	/* Function to send a message to another end node. Asks the user which end node to send the
	 * message to as well as the content of the message. Sends the message into the network.
	 */
	private synchronized void sendMessage() {
		String dest;
		boolean validInput = false;
		byte[] data = new byte[PACKETSIZE];
		setType(data, NODE_MESSAGE);
		setSrc(data, this.socketNumber);

		String stringMessage = terminal.read("Please enter a message to send: ");
		terminal.println("Please enter a message to send: " + stringMessage);
		setMessage(data, stringMessage);
		setLen(data, (byte) stringMessage.length());

		do {
			dest = terminal.read("Send this message to end user 1 or 2 or 3 or 4? ");
			terminal.println("Send this message to end user 1 or 2 or 3 or 4? " + dest);
			if (dest.contains("1")) {
				validInput = true;
				byte finalDst = (byte) (1 + NUM_ROUTERS);
				setDst(data, finalDst);
				DatagramPacket message = new DatagramPacket(data, data.length);
				message.setSocketAddress(dstAddress);
				try {
					socket.send(message);
					terminal.println("Message sent.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (dest.contains("2")) {
				validInput = true;
				byte finalDst = (byte) (2 + NUM_ROUTERS);
				setDst(data, finalDst);
				DatagramPacket message = new DatagramPacket(data, data.length);
				message.setSocketAddress(dstAddress);
				try {
					socket.send(message);
					terminal.println("Message sent.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (dest.contains("3")) {
				validInput = true;
				byte finalDst = (byte) (3 + NUM_ROUTERS);
				setDst(data, finalDst);
				DatagramPacket message = new DatagramPacket(data, data.length);
				message.setSocketAddress(dstAddress);
				try {
					socket.send(message);
					terminal.println("Message sent.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (dest.contains("4")) {
				validInput = true;
				byte finalDst = (byte) (4 + NUM_ROUTERS);
				setDst(data, finalDst);
				DatagramPacket message = new DatagramPacket(data, data.length);
				message.setSocketAddress(dstAddress);
				try {
					socket.send(message);
					terminal.println("Message sent.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} while (!validInput);
	}

	private synchronized void setLen(byte[] data, byte len) {
		data[1] = len;
	}

	private synchronized void setSrc(byte[] data, byte src) {
		data[2] = src;
	}

	private synchronized void setDst(byte[] data, byte dst) {
		data[3] = dst;
	}

	private synchronized void setMessage(byte[] data, String message) {
		byte[] content = (byte[]) message.getBytes();
		for (int i = 0; i < content.length; i++) {
			data[i + 4] = content[i];
		}
	}

}
