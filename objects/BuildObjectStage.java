package atavism.agis.objects;

import java.io.*;
import java.util.*;

public class BuildObjectStage implements Serializable {
	
	protected HashMap<Integer, Integer> itemReqs;
	LinkedHashMap<Integer, String> progressGameObjects = new LinkedHashMap<Integer, String>();
	LinkedHashMap<Integer, String> damagedGameObjects = new LinkedHashMap<Integer, String>();
	LinkedHashMap<Integer, AtavismBuildingColliders> progressColliders = new LinkedHashMap<Integer, AtavismBuildingColliders>();
	LinkedHashMap<Integer, AtavismBuildingColliders> damagedColliders = new LinkedHashMap<Integer, AtavismBuildingColliders>();
	protected String gameObject;
	protected float buildTimeReq = 1;
	protected float repairTimeReq = 1;
    protected int health = 0;
    protected int nextStageID = -1;
    protected String interactionType;
    protected int interactionID;
    protected String interactionData1;
    protected int lootTable=-1;
    protected float lootMinPercentage = 0f;
    protected float lootMaxPercentage = 0f;
    
    	
    public BuildObjectStage(String gameObject, float buildTimeReq, HashMap<Integer, Integer> itemReqs, 
    		int health, int nextStageID) {
    	this.gameObject = gameObject;
    	this.buildTimeReq = buildTimeReq;
    	this.itemReqs = itemReqs;
    	this.health = health;
    	this.nextStageID = nextStageID;
    	 
    }
    	
    public String toString() {
    	return "[BuildObjectStage gameObject="+gameObject+" buildTimeReq="+buildTimeReq+" health="+health+" nextStageID="+nextStageID+" interactionType="+interactionType+" interactionID="+interactionID+" interactionData1="+interactionData1
    			+" itemReqs="+itemReqs+" progressGameObjects="+progressGameObjects+" damagedGameObjects="+damagedGameObjects+"]";
    }
    
    public float getLootMinPercentage() { return lootMinPercentage; }
    public void setLootMinPercentage(float lootMinPercentage) { this.lootMinPercentage = lootMinPercentage; }
   
    public float getLootMaxPercentage() { return lootMaxPercentage; }
    public void setLootMaxPercentage(float lootMaxPercentage) { this.lootMaxPercentage = lootMaxPercentage; }
   
    public int getLootTable() { return lootTable; }
    public void setLootTable(int lootTable) { this.lootTable = lootTable; }
   
    
    public String getGameObject() { return gameObject; }
    public void setGameObject(String gameObject) { this.gameObject = gameObject; }
    
    public LinkedHashMap<Integer, String> getProgressGameObject(){ return progressGameObjects; }
    public void addProgressGameObject(Integer progress, String go){ progressGameObjects.put(progress, go); }
  
    public void setProgressGameObject(LinkedHashMap<Integer, String>go){ progressGameObjects = go; }
    
    public LinkedHashMap<Integer, String> getDamagedGameObject(){ return damagedGameObjects; }
    public void addDamagedGameObject(Integer progress, String go){ damagedGameObjects.put(progress, go); }
  
    public void setDamagedGameObject(LinkedHashMap<Integer, String>go){ damagedGameObjects = go; }
    
    
    public LinkedHashMap<Integer, AtavismBuildingColliders> getProgressColliders(){ return progressColliders; }
    public void addProgressColliders(Integer progress, AtavismBuildingColliders go){ progressColliders.put(progress, go); }
  
    public void setProgressColliders(LinkedHashMap<Integer, AtavismBuildingColliders>go){ progressColliders = go; }
    
    public LinkedHashMap<Integer, AtavismBuildingColliders> getDamagedColliders(){ return damagedColliders; }
    public void addDamagedColliders(Integer progress, AtavismBuildingColliders go){ damagedColliders.put(progress, go); }
  
    public void setDamagedColliders(LinkedHashMap<Integer, AtavismBuildingColliders>go){ damagedColliders = go; }
    
    
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    
    public int getInteractionID() { return interactionID; }
    public void setInteractionID(int interactionID) { this.interactionID = interactionID; }
    
    public String getInteractionData1() { return interactionData1; }
    public void setInteractionData1(String interactionData1) { this.interactionData1 = interactionData1; }
    
    public HashMap<Integer, Integer> getItemReqs() { return itemReqs; }
    public void setItemReqs(HashMap<Integer, Integer> itemReqs) { this.itemReqs = itemReqs; }
    	
    public float getBuildTimeReq() { return buildTimeReq; }
    public void setBuildTimeReq(float buildTimeReq) { this.buildTimeReq = buildTimeReq; }
    public float getRepairTimeReq() { return repairTimeReq; }
    public void setRepairTimeReq(float repairTimeReq) { this.repairTimeReq = repairTimeReq; }
    
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    
    public int getNextStageID() { return nextStageID; }
    public void setNextStageID(int nextStageID) { this.nextStageID = nextStageID; }
    
    private static final long serialVersionUID = 1L;
    
}