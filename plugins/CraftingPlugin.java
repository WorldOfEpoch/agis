package atavism.agis.plugins;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.core.Agis;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.ItemDatabase;
import atavism.agis.effects.MountEffect;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.CraftingClient.CreateResourceNodeFromMobMessage;
import atavism.agis.plugins.CraftingClient.DestroyMobResourceNodeMessage;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

/**
 * Plugin for managing Crafting and Resource gathering.
 * @author Andrew Harrison
 *
 */
public class CraftingPlugin extends EnginePlugin {
	
	public CraftingPlugin()
	{
		super(CRAFTING_PLUGIN_NAME);
		setPluginType("Crafting");
	}
	
	public static String CRAFTING_PLUGIN_NAME = "CraftingPlugin";
	
	public String getName() {
		return CRAFTING_PLUGIN_NAME;
	}

	public void onActivate() {
		Log.debug("CraftingPlugin.onActivate()");
		registerHooks();
		
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
        filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		filter.addType(InstanceClient.MSG_TYPE_INSTANCE_LOADED);
		filter.addType(CraftingClient.MSG_TYPE_MINIGAME_WON);
		filter.addType(CraftingClient.MSG_TYPE_CREATE_RESOURCE_NODE_FROM_MOB);
		filter.addType(CraftingClient.MSG_TYPE_DESTROY_MOB_RESOURCE_NODE);
		filter.addType(CraftingClient.MSG_TYPE_HARVEST_RESOURCE);
		filter.addType(CraftingClient.MSG_TYPE_GATHER_RESOURCE);
		filter.addType(CraftingClient.MSG_TYPE_CRAFTING_CRAFT_ITEM);
		filter.addType(CraftingClient.MSG_TYPE_CRAFTING_GRID_UPDATED);
		filter.addType(CraftingClient.MSG_TYPE_GET_BLUEPRINTS);
		Engine.getAgent().createSubscription(filter, this);
		Log.debug("CRAFTING: completed Plugin activation");
		
		cDB = new ContentDatabase(true);
		
		loadData();
        
	}
	
