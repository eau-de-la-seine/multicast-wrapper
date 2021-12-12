package fr.ekinci.multicastwrapper.utils;

import lombok.Data;

import java.io.Serializable;


/**
 * Simple POJO class for Multicast Message
 * 
 * @author Gokan EKINCI
 */
@Data
public class MulticastActionMessage implements Serializable {    

	/** Type of the machine that sends the Message (ex : "server" or "client"). */
	private String machineType;

	/** Category of the machine that sends the message (ex: Master, Slave, etc) */
	private String machineCategory;

	/** IP of machine that sends the Message. */
	private String machineIp;

	/**
	 * A JSON content, may contain value of executeClass
	 * This attribute is NOT used for Replication {@link SlaveInvoker}
	 */
	private String jsonValue;


	/* FOR REPLICATION, READ BOTTOM ATTRIBUTES */

	/** The name of the class that contains the method to be executed. */
	private String executeClass;

	/**
	 * The name of the method to be executed.
	 * This attribute is used for Replication {@link SlaveInvoker}
	 */
	private String executeMethod;

	/**
	 * The method's arguments in Json.
	 * This attribute is used for Replication {@link SlaveInvoker}
	 */
	private String stringifiedTypeAndValueArray;
}
