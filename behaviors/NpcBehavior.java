package atavism.agis.behaviors;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.core.Agis;
import atavism.agis.database.AuthDatabase;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisMobClient.DialogueOptionChosenMessage;
import atavism.agis.plugins.AgisMobClient.GetNpcInteractionsMessage;
import atavism.agis.plugins.AgisMobClient.StartNpcInteractionMessage;
import atavism.agis.plugins.QuestClient.*;
import atavism.agis.plugins.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.RequirementCheckResult;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.messages.SubscriptionManager;
import atavism.server.util.*;

/**
 * Controls both Quest and Dialogue interactions for an NPC. This needs to be given to any mob that can start or end
 * Quests and Dialogues. 
 * Should be renamed to InteractionBehavior at some point in the future.
 * @author Andrew Harrison
 *
 */
public class NpcBehavior extends Behavior {

	/**
	 * Sets up the subscription to receive messages the behavior needs to catch.
	 */
    public void initialize() {

    	OID mobOid = this.getObjectStub().getOid();
        if (Log.loggingDebug)
            log.debug("NpcBehavior.initialize: my moboid=" + mobOid);

        SubscriptionManager.get().subscribe(this, mobOid, WorldManagerClient.MSG_TYPE_UPDATE_OBJECT,
                QuestClient.MSG_TYPE_REQ_QUEST_INFO, QuestClient.MSG_TYPE_REQ_CONCLUDE_QUEST,
                ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE, ClassAbilityClient.MSG_TYPE_SKILL_LEVEL_CHANGE,
                QuestClient.MSG_TYPE_REQ_QUEST_PROGRESS, QuestClient.MSG_TYPE_COMPLETE_QUEST,
                QuestClient.MSG_TYPE_QUEST_CONCLUDE_UPDATE, AgisMobClient.MSG_TYPE_GET_INTERACTION_OPTIONS,
                AgisMobClient.MSG_TYPE_START_INTERACTION, AgisMobClient.MSG_TYPE_DIALOGUE_OPTION_CHOSEN,
                AgisInventoryClient.MSG_TYPE_GET_MERCHANT_LIST, AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT);
        Log.debug("NpcBehavior: created subject filter for oid=" + mobOid);
        
        MessageTypeFilter filter2 = new MessageTypeFilter();
        filter2.addType(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE);
        filter2.addType(ClassAbilityClient.MSG_TYPE_SKILL_LEVEL_CHANGE);
        filter2.addType(AgisMobClient.MSG_TYPE_DIALOG_CHECK);
        // Subscribe to all state status change messages. This is inefficient, but it works.
        filter2.addType(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
              //filter2.addType(QuestClient.MSG_TYPE_COMPLETE_QUEST);
        eventSub2 = Engine.getAgent().createSubscription(filter2, this);

    	authDB = new AuthDatabase();
    }

    public void activate() {
    }

    /**
     * Removes the subscriptions this behavior has.
     */
    public void deactivate() {
        lock.lock();
        try {
            SubscriptionManager.get().unsubscribe(this);
            if (eventSub2 != null) {
                Engine.getAgent().removeSubscription(eventSub2);
                eventSub2 = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void handleMessage(Message msg, int flags) {
    	 	log.debug(" NPC Behav handleMessage "+msg+" "+msg.getMsgType());
    	 if (msg instanceof AgisMobClient.DialogCheckMessage) {
    		 AgisMobClient.DialogCheckMessage updateMsg = (AgisMobClient.DialogCheckMessage) msg;
    		 if (Log.loggingTrace)	 log.trace(" NPC Behav handleMessage DialogCheckMessage");
    	    	 
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
        	if (Log.loggingDebug) log.debug("NPC: level change message");
        	ClassAbilityClient.levelChangeMessage nMsg = (ClassAbilityClient.levelChangeMessage) msg;
        	processLevelChangeMsg(nMsg);
        } else if (msg instanceof ClassAbilityClient.skillLevelChangeMessage) {
        	if (Log.loggingDebug) 	log.debug("NPC: skill level change message");
        	ClassAbilityClient.skillLevelChangeMessage nMsg = (ClassAbilityClient.skillLevelChangeMessage) msg;
        	processSkillLevelChangeMsg(nMsg);
        } else if (msg instanceof TargetedExtensionMessage) {
        	TargetedExtensionMessage eMsg = (TargetedExtensionMessage) msg;
        	if (Log.loggingDebug) log.debug("MOB: targetedExtesionType: " + eMsg.getMsgType());
        	if (eMsg.getMsgType().equals(QuestClient.MSG_TYPE_COMPLETE_QUEST)) {
        		if (Log.loggingDebug) 	log.debug("NPC BEHAV: complete quest message caught");
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
        
      //  lock.lock();
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
      //  lock.unlock();
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
       // lock.lock();
        try {
        	quests = offeredQuestMap.remove(playerOid);
        }
        finally {
         //   lock.unlock();
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
      //  lock.lock();
        try {
            offeredQuestMap.put(playerOid, quests);
        }
        finally {
       //     lock.unlock();
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
   	 	if (allDialogues.contains(dialogue.getID()) && !dialogue.getRepeatable()) {
   	 		Log.debug("QUEST: isDialogueAvailable - player already has dialogue");
   	 		return false;
   	 	}
   		 
       	Log.debug("QUEST: isDialogueAvailable - prereq = " + dialogue.getPrereqDialogue());
       	if (!allDialogues.contains(dialogue.getPrereqDialogue()) && dialogue.getPrereqDialogue() > 0) {
       		Log.debug("QUEST: isDialogueAvailable - prereq does not exist");
       		return false;
		}
		if (dialogue.getPrereqFaction() > 0) {
			int rep = FactionClient.getStance(playerOid, dialogue.getPrereqFaction());
			if (rep < dialogue.getPrereqFactionStance() || rep == FactionPlugin.Unknown) {
				Log.debug("QUEST: isDialogueAvailable - Faction stance " + rep + " not match Prereq Faction Stance of dialogue " + dialogue.getPrereqFactionStance());
				return false;
			}
		} else {
			Log.debug("QUEST: isDialogueAvailable PrereqFaction is < 0");
		}
        // Check if the quest has a quest started requirement
		if (dialogue.getPrereqQuest() > 0) {
			boolean found = false;

			HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);

			for (QuestState qs : activeQuests.values()) {
				if (dialogue.getPrereqQuest() == qs.getQuestRef()) {
					found = true;
				}
			}
			if (!found) {
				Log.debug("QUEST: isDialogueAvailable - prereqQuest not found");
				return false;
			}
		}

    	Log.debug("QUEST: isDialogueAvailable success");
        return true;
    }

	protected void offerDialogueToPlayer(OID playerOid, Dialogue dialogue,Dialogue.DialogueOption dOption) {
		if (Log.loggingDebug)
			log.debug("offerDialogueToPlayer: sending dialogue info for dialogue: " + dialogue);
		LinkedList<AgisQuest> offeredQuests = getAvailableQuests(playerOid, playerOid);
		LinkedList<QuestState> progressQuests = getQuestProgress(playerOid, playerOid);
		HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
	      
		ArrayList<Integer> completedQuests =  QuestClient.getCompletedQuests(playerOid);
		LinkedList<Integer> completedDialogues = new LinkedList<Integer>();
		if (Log.loggingDebug)
			Log.debug("DIALOGUE: dialogue " + dialogue.getID() + " offeredQuests " + offeredQuests+" progressQuests="+progressQuests+" activeQuests="+activeQuests+" completedQuests="+completedQuests+" completedDialogues="+completedDialogues);
		
		try {
			completedDialogues = (LinkedList<Integer>) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "completedDialogues");
		} catch (Exception e) {

		}
		if (completedDialogues == null)
			completedDialogues = new LinkedList<Integer>();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "npc_dialogue");
		props.put("npcOid", getObjectStub().getOid());
		props.put("dialogueID", dialogue.getID());
		props.put("title", dialogue.getName());
		props.put("text", dialogue.getText());
		if(dOption != null)
			props.put("audio", dOption.audioClip);
		else
			props.put("audio", dialogue.getAudioClip());
			if (Log.loggingDebug)
			Log.debug("DIALOGUE: dialogue " + dialogue.getID() + " has " + dialogue.getOptions().size() + " options");
		int count = 0;
		for (int i = 0; i < dialogue.getOptions().size(); i++) {
			Dialogue.DialogueOption option = dialogue.getOptions().get(i);
			if (Log.loggingDebug)
				Log.debug("DIALOGUE: dialogue " + dialogue.getID() + " option " + option);
			boolean add = false;
			if (option.action.equals("Quest")) {
				for (AgisQuest aq : offeredQuests) {
					if (aq.getID() == option.actionID)
						add = true;
				}
			} else if (option.action.equals("QuestProgress")) {
				for (QuestState aq : progressQuests) {
					if (aq.getQuestRef() == option.actionID)
						add = true;
				}
			} else if (option.action.equals("Dialogue")) {
				Dialogue dial = AgisMobPlugin.getDialogue(option.actionID);
				if (isDialogueAvailableHelper(dial, completedDialogues, playerOid)) {
					if (dial != null && !dial.getRepeatable()) {
						if (!completedDialogues.contains(option.actionID)) {
							add = true;
						}
					} else {
						add = true;
					}
				}
			} else {
				add = true;
			}
			if (add) {
				if (option.reqOpenedQuest > 0) {
				//	boolean add2 = false;
					if(!activeQuests.containsKey(option.reqOpenedQuest))
						add = false;
					/*for (QuestState aq : progressQuests) {
						if (aq.getQuestRef() == option.reqOpenedQuest)
							add2 = true;
					}
					if (!add2)
						add = false;*/
				}
			}
			if (add) {
				if (option.reqCompletedQuest > 0) {
					if (!completedQuests.contains(option.reqCompletedQuest))
						add = false;
				}
			}
			if(add) {
				if(option.excludingQuest > 0) {
					if(completedQuests.contains(option.excludingQuest))
						add = false;
					if(activeQuests.containsKey(option.excludingQuest))
						add = false;
				}
			}
			if (add) {
				HashMap<Integer, HashMap<String, Integer>> requirements = option.requirements;
				if (RequirementChecker.DoesPlayerMeetRequirements(playerOid, requirements).result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
					add = true;
				} else {
					log.debug("RequirementChecker falied "+option.action);
					add = false;
				}
			}
			// }
			if (add) {
				props.put("option" + count + "action", option.action);
				props.put("option" + count + "actionID", option.actionID);
				props.put("option" + count + "text", option.text);
				props.put("option" + count + "curr", option.currency);
				props.put("option" + count + "currA", option.amount);
				props.put("option" + count + "audio", option.audioClip);
				props.put("option" + count + "item", option.itemReq);
				count++;
			}
		}
		props.put("numOptions", count);

		completedDialogues.add(dialogue.getID());

		if (Log.loggingDebug)
			log.debug("offerDialogueToPlayer: sending dialogue info for dialogue: props=" + props);

		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
		EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "completedDialogues", completedDialogues);
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
    	int rep = FactionClient.getStance(getObjectStub().getOid(),playerOid);
    	if (rep < 0 || rep == FactionPlugin.Unknown) {
    		Log.debug("QUEST: isDialogueAvailable - Faction stance "+rep);
			return offeredDialogues;
		}
        @SuppressWarnings("unchecked")
		LinkedList<Integer> completedDialogues = (LinkedList<Integer>) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "completedDialogues");
        if(completedDialogues==null)
        	completedDialogues = new LinkedList<Integer>();
        for (Dialogue d : getStartDialogues()) {
            if (isDialogueAvailableHelper(d, completedDialogues, playerOid)) {
            	offeredDialogues.add(d);
            }
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
        
        if (Log.loggingDebug)
            log.debug("processInteractionsRequestMsg: mob=" + myOid + ", player=" + playerOid+" dialogues="+dialogues.size()+" offeredQuests="+offeredQuests.size()+" progressQuests="+progressQuests.size()+" otherActions="+otherActions.size()+" merchantTable="+merchantTable+" totalInteractions="+totalInteractions);
     
        // If we have a chat dialogue and another interaction, remove the dialogue from the list
        if (chatDialogue != null && totalInteractions > 0) {
        	dialogues.remove(chatDialogue);
        }
        
        Log.debug("INTERAC: total interactions: " + totalInteractions);
        if (dialogues.size()==0 &&(totalInteractions > 1 || otherActions.size() > 0 || (totalInteractions == 1 && chatDialogue != null))) {
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
    			props.put("interactionValue_" + i, false);
    			props.put("interactionLevel_" + i, q.getQuestLevelReq());
        			i++;
    		}
    		for (QuestState q : progressQuests) {
    			props.put("interactionType_" + i, "progress_quest");
    			props.put("interactionTitle_" + i, q.getName());
    			props.put("interactionID_" + i, q.getQuestRef());
    			props.put("interactionValue_" + i, q.getCompleted());
    			props.put("interactionLevel_" + i, q.getReqLevel());
    	        		i++;
    		}
    		for (Dialogue d : dialogues) {
    			props.put("interactionType_" + i, "dialogue");
    			props.put("interactionTitle_" + i, d.getName());
    			props.put("interactionID_" + i, d.getID());
    			props.put("interactionValue_" + i, false);
    	        		i++;
    		}
    		for (String action : otherActions) {
    			props.put("interactionType_" + i, action);
    			props.put("interactionTitle_" + i, action);
    			props.put("interactionID_" + i, -1);
    			props.put("interactionLevel_" + i, false);
    			props.put("interactionValue_" + i, false);
    	    	    	i++;
    		}
    		if (merchantTable > 0) {
    			props.put("interactionType_" + i, "merchant");
    			props.put("interactionTitle_" + i, "View items for sale");
    			props.put("interactionID_" + i, merchantTable);
    			props.put("interactionLevel_" + i, false);
    			props.put("interactionValue_" + i, false);
    	    	       }
    		
    		props.put("numInteractions", totalInteractions);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
    		Log.debug("INTERAC: sending down interaction options");
        } else {
        	Log.debug("INTERAC: sending single interaction");
        	if (dialogues.size() != 0) {
        		Log.debug("INTERAC: sending dialogue");
        		offerDialogueToPlayer(playerOid, dialogues.get(0), null);
        	} else if (offeredQuests.size() != 0) {
        		Log.debug("INTERAC: sending offered Quests");
        		offerQuestToPlayer(playerOid, offeredQuests);
        	} else if (progressQuests.size() != 0) {
        		Log.debug("INTERAC: sending progress Quest");
        		QuestPlugin.sendQuestProgressInfo(playerOid, myOid, progressQuests);
        	}  else if (merchantTable > 0) {
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
             log.debug("processStartInteractionMsg: mob=" + myOid + ", player=" + playerOid +" "+reqMsg.getInteractionType()+" "+reqMsg.getInteractionID());
        
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
    				offerDialogueToPlayer(playerOid, d, null);
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
   	 if (Log.loggingDebug)
         log.debug("processDialogueOptionChosenMsg: dialogueID ="+ reqMsg.getDialogueID() + ", actionID ="+ reqMsg.getActionID()+" actionType ="+ reqMsg.getInteractionType());
   	LinkedList<Integer> completedDialogues = (LinkedList<Integer>) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "completedDialogues");
    if(completedDialogues==null)
    	completedDialogues = new LinkedList<Integer>();
        // Verify the mob has the dialogue and the dialogue has the action
        for (Dialogue d : getStartDialogues()) {
            if (d.getID() == dialogueID) {
            	dialogue = d;
            	break;
            }
		}
		if (dialogue == null) {
			
			ArrayList<Dialogue> dialogList = new ArrayList<Dialogue>();
			for (Dialogue d : getStartDialogues()) {
				dialogList.add(d);
			}

			boolean added = true;
			while (added) {
				added = false;
				ArrayList<Dialogue> dialogListtemp = new ArrayList<Dialogue>();
				for (Dialogue d : dialogList) {
					for (int i = 0; i < d.getOptions().size(); i++) {
						Dialogue.DialogueOption option = d.getOptions().get(i);
						if (option.action.equals("Dialogue")) {
							Dialogue dd = AgisMobPlugin.getDialogue(option.actionID);
							if (!dialogList.contains(dd) && !dialogListtemp.contains(dd)) {
								if(isDialogueAvailableHelper(dd,completedDialogues,playerOid)) {
								dialogListtemp.add(AgisMobPlugin.getDialogue(option.actionID));
								added = true;
								}
							}
						}
					}
				}
				if (added) {
					dialogList.addAll(dialogListtemp);
				}
			}
			for (Dialogue d : dialogList) {
				if (d.getID() == dialogueID) {
					dialogue = d;
					break;
				}
			}

		}

   	 	if (Log.loggingDebug)
   	 		log.debug("processDialogueOptionChosenMsg: dialogue =" + dialogue);
   	 	
        if (dialogue == null) {
    		log.debug("processDialogueOptionChosenMsg: dialogue =" + dialogue +" is null  ");
        	return;
        }
        Dialogue.DialogueOption dialogOption=null;
        // TODO: action check
        for(Dialogue.DialogueOption option : dialogue.getOptions()) {
        	if (option.action.equals(actionType) && option.actionID == actionID ) {
        		dialogOption = option;
        	}
        	
        	if (option.action.equals(actionType) && option.actionID == actionID && option.itemReq > 0) {
        		Log.debug("DIALOGUE: checking item requirement for dialogue action");
        		OID itemOid = InventoryClient.findItem(playerOid, option.itemReq);
        		if (itemOid == null) {
        			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.MISSING_ITEM, option.itemReq, "");
        			return;
        		}
        		if(option.itemConsume) {
        			HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer,Integer>();
        			itemsToRemove.put(option.itemReq, 1);
        			AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);
        		}
        	}
			if (option.action.equals(actionType) && option.actionID == actionID && option.amount > 0 && option.currency > 0) {

				if (!AgisInventoryClient.checkCurrency(playerOid, option.currency, option.amount)) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
					log.debug("Dialogue Action: playerOid:" + playerOid + " NOT_ENOUGH_CURRENCY");
					return;
				}
				AgisInventoryClient.alterCurrency(playerOid, option.currency, -option.amount);
			}
        }
        
		if (actionType.equals("Dialogue")) {
			Dialogue nextDialogue = AgisMobPlugin.getDialogue(actionID);
			if (isDialogueAvailableHelper(nextDialogue, completedDialogues, playerOid)) {
				offerDialogueToPlayer(playerOid, nextDialogue, dialogOption);
			}
        } else if (actionType.equals("Quest")) {
        	Map<String, Serializable> props = new HashMap<String, Serializable>();
    		props.put("ext_msg_subtype", "close_dialogue");
    		props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
    		
        	LinkedList<Integer> quests = new LinkedList<Integer>();
        	quests.add(actionID);
        	
        	QuestClient.offerQuestToPlayer(playerOid, myOid, quests, false);
		} else if (actionType.equals("Ability")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);

			CombatClient.startAbility(actionID, myOid, playerOid, null);
		} else if (actionType.equals("Repair")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			
			props.clear();
			props.put("ext_msg_subtype", "repair_start");
			TargetedExtensionMessage msg1 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg1);
		} else if (actionType.equals("Merchant")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			MerchantTable mt = Agis.MerchantTableManager.get(actionID);
			sendMerchantList(playerOid,mt);
        
		} else if (actionType.equals("GuildMerchant")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			int mtId = GuildClient.GetGuildMerchant(playerOid);
			MerchantTable mt = Agis.MerchantTableManager.get(mtId);
			if(mt != null)
				sendMerchantList(playerOid, mt);
        
		} else if (actionType.equals("Bank")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			AgisInventoryClient.SendBankInventory(playerOid, actionID);
		} else if (actionType.equals("QuestProgress")){
			LinkedList<QuestState> progressQuests = getQuestProgress(myOid, playerOid);
        	for (QuestState q : progressQuests) {
        		if (q.getQuestRef() == actionID) {
        			LinkedList<QuestState> questInProgress = new LinkedList<QuestState>();
        			questInProgress.add(q);
        			QuestPlugin.sendQuestProgressInfo(playerOid, myOid, questInProgress);
        			break;
        		}
        	}
		} else if(actionType.equals("Auction")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			//props.clear();
			
			
			
			ExtensionMessage auctionMsg = new ExtensionMessage(AuctionClient.MSG_TYPE_AUCTION_LIST, null, playerOid);
			auctionMsg.setProperty("auctioneer", (long)actionID);
			auctionMsg.setProperty("npc", true);
	        Engine.getAgent().sendBroadcast(auctionMsg);
		
		} else if(actionType.equals("Mail")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			props.clear();
			props.put("ext_msg_subtype", "mail_start");
			TargetedExtensionMessage msg1 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg1);
		} else if(actionType.equals("GearModification")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			props.clear();
			props.put("ext_msg_subtype", "gearmod_start");
			TargetedExtensionMessage msg1 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg1);
		} else if(actionType.equals("GuildWarehouse")) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "close_dialogue");
			props.put("audio", dialogOption.audioClip);
			props.put("npc", myOid.toLong());
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			OID wh = GuildClient.GetGuildWarehouse(playerOid);
			if(wh!=null) {
				AgisInventoryClient.openNonPlayerStorage(playerOid, wh);
			}
		}
		 if (Log.loggingDebug)
             log.debug("processDialogueOptionChosenMsg: End");
    }

	private LinkedList<QuestState> getQuestProgress(OID myOid, OID playerOid) {

		if (Log.loggingDebug)
			log.debug("processReqProgressMsg: mob=" + myOid + ", player=" + playerOid);

		// find all the quests the player is on
		AgisQuest completedQuest = null;
		HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
		LinkedList<QuestState> progressQuests = new LinkedList<QuestState>();
		if (activeQuests != null) {
			for (QuestState qs : activeQuests.values()) {
				int questRef = qs.getQuestRef();
				if (Log.loggingDebug)
					log.debug("processReqProgressMsg: checking status for quest " + questRef + ", completed=" + qs.getCompleted()+" concluded="+qs.getConcluded() );
				if (qs.getConcluded() == false) {
					// found a quest
					completedQuest = getEndQuest(questRef);
					if (completedQuest != null) {
						if (Log.loggingDebug)
							log.debug("processReqConcludeMsg: found a completed quest: " + questRef);
						progressQuests.add(qs);
					} else {
						log.warn("processReqConcludeMsg: quest is completed, but not in end quests");
					}
				}
			}
		} else {
			log.warn("processReqConcludeMsg: active quest list is null");
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
		 if (Log.loggingTrace) log.trace("NpcBehavior.handleQuestState oid="+myOid+" plyOid="+playerOid);
		// Check Faction stance player to mob
		   boolean hasAvailableQuest = false;
	        boolean hasInProgressQuest = false;
	        boolean hasConcludableQuest = false;

		int plyRep = FactionClient.getStance(playerOid, myOid);
		log.debug("NpcBehavior.handleQuestState stance="+plyRep);
		if (plyRep < 0 || plyRep == FactionPlugin.Unknown) {
			log.debug("NpcBehavior.handleQuestState state under 0 quests not avalable");
		    TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
	        propMsg.setProperty(AgisStates.QuestAvailable.toString(), hasAvailableQuest);
	        propMsg.setProperty(AgisStates.QuestInProgress.toString(), hasInProgressQuest);
	        propMsg.setProperty(AgisStates.QuestConcludable.toString(), hasConcludableQuest);
	        Engine.getAgent().sendBroadcast(propMsg);
			return;
		}

		HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
        
        // ask the quest plugin if the player has completed the quests we can give out
        Collection<AgisQuest> startQuests = getStartQuests();
        Collection<AgisQuest> endQuests = getEndQuests();
        log.debug("NpcBehavior.handleQuestState startQuest="+startQuests+" endQuests="+endQuests);
        if (startQuests.isEmpty() && endQuests.isEmpty()) {
            // mob has no quests
            if (Log.loggingDebug)
                log.debug("NpcBehavior.handleQuestState: playerOid=" + playerOid + " has no quests, returning");
            return;
        }
        
        //FIXME Add Check Stance Faction 
        if (Log.loggingDebug)
            log.debug("NpcBehavior.handleQuestState: getting quest status for player=" + playerOid + ", starts "
                      + startQuests.size() + " quests, ends " + endQuests.size() + " quests");

     
		for (AgisQuest q : startQuests) {
			if (QuestClient.canPlayerStartQuest(playerOid, q.getID())) {
				if (Log.loggingDebug)
					log.debug("NpcBehavior.handleQuestState: playerOid=" + playerOid + " startQuest=" + q + " quest is available");
				hasAvailableQuest = true;
				String oidQName = playerOid + q.getName();
				if (!questStartAdvertised.contains(oidQName)) {
					// Commented out for Neos
					//WorldManagerClient.sendObjChatMsg(playerOid, 0, WorldManagerClient.getObjectInfo(myOid).name + " starts '" + q.getName() + "'.");
					questStartAdvertised.add(oidQName);
				}
			} else {
				if (Log.loggingDebug)
					log.debug("NpcBehavior.handleQuestState: playerOid=" + playerOid + " startQuest=" + q + " quest is not available");
			}
		}
if(activeQuests!=null)
        for (AgisQuest q : endQuests) {
        	int rep = FactionClient.getStance(playerOid, q.getFaction());
    	
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
        		Log.debug("NpcBehavior.handleQuestState: playerOid = " + playerOid + " no quest state for quest: " 
        				+ q.getName());
        	} else {
        		
        		if (Log.loggingDebug)
                    log.debug("NpcBehavior.handleQuestState: playerOid=" + playerOid + " endQuest=" + q
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
        log.debug("NpcBehavior.handleQuestState: End");
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
    	if (rep < 0  || rep == FactionPlugin.Unknown) {
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
      //  lock.lock();
        try {
            AgisQuest q = startQuestsMap.get(questID);
            if (q != null) {
                return q;
            }
            return endQuestsMap.get(questID);
        }
        finally {
           // lock.unlock();
        }
    }
    
    public AgisQuest getStartQuest(String questName) {
      //  lock.lock();
        try {
            return startQuestsMap.get(questName);
        }
        finally {
           // lock.unlock();
        }
    }
    
    public AgisQuest getEndQuest(int questID) {
      //  lock.lock();
        try {
            return endQuestsMap.get(questID);
        }
        finally {
          //  lock.unlock();
        }
    }
    
    public Collection<AgisQuest> getStartQuests() {
      //  lock.lock();
        try {
            return new LinkedList<AgisQuest>(startQuestsMap.values());
        }
        finally {
          //  lock.unlock();
        }
    }
    public Collection<AgisQuest> getEndQuests() {
      //  lock.lock();
        try {
            return new LinkedList<AgisQuest>(endQuestsMap.values());
        }
        finally {
         //   lock.unlock();
        }
    }
    public Collection<AgisQuest> getAllQuests() {
       // lock.lock();
        try {
            Set<AgisQuest> l = new HashSet<AgisQuest>();
            l.addAll(getStartQuests());
            l.addAll(getEndQuests());
            return l;
        }
        finally {
          //  lock.unlock();
        }
    }
    public Collection<Integer> getAllQuestRefs() {
      //  lock.lock();
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
          //  lock.unlock();
        }
    }
    public Collection<Integer> getStartQuestRefs() {
        //lock.lock();
        try {
            Collection<Integer> set = new HashSet<Integer>();
            for (AgisQuest q : getStartQuests()) {
                set.add(q.getID());
                set.addAll(q.getQuestPrereqs());
            }
            return set;
        }
        finally {
          //  lock.unlock();
        }
    }
    
    private Map<Integer, AgisQuest> startQuestsMap = new HashMap<Integer, AgisQuest>();
    private Map<Integer, AgisQuest> endQuestsMap = new HashMap<Integer, AgisQuest>();
    
    // players who have asked for quest info, we keep track of what quest we gave them
    private Map<OID, LinkedList<AgisQuest>> offeredQuestMap = new ConcurrentHashMap<OID, LinkedList<AgisQuest>>();
    
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
       // lock.lock();
        try {
            return new LinkedList<Dialogue>(startDialoguesMap.values());
        }
        finally {
         //   lock.unlock();
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
    
    HashMap<Integer, ArrayList<MerchantItem>> merchatItemList = new HashMap<Integer, ArrayList<MerchantItem>>();
    HashMap<OID, Integer> playerMerchatItemList = new HashMap<OID,Integer>();
    
    
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
	void sendMerchantList(OID oid, MerchantTable mt) {
		ArrayList<MerchantItem> mi = merchatItemList.get(mt.getID());
		if (AgisMobPlugin.DevMode) {
			if (mi != null) {
				ArrayList<MerchantItem> todelete = new ArrayList<MerchantItem>();
				todelete.addAll(mi);
				for (int i = 0; i < mt.getItems().size(); i++) {
					boolean found = false;
					for (MerchantItem _mi : mi) {
						if (_mi.itemID == mt.getItems().get(i)) {
							todelete.remove(_mi);
							if (_mi.maxCount != mt.getItemCounts().get(i)) {
								_mi.count = mt.getItemCounts().get(i);
							}
							_mi.maxCount = mt.getItemCounts().get(i);
							_mi.refreshTime = mt.getItemRespawns().get(i);
							found = true;
						}
					}
					if (!found) {
						MerchantItem merchantItem = new MerchantItem();
						merchantItem.itemID = mt.getItems().get(i);
						merchantItem.maxCount = mt.getItemCounts().get(i);
						merchantItem.count = mt.getItemCounts().get(i);
						merchantItem.refreshTime = mt.getItemRespawns().get(i);
						mi.add(merchantItem);
					}
				}
				if (todelete.size() > 0) {
					for (MerchantItem _mi : todelete) {
						mi.remove(_mi);
					}
				}
			}
		}
		playerMerchatItemList.put(oid, mt.getID());
		if (mi == null) {
			mi = new ArrayList<MerchantItem>();
			for (int i = 0; i < mt.getItems().size(); i++) {
				MerchantItem merchantItem = new MerchantItem();
				merchantItem.itemID = mt.getItems().get(i);
				merchantItem.maxCount = mt.getItemCounts().get(i);
				merchantItem.count = mt.getItemCounts().get(i);
				merchantItem.refreshTime = mt.getItemRespawns().get(i);
				mi.add(merchantItem);
			}
			merchatItemList.put(mt.getID(), mi);
		}
		
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "MerchantList");
		props.put("npcOid", getObjectStub().getOid());
		props.put("mtid", mt.getID());
		int numItems = 0;
		LinkedList<String> param = new LinkedList<String>();
    	param.add("bonuses");
    	HashMap<String,Serializable> objecParams = CombatClient.getCombatInfoParams(oid, param );
		HashMap<String, BonusSettings> bonuses = (HashMap<String, BonusSettings>)objecParams.get("bonuses");
		float vipModp = 0;
		long vipMod = 0;
		if(bonuses.containsKey("PriceMerchant")) {
			vipMod =bonuses.get("PriceMerchant").GetValue();
			vipModp =bonuses.get("PriceMerchant").GetValuePercentage();
  		}
		if(AgisMobPlugin.globalEventBonusesArray.containsKey("PriceMerchant")) {
			vipMod += AgisMobPlugin.globalEventBonusesArray.get("PriceMerchant").GetValue();
  			vipModp += AgisMobPlugin.globalEventBonusesArray.get("PriceMerchant").GetValuePercentage();
  		}
		for (MerchantItem merchantItem : mi) {
			Log.debug("MERCHANT: adding item: " + merchantItem.itemID);
			int itemID = merchantItem.itemID;
			int itemCount = merchantItem.count;
			Template tmpl = ObjectManagerClient.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
			int purchaseCurrency = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency");
			long cost = (Long) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCost");
			if (Log.loggingDebug)
				log.debug("sendMerchantList: adding itemPos=" + numItems + ", itemName=" + itemID);
			long PriceValue = (long) Math.round(Math.ceil(vipMod + (cost) + (cost * vipModp) / 100f));
			if(PriceValue < 0)
				PriceValue = 0;
			props.put("item_" + numItems + "ID", itemID);
			props.put("item_" + numItems + "Count", itemCount);
			props.put("item_" + numItems + "Cost", PriceValue);
			props.put("item_" + numItems + "Currency", purchaseCurrency);
			numItems++;
		}
		Log.debug("MERCHANT: sending merchant table");
		props.put("numItems", numItems);
		TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(TEmsg);
	}
    
    
    /**
     * Sends down the list of item in the specified merchant table to the player.
     * @param oid
     * @param merchantTableNum
     */
	void sendMerchantList(OID oid) {
		if (AgisMobPlugin.DevMode) {
			MerchantTable mt = Agis.MerchantTableManager.get(merchantTable);
			ArrayList<MerchantItem> todelete = new ArrayList<MerchantItem>();
			todelete.addAll(itemsForSale);
			for (int i = 0; i < mt.getItems().size(); i++) {
				boolean found = false;
				for (MerchantItem _mi : itemsForSale) {
					if (_mi.itemID == mt.getItems().get(i)) {
						todelete.remove(_mi);
						if (_mi.maxCount != mt.getItemCounts().get(i)) {
							_mi.count = mt.getItemCounts().get(i);
						}
						_mi.maxCount = mt.getItemCounts().get(i);
						_mi.refreshTime = mt.getItemRespawns().get(i);
						found = true;
					}
				}
				if (!found) {
					MerchantItem merchantItem = new MerchantItem();
					merchantItem.itemID = mt.getItems().get(i);
					merchantItem.maxCount = mt.getItemCounts().get(i);
					merchantItem.count = mt.getItemCounts().get(i);
					merchantItem.refreshTime = mt.getItemRespawns().get(i);
					itemsForSale.add(merchantItem);
				}
			}
			if (todelete.size() > 0) {
				for (MerchantItem _mi : todelete) {
					itemsForSale.remove(_mi);
				}
			}

		}
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "MerchantList");
		props.put("npcOid", getObjectStub().getOid());
		props.put("mtid", 0);
		
		int numItems = 0;
		LinkedList<String> param = new LinkedList<String>();
    	param.add("bonuses");
    	HashMap<String,Serializable> objecParams = CombatClient.getCombatInfoParams(oid, param );
		HashMap<String, BonusSettings> bonuses = (HashMap<String, BonusSettings>)objecParams.get("bonuses");
		float vipModp = 0;
		long vipMod = 0;
		if(bonuses.containsKey("PriceMerchant")) {
			vipMod =bonuses.get("PriceMerchant").GetValue();
			vipModp =bonuses.get("PriceMerchant").GetValuePercentage();
  		}
		if(AgisMobPlugin.globalEventBonusesArray.containsKey("PriceMerchant")) {
			vipMod += AgisMobPlugin.globalEventBonusesArray.get("PriceMerchant").GetValue();
  			vipModp += AgisMobPlugin.globalEventBonusesArray.get("PriceMerchant").GetValuePercentage();
  		}
		for (MerchantItem merchantItem : itemsForSale) {
			Log.debug("MERCHANT: adding item: " + merchantItem.itemID);
			int itemID = merchantItem.itemID;
			int itemCount = merchantItem.count;
			Template tmpl = ObjectManagerClient.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
			int purchaseCurrency = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency");
			// String currencyType = "" + purchaseCurrency;
			long cost = (Long) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCost");
			if (Log.loggingDebug)
				log.debug("sendMerchantList: adding itemPos=" + numItems + ", itemName=" + itemID);
			
			long PriceValue = (long) Math.round(Math.ceil(vipMod + (cost) + (cost * vipModp) / 100f));
			if(PriceValue < 0)
				PriceValue = 0;
			props.put("item_" + numItems + "ID", itemID);
			props.put("item_" + numItems + "Count", itemCount);
			props.put("item_" + numItems + "Cost", PriceValue);
			props.put("item_" + numItems + "Currency", purchaseCurrency);
			numItems++;
		}
		Log.debug("MERCHANT: sending merchant table");
		props.put("numItems", numItems);
		TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(TEmsg);
	}
    
    /**
     * Processes the PurchaseItemFromMerchant Message. Checks that the Merchant has the item
     * available, then sends out the purchaseItem message to attempt to complete the purchase.
     * @param msg
     */
	private void processPurchaseItemFromMerchantMsg(AgisInventoryClient.purchaseItemFromMerchantMessage msg) {
		OID playerOid = msg.getPlayerOid();
		log.debug("processPurchaseItemFromMerchantMsg: plyOid="+playerOid+" "+itemsForSale.size());
		// Does the merchant have the item?
		if(playerMerchatItemList.containsKey(playerOid)) {
			MerchantTable mt = Agis.MerchantTableManager.get(playerMerchatItemList.get(playerOid));
			ArrayList<MerchantItem> mi = merchatItemList.get(mt.getID());
			for (MerchantItem mItem : mi) {
				if (mItem.itemID == msg.getItemID()) {
					if (mItem.count == -1 || mItem.count >= msg.getCount()) {
						if (AgisInventoryClient.purchaseItem(playerOid, mItem.itemID, msg.getCount())) {
							Log.debug("MERCHANT: purchase was a success");
							if (mItem.maxCount != -1) {
								mItem.count -= msg.getCount();
								mItem.refresh();
							}
							sendMerchantList(playerOid,mt);
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
						TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
						Engine.getAgent().sendBroadcast(resultMsg);
					}
				}
			}
			
			
		}else {
		for (MerchantItem mItem : itemsForSale) {
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
					TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
					Engine.getAgent().sendBroadcast(resultMsg);
				}
			}
		}
		}
		log.debug("processPurchaseItemFromMerchantMsg: END");
	}
	protected AuthDatabase authDB;
    Long eventSub2 = null;
    static final Logger log = new Logger("NpcBehavior");
    private static final long serialVersionUID = 1L;
}
