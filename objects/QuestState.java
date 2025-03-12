package atavism.agis.objects;

import java.util.concurrent.locks.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.agis.plugins.*;
import atavism.agis.plugins.QuestClient.StateStatusChangeMessage;
import atavism.msgsys.*;
import java.io.*;
import java.util.*;

public abstract class QuestState
        implements MessageDispatch, Serializable
{
    public QuestState() {
        setupTransient();
    }

    public QuestState(AgisQuest quest, 
    		OID playerOid) {
        // we store the playeroid instead because we are probably
        // loading ourselves while loading up the player object,
        // in which case the player ref isnt available yet
        setupTransient();
        this.playerOid = playerOid;
        setQuestOid(quest.getOid());
        Apply(quest);
    }
    
    
    public void Apply(AgisQuest quest) {
    	setQuestRef(quest.getID());
        setQuestTitle(quest.getName());
        setQuestDesc(quest.getDesc());
        setQuestObjective(quest.getObjective());
        setQuestProgressText(quest.getProgressText());
        setQuestCompletionText(quest.getCompletionText());
		setGrades(quest.getSecondaryGrades());
		setRewards(quest.getRewards());
		setItemRewardsOrder(quest.getItemRewardsOrder());
		setItemRewardsToChooseOrder(quest.getItemRewardsToChooseOrder());
		setRewardsToChoose(quest.getRewardsToChoose());
		setXpRewards(quest.getXpReward());
		setCurrencyRewards(quest.getCurrencyRewards());
		setRepRewards(quest.getRepRewards());
		setReqLevel(quest.getQuestLevelReq());//Dragonsan
    }
    
    // called from constructor and readObject
    protected void setupTransient() {
        lock = LockFactory.makeLock("QuestStateLock");
    }

    public String toString() {
        return "[AbstractQuestStateObject]";
    }

    /**
     * gets activated by QuestPlugin.  the quest is typically created by the QuestBehavior and
     * sent over to the QuestPlugin, which then adds it to the players quest states and also
     * calls activate on the quest state object
     */
    abstract public boolean activate();
    abstract public void deactivate();
    
    abstract public void abandonQuest(OID playerOid);

    public String getName() {
        return getQuestTitle();
    }

    public OID getPlayerOid() {
        return playerOid;
    }
    public void setPlayerOid(OID oid) {
	this.playerOid = oid;
    }

    /**
     * called after the queststate is initialized and set by the world
     * server to the player
     */
    public void handleInit() {
    }
    
    /**
     * called when a mob is killed that the player is getting credit for
     */
    public void handleDeath(AgisMob mobKilled) {
    }

    /**
     * called when the player's inv changes
     */
    public void handleInvUpdate() {
    }

    /**
     * called when the player is concluding (turning in) the quest
     * returns false if the quest state cannot conclude the quest
     */
    public boolean handleConclude() {
    	setConcluded(true);
        return true;
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
        	Log.debug("QUEST STATE: updating quest log info; items rewards: " + itemRewards);
        	QuestPlugin.sendQuestLogInfo(playerOid, questOid, questRef, questTitle, questDesc, questObjective, grades, 
            		xpRewards, currencyRewards, itemRewards, itemRewardsToChoose, repRewards, getObjectiveStatus(), getCompleted(), getItemRewardsOrder(), getItemRewardsToChooseOrder());
        }
    	Log.debug("QUEST: update historical quest log info");
        QuestPlugin.sendQuestHistoryLogInfo(playerOid);
    	Log.debug("QUEST: after update historical quest log info");
            }

    public void updateQuestObjectives() {
        if (Log.loggingDebug)
            Log.debug("QuestState.updateQuestObjectives: this " + this + ", playerOid " + getPlayerOid() + ", questOid " + getQuestOid());
        QuestPlugin.sendQuestStateInfo(getPlayerOid(), getQuestOid(), getCompleted(), getObjectiveStatus());
    }

    /**
     * send a StateStatusChangeMessage to notify that this quest has been updated
     */
    public void sendStateStatusChange() {
        StateStatusChangeMessage statusMsg = new StateStatusChangeMessage(playerOid, getQuestRef());
        Engine.getAgent().sendBroadcast(statusMsg);
        if (Log.loggingDebug)
            Log.debug("sendStateStatusChange: playerOid=" + playerOid + ", questRef=" + getQuestRef());
    }
    
    public int getCompletionLevel() {
    	return 0;
    }

    public int getQuestRef() {
        return questRef;
    }

    public void setQuestRef(int quest) {
        this.questRef = quest;
    }
//Dragonsan
    public int getReqLevel() {
        return questReqLevel;
    }
