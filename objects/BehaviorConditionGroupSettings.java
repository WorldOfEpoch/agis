package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.Point;





public class BehaviorConditionGroupSettings implements Serializable {
	
	public List<BehaviorConditionSettings> conditions = new ArrayList<BehaviorConditionSettings>();
	
	public BehaviorConditionGroupSettings() {
	}
	

	public String toString() {
		return "[BehaviorConditionGroupSettings: conditions="+conditions+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
