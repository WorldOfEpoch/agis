package atavism.agis.core;

import atavism.agis.objects.*;
import atavism.server.engine.OID;

import java.io.*;

/**
 * used with Agisitems - this gets called when a user activates this
 * item
 */
public interface ActivateHook extends Serializable {

    /**
     * returns whether the item was successfully activated
     */
    public boolean activate(OID activator, AgisItem item, OID target);
}
