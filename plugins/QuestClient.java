package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import atavism.agis.objects.QuestState;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;
import atavism.server.messages.*;

public class QuestClient {
 
	/**
     * makes a request to the questplugin to get the players active quests
     * @param playerOid player
     */
    public static HashMap<Integer,QuestState> getActiveQuests(OID playerOid) {
        GetQuestStatusMessage msg = new GetQuestStatusMessage(playerOid, QuestTypeActive);
        HashMap<Integer, QuestState> questStatusMap = (HashMap<Integer,QuestState>) Engine.getAgent().sendRPCReturnObject(msg);
        return questStatusMap;
    }
    
    public static ArrayList<Integer> getCompletedQuests(OID playerOid) {
        GetQuestStatusMessage msg = new GetQuestStatusMessage(playerOid, QuestTypeCompleted);
        ArrayList<Integer> questStatusMap = (ArrayList<Integer>) Engine.getAgent().sendRPCReturnObject(msg);
        return questStatusMap;
    }
    
    public static HashMap<Integer, Boolean> getAllQuests(OID playerOid) {
        GetQuestStatusMessage msg = new GetQuestStatusMessage(playerOid, QuestTypeAll);
        HashMap<Integer, Boolean> questStatusMap = (HashMap<Integer, Boolean>) Engine.getAgent().sendRPCReturnObject(msg);
        return questStatusMap;
    }
    
