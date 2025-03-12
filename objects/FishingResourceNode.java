package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import atavism.agis.effects.MountEffect;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CraftingPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class FishingResourceNode extends ResourceNode {

	public FishingResourceNode() {
	}

	public FishingResourceNode(int id, AOVector loc, OID instanceOID) {
		super(id, loc, instanceOID);
	}
	
    /**
     * Checks whether the player can gather items from this resource. Checks their skill level
     * and weapon.
     * @param playerOID
     * @return
     */
	@Override
    boolean playerCanGather(OID playerOid, boolean checkSkillAndWeapon, int playerSkillLevel, boolean checkTask) {
		// No one else is currently gathering are they?
    	if (checkTask && task != null) {
    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.RESOURCE_NODE_BUSY, 0, "");
    		Log.debug("RESOURCE: node is busy");
    		return false;
    	}
    	
    	// remove bait
    	Integer ammoLoaded = (Integer)  EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
    	if(ammoLoaded == null || ammoLoaded <= 0) {
    		return false;
    	}
		
		// Dead check
    	boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
    	if (dead) {
    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_DEAD, 0, "");
    		return false;
    	}
    	// Mounted check
    	if (!CraftingPlugin.CAN_HARVEST_WHILE_MOUNTED) {
    		String mount = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, MountEffect.MOUNT_PROP);
        	if (mount != null && !mount.isEmpty()) {
        		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_MOUNTED, 0, "");
        		return false;
        	}
    	}
    	
    	// location check
    	Point p = WorldManagerClient.getObjectInfo(playerOid).loc;
    	// Player must be within 4 meters of the node (16 for squared)
    	if (Point.distanceToSquared(p, new Point(loc)) > CraftingPlugin.RESOURCE_GATHER_DISTANCE * CraftingPlugin.RESOURCE_GATHER_DISTANCE) {
    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.TOO_FAR_AWAY, 0, "");
    		Log.debug("RESOURCE: player is too far away");
    		return false;
    	}
    	if (checkSkillAndWeapon) {
    		// skill check
    		if (skill > 0 && skillLevelReq > 0) {
    			Log.debug("RESOURCE: checking skill: " + skill + " against playerSkillLevel: " + playerSkillLevel);
    			if (playerSkillLevel < skillLevelReq) {
    				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SKILL_LEVEL_TOO_LOW, skill, "" + skillLevelReq);
    				return false;
    			}
    		}
    		// weapon check
    		if (weaponReq != null && !weaponReq.equals("") && !weaponReq.equals("None")) {
    			ArrayList<String> weaponType = new ArrayList<String>();
    			try {
    				Serializable wype =  EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "toolType");
    				if(wype !=null)
    					weaponType =  (ArrayList<String>)wype;
    			} catch (Exception e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			
    			Log.debug("RESOURCE: checking weaponReq: " + weaponReq + " against: " + weaponType);
    			
    			if (!weaponType.contains(weaponReq) )     			{
    				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.EQUIP_MISSING, 0, weaponReq);
    				return false;
    			}
    		
    		}
    	}
    	
    	Log.debug("RESOURCE: player can harvest resource");
    	return true;
    }
	
