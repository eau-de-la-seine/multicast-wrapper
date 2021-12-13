package fr.ekinci.multicastwrapper;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


/**
 * A Multicast wrapper class for sending and receiving messages in a Clustered environment.
 * 
 * You can easily create your own class and extends from this class.
 * 
 * Note : This class uses {@link java.nio.channels.DatagramChannel} (not {@link java.nio.channels.MulticastChannel} )
 * Reminder: This class may not work correctly if you are NOT working in a LAN (Local Area Network)
 * 
 * 
 * @author Gokan EKINCI
 */
@Slf4j
public class MulticastBase implements AutoCloseable {

	/**
	 * Max size for UDP Datagram is 65_507 (65_535 bytes - 8-byte UDP header - 20-byte IP header)
	 * Be careful if :
	 * - You are using a router and it has a limitation for packets size
	 * - Your message is too large
	 */
	protected final static int RECEIVED_MESSAGE_MAX_SIZE = 65_507;

	protected final Thread currentMessageListenerThread;
	protected volatile boolean continueLoopInThread;

	/** MulticastChannel and MembershipKey*/
	protected final DatagramChannel dc;
	protected final MembershipKey key;

	/** Network interface name (ex: eth0) and other parameters */
	protected final NetworkInterface currentMachineNetworkInterface; // example: "eth0";
	protected final String multicastVirtualGroupIpAddress;           // example: "224.1.1.1";
	protected final int multicastVirtualGroupPort;                   // example: 1234;

	/** Other attributes produced in the constructor */
	protected final InetSocketAddress multicastSocketAddress;
	protected final String implClassName;

	/**
	 * Create the multicast base object
	 *
	 * @param currentMachineNetworkInterface A valid NetworkInterface for multicasting
	 * @param multicastVirtualGroupIpAddress Multicast Virtual Group IP Address
	 * @param multicastVirtualGroupPort      Multicast Virtual Group Port
	 * @param consumerCallback               Consumer
	 * @param ipMulticastLoop                Accept messages from current machine (set to false for ignoring message from myself)
	 * @throws IOException                   If an error happens
	 */
	public MulticastBase(
		NetworkInterface currentMachineNetworkInterface,
		String multicastVirtualGroupIpAddress,
		int multicastVirtualGroupPort,
		Consumer<byte[]> consumerCallback,
		boolean ipMulticastLoop
	) throws IOException {
		this.currentMachineNetworkInterface = checkNotNull(currentMachineNetworkInterface, "currentMachineNetworkInterface");
		this.multicastVirtualGroupIpAddress = checkNotNull(multicastVirtualGroupIpAddress, "multicastVirtualGroupIpAddress");
		this.multicastVirtualGroupPort = checkPort(multicastVirtualGroupPort);
		this.currentMessageListenerThread = nonNull(consumerCallback) ?
			new Thread(() -> multicastConsumerLoop(consumerCallback)) : null;
		this.continueLoopInThread = true;

		// init instanciated simple name :
		implClassName = getClass().getSimpleName();

		// Multicast Socket Address
		InetAddress multicastVirtualGroupInetAddress = InetAddress.getByName(multicastVirtualGroupIpAddress);
		multicastSocketAddress = new InetSocketAddress(multicastVirtualGroupInetAddress, multicastVirtualGroupPort);

		// DatagramChannel initialization
		dc = createMulticastDatagramChannel(multicastVirtualGroupPort, currentMachineNetworkInterface, ipMulticastLoop);

		// Multicast join
		key = dc.join(multicastVirtualGroupInetAddress, currentMachineNetworkInterface);
		log.debug("Current machine '{}' has joined multicast! {} with identityHashCode: '{}' has been instanciated",
			currentMachineNetworkInterface.getName(),
			implClassName,
			System.identityHashCode(this));
	}

	private <T> T checkNotNull(T param, String name) {
		if (isNull(param)) {
			throw new IllegalArgumentException(String.format("'%s' parameter must not be null", name));
		}

		return param;
	}

	private int checkPort(int port) {
		if (port < 0 || port > 0xFFFF) {
			throw new IllegalArgumentException("'multicastVirtualGroupPort' is out of range: " + port);
		}

		return port;
	}

