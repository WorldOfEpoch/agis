package atavism.agis.objects;

import atavism.agis.core.AgisAbility;
import atavism.server.engine.EnginePlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.*;

public class AbilityPowerUpData implements Serializable {

	public AbilityPowerUpData() {
	}

	public String toString() {
		return "[AbilityPowerUpData: id=:"+id+"; effects="+effects+"; abilities="+abilities+"; triggers="+triggers+"; coordEffectMap="+coordEffectMap+"]";
	}


	public int getID(){
		return id;
	}
	public void setID(int id){
		this.id = id;
	}

	protected int id = -1;

	/** Add Effect definition
	 * @param aed
	 */
	public void addEffectDefinition(AbilityEffectDefinition aed) {
		effects.add(aed);
	}

	/** Get Effect definition
	 * @return
	 */
	public ArrayList<AbilityEffectDefinition> getEffectDefinition() {
		return effects;
	}

	/**
	 * Stored list of effect definitions
	 */
	protected ArrayList<AbilityEffectDefinition> effects = new ArrayList<AbilityEffectDefinition>();

	/** Add Ability Definition
	 * @param aed
	 */
	public void addAbilityDefinition(AbilityAbilitiesDefinition aed) {
		abilities.add(aed);
	}

	/** Get Ability definition
	 * @return
	 */
	public ArrayList<AbilityAbilitiesDefinition> getAbilityDefinition() {
		return abilities;
	}

	/**
	 *
	 */
	protected ArrayList<AbilityAbilitiesDefinition> abilities = new ArrayList<AbilityAbilitiesDefinition>();


	public int CalculateTriggers(TriggerProfile.Type eventType, ArrayList<Integer> tag, int value, CombatInfo caster, CombatInfo target, AgisAbility ability ) {
		if(Log.loggingDebug)
			Log.debug("AgisAbility.CalculateTriggers  Ability "+ability.getID()+" "+ability.getName()+" eventType=" + eventType + " tags=" + ability.getTags() + " value=" + value + " caster=" + caster + " target=" + target+" triggerProfiles="+triggerProfiles.size());
		if(Log.loggingDebug)
			Log.debug("AgisAbility.CalculateTriggers "+caster.getProperty("race")+" "+caster.getProperty("aspect"));
		int race = 0;
		if(caster.getProperty("race") != null) {
			race = caster.getIntProperty("race");
		}else {
			try {
				race = (int) EnginePlugin.getObjectProperty(caster.getOid(), WorldManagerClient.NAMESPACE, "race");
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
			Log.debug("AgisAbility.CalculateTriggers race="+race+" calss="+_class);
		boolean dealt = true;
//        if (state.getTargetOid().equals(caster.getOid()))
//            dealt = true;
		for (TriggerProfile tp : triggerProfiles) {
			if(Log.loggingDebug)
				Log.debug("AgisAbility.CalculateTriggers TriggerProfile="+tp);
			//if (taken)
			if (dealt)
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.DEALT, value, caster, target);
			else
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);

			if (dealt && target!=null && caster.getOid().equals(target.getOid()))
				value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);
			if(Log.loggingDebug)
				Log.debug("AgisAbility.CalculateTriggers TriggerProfile END");
		}
		if(Log.loggingDebug)
			Log.debug("AgisAbility.CalculateTriggers END value="+value);
		return value;
	}

	ArrayList<TriggerProfile> triggerProfiles = new ArrayList<TriggerProfile>();

	public void addTriggerProfile(TriggerProfile tp) {
		triggerProfiles.add(tp);
	}

	protected ArrayList<Integer> triggers = new ArrayList<Integer>();

	public boolean addCoordEffect(AgisAbility.ActivationState state, CoordinatedEffect effect) {
		Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
		if (effectSet == null) {
			effectSet = new HashSet<CoordinatedEffect>();
			coordEffectMap.put(state, effectSet);
		}
		return effectSet.add(effect);
	}

	public boolean removeCoordEffect(AgisAbility.ActivationState state, CoordinatedEffect effect) {
		Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
		if (effectSet == null) {
			return false;
		}
		return effectSet.remove(effect);
	}

	public Collection<CoordinatedEffect> getCoordEffects(AgisAbility.ActivationState state) {
		Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
		if (effectSet == null) {
			effectSet = new HashSet<CoordinatedEffect>();
			coordEffectMap.put(state, effectSet);
		}
		return effectSet;
	}

	    public void changeCoordinatedEffect(String result) {
    	Set<CoordinatedEffect> effectSet = coordEffectMap.get(AgisAbility.ActivationState.COMPLETED);
    	if (effectSet == null)
    		return;
	    Iterator<CoordinatedEffect> iter = effectSet.iterator();
	    while (iter.hasNext()) {
	    	CoordinatedEffect effect = iter.next();
	    	String argument = (String) effect.getArgument("result");
	    	if (argument != null)
	    		effect.putArgument("result", result);
	    }
    }
	protected Map<AgisAbility.ActivationState, Set<CoordinatedEffect>> coordEffectMap =	new HashMap<AgisAbility.ActivationState, Set<CoordinatedEffect>>();

	public Long getThresholdMaxTime(){
		return thresholdMaxTime;
	}
	public void setThresholdMaxTime(Long time){
		thresholdMaxTime = time;
	}

	protected Long thresholdMaxTime = 0L;


	private static final long serialVersionUID = 1L;
}
 