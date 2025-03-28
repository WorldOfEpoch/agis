package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that deals Damage to the target, reducing one of their value stats (e.g Health or Mana)
 */

public class AttackEffect extends AgisEffect {
	
	static Random random = new Random();

    public AttackEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		if(Log.loggingDebug)Log.debug("AttackEffect: effect state is: " + state);
		Map<String, Serializable> params = state.getParams();
		if(Log.loggingDebug)Log.debug("AttackEffect: apply effect params is: " + params);
		int result = (int) params.get("result");
		hitRoll = (int) params.get("hitRoll");

		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		
		Log.debug("AttackEffect: apply |");
		CombatInfo target = null;
		if(claimID==-1 && objectID==-1) {
			target = state.getTarget();
			if (target != null) {
				if (target.dead()) {
					Log.debug("AttackEffect: target is dead");
					state.wasApplied(false);
					return;
				}
			}
		}
		CombatInfo caster = state.getSource();
		if(caster == null) {
			state.wasApplied(false);
			return ;
		}
		int dmg = 0;
		if(Log.loggingDebug)	Log.debug("AttackEffect: apply  result=" + result+" hitRoll="+hitRoll+" claimID="+claimID+" objectID="+objectID);
		switch (result) {
		case AgisAbility.RESULT_MISSED:
			break;
		case AgisAbility.RESULT_PARRIED:
			break;
		case AgisAbility.RESULT_DODGED:
			break;
		case AgisAbility.RESULT_EVADED:
			break;
		case AgisAbility.RESULT_IMMUNE:
			break;
		default:
			dmg = minDmg;
			// Get level difference
			// Now lets take the players stats into account
			
			if (useWeaponDamage) {
			double dmg_base = caster.statGetCurrentValueWithPrecision("dmg-base");
			double dmg_max = caster.statGetCurrentValueWithPrecision("dmg-max");
			if (dmg_max <= dmg_base) {
				dmg += dmg_base;
			} else {
				dmg += dmg_base + random.nextDouble()*(dmg_max - dmg_base);
			}
			}
			int ammoId = 0;
			int ammoDamage = 0;
			Integer ammoType = 0;
			Boolean ammoMatch = false;
			try {
				ammoId = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
				ammoDamage = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_DAMAGE);
				if(Log.loggingDebug)Log.debug("AttackEffect: ammoId: " + ammoId + " ammoDamage:" + ammoDamage);
				if (ammoId > 0) {
					Template itemTemplate = ObjectManagerClient.getTemplate(ammoId, ObjectManagerPlugin.ITEM_TEMPLATE);
					if (itemTemplate != null)
						ammoType = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE);
				}
			} catch (Exception e) {
				Log.debug("AttackEffect: ammo error " + e);
			}
			if (ammoId > 0) {
				List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
				for(AgisEquipSlot aes : slots) {
					Integer weaponAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), aes);
					if (weaponAmmoType != null && weaponAmmoType == ammoType) {
						ammoMatch = true;
					}	
				}
				/*
				 Integer primAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), AgisEquipSlot.PRIMARYWEAPON);
				 Integer secAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), AgisEquipSlot.SECONDARYWEAPON);
				if (primAmmoType != null && primAmmoType == ammoType) {
					ammoMatch = true;
				}
				if (secAmmoType != null && secAmmoType == ammoType) {
					ammoMatch = true;
				}*/
			}
			if (ammoMatch)
				dmg += ammoDamage;
			dmg = CombatHelper.CalcDamage(target, caster, dmg, damageType, skillEffectMod.get(0), effectSkillType, hitRoll, true, params);
			// Take in the effect base damage modifiers
			if (DamageMod != 1.0f) {
				// Log.debug("MELEESTRIKE: DamageMod: " + DamageMod + " Damage: " + dmg);
				float dmgF = (float) dmg * DamageMod;
				// Log.debug("MELEESTRIKE: DamageFloat: " + dmgF);
				dmg = Math.round(dmgF);
				if(Log.loggingDebug)Log.debug("AttackEffect: Damage: " + dmg);
			}

			// If this ability uses any effects to boost up dmg (and consumes the effect
			// at the same time
			if (bonusDmgEffectVals != null && bonusDmgEffectVals.size() > 0) {
				if(Log.loggingDebug)Log.debug("AttackEffect: effect has bonusDmgEffectVal; effects required: " + bonusDmgEffectVals.toString());
				for (int i = 0; i < bonusDmgEffectVals.size(); i++) {
					boolean effectPresent = false;
					for (EffectState existingState : caster.getCurrentEffects()) {
						if (bonusDmgEffectVals.get(i) == existingState.getEffect().getID()) {
							effectPresent = true;
						}
					}
					if (effectPresent) {
						// AgisEffect.removeEffectByID(target, bonusEffectReq);

						dmg = dmg + bonusDmgVals.get(i);
						if(Log.loggingDebug)Log.debug("AttackEffect: removed effect position: " + effectPresent + "; and boosted dmg by: " + bonusDmgVals.get(i));
					}
				}
			}
			if (target != null) {
				Set<EffectState> effects = target.getCurrentEffects();
				List<Integer> listToRemove = new ArrayList<Integer>();
				for (EffectState ef : effects) {
					if(Log.loggingDebug)Log.debug("AttackEffect Damage: Check is SleepEffect " + ef.getEffectName());
					if (ef.getEffect().getClass().equals(SleepEffect.class)) {
						if(Log.loggingDebug)Log.debug("AttackEffect Damage: add to remove SleepEffect " + ef.getEffectName());
						if (!listToRemove.contains(ef.getEffect().getID()))
							listToRemove.add(ef.getEffect().getID());
					}

				}
				Log.debug("AttackEffect.apply Removing Sleep Effects");
				for (Integer id : listToRemove) {
					AgisEffect.removeEffectByID(target, id);
				}
				Log.debug("AttackEffect.apply Removed Sleep Effects");
			}
			//
			Log.debug("AttackEffect: Apply before switch mod dmg->" + dmg);
			switch (result) {
			// Finally lets check for critical or blocks or parry
			case AgisAbility.RESULT_BLOCKED:
				dmg = dmg / 2;
				break;
			case AgisAbility.RESULT_PARRIED:
				dmg = (int) (dmg * 0.6);
				break;
			case AgisAbility.RESULT_CRITICAL:
				// dmg = dmg * 2;
				break;
			}

			if (caster.isUser() && target != null && target.isUser() && !caster.getOid().equals(target.getOid()) && CombatPlugin.PVP_DAMAGE_REDUCTION_USE) {
				dmg = (int) (dmg * CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
				if(Log.loggingDebug)	Log.debug("AttackEffect:   caster:" + caster + " target:" + target + " dmg * "+CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
			}

			if (Log.loggingDebug)
				Log.debug("AttackEffect.apply: doing instant damage " + dmg + " to obj=" + state.getTarget() + " from=" + state.getSource());
		}
		if(Log.loggingDebug)Log.debug("AttackEffect: Apply dmg->" + dmg);

		params.put("dmg", dmg);
	}
    
    public void pulse(EffectState state) {
    	Log.debug("PULSE: running pulse for AttackEffect");
    	Map<String, Serializable> params = state.getParams();
    	int dmg = (int)params.get("dmg");
    	int result = (int)params.get("result");
    	int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if(Log.loggingDebug)Log.debug("PULSE: running pulse for AttackEffect dmg="+dmg+" result="+result+" claimID="+claimID+" objectID="+objectID);
    	
    	CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
		OID tPetOwnerOid=null;
    	Serializable tPetOwner = target.getProperty("petOwner");
		if(tPetOwner != null) {
			tPetOwnerOid = (OID)tPetOwner;
		}
		OID cPetOwnerOid=null;
		Serializable cPetOwner = source.getProperty("petOwner");
		if(cPetOwner != null) {
			cPetOwnerOid = (OID)cPetOwner;
		}


		String abilityEvent = EventMessageHelper.COMBAT_DAMAGE;
        switch (result) {
    	case AgisAbility.RESULT_MISSED:
    		abilityEvent = EventMessageHelper.COMBAT_MISSED;
    		break;
    	case AgisAbility.RESULT_PARRIED:	
    		abilityEvent = EventMessageHelper.COMBAT_PARRIED;
    		break;
    	case AgisAbility.RESULT_DODGED:
    		abilityEvent = EventMessageHelper.COMBAT_DODGED;
    		break;
    	case AgisAbility.RESULT_EVADED:
    		abilityEvent = EventMessageHelper.COMBAT_EVADED;
    		break;
    	case AgisAbility.RESULT_IMMUNE:
    		abilityEvent = EventMessageHelper.COMBAT_IMMUNE;
    		break;
    	case AgisAbility.RESULT_BLOCKED:
    		abilityEvent = EventMessageHelper.COMBAT_BLOCKED;
    		break;
    	case AgisAbility.RESULT_CRITICAL:
    		abilityEvent = EventMessageHelper.COMBAT_DAMAGE_CRITICAL;
    		break;
        }

		if(Log.loggingDebug) Log.debug("PULSE: running pulse for AttackEffect dmg="+dmg);
       dmg = CombatHelper.modifyDamage(source, target, dmg);
		if(Log.loggingDebug)Log.debug("PULSE: running pulse for AttackEffect mod dmg="+dmg);
       Set<EffectState> effects =  null;
       if(target!=null)
    		   effects =state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
		Set<EffectState> ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
	  //Triggers Calculations
        Log.debug("AttackEffect.pulse: Check Triggers on caster");
        for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
			}
		}
        Log.debug("AttackEffect.pulse: Check Triggers on target");
		if (effects!=null && !state.getSourceOid().equals(state.getTargetOid())) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
				}
			}
		}
		CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.DAMAGE);
		CombatClient.sendCombatEvent(state.getTargetOid(),true, TriggerProfile.Type.DAMAGE);

		if(Log.loggingDebug) Log.debug("AttackEffect.pulse: result="+result+" dmg="+dmg);
		if (result == AgisAbility.RESULT_CRITICAL) {
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
				}
			}
			Log.debug("AttackEffect.pulse: Check Triggetson target");
			if (effects!=null && !state.getSourceOid().equals(state.getTargetOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
					}
				}
			}
			CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.CRITICAL);
			CombatClient.sendCombatEvent(state.getTargetOid(),true, TriggerProfile.Type.CRITICAL);

		}
		// Ability Triggers Calculations
		if(state.getAbilityID() > 0) {
			AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
			if(aa!=null) {
				long time = (long) params.get("powerUp");
				dmg = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, aa);
				if (result == AgisAbility.RESULT_CRITICAL) {
					dmg = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, aa);
				}
			}
		}
		Log.debug("AttackEffect.pulse: Check Shield Effects");
		if (effects != null)
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(ShieldEffect.class)) {
					ShieldEffect se = (ShieldEffect) ef.getEffect();
					dmg = se.Calculate(source, target, dmg, getDamageProperty(), tags, ef);
				}
			}
		Log.debug("AttackEffect.pulse: Check target has stat lower then dmg");
		// Check if applying the damage from this effect is going to kill the target
		if (target != null) {
			double targetHealth = target.statGetCurrentValueWithPrecision(getDamageProperty());
			double casterHealth = source.statGetCurrentValueWithPrecision(getDamageProperty());
			if (dmg >= targetHealth) {

				// Check if this will cause a duel defeat
				if (checkDuelDefeat(target, source, getDamageProperty())) {
					dmg = (int) (Math.round(targetHealth) - 1);
				} else {
					for (EffectState ef : ceffects) {
						if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
							TriggerEffect se = (TriggerEffect) ef.getEffect();
							dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
						}
					}
					Log.debug("AttackEffect.pulse: Check Triggetson target");
					for (EffectState ef : effects) {
						if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
							TriggerEffect se = (TriggerEffect) ef.getEffect();
							dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
						}
					}
					// Ability Triggers Calculations
					if(state.getAbilityID() > 0) {
						AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
						if(aa!=null) {
							long time = (long) params.get("powerUp");
							dmg = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.KILL, tags, dmg, source, target, aa);
						}
					}
					CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.KILL);
					CombatClient.sendCombatEvent(state.getTargetOid(),true, TriggerProfile.Type.KILL);
				}
				// Check for arena kill
				if (target.isUser()) {
					Integer arenaID = (Integer) target.getProperty(CombatInfo.COMBAT_PROP_ARENA_ID);
					if (arenaID != null && arenaID > -1) {
						ArenaClient.arenaDeath(source.getOwnerOid(), target.getOwnerOid());
					}
				}
			}
			if(Log.loggingDebug)Log.debug("AttackEffect.pulse targetHealth:" + targetHealth + " casterHealth:" + casterHealth + " apply damage " + dmg + " to " + target.getOwnerOid() + " from " + state.getSource().getOwnerOid());
		}
		if(Log.loggingDebug)Log.debug("AttackEffect.pulse abilityEvent:" + abilityEvent + " damageType:" + damageType + " apply damage " + dmg + " to " + (target != null ? target.getOwnerOid() : "NA") + " from "
				+ state.getSource().getOwnerOid());

		int threat = dmg;
		if (threat == 0)
			threat = 1;
		if (claimID > 0 && objectID > 0) {
			VoxelClient.sendBuildingDamage(state.getSource().getOwnerOid(),claimID, objectID, dmg);
		}
		if (target != null) {
			Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(), state.getSource().getOwnerOid(), dmg, damageType, threat));

			// Hopefully this will make the ai respond even if the attack was a miss etc.
			target.statModifyBaseValue(getDamageProperty(), -dmg);
			target.statSendUpdate(false);
			// Send interrupt to target
			if ((interruption_chance == 0 && interruption_chance_max > 0) || interruption_chance > 0 || forceInterruption) {
				CombatClient.interruptAbilityMessage interruptMsg = new CombatClient.interruptAbilityMessage(target.getOwnerOid());
				interruptMsg.setForce(forceInterruption);
				float chance = interruption_chance;
				if (interruption_chance < interruption_chance_max) {
					Random rand = new Random();
					float f = rand.nextFloat();
					chance = interruption_chance + (interruption_chance_max - interruption_chance) * f;
				}
				interruptMsg.setChance(chance);
				Engine.getAgent().sendBroadcast(interruptMsg);
			}
			// if (dmg > 0)

		//	EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), dmg, -1);
		}
			EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target!=null?target.getOwnerOid():new OID()), abilityEvent, state.getAbilityID(), getID(), dmg, claimID, objectID+"", damageType);
		if(cPetOwnerOid!=null){
			EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target!=null?target.getOwnerOid():new OID()), abilityEvent, state.getAbilityID(), getID(), dmg, claimID, objectID+"", damageType,cPetOwnerOid);
		}
		if(tPetOwnerOid!=null){
			EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target!=null?target.getOwnerOid():new OID()), abilityEvent, state.getAbilityID(), getID(), dmg, claimID, objectID+"", damageType,tPetOwnerOid);
		}

		double healD = dmg * transferModifier;
		if(Log.loggingDebug) Log.debug("AttackEffect.pulse: running pulse dmg="+dmg+";  transferModifier="+transferModifier+" healD="+healD);
    	
        int heal = (int) healD;
		if(Log.loggingDebug) Log.debug("AttackEffect.pulse: running pulse dmg="+dmg+";  transferModifier="+transferModifier+" heal="+heal+" property="+getDamageProperty());
        source.statModifyBaseValue(getDamageProperty(), heal);
        source.sendStatusUpdate();
	    // If there is a pulse coord effect, run it now
        
        Log.debug("AttackEffect.pulse: pulseCoordEffect="+pulseCoordEffect); 
        if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
        	CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
    	    cE.sendSourceOid(true);
    	    cE.sendTargetOid(true);
    	    cE.putArgument("claimID", claimID);
    	    cE.putArgument("claimObjID", objectID); 
     	     if (claimID > 0 && objectID > 0) {
				 state.getCoordinatedEffectStates().add(cE.invoke(source.getOwnerOid(), source.getOwnerOid()));
    	    } else if(target != null) {
				 state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), target.getOwnerOid()));

    	    }
        }
        Log.debug("AttackEffect.pulse: END"); 
        
    }

    public int getMinInstantDamage() { return minDmg; }
    public void setMinInstantDamage(int hps) { minDmg = hps; }
    protected int minDmg = 0;

    public int getMaxInstantDamage() { return maxDmg; }
    public void setMaxInstantDamage(int hps) { maxDmg = hps; }
    protected int maxDmg = 0;

    public int getMinPulseDamage() { return minPulseDmg; }
    public void setMinPulseDamage(int hps) { minPulseDmg = hps; }
    protected int minPulseDmg = 0;

    public int getMaxPulseDamage() { return maxPulseDmg; }
    public void setMaxPulseDamage(int hps) { maxPulseDmg = hps; }
    protected int maxPulseDmg = 0;

    public String getDamageProperty() { return damageProperty; }
    public void setDamageProperty(String property) { damageProperty = property; }
    protected String damageProperty = CombatPlugin.HEALTH_STAT;
    
    public float getDamageMod() { return DamageMod; }
    public void setDamageMod(float hps) { DamageMod = hps; }
    protected float DamageMod = 1.0f;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;
    
    public double getTransferModifier() { return transferModifier; }
    public void setTransferModifier(double modifier) { transferModifier = modifier; }
    protected double transferModifier = 1.0;

    // List of bonus damage effect values
    public void addBonusDmgEffectVal(int effect) {
    	bonusDmgEffectVals.add(effect);
    }
    public LinkedList<Integer> GetBonusDmgEffectVal() {
        return bonusDmgEffectVals;
    }
    public LinkedList<Integer> bonusDmgEffectVals = new LinkedList<Integer>();
    
    // List of bonus damage effect values
    public void addBonusDmgVal(int val) {
    	bonusDmgVals.add(val);
    }
    public LinkedList<Integer> GetBonusDmgVal() {
        return bonusDmgVals;
    }
    public LinkedList<Integer> bonusDmgVals = new LinkedList<Integer>();
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
    public void setHitRoll(int roll) {
    	hitRoll = roll;
    }
    public int GetHitRoll() {
        return hitRoll;
    }
    public int hitRoll = 0;
    
    public void setUseWeaponDamage(boolean useWeaponDamage) {
		this.useWeaponDamage = useWeaponDamage;
	}

	public boolean getUseWeaponDamage() {
		return useWeaponDamage;
	}

	public boolean useWeaponDamage = false;
    private static final long serialVersionUID = 1L;
}
