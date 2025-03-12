package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.Point;


public class MobAbility implements Serializable {
	
	/**
	 * MobBehavior Type
	 * 0-Melee; 1-Ranged Offensive; 2-Ranged Defensive; 3-Defend; 4-Flee; 5-Heal
	 */
	public int id =-1;
	public String abilities ="";
	public float minAbilityRangePercentage=0f;
	public float maxAbilityRangePercentage=0f;
	public List<BehaviorConditionGroupSettings> conditionsGroup = new ArrayList<BehaviorConditionGroupSettings>();
	
	public MobAbility() {
	}
	
	

	public String toString() {
		return "[MobAbility: id="+id+" abilities="+abilities+" minAbilityRangePercentage="+minAbilityRangePercentage+" maxAbilityRangePercentage="+maxAbilityRangePercentage+" conditionsGroup="+conditionsGroup+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
