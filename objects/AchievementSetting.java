package atavism.agis.objects;

import java.io.*;
import java.util.*;


public class AchievementSetting implements Serializable {
	public AchievementSetting() {
	}

	public String toString() {
		return "[AchievementSetting: id=" + id + " name=" + name + ";  type=" + type + "; objects="+objects+"; val=" + val + "; bonuses=" + bonuses + "; stats=" + stats + "]";
	}

	protected int id = -1;
	protected Integer val = 0;
	protected Integer type = -1;
	protected String objects = "";
	protected String[] objectsArray = new String[] {};
	protected ArrayList<BonusSettings> bonuses = new ArrayList<BonusSettings>();
	protected String name = "";
	protected String description = "";
	protected ArrayList<EnchantStat> stats = new ArrayList<EnchantStat>();

	public void setBonuses(ArrayList<BonusSettings> bonuses) {
		this.bonuses = bonuses;
	}

	public ArrayList<BonusSettings> getBonuses() {
		return bonuses;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	private static final long serialVersionUID = 1L;
}
