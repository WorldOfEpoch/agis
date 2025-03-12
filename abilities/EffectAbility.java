package atavism.agis.abilities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import atavism.agis.core.*;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.effects.TriggerEffect;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.engine.OID;
import atavism.server.util.Log;

/**
 * This class is for abilities that do not cause some form of instant damage or require the player to physically
 * "hit" the player, but still will place an effect on an enemy target. As such to-hit calculations do need to 
 * be done. If you want a certain effect to always be applied regardless, use FriendlyEffectAbility.
 * @author Andrew Harrison
 *
 */
public class EffectAbility extends AgisAbility {
	static Random random = new Random();
//	protected LinkedList<AgisEffect> activationEffects;
//	public LinkedList<String> effectTarget;
	protected AgisEffect channelEffect;
	protected AgisEffect activeEffect;
	
    public EffectAbility(String name) {
        super(name);
      //  this.params = new HashMap<String, Serializable>();
//        this.activationEffects = new LinkedList<AgisEffect>();
//        this.effectTarget = new LinkedList<String>();
        this.activeEffect = null;
        this.channelEffect = null;
    }
    
    protected AbilityResult checkAbility(CombatInfo obj, CombatInfo target, ActivationState state, AgisAbilityState abilityState, boolean skipCheckCooldown) {

		AbilityResult result = super.checkAbility(obj, target, state,abilityState, skipCheckCooldown);
		if (Log.loggingDebug)
			Log.debug("EffectAbility.checkAbility: result: " + result);

		if (result != AbilityResult.SUCCESS) {
        	ExtendedCombatMessages.sendAbilityFailMessage(obj, result, getID(),costProp);
            return result;
        }
      //  this.params = new HashMap<String, Serializable>();
        
        return AbilityResult.SUCCESS;
    }
    
    HashMap<String, Serializable> runHitRoll(CombatInfo source, CombatInfo target, AgisAbilityState state) {
    	double rand = random.nextDouble();
		int hitRoll = (int)(rand * 100);
		HashMap<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("hitRoll", hitRoll);
		if (Log.loggingDebug)Log.debug("ZBEFFECTABILITY: rand value = " + rand + "; ZBhitChance = " + chance +"; hitRoll = "+hitRoll);
			Log.info("HITROLL: source = " + source + ", target = " + target);
		if (target == null || target.getState() == null) {
			attackerResult = AgisAbility.RESULT_HIT;
		} else if (target.getState().equals(CombatInfo.COMBAT_STATE_IMMUNE)) {
			attackerResult = AgisAbility.RESULT_IMMUNE;
			   Log.debug("ZBRESULT: set result to immune ");
		} else if (target.getState().equals(CombatInfo.COMBAT_STATE_EVADE)) {
			attackerResult = AgisAbility.RESULT_EVADED;
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.EVADE, tags, 0, source, target,this);
			   Log.debug("ZBRESULT: set result to evade ");
		} 
		else if (rand < getMissChance()/100f) {
			attackerResult = AgisAbility.RESULT_MISSED;
			getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect("missed");
			   Log.debug("ZBRESULT: set result to missed ");
			casterResultEffect = getResultVal("missed", true);
			targetResultEffect = getResultVal("missed", false);
			if (casterResultEffect != null && targetResultEffect != null) {
				if (Log.loggingDebug) Log.debug("RESULT: set result to missed with caster effect: " + casterResultEffect.getName() + " and target effect: " + targetResultEffect.getName());
			}
			Set<EffectState> effects = target.getCurrentEffects() != null ? new HashSet<EffectState>(target.getCurrentEffects()) : new HashSet<EffectState>();
			Set<EffectState> ceffects = source.getCurrentEffects() != null ? new HashSet<EffectState>(source.getCurrentEffects()) : new HashSet<EffectState>();
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					se.Calculate(TriggerProfile.Type.MISS, tags, 0, source, target, ef);
				}
			}

			Log.debug("EffectAbility.runHitRoll: Check Triggetson target");
			if (!source.getOid().equals(target.getOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.MISS, tags, 0, source, target, ef);
					}
				}
			}
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.MISS, tags, 0, source, target,this);
			CombatClient.sendCombatEvent(source.getOid(), true, TriggerProfile.Type.MISS);
			CombatClient.sendCombatEvent(target.getOid(), false, TriggerProfile.Type.MISS);
		} else if (rand < (1-chance)) {
			attackerResult = AgisAbility.RESULT_DODGED;
			getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect("dodged");
			Log.debug("ZBRESULT: set result to dodged ");
			casterResultEffect = getResultVal("dodged", true);
			targetResultEffect = getResultVal("dodged", false);
			if (casterResultEffect != null && targetResultEffect != null) {
				if (Log.loggingDebug)Log.debug("RESULT: set result to dodged with caster effect: " + casterResultEffect.getName() + " and target effect: " + targetResultEffect.getName());
			}
			Set<EffectState> effects = target.getCurrentEffects() != null ? new HashSet<EffectState>(target.getCurrentEffects()) : new HashSet<EffectState>();
			Set<EffectState> ceffects = source.getCurrentEffects() != null ? new HashSet<EffectState>(source.getCurrentEffects()) : new HashSet<EffectState>();
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					se.Calculate(TriggerProfile.Type.DODGE, tags, 0, source, target, ef);
				}
			}
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.DODGE, tags, 0, source, target,this);
			CombatClient.sendCombatEvent(source.getOid(), true, TriggerProfile.Type.DODGE);
			CombatClient.sendCombatEvent(target.getOid(), false, TriggerProfile.Type.DODGE);
			Log.debug("EffectAbility.runHitRoll: Check Triggetson target");
			if (!source.getOid().equals(target.getOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.DODGE, tags, 0, source, target, ef);
					}
				}
			}
		}
		else {
			   Log.debug("ZBRESULT: set result to hit ");

			attackerResult = AgisAbility.RESULT_HIT;
		}
		
		// Only send skill up chance when hit roll is over 80
    	Log.debug("SKILL: hit roll is: " + hitRoll);
		if (hitRoll > getSkillUpChance()) {
			sendSkillUpChance = false;
		}
		return params;
    }
    
  //  private HashMap<String, Serializable> params = null;
    private AgisEffect casterResultEffect = null;
    private AgisEffect targetResultEffect = null;
    private int attackerResult = 0;
    
    // Copied from EffectAbility
