package atavism.agis.abilities;

import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.agis.core.*;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.*;
import atavism.agis.effects.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * Runs the checks and calculations for Attack Abilities and applies the
 * effects.
 * 
 *
 */
public class AttackAbility extends AgisAbility {
	static Random random = new Random();

	public AttackAbility(String name) {
		super(name);
	}

	protected AbilityResult checkAbility(CombatInfo obj, CombatInfo target, ActivationState state, AgisAbilityState abilityState, boolean skipCheckCooldown) {
		AbilityResult result = super.checkAbility(obj, target, state, abilityState, skipCheckCooldown);

		// Check basic results
		if (result != AbilityResult.SUCCESS) {
			if (Log.loggingDebug)
				Log.debug("AttackAbility.checkAbility: ABILITY: ability failed. Reason: " + result);
			ExtendedCombatMessages.sendAbilityFailMessage(obj, result, getID(), costProp);
			return result;
		}

		return AbilityResult.SUCCESS;
	}

	void runHitRoll(CombatInfo source, CombatInfo target,AgisAbilityState state) {
		getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect("success");

		/*
		 * How this works: We start with a base chance to hit (95%) and a chance to miss
		 * (5%) Now from the hit % we take away the chance to be dodged, parried,
		 * blocked. Then we take away the crit chance from the remaining hit chance. If
		 * the crit chance is higher than the remaining hit chance then the crit chance
		 * will only be as big as the hit chance.
		 */

		// Check if the target has an appropriate weapon equipped and defensive skill
		String defensiveType = "dodged";
		//ArrayList<String> weaponType = new ArrayList<String>();
		//if(target.getPropertyMap().containsKey("weaponType"))
		//		weaponType = (ArrayList<String>)target.getProperty("weaponType");
		//String weapType = target.getStringProperty("weaponType");
		boolean hasParry = false;
		try {
			hasParry = target.getBooleanProperty("weaponParry");
		} catch (Exception e1) {
			Log.debug("AttackAbility.runHitRoll: Target dont have param parry");
		}

		double parryChance = 0;
		if (hasParry == true) {
			double accuracy = 0, parry = 0;
			if (CombatPlugin.DAMAGE_TYPES.containsKey(damageType) && CombatPlugin.DAMAGE_TYPES.get(damageType).getAccuracyStat() != null) {
				accuracy = source.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(damageType).getAccuracyStat());
			}
			/*if (CombatPlugin.PHYSICAL_ACCURACY_STAT != null) {
				accuracy = source.statGetCurrentValue(CombatPlugin.PHYSICAL_ACCURACY_STAT);
			}*/
			if (CombatPlugin.PARRY_STAT != null) {
				parry = target.statGetCurrentValueWithPrecision(CombatPlugin.PARRY_STAT);

			}
			int targetLevel = target.statGetCurrentValue("level");
			int casterLevel = source.statGetCurrentValue("level");
			double statsDiff = ((parry - accuracy) / CombatPlugin.HIT_CHANCE_POINT_PER_PERCENTAGE);
			if (Log.loggingDebug)
				Log.debug("AttackAbility.runHitRoll:: Parry chance accuracy:" + accuracy + " parry:" + parry + " HIT_CHANCE_POINT_PER_PERCENTAGE:" + CombatPlugin.HIT_CHANCE_POINT_PER_PERCENTAGE);
			if (statsDiff > CombatPlugin.PARRY_PERCENTAGE_CAP)
				statsDiff = CombatPlugin.PARRY_PERCENTAGE_CAP;
			if (statsDiff < 0)
				statsDiff = 0;
			double levelDiff = (targetLevel - casterLevel) * CombatPlugin.HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL;
			if (Log.loggingDebug)
				Log.debug("AttackAbility.runHitRoll:: Parry chance targetLevel:" + targetLevel + " casterLevel:" + casterLevel + " HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL:"
						+ CombatPlugin.HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL);
			if (levelDiff < 0)
				levelDiff = 0;
			parryChance = 0.05 + (statsDiff / 100f + levelDiff / 100f);
			if (Log.loggingDebug)
				Log.debug("AttackAbility.runHitRoll:: Parry Chance:" + parryChance + " levelDiff%:" + levelDiff + " statsDiff%:" + statsDiff + " target.isMob:" + target.isMob() + " caster.isMob:" + source.isMob());

			defensiveType = "parried";
		}

