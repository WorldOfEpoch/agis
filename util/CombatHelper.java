package atavism.agis.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.SkillData;
import atavism.agis.objects.StatThreshold;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatPlugin;
import atavism.server.engine.*;
import atavism.server.math.Quaternion;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * Contains a collection of static functions that are used to assist in combat calculations.
 * If you want to modify the combat calculations used in Atavism, this is where you make the
 * changes.
 * @author Andrew Harrison
 *
 */
public class CombatHelper {
	static Random random = new Random();
	/**
	 * Calculate the chance to hit when using a physical ability such as a melee weapon attack.
	 * @param obj: The character performing the ability
	 * @param target: The target of the ability
	 * @param skillType: The skill type of the ability
	 * @return
	 */
	
	
	public static double CalcHitChance(CombatInfo obj, CombatInfo target, int skillType, String dmgType, AgisAbility ability) {
		double accuracy = 1;
		if (CombatPlugin.DAMAGE_TYPES.containsKey(dmgType) && CombatPlugin.DAMAGE_TYPES.get(dmgType).getAccuracyStat() != null) {
			accuracy = obj.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getAccuracyStat());
		}
		double evasion = 1;
		if (CombatPlugin.DAMAGE_TYPES.containsKey(dmgType) && CombatPlugin.DAMAGE_TYPES.get(dmgType).getEvasionStat() != null) {
			evasion = target.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getEvasionStat());
		}
		int targetLevel = target.statGetCurrentValue("level");
		int casterLevel = obj.statGetCurrentValue("level");

		double statsDiff = ((evasion - accuracy) / CombatPlugin.HIT_CHANCE_POINT_PER_PERCENTAGE);
		Log.debug("COMBATHELPER: CalcHitChance accuracy:" + accuracy + " evasion:" + evasion + " HIT_CHANCE_POINT_PER_PERCENTAGE:" + CombatPlugin.HIT_CHANCE_POINT_PER_PERCENTAGE);

		if (statsDiff > CombatPlugin.HIT_CHANCE_PERCENTAGE_CAP)
			statsDiff = CombatPlugin.HIT_CHANCE_PERCENTAGE_CAP;
		if (statsDiff < 0)
			statsDiff = 0;

		double levelDiff = (targetLevel - casterLevel) * CombatPlugin.HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL;
		Log.debug("COMBATHELPER: CalcHitChance targetLevel:" + targetLevel + " casterLevel:" + casterLevel + " HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL:" + CombatPlugin.HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL);
		if (levelDiff < 0)
			levelDiff = 0;
		double hitChance = 1-(ability.getMissChance() / 100f) - (statsDiff / 100f + levelDiff / 100f);
		Log.debug("COMBATHELPER: CalcHitChance hitChance:" + hitChance + " levelDiff%:" + levelDiff + " statsDiff%:" + statsDiff + " target.isMob:" + target.isMob() + " caster.isMob:" + obj.isMob());

		return hitChance;

	}
	
	/**
	 * Calculates the amount of damage done by the effect 
	 * @param obj
	 * @param caster
	 * @param dmg
	 * @param dmgType
	 * @param skillMod
	 * @param skillType
	 * @param hitRoll
	 * @return
	 */
	
	public static int CalcDamage(CombatInfo obj, CombatInfo caster, int dmg, String dmgType, float skillMod, int skillType, int hitRoll, boolean useHitRoll, Map<String, Serializable> params) {
		if(Log.loggingDebug)Log.debug("COMBATHELPER: CalcDamage with base dmage: " + dmg + ", about to get attackType");
		if (dmgType == null || dmgType.equals("")) {
			// Get the attackers damageType
			dmgType = (String) caster.getProperty("attackType");
		}
		if(Log.loggingDebug)Log.debug("COMBATHELPER: CalcDamage, just got attackType: " + dmgType);
		double damage = dmg;
		// increase the damage taken based on casters stats
		int skillLevel = 0;
		if (skillType != -1) {
			if (!caster.getCurrentSkillInfo().getSkills().containsKey(skillType))
				Log.warn("COMBAT HELPER: player does not have this skill: " + skillType);
			else
				skillLevel = caster.getCurrentSkillInfo().getSkills().get(skillType).getSkillLevel();
		}
		if(Log.loggingDebug)Log.debug("COMBATHELPER: CalcDamage, skillMod: " + skillMod + " skillLevel:" + skillLevel + " skillType:" + skillType);

		damage += skillMod * (float) skillLevel;
		if(Log.loggingDebug)Log.debug("COMBAT: after skill mod dmg: " + damage + "; damage type: " + dmgType);
		if(Log.loggingDebug)Log.debug("COMBAT: POWER_STAT " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat() + "; DAMAGE_DEALT_MODIFIER: " + CombatPlugin.DAMAGE_DEALT_MODIFIER);

		if (CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat() != null) {
			double casterStrength = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat());
			if(Log.loggingDebug)Log.debug("COMBAT: POWER_STAT casterStrength="+casterStrength);
			double strengthModifier = casterStrength / 25.0; // 100.0;
			// Log.debug("COMBAT: strength modifier: " + strengthModifier);
			damage += damage * strengthModifier;
		}
		if(Log.loggingDebug)Log.debug("COMBAT: after Power mod dmg: " + damage + "; damage type: " + dmgType);
		double casterDamageModifier = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_DEALT_MODIFIER);
		if(Log.loggingDebug)Log.debug("COMBAT: casterDamageModifier: " + casterDamageModifier );
		casterDamageModifier /= 100.0;
		damage += damage * casterDamageModifier;
		// Modify by the hit Roll
		double randRoll = random.nextDouble();
		double roll = randRoll * 100d;
		if(Log.loggingDebug)Log.debug("COMBAT: before hitRoll  calculation dmg: " + damage + "; damage type: " + dmgType);
		if(Log.loggingDebug)Log.debug("COMBAT: hitRoll " + hitRoll + " useHitRoll=" + useHitRoll + " roll=" + roll + " DAMAGE_HITROLL_MODIFIER=" + CombatPlugin.DAMAGE_HITROLL_MODIFIER);
		if (useHitRoll) {
			damage *= ((roll / (100d / CombatPlugin.DAMAGE_HITROLL_MODIFIER) + (100d - CombatPlugin.DAMAGE_HITROLL_MODIFIER)) / 100.0);
		}

		// Decrease the damage taken by the modifier
		if(Log.loggingDebug)Log.debug("COMBAT: before resist melee dmg: " + damage + "; damage type: " + dmgType);
		if(Log.loggingDebug)Log.debug("DMGTYPE: getting resistance stat: " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getResistanceStat() + " for damage type: " + dmgType);
		
		
		
		double targetArmor = 0;
		if(obj!=null)
			targetArmor=  obj.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getResistanceStat());
		if(Log.loggingDebug)Log.debug("COMBAT: Helper targetArmor:" + targetArmor + "; Calcultion Flat bool->" + CombatPlugin.FLAT_ARMOR_DAMAGE_CALCULATIONS);
		if (!CombatPlugin.FLAT_ARMOR_DAMAGE_CALCULATIONS) {
			double damageFlat = damage * 0.01;
			double damageVar = damage * 0.99 - targetArmor;
			if (damageVar < 0)
				damageVar = 0;
			damage = damageFlat + damageVar;
		} else {
			damage -= targetArmor;
		}
		if(Log.loggingDebug)Log.debug("COMBAT: final melee dmg: " + damage);
		double targetDamageModifier = 0;
		if(obj!=null)
			targetDamageModifier = obj.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TAKEN_MODIFIER);
		targetDamageModifier /= 100.0;
		damage += damage * targetDamageModifier;
		if(Log.loggingDebug)Log.debug("COMBAT: final melee dmg2: " + damage);
		if (damage <= 1) {
			damage = 1.0;
		}

		double criticalChance = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticChanceStat());
		if(Log.loggingDebug)Log.debug("COMBAT: Critic statChance: " + criticalChance +" of "+CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticChanceStat());
		
		//Log.debug("COMBAT: Physical Critic statChance: " + criticalChance );
		double criticalChanceCalculated = 0;
		if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.CRITIC_CHANCE)) {
			StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.CRITIC_CHANCE);
			double pointsCalculated = 0;
			for (int i = 1; i <= def.getPoints().size(); i++) {
				if(Log.loggingDebug)Log.debug("COMBAT: Critic i="+i+" pointsCalculated="+pointsCalculated+" Th="+def.getThresholds().get(i)+" points="+def.getPoints().get(i)+" criticalChanceCalculated="+criticalChanceCalculated);
				if (criticalChance <= def.getThresholds().get(i)) {
					Log.debug("COMBAT: Critic criticalChance < th");
					if (criticalChance - pointsCalculated < 0)
						break;
					criticalChanceCalculated += Math.round((criticalChance - pointsCalculated) / def.getPoints().get(i));
					pointsCalculated += criticalChance - pointsCalculated;
				} else {
					Log.debug("COMBAT: Critic criticalChance > th");
					criticalChanceCalculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
					pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
				}
			}
			/*
			 * if (pointsCalculated < criticalChance) { 
			 * 	criticalChanceCalculated += Math.round((criticalChance - pointsCalculated) / def.getPoints().get(def.getPoints().size())); 
			 * }
			 */
		} else {
			criticalChanceCalculated = criticalChance;
		}
		if(Log.loggingDebug)Log.debug("COMBAT: Critic criticalChanceCalculated: " + criticalChanceCalculated);

		double rand = random.nextDouble();
		if(Log.loggingDebug)Log.debug("COMBAT: Critic chance: " + rand + "; calc chance: " + (criticalChanceCalculated / 100f) + "; calc chance v2: " + (criticalChanceCalculated / 100.0));

		if (rand < (criticalChanceCalculated / 100.0)) {
			double modValue = 0f;
			if (CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat() != null) {
				double criticalPower = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat());
				if (Log.loggingDebug)
					Log.debug("COMBAT: Critic stat: " + criticalPower + " of " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat());
				double criticalPowerCalculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.CRITIC_POWER)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.CRITIC_POWER);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("COMBAT: Critic i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " criticalChanceCalculated="
									+ criticalPowerCalculated);
						if (criticalPower <= def.getThresholds().get(i)) {
							Log.debug("COMBAT: Critic criticalChance < th");
							if (criticalPower - pointsCalculated < 0)
								break;
							criticalPowerCalculated += Math.round((criticalPower - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += criticalPower - pointsCalculated;
						} else {
							Log.debug("COMBAT: Critic criticalChance > th");
							criticalPowerCalculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*
					 * if (pointsCalculated < criticalChance) { criticalChanceCalculated +=
					 * Math.round((criticalChance - pointsCalculated) /
					 * def.getPoints().get(def.getPoints().size())); }
					 */
				} else {
					criticalPowerCalculated = criticalPower;
				}
				modValue = criticalPowerCalculated / 100f;
				if (Log.loggingDebug)
					Log.debug("COMBAT: Critic criticalChanceCalculated: " + criticalPowerCalculated + " modValue=" + modValue);
			}

			damage = damage + damage * modValue;

			Log.warn("COMBAT: Critic damage: " + damage + "; damage type: " + dmgType);
			params.put("result", AgisAbility.RESULT_CRITICAL);
		}

		return (int) Math.round(Math.ceil(damage));
	}
	
	
	

	public static int modifyDamage(CombatInfo caster, CombatInfo target, int dmg) {

		double damageDealtMod = 1;
		if (CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT != null) {
			double statValue = caster.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT);
			if (Log.loggingDebug)
				Log.debug("modifyDamage: damageDealtMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT);
				double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("modifyDamage: damageDealtMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("modifyDamage: damageDealtMod statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("modifyDamage: damageDealtMod statValue > th");
						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
				// if (pointsCalculated < statValue) {
				// calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
				// }
			} else {
				calculated = statValue;
			}
			if (Log.loggingDebug)
				Log.debug("modifyDamage: damageDealtMod calculated: " + calculated);
			damageDealtMod = calculated / 100f;
			if (Log.loggingDebug)
				Log.debug("modifyDamage: damageDealtMod calculated=" + calculated + " mod=" + damageDealtMod);
		}

		double damageReciveMod = 1;
		if (target != null)
			if (CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT != null) {
				double statValue = target.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("modifyDamage: damageReciveMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT);
					double pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("modifyDamage: damageReciveMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated="
									+ calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("modifyDamage: damageReciveMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("modifyDamage: damageReciveMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					// if (pointsCalculated < statValue) {
					// calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
					// }
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("modifyDamage: damageReciveMod calculated: " + calculated);
				damageReciveMod = calculated / 100f;
				if (Log.loggingDebug)
					Log.debug("modifyDamage: damageReciveMod calculated=" + calculated + " mod=" + damageReciveMod);
			}

		
		
		
		return (int) Math.round(dmg * damageReciveMod * damageDealtMod);
	}

	
	
	
	/**
	 * Calculates the amount of health restored by the effect calling it. Uses magical based stats.
	 * @param obj
	 * @param caster
	 * @param heal
	 * @param skillMod
	 * @param skillType
	 * @return
	 */
	public static int CalcHeal(CombatInfo obj, CombatInfo caster, int heal, float skillMod, int skillType, String dmgType, Map<String, Serializable> params) {
		if (Log.loggingDebug)
			Log.debug("CombatHelper.CalcHeal: CalcHeal hit with heal: " + heal + " skillType: " + skillType + " and skillMod: " + skillMod+" dmgType:"+dmgType+" params: "+params);
		double healVal = heal;
		// increase the damage taken based on casters stats
		int skillLevel = 0;
		if (caster != null) {
			if (skillType != -1) {
				if (!caster.getCurrentSkillInfo().getSkills().containsKey(skillType))
					Log.warn("CombatHelper.CalcHeal: player does not have this skill: " + skillType);
				else
					skillLevel = caster.getCurrentSkillInfo().getSkills().get(skillType).getSkillLevel();
				// skillLevel =
				// caster.getCurrentSkillInfo().getSkills().get(skillType).getSkillCurrent();
			}
		}
		healVal += skillMod * (float) skillLevel;
		if (caster != null) {
			if (CombatPlugin.DAMAGE_TYPES.get(dmgType) != null) {
				// double casterPotential =
				// caster.statGetCurrentValue(CombatPlugin.MAGICAL_POWER_STAT);
				// double potentialModifier = (casterPotential) / 25.0; // 100
				// Log.debug("HEAL: healVal: " + healVal + " potentialMod: " +
				// potentialModifier);
				// healVal += healVal * potentialModifier;

				double healPowerMod = 1;
				if (CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat() != null) {
					double statValue = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat());
					if (Log.loggingDebug)
						Log.debug("CombatHelper.CalcHeal: healPowerMod statValue: " + statValue + " of " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getPowerStat());
					double calculated = 0;
					if (Log.loggingDebug)
						Log.debug("CombatHelper.CalcHeal: healPowerMod STAT_THRESHOLDS " + CombatPlugin.STAT_THRESHOLDS);
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.POWER)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.POWER);
						double pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("CombatHelper.CalcHeal: healPowerMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i)
										+ " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("CombatHelper.CalcHeal: healPowerMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("CombatHelper.CalcHeal: healPowerMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						// if (pointsCalculated < statValue) {
						// calculated += Math.round((statValue - pointsCalculated) /
						// def.getPoints().get(def.getPoints().size()));
						// }
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("CombatHelper.CalcHeal: healPowerMod calculated: " + calculated);
					healPowerMod = (calculated / 100f);
					if (Log.loggingDebug)
						Log.debug("CombatHelper.CalcHeal: healPowerMod calculated=" + calculated + " mod=" + healPowerMod);
					healVal += Math.round(healVal * healPowerMod);
				}

			}
		}
		if (caster != null) {
			if (CombatPlugin.DAMAGE_TYPES.get(dmgType) != null) {
				double criticalChance = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticChanceStat());
				if (Log.loggingDebug)
					Log.debug("CombatHelper.CalcHeal: Critic statChance: " + criticalChance + " of " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticChanceStat());

				// Log.debug("COMBAT: Physical Critic statChance: " + criticalChance );
				double criticalChanceCalculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.CRITIC_CHANCE)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.CRITIC_CHANCE);
					double pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						Log.debug("CombatHelper.CalcHeal: Critic i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i)
								+ " criticalChanceCalculated=" + criticalChanceCalculated);
						if (criticalChance <= def.getThresholds().get(i)) {
							Log.debug("CombatHelper.CalcHeal: Critic criticalChance < th");
							if (criticalChance - pointsCalculated < 0)
								break;
							criticalChanceCalculated += Math.round((criticalChance - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += criticalChance - pointsCalculated;
						} else {
							Log.debug("CombatHelper.CalcHeal: Critic criticalChance > th");
							criticalChanceCalculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*
					 * if (pointsCalculated < criticalChance) { criticalChanceCalculated +=
					 * Math.round((criticalChance - pointsCalculated) /
					 * def.getPoints().get(def.getPoints().size())); }
					 */
				} else {
					criticalChanceCalculated = criticalChance;
				}
				Log.debug("CombatHelper.CalcHeal: Critic criticalChanceCalculated: " + criticalChanceCalculated);

				double rand = random.nextDouble();
				Log.warn("CombatHelper.CalcHeal: Critic chance: " + rand + "; calc chance: " + (criticalChanceCalculated / 100f) + "; calc chance v2: " + (criticalChanceCalculated / 100.0));

				if (rand < (criticalChanceCalculated / 100.0)) {
					double modValue = 0f;
					if (CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat() != null) {
						double criticalPower = caster.statGetCurrentValueWithPrecision(CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat());
						if (Log.loggingDebug)
							Log.debug("CombatHelper.CalcHeal: Critic stat: " + criticalPower + " of " + CombatPlugin.DAMAGE_TYPES.get(dmgType).getCriticPowerStat());
						double criticalPowerCalculated = 0;
						if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.CRITIC_POWER)) {
							StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.CRITIC_POWER);
							double pointsCalculated = 0;
							for (int i = 1; i <= def.getPoints().size(); i++) {
								if (Log.loggingDebug)
									Log.debug("CombatHelper.CalcHeal: Critic i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i)
											+ " criticalChanceCalculated=" + criticalPowerCalculated);
								if (criticalPower <= def.getThresholds().get(i)) {
									Log.debug("CombatHelper.CalcHeal: Critic criticalChance < th");
									if (criticalPower - pointsCalculated < 0)
										break;
									criticalPowerCalculated += Math.round((criticalPower - pointsCalculated) / def.getPoints().get(i));
									pointsCalculated += criticalPower - pointsCalculated;
								} else {
									Log.debug("CombatHelper.CalcHeal: Critic criticalChance > th");
									criticalPowerCalculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
									pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
								}
							}
							/*
							 * if (pointsCalculated < criticalChance) { criticalChanceCalculated +=
							 * Math.round((criticalChance - pointsCalculated) /
							 * def.getPoints().get(def.getPoints().size())); }
							 */
						} else {
							criticalPowerCalculated = criticalPower;
						}
						modValue = criticalPowerCalculated / 100f;
						if (Log.loggingDebug)
							Log.debug("CombatHelper.CalcHeal: Critic criticalChanceCalculated: " + criticalPowerCalculated + " modValue=" + modValue);
					}

					healVal = healVal + healVal * modValue;
					Log.warn("CombatHelper.CalcHeal: Critic : " + healVal + "; damage type: " + dmgType);
					params.put("result", AgisAbility.RESULT_CRITICAL);
				}
			}
		}
		
		double healDealtMod = 1;
		if (caster != null) {
			if (CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT != null) {
				double statValue = caster.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("CombatHelper.CalcHeal: healDealtMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("CombatHelper.CalcHeal: healDealtMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated="
									+ calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("CombatHelper.CalcHeal: healDealtMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("CombatHelper.CalcHeal: healDealtMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					// if (pointsCalculated < statValue) {
					// calculated += Math.round((statValue - pointsCalculated) /
					// def.getPoints().get(def.getPoints().size()));
					// }
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("CombatHelper.CalcHeal: healDealtMod calculated: " + calculated);
				healDealtMod = calculated / 100f;
				if (Log.loggingDebug)
					Log.debug("CombatHelper.CalcHeal: healDealtMod calculated=" + calculated + " mod=" + healDealtMod);
			}
		}

		double healReciveMod = 1;
		if (CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT != null) {
			double statValue = obj.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
			if (Log.loggingDebug)
				Log.debug("CombatHelper.CalcHeal: healReciveMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT);
				int pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("CombatHelper.CalcHeal: healReciveMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated="
								+ calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("CombatHelper.CalcHeal: healReciveMod statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("CombatHelper.CalcHeal: healReciveMod statValue > th");
						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
				// if (pointsCalculated < statValue) {
				// calculated += Math.round((statValue - pointsCalculated) /
				// def.getPoints().get(def.getPoints().size()));
				// }
			} else {
				calculated = statValue;
			}
			if (Log.loggingDebug)
				Log.debug("CombatHelper.CalcHeal: healReciveMod calculated: " + calculated);
			healReciveMod = calculated / 100f;
			if (Log.loggingDebug)
				Log.debug("CombatHelper.CalcHeal: healReciveMod calculated=" + calculated + " mod=" + healReciveMod);
		}

		if (healVal <= 0) {
			healVal = 1f;
		}
		return (int) Math.round(healVal * healDealtMod * healReciveMod);
	}

	public static int calculateProperty(int level, int percentage) {
		return (int) ((float) (100 + level) * ((float) percentage / 100.0f));
	}

	public static int calculateFlatResist(int level, int percentage) {
		return (int) ((float) (100 + level) * ((float) percentage / 100.0f));
	}

	public static int calculatePercentResist(int level, int percentage) {
		return (int) (10f * ((float) percentage / 100.0f));
	}

	/**
	 * Calculates the chance the player has of leveling up the given skill. The chance
	 * is out of 100.
	 * @param skillData
	 * @return
	 */
	public static float calcSkillUpChance(SkillData skillData) {
		float maxLevel = Agis.SkillManager.get(skillData.getSkillID()).getMaxLevel();
		float increaseChance = ((maxLevel - (float) skillData.getSkillLevel()) / (maxLevel / 100) * ClassAbilityPlugin.SKILL_UP_RATE);
		return increaseChance;
	}

	/**
	 * Calculates the chance the player has of levelling up the max skill level of the given skill.
	 * The chance is out of 100.
	 * @param skillData
	 * @return
	 */
	public static float calcMaxSkillUpChance(SkillData skillData) {
		float maxLevel = Agis.SkillManager.get(skillData.getSkillID()).getMaxLevel();
		float increaseChance = ((maxLevel - (float) skillData.getSkillLevel()) / (maxLevel / 100) * ClassAbilityPlugin.SKILL_UP_RATE);
		return increaseChance;
	}

	/*
	 * Calculates the angle difference between the direction target is facing compared to
	 * the position of obj.
	 */
	public static float calculateValue(CombatInfo obj, CombatInfo target) {
		float angle = 0;
		BasicWorldNode attackerNode;
		try {
			attackerNode = WorldManagerClient.getWorldNode(obj.getOwnerOid());
			angle = getAngleToTarget(attackerNode.getLoc().getX(), attackerNode.getLoc().getZ(), 
					target.getOwnerOid()); // pass my info as attacker
			// and use targets as base
		} catch (IOException e1) {
			Log.error("draive io exception occured: " + e1.getMessage());
		}

		return angle;
	}

	public static float getAngleToTarget(float f, float g, OID oid) throws IOException {
		BasicWorldNode targetNode = WorldManagerClient.getWorldNode(oid);
		float headingDifference = (getHeadingToSpot(f, g, oid) & 0xFFF) - ((short) getMobsHeading(targetNode.getOrientation()) & 0xFFF);
		if (headingDifference < 0)
			headingDifference += 4096.0f;
		return (headingDifference * 360.0f / 4096.0f);
	}

	public static float getMobsHeading(Quaternion q1) {
		float w = q1.getW();
		float y = q1.getY();
		float x = q1.getX();
		float z = q1.getZ();
		double heading = 0;
		double test = x * y + z * w;

		if (test > 0.499) {
			heading = 2 * Math.atan2(x, w);
			heading = heading * (180 / Math.PI);
			return (int) heading;
		}
		if (test < -0.499) {
			heading = -2 * Math.atan2(x, w);
			heading = heading * (180 / Math.PI);
			return (int) heading;
		}

		heading = Math.atan2(2 * y * w - 2 * x * z, 1 - 2 * y * y - 2 * z * z);
		heading = heading * (180 / Math.PI);

		if (heading < 0) {
			// remove this if not using the getangle method and get a raw 0-30 value
			// heading = Math.abs(heading);
			return (int) heading;
		} else {
			// remove this if not using the getangle method and get a raw 0-30 value
			// heading = 360 - heading;
			return (int) heading;
		}
	}

	public static short getHeadingToSpot(float f, float g, OID oid) throws IOException {
		BasicWorldNode tnode = WorldManagerClient.getWorldNode(oid);
		float dx = (long) f - tnode.getLoc().getX();
		float dz = (long) g - tnode.getLoc().getZ();
		short heading = (short) (Math.atan2(-dx, dz) * HEADING_CONST);
		if (heading < 0)
			heading += 0x1000;
		return heading;
	}

	public static double HEADING_CONST = 651.89864690440329530934789477382;
}