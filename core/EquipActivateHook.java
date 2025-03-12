package atavism.agis.core;

import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.server.plugins.*;
import atavism.agis.plugins.*;
import atavism.agis.objects.*;

/**
 * an activate hook attached to equippable items, eg: weapons, armor
 * hook will unequip the item in the current slot and equip the item
 * associated with the hook
 */
public class EquipActivateHook implements ActivateHook {
    public EquipActivateHook() {
        super();
    }

    /**
     * returns whether the item was successfully activated
     */
    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	// get the inventoryplugin
    	AgisInventoryPlugin invPlugin = (AgisInventoryPlugin)Engine.getPlugin(InventoryPlugin.INVENTORY_PLUGIN_NAME);
		if (Log.loggingDebug)
			Log.debug("EquipActivateHook: calling invPlugin, item=" + item + ", activatorOid=" + activatorOid + ", targetOid=" + targetOid);

    	// is this item already equipped
    	AgisInventoryPlugin.EquipMap equipMap = invPlugin.getEquipMap(activatorOid);
    	AgisEquipSlot slot;
    	
    	invPlugin.getLock().lock();
    	try {
    		slot = equipMap.getSlot(item.getMasterOid());
    	
    	} finally {
    		invPlugin.getLock().unlock();
    	}
    	
    	if (slot == null) {
    		// its not equipped
    		if (Log.loggingDebug)
    			Log.debug("EquipActivateHook: item not equipped: " + item);
    		 invPlugin.equipItem(item, activatorOid, true);
    	} else {
    		// it is equipped, unequip it
    		if (Log.loggingDebug)
    			Log.debug("EquipActivateHook: item IS equipped: " + item);
    		 invPlugin.unequipItem(item, activatorOid, false);
    	}
    	return false;
    }

    public String toString() {
    	return "[EquipActivateHook: itemOid="+itemOid+"]";
    }
    // use oids since cheaper to serialize
    protected OID itemOid;
    private static final long serialVersionUID = 1L;
}
