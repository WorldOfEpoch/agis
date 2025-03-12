package atavism.agis.objects;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.util.*;

import atavism.agis.abilities.FriendlyEffectAbility;
import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.objects.SkillTemplate.SkillAbility;
import atavism.agis.plugins.ChatClient;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.FactionClient;
import atavism.agis.util.CombatHelper;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementCheckResult;
import atavism.server.engine.Engine;
import atavism.server.util.*;

/**
 * The SkillInfo class stores information about the skills a player has and
 * provides functions for learning skills, along with increasing and decreasing
 * skill levels.
 * 
 * @author Andrew Harrison
 *
 */
public class SkillInfo implements Serializable {
	protected int category;
	protected int skillPoints;
	protected int pointsSpent;
	protected int pointsBought;
	protected int talentPoints;
	protected int talentPointsBought;
	protected int talentPointsSpent;
	// protected int experience;
	protected transient HashMap<Integer, SkillData> skills = new HashMap<Integer, SkillData>();

	public SkillInfo() {
	}

	public SkillInfo(int category) {
		this.category = category;
		this.skillPoints = 0;
		this.pointsSpent = 0;
		this.pointsBought = 0;
		this.talentPoints = 0;
		this.talentPointsBought = 0;
		this.talentPointsSpent = 0;

		// this.experience = 0;
		this.skills = new HashMap<Integer, SkillData>();
	}

	/**
	 * Adds a
	 * 
	 * @param tmpl
	 * @return
	 */
	public SkillData addSkill(SkillTemplate tmpl) {
		if (Log.loggingDebug)
			Log.debug("SKILL: adding skill " + tmpl.skillID + " to info");
		SkillData skillData = new SkillData(tmpl.getSkillID(), tmpl.getSkillName(), 0, 1, tmpl.getMaxLevel(), tmpl.getParentSkill(), tmpl.isTalent());
		if (tmpl.getSkillProfileID() > 0) {

			SkillProfileTemplate skillProfile = Agis.SkillProfileManager.get(tmpl.getSkillProfileID());
			skillData.setExperienceMax(skillProfile.getLevelExp(skillData.getSkillLevel()));
			if (Log.loggingDebug)
				Log.debug("SKILL: a skill: " + skillData.getSkillID() + " " + skillData.getSkillName() + " Profile:" + tmpl.getSkillProfileID() + " set XP Max:" + skillData.getExperienceMax());
		}
		this.skills.put(skillData.getSkillID(), skillData);
		if (Log.loggingDebug)
			Log.debug("SKILL: added new skill: " + tmpl.getSkillID() + "." + tmpl.getSkillName());
		return skillData;
	}

	public int getCategory() {
		return category;
	}

	public void setCategory(int category) {
		this.category = category;
	}

	public int getSkillPoints() {
		return skillPoints;
	}

	public void setSkillPoints(int skillPoints) {
		this.skillPoints = skillPoints;
	}

	public int getBoughtSkillPoints() {
		return pointsBought;
	}

	public void setBoughtSkillPoints(int pointsBought) {
		this.pointsBought = pointsBought;
	}

	public int getPointsSpent() {
		return pointsSpent;
	}

	public void setPointsSpent(int pointsSpent) {
		this.pointsSpent = pointsSpent;
	}

	public int getTalentPoints() {
		return talentPoints;
	}

	public void setTalentPoints(int talentPoints) {
		this.talentPoints = talentPoints;
	}

	public int getBoughtTalentPoints() {
		return talentPointsBought;
	}

	public void setBoughtTalentPoints(int talentPointsBought) {
		this.talentPointsBought = talentPointsBought;
	}

	public int getTalentPointsSpent() {
		return talentPointsSpent;
	}

	public void setTalentPointsSpent(int talentPointsSpent) {
		this.talentPointsSpent = talentPointsSpent;
	}

	public HashMap<Integer, SkillData> getSkills() {
		return skills;
	}

	public void setSkills(HashMap<Integer, SkillData> skills) {
		this.skills = skills;
	}

	public static void learnSkill(SkillInfo skillInfo, int skillType, int aspect, CombatInfo info) {
		if (Log.loggingDebug)
			Log.debug("SKILL: learning skill: " + skillType);

		if (skillInfo.getSkills().containsKey(skillType) && skillInfo.getSkills().get(skillType).getSkillLevel() > 0) {
			return;
		}

		SkillTemplate template = Agis.SkillManager.get(skillType);
		if (template.mainAspectOnly() && template.getAspect() != aspect) {
			// Send some message to the client saying they don't have enough points
			if (template.isTalent()) {
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your class cannot learn this talent.");
			} else {
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your class cannot learn this skill.");
			}
			return;
		}

		if (skillInfo.getSkills().containsKey(skillType)){
			skillInfo.getSkills().get(skillType).setSkillLevel(1);
		} else {
			SkillData skillData = skillInfo.addSkill(template);
		}
		// Give the player the first ability from this skill

		newSkillGained(info, skillType);
		// ClassAbilityClient.skillLevelChange(info.getOid());
		Engine.getPersistenceManager().setDirty(info);
		// ExtendedCombatMessagers.sendSkills()
		ClassAbilityClient.skillLevelChange(info.getOid());
		if (Log.loggingDebug)
			Log.debug("SKILL: learning skill: " + skillType + " End");

	}



