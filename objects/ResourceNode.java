package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.effects.MountEffect;
import atavism.agis.plugins.AchievementsClient;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CraftingPlugin;
import atavism.agis.plugins.QuestClient;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

/**
 * A ResourceNode is an object players can gather items from. The ResourceNode randomly generates its items upon spawn
 * from the items it has been given and allows the player to gather them if they meet the requirements.
 * @author Andrew Harrison
 *
 */
public class ResourceNode implements Serializable, MessageDispatch, Runnable {
    public ResourceNode() {
    }
    
    public ResourceNode(int id, AOVector loc,  OID instanceOID) {
    	this.id = id;
    	this.loc = loc;
    	this.instanceOID = instanceOID;
    }
    
    public void AddResourceDrop(int item, int min, int max, float chance, float chanceMax) {
    	drops.add(new ResourceDrop(item, min, max, chance, chanceMax));
    }
    public void AddResourceDrop(ResourceDrop drop) {
    	drops.add(drop);
    }
    
    public void AddResourceDrops(List<ResourceDrop> drop) {
    	drops.addAll(new ArrayList<ResourceDrop>(drop));
    }
    public void setResourceDrops(List<ResourceDrop> drop) {
    	drops = new ArrayList<ResourceDrop>(drop);
    }
    /**
     * Subscribes the instance to receive certain relevant messages that are sent to the world object 
     * created by this instance.
     */
    public void activate() {
    	SubjectFilter filter = new SubjectFilter(objectOID);
        filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
        eventSub = Engine.getAgent().createSubscription(filter, this);
        // Set the reaction radius tracker to alert the object if a player has entered its draw radius
        MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, 100);
        active = true;
        Log.debug("ResourceNode.activate: id:"+id+" active="+active);
		
