package atavism.agis.objects;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import atavism.agis.core.AgisEffect.EffectState;
import atavism.server.engine.OID;
import atavism.server.network.*;
import atavism.server.util.Log;

public class AgisStat implements Serializable, ClientSerializable {
	protected String name;

	public AgisStat() {
	//	Log.dumpStack("Create AgisStat |");
	}

	public AgisStat(String statName) {
//		Log.error("Create AgisStat || name="+statName);
		this.name = statName;
	}
	public AgisStat(int id, String statName) {

//		Log.error("Create AgisStat || "+id+" name="+statName);
		this.id = id;
		this.name = statName;
	}

	public AgisStat(int id, String statName, int value) {
//		Log.error("Create AgisStat ||| id="+id+" "+statName+" v="+value);
		this.id = id;
		this.name = statName;
		this.base = this.current = this.max = value;
	}
	public AgisStat(String statName, int value) {
//		Log.error("Create AgisStat |V "+statName+" v="+value);
		this.name = statName;
		this.base = this.current = this.max = value;
	}

	public AgisStat(String statName, int min, int max){
//		Log.error("Create AgisStat V "+statName+" min"+min+" max"+max);
		this.name = statName;
		this.min = min;
		this.max = max;
		this.base = this.current = min;
	}
	
	public AgisStat(String statName, int min, int max, boolean startAtMax){
//		Log.error("Create AgisStat V| "+statName+" min"+min+" max"+max+" "+startAtMax);
		this.name = statName;
		this.min = min;
		this.max = max;
		if (startAtMax){
			this.base = this.current = max;
		} else {
			this.base = this.current = min;
		}
	}

	public AgisStat(String statName, int min, int max, int base) {
//		Log.error("Create AgisStat V|| "+statName+" min"+min+" max"+max+" base="+base);
		this.name = statName;
		this.min = min;
		this.max = max;
		this.base = this.current = base;
		this.canExceedMax = false;
	}

	public String toString()
	{
		return "[AgisStat: "+getId()+":"+name+"="+current+" (base="+base+" min="+min+" max="+max+")]";
	}
	public Integer getId() { return id; }
	public void setId(Integer id) { this.id = id; }
	public Integer id = -1;
	public Integer getMin() { return min; }
	public void setMin(Integer min) { this.min = min; }
	public Integer min;

	public Integer getMax() { return max; }
	public void setMax(Integer max) { this.max = max; }
	public Integer max;

	public Integer getBase() { return base; }
	public void setBase(Integer base) { this.base = base; }
	public Integer base;

	public Integer getCurrent() { return current; }
	public void setCurrent(Integer current) { this.current = current; }
	public Integer current;
	
	
	public Float getThreshold() { return threshold; }
	public void setThreshold(float threshold) { this.threshold = threshold; }
	public Float threshold;

	public Float getThreshold2() { return threshold2; }
	public void setThreshold2(float threshold) { this.threshold2 = threshold; }
	public Float threshold2;
	
	public Float getThreshold3() { return threshold3; }
	public void setThreshold3(float threshold) { this.threshold3 = threshold; }
	public Float threshold3;
	
	public Float getThreshold4() { return threshold4; }
	public void setThreshold4(float threshold) { this.threshold4 = threshold; }
	public Float threshold4;

	public Integer getPrecision() {
		return precision;
	}

	public void setPrecision(Integer precision) {
		this.precision = precision;
	}

	protected Integer precision;
	
	
	
	
	public int getShift(int direction) {
		if (direction == -1) {
			if (isShiftPercent) {
				return shiftReverse * max / 100;
			} else {
				return shiftReverse;
			}
		} else {
			if (isShiftPercent) {
				return shift * max / 100;
			} else {
				return shift;
			}
		}
	}
	
	public Integer getShift() { return shift; }
	public void setShift(Integer shift) { this.shift = shift; }
	public Integer shift;
	public Integer getShiftReverse() { return shiftReverse; }
	public void setShiftReverse(Integer shiftReverse) { this.shiftReverse = shiftReverse; }
	public Integer shiftReverse;
	public Integer getShiftBase() { return shiftBase; }
	public void setShiftBase(Integer shiftBase) { this.shiftBase = shiftBase; }
	public Integer shiftBase;
	public boolean isShiftPercent() { return isShiftPercent; }
	public void isShiftPercent(boolean isShiftPercent) { this.isShiftPercent = isShiftPercent; }
	public boolean isShiftPercent;
	
	public Boolean getCanExceedMax() { return canExceedMax; }
	public void setCanExceedMax(Boolean canExceedMax) { 
		this.canExceedMax = canExceedMax;
	}
	transient public boolean canExceedMax = false;

