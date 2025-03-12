package atavism.agis.util;

import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.*;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

import java.util.HashMap;

/**
 * Contains static functions to run requirement checks for items, quests and other purposes. 
 * @author Andrew Harrison
 *
 */
public class RequirementChecker {
	
	/**
	 * Checks if the player meets all the requirements specified in the requirements map.
	 * @param oid
	 */
	public static RequirementCheckResult DoesPlayerMeetRequirements(OID oid, HashMap<Integer, HashMap<String, Integer>> requirements) {
		Log.debug("REQ: Running CanPlayerUse requirementCheck "+requirements);
		
		for(int requirementType : requirements.keySet()) {
			Log.debug("REQ: got requirement type: " + requirementType);
			String requirementTypeText = getRequirementTypeText(requirementType);
			Log.debug("REQ: got requirement text: " + requirementTypeText);
			if (requirementTypeText == null) {
				Log.error("RequirementCheck requirement type is null stop checking");
				continue;
			}
			
			if (requirementTypeText.equals("Race")) {
				boolean raceMatched = false;
				for (String race : requirements.get(requirementType).keySet()) {
					int raceID = Integer.parseInt(race);
					if (checkPlayersRace(oid, raceID)) {
						raceMatched = true;
						break;
					}
				}
				if (!raceMatched) {
					return new RequirementCheckResult(RequirementCheckResult.RESULT_WRONG_RACE, -1, "");
				}
			} else if (requirementTypeText.equals("Class")) {
				boolean classMatched = false;
				for (String aspect : requirements.get(requirementType).keySet()) {
					int classID = Integer.parseInt(aspect);
					if (checkPlayersClass(oid, classID)) {
						classMatched = true;
						break;
					}
				}
				if (!classMatched) {
					return new RequirementCheckResult(RequirementCheckResult.RESULT_WRONG_CLASS, -1, "");
				}
			} else if (requirementTypeText.equals("Level")) {
				for (int levelReq : requirements.get(requirementType).values()) {
					if (!checkPlayersLevel(oid, levelReq))
						return new RequirementCheckResult(RequirementCheckResult.RESULT_LEVEL_TOO_LOW, levelReq, "");
				}
			} else if (requirementTypeText.equals("Skill Level")) {
				for (String skill : requirements.get(requirementType).keySet()) {
					int skillID = Integer.parseInt(skill);
					int skillLevelReq = requirements.get(requirementType).get(skill);
					if (!checkPlayersSkillLevel(oid, skillID, skillLevelReq))
						return new RequirementCheckResult(RequirementCheckResult.RESULT_SKILL_TOO_LOW, skillID, "" + skillLevelReq);
				}
			} else if (requirementTypeText.equals("Stat")) {
				for (String stat : requirements.get(requirementType).keySet()) {
					int statValueReq = requirements.get(requirementType).get(stat);
					if (!checkPlayersStat(oid, stat, statValueReq))
						return new RequirementCheckResult(RequirementCheckResult.RESULT_STAT_TOO_LOW, statValueReq, stat);
				}
			} else if (requirementTypeText.equals("Guild Level")) {
				for (int guildLevelReq : requirements.get(requirementType).values()) {
					//int guildLevel = Integer.parseInt(guild);
					//int guildLevelReq = requirements.get(requirementType).get(guildLevel);
					if (!checkPlayersGuildLevel(oid,  guildLevelReq))
						return new RequirementCheckResult(RequirementCheckResult.RESULT_GUILD_TOO_LOW, guildLevelReq, "" );
				}
			}
		}
		
		return new RequirementCheckResult(RequirementCheckResult.RESULT_SUCCESS, -1, "");
	}
	
	static boolean checkPlayersRace(OID oid, int race) {
		Log.debug("REQ: checking players level");
		int playerRace = (Integer)EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
		Log.debug("REQ: comparing players race: " + playerRace + " against race: " + race);
		if (race != playerRace)
			return false;
		return true;
	}
	
