package atavism.agis.objects;

import java.io.*;
import java.util.*;
import atavism.server.util.*;

/**
 * The SkillTemplate class stores all the information needed about a skill. 
 * @author Andrew Harrison
 *
 */
public class SkillTemplate implements Serializable {
	protected int skillID;
	protected String skillName;
	protected int aspect;
    protected int oppositeAspect;
    protected int type = 0;
    
    protected boolean mainAspectOnly = false;
    protected String primaryStat;
    protected int primaryStatValue;
    protected int primaryStatInterval;
    protected String secondaryStat;
    protected int secondaryStatValue;
    protected int secondaryStatInterval;
    protected String thirdStat;
    protected int thirdStatValue;
    protected int thirdStatInterval;
    protected String fourthStat;
    protected int fourthStatValue;
    protected int fourthStatInterval;
    protected LinkedList<Integer> subSkills;
    protected int parentSkill = -1;
    protected int parentSkillLevelReq = 1;
    protected int maxLevel;
    protected int prereqSkill1 = -1;
    protected int prereqSkill1Level = 1;
    protected int prereqSkill2 = -1;
    protected int prereqSkill2Level = 1;
    protected int prereqSkill3 = -1;
    protected int prereqSkill3Level = 1;
    protected int playerLevelReq = 1;
    protected int skillPointCost = 1;
    protected boolean automaticallyLearn = true;
    protected int skill_profile_id =-1;
    protected LinkedList<SkillAbility> abilities = new LinkedList<SkillAbility>();
    protected long date = 0L;
   
    protected boolean talent = false;

    /**
     *
     * @param type
     * @param skillName
     * @param aspect
     * @param oppositeAspect
     * @param mainAspectOnly
     * @param primaryStat
     * @param primaryStatValue
     * @param primaryStatInterval
     * @param secondaryStat
     * @param secondaryStatValue
     * @param secondaryStatInterval
     * @param thirdStat
     * @param thirdStatValue
     * @param thirdStatInterval
     * @param fourthStat
     * @param fourthStatValue
     * @param fourthStatInterval
     * @param autoLearn
     * @param talent
     */
    public SkillTemplate(int type, String skillName, int aspect, int oppositeAspect, boolean mainAspectOnly,
                         String primaryStat, int primaryStatValue, int primaryStatInterval,
                         String secondaryStat,  int secondaryStatValue, int secondaryStatInterval,
                         String thirdStat, int thirdStatValue, int thirdStatInterval,
                         String fourthStat, int fourthStatValue, int fourthStatInterval,
                         boolean autoLearn,boolean talent) {
    	//Log.debug("SKILL TEMPLATE: starting skillTemplate creation");
    	this.skillID = type;
    	this.skillName = skillName;
    	this.aspect = aspect;
    	this.oppositeAspect = oppositeAspect;
    	this.mainAspectOnly = mainAspectOnly;
        this.primaryStat = primaryStat;
        this.primaryStatValue = primaryStatValue;
        this.primaryStatInterval = primaryStatInterval;
        this.secondaryStat = secondaryStat;
        this.secondaryStatValue = secondaryStatValue;
        this.secondaryStatInterval = secondaryStatInterval;
        this.thirdStat = thirdStat;
        this.thirdStatValue = thirdStatValue;
        this.thirdStatInterval = thirdStatInterval;
        this.fourthStat = fourthStat;
        this.fourthStatValue = fourthStatValue;
        this.fourthStatInterval = fourthStatInterval;
    	this.automaticallyLearn = autoLearn;
    	this.talent = talent;
	    //Log.debug("SKILL TEMPLATE: finished skillTemplate creation");
    }
    
    public void addSkillAbility(int skillLevelReq, int abilityID, String abilityName, boolean autoLearn) {
    	SkillAbility skillAbility = new SkillAbility();
    	skillAbility.skillLevelReq = skillLevelReq;
    	skillAbility.abilityID = abilityID;
    	skillAbility.abilityName = abilityName;
    	skillAbility.automaticallyLearn = autoLearn;
    	abilities.add(skillAbility);
    }
    
    public ArrayList<SkillAbility> getAbilitiesByLevel(int level) {
    	ArrayList<SkillAbility> levelAbilities = new ArrayList<SkillAbility>();
    	for (SkillAbility ability : abilities) {
    		if (ability.skillLevelReq == level) {
    			levelAbilities.add(ability);
    		}
    	}
    	Log.debug("SKILL: got abilities: " + levelAbilities + " for skill: " + skillID + " at level: " + level);
    	return levelAbilities;
    }
    
    public ArrayList<String> getStartAbilities() {
    	ArrayList<String> abilityNames = new ArrayList<String>();
    	for (SkillAbility ability : abilities) {
    		if ((ability.skillLevelReq == 1 || ability.skillLevelReq == 0) && ability.automaticallyLearn) {
    			abilityNames.add(ability.abilityName);
    		}
    	}
    	return abilityNames;
    }
    
    public ArrayList<Integer> getStartAbilityIDs() {
    	ArrayList<Integer> abilityIDs = new ArrayList<Integer>();
    	for (SkillAbility ability : abilities) {
    		if ((ability.skillLevelReq == 1 || ability.skillLevelReq == 0) && ability.automaticallyLearn) {
    			abilityIDs.add(ability.abilityID);
    		}
    	}
    	
    	return abilityIDs;
    }
    
    public int getSkillID() { return skillID; }
    public void setSkillID(int skillID) { this.skillID = skillID; }
   
