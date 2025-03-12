package atavism.agis.objects;

import atavism.agis.plugins.CombatPlugin;

public class ResistanceStatDef extends AgisStatDef {

	public ResistanceStatDef(String name) {
		super(name);
	}
    
	public void update(AgisStat stat, CombatInfo info) {
        stat.max = CombatPlugin.RESISTANCE_STAT_MAX;
        stat.min = 0;
        stat.setDirty(true);
        super.update(stat, info);
	}
}