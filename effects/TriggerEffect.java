package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.engine.EnginePlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that alters the value of a stat on the target for a period
 * of time. Can be permanent if the effect is a passive one.
 * 
 * @author Andrew Harrison
 *
 */
public class TriggerEffect extends AgisEffect {
	public TriggerEffect() {

	}

	public TriggerEffect(int id, String name) {
		super(id, name);
		isPeriodic(false);
		isPersistent(true);
	}

	protected Map<String, Float> statMap = new HashMap<String, Float>();

	public void setStat(String stat, float adj) {
		statMap.put(stat, adj);
	}

	public Float getStat(String stat) {
		return statMap.get(stat);
	}

	// add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("TriggerEffect: this effect is not for buildings");
			return;
		}
	
		String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;

		CombatInfo caster = state.getSource();
		CombatInfo target = state.getTarget();

		if (target != null) {
			if (target.dead()) {
				Log.debug("TriggerEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: apply " + getName() + " " + state + " caster=" + caster + " target=" + target + " ");

		Log.debug("TriggerEffect: apply end");
	}

	public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		remove(state, target);
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: removing " + this.getName());
	}

	// remove the effect from the object
	public void remove(EffectState state, CombatInfo target) {

		if (Log.loggingDebug)
			Log.debug("TriggerEffect: removing " + this.getName());
		super.remove(state);
	}

	public void unload(EffectState state, CombatInfo target) {
		remove(state, target);
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: unload " + this.getName());
	}

	// perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		super.pulse(state);
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: pulse " + this.getName());
		/*
		 * if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
		 * CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
		 * cE.sendSourceOid(true); cE.sendTargetOid(true);
		 * cE.invoke(state.getTarget().getOwnerOid(), state.getTarget().getOwnerOid());
		 * }
		 */
	}

	public void activate(EffectState state) {
		super.activate(state);
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: activate " + this.getName());
	}

	public void deactivate(EffectState state) {
		super.deactivate(state);
		if (Log.loggingDebug)
			Log.debug("TriggerEffect: deactivate " + this.getName());
	}

	public String getPulseCoordEffect() {
		return pulseCoordEffect;
	}

	public void setPulseCoordEffect(String coordEffect) {
		pulseCoordEffect = coordEffect;
	}

	protected String pulseCoordEffect;

	public int Calculate(TriggerProfile.Type eventType, ArrayList<Integer> tag, int value, CombatInfo caster, CombatInfo target, EffectState state) {
		if(Log.loggingDebug)
			Log.debug("TriggerEffect.Calculate " + state + " eventType=" + eventType + " tags=" + tags + " value=" + value + " caster=" + caster + " target=" + target+" profiles="+profiles.size());
		if(Log.loggingDebug)
		Log.debug("TriggerEffect.Calculate "+caster.getProperty("race")+" "+caster.getProperty("aspect"));
		int race = 0;
		if(caster.getProperty("race") != null) {
			race = caster.getIntProperty("race");
		}else {
			try {
				race = (int)EnginePlugin.getObjectProperty(caster.getOid(), WorldManagerClient.NAMESPACE, "race");
				caster.setProperty("race", race);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		int _class = 0;
		if(caster.getProperty("aspect") != null)
			_class = caster.getIntProperty("aspect");
		if(Log.loggingDebug)
			Log.debug("TriggerEffect.Calculate race="+race+" calss="+_class);
		boolean taken = false;
		if (state.getTargetOid().equals(caster.getOid()))
			taken = true;
		for (TriggerProfile tp : profiles) {
			if(Log.loggingDebug)
				Log.debug("TriggerEffect.Calculate TriggerProfile="+tp);
			//if (taken)
			if (taken)
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.DEALT, value, caster, target);
			else
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);
			
			if (taken && target!=null && caster.getOid().equals(target.getOid()))
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);
			if(Log.loggingDebug)
				Log.debug("TriggerEffect.Calculate TriggerProfile END");
		}
		if(Log.loggingDebug)
			Log.debug("TriggerEffect.Calculate END value="+value);
		return value;
	}

	ArrayList<TriggerProfile> profiles = new ArrayList<TriggerProfile>();

	public void addProfile(TriggerProfile tp) {
		profiles.add(tp);
	}

	private static final long serialVersionUID = 1L;
}