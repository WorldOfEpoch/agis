package atavism.agis.core;

import atavism.agis.effects.StealthEffect;
import atavism.agis.events.CooldownEvent;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.agis.core.AgisAbility.AbilityResult;
import atavism.agis.effects.DamageMitigationEffect;
import atavism.agis.effects.TeachAbilityEffect;
import atavism.agis.objects.*;
import atavism.agis.plugins.ArenaClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.EventMessageHelper;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.stream.Stream;

public class AgisEffect implements Serializable{
	
    public AgisEffect() {
    }

    public AgisEffect(int id, String name) {
    	setID(id);
        setName(name);
    }
    
    /**
     * Variables for effect stacking
     */
	public boolean multipleCopies = false;
	public boolean replacable = true;
	public boolean stackTime = false;
//	public boolean resetTime = false;
	public int stackLimit = 1; // A default value of 1 means it cannot be stacked
	public ArrayList<Integer> tags = new ArrayList<Integer>();

	public int getStackLimit() {
		return this.stackLimit;
	}

	public void setStackLimit(int stackLimit) {
		this.stackLimit = stackLimit;
	}

	public boolean isStackTime() {
		return stackTime;
	}
	public void setStackTime(boolean stackTime) {
		this.stackTime = stackTime;
	}

	public void setMultipleCopies(boolean multipleCopies) {
		this.multipleCopies = multipleCopies;
	}

	public void setReplacable(boolean replacable) {
		this.replacable = replacable;
	}

	public ArrayList<Integer> getTags() {
		return tags;
	}

	public void addTag(int val) {
		tags.add(val);
	}

	public String toString() {
		return "[AgisEffect: " + getID() + ":" + getName() + " tags=" + tags + " ]";
	}

	  public boolean equals(Object other) {
        AgisEffect otherEffect = (AgisEffect) other;
        boolean val = getName().equals(otherEffect.getName());
        return val;
    }

    public int hashCode() {
        int hash = getName().hashCode();
        return hash;
    }
    
    /**
     * the name is used to refer to the effect, so use a unique name
     */
    public void setID(int id) { this.id = id; }
    public int getID() { return id; }
    int id = -1;
    
