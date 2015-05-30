package fr.ekinci.multicastwrapper;


/**
 * FunctionalInterface for message reception
 * 
 * @author Gokan EKINCI
 */
// @FunctionalInterface --> in commentary because of Java 7 retro-compatibility
public interface ActionMessageListener {
	
    /**
     * Action to do when receiving a message
     * its content depends of machine type
     * 
     * @category Handler
     */
    void onMessage(MulticastActionMessage message);
}
