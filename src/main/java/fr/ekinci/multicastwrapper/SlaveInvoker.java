package fr.ekinci.multicastwrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;



/**
 * A class which invoke a method in other nodes
 * This class is used for Replication in a Clustered environment
 * 
 * @author Gokan EKINCI
 */
public class SlaveInvoker {
	private final static Logger LOG = Logger.getLogger(SlaveInvoker.class);
    private final MulticastBase multicast;
    
    public SlaveInvoker(MulticastBase multicast){
    	this.multicast = multicast;
    }
     
    
    /** 
     * Object to Base64 string 
     * @throws IOException 
     */
    public static String encodeBase64(Object object) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        return new String(Base64.encodeBase64(baos.toByteArray())); 
    }
    
    /**
     * Base64 string to Object
     * @param stringifiedObject
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object decodeBase64(String stringifiedObject) 
        throws IOException, ClassNotFoundException {
        byte[] data = Base64.decodeBase64(stringifiedObject);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object result = ois.readObject();
        ois.close();
        return result;
    }
    
    
    /**
     * When a master invoke a method for replicating in slaves
     * This method is executed in a MASTER node
     * 
     * @param clazz
     * @param calledMethodName
     * @param args
     * @throws IOException
     */
    public void invokeInSlaves(Class<?> clazz, String calledMethodName, TypeAndValue... args) 
            throws IOException {
        LOG.debug("invokeInSlaves className        : " + clazz.getName());
        LOG.debug("invokeInSlaves calledMethodName : " + calledMethodName);
        LOG.debug("invokeInSlaves args             : " + args);
        
        MulticastActionMessage message = new MulticastActionMessage();
        message.setExecuteClass(clazz.getName());
        message.setExecuteMethod(calledMethodName);
        message.setStringifiedTypeAndValueArray(encodeBase64(args));
        
        multicast.sendMessage(message);
    }
    
    
    /**
     * When another server send an operation, this method receives it and execute its content
     * This method is executed in a SLAVE node
     * 
     * @param objectToInvoke
     * @param message
     * @return
     * @throws ClassNotFoundException 
     * @throws IOException 
     * @throws SecurityException 
     * @throws NoSuchMethodException 
     * @throws InvocationTargetException 
     * @throws IllegalArgumentException 
     * @throws IllegalAccessException 
     */
    public static <T> Object executeReceivedReplicationMessage(T objectToInvoke, MulticastActionMessage message) throws ClassNotFoundException, IOException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        
        // Class to call (1) | Method name to call (2) | Method arguments types and values (3)
        Class<?> classToCall = Class.forName(message.getExecuteClass()); // 1
        String methodToCallName = message.getExecuteMethod();            // 2
        
        
        TypeAndValue[] stringifiedTypeAndValueArray = 
            (TypeAndValue[]) decodeBase64(message.getStringifiedTypeAndValueArray()); // 3
        
        // Types 
        Class<?>[] objectClasses = new Class<?>[stringifiedTypeAndValueArray.length];
        for(int i = 0; i < stringifiedTypeAndValueArray.length; i++){
            objectClasses[i] = stringifiedTypeAndValueArray[i].getObjectClass();
        }
        
        // Values
        Object[] objectValues = new Object[stringifiedTypeAndValueArray.length];
        for(int i = 0; i < stringifiedTypeAndValueArray.length; i++){
            objectValues[i] = stringifiedTypeAndValueArray[i].getObjectValue();
        }
        
        // Get method and invoke it
        Method method = classToCall.getMethod(methodToCallName, objectClasses); 
        return method.invoke(objectToInvoke, objectValues);
    }
}