    /**
     * the name is used to refer to the effect, so use a unique name
     */
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }
    String name = null;

    // add the effect to the object
    public void apply(EffectState state) {
        if (Log.loggingDebug)
			Log.debug("AgisEffect.apply: applying effect " + state.getEffectName() + " to " + state.getSource());
  
        // There is no point running the rest of the code if there is no effectVal
        /*if (state.getParams() == null) {
        	return;
        }*/
        
        // If this effect uses any effects to activate another effect
    	if (bonusEffect != -1) {
    		CombatInfo target = state.getTarget();
            CombatInfo caster = state.getSource();
    		Log.debug("EFFECT: going to apply bonus effect: " + bonusEffect + " with req: " + bonusEffectReq);
    		boolean applyBonus = false;
    		if (bonusEffectReq == -1) {
    			applyBonus = true;
    		} else {
				if (target != null) {
					for (EffectState existingState : target.getCurrentEffects()) {
						if (bonusEffectReq == existingState.getEffect().getID()) {
							applyBonus = true;
						}
					}
				}
    		}
		    if (applyBonus) {
		    	if (bonusEffectReq != -1 && bonusEffectReqConsumed) {
		    		if (target != null) AgisEffect.removeEffectByID(target, bonusEffectReq);
    		    	Log.debug("BONUS: removed effect position: " + applyBonus);
		    	}
		    	AgisEffect extraEffect = Agis.EffectManager.get(bonusEffect);
		    	state.setBonusEffect(AgisEffect.applyEffect(extraEffect, caster, target, state.getAbilityID(), state.getParams()));
		    	Log.debug("BONUS: applied bonus effect: " + bonusEffect + " to obj: " + (target != null ? target.getName() : "NA"));
		    }
    	}
    }
    
    public void unload(EffectState state, CombatInfo target) {
    	if (Log.loggingDebug)
			Log.debug("AgisEffect.unload: unloading effect " + state.getEffectName() + " from " + state.getTargetOid());
	}

    // remove the effect from the object
	public void remove(EffectState state) {
		if (Log.loggingDebug)
			Log.debug("AgisEffect.remove: removing effect " + state.getEffectName() + " from " + state.getTargetOid());
		for (CoordinatedEffectState ceState: state.getCoordinatedEffectStates()) {
			if(ceState != null)
				ceState.invokeCancel();
		}
		state.getCoordinatedEffectStates().clear();
		if (removeBonusWhenEffectRemoved && state.getBonusEffect() != null) {
			AgisEffect.removeEffect(state.getBonusEffect());
		}
	}

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
        if (Log.loggingDebug)
            Log.debug("AgisEffect.pulse: pulsing effect " + state.getEffectName() + " on " + state.getSource());
	}

	public void stealthReduce(CombatInfo ci , int abilityID){
		if (getStealthReduce()) {
			ArrayList<EffectState> targetEffects = new ArrayList<EffectState>(ci.getCurrentEffects());
			for (EffectState effect : targetEffects) {
				if (effect.getEffect() instanceof StealthEffect) {
					AgisEffect.removeEffect(effect);
				}
			}
			if (CombatPlugin.STEALTH_STAT != null) {
				//int val = source.statGetCurrentValue(CombatPlugin.STEALTH_STAT);
				AgisAbility ability = Agis.AbilityManager.get(abilityID);
				if(ability.getStealthReductionAmount() != 0) {
					ci.statAddModifier(CombatPlugin.STEALTH_STAT, "Ability", -ability.getStealthReductionAmount(), false);
				} else if(CombatPlugin.USE_ABILITY_STEALTH_REDUCTION != 0)
					ci.statAddModifier(CombatPlugin.STEALTH_STAT, "Ability", -CombatPlugin.USE_ABILITY_STEALTH_REDUCTION, false);
				if(ability.getStealthReductionPercentage() != 0f) {
					ci.statAddPercentModifier(CombatPlugin.STEALTH_STAT, "Ability", -ability.getStealthReductionPercentage(), false);
				} else if(CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE != 0f)
					ci.statAddPercentModifier(CombatPlugin.STEALTH_STAT, "Ability", -CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE, false);
				if(ability.getStealthReductionPercentage() != 0f || ability.getStealthReductionAmount() != 0 ||
						(ability.getStealthReductionPercentage() == 0f && CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE != 0f) ||
						(ability.getStealthReductionAmount() == 0 && CombatPlugin.USE_ABILITY_STEALTH_REDUCTION != 0 )  ) {
					ci.statSendUpdate(false);
					CombatPlugin.RunStealthReduceTimeOut(ci.getOid(), ability);
				}
			}
		}
	}

    
    /**
     * Used for passive effects to activate the "effects" of the effect 
     */
    public void activate(EffectState state) {
    	state.isActive(true);
    }
    
    /**
     * Used for passive effects to deactive the "effects" of the effect
     */
    public void deactivate(EffectState state) {
    	state.isActive(false);
    }
    
    /**
     * This function checks to see what stacking behaviour this effect has, and returns an existing
     * EffectState if there is one to be over-written.
     * Return value meanings:
     * 0: Do not apply this new effect
     * 1: Apply this new effect and remove and existing one by the same player
     * 2: Apply and increment the stack but remove the one that belongs to this player
     * 3: Apply and remove the existing one
     * 4: Apply and increment the stack while removing the existing one
     * @return an Integer referring to what should be done.
     */
	public int stackCheck() {
		// If an effect isn't replaceable there is no point checking anything else
		if (this.replacable == false)
			return 0;
		if (this.multipleCopies == true) {
			if (this.stackLimit == 1) {
				return 1;
			} else {
				if (this.stackTime)
					return 5;
				else
					return 2;
			}
		} else {
			// There is only 1 copy of the effect allowed on the target
			if (this.stackLimit == 1) {
				return 3;
			} else {
				if (this.stackTime)
					return 6;
				else
					return 4;
			}
		}
	}
    
    protected int checkDamageMitigation(EffectState state, int damageAmount) {
    	CombatInfo target = state.getTarget();
    	DamageMitigationEffect dEffect = null;
    	for (EffectState existingState : target.getCurrentEffects()) {
		    if (existingState.getEffect().getClass().equals(DamageMitigationEffect.class)) {
		    	dEffect = (DamageMitigationEffect) existingState.getEffect();
			    break;
	    	}
	    }
    	if (dEffect != null) {
    		damageAmount = dEffect.mitigateDamage(damageAmount);
    		if (dEffect.isEffectCompleted())
    			AgisEffect.removeEffectByID(target, dEffect.getID());
    	}
    	return damageAmount;
    }
    
    public String getDamageType() { return damageType; }
    public void setDamageType(String type) { damageType = type; }
    protected String damageType = "";
    
    public int getEffectSkillType() { return effectSkillType; }
    public void setEffectSkillType(int type) { effectSkillType = type; }
    public int effectSkillType = 0;
    
    public List<Float> getSkillEffectMod() { return skillEffectMod; }
    public void setSkillEffectMod(float mod) { skillEffectMod.add(mod);}
    protected List<Float> skillEffectMod = new ArrayList<Float>();
    
    public float getSkillDurationMod() { return skillDurationMod; }
    public void setSkillDurationMod(float mod) { skillDurationMod = mod; }
    protected float skillDurationMod = 0.0f;

    public long getDuration() { return duration; }
    public void setDuration(long dur) { duration = dur; }
    protected long duration = 0;

    public boolean getDOT() { return dot; }
    public void setDOT(boolean d) { dot = d; }
    protected boolean dot = false;

    public int getNumPulses() { return numPulses; }
    public  void setNumPulses(int num) { this.numPulses = num; }
    protected int numPulses = 0;
    public long getPulseTime() { return (numPulses > 0) ? (numPulses > 1 && dot) ? (duration/(numPulses-1)):(duration/(numPulses)) : 0; }
    
    public void setBonusEffectReq(int effectNum) { bonusEffectReq = effectNum;}
    public int getBonusEffectReq() { return bonusEffectReq;}
    protected int bonusEffectReq = -1;
    
    public void setBonusEffectReqConsumed(boolean consumed) { bonusEffectReqConsumed = consumed;}
    public boolean getBonusEffectReqConsumed() { return bonusEffectReqConsumed;}
    protected boolean bonusEffectReqConsumed = false;
    
    public void setBonusEffect(int bonusEffect) { this.bonusEffect = bonusEffect;}
    public int getBonusEffect() { return bonusEffect;}
    protected int bonusEffect = -1;
    
    public void removeBonusWhenEffectRemoved(boolean removeBonus) { removeBonusWhenEffectRemoved = removeBonus;}
    public boolean removeBonusWhenEffectRemoved() { return removeBonusWhenEffectRemoved;}
    protected boolean removeBonusWhenEffectRemoved = false;
    
    public void isBuff(boolean isBuff) { this.isBuff = isBuff;}
    public boolean isBuff() { return isBuff;}
    protected boolean isBuff = false;

    public void setIcon(String icon) { this.icon = icon; }
    public String getIcon() { return (icon == null) ? "UNKNOWN_ICON" : icon; }
    String icon = null;

    public float getChance() {return chance;}
    public void setChance(float chance) {this.chance = chance;}
    protected float chance = 1f;

    public boolean isPeriodic() { return periodic; }
    public void isPeriodic(boolean b) { periodic = b; }
    private boolean periodic = false;

    // Persistent effects are non-instant effects. They will hav a scheduler run when being applied.
    public boolean isPersistent() { return persistent; }
    public void isPersistent(boolean b) { persistent = b; }
    private boolean persistent = false;
    
    // Passive effects are never removed unless the person unlearns the ability
    public boolean isPassive() { return passive; }
    public void isPassive(boolean b) { passive = b; }
    private boolean passive = false;
    
    // Continuous effects don't get removed after login/logout and upon death
    // Usually only stat and property effects should be continuous
    public boolean isContinuous() { return continuous; }
    public void isContinuous(boolean b) { continuous = b; }
    private boolean continuous = false;
    
    public boolean canApplyToImmune() { return applyToImmune; }
    public void canApplyToImmine(boolean canApply) { applyToImmune = canApply; }
    private boolean applyToImmune = false;
    
    public int getDuelEffect() { return duelEffect; }
    public void setDuelEffect(int duelID) { duelEffect = duelID; }
    private int duelEffect = -1;
    
    public float getInterruptionChance() { return interruption_chance; }
    public void setInterruptionChance(float chance) { this.interruption_chance = chance; }
    protected float interruption_chance;
    public float getInterruptionChanceMax() { return interruption_chance_max; }
    public void setInterruptionChanceMax(float chance) { this.interruption_chance_max = chance; }
    protected float interruption_chance_max;
  
    public boolean getForceInterruption() {
    	return forceInterruption;
    }
    public void setForceInterruption(boolean forceInterruption) {
    	this.forceInterruption = forceInterruption;
    }
    protected boolean forceInterruption = true;

	public boolean getStealthReduce() { return stealthReduce;}

	public void setStealthReduce(boolean value) { stealthReduce = value;}

	public boolean stealthReduce = false;
    
    protected boolean checkDuelDefeat(CombatInfo target, CombatInfo caster, String damageProperty) {
    	// Is the target a player and in a duel?
    	if (target.isUser()) {
    		Integer duelID = (Integer) target.getProperty(CombatInfo.COMBAT_PROP_DUEL_ID);
    		if (duelID != null && duelID > -1) {
    			// The target is in a duel
    			if (caster.isUser()) {
    				// Check if the caster is in the same duel as the target
        			Integer duel2ID = (Integer) caster.getProperty(CombatInfo.COMBAT_PROP_DUEL_ID);
        			if (duel2ID != null && duelID == duel2ID) {
            			// Does the damage property stat kill the player if it reaches 0?
            			if (CombatPlugin.lookupStatDef(damageProperty) instanceof VitalityStatDef) {
            				VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.lookupStatDef(damageProperty);
            				if (statDef.getOnMinHit() != null && statDef.getOnMinHit().equals("death")) {
            					// Send a defeat/removal message
            					ArenaClient.duelDefeat(target.getOwnerOid());
            					removeDuelEffects(target, caster);
                            	return true;
            				}
            			}
            		}
        		}
    			ArenaClient.duelDefeat(target.getOwnerOid());
    			removeDuelEffects(target, caster);
    		}
    	}
    	return false;
    }
    
    void removeDuelEffects(CombatInfo target, CombatInfo caster) {
    	// Remove all other effects from the target
		List<EffectState> effectsToRemove = new ArrayList<EffectState>();
		for (EffectState state : target.getCurrentEffects()) {
			if (state.getSource().equals(caster)) {
				effectsToRemove.add(state);
			}
		}
		
		for (EffectState state : effectsToRemove) {
			removeEffect(state, target);
		}
		
		// Remove all other effects from the caster
		effectsToRemove.clear();
		for (EffectState state : caster.getCurrentEffects()) {
			if (state.getSource().equals(target)) {
				effectsToRemove.add(state);
			}
		}
		
		for (EffectState state : effectsToRemove) {
			removeEffect(state, caster);
		}
    }
    
    public LinkedList<EffectState> getTargetEffectsOfMatchingType(CombatInfo target) {
    	LinkedList<EffectState> matchingStates = new LinkedList<EffectState>();
    	if (target != null) {
    		for (EffectState state :target.getCurrentEffects()) {
    			if (state.getEffect().getClass().equals(this.getClass()))
    				matchingStates.add(state);
    		}
    	}
    	return matchingStates;
    }

    protected EffectState generateState(CombatInfo source, CombatInfo target, Map<String, Serializable> params) {
        return new EffectState(this, source, target, params, -1);
    }
    protected EffectState generateState(CombatInfo source, CombatInfo target, Map<String, Serializable> params, int abilityID) {
        return new EffectState(this, source, target, params, abilityID);
    }
    protected EffectState generateState(CombatInfo source, CombatInfo target, Map<String, Serializable> params, int abilityID, AgisItem item) {
        return new EffectState(this, source, target, params, abilityID, item);
    }

    public static EffectState applyEffect(AgisEffect effect, CombatInfo source, CombatInfo target, int abilityID) {
    	Map<String, Serializable> params = new HashMap<String, Serializable>();
    	params.put("result", 1);
		params.put("skillType", -1);
		params.put("hitRoll", 100);
		params.put("dmgType", "");
		params.put("powerUp",0L);
		return applyEffect(effect, source, target, params, abilityID, null, null);
    }

    public static EffectState applyEffect(AgisEffect effect, CombatInfo source, CombatInfo target, int abilityID, Map<String, Serializable> params) {
        return applyEffect(effect, source, target, params, abilityID, null, null);
    }
    
    public static EffectState applyEffect(AgisEffect effect, CombatInfo source, CombatInfo target, int abilityID, Map<String, Serializable> params, AgisItem item) {
        return applyEffect(effect, source, target, params, abilityID, item, null);
    }

	public static EffectState applyEffect(AgisEffect effect, CombatInfo source, CombatInfo target, int abilityID, Map<String, Serializable> params, AgisItem item, long delay) {
		EffectState state = null;
		if(delay>0L) {
			state = effect.generateState(source, target, new HashMap(params), abilityID, item);
			state.setDelayed();
			state.schedule(delay);
		}else{
			state = applyEffect(effect, source, target, params, abilityID, item, null);
		}
		return state;
	}
	/**
	 * Apply Effect on target
	 * 
	 * @param effect
	 * @param source
	 * @param target
	 * @param params
	 * @param abilityID
	 * @param item
	 * @return
	 */
    public static EffectState applyEffect(AgisEffect effect, CombatInfo source, CombatInfo target, Map<String, Serializable> params, int abilityID, AgisItem item, EffectState state) {
    	
		if (effect == null) {
			Log.dumpStack("Effect is null for ability "+abilityID);
			return null;
		}

		if (Log.loggingDebug)
			Log.debug("AgisEffect.applyEffect: applying effect " + effect.getName() + " to " + (target != null ? target.getName() : "NA")+ " params="+params);
		if (params != null) {// check params for null before checking keys
			if (params.containsKey("result")) {
				if (target != null && target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_EVADE)) {
					if (!params.containsKey("sendMsg")) {
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_EVADED, abilityID, effect.id, -1, -1);
						params.put("sendMsg", 1);
					}
					return null;
				}
				if (!effect.canApplyToImmune() && target != null && target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_IMMUNE)) {
					if (!params.containsKey("sendMsg")) {
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target != null ?target.getOwnerOid():null), EventMessageHelper.COMBAT_IMMUNE, abilityID, effect.id, -1, -1);
						params.put("sendMsg", 1);
					}
					return null;
				}

				if (params.get("result").equals(AgisAbility.RESULT_MISSED)) {
					if (Log.loggingDebug)Log.debug("AGISEFFECT: apply effect: " + effect.getName() + " to: " +  (target != null ? target.getName() : "NA") + " MISSED");
					if (!params.containsKey("sendMsg")) {
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target != null ?target.getOwnerOid():null), EventMessageHelper.COMBAT_MISSED, abilityID, effect.id, -1, -1);
						params.put("sendMsg", 1);
					}
					return null;
				}
				if (params.get("result").equals(AgisAbility.RESULT_DODGED)) {
					if (Log.loggingDebug)Log.debug("AGISEFFECT: apply effect: " + effect.getName() + " to: " + (target != null ? target.getName() : "NA") + " DODGED");
					if (!params.containsKey("sendMsg")) {
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target != null ?target.getOwnerOid():null), EventMessageHelper.COMBAT_DODGED, abilityID, effect.id, -1, -1);
						params.put("sendMsg", 1);
					}
					return null;
				}
			}
		}
		
		if (effect.getChance() > 0) {
			Random random = new Random();
			double rand = random.nextDouble();
			if (Log.loggingDebug)
				Log.debug("AGISEFFECT: random value = " + rand + "; hitChance = " + effect.getChance());
			if (rand < (1 - effect.getChance())) {
				if (Log.loggingDebug)
					Log.debug("AGISEFFECT: result skip effect");
				return null;
			}

		}
		
    	List<Lock> requiredLocks = new ArrayList<Lock>();
        if (source != null) {
            requiredLocks.add(source.getLock());
        }
        if (target != null) {
            requiredLocks.add(target.getLock());
        }

        try {
        	if(Log.loggingDebug)	Log.debug("AGISEFFECT: attempting to set locks: " + effect.getName() + " to: " + (target != null ? target.getName() : "NA"));
			   ObjectLockManager.lockAll(requiredLocks);
			   if(Log.loggingDebug)	Log.debug("AGISEFFECT: attempting to apply effect: " + effect.getName() + " to: " +  (target != null ? target.getName() : "NA"));
			if(state == null)
             state = effect.generateState(source, target, new HashMap(params), abilityID, item);

            if (effect.isPeriodic() && !effect.isPersistent()) {
                throw new AORuntimeException("AgisEffect: periodic effects must be persistent");
            }
            long applystart = System.nanoTime();
            effect.apply(state);
            if(Log.loggingDebug)  Log.debug("AgisEffect.applyEffect: applying time "+(System.nanoTime()-applystart)+" ns");
            // Check if the effect wasn't applied for one reason or another
            if (!state.wasApplied()) {
            	return state;
            }
            if(Log.loggingDebug)  Log.debug("AGISEFFECT: isPersistent=" + effect.isPersistent()+" getPulseTime="+effect.getPulseTime()
            +" getDuration="+effect.getDuration()+" isPeriodic="+effect.isPeriodic()+
            " isPassive="+effect.isPassive()+" isContinuous="+effect.isContinuous());
            
            
            if (effect.isPersistent() && effect.getPulseTime()  > 0) {
            	if(target!=null)
            		target.addEffect(state);
                if (effect.isPeriodic()) {
                	if(effect.dot) {
                		effect.pulse(state);
                		state.setNextPulse(0);
                		//if(effect.getNumPulses() > 1)
                			state.schedule(effect.getPulseTime());
                	}else {
                		state.setNextPulse(0);
                		state.schedule(effect.getPulseTime());
                	}
                }
                else 
                {
                    state.schedule(effect.getDuration());
                    if(Log.loggingDebug) Log.debug("AGISEFFECT: effect being applied is persistent but not periodic with a duration of:" + effect.getDuration());
                }
                return state;
            } else if(effect.isPersistent() && effect.getPulseTime()  == 0 && effect.getDuration() > 0){
            	//For State
				if (!effect.isPeriodic()) {
					if(target!=null)
		            	target.addEffect(state);
					state.schedule(effect.getDuration());
					if(Log.loggingDebug)Log.debug("AGISEFFECT: effect being applied is persistent but not periodic with a duration of:" + effect.getDuration());
				}
            } else  if (effect.isPassive() || effect.isContinuous()) {
            	// Save to the characters properties so it can be retrieved later
            	if(target!=null)
                	target.addEffect(state);
            	Log.debug("AGISEFFECT: effect being applied is passive or continuous");
            } 
            //effect.apply(state);
            
            if (!effect.isPersistent() && !effect.isPassive() || !effect.isContinuous()) {
            	// For instant effects, check if there is a pulse time - which means to delay the application slightly
            	if(Log.loggingDebug)Log.debug("PULSE: pulseTime is: " + effect.getPulseTime());
            	if (effect.getPulseTime() > 0) {
            		if(effect.dot)effect.pulse(state);
            		state.setNextPulse(0);
                    state.schedule(effect.getPulseTime());
            	} else {
            		effect.pulse(state);
            	}
            }
            
            return state;
        } catch (Exception e) {
			Log.exception("Exception in Effect "+(effect != null ? effect.id : "NA" ) + " from ability " + abilityID +
					" for source "+(source != null ? source.getOwnerOid() : "NA" ) + " target " + (target != null ? target.getOwnerOid() : "NA"),e);
		}
        finally {
            ObjectLockManager.unlockAll(requiredLocks);
        }
		return null;
    }

    /**
     * Apply Passive Effect 
     * @param effect
     * @param source
     * @param target
     * @param abilityID
     * @param params
     * @return
     */
    public static EffectState applyPassiveEffect(AgisEffect effect, CombatInfo source, CombatInfo target, int abilityID, HashMap<String, Serializable> params) {
    	List<Lock> requiredLocks = new ArrayList<Lock>();
        if (source != null) {
            requiredLocks.add(source.getLock());
        }
        if (target != null) {
            requiredLocks.add(target.getLock());
        }
        
        try {
            ObjectLockManager.lockAll(requiredLocks);
        	//Log.debug("AGISEFFECT: attempting to apply effect: " + effect.getName() + " to: " + obj.getName());
            EffectState state = effect.generateState(source, target, params, abilityID, null);

            if (effect.isPeriodic() && !effect.isPersistent()) {
                throw new AORuntimeException("AgisEffect: periodic effects must be persistent");
            }
            effect.apply(state);
            // Check if the effect wasn't applied for one reason or another
            if (!state.wasApplied()) {
            	return state;
            }
            
            // Check for requirements - and set inactive if not met
            boolean requirementsMet = true;
            AgisAbility ability = Agis.AbilityManager.get(abilityID);
            if (ability.checkEquip(source, source, null) != AbilityResult.SUCCESS) {
            	requirementsMet = false;
            }
            if (!requirementsMet) {
            	// Disable the boost from the passive effect
            	effect.deactivate(state);
            }
            
            // Save to the characters properties so it can be retrieved later
            target.addEffect(state);
            Log.debug("AGISEFFECT: effect being applied is passive or continuous");
            return state;
        }
        finally {
            ObjectLockManager.unlockAll(requiredLocks);
        }
    }
    
    /**
     * Restore effect
     * @param state
     */
    public void resume(EffectState state) {
    	 if (Log.loggingDebug)
             Log.debug("AgisEffect.resume: resume effect " + state.effect.getName() + " to " + state.getTarget().getName()); 
    	 if(state.getTarget()!=null) {
    		 state.getTarget().updateEffectsProperty();
			}
    }
    
    /**
     * Remove  an effect on the target that matches the ID passed in
     * @param target
	 * @param effectID
	 * @return
     */
	public static boolean removeEffectByID(CombatInfo target, int effectID) {
    	return removeEffectByID(target, effectID, null, null);
    }
	
	 /**
     * Removes a effect on the target that matches the ID passed in
     * @param target
	 * @param effectID
	 * @param removeStackAmount
	 * @return
     */
	public static boolean removeEffectByID(CombatInfo target, int effectID, Integer removeStackAmount) {
    	return removeEffectByID(target, effectID, removeStackAmount, null);
    }
    
    /**
     * Removes a effect on the target that matches the ID passed in
     * @param target
	 * @param effectID
	 * @param removeStackAmount
	 * @param params
	 * @return
     */
	public static boolean removeEffectByID(CombatInfo target, int effectID, Integer removeStackAmount, HashMap<String, Serializable> params) {
		Log.debug("AgisEffect.removeEffectByID");
		
		AgisEffect agEffect = Agis.EffectManager.get(effectID);		
		EffectState stateToRemove = null;
		for (EffectState effect : target.getCurrentEffects()) {
			if (effect.effect != null) {
				if (effect.effect.id == effectID) {
					stateToRemove = effect;
					break;
				}
			} else {
				stateToRemove =effect;// broken effect... clean up
			}
		}
    	if (stateToRemove == null) {
    		Log.debug("AgisEffect.removeEffectByID effectID="+effectID+" effect not found on target");
    		return false;
    	}
    	
    	CombatInfo source = stateToRemove.getSource();
        List<Lock> requiredLocks = new ArrayList<Lock>();
        if (source != null) {
            requiredLocks.add(source.getLock());
        }
        if (target != null) {
            requiredLocks.add(target.getLock());
        }
        Log.debug("EFFECT: removing effect with ID: " + stateToRemove.getEffectName());
        int currentStackToReapply = 0;   
        try {
            ObjectLockManager.lockAll(requiredLocks);
            if (stateToRemove.getEffect() == null) {  // broken effect... clean up -- CYC 2009/07/16
            	stateToRemove.isActive(false);
                Engine.getExecutor().remove(stateToRemove);
                if(stateToRemove.getTarget()!=null) {
                      stateToRemove.getTarget().removeEffect(stateToRemove);
                }
                Log.warn("AgisEffect.removeEffect: removing a null effect - effectName=" + stateToRemove.getEffectName());
            } else {
                if (!stateToRemove.getEffect().isPersistent()) {
                    Log.warn("AgisEffect.removeEffect: removing a non-persistent effect: oid=" 
                    		+ stateToRemove.getTargetOid() + " sourceOid=" + stateToRemove.getSourceOid() 
                    		+ " effectName=" + stateToRemove.getEffectName());
                }
                if (removeStackAmount != null && removeStackAmount > 0) {
                	if (stateToRemove.getCurrentStack() >= removeStackAmount) {                		
                		//Remove current effect and re-apply it
                		currentStackToReapply = stateToRemove.getCurrentStack() - removeStackAmount;  
                	}            	
                }
                
                stateToRemove.isActive(false);
                Engine.getExecutor().remove(stateToRemove);
                stateToRemove.getEffect().remove(stateToRemove);
                if(stateToRemove.getTarget()!=null) {
                	stateToRemove.getTarget().removeEffect(stateToRemove);
                }
                
                //Re-apply effect to match new stack
                if (currentStackToReapply > 0) 
                {              
                	if (params == null) {
            			params = new HashMap<String, Serializable>();
            		}
        			params.put("skillType", -1);
        			params.put("hitRoll", 100);
        			params.put("result", AgisAbility.RESULT_HIT);
        			params.put("dmgType", CombatPlugin.DEFAULT_EFFECT_DAMAGE_TYPE);
					params.put("powerUp",0L);
                	for (int i = 0; i < currentStackToReapply; i++) {
                		AgisEffect.applyEffect(agEffect, source, target, -1, params);
                	}
                }
                target.updateEffectsProperty();
            }
        }catch (Exception e) {
        	Log.debug("AgisEffect.removeEffectByID Exception " + e.getMessage() + " " + e.getLocalizedMessage()+" "+e);
        }  
        finally {
            ObjectLockManager.unlockAll(requiredLocks);
        }
     	Log.debug("AgisEffect.removeEffectByID end");
        
        return true;
    }
    
   /**
    * Finds an effect on the target that matches the ID and remove it
    * @param target
    * @param effectID
    * @param all
    * @return
    */
    public static boolean removeAllEffectsByID(CombatInfo target, int effectID, boolean all) {
    	Log.debug("AgisEffect.removeAllEffectsByID");
    	List<EffectState> statesToRemove = new ArrayList<EffectState>();
		for (EffectState effect : target.getCurrentEffects()) {
			if (effect.effect != null) {
				if (effect.effect.id == effectID) {
					statesToRemove.add(effect);
					if (!all)
						break;
				}
			} else {
				statesToRemove.add(effect);// broken effect... clean up 
			}
		}

    	Log.debug("AgisEffect.removeAllEffectsByID effectID="+effectID+" effect found on target "+statesToRemove.size());
		for (EffectState stateToRemove : statesToRemove) {
			
			CombatInfo source = stateToRemove.getSource();
			List<Lock> requiredLocks = new ArrayList<Lock>();
			if (source != null) {
				requiredLocks.add(source.getLock());
			}
			if (target != null) {
				requiredLocks.add(target.getLock());
			}
			Log.debug("removeAllEffectsByID: removing effect with ID: " + stateToRemove.getEffectName());
			try {
				ObjectLockManager.lockAll(requiredLocks);
				if (stateToRemove.getEffect() == null) { // broken effect... clean up -- CYC 2009/07/16
					stateToRemove.isActive(false);
					Engine.getExecutor().remove(stateToRemove);
					if (stateToRemove.getTarget() != null) {
						stateToRemove.getTarget().removeEffect(stateToRemove);
					}
					Log.warn("AgisEffect.removeAllEffectsByID: removing a null effect - effectName=" + stateToRemove.getEffectName());
				} else {
					if (!stateToRemove.getEffect().isPersistent()) {
						Log.warn("AgisEffect.removeAllEffectsByID: removing a non-persistent effect: oid=" + stateToRemove.getTargetOid() + " sourceOid=" + stateToRemove.getSourceOid() + " effectName="
								+ stateToRemove.getEffectName());
					}
					stateToRemove.isActive(false);
					Engine.getExecutor().remove(stateToRemove);
					stateToRemove.getEffect().remove(stateToRemove);
					if (stateToRemove.getTarget() != null) {
						stateToRemove.getTarget().removeEffect(stateToRemove);
					}
				}
			} catch (Exception e) {
				Log.debug("AgisEffect.removeAllEffectsByID Exception " + e.getMessage() + " " + e.getLocalizedMessage() + " " + e);
			} finally {
				ObjectLockManager.unlockAll(requiredLocks);
			}
		}
		Log.debug("AgisEffect.removeAllEffectsByID end");
        
        return true;
    }
    
    /**
     * Remove effect 
     * @param state
     */
    
    public static void removeEffect(AgisEffect.EffectState state) {
    	CombatInfo target = state.getTarget();
    	removeEffect(state, target);
    }
    /**
     * Remove effect on specific target
     * @param state
     * @param target
     */
    public static void removeEffect(AgisEffect.EffectState state, CombatInfo target) {
    	CombatInfo source = state.getSource();
        List<Lock> requiredLocks = new ArrayList<Lock>();
        /*if (source != null) {
            requiredLocks.add(source.getLock());
        }*/
        if (target != null) {
            requiredLocks.add(target.getLock());
        }
        Log.debug("EFFECT: removing effect: " + state.getEffectName());
        try {
            ObjectLockManager.lockAll(requiredLocks);
            if (state.getEffect() == null) {  // broken effect... clean up -- CYC 2009/07/16
                state.isActive(false);
                Engine.getExecutor().remove(state);
                target.removeEffect(state);
                Log.warn("AgisEffect.removeEffect: removing a null effect - effectName=" + state.getEffectName() + "; ID=" + state.getEffectID());
            } else {
                if (!state.getEffect().isPersistent()) {
                    Log.warn("AgisEffect.removeEffect: removing a non-persistent effect: oid=" + state.getTargetOid() + " sourceOid=" + state.getSourceOid() + " effectName=" + state.getEffectName());
                }
                state.isActive(false);
                Engine.getExecutor().remove(state);
                state.getEffect().remove(state);
                Log.debug("EFFECT: about to remove effect from target: " + target);
                target.removeEffect(state);
                Log.debug("EFFECT: removed effect from target");
            }
        }
        finally {
            ObjectLockManager.unlockAll(requiredLocks);
        }
    }
    
    /**
     * Used when a player logs in to ensure all old non-continuous effects are removed.
     * @param target
     */
    public static void removeNonContinuousEffects(CombatInfo target, boolean resume) {
       if(Log.loggingDebug)	Log.debug("AGISEFFECT: removeNonContinuousEffects start "+target.getOid()+" num effects="+target.getCurrentEffects().size());
       	LinkedList<EffectState> effectsToRemove = new LinkedList<EffectState>();
		Set<EffectState> effects = new HashSet<EffectState>(target.getCurrentEffects());
    	for (EffectState state : effects) {
			if(Log.loggingDebug)Log.debug("AGISEFFECT: checking to remove effect: " + state + " it is null? " + state.getEffect()+" resume="+resume);
    		if (resume) {
    			AgisEffect effect = Agis.EffectManager.get(state.getEffectID());
				if(effect==null){
					Log.error("AGISEFFECT: checking to remove effect: " + state.effectName + " it is null? " + state.getEffect()+" resume="+resume +" not found definition remove");
					effectsToRemove.add(state);
				} else {
					if (!effect.isContinuous()) {
						effectsToRemove.add(state);
						if(Log.loggingDebug)Log.debug("AGISEFFECT: adding effect to remove: " + state.effectName);
					} else {
						state.resume();
					}
				}
    		} else if (state.getEffect() == null || !state.effect.isContinuous()) {

				if(Log.loggingDebug)Log.debug("AGISEFFECT: adding effect to remove: " + state.effectName);
    		}
    	}
    	for (EffectState state : effectsToRemove) {
    		CombatInfo source = state.getSource();
            List<Lock> requiredLocks = new ArrayList<Lock>();
            if (source != null) {
                requiredLocks.add(source.getLock());
            }
            if (target != null) {
                requiredLocks.add(target.getLock());
            }
			if(Log.loggingDebug) Log.debug("EFFECT: unloading effect: " + state);
            try {
                ObjectLockManager.lockAll(requiredLocks);
                if (state.getEffect() == null) {  // broken effect... clean up -- CYC 2009/07/16
                	
                    state.isActive(false);
                    Engine.getExecutor().remove(state);
                    target.removeEffect(state);
					AgisEffect effect = Agis.EffectManager.get(state.getEffectID());
					if(effect!=null)
						effect.unload(state, target);
                    Log.warn("AgisEffect.removeNonContinuousEffects: removing a null effect - effectName=" + state.getEffectName() + "; ID=" + state.getEffectID());
                } else {
                    if (!state.getEffect().isPersistent()) {
                        Log.warn("AgisEffect.removeNonContinuousEffects: removing a non-persistent effect: oid=" + state.getTargetOid() + " sourceOid=" + state.getSourceOid() + " effectName=" + state.getEffectName());
                    }
                    //state.getEffect().remove(state);
                    state.isActive(false);
                    Engine.getExecutor().remove(state);
                    state.getEffect().unload(state, target);
                    target.removeEffect(state);
                }
            } catch(Exception e) {
            	Log.exception("AgisEffect.removeNonContinuousEffects Exception ", e);
            }
            finally {
                ObjectLockManager.unlockAll(requiredLocks);
            }
    	}
    	Log.debug("AGISEFFECT: removeNonContinuousEffects End");
    }
    
    /**
     * Used when a target dies in to ensure all old non-passive effects are removed.
     * @param target
     */
    public static void removeNonPassiveEffects(CombatInfo target) {
    	LinkedList<EffectState> effectsToRemove = new LinkedList<EffectState>();
    	for (EffectState state :target.getCurrentEffects()) {
    		if (!state.effect.isPassive())
    			effectsToRemove.add(state);
    	}
    	for (EffectState state : effectsToRemove) {
    		removeEffect(state);
    	}
    }

	public static class EffectState implements Runnable, Serializable {
		public EffectState() {
			defaultName = null;
			defaultValue = null;

		}

		/**
		 * Create State for effect
		 * @param effect
		 * @param source
		 * @param target
		 * @param params
		 */
        public EffectState(AgisEffect effect, CombatInfo source, CombatInfo target, Map<String, Serializable> params) {
        	this();
            this.effect = effect;
            this.effectID = effect.getID();
            this.effectName = effect.getName();
            this.sourceOid = source.getOid();
            this.targetOid = target.getOid();
            this.params = toConcurrentMap(params);
            this.abilityID = -1;
			id = System.nanoTime();
            this.setTimeUntilEnd(effect.getDuration());
		}

		/**
		 * Create State for effect
		 * 
		 * @param effect
		 * @param source
		 * @param target
		 * @param params
		 * @param abilityID
		 */
        public EffectState(AgisEffect effect, CombatInfo source, CombatInfo target, Map<String, Serializable> params, int abilityID) {
        	this();
            this.effect = effect;
            this.effectID = effect.getID();
            this.effectName = effect.getName();
            this.sourceOid = source.getOid();
            this.targetOid = target.getOid();
            this.params = toConcurrentMap(params);
            this.abilityID = abilityID;
			id = System.nanoTime();
            this.setTimeUntilEnd(effect.getDuration());
        }

		/**
		 * Create State for effect
		 * 
		 * @param effect
		 * @param source
		 * @param target
		 * @param params
		 * @param abilityID
		 * @param item
		 */
        public EffectState(AgisEffect effect, CombatInfo source, CombatInfo target, Map<String, Serializable> params, int abilityID, AgisItem item) {
        	this();
            this.effect = effect;
            this.effectID = effect.getID();
            this.effectName = effect.getName();
            this.sourceOid = source.getOid();
            if(target!=null)
            	this.targetOid = target.getOid();
            this.params = toConcurrentMap(params);
            this.abilityID = abilityID;
            this.item = item;
			id = System.nanoTime();
            this.setTimeUntilEnd(effect.getDuration());
        }

        public void run() {
            try {
				if(delayed) {
					if(Log.loggingDebug)Log.debug("EFFECT: running updateState for " + effectName+" delayed");
					delayed = false;
					AgisEffect.applyEffect(effect, getSource(), getTarget(), getParams(), abilityID, getItem(),this);
				} else {
					updateState();
				}
            }
            catch (AORuntimeException e) {
                Log.exception("EffectState.run: got exception", e);
            }
        }

        public void updateState() {
        	if(Log.loggingDebug)Log.debug("EFFECT: running updateState for " + effectName);
            if (!isActive()) {
                return;
            }
            if(Log.loggingDebug)  Log.debug("EFFECT: running updateState for " + effectName+" nextPulse="+nextPulse+" NumPulses="+effect.getNumPulses()+" isPeriodic="+effect.isPeriodic()+" currentStack="+currentStack);
            
            if (effect.isPeriodic() || effect.getNumPulses() > 0 ) {
            	if(nextPulse < effect.getNumPulses())
                	effect.pulse(this);
                nextPulse++;
                if(Log.loggingDebug) Log.debug("EFFECT: running updateState for " + effectName+" nextPulse="+nextPulse+" NumPulses="+effect.getNumPulses()+" passive="+effect.isPassive());
                
                if (nextPulse < effect.getNumPulses()-1 || effect.isPassive()) {
                    schedule(effect.getPulseTime());
                    return;
                } else if(nextPulse >= effect.getNumPulses()-1)  {
                	if(effect.isStackTime()) {
                		currentStack--;
                		if(currentStack > 0) {
                			nextPulse=-1;
                			schedule(effect.getPulseTime());
                			setTimeUntilEnd(effect.getDuration()+effect.getDuration()/effect.getNumPulses());
                			getTarget().updateEffectsProperty();
                			return;
                		}
                	}
                }
            }
            if(Log.loggingDebug)  Log.debug("EFFECT: running updateState for " + effectName+" passive="+effect.isPassive()+" isStackTime="+effect.isStackTime());
            
            if (effect.isPassive())
            	return;
            if(effect.isStackTime() && effect.getNumPulses() < 1) {
            	currentStack--;
            	if(currentStack > 0) {
            		setTimeUntilEnd(effect.getDuration());
            		schedule(effect.getDuration());
            		getTarget().updateEffectsProperty();
            		return;
            	}
            }
            if(Log.loggingDebug)  Log.debug("EFFECT: going to remove effect " + getEffectName());
            AgisEffect.removeEffect(this);
		}

		/**
		 * Shedule effect delay or next pulse
		 * 
		 * @param delay
		 */
		public void schedule(long delay) {
			// Log.dumpStack("EFFECT: schedule " + effectName+" delay="+delay);
			setTimeRemaining(delay);
			timer = Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);
		}

		/**
		 * Reschedule effect
		 */
		public void reschedule() {
			Engine.getExecutor().remove(this);
			timer = Engine.getExecutor().schedule(this, getTimeRemaining(), TimeUnit.MILLISECONDS);
		}

		/**
		 * Reschedule effect
		 * 
		 * @param delay
		 */
		public void reschedule(long delay) {
			if (timer != null)
				timer.cancel(true);
			setTimeRemaining(delay);
			Engine.getExecutor().remove(this);
			timer = Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);
		}

		/**
		 * Resume effect
		 */
		public void resume() {
			effect = Agis.EffectManager.get(effectID);
			if (Log.loggingDebug)
				Log.debug("AgisEffect.resume: id="+id+"  effectName=" + effectName + " effect=" + effect + " timeRemaining=" + getTimeRemaining() + " isContinuous? " + effect.isContinuous() + " or passive: " + effect.isPassive());
			effect.resume(this);
			if ( /*!effect.isContinuous() &&*/  !effect.isPassive())
				timer = Engine.getExecutor().schedule(this, getTimeRemaining(), TimeUnit.MILLISECONDS);
		}

        public String  toString() {
    		return "[EffectState: id="+id+" effectName="+effectName+"; effectID="+effectID+"; effect="+effect+"; abilityID="+abilityID+"; sourceOid="+sourceOid+"; targetOid="+targetOid+"; params="+params+"; item"+item+" RemainingTime="+getTimeRemaining()+"]";
    	}

        public AgisEffect getEffect() { return effect; }
        public void setEffect(AgisEffect effect) { this.effect=effect; }
        //protected  AgisEffect effect = null;
        protected transient AgisEffect effect = null;

        public long getId() { return id; }
        public void setId(long id) { this.id=id; }
        protected long id ;
        
        public int getEffectID() { return effectID; }
        public void setEffectID(int effectID) { this.effectID = effectID; }
        protected int effectID;

        public String getEffectName() { return effectName; }
        public void setEffectName(String effectName) { this.effectName = effectName; }
        protected String effectName;
        
        public String getDefaultName() { return defaultName; }
        public void setDefaultName(String defaultName) { this.defaultName = defaultName; }
        protected String defaultName;
        
        public Serializable getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Serializable defaultValue) { this.defaultValue = defaultValue; }
        protected Serializable defaultValue;
        
        public int getAbilityID() { return abilityID; }
        public void setAbilityID(int abilityID) { this.abilityID = abilityID; }
        protected int abilityID;
        
        public EffectState getBonusEffect() { return bonusEffect; }
        public void setBonusEffect(EffectState bonusEffect) { this.bonusEffect = bonusEffect; }
        protected EffectState bonusEffect;

        public CombatInfo getTarget() { 
        	Log.debug("EFFECT: getting target: " + targetOid);
        	return CombatPlugin.getCombatInfo(targetOid); 
        }
        public OID getTargetOid() { return targetOid; }
        public void setTargetOid(OID oid) { targetOid = oid; }
        protected OID targetOid;

        public CombatInfo getSource() { return CombatPlugin.getCombatInfo(sourceOid); }
        public OID getSourceOid() { return sourceOid; }
        public void setSourceOid(OID oid) { sourceOid = oid; }
        protected OID sourceOid = null;

        public long getNextWakeupTime() { return nextWakeupTime; }
        public long getTimeRemaining() { 
        	return nextWakeupTime - System.currentTimeMillis(); }
        public void setTimeRemaining(long time) {
        	nextWakeupTime = System.currentTimeMillis() + time; 
        	}
        
        public void addTimeRemaining(long time) {
        	nextWakeupTime = nextWakeupTime + time; 
        	}
    
        protected long nextWakeupTime;
        
        public long getEndTime() { return endTime; }
        public long getTimeUntilEnd() { 
        	//Log.error("Effect : getTimeUntilEnd N:"+effectName+" ET:"+endTime+ " ScTM:"+System.currentTimeMillis()+" diff:"+(endTime - System.currentTimeMillis()));
        	return endTime - System.currentTimeMillis(); }
        public void setTimeUntilEnd(long time) { 
           //	Log.error("Effect : setTimeUntilEnd "+time);
            	endTime = System.currentTimeMillis() + time; 
               //	Log.error("Effect : setTimeUntilEnd  N:"+effectName+" ET:"+endTime+ " ScTM "+System.currentTimeMillis()+" diff"+(endTime - System.currentTimeMillis()));
               //	getTarget().updateEffectsProperty();
                }
        public void addTimeUntilEnd(long time) {
        	endTime = endTime + time;
        }
        protected long endTime;
        
        public long getStartTime() { return endTime; }
        public void setStartTime() { startTime = System.currentTimeMillis(); }
        
        protected long startTime;

        public int getNextPulse() { return nextPulse; }
        public void setNextPulse(int num) { nextPulse = num; }
        protected int nextPulse = 0;

        public boolean isActive() { return active; }
        public void isActive(boolean active) { this.active = active; }
        protected boolean active = true;
        
        public Map getParams() { return params; }
        public void setParams(Map params) { this.params = toConcurrentMap(params); }

        private ConcurrentHashMap toConcurrentMap(Map params) {
            if (params == null) {
                return null;
            }
            ConcurrentHashMap map = new ConcurrentHashMap();
            Stream<Entry> s = params.entrySet().stream();
            s.filter(e -> e.getKey() != null && e.getValue() != null).forEach(e -> map.put(e.getKey(), e.getValue()));
            return map;
        }

        protected ConcurrentHashMap params = null;
        
        public AgisItem getItem() { return item; }
        public void setItem(AgisItem item) { this.item = item; }
        protected AgisItem item = null;
        
        public int getCurrentStack() { return this.currentStack; }
        public void setCurrentStack(int currentStack) {
        	if (effect != null) {
        		if (currentStack > effect.stackLimit)
        			currentStack = effect.stackLimit;
        	}
    		this.currentStack = currentStack;
    	}
        protected int currentStack = 1;
        
        public OID getStackCaster() { return stackCaster; }
        public void setStackCaster(OID caster) { this.stackCaster = caster; }
        protected OID stackCaster;
        
        public boolean wasApplied() {
        	return wasApplied;
        }
        public void wasApplied(boolean wasApplied) {
        	this.wasApplied = wasApplied;
        }
        protected boolean wasApplied = true;


		protected ScheduledFuture<?> timer = null;
        
        private static final long serialVersionUID = 1L;
        static {
    		try {
    			BeanInfo info = Introspector.getBeanInfo(EffectState.class);
    			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
    			for (int i = 0; i < propertyDescriptors.length; ++i) {
    				PropertyDescriptor pd = propertyDescriptors[i];
    				if (pd.getName().equals("effect")) {
    					pd.setValue("transient", Boolean.TRUE);
    				}else if (pd.getName().equals("coordinatedEffectStates")) {
						pd.setValue("transient", Boolean.TRUE);
					}
    				//log.debug("BeanInfo name="+pd.getName());
    			}
    		} catch (Exception e) {
    			Log.error("failed beans initalization");
    		}
    	}

		public void setDelayed() {
			delayed = true;
		}
		protected boolean delayed = false;

		public ArrayList<CoordinatedEffectState> getCoordinatedEffectStates() {
			return coordinatedEffectStates;
		}

		public void setCoordinatedEffectStates(ArrayList<CoordinatedEffectState> coordinatedEffectStates) {
			this.coordinatedEffectStates = coordinatedEffectStates;
		}

		transient protected ArrayList<CoordinatedEffectState> coordinatedEffectStates = new ArrayList<>();
	}
    
    private static final long serialVersionUID = 1L;
}
