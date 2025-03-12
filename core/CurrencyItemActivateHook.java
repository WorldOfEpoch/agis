package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger abilities
 * when the item is activated, the mob uses the ability
 */
public class CurrencyItemActivateHook implements ActivateHook {
    public CurrencyItemActivateHook() {
    	super();
    }

    public CurrencyItemActivateHook(int currencyID,boolean oadelete) {
    	super();
    	setCurrencyID(currencyID);
     	this.oadelete = oadelete;
        }

    public void setCurrencyID(int currencyID) {
        if (currencyID < 1) {
            throw new RuntimeException("CurrencyItemActivateHook.setCurrencyID: bad currency");
        }
        this.currencyID = currencyID;
    }
    public int getCurrencyID() {
    	return currencyID;
    }
    protected int currencyID;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
        if (Log.loggingDebug)
            Log.debug("CurrencyItemActivateHook.activate: activator=" + activatorOid + " item=" + item + " ability=" + currencyID + " target=" + targetOid);
        int currencyAmount  = item.getStackSize();
        AgisInventoryClient.alterCurrency(activatorOid, currencyID, /*currencyAmount*/1);
        // Need to remove the item
      //  AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), true, 1);
        return oadelete;
    }
    
    public String toString() {
    	return "CurrencyItemActivateHook.currency=" + currencyID;
    }

    private static final long serialVersionUID = 1L;
}
