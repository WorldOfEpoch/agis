package atavism.agis.objects;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import atavism.agis.plugins.AchievementsClient;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CraftingPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;
//import sun.util.logging.resources.logging;

/**
 * Runs the task of crafting resulting in an item upon completion.
 * @author Andrew
 *
 */
public class CraftingTask implements Runnable, MessageDispatch {
	
	protected CraftingRecipe recipe;
	LinkedList<Integer> componentIds;
	LinkedList<Integer> componentStacks;
	LinkedList<Long> specificComponents;
	LinkedList<Integer> specificComponentStacks;
	boolean useSpecificItems = false;
	OID playerOid;
	int recipeId;
	int count=1;
	boolean interrupted = false;
	transient Long sub = null;
	String _coordEffect = "";
	protected CoordinatedEffectState coordinatedEffectState;
	public CraftingTask(CraftingRecipe recipe, OID oid, int recipeId, int count) {
		this.recipe = recipe;
		Log.debug("CRAFTING: Create Task recipe:"+recipe+" oid:"+oid+" recipeId:"+recipeId+" count:"+count);
		componentIds = new LinkedList<Integer>();
		componentStacks = new LinkedList<Integer>();
		playerOid = oid;
		this.recipeId = recipeId;
		this.count = count;

		LinkedList<LinkedList<CraftingComponent>> components = recipe.getRequiredCraftingComponents();
		for (int i = 0; i < CraftingPlugin.GRID_SIZE; i++) {
			for (int j = 0; j < CraftingPlugin.GRID_SIZE; j++) {
				componentIds.add(components.get(i).get(j).getItemId());
				componentStacks.add(components.get(i).get(j).getCount());
			}
		}

		if (recipe.getCreationTime() > 0) {
			setupMessageSubscription();
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", CraftingPlugin.TASK_CRAFTING);
		}
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
		int time = (int) ((recipe.getCreationTime() + bonusModTime+(recipe.getCreationTime()*bonusModTimep/100D)));
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "CraftingMsg");
		props.put("PluginMessageType", "CraftingStarted");
		props.put("creationTime", time);
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		
		Engine.getAgent().sendBroadcast(playerMsg);
	}

	public CraftingTask(CraftingRecipe r, LinkedList<Long> cid, LinkedList<Integer> cs, OID oid, int recipeId) {
		Log.debug("CRAFTING: Create Task");
		recipe = r;
		specificComponents = cid;
		specificComponentStacks = cs;
		playerOid = oid;
		this.recipeId = recipeId;
		if (recipe == null || recipe.getMustMatchLayout())
			useSpecificItems = true;

		componentIds = new LinkedList<Integer>();
		componentStacks = new LinkedList<Integer>();
		LinkedList<LinkedList<CraftingComponent>> components = recipe.getRequiredCraftingComponents();
		for (int i = 0; i < CraftingPlugin.GRID_SIZE; i++) {
			for (int j = 0; j < CraftingPlugin.GRID_SIZE; j++) {
				componentIds.add(components.get(i).get(j).getItemId());
				componentStacks.add(components.get(i).get(j).getCount());
			}
		}

		if (recipe.getCreationTime() > 0) {
			setupMessageSubscription();
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", CraftingPlugin.TASK_CRAFTING);
		}
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
		int time = (int) ((recipe.getCreationTime() + bonusModTime+(recipe.getCreationTime()*bonusModTimep/100D)));
		
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "CraftingMsg");
		props.put("PluginMessageType", "CraftingStarted");
		props.put("creationTime", time);
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);

		Engine.getAgent().sendBroadcast(playerMsg);
	}
	
	void setupMessageSubscription() {
	    // Set up a message hook for when the caster is moving so we can interrupt
	    SubjectFilter filter = new SubjectFilter(playerOid);
	    filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
	    sub = Engine.getAgent().createSubscription(filter, this);
	    Log.debug("CRAFTING: subscribed to interrupt message");
	}
	
	public void handleMessage(Message msg, int flags) {
        if (msg instanceof CombatClient.interruptAbilityMessage) {
            processInterrupt();
        } else {
            Log.error("CraftingTask: unknown msg: " + msg);
        }
    }
    
    private void processInterrupt() {
    	Log.debug("CraftingTask: CRAFTING: got interrupt");
    	interrupted = true;
    	if (sub != null)
            Engine.getAgent().removeSubscription(sub);
    	Log.debug("CRAFTING: Task processing interrupt ");
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "CraftingMsg");
		props.put("PluginMessageType", "CraftingInterrupted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
				playerOid, false, props);

		if(coordinatedEffectState!=null){
			coordinatedEffectState.invokeCancel();
		}
		//String currentTask = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
		//Log.debug("TASK: Got current task: " + currentTask);
		//if (currentTask.equals(CraftingPlugin.TASK_CRAFTING)) {
			
		//}
		Engine.getAgent().sendBroadcast(playerMsg);
		
		EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
    }
    
    public void PlayCoordinatedEffect(String coordEffect) {
    	CoordinatedEffect cE = new CoordinatedEffect(coordEffect);
	    cE.sendSourceOid(true);
	    cE.sendTargetOid(true);
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
		long time = (long) ((recipe.getCreationTime() + bonusModTime + (recipe.getCreationTime() * bonusModTimep / 100D)));
		
	    cE.putArgument("length", (float)time);
		coordinatedEffectState = cE.invoke(playerOid, playerOid);
	    _coordEffect = coordEffect;
    }

	@Override
	public void run() {
		Log.debug("CRAFTING: running crafting task with specific items: " + useSpecificItems);
		if (sub != null)
            Engine.getAgent().removeSubscription(sub);
		
		if (interrupted)
			return;
		
		// Reset currentTask
		String currentTask = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
		if (currentTask.equals(CraftingPlugin.TASK_CRAFTING)) {
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
		}
		
		// Dead check
    	boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
    	if (dead) {
    		return;
    	}
    	
    	HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
    	int loot=1;
    	boolean failed = false;
    	Random random = new Random();
    	float roll = random.nextInt(10000)/100f;
    	Log.debug("CRAFTING: roll " + roll);
    	Log.debug("CRAFTING: recipe:"+recipe);
    	Log.debug("CRAFTING: getResultItemChance4:"+recipe.getResultItemChance4());
    	Log.debug("CRAFTING: IDS:"+recipe.getResultItemIds4());
    	Log.debug("CRAFTING: size:"+recipe.getResultItemIds4().size());
    	Log.debug("CRAFTING: getResultItemChance3:"+recipe.getResultItemChance3()+" | size:"+recipe.getResultItemIds3().size());
    	Log.debug("CRAFTING: getResultItemChance2:"+recipe.getResultItemChance2()+" | size:"+recipe.getResultItemIds2().size());
    	Log.debug("CRAFTING: getResultItemChance1:"+recipe.getResultItemChance()+" | size:"+recipe.getResultItemIds().size());
    	InventoryInfo ai = AgisInventoryPlugin.getInventoryInfo(playerOid);
		
    	  	
    	
    	float bonusModp = 0f;
    	int bonusModTime = 0;
		float bonusModTimep = 0;
		if(ai != null) {
			if(ai.getBonuses().containsKey("CraftingChance")) {
				bonusModp =ai.getBonuses().get("CraftingChance").GetValuePercentage();
	      	}
			if(ai.getBonuses().containsKey("CraftingTime")) {
				bonusModTime = ai.getBonuses().get("CraftingTime").GetValue();
				bonusModTimep = ai.getBonuses().get("CraftingTime").GetValuePercentage();
	      	}
		}
		if(AgisInventoryPlugin.globalEventBonusesArray.containsKey("CraftingChance")) {
			bonusModp += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingChance").GetValuePercentage();
  		}
		if(AgisInventoryPlugin.globalEventBonusesArray.containsKey("CraftingTime")) {
			bonusModTime += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValue();
			bonusModTimep += AgisInventoryPlugin.globalEventBonusesArray.get("CraftingTime").GetValuePercentage();
  		}
		Log.debug("CRAFTING: CraftingTime " + recipe.getCreationTime() + " bonusModTime=" + bonusModTime + " bonusModTimep=" + bonusModTimep);
		long time = (long) ((recipe.getCreationTime() + bonusModTime + (recipe.getCreationTime() * bonusModTimep / 100D)));
    
		Log.debug("CRAFTING roll=" + roll + " additional chance bonus " + bonusModp);
		if (recipe.getResultItemChance4() + recipe.getResultItemChance4() * bonusModp / 100D>= roll && recipe.getResultItemIds4().size() > 0) {
			Log.debug("CRAFTING: Chance for group 4 : " + recipe.getResultItemChance4() + ">=" + roll);
			for (int i = 0; i < recipe.getResultItemIds4().size(); i++) {
				if (recipe.getResultItemIds4().get(i) > 0)
					itemsToGenerate.put(recipe.getResultItemIds4().get(i), recipe.getResultItemCounts4().get(i));
				// AgisInventoryClient.generateItem(playerOid, recipe.getResultItemIds().get(i),
				// "" /*recipe.getName()*/, recipe.getResultItemCounts().get(i), null);
			}

			loot = 4;
		} else if (recipe.getResultItemChance3() + recipe.getResultItemChance3() * bonusModp / 100D>= roll && recipe.getResultItemIds3().size() > 0) {
			Log.debug("CRAFTING: Chance for group 3 : " + recipe.getResultItemChance3() + ">=" + roll);
			for (int i = 0; i < recipe.getResultItemIds3().size(); i++) {
				if (recipe.getResultItemIds3().get(i) > 0)
					itemsToGenerate.put(recipe.getResultItemIds3().get(i), recipe.getResultItemCounts3().get(i));
				// AgisInventoryClient.generateItem(playerOid, recipe.getResultItemIds().get(i),
				// "" /*recipe.getName()*/, recipe.getResultItemCounts().get(i), null);
			}
			loot = 3;
		} else if (recipe.getResultItemChance2() + recipe.getResultItemChance2() * bonusModp / 100D>= roll && recipe.getResultItemIds2().size() > 0) {
			Log.debug("CRAFTING: Chance for group 2 : " + recipe.getResultItemChance2() + ">=" + roll);
			for (int i = 0; i < recipe.getResultItemIds2().size(); i++) {
				if (recipe.getResultItemIds2().get(i) > 0)
					itemsToGenerate.put(recipe.getResultItemIds2().get(i), recipe.getResultItemCounts2().get(i));
				// AgisInventoryClient.generateItem(playerOid, recipe.getResultItemIds().get(i),
				// "" /*recipe.getName()*/, recipe.getResultItemCounts().get(i), null);
			}
			loot = 2;
		} else if (recipe.getResultItemChance() + recipe.getResultItemChance() * bonusModp / 100D >= roll) {
			Log.debug("CRAFTING: Chance for group 1 : " + recipe.getResultItemChance() + ">=" + roll);
			for (int i = 0; i < recipe.getResultItemIds().size(); i++) {
				if (recipe.getResultItemIds().get(i) > 0)
					itemsToGenerate.put(recipe.getResultItemIds().get(i), recipe.getResultItemCounts().get(i));
				// AgisInventoryClient.generateItem(playerOid, recipe.getResultItemIds().get(i),
				// "" /*recipe.getName()*/, recipe.getResultItemCounts().get(i), null);
			}
			loot = 1;
		} else {
			failed = true;
			Log.debug("CRAFTING: Crafting Failed !! roll out of chance");
		}
    	Log.debug("CRAFTING: Items To Generate: "+itemsToGenerate);
		// Check inventory has space
		if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
			return;
		}
		
		
		if (useSpecificItems) {
			// Get AgisItem Info for each Item
			/*for (int i = 0; i < specificComponents.size(); i++) {
				Log.debug("CRAFTING: removing specific item: " + OID.fromLong(specificComponents.get(i)) + " x" + componentStacks.get(i));
			    AgisInventoryClient.removeSpecificItem(playerOid, OID.fromLong(specificComponents.get(i)), false, componentStacks.get(i));
			}*/
			HashMap<OID, Integer> itemsToRemove = new HashMap<OID, Integer>();
			/*for (int i = 0; i < componentIds.size(); i++) {
				if (componentIds.get(i) > 0) {
					Log.debug("CRAFTING: removing specific item: " + componentIds.get(i) + " x" + componentStacks.get(i));
					//AgisInventoryClient.removeGenericItem(playerOid, componentIds.get(i), false, componentStacks.get(i));
					itemsToRemove.put(componentIds.get(i), componentStacks.get(i));
				}
			}*/
			
			Log.debug("CRAFTING: sending removeGenericItems message");
			// AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);
			for (int i = 0; i < specificComponents.size(); i++) {
				if (specificComponents.get(i) > 0) {
					Log.debug("CRAFTING: removing specific item: " + specificComponents.get(i) + " x" + specificComponentStacks.get(i));
					// AgisInventoryClient.removeGenericItem(playerOid, componentIds.get(i), false, componentStacks.get(i));
					itemsToRemove.put(OID.fromLong(specificComponents.get(i)), specificComponentStacks.get(i));
				}
			}

			Log.debug("CRAFTING PLUGIN: playerOid="+playerOid+" componentIds="+componentIds+" componentStacks="+componentStacks+" specificComponents="+specificComponents+" specificComponentStacks="+specificComponentStacks);
			if (!AgisInventoryClient.checkSpecificComponents(playerOid, componentIds, componentStacks, specificComponents, specificComponentStacks)) {
				Log.debug("CRAFTING PLUGIN: User doesn't have the required Components in their Inventory!");

				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "CraftingMsg");
				props.put("PluginMessageType", "CraftingFailed");
				props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
				TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);

				Engine.getAgent().sendBroadcast(playerMsg);
				return;
			}

			AgisInventoryClient.removeSpecificItem(playerOid, itemsToRemove, false);
		} else {
			//ArrayList<Integer> test = new ArrayList<Integer>();
			//test.addAll(componentIds);
			//List<OID> itemList = InventoryClient.findItems(playerOid, test);
			HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer, Integer>();
			Log.debug("CRAFTING: Task Run Check Items to Remove from Ids->"+componentIds.size()+" |Stacks ->"+componentStacks.size());
			for (int i = 0; i < componentIds.size(); i++) {
				Log.debug("CRAFTING: Task Run Check Item ->" + i + " |Ids->" + componentIds.get(i));
				Log.debug("CRAFTING: Task Run Check Item ->" + i + " |Stack" + componentStacks.get(i));
					if (componentIds.get(i) > 0) {
					Log.debug("CRAFTING: removing generic item: " + componentIds.get(i) + " x" + componentStacks.get(i));
					//AgisInventoryClient.removeGenericItem(playerOid, componentIds.get(i), false, componentStacks.get(i));
					if (itemsToRemove.keySet().contains(componentIds.get(i)))
						itemsToRemove.put(componentIds.get(i), componentStacks.get(i)+itemsToRemove.get(componentIds.get(i)));
					else	
						itemsToRemove.put(componentIds.get(i), componentStacks.get(i));
				}
			}
			Log.debug("CRAFTING: Task Run Items to Remove ->"+itemsToRemove.size()+" | "+itemsToRemove);
			if(!AgisInventoryClient.checkComponents(playerOid, itemsToRemove)) {
				Log.debug("CRAFTING PLUGIN: User doesn't have the required Components in their Inventory!");
				
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "CraftingMsg");
				props.put("PluginMessageType", "CraftingFailed");
				props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
				TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	
				Engine.getAgent().sendBroadcast(playerMsg);
				return;
			}
			AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);
		}
		Log.debug("CRAFTING: After Remove Items Before Generate Item");
		
		
		if (itemsToGenerate.size() > 0) {
    		AgisInventoryClient.generateItems(playerOid, itemsToGenerate, false);
    		}	
		count--;
    
		Log.debug("CRAFTING: After Generate Item");
		CoordinatedEffect cE = new CoordinatedEffect("CraftEffect"+loot);
    	cE.sendSourceOid(true);
    	cE.sendTargetOid(true);
    	cE.invoke(playerOid, playerOid);
    	//float diff = CombatClient.GetSkillDiff(playerOid, recipe.getSkillID(),recipe.getRequiredSkillLevel());
    	if (!failed)
    		CombatClient.abilityUsed(playerOid, recipe.getSkillID(),recipe.getExperience(),recipe.getRequiredSkillLevel());
		Log.debug("CRAFTING: Crafting Recipe " + recipeId + " with skill: " + recipe.getSkillID()+" "+recipe.getExperience()+" recipe.getRequiredSkillLevel():"+recipe.getRequiredSkillLevel()+" failed:"+failed);
		
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "CraftingMsg");
		props.put("PluginMessageType", "CraftingCompleted");
		int ii = 0;
		for(Integer itemId : itemsToGenerate.keySet()) {
			props.put("item"+ii+"Id",itemId);
			AgisInventoryPlugin.addListsRankingData(playerOid, AchievementsClient.CRAFT, itemId);
			props.put("item"+ii+"Count",itemsToGenerate.get(itemId));
			ii++;
		}
		props.put("num", ii);
		//AgisInventoryPlugin.addListsRankingData(playerOid, AchievementsClient.CRAFT, 1 );
		
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		if (count>0) {
			Log.debug("CRAFTING: Count >1 Start new Task");
			CraftingTask task = new CraftingTask(recipe, playerOid, recipeId,count);
			Engine.getExecutor().schedule(task, time, TimeUnit.SECONDS);
			task.PlayCoordinatedEffect(_coordEffect);
			
		}
		Log.debug("CRAFTING: End Task");
		
	}
}