		double hitChance = CombatHelper.CalcHitChance(source, target, skillType, damageType, this);

		double rand = random.nextDouble();
		if (Log.loggingDebug)
			Log.debug("COMBATMELEE: random value = " + rand + "; hitChance = " + hitChance);
		hitRoll = (int) (rand * 100);
		Log.info("AttackAbility.runHitRoll: HITROLL: source = " + source + ", target = " + target);
		if (target == null || target.getState() == null) {
			attackerResult = AgisAbility.RESULT_HIT;
		} else if (target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_IMMUNE)) {
			attackerResult = AgisAbility.RESULT_IMMUNE;
		} else if (target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_EVADE)) {
			attackerResult = AgisAbility.RESULT_EVADED;
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.EVADE, tags, 0, source, target,this);
		} else if (rand < getMissChance() / 100f) {
			attackerResult = AgisAbility.RESULT_MISSED;
			getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect("missed");
			casterResultEffect = getResultVal("missed", true);
			targetResultEffect = getResultVal("missed", false);
			if (casterResultEffect != null && targetResultEffect != null) {
				if (Log.loggingDebug)
					Log.debug("AttackAbility.runHitRoll: RESULT: set result to missed with caster effect: " + casterResultEffect.getName() + " and target effect: " + targetResultEffect.getName());
			}

			Set<EffectState> effects = target.getCurrentEffects() != null ? new HashSet<EffectState>(target.getCurrentEffects()) : new HashSet<EffectState>();
			Set<EffectState> ceffects = source.getCurrentEffects() != null ? new HashSet<EffectState>(source.getCurrentEffects()) : new HashSet<EffectState>();

			Log.debug("AttackAbility.runHitRoll: Miss Check Triggets on caster");
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					se.Calculate(TriggerProfile.Type.MISS, tags, 0, source, target, ef);
				}
			}

			Log.debug("AttackAbility.runHitRoll: Miss Check Triggetson target");
			if (!source.getOid().equals(target.getOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.MISS, tags, 0, source, target, ef);
					}
				}
			}
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.MISS, tags, 0, source, target, this);
			CombatClient.sendCombatEvent(source.getOid(), true, TriggerProfile.Type.MISS);
			CombatClient.sendCombatEvent(target.getOid(), false, TriggerProfile.Type.MISS);
		} else if (rand < parryChance) {
			attackerResult = AgisAbility.RESULT_PARRIED;
			casterResultEffect = getResultVal("parried", true);
			targetResultEffect = getResultVal("parried", false);
			if (casterResultEffect != null && targetResultEffect != null) {
				if (Log.loggingDebug)
					Log.debug("RESULT: set result to parried with caster effect: " + casterResultEffect.getName() + " and target effect: " + targetResultEffect.getName());
			}
			Set<EffectState> effects = target.getCurrentEffects() != null ? new HashSet<EffectState>(target.getCurrentEffects()) : new HashSet<EffectState>();
			Set<EffectState> ceffects = source.getCurrentEffects() != null ? new HashSet<EffectState>(source.getCurrentEffects()) : new HashSet<EffectState>();
			Log.debug("AttackAbility.runHitRoll: Parry Check Triggets on caster");
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					se.Calculate(TriggerProfile.Type.PARRY, tags, 0, source, target, ef);
				}
			}

			Log.debug("AttackAbility.runHitRoll: Parry Check Triggets on target");
			if (!source.getOid().equals(target.getOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.PARRY, tags, 0, source, target, ef);
					}
				}
			}
			getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.PARRY, tags, 0, source, target,this);
			CombatClient.sendCombatEvent(target.getOid(), true, TriggerProfile.Type.PARRY);
			CombatClient.sendCombatEvent(source.getOid(), false, TriggerProfile.Type.PARRY);
		} else if (rand < (1 - hitChance)) {
			getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect(defensiveType);
			if (defensiveType.equals("dodged")) {
				attackerResult = AgisAbility.RESULT_DODGED;
				getPowerUpDefinition(state.getPowerUptime()).changeCoordinatedEffect("dodged");
				casterResultEffect = getResultVal("dodged", true);
				targetResultEffect = getResultVal("dodged", false);
				if (casterResultEffect != null && targetResultEffect != null) {
					Log.debug("RESULT: set result to dodged with caster effect: " + casterResultEffect.getName() + " and target effect: " + targetResultEffect.getName());
				}

				Set<EffectState> effects = target.getCurrentEffects() != null ? new HashSet<EffectState>(target.getCurrentEffects()) : new HashSet<EffectState>();
				Set<EffectState> ceffects = source.getCurrentEffects() != null ? new HashSet<EffectState>(source.getCurrentEffects()) : new HashSet<EffectState>();
				Log.debug("AttackAbility.runHitRoll: dodged Check Triggets on caster");
				for (EffectState ef : ceffects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.DODGE, tags, 0, source, target, ef);
					}
				}

				Log.debug("AttackAbility.runHitRoll: dodged Check Triggets on target");
				if (!source.getOid().equals(target.getOid())) {
					for (EffectState ef : effects) {
						if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
							TriggerEffect se = (TriggerEffect) ef.getEffect();
							se.Calculate(TriggerProfile.Type.DODGE, tags, 0, source, target, ef);
						}
					}
				}
				getPowerUpDefinition(state.getPowerUptime()).CalculateTriggers(TriggerProfile.Type.DODGE, tags, 0, source, target,this);
				CombatClient.sendCombatEvent(target.getOid(), true,  TriggerProfile.Type.DODGE);
				CombatClient.sendCombatEvent(source.getOid(), false, TriggerProfile.Type.DODGE);

			}
		} else {
			attackerResult = AgisAbility.RESULT_HIT;
		}

		// Only send skill up chance when hit roll is over 80
		if (Log.loggingDebug)
			Log.debug("SKILL: hit roll is: " + hitRoll);
		if (hitRoll > getSkillUpChance()) {
			sendSkillUpChance = false;
		}
	}

	private AgisEffect casterResultEffect = null;
	private AgisEffect targetResultEffect = null;
	private int attackerResult = 0;
	private int hitRoll = 0;

	// Does this ability require any special positions?
	public void setShieldReq(int shieldNeeded) {
		shieldReq = shieldNeeded;
	}

	public int getShieldReq() {
		return shieldReq;
	}

	public int shieldReq = 0;

	// Copied from EffectAbility
