package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.Point;






public class BehaviorConditionSettings implements Serializable {
	
	/**
	 * MobBehavior Type
	 * 0-Event; 1-Distance; 2-Stat; 3-Effect; 4-CombatState; 5-DeathState; 6-NumberOfTargets
	 */
	public int type=0;
	
	public float distance = 0;
	public boolean less = false;
	public String statName = "";
	public float statValue = 0;
	public boolean statVitalityPercentage = false;
	
	public int target=0;// '0-Caster; 1-Target',
	public int effectTag = -1;
	public boolean onTarget=true;
	public boolean combatState = false;
	public boolean deathState = false;
	public TriggerProfile.Type triggerEvent = TriggerProfile.Type.DAMAGE;//0-Parry; 1-Dodge; 2-Miss; 3-Damage; 4-Heal; 5-Critical; 6-Kill; 7-Stun; 8-Sleep
	
	public int target_number=0;
	public boolean target_ally=false;
	
	
	public BehaviorConditionSettings() {
	}
	
	
	/*public void setType(int type){
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
	
	public Point setFleePoint(){
		return fleePoint;
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
	
	public void setIgnoreChaseDistance(boolean ignore){
		this.ignoreChaseDistance = ignore;
	}
	
	public boolean getIgnoreChaseDistance(){
		return ignoreChaseDistance;
	}
	*/
	
	

	public String toString() {
		return "[BehaviorConditionSettings: type="+type+" distance="+distance+" less="+less+" statName="+statName+" statValue="+statValue+" statVitalityPercentage="+statVitalityPercentage+
				" target"+target+" effectTag="+effectTag+" onTarget="+onTarget+" combatState="+combatState+" deathState="+deathState+" triggerEvent="+triggerEvent+" target_number="+target_number+" target_ally="+target_ally+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
