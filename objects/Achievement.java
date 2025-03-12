package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Defines what is required to and what is rewarded for completing an achievement. Contains a criteria
 * sub class that defines what is needed to complete the achievement.
 * @author Andrew
 *
 */
public class Achievement implements Serializable {
    public Achievement() {
    }
    
    public Achievement(int id, String name, int prereq, int points, int experience, int item, int itemCount, String skinUnlocked) {
    	this.id = id;
    	this.name = name;
    	this.prereqID = prereq;
    	this.points = points;
    	this.experience = experience;
    	this.item = item;
    	this.itemCount = itemCount;
    	this.skinUnlocked = skinUnlocked;
    	criteria = new HashMap<Integer, AchievementCriteria>();
    }
    
    public void addCriteria(int acID, int eventType, int event, int eventValue, int eventCount, int resetEvent1, int resetEvent2) {
    	AchievementCriteria ac = new AchievementCriteria(eventType, event, eventValue, eventCount, resetEvent1, resetEvent2);
    	criteria.put(acID, ac);
    }

	public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public int getPreReqID() { return prereqID;}
    public void setPreReqID(int prereqID) {
    	this.prereqID = prereqID;
    }
    
    public int getPoints() { return points;}
    public void setPoints(int points) {
    	this.points = points;
    }
    public int getExperience() { return experience;}
    public void setExperience(int experience) {
    	this.experience = experience;
    }
    public int getItem() { return item;}
    public void setItem(int item) {
    	this.item = item;
    }
    public int getItemCount() { return itemCount;}
    public void setItemCount(int itemCount) {
    	this.itemCount = itemCount;
    }
    
    public String getSkinUnlocked() { return skinUnlocked;}
    public void setSkinUnlocked(String skinUnlocked) {
    	this.skinUnlocked = skinUnlocked;
    }

    int id;
    String name;
    int prereqID;
    int points;
    int experience;
    int item;
    int itemCount;
    String skinUnlocked;
    HashMap<Integer, AchievementCriteria> criteria;
    
    /**
     * A criteria object that can be used to define what a player is required to do.
     * @author Andrew
     *
     */
    public class AchievementCriteria {
    	
    	public AchievementCriteria(int eventType, int event, int eventValue, int eventCount, int resetEvent1, int resetEvent2) {
        	this.eventType = eventType;
        	this.event = event;
        	this.eventValue = eventValue;
        	this.eventCount = eventCount;
        	this.resetEvent1 = resetEvent1;
        	this.resetEvent2 = resetEvent2;
        }
    	
    	int eventType;
    	int event;
    	int eventValue;
    	int eventCount;
    	int resetEvent1;
    	int resetEvent2;
    }

    private static final long serialVersionUID = 1L;
}
