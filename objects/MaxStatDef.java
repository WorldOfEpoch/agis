package atavism.agis.objects;

public class MaxStatDef extends AgisStatDef {
	
	String baseStat;
	String modifierStat;
	int baseValue;
	int modifierValue;

	public MaxStatDef(String name) {
		super(name);
	}
    
	public void update(AgisStat stat, CombatInfo info) {
		stat.max = Integer.MAX_VALUE;
		stat.min = 0;
		modifierValue = info.statGetCurrentValue(modifierStat);
		baseValue = info.statGetCurrentValue(baseStat);
		if (modifierValue == 0) {
		    stat.base = baseValue;
		} else {
		    int calc = baseValue + modifierValue * 10;
		    //Log.debug("MAXSTAT: " + name + " calc2: " + calc + " from modStat: " + modifierStat + " and mod: " + modifierValue);
		    stat.base = calc;
		}
		stat.setDirty(true);
        super.update(stat, info);
	}
	
	public void SetBaseStat(String statName) {
		baseStat = statName;
	}
	
	public void SetModifierStat(String statName) {
		modifierStat = statName;
	}
	
	
}