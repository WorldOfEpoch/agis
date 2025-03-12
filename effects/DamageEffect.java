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

public class DamageEffect extends AgisEffect {
	
	static Random random = new Random();

    public DamageEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
		Map<String, Serializable> params = state.getParams();
	
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
	
		CombatInfo target = state.getTarget();
	    CombatInfo caster = state.getSource();
	    int dmg = minDmg;
	        	
	    if (Log.loggingDebug)
	        Log.debug("DamageEffect.apply: doing instant damage to obj=" + target + " from=" + caster);
	    
	    if (maxDmg > minDmg) {
	        dmg += random.nextInt(maxDmg - minDmg);
	    }
	    
	    if (Log.loggingDebug)
	        Log.debug("DamageEffect.apply: minDmg="+minDmg+" maxDmg="+maxDmg+" dmg="+dmg);
		int ammoId = 0;
		int ammoDamage = 0;
		Integer ammoType = 0;
		Boolean ammoMatch = false;
		try {
			ammoId = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
			ammoDamage = (Integer) caster.getProperty(CombatInfo.COMBAT_AMMO_DAMAGE);
			if (ammoId > 0) {
				Template itemTemplate = ObjectManagerClient.getTemplate(ammoId, ObjectManagerPlugin.ITEM_TEMPLATE);
				if (itemTemplate == null)
					ammoType = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE);
			}
		} catch (Exception e) {
			Log.debug("DamageEffect: ammo error " + e);
		}
	     if (ammoId>0) {
	    	 List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
				for(AgisEquipSlot aes : slots) {
					Integer weaponAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), aes);
					if (weaponAmmoType != null && weaponAmmoType == ammoType) {
						ammoMatch = true;
					}	
				}
	    /*	 Integer primAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), AgisEquipSlot.PRIMARYWEAPON);
	         Integer secAmmoType = AgisInventoryClient.findItemAmmoType(caster.getOid(), AgisEquipSlot.SECONDARYWEAPON);
   	             if (primAmmoType != null && primAmmoType == ammoType) {
	            	 ammoMatch=true;
	             }
	         if (secAmmoType != null && secAmmoType == ammoType) {
	        	 ammoMatch=true;
	             }*/
	         }
		if (ammoMatch)
			dmg += ammoDamage;
		 if (Log.loggingDebug)
		        Log.debug("DamageEffect.apply:  dmg="+dmg+" ammoMatch="+ammoMatch+" ammoId="+ammoId+" ammoDamage="+ammoDamage);
		if (target != null) {
			Set<EffectState> effects = target.getCurrentEffects();
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(SleepEffect.class)) {
					AgisEffect.removeEffectByID(target, ef.getEffect().getID());
				}

			}
		}
		 if (caster.isUser()  && target != null && target.isUser() && !caster.getOid().equals(target.getOid())  && CombatPlugin.PVP_DAMAGE_REDUCTION_USE)	{
	     		dmg = (int)(dmg * CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
	     		Log.debug("COMBAT: DamageEffect  caster:"+caster+" target:"+target+" dmg * "+CombatPlugin.PVP_DAMAGE_REDUCTION_PERCENT);
	     		}
		 if (Log.loggingDebug)
		        Log.debug("DamageEffect.apply:  dmg="+dmg+" end");
        params.put("dmg", dmg);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	Log.debug("PULSE: running pulse for DamageEffect");
    	Map<String, Serializable> params = state.getParams();
    	int dmg = (int)params.get("dmg");
    	int result = (int)params.get("result");
		int claimID = -1;
		int objectID = -1;
		if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (Log.loggingDebug)
	        Log.debug("DamageEffect.pulse:  dmg="+dmg+" result="+result+" claimID="+claimID+" objectID="+objectID);
    	
    	CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
        if(target  == null) {
        	return;
        }
        Set<EffectState> effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
		Set<EffectState> ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
	 // Triggers Calculations
		for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
			}
		}
		if (!state.getSourceOid().equals(state.getTargetOid())) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.DAMAGE, tags, dmg, source, target, ef);
				}
			}
		}
		if (result == AgisAbility.RESULT_CRITICAL) {
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
				}
			}
			Log.debug("DamageEffect.pulse: Check Triggetson target");
			if (!state.getSourceOid().equals(state.getTargetOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						dmg = se.Calculate(TriggerProfile.Type.CRITICAL, tags, dmg, source, target, ef);
					}
				}
			}
		}
		if (target != null) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(SleepEffect.class)) {
					AgisEffect.removeEffectByID(target, ef.getEffect().getID());
				}

			}

			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(ShieldEffect.class)) {
					ShieldEffect se = (ShieldEffect) ef.getEffect();
					dmg = se.Calculate(source, target, dmg, getDamageProperty(), tags, ef);
				}
			}
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

		if (Log.loggingDebug)
	        Log.debug("DamageEffect.pulse: | dmg="+dmg);
		if (dmg > 0) {
        	
        	if(claimID>0 && objectID>0) {
        		
        	}else {
			
				int targetHealth = target.statGetCurrentValue(getDamageProperty());
				if (dmg >= targetHealth) {
					// Check if this will cause a duel defeat
					if (checkDuelDefeat(target, source, getDamageProperty())) {
						dmg = targetHealth - 1;
					} else {
						for (EffectState ef : ceffects) {
							if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
								TriggerEffect se = (TriggerEffect) ef.getEffect();
								dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
							}
						}
						Log.debug("DamageEffect.pulse: Check Triggetson target");
						if (!state.getSourceOid().equals(state.getTargetOid())) {
							for (EffectState ef : effects) {
								if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
									TriggerEffect se = (TriggerEffect) ef.getEffect();
									dmg = se.Calculate(TriggerProfile.Type.KILL, tags, dmg, source, target, ef);
								}
							}
						}
					}
				}

				// Log.error("DamageEffect.pulse params kkeys:"+params.keySet());
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
				int threat = dmg;
				if (threat == 0)
					threat = 1;
				Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(), state.getSource().getOwnerOid(), dmg, damageType, threat));

				
			//	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), dmg, -1);
			}
        	String abilityEvent = EventMessageHelper.COMBAT_DAMAGE;
        	EventMessageHelper.SendCombatEvent(source.getOwnerOid(), (target!=null?target.getOwnerOid():new OID()), abilityEvent, state.getAbilityID(), getID(), dmg, claimID, objectID+"", damageType);
    		
			double healD = dmg * transferModifier;
			Log.debug("PULSE: running pulse of DamageEffect dmg=" + dmg + ";  transferModifier=" + transferModifier + " healD=" + healD);

			int heal = (int) healD;
			Log.debug("PULSE: running pulse of DamageEffect dmg=" + dmg + ";  transferModifier=" + transferModifier + " heal=" + heal + " property=" + getDamageProperty());
			source.statModifyBaseValue(getDamageProperty(), heal);
			source.sendStatusUpdate();
			Log.debug("PULSE: running pulse of DamageEffect  running pulse Coord Effect");
			// If there is a pulse coord effect, run it now
			if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
				CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
				cE.sendSourceOid(true);
				cE.sendTargetOid(true);
				cE.putArgument("claimID", claimID);
				cE.putArgument("cObjectID", objectID);
				state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), target.getOwnerOid()));
			}
            Log.debug("PULSE: running pulse of DamageEffect  END"); 
        	
        }else {
            Log.debug("PULSE: running pulse of DamageEffect  dmg="+dmg+" no apply"); 

        }
        Log.debug("PULSE: running pulse of DamageEffect  END"); 
	    
    }
    
    // remove the effect from the object
    public void remove(EffectState state) {
	    //CombatInfo obj = state.getTarget();
	    // Remove the effectVal on the object

	    super.remove(state);
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

    public String getDamageType() { return damageType; }
    public void setDamageType(String damageType) { this.damageType = damageType; }
    protected String damageType = "";
    
    public float getDamageMod() { return DamageMod; }
    public void setDamageMod(float hps) { DamageMod = hps; }
    protected float DamageMod = 1.0f;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;
    
    public double getTransferModifier() { return transferModifier; }
    public void setTransferModifier(double modifier) { transferModifier = modifier; }
    protected double transferModifier = 1.0;

    
    // Effect Value that needs to be removed upon effect removal
    public void SetEffectVal(int effect) {
    	effectVal = effect;
    }
    public int GetEffectVal() {
        return effectVal;
    }
    public int effectVal = 0;
    
    private static final long serialVersionUID = 1L;
}
