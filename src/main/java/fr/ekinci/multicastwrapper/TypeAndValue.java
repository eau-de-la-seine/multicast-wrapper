package fr.ekinci.multicastwrapper;

import java.io.IOException;
import java.io.Serializable;


/**
 * A simple POJO container which Base64 encoded method arguments 
 * 
 * 
 * @author Gokan EKINCI
 */
public class TypeAndValue implements Serializable {
    
    private String stringClassName;
    private String stringValue;    
    
    public <T> TypeAndValue(Class<? super T> superClass, T value) throws IOException{
        stringClassName = superClass.getCanonicalName();    
        stringValue = SlaveInvoker.encodeBase64(value);
    }
    
    public String getStringClassName(){
        return stringClassName;
    }
    
    public String getStringValue(){
        return stringValue;
    }
    
    public Object getObjectValue() throws ClassNotFoundException, IOException{
        return SlaveInvoker.decodeBase64(stringValue);
    }
    
    public Class<?> getObjectClass() throws ClassNotFoundException {
        return Class.forName(stringClassName);
    }
}