//    public LinkedList<AgisEffect> getActivationEffect() {
//        return this.activationEffects;
//    }
//    public void addActivationEffect(AgisEffect effect) {
//    	this.activationEffects.add(effect);
//    }
//
//    public void addEffectTarget(String target) {
//    	effectTarget.add(target);
//    }
//    public LinkedList<String> getEffectTarget() {
//        return effectTarget;
//    }
    
    public float getChance() {return chance;}
    public void setChance(float chance) {this.chance = chance;}
    protected float chance = 1f;

    public AgisEffect getChannelEffect() { return this.channelEffect; }
    public void setChannelEffect(AgisEffect effect) { this.channelEffect = effect; }

    public AgisEffect getActiveEffect() { return this.activeEffect; }
    public void setActiveEffect(AgisEffect effect) { this.activeEffect = effect; }

    public void completeActivation(AgisAbilityState state) {
        
    	super.completeActivation(state);
    	runCasterActivation(state);
     	runTargetActivation(state);
    }
    	
    
	public void runCasterActivation(AgisAbilityState state) {
		long start = System.nanoTime();
		if (Log.loggingDebug)
			Log.debug("EffectAbility: runCasterActivation " + state + " " );
		boolean effectApplyed = false;
		if (targetType == TargetType.LOCATION) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			CombatInfo targetInfo = state.getSource();
			HashMap<String, Serializable> params = runHitRoll(state.getSource(), targetInfo,state);
			params.put("result", 1);
			params.put("skillType", skillType);
			params.put("hitRoll", 0);
			params.put("location", state.getLocation());
			params.put("dmgType", damageType);
			params.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
					state.addEffectState(eState);
					effectApplyed = true;
				}
			}
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
				AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
				if(aad.getTarget().equals("caster") && aad.CanBeApply()){
					AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}
//			for (int i = 0; i < activationEffects.size(); i++) {
//				String target = effectTarget.get(i);
//				targetInfo = state.getSource();
//				// Log.error("RESULT: adding param result with value: " + attackerResult + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//				AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//				effectApplyed = true;
//			}
		} else if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			HashMap<String, Serializable> params =runHitRoll(state.getSource(), null,state);
			params.put("skillType", skillType);
			params.put("result", attackerResult);
			params.put("dmgType", damageType);
			params.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
					state.addEffectState(eState);
					effectApplyed = true;
				}
			}
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
				AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
				if(aad.getTarget().equals("caster") && aad.CanBeApply()){
					AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}
