package atavism.agis.effects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;

import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.server.util.Log;

/**
 * Effect child class that removes effects from the target.
 * 
 * @author Andrew Harrison
 *
 */
public class DispelEffect extends AgisEffect {

	public DispelEffect(int id, String name) {
		super(id, name);
	}

	// add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("DispelEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("DispelEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		ArrayList<EffectState> targetEffects = new ArrayList<EffectState>(obj.getCurrentEffects());
		Log.debug("REMOVE: removing effects matching type: " + dispelType);

		int effectsRemoved = 0;
		if (dispelType.equals("All")) {
			for (EffectState effect : targetEffects) {
				AgisEffect.removeEffect(effect);
				effectsRemoved++;
				if (effectsRemoved >= effectsToRemove) {
					break;
				}
			}
		} else if (dispelType.equals("By Tags")) {
			for (EffectState effect : targetEffects) {
				if (effectsRemoved >= effectsToRemove) {
					break;
				}
				for (int tag : dispelTags) {
					if (effectsRemoved >= effectsToRemove) {
						break;
					}
					boolean toRemove = false;
					if (effect.getEffect() != null) {
						if (effect.getEffect().getTags().contains(tag)) {
							toRemove = true;
						}
					}
					if (toRemove) {
						AgisEffect.removeEffect(effect);
						effectsRemoved++;
					}
					
				}
			}
		} else if (dispelType.equals("MountEffect")) {
			for (EffectState effect : targetEffects) {
				if (effect.getEffect() instanceof MountEffect) {
					AgisEffect.removeEffect(effect);
					effectsRemoved++;
					if (effectsRemoved >= effectsToRemove) {
						break;
					}
				}
			}
		} else if (dispelType.equals("MorphEffect")) {
			for (EffectState effect : targetEffects) {
				if (effect.getEffect() instanceof MorphEffect) {
					AgisEffect.removeEffect(effect);
					effectsRemoved++;
					if (effectsRemoved >= effectsToRemove) {
						break;
					}
				}
			}
		} else {
			Log.debug("REMOVE: removing effects type: " + dispelType + " cant be removed");

		}
	}

	public String getDispelType() {
		return dispelType;
	}

	public void setDispelType(String dispelType) {
		this.dispelType = dispelType;
	}

	protected String dispelType = null;

	public int getEffectsToRemove() {
		return effectsToRemove;
	}

	public void setEffectsToRemove(int effectsToRemove) {
		this.effectsToRemove = effectsToRemove;
	}

	protected int effectsToRemove = 1;

	public LinkedList<Integer> getDispelTags() {
		return dispelTags;
	}

	public void addDispelTag(int tag) {
		dispelTags.add(tag);
	}

	protected LinkedList<Integer> dispelTags = new LinkedList<Integer>();

	private static final long serialVersionUID = 1L;
}
