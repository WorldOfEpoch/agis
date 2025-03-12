package atavism.agis.objects;

import atavism.server.objects.*;
import atavism.server.engine.*;

import java.util.*;

abstract public class AgisQuest extends Entity {

	public AgisQuest() {
        super();
        // For now, put establish the quest oid in this constructor.
        // When quests are persisted, however, the quest oid will come
        // from the one-arg constructor
        setOid(Engine.getOIDManager().getNextOid());
        setNamespace(Namespace.QUEST);
    }
    
    public void setID(int id) {
        this.id = id;
    }
    public int getID() {
        return id;
    }
    int id = -1;
    
    public abstract HashMap<Integer, List<String>> getObjectives();
    
    public abstract List<String> getGradeObjectives(int grade);

    public void setDesc(String desc) {
        this.desc = desc;
    }
    public String getDesc() {
        return desc;
    }
    String desc = null;

    public void setObjective(String s) {
	this.objective = s;
    }
    public String getObjective() {
	return objective;
    }
    String objective = null;
    
    public void setProgressText(String s) {
    	this.progressText = s;
    }
    public String getProgressText() {
    	return progressText;
    }
    String progressText = null;
    
    public void setCompletionText(int grade, String s) {
    	this.completionText.put(grade, s);
    }
    public HashMap<Integer, String> getCompletionText() {
    	return completionText;
    }
    HashMap<Integer, String> completionText = new HashMap<Integer, String>();
    
    public void setSecondaryGrades(int numGrades) {
        this.grades = numGrades;
    }
    public int getSecondaryGrades() {
    	return grades;
    }
    int grades = 0;

    public void setCurrencyReward(int grade, Integer currencyType, int reward) {
    	HashMap<Integer, Integer> gradeRep = currencyRewards.get(grade);
    	if (gradeRep == null)
    		gradeRep = new HashMap<Integer, Integer>();
    	gradeRep.put(currencyType, reward);
    	currencyRewards.put(grade, gradeRep);
    }
    public HashMap<Integer, HashMap<Integer, Integer>> getCurrencyRewards() {
	return currencyRewards;
    }
    HashMap<Integer, HashMap<Integer, Integer>> currencyRewards = new HashMap<Integer, HashMap<Integer, Integer>>();
    
    public void setXpReward(int grade, int reward) {
    	xpRewards.put(grade, reward);
    }
    public HashMap<Integer, Integer> getXpReward() {
    	return xpRewards;
    }
    HashMap<Integer, Integer> xpRewards = new HashMap<Integer, Integer>();
    
