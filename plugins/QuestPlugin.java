package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.io.Serializable;

import atavism.agis.core.Agis;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.QuestState;
import atavism.agis.objects.BasicQuestState;
import atavism.agis.objects.CoordinatedEffect;
import atavism.agis.objects.Faction;
import atavism.agis.objects.AgisBasicQuest;
import atavism.agis.objects.AgisItem;
import atavism.agis.objects.AgisQuest;
import atavism.agis.objects.QuestStateInfo;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.InventoryClient;
import atavism.agis.plugins.QuestClient.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.RequirementCheckResult;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.Template;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;

/**
 * Handles requests for quest state information related to a player. manages all
 * quest states for players.
 * 
 */
public class QuestPlugin extends EnginePlugin {
	public QuestPlugin() {
		super("Quest");
		setPluginType("Quest");
	}

	public void onActivate() {
		registerHooks();

		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
		filter.addType(QuestClient.MSG_TYPE_REQ_RESET_QUESTS);
		filter.addType(QuestClient.MSG_TYPE_ABANDON_QUEST);
		// filter.addType(AgisMobClient.MSG_TYPE_CATEGORY_UPDATED);
		filter.addType(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
		filter.addType(QuestClient.MSG_TYPE_OFFER_QUEST);
		filter.addType(QuestClient.MSG_TYPE_QUEST_RESP);
		/* Long sub = */ Engine.getAgent().createSubscription(filter, this);

		filter = new MessageTypeFilter();
		filter.addType(QuestClient.MSG_TYPE_NEW_QUESTSTATE);
		filter.addType(QuestClient.MSG_TYPE_GET_QUEST_STATUS);
		filter.addType(QuestClient.MSG_TYPE_CONCLUDE_QUEST);
		filter.addType(QuestClient.MSG_TYPE_QUEST_ITEM_REQS);
		filter.addType(QuestClient.MSG_TYPE_CAN_PLAYER_START_QUEST);
		filter.addType(QuestClient.MSG_TYPE_START_QUEST);
		// filter.addType(LoginMessage.MSG_TYPE_LOGIN);
		/* Long sub = */ Engine.getAgent().createSubscription(filter, this, MessageAgent.RESPONDER);

		if (Log.loggingDebug)
			log.debug("QuestPlugin activated");
		 loadData();
		registerLoadHook(Namespace.QUEST, new QuestStateLoadHook());
		//registerSaveHook(Namespace.QUEST, new QuestStateSaveHook());
		registerUnloadHook(Namespace.QUEST, new QuestStateUnloadHook());
		registerPluginNamespace(Namespace.QUEST, new QuestSubObjectHook());
		  Log.debug("Registering Quest plugin");
			Engine.registerStatusReportingPlugin(this);
 
	}

	// how to process incoming messages
	protected void registerHooks() {
		getHookManager().addHook(QuestClient.MSG_TYPE_GET_QUEST_STATUS, new GetQuestStatusHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_NEW_QUESTSTATE, new NewQuestStateHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_CONCLUDE_QUEST, new ConcludeQuestHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT, new UpdateObjHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_REQ_RESET_QUESTS, new ResetQuestsHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_CAN_PLAYER_START_QUEST, new CanPlayerStartQuestHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_OFFER_QUEST, new OfferQuestsToPlayerHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_QUEST_RESP, new QuestResponseHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_START_QUEST, new StartQuestHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_ABANDON_QUEST, new AbandonQuestHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_QUEST_ITEM_REQS, new GetQuestItemReqsHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_CATEGORY_UPDATED, new CategoryUpdatedHook());
		getHookManager().addHook(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE, new QuestStatusChangedHook());
	}

	void loadData() {
		MobDatabase mobDataBase = new MobDatabase(true);
		loadCategoryContent(mobDataBase,1);
		ContentDatabase cDB = new ContentDatabase(false);
	    cDB.loadEditorOptions();
	}

	protected void ReloadTemplates(Message msg) {
		Log.debug("QuestPlugin ReloadTemplates Start");
			loadData();
			Entity[] objects = EntityManager.getAllEntitiesByNamespace(Namespace.QUEST);
			for(Entity e : objects){
				QuestStateInfo qsi = (QuestStateInfo)e;
				qsi.ReloadQuests(1);
			}
		Log.debug("QuestPlugin ReloadTemplates End");
	}
    
  
	
	
	
	 private void loadCategoryContent(MobDatabase mDB, int categoryID) {
		 if (Log.loggingDebug)Log.debug("QUEST: loading content for category: " + categoryID);
	    	
	    	HashMap<Integer, AgisBasicQuest> questMap =  mDB.loadQuests(categoryID);
	    	for (int questID : questMap.keySet()) {
	    		 if (Log.loggingDebug)Log.debug("QUEST: register quest id="+questID);
	    		Agis.QuestManager.register(questID, questMap.get(questID));
	    	}
	    	// Factions
	    	ArrayList<Faction> factions = mDB.loadFactions(categoryID);
	    	for (Faction faction: factions) {
	    		Agis.FactionManager.register(faction.getID(), faction);
	    		 if (Log.loggingDebug)Log.debug("MOB: loaded faction: [" + faction.getName() + "]");
	    	}
	    	
	    }
	    
	
	
	
	public static QuestStateInfo getQuestStateInfo(OID oid) {
		return (QuestStateInfo) EntityManager.getEntityByNamespace(oid, Namespace.QUEST);
	}

	public static void registerQuestStateInfo(QuestStateInfo qsInfo) {
		EntityManager.registerEntityByNamespace(qsInfo, Namespace.QUEST);
	}

	class QuestStateLoadHook implements LoadHook {
		public void onLoad(Entity e) {
			QuestStateInfo qsInfo = (QuestStateInfo) e;
			// Re-activate all quest states
			for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
				qs.activate();
			}
		}
	}