//Dragonsan
    public void setReqLevel(int leval) {
        this.questReqLevel = leval;
    }

    public void setCompleted(boolean flag) {
        completedFlag = flag;
    }
    public boolean getCompleted() {
        return completedFlag;
    }

    public void setConcluded(boolean flag) {
	concludedFlag = flag;
    }
    public boolean getConcluded() {
	return concludedFlag;
    }

    /**
     * returns a string representation of the current objectives for display
     * on the client.
     * should return a copy that wont be changed
     * eg: entry1: 0/1 orc scalps
     *     entry2: 4/10 orc hides
     */
    abstract public HashMap<Integer, List<String>> getObjectiveStatus();


    public OID getQuestOid() {
	return questOid;
    }
    public void setQuestOid(OID oid) {
	this.questOid = oid;
    }

    public String getQuestTitle() {
	return questTitle;
    }
    public void setQuestTitle(String title) {
	questTitle = title;
    }

    public String getQuestDesc() {
	return questDesc;
    }
    public void setQuestDesc(String desc) {
	questDesc = desc;
    }

    public String getQuestObjective() {
	return questObjective;
    }
    public void setQuestObjective(String objective) {
	questObjective = objective;
    }
    
    public void setQuestProgressText(String s) {
    	this.progressText = s;
    }
    public String getQuestProgressText() {
    	return progressText;
    }
    
    public void setQuestCompletionText(HashMap<Integer, String> completionTexts) {
    	this.completionText = completionTexts;
    }
    public HashMap<Integer, String> getQuestCompletionText() {
    	return completionText;
    }
    
    public void setGrades(int numGrades) {
        this.grades = numGrades;
    }
    public int getGrades() {
    	return grades;
    }
    
    public boolean getRepeatable() {
    	return repeatable;
    }
    public void setRepeatable(boolean repeatable) {
    	this.repeatable = repeatable;
    }

    /**
     * returns a list item template names
     */
    public HashMap<Integer, HashMap<Integer, Integer>> getRewards() {
        return itemRewards;
    }
    public void setRewards(HashMap<Integer, HashMap<Integer, Integer>> rewards) {
        itemRewards = rewards;
    }
    public void addReward(int grade, int reward, int number) {
        lock.lock();
        try {
        	HashMap<Integer, Integer> gradeRewards = itemRewards.get(grade);
        	if (gradeRewards == null)
        		gradeRewards = new HashMap<Integer, Integer>();
        	gradeRewards.put(reward, number);
        	itemRewards.put(grade, gradeRewards);
        }
        finally {
            lock.unlock();
        }
    }
    HashMap<Integer, HashMap<Integer, Integer>> itemRewards = new HashMap<Integer, HashMap<Integer, Integer>>();
    
	HashMap<Integer, List<Integer>> itemRewardsOrder = new HashMap<Integer, List<Integer>>();

	public void setItemRewardsOrder(HashMap<Integer, List<Integer>> order) {
		itemRewardsOrder = order;
	}

	public HashMap<Integer, List<Integer>> getItemRewardsOrder() {
		return itemRewardsOrder;
	}

    /**
     * returns a list item template names
     */
    public HashMap<Integer, HashMap<Integer, Integer>> getRewardsToChoose() {
        return itemRewardsToChoose;
    }
    public void setRewardsToChoose(HashMap<Integer, HashMap<Integer, Integer>> rewards) {
    	itemRewardsToChoose = rewards;
    }
    public void addRewardToChoose(int grade, int reward, int number) {
        lock.lock();
        try {
        	HashMap<Integer, Integer> gradeRewards = itemRewardsToChoose.get(grade);
        	if (gradeRewards == null)
        		gradeRewards = new HashMap<Integer, Integer>();
        	gradeRewards.put(reward, number);
        	itemRewardsToChoose.put(grade, gradeRewards);
        }
        finally {
            lock.unlock();
        }
    }
    HashMap<Integer, HashMap<Integer, Integer>> itemRewardsToChoose = new HashMap<Integer, HashMap<Integer, Integer>>();
	HashMap<Integer, List<Integer>> itemRewardsToChooseOrder = new HashMap<Integer, List<Integer>>();

	public void setItemRewardsToChooseOrder(HashMap<Integer, List<Integer>> order) {
		itemRewardsToChooseOrder = order;
	}

	public HashMap<Integer, List<Integer>> getItemRewardsToChooseOrder() {
		return itemRewardsToChooseOrder;
	}

    public HashMap<Integer, Integer> getXpRewards() {
        return xpRewards;
    }
    public void setXpRewards(HashMap<Integer, Integer> rewards) {
        xpRewards = rewards;
    }
    HashMap<Integer, Integer> xpRewards = new HashMap<Integer, Integer>();
    
    public void setRepRewards(HashMap<Integer, HashMap<Integer, Integer>> rewards) {
    	repRewards = rewards;
    }
    public HashMap<Integer, HashMap<Integer, Integer>> getRepRewards() {
    	return repRewards;
    }
    HashMap<Integer, HashMap<Integer, Integer>> repRewards = new HashMap<Integer, HashMap<Integer, Integer>>();
    
    public void setCurrencyRewards(HashMap<Integer, HashMap<Integer, Integer>> rewards) {
    	currencyRewards = rewards;
    }
    public HashMap<Integer, HashMap<Integer, Integer>> getCurrencyRewards() {
    	return currencyRewards;
    }
    HashMap<Integer, HashMap<Integer, Integer>> currencyRewards = new HashMap<Integer, HashMap<Integer, Integer>>();

    public abstract void handleMessage(Message msg, int flags);

    transient protected Lock lock = null;
    
    int questRef = -1;
    int questReqLevel = 1;//dragonsan
    OID playerOid = null;
    OID questOid = null;
    boolean completedFlag = false;
    boolean concludedFlag = false;
    String questTitle = null;
    String questDesc = null;
    String questObjective = null;
    String progressText = null;
    HashMap<Integer, String> completionText = new HashMap<Integer, String>();
    int grades = 0;
    boolean repeatable = false;
    
    private static final long serialVersionUID = 1L;
}
