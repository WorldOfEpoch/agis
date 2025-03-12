package atavism.agis.objects;

import java.io.*;
import java.util.*;

import atavism.server.util.Log;

/**
 * The BuildObjectTemplate class stores all the information needed about a building object. 
 * @author Andrew Harrison
 *
 */
public class BuildObjectTemplate implements Serializable {
	protected int id;
	protected String name;
    protected int skill = -1;
    protected int skillLevelReq = 0;
    protected String weaponReq = "";
    protected float maxDistance = 3f;
    protected boolean buildTaskReqPlayer = true;
    protected boolean buildSolo = false;
    protected boolean fixedTime = false;
    protected int claim_object_category = -1;
    protected int maxHealth = 1;
    protected String interactionType;
    protected int interactionID;
    protected String interactionData1;
    protected boolean lockable;
    protected int lockLimit;
    protected boolean attackable=false;
    protected boolean repairable=false;
    protected ArrayList<BuildObjectStage> stages = new ArrayList<BuildObjectStage>();
    protected ArrayList<Integer> validClaimType =new ArrayList<Integer>();
    //protected int validClaimType = 1;
    public BuildObjectTemplate() {
    	
    }

    public BuildObjectTemplate(int id, String name, int skill, int skillLevelReq, 
    		String weaponReq, float maxDistance, boolean buildTaskReqPlayer) {
    	//Log.debug("SKILL TEMPLATE: starting skillTemplate creation");
    	this.id = id;
    	this.name = name;
    	this.skill = skill;
    	this.skillLevelReq = skillLevelReq;
    	this.weaponReq = weaponReq;
    	this.maxDistance = maxDistance;
    	this.buildTaskReqPlayer = buildTaskReqPlayer;
	    //Log.debug("SKILL TEMPLATE: finished skillTemplate creation");
    }
    public String toString() {
    	return "[BuildObjectTemplate id="+id+" name="+name+" skill="+skill+" skillLevelReq="+skillLevelReq+" interactionType="+interactionType+" interactionID="+interactionID+" interactionData1="+interactionData1
    			+" weaponReq="+weaponReq+" stages="+stages+" validClaimType="+validClaimType+" buildTaskReqPlayer="+buildTaskReqPlayer+" buildSolo="+buildSolo+" fixedTime="+fixedTime+" claim_object_category="+claim_object_category+"]";
    }
    
    /**
     * Adds a stage from the database.
     * @param stage
     */
    public void addStage(BuildObjectStage stage) {
    	if(Log.loggingDebug)
				Log.debug("BuildObjectTemplate: id="+id+" addStage="+stage);
    	stages.add(stage);
    }
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
   
    public int getClaimObjectCategory() { return claim_object_category; }
    public void setClaimObjectCategory(int id) { claim_object_category = id; }
  
    public boolean getAttackable() { return attackable; }
    public void setAttackable(boolean attackable) { this.attackable = attackable; }
    public boolean getRepairable() { return repairable; }
    public void setRepairable(boolean repairable) { this.repairable = repairable; }
 
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getSkill() { return skill; }
    public void setSkill(int skill) { this.skill = skill; }
    
    public int getSkillLevelReq() { return skillLevelReq; }
    public void setSkillLevelReq(int skillLevelReq) { this.skillLevelReq = skillLevelReq; }
    
    public String getWeaponReq() { return weaponReq; }
    public void setWeaponReq(String weaponReq) { this.weaponReq = weaponReq; }
    
    public float getMaxDistance() { return maxDistance; }
    public void setMaxDistance(float maxDistance) { this.maxDistance = maxDistance; }
    
    public boolean buildTaskRequiresPlayer() { return buildTaskReqPlayer; }
    public void buildTaskRequiresPlayer(boolean buildTaskReqPlayer) { this.buildTaskReqPlayer = buildTaskReqPlayer; }
   
    public boolean buildTaskSolo() { return buildSolo; }
    public void buildTaskSolo(boolean buildSolo) { this.buildSolo = buildSolo; }
   
    public boolean buildTaskFixedTime() { return fixedTime; }
    public void buildTaskFixedTime(boolean fixedTime) { this.fixedTime = fixedTime; }
    
    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }
    
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    
    public int getInteractionID() { return interactionID; }
    public void setInteractionID(int interactionID) { this.interactionID = interactionID; }
    
    public String getInteractionData1() { return interactionData1; }
    public void setInteractionData1(String interactionData1) { this.interactionData1 = interactionData1; }
    
    public boolean getLockable() { return lockable; }
    public void setLockable(boolean lockable) { this.lockable = lockable; }
    
    public int getLockLimit() { return lockLimit; }
    public void setLockLimit(int lockLimit) { this.lockLimit = lockLimit; }

    public ArrayList<Integer> getValidClaimType() { return validClaimType; }
    public void setValidClaimType(ArrayList<Integer> validClaimType) { this.validClaimType = validClaimType; }
    public void addValidClaimType(Integer claimType) { this.validClaimType.add(claimType); }

    public ArrayList<BuildObjectStage> getStages() { return stages; }
    public void setStages(ArrayList<BuildObjectStage> stages) { this.stages = stages; }
    
    public BuildObjectStage getStage(int index) {
    	if (stages.size() > index)
    		return stages.get(index);
    	else
    		return null;
    }
    
    private static final long serialVersionUID = 1L;
}