//	@Override
//	public void printType() {
//		Log.debug("FishingNode");
//	}
	
	public void setItem(int item) {
		currentItems = new HashMap<Integer, Integer>();
		currentItems.put(item, 1);
	}
	
	public void startFishing(OID playerOid, Float length) {
		int playerSkillLevel = 1;
    	if (skill > 0)
    		playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, skill);
    	if(!playerCanGather(playerOid, false, playerSkillLevel, true)) {
    		return;
    	}
		
	    fishingTask = new FishTask();
	    fishingTask.StartFishTask(loc, Quaternion.Identity, playerOid, playerSkillLevel, this);
	    fishingTask.sendStartFishingTask(length);
	    
	    SubjectFilter filter = new SubjectFilter(playerOid);
        filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
        sub = Engine.getAgent().createSubscription(filter, this);
	}

	@Override
    public void tryHarvestResources(OID playerOid) {
    	Log.debug("RESOURCE: got player trying to fish resource with skillID: " + skill);
    
	    fishingTask.run();
    }
	
	@Override
	void interruptHarvestTask() {
    	if (fishingTask != null) {
    		fishingTask.interrupt();
    		fishingTask = null;
    		if (sub != null)
                Engine.getAgent().removeSubscription(sub);
    	}
    }
	
    void fishingComplete(FishTask task) {
    	// Do a success calculation check
	    if (skillLevelMax < 2)
	    	skillLevelMax = 2;
	    if (skillLevelMax < skillLevelReq)
	    	skillLevelMax = skillLevelReq+1;
	    int rollMax = (skillLevelMax-skillLevelReq) * 130 / 100;
	    int skillLevelCalc = (task.playerSkillLevel - skillLevelReq + ((skillLevelMax-skillLevelReq) / 2)) * 200 / 300;
	    if (skillLevelCalc > skillLevelMax)
	    	skillLevelCalc = skillLevelMax;
	    
	    Random rand = new Random();
    	// Check if there are any items to give
    	if (currentItems.isEmpty() || (CraftingPlugin.RESOURCE_GATHER_CAN_FAIL && skill > 0 
    			&& skillLevelReq > 0 && skillLevelCalc < rand.nextInt(rollMax))) {
			if (CraftingPlugin.RESOURCE_DROPS_ON_FAIL && !CraftingPlugin.RESOURCE_COUNT_IS_LOOT_COUNT)
    			harvestsLeft--;
			if (harvestsLeft == 0) {
				despawnResource();
			} else {
				Log.debug("RESOURCE: generating items from tryHarvestResources as currentItems is empty");
				generateItems();
			}
			// Still send down item list (which will be empty)
			sendNoItems(task.playerOid);
			//TODO: send down some form of failed to harvest message
			EventMessageHelper.SendErrorEvent(task.playerOid, EventMessageHelper.RESOURCE_HARVEST_FAILED, 0, "");
			return;
		}
    	
    	// Do skill up
    	if (skill > 0 && !skillupGiven) {
			Log.debug("RESOURCE: checking skill: " + skill + " against playerSkillLevel: " + task.playerSkillLevel);
			if (task.playerSkillLevel < skillLevelMax) {
				CombatClient.abilityUsed(task.playerOid, skill);
				skillupGiven = true;
			} else if (CraftingPlugin.GAIN_SKILL_AFTER_MAX && rand.nextInt(4) == 0) {
				CombatClient.abilityUsed(task.playerOid, skill);
				skillupGiven = true;
			}
    	}
    	
    	// Give exp
    	if (CraftingPlugin.RESOURCE_HARVEST_XP_REWARD > 0) {
    		CombatClient.alterExpMessage expMsg = new CombatClient.alterExpMessage(task.playerOid, CraftingPlugin.RESOURCE_HARVEST_XP_REWARD);
            Engine.getAgent().sendBroadcast(expMsg);
    	}
    	
    	// remove bait
    	Integer ammoLoaded = (Integer)  EnginePlugin.getObjectProperty(fishingTask.playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
    	if(ammoLoaded != null || ammoLoaded > 0) {
    		AgisInventoryClient.removeGenericItem(fishingTask.playerOid, ammoLoaded, false, 1);
    	}
    	
    	// Send items
    	if (CraftingPlugin.AUTO_PICKUP_RESOURCES) {
    		OID playerOid = task.playerOid;
    		gatherAllItems(playerOid);
    		this.fishingTask = null;
    	} else {
    		sendItems(task.playerOid);
    	}
    	
    	// Reduce item durability
    	if (weaponReq != null && !weaponReq.contains("None"))
    		AgisInventoryClient.equippedItemUsed(task.playerOid, "Gather");
    }
	
	FishTask fishingTask;
	Integer baitID;
	
	public class FishTask extends HarvestTask {
		protected FishingResourceNode fishingNode;
		
		FishTask() {
		}
		
		public void StartFishTask(AOVector loc, Quaternion orient, OID playerOid, int playerSkillLevel, FishingResourceNode fishingNode) {
    		Log.debug("RESOURCE: creating new fishing task");
    		this.loc = loc;
    		this.orient = orient;
    		this.playerOid = playerOid;
    		this.playerSkillLevel = playerSkillLevel;
    		this.fishingNode = fishingNode;
    	}
		
		public void sendStartFishingTask(float length) {
    		Log.debug("RESOURCE: sending start fishing task");
    		
    		// Send animation
        	CoordinatedEffect cE = new CoordinatedEffect(fishingNode.harvestCoordinatedEffect);
    	    cE.sendSourceOid(true);
    	    cE.sendTargetOid(true);
    	    cE.putArgument("length", length);
    	    cE.putArgument("resourceNodeID", id);
    	    cE.invoke(playerOid, playerOid);
    	}
		
		@Override
		public void run() {
			if (fishingNode.sub != null)
                Engine.getAgent().removeSubscription(fishingNode.sub);
			
			if (interrupted) {
				Log.debug("BUILD: fish task was interrupted, not completing run");
				fishingNode.fishingTask = null;
				return;
			}
			
			// Dead check
	    	boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
	    	if (!dead) {
	    		fishingNode.fishingComplete(this);
	    	}
	    	fishingNode.fishingTask = null;
		}
		
		@Override
		public void interrupt() {
			interrupted = true;
			Map<String, Serializable> props = new HashMap<String, Serializable>();
        	props.put("ext_msg_subtype", "fishing_task_interrupted");
        	TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    				playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
		}
	}
	
	private static final long serialVersionUID = 1L;
}