//	public LinkedList<AgisEffect> getActivationEffect() {
//		return activationEffects;
//	}
//
//	public void addActivationEffect(AgisEffect effect) {
//		if (activationEffects == null) {
//			activationEffects = new LinkedList<AgisEffect>();
//		}
//		activationEffects.add(effect);
//	}
//
//	protected LinkedList<AgisEffect> activationEffects = null;

//	public void addEffectTarget(String target) {
//		if (effectTarget == null) {
//			effectTarget = new LinkedList<String>();
//		}
//		effectTarget.add(target);
//	}
//
//	public LinkedList<String> GetEffectTarget() {
//		return effectTarget;
//	}
//
//	public LinkedList<String> effectTarget = null;

	public AgisEffect getChannelEffect() {
		return channelEffect;
	}

	public void setChannelEffect(AgisEffect effect) {
		this.channelEffect = effect;
	}

	protected AgisEffect channelEffect = null;

	public AgisEffect getActiveEffect() {
		return activeEffect;
	}

	public void setActiveEffect(AgisEffect effect) {
		this.activeEffect = effect;
	}

	protected AgisEffect activeEffect = null;

	public void completeActivation(AgisAbilityState state) {

		super.completeActivation(state);
		if (Log.loggingDebug)
			Log.debug("AttackAbility.completeActivation " + state + " " );
		runCasterActivation(state);
		runTargetActivation(state);
	}

	public void runCasterActivation(AgisAbilityState state) {
		if (Log.loggingDebug)
			Log.debug("AttackAbility.runCasterActivation: " + state + " " );

		long start = System.nanoTime();
		boolean effectApplyed = false;

		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			Log.debug("AttackAbility.runCasterActivation: AREA_ENEMY");
			try {
				runHitRoll(state.getSource(), state.getSource(), state);
				Map<String, Serializable>params = new HashMap<>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("hitRoll", hitRoll);
				params.put("location", state.getLocation());
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
						AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
						state.addAbilitiesState(aState);
						effectApplyed = true;
					}
				}
