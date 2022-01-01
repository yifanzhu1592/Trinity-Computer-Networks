import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;

public class Router extends Node {
	private Terminal terminal;
	private int routerNumber;
	private byte[][] flowTable;
	private InetSocketAddress controllerAddress;
	private InetSocketAddress endNodeAddress;

	/** Initialises the terminal, datagram socket and listener.
	*/
	Router(byte routerNumber) throws SocketException {
		this.routerNumber = BASE_PORT_NUMBER + routerNumber;
		socket = new DatagramSocket(this.routerNumber);
		this.terminal = new Terminal("Router " + routerNumber);
		controllerAddress = new InetSocketAddress(LOCALHOST, BASE_PORT_NUMBER + CONTROLLER_PORT);
		endNodeAddress = null;
		listener.go();
	}

	/* Start the router by sending a Hello packet to the controller.
	*/
	public synchronized void start() {
		sendHello();
	}

	/* Implementation of the abstract function in Node.java. Hands over to
	 * another function based on the source address.
	 */
	@Override
	public synchronized void onReceipt(DatagramPacket packet) {
		if (packet.getSocketAddress().equals(controllerAddress)) {
			handleControllerPacket(packet);
		} else {
			handleEndNodePacket(packet);
		}
	}

	/* Handles a packet sent from a controller.
	*/
	private synchronized void handleControllerPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		switch (getType(data)) {
		// If the packet is a hello, print to the terminal.
		case HELLO:
			terminal.println("The Hello packet is received by the controller.");
			break;
		// If the packet is a flow mod packet, parse the router's flow table from
		// the packet into a two-dimensional array.
		case FLOW_MOD:
			terminal.println("The Flow mod packet is received by the controller.");
			byte[] flatTable = Arrays.copyOfRange(data, 1, data.length);
			updateFlowtable(flatTable);
			packet.setSocketAddress(controllerAddress);
			try {
				socket.send(packet);
				terminal.println("Sent acknowledgment to the controller.");
			} catch (IOException e) {
				e.printStackTrace();
			}
			setEndNodeAddress();
			break;
		// If the packet tells the router to drop the packet, do nothing.
		case FLOW_REMOVED:
			terminal.println("Packet from end node dropped at instruction of controller.");
		}
	}

	/* Update the router's flowtable given a one-dimensional array 
	 * representing it.
	 */
	private synchronized void updateFlowtable(byte[] flatFlowtable) {
		int rowCount;
		for (rowCount = 0; flatFlowtable[rowCount * 5] != 0; rowCount++)
			;
		flowTable = new byte[rowCount][OUTPUT_INDEX + 1];
		int i = 0;
		for (int j = 0; j < flowTable.length; j++) {
			for (int k = 0; k < flowTable[0].length; k++) {
				flowTable[j][k] = flatFlowtable[i];
				i++;
			}
		}
	}

	/* Send a hello packet to the controller.
	*/
	private synchronized void sendHello() {
		byte[] data = { HELLO };
		DatagramPacket hello = new DatagramPacket(data, data.length);
		hello.setSocketAddress(controllerAddress);
		try {
			socket.send(hello);
			terminal.println("Sent a Hello packet to the controller.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method lets the router figure out which end node it is connected to by
	 * checking its flow table. If at any point in the table its immediate input or
	 * output is beyond the router index, then it is an end node. Each router is
	 * connected to a maximum of one node in the system so the check can end once if
	 * the end node is found.
	 */
	private synchronized void setEndNodeAddress() {
		boolean addressSet = false;
		int i = 0;
		while (i < flowTable.length && !addressSet) {
			if (flowTable[i][INPUT_INDEX] > NUM_ROUTERS) {
				int portNumber = flowTable[i][INPUT_INDEX] + BASE_PORT_NUMBER;
				this.endNodeAddress = new InetSocketAddress(LOCALHOST, portNumber);
				addressSet = true;
				terminal.println(
						"This router is connected to end user " + (portNumber - BASE_PORT_NUMBER - NUM_ROUTERS) + ".");
			} else if (flowTable[i][OUTPUT_INDEX] > NUM_ROUTERS) {
				int portNumber = flowTable[i][OUTPUT_INDEX] + BASE_PORT_NUMBER;
				this.endNodeAddress = new InetSocketAddress(LOCALHOST, portNumber);
				addressSet = true;
				terminal.println(
						"This router is connected to end user " + (portNumber - BASE_PORT_NUMBER - NUM_ROUTERS) + ".");
			}
			i++;
		}
		if (!addressSet) {
			terminal.println("This router is not connected to an end node in the network.");
		} else {
			byte[] data = { NODE_INITIALISE_ROUTER };
			DatagramPacket initialisation = new DatagramPacket(data, data.length);
			initialisation.setSocketAddress(endNodeAddress);
			try {
				socket.send(initialisation);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/** Handle a packet not sent by the controller. This is a message meant to be forwared
	  * based on the router's flow table. If the source and destination are not recognised
	  * together in a row of the flow table, the packet is forwarded to the controller to
	  * find out what to do with it. Otherwise the packet is forawrded to the next hop on
	  * the flow table.
	  */
	private synchronized void handleEndNodePacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		if (getType(data) == NODE_MESSAGE) {
			byte nextHop = checkFlowtable(data, packet.getPort());
			if (nextHop == CONTROLLER_PORT) {
				terminal.println("Next hop not in flow table.");
				byte[] unrecognised = new byte[data.length];
				setType(unrecognised, PACKET_IN);
				for (int i = 1; i < unrecognised.length; i++) {
					unrecognised[i] = data[i - 1];
				}
				DatagramPacket packetIn = new DatagramPacket(unrecognised, unrecognised.length);
				packetIn.setSocketAddress(controllerAddress);
				try {
					socket.send(packetIn);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				InetSocketAddress outputAddress = new InetSocketAddress(LOCALHOST, BASE_PORT_NUMBER + nextHop);
				packet.setSocketAddress(outputAddress);
				try {
					socket.send(packet);
					if (nextHop <= NUM_ROUTERS) {
						terminal.println(
								"Packet forwarded to router " + nextHop + ".");
					} else {
						while (nextHop > NUM_ROUTERS) {
							nextHop -= NUM_ROUTERS;
						}
						terminal.println("Packet forwarded to end user " + nextHop + ".");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/* Takes the data from a message and the port number of the previous hop of the packet.
	 * Checks the flowtable for a row with the correct previous hop, source address and 
	 * destination address to return the next hop in the table. If no next hop is found,
	 * the next hop is set as the controller port so that the controller can decide what to
	 * do with the packet.
	 */
	private synchronized byte checkFlowtable(byte[] data, int port) {
		assert (getType(data) == NODE_MESSAGE);
		byte prev = (byte) (port - BASE_PORT_NUMBER);
		if (prev <= NUM_ROUTERS) {
			terminal.println("Received a message from router " + prev + ".");
		} else {
			terminal.println("Received a message from end user " + (prev - NUM_ROUTERS) + ".");
		}
		byte src = getMessageSource(data);
		byte dst = getMessageDest(data);

		int nextHop = CONTROLLER_PORT;
		int i = 0;
		while (i < flowTable.length && nextHop == CONTROLLER_PORT) {
			if (src == flowTable[i][SRC_INDEX] && dst == flowTable[i][DST_INDEX] && prev == flowTable[i][INPUT_INDEX]) {
				nextHop = flowTable[i][OUTPUT_INDEX];
			}
			i++;
		}
		return (byte) nextHop;
	}
}
