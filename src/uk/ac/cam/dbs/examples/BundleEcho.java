/*
 * Distributed bus system for robotic applications
 * Copyright (C) 2009 University of Cambridge
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package uk.ac.cam.dbs.examples;

import uk.ac.cam.dbs.bundle.*;
import uk.ac.cam.dbs.SystemBus;

public class BundleEcho implements EndpointEventListener {

    String endpoint;
    BundleAgent agent;

    public BundleEcho(BundleAgent agent) {

        this.agent = agent;

        endpoint = "dtn://["
            + SystemBus.getSystemBus().getMainAddress().toString()
            + "]/echo";

        agent.registerEndpoint(endpoint, this);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void deliverBundle(Bundle b) {
        b.setDestEndpoint(b.getSourceEndpoint());
        b.setSourceEndpoint(this.getEndpoint());

        agent.sendBundle(b);
    }

}
