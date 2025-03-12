package atavism.agis.abilities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;

import atavism.agis.core.*;
import atavism.agis.effects.ReviveEffect;
import atavism.agis.objects.AbilityAbilitiesDefinition;
import atavism.agis.objects.AbilityEffectDefinition;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.util.Log;

/**
 * Ability child class that applies effects to a friendly target.
 * It skips all the to-hit calculations and just straight up applies the effect if 
 * it meets all the basic requirements (enough energy/ close enough etc.).
 * 
 * @author: Andrew Harrison
 */
public class FriendlyEffectAbility extends AgisAbility {
	 static Random random = new Random();
	 
	private HashMap<String, Serializable> params;
	public LinkedList<Integer> effectVals;
//	protected LinkedList<AgisEffect> activationEffects;
//	public LinkedList<String> effectTarget;
	protected AgisEffect channelEffect;
	protected AgisEffect activeEffect;
	
    public FriendlyEffectAbility(String name) {
        super(name);
        this.params = new HashMap<String, Serializable>();
        this.effectVals = new LinkedList<Integer>();
//        this.activationEffects = new LinkedList<AgisEffect>();
//        this.effectTarget = new LinkedList<String>();
        this.activeEffect = null;
        this.channelEffect = null;
    }
    
    protected AbilityResult checkAbility(CombatInfo obj, CombatInfo target, ActivationState state, AgisAbilityState abilityState, boolean skipCheckCooldown) {

		AbilityResult result = super.checkAbility(obj, target, state,abilityState, skipCheckCooldown);
		if(Log.loggingDebug)	Log.debug("FriendlyEffectAbility checkAbility effect activated result:"+result);
		if (result != AbilityResult.SUCCESS) {
			ExtendedCombatMessages.sendAbilityFailMessage(obj, result, getID(),costProp);
			return result;
		}
		return AbilityResult.SUCCESS;
	}

	  void runHitRoll(CombatInfo source, CombatInfo target) {
		  if(Log.loggingDebug)Log.debug("FriendlyEffectAbility: runHitRoll source="+source+" target="+target); 
    	// Nothing to do so far
    	params = new HashMap<String, Serializable>();
    	double rand = random.nextDouble();
    	if(Log.loggingDebug)Log.debug("FriendlyEffectAbility: random value = " + rand );
		int hitRoll = (int)(rand * 100);
		if(Log.loggingDebug)Log.debug("SKILL: hit roll is: " + hitRoll);
		if (hitRoll > getSkillUpChance()) {
			sendSkillUpChance = false;
		}
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility: runHitRoll="+hitRoll+" sendSkillUpChance="+sendSkillUpChance+" source="+source+" target="+target); 
    	
    }

    // List of effect values
    public void addEffectVal(int effect) {
    	effectVals.add(effect);
    }
    public LinkedList<Integer> GetEffectVal() {
        return effectVals;
    }
    
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

    public AgisEffect getChannelEffect() { return this.channelEffect; }
    public void setChannelEffect(AgisEffect effect) { this.channelEffect = effect; }

    public AgisEffect getActiveEffect() { return this.activeEffect; }
    public void setActiveEffect(AgisEffect effect) { this.activeEffect = effect; }

    public void completeActivation(AgisAbilityState state) {
        
    	super.completeActivation(state);
     	runCasterActivation(state);
      	runTargetActivation(state);
    }
    	
