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
 * Effect child class that deals instant Magical Damage to the target, reducing one of their value stats (e.g Health or Mana).
 * @author Andrew Harrison
 *
 */
@Deprecated
public class MagicalStrikeEffect extends AgisEffect {
	
	static Random random = new Random();

    public MagicalStrikeEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
	    Map<String, Serializable> params = state.getParams();
        int result = (int)params.get("result");
   //     effectSkillType = params.get("skillType");
        hitRoll = (int)params.get("hitRoll");
    	int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
	Log.debug("MeleeStrikeEffect: apply |");
		
        String abilityEvent = EventMessageHelper.COMBAT_DAMAGE;
        
        CombatInfo target = state.getTarget();
        CombatInfo caster = state.getSource();
        
        int dmg = 0;
        
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
			// if (maxDmg > minDmg) {
			// dmg += random.nextInt(maxDmg - minDmg);
			// }

			// Now lets take the players stats into account
			if (CombatPlugin.MAGICAL_ATTACKS_USE_WEAPON_DAMAGE) {
				int dmg_base = caster.statGetCurrentValue("dmg-base");
				int dmg_max = caster.statGetCurrentValue("dmg-max");
				if (dmg_max <= dmg_base) {
					dmg += dmg_base;
				} else {
					dmg += dmg_base + random.nextInt(dmg_max - dmg_base);
				}
			}
			int ammoId = 0;
			int ammoDamage = 0;
			Boolean ammoMatch = false;
			Integer ammoType = 0;
			try {
				ammoId = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
				ammoDamage = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_DAMAGE);
				if (ammoId > 0) {
					Template itemTemplate = ObjectManagerClient.getTemplate(ammoId, ObjectManagerPlugin.ITEM_TEMPLATE);
					if (itemTemplate == null)
						ammoType = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE);
				}
			} catch (Exception e) {
				Log.debug("No ammo param " + e);
			}
			if (ammoId > 0) {
			/*	Integer primAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), AgisEquipSlot.PRIMARYWEAPON);
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
			if (DamageMod != 1.0) {
				Log.debug("MagicSTRIKE: DamageMod: " + DamageMod + " Damage: " + dmg);
				float dmgF = (float) dmg * DamageMod;
				Log.debug("MagicSTRIKE: DamageFloat: " + dmgF);
				dmg = Math.round(dmgF);
				Log.debug("MagicSTRIKE: Damage: " + dmg);
			}

			// If this ability uses any effects to boost up dmg (and consumes the effect
			// at the same time
			if (bonusDmgEffectVals != null && bonusDmgEffectVals.size() > 0) {
				Log.debug("EFFECT: effect has bonusDmgEffectVal; effects required: " + bonusDmgEffectVals.toString());
				for (int i = 0; i < bonusDmgEffectVals.size(); i++) {
					boolean effectPresent = false;
					for (EffectState existingState : caster.getCurrentEffects()) {
						if (bonusDmgEffectVals.get(i) == existingState.getEffect().getID()) {
							effectPresent = true;
						}
					}
					if (effectPresent) {
						//AgisEffect.removeEffectByID(target, bonusEffectReq);
						dmg = dmg + bonusDmgVals.get(i);
						Log.debug("EFFECT: removed effect position: " + effectPresent + "; and boosted dmg by: " + bonusDmgVals.get(i));
					}
				}
			}

	        	  //Dragonsan Remove Sleep because apply damage
			if (target != null) {  
	            Set<EffectState> effects = target.getCurrentEffects();
	            List<Integer> listToRemove = new ArrayList<Integer>();
	            for (EffectState ef :effects) {
	            	Log.debug("Magical Strike Effect Damage: Check is SleepEffect "+ef.getEffectName());
	               	if (ef.getEffect().getClass().equals(SleepEffect.class)) {
	               		Log.debug("Magical Strike Effect Damage: add to remove SleepEffect "+ef.getEffectName());
	               		if(!listToRemove.contains(ef.getEffect().getID()))
	               			listToRemove.add(ef.getEffect().getID());
	               	}
	            	
	            }
	            Log.debug("MagicalStrikeEffect.apply Removing Sleep Effects");
	            for(Integer id : listToRemove) {
	            	AgisEffect.removeEffectByID(target, id);
	            }
	            Log.debug("MagicalStrikeEffect.apply Removed Sleep Effects");
			}
	        	
        	  Log.debug("COMBAT: Magical Strike Effect Apply before switch mod dmg->"+dmg);
	              	
	        	switch (result) {
			        // Finally lets check for critical or blocks
			        case AgisAbility.RESULT_BLOCKED:
			        	dmg = dmg / 2;
			        	break;
			        case AgisAbility.RESULT_PARRIED:
			        	dmg = (int)(dmg * 0.6);
			        	break;
			        case AgisAbility.RESULT_CRITICAL:
		        	//	dmg = dmg * 2;
		        		break;
		        		
	        	}
		        if (caster.isUser()  && target != null && target.isUser() && !caster.getOid().equals(target.getOid())  && CombatPlugin.PVP_DAMAGE_REDUCTION_USE)	{
	        		dmg = (int)(dmg * CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
	        		Log.debug("COMBAT: Magical Strike Effect  caster:"+caster+" target:"+target+" dmg * "+CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
	        		}
        	
	        	if (Log.loggingDebug) {
	                Log.debug("DamageEffect.apply: doing instant damage to obj=" + state.getTarget() +
	    		      " from=" + state.getSource());
	        	}
        }
        
        Log.debug("COMBAT: Magical Strike Effect Apply dmg->"+dmg);
        
       
        
        params.put("dmg", dmg);
        
    }
    
    public void pulse(EffectState state) {
    	Log.debug("PULSE: running pulse for MagicalStrikeEffect");
    	Map<String, Serializable> params = state.getParams();
    	int dmg = (int)params.get("dmg");
    	int result = (int)params.get("result");
    	int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		Log.debug("PULSE: running pulse for MagicalStrikeEffect dmg="+dmg+" result="+result+" claimID="+claimID+" objectID="+objectID);
    	CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
    	
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
    	Log.debug("PULSE: running pulse for MagicalStrikeEffect abilityEvent ->"+abilityEvent);
        
    	
    	
    	dmg = CombatHelper.modifyDamage(source, target, dmg);
    	
   		
    	 Set<EffectState> effects =  null;
         if(target!=null)
      		   effects = state.getTarget().getCurrentEffects() != null ? new HashSet<EffectState>(state.getTarget().getCurrentEffects()) : new HashSet<EffectState>();
		Set<EffectState> ceffects = state.getSource().getCurrentEffects() != null ? new HashSet<EffectState>(state.getSource().getCurrentEffects()) : new HashSet<EffectState>();
		// Triggers Calculations
		for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
			}
		}
		if (effects!=null && !state.getSourceOid().equals(state.getTargetOid())) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
				}
			}
		}
		CombatClient.sendCombatEvent(state.getSourceOid(), false, TriggerProfile.Type.DAMAGE);
		CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.DAMAGE);
		if (result == AgisAbility.RESULT_CRITICAL) {
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
				}
			}
			Log.debug("MeleeStrikeEffect.pulse: Check Triggetson target");
			if (effects!=null && !state.getSourceOid().equals(state.getTargetOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
					}
				}
			}
			CombatClient.sendCombatEvent(state.getSourceOid(), false, TriggerProfile.Type.CRITICAL);
			CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.CRITICAL);
		}
		if(effects!=null ) 
          for (EffectState ef : effects) {
          	if (ef.getEffect().getClass().equals(ShieldEffect.class)) {
          		ShieldEffect se = (ShieldEffect)ef.getEffect();
          		dmg = se.Calculate(source, target, dmg, getDamageProperty(), tags, ef);
      		}
      	}
		if (target != null) {
    	int targetHealth = target.statGetCurrentValue(getDamageProperty());
        if (dmg >= targetHealth) {
        	Log.debug("PULSE: running pulse for MagicalStrikeEffect dmg >= targetHealth");
            
        	// Check if this will cause a duel defeat
        	if (checkDuelDefeat(target, source, getDamageProperty())) {
        		dmg = targetHealth - 1;
        	}else {
       		 for (EffectState ef : ceffects) {
 				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
 					TriggerEffect se = (TriggerEffect) ef.getEffect();
 					dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
 				}
 			}
 	        Log.debug("MeleeStrikeEffect.pulse: Check Triggetson target");
 	        for (EffectState ef : effects) {
 				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
 					TriggerEffect se = (TriggerEffect) ef.getEffect();
 					dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
 				}
 			}
  	       CombatClient.sendCombatEvent(state.getSourceOid(), false, TriggerProfile.Type.KILL);
 	       CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.KILL);
 	}
        	
        	// Check for arena kill
        	if (target.isUser()) {
        		Integer arenaID = (Integer) target.getProperty(CombatInfo.COMBAT_PROP_ARENA_ID);
        		if (arenaID != null && arenaID > -1) {
        			ArenaClient.arenaDeath(source.getOwnerOid(), target.getOwnerOid());
        		}
        	}
        }
		}
    	Log.debug("PULSE: running pulse for MagicalStrikeEffect damageType >"+damageType+"; dmgType > before"); 
        //int dmgType = params.get("dmgType");
    	//Log.debug("PULSE: running pulse for MagicalStrikeEffect damageType >"+damageType+"; dmgType >"+dmgType); 
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
        //if (dmg > 0)
        Log.debug("PULSE: running pulse for MagicalStrikeEffect dmg ->"+dmg); 
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
	   // EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), dmg, -1);
		}
		EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target!=null?target.getOwnerOid():new OID()), abilityEvent, state.getAbilityID(), getID(), dmg, claimID, objectID+"", "");
    	
	    double healD = dmg * transferModifier;
        int heal = (int) healD;
        source.statModifyBaseValue(getDamageProperty(), heal);
        source.sendStatusUpdate();
		// If there is a pulse coord effect, run it now
        if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
        	CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
    	    cE.sendSourceOid(true);
    	    cE.sendTargetOid(true);
    	    cE.putArgument("claimID", claimID);
    	    cE.putArgument("claimObjID", objectID); 
    	    if (claimID > 0 && objectID > 0) {
				cE.invoke(source.getOwnerOid(), source.getOwnerOid());
			} else {
				cE.invoke(target.getOwnerOid(), target.getOwnerOid());
			}
        }
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
    
    private static final long serialVersionUID = 1L;
}
