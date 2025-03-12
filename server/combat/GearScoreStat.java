package atavism.agis.server.combat;

import atavism.agis.objects.*;

public class GearScoreStat extends AgisStatDef {
    public GearScoreStat(String name) {
        super(name);
    }

    public void update(AgisStat stat, CombatInfo info) {
        stat.max = 1000000000;
        stat.min = 0;
        stat.setDirty(true);
        super.update(stat, info);
    }
}