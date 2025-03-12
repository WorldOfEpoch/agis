package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.Point;


public class MobBehavior implements Serializable {
	
	public int id = -1;
	
	/**
	 * MobBehavior Type
	 * 0-Melee; 1-Ranged Offensive; 2-Ranged Defensive; 3-Defend; 4-Flee; 5-Heal
	 */
	protected int type=0;
	/**\
	 * Flee Type 
	 * 0-Opposite direction; 1-Defined position; 2-To group friendly mobs
	 */
	protected int fleeType =0;
	protected Point fleePoint;
	protected List<Point> fleePoints = new ArrayList<Point>();
	protected long abilityInterval;
	protected int mobTag=-1;
	protected int weapon=-1;
	protected boolean ignoreChaseDistance=false;
	public List<MobAbility> abilities = new ArrayList<MobAbility>(); 
	public List<MobAbility> startAbilities = new ArrayList<MobAbility>(); 
	public List<MobAbility> endAbilities = new ArrayList<MobAbility>(); 
	public List<BehaviorConditionGroupSettings> conditionsGroup = new ArrayList<BehaviorConditionGroupSettings>();
	
	public MobBehavior() {
	}
	
	
	public void setType(int type){
		this.type = type;
	}
	
	public int getType(){
		return type;
	}
	
	public void setFleeType(int type){
		this.fleeType = type;
	}
	
	public int getFleeType(){
		return fleeType;
	}
	
	public void setFleePoint(Point point){
		this.fleePoint = point;
	}
	
	public Point getFleePoint(){
		return fleePoint;
	}

	public void addFleePoint(Point p){
		fleePoints.add(p);
	}
	

	public List<Point> getFleePoints(){
		return fleePoints;
	}
	
	public void setAbilityInterval(long interval){
		this.abilityInterval = interval;
	}
	
	public long getAbilityInterval(){
		return abilityInterval;
	}
	
	public void setMobTag(int tag){
		this.mobTag = tag;
	}
	
	public int getMobTag(){
		return mobTag;
	}
	
	public void setWeapon(int weapon){
		this.weapon = weapon;
	}
	
	public int getWeapon(){
		return weapon;
	}
	
	public void setIgnoreChaseDistance(boolean ignore){
		this.ignoreChaseDistance = ignore;
	}
	
	public boolean getIgnoreChaseDistance(){
		return ignoreChaseDistance;
	}
	
	
	
	

	public String toString() {
		return "[MobBehavior: type="+type+" fleeType="+fleeType+" fleePoint="+fleePoint+" abilityInterval="+abilityInterval+" mobTag="+mobTag+" ignoreChaseDistance="+ignoreChaseDistance+" abilities="+abilities+" startAbilities="+startAbilities+" endAbilities="+endAbilities+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
