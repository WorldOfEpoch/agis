package atavism.agis.core;

import atavism.agis.objects.*;
import atavism.server.engine.OID;

import java.io.*;

/**
 * Class used with AgisItems. Run when an item is being removed from a players bag.
 */
public interface UnacquireHook extends Serializable {

    /**
     * Run when an item is removed from a players bag. 
     */
    public boolean unacquired(OID activator, AgisItem item);
}