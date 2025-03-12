package atavism.agis.core;

import atavism.agis.objects.*;
import atavism.server.engine.OID;

import java.io.*;

/**
 * Class used with AgisItems. Run when an item is being added to a players bag.
 */
public interface AcquireHook extends Serializable {

    /**
     * Run when an item is added to a players bag. If it returns true, the item will not be added to the bag.
     */
    public boolean acquired(OID activator, AgisItem item);
}