//				for (int i = 0; i < activationEffects.size(); i++) {
//					String target = effectTarget.get(i);
//					CombatInfo ci = null;
//					if (target.equals("caster")) {
//						ci = state.getSource();
//						AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), ci, getID(), params, state.getItem());
//						effectApplyed = true;
//					}
//				}
			} catch (Exception e) {
				Log.exception("runCasterActivation", e);
				e.printStackTrace();
			}

			Log.debug("AttackAbility.runCasterActivation: AREA_ENEMY END");

		} else {
			// If this ability doesn't require a target, and the player doesn't have a
			// target (they have targeted themselves), don't apply any effects
			if (!reqTarget && state.getSourceOid().equals(state.getTargetOid())) {
				if (Log.loggingDebug)
					Log.debug("AttackAbility.runCasterActivation: reqTarget is false target is same as caster, don't apply any effects ; targetType:" + targetType);
				return;
			}
			// Here we need to add in the effectsList to the parameters
			CombatInfo targetInfo = state.getTarget();

			runHitRoll(state.getSource(), targetInfo,state);
			// Lets try auto-attack this mob and add to attackMaplist for questmobs
			Log.debug("AttackAbility.runCasterActivation try auto attack");
			CombatInfo sourceInfo = state.getSource();
			sourceInfo.setAutoAttack(targetInfo.getOid());
            Map<String, Serializable>params = new HashMap<>();
			params.put("skillType", skillType);
			params.put("result", attackerResult);
			params.put("hitRoll", hitRoll);
			params.put("location", state.getLocation());
			params.put("dmgType", damageType);
			params.put("powerUp",state.getPowerUptime());
			Log.debug("AttackAbility.runCasterActivation start apply effects");
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
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}
//			if (activationEffects != null)
//				for (int i = 0; i < activationEffects.size(); i++) {
//					String target = effectTarget.get(i);
//					if (target.equals("caster")) {
//						targetInfo = state.getSource();
//						if (Log.loggingDebug)Log.debug("AttackAbility.runCasterActivation adding param result with value: " + attackerResult + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//						AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//						if (Log.loggingDebug)Log.debug("AttackAbility.runCasterActivation: applayed effect " + activationEffects.get(i).getName());
//						effectApplyed = true;
//					}
//				}

			state.getTarget().setCombatState(true);
			Log.debug("AttackAbility.runCasterActivation: try auto attack");

			// Finally, lets try auto-attack this mob
			CombatInfo info = state.getSource();
			CombatInfo target = state.getTarget();
			OID oid = target.getOwnerOid();
			info.setAutoAttack(oid);
			AgisInventoryClient.equippedItemUsed(state.getTargetOid(), "Defend");
			Log.debug("AttackAbility.runCasterActivation: ");

		}

		if (Log.loggingDebug)
			Log.debug("AttackAbility.runCasterActivation: effectApplyed=" + effectApplyed + " usedAbility=" + state.usedAbility + " skillType=" + skillType + " skillExp=" + skillExp + " sendSkillUpChance="
					+ sendSkillUpChance);

		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				if (Log.loggingDebug)
					Log.debug("AttackAbility.runCasterActivation: just before sending ability Used skillType=" + skillType + " skillExp=" + skillExp);
				Log.debug("AttackAbility.runCasterActivation: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}
		}

		long end = System.nanoTime();
		long microseconds = (end - start) / 1000;
		Log.debug("AgisAbility.runCasterActivation: microseconds: " + microseconds);
	}

	public void runTargetActivation(AgisAbilityState state) {
		runTargetActivation(state, targets);
	}

	public void runTargetActivation(AgisAbilityState state, ArrayList<CombatInfo> targets) {
		long start = System.nanoTime();
		if (Log.loggingDebug)
			Log.debug("AttackAbility.runTargetActivation: " + state + " " );

		boolean effectApplyed = false;

		Collections.sort(targets, new Comparator<CombatInfo>() {
			@Override
			public int compare(CombatInfo o1, CombatInfo o2) {
				return o1.getOid().compareTo(o2.getOid());
			}
		});
		/*for (CombatInfo ci : targets) {
			if (Log.loggingDebug)
				Log.debug("completeActivation: " + ci + " lock:" + ci.getLock() + " Thread" + Thread.currentThread().getName());
		}*/
		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			for (CombatInfo ci : targets) {
				requiredLocks.add(ci.getLock());
			}
			// ObjectLockManager.lockAll(requiredLocks);
			for (CombatInfo targetInfo : targets) {
				if (targetInfo == null) {
					Log.debug("AttackAbility.runTargetActivation: targetInfo is null");
					continue;
				}
				if (CheckTargetState(targetInfo) != AbilityResult.SUCCESS) {
					Log.debug("AttackAbility.runTargetActivation: check target state failed");
					continue;
				}
				if (!targetInfo.attackable()) {
					Log.debug("AttackAbility.runTargetActivation: target " + targetInfo.getOid() + " is not attackable");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					Log.debug("AttackAbility.runTargetActivation: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}
				runHitRoll(state.getSource(), targetInfo,state);
				CombatPlugin.addAttacker(targetInfo.getOid(), state.getSourceOid());

                Map<String, Serializable>params = new HashMap<>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("hitRoll", hitRoll);
				params.put("location", state.getLocation());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if(aed.getTarget().equals("target") && aed.CanBeApply()){
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if(aad.getTarget().equals("target") && aad.CanBeApply()){
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						CombatInfo ci = null;
//						if (!target.equals("caster")) {
//							ci = targetInfo;
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), ci, getID(), params, state.getItem());
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
					Log.debug("AttackAbility.runTargetActivation: targetInfo is null");
					continue;
				}
				if (Log.loggingDebug)
					Log.error("AttackAbility.runTargetActivation: targetInfo = " + targetInfo);
                Map<String, Serializable>params = new HashMap<>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("hitRoll", hitRoll);
				params.put("location", state.getLocation());
				params.put("claimID", targetInfo.getClaimId());
				params.put("objectID", targetInfo.getObjectId());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if(aed.getTarget().equals("target") && aed.CanBeApply()){
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), null, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if(aad.getTarget().equals("target") && aad.CanBeApply()){
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), null, state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
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
		} else {
			if (state.getClaimID() > 0 && state.getClaimObjID() > 0) {
                Map<String, Serializable>params = new HashMap<>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("hitRoll", hitRoll);
				params.put("claimID", state.getClaimID());
				params.put("objectID", state.getClaimObjID());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
					AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
					if(aed.getTarget().equals("target") && aed.CanBeApply()){
						AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
						AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), null, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
						state.addEffectState(eState);
						effectApplyed = true;
					}
				}
				for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
					AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
					if(aad.getTarget().equals("target") && aad.CanBeApply()){
						AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
						AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), null, state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
						state.addAbilitiesState(aState);
						effectApplyed = true;
					}
				}
