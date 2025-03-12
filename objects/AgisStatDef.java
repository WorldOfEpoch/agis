package atavism.agis.objects;

import java.util.*;

import atavism.server.util.*;

public class AgisStatDef {
    public AgisStatDef(String name) {
	this.name = name;
    }

	public String toString(){
		return "[AgisStatDef "+id+":"+name+" precision="+precision+"]";
	}
	public String getName() { return name; }
	protected String name;

	public void setId(int id) {
		this.id = id;
	}

	public int getId() { return id; }
	protected int id;

	public void addDependent(AgisStatDef stat) {
    	dependents.add(stat);
    }
    protected Set<AgisStatDef> dependents = new HashSet<AgisStatDef>();
    
    public int getMobStartingValue() { return mobStartingValue; }
    public void setMobStartingValue(int value) {
    	mobStartingValue = value;
    }
    protected int mobStartingValue;

	public int getPrecision() {
		return precision;
	}

	public void setPrecision(int precision) {
		this.precision = precision;
	}

	public void addPrecision(int precision) {
		this.precision = (int)Math.pow(10, precision);
	}


	protected int precision;
    public int getMobLevelIncrease() { return mobLevelIncrease; }
    public void setMobLevelIncrease(int value) {
    	mobLevelIncrease = value;
    }
    protected int mobLevelIncrease;
    
    public float getMobLevelPercentIncrease() { return mobLevelPercentIncrease; }
    public void setMobLevelPercentIncrease(float value) {
    	mobLevelPercentIncrease = value;
    }
    protected float mobLevelPercentIncrease;

	public Boolean getSendToClient() { return sendToClient; }
	public void setSendToClient(Boolean sendToClient) { this.sendToClient = sendToClient; }
	public Boolean sendToClient = false;

	public Boolean getServerPresent() { return serverPresent; }
	public void setServerPresent(Boolean serverPresent) { this.serverPresent = serverPresent; }
	public Boolean serverPresent = false;


	public Boolean getCanExceedMax() { return canExceedMax; }
	public void setCanExceedMax(Boolean canExceedMax) { this.canExceedMax = canExceedMax; }
	public Boolean canExceedMax = false;

	public void addStatLink(String statname, int pointForChange, int changePerPoint) {
		if(Log.loggingDebug)
			Log.debug("STATDEF: added statlink for " + name + " to stat: " + statname + " with change: " + pointForChange+" to "+changePerPoint);
		statLinks.put(statname, changePerPoint);
		statLinksPoints.put(statname, pointForChange);
	}
	public HashMap<String, Integer> statLinks = new HashMap<String, Integer>();
	public HashMap<String, Integer> statLinksPoints = new HashMap<String, Integer>();

	public void update(AgisStat stat, CombatInfo info) {
    	if ((stat.min != null) &&(stat.base == null))
    		stat.base = stat.min;
    	if ((stat.min != null) && (stat.base <= stat.min)) {
    		stat.base = stat.min;
    	}
    	if ((stat.max != null) && (stat.base >= stat.max)) {
    		stat.base = stat.max;
    	}
    	stat.applyMods();
    	if ((stat.min != null) && (stat.current <= stat.min)) {
    		stat.current = stat.min;
    	}
    	if ((stat.max != null) && (stat.current >= stat.max) && !canExceedMax) {
    		stat.current = stat.max;
    	}

    	int oldFlags = stat.flags;
    	stat.flags = stat.computeFlags();

    	for (AgisStatDef statDef : dependents) {
    		AgisStat depStat = (AgisStat) info.getProperty(statDef.name);
    		if (depStat != null) {
    			if(Log.loggingDebug)
    					Log.debug("AgisStatDef.update: stat=" + name + " updating dependent stat=" + statDef.getName());
    			// Reapply the stat modifiers
    			if (statLinks.containsKey(statDef.name)) {
    				//info.statRemoveModifier(statDef.name, "stat_" + name);
    				info.statReapplyModifier(statDef.name, "stat_" + name, ((int)(stat.current / (float)statLinksPoints.get(statDef.name))*statLinks.get(statDef.name)), false);
    			}
    			statDef.update(depStat, info);
    		}
    	}

    	//Log.debug("STAT: updated stat with value: " + stat.current + " and max: " + stat.max);
    	notifyFlags(stat, info, oldFlags, stat.flags);

    }
    public void notifyFlags(AgisStat stat, CombatInfo info, int oldFlags, int newFlags) {
    }

    public final static int AGIS_STAT_FLAG_MIN = 1;
    public final static int AGIS_STAT_FLAG_MAX = 2;
    public final static int AGIS_STAT_FLAG_TRESHOLD1 = 3;
    public final static int AGIS_STAT_FLAG_TRESHOLD2 = 4;
    public final static int AGIS_STAT_FLAG_TRESHOLD3 = 5;
    public final static int AGIS_STAT_FLAG_TRESHOLD4 = 6;
    public final static int AGIS_STAT_FLAG_TRESHOLD5 = 7;
}