    public boolean isTalent() { return talent; }
   // public void setSkillType(boolean talent) { this.talent = talent; }
   
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
  
    public int getSkillProfileID() { return skill_profile_id; }
    public void setSkillProfileID(int skillProfileID) { this.skill_profile_id = skillProfileID; }
  
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    
    public int getAspect() { return aspect; }
    public void setAspect(int aspect) { this.aspect = aspect; }
    
    public int getOppositeAspect() { return oppositeAspect; }
    public void setOppositeAspect(int oppositeAspect) { this.oppositeAspect = oppositeAspect; }
    
    public boolean mainAspectOnly() { return mainAspectOnly; }
    public void mainAspectOnly(boolean mainAspectOnly) { this.mainAspectOnly = mainAspectOnly; }

    public String getPrimaryStat() { return primaryStat; }
    public void setPrimaryStat(String primaryStat) { this.primaryStat = primaryStat; }
    public int getPrimaryStatValue() { return primaryStatValue; }
    public void setPrimaryStatValue(int primaryStatValue) { this.primaryStatValue = primaryStatValue; }
    public int getPrimaryStatInterval() { return primaryStatInterval; }
    public void setPrimaryStatInterval(int primaryStatInterval) { this.primaryStatInterval = primaryStatInterval; }

    public String getSecondaryStat() { return secondaryStat; }
    public void setSecondaryStat(String secondaryStat) { this.secondaryStat = secondaryStat; }
    public int getSecondaryStatValue() { return secondaryStatValue; }
    public void setSecondaryStatValue(int secondaryStatValue) { this.secondaryStatValue = secondaryStatValue; }
    public int getSecondaryStatInterval() { return secondaryStatInterval; }
    public void setSecondaryStatInterval(int secondaryStatInterval) { this.secondaryStatInterval = secondaryStatInterval; }

    public String getThirdStat() { return thirdStat; }
    public void setThirdStat(String thirdStat) { this.thirdStat = thirdStat; }
    public int getThirdStatValue() { return thirdStatValue; }
    public void setThirdStatValue(int thirdStatValue) { this.thirdStatValue = thirdStatValue; }
    public int getThirdStatInterval() { return thirdStatInterval; }
    public void setThirdStatInterval(int thirdStatInterval) { this.thirdStatInterval = thirdStatInterval; }

    public String getFourthStat() { return fourthStat; }
    public void setFourthStat(String fourthStat) { this.fourthStat = fourthStat; }
    public int getFourthStatValue() { return fourthStatValue; }
    public void setFourthStatValue(int fourthStatValue) { this.fourthStatValue = fourthStatValue; }
    public int getFourthStatInterval() { return fourthStatInterval; }
    public void setFourthStatInterval(int fourthStatInterval) { this.fourthStatInterval = fourthStatInterval; }

    public LinkedList<SkillAbility> getAbilities() { return abilities; }
    public void setAbilities(LinkedList<SkillAbility> abilities) { this.abilities = abilities; }
    
    public int getParentSkillLevelReq() { return parentSkillLevelReq; }
    public void setParentSkillLevelReq(int parentSkillLevelReq) { this.parentSkillLevelReq = parentSkillLevelReq; }
    
    public int getParentSkill() { return parentSkill; }
    public void setParentSkill(int parentSkill) { this.parentSkill = parentSkill; }
    
    public LinkedList<Integer> getSubSkills() { return subSkills; }
    public void setSubSkills(LinkedList<Integer> subSkills) { this.subSkills = subSkills; }
    public void addSubSkill(int subSkill) { subSkills.add(subSkill); }
    
    public int getMaxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }
    
    public int getPrereqSkill1() { return prereqSkill1; }
    public void setPrereqSkill1(int prereqSkill1) { this.prereqSkill1 = prereqSkill1; }
    
    public int getPrereqSkill1Level() { return prereqSkill1Level; }
    public void setPrereqSkill1Level(int prereqSkill1Level) { this.prereqSkill1Level = prereqSkill1Level; }
    
    public int getPrereqSkill2() { return prereqSkill2; }
    public void setPrereqSkill2(int prereqSkill2) { this.prereqSkill2 = prereqSkill2; }
    
    public int getPrereqSkill2Level() { return prereqSkill2Level; }
    public void setPrereqSkill2Level(int prereqSkill2Level) { this.prereqSkill2Level = prereqSkill2Level; }
    
    public int getPrereqSkill3() { return prereqSkill3; }
    public void setPrereqSkill3(int prereqSkill3) { this.prereqSkill3 = prereqSkill3; }
    
    public int getPrereqSkill3Level() { return prereqSkill3Level; }
    public void setPrereqSkill3Level(int prereqSkill3Level) { this.prereqSkill3Level = prereqSkill3Level; }
    
    public int getPlayerLevelReq() { return playerLevelReq; }
    public void setPlayerLevelReq(int playerLevelReq) { this.playerLevelReq = playerLevelReq; }
    
    public int getSkillPointCost() { return skillPointCost; }
    public void setSkillPointCost(int skillPointCost) { this.skillPointCost = skillPointCost; }
    
    public boolean getAutomaticallyLearn() { return automaticallyLearn; }
    public void setAutomaticallyLearn(boolean automaticallyLearn) { this.automaticallyLearn = automaticallyLearn; }
   
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
  
    public class SkillAbility {
    	public int skillLevelReq = 1;
    	public int abilityID = -1;
    	public String abilityName = "";
    	public boolean automaticallyLearn = true;
    }
    
    private static final long serialVersionUID = 1L;
}