//				if (activationEffects == null)
//					if(Log.loggingWarn)
//						Log.warn("AttackAbility.runTargetActivation: activationEffects is null "+getID() + ":" +getName() + " "  + state);
//				Log.debug("AttackAbility.runTargetActivation: start apply effects to claim object");
//				if (activationEffects != null)
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (Log.loggingDebug)
//							Log.debug("AttackAbility.runTargetActivation: apply effect target=" + target);
//						if (!target.equals("caster")) {
//							if (Log.loggingDebug)
//								Log.debug("AttackAbility.runTargetActivation: adding param result with value: " + attackerResult + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), null, getID(), params, state.getItem());
//							if (Log.loggingDebug)
//								Log.debug("AttackAbility.runTargetActivation: applayed effect " + activationEffects.get(i).getName());
//							effectApplyed = true;
//						}
//					}

			} else {
				// If this ability doesn't require a target, and the player doesn't have a
				// target (they have targeted themselves), don't apply any effects
				if (!reqTarget && state.getSourceOid().equals(state.getTargetOid())) {
					Log.debug("AttackAbility.runTargetActivation: reqTarget is false target is same as caster, don't apply any effects ; targetType:" + targetType);
					return;
				}
				// Here we need to add in the effectsList to the parameters
				CombatInfo targetInfo = state.getTarget();

				runHitRoll(state.getSource(), targetInfo,state);
				// Lets try auto-attack this mob and add to attackMaplist for questmobs
				Log.debug("AttackAbility.runTargetActivation: try auto attack");
				CombatInfo sourceInfo = state.getSource();
				sourceInfo.setAutoAttack(targetInfo.getOid());
                Map<String, Serializable>params = new HashMap<>();
				params.put("skillType", skillType);
				params.put("result", attackerResult);
				params.put("hitRoll", hitRoll);
				params.put("claimID", state.getClaimID());
				params.put("objectID", state.getClaimObjID());
				params.put("location", state.getLocation());
				params.put("dmgType", damageType);
				params.put("powerUp",state.getPowerUptime());
				Log.debug("AttackAbility.runTargetActivation: start apply effects");
				for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
					AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
					if(aed.getTarget().equals("target") && aed.CanBeApply()){
						AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
						AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(params), state.getItem(), aed.getDelay());
						state.addEffectState(eState);
						effectApplyed = true;
					}
				}
				for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
					AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
					if(aad.getTarget().equals("target") && aad.CanBeApply()){
						AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
						AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, state.getItem(), state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
						state.addAbilitiesState(aState);
						effectApplyed = true;
					}
				}