        Log.debug("RESOURCE: node with oid: " + objectOID + " activated");
    }
    
    /**
     * Deals with the messages the instance has picked up.
     */
    public void handleMessage(Message msg, int flags) {
    	if (active == false) {
    	    return;
    	}
    	if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
    	    ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
     	   if(Log.loggingDebug) Log.debug("RESOURCE: myOid=" + objectOID + " objOid=" + nMsg.getSubject()
     		      + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());
    	    if (nMsg.getInRadius()) {
    	    	addPlayer(nMsg.getSubject());
    	    } else {
    	    	// Remove subject from targets in range
    	    	removePlayer(nMsg.getSubject());
    	    }
    	} else if (msg instanceof CombatClient.interruptAbilityMessage) {
            interruptHarvestTask();
        }
    }
    
    
    public void applySettings() {
		if(Log.loggingDebug)Log.debug("ResourceNode.applySettings Start "+id+" profileId="+profileId+" settingId="+settingId);
    	ResourceNodeProfile rnp = CraftingPlugin.recourceNodeProfile.get(profileId);
		ResourceNodeProfileSettings rnps = rnp.getSetting(settingId);
		if(rnps!=null) {
		setSettingId(settingId);
		setHarvestCoordEffect(rnps.getHarvestCoordEffect());
		setActivateCoordEffect(rnps.getActivateCoordEffect());
		setDeactivateCoordEffect(rnps.getDeactivateCoordEffect());
		setSkill(rnps.getSkill());
		setSkillLevelReq(rnps.getSkillLevelReq());
		setSkillLevelMax(rnps.getSkillLevelMax());
		setSkillExp(rnps.getSkillExp());
		setWeaponReq(rnps.getWeaponReq());
		setEquippedReq(rnps.getEquippedReq());
		setRespawnTime(rnps.getRespawnTime());
		setRespawnTimeMax(rnps.getRespawnTimeMax());
		setHarvestCount(rnps.getHarvestCount());
		setHarvestTimeReq(rnps.getHarvestTimeReq());
		setMaxHarvestDistance(rnp.getDistance());
		setCooldown(rnps.getCooldown());
		setDeactivationDelay(rnps.getDeactivationDelay());
		setResourceDrops(rnps.getResourceDrops());
		setLootMaxCount(rnps.getLootMaxCount());
		setEnsureLoot(rnps.getEnsureLoot());
		harvestsLeft = harvestCount;
		} else {
			Log.error ("ResourceNode.applySettings for ResourceNode "+id+" with profileId="+profileId+" cant find settingId="+settingId);
		}
		if(Log.loggingDebug)Log.debug("ResourceNode.applySettings End "+id);
	    
    }
    
    
    @Override
	public void run() {
		if(Log.loggingDebug)Log.debug("ResourceNode.Run Start "+id);
		try {
			// active = true;
			harvestsLeft = harvestCount;
			List<Integer> list = new ArrayList<Integer>();
			HashMap<Integer, ResourceNode> nodes = CraftingPlugin.resourceNodes.get(instanceOID);
			for (ResourceNode rn : nodes.values()) {
				if (rn.getProfileId() == profileId && !rn.getActive() && !rn.isLock()) {
					list.add(rn.getID());
				}
			}

			if(Log.loggingDebug)Log.debug("ResourceNode.Run "+id+" | list="+list);
			if (list.size() > 0) {
				Random rand=new Random();
				Collections.shuffle(list);

				ResourceNode rn = nodes.get(list.get(list.size() > 1 ? rand.nextInt(list.size() - 1) : 0));
				ResourceNodeProfile rnp = CraftingPlugin.recourceNodeProfile.get(profileId);

				ResourceNodeProfileSettings rnps = rnp.getSetting(settingId);
				rn.setSettingId(settingId);
				rn.setHarvestCoordEffect(rnps.getHarvestCoordEffect());
				rn.setActivateCoordEffect(rnps.getActivateCoordEffect());
				rn.setDeactivateCoordEffect(rnps.getDeactivateCoordEffect());
				rn.setSkill(rnps.getSkill());
				rn.setSkillLevelReq(rnps.getSkillLevelReq());
				rn.setSkillLevelMax(rnps.getSkillLevelMax());
				rn.setSkillExp(rnps.getSkillExp());
				rn.setWeaponReq(rnps.getWeaponReq());
				rn.setEquippedReq(rnps.getEquippedReq());
				rn.setRespawnTime(rnps.getRespawnTime());
				rn.setRespawnTimeMax(rnps.getRespawnTimeMax());
				rn.setHarvestCount(rnps.getHarvestCount());
				rn.setHarvestTimeReq(rnps.getHarvestTimeReq());
				rn.setMaxHarvestDistance(rnp.getDistance());
				
				rn.setCooldown(rnps.getCooldown());
				rn.setDeactivationDelay(rnps.getDeactivationDelay());
				rn.setResourceDrops(rnps.getResourceDrops());
				rn.setLootMaxCount(rnps.getLootMaxCount());
				rn.setEnsureLoot(rnps.getEnsureLoot());
				
				if (CraftingPlugin.USE_RESOURCE_GROUPS) {
					if(Log.loggingDebug)	Log.debug("ResourceNode.Run id:"+id+" USE_RESOURCE_GROUPS Spawn diffrent ResourceNode "+rn.id);
					rn.activateAsChildOfGroup();
					rn.sendState();
				} else {
					if(Log.loggingDebug)	Log.debug("ResourceNode.Run id:"+id+" Spawn diffrent ResourceNode "+rn.id);
					rn.spawn();
				}

			} else {
				if(Log.loggingDebug)Log.debug("ResourceNode.Run: id:"+id+" active="+active);
				active = true;
				harvestsLeft = harvestCount;
				if(Log.loggingDebug)	Log.debug("ResourceNode.Run: id:"+id+" | active="+active);
				for (OID playerOid : playersInRange) {
					sendState(playerOid);
				}
			}
     
			lock = false;
		} catch (Exception e) {
			Log.exception("ResourceNode.Run "+id,e);
			e.printStackTrace();
		}
		Log.debug("ResourceNode.Run END");
	    
	}
    
    /**
     * An external call to spawn a world object for the claim.
     * @param instanceOID
     */
    public void spawn(OID instanceOID) {
    	this.instanceOID = instanceOID;
    	spawn();
    }
    
    /**
     * Spawn a world object for the claim.
     */
    public void spawn() {
    	Template markerTemplate = new Template();
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "_ign_" + name + id);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
    	markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
        	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
    	//markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	DisplayContext dc = new DisplayContext(gameObject, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "model", gameObject); 
    	// Put in any additional props
    	if (props != null) {
    		for (String propName : props.keySet()) {
    			markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
    		}
    	}
    	// Create the object
    	objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
                ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
    	
    	if (objectOID != null) {
    		// Need to create an interpolated world node to add a tracker/reaction radius to the claim world object
    		BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objectOID);
    		InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
    		resourceNodeEntity = new ResourceNodeEntity(objectOID, iwNode);
    		EntityManager.registerEntityByNamespace(resourceNodeEntity, Namespace.MOB);
    		MobManagerPlugin.getTracker(instanceOID).addLocalObject(objectOID, 100);
    		
            WorldManagerClient.spawn(objectOID);
            Log.debug("RESOURCE: spawned resource at : " + loc);
            activate();
            
            harvestsLeft = harvestCount;
            //generateItems();
        }
    }
    
    public void activateAsChildOfGroup() {
    	active = true;
		if(Log.loggingDebug) Log.debug("ResourceNode.activateAsChildOfGroup: id:"+id+" active="+active);
 		
    	harvestsLeft = harvestCount;
       // generateItems();
    }
    public void deactivateAsChildOfGroup() {
    	active = false;
		if(Log.loggingDebug)	 Log.debug("ResourceNode.deactivateAsChildOfGroup: id:"+id+" active="+active);
   	 harvestsLeft = harvestCount;
       // generateItems();
    }
    public void setMobAsSkinnable(OID mobOid) {
    	this.objectOID = mobOid;
    	isMob = true;
    	active = true;
    	harvestsLeft = harvestCount;
      //  generateItems();
    }
    
    void generateItems() {
    	currentItems = new HashMap<Integer, Integer>();
    	Random rand = new Random();
		if(Log.loggingDebug)Log.debug("RESOURCE: generateItems "+drops.size());
    		
		int itemsAdded = 0;
		for (ResourceDrop drop : drops) {
			if (itemsAdded < lootMaxCount) {
				if (rand.nextInt(100 * 100) < (drop.Chance() * 100f)) {
					int amount = drop.min + rand.nextInt(drop.max - drop.min + 1);
					if (currentItems.containsKey(drop.item)) {
						currentItems.put(drop.item, currentItems.get(drop.item) + amount);
					} else {
						currentItems.put(drop.item, amount);
					}
					itemsAdded++;
				}
			}
		}

		if(Log.loggingDebug)Log.debug("RESOURCE: generateItems currentItems="+currentItems);
    	if (currentItems.size() == 0 && drops.size() > 0 && ensureLoot) {
    		ResourceDrop drop = drops.get(drops.size()-1);
    		int amount = drop.min + rand.nextInt(drop.max - drop.min + 1);
			currentItems.put(drop.item, amount);
    	}
   
    	skillupGiven = false;
		if(Log.loggingDebug)Log.debug("RESOURCE: generateItems END currentItems="+currentItems);
    }
    
    /**
     * Add a player to the update list for this ResourceNode. The player will receive data about the node and any updates
     * that occur.
     * @param playerOID
     */
    public void addPlayer(OID playerOid) {
    	Log.debug("RESOURCE: added player: " + playerOid+"; to "+id);
    	// Send down the state to the player
    	//sendState(playerOid);
			
		if (!playersInRange.contains(playerOid)) {
	    	playersInRange.add(playerOid);
	    	sendState(playerOid);
	    }
    }
    
    /**
     * Removes a player from the ResourceNode. They will no longer receive updates.
     * @param playerOID
     * @param removeLastID
     */
    public void removePlayer(OID playerOid) {
		if(Log.loggingDebug)Log.debug("RESOURCE: remove player: " + playerOid+"; from "+id);
    		if (playersInRange.contains(playerOid))
    		playersInRange.remove(playerOid);
    }
    
    /**
     * Checks whether the player can gather items from this resource. Checks their skill level
     * and weapon.
     * @param playerOID
     * @return
     */
    boolean playerCanGather(OID playerOid, boolean checkSkillAndWeapon, int playerSkillLevel, boolean checkTask) {
    	// No one else is currently gathering are they?
    	if (checkTask && task != null) {
    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.RESOURCE_NODE_BUSY, 0, "");
			if(Log.loggingDebug)	Log.debug("RESOURCE: node is busy id="+id);
    		return false;
    	}
    	// Harvests left check
		if (harvestsLeft == 0 && harvestCount != -1) {
			if(Log.loggingDebug)	Log.debug("RESOURCE: no harvests left id="+id);
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
    	/*if (Point.distanceToSquared(p, new Point(loc)) > CraftingPlugin.RESOURCE_GATHER_DISTANCE * CraftingPlugin.RESOURCE_GATHER_DISTANCE) {
    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.TOO_FAR_AWAY, 0, "");
    		Log.debug("RESOURCE: player is too far away");
    		return false;
    	}*/
    	
    	if (Point.distanceTo(p, new Point(loc)) > maxHarvestDistance) {
			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.TOO_FAR_AWAY, 0, "");
			Log.debug("RESOURCE: player is too far away");
			return false;
		}
    	
    	
    	if (checkSkillAndWeapon) {
    		// skill check
    		if (skill > 0 /*&& skillLevelReq > 0*/) {//Andrew 30.10.2017 rem skillLevelReq
				if(Log.loggingDebug)Log.debug("RESOURCE: checking skill: " + skill + " against playerSkillLevel: " + playerSkillLevel);
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

				if(Log.loggingDebug)Log.debug("RESOURCE: checking weaponReq: " + weaponReq + " against: " + weaponType);
    			
    			if (!weaponType.contains(weaponReq) ) {
    				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.EQUIP_MISSING, 0, weaponReq);
    				return false;
    			}
    		
    		
    		}
    	
    		
    	}
    	
    	Log.debug("RESOURCE: player can harvest resource");
    	return true;
    }
    
    public void tryHarvestResources(OID playerOid) {
		if(Log.loggingDebug)Log.debug("tryHarvestResources: RESOURCE: id="+id);
    	if (active == false) {
    		 Log.error("ResourceNode.tryHarvestResources: Player Can't Gather Resource because is inactive id="+id);
    	    return;
    	}

		if(Log.loggingDebug)Log.debug("tryHarvestResources: RESOURCE: got player trying to harvest resource with skillID: " + skill+" harvestTimeReq="+harvestTimeReq);
    	int playerSkillLevel = 1;
    	if (skill > 0)
    		playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, skill);
    	if (!playerCanGather(playerOid, true, playerSkillLevel, true)) {
    		Log.debug("tryHarvestResources: RESOURCE: Player Can't Gather Resource !!");
    		return;
    	}
	    
	    task = new HarvestTask();
	    task.StartHarvestTask(loc, Quaternion.Identity, playerOid, playerSkillLevel, this);
	    
	    if (harvestTimeReq > 0) {
	    	InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(task.playerOid);
			float time = harvestTimeReq;
			long vipMod = 0l;
			float vipModp = 0f;
			if (ai != null) {
				
				if (ai.getBonuses().containsKey("HarvestTime")) {
					vipMod = ai.getBonuses().get("HarvestTime").GetValue();
					vipModp = ai.getBonuses().get("HarvestTime").GetValuePercentage();
				}
			}

			if (AgisInventoryPlugin.globalEventBonusesArray.containsKey("HarvestTime")) {
				vipMod += AgisInventoryPlugin.globalEventBonusesArray.get("HarvestTime").GetValue();
				vipMod +=  AgisInventoryPlugin.globalEventBonusesArray.get("HarvestTime").GetValuePercentage();
			}
			time = time + vipMod + time * vipModp/100F;
			if(Log.loggingDebug)Log.debug("RESOURCE HarvestTime="+harvestTimeReq+" with bonus "+time);
    		resourceTask =  Engine.getExecutor().schedule(task, (long)time * 1000, TimeUnit.MILLISECONDS);
    		task.sendStartHarvestTask(time);
    		// Register for player movement to interrupt the gathering
    		SubjectFilter filter = new SubjectFilter(playerOid);
	        filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
	        sub = Engine.getAgent().createSubscription(filter, this);
    	} else {
    		task.run();
    	}
	    
	    Log.debug("tryHarvestResources: END ");
    }
    
    ScheduledFuture resourceTask;
    void interruptHarvestTask() {
    	if (task != null) {
    		task.interrupt();
    	if(resourceTask!=null) {
    		resourceTask.cancel(true);
    	}
    		task = null;
    		if (sub != null)
                Engine.getAgent().removeSubscription(sub);
    	}
    }
    
    void harvestComplete(HarvestTask task) {
    	// Do a success calculation check
		try {
			if(Log.loggingDebug)Log.debug("RESOURCE: harvestComplete Start currentItems=" + currentItems);
			if (currentItems != null && !currentItems.isEmpty()) {
				Log.debug("RESOURCE: harvestComplete send items");
				
				sendItems(task.playerOid);
				return;
			} else {
				Log.debug("RESOURCE: harvestComplete generate items");
				generateItems();
				if(Log.loggingDebug)Log.debug("RESOURCE: harvestComplete generate items currentItems="+currentItems+" ply="+task.playerOid);
				List<Integer> questItemReqs = QuestClient.getQuestItemReqs(task.playerOid);
				if(Log.loggingDebug)Log.debug("RESOURCE: harvestComplete questItemReqs="+questItemReqs);
				
				ArrayList<Integer> itemsToDel = new ArrayList<Integer>();
				for(Integer item : currentItems.keySet()) {
					Template tmpl = ObjectManagerClient.getTemplate(item, ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemType = (String) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemType");
					if(itemType.equals("Quest") && !questItemReqs.contains(item)) {
						itemsToDel.add(item);
						
					}
				}
				for(Integer item : itemsToDel) {
					currentItems.remove(item);
				}
			}

			Log.debug("RESOURCE: harvestComplete |");

			if (skillLevelMax < 2)
				skillLevelMax = 2;
			if (skillLevelMax < skillLevelReq)
				skillLevelMax = skillLevelReq+1;
			int rollMax = (skillLevelMax-skillLevelReq) * 130 / 100;
			int skillLevelCalc = (task.playerSkillLevel - skillLevelReq + ((skillLevelMax-skillLevelReq) / 2)) * 200 / 300;
			if (skillLevelCalc > skillLevelMax)
				skillLevelCalc = skillLevelMax;
			Log.debug("RESOURCE: harvestComplete set castingParam=0");
			EnginePlugin.setObjectPropertyNoResponse(task.playerOid, WorldManagerClient.NAMESPACE, "castingParam", 0);

			if(Log.loggingDebug)Log.debug("RESOURCE: harvestComplete || harvestsLeft="+harvestsLeft+" currentItems="+currentItems);
			
			
			Random rand = new Random();
			int roll  = rand.nextInt(rollMax);
			if(Log.loggingDebug)	Log.debug("RESOURCE: harvestComplete |-| currentItems empty? "+currentItems.isEmpty()+" RESOURCE_GATHER_CAN_FAIL="+CraftingPlugin.RESOURCE_GATHER_CAN_FAIL+" skill="+skill+" skillLevelReq="+skillLevelReq+" rollMax="+rollMax+" skillLevelCalc="+skillLevelCalc+" rol="+roll);

			// Check if there are any items to give
			if (currentItems.isEmpty() || (CraftingPlugin.RESOURCE_GATHER_CAN_FAIL && skill > 0 && skillLevelReq > 0 && skillLevelCalc < roll)) {
				currentItems.clear();
				if (CraftingPlugin.RESOURCE_DROPS_ON_FAIL && !CraftingPlugin.RESOURCE_COUNT_IS_LOOT_COUNT)
					harvestsLeft--;
				if (harvestsLeft == 0 && harvestCount != -1) {
					despawnResource();
				} else {
					Log.debug("RESOURCE: generating items from tryHarvestResources as currentItems is empty");
				//	generateItems();
				}
				// Still send down item list (which will be empty)
				sendNoItems(task.playerOid);
				//TODO: send down some form of failed to harvest message
				EventMessageHelper.SendErrorEvent(task.playerOid, EventMessageHelper.RESOURCE_HARVEST_FAILED, 0, "");
				return;
			}

			if(Log.loggingDebug)Log.debug("RESOURCE: checking skill+"+skill+" skillupGiven="+skillupGiven);
			// Do skill up
			if (skill > 0 && !skillupGiven) {
				if(Log.loggingDebug)Log.debug("RESOURCE: checking skill: " + skill + " against playerSkillLevel: " + task.playerSkillLevel);
				if (task.playerSkillLevel < skillLevelMax) {
					CombatClient.abilityUsed(task.playerOid, skill, skillExp, skillLevelReq);
					skillupGiven = true;
				} else if (CraftingPlugin.GAIN_SKILL_AFTER_MAX && rand.nextInt(4) == 0) {
					CombatClient.abilityUsed(task.playerOid, skill, skillExp, skillLevelReq);
					skillupGiven = true;
				}
				
			}
			AgisInventoryPlugin.addListsRankingData(task.playerOid, AchievementsClient.HARVEST, skill);
				
			// Give exp
			if (CraftingPlugin.RESOURCE_HARVEST_XP_REWARD > 0) {
				InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(task.playerOid);
				int expwithBonus = CraftingPlugin.RESOURCE_HARVEST_XP_REWARD;
				if (ai != null) {
					float vipModp = 0f;
					long vipMod = 0l;
					if (ai.getBonuses().containsKey("ExpGain")) {
						vipMod = ai.getBonuses().get("ExpGain").GetValue();
						vipModp = ai.getBonuses().get("ExpGain").GetValuePercentage();
					}
					if(AgisInventoryPlugin.globalEventBonusesArray.containsKey("ExpGain")) {
						vipMod += AgisInventoryPlugin.globalEventBonusesArray.get("ExpGain").GetValue();
		      			vipModp += AgisInventoryPlugin.globalEventBonusesArray.get("ExpGain").GetValuePercentage();
		      		}
					
					expwithBonus = (int) Math.round(Math.ceil(CraftingPlugin.RESOURCE_HARVEST_XP_REWARD + vipMod + CraftingPlugin.RESOURCE_HARVEST_XP_REWARD * vipModp / 100f));
				}
				if(Log.loggingDebug)Log.debug("RESOURCE: Harvest Exp=" + CraftingPlugin.RESOURCE_HARVEST_XP_REWARD + " with bonus " + expwithBonus);
				AgisInventoryPlugin.addListsRankingData(task.playerOid, AchievementsClient.EXPERIENCE, expwithBonus);
				CombatClient.alterExpMessage expMsg = new CombatClient.alterExpMessage(task.playerOid, expwithBonus);
				Engine.getAgent().sendBroadcast(expMsg);
			}
			
			//currentItems
			if(Log.loggingDebug)Log.debug("harvestComplete harvestsLeft="+harvestsLeft);
			// Send items
			if (CraftingPlugin.AUTO_PICKUP_RESOURCES) {
				OID playerOid = task.playerOid;
				gatherAllItems(playerOid);
				this.task = null;
			} else {
				sendItems(task.playerOid);
			}
			
			// Reduce item durability
			if (weaponReq != null && !weaponReq.equals("None") && !weaponReq.equals("") && !weaponReq.equals("~ none ~")) {
				AgisInventoryClient.equippedItemUsed(task.playerOid, "Gather", weaponReq);
				//AgisInventoryPlugin.addListsRankingData(task.playerOid, AchievementsClient.HARVESTING, 1);
				
			}
		} catch (Exception e) {
			Log.exception("harvestComplete",e);
			e.printStackTrace();
		}
		Log.debug("harvestComplete End");
    }
    
	void sendItems(OID playerOID) {
		Log.debug("harvestComplete sendItems");
		if(!AgisInventoryPlugin.INVENTORY_LOOT_ON_GROUND) {//INVENTORY_LOOT_ON_GROUND
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "resource_drops");
			props.put("resourceNode", id);
			props.put("harvestCount", harvestCount);
			props.put("harvestsLeft", harvestsLeft);
			props.put("numDrops", currentItems.size());
			int dropNum = 0;
			for (int item : currentItems.keySet()) {
				props.put("drop" + dropNum, item);
				props.put("dropCount" + dropNum, currentItems.get(item));
				dropNum++;
			}
			if(Log.loggingDebug)	Log.debug("RESOURCE: Harvest sendItems props=" + props);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOID, playerOID, false, props);
			Engine.getAgent().sendBroadcast(msg);
		}else{
			AgisInventoryClient.generateGroundLoot(task.playerOid, currentItems);

			currentItems.clear();
			harvestsLeft--;
			if (harvestsLeft == 0 && harvestCount != -1) {
				despawnResource();
			} else {
				//	generateItems();
				return;
			}
		}
	}
    
	void sendNoItems(OID playerOID) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "resource_drops");
		props.put("resourceNode", id);
		props.put("harvestCount", harvestCount);
		props.put("harvestsLeft", harvestsLeft);
		props.put("numDrops", 0);

		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOID, playerOID, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public void gatherItem(OID playerOid, int itemID) {
		int playerSkillLevel = -1;
		if (skill > 0)
			playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, skill);
		if (!playerCanGather(playerOid, false, playerSkillLevel, false)) {
			return;
		}
		if (currentItems.containsKey(itemID)) {
			HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
			itemsToGenerate.put(itemID, currentItems.get(itemID));
			if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
				return;
			}
			int count = currentItems.get(itemID);
			// Add item to player
			AgisInventoryClient.generateItem(playerOid, itemID, null, count, null);
			EventMessageHelper.SendInventoryEvent(playerOid, EventMessageHelper.ITEM_HARVESTED, itemID, count, "");

			// Remove item from currentItems
			currentItems.remove(itemID);
			sendItems(playerOid);

			if (currentItems.isEmpty()) {
				harvestsLeft--;
				if (harvestsLeft == 0 && harvestCount != -1) {
					despawnResource();
				} else {
				//	generateItems();
					return;
				}
			}
		} else {
			sendItems(playerOid);
		}

	}

	public void gatherAllItems(OID playerOid) {
		int playerSkillLevel = -1;
		if (skill > 0)
			playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, skill);
		if (!playerCanGather(playerOid, false, playerSkillLevel, false)) {
			return;
		}
		HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
		for (int itemID : currentItems.keySet()) {
			// Add item to player
			itemsToGenerate.put(itemID, currentItems.get(itemID));
		}
		if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
			return;
		}
		for (int itemID : currentItems.keySet()) {
			EventMessageHelper.SendInventoryEvent(playerOid, EventMessageHelper.ITEM_HARVESTED, itemID, currentItems.get(itemID), "");
		}
		if (itemsToGenerate.size() > 0) {
			AgisInventoryClient.generateItems(playerOid, itemsToGenerate, false);
		}

		currentItems.clear();
		harvestsLeft--;
		if(!AgisInventoryPlugin.INVENTORY_LOOT_ON_GROUND)
			sendItems(playerOid);
		if (harvestsLeft == 0 && harvestCount != -1) {
			despawnResource();
		} else {
			//generateItems();
		}

	}

	public void despawnResource() {
		if(Log.loggingDebug)	Log.debug("RESOURCE: despawning resource nodeID:" + id );
		active = false;
		if(Log.loggingDebug) Log.debug("ResourceNode.despawnResource: id:"+id+" active="+active);
		lock = true;
		// Loop through players in range and send them the update
		for (OID playerOid : playersInRange) {
			sendState(playerOid);
		}

		// Schedule the respawn
		if (respawnTime > 0 || respawnTimeMax > 0) {

			int time = respawnTime;
			if (respawnTime < respawnTimeMax) {
				Random rand = new Random();
				time = respawnTime + rand.nextInt(respawnTimeMax - respawnTime);
			}
			Engine.getExecutor().schedule(this, time, TimeUnit.SECONDS);
		} else if (!CraftingPlugin.USE_RESOURCE_GROUPS || isMob) {
			WorldManagerClient.despawn(objectOID);
		}
	}

	/**
	 * Send state for players in range
	 */
	void sendState() {
		for (OID playerOid : playersInRange) {
			sendState(playerOid);
		}
	}
	/**
	 * Send state for player
	 * @param playerOid
	 */
	void sendState(OID playerOid) {
		if(Log.loggingDebug) Log.debug("ResourceNode.sendState: id:"+id+" active="+active);
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "resource_state");
		props.put("nodeID", id);
		props.put("active", active);
		props.put("set", settingId);
		props.put("dist", (int)(maxHarvestDistance * 1000));
		props.put("ac", activeCoordinatedEffect);
		props.put("dc", deactiveCoordinatedEffect);
		props.put("cool", (int) (cooldown * 1000));
		props.put("del", (int) (deactivationDelay * 1000));
		if(Log.loggingDebug)Log.debug("RESOURCE: despawning resource sendState -> nodeID:" + id + "; active:" + active + "; playerOid:" + playerOid);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public void printType() {
		Log.debug("Resource Node");
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}
    
    public String getName() { return name; }
    public void setName(String name) {
    	this.name = name;
    }
    
    public String getGameObject() { return gameObject; }
    public void setGameObject(String gameObject) {
    	this.gameObject = gameObject;
    }
    
    public String getHarvestCoordEffect() { return harvestCoordinatedEffect; }
    public void setHarvestCoordEffect(String coordinatedEffect) {
    	this.harvestCoordinatedEffect = coordinatedEffect;
    }
    
    public String getActivateCoordEffect() { return activeCoordinatedEffect; }
    public void setActivateCoordEffect(String coordinatedEffect) {
    	this.activeCoordinatedEffect = coordinatedEffect;
    }
    
    public String getDeactivateCoordEffect() { return deactiveCoordinatedEffect; }
    public void setDeactivateCoordEffect(String coordinatedEffect) {
    	this.deactiveCoordinatedEffect = coordinatedEffect;
    }

    public AOVector getLoc() { return loc; }
    public void setLoc(AOVector loc) {
    	this.loc = loc;
    }
    
    public HashMap<String, Serializable> getProps() { return props; }
    public void setProps(HashMap<String, Serializable> props) {
    	this.props = props;
    }
    
    public OID getInstanceOID() { return instanceOID; }
    public void setInstanceOID(OID instanceOID) {
    	this.instanceOID = instanceOID;
    }
    
    public OID getObjectOID() { return objectOID; }
    public void setObjectOID(OID objectOID) {
    	this.objectOID = objectOID;
    }
    
    public boolean getEquippedReq() { return equippedReq; }
    public void setEquippedReq(boolean equippedReq) {
    	this.equippedReq = equippedReq;
    }
    
    public int getSkill() { return skill; }
    public void setSkill(int skill) {
    	this.skill = skill;
    }
    
    public int getSkillLevelReq() { return skillLevelReq; }
    public void setSkillLevelReq(int skillLevelReq) {
    	this.skillLevelReq = skillLevelReq;
    	if (this.skillLevelReq > skillLevelMax) 
    		this.skillLevelMax = this.skillLevelReq;
    }
    
    public int getSkillLevelMax() { return skillLevelMax; }
    public void setSkillLevelMax(int skillLevelMax) {
    	this.skillLevelMax = skillLevelMax;
    	if (this.skillLevelMax < this.skillLevelReq)
    		this.skillLevelMax = this.skillLevelReq;
    }
    
    public int getSkillExp() { return skillExp; }
    public void setSkillExp(int skillexp) {
    	this.skillExp = skillexp;
    }
    
    public String getWeaponReq() { return weaponReq; }
    public void setWeaponReq(String weaponReq) {
    	this.weaponReq = weaponReq;
    }

    public boolean getActive() { return active; }
    public void setActive(boolean active) {
    	this.active = active;
    }
    
    public int getRespawnTime() { return respawnTime; }
    public void setRespawnTime(int respawnTime) {
    	this.respawnTime = respawnTime;
    }
    
    public int getRespawnTimeMax() { return respawnTimeMax; }
    public void setRespawnTimeMax(int respawnTimeMax) {
    	this.respawnTimeMax = respawnTimeMax;
    }
    
    public int getHarvestCount() { return harvestCount; }
    public void setHarvestCount(int harvestCount) {
    	this.harvestCount = harvestCount;
    }
    
    public float getHarvestTimeReq() { return harvestTimeReq; }
    public void setHarvestTimeReq(float harvestTimeReq) {
    	this.harvestTimeReq = harvestTimeReq;
    }
    
    public float getMaxHarvestDistance() { return maxHarvestDistance; }
    public void setMaxHarvestDistance(float maxHarvestDistance) {
    	this.maxHarvestDistance = maxHarvestDistance;
    }
    public int getProfileId() { return profileId; }
    public void setProfileId(int profileId) {
    	this.profileId = profileId;
    }
    
    public int getSettinhgId() { return settingId; }
    public void setSettingId(int settingId) {
    	this.settingId = settingId;
    }
    
    public float getCooldown() { return cooldown; }
    public void setCooldown(float cooldown) {
    	this.cooldown = cooldown;
    }
    
    public float getDeactivationDelay() { return deactivationDelay; }
    public void setDeactivationDelay(float deactivationDelay) {
    	this.deactivationDelay = deactivationDelay;
    }
    
    public int getLootMaxCount() { return lootMaxCount; }
    public void setLootMaxCount(int lootMaxCount) {
    	this.lootMaxCount = lootMaxCount;
    }
  
    public boolean getEnsureLoot() { return ensureLoot; }
    public void setEnsureLoot(boolean ensureLoot) {
    	this.ensureLoot = ensureLoot;
    }
    
    public boolean isLock() {
    	return lock;
    }
    
    int id;
    String name;
    int settingId = -1;
    int profileId = -1;
    int skill;
    int skillLevelReq;
    int skillLevelMax;
    int skillExp=0;
    String weaponReq;
    boolean equippedReq = false;
    String gameObject;
    String harvestCoordinatedEffect;
    String activeCoordinatedEffect;
    String deactiveCoordinatedEffect;
    AOVector loc;
    int respawnTime;
    int respawnTimeMax;
     OID instanceOID;
    OID objectOID;
    HashMap<String, Serializable> props;
    int harvestCount;
    int harvestsLeft;
    float harvestTimeReq = 0;
    float maxHarvestDistance = 3;
    float cooldown = 0F;
    float deactivationDelay = 0F;
    boolean lock=false;
    List<ResourceDrop> drops = new LinkedList<ResourceDrop>();
    HashMap<Integer, Integer> currentItems;
    boolean skillupGiven = false;
    boolean active;
    Long eventSub = null;
    //LinkedList<OID> playersInRange = new LinkedList<OID>();
    Set<OID> playersInRange = ConcurrentHashMap.newKeySet();
    boolean isMob = false;
    int lootMaxCount = 1;
    boolean ensureLoot = true;
    HarvestTask task;
    Long sub = null;
    ResourceNodeEntity resourceNodeEntity;
    
    /**
     * A Runnable class that adds an object to the claim when it is run. 
     * @author Andrew Harrison
     *
     */
    public class HarvestTask implements Runnable {
    	
    	protected AOVector loc;
    	protected Quaternion orient;
    	protected OID playerOid;
    	protected int playerSkillLevel;
    	protected ResourceNode resourceNode;
    	protected boolean interrupted;
    	protected CoordinatedEffectState coordinatedEffectState;
    	public HarvestTask() {
    		
    	}
    	
    	public void StartHarvestTask(AOVector loc, Quaternion orient, OID playerOid, int playerSkillLevel, ResourceNode resourceNode) {
    		Log.debug("RESOURCE: creating new harvest task");
    		this.loc = loc;
    		this.orient = orient;
    		this.playerOid = playerOid;
    		this.playerSkillLevel = playerSkillLevel;
    		this.resourceNode = resourceNode;
    	}
    	
    	public void sendStartHarvestTask(float length) {
    		Log.debug("RESOURCE: sending start harvest task");
    		Map<String, Serializable> props = new HashMap<String, Serializable>();
    	 	EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "castingParam", 1);
    	    
        	props.put("ext_msg_subtype", "start_harvest_task");
    		props.put("length", length);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
	
    		// Send animation
        	CoordinatedEffect cE = new CoordinatedEffect(resourceNode.harvestCoordinatedEffect);
    	    cE.sendSourceOid(true);
    	    cE.sendTargetOid(true);
    	    cE.putArgument("length", length);
    	    cE.putArgument("resourceNodeID", id);
			coordinatedEffectState = cE.invoke(playerOid, playerOid);
    	}
    	
		@Override
		public void run() {
			Log.debug("RESOURCE: Task run");
			if (resourceNode.sub != null)
                Engine.getAgent().removeSubscription(resourceNode.sub);
			Log.debug("RESOURCE: removeSubscription");
			
			if (interrupted) {
				Log.debug("BUILD: task was interrupted, not completing run");
				resourceNode.task = null;
				return;
			}
			Log.debug("RESOURCE: removeSubscription");
			
			// Dead check
	    	boolean dead = true;
	    	try {
				dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
			} catch (Exception e) {
			}
	    	if (!dead) {
	    		Log.debug("RESOURCE: player not dead run harvestComplete");
					resourceNode.harvestComplete(this);
	    	}else {
	    		Log.debug("RESOURCE: player is dead");
	    	}
	    	resourceNode.task = null;
			coordinatedEffectState = null;
	    	Log.debug("RESOURCE: Task End");
		}
		
		public void interrupt() {
			interrupted = true;
			Map<String, Serializable> props = new HashMap<String, Serializable>();
		 	EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "castingParam", -1);
	         if(coordinatedEffectState != null)
				 coordinatedEffectState.invokeCancel();
        	props.put("ext_msg_subtype", "harvest_task_interrupted");
        	TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    				playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
		}
    }
    
    
    /**
     * Sub-class needed for the interpolated world node so a perceiver can be created.
     * @author Andrew
     *
     */
	public class ResourceNodeEntity extends ObjectStub implements EntityWithWorldNode
	{

		public ResourceNodeEntity(OID oid, InterpolatedWorldNode node) {
	    	setWorldNode(node);
	    	setOid(oid);
	    }
		
		private static final long serialVersionUID = 1L;

	}
	
	private static final long serialVersionUID = 1L;

	
}