	class QuestStateUnloadHook implements UnloadHook {
		public void onUnload(Entity e) {
			QuestStateInfo qsInfo = (QuestStateInfo) e;
			// Re-activate all quest states
			for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
				qs.deactivate();
			}
		}
	}

	public class QuestSubObjectHook extends GenerateSubObjectHook {
		public QuestSubObjectHook() {
			super(QuestPlugin.this);
		}

		public SubObjData generateSubObject(Template template, Namespace name, OID masterOid) {
			if (Log.loggingDebug)
				Log.debug("QuestPlugin::GenerateSubObjectHook::gernateSubObject()");
			if (masterOid == null) {
				Log.error("GenerateSubObjectHook: no master oid");
				return null;
			}
			if (Log.loggingDebug)
				Log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", template=" + template);

			Map<String, Serializable> props = template.getSubMap(Namespace.QUEST);

			// generate the subobject
			QuestStateInfo qsInfo = new QuestStateInfo(masterOid);
			qsInfo.setName(template.getName());
			qsInfo.setCurrentCategory(1);

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;
			qsInfo.setPersistenceFlag(persistent);

			if (props != null) {
				// copy properties from template to object
				for (Map.Entry<String, Serializable> entry : props.entrySet()) {
					String key = entry.getKey();
					Serializable value = entry.getValue();
					if (!key.startsWith(":")) {
						qsInfo.setProperty(key, value);
					}
				}
			}
			if (Log.loggingDebug)
				Log.debug("GenerateSubObjectHook: created entity " + qsInfo);

			// register the entity
			registerQuestStateInfo(qsInfo);

			if (persistent)
				Engine.getPersistenceManager().persistEntity(qsInfo);

			// send a response message
			return new SubObjData();
		}
	}
    
	public class CanPlayerStartQuestHook implements Hook {
		public boolean processMessage(Message m, int flags) {
			QuestClient.CanPlayerStartQuestMessage msg = (QuestClient.CanPlayerStartQuestMessage) m;
			OID playerOid = msg.getSubject();
			int questID = msg.getQuestID();
			if (Log.loggingDebug)
				log.debug("StartQuestHook: playerOid=" + playerOid + ", questID=" + questID);
			Engine.getAgent().sendBooleanResponse(msg, canPlayerStartQuest(playerOid, questID));
			return false;
		}
	}

    boolean canPlayerStartQuest(OID playerOid, int questID) {
    	 if (Log.loggingDebug)Log.debug("QUEST: checking if player "+playerOid+" can start quest "+questID);
    	AgisQuest quest = Agis.QuestManager.get(questID);
    	 if (Log.loggingDebug)	Log.debug("QUEST: quest= "+quest);
        
    	//quest.getFaction()
		/*FactionStateInfo fsi = FactionPlugin.getFactionStateInfo(playerOid);
		int subjectFaction = (Integer) fsi.getProperty(FactionStateInfo.FACTION_PROP);
		Faction newFaction = Agis.FactionManager.get(subjectFaction);
		int reputation = newFaction.getDefaultReputation(quest.getFaction());
		int rep = FactionPlugin.calculateStanding(reputation);
		log.error("canPlayerStartQuest: quest:"+quest.getID()+" plyFaction:"+subjectFaction+" plyReputation:"+reputation+" plyRep:"+rep+ " quest.getFaction()"+quest.getFaction());*/
    	int rep = FactionClient.getStance(playerOid, quest.getFaction());
    	if (rep < 0 || rep == FactionPlugin.Unknown) {
			return false;
		}
		Lock lock = getObjectLockManager().getLock(playerOid);
        lock.lock();
        try {
        	
        	QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
        	if(qsInfo==null) {
        		 if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " QuestStateInfo is null ");
                return false;
        	}
        	// Is player already on quest or completed it?
        	if (qsInfo.getAllQuests().containsKey(questID)) {
        		if (quest.getRepeatable() && !qsInfo.getCurrentActiveQuests().containsKey(questID)) {
        			// If it is a repeatable quest and it isn't in the active list, it can be started again
        		} else {
        			 if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " already has quest: " + questID);
                    return false;
        		}
        	}
        	
            // Has the player completed the prereqs?
        	for (int prereq : quest.getQuestPrereqs()) {
        		if (!qsInfo.getCurrentCompletedQuests().contains(prereq)) {
        			 if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " has not completed prereq: " + questID);
                    return false;
        		}
        	}
        	
        	// Check if the player has met the QuestStarted Requirement
        	if (quest.getQuestStartedReq() > 0 && !qsInfo.getAllQuests().containsKey(quest.getQuestStartedReq())) {
        		 if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " has not started quest: " + questID);
                return false;
        	}
        } finally {
            lock.unlock();
        }
        
        
        // Other requirements
        RequirementCheckResult canStart = RequirementChecker.DoesPlayerMeetRequirements(playerOid, quest.getRequirements());
        if (!canStart.result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
        	 if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " does not meet quest requirements with result: " + canStart.result);
        	return false;
        }
        if (Log.loggingDebug)log.debug("QUEST: player " + playerOid + " can meet quest requirements with result: " + canStart.result+" for quest "+questID);
        
    	return true;
    }
    
  /*  boolean canPlayerStartQuests(OID playerOid, ArrayList<Integer> questIDs) {
    	Log.debug("QUEST: checking if player "+playerOid+" can start quest "+questID);
    	
    	
    	
    	
    	
    	
    	AgisQuest quest = Agis.QuestManager.get(questID);
    	Log.debug("QUEST: quest= "+quest);
        
    	
    	int rep = FactionClient.getStance(playerOid, quest.getFaction());
    	if (rep < 0 || rep == FactionPlugin.Unknown) {
			return false;
		}
		Lock lock = getObjectLockManager().getLock(playerOid);
        lock.lock();
        try {
        	
        	QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
        	if(qsInfo==null) {
        		log.debug("QUEST: player " + playerOid + " QuestStateInfo is null ");
                return false;
        	}
        	// Is player already on quest or completed it?
        	if (qsInfo.getAllQuests().containsKey(questID)) {
        		if (quest.getRepeatable() && !qsInfo.getCurrentActiveQuests().containsKey(questID)) {
        			// If it is a repeatable quest and it isn't in the active list, it can be started again
        		} else {
        			log.debug("QUEST: player " + playerOid + " already has quest: " + questID);
                    return false;
        		}
        	}
        	
            // Has the player completed the prereqs?
        	for (int prereq : quest.getQuestPrereqs()) {
        		if (!qsInfo.getCurrentCompletedQuests().contains(prereq)) {
        			log.debug("QUEST: player " + playerOid + " has not completed prereq: " + questID);
                    return false;
        		}
        	}
        	
        	// Check if the player has met the QuestStarted Requirement
        	if (quest.getQuestStartedReq() > 0 && !qsInfo.getAllQuests().containsKey(quest.getQuestStartedReq())) {
        		log.debug("QUEST: player " + playerOid + " has not started quest: " + questID);
                return false;
        	}
        } finally {
            lock.unlock();
        }
        
        
        // Other requirements
        RequirementCheckResult canStart = RequirementChecker.DoesPlayerMeetRequirements(playerOid, quest.getRequirements());
        if (!canStart.result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
        	log.debug("QUEST: player " + playerOid + " does not meet quest requirements with result: " + canStart.result);
        	return false;
        }
    	log.debug("QUEST: player " + playerOid + " can meet quest requirements with result: " + canStart.result+" for quest "+questID);
        
    	return true;
    }
    
    */
    
    /**
     * Hook for the StartQuestMessage. Will attempt to add the specified quest to the list of 
     * quests the player is on.
     * @author Andrew Harrison
     *
     */
	public class StartQuestHook implements Hook {
		public boolean processMessage(Message m, int flags) {
			QuestClient.StartQuestMessage msg = (QuestClient.StartQuestMessage) m;
			OID playerOid = msg.getSubject();
			int questID = msg.getQuestID();
			if (Log.loggingDebug)
				log.debug("StartQuestHook: playerOid=" + playerOid + ", questID=" + questID);

			if (!canPlayerStartQuest(playerOid, questID)) {
				Engine.getAgent().sendBooleanResponse(msg, Boolean.FALSE);
				return true;
			}

			AgisQuest quest = Agis.QuestManager.get(questID);
			AgisBasicQuest abq =(AgisBasicQuest)quest;
	    	List<Integer> items = abq.getDeliveryItems();
	    	HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
			if (items.size() > 0) {
				for (int templateID : items) {
					itemsToGenerate.put(templateID, 1);
				}
				if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
					Engine.getAgent().sendBooleanResponse(msg, Boolean.FALSE);
					return true;
				}
			}
			QuestState qs = quest.generate(playerOid);
			if (Log.loggingDebug)
				log.debug("processQuestRespMsg: sending new quest state msg: " + qs);
			if (Log.loggingTrace)
				log.trace("StartQuestHook.processMessage berofer lock and try " + playerOid);
			Lock lock = getObjectLockManager().getLock(playerOid);
			lock.lock();
			try {
				// add this quest state to the player's quest states object
				QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
				if (Log.loggingTrace)
					log.trace("StartQuestHook.processMessage  try:  after getQuestStateInfo " + playerOid);
				qsInfo.addActiveQuest(qs.getQuestRef(), qs);
			} finally {
				lock.unlock();
			}
			if (Log.loggingTrace)
				log.trace("StartQuestHook.processMessage  after try  " + playerOid);

			Engine.getAgent().sendBooleanResponse(msg, Boolean.TRUE);

			return true;
		}
	}

    public class OfferQuestsToPlayerHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            QuestClient.OfferQuestMessage msg = (QuestClient.OfferQuestMessage) m;
            sendQuestInfo(msg.getSubject(), msg.getOfferer(), msg.getQuests(), msg.deleteItem());
            return true;
        }
    }
    
    public class QuestResponseHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            QuestClient.QuestResponseMessage msg = (QuestClient.QuestResponseMessage) m;
            
            //OID myOid = getObjectStub().getOid();
        	OID playerOid = msg.getPlayerOid();
        	OID questID = msg.getQuestID();
            Boolean acceptStatus = msg.getAcceptStatus();
            HashMap<Integer, OID> quests;
            AgisQuest quest = null;
            lock.lock();
            try {
            	quests = offeredQuestMap.remove(playerOid);
            }
            finally {
                lock.unlock();
            }
            
            if (! acceptStatus) {
                if (Log.loggingDebug)
                    log.debug("processQuestRespMsg: player " + playerOid + " declined quest");
                return true;
            }
            
            if (quests == null) {
                log.error("quest hasn't been offered");
                return true;
            }

            OID itemToDelete = null;
            for (Integer id : quests.keySet()) {
            	AgisQuest q = Agis.QuestManager.get(id);
            	if (q.getOid().equals(questID)) {
            		quest = q;
            		itemToDelete = quests.get(id);
            	}
            }

            if (quest == null) {
            	log.error("QUEST BEHAV: quest does not exist");
            	return true;
            }
            
            if (Log.loggingDebug)
                log.debug("processQuestRespMsg: player " + playerOid + " has accepted quest");
            
            // Send the start quest message
            QuestClient.startQuestForPlayer(playerOid, quest.getID());
            
            if (itemToDelete != null) {
            	AgisInventoryClient.removeSpecificItem(playerOid, itemToDelete, false, 1);
            }
            
            // update the players quest availability info
            log.debug("processQuestRespMsg: updating availability");
            
            return true;
        }
    }
    
    public class NewQuestStateHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            QuestClient.NewQuestStateMessage msg = (QuestClient.NewQuestStateMessage) m;
            OID playerOid = msg.getSubject();
            QuestState qs = msg.getQuestState();
            if (Log.loggingDebug)
                log.debug("NewQuestStateHook: playerOid=" + playerOid + ", qs=" + qs);
            
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
                // add this quest state to the player's quest states object
                QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
                qsInfo.addActiveQuest(qs.getQuestRef(), qs);
                // send response msg
                Engine.getAgent().sendBooleanResponse(msg, Boolean.TRUE);
            }
            finally {
                lock.unlock();
            }
            return false;
        }
    }
    
    public class GetQuestStatusHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            QuestClient.GetQuestStatusMessage pMsg = (QuestClient.GetQuestStatusMessage) msg;

            OID oid = pMsg.getSubject();

            if (Log.loggingDebug)
                log.debug("GetQuestStatusHook: player=" + oid);

            Lock lock = getObjectLockManager().getLock(oid);
            lock.lock();
            try {
            	QuestStateInfo qsInfo = getQuestStateInfo(oid);
            	if (qsInfo==null) {
            		Engine.getAgent().sendObjectResponse(pMsg, null);
            		log.debug("GetQuestStatusHook QuestStateInfo is null");
                    return true;
            	}
            		
            	int questType = pMsg.getQuestType();
            	if (questType == QuestClient.QuestTypeActive)
            		Engine.getAgent().sendObjectResponse(pMsg, qsInfo.getCurrentActiveQuests());
            	else if (questType == QuestClient.QuestTypeCompleted)
            		Engine.getAgent().sendObjectResponse(pMsg, qsInfo.getCurrentCompletedQuests());
            	else
            		Engine.getAgent().sendObjectResponse(pMsg, qsInfo.getAllQuests());
                log.debug("GetQuestStatusHook: sent response");
                return true;
            } finally {
                lock.unlock();
            }
        }
    }
    
    public class QuestStatusChangedHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            QuestClient.StateStatusChangeMessage msg = (QuestClient.StateStatusChangeMessage) m;
            OID playerOid = msg.getSubject();
            if (Log.loggingDebug)
                log.debug("QuestStatusChange: playerOid=" + playerOid);
            
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
                // add this quest state to the player's quest states object
                QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
                Engine.getPersistenceManager().setDirty(qsInfo);
            }
            finally {
                lock.unlock();
            }
            return false;
        }
    }
    
    public class ConcludeQuestHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            QuestClient.ConcludeMessage msg = (QuestClient.ConcludeMessage) m;
            OID playerOid = msg.getSubject();
            OID questOid = msg.getQuestOid();
            int chosenReward = msg.getChosenReward();
            if (Log.loggingDebug)
                log.debug("ConcludeQuestHook: playerOid=" + playerOid + ", qs=" + questOid);
            
            // Get the quest
            AgisQuest completedQuest = null;
            QuestState completedQuestState = null;
            boolean concluded = false;
            
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
            	QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
            	for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
            		int questRef = qs.getQuestRef();
            		if (Log.loggingDebug)
            			log.debug("processReqConcludedMsg: checking status for quest " + questRef + ", completed=" + qs.getCompleted()
                    		+ " and qsOID:" + qs.getQuestOid());
            		if (qs.getCompleted() == true && qs.getConcluded() == false && qs.getQuestOid().equals(questOid)) {
            			// found the quest
            			completedQuest = Agis.QuestManager.get(questRef);
            			completedQuestState = qs;
            			if (completedQuest != null) {
            				if (Log.loggingDebug)
            					log.debug("processReqConcludeMsg: found a completed quest: " + questRef);
            				break;
            			}
            			else {
            				log.warn("processReqConcludeMsg: quest is completed, but not in end quests");
            			}
            		}
            	}
            	
            	if (completedQuest == null || completedQuestState == null) {
            		// send response msg
    	            Engine.getAgent().sendBooleanResponse(msg, concluded);
    	            return true;
            	}
            	
            	// Get the Quest
                boolean repeatable = false;
                if (completedQuest != null) {
                	 if (Log.loggingDebug)Log.debug("QUEST: got Quest object: " + completedQuest.getName());
                	repeatable = completedQuest.getRepeatable();
                }
                
                // Work out space needed for item rewards to ensure the player can get all of the rewards
                int completionLevel = 0;
                if (completedQuestState instanceof BasicQuestState) {
                	BasicQuestState qs = (BasicQuestState) completedQuestState;
                	completionLevel = qs.getCompletionLevel();
                }
                HashMap<Integer, Integer> sumRrewards = new HashMap<Integer, Integer>();
                HashMap<Integer, Integer> rewards = completedQuest.getRewards().get(completionLevel);
                if (rewards != null) {
                    for (int rewardTemplate : rewards.keySet()) {
                    	if (rewardTemplate == -1)
                     		continue;
                    	sumRrewards.put(rewardTemplate, rewards.get(rewardTemplate));
                     }
                 } 
                 
                 HashMap<Integer, Integer> rewardsToChoose = completedQuest.getRewardsToChoose().get(completionLevel);
                 if (rewardsToChoose != null) {
                	 if (Log.loggingDebug) Log.debug("processReqConcludedMsg: createitem: templ=" + chosenReward + ", generating object");
                     sumRrewards.put(chosenReward, rewardsToChoose.get(chosenReward));
                 }
                 if (sumRrewards.size() > 0) {
                	 boolean hasSpace = AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, sumRrewards);
                     	 if (!hasSpace) {
                		 EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
                		 Engine.getAgent().sendBooleanResponse(msg, concluded);
                		 return true;
                	 }
                 }
            	
                 // Now conclude the quest
            	concluded = qsInfo.concludeQuest(completedQuest.getID(), repeatable);
            } finally {
                lock.unlock();
            }
            
            // send response msg before generating items
            Engine.getAgent().sendBooleanResponse(msg, concluded);
            
            if (concluded) {
            	// dish out rewards
            	ChatClient.sendObjChatMsg(playerOid, 2, "You have concluded quest: " + completedQuest.getName());

                // generate the reward item
            	HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
                int completionLevel = 0;
                if (completedQuestState instanceof BasicQuestState) {
                	BasicQuestState qs = (BasicQuestState) completedQuestState;
                	completionLevel = qs.getCompletionLevel();
                }
                HashMap<Integer, Integer> rewards = completedQuest.getRewards().get(completionLevel);
                if (rewards != null) {
                    for (int rewardTemplate : rewards.keySet()) {
                    	if (rewardTemplate == -1)
                     		continue;
                        if (Log.loggingDebug)
                            Log.debug("processReqConcludedMsg: createitem: templ=" + rewardTemplate + ", generating object");
                        if (itemsToGenerate.containsKey(rewardTemplate)) {
                            itemsToGenerate.put(rewardTemplate, rewards.get(rewardTemplate)+itemsToGenerate.get(rewardTemplate));
                        }else {
                           itemsToGenerate.put(rewardTemplate, rewards.get(rewardTemplate));
                           }
                        //AgisInventoryClient.generateItem(playerOid, rewardTemplate, "" , rewards.get(rewardTemplate), null);
                     }
                 }
                 
                 HashMap<Integer, HashMap<Integer, Integer>> rewardsToChoose = completedQuest.getRewardsToChoose();
                 if (rewardsToChoose != null && rewardsToChoose.containsKey(completionLevel)) {
                	 HashMap<Integer, Integer> rC = rewardsToChoose.get(completionLevel);
                	 if (Log.loggingDebug) Log.debug("processReqConcludedMsg: createitem: templ=" + chosenReward + ", generating object");
                  if (itemsToGenerate.containsKey(chosenReward)) {
                     itemsToGenerate.put(chosenReward, rC.get(chosenReward)+itemsToGenerate.get(chosenReward));
                  }else {
                      itemsToGenerate.put(chosenReward, rC.get(chosenReward));
                       
                  }
                     //AgisInventoryClient.generateItem(playerOid, chosenReward, "" , rewardsToChoose.get(chosenReward), null);
                 }
                 
                 //Log.debug("QUEST: got " + itemsToGenerate.size() + " items to generate");
                 if (itemsToGenerate.size() > 0) {
             		AgisInventoryClient.generateItemsNoResponse(playerOid, itemsToGenerate, false);
             	 }
                 
                 // Give currency rewards
                 HashMap<Integer, Integer> currencyRewards = completedQuest.getCurrencyRewards().get(completionLevel);
                 if (currencyRewards != null) {
                     for (int currencyID : currencyRewards.keySet()) {
                     	if (currencyID == -1)
                      		continue;
                         if (Log.loggingDebug)
                             Log.debug("processReqConcludedMsg: giveCurrency: currency=" + currencyID);
                        AgisInventoryClient.alterCurrency(playerOid, currencyID, currencyRewards.get(currencyID));
                      }
                 }
                 
                 // give rep rewards
                 HashMap<Integer, Integer> repRewards = completedQuest.getRepRewards().get(completionLevel);
                 if (repRewards != null) {
                     for (int repFaction : repRewards.keySet()) {
                     	if (repFaction == -1)
                      		continue;
                         if (Log.loggingDebug)
                             Log.debug("processReqConcludedMsg: giveRep: faction=" + repFaction);
                        FactionClient.alterReputation(playerOid, repFaction, repRewards.get(repFaction));
                      }
                 }
                CoordinatedEffect cE = new CoordinatedEffect("QuestConclude"+completedQuest.getID());
             	cE.sendSourceOid(true);
             	cE.sendTargetOid(true);
             	cE.invoke(playerOid, playerOid);
            }
            
            return true;
        }
    }
    
    class UpdateObjHook implements Hook {
        public boolean processMessage(Message msg, int flags) {

            WorldManagerClient.UpdateMessage cMsg = (WorldManagerClient.UpdateMessage) msg;
            OID oid = cMsg.getSubject();
            
            // only send quest log data if object is asking about itself
            if (!oid.equals(cMsg.getTarget())) {
            	return true;
            }
                  if (Log.loggingDebug)
            	log.debug("QuestPlugin.UpdateObjHook: updating obj " + oid + " with quest info");
            QuestStateInfo qsInfo = getQuestStateInfo(oid);
            if(qsInfo == null) {
            	log.error("QuestPlugin.UpdateObjHook: QuestStateInfo is null");
            	return true;
            }
	    	for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
	    		qs.updateQuestLog();
	    	}
	    
		    log.debug("QuestPlugin.UpdateObjHook: updating obj " + oid + " with quest info before sendQuestHistoryLogInfo");
            
            sendQuestHistoryLogInfo(oid);//Dragonsan
          	log.debug("QuestPlugin.UpdateObjHook: updating obj " + oid + " with quest info after sendQuestHistoryLogInfo");
    
    	return true;
        }
    }

    class ResetQuestsHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		SubjectMessage qMsg = (SubjectMessage) msg;
    		OID oid = qMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("ResetQuestsHook: resetting quests for oid=" + oid);
            QuestStateInfo qsInfo = getQuestStateInfo(oid);
            for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
                if (Log.loggingDebug)
                    log.debug("ResetQuestsHook: resetting quest=" + qs.getQuestRef() + " for oid=" + oid);
                qs.deactivate();
				TargetedExtensionMessage rMsg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_REMOVE_QUEST_RESP, "ao.REMOVE_QUEST_RESP", qs.getPlayerOid(), qs.getQuestOid());
	             Engine.getAgent().sendBroadcast(rMsg);
                StateStatusChangeMessage cMsg = new StateStatusChangeMessage(oid, qs.getQuestRef());
				Engine.getAgent().sendBroadcast(cMsg);
            }
            qsInfo.setCurrentActiveQuests(new HashMap<Integer, QuestState>());
	    	return true;
    	}
    }
    
	class AbandonQuestHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			QuestClient.AbandonQuestMessage qMsg = (QuestClient.AbandonQuestMessage) msg;
			OID oid = qMsg.getPlayerOid();
			OID questID = qMsg.getQuestID();
			if (Log.loggingDebug)
				log.debug("AbandonQuestHook: removing quest " + questID + " for oid=" + oid);
			QuestStateInfo qsInfo = getQuestStateInfo(oid);
			for (QuestState qs : qsInfo.getCurrentActiveQuests().values()) {
				 if (Log.loggingDebug)Log.debug("Comparing quest ids: " + qs.getQuestOid() + " and " + questID);
				if (qs.getQuestOid().equals(questID)) {
					if (Log.loggingDebug)
						log.debug("AbandonQuestHook: found quest " + qs.getQuestRef() + " for oid=" + oid);
					qs.abandonQuest(oid);
					qs.deactivate();

					qsInfo.removeActiveQuest(qs.getQuestRef());

					TargetedExtensionMessage rMsg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_REMOVE_QUEST_RESP, "ao.REMOVE_QUEST_RESP", qs.getPlayerOid(), qs.getQuestOid());
					Engine.getAgent().sendBroadcast(rMsg);
					StateStatusChangeMessage cMsg = new StateStatusChangeMessage(oid, qs.getQuestRef());
					Engine.getAgent().sendBroadcast(cMsg);
					break;
				}
			}
			return true;
		}
	}

	public class GetQuestItemReqsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			QuestClient.GetQuestItemReqsMessage pMsg = (QuestClient.GetQuestItemReqsMessage) msg;
			OID oid = pMsg.getSubject();
			Log.debug("QUEST: got quest item reqs hook");

			Lock lock = getObjectLockManager().getLock(oid);
			lock.lock();
			try {
				// get the response
				LinkedList<Integer> itemList = new LinkedList<Integer>();
				QuestStateInfo qsInfo = getQuestStateInfo(oid);
				for (QuestState qs2 : qsInfo.getCurrentActiveQuests().values()) {
					if (qs2 != null && qs2 instanceof BasicQuestState) {
						BasicQuestState qs3 = (BasicQuestState) qs2;
						 if (Log.loggingDebug)Log.debug("QUEST: got BasicQuestState: " + qs3.getName());
						// if (qs3.getCompleted() == false && qs3.getConcluded() == false) {
						if (qs3.getConcluded() == false) {
							List<BasicQuestState.CollectionGoalStatus> itemReqs = qs3.getGoalsStatus();
							for (int j = 0; j < itemReqs.size(); j++) {
								BasicQuestState.CollectionGoalStatus cgStatus = itemReqs.get(j);
								if (cgStatus.getCurrentCount() < cgStatus.getTargetCount())
									itemList.add(cgStatus.getTemplateID());
							}
						}
					}
				}

				Engine.getAgent().sendObjectResponse(pMsg, itemList);
				 if (Log.loggingDebug)Log.debug("QUEST: GetQuestItemReqsHook: sent response: " + itemList.toString());
				return true;
			} finally {
				lock.unlock();
			}
		}
	}

	protected static String getItemTemplateIcon(int templateID) {
		Template template = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
		return (String) template.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ICON);
	}

	protected static String getItemTemplateName(int templateID) {
		 if (Log.loggingDebug)Log.debug("Q: getting item template name for templateID: " + templateID);
		Template template = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
		return (String) template.getName();
	}

	public static void sendRemoveQuestResp(OID playerOid, OID questOid) {
		TargetedExtensionMessage msg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_REMOVE_QUEST_RESP, "ao.REMOVE_QUEST_RESP", playerOid, questOid);
		if (Log.loggingDebug)
			Log.debug("QuestState.sendRemoveQuestResp: removing questOid=" + questOid + " from player=" + playerOid);
		Engine.getAgent().sendBroadcast(msg);
	}

	 /**
     * Sends down the QUEST_LOG_INFO message to the client with all of the information about a quest.
     * @param playerOid
     * @param questOid
     * @param questTitle
     * @param questDesc
     * @param questObjective
     * @param grades
     * @param expRewards
     * @param currencyRewards
     * @param itemRewards
     * @param itemRewardsToChoose
     * @param objectives
     */
    public static void sendQuestLogInfo(OID playerOid, OID questOid, int questId,  String questTitle, String questDesc,
    		String questObjective, int grades, HashMap<Integer, Integer> expRewards, 
            HashMap<Integer, HashMap<Integer, Integer>> currencyRewards,
            HashMap<Integer, HashMap<Integer, Integer>> itemRewards,
            HashMap<Integer, HashMap<Integer, Integer>> itemRewardsToChoose,
             HashMap<Integer, HashMap<Integer, Integer>>  repRewards,
             HashMap<Integer, List<String>> objectives, 
              boolean questComplete,HashMap<Integer, List<Integer>> orderRewards, HashMap<Integer,List<Integer>> orderRewardsToChoose  ) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.QUEST_LOG_INFO");
        //props.put("title", questTitle);
        //props.put("description", questDesc);
        //props.put("objective", questObjective);
        props.put("complete", questComplete);
        props.put("qId", questId);
     //   AgisQuest q = Agis.QuestManager.get((int)questOid.toLong());
     //   props.put("reqLevel", q.getQuestLevelReq());
           //HashMap<Integer, List<String>> objectivesList = new HashMap<Integer, List<String>>(objectives);
        //props.put("objectives", objectivesList);
        if (Log.loggingDebug) Log.debug("QUEST: got objectives map with entries: " + objectives.keySet() + " for quest: " + questTitle);
        //props.put("grades", grades);
        if (Log.loggingDebug) Log.debug("QUEST: num grades: " + grades);
        for (int i = 0; i <= grades; i++) {
        	// Objectives
			if (objectives.containsKey(i)) {
				 if (Log.loggingDebug)Log.debug("QUEST: got objectives for grade: " + i + " with entries: " + objectives.get(i).size());
				LinkedList<String> gradeObjectives = (LinkedList<String>) objectives.get(i);
				props.put("numObjectives" + i, gradeObjectives.size());
				for (int j = 0; j < gradeObjectives.size(); j++) {
					props.put("objective" + i + "_" + j, gradeObjectives.get(j));
				}
			}
	    	/*// Rewards
        	HashMap<Integer, Integer> rewards = itemRewards.get(i);
        	List<Integer> order = orderRewards.get(i); 
        	 if (Log.loggingDebug)Log.debug("QUEST PLUGIN: Adding rewards: " + rewards + "for grade " + i);
        	int pos = 0;
			if (rewards != null) {
				if (order != null) {
					for (Integer rewardID : order) {
						if (rewardID != -1) {
							props.put("rewards" + i + "_" + pos, rewardID);
							props.put("rewards" + i + "_" + pos + "Count", rewards.get(rewardID));
							pos++;
						}
					}
				} else {
					for (Integer rewardID : rewards.keySet()) {
						if (rewardID != -1) {
							props.put("rewards" + i + "_" + pos, rewardID);
							props.put("rewards" + i + "_" + pos + "Count", rewards.get(rewardID));
							pos++;
						}
					}
				}
			}
        	props.put("rewards"+ i, pos);
        	
        	// Rewards where you can only choose 1 item from the list
        	HashMap<Integer, Integer> rewards2 = itemRewardsToChoose.get(i);
        	List<Integer> order2 = orderRewardsToChoose.get(i); 
        	 if (Log.loggingDebug)Log.debug("QUEST PLUGIN: Adding rewards to choose: " + rewards2 + "for grade " + i);
        	pos = 0;
        	
        	if (rewards2 != null){
        		if(order2!=null) {
        	    for (int rewardID : order2) {
        	    	if (rewardID != -1) {
        	    		props.put("rewardsToChoose" + i + "_" + pos, rewardID);
        	    		props.put("rewardsToChoose" + i + "_" + pos + "Count", rewards2.get(rewardID));
        	    		pos++;
        	    	}
                }
        		}else{
                    for (Integer rewardID : rewards2.keySet()) {
                    	if (rewardID != -1) {
            	    		props.put("rewardsToChoose" + i + "_" + pos, rewardID);
            	    		props.put("rewardsToChoose" + i + "_" + pos + "Count", rewards2.get(rewardID));
            	    		pos++;
            	    	}
                    }
        		}
        	}
        	props.put("rewardsToChoose" + i, pos);
        	
        	HashMap<Integer, Integer> _repRewards = repRewards.get(i);
        	
        	pos = 0;
        	if (_repRewards != null){
        	    for (int rewardID : _repRewards.keySet()) {
        	    	if (rewardID != -1) {
        	    		Faction f =  Agis.FactionManager.get(rewardID);
        	    		props.put("rewardsRep" + i + "_" + pos, f.getName());
        	    		props.put("rewardsRep" + i + "_" + pos + "Count", _repRewards.get(rewardID));
        	    		pos++;
        	    	}
                }
        	}
        	props.put("rewardsRep" + i , pos);
        	
        	
        	// Xp Rewards
        	props.put("xpReward" + i, expRewards.get(i));
        	// Currency Rewards
        	HashMap<Integer, Integer> currencies = currencyRewards.get(i);
        	pos = 0;
        	if (currencies != null){
        	    for (int currencyID : currencies.keySet()) {
        	    	if (currencyID != -1) {
        	    		props.put("currency" + i + "_" + pos, currencyID);
        	    		props.put("currency" + i + "_" + pos + "Count", currencies.get(currencyID));
        	    		pos++;
        	    	}
                }
        	}
        	props.put("currencies" + i, pos);
        	*/
        }
        Log.debug("QUEST: about to send quest offer");
        if (Log.loggingDebug)Log.debug("QUEST: sendQuestLogInfo props "+props );
		TargetedExtensionMessage msg = new TargetedExtensionMessage(/* QuestClient.MSG_TYPE_QUEST_LOG_INFO */WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, questOid, false, props);
	     if (Log.loggingDebug)
            Log.debug("QuestState.sendQuestLogInfo: updating player=" + playerOid + " with quest="
                      + questTitle);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    
    /**
     * Sends down the QUEST_HISTORY_LOG_INFO message to the client with all of the information about a historical quest.
     * @param playerOid
     */
    public static void sendQuestHistoryLogInfo(OID playerOid) {
    	 if (Log.loggingDebug)	log.debug("Start sendQuestHistoryLogInfo "+playerOid);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.QUEST_HISTORY_LOG_INFO");
        ArrayList<Integer> cQuests = QuestClient.getCompletedQuests(playerOid);
        ArrayList<Integer> completedQuests = new ArrayList<Integer>();
        if (cQuests==null)
        	return;
    	for (int i = 0; i < cQuests.size(); i++) {
    		if (!completedQuests.contains(cQuests.get(i))) {
    			completedQuests.add(cQuests.get(i));
    		}
    	}
        props.put("numQuests", completedQuests.size());
    	for (int i = 0; i < completedQuests.size(); i++) {
    		 if (Log.loggingDebug) log.debug("Start sendQuestHistoryLogInfo in for "+i);
            	AgisQuest q = Agis.QuestManager.get(completedQuests.get(i));
    		 props.put("questId"+i, q.getOid().toLong());
    		 props.put("qId"+i, q.getID());
     		 //props.put("title"+i, q.getName());
     		 //props.put("description"+i, q.getDesc());
    	     //props.put("objective"+i, q.getObjective());
    	    // props.put("complete"+i, q.getCompletionText().get(0));
    	     props.put("level"+i, q.getQuestLevelReq());
    	     if (Log.loggingDebug)
    	        Log.debug("QuestState.sendQuestHistoricalLogInfo: updating player=" + playerOid + " with quest ="+ q.getName());
    	}
    	 if (Log.loggingDebug)log.debug("Start sendQuestHistoryLogInfo za for "+playerOid);
        
    	 if (Log.loggingDebug) Log.debug("QUEST: about to send quest history");
    	   if (Log.loggingDebug)Log.debug("QUEST: sendQuestHistoryLogInfo props "+props );
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION,playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(msg);
        if (Log.loggingDebug) Log.debug("QUEST: after send quest history");
        
    }
    
    

    public void sendQuestInfo(OID playerOid, OID sourceOid, LinkedList<Integer> questsOnOffer, boolean deleteItem)
    {
    	// First verify they can start each offered quest
    	for (int questID : questsOnOffer) {
            if (!QuestClient.canPlayerStartQuest(playerOid, questID)) {
                return;
            }
        }
    	
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.QUEST_OFFER");
        props.put("numQuests", questsOnOffer.size());
        props.put("npcID", sourceOid);
    	for (int i = 0; i < questsOnOffer.size(); i++) {
    		AgisQuest q = Agis.QuestManager.get(questsOnOffer.get(i));
    		//props.put("title"+i, q.getName());
    		props.put("questID"+i, q.getOid());
    		props.put("qId"+i, q.getID());
      		//props.put("description"+i, q.getDesc());
    		//props.put("objective"+i, q.getObjective());
    		HashMap<Integer, List<String>> objectivesList = new HashMap<Integer, List<String>>(q.getObjectives());
            //props.put("objectives", objectivesList);
    		//props.put("grades"+i, q.getSecondaryGrades());
    		 if (Log.loggingDebug)	Log.debug("QUEST: num grades: " + q.getSecondaryGrades());
            /*for (int j = 0; j <= q.getSecondaryGrades(); j++) {
            	// Objectives
            	if (objectivesList.containsKey(j)) {
            		LinkedList<String> gradeObjectives = (LinkedList<String>) objectivesList.get(j);
            		props.put("numObjectives" + i + "_" + j, gradeObjectives.size());
            		for (int k = 0; k < gradeObjectives.size(); k++) {
            			props.put("objective" + i + "_" + j + "_" + k, gradeObjectives.get(k));
            		}
            	}
            	HashMap<Integer, Integer> rewards = q.getRewards().get(j);
            	List<Integer> order = q.getItemRewardsOrder().get(j);
            	 if (Log.loggingDebug)log.debug("QUEST: rewards="+rewards+" order="+order);
            	int pos = 0;
            	if (rewards != null){
            	    for (Integer rewardID : order) {
            	    	if (rewardID != -1) {
            	    		props.put("rewards" + i + "_" + j + "_" + pos, rewardID);
            	    		props.put("rewards" + i + "_" + j + "_" + pos + "Count", rewards.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewards"+ i + " " + j, pos);
            	
            	// Rewards where you can only choose 1 item from the list
            	HashMap<Integer, Integer> rewards2 = q.getRewardsToChoose().get(j);
            	List<Integer> order2 = q.getItemRewardsToChooseOrder().get(j);
            	 if (Log.loggingDebug)log.debug("QUEST: choose rewards="+rewards2+" order="+order2);
            	pos = 0;
            	if (rewards2 != null){
            	    for (int rewardID : order2) {
            	    	if (rewardID != -1) {
            	    		props.put("rewardsToChoose" + i + "_" + j + "_" + pos, rewardID);
            	    		props.put("rewardsToChoose" + i + "_" + j + "_" + pos + "Count", rewards2.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewardsToChoose" + i + " " + j, pos);
            	
            	HashMap<Integer, Integer> repRewards = q.getRepRewards().get(j);
            	pos = 0;
            	if (repRewards != null){
            	    for (int rewardID : repRewards.keySet()) {
            	    	if (rewardID != -1) {
            	    		Faction f =  Agis.FactionManager.get(rewardID);
            	    		props.put("rewardsRep" + i + "_" + j + "_" + pos, f.getName());
            	    		props.put("rewardsRep" + i + "_" + j + "_" + pos + "Count", repRewards.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewardsRep" + i + " " + j, pos);
                
            	
            	// Xp Rewards
            	props.put("xpReward" + i + " " + j, q.getXpReward().get(i));
            	// Currency rewards
            	HashMap<Integer, Integer> currencyRewards = q.getCurrencyRewards().get(j);
            	pos = 0;
            	if (currencyRewards != null){
            	    for (int currencyID : currencyRewards.keySet()) {
            	    	if (currencyID != -1) {
            	    		props.put("currency" + i + "_" + j + "_" + pos, currencyID);
            	    		props.put("currency" + i + "_" + j + "_" + pos + "Count", currencyRewards.get(currencyID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("currencies" + i + " " + j, pos);
    	    }*/
    	}
    	 if (Log.loggingDebug)Log.debug("QUEST: sendQuestInfo props: " + props.toString());
		TargetedExtensionMessage msg = new TargetedExtensionMessage(/* QuestClient.MSG_TYPE_QUEST_INFO */WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, sourceOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
       
        lock.lock();
        try {
        	HashMap<Integer, OID> quests = new HashMap<Integer, OID>();
        	for (int questID : questsOnOffer) {
        		// If deleteItem is set to true, store the sourceOid (really an itemOid)
        		if (deleteItem) {
        			quests.put(questID, sourceOid);
        		} else {
        			quests.put(questID, null);
        		}
        	}
        	offeredQuestMap.put(playerOid, quests);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Sends down the updated stat of a Quest. Used when the objective status for a Quest has been 
     * updated (for example, the player looted a Quest Item).
     * @param playerOid
     * @param questOid
     * @param complete
     * @param objectives
     */
	public static void sendQuestStateInfo(OID playerOid, OID questOid, Boolean complete, HashMap<Integer, List<String>> objectives) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "ao.QUEST_STATE_INFO");
		HashMap<Integer, List<String>> objectivesList = new HashMap<Integer, List<String>>(objectives);
		// props.put("objectives", objectivesList);
		for (int grade : objectivesList.keySet()) {
			LinkedList<String> gradeObjectives = (LinkedList<String>) objectivesList.get(grade);
			props.put("numObjectives" + grade, gradeObjectives.size());
			for (int k = 0; k < gradeObjectives.size(); k++) {
				props.put("objective" + grade + "_" + k, gradeObjectives.get(k));
			}
		}
		props.put("complete", complete);
		 if (Log.loggingDebug)Log.debug("QUEST: sendQuestStateInfo props "+props );
		TargetedExtensionMessage msg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_QUEST_INFO, playerOid, questOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

    /**
     * Sends down information about the specified Quest that is currently in progress for the player. 
     * The information is then displayed by the NPC who ends the Quest.
     * @param playerOid
     * @param npcOid
     * @param questsInProgress
     */
    public static void sendQuestProgressInfo(OID playerOid, OID npcOid, LinkedList<QuestState> questsInProgress)
    {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("ext_msg_subtype", "ao.QUEST_PROGRESS");
    	props.put("numQuests", questsInProgress.size());
    	props.put("npcID", npcOid);
    	for (int i = 0; i < questsInProgress.size(); i++) {
    		QuestState qs = questsInProgress.get(i);
    		props.put("title"+i, qs.getQuestTitle());
    		props.put("questID"+i, qs.getQuestOid());
    		props.put("qId"+i, qs.getQuestRef());
    		//props.put("objective"+i, qs.getQuestObjective());
    		HashMap<Integer, List<String>> objectivesList = new HashMap<Integer, List<String>>(qs.getObjectiveStatus());
    		//props.put("objectives"+i, objectivesList);
    		//props.put("progress"+i, qs.getQuestProgressText());
    		props.put("complete"+i, qs.getCompleted());
    		
    		//props.put("grades"+i, qs.getGrades());
    		props.put("currentGrade"+i, qs.getCompletionLevel());
    		 if (Log.loggingDebug)Log.debug("QUEST: Quest Grade Completed: " + qs.getCompletionLevel());
            /*for (int j = 0; j <= qs.getGrades(); j++) {
            	if (objectivesList.containsKey(j)) {
            		LinkedList<String> gradeObjectives = (LinkedList<String>) objectivesList.get(j);
            		props.put("numObjectives" + i + "_" + j, gradeObjectives.size());
            		for (int k = 0; k < gradeObjectives.size(); k++) {
            			props.put("objective" + i + "_" + j + "_" + k, gradeObjectives.get(k));
            		}
            	}
            	HashMap<Integer, Integer> rewards = qs.getRewards().get(j);
            	List<Integer> order = qs.getItemRewardsOrder().get(j);
            	int pos = 0;
            	if (rewards != null && order !=null ){
            	    for (Integer rewardID : order) {
            	    	if (rewardID != -1) {
            	    		props.put("rewards" + i + "_" + j + "_" + pos, rewardID);
            	    		props.put("rewards" + i + "_" + j + "_" + pos + "Count", rewards.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewards"+ i + " " + j, pos);
            	
            	// Rewards where you can only choose 1 item from the list
            	HashMap<Integer, Integer> rewards2 = qs.getRewardsToChoose().get(j);
            	List<Integer> order2 = qs.getItemRewardsToChooseOrder().get(j);
            	pos = 0;
            	if (rewards2 != null && order2 !=null ){
            	    for (int rewardID : order2) {
            	    	if (rewardID != -1) {
            	    		props.put("rewardsToChoose" + i + "_" + j + "_" + pos, rewardID);
            	    		props.put("rewardsToChoose" + i + "_" + j + "_" + pos + "Count", rewards2.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewardsToChoose" + i + " " + j, pos);
            	
            	
            	HashMap<Integer, Integer> repRewards = qs.getRepRewards().get(j);
            	pos = 0;
            	if (repRewards != null){
            	    for (int rewardID : repRewards.keySet()) {
            	    	if (rewardID != -1) {
            	    		Faction f =  Agis.FactionManager.get(rewardID);
            	    		props.put("rewardsRep" + i + "_" + j + "_" + pos, f.getName());
            	    		props.put("rewardsRep" + i + "_" + j + "_" + pos + "Count", repRewards.get(rewardID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("rewardsRep" + i + " " + j, pos);
            	
            	
            	props.put("xpReward" + i + " " + j, qs.getXpRewards().get(i));
            	// Currency rewards
            	HashMap<Integer, Integer> currencyRewards = qs.getCurrencyRewards().get(j);
            	pos = 0;
            	if (currencyRewards != null){
            	    for (int currencyID : currencyRewards.keySet()) {
            	    	if (currencyID != -1) {
            	    		props.put("currency" + i + "_" + j + "_" + pos, currencyID);
            	    		props.put("currency" + i + "_" + j + "_" + pos + "Count", currencyRewards.get(currencyID));
            	    		pos++;
            	    	}
                    }
            	}
            	props.put("currencies" + i + " " + j, pos);
            	
            	props.put("completion" + i + "_" + j, qs.getQuestCompletionText().get(j));
    	    }*/
    	}
    	 if (Log.loggingDebug)Log.debug("QUEST: sendQuestProgressInfo props: " + props.toString());
		TargetedExtensionMessage msg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_QUEST_INFO, playerOid, playerOid, false, props);
	 	Engine.getAgent().sendBroadcast(msg);
    }
    
    // Log the login information and send a response
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            OID instanceOid = message.getInstanceOid();
        //    Log.debug("QuestLoginHook: playerOid=" + playerOid + " sendQuestHistoryLogInfo");
         //   sendQuestHistoryLogInfo(playerOid);//Dragonsan
          //  Log.debug("QuestLoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid);
            QuestPlugin.sendQuestHistoryLogInfo(message.getSubject());//Dragonsan send History
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    
    public class CategoryUpdatedHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisMobClient.categoryUpdatedMessage pMsg = (AgisMobClient.categoryUpdatedMessage) msg;
            OID playerOid = pMsg.getSubject();
            int category = (Integer) pMsg.getProperty("category");
            if (Log.loggingDebug) Log.debug("CATEGORY: updating category for player " + playerOid + " and category: " + category);
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
                // add this quest state to the player's quest states object
                QuestStateInfo qsInfo = getQuestStateInfo(playerOid);
                qsInfo.categoryUpdated(category);
            }
            finally {
                lock.unlock();
            }
            return true;
        }
    }
    
    // players who have asked for quest info, we keep track of what quest we gave them
    private Map<OID, HashMap<Integer, OID>> offeredQuestMap = new HashMap<OID, HashMap<Integer, OID>>();

    private static final Logger log = new Logger("QuestPlugin");
}
