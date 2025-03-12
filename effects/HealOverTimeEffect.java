package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that restores health over time.
 * 
 * @author Andrew Harrison
 */
public class HealOverTimeEffect extends AgisEffect {
    static Random random = new Random();

    public HealOverTimeEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Log.debug("HealOverTimeEffect.apply: "+getName());
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealOverTimeEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");if (claimID > 0 && objectID > 0) {
			Log.debug("HealOverTimeEffect: this effect is not for buildings");
			return;
		}	
			
		String dmgType =  (String)params.get("dmgType");
    //	Map<String, Serializable> params = state.getParams();
      //  effectSkillType = params.get("skillType");
        
        String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
        
        CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
  
        //FIXME Stacking
     	// Stacking handling
    	int stackCase = stackCheck();
    	if (stackCase == 0) {
    		// Do not apply this effect
    		return;
    	} 
    	if(target==null) {
    		Log.error("HealOverTimeEffect.apply: target is null "+state);
    		return;
    	}
    		
    	int stackLevel = 1;
    	boolean hasThisEffect = false;
    	boolean fromThisCaster = false;
    	int healFromEffects =0;
    	int healFromEffectsSame =0;
    	// AgisEffect effect = state.getEffect();
    	EffectState similarEffect = null;
    	EffectState sameCasterEffect = null;
    	Set<EffectState> effset = new HashSet<>(target.getCurrentEffects());
    	for (EffectState existingState : effset) {
    		if (existingState.getEffect().getID() == getID() && !state.equals(existingState)) {
    			HealOverTimeEffect ef = (HealOverTimeEffect)existingState.getEffect();
    			healFromEffects +=ef.getPulseHeal();
    			hasThisEffect = true;
    			similarEffect = existingState;
    		    if (source.getOwnerOid().equals(similarEffect.getStackCaster())) {
    		    	healFromEffectsSame +=ef.getPulseHeal();
    		    	fromThisCaster = true;
    		    	sameCasterEffect = similarEffect;
    		    }
    		}
    	}
    	
    	
    	
		int heal = minHeal;
		if (maxHeal > minHeal) {
			heal += random.nextInt(maxHeal - minHeal);
		}

		VitalityStatDef vitalStatDef = (VitalityStatDef) CombatPlugin.lookupStatDef(getHealProperty());
		float percentage = minPercentageHeal;
  		if (maxPercentageHeal > minPercentageHeal) {
  			percentage = minPercentageHeal + random.nextFloat() * (maxPercentageHeal - minPercentageHeal);
  		}
  		heal += state.getTarget().statGetCurrentValue(vitalStatDef.getMaxStat()) * percentage / 100;
  	
  		//  heal *=stackLevel;
        heal = CombatHelper.CalcHeal(target, source, heal, skillEffectMod.get(0), effectSkillType, dmgType,params);
          
          
          // Divide the total damage by the number of pulses
          pulseHeal = heal / this.numPulses;
          Log.debug("HealOverTimeEffect.apply: total heal is: " + heal + " with pulse heal: " + pulseHeal);
      	
          
          
    	Log.debug("HealOverTimeEffect.apply: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);
    	
    	if (stackCase == 1) {
    		// If the target already has the same effect type from the same caster, remove the old one
    		if (fromThisCaster)
    			AgisEffect.removeEffect(sameCasterEffect);
    		healFromEffects=0;
    	} else if (stackCase == 2) {
    		// If the target already has the same effect type from the same caster, increment the stack and heal pulse
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    		//	AgisEffect.removeEffect(sameCasterEffect);
    			if (stackLevel < this.stackLimit) {
        		    stackLevel++;
        		   
    			}
    			sameCasterEffect.setTimeUntilEnd(getDuration());
    			sameCasterEffect.setNextPulse(0);
    			sameCasterEffect.reschedule(getPulseTime());
    			sameCasterEffect.setCurrentStack(stackLevel);
    			sameCasterEffect.getTarget().updateEffectsProperty();
    			 HealOverTimeEffect se = (HealOverTimeEffect) sameCasterEffect.getEffect();
 				se.setPulseHeal(pulseHeal * stackLevel);
				
				state.wasApplied(false);
				return;
    			
    		}
    	} else if (stackCase == 3) {
    		// If the target already has the same effect type, remove the old one
    		if (hasThisEffect)
    			AgisEffect.removeEffect(similarEffect);
			healFromEffects = 0;
		} else if (stackCase == 4) {
			// If the target already has the same effect type, increment the stack and heal
			// pulse
			if (hasThisEffect) {
				stackLevel = similarEffect.getCurrentStack();
				// AgisEffect.removeEffect(similarEffect);
				if (stackLevel < this.stackLimit) {
					stackLevel++;
				}
				similarEffect.setTimeUntilEnd(getDuration());
				similarEffect.setNextPulse(0);
    			similarEffect.reschedule(getPulseTime());
				similarEffect.setCurrentStack(stackLevel);
				similarEffect.getTarget().updateEffectsProperty();
				HealOverTimeEffect se = (HealOverTimeEffect) similarEffect.getEffect();
				se.setPulseHeal(pulseHeal * stackLevel);
				state.wasApplied(false);
				return;

			}
		} else if (stackCase == 5) {
			//Time stacking
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			sameCasterEffect.setCurrentStack(stackLevel);
    			sameCasterEffect.getTarget().updateEffectsProperty();
           		state.wasApplied(false);
    		    return;
    		}
    	} else if (stackCase == 6) {
			//Time stacking
    		if (hasThisEffect) {
    			stackLevel = similarEffect.getCurrentStack();
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			similarEffect.setCurrentStack(stackLevel);
    			similarEffect.getTarget().updateEffectsProperty();
    		    state.wasApplied(false);
    		    return;
    		}
    	}
    	
        
        
      
        pulseHeal = pulseHeal * stackLevel;
        
        EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
        Log.debug("HealOverTimeEffect.apply: total pulse heal: " + pulseHeal+ " from stack");
    	state.setStackCaster(source.getOwnerOid());
    	state.setCurrentStack(stackLevel);
    	Log.debug("HealOverTimeEffect.apply: "+getName()+" END");
        
    }

    // perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		super.pulse(state);
		Log.debug("HealOverTimeEffect.pulse: "+getName()+" stat="+getHealProperty()+" pulseHeal="+pulseHeal);
		String abilityEvent = EventMessageHelper.COMBAT_HEAL;
		Map<String, Serializable> params = state.getParams();
		int result = (int)params.get("result");
    	if(result == AgisAbility.RESULT_CRITICAL)
    		abilityEvent = EventMessageHelper.COMBAT_HEAL_CRITICAL;
		CombatInfo target = state.getTarget();
		CombatInfo source = state.getSource();

		if (pulseHeal > 0) {
			Log.debug("HealOverTimeEffect.PULSE: giving heal: " + pulseHeal);
			int heal = pulseHeal;
			
			Set<EffectState> effects = null;
			if (target != null)
				effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
			Set<EffectState> ceffects = null;
			if (source != null)
				ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
		 
			// Triggers Calculations
			Log.debug("HealOverTimeEffect.pulse: Check Triggetson caster "+(ceffects!=null?ceffects.size():"BD"));
			
			if(ceffects!=null)
				for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
				}
			}
			Log.debug("HealOverTimeEffect.pulse: Check Triggetson target "+(effects!=null?effects.size():"BD")+" C="+state.getSourceOid()+" T="+state.getTargetOid()+" ==?"+(state.getSourceOid().equals(state.getTargetOid())));
			if (!state.getSourceOid().equals(state.getTargetOid())) {
				if (effects != null)
					for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						Log.debug("HealOverTimeEffect.pulse: TriggerEffect");
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						Log.debug("HealOverTimeEffect.pulse: TriggerEffect " + se);
						heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
						Log.debug("HealOverTimeEffect.pulse: TriggerEffect END");
					}
				}
			}
			// Ability Triggers Calculations
			if(state.getAbilityID() > 0) {
				AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
				if(aa!=null) {
					long time = (long) params.get("powerUp");
					heal = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.DAMAGE, tags, heal, source, target, aa);
				}
			}
			CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.HEAL);
			CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.HEAL);
			Log.debug("HealOverTimeEffect.pulse: Check Triggetson END heal="+heal);
			target.statModifyBaseValue(getHealProperty(), heal);
			target.sendStatusUpdate();
			// Engine.getAgent().sendBroadcast(new
			// CombatClient.DamageMessage(target.getOwnerOid(),
			// source.getOwnerOid(), pulseHeal, this.damageType));
			Log.debug("HealOverTimeEffect.pulse: pulseCoordEffect="+pulseCoordEffect);
			
			// If there is a pulse coord effect, run it now
			if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
				CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
				cE.sendSourceOid(true);
				cE.sendTargetOid(true);
				state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), target.getOwnerOid()));
			}

	        EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1,getHealProperty(),"");
	        ApplyHealThreat(source,target,heal);
	        Log.debug("HealOverTimeEffect.pulse: "+getName()+" END");
			
        }
    }
	

	private int areaRadiusCheck = 7; // defines radius around healing target so mobs can be added to threat map
	private float areaAngle = 360; // defines area angle around healing target so mobs can be added to threat map

	private void ApplyHealThreat(CombatInfo caster, CombatInfo target, int amount) {
		if (caster == null || target == null)
			return;
		// Don't apply threat if its a self heal, or if the caster isn't a player.
		if (caster.getOid().equals(target.getOid()) || !caster.isUser())
			return;

		// if (Log.loggingDebug)Log.debug("HealInstantEffect.ApplyHealThreat: amount
		// "+amount);

		BasicWorldNode node = WorldManagerClient.getWorldNode(target.getOid());
		if (node != null) {
			ArrayList<OID>	targetsToCheck = new ArrayList<OID>(); // less GC than having to declare new ArrayList<> each time we call method.

			// Store all attackable targets into ArrayList<>
			if (target.getAttackableTargets() != null)
				for (OID t : target.getAttackableTargets().keySet())
					targetsToCheck.add(t);

			// if (Log.loggingDebug)Log.debug("HealInstantEffect.ApplyHealThreat:
			// targetsToCheck - "+targetsToCheck.size()+" attackableTargets -
			// "+attackableTargets.size());

			if (targetsToCheck.size() > 0) {
				targetsToCheck = AgisWorldManagerClient.checkIfTargetsInArea(caster.getOid(), targetsToCheck, node.getLoc(), areaRadiusCheck, areaAngle, node.getOrientation(), 0);
				for (OID t : targetsToCheck) {
					CombatInfo targetInfo = CombatPlugin.getCombatInfo(t);
					if (targetInfo == null)
						continue;

					if (targetInfo.dead())
						continue;

					if (!targetInfo.attackable())
						continue;

					// make sure target is not a user and target is engaged in combat
					if (!targetInfo.isUser() && targetInfo.inCombat()) {
						// check if target is engaged with your heal target.
						if (target.getOid().equals(targetInfo.getAutoAttackTarget())) {
							CombatClient.sendAlterThreat(targetInfo.getOid(), caster.getOid(), amount);
							if (Log.loggingDebug)
								Log.debug("HealOverTimeEffect.ApplyHealThreat: sendAlterThreat to targetInfo=" + targetInfo.toString());
						}
					}
				}
			}
		}
	}
    public int getMinHeal() { return minHeal; }
    public void setMinHeal(int hps) { minHeal = hps; }
    protected int minHeal = 0;

    public int getMaxHeal() { return maxHeal; }
    public void setMaxHeal(int hps) { maxHeal = hps; }
    protected int maxHeal = 0;
    
    public int getPulseHeal() { return pulseHeal; }
    public void setPulseHeal(int hps) { pulseHeal = hps; }
    protected int pulseHeal = 0;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;

    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    public float getHealMod() { return HealMod; }
    public void setHealMod(float hps) { HealMod = hps; }
    protected float HealMod = 1.0f;
    
    public float getMinPercentageHeal() { return minPercentageHeal; }
    public void setMinPercentageHeal(float val) { minPercentageHeal = val; }
    protected float minPercentageHeal = 0f;
   
    public float getMaxPercentageHeal() { return maxPercentageHeal; }
    public void setMaxPercentageHeal(float val) { maxPercentageHeal = val; }
    protected float maxPercentageHeal = 0f;

    // Effect Value that needs to be removed upon effect removal
    public void setEffectVal(int effect) {
    	effectVal = effect;
    }
    public int GetEffectVal() {
        return effectVal;
    }
    public int effectVal = 0;
    
    public void setDisplayName(String eName) {
    	displayName = eName;
    }
    public String getDisplayName() {
	return displayName;
    }
    protected String displayName = "";
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    public int effectType = 0;
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
    private static final long serialVersionUID = 1L;
}
