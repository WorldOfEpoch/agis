package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * An acquire hook for items that turn into currency
 * when acquired.
 */
public class CurrencyItemAcquireHook implements AcquireHook {
    public CurrencyItemAcquireHook() {
    	super();
    }

    public CurrencyItemAcquireHook(int currencyID) {
    	super();
    	setCurrencyID(currencyID);
    }

    public void setCurrencyID(int currencyID) {
        if (currencyID < 1) {
            throw new RuntimeException("CurrencyItemAcquireHook.setCurrencyID: bad currency");
        }
        this.currencyID = currencyID;
    }
    public int getCurrencyID() {
    	return currencyID;
    }
    protected int currencyID;

    /**
     * Adds the item as currency for the player and returns true telling the item to be
     * destroyed.
     */
    public boolean acquired(OID activatorOid, AgisItem item) {
    	Log.dumpStack("acquired");
        if (Log.loggingDebug)
            Log.debug("CurrencyItemAcquireHook.activate: activator=" + activatorOid + " item=" + item + " resource=" + currencyID);
        // Only convert it if it is acquired by a player
        if (WorldManagerClient.getObjectInfo(activatorOid).objType != ObjectTypes.player) {
        	return false;
        }
        int currencyAmount  = item.getStackSize();
        AgisInventoryClient.alterCurrency(activatorOid, currencyID, currencyAmount);
        return true;
    }
    
    public String toString() {
    	return "CurrencyItemAcquireHook=" + currencyID;
    }

    private static final long serialVersionUID = 1L;
}
