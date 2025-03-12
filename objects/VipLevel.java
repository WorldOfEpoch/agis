package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;

public class VipLevel implements Serializable {
	protected int id;
	protected String name;
	protected String desc;
	protected int level;
	protected long points_max;
	
	
	HashMap<String, BonusSettings> settings = new HashMap<String, BonusSettings>();	
	public VipLevel() {
	}
	
	public VipLevel(int id,String name, int level,long points_max,String desc) {
		this.id = id;
		this.name = name;
		this.level = level;
		this.points_max = points_max;
		this.desc=desc;
	}
	
	public void setName(String name){
		this.name = name;
	}
	public String getName(){
		return name;
	}
	
	public void setDescription (String desc){
		this.desc = desc;
	}
	public String getDescription (){
		return desc;
	}

	public void setLevel(int level){
		this.level = level;
	}
	public int getLevel(){
		return level;
	}
	public void setPointsMax(long points_max){
		this.points_max = points_max;
	}
	public long getPointsMax(){
		return points_max;
	}

	
	public void SetSettings(HashMap<String, BonusSettings> settings){
		this.settings = settings;
	}
	public HashMap<String, BonusSettings> GetSettings(){
		return settings;
	}
	
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
}