//			for (int i = 0; i < activationEffects.size(); i++) {
//				String target = effectTarget.get(i);
//				if (target.equals("caster")) {
//					AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), state.getSource(), getID(), params, state.getItem());
//					effectApplyed = true;
//				}
//			}
		} else {
			// Here we need to add in the effectsList to the parameters
			CombatInfo targetInfo = state.getTarget();
			HashMap<String, Serializable> params = runHitRoll(state.getSource(), targetInfo,state);
			params.put("skillType", skillType);
			params.put("result", attackerResult);
			params.put("dmgType", damageType);
			params.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
					state.addEffectState(eState);
					effectApplyed = true;
				}
			}
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
				AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
				if(aad.getTarget().equals("caster") && aad.CanBeApply()){
					AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}
//			for (int i = 0; i < activationEffects.size(); i++) {
//				String target = effectTarget.get(i);
//				if (target.equals("caster")) {
//					targetInfo = state.getSource();
//					AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//					effectApplyed = true;
//				}
//			}
			state.getTarget().setCombatState(true);

			// Finally, lets try auto-attack this mob
			CombatInfo info = state.getSource();
			CombatInfo target = state.getTarget();
			if (!info.getOwnerOid().equals(target.getOwnerOid())) {
				OID oid = target.getOwnerOid();
				info.setAutoAttack(oid);
			}
		}
		// Finally, lets try auto-attack this mob if it is not the caster themselves
		if (Log.loggingDebug)
			Log.debug("EffectAbility.runCasterActivation: effectApplyed=" + effectApplyed + " usedAbility=" + state.usedAbility + " skillType=" + skillType + " skillExp=" + skillExp + " sendSkillUpChance="
					+ sendSkillUpChance);

		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				Log.debug("SKILL: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}

		}
	}

	public void runTargetActivation(AgisAbilityState state) {
		runTargetActivation(state, targets);
	}
  
    public void runTargetActivation(AgisAbilityState state, ArrayList<CombatInfo> targets) {
		long start = System.nanoTime();
		if (Log.loggingDebug) Log.debug("EffectAbility: runTargetActivation " + state + " " );

		boolean effectApplyed = false;
		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			for (CombatInfo ci : targets) {
				requiredLocks.add(ci.getLock());
			}
			// ObjectLockManager.lockAll(requiredLocks);
			for (CombatInfo targetInfo : targets) {
				if (targetInfo == null) {
					continue;
				}
				if (CheckTargetState(targetInfo) != AbilityResult.SUCCESS) {
					Log.debug("AOE: target is Check Target State failed");
					continue;
				}
				if (!targetInfo.attackable()) {
					Log.debug("AOE: target " + targetInfo.getOid() + " is not attackable");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					Log.debug("ABILITY: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {

					HashMap<String, Serializable> params = runHitRoll(state.getSource(), targetInfo,state);
					CombatPlugin.addAttacker(targetInfo.getOid(), state.getSourceOid());
					params.put("skillType", skillType);
					params.put("result", attackerResult);
					params.put("dmgType", damageType);
					params.put("powerUp",state.getPowerUptime());
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if (aed.getTarget().equals("target") && aed.CanBeApply()) {
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if (aad.getTarget().equals("target") && aad.CanBeApply()) {
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, null, state.getLocation(), state.getClaimID(), state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (!target.equals("caster")) {
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//							effectApplyed = true;
//						}
//					}
					if (effectApplyed)
						state.countapplyed++;
					targetInfo.setCombatState(true);
					AgisInventoryClient.equippedItemUsed(targetInfo.getOid(), "Defend");
				}
			}
			for (CombatBuildingTarget targetInfo : buildingTargets) {
				if (targetInfo == null) {
					Log.debug("ComabtMeleAbility: runTargetActivation: targetInfo is null");
					continue;
				}
				HashMap<String, Serializable> params = new HashMap<String, Serializable>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				//params.put("hitRoll", hitRoll);
				params.put("location", state.getLocation());
				params.put("claimID", targetInfo.getClaimId());
				params.put("objectID", targetInfo.getObjectId());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if (aed.getTarget().equals("target") && aed.CanBeApply()) {
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), null, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if (aad.getTarget().equals("target") && aad.CanBeApply()) {
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), null, null, state.getLocation(), targetInfo.getClaimId(), targetInfo.getObjectId(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						CombatInfo ci = null;
//						if (!target.equals("caster")) {
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), null, getID(), params, state.getItem());
//							effectApplyed = true;
//						}
//					}
					if (effectApplyed)
						state.countapplyed++;
				}
			}

			// ObjectLockManager.unlockAll(requiredLocks);
		} else {
			if (state.getClaimID() > 0 && state.getClaimObjID() > 0) {
				HashMap<String, Serializable> params = new HashMap<String, Serializable>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				// params.put("hitRoll", hitRoll);
				params.put("claimID", state.getClaimID());
				params.put("objectID", state.getClaimObjID());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if (aed.getTarget().equals("target") && aed.CanBeApply()) {
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), null, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if (aad.getTarget().equals("target") && aad.CanBeApply()) {
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), null, null, state.getLocation(), state.getClaimID(), state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//				if (activationEffects == null)
//					if (Log.loggingWarn)
//						Log.warn("EffectAbility: runTargetActivation activationEffects is null "+getID() + ":" +getName() + " " + state);
//				Log.debug("EffectAbility: runTargetActivation start apply effects to claim object");
//
//				if (activationEffects != null)
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (Log.loggingDebug)
//							Log.debug("EffectAbility: runTargetActivation apply effect target=" + target);
//						if (!target.equals("caster")) {
//							if (Log.loggingDebug)
//								Log.debug("EffectAbility: runTargetActivation adding param result with value: " + attackerResult + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), null, getID(), params, state.getItem());
//							if (Log.loggingDebug)
//								Log.debug("EffectAbility: runTargetActivation applayed effect " + activationEffects.get(i).getName());
//							effectApplyed = true;
//						}
				}

			} else {
				// Here we need to add in the effectsList to the parameters
				CombatInfo targetInfo = state.getTarget();
				HashMap<String, Serializable> params = runHitRoll(state.getSource(), targetInfo,state);
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if (aed.getTarget().equals("target") && aed.CanBeApply()) {
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if (aad.getTarget().equals("target") && aad.CanBeApply()) {
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, null, state.getLocation(), state.getClaimID(), state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//					String target = effectTarget.get(i);
//					if (!target.equals("caster")) {
//						targetInfo = state.getTarget();
//						AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//						effectApplyed = true;
				}

				state.getTarget().setCombatState(true);

				// Finally, lets try auto-attack this mob
				CombatInfo info = state.getSource();
				CombatInfo target = state.getTarget();
				// OID oid = target.getOwnerOid();
				if (!info.getOwnerOid().equals(target.getOwnerOid())) {
					OID oid = target.getOwnerOid();
					info.setAutoAttack(oid);
				}
			}
		}
		if (Log.loggingDebug)
			Log.debug("EffectAbility.runTargetActivation: effectApplyed=" + effectApplyed + " usedAbility=" + state.usedAbility + " skillType=" + skillType + " skillExp=" + skillExp + " sendSkillUpChance="
					+ sendSkillUpChance);

		// Finally, lets try auto-attack this mob if it is not the caster themselves
		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				Log.debug("SKILL: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}

		}
	}


    public void pulseChannelling(AgisAbilityState state) {
        super.pulseChannelling(state);
        if (Log.loggingDebug)	Log.debug("EffectAbility.pulseChannelling "+state+" ");
    	runCasterActivation(state);
     	runTargetActivation(state);
    }

	public void pulseCaster(AgisAbilityState state) {
		super.pulseCaster(state);
		if (Log.loggingDebug)Log.debug("EffectAbility.pulseCaster " + state + " "  );
		runCasterActivation(state);
	}

	public void pulseTarget(AgisAbilityState state) {
		super.pulseTarget(state);
		if (Log.loggingDebug)Log.debug("EffectAbility.pulseTarget " + state + " " );
		runTargetActivation(state);
	}
       
	public void pulseTarget(AgisAbilityState state, ArrayList<CombatInfo> targets) {
		super.pulseTarget(state);
		if (Log.loggingDebug)Log.debug("EffectAbility.pulseTarget " + state + " " );
		runTargetActivation(state, targets);
	}
       
    public void pulseActivated(AgisAbilityState state) {
        super.pulseActivated(state);
    }
}