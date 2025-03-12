package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.objects.SkillTemplate.SkillAbility;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatPlugin;

import java.io.Serializable;
import java.util.*;

import atavism.agis.abilities.FriendlyEffectAbility;
import atavism.agis.core.*;
import atavism.agis.util.EquipHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementChecker;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.DisplayContext.Submesh;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * Effect child class that permanently alters the class(aspect) of the character 
 * 
 */
public class ChangeClassEffect extends AgisEffect {

	public ChangeClassEffect(int id, String name) {
		super(id, name);
	}

	// add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		Log.debug("ChangeClassEffect.apply: Start");
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("ChangeClassEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
		if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ChangeClassEffect: this effect is not for buildings");
			return;
		}
		Log.debug("ChangeClassEffect aspectId " + aspectId);
		CombatInfo obj = state.getTarget();
		try {
			obj.setProperty("aspect", aspectId);
		//	EnginePlugin.setObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "model", model);
			
			PropertyMessage propMsg = new PropertyMessage(obj.getOwnerOid());
			propMsg.setProperty("aspect", aspectId);
			propMsg.setProperty("aspectName", RequirementChecker.getClass(aspectId));

			obj.setProperty("aspectName", RequirementChecker.getClass(aspectId));
			Engine.getAgent().sendBroadcast(propMsg);
	        
			int genderId = obj.getIntProperty("genderId");

			int race = obj.getIntProperty("race");
			Log.debug("ChangeClassEffect race " + race);

			if (race < 1)
				race = (Integer) EnginePlugin.getObjectProperty(obj.getOwnerOid(), WorldManagerClient.NAMESPACE, "race");
			int aspect = obj.aspect();
			CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspectId);
			Log.debug("ChangeClassEffect CharacterTemplate  " + race + " " + aspect + " " + genderId);
			
			//Change xpProfile before calculating stats
			obj.setExpProfile(tmpl.getExpProfile());

			ClassAbilityPlugin.calculatePlayerStats(obj, tmpl);

			for (String statName : obj.getVitalityStats().keySet()) {
				if (!obj.getPropertyMap().containsKey(statName))
					Log.error("ChangeClassEffect CombatInfo not contains Stat " + statName);
				AgisStat stat = (AgisStat) obj.getProperty(statName);
				VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.lookupStatDef(statName);
				if (stat == null) {
					Log.error("ChangeClassEffect Combat Stat " + statName + " not find ");
					continue;
				}
				if (statDef == null) {
					Log.error("ChangeClassEffect Combat Stat def " + statName + " not find ");
					continue;
				}
				stat.setBaseShiftValue(statDef.getShiftValue(), statDef.getReverseShiftValue(), statDef.getIsShiftPercent());

				// Set the canExceedMax property
				if (CombatPlugin.lookupStatDef(stat.getName()).getCanExceedMax()) {
					stat.setCanExceedMax(true);
					Log.debug("ChangeClassEffect MAX: canExceedMax for stat: " + stat.getName());
				}
			}

			Log.debug("ChangeClassEffect skills");
			ArrayList<Integer> startingSkills = new ArrayList<Integer>(tmpl.startingSkills);
			Log.debug("ChangeClassEffect startingSkills=" + tmpl.startingSkills);
			Collection<Integer> currentAbilities = obj.getCurrentAbilities();
			ArrayList<ArrayList<String>> currentActions = obj.getCurrentActions();
			Log.debug("ChangeClassEffect: Start currentAbilities="+currentAbilities+" currentActions="+currentActions);
			ArrayList<Integer> keepAbilities = new ArrayList<Integer>();
			ArrayList<SkillData> SkillsToAplly = new ArrayList<SkillData>();