	/**
	 * Increase the skill level of the given skill.
	 * 
	 * @param skillInfo
	 * @param skillID
	 * @param aspect
	 * @param info
	 * @param increaseCurrent
	 * @param admin
	 */
	public static void increaseSkill(SkillInfo skillInfo, int skillID, int aspect, CombatInfo info, boolean increaseCurrent, boolean admin) {
		if (Log.loggingDebug)
			Log.debug("SKILL: increasing skill: skillID=" + skillID + " aspect=" + aspect + " skillInfo=" + skillInfo + " increaseCurrent=" + increaseCurrent + " info=" + info);
		if(skillID < 1) {
			Log.error("SKILL: increasing skill: cant load skill temlate for id -1");
			return;
		}
		SkillTemplate template = Agis.SkillManager.get(skillID);
		if(!admin) {
		if ((!template.isTalent() && !ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) || (template.isTalent() && !ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS)) {
			Log.error("Player " + info.getOid() + " has tried to use skill points, but this feature is not activated");
			return ;
		}
		}
		if (!canPlayerIncreaseSkill(skillInfo, info)) {
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your total skill level is already at the maximum");
			return;
		}
		
		
		
		// int skillAspect = template.getAspect();
		// int oppositeAspect = template.getOppositeAspect();
		int upgradeCost = template.getSkillPointCost();

		if (aspect == template.getAspect())
			upgradeCost = template.getSkillPointCost();
		else if (aspect == template.getOppositeAspect())
			upgradeCost = template.getSkillPointCost() * 2;

		// TODO: Check if the skill level will be higher than the player level?
		int level = info.statGetCurrentValue("level");
if(!admin) {
		if (!template.isTalent())
			if (ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS && skillInfo.skillPoints < upgradeCost) {
				// Send some message to the client saying they don't have enough points
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You do not have enough points to increase that skill");
				return;
			}
		if (template.isTalent())
			if (ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS && skillInfo.talentPoints < upgradeCost) {
				// Send some message to the client saying they don't have enough points
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You do not have enough points to increase that talent");
				return;
			}
		if (template.mainAspectOnly() && template.getAspect() != aspect) {
			// Send some message to the client saying they don't have enough points
			if (!template.isTalent())
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your class cannot learn this skill.");
			if (template.isTalent())
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your class cannot learn this talent.");
			return;
		}
}
		SkillData skillData;
		if (!skillInfo.skills.containsKey(skillID)) {

			if (checkSkillRequirement(skillInfo, info, template)) {

				// The player has not learned this skill at all
				skillData = skillInfo.addSkill(template);
				// Give the player the first ability from this skill
				newSkillGained(info, skillID);

				if (!template.isTalent() && ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) {
					skillInfo.skillPoints -= upgradeCost;
					skillInfo.pointsSpent += upgradeCost;
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.skillPoints + " skill points left.");
				}
				if (template.isTalent() && ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS) {
					skillInfo.talentPoints -= upgradeCost;
					skillInfo.talentPointsSpent += upgradeCost;
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.talentPoints + " talent points left.");
				}
				checkAutoLearnSkills(skillInfo, info);
				skillPointGain(info, skillID, skillData.getSkillLevel());
			} else {

			}

		} else {
			skillData = skillInfo.skills.get(skillID);
			if (skillData.getSkillLevel() < skillData.getSkillMaxLevel()) {
				// increase the cost of incrementing the skill by how many levels the player is
				// behind
				// upgradeCost = upgradeCost + (skillData.getSkillMaxLevel() + 1 - level);
				// upgradeCost++;
				if (!template.isTalent())
					if (ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS && skillInfo.skillPoints < upgradeCost) {
						// Send some message to the client saying they don't have enough points
						ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You do not have enough points to increase that skill");
						return;
					}
				if (template.isTalent())
					if (ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS && skillInfo.talentPoints < upgradeCost) {
						// Send some message to the client saying they don't have enough points
						ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You do not have enough points to increase that Talent");
						return;
					}

				// Check to see if the maximum is already at the limit
				// if (/* skillData.getSkillMaxLevel() == ClassAbilityPlugin.SKILL_MAX || */
				// skillData.getSkillMaxLevel() == template.getMaxLevel()) {
				// WorldManagerClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your " +
				// template.getSkillName() + " skill maximum is already at the current limit ("
				// + template.getMaxLevel() + ").");
				// if (!increaseCurrent) {
				// return;
				// }
				// } else {
				//
				// }

				// skillData.alterSkillMax(1);
				if (checkSkillRequirement(skillInfo, info, template)) {
					if (!template.isTalent()) {
						if (!ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS /*|| increaseCurrent*/) {
							Log.debug("SKILL: increasing skill level ");
							skillData.alterSkillLevel(1);
							checkAutoLearnSkills(skillInfo, info);
							skillPointGain(info, skillID, skillData.getSkillLevel());
						}

						if (ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) {
							skillData.alterSkillLevel(1);
							checkAutoLearnSkills(skillInfo, info);
							skillInfo.skillPoints -= upgradeCost;
							skillInfo.pointsSpent += upgradeCost;
							skillPointGain(info, skillID, skillData.getSkillLevel());
							ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.skillPoints + " skill points left.");
						}
					}
				if (template.isTalent()) {
					if (!ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS /*|| increaseCurrent*/) {
						Log.debug("SKILL: increasing skill level");
						skillData.alterSkillLevel(1);
						checkAutoLearnSkills(skillInfo, info);
						skillPointGain(info, skillID, skillData.getSkillLevel());
					}

					if (ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS) {
						skillData.alterSkillLevel(1);
						checkAutoLearnSkills(skillInfo, info);
						skillInfo.talentPoints -= upgradeCost;
						skillInfo.talentPointsSpent += upgradeCost;
						skillPointGain(info, skillID, skillData.getSkillLevel());
						ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.talentPoints + " talent points left.");
					}
				}
				}
				// info.ReapplyPassiveStatModifier();
			} else {
				if (!template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your " + template.getSkillName() + " skill level is already at the maximum");
				if (template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your " + template.getSkillName() + " talent level is already at the maximum");
			}
		}
		ClassAbilityClient.skillLevelChange(info.getOid());
	}

	/**
	 * Decrease the level of the given skill. The skill will be removed from the
	 * player if it reaches 0 unless it belongs to the same class as the player.
	 * 
	 * @param skillInfo
	 * @param skillID
	 * @param aspect
	 * @param info
	 */
	public static void decreaseSkill(SkillInfo skillInfo, int skillID, int aspect, CombatInfo info) {
		SkillTemplate template = Agis.SkillManager.get(skillID);
		int skillAspect = template.getAspect();
		// int oppositeAspect = template.getOppositeAspect();

		if (!skillInfo.skills.containsKey(skillID)) {
			Log.warn("SKILL: player attempted to decrease a skill they do not have");
			return;
		}
		SkillData skillData = skillInfo.skills.get(skillID);
		// Some things to check for:
		// *if a skill is being decreased to 0 and as such, it needs removed
		// *if a skill is being decreased to 0 but its from the players aspect

		// Decrease the skills max/current/value as appropriate
		if (skillData.getSkillMaxLevel() == 1) {
			if (aspect == skillAspect) {
				// Not allowed to be decreased to 0
				if (!template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You cannot unlearn " + template.getAspect() + " aspect skills.");
				if (template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You cannot unlearn " + template.getAspect() + " aspect talent.");
				return;
			} else {
				// Lets remove the skill
				skillInfo.skills.remove(skillID);
				skillLost(info, skillID);
				if (!template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You no longer have the " + template.getSkillName() + " skill.");
				if (template.isTalent())
					ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You no longer have the " + template.getSkillName() + " talent.");
			}
		} else {
			skillData.alterSkillMax(-1);
			if (skillData.getSkillLevel() > skillData.getSkillMaxLevel()) {
				skillData.alterSkillLevel(-1);
				skillPointLoss(info, skillID, skillData.getSkillLevel());
			}
			if (!template.isTalent())
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your " + template.getSkillName() + " skill maximum level is now: " + skillData.getSkillMaxLevel());
			if (template.isTalent())
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your " + template.getSkillName() + " talent maximum level is now: " + skillData.getSkillMaxLevel());
		}

		int level = info.statGetCurrentValue("level");
		int downgradeCost = 1; // 5;

		if (!template.isTalent()) {
			skillInfo.pointsSpent -= downgradeCost;
			skillInfo.skillPoints += downgradeCost;
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.skillPoints + " skill points left.");
		}
		if (template.isTalent()) {
			skillInfo.talentPointsSpent -= downgradeCost;
			skillInfo.talentPoints += downgradeCost;
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.talentPoints + " talent points left.");
		}
	}

	public static float skillDiffExp(SkillInfo skillInfo, int skillID, CombatInfo info, int level) {
		SkillData skillData = skillInfo.skills.get(skillID);
		SkillTemplate template = Agis.SkillManager.get(skillID);
		if (template.getSkillProfileID() > 0) {
			SkillProfileTemplate skillProfile = Agis.SkillProfileManager.get(template.getSkillProfileID());
			int diff = skillData.getSkillLevel() - level;
			if (diff < 1)
				return 100f;

			return skillProfile.getLevelDiff(diff);
		}

		return 0f;
	}

	public static boolean checkSkillRequirement(SkillInfo skillInfo, CombatInfo info, SkillTemplate template) {

		if ((template.mainAspectOnly() && template.getAspect() == info.aspect()) || !template.mainAspectOnly())
		// if (template.getAutomaticallyLearn())
		{
			if (template.getParentSkill() > 0) {
				if (!skillInfo.skills.containsKey(template.getParentSkill())) {
					EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
							new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getParentSkill(), "" + template.getParentSkillLevelReq()));
					return false;
				} else {
					if (skillInfo.skills.get(template.getParentSkill()).skillLevel >= template.getParentSkillLevelReq()) {
						// parent=true;
					} else {
						EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
								new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getParentSkill(), "" + template.getParentSkillLevelReq()));

						// parent=false;
						// continue;
						return false;
					}
				}
			} else {
				// parent=true;
			}
			Log.debug("checkSkillRequirement check PrereqSkill1");

			if (template.getPrereqSkill1() > 0) {
				if (!skillInfo.skills.containsKey(template.getPrereqSkill1())) {
					EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
							new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill1(), "" + template.getPrereqSkill1Level()));
					return false;
				} else {
					if (skillInfo.skills.get(template.getPrereqSkill1()).skillLevel >= template.getPrereqSkill1Level()) {
						// parent=true;
					} else {
						EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
								new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill1(), "" + template.getPrereqSkill1Level()));
						return false;
					}
				}
			} else {
				// parent=true;
			}
			Log.debug("checkSkillRequirement check PrereqSkill2");
			if (template.getPrereqSkill2() > 0) {
				if (!skillInfo.skills.containsKey(template.getPrereqSkill2())) {
					EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
							new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill2(), "" + template.getPrereqSkill2Level()));
					return false;
				} else {
					if (skillInfo.skills.get(template.getPrereqSkill2()).skillLevel >= template.getPrereqSkill2Level()) {
						// parent=true;
					} else {
						EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
								new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill2(), "" + template.getPrereqSkill2Level()));
						return false;
					}
				}
			} else {
				// parent=true;
			}
			Log.debug("checkSkillRequirement check PrereqSkill3");

			if (template.getPrereqSkill3() > 0) {
				if (!skillInfo.skills.containsKey(template.getPrereqSkill3())) {
					EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
							new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill3(), "" + template.getPrereqSkill3Level()));
					return false;
				} else {
					if (skillInfo.skills.get(template.getPrereqSkill3()).skillLevel >= template.getPrereqSkill3Level()) {
						// parent=true;
					} else {
						EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(),
								new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, template.getPrereqSkill3(), "" + template.getPrereqSkill3Level()));
						return false;
					}
				}
			} else {
				// parent=true;
			}
			Log.debug("checkSkillRequirement check Ply level");

			// Check Player Level
			if (template.getPlayerLevelReq() > info.statGetCurrentValue("level")) {
				EventMessageHelper.SendRequirementFailedEvent(info.getOwnerOid(), new RequirementCheckResult(RequirementCheckResult.RESULT_LEVEL_TOO_LOW, template.getPlayerLevelReq(), ""));
				return false;
			}
			Log.debug("checkSkillRequirement pass");
			return true;
		}
		return false;

	}

	protected static void checkAutoLearnSkills(SkillInfo skillInfo, CombatInfo info) {
		Log.debug("checkAutoLearnSkills ");
		for (Integer id : Agis.SkillManager.keySet()) {
			SkillTemplate template = Agis.SkillManager.get(id);
			if (!skillInfo.skills.containsKey(template.skillID)) {
				if ((template.mainAspectOnly() && template.getAspect() == info.aspect()) || !template.mainAspectOnly())
					if (template.getAutomaticallyLearn()) {
						if (template.getParentSkill() > 0) {
							if (!skillInfo.skills.containsKey(template.getParentSkill())) {
								continue;
								// parent=false;
							} else {
								if (skillInfo.skills.get(template.getParentSkill()).skillLevel >= template.getParentSkillLevelReq()) {
									// parent=true;
								} else {
									// parent=false;
									continue;
								}
							}
						} else {
							// parent=true;
						}
						Log.debug("checkAutoLearnSkills check PrereqSkill1");

						if (template.getPrereqSkill1() > 0) {
							if (!skillInfo.skills.containsKey(template.getPrereqSkill1())) {
								continue;
								// parent=false;
							} else {
								if (skillInfo.skills.get(template.getPrereqSkill1()).skillLevel >= template.getPrereqSkill1Level()) {
									// parent=true;
								} else {
									// parent=false;
									continue;
								}
							}
						} else {
							// parent=true;
						}
						Log.debug("checkAutoLearnSkills check PrereqSkill2");
						if (template.getPrereqSkill2() > 0) {
							if (!skillInfo.skills.containsKey(template.getPrereqSkill2())) {
								continue;
								// parent=false;
							} else {
								if (skillInfo.skills.get(template.getPrereqSkill2()).skillLevel >= template.getPrereqSkill2Level()) {
									// parent=true;
								} else {
									// parent=false;
									continue;
								}
							}
						} else {
							// parent=true;
						}
						Log.debug("checkAutoLearnSkills check PrereqSkill3");

						if (template.getPrereqSkill3() > 0) {
							if (!skillInfo.skills.containsKey(template.getPrereqSkill3())) {
								continue;
								// parent=false;
							} else {
								if (skillInfo.skills.get(template.getPrereqSkill3()).skillLevel >= template.getPrereqSkill3Level()) {
									// parent=true;
								} else {
									// parent=false;
									continue;
								}
							}
						} else {
							// parent=true;
						}
						Log.debug("checkAutoLearnSkills check Ply level");

						// Check Player Level
						if (template.getPlayerLevelReq() > info.statGetCurrentValue("level")) {
							continue;
						}
						Log.debug("checkAutoLearnSkills Learn template" + template);
						// Learn Skill
						skillInfo.addSkill(template);
						ClassAbilityClient.skillLevelChange(info.getOid());
					}
			}
		}
		Log.debug("Check Abilities for know skills Start");
		for (SkillData sd : skillInfo.getSkills().values()) {
			if (sd != null) {
				if(Agis.SkillManager.get(sd.getSkillID()) !=null) {
					if (SkillInfo.checkSkillRequirement(info.getCurrentSkillInfo(), info, Agis.SkillManager.get(sd.getSkillID()))) {
						SkillInfo.applyNewAbilities(info, Agis.SkillManager.get(sd.getSkillID()), sd.getSkillLevel());
					}
				}else{
					Log.error("Check Abilities for know skills not found skill definition with id " + sd.getSkillID());
				}
			}
		}
		Log.debug("Check Abilities for know skills End");
		Log.debug("checkAutoLearnSkills End");
	}

	/**
	 * Attempt to increase the players level and max level of the given skill.
	 * 
	 * @param skillInfo
	 * @param skillID
	 * @param info
	 */
	public static void skillUpAttempt(SkillInfo skillInfo, int skillID, CombatInfo info) {
		skillUpAttempt(skillInfo, skillID, info, 0, 1);
	}

	public static void skillUpAttempt(SkillInfo skillInfo, int skillID, CombatInfo info, int experience, int level) {
		Log.debug("skillUpAttempt");
		if (Log.loggingDebug)
			Log.warn("SKILL: skill: " + skillID + " experience: " + experience);
		if (!skillInfo.skills.containsKey(skillID)) {
			Log.warn("SKILL: player does not have this skill: " + skillID + " experience: " + experience);
			return;
		}
		SkillData skillData = skillInfo.skills.get(skillID);
		if (skillData.state != 1) {
			// Skill is not set to skill up
			return;
		}

		SkillData parentSkillData = null;
		if (skillData.getParentSkill() != -1) {
			parentSkillData = skillInfo.skills.get(skillData.getParentSkill());
		}
		SkillTemplate template = Agis.SkillManager.get(skillID);
		if (template.getSkillProfileID() > 0) {
			if (Log.loggingDebug)
				Log.debug("SKILL: skillUpAttempt skillID:" + skillID + " template.getSkillProfileID():" + template.getSkillProfileID());
			SkillProfileTemplate skillProfile = Agis.SkillProfileManager.get(template.getSkillProfileID());
			if (Log.loggingDebug)
				Log.debug("SKILL: skillUpAttempt skillProfile:" + skillProfile + " Level= " + skillData.getSkillLevel() + " Exp= " + skillData.getExperience() + " ExpMax= " + skillData.getExperienceMax()
						+ " Profile MaxExp for level= " + skillProfile.getLevelExp(skillData.getSkillLevel()));
			if (skillData.getExperienceMax() != skillProfile.getLevelExp(skillData.getSkillLevel())) {
				skillData.setExperienceMax(skillProfile.getLevelExp(skillData.getSkillLevel()));
				Log.error("SKILL: skillUpAttempt skill Max Exp does not match the profile settings and is setup according to the profile");
			}
			if (skillData.getExperience() < 0)
				skillData.setExperience(0);
			if (Log.loggingDebug)
				Log.debug("SKILL: skillUpAttempt skillProfile: " + skillProfile + " " + skillData.getExperience() + " " + skillData.getExperienceMax());

			int diff = skillData.getSkillLevel() - level;
			if (Log.loggingDebug)
				Log.debug("SKILL: skillUpAttempt diff Level: " + diff);
			if (diff > 0) {
				float _diff = skillProfile.getLevelDiff(diff);
				if (Log.loggingDebug)
					Log.debug("SKILL: skillUpAttempt _diff XP: " + _diff + " for diff Level: " + diff);
				experience = Math.round(experience * (_diff / 100f));
				if (Log.loggingDebug)
					Log.debug("SKILL: skillUpAttempt experience " + experience);
			}

			if (skillData.getSkillLevel() < skillData.getSkillMaxLevel()) {
				if (canPlayerIncreaseSkill(skillInfo, info)) {
					if (skillData.getExperience() + experience >= skillProfile.getLevelExp(skillData.getSkillLevel())) {
						if (Log.loggingDebug)
							Log.debug("SKILL: increasing skill level experience :" + experience);
						skillData.setExperience(skillData.getExperience() + experience - skillProfile.getLevelExp(skillData.getSkillLevel()));
						skillData.alterSkillLevel(1);

						skillData.setExperienceMax(skillProfile.getLevelExp(skillData.getSkillLevel()));
						while (skillData.getExperience() > skillProfile.getLevelExp(skillData.getSkillLevel())) {
							if (Log.loggingDebug)
								Log.debug("SKILL: increasing skill level (while) exp: " + skillData.getExperience() + " expMax:" + skillProfile.getLevelExp(skillData.getSkillLevel()));
							skillData.setExperience(skillData.getExperience() - skillProfile.getLevelExp(skillData.getSkillLevel()));
							skillData.alterSkillLevel(1);
							skillData.setExperienceMax(skillProfile.getLevelExp(skillData.getSkillLevel()));
						}
						skillPointGain(info, skillID, skillData.getSkillLevel());
						checkAutoLearnSkills(skillInfo, info);
						ClassAbilityClient.skillLevelChange(info.getOid());
					} else {
						if (Log.loggingDebug)
							Log.debug("SKILL: increasing skill level experience" + experience + " exp to low to level up");
						skillData.alterExperience(experience);
					}
				} else {
					Log.debug("SKILL: can not Player Increase Skill ");
				}
			} else {
				Log.debug("SKILL: Max Level");

			}

			if (Log.loggingDebug)
				Log.debug("SKILL: skillUpAttempt after level:" + skillData.getSkillLevel() + " Xp:" + skillData.getExperience() + " XpMax:" + skillData.getExperienceMax());
		} else {
			
			// Calculate skill up chance and do a roll of the dice to see if it should increase
			
			if ((ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS && !skillData.getTalent()) || (ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS && skillData.getTalent())) {
				Log.debug("SKILL: can level only by skill points");
				return;
			}
			
			float increaseChance = CombatHelper.calcSkillUpChance(skillData);
			Random random = new Random();
			int rand = random.nextInt(100000);
			if (Log.loggingDebug)
				Log.debug("SKILL: increaseChance: " + increaseChance + "; rand: " + rand + " for skill: " + skillData.getSkillName());
			if (increaseChance > rand / 1000f && skillData.getSkillLevel() < skillData.getSkillMaxLevel()) {
				// skillData.alterSkillCurrent(1);
				if (parentSkillData == null || parentSkillData.skillLevel > skillData.skillLevel) {
					Log.debug("SKILL: increasing skill level");
					// If we have reached the limit, find a skill to decrease
					if (canPlayerIncreaseSkill(skillInfo, info)) {
						skillData.alterSkillLevel(1);
						checkAutoLearnSkills(skillInfo, info);
						ClassAbilityClient.skillLevelChange(info.getOid());
						skillPointGain(info, skillID, skillData.getSkillLevel());
					}
				}
				/*
				 * if (skillData.getSkillCurrent() == (ClassAbilityPlugin.POINTS_PER_SKILL_LEVEL
				 * * skillData.getSkillLevel() + 10)) { // Time to level up the skill
				 * skillData.alterSkillLevel(1); skillPointGain(info, skillType,
				 * skillData.getSkillLevel()); }
				 */
			}

			if (!ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) {
				// Calculate skill level max skill up chance and do a roll of the dice to see if
				// it should increase
				float maxIncreaseChance = CombatHelper.calcMaxSkillUpChance(skillData);
				rand = random.nextInt(100000);
				if (maxIncreaseChance > rand / 1000f) {
					// Level up the skill max
					if ((parentSkillData == null || parentSkillData.skillMaxLevel > skillData.skillMaxLevel) && skillData.getSkillMaxLevel() < template.getMaxLevel()) {
						Log.debug("SKILL: increasing skill max level");
						skillData.alterSkillMax(1);
					}
				}
			}

			// Also increase the skill level and max level for the parent skill if it exists
			// and the roll is high enough
			if (parentSkillData != null) {
				rand = random.nextInt(100000);
				if (increaseChance > rand / 1000f && parentSkillData.getSkillLevel() < parentSkillData.getSkillMaxLevel()) {
					parentSkillData.alterSkillLevel(1);
					checkAutoLearnSkills(skillInfo, info);
					skillPointGain(info, parentSkillData.getSkillID(), parentSkillData.getSkillLevel());
				}

				if (!ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) {
					template = Agis.SkillManager.get(parentSkillData.getSkillID());
					float maxIncreaseChance = CombatHelper.calcMaxSkillUpChance(skillData);
					rand = random.nextInt(100000);
					if (maxIncreaseChance > rand / 1000f && parentSkillData.getSkillMaxLevel() < template.getMaxLevel()) {
						parentSkillData.alterSkillMax(1);
					}
				}
			}
		}
		Engine.getPersistenceManager().setDirty(info);
	}

	/**
	 * Checks if the total skill level of the player is greater than the max, and if
	 * so, will find another skill to decrease. Returns false if the total skill
	 * level is greater than max, and there is no skill to decrease.
	 * 
	 * @param skillInfo
	 * @param info
	 * @return
	 */
	static boolean canPlayerIncreaseSkill(SkillInfo skillInfo, CombatInfo info) {
		// Get total skillLevel
		int totalSkillLevel = 0;
		for (SkillData skillData : skillInfo.getSkills().values()) {
			totalSkillLevel += skillData.skillLevel;
		}

		// If greater than or equal to max, find skill to decrease
		if (totalSkillLevel >= ClassAbilityPlugin.TOTAL_SKILL_MAX) {
			ArrayList<SkillData> skillsToDecrease = new ArrayList<SkillData>();
			for (SkillData skillData : skillInfo.getSkills().values()) {
				if (skillData.state == -1 && skillData.skillLevel > 0) {
					skillsToDecrease.add(skillData);
				}
			}
			// If no skill to decrease, return false
			if (skillsToDecrease.size() == 0)
				return false;

			// Else randomly decrease a skill
			Random rand = new Random();
			int skillPos = rand.nextInt(skillsToDecrease.size());
			skillsToDecrease.get(skillPos).alterSkillLevel(-1);
			skillPointLoss(info, skillsToDecrease.get(skillPos).getSkillID(), skillsToDecrease.get(skillPos).getSkillLevel());
		}

		return true;
	}

	public static void addSkillPoints(SkillInfo skillInfo, CombatInfo info, int points) {
		if (Log.loggingDebug)
			Log.debug("addSkillPoints skillInfo=" + skillInfo + " info=" + info + " points=" + points + " skillPoints=" + skillInfo.skillPoints + " pointsBought=" + skillInfo.pointsBought);
		skillInfo.skillPoints += points;
		skillInfo.pointsBought += points;
		if (Log.loggingDebug)
			Log.debug("addSkillPoints skillInfo=" + skillInfo + " info=" + info + " points=" + points + " skillPoints=" + skillInfo.skillPoints + " pointsBought=" + skillInfo.pointsBought);

		if (ClassAbilityPlugin.SKILL_POINTS_GIVEN_PER_LEVEL > 0)
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.skillPoints + " skill points to spend.");
		Log.debug("addSkillPoints End");
	}

	public static void addTalentPoints(SkillInfo skillInfo, CombatInfo info, int points) {
		if (Log.loggingDebug)
			Log.debug("addSkillPoints skillInfo=" + skillInfo + " info=" + info + " points=" + points + " talentPoints=" + skillInfo.talentPoints + " talentPointsBought=" + skillInfo.talentPointsBought);
		skillInfo.talentPoints += points;
		skillInfo.talentPointsBought += points;
		if (Log.loggingDebug)
			Log.debug("addSkillPoints skillInfo=" + skillInfo + " info=" + info + " points=" + points + " talentPoints=" + skillInfo.talentPoints + " talentPointsBought=" + skillInfo.talentPointsBought);
		if (ClassAbilityPlugin.TALENT_POINTS_GIVEN_PER_LEVEL > 0)
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.talentPoints + " talent points to spend.");
		if (Log.loggingDebug)
			Log.debug("addSkillPoints End");
	}

	/**
	 * Clear all skills and give the player back their skill points. WARNING: Needs
	 * tested and possibly fixed.
	 * 
	 * @param skillInfo
	 * @param info
	 */
	public static void resetSkills(SkillInfo skillInfo, CombatInfo info) {
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		ArrayList<ArrayList<String>> currentActions = info.getCurrentActions();
		// info.getCurrentActionsOnCurrentBar();
		// info.getCurrentActions();
		Log.debug("resetSkills Start");
		if (Log.loggingDebug)
			Log.debug("resetSkills get currentAbilities=" + currentAbilities + " currentActions=" + currentActions);
		CharacterTemplate ct = ClassAbilityPlugin.getCharacterTemplate(info.getIntProperty("race") + " " + info.aspect());
		if (Log.loggingDebug)
			Log.debug("resetSkills " + info.getIntProperty("race") + " " + info.aspect());
		ArrayList<Integer> startSkills = new ArrayList<Integer>();
		if (ct != null) {
			startSkills = ct.startingSkills;
		}
		Log.debug("resetSkills " + startSkills);
		for (SkillData skillData : skillInfo.skills.values()) {
			if (Log.loggingDebug)
				Log.debug("resetSkills skillData=" + skillData + " talent=" + skillData.talent);
			if (skillData.talent)
				continue;
			if (Log.loggingDebug)
				Log.debug("resetSkills skillData=" + skillData);

			// Go through each skill and reset the players stats from it etc.
			Integer skillID = skillData.getSkillID();
			if (Log.loggingDebug)
				Log.debug("resetSkills skillID=" + skillID);
			SkillTemplate template = Agis.SkillManager.get(skillID);
			int skillLevel = skillData.getSkillMaxLevel();
			String stat = template.getPrimaryStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getSecondaryStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getThirdStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getFourthStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			applyStatModifications(info, template, 0);
			// Now remove any abilities they may have from this skill

			for (int j = 0; j <= skillLevel; j++) {
				// String ability = abilities.get(j);
				ArrayList<SkillAbility> abilities = template.getAbilitiesByLevel(j);
				for (SkillAbility ability : abilities) {
					AgisAbility ab = Agis.AbilityManager.get(ability.abilityID);
					Integer abilityID = ab.getID();
					if (currentAbilities.contains(abilityID)) {
						currentAbilities.remove(abilityID);
						if (ab.getAbilityType() == 2) {
							if (ab instanceof FriendlyEffectAbility) {
								FriendlyEffectAbility Eability = (FriendlyEffectAbility) ab;
								ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
								for (int i = 0; i < effectsToAdd.size(); i++) {
									Log.debug("resetSkills: about to remove passive effect: " + effectsToAdd.get(i).effectId + " to player: " + info.getOwnerOid());
									AgisEffect.removeEffectByID(info, effectsToAdd.get(i).effectId);
								}
							}
						}
					}
					// Check if this ability was on the action bar, if it was, remove it
					for (int i = 0; i < currentActions.size(); i++) {
						Log.debug("resetSkills ability=" + ability + " ");
						if (currentActions.get(i) != null) {
							for (int k = 0; k < currentActions.get(i).size(); k++) {
								if (currentActions.get(i).get(k).equals("a" + abilityID))
									currentActions.get(i).set(k, "");
							}
						}
					}
				}
			}
			if (startSkills.contains(skillID))
				skillData.setSkillLevel(1);
			else
				skillData.setSkillLevel(0);
			// skillData.setSkillCurrent(0);

		}

		int newLevel = info.statGetCurrentValue("level");
		int totalPoints = (newLevel - 1) * ClassAbilityPlugin.SKILL_POINTS_GIVEN_PER_LEVEL;
		skillInfo.skillPoints = totalPoints + skillInfo.pointsBought;
		skillInfo.pointsSpent = 0;
		if (Log.loggingDebug)
			Log.debug("resetSkills set currentAbilities=" + currentAbilities + " currentActions=" + currentActions);

		// Clear all the lists
		info.setCurrentAbilities(currentAbilities);
		// info.setCurrentActionsOnCurrentBar(currentActions);
		info.setCurrentActions(currentActions);

		checkAutoLearnSkills(skillInfo, info);
		ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your skills have been reset.");
		Log.debug("resetSkills End");

	}

	/**
	 * Clear all skills and give the player back their skill points. WARNING: Needs
	 * tested and possibly fixed.
	 * 
	 * @param skillInfo
	 * @param info
	 */
	public static void resetTalents(SkillInfo skillInfo, CombatInfo info) {
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		// ArrayList<String> currentActions = info.getCurrentActionsOnCurrentBar();
		ArrayList<ArrayList<String>> currentActions = info.getCurrentActions();
		Log.debug("resetTalents " + info.getIntProperty("race") + " " + info.aspect());
		if (Log.loggingDebug)
			Log.debug("resetTalents get currentAbilities=" + currentAbilities + " currentActions=" + currentActions);

		CharacterTemplate ct = ClassAbilityPlugin.getCharacterTemplate(info.getIntProperty("race") + " " + info.aspect());
		ArrayList<Integer> startSkills = new ArrayList<Integer>();
		if (ct != null) {
			startSkills = ct.startingSkills;
		}
		if (Log.loggingDebug)
			Log.debug("resetTalents " + startSkills);

		for (SkillData skillData : skillInfo.skills.values()) {
			if (Log.loggingDebug)
				Log.debug("resetTalents skillData=" + skillData + " talent=" + skillData.talent);
			if (!skillData.talent)
				continue;
			Integer skillID = skillData.getSkillID();

			if (Log.loggingDebug)
				Log.debug("resetTalents skillID=" + skillID + " " + skillData);
			// Go through each skill and reset the players stats from it etc.
			SkillTemplate template = Agis.SkillManager.get(skillID);
			int skillLevel = skillData.getSkillLevel();
			String stat = template.getPrimaryStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getSecondaryStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getThirdStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			stat = template.getFourthStat();
			if (!stat.equals("~ none ~"))
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have lost your " + stat + " bonus from " + template.getSkillName());
			applyStatModifications(info, template, 0);
			// Now remove any abilities they may have from this skill

			for (int j = 0; j <= skillLevel; j++) {
				// String ability = abilities.get(j);
				ArrayList<SkillAbility> abilities = template.getAbilitiesByLevel(j);
				for (SkillAbility ability : abilities) {
					AgisAbility ab = Agis.AbilityManager.get(ability.abilityID);
					Integer abilityID = ab.getID();
					if (currentAbilities.contains(abilityID)) {
						currentAbilities.remove(abilityID);
						if (ab.getAbilityType() == 2) {
							if (ab instanceof FriendlyEffectAbility) {
								FriendlyEffectAbility Eability = (FriendlyEffectAbility) ab;
								ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
								for (int i = 0; i < effectsToAdd.size(); i++) {
									Log.debug("resetTalents: about to remove passive effect: " + effectsToAdd.get(i).getEffectId() + " to player: " + info.getOwnerOid());
									AgisEffect.removeEffectByID(info, effectsToAdd.get(i).getEffectId());
								}
							}
						}
					}
					// Check if this ability was on the action bar, if it was, remove it
					for (int i = 0; i < currentActions.size(); i++) {
						if (currentActions.get(i) != null)
							for (int k = 0; k < currentActions.get(i).size(); k++) {
								if (currentActions.get(i).get(k).equals("a" + abilityID))
									currentActions.get(i).set(k, "");
							}
					}
				}
			}
			if (startSkills.contains(skillID))
				skillData.setSkillLevel(1);
			else
				skillData.setSkillLevel(0);
			// skillData.setSkillCurrent(0);
		}
		Log.debug("resetTalents reset points");

		int newLevel = info.statGetCurrentValue("level");
		int totalPoints = (newLevel - 1) * ClassAbilityPlugin.TALENT_POINTS_GIVEN_PER_LEVEL;
		skillInfo.talentPoints = totalPoints + skillInfo.talentPointsBought;
		skillInfo.talentPointsSpent = 0;
		if (Log.loggingDebug)
			Log.debug("resetTalents talentPoints=" + skillInfo.talentPoints + " talentPointsSpent=" + skillInfo.talentPointsSpent);
		if (Log.loggingDebug)
			Log.debug("resetTalents set currentAbilities=" + currentAbilities + " currentActions=" + currentActions);

		// Clear all the lists
		info.setCurrentAbilities(currentAbilities);
		// info.setCurrentActionsOnCurrentBar(currentActions);
		info.setCurrentActions(currentActions);
		checkAutoLearnSkills(skillInfo, info);

		ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "Your Talents have been reset.");
		Log.debug("resetTalents End");

	}

	/**
	 * Gives players skill points for having leveled up. WARNING: Verify this
	 * function is still used, and open it up to allow the amount of points to be
	 * set.
	 * 
	 * @param skillInfo
	 * @param info
	 * @param newLevel
	 */
	public static void levelChanged(SkillInfo skillInfo, CombatInfo info, int newLevel) {
		if (ClassAbilityPlugin.USE_SKILL_PURCHASE_POINTS) {
			int totalPoints = (newLevel - 1) * ClassAbilityPlugin.SKILL_POINTS_GIVEN_PER_LEVEL;
			skillInfo.skillPoints = totalPoints + skillInfo.pointsBought - skillInfo.pointsSpent;
		}
		if (ClassAbilityPlugin.USE_TALENT_PURCHASE_POINTS) {
			int totalPoints = (newLevel - 1) * ClassAbilityPlugin.TALENT_POINTS_GIVEN_PER_LEVEL;
			skillInfo.talentPoints = totalPoints + skillInfo.talentPointsBought - skillInfo.talentPointsSpent;
		}
		// Now we need to refund points for skills that now match the players level
		/*
		 * for (SkillData skillData : skillInfo.skills.values()) { if
		 * (skillData.getSkillMaxLevel() >= newLevel) { skillInfo.skillPoints++;
		 * skillInfo.pointsSpent--; } }
		 */

		if (Log.loggingDebug)
			Log.debug("LEVELUP: setting player skill points to: " + skillInfo.skillPoints + ", talent ponts to " + skillInfo.talentPoints);
		if (ClassAbilityPlugin.SKILL_POINTS_GIVEN_PER_LEVEL > 0)
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.skillPoints + " skill points to spend.");
		if (ClassAbilityPlugin.TALENT_POINTS_GIVEN_PER_LEVEL > 0)
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have " + skillInfo.talentPoints + " talent points to spend.");

		checkAutoLearnSkills(skillInfo, info);

	}

	/**
	 * Not used.
	 * 
	 * @param skillInfo
	 * @param skillID
	 * @param alterValue
	 * @param info
	 */
	public static void increaseSkillCurrent(SkillInfo skillInfo, int skillID, int alterValue, CombatInfo info) {
		SkillData skillData = skillInfo.skills.get(skillID);
		for (int i = 0; i < alterValue; i++) {
			if (skillData.getSkillCurrent() >= (skillData.getSkillMaxLevel() * ClassAbilityPlugin.POINTS_PER_SKILL_LEVEL))
				break;

			skillData.alterSkillCurrent(1);
			if (skillData.getSkillCurrent() == (ClassAbilityPlugin.POINTS_PER_SKILL_LEVEL * skillData.getSkillLevel() + 10)) {
				// Time to level up the skill
				skillData.alterSkillLevel(1);
				checkAutoLearnSkills(skillInfo, info);
				skillPointGain(info, skillID, skillData.getSkillLevel());
			}
		}
	}

	/**
	 * Helper function. When a player learns a new skill this function will give
	 * them the start abilities from that skill.
	 * 
	 * @param info
	 * @param skillID
	 */
	public static void newSkillGained(CombatInfo info, int skillID) {
		// SkillTemplate template = skillTemplates.get(skillType);
		Log.debug("SKILL: learned new skill: " + skillID);
		SkillTemplate template = Agis.SkillManager.get(skillID);
		ArrayList<Integer> abilities = template.getStartAbilityIDs();
		Log.debug("SKILL: learned new skill: " + skillID + " abilities=" + abilities);
		for (int i = 0; i < abilities.size(); i++) {
			int ability = abilities.get(i);
			learnAbility(info, ability);
		}

	}

	/**
	 * Helper function. When a player loses a skill this function will remove the
	 * starting abilities.
	 * 
	 * @param info
	 * @param skillID
	 */
	public static void skillLost(CombatInfo info, int skillID) {
		// SkillTemplate template = skillTemplates.get(skillType);
		SkillTemplate template = Agis.SkillManager.get(skillID);
		ArrayList<Integer> abilities = template.getStartAbilityIDs();
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		for (int i = 0; i < abilities.size(); i++) {
			int ability = abilities.get(i);
			AgisAbility ab = Agis.AbilityManager.get(ability);
			int abilityID = ab.getID();
			currentAbilities.remove(abilityID);
			ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have forgotten the ability " + template.getSkillName());
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<Forgot: " + ability + ">", 16);
			// Check if the action bar has any empty spots, if so, remove the ability from
			// it
			if (ab.getAbilityType() == 1)
				info.removeAbilityAction(abilityID);
			else if (ab.getAbilityType() == 2)
				removePassiveEffect(ab, info);
		}
		info.setCurrentAbilities(currentAbilities);
	}

	public static void unlearnAbility(CombatInfo info, int id) 
	{
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		boolean removedAbility = currentAbilities.remove(id);
		if (removedAbility) {
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<Forgot: " + id + ">", 16);
			// Check if the action bar has any empty spots, if so, remove the ability from
			// it
			AgisAbility ab = Agis.AbilityManager.get(id);
			if (ab.getAbilityType() == 1)
				info.removeAbilityAction(id);
			else if (ab.getAbilityType() == 2)
				removePassiveEffect(ab, info);
		}
		info.setCurrentAbilities(currentAbilities);
		ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), currentAbilities);
		ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
	}

	/**
	 * Helper function. When a player gains a point in a skill this function will
	 * increase the player stats and give them any abilities they have now unlocked.
	 * 
	 * @param info:
	 *            The CombatInfo of the player who increased their skill value
	 * @param skillID:
	 *            The id of skill that was increased
	 * @param skillValue:
	 *            The new value of the skill
	 */
	public static void skillPointGain(CombatInfo info, int skillID, int skillValue) {
		if (Log.loggingDebug)
			Log.debug("SKILL: skillPointGain hit with skill: " + skillID + " skillValue=" + skillValue);
		SkillTemplate template = Agis.SkillManager.get(skillID);
		if (template.isTalent())
			ExtendedCombatMessages.sendAnouncementMessage(info.getOwnerOid(), "Your " + template.getSkillName() + " talent has reached level " + skillValue + "!", "Skill");
		else
			ExtendedCombatMessages.sendAnouncementMessage(info.getOwnerOid(), "Your " + template.getSkillName() + " skill has reached level " + skillValue + "!", "Skill");
		ArrayList<SkillAbility> abilities = template.getAbilitiesByLevel(skillValue);
		if (abilities != null) {
			for (SkillAbility ability : abilities) {
				if (ability.automaticallyLearn)
					learnAbility(info, ability.abilityID);
			}
		}
		if (template.getPrimaryStatInterval() > 0 && (skillValue % template.getPrimaryStatInterval()) == 0) {
			String stat = template.getPrimaryStat();
			Log.debug("SKILL: skillPointGain hit with skill: " + skillID + " 1 stat " + stat);
			if (!stat.equals("~ none ~")) {
				EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_INCREASE, template.getPrimaryStatValue(), stat);
				ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "+"+template.getPrimaryStatValue()+" " + stat + "", 15);
			}
		}
		if (template.getSecondaryStatInterval() > 0 && (skillValue % template.getSecondaryStatInterval()) == 0) {
			String stat = template.getSecondaryStat();
			Log.debug("SKILL: skillPointGain hit with skill: " + skillID + " 2 stat " + stat);
			if (!stat.equals("~ none ~")) {
				EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_INCREASE, template.getSecondaryStatValue(), stat);
				ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "+"+template.getSecondaryStatValue()+" " + stat + "", 15);
			}
		}
		if (template.getThirdStatInterval() > 0 && (skillValue % template.getThirdStatInterval()) == 0) {
			String stat = template.getThirdStat();
			Log.debug("SKILL: skillPointGain hit with skill: " + skillID + " 3 stat " + stat);
			if (!stat.equals("~ none ~")) {
				EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_INCREASE, template.getThirdStatValue(), stat);
				ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "+"+template.getThirdStatValue()+" " + stat + "", 15);
			}
		}
		if (template.getFourthStatInterval() > 0 && (skillValue % template.getFourthStatInterval()) == 0) {
			String stat = template.getFourthStat();
			Log.debug("SKILL: skillPointGain hit with skill: " + skillID + " 4 stat " + stat);
			if (!stat.equals("~ none ~")) {
				EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_INCREASE, template.getFourthStatValue(), stat);
				ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "+"+template.getFourthStatValue()+" " + stat + "", 15);
			}
		}

		applyStatModifications(info, template, skillValue);

		// ANDREW (21/9/17): Terrible hack for Arcfall
		if (template.getSkillName().equals("Stealing")) {
			FactionClient.alterReputation(info.getOwnerOid(), 5, -1);
		}
	}

	/**
	 * Helper function. When a player loses a point in a skill this function will
	 * decrease the player stats and remove any abilities they can no longer use.
	 * 
	 * @param info:
	 *            The CombatInfo of the player who decreased their skill value
	 * @param skillID:
	 *            The ID of skill that was decreased
	 * @param skillValue:
	 *            The new value of the skill
	 */
	public static void skillPointLoss(CombatInfo info, int skillID, int skillValue) {
		SkillTemplate template = Agis.SkillManager.get(skillID);
		ArrayList<SkillAbility> abilities = template.getAbilitiesByLevel(skillValue + 1);
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		if (abilities != null) {
			for (SkillAbility ability : abilities) {
				currentAbilities.remove(ability.abilityID);
				String abilityName = ability.abilityName;
				ChatClient.sendObjChatMsg(info.getOwnerOid(), 2, "You have forgotten the " + template.getSkillName() + " ability: " + abilityName);
				ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<Forgot: " + ability + ">", 16);
				// Check if the action bar has any empty spots, if so, add the ability onto it
				AgisAbility ab = Agis.AbilityManager.get(ability.abilityID);
				if (ab.getAbilityType() == 1)
					info.removeAbilityAction(ability.abilityID);
				else if (ab.getAbilityType() == 2)
					removePassiveEffect(ab, info);
			}
			info.setCurrentAbilities(currentAbilities);
			ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), currentAbilities);
		}
		if ( template.getPrimaryStatInterval() > 0 && (skillValue % template.getPrimaryStatInterval()) == (template.getPrimaryStatInterval() - 1)) {
			String stat = template.getPrimaryStat();
			EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_DECREASE, template.getPrimaryStatValue(), stat);
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<-"+template.getPrimaryStatValue()+" " + stat + ">", 15);
		}
		if (template.getSecondaryStatInterval() > 0 && (skillValue % template.getSecondaryStatInterval()) == (template.getSecondaryStatInterval() - 1)) {
			String stat = template.getSecondaryStat();
			EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_DECREASE, template.getSecondaryStatValue(), stat);
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<-"+template.getSecondaryStatValue()+" " + stat + ">", 15);
		}
		if (template.getThirdStatInterval() > 0 && (skillValue % template.getThirdStatInterval()) == (template.getThirdStatInterval() - 1)) {
			String stat = template.getThirdStat();
			EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_DECREASE, template.getThirdStatValue(), stat);
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<-"+template.getThirdStatValue()+" " + stat + ">", 15);
		}
		if (template.getFourthStatInterval() > 0 && (skillValue % template.getFourthStatInterval()) == (template.getFourthStatInterval() - 1)) {
			String stat = template.getFourthStat();
			EventMessageHelper.SendGeneralEvent(info.getOwnerOid(), EventMessageHelper.STAT_DECREASE, template.getFourthStatValue(), stat);
			ExtendedCombatMessages.sendCombatText(info.getOwnerOid(), "<-"+template.getFourthStatValue()+" " + stat + ">", 15);
		}

		applyStatModifications(info, template, skillValue);
	}

	/**
	 * Adds the given ability to the players list of abilities they know. Adds the
	 * ability to their action bar if there is space and it isn't a passive ability.
	 * 
	 * @param info
	 * @param abilityID
	 */
	public static void learnAbility(CombatInfo info, int abilityID) {
		Log.debug("learnAbility CInfo=" + info + " ability=" + abilityID);
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		if (currentAbilities.contains(abilityID)) {
			// Player already knows the ability
			return;
		}
		Log.debug("SkillInfo.learnAbility " + abilityID);
		AgisAbility ab = Agis.AbilityManager.get(abilityID);
		if (ab == null) {
			Log.debug("SKILL: got invalid ability ID: " + abilityID);
			return;
		}
		Log.debug("SkillInfo.learnAbility " + abilityID + " " + ab.getName());
		// int abilityID = ab.getID();
		currentAbilities.add(abilityID);
		// Send a combat event that the client can pick up and display a message for.
		EventMessageHelper.SendCombatEvent(info.getOwnerOid(), info.getOwnerOid(), EventMessageHelper.COMBAT_ABILITY_LEARNED, abilityID, -1, -1, -1);
		Log.debug("SkillInfo.learnAbility " + abilityID + " " + ab.getAbilityType());

		if (ab.getAbilityType() == 2) {
			Log.debug("SkillInfo.learnAbility " + abilityID + " applyPassive");

			// Passive ability, lets apply the effect now
			applyPassiveEffects(ab, info);
		} else if (ClassAbilityPlugin.AUTO_ADD_ABILITIES_TO_ACTION_BAR) {
			Log.debug("SkillInfo.learnAbility " + abilityID + " no passive add to bar");

			// Check if the action bar has any empty spots, if so, add the ability onto it
			ArrayList<String> actions = info.getCurrentActionsOnCurrentBar();
			for (int j = 0; j < 10; j++) {
				if (actions.size() <= j) {
					actions.add("a" + abilityID);
					break;
				} else if (actions.get(j).equals("")) {
					actions.set(j, "a" + abilityID);
					break;
				}
			}
			ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
		}
		info.setCurrentAbilities(currentAbilities);
		ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), currentAbilities);
	}

	
	
	/**
	 * Adds the given ability to the players list of abilities they know. Adds the
	 * ability to their action bar if there is space and it isn't a passive ability.
	 * 
	 * @param info
	 * @param abilityID
	 */
	public static void learnExtraAbility(CombatInfo info, int abilityID) {
		Log.debug("learnExtraAbility CInfo=" + info + " ability=" + abilityID);
		Collection<Integer> currentAbilities = info.getCurrentAbilities();
		if (currentAbilities.contains(abilityID)) {
			// Player already knows the ability
			return;
		}
		Log.debug("SkillInfo.learnExtraAbility " + abilityID);
		AgisAbility ab = Agis.AbilityManager.get(abilityID);
		if (ab == null) {
			Log.debug("SKILL: got invalid ability ID: " + abilityID);
			return;
		}
		Log.debug("SkillInfo.learnExtraAbility " + abilityID + " " + ab.getName());
		// int abilityID = ab.getID();
		currentAbilities.add(abilityID);
		// Send a combat event that the client can pick up and display a message for.
		EventMessageHelper.SendCombatEvent(info.getOwnerOid(), info.getOwnerOid(), EventMessageHelper.COMBAT_ABILITY_LEARNED, abilityID, -1, -1, -1);
		Log.debug("SkillInfo.learnExtraAbility " + abilityID + " " + ab.getAbilityType());

		if (ab.getAbilityType() == 2) {
			Log.debug("SkillInfo.learnExtraAbility " + abilityID + " applyPassive");

			// Passive ability, lets apply the effect now
			applyPassiveEffects(ab, info);
		} else if (ClassAbilityPlugin.AUTO_ADD_ABILITIES_TO_ACTION_BAR) {
			Log.debug("SkillInfo.learnExtraAbility " + abilityID + " no passive add to bar");

			// Check if the action bar has any empty spots, if so, add the ability onto it
			ArrayList<String> actions = info.getCurrentActionsOnCurrentBar();
			for (int j = 0; j < 10; j++) {
				if (actions.size() <= j) {
					actions.add("a" + abilityID);
					break;
				} else if (actions.get(j).equals("")) {
					actions.set(j, "a" + abilityID);
					break;
				}
			}
			ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
		}
		info.setCurrentAbilities(currentAbilities);
		ExtendedCombatMessages.sendExtraAbilities(info.getOwnerOid(), currentAbilities);
	}
	
	/**
	 * Applies the effects from the given ability to the player. Called when a
	 * passive ability is learned.
	 * 
	 * @param ability
	 * @param player
	 */
	
	public static void applyPassiveEffects(AgisAbility ability, CombatInfo player) {
		if (Log.loggingDebug)
			Log.debug("COMBATPLUGIN: about to apply passive ability: " + ability.getID() + " to player: " + player.getOwnerOid());

		if (Log.loggingDebug)
			Log.debug("COMBATPLUGIN: about to apply passive ability: " + ability.getID() + "(" + ability.getName() + ")" + " to player: " + player.getOwnerOid());
		if (ability instanceof FriendlyEffectAbility) {
			FriendlyEffectAbility Eability = (FriendlyEffectAbility) ability;
			ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
			for (int i = 0; i < effectsToAdd.size(); i++) {
				if (effectsToAdd.get(i) != null) {
					if (Log.loggingDebug)
						Log.debug("COMBATPLUGIN: about to apply passive effect: " + effectsToAdd.get(i).getEffectId() + " to player: " + player.getOwnerOid());
					HashMap<String, Serializable> params = new HashMap<String, Serializable>();
					params.put("skillType", Eability.getSkillType());
					params.put("powerUp", 0L);
					AgisEffect effect = Agis.EffectManager.get(effectsToAdd.get(i).getEffectId());
					AgisEffect.applyPassiveEffect(effect, player, player, ability.getID(), params);
				}
			}
		}
	}

	/**
	 * Removed the effects from the given ability from the player. Called when a
	 * passive ability has been forgotten.
	 * 
	 * @param ability
	 * @param player
	 */
	public static void removePassiveEffect(AgisAbility ability, CombatInfo player) {
		if (Log.loggingDebug)
			Log.debug("COMBATPLUGIN: about to remove passive ability: " + ability.getID() + " from player: " + player.getOwnerOid());

		if (ability instanceof FriendlyEffectAbility) {
			FriendlyEffectAbility Eability = (FriendlyEffectAbility) ability;
			ArrayList<AbilityEffectDefinition> effectsToRemove = Eability.getPowerUpDefinition(0L).getEffectDefinition();
			for (int i = 0; i < effectsToRemove.size(); i++) {
				if (Log.loggingDebug)
					Log.debug("COMBATPLUGIN: about to remove passive effect: " + effectsToRemove.get(i).getEffectId() + " from player: " + player.getOwnerOid());
				AgisEffect.removeEffectByID(player, effectsToRemove.get(i).getEffectId());
			}
		}
	}

	/**
	 * Applies stat modifications to the players CombatInfo based on their skill
	 * level for the specified skill template. Removes all existing stat
	 * modifications first and recalculates all values.
	 * 
	 * @param info
	 * @param tmpl
	 */
	public static void applyStatModifications(CombatInfo info, SkillTemplate tmpl, int skillValue) {
		// Clear all stats linked to the stat skill key
		if (!checkSkillRequirement(info.getCurrentSkillInfo(), info, tmpl)) {
			skillValue = 0;
		}
		ArrayList<String> givenStats = new ArrayList<String>();
		String stat = tmpl.getPrimaryStat();
		if (!stat.equals("~ none ~") && !stat.equals("") && tmpl.getPrimaryStatInterval() > 0) {
			int i = (int)skillValue/tmpl.getPrimaryStatInterval();
			info.statReapplyModifier(stat, getStatSkillKey(tmpl, 1), i * tmpl.getPrimaryStatValue(), false);
			// info.statAddModifier(stat, getStatSkillKey(tmpl, 1), skillValue /
			// ClassAbilityPlugin.SKILL_PRIMARY_STAT_GAIN_INCREMENT, false);
			givenStats.add(stat);
		}
		stat = tmpl.getSecondaryStat();
		if (!stat.equals("~ none ~") && !stat.equals("") && tmpl.getSecondaryStatInterval() > 0) {
			int i = (int)skillValue/tmpl.getSecondaryStatInterval();
			info.statReapplyModifier(stat, getStatSkillKey(tmpl, 2), i * tmpl.getSecondaryStatValue(), false);
			// info.statAddModifier(stat, getStatSkillKey(tmpl, 2), skillValue /
			// ClassAbilityPlugin.SKILL_SECONDARY_STAT_GAIN_INCREMENT, false);
			givenStats.add(stat);
		}
		stat = tmpl.getThirdStat();
		if (!stat.equals("~ none ~") && !stat.equals("") && tmpl.getThirdStatInterval() > 0) {
			int i = (int)skillValue/tmpl.getThirdStatInterval();
			info.statReapplyModifier(stat, getStatSkillKey(tmpl, 3), i * tmpl.getThirdStatValue(), false);
			// info.statAddModifier(stat, getStatSkillKey(tmpl, 3), skillValue /
			// ClassAbilityPlugin.SKILL_THIRD_STAT_GAIN_INCREMENT, false);
			givenStats.add(stat);
		}
		stat = tmpl.getFourthStat();
		if (!stat.equals("~ none ~") && !stat.equals("") && tmpl.getFourthStatInterval() > 0) {
			int i = (int)skillValue/tmpl.getFourthStatInterval();
			info.statReapplyModifier(stat, getStatSkillKey(tmpl, 4), i * tmpl.getFourthStatValue(), false);
			// info.statAddModifier(stat, getStatSkillKey(tmpl, 4), skillValue /
			// ClassAbilityPlugin.SKILL_FOURTH_STAT_GAIN_INCREMENT, false);
			givenStats.add(stat);
		}
		if (Log.loggingDebug)
			Log.debug("SkillInfo.applyStatModifications  givenStats=" + givenStats);
		for (String existingStat : CombatPlugin.STAT_LIST) {
			if (givenStats.contains(existingStat)) {
				continue;
			}
			info.statRemoveModifier(existingStat, getStatSkillKey(tmpl, 1), false);
			info.statRemoveModifier(existingStat, getStatSkillKey(tmpl, 2), false);
			info.statRemoveModifier(existingStat, getStatSkillKey(tmpl, 3), false);
			info.statRemoveModifier(existingStat, getStatSkillKey(tmpl, 4), false);
		}

		info.statSendUpdate(false);
	}

	public static void applyNewAbilities(CombatInfo info, SkillTemplate tmpl, int skillValue) {
		for (int i = 0; i <= skillValue; i++) {
			ArrayList<SkillAbility> abilities = tmpl.getAbilitiesByLevel(i);
			if (abilities != null) {
				for (SkillAbility ability : abilities) {
					if (ability.automaticallyLearn && !info.getCurrentAbilities().contains(ability.abilityID))
						learnAbility(info, ability.abilityID);
				}
			}
		}
	}

	public static String getStatSkillKey(SkillTemplate tmpl, int statNum) {
		return "Skill" + tmpl.getSkillID() + "_" + statNum;
	}

	static int SKILL = 0;
	static int TALENT = 0;
	static {
		try {
			BeanInfo info = Introspector.getBeanInfo(CombatInfo.class);
			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
			for (int i = 0; i < propertyDescriptors.length; ++i) {
				PropertyDescriptor pd = propertyDescriptors[i];
				if (pd.getName().equals("skills")) {
					pd.setValue("transient", Boolean.TRUE);
				}
				Log.debug("BeanInfo name="+pd.getName());
			}
		} catch (Exception e) {
			Log.error("failed beans initalization");
		}
	}
	private static final long serialVersionUID = 1L;
}
