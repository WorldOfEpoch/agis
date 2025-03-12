package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.agis.core.*;
import atavism.server.engine.BasicWorldNode;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * Effect child class used to spawn an object. 
 * Currently used for spawning pets, but not tested in a long time.
 * @author Andrew Harrison
 *
 */
public class SpawnEffect extends AgisEffect {

    public SpawnEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SpawnEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SpawnEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();
		effectSkillType = (int)params.get("skillType");
		Log.debug("SpawnEffect.apply spawnType:"+spawnType);
			if (spawnType == 0) {
			BasicWorldNode wnode = WorldManagerClient.getWorldNode(obj.getOid());
			AgisMobClient.spawnMob(mobID, wnode.getLoc(), wnode.getOrientation(), wnode.getInstanceOid(), true);
		} else {
			if (mobID == -1) {
				// Use the name of the target
				mobID = obj.getID();
			}
			BasicWorldNode wnode = WorldManagerClient.getWorldNode(obj.getOid());
			
		    AgisMobClient.spawnPet(obj.getOwnerOid(), wnode.getInstanceOid(), mobID, spawnType, duration, passiveEffect, effectSkillType);
		}
		
    }

    /**
     * 0: Wild (Will do it's own thing)
     * 1: Quest Follower
     * 2: Non Combat pet (Follows the player)
     * 3: Combat pet (player can control it with commands)
     * @return
     */
    protected int spawnType = 0;
    public int getSpawnType() { return spawnType; }
    public void setSpawnType(int spawnType) { this.spawnType = spawnType; }
    
    public int getMobID() { return mobID; }
    public void setMobID(int mobID) { this.mobID = mobID; }
    protected int mobID = -1;
    
    public int getPassiveEffect() { return passiveEffect; }
    public void setPassiveEffect(int passiveEffect) { this.passiveEffect = passiveEffect; }
    protected int passiveEffect = -1;
    
    /*public String getInstanceName() { return instanceName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    protected String instanceName = null;*/
    
    private static final long serialVersionUID = 1L;
}
