package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class PetProfileLevel implements Serializable {
	protected int id;
	protected int level;
	protected int experience;
	protected String levelUpCoordEffect = "LevelUpEffect";
	protected int template_id;

	protected int slots_profile_id = -1;

	public PetProfileLevel() {}

	public PetProfileLevel(int id, int level) {
		this.id = id;
		this.level = level;
	}
	
	public void SetLevel(int level){
		this.level = level;
	}
	public int GetLevel(){
		return level;
	}

	public void setExperience(int experience) {this.experience = experience;}
	public int getExperience() {return experience;}


	public void setTemplateId(int template_id) {this.template_id = template_id;}
	public int getTemplateId() {return template_id;}

	public void setLevelUpCoordEffect(String levelUpCoordEffect) {this.levelUpCoordEffect = levelUpCoordEffect;}
	public String getLevelUpCoordEffect() {return levelUpCoordEffect;}

	public void setSlotsProfileId(int id) {this.slots_profile_id = id;}

	public int getSlotsProfileId() {return slots_profile_id;}

	@Override
	public String toString() {
		return "PetProfileLevel [id=" + id + ", level=" + level + ", experience="+experience + ", template_id=" + template_id + ", slots_profile_id=" + slots_profile_id + "]";
	}

	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
}
