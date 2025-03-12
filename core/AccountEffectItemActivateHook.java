package atavism.agis.core;


import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;
import atavism.agis.objects.*;

/**
 * An acquire hook for items that turn into currency
 * when acquired.
 */
public class AccountEffectItemActivateHook implements ActivateHook {
    public AccountEffectItemActivateHook() {
    	super();
    }

    public AccountEffectItemActivateHook(String effectType, int effectValue,boolean oadelete) {
    	super();
    	setEffectType(effectType);
    	setEffectValue(effectValue);
     	this.oadelete = oadelete;
          }
    
    public void setEffectType(String effectType) {
        if (effectType.equals("")) {
            throw new RuntimeException("AccountEffectItemAcquireHook.setEffectType: Empty effectType");
        }
        this.effectType = effectType;
    }
    public String getEffectType() {
    	return effectType;
    }
    protected String effectType;

    public void setEffectValue(int effectValue) {
        this.effectValue = effectValue;
    }
    public int getEffectValue() {
    	return effectValue;
    }
    protected int effectValue;
    protected boolean oadelete = false;

    /**
     * Adds the item as currency for the player and returns true telling the item to be
     * destroyed.
     */
    // public boolean activate(OID activatorOid, AgisItem item, OID targetOid) 
    public boolean acquired(OID activatorOid, AgisItem item) {
        if (Log.loggingDebug)
            Log.debug("AccountEffectItemAcquireHook.acquired: activator=" + activatorOid + " item=" + item + " resource=" + effectType);
        // Only convert it if it is acquired by a player
        if (WorldManagerClient.getObjectInfo(activatorOid).objType != ObjectTypes.player) {
        	return false;
        }
        //TODO: Do if else statement on effect Types
        return true;
    }
    
    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	 if (Log.loggingDebug)
             Log.debug("AccountEffectItemAcquireHook.activate: activator=" + activatorOid + " item=" + item + " targetOid="+targetOid+" resource=" + effectType);
         // Only convert it if it is acquired by a player
    	 return oadelete;
    }
    
    
    public String toString() {
		return "AccountEffectItemAcquireHook [effectType=" + effectType + " effectValue=" + effectValue + "]";
	  }

    private static final long serialVersionUID = 1L;
}
