package atavism.agis.core;

import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.util.*;

import java.util.List;

import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.agis.plugins.AgisInventoryPlugin.EquipMap;

/**
 * an activate hook for items that trigger abilities
 * when the item is activated, the mob uses the ability
 */
public class AmmoItemActivateHook implements ActivateHook {
    public AmmoItemActivateHook() {
    	super();
    }
    public AmmoItemActivateHook(int ammoType,boolean oadelete) {
    	super();
    	setAmmo(ammoType);
     	this.oadelete = oadelete;
           }

    public void setAmmo(int ammoType) {
        Log.debug("AJ: setting ammoType to: " + ammoType);
        this.ammoType = ammoType;
    }
    public int getAmmo() {
    	return ammoType;
    }
    protected int ammoType;
    
    public void setAmmoEffectID(int ammoEffectID) {
        Log.debug("AJ: setting ammoType to: " + ammoEffectID);
        this.ammoEffectID = ammoEffectID;
    }
    public int getAmmoEffectID() {
    	return ammoEffectID;
    }
    protected int ammoEffectID;
    protected boolean oadelete = false;

	public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
		if (Log.loggingDebug)
			Log.debug("AmmoItemActivateHook.activate: activator=" + activatorOid + " item=" + item + " target=" + targetOid);

		// Check if the Ammo Type suits the weapon
		InventoryInfo iInfo = AgisInventoryPlugin.getInventoryInfo(activatorOid);
		if (iInfo == null) {
			Log.debug("AmmoItemActivateHook.activate: no inventoryInfo found");
			return false;
		}
		int ammoId = -1;
		try {
			ammoId = (Integer) EnginePlugin.getObjectProperty(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
		} catch (Exception e) {
			Log.debug("AmmoItemActivateHook.activate: no ammo loaded error " + e);
		}
		if (item.getTemplateID() != ammoId) {
			Boolean ammoTypeMatch = false;
			EquipMap equipMap = (EquipMap) iInfo.getProperty(AgisInventoryPlugin.EQUIP_MAP_PROP);
			if (equipMap != null) {
				List<AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
				for (AgisEquipSlot aes : slots) {

					OID oItemOid = equipMap.get(aes);
					if (oItemOid != null) {
						AgisItem oItemObj = AgisInventoryPlugin.getAgisItem(oItemOid);
						if (oItemObj != null) {
							Integer ammoTypeReq = (Integer) oItemObj.getProperty(AgisItem.AMMO_TYPE);

							if (ammoTypeReq != null && ammoTypeReq == ammoType) {
								ammoTypeMatch = true;
								Log.debug("AmmoItemActivateHook.activate: " + aes + " oItemOid " + oItemOid + " match " + ammoTypeMatch);

							} else {
								Log.debug("AmmoItemActivateHook.activate: " + aes + " Ammo of type: " + ammoType + " could not be activated as the weapon requires: " + ammoTypeReq);
								// return false;
							}
						} else {
							Log.debug("AmmoItemActivateHook.activate: " + aes + "  oItemObj " + oItemObj + "  is null");
						}
					} else {
						Log.debug("AmmoItemActivateHook.activate: " + aes + "  oItemOid " + oItemOid + "  is null");

					}
				}
			/*	OID soItemOid = equipMap.get(AgisEquipSlot.SECONDARYWEAPON);
				if (soItemOid != null) {
					AgisItem soItemObj = AgisInventoryPlugin.getAgisItem(soItemOid);
					if (soItemObj != null) {
						Integer ammoTypeReq = (Integer) soItemObj.getProperty(AgisItem.AMMO_TYPE);

						if (ammoTypeReq != null && ammoTypeReq == ammoType) {
							ammoTypeMatch = true;
							Log.debug("AmmoItemActivateHook.activate: secondary soItemOid " + soItemOid + " match " + ammoTypeMatch);
						} else {
							Log.debug("AmmoItemActivateHook.activate: Ammo of type: " + ammoType + " could not be activated as the weapon secondaty requires: " + ammoTypeReq);
						}
					} else {
						Log.debug("AmmoItemActivateHook.activate: secondary soItemObj " + soItemObj + "  is null");
					}
				} else {
					Log.debug("AmmoItemActivateHook.activate: secondary soItemOid " + soItemOid + "  is null");

				}
				*/
			}
			if (!ammoTypeMatch) {
				Log.debug("AmmoItemActivateHook.activate: Ammo no match to eqiped weapomns");
				return false;
			}
			Log.debug("AmmoItemActivateHook.activate: Ammo match to eqiped weapomns");

			// Set combat property to indicate item template ID of ammo equipped
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED, item.getTemplateID());

			int count = AgisInventoryClient.getCountGenericItem(activatorOid, item.getTemplateID());
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_AMOUNT, count);

			// Set combat ammo damage amount
			int damage = (Integer) item.getProperty("damage");
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_DAMAGE, damage);

			// Set combat ammo effect

			Log.debug("AmmoItemActivateHook.activate: ammo set: " + ammoType);
		} else {
			Log.debug("AmmoItemActivateHook.activate: Deactivate Ammo " + ammoId);
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED, -1);
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_DAMAGE, 0);
			EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_AMOUNT, 0);

		}
		AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(activatorOid);
		Engine.getAgent().sendBroadcast(invUpdateMsg);

		return false;
	}
    
    public String toString() {
    	return "AmmoItemActivateHook=" + ammoType;
    }

    private static final long serialVersionUID = 1L;
}