    public void setRepReward(int grade, Integer faction, int reward) {
    	HashMap<Integer, Integer> gradeRep = repRewards.get(grade);
    	if (gradeRep == null)
    		gradeRep = new HashMap<Integer, Integer>();
    	gradeRep.put(faction, reward);
    	repRewards.put(grade, gradeRep);
    }
    public HashMap<Integer, HashMap<Integer, Integer>> getRepRewards() {
    	return repRewards;
    }
    HashMap<Integer, HashMap<Integer, Integer>> repRewards = new HashMap<Integer, HashMap<Integer, Integer>>();

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
        	if (gradeRewards == null) {
        		gradeRewards = new HashMap<Integer, Integer>();
        		List<Integer> order = new LinkedList<Integer>();
        		itemRewardsOrder.put(grade, order);
        	}
        	gradeRewards.put(reward, number);
        	itemRewards.put(grade, gradeRewards);
        	itemRewardsOrder.get(grade).add(reward);
        }
        finally {
            lock.unlock();
        }
    }
    HashMap<Integer, HashMap<Integer, Integer>> itemRewards = new HashMap<Integer, HashMap<Integer, Integer>>();
    HashMap<Integer,  List<Integer>> itemRewardsOrder = new HashMap<Integer,  List<Integer>>();
    
    public HashMap<Integer,  List<Integer>>  getItemRewardsOrder() {
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
        	if (gradeRewards == null) {
        		gradeRewards = new HashMap<Integer, Integer>();
        		 List<Integer> order = new LinkedList<Integer>();
        		itemRewardsToChooseOrder.put(grade, order);
        	}
        	gradeRewards.put(reward, number);
        	itemRewardsToChoose.put(grade, gradeRewards);
        	itemRewardsToChooseOrder.get(grade).add(reward);
        }
        finally {
            lock.unlock();
        }
    }
    HashMap<Integer, HashMap<Integer, Integer>> itemRewardsToChoose = new HashMap<Integer, HashMap<Integer, Integer>>();
    HashMap<Integer,  List<Integer>> itemRewardsToChooseOrder = new HashMap<Integer, List<Integer>>();
    public HashMap<Integer,  List<Integer>>  getItemRewardsToChooseOrder() {
    	return itemRewardsToChooseOrder;
        }
    
    public List<Integer> getQuestPrereqs() {
    	return questPrereqs;
        }
    public void setQuestPrereqs(List<Integer> prereqs) {
	questPrereqs = prereqs;
    }
    public void addQuestPrereq(int questRef) {
	questPrereqs.add(questRef);
    }
    List<Integer> questPrereqs = new LinkedList<Integer>();
    
    public int getQuestStartedReq() {
    	return questStartedReq;
    }
    public void setQuestStartedReq(int req) {
    	questStartedReq = req;
    }
    int questStartedReq = -1;
    
    public int getQuestLevelReq() {
    	return levelReq;
    }
    public void setQuestLevelReq(int req) {
    	levelReq = req;
    }
    int levelReq = 0;
    
    public int getQuestRepReq() {
    	return repReq;
    }
    public void setQuestRepReq(int req) {
    	repReq = req;
    }
    int repReq = 0;
    
    public int getFaction() {
    	return faction;
    }
    public void setFaction(int req) {
    	faction = req;
    }
    public int faction = 0;
    
    public int getQuestSecondaryGrades() {
    	return secondaryGrades;
    }
    public void setQuestSecondaryGrades(int grades) {
    	secondaryGrades = grades;
    }
    int secondaryGrades = 0;
    
    public int getReqCompletedGradeB() {
    	return reqCompletedGradeB;
    }
    public void setReqCompletedGradeB(int num) {
    	reqCompletedGradeB = num;
    }
    int reqCompletedGradeB = 0;
    
    public int getReqCompletedGradeA() {
    	return reqCompletedGradeA;
    }
    public void setReqCompletedGradeA(int num) {
    	reqCompletedGradeA = num;
    }
    int reqCompletedGradeA = 0;
    
    public void setRequirements(HashMap<Integer, HashMap<String, Integer>> requirements) {
        lock.lock();
        try {
            this.requirements = requirements;
        }
        finally {
            lock.unlock();
        }
    }
    public HashMap<Integer, HashMap<String, Integer>> getRequirements() {
        lock.lock();
        try {
            return requirements;
        }
        finally {
            lock.unlock();
        }
    }
    public void addRequirement(int requirementType, String requirementOption, int requiredValue) {
        lock.lock();
        try {
        	if (requirements.containsKey(requirementType)) {
				HashMap<String, Integer> requirementOptions = requirements.get(requirementType);
				requirementOptions.put(requirementOption, requiredValue);
				requirements.put(requirementType, requirementOptions);
			} else {
				HashMap<String, Integer> requirementOptions = new HashMap<String, Integer>();
				requirementOptions.put(requirementOption, requiredValue);
				requirements.put(requirementType, requirementOptions);
			}
        }
        finally {
            lock.unlock();
        }
    }
    HashMap<Integer, HashMap<String, Integer>> requirements = new HashMap<Integer, HashMap<String, Integer>>();

    // quest that is immediately offered when this quest is concluded
    public AgisQuest getChainQuest() {
        return chainQuest;
    }
    public void setChainQuest(AgisQuest chainQuest) {
        this.chainQuest = chainQuest;
    }
    AgisQuest chainQuest = null;
    
    // Is the quest repeatable?
    public boolean getRepeatable() {
    	return repeatable;
    }
    public void setRepeatable(boolean repeatable) {
    	this.repeatable = repeatable;
    }
    boolean repeatable = false;
    
    // Is the quest repeatable?
   /* public boolean getReqGuild() {
    	return reqGuild;
    }
    public void setReqGuildLevel(if reqGuild) {
    	this.reqGuild = reqGuild;
    }
    boolean reqGuild = false;
    */
    public String toString() {
    	return "[AgisQuest "+getName()+"; ID="+getID()+"; faction="+getFaction()+"; repeat="+getRepeatable()+"; req="+getRequirements()+" ]";
    }
    
    public abstract QuestState generate(OID playerOid);
    
    private static final long serialVersionUID = 1L;
}
