package atavism.agis.behaviors;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import atavism.agis.objects.*;
import atavism.agis.plugins.AgisMobClient.DialogueOptionChosenMessage;
import atavism.agis.plugins.AgisMobClient.GetNpcInteractionsMessage;
import atavism.agis.plugins.AgisMobClient.StartNpcInteractionMessage;
import atavism.agis.plugins.QuestClient.*;
import atavism.agis.plugins.*;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;

/**
 * 
 * Controls both Quest and Dialogue interactions for an NPC. This needs to be given to any mob that can start or end
 * Quests and Dialogues. 
 * Should be renamed to InteractionBehavior at some point in the future.
 * @author Andrew Harrison
 *
 */
@Deprecated  
public class QuestBehavior extends Behavior {

	/**
	 * Sets up the subscription to receive messages the behavior needs to catch.
	 */
    public void initialize() {

    	OID mobOid = this.getObjectStub().getOid();
        if (Log.loggingDebug)
            log.debug("QuestBehavior.initialize: my moboid=" + mobOid);

        SubjectFilter filter = new SubjectFilter(mobOid);
        filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
        filter.addType(QuestClient.MSG_TYPE_REQ_QUEST_INFO);
        //filter.addType(QuestClient.MSG_TYPE_QUEST_RESP);
        filter.addType(QuestClient.MSG_TYPE_REQ_CONCLUDE_QUEST);
        filter.addType(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE);
        filter.addType(ClassAbilityClient.MSG_TYPE_SKILL_LEVEL_CHANGE);
        filter.addType(QuestClient.MSG_TYPE_REQ_QUEST_PROGRESS);
        filter.addType(QuestClient.MSG_TYPE_COMPLETE_QUEST);
        filter.addType(QuestClient.MSG_TYPE_QUEST_CONCLUDE_UPDATE);
        filter.addType(AgisMobClient.MSG_TYPE_GET_INTERACTION_OPTIONS);
        filter.addType(AgisMobClient.MSG_TYPE_START_INTERACTION);
        filter.addType(AgisMobClient.MSG_TYPE_DIALOGUE_OPTION_CHOSEN);
        filter.addType(AgisInventoryClient.MSG_TYPE_GET_MERCHANT_LIST);
        filter.addType(AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT);
     //   filter.addType(AgisMobClient.MSG_TYPE_DIALOG_CHECK);
        eventSub = Engine.getAgent().createSubscription(filter, this);
        Log.debug("QuestBehavior: created subject filter for oid=" + mobOid);
        
        MessageTypeFilter filter2 = new MessageTypeFilter();
        filter2.addType(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE);
        filter2.addType(ClassAbilityClient.MSG_TYPE_SKILL_LEVEL_CHANGE);
        filter2.addType(AgisMobClient.MSG_TYPE_DIALOG_CHECK);
              //filter2.addType(QuestClient.MSG_TYPE_COMPLETE_QUEST);
        eventSub2 = Engine.getAgent().createSubscription(filter2, this);

        // Subscribe to all state status change messages. This is inefficient, but it works.
        MessageTypeFilter statusFilter = new MessageTypeFilter(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
        statusSub = Engine.getAgent().createSubscription(statusFilter, this);
    }

    public void activate() {
    }

    /**
     * Removes the subscriptions this behavior has.
     */
    public void deactivate() {
        lock.lock();
        try {
            if (eventSub != null) {
                Engine.getAgent().removeSubscription(eventSub);
                eventSub = null;
            }
            if (eventSub2 != null) {
                Engine.getAgent().removeSubscription(eventSub2);
                eventSub2 = null;
            }
            if (statusSub != null) {
                Engine.getAgent().removeSubscription(statusSub);
                statusSub = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void handleMessage(Message msg, int flags) {
    	  if (Log.loggingTrace)	log.trace(" Quest Behav handleMessage "+msg+" "+msg.getMsgType());
    	 if (msg instanceof AgisMobClient.DialogCheckMessage) {
    		 AgisMobClient.DialogCheckMessage updateMsg = (AgisMobClient.DialogCheckMessage) msg;
    		 if (Log.loggingTrace)	 log.trace(" Quest Behav handleMessage DialogCheckMessage");
    	    	 
             processUpdateMsg(updateMsg);
    	 } else if (msg instanceof WorldManagerClient.UpdateMessage) {
            WorldManagerClient.UpdateMessage updateMsg = (WorldManagerClient.UpdateMessage) msg;
            processUpdateMsg(updateMsg);
        } else if (msg instanceof GetNpcInteractionsMessage) {
        	GetNpcInteractionsMessage reqMsg = (GetNpcInteractionsMessage) msg;
        	processInteractionsRequestMsg(reqMsg);
        } else if (msg instanceof StartNpcInteractionMessage) {
        	StartNpcInteractionMessage reqMsg = (StartNpcInteractionMessage) msg;
        	processStartInteractionMsg(reqMsg);
        } else if (msg instanceof DialogueOptionChosenMessage) {
        	DialogueOptionChosenMessage reqMsg = (DialogueOptionChosenMessage) msg;
        	processDialogueOptionChosenMsg(reqMsg);
        } else if (msg instanceof QuestClient.QuestResponseMessage) {
            QuestClient.QuestResponseMessage respMsg = (QuestClient.QuestResponseMessage) msg;
            processQuestRespMsg(respMsg);
        } else if (msg instanceof QuestClient.StateStatusChangeMessage) {
            QuestClient.StateStatusChangeMessage nMsg = (QuestClient.StateStatusChangeMessage) msg;
            processStateStatusChangeMsg(nMsg);
            QuestPlugin.sendQuestHistoryLogInfo(nMsg.getSubject());//Dragonsan send History
        } else if (msg instanceof ClassAbilityClient.levelChangeMessage) {
        	if (Log.loggingDebug) log.debug("QUEST: level change message");
        	ClassAbilityClient.levelChangeMessage nMsg = (ClassAbilityClient.levelChangeMessage) msg;
        	processLevelChangeMsg(nMsg);
        } else if (msg instanceof ClassAbilityClient.skillLevelChangeMessage) {
        	if (Log.loggingDebug) 	log.debug("QUEST: skill level change message");
        	ClassAbilityClient.skillLevelChangeMessage nMsg = (ClassAbilityClient.skillLevelChangeMessage) msg;
        	processSkillLevelChangeMsg(nMsg);
        } else if (msg instanceof TargetedExtensionMessage) {
        	TargetedExtensionMessage eMsg = (TargetedExtensionMessage) msg;
        	if (Log.loggingDebug) log.debug("MOB: targetedExtesionType: " + eMsg.getMsgType());
        	if (eMsg.getMsgType().equals(QuestClient.MSG_TYPE_COMPLETE_QUEST)) {
        		if (Log.loggingDebug) 	log.debug("QUEST BEHAV: complete quest message caught");
                //processReqConcludeMsg(eMsg);
        	}
        } else if (msg instanceof QuestClient.CompleteQuestMessage) {
        	QuestClient.CompleteQuestMessage eMsg = (QuestClient.CompleteQuestMessage) msg;
        	processReqConcludeMsg(eMsg);
            QuestPlugin.sendQuestHistoryLogInfo(eMsg.getSubject());//Dragonsan send History
        } else if (msg instanceof QuestClient.ConcludeUpdateMessage) {
        	QuestClient.ConcludeUpdateMessage concMsg = (QuestClient.ConcludeUpdateMessage) msg;
        	processConcludeUpdateMsg(concMsg);
            QuestPlugin.sendQuestHistoryLogInfo(concMsg.getSubject());//Dragonsan send History
        } else if (msg instanceof AgisInventoryClient.getMerchantListMessage) {
        	AgisInventoryClient.getMerchantListMessage concMsg = (AgisInventoryClient.getMerchantListMessage) msg;
        	processGetMerchantListMsg(concMsg);
        } else if (msg instanceof AgisInventoryClient.purchaseItemFromMerchantMessage) {
        	AgisInventoryClient.purchaseItemFromMerchantMessage concMsg = (AgisInventoryClient.purchaseItemFromMerchantMessage) msg;
        	processPurchaseItemFromMerchantMsg(concMsg);
        } else {
            log.error("onMessage: got unknown msg: " + msg);
            return; //return false;
        }
        //return true;
    }

    private void processStateStatusChangeMsg(QuestClient.StateStatusChangeMessage msg) {
    	OID playerOid = msg.getSubject();
        int questRef = msg.getQuestRef();
        if (Log.loggingDebug)
			log.debug("processStateStatusChangeMsg: myOid=" + getObjectStub().getOid() + " playerOid=" + playerOid + " questRef=" + questRef);
     
        handleQuestState(playerOid);
    }
    
    private void processConcludeUpdateMsg(QuestClient.ConcludeUpdateMessage msg) {
    	OID playerOid = msg.getSubject();
        if (Log.loggingDebug)
			log.debug("processConcludeUpdateMsg: myOid=" + getObjectStub().getOid() + " playerOid=" + playerOid);
      
        handleQuestState(playerOid);
    }
    
    /**
     * Processes the request to conclude a Quest. If the Quest is completed
     * it will give the rewards to the player and remove the Quest from their Log.
     * @param msg
     */
    private void processReqConcludeMsg(CompleteQuestMessage msg) {
    	Log.debug("processReqConcludeMsg: msg=" + msg);
    	OID myOid = getObjectStub().getOid();
    	//OID playerOid = msg.getSubject();
        //int chosenReward = (Integer)msg.getProperty("reward");
        //OID questOID = (OID)msg.getProperty("questOID");
    	OID playerOid = msg.getPlayerOid();
    	int chosenReward = msg.getItemChosen();
    	OID questOID = msg.getQuestID();
        
        if (Log.loggingDebug)
            log.debug("processReqConcludeMsg: mob=" + myOid + ", player=" + playerOid);
        
        lock.lock();
        // find a completed quest
        AgisQuest completedQuest = null;
        HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
        for (QuestState qs : activeQuests.values()) {
        	int questRef = qs.getQuestRef();
            if (Log.loggingDebug)
                log.debug("processReqConcludedMsg: checking status for quest " + questRef + ", completed=" + qs.getCompleted()
                		+ " with questOID: " + questOID + " and qsOID:" + qs.getQuestOid());
            if (qs.getCompleted() == true && qs.getConcluded() == false && qs.getQuestOid().equals(questOID)) {
                // found the quest
                completedQuest = getEndQuest(questRef);
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
        if (completedQuest == null) {
            log.warn("processReqConcludedMsg: did not find completed quest");
            return;
        }

        Log.debug("QUEST: sending conclude message. Quest oid: " + questOID);
        QuestClient.ConcludeMessage concludeMsg = new QuestClient.ConcludeMessage(playerOid, myOid, questOID, chosenReward);
        Engine.getAgent().sendRPC(concludeMsg);

        handleQuestState(playerOid);
        lock.unlock();
    }
    
    private void processQuestRespMsg(QuestResponseMessage msg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = msg.getPlayerOid();
    	OID questID = msg.getQuestID();
        Boolean acceptStatus = msg.getAcceptStatus();

        Log.debug("processQuestResp: player=" + playerOid + " mob=" + myOid + " acceptStatus=" + acceptStatus);
        // find out what quest they are responding to
        LinkedList<AgisQuest> quests;
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
                log.debug("processQuestRespMsg: player " + playerOid + " declined quest for mob " + myOid);
            return;
        }
        if (quests == null) {
        	if (Log.loggingDebug)  log.debug("mob " + myOid + " hasnt offered player " + playerOid + " any quests");
            return;
        }

        for (AgisQuest q : quests) {
        	if (q.getOid().equals(questID))
        		quest = q;
        }

        if (quest == null) {
        	 if (Log.loggingDebug) 	log.debug("QUEST BEHAV: quest does not exist");
        	return;
        }
        
        
        if (Log.loggingDebug)
            log.debug("processQuestRespMsg: player " + playerOid + " has accepted quest " + quest + ", by mob " + myOid);
        
        // Send the start quest message
        QuestClient.startQuestForPlayer(playerOid, quest.getID());
        
        // update the players quest availability info
        log.debug("processQuestRespMsg: updating availability");
        
        handleQuestState(playerOid);
    }
 
     protected void offerQuestToPlayer(OID playerOid, LinkedList<AgisQuest> quests) {
    	 OID myOid = getObjectStub().getOid();
          if (Log.loggingDebug)
              log.debug("offerQuestToPlayer: sending quests info for quest: " + quests);
        lock.lock();
        try {
            offeredQuestMap.put(playerOid, quests);
        }
        finally {
            lock.unlock();
        }
        
        LinkedList<Integer> questIds = new LinkedList<Integer>();
        for(AgisQuest q : quests) {
        	questIds.add(q.getID());
        }

        QuestClient.offerQuestToPlayer(playerOid, myOid, questIds, false);
        //QuestPlugin.sendQuestInfo(playerOid, myOid, quests);
    }
     
    private LinkedList<AgisQuest> getAvailableQuests(OID myOid, OID playerOid) {   
        // get quest states for this player
        LinkedList<AgisQuest> offeredQuests = new LinkedList<AgisQuest>();
        //HashMap<Integer, Boolean> allQuests = QuestClient.getAllQuests(playerOid);
        for (AgisQuest q : getStartQuests()) {
            if (QuestClient.canPlayerStartQuest(playerOid, q.getID())) {
                offeredQuests.add(q);
            }
        }
         
        if (offeredQuests.size() == 0) {
            if (Log.loggingDebug)
                log.debug("processReqQuestInfoMsg: playerOid=" + playerOid + ", mobOid=" + myOid + ", no quest to offer");
            //return;
        }
        
        return offeredQuests;
    }
    
    private boolean isDialogueAvailableHelper(Dialogue dialogue, LinkedList<Integer> allDialogues, OID playerOid) {
    	Log.debug("QUEST: isDialogueAvailable - dialogue = " + dialogue.getName());
   	 	if (allDialogues.contains(dialogue.getID())) {
   	 		Log.debug("QUEST: isDialogueAvailable - player already has dialogue");
   	 		return false;
   	 	}
   		 
       	Log.debug("QUEST: isDialogueAvailable - prereq = " + dialogue.getPrereqDialogue());
       	if (!allDialogues.contains(dialogue.getID())) {
       		Log.debug("QUEST: isDialogueAvailable - prereq does not exist");
       		return false;
       	}
        // Check if the quest has a quest started requirement
        /*int questStartedReq = dialogue.getPrereqQuest();
        if (questStartedReq != -1) {
       	 for (int key : allDialogues.keySet()) {
       		 if (key == questStartedReq)
       			 qs2 = key;
       	 }
       	 Log.debug("QUEST: isQuestAvailable - prereq started = " + questStartedReq);
       	 if (qs2 == -1) {
       		 Log.debug("QUEST: isQuestAvailable - prereq started does not exist");
       		 return false;
       	 }
        }*/
        
        // Also check for level
        /*int levelReq = dialogue.getQuestLevelReq();
        AgisStat playerLevel = (AgisStat) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "level");
        int playerLevel2 = playerLevel.getCurrentValue();
        if (levelReq > playerLevel2)
       	 return false;*/
        
        // Check for faction
        /*int faction = dialogue.getFaction();
        String race = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "race");
        if (faction == 1) {
       	 if (!race.equals("Human"))
       		 return false;
        } else if (faction == 2) {
       	 if (!race.equals("Orc"))
       		 return false;
        }*/
        return true;
    }

    protected void offerDialogueToPlayer(OID playerOid, Dialogue dialogue) {
        if (Log.loggingDebug)
             log.debug("offerDialogueToPlayer: sending dialogue info for dialogue: " + dialogue);

        Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "npc_dialogue");
		props.put("npcOid", getObjectStub().getOid());
		props.put("dialogueID", dialogue.getID());
		props.put("title", dialogue.getName());
		props.put("text", dialogue.getText());
		props.put("numOptions", dialogue.getOptions().size());
		Log.debug("DIALOGUE: dialogue " + dialogue.getID() + " has " + dialogue.getOptions().size() + " options");
		for (int i = 0; i < dialogue.getOptions().size(); i++) {
			Dialogue.DialogueOption option = dialogue.getOptions().get(i);
			props.put("option" + i + "action", option.action);
			props.put("option" + i + "actionID", option.actionID);
			props.put("option" + i + "text", option.text);
		}
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
				playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Get the list of Dialogues that are available to the player from this NPC. Completed Dialogues are removed from the 
     * list of Dialogues the NPC offers.
     * @param myOid
     * @param playerOid
     * @return
     */
    private LinkedList<Dialogue> getAvailableDialogues(OID myOid, OID playerOid) {   
        // get dialogue states for this player
        LinkedList<Dialogue> offeredDialogues = new LinkedList<Dialogue>();
        //LinkedList<Integer> completedDialogues = (LinkedList) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "completedDialogues");
        for (Dialogue d : getStartDialogues()) {
            //if (isDialogueAvailableHelper(q, completedDialogues, playerOid)) {
            	offeredDialogues.add(d);
            //}
        }
         
        if (offeredDialogues.size() == 0) {
            if (Log.loggingDebug)
                log.debug("processReqQuestInfoMsg: playerOid=" + playerOid + ", mobOid=" + myOid + ", no dialogue to offer");
        }
        return offeredDialogues;
    }
    
    /**
     * Sends all the interaction options that this NPC can offer to the requesting player.
     * @param reqMsg
     */
    private void processInteractionsRequestMsg(GetNpcInteractionsMessage reqMsg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = reqMsg.getPlayerOid();
        
        if (Log.loggingDebug)
            log.debug("processInteractionsRequestMsg: mob=" + myOid + ", player=" + playerOid);
        
        
        // Work out how many interactions there are
        LinkedList<AgisQuest> offeredQuests = getAvailableQuests(myOid, playerOid);
        LinkedList<QuestState> progressQuests = getQuestProgress(myOid, playerOid);
        LinkedList<Dialogue> dialogues = getAvailableDialogues(myOid, playerOid);
        int totalInteractions = offeredQuests.size() + progressQuests.size();
        Dialogue chatDialogue = null;
        for (int i = 0; i < dialogues.size(); i++) {
        	if (dialogues.get(i).getOptions().size() > 0 || chatDialogue != null) {
        		totalInteractions++;
        	} else {
        		chatDialogue = dialogues.get(i);
        	}
        }
        if (merchantTable > 0)
        	totalInteractions++;
        
        // Add other actions
        totalInteractions += otherActions.size();
        
        // If we have a chat dialogue and another interaction, remove the dialogue from the list
        if (chatDialogue != null && totalInteractions > 0) {
        	dialogues.remove(chatDialogue);
        }
        
        Log.debug("INTERAC: total interactions: " + totalInteractions);
        if (totalInteractions > 1 || otherActions.size() > 0 || (totalInteractions == 1 && chatDialogue != null)) {
        	// send down interaction options message
        	Map<String, Serializable> props = new HashMap<String, Serializable>();
    		props.put("ext_msg_subtype", "npc_interactions");
    		props.put("npcOid", getObjectStub().getOid());
    		if (chatDialogue != null) {
    			props.put("dialogue_text", chatDialogue.getText());
    		} else {
    			props.put("dialogue_text", "");
    		}
    		int i = 0;
    		for (AgisQuest q : offeredQuests) {
    			props.put("interactionType_" + i, "offered_quest");
    			props.put("interactionTitle_" + i, q.getName());
    			props.put("interactionID_" + i, q.getID());
    			props.put("interactionValue_" + i, false);//dragonsan
    			props.put("interactionLevel_" + i, q.getQuestLevelReq());//dragonsan
        			i++;
    		}
    		for (QuestState q : progressQuests) {
    			props.put("interactionType_" + i, "progress_quest");
    			props.put("interactionTitle_" + i, q.getName());
    			props.put("interactionID_" + i, q.getQuestRef());
    			props.put("interactionValue_" + i, q.getCompleted());//dragonsan
    			props.put("interactionLevel_" + i, q.getReqLevel());//dragonsan
    	        		i++;
    		}
    		for (Dialogue d : dialogues) {
    			props.put("interactionType_" + i, "dialogue");
    			props.put("interactionTitle_" + i, d.getName());
    			props.put("interactionID_" + i, d.getID());
    			props.put("interactionValue_" + i, false);//dragonsan
    	        		i++;
    		}
    		for (String action : otherActions) {
    			props.put("interactionType_" + i, action);
    			props.put("interactionTitle_" + i, action);
    			props.put("interactionID_" + i, -1);
    			props.put("interactionLevel_" + i, false);//dragonsan
    			props.put("interactionValue_" + i, false);//dragonsan
    	    	      	i++;
    		}
    		if (merchantTable > 0) {
    			props.put("interactionType_" + i, "merchant");
    			props.put("interactionTitle_" + i, "View items for sale");
    			props.put("interactionID_" + i, merchantTable);
    			props.put("interactionLevel_" + i, false);//dragonsan
    			props.put("interactionValue_" + i, false);//dragonsan
    	    	       }
    		
    		props.put("numInteractions", totalInteractions);
    		TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    				playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
    		Log.debug("INTERAC: sending down interaction options");
        } else {
        	Log.debug("INTERAC: sending single interaction");
        	if (offeredQuests.size() != 0) {
        		Log.debug("INTERAC: sending offered Quests");
        		offerQuestToPlayer(playerOid, offeredQuests);
        	} else if (progressQuests.size() != 0) {
        		Log.debug("INTERAC: sending progress Quest");
        		QuestPlugin.sendQuestProgressInfo(playerOid, myOid, progressQuests);
        	} else if (dialogues.size() != 0) {
        		Log.debug("INTERAC: sending dialogue");
        		offerDialogueToPlayer(playerOid, dialogues.get(0));
        	} else if (merchantTable > 0) {
        		Log.debug("INTERAC: sending merchantTable");
        		sendMerchantList(playerOid);
        	}
        	Log.debug("INTERAC: sent single interaction");
        }
        
        return;
    }
    
    /**
     * Starts the requested interaction.
     * @param reqMsg
     */
    private void processStartInteractionMsg(StartNpcInteractionMessage reqMsg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = reqMsg.getPlayerOid();
    	
    	 if (Log.loggingDebug)
             log.debug("processStartInteractionMsg: mob=" + myOid + ", player=" + playerOid);
        
        if (reqMsg.getInteractionType().equals("offered_quest")) {
        	LinkedList<AgisQuest> offeredQuests = getAvailableQuests(myOid, playerOid);
        	for (AgisQuest q : offeredQuests) {
    			if (q.getID() == reqMsg.getInteractionID()) {
    				LinkedList<AgisQuest> questOffered = new LinkedList<AgisQuest>();
    				questOffered.add(q);
    				offerQuestToPlayer(playerOid, questOffered);
    				break;
    			}
    		}
        } else if (reqMsg.getInteractionType().equals("progress_quest")) {
        	LinkedList<QuestState> progressQuests = getQuestProgress(myOid, playerOid);
        	for (QuestState q : progressQuests) {
        		if (q.getQuestRef() == reqMsg.getInteractionID()) {
        			LinkedList<QuestState> questInProgress = new LinkedList<QuestState>();
        			questInProgress.add(q);
        			QuestPlugin.sendQuestProgressInfo(playerOid, myOid, questInProgress);
        			break;
        		}
        	}
        } else if (reqMsg.getInteractionType().equals("dialogue")) {
        	LinkedList<Dialogue> dialogues = getAvailableDialogues(myOid, playerOid);
        	for (Dialogue d : dialogues) {
    			if (d.getID() == reqMsg.getInteractionID()) {
    				offerDialogueToPlayer(playerOid, d);
    				break;
    			}
    		}
        } else if (reqMsg.getInteractionType().equals("merchant")) {
        	sendMerchantList(playerOid);
        }
    }
    
    /**
     * Processes the dialogue option chosen by the player.
     * @param reqMsg
     */
    private void processDialogueOptionChosenMsg(DialogueOptionChosenMessage reqMsg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = reqMsg.getPlayerOid();
    	
    	 if (Log.loggingDebug)
             log.debug("processDialogueOptionChosenMsg: mob=" + myOid + ", player=" + playerOid);
        
    	int dialogueID = reqMsg.getDialogueID();
        int actionID = reqMsg.getActionID();
        String actionType = reqMsg.getInteractionType();
        Dialogue dialogue = null;
        
        // Verify the mob has the dialogue and the dialogue has the action
        for (Dialogue d : getStartDialogues()) {
            if (d.getID() == dialogueID) {
            	dialogue = d;
            	break;
            }
        }
        
        if (dialogue == null) {
        	return;
        }
        // TODO: action check
        for(Dialogue.DialogueOption option : dialogue.getOptions()) {
        	if (option.action.equals(actionType) && option.actionID == actionID && option.itemReq > 0) {
        		Log.debug("DIALOGUE: checking item requirement for dialogue action");
        		OID itemOid = InventoryClient.findItem(playerOid, option.itemReq);
        		if (itemOid == null) {
        			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.MISSING_ITEM, 0, "");
        			return;
        		}
        	}
        }
        
        if(actionType.equals("Dialogue")) {
        	Dialogue nextDialogue = AgisMobPlugin.getDialogue(actionID);
        	offerDialogueToPlayer(playerOid, nextDialogue);
        } else if (actionType.equals("Quest")) {
        	Map<String, Serializable> props = new HashMap<String, Serializable>();
    		props.put("ext_msg_subtype", "close_dialogue");
    		TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    				playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
    		
        	LinkedList<Integer> quests = new LinkedList<Integer>();
        	quests.add(actionID);
        	QuestClient.offerQuestToPlayer(playerOid, myOid, quests, false);
        } else if (actionType.equals("Ability")) {
        	Map<String, Serializable> props = new HashMap<String, Serializable>();
    		props.put("ext_msg_subtype", "close_dialogue");
    		TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    				playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
    		
        	CombatClient.startAbility(actionID, myOid, playerOid, null);
        }
        
    }
    
    private LinkedList<QuestState> getQuestProgress(OID myOid, OID playerOid) {
        
        if (Log.loggingDebug)
            log.debug("processReqProgressMsg: mob=" + myOid + ", player=" + playerOid);
        
        // find all the quests the player is on
        AgisQuest completedQuest = null;
        HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
        LinkedList<QuestState> progressQuests = new LinkedList<QuestState>();
        for (QuestState qs : activeQuests.values()) {
        	int questRef = qs.getQuestRef();
            if (Log.loggingDebug)
                log.debug("processReqProgressMsg: checking status for quest " + questRef + ", completed=" + qs.getCompleted());
            if (qs.getConcluded() == false) {
                // found a quest
                completedQuest = getEndQuest(questRef);
                if (completedQuest != null) {
                    if (Log.loggingDebug)
                        log.debug("processReqConcludeMsg: found a completed quest: " + questRef);
                    progressQuests.add(qs);
                }
                else {
                    log.warn("processReqConcludeMsg: quest is completed, but not in end quests");
                }
            }
        }
        
        //QuestPlugin.sendQuestProgressInfo(playerOid, myOid, progressQuests);
        return progressQuests;
    }
 
    public void processUpdateMsg(WorldManagerClient.UpdateMessage msg) {
    	OID myOid = msg.getSubject();
    	OID playerOid = msg.getTarget();
        if (Log.loggingTrace)log.trace("processUpdateMsg");
    	
        if (Log.loggingDebug)
            log.debug("processUpdateMsg: myOid=" + myOid + ", playerOid=" + playerOid);

        if (!myOid.equals(this.getObjectStub().getOid())) {
            log.debug("processUpdateMsg: oids dont match!");
        }

        handleQuestState(playerOid);
        handleMerchantState(playerOid);
        handleOtherActionsState(playerOid);
        handleDialogueState(playerOid);
    }
    
    public void processUpdateMsg(AgisMobClient.DialogCheckMessage msg) {
    	OID playerOid = msg.getSubject();
    	 if (Log.loggingTrace) log.trace("ZBDS processUpdateMsg DialogCheckMessage");
        handleQuestState(playerOid);
        handleMerchantState(playerOid);
        handleOtherActionsState(playerOid);
        handleDialogueState(playerOid);
    }
    
    public void processLevelChangeMsg(ClassAbilityClient.levelChangeMessage msg) {
    	 if (Log.loggingTrace)	log.trace("QUEST: level change message 2");
    	OID playerOid = msg.getSubject();
        handleQuestState(playerOid);
    }

    public void processSkillLevelChangeMsg(ClassAbilityClient.skillLevelChangeMessage msg) {
    	 if (Log.loggingTrace) log.trace("QUEST: skill level change message 2");
    	OID playerOid = msg.getSubject();
        handleQuestState(playerOid);
    }

 
    protected void handleQuestState(OID playerOid) {
		OID myOid = getObjectStub().getOid();
		 if (Log.loggingTrace) log.trace("ZBDS handleQuestState");
		// Check Faction stance player to mob
		   boolean hasAvailableQuest = false;
	        boolean hasInProgressQuest = false;
	        boolean hasConcludableQuest = false;

		int plyRep = FactionClient.getStance(playerOid, myOid);
		if (plyRep < 0 || plyRep == FactionPlugin.Unknown) {
		    TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
	        propMsg.setProperty(AgisStates.QuestAvailable.toString(), hasAvailableQuest);
	        propMsg.setProperty(AgisStates.QuestInProgress.toString(), hasInProgressQuest);
	        propMsg.setProperty(AgisStates.QuestConcludable.toString(), hasConcludableQuest);
	        Engine.getAgent().sendBroadcast(propMsg);
			return;
		}

		/*	CombatInfo info = null;
		try {
			info = CombatPlugin.getCombatInfo(playerOid);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (info != null) {
			if (info.getAttackableTargets().containsKey(myOid)) {
				if (Log.loggingDebug)
					log.error("handleQuestState: myOid:" + myOid + " is in AttackableTargets of playerOid:" + playerOid);
				return;
			}
		} else {

		}*/
    	HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
        
        // ask the quest plugin if the player has completed the quests we can give out
        Collection<AgisQuest> startQuests = getStartQuests();
        Collection<AgisQuest> endQuests = getEndQuests();
        
        if (startQuests.isEmpty() && endQuests.isEmpty()) {
            // mob has no quests
            if (Log.loggingDebug)
                log.debug("QuestBehavior.handleQuestState: playerOid=" + playerOid + " has no quests, returning");
            return;
        }
        
        //FIXME Add Check Stance Faction 
        if (Log.loggingDebug)
            log.debug("QuestBehavior.handleQuestState: getting quest status for player=" + playerOid + ", starts "
                      + startQuests.size() + " quests, ends " + endQuests.size() + " quests");

     
		for (AgisQuest q : startQuests) {
			if (QuestClient.canPlayerStartQuest(playerOid, q.getID())) {
				if (Log.loggingDebug)
					log.debug("QuestBehavior.handleQuestState: playerOid=" + playerOid + " startQuest=" + q + " quest is available");
				hasAvailableQuest = true;
				String oidQName = playerOid + q.getName();
				if (!questStartAdvertised.contains(oidQName)) {
					// Commented out for Neos
					//WorldManagerClient.sendObjChatMsg(playerOid, 0, WorldManagerClient.getObjectInfo(myOid).name + " starts '" + q.getName() + "'.");
					questStartAdvertised.add(oidQName);
				}
			} else {
				if (Log.loggingDebug)
					log.debug("QuestBehavior.handleQuestState: playerOid=" + playerOid + " startQuest=" + q + " quest is not available");
			}
		}

        for (AgisQuest q : endQuests) {
        	int rep = FactionClient.getStance(playerOid, q.getFaction());
    	/*	
        	FactionStateInfo fsi = FactionPlugin.getFactionStateInfo(playerOid);
    		int subjectFaction = (Integer) fsi.getProperty(FactionStateInfo.FACTION_PROP);
    		Faction newFaction = Agis.FactionManager.get(subjectFaction);
    		int reputation = newFaction.getDefaultReputation(q.getFaction());
    		int rep = FactionPlugin.calculateStanding(reputation);
    		log.error("handleQuestState: q:"+q.getID()+" plyFaction:"+subjectFaction+" plyReputation:"+reputation+" plyRep:"+rep+ " q.getFaction():"+q.getFaction());*/
    			if (rep < 0 || rep == FactionPlugin.Unknown) {
    			continue;
    		}
        	  //byte status = questStatusMap.get(q.getName());
        	QuestState qs = null;
        	for (int key : activeQuests.keySet()) {
        		if (key == q.getID())
        			qs = activeQuests.get(key);
        	}
        	if (qs == null) {
        		Log.debug("QuestBehavior.handleQuestState: playerOid = " + playerOid + " no quest state for quest: " 
        				+ q.getName());
        	} else {
        		
        		if (Log.loggingDebug)
                    log.debug("QuestBehavior.handleQuestState: playerOid=" + playerOid + " endQuest=" + q
                              + " completed=" + qs.getCompleted() + " concluded=" + qs.getConcluded());
        		if (qs.getConcluded() == false)
        			hasInProgressQuest = true;
                if (qs.getCompleted() == true && qs.getConcluded() == false) {
                    hasConcludableQuest = true;
                    String oidQName = playerOid + "_" + q.getID();
                    if (!questConcludeAdvertised.contains(oidQName)) {
                    	// Commented out for Neos
                        //WorldManagerClient.sendObjChatMsg(playerOid, 0, WorldManagerClient.getObjectInfo(myOid).name + " concludes '" + q.getName() + "'.");
                        questConcludeAdvertised.add(oidQName);
                    }
                }
        	}
        }
        
        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty(AgisStates.QuestAvailable.toString(), hasAvailableQuest);
        propMsg.setProperty(AgisStates.QuestInProgress.toString(), hasInProgressQuest);
        propMsg.setProperty(AgisStates.QuestConcludable.toString(), hasConcludableQuest);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    /**
     * Checks a players faction and/or rep to determine whether this npc will sell items to the player in question.
     * @param playerOid
     */
    protected void handleMerchantState(OID playerOid) {
    	OID myOid = getObjectStub().getOid();
    	 if (Log.loggingDebug)
             log.debug(" handleMerchantState merchant");
    	
        if (merchantTable < 1) {
            // mob has no quests
            if (Log.loggingDebug)
                log.debug("QuestBehavior.handleMerchantState: playerOid=" + playerOid + " has no merchant table, returning");
            return;
        }

        boolean hasItemsToSell = true;
        //TODO: Check for faction
    	int rep = FactionClient.getStance(playerOid, myOid);
    	if (rep < 0 || rep == FactionPlugin.Unknown) {
    		hasItemsToSell = false;
		}
        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty(AgisStates.ItemsToSell.toString(), hasItemsToSell);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    /**
     * Checks a players faction and/or rep to determine whether this npc will sell items to the player in question.
     * @param playerOid
     */
    protected void handleOtherActionsState(OID playerOid) {
    	OID myOid = getObjectStub().getOid();
    	 if (Log.loggingDebug)
             log.debug(" handleOtherActionsState bank");
    	
        if (otherActions.isEmpty()) {
            // mob has no quests
            if (Log.loggingDebug)
                log.debug("QuestBehavior.handleMerchantState: playerOid=" + playerOid + " has no otherActions, returning");
            return;
        }

        if (otherActions.contains("Bank")) {
        	boolean isBankTeller = true;
        	//TODO: Check for faction
        	int rep = FactionClient.getStance(playerOid, myOid);
        	if (rep < 0 || rep == FactionPlugin.Unknown) {
        		isBankTeller = false;
    		}
        	TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        	propMsg.setProperty(AgisStates.BankTeller.toString(), isBankTeller);
        	Engine.getAgent().sendBroadcast(propMsg);
        }
    }
    
    /**
     * Checks a players faction and/or rep to determine whether this npc will sell items to the player in question.
     * @param playerOid
     */
    protected void handleDialogueState(OID playerOid) {
    	OID myOid = getObjectStub().getOid();
    	 if (Log.loggingDebug)
             log.debug(" handleDialogueState dialog");
        
        if (startDialoguesMap.isEmpty()) {
            // mob has no quests
            if (Log.loggingDebug)
                log.debug("QuestBehavior.handleDialogueState: playerOid=" + playerOid + " has no dialogues available");
            return;
        }
        
        //TODO: Check for faction
    	int rep = FactionClient.getStance(playerOid, myOid);
    	if (rep < 0 || rep == FactionPlugin.Unknown) {
    		 TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
    	        propMsg.setProperty("dialogue_available", 0);
    	        Engine.getAgent().sendBroadcast(propMsg);
    	        return;
		}
        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty("dialogue_available", startDialoguesMap.keySet().iterator().next());
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public void startsQuest(AgisQuest quest) {
        lock.lock();
        try {
        	if (quest != null) {
        		startQuestsMap.put(quest.getID(), quest);
        		if (Log.loggingDebug)
        			log.debug("startsQuest: added quest " + quest);
        	}
        }
        finally {
            lock.unlock();
        }
    }
    public void endsQuest(AgisQuest quest) {
        lock.lock();
        try {
        	if (quest != null) {
        		endQuestsMap.put(quest.getID(), quest);
        		if (Log.loggingDebug)
        			log.debug("endsQuest: adding quest " + quest);
        	}
        }
        finally {
            lock.unlock();
        }
    }
    
   
    public AgisQuest getQuest(int questID) {
        lock.lock();
        try {
            AgisQuest q = startQuestsMap.get(questID);
            if (q != null) {
                return q;
            }
            return endQuestsMap.get(questID);
        }
        finally {
            lock.unlock();
        }
    }
    
    public AgisQuest getStartQuest(String questName) {
        lock.lock();
        try {
            return startQuestsMap.get(questName);
        }
        finally {
            lock.unlock();
        }
    }
    
    public AgisQuest getEndQuest(int questID) {
        lock.lock();
        try {
            return endQuestsMap.get(questID);
        }
        finally {
            lock.unlock();
        }
    }
    
    public Collection<AgisQuest> getStartQuests() {
        lock.lock();
        try {
            return new LinkedList<AgisQuest>(startQuestsMap.values());
        }
        finally {
            lock.unlock();
        }
    }
    public Collection<AgisQuest> getEndQuests() {
        lock.lock();
        try {
            return new LinkedList<AgisQuest>(endQuestsMap.values());
        }
        finally {
            lock.unlock();
        }
    }
    public Collection<AgisQuest> getAllQuests() {
        lock.lock();
        try {
            Set<AgisQuest> l = new HashSet<AgisQuest>();
            l.addAll(getStartQuests());
            l.addAll(getEndQuests());
            return l;
        }
        finally {
            lock.unlock();
        }
    }
    public Collection<Integer> getAllQuestRefs() {
        lock.lock();
        try {
            Collection<Integer> set = new HashSet<Integer>();
            for (AgisQuest q : getStartQuests()) {
                set.add(q.getID());
                set.addAll(q.getQuestPrereqs());
            }
            for (AgisQuest q : getEndQuests()) {
                set.add(q.getID());
            }
            return set;
        }
        finally {
            lock.unlock();
        }
    }
    public Collection<Integer> getStartQuestRefs() {
        lock.lock();
        try {
            Collection<Integer> set = new HashSet<Integer>();
            for (AgisQuest q : getStartQuests()) {
                set.add(q.getID());
                set.addAll(q.getQuestPrereqs());
            }
            return set;
        }
        finally {
            lock.unlock();
        }
    }
    
    private Map<Integer, AgisQuest> startQuestsMap = new HashMap<Integer, AgisQuest>();
    private Map<Integer, AgisQuest> endQuestsMap = new HashMap<Integer, AgisQuest>();
    
    // players who have asked for quest info, we keep track of what quest we gave them
    private Map<OID, LinkedList<AgisQuest>> offeredQuestMap = new HashMap<OID, LinkedList<AgisQuest>>();
    
    private List<String> questStartAdvertised = new LinkedList<String>();
    private List<String> questConcludeAdvertised = new LinkedList<String>();
    
    // Dialogues
    public void startsDialogue(Dialogue dialogue) {
        lock.lock();
        try {
        	startDialoguesMap.put(dialogue.getID(), dialogue);
            if (Log.loggingDebug)
                log.debug("startsDialogue: added dialogue " + dialogue.getID());
        }
        finally {
            lock.unlock();
        }
    }
    public Collection<Dialogue> getStartDialogues() {
        lock.lock();
        try {
            return new LinkedList<Dialogue>(startDialoguesMap.values());
        }
        finally {
            lock.unlock();
        }
    }
    
    private Map<Integer, Dialogue> startDialoguesMap = new HashMap<Integer, Dialogue>();
    
    // Merchant
    
    // The number of the merchant table to use
    public void setMerchantTable(MerchantTable table) {
    	this.merchantTable = table.getID();
    	
    	for (int i = 0; i < table.getItems().size(); i++) {
    		MerchantItem merchantItem = new MerchantItem();
    		merchantItem.itemID = table.getItems().get(i);
    		merchantItem.maxCount = table.getItemCounts().get(i);
    		merchantItem.count = table.getItemCounts().get(i);
    		merchantItem.refreshTime = table.getItemRespawns().get(i);
    		itemsForSale.add(merchantItem);
    	}
    }
    public int getMerchantTable() {
    	return merchantTable;
    }
    private int merchantTable = -1;
    
    // What faction(s) the merchant will sell to
    public void setMerchantFaction(int factionNum) {
    	this.merchantFaction = factionNum;
    }
    public int getMerchantFaction() {
    	return merchantFaction;
    }
    private int merchantFaction = 0;
    
    ArrayList<MerchantItem> itemsForSale = new ArrayList<MerchantItem>();
    
    public void setOtherActions(ArrayList<String> otherActions) {
    	this.otherActions = otherActions;
    }
    public ArrayList<String> getOtherActions() {
    	return otherActions;
    }
    ArrayList<String> otherActions = new ArrayList<String>();
    
    class MerchantItem implements Runnable {
    	int itemID;
    	int maxCount;
    	int count;
    	int refreshTime;
    	int availableTime;
    	boolean refreshing = false;
    	
    	public void refresh() {
    		if (!refreshing && count < maxCount && refreshTime > 0) {
    			Engine.getExecutor().schedule(this, (long) refreshTime * 1000, TimeUnit.MILLISECONDS);
    			refreshing = true;
    		}
    	}
    	
		@Override
		public void run() {
			if (count < maxCount)
				count++;
			refreshing = false;
			
			refresh();
		}
    }
    
    /**
     * Processes the GetMerchantList Message resulting in the list of items the Merchant
     * has for sale being sent down.
     * @param msg
     */
    private void processGetMerchantListMsg(AgisInventoryClient.getMerchantListMessage msg) {
    	OID playerOid = msg.getPlayerOid();
    	sendMerchantList(playerOid);
    }
    
    /**
     * Sends down the list of item in the specified merchant table to the player.
     * @param oid
     * @param merchantTableNum
     */
    void sendMerchantList(OID oid) {
    	//int merchantTableNum = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "merchantTable");

        Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "MerchantList");
		props.put("npcOid", getObjectStub().getOid());
		int numItems = 0;
        for (MerchantItem merchantItem : itemsForSale) {
        	Log.debug("MERCHANT: adding item: " + merchantItem.itemID);
        	int itemID = merchantItem.itemID;
        	int itemCount = merchantItem.count;
        	Template tmpl = ObjectManagerClient.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
	        int purchaseCurrency = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency");
	        //String currencyType = "" + purchaseCurrency;
	        long cost = (long) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCost");
            if (Log.loggingDebug)
                log.debug("sendMerchantList: adding itemPos=" + numItems + ", itemName=" + itemID);
            props.put("item_" + numItems + "ID", itemID);
            props.put("item_" + numItems + "Count", itemCount);
            props.put("item_" + numItems + "Cost", cost);
            props.put("item_" + numItems + "Currency", purchaseCurrency);
            numItems++;
        }
        Log.debug("MERCHANT: sending merchant table");
        props.put("numItems", numItems);
    	TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(TEmsg);
    }
    
    /**
     * Processes the PurchaseItemFromMerchant Message. Checks that the Merchant has the item
     * available, then sends out the purchaseItem message to attempt to complete the purchase.
     * @param msg
     */
    private void processPurchaseItemFromMerchantMsg(AgisInventoryClient.purchaseItemFromMerchantMessage msg) {
    	OID playerOid = msg.getPlayerOid();
    	// Does the merchant have the item?
    	for(MerchantItem mItem : itemsForSale) {
    		if (mItem.itemID == msg.getItemID()) {
    			if (mItem.count == -1 || mItem.count >= msg.getCount()) {
    				if (AgisInventoryClient.purchaseItem(playerOid, mItem.itemID, msg.getCount())) {
    					Log.debug("MERCHANT: purchase was a success");
    					if (mItem.maxCount != -1) {
    						mItem.count -= msg.getCount();
    						mItem.refresh();
    					}
    					sendMerchantList(playerOid);
    				} else {
    					Log.debug("MERCHANT: purchase failed");
    				}
    			} else {
    				// Send message saying item is no longer available
    				Map<String, Serializable> props = new HashMap<String, Serializable>();
    				props.put("ext_msg_subtype", "item_purchase_result");
    				props.put("result", "no_item");
    				Template itemTemplate = ObjectManagerClient.getTemplate(mItem.itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
    				props.put("itemName", itemTemplate.getName());
    				TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(
    						WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    						playerOid, false, props);
    	        	Engine.getAgent().sendBroadcast(resultMsg);
    			}
    		}
    	}
    }
    
    Long eventSub = null;
    Long eventSub2 = null;
    Long statusSub = null;
    static final Logger log = new Logger("QuestBehavior");
    private static final long serialVersionUID = 1L;
}
