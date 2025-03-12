package atavism.agis.core;


import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger abilities
 * when the item is activated, the mob uses the ability
 */
public class CreateClaimActivateHook implements ActivateHook {
    public CreateClaimActivateHook() {
    	super();
    }

    public CreateClaimActivateHook(AgisAbility ability,boolean oadelete) {
    	super();
    	Log.debug("AJ: creating CreateClaimActivateHook with ability: " + ability.getID());
    	setSize(ability.getID());
       	this.oadelete = oadelete;
          }

    public CreateClaimActivateHook(int size,boolean oadelete) {
    	super();
    	setSize(size);
       	this.oadelete = oadelete;
         }
    public CreateClaimActivateHook(int size,int type,boolean oadelete) {
    	super();
    	setSize(size);
    	setType(type);
    	
       	this.oadelete = oadelete;
         }

    public void setSize(int size) {
        if (size < 5) {
            throw new RuntimeException("CreateClaimActivateHook.setSize: bad size");
        }
        Log.debug("AJ: setting size to: " + size);
        this.size = size;
    }
    public int getSize() {
    	return size;
    }
    protected int size;
    
    public void setType(int type) {
        Log.debug("CLIME: setting claim Type to: " + type);
        this.claimType = type;
    }
    public int getType() {
    	return claimType;
    }
    protected int claimType;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
        if (Log.loggingDebug)
            Log.debug("CreateClaimActivateHook.activate: activator=" + activatorOid + " item=" + item + " size=" + size + " target=" + targetOid+ " claimType:"+claimType);
        //CombatClient.startAbility(abilityID, activatorOid, targetOid, item, null);
        ExtensionMessage eMsg = new ExtensionMessage(VoxelClient.MSG_TYPE_CREATE_CLAIM, null, activatorOid);
        eMsg.setProperty("name", "My Claim");
        BasicWorldNode wNode = WorldManagerClient.getWorldNode(activatorOid);
        eMsg.setProperty("loc", new AOVector(wNode.getLoc()));
        eMsg.setProperty("size", size);
        eMsg.setProperty("forSale", false);
        eMsg.setProperty("cost", 0L);
        eMsg.setProperty("currency", 0);
        eMsg.setProperty("owned", true);
        eMsg.setProperty("claimType", claimType);
        eMsg.setProperty("item", item.getOid());
        eMsg.setProperty("claimTemplateItem", item.getTemplateID());
        eMsg.setProperty("taxCurrency", -1);
        eMsg.setProperty("taxAmount", 0L);
        eMsg.setProperty("taxInterval", 0L);
        eMsg.setProperty("taxTimePay", 0L);
        eMsg.setProperty("taxTimeSell",0L);
        
        Engine.getAgent().sendBroadcast(eMsg);
        return oadelete;
    }
    
    public String toString() {
    	return "CreateClaimActivateHook:size=" + size + ";claimType="+claimType;
    }

    private static final long serialVersionUID = 1L;
}
