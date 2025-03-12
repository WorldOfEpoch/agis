package atavism.agis.objects;

import java.io.*;
import java.util.*;


public class MobBehaviorProfile implements Serializable {
	public MobBehaviorProfile() {
	}

	public String toString() {
		return "[MobBehaviorProfile: id=" + id + " name=" + name +" behaviors="+behaviors+"]";
	}

	/**
	 * Profile id
	 */
	protected int id = -1;
	/**
	 * Profile Name
	 */
	protected String name = "";
	protected Integer val = 0;
	//protected Integer type = -1;
	//protected String objects = "";
	//protected String[] objectsArray = new String[] {};
	public  ArrayList<MobBehavior> behaviors = new ArrayList<MobBehavior>();
	//protected String description = "";
	//protected ArrayList<EnchantStat> stats = new ArrayList<EnchantStat>();

	
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	
	public void addBehavior(MobBehavior mb) {
		behaviors.add(mb);
	}
	

	/*
	public void setBonuses(ArrayList<BonusSettings> bonuses) {
		this.bonuses = bonuses;
	}

	public ArrayList<BonusSettings> getBonuses() {
		return bonuses;
	}


	public Integer getValue() {
		return val;
	}

	public void setValue(Integer val) {
		this.val = val;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public String getObjects() {
		return objects;
	}

	public String[] getObjectsArray() {
		return objectsArray;
	}

	public void setObjects(String objects) {
		this.objects = objects;
		objectsArray = objects.split(";");
	}


	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setStats(ArrayList<EnchantStat> stats) {
		this.stats = stats;
	}

	public ArrayList<EnchantStat> getStats() {
		return stats;
	}
*/
	private static final long serialVersionUID = 1L;
}
