package atavism.agis.server.combat;

import atavism.agis.objects.*;

public class ExpMaxStat extends AgisStatDef {
    public ExpMaxStat(String name) {
        super(name);
    }

    public void update(AgisStat stat, CombatInfo info) {
        stat.max = 2000000000;
        stat.min = 0;
        stat.setDirty(true);
        super.update(stat, info);
    }
}