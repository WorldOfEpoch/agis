package atavism.agis.objects;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import atavism.server.util.*;





public class ResourceNodeProfileSettings implements Serializable {
	
    int id;
    int skill;
    int skillLevelReq;
    int skillLevelMax;
    int skillExp=0;
    String weaponReq;
    boolean equippedReq = false;
    String gameObject;
    String harvestCoordinatedEffect;
    String activeCoordinatedEffect;
    String deactiveCoordinatedEffect;
    int respawnTime;
    int respawnTimeMax;
     int harvestCount;
     float harvestTimeReq = 0;
     float maxHarvestDistance = 0;
       float cooldown = 0F;
    float deactivationDelay =0F;
    List<ResourceDrop> drops = new LinkedList<ResourceDrop>();
	protected Integer priority = 1;
	protected Integer priorityMax = 1;
	protected Integer lootMaxCount = 1;
	protected boolean ensureLoot = true ;
	
	public ResourceNodeProfileSettings() {}
	
	
	 public void AddResourceDrop(int item, int min, int max, float chance, float chanceMax) {
	    	drops.add(new ResourceDrop(item, min, max, chance, chanceMax));
	    }
	
	
	public int getID() { return id; }
    public void setID(int id) {
    	this.id = id;
    }
    
    public int getSkill() { return skill; }
    public void setSkill(int skill) {
    	this.skill = skill;
    }
    
    public int getSkillLevelReq() { return skillLevelReq; }
    public void setSkillLevelReq(int skillLevelReq) {
    	this.skillLevelReq = skillLevelReq;
    	if (this.skillLevelReq > skillLevelMax) 
    		this.skillLevelMax = this.skillLevelReq;
    }
    
    public int getSkillLevelMax() { return skillLevelMax; }
    public void setSkillLevelMax(int skillLevelMax) {
    	this.skillLevelMax = skillLevelMax;
    	if (this.skillLevelMax < this.skillLevelReq)
    		this.skillLevelMax = this.skillLevelReq;
    }
    
    public int getSkillExp() { return skillExp; }
    public void setSkillExp(int skillexp) {
    	this.skillExp = skillexp;
    }
    
    public String getWeaponReq() { return weaponReq; }
    public void setWeaponReq(String weaponReq) {
    	this.weaponReq = weaponReq;
    }
    
    public boolean getEquippedReq() { return equippedReq; }
    public void setEquippedReq(boolean equippedReq) {
    	this.equippedReq = equippedReq;
    }
    
    public String getGameObject() { return gameObject; }
    public void setGameObject(String gameObject) {
    	this.gameObject = gameObject;
    }
    
    public String getHarvestCoordEffect() { return harvestCoordinatedEffect; }
    public void setHarvestCoordEffect(String coordinatedEffect) {
    	this.harvestCoordinatedEffect = coordinatedEffect;
    }
    
    public String getActivateCoordEffect() { return activeCoordinatedEffect; }
    public void setActivateCoordEffect(String coordinatedEffect) {
    	this.activeCoordinatedEffect = coordinatedEffect;
    }
    
    public String getDeactivateCoordEffect() { return deactiveCoordinatedEffect; }
    public void setDeactivateCoordEffect(String coordinatedEffect) {
    	this.deactiveCoordinatedEffect = coordinatedEffect;
    }
   
    public int getRespawnTime() { return respawnTime; }
    public void setRespawnTime(int respawnTime) {
    	this.respawnTime = respawnTime;
    }
    
    public int getRespawnTimeMax() { return respawnTimeMax; }
    public void setRespawnTimeMax(int respawnTimeMax) {
    	this.respawnTimeMax = respawnTimeMax;
    }
    
    public int getHarvestCount() { return harvestCount; }
    public void setHarvestCount(int harvestCount) {
    	this.harvestCount = harvestCount;
    }
    
    public float getHarvestTimeReq() { return harvestTimeReq; }
    public void setHarvestTimeReq(float harvestTimeReq) {
    	this.harvestTimeReq = harvestTimeReq;
    }
    
    public float getMaxHarvestDistance() { return maxHarvestDistance; }
    public void setMaxHarvestDistance(float maxHarvestDistance) {
    	this.maxHarvestDistance = maxHarvestDistance;
    }
    
    public float getCooldown() { return cooldown; }
    public void setCooldown(float cooldown) {
    	this.cooldown = cooldown;
    }
    
    public float getDeactivationDelay() { return deactivationDelay; }
    public void setDeactivationDelay(float deactivationDelay) {
    	this.deactivationDelay = deactivationDelay;
    }
    public int getPriority() { return priority; }
    public void setPriority(int priority) {
    	this.priority = priority;
    }
   
    public int getPriorityMax() { return priorityMax; }
    public void setPriorityMax(int priorityMax) {
    	this.priorityMax = priorityMax;
    }
    
    public int getLootMaxCount() { return lootMaxCount; }
    public void setLootMaxCount(int lootMaxCount) {
    	this.lootMaxCount = lootMaxCount;
    }

    public boolean getEnsureLoot() { return ensureLoot; }
    public void setEnsureLoot(boolean ensureLoot) {
    	this.ensureLoot = ensureLoot;
    }
    
    public List<ResourceDrop> getResourceDrops(){
    	Log.debug("getResourceDrops: "+drops.size());
    	return drops;
    }
    
	private static final long serialVersionUID = 1L;
}