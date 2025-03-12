package atavism.agis.objects;

public class BaseStatDef extends AgisStatDef {
	
	private int min = 0;
	private int max = 10000000;

	public BaseStatDef(String name) {
		super(name);
	}
    
	public void update(AgisStat stat, CombatInfo info) {
        stat.max = max;
        stat.min = min;
        stat.setDirty(true);
        super.update(stat, info);
	}
	
	public void setMin(int min) {
		this.min = min;
	}
	
	public void setMax(int max) {
		this.max = max;
	}
}