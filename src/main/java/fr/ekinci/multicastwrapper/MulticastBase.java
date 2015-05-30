package fr.ekinci.multicastwrapper;

import java.io.IOException;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Enumeration;
import org.apache.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;


/**
 * A Multicast wrapper class for sending and receiving {@link MulticastActionMessage} messages
 * in a Clustered environment.
 * 
 * You can easily create your own class and extends from this class.
 * 
 * Note : This class uses {@link java.nio.channels.DatagramChannel} (not {@link java.nio.channels.MulticastChannel} )
 * Reminder: This class may not work correctly if you are NOT working in a LAN (Local Area Network)
 * 
 * 
 * @author Gokan EKINCI
 */
public class MulticastBase {
    private final static Logger LOG = Logger.getLogger(MulticastBase.class);
    
    protected final static Gson gson = new Gson();
    
    /**
     * Max size for UDP Datagram is 65535
     * Be careful if :
     * - Your are using a router and it has a limitation for packets size
     * - Your message is too large
     */
    protected final static int RECEIVED_MESSAGE_MAX_SIZE = 65_535;
    
    protected Thread currentMessageListenerThread;
    protected volatile boolean continueLoopInThread;

    // /** The datagram Packet<br>Must be the same http://stackoverflow.com/a/7469403 */
    // protected DatagramPacket datagramPacketReceive;
    
    /** MulticastChannel and MembershipKey*/
    protected final DatagramChannel dc;
    protected final MembershipKey key;
    
    /** Network interface name (ex: eth0) and other parameters */
    protected final String currentMachineNetworkInterfaceName; // example: "eth0";
    protected final String multicastVirtualGroupIpV4Address;   // example: "224.1.1.1";
    protected final int    multicastVirtualGroupPort;          // example: 1234;
    protected final String currentMachineType;                 // Receiver's type  
    
    /** Other attributes produced in the constructor */
    protected final String ipV4AddressOfThisMachine; 
    protected final InetSocketAddress multicastSocketAddress;
    protected final String subClassName;
    
    /**
     * Create the multicast base object
     * 
     * @param currentMachineType (ex : "server" or "client")
     * @throws IOException
     */
    public MulticastBase(
            String currentMachineNetworkInterfaceName,
            String multicastVirtualGroupIpV4Address, 
            int multicastVirtualGroupPort,
            String currentMachineType
    ) throws IOException, SocketException, UnknownHostException { 
        this.currentMachineNetworkInterfaceName = currentMachineNetworkInterfaceName;
        this.multicastVirtualGroupIpV4Address = multicastVirtualGroupIpV4Address;
        this.multicastVirtualGroupPort = multicastVirtualGroupPort;
        this.currentMachineType = currentMachineType;
        this.continueLoopInThread = true;
        
        // init instanciated simple name :
        subClassName = getClass().getSimpleName();
        
        // Current machine ip address
        NetworkInterface currentMachineNetworkInterface = NetworkInterface.getByName(currentMachineNetworkInterfaceName);
        ipV4AddressOfThisMachine = getIpV4AddressFromNetworkInterface(currentMachineNetworkInterface);
               
        // Multicast Socket Address
        InetAddress multicastVirtualGroupInetAddress = InetAddress.getByName(multicastVirtualGroupIpV4Address);
        multicastSocketAddress = new InetSocketAddress(multicastVirtualGroupInetAddress, multicastVirtualGroupPort);
        
        // DatagramChannel initialization
        dc = DatagramChannel.open(StandardProtocolFamily.INET)
            .setOption(StandardSocketOptions.SO_REUSEADDR, true)
            .bind(new InetSocketAddress(multicastVirtualGroupPort))
            .setOption(StandardSocketOptions.IP_MULTICAST_IF, currentMachineNetworkInterface);
        
        // Multicast join
        key = dc.join(multicastVirtualGroupInetAddress, currentMachineNetworkInterface);
        LOG.debug("Current machine : " + ipV4AddressOfThisMachine + " has joined multicast ! \n"
        		+ subClassName + " with identityHashCode: " + System.identityHashCode(this) + " has been instanciated");
    }
    
    
    /**
     * Send message to the group
     * 
     * @param messageToSend The message to be sent
     * @throws IOException
     */
    public void sendMessage(MulticastActionMessage messageToSend) throws IOException {
        messageToSend.setMachineType(currentMachineType);
        messageToSend.setMachineIp(ipV4AddressOfThisMachine);
        String jsonMessageToSend = gson.toJson(messageToSend, MulticastActionMessage.class);
        
        LOG.debug(subClassName + " : Current machine type : " + messageToSend.getMachineType() + " with IP " + messageToSend.getMachineIp()
                + "\nSerialized JSON before send :\n" + jsonMessageToSend);
        ByteBuffer byteBuffer = ByteBuffer.wrap(jsonMessageToSend.getBytes());
        dc.send(byteBuffer, multicastSocketAddress);
        LOG.debug("Serialized JSON has been sent.");
    }
    
    
    /**
     * Send message to the group and wait time out
     * 
     * @param messageToSend
     * @param timeOut in milliseconds
     * @throws IOException
     */
    public void sendMessage(MulticastActionMessage messageToSend, long timeOut) throws IOException {
        sendMessage(messageToSend);
        try {
            Thread.sleep(timeOut);
        } catch (InterruptedException e) {
            LOG.error("Error sleep() in Multicastbase.sendMessage", e);
        }
    }
	
    
    /**
     * Receive asynchrounous messages (infinite loop)
     * Note : Inspired by myButton.addActionListener(ActionListener a)
     * 
     * @param messageListener
     */
    public void addMessageListener(final ActionMessageListener messageListener){ 
        Runnable run = new Runnable(){
            @Override
            public void run() {
                try {
                    
                    while(continueLoopInThread){
                        LOG.debug(subClassName + " is waiting for receiving datagram");
                        ByteBuffer receivedByteBuffer = ByteBuffer.allocate(RECEIVED_MESSAGE_MAX_SIZE);
                        InetSocketAddress senderAddress = (InetSocketAddress) dc.receive(receivedByteBuffer); // waiting for datagram and fill receivedByteBuffer
                        String receivedJsonMessage = byteBufferToString(receivedByteBuffer);                        
                        String datagramSendersIp = senderAddress.getAddress().toString().replace("/", "");

                        LOG.debug(subClassName + " has received a new message :"
                        + "\n    Sender's datagram address  : " + datagramSendersIp
                        + "\n    Local address              : " + ipV4AddressOfThisMachine
                        + "\n    Is same machine            : " + datagramSendersIp.equals(ipV4AddressOfThisMachine)
                        + "\n    Unserialized JSON received : " + receivedJsonMessage
                        + "\nEnd Test");
                        
                        // ignore packet sent from myself
                        if(!datagramSendersIp.equals(ipV4AddressOfThisMachine)){                             
                            try {                                                
                                MulticastActionMessage response = fromLenientJson(receivedJsonMessage, MulticastActionMessage.class);                                
                                messageListener.onMessage(response); // Action inside depends if it's a server or client instance
                            } catch(com.google.gson.JsonSyntaxException e){
                                LOG.error("Error during Json unmarshalling", e);
                            }
                        } // End of conditional block "ignore packaet sent from myself"
                    } // End of while(continueLoopInThread)
                    
                } catch (IOException e) {
                    LOG.error("", e);
                }   
            }
        };
        
        currentMessageListenerThread = new Thread(run);
        currentMessageListenerThread.start(); 
    }
    