	public Map<Object, Integer> getModifiers() { return modifiers; }
	public void setModifiers(Map<Object, Integer> modifiers) { this.modifiers = modifiers; }
	Map<Object, Integer> modifiers = new ConcurrentHashMap<Object, Integer>();
	
	public Map<Object, Float> getPercentModifiers() { return percentModifiers; }
	public void setPercentModifiers(Map<Object, Float> modifiers) { this.percentModifiers = modifiers; }
	Map<Object, Float> percentModifiers = new ConcurrentHashMap<Object, Float>();
	
	public Map<Object, Integer> getShiftModifiers() { return shiftModifiers; }
	public void setShiftModifiers(Map<Object, Integer> modifiers) { this.shiftModifiers = modifiers; }
	Map<Object, Integer> shiftModifiers = new ConcurrentHashMap<Object, Integer>();

	transient boolean dirty = false;

	public int getFlags() { return flags; }
	public void setFlags(int flags) { this.flags = flags; }
	int flags = 0;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Modifies the base value of the stat by the given amount. Only to be used by statDef and CombatInfo classes.
	 * @param delta
	 */
	public void modifyBaseValue(int delta) {
		base += delta;
		if ((min != null) && (base < min)) {
			base = min;
		}
		if ((max != null) && (base > max) && !canExceedMax) {
			base = max;
		}
		applyMods();
		setDirty(true);
	}
	
	/**
	 * Sets the base value of the stat. Only to be used by statDef and CombatInfo classes.
	 * @param value
	 */
	public void setBaseValue(int value) {
		base = value;
		if ((min != null) && (base < min)) {
			base = min;
		}
		if ((max != null) && (base > max) && !canExceedMax) {
			base = max;
		}
		applyMods();
		setDirty(true);
	}
	public boolean addModifier(Object id, int delta) {
		if(delta==0) return false;
		if(id instanceof EffectState){
			EffectState es = (EffectState)id;
			modifiers.put("EffectState|"+es.getEffectID()+"|"+es.getId(),delta);
		}else {
			modifiers.put(id, delta);
		}
		//Log.error("addModifier id="+id+" delta="+delta);
		applyMods();
		setDirty(true);
		return true;
	}

	public boolean removeModifier(Object id) {
		boolean found = false;
		if(id instanceof EffectState){
			EffectState es = (EffectState)id;
			if(modifiers.containsKey(id) || modifiers.containsKey("EffectState|"+es.getEffectID()+"|"+es.getId())) {
			modifiers.remove("EffectState|"+es.getEffectID()+"|"+es.getId());
			modifiers.remove(id);
			found = true;
			}
		}else {
			if(modifiers.containsKey(id)) {
				modifiers.remove(id);
			found = true;
			}
		}
		if(found) {
			applyMods();
			setDirty(true);
		}
		return found;
	}

	public ArrayList<OID> getOidModifiers() {
		ArrayList<OID> list = new ArrayList<OID>();
		for (Object id : modifiers.keySet()) {
			if (id instanceof OID) {
				list.add((OID) id);
			}
		}
		for (Object id : percentModifiers.keySet()) {
			if (id instanceof OID) {
				list.add((OID) id);
			}
		}
		return list;
	}
	
	public ArrayList<EffectState> getEffectModifiers() {
		ArrayList<EffectState> list = new ArrayList<EffectState>();
		for (Object id : modifiers.keySet()) {
			if (id instanceof EffectState) {
				list.add((EffectState) id);
			}
		}
		for (Object id : percentModifiers.keySet()) {
			if (id instanceof EffectState) {
				list.add((EffectState) id);
			}
		}
		return list;
	}
	public ArrayList<String> getEffectNewModifiers() {
		ArrayList<String> list = new ArrayList<String>();
		for (Object id : modifiers.keySet()) {
			if (id instanceof String) {
				String _id = (String)id;
				if(_id.startsWith("EffectState"))
					list.add(_id);
			}
		}
		for (Object id : percentModifiers.keySet()) {
			if (id instanceof String) {
				String _id = (String)id;
				if(_id.startsWith("EffectState"))
					list.add(_id);
			}
		}
		return list;
	}