    public static void requestQuestInfo(OID mobOid, OID playerOid) {
        RequestQuestInfoMessage msg = new RequestQuestInfoMessage(mobOid, playerOid);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    public static void requestConclude(OID mobOid, OID playerOid) {
        RequestConcludeMessage msg = new RequestConcludeMessage(mobOid, playerOid);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void resetQuests(OID playerOid) {
	SubjectMessage msg = new SubjectMessage(MSG_TYPE_REQ_RESET_QUESTS, playerOid);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    public static List<Integer> getQuestItemReqs(OID playerOid) {
    	Log.debug("QUEST: client hit GetQuestItemsReq 1");
    	GetQuestItemReqsMessage msg = new GetQuestItemReqsMessage(playerOid);
        List<Integer> questItemReqs = (List<Integer>) Engine.getAgent().sendRPCReturnObject(msg);
        return questItemReqs;
    }
    
    public static void completeQuest(OID npcOid, OID playerOid, OID questID, int chosenItem) {
    	Log.debug("QUEST: hit completeQuest");
    	CompleteQuestMessage msg = new CompleteQuestMessage(npcOid, playerOid, questID, chosenItem);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    public static boolean canPlayerStartQuest(OID playerOid, int questID) {
    	Log.debug("QUEST: client hit canPlayerStartQuest 1");
    	CanPlayerStartQuestMessage msg = new CanPlayerStartQuestMessage(playerOid, questID);
    	Boolean canStartQuest = (Boolean) Engine.getAgent().sendRPCReturnBoolean(msg);
    	Log.debug("QUEST: client hit canPlayerStartQuest canStartQuest="+canStartQuest);
            return canStartQuest;
    }
    
    public static ArrayList<Integer> canPlayerStartQuestList(OID playerOid, ArrayList<Integer> list) {
    	Log.debug("QUEST: client hit canPlayerStartQuestList 1");
    	CanPlayerStartQuestMessage msg = new CanPlayerStartQuestMessage(playerOid, -1);
    	msg.setQuestIDList(list);
    	ArrayList<Integer> canStartQuest = (ArrayList<Integer>) Engine.getAgent().sendRPCReturnObject(msg);
    	Log.debug("QUEST: client hit canPlayerStartQuestList canStartQuest="+canStartQuest);
            return canStartQuest;
    }
    
    /**
     * Message used to verify if the player meets the requirements to start the specified quest.
     * @author Andrew Harrison
     *
     */
    public static class CanPlayerStartQuestMessage extends SubjectMessage {

        public CanPlayerStartQuestMessage() {
            super(MSG_TYPE_CAN_PLAYER_START_QUEST);
        }

        public CanPlayerStartQuestMessage(OID playerOid, int questID) {
            super(MSG_TYPE_CAN_PLAYER_START_QUEST, playerOid);
            setQuestID(questID);
        }
        
        public int getQuestID() {
            return questID;
        }

        public void setQuestID(int questID) {
            this.questID = questID;
        }
        int questID;
        
        public ArrayList<Integer> getQuestIDList() {
            return questList;
        }

        public void setQuestIDList(ArrayList<Integer> list) {
            this.questList = list;
        }
        ArrayList<Integer> questList;
        private static final long serialVersionUID = 1L;
    }
    
    /**
     * Sends the StartQuestMessage telling the system that the player should
     * be trying to start the given quest.
     * @param playerOid
     * @param questID
     */
    public static void offerQuestToPlayer(OID playerOid, OID offererOid, LinkedList<Integer> quests, boolean deleteItem){
    	OfferQuestMessage msg = new OfferQuestMessage(playerOid, offererOid, quests, deleteItem);
    	Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Message used to try start the specified quest.
     * @author Andrew Harrison
     *
     */
    public static class OfferQuestMessage extends SubjectMessage {

        public OfferQuestMessage() {
            super(MSG_TYPE_OFFER_QUEST);
        }

        public OfferQuestMessage(OID playerOid, OID offererOid, LinkedList<Integer> quests, boolean deleteItem) {
            super(MSG_TYPE_OFFER_QUEST, playerOid);
            setQuests(quests);
            setOfferer(offererOid);
            deleteItem(deleteItem);
        }
        
        public LinkedList<Integer> getQuests() {
            return quests;
        }
        public void setQuests(LinkedList<Integer> quests) {
            this.quests = quests;
        }
        LinkedList<Integer> quests;
        
        public OID getOfferer() {
            return offererOid;
        }
        public void setOfferer(OID offererOid) {
            this.offererOid = offererOid;
        }
        OID offererOid;
        
        public boolean deleteItem() {
            return deleteItem;
        }
        public void deleteItem(boolean deleteItem) {
            this.deleteItem = deleteItem;
        }
        boolean deleteItem = false;
        
        private static final long serialVersionUID = 1L;
    }
    
    /**
     * Sends the StartQuestMessage telling the system that the player should
     * be trying to start the given quest.
     * @param playerOid
     * @param questID
     */
    public static boolean startQuestForPlayer(OID playerOid, int questID){
    	StartQuestMessage msg = new StartQuestMessage(playerOid, questID);
    	Boolean questStarted = (Boolean) Engine.getAgent().sendRPCReturnBoolean(msg);
        return questStarted;
    }
    
    /**
     * Message used to try start the specified quest.
     * @author Andrew Harrison
     *
     */
    public static class StartQuestMessage extends SubjectMessage {

        public StartQuestMessage() {
            super(MSG_TYPE_START_QUEST);
        }

        public StartQuestMessage(OID playerOid, int questID) {
            super(MSG_TYPE_START_QUEST, playerOid);
            setQuestID(questID);
        }
        
        public int getQuestID() {
            return questID;
        }

        public void setQuestID(int questID) {
            this.questID = questID;
        }
        int questID;
        
        private static final long serialVersionUID = 1L;
    }

    /**
     * the quest plugin (usually via quest state object) has updated its state,
     * and is alerting others (usually quest behavior) so that they can let the player know
     * if their available actions have changed (such as ability to turn in a quest)
     * @author cedeno
     */
    public static class StateStatusChangeMessage extends SubjectMessage {

        public StateStatusChangeMessage() {
            super(MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
        }

        public StateStatusChangeMessage(OID playerOid, int questRef) {
            super(MSG_TYPE_QUEST_STATE_STATUS_CHANGE, playerOid);
            setQuestRef(questRef);
        }
        
        public int getQuestRef() {
            return questRef;
        }

        public void setQuestRef(int questRef) {
            this.questRef = questRef;
        }
        int questRef;
        
        private static final long serialVersionUID = 1L;
    }


    public static class RequestConcludeMessage extends SubjectMessage {

        public RequestConcludeMessage() {
            super(MSG_TYPE_REQ_CONCLUDE_QUEST);
        }

        public RequestConcludeMessage(OID mobOid, OID playerOid) {
            super(MSG_TYPE_REQ_CONCLUDE_QUEST, mobOid);
            setPlayerOid(playerOid);
        }

        public OID getPlayerOid() {
            return playerOid;
        }

        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        OID playerOid;

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * Message from a mob (quest behavior) is telling us (usually quest state obj) that the quest has been concluded
     * player is subject because it is going to the player's quest state
     * @author AJ
     *
     */
    public static class ConcludeMessage extends SubjectMessage {

        public ConcludeMessage() {
            super(MSG_TYPE_CONCLUDE_QUEST);
        }

        public ConcludeMessage(OID playerOid, OID mobOid, OID questOid, int chosenReward) {
            super(MSG_TYPE_CONCLUDE_QUEST, playerOid);
            setMobOid(mobOid);
            setQuestOid(questOid);
            setChosenReward(chosenReward);
        }

        public OID getMobOid() {
            return mobOid;
        }

        public void setMobOid(OID mobOid) {
            this.mobOid = mobOid;
        }
        OID mobOid;

        public OID getQuestOid() {
            return questOid;
        }

        public void setQuestOid(OID questOid) {
            this.questOid = questOid;
        }
        OID questOid;
        
        public int getChosenReward() {
            return chosenReward;
        }

        public void setChosenReward(int chosenReward) {
            this.chosenReward = chosenReward;
        }
        int chosenReward;

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * (usually quest behavior) asking quest plugin for the status of various quest states
     * @author cedeno
     *
     */
    public static class GetQuestStatusMessage extends SubjectMessage {

        public GetQuestStatusMessage() {
            super(MSG_TYPE_GET_QUEST_STATUS);
        }
        
        public GetQuestStatusMessage(OID playerOid, int questType) {
            super(MSG_TYPE_GET_QUEST_STATUS, playerOid);
            setQuestType(questType);
        }
        
        public void setQuestType(int questType) {
        	this.questType = questType;
        }
        public int getQuestType() {
        	return questType;
        }
        private int questType = QuestTypeActive;

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * This is used when looting an item and we need to know what quest items a player requires
     * @author Andrew
     *
     */
    public static class GetQuestItemReqsMessage extends SubjectMessage {

        public GetQuestItemReqsMessage() {
            super(MSG_TYPE_QUEST_ITEM_REQS);
        }
        
        public GetQuestItemReqsMessage(OID playerOid) {
            super(MSG_TYPE_QUEST_ITEM_REQS, playerOid);
            Log.debug("QUEST: client hit GetQuestItemsReq 2");
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * (usually proxy plugin) asking for quest info
     * @author cedeno
     *
     */
    public static class RequestQuestInfoMessage extends SubjectMessage {

        public RequestQuestInfoMessage() {
            super(MSG_TYPE_REQ_QUEST_INFO);
        }

        RequestQuestInfoMessage(OID npcOid, OID playerOid) {
            super(MSG_TYPE_REQ_QUEST_INFO, npcOid);
            setPlayerOid(playerOid);
        }
        
        OID playerOid = null;

        public OID getPlayerOid() {
            return playerOid;
        }

        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * client is responding to server, accepting or declining quest
     *
     */
    public static class QuestResponseMessage extends SubjectMessage {

        public QuestResponseMessage() {
            super(MSG_TYPE_QUEST_RESP);
        }

        public QuestResponseMessage(OID npcOid, OID playerOid, OID questID, boolean acceptStatus) {
            super(MSG_TYPE_QUEST_RESP, npcOid);
            setPlayerOid(playerOid);
            setAcceptStatus(acceptStatus);
            setQuestID(questID);
        }
        
        public Boolean getAcceptStatus() {
            return acceptStatus;
        }
        public void setAcceptStatus(Boolean acceptStatus) {
            this.acceptStatus = acceptStatus;
        }
        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        public OID getQuestID() {
            return questID;
        }
        public void setQuestID(OID questID) {
            this.questID = questID;
        }
        
        private Boolean acceptStatus;
        private OID playerOid;
        private OID questID;

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * client accepted a quest, so the quest behavior has created a quest state object
     * and is now alerting the quest plugin about it so it can keep track of it
     * @author cedeno
     *
     */
    public static class NewQuestStateMessage extends SubjectMessage {

        public NewQuestStateMessage() {
            super(MSG_TYPE_NEW_QUESTSTATE);
        }
        
        public NewQuestStateMessage(OID playerOid, QuestState questState) {
            super(MSG_TYPE_NEW_QUESTSTATE, playerOid);
            setQuestState(questState);
        }

        public QuestState getQuestState() {
            return questState;
        }

        public void setQuestState(QuestState questState) {
            this.questState = questState;
        }
        
        private QuestState questState;

        private static final long serialVersionUID = 1L;
    }
    
    public static class QuestItemUpdateMessage extends SubjectMessage {
        public QuestItemUpdateMessage() {
            super(MSG_TYPE_QUEST_ITEM_UPDATE);
        }
        public QuestItemUpdateMessage(OID playerOid, List<Integer> items) {
            super(MSG_TYPE_QUEST_ITEM_UPDATE, playerOid);
            setItemsRequired(items);
            Log.debug("QuestItemUpdateMessage: items required: " + items);
        }

        public List<Integer> getItemsRequired() {
            return itemsRequired;
        }
        public void setItemsRequired(List<Integer> itemsRequired) {
            this.itemsRequired = itemsRequired;
        }
        List<Integer> itemsRequired;

        private static final long serialVersionUID = 1L;
    }
    
    public static class TaskUpdateMessage extends SubjectMessage {
        public TaskUpdateMessage() {
            super(MSG_TYPE_QUEST_TASK_UPDATE);
        }

        public TaskUpdateMessage(OID playerOid, int task, int status) {
            super(MSG_TYPE_QUEST_TASK_UPDATE, playerOid);
            setStatus(status);
            setTaskID(task);
        }

        public int getStatus() {
            return status;
        }
        public void setStatus(int status) {
            this.status = status;
        }
        int status;

        public int getTaskID() {
            return taskID;
        }
        public void setTaskID(int taskID) {
            this.taskID = taskID;
        }
        int taskID;

        private static final long serialVersionUID = 1L;
    }
    
    public static class RequestQuestProgressMessage extends SubjectMessage {
        public RequestQuestProgressMessage() {
            super(MSG_TYPE_REQ_QUEST_PROGRESS);
        }

        public RequestQuestProgressMessage(OID npcOid, OID playerOid) {
            super(MSG_TYPE_REQ_QUEST_PROGRESS, npcOid);
            setPlayerOid(playerOid);
        }
        
        OID playerOid = null;
        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }

        private static final long serialVersionUID = 1L;
    }
    
    public static class CompleteQuestMessage extends SubjectMessage {
        public CompleteQuestMessage() {
            super(MSG_TYPE_COMPLETE_QUEST);
        }

        public CompleteQuestMessage(OID npcOid, OID playerOid) {
            super(MSG_TYPE_COMPLETE_QUEST, npcOid);
            setPlayerOid(playerOid);
        }
        
        public CompleteQuestMessage(OID npcOid, OID playerOid, OID questID, int chosenItem) {
            super(MSG_TYPE_COMPLETE_QUEST, npcOid);
            setPlayerOid(playerOid);
            setQuestID(questID);
            setItemChosen(chosenItem);
        }
        
        OID playerOid = null;
        public OID getPlayerOid() {return playerOid;}
        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}
        
        OID questID = null;
        public OID getQuestID() {return questID;}
        public void setQuestID(OID questID) {this.questID = questID;}
        
        int itemChosen = -1;
        public int getItemChosen() {return itemChosen;}
        public void setItemChosen(int itemChosen) {this.itemChosen = itemChosen;}

        private static final long serialVersionUID = 1L;
    }
    
    public static class ConcludeUpdateMessage extends SubjectMessage {

        public ConcludeUpdateMessage() {
            super(MSG_TYPE_QUEST_CONCLUDE_UPDATE);
        }

        public ConcludeUpdateMessage(OID mobOid, OID playerOid) {
            super(MSG_TYPE_QUEST_CONCLUDE_UPDATE, mobOid);
            setPlayerOid(playerOid);
        }

        public OID getPlayerOid() {
            return playerOid;
        }

        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        OID playerOid;

        private static final long serialVersionUID = 1L;
    }
    
    public static class AbandonQuestMessage extends PropertyMessage {
        public AbandonQuestMessage() {
            super(MSG_TYPE_ABANDON_QUEST);
        }

        public AbandonQuestMessage(OID playerOid, OID questID) {
            super(MSG_TYPE_ABANDON_QUEST);
            setPlayerOid(playerOid);
            setQuestID(questID);
        }
        
        OID playerOid = null;
        public OID getPlayerOid() {return playerOid;}
        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}
        
        OID questID = null;
        public OID getQuestID() {return questID;}
        public void setQuestID(OID questID) {this.questID = questID;}

        private static final long serialVersionUID = 1L;
    }

    // Enumerated values of QuestStatus
    public static final byte QuestStatusDNE = 1;
    public static final byte QuestStatusInProgress = 2;
    public static final byte QuestStatusCompleted = 3;
    public static final byte QuestStatusConcluded = 4;
    
    public static final byte QuestTypeActive = 1;
    public static final byte QuestTypeCompleted = 2;
    public static final byte QuestTypeAll = 3;
    
    public static final MessageType MSG_TYPE_REQ_QUEST_INFO = MessageType.intern("ao.REQ_QUEST_INFO");
    public static final MessageType MSG_TYPE_REQ_CONCLUDE_QUEST = MessageType.intern("ao.REQ_CONCLUDE_QUEST");
    public static final MessageType MSG_TYPE_QUEST_INFO = MessageType.intern("ao.QUEST_INFO");
    public static final MessageType MSG_TYPE_GET_QUEST_STATUS = MessageType.intern("ao.GET_QUEST_STATUS");
    public static final MessageType MSG_TYPE_QUEST_RESP = MessageType.intern("ao.QUEST_RESP");
    public static final MessageType MSG_TYPE_NEW_QUESTSTATE = MessageType.intern("ao.NEW_QUESTSTATE");
    public static final MessageType MSG_TYPE_CONCLUDE_QUEST = MessageType.intern("ao.CONCLUDE_QUEST");
    public static final MessageType MSG_TYPE_OFFER_QUEST = MessageType.intern("ao.OFFER_QUEST");
    public static final MessageType MSG_TYPE_START_QUEST = MessageType.intern("ao.START_QUEST");
    public static final MessageType MSG_TYPE_CAN_PLAYER_START_QUEST = MessageType.intern("ao.CAN_PLAYER_START_QUEST");
    public static final MessageType MSG_TYPE_QUEST_STATE_STATUS_CHANGE = MessageType.intern("ao.QUEST_STATE_STATUS_CHANGE");
    public static final MessageType MSG_TYPE_QUEST_LOG_INFO = MessageType.intern("ao.QUEST_LOG_INFO");
    public static final MessageType MSG_TYPE_QUEST_HISTORY_LOG_INFO = MessageType.intern("ao.QUEST_HISTORY_LOG_INFO");
    public static final MessageType MSG_TYPE_QUEST_STATE_INFO = MessageType.intern("ao.QUEST_STATE_INFO");
    public static final MessageType MSG_TYPE_REMOVE_QUEST_RESP = MessageType.intern("ao.REMOVE_QUEST_RESP");
    public static final MessageType MSG_TYPE_REQ_RESET_QUESTS = MessageType.intern("ao.REQ_RESET_QUESTS");
    public static final MessageType MSG_TYPE_QUEST_ITEM_REQS = MessageType.intern("ao.QUEST_ITEM_REQ");
    public static final MessageType MSG_TYPE_QUEST_ITEM_UPDATE = MessageType.intern("ao.QUEST_ITEM_UPDATE");
    public static final MessageType MSG_TYPE_QUEST_TASK_UPDATE = MessageType.intern("ao.QUEST_TASK_UPDATE");
    public static final MessageType MSG_TYPE_REQ_QUEST_PROGRESS = MessageType.intern("ao.REQ_QUEST_PROGRESS");
    public static final MessageType MSG_TYPE_COMPLETE_QUEST = MessageType.intern("ao.COMPLETE_QUEST");
    public static final MessageType MSG_TYPE_QUEST_CONCLUDE_UPDATE = MessageType.intern("ao.QUEST_CONCLUDE_UPDATE");
    public static final MessageType MSG_TYPE_ABANDON_QUEST = MessageType.intern("ao.ABANDON_QUEST");
}
