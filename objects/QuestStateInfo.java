package atavism.agis.objects;

import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.agis.core.Agis;
import atavism.agis.plugins.*;

import java.beans.*;
import java.util.*;


/**
 * Information related to the combat system. Any object that wants to be involved
 * in combat needs one of these.
 */
public class QuestStateInfo extends Entity {
	public QuestStateInfo() {
		super();
		setNamespace(Namespace.QUEST);
	}

	public QuestStateInfo(OID objOid) {
		super(objOid);
		setNamespace(Namespace.QUEST);
	}

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() + "]";
    }

    public ObjectType getType() {
        return ObjectTypes.questStateInfo;
    }
	
	public int getID() {
		return id;
	}
	public void setID(int id) {
		this.id = id;
	}
	protected int id;
	
	/**
	 * Category control
	 * Each category has its own collection of quests.
	 */
	public int getCurrentCategory() {
		return currentCategory;
	}
	public void setCurrentCategory(int category) {
		Log.debug("QSI: setting current category to: " + category + " from: " + currentCategory);
		this.currentCategory = category;
	}
	public void categoryUpdated(int category) {
		// Deactivate quests belonging to the old category
		for (QuestState qs : activeQuests.get(currentCategory).values()) {
			qs.deactivate();
			// Send message to remove from quest log
			TargetedExtensionMessage rMsg = new TargetedExtensionMessage(QuestClient.MSG_TYPE_REMOVE_QUEST_RESP,
                    "ao.REMOVE_QUEST_RESP",
                    qs.getPlayerOid(), qs.getQuestOid());
			Engine.getAgent().sendBroadcast(rMsg);
		}
		this.currentCategory = category;
		if (!activeQuests.containsKey(category))
			activeQuests.put(category, new HashMap<Integer, QuestState>());
		// Activate quests belonging to the new category
		for (QuestState qs : activeQuests.get(currentCategory).values())
			qs.activate();
		if (!completedQuests.containsKey(category))
			completedQuests.put(category, new ArrayList<Integer>());
		Engine.getPersistenceManager().setDirty(this);
	}
	protected int currentCategory;
	
	public HashMap<Integer, Boolean> getAllQuests() {
		HashMap<Integer, Boolean> allQuests = new HashMap<Integer, Boolean>();
		for (int key : getActiveQuests(currentCategory).keySet())
			allQuests.put(key, false);
		for (int key : getCompletedQuests(currentCategory))
			allQuests.put(key, true);
		Log.debug("QSI: all quests: " + allQuests);
		return allQuests;
	}

	public void addActiveQuest(int questID, QuestState qs) {
		if (Log.loggingDebug) {
			Log.debug("QuestStateInfo.addActiveQuest: adding quest=" + questID + " to obj=" + this
					+ " in category=" + currentCategory);
        }
		if (Log.loggingDebug) 
			log.debug("StartStateInfo  addActiveQuest before lock ");
        lock.lock();
        try {
        	if (Log.loggingDebug)	 log.debug("StartStateInfo  addActiveQuest try");
            if (getActiveQuests(currentCategory).containsKey(questID)) {
                return;
            }
            if (Log.loggingDebug)	 log.debug("StartStateInfo  addActiveQuest after 1if");
         
            getActiveQuests(currentCategory).put(questID, qs);
            if (Log.loggingDebug) log.debug("StartStateInfo  addActiveQuest after put ");
         
            qs.activate();
            if (Log.loggingDebug) log.debug("StartStateInfo  addActiveQuest after activate ");
         
            Engine.getPersistenceManager().setDirty(this);
            if (Log.loggingDebug) log.debug("StartStateInfo  addActiveQuest after set Dirty ");
         
        } finally {
            lock.unlock();
        }
	}
	public void removeActiveQuest(int questID) {
		if (Log.loggingDebug) {
			Log.debug("QuestStateInfo.removeActiveQuest: removing quest=" + questID + " from obj=" + this);
        }
        lock.lock();
        try {
        	QuestState qs = getActiveQuests(currentCategory).remove(questID);
        	qs.deactivate();
            Engine.getPersistenceManager().setDirty(this);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, QuestState> getCurrentActiveQuests() {
        lock.lock();
        try {
            return getActiveQuests(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setCurrentActiveQuests(HashMap<Integer, QuestState>activeQuests) {
        lock.lock();
        try {
            this.activeQuests.put(currentCategory, new HashMap<Integer, QuestState>(activeQuests));
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, HashMap<Integer, QuestState>> getActiveQuests() {
        lock.lock();
        try {
            return new HashMap<Integer, HashMap<Integer, QuestState>>(activeQuests);
        } finally {
            lock.unlock();
        }
    }
	public void setActiveQuests(HashMap<Integer, HashMap<Integer, QuestState>>activeQuests) {
        lock.lock();
        try {
            this.activeQuests = new HashMap<Integer, HashMap<Integer, QuestState>>(activeQuests);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, QuestState> getActiveQuests(int category) {
        lock.lock();
        try {
        	if (!activeQuests.containsKey(category))
        		activeQuests.put(category, new HashMap<Integer, QuestState>());
            return activeQuests.get(category);
        } finally {
            lock.unlock();
        }
    }
	/*public void setActiveQuests(int category, HashMap<Integer, QuestState>activeQuests) {
        lock.lock();
        try {
        	Log.debug("QSI: putting active quests: " + activeQuests + " into category: " + category);
            this.activeQuests.put(category, new HashMap<Integer, QuestState>(activeQuests));
        } finally {
            lock.unlock();
        }
	}*/
	
	public void addCompletedQuest(int questID) {
		if (Log.loggingDebug) {
			Log.debug("QuestStateInfo.addCompletedQuest: adding quest=" + questID + " to obj=" + this);
        }
        lock.lock();
        try {
            if (getCompletedQuests(currentCategory).contains(questID)) {
                return;
            }
            getCompletedQuests(currentCategory).add(questID);
            Engine.getPersistenceManager().setDirty(this);
        } finally {
            lock.unlock();
        }
	}
	public void removeCompletedQuest(int questID) {
		if (Log.loggingDebug) {
			Log.debug("QuestStateInfo.removeCompletedQuest: removing quest=" + questID + " from obj=" + this);
        }
        lock.lock();
        try {
        	getCompletedQuests(currentCategory).remove(questID);
            Engine.getPersistenceManager().setDirty(this);
        } finally {
            lock.unlock();
        }
	}
	public ArrayList<Integer> getCurrentCompletedQuests() {
        lock.lock();
        try {
            return getCompletedQuests(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setCurrentCompletedQuests(ArrayList<Integer> completedQuests) {
        lock.lock();
        try {
            this.completedQuests.put(currentCategory, new ArrayList<Integer>(completedQuests));
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, ArrayList<Integer>> getCompletedQuests() {
        lock.lock();
        try {
            return new HashMap<Integer, ArrayList<Integer>>(completedQuests);
        } finally {
            lock.unlock();
        }
    }
	public void setCompletedQuests(HashMap<Integer, ArrayList<Integer>> completedQuests) {
        lock.lock();
        try {
            this.completedQuests = new HashMap<Integer, ArrayList<Integer>>(completedQuests);
        } finally {
            lock.unlock();
        }
	}
	public ArrayList<Integer> getCompletedQuests(int category) {
        lock.lock();
        try {
        	if (!completedQuests.containsKey(category))
    			completedQuests.put(category, new ArrayList<Integer>());
            return completedQuests.get(category);
        } finally {
            lock.unlock();
        }
    }
	/*public void setCompletedQuests(int category, ArrayList<Integer> completedQuests) {
        lock.lock();
        try {
            this.completedQuests.put(category, new ArrayList<Integer>(completedQuests));
        } finally {
            lock.unlock();
        }
	}*/
	private HashMap<Integer, HashMap<Integer, QuestState>> activeQuests = new HashMap<Integer, HashMap<Integer, QuestState>>();
	private HashMap<Integer, ArrayList<Integer>> completedQuests = new HashMap<Integer, ArrayList<Integer>>();
	
	/**
	 * Removes a quest from the players list of active Quests and adds it to the list of 
	 * completed Quests (unless it is repeatable).
	 * @param questID
	 * @param repeatable
	 * @return
	 */
	public boolean concludeQuest(int questID, boolean repeatable) {
		if (!activeQuests.get(currentCategory).containsKey(questID))
			return false;
		boolean concluded = false;
		lock.lock();
		try {
			concluded = activeQuests.get(currentCategory).get(questID).handleConclude();
			if (concluded) {
				// remove the quest from the active list
				QuestState qs = activeQuests.get(currentCategory).remove(questID);
				// add the quest to the completed list unless it is repeatable
				//if (!repeatable) {
					getCompletedQuests(currentCategory).add(questID);
				//}
				Engine.getPersistenceManager().setDirty(this);
				qs.sendStateStatusChange();
				Log.debug("QSI: Moved quest: " + questID + " from active to completed.");
			}
		} finally {
			lock.unlock();
		}
		return concluded;
	}
	

    public InterpolatedWorldNode getWorldNode() { return node; }
    public void setWorldNode(InterpolatedWorldNode node) { this.node = node; }
    InterpolatedWorldNode node;
    
    /*
     * Group specific data
     */
    
	transient protected OID groupOid = null;
	
	public void setGroupOid(OID groupOid){
		this.groupOid = groupOid;
	}

	public OID getGroupOid(){
		return groupOid;
	}	
	
	transient protected OID groupMemberOid = null;
	
	public void setGroupMemberOid(OID groupMemberOid){
		this.groupMemberOid = groupMemberOid;
	}
	
	public OID getGroupMemberOid(){
		return groupMemberOid;
	}	 
	
	public boolean isGrouped(){
		return groupOid != null;
	}
	

	
	public void ReloadQuests(int category) {
		if (activeQuests.containsKey(category)) {
			for (QuestState qs : activeQuests.get(category).values()) {
				AgisBasicQuest quest = (AgisBasicQuest)Agis.QuestManager.get(qs.getQuestRef());
				qs.Apply(quest);
				BasicQuestState bqs = (BasicQuestState)qs;
				quest.AppyGoals(bqs);
				bqs.updateQuestLog(); 
			}
		}
		 QuestPlugin.sendQuestHistoryLogInfo(getOid());
	}
	
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
	 static {
 		try {
 			BeanInfo info = Introspector.getBeanInfo(QuestStateInfo.class);
 			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
 			for (int i = 0; i < propertyDescriptors.length; ++i) {
 				PropertyDescriptor pd = propertyDescriptors[i];
 				if (pd.getName().equals("groupOid")) {
 					pd.setValue("transient", Boolean.TRUE);
 				}else if (pd.getName().equals("groupMemberOid")) {
 					pd.setValue("transient", Boolean.TRUE);
 				}
 				Log.debug("BeanInfo name="+pd.getName());
 			}
 		} catch (Exception e) {
 			Log.error("failed beans initalization");
 		}
 	}
}
