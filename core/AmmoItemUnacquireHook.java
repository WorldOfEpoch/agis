package atavism.agis.core;

import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.util.Log;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * An acquire hook for items that turn into a building resource
 * when acquired.
 */
public class AmmoItemUnacquireHook implements UnacquireHook {
    public AmmoItemUnacquireHook() {
    	super();
    }
    
    public boolean unacquired(OID activator, AgisItem item) {
		// If the item that is lost matches the ammo equipped and the player has no other ammo of this type
    	// set the ammoId for the player to -1
    	Integer ammoID = (Integer) EnginePlugin.getObjectProperty(activator, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
    	if (ammoID == null || ammoID != item.getTemplateID()) {
    		// This ammo type does not match the players ammo type
    		return true;
    	}
    	
    	Log.debug("AMMO: getting InventoryInfo");
    	InventoryInfo iInfo = AgisInventoryPlugin.getInventoryInfo(activator);
        if (iInfo == null) {
        	return true;
        }
    	
        Log.debug("AMMO: checking for more ammo");
        boolean foundMoreAmmo = false;
        OID[] subBags = iInfo.getBags();
        for (int i = 0; i < subBags.length; i++) {
        	OID subBagOid = subBags[i];
        	if (subBagOid == null)
        		continue;
        	Bag subBag = AgisInventoryPlugin.getBag(subBagOid);
        	for (OID itemOid : subBag.getItemsList()) {
        	    if (itemOid == null)
        		    continue;
        		AgisItem itemToCheck = AgisInventoryPlugin.getAgisItem(itemOid);
        		if (ammoID == itemToCheck.getTemplateID()) {
        			foundMoreAmmo = true;
        			break;
        		}
        	}
        }
        
        Log.debug("AMMO: found more ammo? " + foundMoreAmmo);
    	if (!foundMoreAmmo) {
    		EnginePlugin.setObjectPropertyNoResponse(activator, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED, -1);
            EnginePlugin.setObjectPropertyNoResponse(activator, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_DAMAGE, 0);
 		    EnginePlugin.setObjectPropertyNoResponse(activator, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_AMOUNT, 0);
 			}
    	
		return true;
	}
    
    public String toString() {
    	return "AmmoItemUnacquireHook";
    }

    private static final long serialVersionUID = 1L;
	
}
