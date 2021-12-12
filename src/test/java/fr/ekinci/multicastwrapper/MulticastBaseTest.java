package fr.ekinci.multicastwrapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


class MulticastBaseTest {
	static final String MULTICAST_IP = "224.0.0.1";
	static final int MULTICAST_PORT = 14725;
	final byte[] messageToSend = new byte[]{0x41, 0x42, 0x43};

	@Test
	void listNetworkInterfaces_nominal() throws SocketException {
		// WHEN
		List<NetworkInterface> networkInterfaces = MulticastBase.listNetworkInterfaces();

		// THEN
		assertThat(networkInterfaces).isNotNull();
	}

	@Test
	void launchConsumer_sendMessage_nominal() throws IOException {
		// GIVEN
		boolean ipMulticastLoop = true;
		List<byte[]> container = new ArrayList<>();
		NetworkInterface networkInterface = MulticastBase.listNetworkInterfaces()
			.stream()
			.findAny()
			.orElseThrow(() -> new RuntimeException("networkInterface is empty!"));
		try (MulticastBase multicastBase = new MulticastBase(
				networkInterface,
				MULTICAST_IP,
				MULTICAST_PORT,
				container::add,
				ipMulticastLoop)) {
			// WHEN
			multicastBase.launchConsumer();
			multicastBase.sendMessage(messageToSend);

			// THEN
			await().atMost(5, SECONDS).until(() -> !container.isEmpty());
			assertThat(container).hasSize(1);
			byte[] message = container.get(0);
			assertThat(message).isNotNull();
			assertThat(message.length).isEqualTo(3);
			assertThat(message).isEqualTo(messageToSend);
		}
	}

	@Test
	void hasInetAddress_nominal() throws SocketException {
		// GIVEN
		List<NetworkInterface> networkInterfaces = MulticastBase.listNetworkInterfaces();

		// Test hasInetAddress = true
		if (networkInterfaces.size() >= 1) {
			extractFistValidNetworkInterface(networkInterfaces)
				.ifPresent(networkInterface -> {
					extractFirstInetAddress(networkInterface)
						.ifPresent(supposedlyValidInetAddress -> {
							assertThat(MulticastBase.hasInetAddress(networkInterface, supposedlyValidInetAddress)).isTrue();
						});
				});
		}

		// Test hasInetAddress = false
		if (networkInterfaces.size() >= 2) {
			extractFistValidNetworkInterface(networkInterfaces)
				.ifPresent(networkInterface1 -> {
					// Reverse order for getting the last network interface of the list
					Collections.reverse(networkInterfaces);
					extractFistValidNetworkInterface(networkInterfaces)
						.ifPresent(networkInterface2 -> {
							if (!networkInterface1.equals(networkInterface2)) {
								extractFirstInetAddress(networkInterface2)
									.ifPresent(supposedlyInvalidInetAddress -> {
										assertThat(MulticastBase.hasInetAddress(networkInterface1, supposedlyInvalidInetAddress)).isFalse();
									});
							}
						});
				});
		}
	}

	private Optional<NetworkInterface> extractFistValidNetworkInterface(List<NetworkInterface> networkInterfaces) {
		return networkInterfaces.stream()
			.filter(networkInterface -> networkInterface.inetAddresses().count() > 1L)
			.findFirst();
	}

	private Optional<InetAddress> extractFirstInetAddress(NetworkInterface networkInterface) {
		return networkInterface.inetAddresses().findFirst();
	}
}