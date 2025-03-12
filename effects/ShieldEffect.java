package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that alters the value of a stat on the target for a period of time.
 * Can be permanent if the effect is a passive one.
 * @author Andrew Harrison
 *
 */
public class ShieldEffect extends AgisEffect {
	public ShieldEffect() {
		
	}
    public ShieldEffect(int id, String name) {
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
			Log.debug("ShieldEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ShieldEffect: this effect is not for buildings");
			return;
		}	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	
    	CombatInfo caster = state.getSource();
		CombatInfo target = state.getTarget();

		boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
		
		if (target != null) {
			if (target.dead() && !ignoreDead) {
				Log.debug("ShieldEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
    	
    	// Stacking handling
    	int stackCase = stackCheck();
    	if (stackCase == 0) {
    		// Do not apply this effect
    		Log.error("ShieldEffect: stack is 0 not apply");
    		return;
    	} 
    	if(target==null) {
    		Log.error("ShieldEffect: target is null "+state);
    		return;
    	}
    	
    	int stackLevel = 1;
    	boolean hasThisEffect = false;
    	boolean fromThisCaster = false;
    	// AgisEffect effect = state.getEffect();
    	EffectState similarEffect = null;
    	EffectState sameCasterEffect = null;
    	for (EffectState existingState : target.getCurrentEffects()) {
    		if (existingState.getEffect().getID() == getID() && !state.equals(existingState)) {
    			hasThisEffect = true;
    			similarEffect = existingState;
    		    if (caster.getOwnerOid().equals(similarEffect.getStackCaster())) {
    		    	fromThisCaster = true;
    		    	sameCasterEffect = similarEffect;
    		    }
    		}
    	}
    	
    	if(Log.loggingDebug)
				Log.debug("ShieldEffect: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);
    	
    	if (stackCase == 1) {
    		// If the target already has the same effect type from the same caster, remove the old one
    		if (fromThisCaster)
    			AgisEffect.removeEffect(sameCasterEffect);
    	} else if (stackCase == 2) {
    		// If the target already has the same effect type from the same caster, remove the old one and increment the stack
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    			AgisEffect.removeEffect(sameCasterEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    		}
    	} else if (stackCase == 3) {
    		// If the target already has the same effect type, remove the old one
    		if (hasThisEffect)
    			AgisEffect.removeEffect(similarEffect);
    	} else if (stackCase == 4) {
    		// If the target already has the same effect type, remove the old one and increment the stack
    		if (hasThisEffect) {
    			stackLevel = similarEffect.getCurrentStack();
    			AgisEffect.removeEffect(similarEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    		}
    	}
    	
    	int skillLevel = 0;
		Log.debug("ShieldEffect: about to check for skill level");
		if (effectSkillType != -1) {
	        if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
	    	    Log.warn("ShieldEffect: player does not have this skill: " + effectSkillType);
	        else
	        	skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}
		if(Log.loggingDebug)
			Log.debug("ShieldEffect: skill " + effectSkillType + " level is " + skillLevel + " skillEffectMod " + skillEffectMod.get(0));
		
		if(Log.loggingDebug)
			Log.debug("ShieldEffect: applying effect: " + getName() + " with effectVal: " + getID());
		if(shieldAmount > 0)
			maxShieldAmount =  (int) (shieldAmount + Math.ceil(skillLevel * skillEffectMod.get(0)));
		else
			maxShieldAmount = shieldAmount;
		currentShieldAmount = maxShieldAmount;
		
		if(hitCount > 0)
			maxHitCount =  (int) (hitCount + Math.ceil(skillLevel * skillEffectMod.get(0)));
		currentHitCount=0;
		Log.debug("ShieldEffect.Calculate: tags="+tags+" state="+state+" currentShieldAmount="+currentShieldAmount+" currentHitCount="+currentHitCount+"/"+maxHitCount);
		
		sendShieldUpdate(state);
		EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, "shield_"+getName(), true);
		if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
			CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			state.getCoordinatedEffectStates().add(	cE.invoke(state.getTarget().getOwnerOid(), state.getTarget().getOwnerOid()));
		}
    	EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    	Log.debug("ShieldEffect: apply end");
    }
    
    public void remove(EffectState state) {
    	CombatInfo target = state.getTarget();
    	remove(state, target);
    }

    // remove the effect from the object
    public void remove(EffectState state, CombatInfo target) {
		if (target == null)
			return;
		if(Log.loggingDebug)
			Log.debug("ShieldEffect: removing statEffect: " + this.getName());
		
		if (state.getSource() != null && target != null) {
			EventMessageHelper.SendCombatEvent(state.getSource().getOwnerOid(), target.getOwnerOid(),  EventMessageHelper.COMBAT_BUFF_LOST, state.getAbilityID(), getID(), -1, -1);
		}
		if(maxShieldAmount>0)
			currentShieldAmount = 0;
		if(maxHitCount > 0)
			currentHitCount = maxHitCount;
		sendShieldUpdate(state);
		EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, "shield_"+getName(), false);
		
		super.remove(state);
    }
    
    public void unload(EffectState state, CombatInfo target) {
    	remove(state, target);
    }

    // perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		super.pulse(state);
		if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
			CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			state.getCoordinatedEffectStates().add(cE.invoke(state.getTarget().getOwnerOid(), state.getTarget().getOwnerOid()));
		}
	}
    
    public void activate(EffectState state) {
    	super.activate(state);
    	
    	CombatInfo caster = state.getSource();
    	CombatInfo target = state.getTarget();
    	int skillLevel = 0;
    	int stackLevel = 1;
		Log.debug("COMBATHELPER: about to check for skill level");
		
	/*	if (effectSkillType != -1) {
	        if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
	    	    Log.warn("COMBAT HELPER: player does not have this skill: " + effectSkillType);
	        else
	        	skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}*/
		
    
    }
    
    public void deactivate(EffectState state) {
    	super.deactivate(state);
    	
    	CombatInfo target = state.getTarget();
    	
    }
    
	public int Calculate(CombatInfo caster, CombatInfo target, int damage, String property, ArrayList<Integer> tags, EffectState state) {
		if(Log.loggingDebug)
			Log.debug("ShieldEffect.Calculate: caster="+caster+" target="+target+" damage="+damage+" property="+property+" tags="+tags+" state="+state+" currentShieldAmount="+currentShieldAmount+" currentHitCount="+currentHitCount+"/"+maxHitCount);
		boolean foundAbs = false;
		boolean foundRef = false;
		boolean toDelete = false;
		for (ShieldSetting ss : settings) {
			if(Log.loggingDebug)
				Log.debug("ShieldEffect.Calculate: setting=" + ss + " foundAbs=" + foundAbs + " foundRef=" + foundRef);
			if (ss.tags.size() == 0) {
				if ((ss.reflect && !foundRef) || (!ss.reflect && !foundAbs)) {
					if (ss.reflect) {
						foundRef = true;
						int dmg = ss.value + (int) Math.ceil((double) damage * ss.valuep / 100D);
						if(Log.loggingDebug)
							Log.debug("ShieldEffect.Calculate: reflect dmg=" + dmg);
						caster.statModifyBaseValue(property, -dmg);
						caster.statSendUpdate(false);
					} else {
						foundAbs = true;
						int dmg = ss.value + (int) Math.ceil((double) damage * ss.valuep / 100D);
						if (Log.loggingDebug)
							Log.debug("ShieldEffect.Calculate: absorb dmg=" + dmg);
						if (dmg > damage)
							dmg = damage;
						if (Log.loggingDebug)
							Log.debug("ShieldEffect.Calculate: absorb dmg=" + dmg);
						if (currentShieldAmount == -1) {
							damage = damage - dmg;
						} else if (currentShieldAmount > 0) {
							if (dmg >= currentShieldAmount) {
								damage = damage - currentShieldAmount;
								currentShieldAmount = 0;
								toDelete = true;
							} else {
								currentShieldAmount = currentShieldAmount - dmg;
								damage = damage - dmg;
							}
						} else {
							toDelete = true;
						}
					}
				}

			} else {

				for (int tag : ss.tags) {
					if ((ss.reflect && !foundRef) || (!ss.reflect && !foundAbs)) {
						if (tags.contains(tag)) {
							if (ss.reflect) {
								foundRef = true;

								int dmg = ss.value + (int) Math.ceil((double) damage * ss.valuep / 100D);
								if(Log.loggingDebug)
									Log.debug("ShieldEffect.Calculate: reflect dmg=" + dmg);
								caster.statModifyBaseValue(property, -dmg);
								caster.statSendUpdate(false);
							} else {
								foundAbs = true;
								int dmg = ss.value + (int) Math.ceil((double) damage * ss.valuep / 100D);
								if (Log.loggingDebug)
									Log.debug("ShieldEffect.Calculate: absorb dmg=" + dmg);
								if (dmg > damage)
									dmg = damage;
								if (Log.loggingDebug)
									Log.debug("ShieldEffect.Calculate: absorb dmg=" + dmg);
								if (currentShieldAmount == -1) {
									damage = damage - dmg;
								} else if (currentShieldAmount > 0) {
									if (dmg >= currentShieldAmount) {
										damage = damage - currentShieldAmount;
										currentShieldAmount = 0;
										toDelete = true;
									} else {
										currentShieldAmount = currentShieldAmount - dmg;
										damage = damage - dmg;
									}
								} else {
									toDelete = true;
								}
							}
						}
					}
				}
			}
		}
		if (foundAbs || foundRef) {
			if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
				CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
				cE.sendSourceOid(true);
				cE.sendTargetOid(true);
				state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), caster.getOwnerOid()));
			}

			currentHitCount++;
			if (maxHitCount > 0 && currentHitCount >= maxHitCount) {
				toDelete = true;
			}
			sendShieldUpdate(state);
			if (toDelete)
				AgisEffect.removeEffect(state);
		}
		if(damage<0)
			damage=0;
		if(Log.loggingDebug)
			Log.debug("ShieldEffect.Calculate: end damage="+damage);
		return damage;
	}

	public String getPulseCoordEffect() {
		return pulseCoordEffect;
	}

	public void setPulseCoordEffect(String coordEffect) {
		pulseCoordEffect = coordEffect;
	}

	protected String pulseCoordEffect;

  /*  public void setDisplayName(String eName) {
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
    
    */
    
	void sendShieldUpdate(EffectState state) {
		TargetedExtensionMessage updateMessage = new TargetedExtensionMessage(state.getSourceOid());
		updateMessage.setExtensionType("shieldUpdate");
		updateMessage.setProperty("name", getName()); 
		updateMessage.setProperty("sMax", maxShieldAmount); 
		updateMessage.setProperty("sCur", currentShieldAmount);
		updateMessage.setProperty("cMax", maxHitCount); 
		updateMessage.setProperty("cCur", maxHitCount-currentHitCount);
		Engine.getAgent().sendBroadcast(updateMessage);
	}
    
    
    public void setShieldAmount(int val) {
    	shieldAmount = val;
    }
    protected int shieldAmount = 0;
        
    protected int currentShieldAmount = 0;
    protected int maxShieldAmount = 0;
     
    protected int currentHitCount = 0;
    public void setHitCount(int val) {
    	hitCount = val;
    }
    protected int hitCount = -1;
    protected int maxHitCount = -1;
    
    
    public void addSetting(ArrayList<Integer> tags, int value, float valuep, boolean reflect) {
    	Log.debug("ShieldEffect tags:"+tags+" v="+value+" v%="+valuep+" ref="+reflect);
		
    	ShieldSetting ss = new ShieldSetting();
    	ss.tags= tags;
    	ss.value = value;
    	ss.valuep = valuep;
    	ss.reflect = reflect;
    	settings.add(ss);
    }
    List<ShieldSetting> settings = new ArrayList<ShieldSetting>();
  
    
    private static final long serialVersionUID = 1L;
}
class ShieldSetting {
	public ArrayList<Integer> tags = new ArrayList<Integer>();
	public Integer value = 0;
	public Float valuep = 0F;
	public boolean reflect = false;
	public String toString() {
		return "[ShieldSetting: Reflect:"+reflect+" value:"+value+" %:"+valuep+" tags:"+tags+"]";
	}
}
