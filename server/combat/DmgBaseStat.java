package atavism.agis.server.combat;

import atavism.agis.objects.*;
import atavism.server.util.Log;

public class DmgBaseStat extends AgisStatDef {
    public DmgBaseStat(String name) {
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
    if(Log.loggingDebug)Log.debug("DmgBaseStat.UPDATE name "+name+" stat: "+stat+", min"+getMin()+" max="+getMax());
        stat.max = getMax();
        stat.min = getMin();
        stat.setDirty(true);
        super.update(stat, info);
    }
}