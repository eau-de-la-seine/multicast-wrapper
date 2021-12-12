package fr.ekinci.multicastwrapper.utils;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import fr.ekinci.multicastwrapper.MulticastBase;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A class which invoke a method in other nodes
 * This class is used for Replication in a Clustered environment
 * 
 * @author Gokan EKINCI
 */
@Slf4j
@AllArgsConstructor
public class SlaveInvoker {
	private final static Gson gson = new Gson();
	private final MulticastBase multicast;

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
		log.debug("invokeInSlaves className        : " + clazz.getName());
		log.debug("invokeInSlaves calledMethodName : " + calledMethodName);
		log.debug("invokeInSlaves args             : " + args);

		MulticastActionMessage message = new MulticastActionMessage();
		message.setExecuteClass(clazz.getName());
		message.setExecuteMethod(calledMethodName);
		message.setStringifiedTypeAndValueArray(encodeBase64(args));

		multicast.sendMessage(gson.toJson(message).getBytes(StandardCharsets.UTF_8));
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
		TypeAndValue[] stringifiedTypeAndValueArray = decodeBase64(message.getStringifiedTypeAndValueArray()); // 3

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
		try (JsonReader jsonreader = new JsonReader(new StringReader(json))) {
			jsonreader.setLenient(true);
			return gson.fromJson(jsonreader, clazz);
		}
	}

	/**
	 * Object to Base64 string
	 * @throws IOException
	 */
	static String encodeBase64(Object object) throws IOException {
		try (
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos)
		) {
			oos.writeObject(object);
			return Base64.getEncoder().encodeToString(baos.toByteArray());
		}
	}

	/**
	 * Base64 string to Object
	 * @param stringifiedObject
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	static <T> T decodeBase64(String stringifiedObject)
			throws IOException, ClassNotFoundException {
		byte[] data = Base64.getDecoder().decode(stringifiedObject);
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
			return (T) ois.readObject();
		}
	}
}