	protected void registerHooks() {
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_LOADED, new InstanceLoadedHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_MINIGAME_WON, new MinigameWonHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_CREATE_RESOURCE_NODE_FROM_MOB, new CreateResourceNodeFromMobHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_DESTROY_MOB_RESOURCE_NODE, new DestroyResourceNodeHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_HARVEST_RESOURCE, new HarvestResourceHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_GATHER_RESOURCE, new GatherResourceHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_CRAFTING_CRAFT_ITEM, new CraftItemHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_CRAFTING_GRID_UPDATED, new CraftingGridUpdatedHook());
		getHookManager().addHook(CraftingClient.MSG_TYPE_GET_BLUEPRINTS, new SendBlueprintHook());
	}
	
	void loadData() {
		String gridSize = cDB.loadGameSetting("GRID_SIZE");
        if (gridSize != null)
        	GRID_SIZE = Integer.parseInt(gridSize);
		String resourceDropsOnFail = cDB.loadGameSetting("RESOURCE_DROPS_ON_FAIL");
        if (resourceDropsOnFail != null)
        	RESOURCE_DROPS_ON_FAIL = Boolean.parseBoolean(resourceDropsOnFail);
        Log.debug("GameSettings: Set RESOURCE_DROPS_ON_FAIL to: " + RESOURCE_DROPS_ON_FAIL);

        String autoPickupResources = cDB.loadGameSetting("AUTO_PICKUP_RESOURCES");
		 if (autoPickupResources != null)
        	AUTO_PICKUP_RESOURCES = Boolean.parseBoolean(autoPickupResources);
		 Log.debug("GameSettings: Set AUTO_PICKUP_RESOURCES to: " + AUTO_PICKUP_RESOURCES);
			
		 String resourceGatherDistance = cDB.loadGameSetting("RESOURCE_GATHER_DISTANCE");
        if (resourceGatherDistance != null)
        	RESOURCE_GATHER_DISTANCE = Integer.parseInt(resourceGatherDistance);
        Log.debug("GameSettings: Set RESOURCE_GATHER_DISTANCE to: " + RESOURCE_GATHER_DISTANCE);
		
        String gatherCanFail = cDB.loadGameSetting("RESOURCE_GATHER_CAN_FAIL");
        if (gatherCanFail != null)
        	RESOURCE_GATHER_CAN_FAIL = Boolean.parseBoolean(gatherCanFail);
        Log.debug("GameSettings: Set RESOURCE_GATHER_CAN_FAIL to: " + RESOURCE_GATHER_CAN_FAIL);
		
        String deleteRecipeOnUse = cDB.loadGameSetting("DELETE_CRAFTING_RECIPE_ON_USE");
        if (deleteRecipeOnUse != null)
        	DELETE_CRAFTING_RECIPE_ON_USE = Boolean.parseBoolean(deleteRecipeOnUse);
        Log.debug("GameSettings: Set DELETE_CRAFTING_RECIPE_ON_USE to: " + DELETE_CRAFTING_RECIPE_ON_USE);
		 
        String skinningSkillID = cDB.loadGameSetting("SKINNING_SKILL_ID");
        if (skinningSkillID != null)
        	SKINNING_SKILL_ID = Integer.parseInt(skinningSkillID);
        Log.debug("GameSettings: Set SKINNING_SKILL_ID to: " + SKINNING_SKILL_ID);
		
       String skinningMaxDistance = cDB.loadGameSetting("SKINNING_MAX_DISTANCE");
        if (skinningMaxDistance != null)
        	SKINNING_MAX_DISTANCE = Float.parseFloat(skinningMaxDistance);
        Log.debug("GameSettings: Set SKINNING_MAX_DISTANCE to: " + SKINNING_MAX_DISTANCE);
		
        
        
        String skinningWeaponReq = cDB.loadGameSetting("SKINNING_WEAPON_REQ");
        if (skinningWeaponReq != null)
        	SKINNING_WEAPON_REQ = skinningWeaponReq;
        Log.debug("GameSettings: Set SKINNING_WEAPON_REQ to: " + SKINNING_WEAPON_REQ);
		
        String resourceCountIsLootCount = cDB.loadGameSetting("RESOURCE_COUNT_IS_LOOT_COUNT");
        if (resourceCountIsLootCount != null)
        	RESOURCE_COUNT_IS_LOOT_COUNT = Boolean.parseBoolean(resourceCountIsLootCount);
        Log.debug("GameSettings: Set RESOURCE_COUNT_IS_LOOT_COUNT to: " + RESOURCE_COUNT_IS_LOOT_COUNT);
		
        String useResourceGroups = cDB.loadGameSetting("USE_RESOURCE_GROUPS");
        if (useResourceGroups != null)
        	USE_RESOURCE_GROUPS = Boolean.parseBoolean(useResourceGroups);
        Log.debug("GameSettings: Set USE_RESOURCE_GROUPS to: " + USE_RESOURCE_GROUPS);
		
        String resourceGroupSize = cDB.loadGameSetting("RESOURCE_GROUP_SIZE");
        if (resourceGroupSize != null)
        	RESOURCE_GROUP_SIZE = Integer.parseInt(resourceGroupSize);
        Log.debug("GameSettings: Set RESOURCE_GROUP_SIZE to: " + RESOURCE_GROUP_SIZE);
		
        String canHarvestWhileMounted = cDB.loadGameSetting("CAN_HARVEST_WHILE_MOUNTED");
        if (canHarvestWhileMounted != null)
        	CAN_HARVEST_WHILE_MOUNTED = Boolean.parseBoolean(canHarvestWhileMounted);
        Log.debug("GameSettings: Set CAN_HARVEST_WHILE_MOUNTED to: " + CAN_HARVEST_WHILE_MOUNTED);
		
        String canCraftWhileMounted = cDB.loadGameSetting("CAN_CRAFT_WHILE_MOUNTED");
        if (canCraftWhileMounted != null)
        	CAN_CRAFT_WHILE_MOUNTED = Boolean.parseBoolean(canCraftWhileMounted);
        Log.debug("GameSettings: Set CAN_CRAFT_WHILE_MOUNTED to: " + CAN_CRAFT_WHILE_MOUNTED);
		
        String resourceHarvestXpReward = cDB.loadGameSetting("RESOURCE_HARVEST_XP_REWARD");
        if (resourceHarvestXpReward != null)
        	RESOURCE_HARVEST_XP_REWARD = Integer.parseInt(resourceHarvestXpReward);
        Log.debug("GameSettings: Set RESOURCE_HARVEST_XP_REWARD to: " + RESOURCE_HARVEST_XP_REWARD);
		
        String gridBasedCrafting = cDB.loadGameSetting("GRID_BASED_CRAFTING");
        if (gridBasedCrafting != null)
        	GRID_BASED_CRAFTING = Boolean.parseBoolean(gridBasedCrafting);
        Log.debug("GameSettings: Set GRID_BASED_CRAFTING to: " + GRID_BASED_CRAFTING);
        
        recourceNodeProfile.putAll(cDB.loadResourceNodeProfile());
        ItemDatabase iDB = new ItemDatabase(false);
		recipes = iDB.loadCraftingRecipes();
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("CraftingPlugin ReloadTemplates Start");
		loadData();
		for (OID instanceOid : resourceNodes.keySet()) {
			if (resourceNodes.containsKey(instanceOid)) {
				Log.debug("RESOURCE: got resource instance: " + instanceOid);
				for (Integer resourceID : resourceNodes.get(instanceOid).keySet()) {
					if (resourceNodes.get(instanceOid).containsKey(resourceID)) {
						Log.debug("RESOURCE: got resource");
						resourceNodes.get(instanceOid).get(resourceID).applySettings();
					}
				}
			}
			Log.error("CraftingPlugin ReloadTemplates End");
		}
	}

	class SpawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            if (spawnedMsg.getType() != null && spawnedMsg.getType().isPlayer()) {            
            	// Set the players world property
            	Log.debug("SPAWNED: getting claims for player: " + objOid);
            	OID instanceOid = spawnedMsg.getInstanceOid();
            	Point p = WorldManagerClient.getObjectInfo(objOid).loc;
            	if (CraftingPlugin.USE_RESOURCE_GROUPS) {
            		if (resourceNodeGroups.containsKey(instanceOid)) {
            			for (ResourceNodeGroup nodeGroup : resourceNodeGroups.get(instanceOid).values()) {
                	    	float distance = Point.distanceToSquaredXZ(p, new Point(nodeGroup.getLoc()));
                	    	if (distance < 100000) {
                	    		nodeGroup.addPlayer(objOid);
                	    	}
                	    }
            		}
            	} else {
            		for (ResourceNode rNode : resourceNodes.get(instanceOid).values()) {
            	    	float distance = Point.distanceToSquaredXZ(p, new Point(rNode.getLoc()));
            	    	if (distance < 100000) {
            	    		rNode.addPlayer(objOid);
            	    	}
            	    }
            	}
        	    
        	    // Clear current task
    			EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "currentTask", "");
            }
            
            
            
            return true;
    	}
    }
	
	class DespawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();
            if (despawnedMsg.getType() != null && despawnedMsg.getType().isPlayer()) {
				OID instanceOid = despawnedMsg.getInstanceOid();
				// Remove the player from the resource Nodes in the instance they despawned from
				if (resourceNodes.containsKey(instanceOid)) {
					for (ResourceNode rNode : resourceNodes.get(instanceOid).values()) {
						rNode.removePlayer(objOid);
					}
				}
			} else if (despawnedMsg.getType() != null && despawnedMsg.getType().isMob()) {
				OID instanceOid = despawnedMsg.getInstanceOid();
				int nodeID = (int) (objOid.toLong() * -1);
				Log.debug("Crafting: Despawn: instance:" + instanceOid + " nodeId:" + nodeID);
				if (resourceNodes.containsKey(instanceOid)) {
					if (resourceNodes.get(instanceOid).containsKey(nodeID)) {
						Log.debug("RESOURCE: despawning mobs resource node: " + nodeID);
						resourceNodes.get(instanceOid).get(nodeID).despawnResource();
						resourceNodes.get(instanceOid).remove(nodeID);
					}
				}
			}

			return true;
		}
	}
	
	/**
     * Hook for the InstanceLoadedMessage. The message is sent when an instance is ready for objects
     * to be loaded in and this hook will load the resource nodes for the instance.
     */
	class InstanceLoadedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		SubjectMessage message = (SubjectMessage) msg;
            OID instanceOid = message.getSubject();
            Log.debug("VOXEL: got instance loaded message with oid: " + instanceOid);
            int instanceID = (Integer)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
            resourceNodes.put(instanceOid, cDB.loadResourceNodes(instanceID, instanceOid, resourceNodeGroups));
    		return true;
    	}
	}
	
	class MinigameWonHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.debug("RESOURCE: got create mini game hook message");
			ExtensionMessage message = (ExtensionMessage)msg;
			OID playerOid = message.getSubject();
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			String mode = (String)message.getProperty("mode");
			int resourceID = (Integer)message.getProperty("resourceID");
			if (resourceNodes.containsKey(instanceOid)) {
            	Log.debug("RESOURCE: got resource instance: " + instanceOid + " looking for node: " + resourceID);
            	if (resourceNodes.get(instanceOid).containsKey(resourceID)) {
            		Log.debug("RESOURCE: got resource");
            		FishingResourceNode fishingNode = (FishingResourceNode)resourceNodes.get(instanceOid).get(resourceID);
            		if(mode.equals("Start")) {
            			fishingNode.startFishing(playerOid, (Float)message.getProperty("length"));
            		} else if(mode.equals("Finish")) {
            			int item = (Integer)message.getProperty("itemID");
	            		fishingNode.setItem(item);
	            		fishingNode.tryHarvestResources(playerOid);
            		}
            	}
            }
			return true;
		}
	}
	
	class CreateResourceNodeFromMobHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	CreateResourceNodeFromMobMessage gridMsg = (CreateResourceNodeFromMobMessage)msg;
            Log.debug("RESOURCE: got create resource node message");
            OID mobOid = gridMsg.getSubject();
            if (mobOid == null)
            	return true;
            
            int lootTable = gridMsg.getLootTable();
            int skillLevelReq = gridMsg.getSkillLevelReq();
            int skillLevelMax = gridMsg.getSkillLevelMax();
            int skillExp = gridMsg.getSkillExp();
            int skillId = gridMsg.getSkillId();
            String weaponReq = gridMsg.getWeaponReq();
            float harvestTime = gridMsg.getHarvestTime();
            
            int nodeID = (int)(mobOid.toLong() * -1);
            Log.debug("RESOURCE: got create resource node nodeID="+nodeID);
            BasicWorldNode mobWNode = WorldManagerClient.getWorldNode(mobOid);
            ResourceNode resourceNode = new ResourceNode(nodeID, new AOVector(mobWNode.getLoc()), mobWNode.getInstanceOid());
            resourceNode.setName("mob");
			resourceNode.setGameObject("");
			resourceNode.setHarvestCoordEffect("SkinningEffect");
			resourceNode.setSkill(skillId);
			resourceNode.setSkillLevelReq(skillLevelReq);
			resourceNode.setSkillLevelMax(skillLevelMax);
			resourceNode.setMaxHarvestDistance(SKINNING_MAX_DISTANCE);
			if(skillExp > 0)
				resourceNode.setSkillExp(skillExp);
			resourceNode.setWeaponReq(weaponReq);
			resourceNode.setEquippedReq(false);
			resourceNode.setRespawnTime(-1); // Set to -1 so it wont respawn
			resourceNode.setHarvestCount(1);
			resourceNode.setHarvestTimeReq(harvestTime);
			
			// Add resource drops
			Log.debug("LOOT: lootManager has: " + Agis.LootTableManager.getMap()+" lootTable="+lootTable);
			
			LootTable lt = Agis.LootTableManager.get(lootTable);
			for (int i = 0; i < lt.getItems().size(); i++) {
				int templateID = lt.getItems().get(i);
				if (templateID > -1) {
					int count = lt.getItemCounts().get(i);
					int countMax = lt.getItemMaxCounts().get(i);
					resourceNode.AddResourceDrop(templateID, count, countMax, lt.getItemChances().get(i), lt.getItemChances().get(i));
				}
			}
            
			resourceNode.setMobAsSkinnable(mobOid);
			// Save resource node
            resourceNodes.get(mobWNode.getInstanceOid()).put(nodeID, resourceNode);
            
            // Set mob as skinnable
            EnginePlugin.setObjectProperty(mobOid, Namespace.WORLD_MANAGER, "skinnableLevel", skillLevelReq);
            Log.debug("RESOURCE: got create resource node END");
            return true;
        }
	}
	
	class DestroyResourceNodeHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	DestroyMobResourceNodeMessage gridMsg = (DestroyMobResourceNodeMessage)msg;
            Log.debug("RESOURCE: got destroy resource message");
            
            OID mobOid = gridMsg.getSubject();
            if (mobOid == null)
            	return true;
            
            OID instanceOid = gridMsg.getInstanceOid();
            
            int nodeID = (int)(mobOid.toLong() * -1);
            resourceNodes.get(instanceOid).get(nodeID).despawnResource();
            resourceNodes.get(instanceOid).remove(nodeID);
            
            return true;
        }
	}
    
    /**
	 * Hook for the Harvest Resource Message. Used when a player attempts to start harvesting 
	 * from a ResourceNode.
	 * @author Andrew Harrison
	 *
	 */
	class HarvestResourceHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            Log.debug("RESOURCE: got harvest resource message");
            OID playerOid = gridMsg.getSubject();
            OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
            int resourceID = (Integer)gridMsg.getProperty("resourceID");
            if (resourceNodes.containsKey(instanceOid)) {
            	Log.debug("RESOURCE: got resource instance: " + instanceOid + " looking for node: " + resourceID);
            	if (resourceNodes.get(instanceOid).containsKey(resourceID)) {
            		Log.debug("RESOURCE: got resource"); 
            		resourceNodes.get(instanceOid).get(resourceID).tryHarvestResources(playerOid);
            	}
            }
            return true;
        }
	}
	
	/**
	 * Hook for the Gather Resource Message. Used when a player is gathering one (or all) of the items they 
	 * have harvested from the ResourceNode.
	 * @author Andrew Harrison
	 *
	 */
	class GatherResourceHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            Log.debug("RESOURCE: got gather resource message");
            OID playerOid = gridMsg.getSubject();
            OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
            int resourceID = (Integer)gridMsg.getProperty("resourceID");
            boolean gatherAll = (Boolean)gridMsg.getProperty("gatherAll");
            if (resourceNodes.containsKey(instanceOid)) {
            	Log.debug("RESOURCE: got resource instance: " + instanceOid + " looking for node: " + resourceID);
            	if (resourceNodes.get(instanceOid).containsKey(resourceID)) {
            		Log.debug("RESOURCE: got resource"); 
            		if (gatherAll) {
            			resourceNodes.get(instanceOid).get(resourceID).gatherAllItems(playerOid);
            		} else {
            			int itemID = (Integer)gridMsg.getProperty("itemID");
            			resourceNodes.get(instanceOid).get(resourceID).gatherItem(playerOid, itemID);
            		}
            	}
            }
            return true;
        }
	}
	
	/**
	 * Hook for the Craft Item Message. Calls either DoGridCraft() or DoStandardCraft()
	 * based on the GRID_BASED_CRAFTING value.
	 * @author Andrew
	 *
	 */
	class CraftItemHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
			OID playerOid = eMsg.getSubject();
			
			int recipeId = (Integer) eMsg.getProperty("RecipeId");
			String stationType = (String) eMsg.getProperty("stationType");
			int recipeItemID = (Integer) eMsg.getProperty("recipeItemID");
			Log.debug("CRAFT: "+playerOid+" start craft recipe:"+recipeId+" on station:"+stationType+" recipeItem:"+recipeItemID);
			
			// Death check
	    	boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
	    	if (dead) {
	    		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_DEAD, 0, "");
	    		return true;
	    	}
	    	
	    	// Busy check
	    	String currentTask = (String)EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
	    	if (currentTask != null && !currentTask.equals("")) {
	    		Log.debug("CRAFT: Player "+playerOid+" is  busy");
	    			// Player is currently on another task
	    		return true;
	    	}
	    	
	    	// Mounted Check
	    	if (!CraftingPlugin.CAN_CRAFT_WHILE_MOUNTED) {
	    		String mount = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, MountEffect.MOUNT_PROP);
	        	if (mount != null && !mount.isEmpty()) {
	        		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_MOUNTED, 0, "");
	        		return false;
	        	}
	    	}
	 		Log.debug("CRAFT: Player "+playerOid+" Grid?:"+GRID_BASED_CRAFTING);
	 		if(eMsg.getProperty("count") != null){
				DoCraftBook(playerOid, recipeId, stationType, eMsg);
			}else if (GRID_BASED_CRAFTING) {
				DoGridCraft(playerOid, recipeId, recipeItemID, stationType, eMsg);
			} else  {
				DoStandardCraft(playerOid, recipeId, stationType, eMsg);
			}
			
		    return true;
		}
	}
	
	/**
	 * Attempts to craft an item based on the grid based crafting system.
	 * @param playerOid
	 * @param recipeId
	 * @param recipeItemID
	 * @param stationType
	 * @param eMsg
	 */
	void DoGridCraft(OID playerOid, int recipeId, int recipeItemID, String stationType, ExtensionMessage eMsg) {
		LinkedList<Long> components = null;
		LinkedList<Integer> componentCounts = null;
		CraftingRecipe recipe = null;
		Log.debug("CRAFTING DoGridCraft");
		InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(playerOid);
		int bonusModTime = 0;
		float bonusModTimep = 0;
		if (ai.getBonuses().containsKey("CraftingTime")) {
			bonusModTime = ai.getBonuses().get("CraftingTime").GetValue();
			bonusModTimep = ai.getBonuses().get("CraftingTime").GetValuePercentage();
		}
		if (AgisInventoryPlugin.globalEventBonusesArray.containsKey("CraftingTime")) {
			bonusModTime += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValue();
			bonusModTimep += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValuePercentage();
		}
		Log.debug("CRAFTING: CraftingTime bonusModTime=" + bonusModTime + " bonusModTimep=" + bonusModTimep);
		if (recipeItemID > 0) {
			CraftingRecipe resultRecipe = null;
			for (CraftingRecipe tempRecipe : recipes.values()) {
				if (tempRecipe.getRecipeItemId() == recipeItemID) {
					resultRecipe = tempRecipe;
				}
			}

			Log.debug("CRAFTING: resultRecipe: " + resultRecipe);
			// Load components in
			LinkedList<Integer> reqComponents = resultRecipe.getRequiredItems();
			componentCounts = resultRecipe.getRequiredItemCounts();
			Log.debug("CRAFTING: reqComponents="+reqComponents+" componentCounts="+componentCounts+" stationType="+stationType+" resultRecipe.getStationReq="+resultRecipe.getStationReq());
			if (AgisInventoryClient.checkComponents(playerOid, reqComponents, componentCounts) && (resultRecipe.getStationReq().equals(stationType) || resultRecipe.getStationReq().contains("Any"))) {

				recipe = resultRecipe;
				long time = (long) ((recipe.getCreationTime() + bonusModTime+(recipe.getCreationTime()*bonusModTimep/100D)));
				
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "CraftingMsg");
				props.put("PluginMessageType", "CraftingStarted");
				props.put("creationTime", (int)time);
				TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);

				Engine.getAgent().sendBroadcast(playerMsg);

				CraftingTask task = new CraftingTask(recipe, playerOid, recipeId, 1);
				Engine.getExecutor().schedule(task, time, TimeUnit.SECONDS);

				// Play coord effect
				String coordEffect = (String) eMsg.getProperty("coordEffect");
				task.PlayCoordinatedEffect(coordEffect);

				// Delete recipe
				if (DELETE_CRAFTING_RECIPE_ON_USE)
					AgisInventoryClient.removeGenericItem(playerOid, recipeItemID, false, 1);
				return;
			}
		} else {
			Log.debug("CRAFTING: got craft item message with recipeID: " + recipeId);
			if (!recipes.containsKey(recipeId)) {
				return;
			}
			
			recipe = recipes.get(recipeId);
			Log.debug("CRAFTING: got recipe: " + recipe);
			LinkedList<Integer> componentIDs = recipe.getRequiredItems();
			LinkedList<Integer> componentReqCounts = recipe.getRequiredItemCounts();
			Log.debug("CRAFTING: componentIDs="+componentIDs+" componentReqCounts="+componentReqCounts+" stationType="+stationType+" recipe.getStationReq="+recipe.getStationReq());
			if (!recipe.getStationReq().equals(stationType) && !recipe.getStationReq().equals("none") && !recipe.getStationReq().contains("Any")) {
				Log.debug("CRAFTING: Station Error");
				return;
			}
			Log.debug("CRAFTING: Check Skill");
				
			// Skill Level Check
			if (recipe.getSkillID() > 0) {
				int playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, recipe.getSkillID());
				Log.debug("CRAFTING: checking skill: " + recipe.getSkillID() + " against playerSkillLevel: " + playerSkillLevel);
				if (playerSkillLevel < recipe.getRequiredSkillLevel()) {
					// Send message saying skill level too low
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SKILL_LEVEL_TOO_LOW, recipe.getSkillID(), "" + recipe.getRequiredSkillLevel());
					return;
				}
			}
			
			components = (LinkedList<Long>) eMsg.getProperty("components");
			componentCounts = (LinkedList<Integer>) eMsg.getProperty("componentCounts");
			
			/*LinkedList<CraftingComponent> craftingComponents = new LinkedList<CraftingComponent>();
			for (int i = 0; i < componentIDs.size(); i++) {
				craftingComponents.add(new CraftingComponent("", componentCounts.get(i), componentIDs.get(i)));
			}*/
			Log.debug("CRAFTING: componentIDs="+componentIDs+" componentReqCounts="+componentReqCounts+" components="+components+" componentCounts="+componentCounts);
			
			if(AgisInventoryClient.checkSpecificComponents(playerOid, componentIDs, componentReqCounts, components, componentCounts))
			{
				long time = (long) ((recipe.getCreationTime() + bonusModTime+(recipe.getCreationTime()*bonusModTimep/100D)));
				
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "CraftingMsg");
				props.put("PluginMessageType", "CraftingStarted");
				props.put("creationTime",(int) time);
				TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	
				Engine.getAgent().sendBroadcast(playerMsg);
				Log.debug("CRAFTING: componentCounts:"+componentCounts+" | componentReqCounts:"+componentReqCounts+" | "+componentReqCounts.size()+" | "+components+" | "+componentIDs);
				// Set component counts so only the ones required are used
				for (int i = 0; i < componentReqCounts.size(); i++) {
					Log.debug("CRAFTING: componentCounts:"+componentCounts+" | componentReqCounts:"+componentReqCounts.get(i));
					if (componentCounts.size()>i)
							componentCounts.add(i, componentReqCounts.get(i));
					else
							componentCounts.set(i, componentReqCounts.get(i));
					
					Log.debug("CRAFTING: setting componentCounts pos: " + i + " to: " + componentCounts);
				}
				Log.debug("CRAFTING: Create Task");
				CraftingTask task = new CraftingTask(recipe, components, componentCounts, playerOid, recipeId);
				Engine.getExecutor().schedule(task, time, TimeUnit.SECONDS);
				Log.debug("CRAFTING: Send CordEffect");
				
				// Play coord effect
				String coordEffect = (String) eMsg.getProperty("coordEffect");
				task.PlayCoordinatedEffect(coordEffect);
				return;
			}else{
				Log.debug("CRAFTING: checkSpecificComponents Error");
				
			}
		}
		
		Log.debug("CRAFTING PLUGIN: Player doesn't have the required Components in their Inventory");
			
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "CraftingMsg");
		props.put("PluginMessageType", "CraftingFailed");
		props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
				playerOid, false, props);
			
		Engine.getAgent().sendBroadcast(playerMsg);
	}
	
	/**
	 * Attempts to craft an item based on the standard crafting system (player doesn't need to place items in a grid).
	 * @param playerOid
	 * @param recipeId
	 * @param stationType
	 * @param eMsg
	 */
	void DoStandardCraft(OID playerOid, int recipeId, String stationType, ExtensionMessage eMsg) {
		String recipeName = (String) eMsg.getProperty("ItemName");
		Log.debug("CRAFTING:DoStandardCraft  got recipe: " + recipeName);
		
		LinkedList<Integer> componentIds = new LinkedList<Integer>(); //(LinkedList<Integer>) cimsg.getProperty("ItemIds");
		Log.debug("CRAFTING: 1: ");
			LinkedList<Integer> componentStacks = (LinkedList<Integer>) eMsg.getProperty("ItemStacks");
		Log.debug("CRAFTING: 2: ");
		
		CraftingRecipe recipe = recipes.get(recipeId);
		Log.debug("CRAFTING: recipe: " + recipe);
		
		if(recipe != null)
		{
			Log.debug("CRAFTING: Station Check");
			if (!recipe.getStationReq().equals(stationType)) {
				Log.debug("CRAFTING: Station no Match");
				//TODO: maybe send error message?
				return;
			}
			Log.debug("CRAFTING: Station ok");
			
			LinkedList<LinkedList<CraftingComponent>> components = recipe.getRequiredCraftingComponents();
			for (int i = 0; i < components.size(); i++) {
				for (int j = 0; j < components.get(i).size(); j++) {
					componentIds.add(components.get(i).get(j).getItemId());
				}
			}
			Log.debug("CRAFTING: Components:"+componentIds);
				
			// Skill Level Check
			int playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, recipe.getSkillID());
			Log.debug("CRAFTING: checking skill: " + recipe.getSkillID() + " against playerSkillLevel: " + playerSkillLevel);
			if (playerSkillLevel < recipe.getSkillID()) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the skill level required to craft this Resource Node");
				return;
			}
			Log.debug("CRAFTING: Skill Level ok");
			
			if(AgisInventoryClient.checkComponents(playerOid, componentIds, componentStacks))
			{
				InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(playerOid);
				int bonusModTime = 0;
				float bonusModTimep = 0;
				if (ai.getBonuses().containsKey("CraftingTime")) {
					bonusModTime = ai.getBonuses().get("CraftingTime").GetValue();
					bonusModTimep = ai.getBonuses().get("CraftingTime").GetValuePercentage();
				}
				if (AgisInventoryPlugin.globalEventBonusesArray.containsKey("CraftingTime")) {
					bonusModTime += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValue();
					bonusModTimep += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValuePercentage();
				}
				Log.debug("CRAFTING: CraftingTime " + recipe.getCreationTime() + " bonusModTime=" + bonusModTime + " bonusModTimep=" + bonusModTimep);
				long time = (long) ((recipe.getCreationTime() + bonusModTime+(recipe.getCreationTime()*bonusModTimep/100D)));
				Log.debug("CRAFTING: CraftingTime DoStandardCraft " +time);
				
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "CraftingMsg");
				props.put("PluginMessageType", "CraftingStarted");
				props.put("creationTime", (int)time);
				TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				
				Engine.getAgent().sendBroadcast(playerMsg);
				
				CraftingTask task = new CraftingTask(recipe, playerOid, recipeId,1);
				Engine.getExecutor().schedule(task, time, TimeUnit.SECONDS);
				
				// Play coord effect
				String coordEffect = (String) eMsg.getProperty("coordEffect");
				task.PlayCoordinatedEffect(coordEffect);
				return;
			}
			Log.debug("CRAFTING PLUGIN: User doesn't have the required Components in their Inventory!");
			
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "CraftingMsg");
			props.put("PluginMessageType", "CraftingFailed");
			props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
					playerOid, false, props);
			
			Engine.getAgent().sendBroadcast(playerMsg);
			
			return;
		}
	}
	
	
	/**
	 * Attempts to craft an item based on the standard crafting system (player doesn't need to place items in a grid).
	 * @param playerOid
	 * @param recipeId
	 * @param stationType
	 * @param eMsg
	 */
	void DoCraftBook(OID playerOid, int recipeId, String stationType, ExtensionMessage eMsg) {
		String recipeName = (String) eMsg.getProperty("ItemName");
		int count = (Integer) eMsg.getProperty("count");
			Log.debug("CRAFTING: DoCraftBook  got recipe: " + recipeName);
		
		//LinkedList<Integer> componentIds = new LinkedList<Integer>(); //(LinkedList<Integer>) cimsg.getProperty("ItemIds");
		Log.debug("CRAFTING: DoCraftBook 1: ");
			LinkedList<Integer> componentStacks = (LinkedList<Integer>) eMsg.getProperty("ItemStacks");
		Log.debug("CRAFTING: DoCraftBook 2: ");
		
		CraftingRecipe recipe = recipes.get(recipeId);
		Log.debug("CRAFTING: DoCraftBook recipe: " + recipe);
		
		if(recipe != null)
		{
			Log.debug("CRAFTING: DoCraftBook Station Check");
			if (!recipe.getStationReq().equals(stationType) && !recipe.getStationReq().contains("none") && (recipe.getStationReq().equals("Any") && stationType == "") ) {
				Log.debug("CRAFTING: DoCraftBook Station no Match");
				ExtendedCombatMessages.sendErrorMessage(playerOid, "Station not vaild");
				//TODO: maybe send error message?
				return;
			}
			Log.debug("CRAFTING: DoCraftBook Station ok");
			HashMap<Integer,Integer> ItemsCount = new HashMap<Integer,Integer>();
  	    	
			LinkedList<LinkedList<CraftingComponent>> components = recipe.getRequiredCraftingComponents();
			for (int i = 0; i < components.size(); i++) {
				for (int j = 0; j < components.get(i).size(); j++) {
					//componentIds.add(components.get(i).get(j).getItemId());
					if(components.get(i).get(j).getItemId()>0)
					if(ItemsCount.containsKey(components.get(i).get(j).getItemId())) {
						ItemsCount.replace(components.get(i).get(j).getItemId(), ItemsCount.get(components.get(i).get(j).getItemId())+components.get(i).get(j).getCount());
					}else {
						ItemsCount.put(components.get(i).get(j).getItemId(), components.get(i).get(j).getCount());
					}
				}
			}
			
			Log.debug("CRAFTING: DoCraftBook Components:"+ItemsCount+" | count:"+count);
			if (count>0)
			for (int key : ItemsCount.keySet()) {
				ItemsCount.replace(key, ItemsCount.get(key)*count);
			}
			Log.debug("CRAFTING: DoCraftBook Components:"+ItemsCount);
			// Skill Level Check
			int playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, recipe.getSkillID());
			Log.debug("CRAFTING: DoCraftBook checking skill: " + recipe.getSkillID() + " against playerSkillLevel: " + playerSkillLevel+" req lev:"+recipe.getRequiredSkillLevel());
			if (playerSkillLevel < recipe.getRequiredSkillLevel()) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the skill level required to craft this Resource Node");
				return;
			}
			Log.debug("CRAFTING: DoCraftBook Skill Level ok");
			
			if(AgisInventoryClient.checkComponents(playerOid, ItemsCount))
			{
				InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(playerOid);
				int bonusModTime = 0;
				float bonusModTimep = 0;
				if (ai.getBonuses().containsKey("CraftingTime")) {
					bonusModTime = ai.getBonuses().get("CraftingTime").GetValue();
					bonusModTimep = ai.getBonuses().get("CraftingTime").GetValuePercentage();
				}
				if (AgisInventoryPlugin.globalEventBonusesArray.containsKey("CraftingTime")) {
					bonusModTime += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValue();
					bonusModTimep += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValuePercentage();
				}
				Log.debug("CRAFTING: CraftingTime " + recipe.getCreationTime() + " bonusModTime=" + bonusModTime + " bonusModTimep=" + bonusModTimep);
				long time = (long) ((recipe.getCreationTime() + bonusModTime + (recipe.getCreationTime() * bonusModTimep / 100D)) * 1000L);
				CraftingTask task = new CraftingTask(recipe, playerOid, recipeId, count);
				Engine.getExecutor().schedule(task, time, TimeUnit.MILLISECONDS);

				// Play coord effect
				String coordEffect = (String) eMsg.getProperty("coordEffect");
				task.PlayCoordinatedEffect(coordEffect);
				return;
			}
			Log.debug("CRAFTING PLUGIN: User doesn't have the required Components in their Inventory!");

			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "CraftingMsg");
			props.put("PluginMessageType", "CraftingFailed");
			props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(playerMsg);

			return;
		} else {
			Log.debug("CRAFTING: CraftingTime recipe not found");

		}
	}
	
	
	
	
	/**
	 * Hook for the Crafting Grid Updated Message. Checks to see if the items the player has placed
	 * in the grid match up with any recipies. If so, it sends down the result to the player.
	 * @author Andrew Harrison
	 *
	 */
	class CraftingGridUpdatedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage cimsg = (ExtensionMessage)msg;
			OID playerOid = cimsg.getSubject();
			
			Log.debug("CRAFTING: got grid updated message");
			
			// Death check
	    	boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
	    	if (dead) {
	    		ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot peform that action when dead");
	    		return true;
	    	}
	    	
	    	// Mounted Check
	    	if (!CraftingPlugin.CAN_CRAFT_WHILE_MOUNTED) {
	    		String mount = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, MountEffect.MOUNT_PROP);
	        	if (mount != null && !mount.isEmpty()) {
	        		Log.debug("CRAFTING: player is mounted");
	        		return false;
	        	}
	    	}
			
			int gridSize = (Integer) cimsg.getProperty("gridSize");
			LinkedList<Integer> componentIDs = (LinkedList<Integer>) cimsg.getProperty("componentIDs");
			LinkedList<Integer> componentCounts = (LinkedList<Integer>) cimsg.getProperty("componentCounts");
			String stationType = (String) cimsg.getProperty("stationType");
			int recipeItemID = (Integer) cimsg.getProperty("recipeItemID");
			
			LinkedList<CraftingComponent> craftingComponents = new LinkedList<CraftingComponent>();
			CraftingRecipe foundRecipe = null;
			Log.debug("CRAFTING: recipeItemID:"+recipeItemID+" stationType:"+stationType+" componentIDs:"+componentIDs+" componentCounts:"+componentCounts);
			
			if (recipeItemID > 0) {
				CraftingRecipe resultRecipe = null;
				for (CraftingRecipe recipe : recipes.values()) {
					if (recipe.getRecipeItemId() == recipeItemID) {
						resultRecipe = recipe;
					}
				}
				Log.debug("CRAFTING: resultRecipe: " + resultRecipe);
				// Load components in
				LinkedList<Integer> reqComponentIDs = resultRecipe.getRequiredItems();
				LinkedList<Integer> componentReqCounts = resultRecipe.getRequiredItemCounts();
				if (AgisInventoryClient.checkComponents(playerOid, reqComponentIDs, componentReqCounts) && resultRecipe.getStationReq().equals(stationType)) {
					foundRecipe = resultRecipe;
				}
			} else {
				for (int i = 0; i < componentIDs.size(); i++) {
					craftingComponents.add(new CraftingComponent("", componentCounts.get(i), componentIDs.get(i)));
				}

				Log.debug("CRAFTING: checking recipes");

				// Split the component list into rows
				LinkedList<LinkedList<CraftingComponent>> componentRows = new LinkedList<LinkedList<CraftingComponent>>();
				for (int i = 0; i < gridSize; i++) {
					LinkedList<CraftingComponent> componentRow = new LinkedList<CraftingComponent>();
					for (int j = 0; j < gridSize; j++) {
						componentRow.add(craftingComponents.get(i * gridSize + j));
						Log.debug("CRAFTING: adding item: " + craftingComponents.get(i * gridSize + j).getItemId() + " to row: " + i + " in column: " + j);
					}
					componentRows.add(componentRow);
				}
				Log.debug("CRAFTING: checking recipes "+recipes.size());

				for (CraftingRecipe recipe : recipes.values()) {
					if (recipe.DoesRecipeMatch(componentRows, stationType)) {
						foundRecipe = recipe;
						break;
					}
				}
			}

			Log.debug("CRAFTING: found recipe: " + foundRecipe);
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "CraftingGridMsg");
			if (foundRecipe != null) {
				props.put("recipeID", foundRecipe.getID());
				props.put("recipeName", foundRecipe.getName());
				props.put("recipeItem", foundRecipe.getRecipeItemId());
				props.put("numResults", foundRecipe.getResultItemIds().size());
				for (int i = 0; i < foundRecipe.getResultItemIds().size(); i++) {
					props.put("resultItem" + i, foundRecipe.getResultItemIds().get(i));
					props.put("resultItemCount" + i, foundRecipe.getResultItemCounts().get(i));
				}
			} else {
				props.put("recipeID", -1);
				props.put("recipeName", "");
				props.put("recipeItem", -1);
				props.put("numResults", 0);
			}
			Log.debug("CRAFTING: send update to client props="+props);
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);

			Engine.getAgent().sendBroadcast(playerMsg);

		    return true;
		}
	}
	
	/**
	 * Hook for the Get Blueprints Message. Sends the Blueprint information to the client for the 
	 * recipes provided.
	 * @author Andrew Harrison
	 *
	 */
	class SendBlueprintHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage cimsg = (ExtensionMessage)msg;
			OID playerOid = cimsg.getSubject();
			Log.debug("CRAFTING: got getBlueprintMessage");
			
			int numRecipes = (Integer) cimsg.getProperty("numRecipes");
			LinkedList<Integer> recipeIDs = new LinkedList<Integer>();
			
			for (int i = 0; i < numRecipes; i++) {
				recipeIDs.add((Integer) cimsg.getProperty("recipe" + i));
			}
			
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "BlueprintMsg");
			
			int blueprintNum = 0;
			for (int recipeId : recipeIDs) {
				CraftingRecipe recipe = recipes.get(recipeId);
				Log.debug("CRAFTING: got crafting recipe: " + recipe);
				if (recipe != null) {
					props.put("recipeID" + blueprintNum, recipe.getID());
					props.put("numResults", recipe.getResultItemIds().size());
					for (int i = 0; i < recipe.getResultItemIds().size(); i++) {
						props.put("resultItem" + i, recipe.getResultItemIds().get(i));
						props.put("resultItemCount" + i, recipe.getResultItemCounts().get(i));
					}
					props.put("recipeItemID" + blueprintNum, recipe.getRecipeItemId());
					props.put("station" + blueprintNum, recipe.getStationReq());
					int row = 0;
					for (LinkedList<CraftingComponent> recipeRow : recipe.getRequiredCraftingComponents()) {
						int column = 0;
						for (CraftingComponent component : recipeRow) {
							props.put("item" + blueprintNum + "_" + row + "_" + column, component.getItemId());
							column++;
						}
						props.put("numColumns" + blueprintNum + "_" + row, column);
						row++;
					}
					props.put("numRows" + blueprintNum, row);
					blueprintNum++;
				}
			}
			props.put("numBlueprints", blueprintNum);
			
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
					playerOid, false, props);
			
			Engine.getAgent().sendBroadcast(playerMsg);
			
			return true;
		}
	}
	
	ContentDatabase cDB;
	
	// Game Settings
	public static int GRID_SIZE = 4;
	boolean GRID_BASED_CRAFTING = true;
	public static boolean RESOURCE_DROPS_ON_FAIL = false;
	public static boolean GAIN_SKILL_AFTER_MAX = true;
	public static boolean AUTO_PICKUP_RESOURCES = false;
	public static int RESOURCE_GATHER_DISTANCE = 4;
	public static boolean RESOURCE_GATHER_CAN_FAIL = false;
	public static boolean DELETE_CRAFTING_RECIPE_ON_USE = false;
	public static boolean RESOURCE_COUNT_IS_LOOT_COUNT = false;
	public static int RESOURCE_HARVEST_XP_REWARD = 0;
	
	public static boolean USE_RESOURCE_GROUPS = true;
	public static int RESOURCE_GROUP_SIZE = 50;
	
	public static float SKINNING_MAX_DISTANCE = 5F;
	public static int SKINNING_SKILL_ID = -1;
	public static int SKINNING_SKILL_EXP = -1;
	public static String SKINNING_WEAPON_REQ = "";
	
	public static boolean CAN_HARVEST_WHILE_MOUNTED = false;
	public static boolean CAN_CRAFT_WHILE_MOUNTED = false;
	
	static HashMap<Integer, CraftingRecipe> recipes = new HashMap<Integer, CraftingRecipe>();
	public static HashMap<OID, HashMap<Integer, ResourceNode>> resourceNodes = new HashMap<OID, HashMap<Integer, ResourceNode>>();
	HashMap<OID, HashMap<String, ResourceNodeGroup>> resourceNodeGroups = new HashMap<OID, HashMap<String, ResourceNodeGroup>>();
	public static ConcurrentHashMap<Integer, ResourceNodeProfile> recourceNodeProfile = new ConcurrentHashMap<Integer, ResourceNodeProfile>();
	public static String TASK_CRAFTING = "crafting";
	public static String TASK_GATHERING = "gathering";
	
}