			for (SkillData sData : obj.getCurrentSkillInfo().getSkills().values()) {
				Log.debug("ChangeClassEffect skill " + sData.getSkillID());
				if (startingSkills.contains(sData.getSkillID())) {
					Log.debug("ChangeClassEffect delete form startingSkills " + sData.getSkillID());
					startingSkills.remove(startingSkills.indexOf(sData.getSkillID()));
				}
				if (SkillInfo.checkSkillRequirement(obj.getCurrentSkillInfo(), obj, Agis.SkillManager.get(sData.getSkillID()))) {
					SkillsToAplly.add(sData);
				/*	SkillInfo.applyStatModifications(obj, Agis.SkillManager.get(sData.getSkillID()), sData.getSkillLevel());
					Log.debug("ChangeClassEffect SkillData " + sData);
					SkillInfo.applyNewAbilities(obj, Agis.SkillManager.get(sData.getSkillID()), sData.getSkillLevel());*/
					for (int i = 0; i <= sData.getSkillLevel(); i++) {
						ArrayList<SkillAbility> abilities = Agis.SkillManager.get(sData.getSkillID()).getAbilitiesByLevel(i);
						if (abilities != null) {
							for (SkillAbility ability : abilities) {
								if (ability.automaticallyLearn)
									keepAbilities.add(ability.abilityID);
							}
						}
					}
				} else {
					if (resetAbility) {
						int skillLevel = sData.getSkillMaxLevel();
						
						for (int j = 0; j <= skillLevel; j++) {
							// String ability = abilities.get(j);
							ArrayList<SkillAbility> abilities = Agis.SkillManager.get(sData.getSkillID()).getAbilitiesByLevel(j);
							for (SkillAbility ability : abilities) {
								AgisAbility ab = Agis.AbilityManager.get(ability.abilityID);
								Integer abilityID = ab.getID();
								if (!keepAbilities.contains(abilityID)) {
									Log.debug("ChangeClassEffect remove Ability "+abilityID); 
									if (currentAbilities.contains(abilityID)) {
										currentAbilities.remove(abilityID);
										if (ab.getAbilityType() == 2) {
											if (ab instanceof FriendlyEffectAbility) {
												FriendlyEffectAbility Eability = (FriendlyEffectAbility) ab;
												ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
												for (int i = 0; i < effectsToAdd.size(); i++) {
													Log.debug("ChangeClassEffect resetSkills: about to remove passive effect: " + effectsToAdd.get(i).getEffectId() + " to player: " + obj.getOwnerOid());
													AgisEffect.removeEffectByID(obj, effectsToAdd.get(i).getEffectId());
												}
											}
										}
									}
									// Check if this ability was on the action bar, if it was, remove it
									for (int i = 0; i < currentActions.size(); i++) {
										Log.debug("ChangeClassEffect resetSkills ability=" + ability + " ");
										if (currentActions.get(i) != null) {
											for (int k = 0; k < currentActions.get(i).size(); k++) {
												if (currentActions.get(i).get(k).equals("a" + abilityID))
													currentActions.get(i).set(k, "");
											}
										}
									}
								}
							}
						}

						Log.debug("ChangeClassEffect: currentAbilities="+currentAbilities+" currentActions="+currentActions);
						
					}
					if (resetSkillStatBonus)
						SkillInfo.applyStatModifications(obj, Agis.SkillManager.get(sData.getSkillID()), 0);
				}
			}
			
			for(SkillData sd : SkillsToAplly) {
				SkillInfo.applyStatModifications(obj, Agis.SkillManager.get(sd.getSkillID()), sd.getSkillLevel());
				Log.debug("ChangeClassEffect SkillData " + sd);
				SkillInfo.applyNewAbilities(obj, Agis.SkillManager.get(sd.getSkillID()), sd.getSkillLevel());
			}
			
			Log.debug("ChangeClassEffect: end currentAbilities="+currentAbilities+" currentActions="+currentActions);
			obj.setCurrentAbilities(currentAbilities);
			obj.setCurrentActions(currentActions);
			Log.debug("ChangeClassEffect startingSkills=" + startingSkills);
			
			for (int skill : startingSkills) {
				Log.debug("ChangeClassEffect SKILL: adding skill: " + skill);
				SkillTemplate stmpl = Agis.SkillManager.get(skill);
				if (stmpl == null)
					continue;
				Log.debug("ChangeClassEffect SKILL: 1 adding skill: " + stmpl.getSkillName());
				obj.getCurrentSkillInfo().addSkill(stmpl);

				Log.debug("ChangeClassEffect SKILL: 2 adding skill: " + skill);
				ArrayList<Integer> abilityIDs = stmpl.getStartAbilityIDs();
				Log.debug("ChangeClassEffect SKILL: got " + abilityIDs.size() + " abilities");
				for (int ability : abilityIDs) {
					SkillInfo.learnAbility(obj, ability);
				}
			}

