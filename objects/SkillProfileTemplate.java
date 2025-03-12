package atavism.agis.objects;

import java.io.*;
import java.util.*;
import atavism.server.util.*;

/**
 * The SkillProfileTemplate class stores all the information needed about a skillProfile. 
 * 
 *
 */
public class SkillProfileTemplate implements Serializable {
	protected int profileID;
	protected String profileName;
	HashMap<Integer, Integer> levelExp = new HashMap<Integer, Integer>();
    HashMap<Integer, Float> levelDiff = new HashMap<Integer, Float>();
   
    public SkillProfileTemplate(int id, String profileName) {
    	//Log.debug("SKILL TEMPLATE: starting skillTemplate creation");
    	this.profileID = id;
    	this.profileName = profileName;
    	
	    //Log.debug("SKILL TEMPLATE: finished skillTemplate creation");
    }
    
    public void addLevelExp(int level, int exp) {
    	levelExp.put(level,exp);
    }
    public void setLevelExp(HashMap<Integer, Integer> lExp) {
    	levelExp= lExp;
    }
  
    public int getLevelExp(int level) {
    	Log.debug("SKILL: getLevelExp "+levelExp+" level:"+level);
    	if (levelExp.containsKey(level))
    	  	return levelExp.get(level);
    	Log.debug("SKILL: getLevelExp no level:"+level+" return 0");
    	return 200000000;
    }
    public void addLevelDiff(int level, float percentage) {
    	levelDiff.put(level,percentage);
    }
    
    public float getLevelDiff(int level) {
    	if(levelDiff.size()==0)
    		return 100f;
    	if (levelDiff.containsKey(level))
    	  	return levelDiff.get(level);
    	return 0f;
    }
   
   
    public int getProfileID() { return profileID; }
    public void setProfileID(int profileID) { this.profileID = profileID; }
   
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    
    private static final long serialVersionUID = 1L;
}
