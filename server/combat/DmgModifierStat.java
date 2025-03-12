package atavism.agis.server.combat;

import atavism.agis.objects.*;

public class DmgModifierStat extends AgisStatDef {
    public DmgModifierStat(String name) {
        super(name);
    }

    public int getMin() {
        return min;
    }
    public void setMin(int min) {
        this.min = min;
    }
    public int getMax() {
        return max;
    }
    public void setMax(int max) {
        this.max = max;
    }
    protected int min =-100;
    protected int max =100;
    public void update(AgisStat stat, CombatInfo info) {
        stat.max = getMax();
        stat.min = getMin();
        stat.setDirty(true);
        super.update(stat, info);
    }
}