			Log.debug("ChangeClassEffect apply model");

			String model = "";
			if (tmpl.gender.containsKey(genderId))
				model = tmpl.gender.get(genderId);
			else {
				Map.Entry<Integer, String> entry = tmpl.gender.entrySet().iterator().next();
				int gender = entry.getKey();
				model = entry.getValue();
			}
			EnginePlugin.setObjectProperty(obj.getOid(), WorldManagerClient.NAMESPACE, "model", model);
			ArrayList<Submesh> submeshes = new ArrayList<Submesh>();
			WorldManagerClient.modifyDisplayContext(obj.getOid(), WorldManagerClient.modifyDisplayContextActionReplace, model, submeshes);
			ExtendedCombatMessages.sendAbilities(obj.getOwnerOid(), obj.getCurrentAbilities());
			ExtendedCombatMessages.sendActions(obj.getOwnerOid(), obj.getCurrentActions(), obj.getCurrentActionBar());
			ExtendedCombatMessages.sendSkills(obj.getOwnerOid(), obj.getCurrentSkillInfo());

			Log.debug("ChangeClassEffect apply equipList");
			HashMap<OID, AgisItem> equipList = null;
			try {
				equipList = AgisInventoryClient.getEquipedItems(obj.getOid());
			} catch (Exception e1) {
				Log.debug("ChangeClassEffect Exception: " + e1);
			}

			if (Log.loggingDebug)
				Log.debug("ChangeClassEffect equipList " + (equipList != null ? equipList.size() : "") + " " + equipList);

			if (equipList != null) {
				for (String statName : CombatPlugin.statDefMap.keySet()) {
					if (Log.loggingDebug)
						Log.debug("ChangeClassEffect stat " + statName);
					AgisStat stat = (AgisStat) obj.getProperty(statName);
					if (stat == null) {
						Log.debug("ChangeClassEffect stat " + statName + " is nul for player ");
						continue;
					}
					ArrayList<OID> list = stat.getOidModifiers();
					if (Log.loggingDebug)
						Log.debug("ChangeClassEffect stat " + statName + " modity " + list);
					for (OID oid : list) {
						if (!equipList.containsKey(oid)) {
							if (Log.loggingDebug)
								Log.debug("ChangeClassEffect not found in equipList remove modifier " + oid + " for stat " + statName + " from Ply " + obj.getOid());
							AgisItem ai = AgisInventoryClient.getItem(oid);
							if (Log.loggingDebug)
								Log.debug("ChangeClassEffect item = " + ai);
							if (ai != null) {
								EquipHelper.UpdateEquiperStats(obj.getOid(), null, ai, obj);
							} else {
								obj.statRemoveModifier(statName, oid, false);
								obj.statRemovePercentModifier(statName, oid, false);
							}
						} else {
							if (Log.loggingDebug)
								Log.debug("ChangeClassEffect " + oid + " is equiped ");
						}
					}
				}
			}

		} catch (Exception e) {
			Log.exception("ChangeClassEffect.apply", e);
			;
			e.printStackTrace();
		}

		Log.debug("ChangeClassEffect.apply: End");

	}

	public int getNewClass() {
		return aspectId;
	}

	public void setNewClass(int aspectId) {
		this.aspectId = aspectId;
	}

	protected int aspectId = -1;

	public boolean getResetAbility() {
		return resetAbility;
	}

	public void setResetAbility(boolean resetAbility) {
		this.resetAbility = resetAbility;
	}

	protected boolean resetAbility = false;

	public boolean getSkillStatBonus() {
		return resetSkillStatBonus;
	}

	public void setSkillStatBonus(boolean resetSkillStatBonus) {
		this.resetSkillStatBonus = resetSkillStatBonus;
	}

	protected boolean resetSkillStatBonus = false;

	private static final long serialVersionUID = 1L;
}