    public String getIpV4AddressOfThisMachine(){
        return ipV4AddressOfThisMachine;
    }
    
    
    /* *** UTIL METHODS *** */
    
    /**
     * Get ip v4 address from network interface
     * @param networkInterface
     * @return
     * @throws SocketException
     */
    public static String getIpV4AddressFromNetworkInterface(NetworkInterface networkInterface) throws SocketException {
        String ip = null;
        if(networkInterface.isUp()){
            Enumeration<InetAddress> e = networkInterface.getInetAddresses();
            while(e.hasMoreElements()) {
                InetAddress ia = e.nextElement();
                if(ia instanceof Inet4Address){
                    ip = ia.getHostAddress();
                    break;
                }
            }
        }

        return ip;
    }
    
    
    /**
     * Util method for converting {@link ByteBuffer} to String
     * 
     * @param byteBuffer
     * @return
     */
    public static String byteBufferToString(ByteBuffer byteBuffer){
        byteBuffer.flip();
        byte[] receivedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(receivedBytes);
        return new String(receivedBytes);
    }
    
    
    /**
     * Get object from json chain with Lenient mod for avoiding network character problems
     * 
     * @param json
     * @param clazz
     * @return
     * @throws IOException  
     */
    public static <T> T fromLenientJson(String json, Class<T> clazz) 
        throws IOException, JsonIOException, JsonSyntaxException {
        
        // Using JsonReader and Lenient for avoiding network character problems
        JsonReader jsonreader = new JsonReader(new StringReader(json));
        jsonreader.setLenient(true);
        T response = gson.fromJson(jsonreader, clazz);
        jsonreader.close();
        
        return response;
    }
    
    
    /**
     * Destroy the {@link DatagramChannel} if current instance dies
     */
    @Override
    public void finalize(){
        continueLoopInThread = false;
        
        try {
            dc.close();
        } catch (IOException e) {
            LOG.error("Error during closing DatagramChannel in MulticastBase.finalize()", e);
        }

        LOG.debug(getClass().getName() + " with identityHashCode: " + System.identityHashCode(this) + " has been destroyed");
    }
}
