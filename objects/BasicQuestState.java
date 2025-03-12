package atavism.agis.objects;

import atavism.agis.util.EventMessageHelper;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.agis.plugins.*;
import atavism.msgsys.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;

public class BasicQuestState extends QuestState
{
    public BasicQuestState() {
        setupTransient();
    }

    public BasicQuestState(AgisQuest quest, OID playerOid) {
        super(quest, playerOid);
        setupTransient();
        Log.debug("QDB: got new quest state with experience: " + this.getXpRewards() 
        		+ " and completionText: " + this.getQuestCompletionText());
    }
    
    /**
     * private method to recreate the lock when deserializing
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        setupTransient();
    }
    
    /**
     * returns the current state of this quest, ie, how many mobs to kill,etc
     */
    public String toString() {
        String status = "Quest=" + getName() + "\n";
        Iterator<List<String>> iter1 = getObjectiveStatus().values().iterator();
        while(iter1.hasNext()) {
        	Iterator<String> iter = iter1.next().iterator();
            while(iter.hasNext()) {
                String s = iter.next();
                status = status + "   " + s + "\n";
            }
        }
        return status;
    }

    public boolean activate() {
        if (Log.loggingDebug)
            log.debug("in activate: this " + this);
        // Clear the old subscribers
        sub = null;
        // subscribe for some messages
        SubjectFilter filter = new SubjectFilter(getPlayerOid());
        //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
        filter.addType(AgisInventoryClient.MSG_TYPE_QUEST_ITEMS_LIST);
        filter.addType(CombatClient.MSG_TYPE_COMBAT_MOB_DEATH);
        filter.addType(QuestClient.MSG_TYPE_QUEST_TASK_UPDATE);
        sub = Engine.getAgent().createSubscription(filter, this);
        if (sub == null)
        	Log.debug("QUEST: sub is null");
        if (Log.loggingDebug)
            log.debug("activate: this " + this+" after set filters");
        makeDeliveryItems();
        if (Log.loggingDebug)
            log.debug("activate: this " + this+" after makeDeliveryItems");
        updateQuestLog();
        if (Log.loggingDebug)
            log.debug("activate: this " + this+" after updateQuestLog");
        //TODO: maybe uncomment the next line?
        //boolean test = checkInventory(true);
        //boolean test = true;
        boolean test = updateObjectiveStatus();
        if (Log.loggingDebug)
            log.debug("activate: this " + this+" after updateObjectiveStatus");
        // updateQuestObjectives();
        log.debug("BasicQuestState for quest: " + getQuestRef() + " activated");
        return test;
    }

