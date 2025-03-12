package atavism.agis.objects;

/**
 * used in agisobjects . setState
 */
public enum AgisStates {
    Dead("deadState"),
    PVP("pvpstate"),
    Combat("combatstate"),
    QuestAvailable("questavailable"),
    QuestInProgress("questinprogress"),
    QuestConcludable("questconcludable"),
    Attackable("attackable"),
    Lootable("lootable"),
    Stunned("stunned"),
    Movement("movement"),
    ItemAvailable("itemavailable"),
    ItemsToSell("itemstosell"),
    BankTeller("bankteller");
    
    
    /**
     * pass in the string which gets sent over to the client.
     * we dont use toString() since the client and server may have different
     * names
     */
    AgisStates(String encodeStr) {
	this.str = encodeStr;
    }

    public String toString() {
	return str;
    }
    
    String str = null;
}