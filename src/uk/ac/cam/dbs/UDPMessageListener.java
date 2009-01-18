package uk.ac.cam.dbs;

import java.util.EventListener;

/** Handler for UDP message receipt. */
public interface UDPMessageListener extends EventListener {

    /** Handle a received UDP message. */
    void recvUDPMessage(BusConnection connection, UDPMessage msg);

}
