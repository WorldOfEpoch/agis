package atavism.agis.util;

public class RequirementCheckResult {
	
	public String result;
	public int numericData;
	public String stringData;
	
	public RequirementCheckResult(String result, int numericData, String stringData) {
		this.result = result;
		this.numericData = numericData;
		this.stringData = stringData;
	}
	
	public static final String RESULT_SUCCESS = "RequirementResultSuccess";
	public static final String RESULT_WRONG_RACE = "RequirementResultWrongRace";
	public static final String RESULT_WRONG_CLASS = "RequirementResultWrongClass";
	public static final String RESULT_LEVEL_TOO_LOW = "RequirementResultLevelTooLow";
	public static final String RESULT_SKILL_TOO_LOW= "RequirementResultSkillTooLow";
	public static final String RESULT_GUILD_TOO_LOW= "RequirementResultGuildTooLow";
	public static final String RESULT_STAT_TOO_LOW = "RequirementResultStatTooLow";
	
}