    public void deactivate() {
        if (Log.loggingDebug)
            log.debug("BasicQuestState.deactivate: playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
        if (sub != null) {
            Engine.getAgent().removeSubscription(sub);
            log.debug("BasicQuestState.deactivate: (2)removed sub for playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
            sub = null;
	    }
    }
    
    public void abandonQuest(OID playerOid) {
    	// Remove all delivery items
    	Log.debug("BASICQUESTSTATE: abandon quest hit");
    	for (int itemID : deliveryItems) {
    		Log.debug("BASICQUESTSTATE: removing delivery item: " + itemID);
    		InventoryClient.removeItem(playerOid, itemID);
    	}
    }
    
    /**
     * process network messages
     */
    public void handleMessage(Message msg, int flags) {
        /*if (msg instanceof InventoryClient.InvUpdateMessage) {
            processInvUpdate((InventoryClient.InvUpdateMessage) msg);
        }*/
    	if (msg instanceof AgisInventoryClient.QuestItemsListMessage) {
            processInvUpdate((AgisInventoryClient.QuestItemsListMessage) msg);
        }
        else if (msg instanceof CombatClient.QuestMobDeath) {
            processMobDeathUpdate((CombatClient.QuestMobDeath) msg);
        }
        else if (msg instanceof QuestClient.TaskUpdateMessage) {
        	processTaskUpdate((QuestClient.TaskUpdateMessage) msg);
        }
        else {
            log.error("unknown msg: " + msg);
        }
        //return true;
    }
    
    //protected boolean processInvUpdate(InventoryClient.InvUpdateMessage msg) {
    protected boolean processInvUpdate(AgisInventoryClient.QuestItemsListMessage msg) {
        if (Log.loggingDebug)
            log.debug("processInvUpdate: player=" + getPlayerOid() + ", itemList=" + msg);
        HashMap<Integer, Integer> itemList = msg.getItemList();
        checkInventory(false, itemList);
        return true;
    }
    
   
    
    protected boolean checkInventory(boolean questUpdated, HashMap<Integer, Integer> itemList) {
    	//boolean questUpdated = false;
    	Log.debug("QUEST: checking quest items: " + itemList);
    	if (questConcluding)
    		return true;
        for (CollectionGoalStatus goalStatus : collectionGoalsStatus) {
			Log.debug("QUEST: : TargetCount=" + goalStatus.getTargetCount());
			//for (int i=0; i < goalStatus.getTargetCount(); i++) {
	    		int itemRequired = goalStatus.getTemplateID();
	    		int priorCount = goalStatus.currentCount;
	    		goalStatus.currentCount = 0;
	    		if (itemList.containsKey(itemRequired)) {
	    			goalStatus.currentCount += itemList.get(itemRequired);
	    		}
	    		// Check if the number of items the player has has changed
	    		Log.debug("QUEST: prior count for item: " + itemRequired + " is: " + priorCount + "; currentCount: " + goalStatus.currentCount);
			if (goalStatus.currentCount != priorCount) {
				questUpdated = true;
				if (goalStatus.currentCount < goalStatus.targetCount) {
					String message = goalStatus.getTemplateName() + " collected: " + goalStatus.currentCount + "/" + goalStatus.targetCount;
					EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.templateID, goalStatus.currentCount, goalStatus.targetCount);
				} else if (goalStatus.currentCount == goalStatus.targetCount) {
					String message = goalStatus.getTemplateName() + " collected: " + goalStatus.currentCount + "/" + goalStatus.targetCount + " (Complete)";
					EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.templateID, goalStatus.currentCount, goalStatus.targetCount);
				}
			}
			// }
	    }
        if (questUpdated)
            return updateObjectiveStatus();
        return true;
    }
    
