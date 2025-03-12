package atavism.agis.objects;

import java.io.Serializable;

import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.server.util.Log;

public class SkillData implements Serializable {
	protected int skillID;
	protected String skillName;
	protected int skillCurrent;
	protected int skillLevel;
	protected int skillMaxLevel;
	protected int parentSkill;
	protected int state = 1; // 0 = locked, 1 = increasing, -1 = decreasing
	protected int experience = 0;
	protected int experienceMax = 0;
	protected boolean talent = false; //0 = Skill, 1 = Talent
	public SkillData() {
	}

	public SkillData(int type, String skillName, int skillCurrent, int skillLevel, int skillMaxLevel, int parentSkill, boolean talent) {
		Log.debug("SKILL TEMPLATE: starting skillTemplate creation: " + skillName+" talent="+talent);
		this.skillID = type;
		this.skillName = skillName;
		this.skillCurrent = skillCurrent;
		this.skillLevel = skillLevel;
		this.skillMaxLevel = skillMaxLevel;
		this.parentSkill = parentSkill;
		this.talent = talent;
		Log.debug("SKILL TEMPLATE: finished skillTemplate creation with level/max: " + skillLevel + "/" + skillMaxLevel);
	}

	public void alterSkillMax(int delta) {
		skillMaxLevel += delta;
		Log.debug("SKILL: skill max increased to " + skillMaxLevel + " for skill " + skillID);
		if (skillCurrent > (skillMaxLevel * ClassAbilityPlugin.POINTS_PER_SKILL_LEVEL))
			skillCurrent = skillMaxLevel * ClassAbilityPlugin.POINTS_PER_SKILL_LEVEL;
		
	}

	public boolean getTalent() {
		return talent;
	}

	public void setTalent(boolean talent) {
		this.talent = talent;
	}
	public int getExperienceMax() {
		return experienceMax;
	}

	public void setExperienceMax(int experience) {
		this.experienceMax = experience;
	}
	
	public int getExperience() {
		return experience;
	}

	public void setExperience(int experience) {
		//Log.dumpStack("SkillData: setExperience: " + experience);
			this.experience = experience;
	}
	
	public void alterExperience(int delta) {
		this.experience += delta;
	}
	public void alterSkillLevel(int delta) {
		skillLevel += delta;
		Log.debug("SKILL: skill level increased to " + skillLevel + " for skill " + skillID);
	}

	public void alterSkillCurrent(int delta) {
		skillCurrent += delta;
	}

	public int getSkillID() {
		return skillID;
	}

	public void setSkillID(int skillID) {
		this.skillID = skillID;
	}

	public String getSkillName() {
		return skillName;
	}

	public void setSkillName(String skillName) {
		this.skillName = skillName;
	}

	public int getSkillCurrent() {
		return skillCurrent;
	}

	public void setSkillCurrent(int skillCurrent) {
		this.skillCurrent = skillCurrent;
	}

	public int getSkillLevel() {
		return skillLevel;
	}

	public void setSkillLevel(int skillLevel) {
		this.skillLevel = skillLevel;
	}

	public int getSkillMaxLevel() {
		return skillMaxLevel;
	}

	public void setSkillMaxLevel(int skillMaxLevel) {
		this.skillMaxLevel = skillMaxLevel;
	}
	
	public int getParentSkill() {
		return parentSkill;
	}

	public void setParentSkill(int parentSkill) {
		this.parentSkill = parentSkill;
	}
	
	public int getState() {
		return state;
	}

	public void setState(int state) {
		this.state = state;
	}

	private static final long serialVersionUID = 1L;
}