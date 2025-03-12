package atavism.agis.server.combat;

import atavism.agis.objects.*;

public class LevelBaseStat extends AgisStatDef {
    public LevelBaseStat(String name) {
        super(name);
    }

    public void update(AgisStat stat, CombatInfo info) {
        stat.max = 10000;
        stat.min = 0;
        stat.setDirty(true);
        super.update(stat, info);
    }
}