	public ArrayList<String> getStringModifiers() {
		ArrayList<String> list = new ArrayList<String>();
		for (Object id : modifiers.keySet()) {
			if (id instanceof String) {
				list.add((String) id);
			}
		}
		for (Object id : percentModifiers.keySet()) {
			if (id instanceof String) {
				list.add((String) id);
			}
		}
		return list;
	}
	public boolean addPercentModifier(Object id, float percent) {
		if(Math.abs(percent)<0.00001f)
			return false;
		if(id instanceof EffectState){
			EffectState es = (EffectState)id;
			percentModifiers.put("EffectState|"+es.getEffectID()+"|"+es.getId(),percent);
		}else {
			percentModifiers.put(id, percent);
		}
		applyMods();
		setDirty(true);
		return true;
	}
	public boolean removePercentModifier(Object id) {
		boolean found = false;
		if(id instanceof EffectState){
			EffectState es = (EffectState)id;
			if(percentModifiers.containsKey(id)|| percentModifiers.containsKey("EffectState|"+es.getEffectID()+"|"+es.getId())) {
				percentModifiers.remove("EffectState|" + es.getEffectID() + "|" + es.getId());
				percentModifiers.remove(id);
				found = true;
			}
		}else {
			if(percentModifiers.containsKey(id)) {
				percentModifiers.remove(id);
				found = true;
			}
		}
		if(found) {
			applyMods();
			setDirty(true);
		}
		return found;
	}
	public void addShiftModifier(Object id, int percent) {
		shiftModifiers.put(id, percent);
		applyShiftMods();
		setDirty(true);
	}
	public void removeShiftModifier(Object id) {
		shiftModifiers.remove(id);
		applyShiftMods();
		setDirty(true);
	}
	
	public void setBaseShiftValue(int value, int reverseValue, boolean shiftPercent) {
		shiftBase = value;
		shift = value;
		shiftReverse = reverseValue;
		isShiftPercent = shiftPercent;
	}

    public void setMaxValue(int value) {
        setMax(value);
        applyMods();
        setDirty(true);
    }

	public int getCurrentValue() {
		if(current == null)
			return 0;
		return current;
	}
	public float getCurrentValueWithPrecision() {
		if(current == null)
			return 0F;
		if(precision == null || precision <= 0)
			return current;
		return (float) current / (float) precision;
	}

	public int getBaseValue() {
		return base;
	}

    public int getMinValue() {
        return min;
    }

    public int getMaxValue() {
        return max;
    }

	public void setDirty(boolean dirty) {
//		Log.dumpStack("Stat "+name+" SetDirty "+dirty+" "+isDirty());
		this.dirty = dirty;
	
		  
	}
	public boolean isDirty() {
		return dirty;
	}
	
	protected void applyMods() {
		//int newFlags = flags & ~(AgisStatDef.AGIS_STAT_FLAG_MIN | AgisStatDef.AGIS_STAT_FLAG_MAX);
		current = base;
	//	Log.error("applyMods Start "+current+" "+this);
	//	Log.error("applyMods mods "+modifiers.keySet());
		for (Integer mod : modifiers.values()) {
			current += mod;
			//Log.error("applyMods after add " + mod + " curr" + current);
		}
	//	Log.error("applyMods % mods "+percentModifiers.keySet());
		// Now add percent modifiers
		for (Float mod : percentModifiers.values()) {
			current += Math.round((float)current * mod / 100f);
			//Log.error("applyMods after add % " + mod + " curr=" + current);
		}
		
		// Check against min and max
		if ((min != null) && (current <= min)) {
			current = min;
		}
		//Log.error("applyMods current "+current);
		if ((max != null) && (current >= max) && !canExceedMax) {
			current = max;
		}
		//Log.error("applyMods End current "+current);
	}
	
	protected void applyShiftMods() {
		shift = shiftBase;
		for (Integer mod : shiftModifiers.values()) {
			current += mod;
		}
	}

	protected int computeFlags() {
		int newFlags = 0;
		if ((min != null) && (current <= min)) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_MIN;
		} else if(threshold !=null && threshold > 0  && current > min && current <= threshold * max) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_TRESHOLD1;
		} else if(threshold2 !=null && threshold2 > 0 && current > min && current <= threshold2 * max) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_TRESHOLD2;
		} else if(threshold3 !=null && threshold3 > 0 && current > min && current <= threshold3 * max) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_TRESHOLD3;
		} else if(threshold4 !=null && threshold4 > 0 && current > min && current <= threshold4 * max) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_TRESHOLD4;
		} else if(current < max) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_TRESHOLD5;
		} else if ((max != null) && (current >= max)) {
			newFlags = AgisStatDef.AGIS_STAT_FLAG_MAX;
		}
		return newFlags;
	}
	
	public boolean isSet(){
		if (current == null){ return false; } else { return true; }
	}
	
	/**
	 * This is where we decide how to send our data over to the client.
	 * In our case, we just send the current value as an integer.
	 */
	public void encodeObject(AOByteBuffer buffer) {
	    AOByteBuffer.putInt(buffer, getCurrentValue());
	}
	
	private static final long serialVersionUID = 1L;
}
