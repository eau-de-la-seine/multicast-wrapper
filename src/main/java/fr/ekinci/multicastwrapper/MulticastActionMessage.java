package fr.ekinci.multicastwrapper;

import java.io.Serializable;


/**
 * Simple POJO class for Multicast Message
 * 
 * @author Gokan EKINCI
 */
public class MulticastActionMessage implements Serializable {    
    
    /** Type of the machine that sends the Message. */
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


    /* *** GETTERS AND SETTERS *** */
    
    public String getMachineType() {
        return machineType;
    }


    public String getMachineCategory() {
        return machineCategory;
    }


    public String getMachineIp() {
        return machineIp;
    }


    public String getJsonValue() {
        return jsonValue;
    }


    public String getExecuteClass() {
        return executeClass;
    }


    public String getExecuteMethod() {
        return executeMethod;
    }


    public String getStringifiedTypeAndValueArray() {
        return stringifiedTypeAndValueArray;
    }


    public void setMachineType(String machineType) {
        this.machineType = machineType;
    }


    public void setMachineCategory(String machineCategory) {
        this.machineCategory = machineCategory;
    }


    public void setMachineIp(String machineIp) {
        this.machineIp = machineIp;
    }


    public void setJsonValue(String jsonValue) {
        this.jsonValue = jsonValue;
    }


    public void setExecuteClass(String executeClass) {
        this.executeClass = executeClass;
    }


    public void setExecuteMethod(String executeMethod) {
        this.executeMethod = executeMethod;
    }


    public void setStringifiedTypeAndValueArray(String stringifiedTypeAndValueArray) {
        this.stringifiedTypeAndValueArray = stringifiedTypeAndValueArray;
    }
    
}
