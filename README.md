# multicastwrapper

A Multicast wrapper for using multicast in a clustered environment

```
final String MULTICAST_IP = "224.0.0.1";
final int MULTICAST_PORT = 14725;
final byte[] messageToSend = new byte[]{0x41, 0x42, 0x43};

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
```