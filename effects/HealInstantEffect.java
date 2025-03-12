package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.CombatHelper;
import atavism.agis.util.EventMessageHelper;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.agis.core.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that restores health instantly.
 * @author Andrew Harrison
 *
 */
public class HealInstantEffect extends AgisEffect {
	
	static Random random = new Random();

    public HealInstantEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealInstantEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("HealInstantEffect: this effect is not for buildings");
			return;
		}	
		Log.debug("HealInstantEffect.apply effectSkillType:"+effectSkillType+" skillType:"+params.get("skillType"));
      //  effectSkillType = params.get("skillType");
        
        String abilityEvent = EventMessageHelper.COMBAT_HEAL;
		String dmgType =  (String)params.get("dmgType");
		  
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
	
        heal = CombatHelper.CalcHeal(state.getTarget(), state.getSource(), heal, skillEffectMod.get(0), effectSkillType,dmgType,params);
        int result = (int)params.get("result");
    	if(result == AgisAbility.RESULT_CRITICAL)
    		abilityEvent = EventMessageHelper.COMBAT_HEAL_CRITICAL;
        Set<EffectState> effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
		if (state.getSource() != null) {
			Set<EffectState> ceffects = state.getSource().getCurrentEffects() != null ? new HashSet<EffectState>(state.getSource().getCurrentEffects()) : new HashSet<EffectState>();

			// Triggers Calculations
			Log.debug("HealInstantEffect.apply: Check Triggetson caster " + ceffects.size());

			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
				}
			}
		}
		Log.debug("HealInstantEffect.apply: Check Triggetson target "+effects.size()+" C="+state.getSourceOid()+" T="+state.getTargetOid()+" ==?"+(state.getSourceOid().equals(state.getTargetOid())));
		if (!state.getSourceOid().equals(state.getTargetOid())) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					Log.debug("HealInstantEffect.apply: TriggerEffect");
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					Log.debug("HealInstantEffect.apply: TriggerEffect " + se);
					heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
					Log.debug("HealInstantEffect.apply: TriggerEffect END");
				}
			}
		}
		// Ability Triggers Calculations
		if(state.getAbilityID() > 0) {
			AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
			if(aa!=null) {
				long time = (long) params.get("powerUp");
				heal = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(),aa);
			}
		}
		CombatClient.sendCombatEvent(state.getSourceOid(), false,TriggerProfile.Type.HEAL);
		CombatClient.sendCombatEvent(state.getTargetOid(), true,TriggerProfile.Type.HEAL);
		Log.debug("HealInstantEffect.apply: Check Triggetson END heal="+heal);
        if (heal == 0) {
		    return;
		}

        CombatInfo target = state.getTarget();
        CombatInfo caster = state.getSource();
    	
        target.statModifyBaseValue(getHealProperty(), heal);
        target.sendStatusUpdate();
        
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), (heal), -1,getHealProperty(),"");
        
        // If there is a pulse coord effect, run it now
        if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
        	CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
    	    cE.sendSourceOid(true);
    	    cE.sendTargetOid(true);
			state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), target.getOwnerOid()));
        }
        Log.debug("HealInstantEffect.apply End");
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
		super.pulse(state);
		Log.debug("HealInstantEffect.pulse");
		Map<String, Serializable> params = state.getParams();

		String abilityEvent = EventMessageHelper.COMBAT_HEAL;
		
        int heal = minPulseHeal;
        if (maxPulseHeal > minPulseHeal) {
            heal += random.nextInt(maxPulseHeal - minPulseHeal);
        }
        
		if (heal == 0) {
		    return;
		}
		
        CombatInfo target = state.getTarget();
        CombatInfo caster = state.getSource();
      	double healDealtMod = 1;
      	if (caster != null) {
    		if(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT!=null) {
    			double statValue = caster.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healDealtMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
    			double calculated = 0;
    			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT)) {
    				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
    				double pointsCalculated = 0;
    				for (int i = 1; i <= def.getPoints().size(); i++) {
    					if (Log.loggingDebug)
    						Log.debug("HealInstantEffect: healDealtMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
    					if (statValue <= def.getThresholds().get(i)) {
    						Log.debug("HealInstantEffect: healDealtMod statValue < th");
    						if (statValue - pointsCalculated < 0)
    							break;
    						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
    						pointsCalculated += statValue - pointsCalculated;
    					} else {
    						Log.debug("HealInstantEffect: healDealtMod statValue > th");
    						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
    						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
    					}
    				}
    				/*if (pointsCalculated < statValue) {
    					calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
    				}*/
    			} else {
    				calculated = statValue;
    			}
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healDealtMod calculated: " + calculated);	
    			healDealtMod = Math.round(calculated/100f);
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healDealtMod calculated="+calculated+" mod="+healDealtMod);
    		}
    		
      	}
    		double healReciveMod = 1;
    		if(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT!=null) {
    			double statValue = target.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healReciveMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
    			double calculated = 0;
    			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT)) {
    				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
    				double pointsCalculated = 0;
    				for (int i = 1; i <= def.getPoints().size(); i++) {
    					if (Log.loggingDebug)
    						Log.debug("HealInstantEffect: healReciveMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
    					if (statValue <= def.getThresholds().get(i)) {
    						Log.debug("HealInstantEffect: healReciveMod statValue < th");
    						if (statValue - pointsCalculated < 0)
    							break;
    						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
    						pointsCalculated += statValue - pointsCalculated;
    					} else {
    						Log.debug("HealInstantEffect: healReciveMod statValue > th");
    						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
    						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
    					}
    				}
    				/*if (pointsCalculated < statValue) {
    					calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
    				}*/
    			} else {
    				calculated = statValue;
    			}
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healReciveMod calculated: " + calculated);	
    			healReciveMod = Math.round(calculated/100f);
    			if (Log.loggingDebug)
    				Log.debug("HealInstantEffect: healReciveMod calculated="+calculated+" mod="+healReciveMod);
    		}
    		
		heal = (int) Math.round(heal * healDealtMod * healReciveMod);
		Set<EffectState> effects = state.getTarget().getCurrentEffects();
		if (caster != null) {
			Set<EffectState> ceffects = state.getSource().getCurrentEffects();
		
		// Triggers Calculations
		Log.debug("HealInstantEffect.pulse: Check Triggetson caster");
		for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
			}
		}
		}
		Log.debug("HealInstantEffect.pulse: Check Triggetson target");
		for (EffectState ef : effects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
			}
		}
		// Ability Triggers Calculations
		if(state.getAbilityID() > 0) {
			AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
			if(aa!=null) {
				long time = (long) params.get("powerUp");
				heal = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(),aa);
			}


		}
		CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.HEAL);
		CombatClient.sendCombatEvent(state.getTargetOid(),true, TriggerProfile.Type.HEAL);
		target.statModifyBaseValue(getHealProperty(), heal);
        target.sendStatusUpdate();
        
        EventMessageHelper.SendCombatEvent( state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), (heal), -1,getHealProperty(),"");
        ApplyHealThreat(caster,target,heal);
    	Log.debug("HealInstantEffect.pulse End");
    	
    }
    
    
    ArrayList<OID> targetsToCheck = new ArrayList<OID>();
    private int areaRadiusCheck = 7; // defines radius around healing target so mobs can be added to threat map
    private float areaAngle = 360; // defines area angle around healing target so mobs can be added to threat map
   
    private void ApplyHealThreat(CombatInfo caster, CombatInfo target, int amount) {
    	if(caster == null)
    		return;
    	// Don't apply threat if its a self heal, or if the caster isn't a player.
    	if (caster.getOid().equals(target.getOid()) || !caster.isUser())
    		return;
    	
		//if (Log.loggingDebug)Log.debug("HealInstantEffect.ApplyHealThreat: amount "+amount);
		
		BasicWorldNode node = WorldManagerClient.getWorldNode(target.getOid());
		if (node != null) {
	    	targetsToCheck.clear(); // less GC than having to declare new ArrayList<> each time we call method.
	    	
	    	// Store all attackable targets into ArrayList<>
			if (target.getAttackableTargets() != null)	
				for (OID t : target.getAttackableTargets().keySet())
					targetsToCheck.add(t);
			
			//if (Log.loggingDebug)Log.debug("HealInstantEffect.ApplyHealThreat: targetsToCheck - "+targetsToCheck.size()+" attackableTargets - "+attackableTargets.size());
	
			if (targetsToCheck.size() > 0) {
				targetsToCheck = AgisWorldManagerClient.checkIfTargetsInArea(caster.getOid(),targetsToCheck, node.getLoc(), areaRadiusCheck, areaAngle, node.getOrientation(), 0);			
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
							if (Log.loggingDebug)Log.debug("HealInstantEffect.ApplyHealThreat: sendAlterThreat to targetInfo="+targetInfo.toString());
						}
					}
	    		}
			}
		}
    }
    
    public int getMinInstantHeal() { return minHeal; }
    public void setMinInstantHeal(int hps) { minHeal = hps; }
    protected int minHeal = 0;

    public int getMaxInstantHeal() { return maxHeal; }
    public void setMaxInstantHeal(int hps) { maxHeal = hps; }
    protected int maxHeal = 0;

    public int getMinPulseHeal() { return minPulseHeal; }
    public void setMinPulseHeal(int hps) { minPulseHeal = hps; }
    protected int minPulseHeal = 0;

    public int getMaxPulseHeal() { return maxPulseHeal; }
    public void setMaxPulseHeal(int hps) { maxPulseHeal = hps; }
    protected int maxPulseHeal = 0;

    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;
    
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
    
    public void setEffectName(String eName) {
    	effectName = eName;
    }
    public String getEffectName() {
	return effectName;
    }
    protected String effectName = "";
    
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
