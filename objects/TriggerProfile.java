package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import atavism.server.util.Log;

public class TriggerProfile implements Serializable {
	public enum Type {
		PARRY, DODGE, MISS, DAMAGE, HEAL, CRITICAL, KILL, STUN, SLEEP, EVADE
	}

	public enum ActionType {
		DEALT, RECEIVED
	}

	protected int id;
	protected String name;
	protected Type type;
	protected int _race = 0;
	protected int _class = 0;
	protected ActionType action_type;
	protected float chance_min = 0f;
	protected float chance_max = 100f;

	ArrayList<Integer> tags = new ArrayList<Integer>();
	ArrayList<TriggerAction> actions = new ArrayList<TriggerAction>();

	public TriggerProfile() {
	}

	public TriggerProfile(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public String toString() {
		return "[TriggerProfile: id=" + id + " name=" + name + " type=" + type + " race=" + _race + " class=" + _class + " action_type=" + action_type + " chance_min=" + chance_min + "; chance_max=" + chance_max
				+ " tags=" + tags + " actions=" + actions + "]";
	}

	/**
	 * 
	 * @param eventType
	 * @param race
	 * @param class
	 * @param actionType
	 * @param value
	 * @return
	 */
	public int Execute(Type eventType, ArrayList<Integer> tag, int race, int clas, ActionType actionType, int value, CombatInfo caster, CombatInfo target) {
		if(Log.loggingDebug)
			Log.debug("TriggerProfile: Execute EventTyp=" + eventType + " tags=" + tag + " race=" + race + " class=" + clas + " actionType=" + actionType + " value=" + value + " caster=" + caster + " target=" + target);
		if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute profile " + this);
		if (type != eventType) {
			Log.debug("TriggerProfile: Execute type not same break !!!!");
			return value;
		}
		boolean foundTag = false;
		if (tags.size() > 0) {
			for (int t : tag) {
				if (tags.contains(t))
					foundTag = true;
			}
		} else {
			foundTag = true;
		}
		if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute  tags=" + tag + " profile tags=" + tags + " found=" + foundTag);
		if (!foundTag) {
			Log.debug("TriggerProfile: Execute tags not found break !!!!");
			return value;
		}
		if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute  race=" + race + " profile race=" + _race);

		if (_race > 0 && _race != race) {
			Log.debug("TriggerProfile: Execute race not same break !!!!");
			return value;
		}
		if(Log.loggingDebug)
			Log.debug("TriggerProfile: Execute  class=" + clas + " profile class=" + _class);
		if (_class > 0 && _class != clas) {
			Log.debug("TriggerProfile: Execute class not same break !!!!");
			return value;
		}
		if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute  actionType=" + actionType + " profile actionType=" + action_type);
		if (action_type != actionType) {
			Log.debug("TriggerProfile: Execute action not same break !!!!");
			return value;
		}
		float roll = new Random().nextFloat();
		if(Log.loggingDebug)
			Log.debug("TriggerProfile: Execute  chance_min=" + chance_min + " chance_max=" + chance_max);
		float chance = chance_min;
		if (chance_max > chance_min) {
			chance = chance_min + (chance_max - chance_min) * (new Random().nextFloat());
		}
		if(Log.loggingDebug)
			Log.debug("TriggerProfile: Execute  roll=" + roll + " profile calcuated chance=" + chance/100f);

		if (roll > chance/100F) {
			Log.debug("TriggerProfile: Execute roll to low break !!!!");
			return value;
		}

		if(Log.loggingDebug)
			Log.debug("TriggerProfile: Execute  value=" + value);

		for (TriggerAction ta : actions) {
			value = ta.Execute(value, caster, target, actionType);
			if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute after action value=" + value);
		}
		if(Log.loggingDebug)
				Log.debug("TriggerProfile: Execute  End value=" + value);

		return value;

	}

	public void SetName(String name) {
		this.name = name;
	}

	public String GetName() {
		return name;
	}

	public void SetType(Type type) {
		this.type = type;
	}

	public Type GetType() {
		return type;
	}

	public void SetRace(int race) {
		this._race = race;
	}

	public int GetRace() {
		return _race;
	}

	public void SetClass(int v) {
		this._class = v;
	}

	public int GetClass() {
		return _class;
	}

	public void SetActionType(ActionType v) {
		this.action_type = v;
	}

	public ActionType GetActionType() {
		return action_type;
	}

	public void SetChanceMin(float chance) {
		this.chance_min = chance;
	}

	public float GetChanceMin() {
		return chance_min;
	}

	public void SetChanceMax(float chance) {
		this.chance_max = chance;
	}

	public float GetChanceMax() {
		return chance_max;
	}

	public void SetActions(ArrayList<TriggerAction> actions) {
		this.actions = actions;
	}

	public ArrayList<TriggerAction> GetActions() {
		return actions;
	}

	public ArrayList<Integer> getTags() {
		return tags;
	}

	public void addTag(int val) {
		tags.add(val);
	} /*
		 * Final Static properties
		 */

	private static final long serialVersionUID = 1L;

}