	private DatagramChannel createMulticastDatagramChannel(
		int multicastVirtualGroupPort,
		NetworkInterface currentMachineNetworkInterface,
		boolean ipMulticastLoop) throws IOException {
		return DatagramChannel.open(StandardProtocolFamily.INET)
			.bind(new InetSocketAddress(multicastVirtualGroupPort))
			.setOption(StandardSocketOptions.SO_REUSEADDR, true)
			.setOption(StandardSocketOptions.IP_MULTICAST_IF, currentMachineNetworkInterface)
			.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, ipMulticastLoop);
	}

	/**
	 * Receive asynchrounous messages (infinite loop)
	 */
	public void launchConsumer() {
		if (isNull(currentMessageListenerThread)) {
			throw new IllegalCallerException("You cannot launchConsumer() if you have not initialized the consumer callback");
		}
		currentMessageListenerThread.start();
	}

	/**
	 * Send message to the group
	 *
	 * @param message The message to be sent
	 */
	@SneakyThrows
	public void sendMessage(byte[] message) {
		checkMessage(message);
		log.debug("{}: Current machine '{}'. Number of bytes to send: {}",
			implClassName,
			currentMachineNetworkInterface.getName(),
			message.length);
		ByteBuffer byteBuffer = ByteBuffer.wrap(message);
		dc.send(byteBuffer, multicastSocketAddress);
	}

	private void checkMessage(byte[] message) {
		checkNotNull(message, "message");

		if (message.length > RECEIVED_MESSAGE_MAX_SIZE) {
			throw new IllegalArgumentException("message's length must be less than or equal " + RECEIVED_MESSAGE_MAX_SIZE + " bytes");
		}
	}

	private void multicastConsumerLoop(Consumer<byte[]> consumerCallback) {
		try {
			while (continueLoopInThread) {
				log.debug("{} is waiting for receiving datagram", implClassName);
				ByteBuffer receivedByteBuffer = ByteBuffer.allocate(RECEIVED_MESSAGE_MAX_SIZE);

				// waiting for datagram and fill receivedByteBuffer
				Optional<InetAddress> datagramSendersIp = Optional.ofNullable(dc.receive(receivedByteBuffer))
					.map(InetSocketAddress.class::cast)
					.map(InetSocketAddress::getAddress);

				log.debug("{} has received a new message from '{}' on network interface '{}'",
					implClassName,
					datagramSendersIp,
					currentMachineNetworkInterface.getName());

				consumeReceivedMessage(consumerCallback, receivedByteBuffer);
			}
		} catch (IOException e) {
			log.error("", e);
		}
	}

	private void consumeReceivedMessage(Consumer<byte[]> consumerCallback, ByteBuffer receivedByteBuffer) {
		byte[] message = new byte[receivedByteBuffer.position()];
		receivedByteBuffer.rewind();
		receivedByteBuffer.get(message);
		consumerCallback.accept(message);
	}

	@Override
	public void close() {
		continueLoopInThread = false;

		try {
			dc.close();
		} catch (IOException e) {
			log.error("Error during closing DatagramChannel in MulticastBase#close()", e);
		}

		log.debug("{} with identityHashCode '{}' has been destroyed",
			implClassName,
			System.identityHashCode(this));
	}

	/* *** UTIL METHODS *** */

	/**
	 * @return                 A list of valid network interfaces for multicasting
	 * @throws SocketException If an error happens
	 */
	public static List<NetworkInterface> listNetworkInterfaces() throws SocketException {
		return NetworkInterface.networkInterfaces()
			.filter(MulticastBase::isValidNetworkInterface)
			.toList();
	}

	private static boolean isValidNetworkInterface(NetworkInterface networkInterface) {
		try {
			return networkInterface.isUp()
				&& networkInterface.supportsMulticast()
				&& hasIp4(networkInterface);
		} catch (SocketException e) {
			log.error("An error happened while checking NetworkInterface: '{}'", networkInterface, e);
			return false;
		}
	}

	private static boolean hasIp4(NetworkInterface networkInterface) {
		return networkInterface.inetAddresses()
			.anyMatch(inetAddress -> inetAddress instanceof Inet4Address);
	}

	/**
	 * @param networkInterface Your network interface
	 * @param inetAddress      Potentially remote Inet4Address or Inet6Address
	 * @return                 true if networkInterface has the given inetAddress, false otherwise
	 */
	public static boolean hasInetAddress(NetworkInterface networkInterface, InetAddress inetAddress) {
		return networkInterface.inetAddresses()
			.anyMatch(ia -> ia.equals(inetAddress));
	}
}