    protected boolean processMobDeathUpdate(CombatClient.QuestMobDeath msg) {
        if (Log.loggingDebug)
            log.debug("processMobDeathUpdate: player=" + getPlayerOid() + ", mobDeathUpdate=" + msg);
        boolean questUpdated = false;
        int mobID = msg.getMobID();
        String mobName = msg.getMobName();
     //   LinkedList<String> questCategories = msg.getQuestCategories();
        
        for (KillGoalStatus goalStatus : killGoalsStatus) {
        	Log.debug("QUEST: checking kill goal status for mob: [" + goalStatus.getMobID() + "] against: [" + mobID + "]");
        	int mobRequired = goalStatus.getMobID();
    	    if (mobRequired == mobID) {
    	    	Log.debug("QUEST: we have a match");
    	    	int currentCount = goalStatus.getCurrentCount();
    	    	goalStatus.setCurrentCount(currentCount + 1);
    	    	questUpdated = true;
				if (goalStatus.currentCount < goalStatus.targetCount) {
					String message = goalStatus.getMobName() + " killed: " + goalStatus.currentCount + "/" + goalStatus.targetCount;
					EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.mobID, goalStatus.currentCount, goalStatus.targetCount);
				} else if (goalStatus.currentCount == goalStatus.targetCount) {
					String message = goalStatus.getMobName() + " killed: " + goalStatus.currentCount + "/" + goalStatus.targetCount + " (Complete)";
					EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.mobID, goalStatus.currentCount, goalStatus.targetCount);
				}
    	    }
		}
		for (CategoryKillGoalStatus goalStatus : categoryKillGoalsStatus) {
			String categoryRequired = goalStatus.getMobCategory();
			String[] strArray = categoryRequired.split(";");
			ArrayList<Integer> intArray = new ArrayList<Integer>();
			for (int i = 0; i < strArray.length; i++) {
				intArray.add(Integer.parseInt(strArray[i]));
			}
		//	if (questCategories != null) {
				// if (questCategories.contains(categoryRequired)) {
				if (intArray.contains(mobID)) {
					int currentCount = goalStatus.getCurrentCount();
					goalStatus.setCurrentCount(currentCount + 1);
					questUpdated = true;
					if (goalStatus.currentCount < goalStatus.targetCount) {
						String message = goalStatus.getName() + " killed: " + goalStatus.currentCount + "/" + goalStatus.targetCount;
						EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, -1, goalStatus.currentCount, goalStatus.targetCount);
					} else if (goalStatus.currentCount == goalStatus.targetCount) {
						String message = goalStatus.getName() + " killed: " + goalStatus.currentCount + "/" + goalStatus.targetCount + " (Complete)";
						EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, -1, goalStatus.currentCount, goalStatus.targetCount);
					}
				}
			//}
		}
        Log.debug("QUEST: at end of mob death update with questUpdated: " + questUpdated);
        if (questUpdated)
            updateObjectiveStatus();
        return true;
    }
    
    protected boolean processTaskUpdate(QuestClient.TaskUpdateMessage msg) {
    	int taskID = msg.getTaskID();
    	Log.debug("BASICQUESTSTATE: Got task update: " + taskID);
    	boolean questUpdated = false;
    	for (TaskGoalStatus goalStatus : taskGoalsStatus) {
    		int taskRequired = goalStatus.getTaskID();
    		if (taskRequired == taskID) {
    			int status = msg.getStatus();
    			int currentCount = goalStatus.getCurrentCount();
    			if (status == 1) {
    				goalStatus.setCurrentCount(currentCount + 1);
    				questUpdated = true;
					if (goalStatus.currentCount < goalStatus.targetCount) {
						String message = goalStatus.taskText + ": " + goalStatus.currentCount + "/" + goalStatus.targetCount;
						EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.taskID, goalStatus.currentCount, goalStatus.targetCount);
					} else if (goalStatus.currentCount == goalStatus.targetCount) {
						String message = goalStatus.taskText + ": " + goalStatus.currentCount + "/" + goalStatus.targetCount + " (Complete)";
						EventMessageHelper.SendQuestEvent(this.playerOid, EventMessageHelper.QUEST_PROGRESS, message, goalStatus.taskID, goalStatus.currentCount, goalStatus.targetCount);
					}
    			}
    		}
    	}
    	if (questUpdated)
    	    updateObjectiveStatus();
        return true;
    }
    
    /**
     * called when the player is concluding (turning in) the quest
     * returns false if the quest is not able to be concluded
     */
    public boolean handleConclude() {
        Log.debug("QUEST: processConclude hit");
        questConcluding = true;
        //if (!questOid.equals(msg.getQuestOid()))
        //    return true;

        if (Log.loggingDebug)
            log.debug("processConcludeQuest: player=" + getPlayerOid());
	    ArrayList<Integer> templateList = new ArrayList<Integer>();
	    HashMap<Integer,Integer> itemList = new HashMap<Integer,Integer>(); 
	    for (CollectionGoalStatus goalStatus : collectionGoalsStatus) {
	    	  if (Log.loggingDebug)
	    		  log.debug("processConcludeQuest: goalStatus="+goalStatus);
	        if(itemList.containsKey(goalStatus.getTemplateID()))
	    		itemList.replace(goalStatus.getTemplateID(), goalStatus.getTargetCount()+itemList.get(goalStatus.getTemplateID()));
	    	else
	    		itemList.put(goalStatus.getTemplateID(), goalStatus.getTargetCount());

	   // for (CollectionGoalStatus goalStatus : collectionGoalsStatus) {
	        for (int i=0; i < goalStatus.getTargetCount(); i++) {
		        templateList.add(goalStatus.getTemplateID());
	        }
	    }

        boolean conclude = false;
        if (templateList.isEmpty()) {
            conclude = true;
        } else {
     //   	for (int templateID : templateList) {
        	for (int templateID : itemList.keySet()) {
        		Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
        		String itemType = (String) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemType");
        		if (itemType == null || itemType.equals("Quest")) {
        			// Remove whole stack if it is a Quest item
        			for (int i=0; i < itemList.get(templateID); i++) {

        			OID itemOid = InventoryClient.removeItem(getPlayerOid(), templateID);
        			//TODO: if this is a stack, we only want to try delete once?
        			if (itemOid != null)
        				ObjectManagerClient.deleteObject(itemOid);
    				else
    					break;
    			}
    		} else {
    			AgisInventoryClient.removeGenericItem(playerOid, templateID, false, itemList.get(templateID));
    			
    		}
    	}
    	
    	
/*        	for (int templateID : templateList) {
    		Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
    		String itemType = (String) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemType");
    		if (itemType == null || itemType.equals("Quest")) {
    			// Remove whole stack if it is a Quest item
    			OID itemOid = InventoryClient.removeItem(getPlayerOid(), templateID);
    			//TODO: if this is a stack, we only want to try delete once?
    			if (itemOid != null)
    				ObjectManagerClient.deleteObject(itemOid);

        		} else {
        			AgisInventoryClient.removeGenericItem(playerOid, templateID, false, 1);
        		}
        	}*/
        	conclude = true;
        	
            /*List<OID> removeResult = InventoryClient.removeItems(getPlayerOid(), templateList);
            if (removeResult != null) {
                conclude = true;
                
                for (OID itemOid : removeResult) {
                    ObjectManagerClient.deleteObject(itemOid);
                }
            }*/
        }
        questConcluding = false;
        if (conclude) {
        	Log.debug("QUEST: setting conclude to true");
            setConcluded(true);
            deactivate();
            updateQuestLog();
            //sendStateStatusChange();
            // Finally lets send an exp gain message, based on the amount of secondary objectives completed
            int completionLevel = getCompletionLevel();
            CombatClient.alterExpMessage expMsg = new CombatClient.alterExpMessage(getPlayerOid(), xpRewards.get(completionLevel));
            Engine.getAgent().sendBroadcast(expMsg);
            return true;
        }
        //QuestClient.ConcludeUpdateMessage QCUmsg = new QuestClient.ConcludeUpdateMessage(mobOid, getPlayerOid());
        //Engine.getAgent().sendBroadcast(QCUmsg);
        sendStateStatusChange();
        return false;
    }

    public boolean updateObjectiveStatus() {
    	
    	Log.debug("QUEST: checking if quest " + getQuestRef() + " is complete");
        updateQuestObjectives();
        sendItemUpdate();
        boolean isComplete = true;

        // update quest completed flag
        for (CollectionGoalStatus goalStatus : collectionGoalsStatus) {
        	if (goalStatus.currentCount < goalStatus.targetCount) {
        		if (goalStatus.getTier() == 0) {
        		    log.debug("updateObjectiveStatus: collection goal: " + goalStatus.getTemplateName() + " not completed");
        		    //boolean wasComplete = getCompleted();
        		    setCompleted(false);
        		    isComplete = false;
        		}
        	}
        }

        for (KillGoalStatus goalStatus : killGoalsStatus) {
        	if (goalStatus.currentCount < goalStatus.targetCount) {
        		if (goalStatus.getTier() == 0) {
        			log.debug("updateObjectiveStatus: kill goal: " + goalStatus.getMobID() + " not completed");
            		//boolean wasComplete = getCompleted();
            		setCompleted(false);
            		isComplete = false;
            	} 
        	}
        }
        
        for (CategoryKillGoalStatus goalStatus : categoryKillGoalsStatus) {
        	if (goalStatus.currentCount < goalStatus.targetCount) {
        		if (goalStatus.getTier() == 0) {
        			log.debug("updateObjectiveStatus: category kill goal: " + goalStatus.getMobCategory() + " not completed");
        		    //boolean wasComplete = getCompleted();
        		    setCompleted(false);
        		    isComplete = false;
        		}
        	}
        }
        
        for (TaskGoalStatus goalStatus : taskGoalsStatus) {
        	if (goalStatus.currentCount < goalStatus.targetCount) {
        		if (goalStatus.getTier() == 0) {
        			log.debug("updateObjectiveStatus: task goal: " + goalStatus.getTaskID() + " not completed");
        		    //boolean wasComplete = getCompleted();
        		    setCompleted(false);
        		    isComplete = false;
        		}
        	}
        }
        
        if (!isComplete || getCompleted()) {
            sendStateStatusChange();
		    return true;
        }

        log.debug("updateObjectiveStatus: quest: " + getQuestRef() + " is completed");
		setCompleted(true);
        sendStateStatusChange();
        updateQuestLog();
        ChatClient.sendObjChatMsg(playerOid, 0, "You have completed quest " + getName());
        // Send a message out so any NPCs nearby will display the quest complete stuff
        return true;
    }
    
    /**
     * Sends out a message with the list of items still required to complete this Quest.
     */
    protected void sendItemUpdate() {
    	List<Integer> itemsRequired = new ArrayList<Integer>();
    	for (CollectionGoalStatus goalStatus : collectionGoalsStatus) {
    		if (goalStatus.currentCount < goalStatus.targetCount) 
    			itemsRequired.add(goalStatus.templateID);
    	}
    	
    	QuestClient.QuestItemUpdateMessage msg = new QuestClient.QuestItemUpdateMessage(playerOid, itemsRequired);
    	Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * sends QuestLogInfo message for this quest
     */
    public void updateQuestLog() {
        if (concludedFlag) {
        	Log.debug("QUEST: removing quest from quest log");
            QuestPlugin.sendRemoveQuestResp(playerOid, questOid);
        }
        else {
        	Log.debug("QUEST STATE: updating quest log info; items rewards: " + getRewards());
        	QuestPlugin.sendQuestLogInfo(playerOid, questOid, questRef, questTitle, questDesc, questObjective, grades, 
            		xpRewards, currencyRewards, itemRewards, itemRewardsToChoose, repRewards, getObjectiveStatus(), getCompleted(), getItemRewardsOrder(), getItemRewardsToChooseOrder());
        }
     
  //TODO Deleted sendQuestHistoryLogInfo because locks
    }

    /**
     * generate delivery items and give them to the player
     */
    protected void makeDeliveryItems() {
    	if (deliveryItemsGiven == true)
    		return;
    	
    	OID playerOid = getPlayerOid();
    	OID bagOid = playerOid;
        if (Log.loggingDebug)
            log.debug("makeDeliveryItems: playerOid " + playerOid + ", bagOid + " + bagOid);

        // Normally the persistence flag is inherited from the enclosing
        // object, but all we have are OIDs.  Assume this is only used
        // for players and players are always persistent.
        Template overrideTemplate = new Template();
        overrideTemplate.put(Namespace.OBJECT_MANAGER,
                ObjectManagerClient.TEMPL_PERSISTENT, true);

        for (int templateID : deliveryItems) {
        	OID itemOid = ObjectManagerClient.generateObject(templateID, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
            InventoryClient.addItem(bagOid, playerOid, bagOid, itemOid);
        }
        
        // If no items were added, force an inventory update message
        if (deliveryItems.size() == 0) {
        	AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
    		Engine.getAgent().sendBroadcast(invUpdateMsg);
        }
        deliveryItemsGiven = true;
    }
    
    /**
     * for client display: current state
     */
    public HashMap<Integer, List<String>> getObjectiveStatus() {
        lock.lock();
        try {
        	HashMap<Integer, List<String>> statusMap = new HashMap<Integer, List<String>>();
            for (int i = 0; i <= grades; i++) {
            	List<String> l = new LinkedList<String>();
            	List<Integer> order = new LinkedList<Integer>();
                	
                Iterator<CollectionGoalStatus> iter = collectionGoalsStatus.iterator();
                while (iter.hasNext()) {
                    CollectionGoalStatus status = iter.next();
                    if (status.getTier() == i) {
                        String itemName = status.getTemplateName();
                        int numNeeded = status.targetCount;
                        int cur = Math.min(status.currentCount, numNeeded);
                
                        String objective = itemName + ": " + cur + "/" + numNeeded;
                        l.add(objective);
                        order.add(status.order);
                    }
                }
            
                Iterator<KillGoalStatus> iter2 = killGoalsStatus.iterator();
                while (iter2.hasNext()) {
                    KillGoalStatus status = iter2.next();
                    if (status.getTier() == i) {
                        int mobID = status.getMobID();
                        int numNeeded = status.targetCount;
                        int cur = Math.min(status.currentCount, numNeeded);
                        
                        String objective = status.getMobName() + " slain: " + cur + "/" + numNeeded;
                        l.add(objective);
                        order.add(status.order);
                    }
                }
            
                Iterator<CategoryKillGoalStatus> iter3 = categoryKillGoalsStatus.iterator();
                while (iter3.hasNext()) {
                    CategoryKillGoalStatus status = iter3.next();
                    if (status.getTier() == i) {
                        String name = status.getName();
                        int numNeeded = status.targetCount;
                        int cur = Math.min(status.currentCount, numNeeded);
                
                        String objective = name + " slain: " + cur + "/" + numNeeded;
                        l.add(objective);
                        order.add(status.order);
                    }
                }
                
                
                Iterator<TaskGoalStatus> iter4 = taskGoalsStatus.iterator();
                while (iter4.hasNext()) {
                	TaskGoalStatus status = iter4.next();
                    if (status.getTier() == i) {
                        String name = status.getTaskText();
                        int numNeeded = status.targetCount;
                        int cur = Math.min(status.currentCount, numNeeded);
                
                        String objective = name + ": " + cur + "/" + numNeeded;
                        l.add(objective);
                        order.add(status.order);
                    }
                }
                
                List<String> lw = new LinkedList<String>();
                int id=0;
                for(int a=0; a<l.size();a++) {
                	for(int b=0;b<l.size();b++) {
                		if(a == order.get(b)) {
                			lw.add(l.get(b));
                		}
                	}
                }
                if(Log.loggingDebug)log.debug("objectives  "+lw);
                statusMap.put(i, lw);
            }
            return statusMap;
        }
        finally {
            lock.unlock();
        }
    }

    public void setGoalsStatus(List<CollectionGoalStatus> goalsStatus) {
        this.collectionGoalsStatus = new LinkedList<CollectionGoalStatus>(goalsStatus);
    }

    public List<CollectionGoalStatus> getGoalsStatus() {
	lock.lock();
	try {
	    return new LinkedList<CollectionGoalStatus>(collectionGoalsStatus);
	}
	finally {
	    lock.unlock();
	}
    }
    
    public void setKillGoalsStatus(List<KillGoalStatus> killGoalsStatus) {
        this.killGoalsStatus = new LinkedList<KillGoalStatus>(killGoalsStatus);
    }

    public List<KillGoalStatus> getKillGoalsStatus() {
	lock.lock();
	try {
	    return new LinkedList<KillGoalStatus>(killGoalsStatus);
	}
	finally {
	    lock.unlock();
	}
    }
    
    public void setCategoryKillGoalsStatus(List<CategoryKillGoalStatus> categoryKillGoalsStatus) {
        this.categoryKillGoalsStatus = new LinkedList<CategoryKillGoalStatus>(categoryKillGoalsStatus);
    }

    public List<CategoryKillGoalStatus> getCategoryKillGoalsStatus() {
	lock.lock();
	try {
	    return new LinkedList<CategoryKillGoalStatus>(categoryKillGoalsStatus);
	}
	finally {
	    lock.unlock();
	}
    }
    
    public void setTaskGoalsStatus(List<TaskGoalStatus> taskGoalsStatus) {
        this.taskGoalsStatus = new LinkedList<TaskGoalStatus>(taskGoalsStatus);
    }

    public List<TaskGoalStatus> getTaskGoalsStatus() {
	lock.lock();
	try {
	    return new LinkedList<TaskGoalStatus>(taskGoalsStatus);
	}
	finally {
	    lock.unlock();
	}
    }


    /**
     * a list of items that the quest gives to the player
     * when the player accepts the quest
     */
    public void setDeliveryItems(List<Integer> items) {
        lock.lock();
        try {
            deliveryItems = new LinkedList<Integer>(items);
        }
        finally {
            lock.unlock();
        }
    }
    public void addDeliveryItem(int item) {
        lock.lock();
        try {
            deliveryItems.add(item);
        }
        finally {
            lock.unlock();
        }
    }
    public List<Integer> getDeliveryItems() {
        lock.lock();
        try {
            return deliveryItems;
        }
        finally {
            lock.unlock();
        }
    }
    
    public void setDeliveryItemsGiven(boolean given) {
        lock.lock();
        try {
        	deliveryItemsGiven = given;
        }
        finally {
            lock.unlock();
        }
    }
    public boolean getDeliveryItemsGiven() {
    	lock.lock();
    	try {
            return deliveryItemsGiven;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * called after the queststate is initialized and set by the world
     * server to the player
     */
    public void handleInit() {
	Lock writeLock = AOObject.transferLock.writeLock();
	writeLock.lock();
	try {
	    handleInitHelper();

	    handleInvUpdate();
	    completeHandler(); 
	}
	finally {
	    writeLock.unlock();
	}
    }

    protected void handleInitHelper() {
        // give the delivery items to the player
        lock.lock();
        try {
        }
        finally {
            lock.unlock();
        }
    }

    public void handleInvUpdate() {
        if (Log.loggingDebug)
            if (Log.loggingDebug)
                Log.debug("CollectionQuestState.handleAcquire: quest=" + 
                          getName());

        lock.lock();
        try {
	    if (getConcluded()) {
		return;
	    }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * marks quest as completed if we just completed it
     */
    protected void completeHandler() {
        lock.lock();
        try {
            if (getCompleted()) {
                return;
            }
        }
        finally {
            lock.unlock();
        }
    }
    
    /**
     * Calculates what grades of the quest have been completed
     * @return the highest secondary 
     */
    public int getCompletionLevel() {
    	int completionLevel = 0;
    	if (getCompleted() == false) {
    		return completionLevel;
    	}
    	for (int i = 1; i <= grades; i++) {
            Iterator<CollectionGoalStatus> iter = collectionGoalsStatus.iterator();
            while (iter.hasNext()) {
                CollectionGoalStatus status = iter.next();
                if (status.getTier() == i) {
                    int numNeeded = status.targetCount;
                    //int cur = Math.min(status.currentCount, numNeeded);
                    int current = status.currentCount;
                    if (current < numNeeded)
                    	return completionLevel;
                }
            }
        
            Iterator<KillGoalStatus> iter2 = killGoalsStatus.iterator();
            while (iter2.hasNext()) {
                KillGoalStatus status = iter2.next();
                if (status.getTier() == i) {
                    int numNeeded = status.targetCount;
                    //int cur = Math.min(status.currentCount, numNeeded);
                    int current = status.currentCount;
                    if (current < numNeeded)
                    	return completionLevel;
                }
            }
        
            Iterator<CategoryKillGoalStatus> iter3 = categoryKillGoalsStatus.iterator();
            while (iter3.hasNext()) {
                CategoryKillGoalStatus status = iter3.next();
                if (status.getTier() == i) {
                    int numNeeded = status.targetCount;
                    //int cur = Math.min(status.currentCount, numNeeded);
                    int current = status.currentCount;
                    if (current < numNeeded)
                    	return completionLevel;
                }
            }
            completionLevel = i;
        }
    	return completionLevel;
    }

    public static class CollectionGoalStatus implements Serializable {
        public CollectionGoalStatus() {
        }

        public CollectionGoalStatus(AgisBasicQuest.CollectionGoal goal) {
            this.templateID = goal.getTemplateID();
            this.templateName = goal.getTemplateName();
            this.targetCount = goal.getNum();
            this.currentCount = 0;
            this.tier = goal.getTier();
            this.order = goal.order;
        }

        public void setTemplateID(int templateID) {
            this.templateID = templateID;
        }
        public int getTemplateID() {
            return templateID;
        }
        public int templateID = -1;
        
        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }
        public String getTemplateName() {
            return templateName;
        }
        public String templateName = null;

        public void setTargetCount(int c) {
            this.targetCount = c;
        }
        public int getTargetCount() {
            return this.targetCount;
        }
        public int targetCount = 0;

        public void setCurrentCount(int c) {
            this.currentCount = c;
        }
        public int getCurrentCount() {
            return this.currentCount;
        }
        public int currentCount = 0;
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }
        public int tier;
        
        public int order=0;
        
        public String toString() {
        	return "[CollectionGoalStatus: templateID="+templateID+" templateName="+templateName+" targetCount="+targetCount+" currentCount="+currentCount+" tier="+tier+"]";
        }

        private static final long serialVersionUID = 1L;
    }
    
    public static class KillGoalStatus implements Serializable {
        public KillGoalStatus() {
        }

        public KillGoalStatus(AgisBasicQuest.KillGoal goal) {
            this.mobID = goal.getMobID();
            this.mobName = goal.getMobName();
            this.targetCount = goal.getNum();
            this.currentCount = 0;
            this.tier = goal.getTier();
            this.order = goal.order;
        }

        public void setMobID(int mobID) {
            this.mobID = mobID;
        }
        public int getMobID() {
            return mobID;
        }
        public int mobID = -1;
        
        public void setMobName(String mobName) {
            this.mobName = mobName;
        }
        public String getMobName() {
            return mobName;
        }
        public String mobName = null;

        public void setTargetCount(int c) {
            this.targetCount = c;
        }
        public int getTargetCount() {
            return this.targetCount;
        }
        public int targetCount = 0;

        public void setCurrentCount(int c) {
            this.currentCount = c;
        }
        public int getCurrentCount() {
            return this.currentCount;
        }
        public int currentCount = 0;
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }
        public int tier;

        public int order=0;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static class CategoryKillGoalStatus implements Serializable {
        public CategoryKillGoalStatus() {
        }

        public CategoryKillGoalStatus(AgisBasicQuest.CategoryKillGoal goal) {
            this.mobCategory = goal.getMobCategory();
            this.name = goal.getName();
            this.targetCount = goal.getNum();
            this.currentCount = 0;
            this.tier = goal.getTier();
            this.order = goal.order; 
        }

        public void setMobCategory(String mobCategory) { this.mobCategory = mobCategory; }
        public String getMobCategory() { return mobCategory; }
        public String mobCategory = null;
        
        public void setName(String name) { this.name = name; }
        public String getName() { return name; }
        public String name = null;

        public void setTargetCount(int c) { this.targetCount = c; }
        public int getTargetCount() { return this.targetCount; }
        public int targetCount = 0;

        public void setCurrentCount(int c) { this.currentCount = c; }
        public int getCurrentCount() { return this.currentCount; }
        public int currentCount = 0;
        
        public void setTier(int tier) { this.tier = tier; }
        public int getTier() { return tier; }
        public int tier;
        public int order=0;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static class TaskGoalStatus implements Serializable {
        public TaskGoalStatus() {
        }

        public TaskGoalStatus(AgisBasicQuest.TaskGoal goal) {
            this.taskID = goal.getTaskID();
            this.taskText = goal.getTaskText();
            this.targetCount = goal.getNum();
            this.currentCount = 0;
            this.tier = goal.getTier();
            this.order = goal.order;
        }

        public void setTaskNID(int taskID) {
            this.taskID = taskID;
        }
        public int getTaskID() {
            return taskID;
        }
        public int taskID = -1;
        
        public void setTaskText(String taskText) {
            this.taskText = taskText;
        }
        public String getTaskText() {
            return taskText;
        }
        public String taskText = null;

        public void setTargetCount(int c) {
            this.targetCount = c;
        }
        public int getTargetCount() {
            return this.targetCount;
        }
        public int targetCount = 0;

        public void setCurrentCount(int c) {
            this.currentCount = c;
        }
        public int getCurrentCount() {
            return this.currentCount;
        }
        public int currentCount = 0;
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }
        public int tier;
        
        public int order=0;
        
        private static final long serialVersionUID = 1L;
    }

    static final Logger log = new Logger("BasicQuestState");

    //transient Long sub = null;
    //transient Long sub2 = null;
    Long sub = null;

    List<CollectionGoalStatus> collectionGoalsStatus = new LinkedList<CollectionGoalStatus>();
    List<KillGoalStatus> killGoalsStatus = new LinkedList<KillGoalStatus>();
    List<CategoryKillGoalStatus> categoryKillGoalsStatus = new LinkedList<CategoryKillGoalStatus>();
    List<TaskGoalStatus> taskGoalsStatus = new LinkedList<TaskGoalStatus>();
    List<Integer> deliveryItems = new LinkedList<Integer>();
    boolean deliveryItemsGiven = false;
    
    boolean questConcluding = false;
    
    private static final long serialVersionUID = 1L;
}