	static boolean checkPlayersClass(OID oid, int aspect) {
		Log.debug("REQ: checking players class");
		int playerAspect = (Integer)EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, "aspect");
		Log.debug("REQ: comparing players class: " + playerAspect + " against class: " + aspect);
		if (aspect != playerAspect)
			return false;
		return true;
	}
	
	static boolean checkPlayersLevel(OID oid, int levelReq) {
		Log.debug("REQ: checking players level");
		int playerLevel = CombatClient.getPlayerStatValue(oid, CombatPlugin.LEVEL_STAT);
		Log.debug("REQ: comparing players level: " + playerLevel + " against levelReq: " + levelReq);
		if (playerLevel < levelReq)
			return false;
		return true;
	}
	
	static boolean checkPlayersSkillLevel(OID oid, int skillID, int levelReq) {
		Log.debug("REQ: checking players skill level");
		int playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(oid, skillID);
		Log.debug("REQ: comparing players skill level: " + playerSkillLevel + "of skill " + skillID + "  against levelReq: " + levelReq);
		if (playerSkillLevel < levelReq)
			return false;
		return true;
	}
	
	static boolean checkPlayersStat(OID oid, String stat, int valueReq) {
		Log.debug("REQ: checking players stat");
		int statValue = CombatClient.getPlayerStatValue(oid, stat);
		Log.debug("REQ: comparing players stat: " + stat + " with value: " + statValue + " against valueReq: " + valueReq);
		if (statValue < valueReq)
			return false;
		return true;
	}
	
	static boolean checkPlayersGuildLevel(OID oid, int levelReq) {
		Log.debug("REQ: checking players Guild level");
		if(aDB==null)
			aDB = new AccountDatabase(true);
		
		int playerGuildLevel = aDB.GetGuildLevel(oid);
		Log.debug("REQ: comparing players Guild level: " + playerGuildLevel + " against levelReq: " + levelReq);
		if (playerGuildLevel < levelReq)
			return false;
		return true;
	}

	public static String getRequirementTypeText(int requirementType) {
		if (REQUIREMENT_OPTION_MAP != null) {
			if (REQUIREMENT_OPTION_MAP.choiceMapping.containsKey(requirementType)) {
				return REQUIREMENT_OPTION_MAP.choiceMapping.get(requirementType);
			}
		}else {
			Log.error("Requirement Option Map is null");
		}
		return null;
	}
	
	public static int getRaceID(String race) {
		if (RACE_OPTION_MAP != null) {
			for (int raceID : RACE_OPTION_MAP.choiceMapping.keySet()) {
				if (RACE_OPTION_MAP.choiceMapping.get(raceID).equals(race)) {
					return raceID;
				}
			}
		}else {
			Log.error("Race Id Option Map is null");
		}
		return -1;
	}
	
	public static int getClassID(String aspect) {
		if (CLASS_OPTION_MAP != null) {
			for (int classID : CLASS_OPTION_MAP.choiceMapping.keySet()) {
				if (CLASS_OPTION_MAP.choiceMapping.get(classID).equals(aspect)) {
					return classID;
				}
			}
		}else {
			Log.error("Class Option Map is null");
		}
		return -1;
	}
	
	public static String getRace(int race) {
		if (RACE_OPTION_MAP != null) {
			if (RACE_OPTION_MAP.choiceMapping.containsKey(race)) {
				return RACE_OPTION_MAP.choiceMapping.get(race);
			}
		}else {
			Log.error("Race Name Option Map is null");
		}
		return null;
	}
	
	public static String getClass(int aspect) {
		if (CLASS_OPTION_MAP != null) {
			if (CLASS_OPTION_MAP.choiceMapping.containsKey(aspect)) {
				return CLASS_OPTION_MAP.choiceMapping.get(aspect);
			}
		}else {
			Log.error("Class Name Option Map is null");
		}
		return null;
	}
	
	/**
	 * Get Id of the Option choice by name choice
	 * @param Option
	 * @param choiceName
	 * @return
	 */
	public static int getIdEditorOptionChoice(String optionName, String choiceName) {
		for (EditorOptionMapping optionMap : editor_options.values()) {
			if (optionMap.name.equals(optionName)) {
				for (int id : optionMap.choiceMapping.keySet()) {
					if (optionMap.choiceMapping.get(id).equals(choiceName)) {
						return id;
					}
				}

			}
		}
		return -1;
	}
	
	public static String getNameEditorOptionChoice(String optionName, int choiceId) {
		Log.debug("RequirementChecker.getNameEditorOptionChoice optionName=" + optionName + " choiceId=" + choiceId + " ");
		for (EditorOptionMapping optionMap : editor_options.values()) {
			if (optionMap.name.equals(optionName)) {
				Log.debug("RequirementChecker.getNameEditorOptionChoice optionName=" + optionName + " choiceId=" + choiceId + " containsKey="+optionMap.choiceMapping.containsKey(choiceId)+" "+optionMap.choiceMapping);

				if (optionMap.choiceMapping.containsKey(choiceId)) {
					return optionMap.choiceMapping.get(choiceId);
				}
			}

		}

		return "";
	}

	public static HashMap<Integer, String> getEditorOptionChoice(String optionName) {
		for (EditorOptionMapping optionMap : editor_options.values()) {
			if (optionMap.name.equals(optionName)) {
				return optionMap.choiceMapping;
			}
		}
		return null;
	}
	
	
	public static void setEditorOptions(HashMap<Integer, EditorOptionMapping> editorOptions) {
		Log.debug("setEditorOptions "+editorOptions);
		editor_options = editorOptions;
		// work out the requirement ID
		for (EditorOptionMapping optionMap : editor_options.values()) {
			if (optionMap.name.equals("Requirement")) {
				REQUIREMENT_OPTION_MAP = optionMap;
			} else if (optionMap.name.equals("Race")) {
				RACE_OPTION_MAP = optionMap;
			} else if (optionMap.name.equals("Class")) {
				CLASS_OPTION_MAP = optionMap;
			}
		}
	}
	static AccountDatabase aDB;
	static HashMap<Integer, EditorOptionMapping> editor_options = new HashMap<Integer, EditorOptionMapping>();
	static EditorOptionMapping REQUIREMENT_OPTION_MAP = null;
	static EditorOptionMapping RACE_OPTION_MAP = null;
	static EditorOptionMapping CLASS_OPTION_MAP = null;
	
}