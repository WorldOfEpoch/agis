package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.SpawnData;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * An acquire hook for items that turn into Mob
 * when acquired.
 */
public class SpawnMobAcquireHook implements AcquireHook {
    public SpawnMobAcquireHook() {
    	super();
    }

    public SpawnMobAcquireHook(int mobTemplateID) {
    	super();
    	setMobTemplateID(mobTemplateID);
    }

    public void setMobTemplateID(int mobTemplateID) {
        if (mobTemplateID < 1) {
            throw new RuntimeException("SpawnMobAcquireHook.setMobTemplateID: bad mob template");
        }
        this.mobTemplateID = mobTemplateID;
    }
    public int getMobTemplateID() {
    	return mobTemplateID;
    }
    protected int mobTemplateID;

    /**
     * Adds the item as Mob for the player and returns true telling the item to be
     * destroyed.
     */
    public boolean acquired(OID activatorOid, AgisItem item) {
        if (Log.loggingDebug)
            Log.debug("SpawnMobAcquireHook.activate: activator=" + activatorOid + " item=" + item + " resource=" + mobTemplateID);
        // Only convert it if it is acquired by a player
        ObjectInfo objInfo = WorldManagerClient.getObjectInfo(activatorOid);
        if (objInfo.objType != ObjectTypes.player) {
        	return false;
        }
        SpawnData sd = new SpawnData();
        sd.setProperty("id", (int)System.currentTimeMillis());
        sd.setTemplateID(mobTemplateID);
        BehaviorTemplate behavTmpl = new BehaviorTemplate();
        behavTmpl.setHasCombat(true);
        behavTmpl.setWeaponsSheathed(false);
		behavTmpl.setRoamRadius(0);
        //TODO: update location to not be right on Player
        sd.setLoc(objInfo.loc);
        sd.setOrientation(objInfo.orient);
        sd.setInstanceOid(objInfo.instanceOid);
        sd.setSpawnRadius(0);
        sd.setCorpseDespawnTime(1000);
        sd.setRespawnTime(1000);
        sd.setNumSpawns(1);
        sd.setProperty(AgisMobPlugin.BEHAVIOR_TMPL_PROP, behavTmpl);
        AgisMobClient.spawnMob(sd);
        return true;
    }
    
    public String toString() {
    	return "SpawnMobAcquireHook=" + mobTemplateID;
    }

    private static final long serialVersionUID = 1L;
}
