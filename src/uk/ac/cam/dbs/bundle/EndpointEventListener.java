package uk.ac.cam.dbs.bundle;

import java.util.EventListener;

public interface EndpointEventListener extends EventListener {

    void deliverBundle(Bundle b);

}