    public void runCasterActivation(AgisAbilityState state)	{
    	long start = System.nanoTime();
    	if(Log.loggingDebug) Log.debug("FriendlyEffectAbility: runCasterActivation completeActivation "+state);
		boolean effectApplyed = false;
		if (targetType == TargetType.LOCATION) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			CombatInfo targetInfo = state.getSource();
			runHitRoll(state.getSource(), targetInfo);
			HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
			param.put("result", 1);
			param.put("skillType", skillType);
			param.put("hitRoll", 0);
			param.put("location", state.getLocation());
			param.put("dmgType", damageType);
			param.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
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
//				targetInfo = state.getSource();
//				AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
//				effectApplyed = true;
//			}

			// ObjectLockManager.unlockAll(requiredLocks);
		} else if (targetType == TargetType.AREA && (targetSubType == TargetSubType.FRIEND)) {
		//	List<Lock> requiredLocks = new ArrayList<Lock>();
		/*	requiredLocks.add(state.getSource().getLock());
			for (CombatInfo ci : targets) {
				requiredLocks.add(ci.getLock());
			}*/

			/*for (CombatInfo targetInfo : targets) {
				if (targetInfo == null) {
					continue;
				}
				if (targetInfo.dead() && targetDeath == 1) {
					Log.debug("AOE: target is dead");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					Log.debug("ABILITY: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}*/
				runHitRoll(state.getSource(), null);
				HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
				param.put("result", 1);
				param.put("skillType", skillType);
				param.put("hitRoll", 0);
				param.put("dmgType", damageType);
			param.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
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
//				for (int i = 0; i < activationEffects.size(); i++) {
//					String target = effectTarget.get(i);
//					if (target.equals("caster")) {
//						//targetInfo = state.getSource();
//						AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem());
//						effectApplyed = true;
//					}
//				}
			//}
			//ObjectLockManager.unlockAll(requiredLocks);
		} else if (targetType == TargetType.GROUP) {
			
		/*	ArrayList<OID> groupMemners = new ArrayList<OID>(GroupClient.GetGroupMembers(state.getSourceOid(),false));
			Log.debug("FriendlyEffectAbility.completeActivation: groupMemners count=" + groupMemners.size() + " " + groupMemners);
			BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSourceOid());
			groupMemners = AgisWorldManagerClient.checkIfTargetsInArea(groupMemners, casterNode.getLoc(), areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation());
			Log.debug("FriendlyEffectAbility.completeActivation: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			if(!groupMemners.contains(state.getSourceOid()))
				groupMemners.add(state.getSourceOid());*/
			//Log.debug("FriendlyEffectAbility.completeActivation: caster added groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			
			//for (OID member : groupMemners) {
			for (CombatInfo targetInfo : targets) {
					
				/*Log.debug("FriendlyEffectAbility.completeActivation: group member=" + member);
				CombatInfo targetInfo = CombatPlugin.getCombatInfo(member);*/
				if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.runCasterActivation completeActivation: group CombatInfo member=" + targetInfo);
				if (targetInfo == null) {
					Log.debug("AOE Goup: CombatInfo target is null");
					continue;
				}
				if (CheckTargetState(targetInfo) != AbilityResult.SUCCESS) {
					Log.debug("AOE: check target state failed");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					if(Log.loggingDebug)Log.debug("ABILITY: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}
				runHitRoll(state.getSource(), targetInfo);
				HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
				param.put("result", 1);
				param.put("skillType", skillType);
				param.put("hitRoll", 0);
				param.put("dmgType", damageType);
				param.put("powerUp",state.getPowerUptime());
				for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
					AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
					if(aed.getTarget().equals("caster") && aed.CanBeApply()){
						AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
						AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
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
//				for (int i = 0; i < activationEffects.size(); i++) {
//					String target = effectTarget.get(i);
//					if (target.equals("caster")) {
//						targetInfo = state.getSource();
//						AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
//						effectApplyed = true;
//					}
//				}
			}
			Log.debug("FriendlyEffectAbility.runCasterActivation completeActivation: group end");

			//ObjectLockManager.unlockAll(requiredLocks);
		} else {
			Log.debug("FriendlyEffectAbility.runCasterActivation: No AoE");
			// Here we need to add in the effectsList to the parameters
			CombatInfo targetInfo = state.getTarget();
			runHitRoll(state.getSource(), targetInfo);
			HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
			param.put("result", 1);
			param.put("skillType", skillType);
			param.put("hitRoll", 0);
			param.put("dmgType", damageType);
			param.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("caster") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
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
//					if(Log.loggingDebug)Log.debug("RESULT: adding param result with value: " + "; params is now: " + params + " and effect: " + activationEffects.get(i));
//					AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
//					effectApplyed = true;
//				}
//			}
			if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.runCasterActivation: No AoE End effectApplyed="+effectApplyed);
		}
		
		
		
		
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.runCasterActivation: effectApplyed="+effectApplyed+" usedAbility="+state.usedAbility+" skillType="+skillType+" skillExp="+skillExp+" sendSkillUpChance="+sendSkillUpChance);
		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				Log.debug("SKILL: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}

		} else {
			Log.debug("FriendlyEffectAbility.runCasterActivation completeActivation: effect not aplaed");

		}
		Log.debug("FriendlyEffectAbility.runCasterActivation: completeActivation: finished");

	}

    /**
     * Apply Effect on Target/targets
     * @param state
     */
    public void runTargetActivation(AgisAbilityState state)	{ 	
    	runTargetActivation( state, targets);
    }
    public void runTargetActivation(AgisAbilityState state, ArrayList<CombatInfo> targets)	{
    	long start = System.nanoTime();
       //  super.completeActivation(state);
    	if(Log.loggingDebug) Log.debug("FriendlyEffectAbility: runTargetActivation completeActivation "+state+" "+" targets="+targets);
   		boolean effectApplyed =false;

		if (targetType == TargetType.AREA && targetSubType == TargetSubType.FRIEND) {
			List<Lock> requiredLocks = new ArrayList<Lock>();
			requiredLocks.add(state.getSource().getLock());
			for (CombatInfo ci : targets) {
				requiredLocks.add(ci.getLock());
			}

			for (CombatInfo targetInfo : targets) {
				Log.debug("FriendlyEffectAbility: runTargetActivation target="+targetInfo); 
				if (targetInfo == null) {
					Log.debug("FriendlyEffectAbility: runTargetActivation CombatInfo is null");
					continue;
				}
				if (CheckTargetState(targetInfo) != AbilityResult.SUCCESS) {
					Log.debug("FriendlyEffectAbility: check target state failed");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					if(Log.loggingDebug)Log.debug("FriendlyEffectAbility: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {

					runHitRoll(state.getSource(), targetInfo);
					HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
					param.put("result", 1);
					param.put("skillType", skillType);
					param.put("hitRoll", 0);
					param.put("dmgType", damageType);
					param.put("powerUp",state.getPowerUptime());
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if(aed.getTarget().equals("target") && aed.CanBeApply()){
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if(aad.getTarget().equals("target") && aad.CanBeApply()){
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (!target.equals("caster")) {
//							//targetInfo = state.getTarget();
////							if (activationEffects.get(i).getClass().equals(ReviveEffect.class) && targetInfo.dead() && targetDeath == 0) {
//								AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
//								effectApplyed = eState == null ? false : true;
////							} else if (!targetInfo.dead() && targetDeath == 1) {
////								AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
////								effectApplyed = eState == null ? false : true;
////							}
//						}
//					}
					if (effectApplyed)
						state.countapplyed++;
				}
			}
			// ObjectLockManager.unlockAll(requiredLocks);
		} else if (targetType == TargetType.GROUP) {

		/*	ArrayList<OID> groupMemners = new ArrayList<OID>(GroupClient.GetGroupMembers(state.getSourceOid(),false));
			Log.debug("FriendlyEffectAbility.completeActivation: groupMemners count=" + groupMemners.size() + " " + groupMemners);
			BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSourceOid());
			groupMemners = AgisWorldManagerClient.checkIfTargetsInArea(groupMemners, casterNode.getLoc(), areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation());
			Log.debug("FriendlyEffectAbility.completeActivation: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			if(!groupMemners.contains(state.getSourceOid()))
				groupMemners.add(state.getSourceOid());*/
			//Log.debug("FriendlyEffectAbility.completeActivation: caster added groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			
			//for (OID member : groupMemners) {
			for (CombatInfo targetInfo : targets) {

				if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.runTargetActivation completeActivation: group CombatInfo member=" + targetInfo);
				if (targetInfo == null) {
					Log.debug("AOE Goup: CombatInfo target is null");
					continue;
				}
				if (CheckTargetState(targetInfo) != AbilityResult.SUCCESS) {
					Log.debug("AOE: check target failed");
					continue;
				}
				if (!checkTargetSpecies(state.getSource(), targetInfo).equals(AbilityResult.SUCCESS)) {
					if(Log.loggingDebug)Log.debug("ABILITY: checkTargetSpecies failed for target " + targetInfo.getOid());
					continue;
				}
				if (aoe_targets_count_type == 0 || ((aoe_targets_count_type == 1 || aoe_targets_count_type == 2) && state.countapplyed < aoe_targets_count)) {
					runHitRoll(state.getSource(), targetInfo);
					HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
					param.put("result", 1);
					param.put("skillType", skillType);
					param.put("hitRoll", 0);
					param.put("dmgType", damageType);
					param.put("powerUp",state.getPowerUptime());
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
						AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
						if(aed.getTarget().equals("target") && aed.CanBeApply()){
							AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
							AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
							state.addEffectState(eState);
							effectApplyed = true;
						}
					}
					for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
						AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
						if(aad.getTarget().equals("target") && aad.CanBeApply()){
							AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
							AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), targetInfo, null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
							state.addAbilitiesState(aState);
							effectApplyed = true;
						}
					}
//					for (int i = 0; i < activationEffects.size(); i++) {
//						String target = effectTarget.get(i);
//						if (!target.equals("caster")) {
//							// targetInfo = state.getTarget();
////							if (activationEffects.get(i).getClass().equals(ReviveEffect.class) && targetInfo.dead() && targetDeath == 0) {
//								AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
//								effectApplyed = eState == null ? false : true;
////							} else if (!targetInfo.dead() && targetDeath == 1) {
////								AgisEffect.EffectState eState = AgisEffect.applyEffect(activationEffects.get(i), state.getSource(), targetInfo, getID(), new HashMap<String,Serializable>(param), state.getItem());
////								effectApplyed = eState == null ? false : true;
////							}
//						}
//					}
					if (effectApplyed)
						state.countapplyed++;
				}
			}
			Log.debug("FriendlyEffectAbility.runTargetActivation completeActivation: group end");

			// ObjectLockManager.unlockAll(requiredLocks);
		} else if(targetSubType  != TargetSubType.SELF) {
			// Here we need to add in the effectsList to the parameters
			CombatInfo targetInfo = state.getTarget();
			runHitRoll(state.getSource(), targetInfo);
			HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
			param.put("result", 1);
			param.put("skillType", skillType);
			param.put("hitRoll", 0);
			param.put("dmgType", damageType);
			param.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("target") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getTarget(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
					state.addEffectState(eState);
					effectApplyed = true;
				}
			}
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
				AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
				if(aad.getTarget().equals("target") && aad.CanBeApply()){
					AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getTarget(), null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}

		} else if(targetSubType  == TargetSubType.SELF) {
			// Here we need to add in the effectsList to the parameters
			CombatInfo targetInfo = state.getTarget();
			runHitRoll(state.getSource(), targetInfo);
			HashMap<String,Serializable> param = new HashMap<String,Serializable>(params);
			param.put("result", 1);
			param.put("skillType", skillType);
			param.put("hitRoll", 0);
			param.put("dmgType", damageType);
			param.put("powerUp",state.getPowerUptime());
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
				AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);
				if(aed.getTarget().equals("target") && aed.CanBeApply()){
					AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, state.getSource(), state.getSource(), getID(), new HashMap<String,Serializable>(param), state.getItem(), aed.getDelay());
					state.addEffectState(eState);
					effectApplyed = true;
				}
			}
			for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().size(); i++) {
				AbilityAbilitiesDefinition aad = getPowerUpDefinition(state.getPowerUptime()).getAbilityDefinition().get(i);
				if(aad.getTarget().equals("target") && aad.CanBeApply()){
					AgisAbility ability = Agis.AbilityManager.get(aad.getAbilityId());
					AgisAbilityState aState = AgisAbility.startAbilityDelay(ability, state.getSource(), state.getSource(), null, state.getLocation(),state.getClaimID(),state.getClaimObjID(), aad.getDelay());
					state.addAbilitiesState(aState);
					effectApplyed = true;
				}
			}

		}
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.runTargetActivation: effectApplyed="+effectApplyed+" usedAbility="+state.usedAbility+" skillType="+skillType+" skillExp="+skillExp+" sendSkillUpChance="+sendSkillUpChance);
		
		if (effectApplyed && !state.usedAbility) {
			if (state.getSource().isUser() && skillType != -1 && sendSkillUpChance) {
				Log.debug("SKILL: sending ability Used");
				CombatClient.abilityUsed(state.getSourceOid(), skillType, skillExp, 1);
				state.usedAbility = true;
			}

		} else {
			Log.debug("FriendlyEffectAbility.runTargetActivation completeActivation: effect not aplaed");

		}
		Log.debug("FriendlyEffectAbility.runTargetActivation: completeActivation: finished");

	}

	public void pulseChannelling(AgisAbilityState state) {
		super.pulseChannelling(state);
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.pulseChannelling " + state + " " );
		runCasterActivation(state);
		runTargetActivation(state);
	}

	public void pulseCaster(AgisAbilityState state) {
		super.pulseCaster(state);
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.pulseCaster " + state + " " );
		runCasterActivation(state);
	}

	public void pulseTarget(AgisAbilityState state) {
		super.pulseTarget(state);
		if(Log.loggingDebug)Log.debug("FriendlyEffectAbility.pulseTarget " + state  );
		runTargetActivation(state);
	}

	public void pulseTarget(AgisAbilityState state, ArrayList<CombatInfo> targets) {
		super.pulseTarget(state);
		if(Log.loggingDebug)Log.debug("EffectAbility.pulseTarget " + state + " targets="+targets );
		runTargetActivation(state, targets);
	}

	public void pulseActivated(AgisAbilityState state) {
		super.pulseActivated(state);
	}

	public void completeChannelling(AgisAbilityState state) {
		super.completeChannelling(state);
		// AgisEffect.applyEffect(activeEffect, state.getSource(), state.getTarget(),
		// getID());
	}
}