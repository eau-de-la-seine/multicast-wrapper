package fr.ekinci.multicastwrapper.utils;

import lombok.Getter;

import java.io.IOException;
import java.io.Serializable;


/**
 * A simple POJO container which Base64 encoded method arguments 
 * 
 * 
 * @author Gokan EKINCI
 */
@Getter
public class TypeAndValue implements Serializable {
	private final String stringClassName;
	private final String stringValue;

	public <T> TypeAndValue(Class<? super T> superClass, T value) throws IOException{
		stringClassName = superClass.getCanonicalName();
		stringValue = SlaveInvoker.encodeBase64(value);
	}

	public Object getObjectValue() throws ClassNotFoundException, IOException{
		return SlaveInvoker.decodeBase64(stringValue);
	}

	public Class<?> getObjectClass() throws ClassNotFoundException {
		return Class.forName(stringClassName);
	}
}
