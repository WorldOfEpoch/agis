package atavism.agis.server.combat;

import atavism.agis.objects.*;
import atavism.server.util.Log;

public class ExperienceStat extends AgisStatDef {
    public ExperienceStat(String name) {
        super(name);
    }

    public void setMaxStatName(String maxStatName) {
        this.maxStatName = maxStatName;
    }

    public String getMaxStatName() {
        return maxStatName;
    }
    protected String maxStatName="experience-max";
    public void update(AgisStat stat, CombatInfo info) {

        int xpMax = info.statGetCurrentValue(getMaxStatName());
//        Log.error("ExperienceStat");
        stat.max = xpMax;
        stat.min = 0;
        stat.setDirty(true);
        super.update(stat, info);
    }
}