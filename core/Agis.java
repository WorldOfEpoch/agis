package atavism.agis.core;

import atavism.agis.objects.*;
import atavism.server.engine.*;

public class Agis {
	public static Manager<AgisAbility> AbilityManager = new Manager<AgisAbility>("AbilityManager");

	public static Manager<AgisEffect> EffectManager = new Manager<AgisEffect>("EffectManager");

	public static Manager<SkillTemplate> SkillManager = new Manager<SkillTemplate>("SkillManager");

	public static Manager<SkillProfileTemplate> SkillProfileManager = new Manager<SkillProfileTemplate>("SkillProfileManager");

	public static Manager<Faction> FactionManager = new Manager<Faction>("FactionManager");

	public static Manager<Currency> CurrencyManager = new Manager<Currency>("CurrencyManager");

	public static Manager<LootTable> LootTableManager = new Manager<LootTable>("LootTableManager");

	public static Manager<AgisQuest> QuestManager = new Manager<AgisQuest>("QuestManager");

	public static Manager<Dialogue> DialogueManager = new Manager<Dialogue>("DialogueManager");

	public static Manager<MerchantTable> MerchantTableManager = new Manager<MerchantTable>("MerchantTableManager");

	public static Manager<PetProfile> PetProfile = new Manager<>("PetProfileManager");

    public static int getDefaultCorpseTimeout() {
	return defaultCorpseTimeout;
    }
    public static void setDefaultCorpseTimeout(int timeout) {
	defaultCorpseTimeout = timeout;
    }
    private static int defaultCorpseTimeout = 60000;
}