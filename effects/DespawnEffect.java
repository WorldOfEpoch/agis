package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.messages.PropertyMessage;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.util.*;

/**
 * Effect child class that despawns an object. 
 * Can be used to despawn a pet or any other object.
 * @author Andrew Harrison
 *
 */
public class DespawnEffect extends AgisEffect {

    public DespawnEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("DespawnEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("DespawnEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();
		
		if (despawnType == 3) {
			TamedPet oldPet = (TamedPet) EnginePlugin.getObjectProperty(caster.getOwnerOid(), WorldManagerClient.NAMESPACE, "CombatPet");
	    	if (oldPet != null) {
	    		oldPet.despawnPet();
	    	} else {
	    		String petKey = (String) EnginePlugin.getObjectProperty(caster.getOwnerOid(), WorldManagerClient.NAMESPACE, "activePet");
	    		TamedPet pet = (TamedPet) ObjectManagerClient.loadObjectData(petKey);
        		pet.despawnPet();
	    	}
		} else if (despawnType == 0) {
			WorldManagerClient.despawn(obj.getOid());
		} else {
			PropertyMessage propMsg = new PropertyMessage(obj.getOwnerOid());
	        propMsg.setProperty("tamed", true);
	        Engine.getAgent().sendBroadcast(propMsg);
		}
        
        Log.debug("DESPAWNEFFECT: despawning object: " + obj.getOwnerOid());
    }
    
    /**
     * 0: The effect target
     * 3: Combat pet (player can control it with commands)
     */
    protected int despawnType = 0;
    public int getDespawnType() { return despawnType; }
    public void setDespawnType(int despawnType) { this.despawnType = despawnType; }
    
    
    public int getMobID() { return mobID; }
    public void setMobID(int mobID) { this.mobID = mobID; }
    protected int mobID = -1;
    
    private static final long serialVersionUID = 1L;
}
