import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class Controller extends Node {

	public static final byte R1 = 1;
	public static final byte R2 = 2;
	public static final byte R3 = 3;
	public static final byte R4 = 4;
	public static final byte R5 = 5;
	public static final byte R6 = 6;
	public static final byte R7 = 7;
	public static final byte R8 = 8;
	public static final byte E1 = 9;
	public static final byte E2 = 10;
	public static final byte E3 = 11;
	public static final byte E4 = 12;

	private static Terminal terminal;

	/**
	 * 2D Array storing the preconfiguration information of the controller. Column 0
	 * = source end user, column 1 = destination end user, column 2 = current
	 * router, column 3 = previous router or end user, column 4 = next router or end
	 * user.
	 */
	private static final byte[][] PRECONF_INFO = { { E1, E2, R1, E1, R3 }, { E1, E2, R3, R1, R6 },
			{ E1, E2, R6, R3, R8 }, { E1, E2, R8, R6, E2 }, { E2, E1, R8, E2, R7 }, { E2, E1, R7, R8, R5 },
			{ E2, E1, R5, R7, R2 }, { E2, E1, R2, R5, R1 }, { E2, E1, R1, R2, E1 }, { E1, E3, R1, E1, R4 },
			{ E1, E3, R4, R1, R6 }, { E1, E3, R6, R4, E3 }, { E3, E1, R6, E3, R4 }, { E3, E1, R4, R6, R1 },
			{ E3, E1, R1, R4, E1 }, { E1, E4, R1, E1, R4 }, { E1, E4, R4, R1, R7 }, { E1, E4, R7, R4, E4 },
			{ E4, E1, R7, E4, R4 }, { E4, E1, R4, R6, R1 }, { E4, E1, R1, R4, E1 }, { E2, E3, R8, E2, R6 },
			{ E2, E3, R6, R8, E3 }, { E3, E2, R6, E3, R8 }, { E3, E2, R8, R6, E2 }, { E2, E4, R8, E2, R7 },
			{ E2, E4, R7, R8, E4 }, { E4, E2, R7, E4, R8 }, { E4, E2, R8, R7, E2 }, { E3, E4, R6, E3, R4 },
			{ E3, E4, R4, R6, R7 }, { E3, E4, R7, R4, E4 }, { E4, E3, R7, E4, R4 }, { E4, E3, R4, R7, R6 },
			{ E4, E3, R6, R4, E3 } };

	/* Arrays to store the other nodes once they are initialised. */
	private Router[] routers;
	private EndUser[] endNodes;

	/* Contruct a new Controller and start its functionality. */
	public static void main(String[] args) {
		try {
			terminal = new Terminal("Controller");
			(new Controller(terminal)).start();
		} catch (java.lang.Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * Initialises the Controller as well as each of the routers and end nodes in
	 * the network. Passed the terminal that it will use for output.
	 */
	Controller(Terminal terminal) throws SocketException {
		// Initialise Controller
		Controller.terminal = terminal;
		this.socket = new DatagramSocket(BASE_PORT_NUMBER + CONTROLLER_PORT);
		listener.go();
		// Initialise routers
		routers = new Router[NUM_ROUTERS + 1];
		for (byte i = R1; i <= NUM_ROUTERS; i++) {
			routers[i] = new Router(i);
		}
		// Initialise EndNodes
		endNodes = new EndUser[NUM_END_NODES];
		for (byte j = 0; j < NUM_END_NODES; j++) {
			endNodes[j] = new EndUser((byte) (j + NUM_ROUTERS + 1));
		}
	}

	/*
	 * Start the initial router. Once the first router has finished setup the second
	 * router will start, and so on.
	 */
	public synchronized void start() throws Exception {
		startRouter(R1);
	}

	/*
	 * Call the start method of the next router, beginning its setup sequence where
	 * the router will send a hello packet.
	 */
	private synchronized void startRouter(int routerNumber) {
		Router s = routers[routerNumber];
		s.start();
	}

	/*
	 * Send the relevant flow table to the relevant router. Parses out the relevant
	 * information from the preconfiguration information into a two-dimensional
	 * array. Converts this into a one-dimensional array which can be encapsulated
	 * into a Datagram Packet to send to the router.
	 */
	private synchronized void sendTable(byte routerNumber) {
		// create a new flow mod table; the row corresponds to the router number
		byte[] table = new byte[PRECONF_INFO.length * PRECONF_INFO[0].length];
		for (int i = 0; i < PRECONF_INFO.length; i++) {
			if (routerNumber == PRECONF_INFO[i][ROUTER_INDEX]) {
				int j = 0;
				while (table[j] != 0) {
					j++; // set j to an index where the value is zero
				}
				for (int k = 0; k <= OUTPUT_INDEX; k++) {
					table[j] = PRECONF_INFO[i][k];
					j++;
				}
			}
		}
		// It is a 2D array. The first row is full of zeros. The other
		// rows correspond to router numbers and contain the tables that should
		// be sent in a packet.
		byte[] flowTable = new byte[table.length + 1];
		// set the first byte of the flowTable to be the type, a FLOW_MOD
		// packet
		for (int i = 0; i < table.length; i++) {
			flowTable[i + 1] = table[i];
		}
		flowTable[0] = FLOW_MOD;
		DatagramPacket packet = new DatagramPacket(flowTable, flowTable.length);
		InetSocketAddress dstAddress = new InetSocketAddress(LOCALHOST, BASE_PORT_NUMBER + routerNumber);
		packet.setSocketAddress(dstAddress);
		try {
			this.socket.send(packet); // send the flow table to the router
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Send a packet of type OPFT_HELLO when passed the destination address.
	 */
	private synchronized void sendHello(InetSocketAddress dstAddress) {
		byte[] data = { HELLO };
		DatagramPacket hello = new DatagramPacket(data, data.length);
		hello.setSocketAddress(dstAddress);
		try {
			socket.send(hello);
			int port = dstAddress.getPort() - BASE_PORT_NUMBER;
			terminal.println("Sent a Hello packet to router " + port + ".");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Implementation of the abstract class in Node.java which handles received
	 * Datagram Packets.
	 */
	@Override
	public synchronized void onReceipt(DatagramPacket packet) {
		byte[] data = packet.getData();
		int port = packet.getPort() - BASE_PORT_NUMBER;
		byte type = getType(data);
		switch (type) {
		// Handle a Hello packet by replying with a Hello.
		case HELLO:
			terminal.println("Got a Hello packet from router " + port + ".");
			sendHello((InetSocketAddress) packet.getSocketAddress());
			sendTable((byte) port);
			break;
		// Handle a confirmation of completion of flow mod as the end of the setup
		// sequence for that router. Start the next swtich.
		case FLOW_MOD:
			terminal.println("Flow mod acknowledged by router " + port + ".");
			if (port < NUM_ROUTERS) {
				startRouter(port + 1); // flow mod is complete, start the next router
			}
			break;
		// Handle an unrecognised packet forwarded by a router by telling that router to
		// drop the packet.
		case PACKET_IN:
			setType(data, FLOW_REMOVED);
			packet.setData(data);
			packet.setSocketAddress(packet.getSocketAddress());
			try {
				socket.send(packet);
				terminal.println("Told router " + port + " to drop packet.");
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		}
	}

}
