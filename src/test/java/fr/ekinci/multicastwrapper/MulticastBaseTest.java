package fr.ekinci.multicastwrapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static fr.ekinci.multicastwrapper.MulticastBase.RECEIVED_MESSAGE_MAX_SIZE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
					Collections.reverse(new ArrayList<>(networkInterfaces));
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

	@Nested
	class CheckMandatoryParametersTest {
		@Test
		void check_NetworkInterface_parameter() {
			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> {
					new MulticastBase(
						null,
						"",
						0,
						null,
						false);
				}).withMessage("'currentMachineNetworkInterface' parameter must not be null");
		}

		@Test
		void check_MulticastVirtualGroupIpAddress_parameter() throws SocketException {
			Optional<NetworkInterface> networkInterface = NetworkInterface.networkInterfaces().findAny();
			if (networkInterface.isEmpty()) {
				return;
			}

			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> {
					new MulticastBase(
						networkInterface.get(),
						null,
						0,
						null,
						false);
				}).withMessage("'multicastVirtualGroupIpAddress' parameter must not be null");
		}

		@Test
		void check_MulticastVirtualGroupPort_parameter() throws SocketException {
			Optional<NetworkInterface> networkInterface = NetworkInterface.networkInterfaces().findAny();
			if (networkInterface.isEmpty()) {
				return;
			}

			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> {
					new MulticastBase(
						networkInterface.get(),
						MULTICAST_IP,
						-1,
						null,
						false);
				}).withMessage("'multicastVirtualGroupPort' is out of range: -1");
		}
	}

	@Nested
	class CheckSendMessageParametersTest {
		@Test
		void sendMessage_error_when_given_message_is_null() throws IOException {
			// GIVEN
			NetworkInterface networkInterface = MulticastBase.listNetworkInterfaces()
				.stream()
				.findAny()
				.orElseThrow(() -> new RuntimeException("networkInterface is empty!"));

			try (MulticastBase multicastBase = new MulticastBase(
				networkInterface,
				MULTICAST_IP,
				MULTICAST_PORT,
				null,
				false)) {
				// WHEN
				assertThatExceptionOfType(IllegalArgumentException.class)
					.isThrownBy(() -> {
						multicastBase.sendMessage(null);
					}).withMessage("'message' parameter must not be null");
				multicastBase.sendMessage(messageToSend);
			}
		}

		@Test
		void sendMessage_error_when_given_message_is_out_of_range() throws IOException {
			// GIVEN
			byte[] okLengthMessage = new byte[RECEIVED_MESSAGE_MAX_SIZE];
			byte[] bigLengthMessage = new byte[RECEIVED_MESSAGE_MAX_SIZE + 1];

			NetworkInterface networkInterface = MulticastBase.listNetworkInterfaces()
				.stream()
				.findAny()
				.orElseThrow(() -> new RuntimeException("networkInterface is empty!"));

			try (MulticastBase multicastBase = new MulticastBase(
				networkInterface,
				MULTICAST_IP,
				MULTICAST_PORT,
				null,
				false)) {
				// WHEN
				multicastBase.sendMessage(okLengthMessage);
				assertThatExceptionOfType(IllegalArgumentException.class)
					.isThrownBy(() -> {
						multicastBase.sendMessage(bigLengthMessage);
					}).withMessage("message's length must be less than or equal 65507 bytes");
				multicastBase.sendMessage(messageToSend);
			}
		}
	}
}