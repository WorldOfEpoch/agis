package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Random;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.objects.TriggerProfile.ActionType;
import atavism.agis.plugins.CombatClient;
import atavism.server.util.Log;

public class TriggerAction implements Serializable {
	public enum Type {
		ABILITY, EFFECT, MODIFIER
	}

	public enum Target {
		ALL, CASTER, TARGET
	}

	protected Type type;
	protected Target target;
	protected float valuePercentage;
	protected int value;
	protected int ability;
	protected int effect;
	protected float chance_min;
	protected float chance_max;

	public TriggerAction() {
	}

	public TriggerAction(Target target, int ability, int effect, int value, float valuePercentage, float chanceMin, float chanceMax) {

		this.target = target;
		this.ability = ability;
		this.effect = effect;
		this.value = value;
		this.valuePercentage = valuePercentage;
		this.chance_min = chanceMin;
		this.chance_max = chanceMax;
		if (ability > 0) {
			type = Type.ABILITY;
		} else if (effect > 0) {
			type = Type.EFFECT;
		} else {
			type = Type.MODIFIER;
		}
	}

	public int Execute(int value, CombatInfo cicaster, CombatInfo citarget, ActionType actionType) {
		float roll = new Random().nextFloat();
		float chance = chance_min;
		if (Log.loggingDebug)
			Log.debug("TriggerAction: Execute  chance_min=" + chance_min + " chance_max=" + chance_max+" actionType="+actionType);
		if (chance_max > chance_min) {
			chance = chance_min + (chance_max - chance_min) * (new Random().nextFloat());
		}
		// if(Log.loggingDebug)
		if (Log.loggingDebug)
			Log.debug("TriggerAction: Execute roll=" + roll + " chance=" + chance / 100f);
		if (roll > chance / 100f) {
			Log.debug("TriggerAction: Execute break !!!!");
			return value;
		}
		if (Log.loggingDebug)
			Log.debug("TriggerAction: Execute type=" + type);

		switch (type) {
		case ABILITY:
			if (Log.loggingDebug)
				Log.debug("TriggerAction: Execute Ability id=" + this.ability + " target=" + target);
			if (target.equals(Target.CASTER) || target.equals(Target.ALL))
				CombatClient.startAbility(ability, cicaster.getOid(), cicaster.getOid(), null, null);
			if ((target.equals(Target.TARGET) && citarget != null) || (target.equals(Target.ALL) && citarget != null && !cicaster.getOid().equals(citarget.getOid())))
				CombatClient.startAbility(ability, cicaster.getOid(), citarget.getOid(), null, null);
			break;
		case EFFECT:

			AgisEffect effect = Agis.EffectManager.get(this.effect);
			if (Log.loggingDebug)
				Log.debug("TriggerAction: Execute Effect id=" + this.effect + " effect=" + effect + " target=" + target);

			HashMap<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("skillType", -1);
			params.put("hitRoll", 100);
			params.put("result", AgisAbility.RESULT_HIT);
			params.put("dmgType", "");
			if (target.equals(Target.CASTER) || target.equals(Target.ALL)) {
			
				AgisEffect.applyEffect(effect, cicaster, cicaster, -1, params);
			}
			if ((target.equals(Target.TARGET) && citarget != null)||  (target.equals(Target.ALL) && citarget != null && !cicaster.getOid().equals(citarget.getOid()))) {
				AgisEffect.applyEffect(effect, cicaster, citarget, -1, params);
			}
			// CombatClient.applyEffect(task.playerOid, interactionID);
			break;
		case MODIFIER:
			if (Log.loggingDebug)
				Log.debug("TriggerAction: Execute mod value=" + this.value + " valuePercentage=" + valuePercentage);
			value = value + this.value + Math.round(value * valuePercentage / 100F);
			break;

		}
		if (Log.loggingDebug)
			Log.debug("TriggerAction: Execute END value="+value);
		return value;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public void setTarget(Target target) {
		this.target = target;
	}

	public Target getTarget() {
		return target;
	}

	public void SetValue(int value) {
		this.value = value;
	}

	public int GetValue() {
		return value;
	}

	public void SetValuePercentage(float valuePercentage) {
		this.valuePercentage = valuePercentage;
	}

	public float GetValuePercentage() {
		return valuePercentage;
	}

	public String toString() {
		return "[TriggerAction: type=" + type + " target=" + target + " value=" + value + "; valuePercentage=" + valuePercentage + " effect="+effect+" ability="+ability+" chance_min="+chance_min+" chance_max="+chance_max+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