//				if (activationEffects != null)
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (Log.loggingDebug)
//							Log.debug("AttackAbility.runTargetActivation: apply effect target=" + target);
//						if (!target.equals("caster")) {
//							if (Log.loggingDebug)
//								Log.debug("AttackAbility.runTargetActivation: adding param result with value: " + attackerResult + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//							AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), params, state.getItem());
//							if (Log.loggingDebug)
//								Log.debug("AttackAbility.runTargetActivation: applayed effect " + activationEffects.get(i).getName());
//							effectApplyed = true;
//						}
//					}
				Log.debug("AttackAbility.runTargetActivation: end apply effects");

				state.getTarget().setCombatState(true);
				Log.debug("AttackAbility.runTargetActivation: try auto attack");

				// Finally, lets try auto-attack this mob
				CombatInfo info = state.getSource();
				CombatInfo target = state.getTarget();
				OID oid = target.getOwnerOid();
				info.setAutoAttack(oid);

				AgisInventoryClient.equippedItemUsed(state.getTargetOid(), "Defend");
			}
			Log.debug("AttackAbility.runTargetActivation: ");

		}
		// Item Used message is not sent if the target is being hit by their dueling
		// partner
		if (Log.loggingDebug) {
			Log.debug("AttackAbility.runTargetActivation: effectApplyed=" + effectApplyed + " usedAbility=" + state.usedAbility + " skillType=" + skillType + " skillExp=" + skillExp + " sendSkillUpChance="
					+ sendSkillUpChance);
		}
		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				if (Log.loggingDebug)
					Log.debug("AttackAbility.runTargetActivation: just before sending ability Used skillType=" + skillType + " skillExp=" + skillExp);
				if (Log.loggingDebug)
					Log.debug("AttackAbility.runTargetActivation: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}
		}

		long end = System.nanoTime();
		long microseconds = (end - start) / 1000;
		if (Log.loggingDebug)
			Log.debug("AgisAbility.runTargetActivation: microseconds: " + microseconds);
	}

	public void pulseChannelling(AgisAbilityState state) {
		super.pulseChannelling(state);
		runCasterActivation(state);
		runTargetActivation(state);
	}

	public void pulseCaster(AgisAbilityState state) {
		super.pulseCaster(state);
		runCasterActivation(state);
	}

	public void pulseTarget(AgisAbilityState state) {
		super.pulseTarget(state);
		runTargetActivation(state);
	}

	public void pulseTarget(AgisAbilityState state, ArrayList<CombatInfo> targets) {
		super.pulseTarget(state);
		if (Log.loggingDebug)
			Log.debug("AttackAbility.pulseTarget " + state + " " );
		runTargetActivation(state, targets);
	}

	public void pulseActivated(AgisAbilityState state) {
		super.pulseActivated(state);
	}

	public void completeChannelling(AgisAbilityState state) {
		super.completeChannelling(state);
	}

}
