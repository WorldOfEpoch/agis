package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisEffect;
import atavism.agis.plugins.CombatPlugin;
import atavism.server.util.Log;

public class VitalityStatDef extends AgisStatDef {
	
	private int min = 0;
	private int max = 100;
	private String maxStat;
	private String shiftModStat;
	private int shiftTarget;
	private String onMax;
	private String onMin;
	private String onThreshold;
	private String onThreshold2;
	private String onThreshold3;
	private String onThreshold4;
	private String onThreshold5;
	
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
	
	
	private int shiftValue = 0; // How much the value of the stat changes each interval
	private int reverseShiftValue = 0;
	private boolean isShiftPercent = false; // Is the shift value a percent
	private int shiftInterval = 2; // Seconds
	// The shift update can only run if the requirement is met (or there is none)
	private ArrayList<StatShiftRequirement> shiftRequirements = new ArrayList<StatShiftRequirement>();
	private int startPercent = 50;
	private int deathResetPercent = -1;
	private int releaseResetPercent = -1;

	public VitalityStatDef(String name, AgisStatDef maxStat) {
		super(name);
		this.maxStat = maxStat.getName();
	}
    
	public VitalityStatDef(String name) {
		super(name);
	}
	
	public int getStartingValue(CombatInfo info) {
		AgisStat depStat = (AgisStat) info.getProperty(maxStat);
		int maxStatValue = 100;
		if (depStat != null) {
			maxStatValue = depStat.getCurrentValue();
			//Log.debug("STAT: got maxStatVal: " + maxStatValue);
		}
		return startPercent * maxStatValue / 100;
	}

	public void update(AgisStat stat, CombatInfo info) {
		if (maxStat != null && !maxStat.isEmpty()) {
			int statMax = info.statGetCurrentValue(maxStat);
			stat.max = statMax;
		} else {
			stat.max = max;
		}
		stat.min = min;
		stat.threshold =threshold;
		stat.threshold2 =threshold2;
		stat.threshold3 =threshold3;
		stat.threshold4 =threshold4;
		stat.setPrecision(precision);
		if (info.dead()) {
			/*if (shiftValue > 0) {
				stat.setBaseValue(0);
			} else {
				if (maxStat != null && !maxStat.isEmpty()) {
					int statMax = info.statGetCurrentValue(maxStat);
					stat.setBaseValue(statMax);
				} else {
					stat.setBaseValue(max);
				}
			}*/
			if (deathResetPercent != -1) {
				int statMax = info.statGetCurrentValue(maxStat);
				stat.setBaseValue(statMax * deathResetPercent / 100);
			}
		}
		stat.setDirty(true);
        super.update(stat, info);
	}
	
	public void notifyFlags(AgisStat stat, CombatInfo info, int oldFlags, int newFlags) {
		if ((shiftTarget == 1 && !info.isUser()) || (shiftTarget == 2 && !info.isMob())) {
			// Don't run the notifyFlags when the target type isn't right
			return;
		}
        if (info.dead() && ((onMax != null && onMax.equals("death")) || (onMin != null && onMin.equals("death")))) {
        	// Need to do anything here?
        	//Log.debug("STAT: target is dead so not checking stat: " + name);
            return;
        }
        if(oldFlags != newFlags) {
        	onLost(oldFlags,stat,info);
        	onHit(newFlags,stat,info);
        	
        }
        
       /* if (((oldFlags ^ newFlags) & AgisStatDef.AGIS_STAT_FLAG_MAX) == 2) {
        	Log.debug("STAT: stat max state changed :"+name);
        	//Log.debug("STAT: newFlags equals: " + (newFlags & AgisStatDef.AGIS_STAT_FLAG_MAX));
            if ((newFlags & AgisStatDef.AGIS_STAT_FLAG_MAX) == 2) {
                // Hit max
            	//Log.debug("STAT: onMax hit for stat: " + name + " with action: " + onMax);
            	onMaxHit(stat, info);
            } else {
                // Not at max anymore
            	//Log.debug("STAT: not at max anymore");
            	onMaxLost(stat, info);
            }
        }
        
        if (((oldFlags ^ newFlags) & AgisStatDef.AGIS_STAT_FLAG_MIN) == 1) {
        	if ((newFlags & AgisStatDef.AGIS_STAT_FLAG_MIN) == 1) {
        		// Do nothing, handled by onMinHit
        		onMinHit(stat, info);
        	} else {
        		onMinLost(stat, info);
        	}
        }*/
	}
	
	
	
	void onHit( int flag ,AgisStat stat, CombatInfo info) {
		if(Log.loggingDebug)
			Log.debug("STAT: onHit hit for stat: " + name + " with action: " + flag);
		String func="";
		switch(flag) {
		case 1:
			func = onMin;
			break;
		case 2:
			func = onMax;
			break;
		case 3:
			func = onThreshold;
			break;
		case 4:
			func = onThreshold2;
			break;
		case 5:
			func = onThreshold3;
			break;
		case 6:
			func = onThreshold4;
			break;
		case 7:
			func = onThreshold5;
			break;
		}
		if (func != null && !func.isEmpty()) {
    		if (func.equals("death")) {
    			Log.debug("STAT: dealing death");
    			CombatPlugin.handleDeath(info);
    			stat.setBaseValue(0);
    		} else if (func.startsWith("effect")) {
    			int effectID = -1;
    			String[] vals = func.split(":");
    			if (vals.length > 1)
    				effectID = Integer.parseInt(vals[1]);
    			if (effectID > 0) {
    				AgisEffect effect = Agis.EffectManager.get(effectID);
    				if (effect != null) {
    					HashMap<String, Serializable> params = new HashMap<String, Serializable>();
    					params.put("result", 1);
    					params.put("skillType", -1);
    					params.put("hitRoll", 100);
    					params.put("dmgType", "");
						params.put("powerUp",0L);
    					if(Log.loggingDebug)
    							Log.debug("Apply Effect "+effectID+" for "+info);
    					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, info, info, -1, params);
    				}
    			}
    		}
    	}
	}
	void onLost(int flag,AgisStat stat, CombatInfo info) {
		//Log.debug("STAT: onMax lost for stat: " + name + " with action: " + onMax);
		if(Log.loggingDebug)
				Log.debug("STAT: onLost hit for stat: " + name + " with action: " + flag);
		String func="";
		switch(flag) {
		case 1:
			func = onMin;
			break;
		case 2:
			func = onMax;
			break;
		case 3:
			func = onThreshold;
			break;
		case 4:
			func = onThreshold2;
			break;
		case 5:
			func = onThreshold3;
			break;
		case 6:
			func = onThreshold4;
			break;
		case 7:
			func = onThreshold5;
			break;
		}
		if (func != null && func.startsWith("effect")) {
			int effectID = -1;
			String[] vals = func.split(":");
			if (vals.length > 1)
				effectID = Integer.parseInt(vals[1]);
			if (effectID > 0) {
				AgisEffect effect = Agis.EffectManager.get(effectID);
				if (effect != null) {
					Log.debug("Remove Effect "+effectID+" for "+info);
					AgisEffect.removeEffectByID(info, effectID);
				}
			}
		}
	}
	
	void onMaxHit(AgisStat stat, CombatInfo info) {
		if(Log.loggingDebug)
				Log.debug("STAT: onMax hit for stat: " + name + " with action: " + onMax);
		if (onMax != null && !onMax.isEmpty()) {
    		if (onMax.equals("death")) {
    			Log.debug("STAT: dealing death");
    			CombatPlugin.handleDeath(info);
    			stat.setBaseValue(0);
    		} else if (onMax.startsWith("effect")) {
    			int effectID = -1;
    			String[] vals = onMax.split(":");
    			if (vals.length > 1)
    				effectID = Integer.parseInt(vals[1]);
    			if (effectID != -1) {
    				AgisEffect effect = Agis.EffectManager.get(effectID);
    				if (effect != null) {
    					HashMap<String, Serializable> params = new HashMap<String, Serializable>();
    					params.put("result", 1);
    					params.put("skillType", -1);
    					params.put("hitRoll", 75);
    					params.put("dmgType", "");
						params.put("powerUp",0L);
    						AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, info, info, -1, params);
    				}
    			}
    		}
    	}
	}
	
	void onMaxLost(AgisStat stat, CombatInfo info) {
		//Log.debug("STAT: onMax lost for stat: " + name + " with action: " + onMax);
		if (onMax != null && onMax.startsWith("effect")) {
			int effectID = -1;
			String[] vals = onMax.split(":");
			if (vals.length > 1)
				effectID = Integer.parseInt(vals[1]);
			if (effectID != -1) {
				AgisEffect effect = Agis.EffectManager.get(effectID);
				if (effect != null) {
					AgisEffect.removeEffectByID(info, effectID);
				}
			}
		}
	}
	
	void onMinHit(AgisStat stat, CombatInfo info) {
		if(Log.loggingDebug)
			Log.debug("STAT: onMin hit for stat: " + name + " with action: " + onMin);
		if (onMin != null && !onMin.isEmpty()) {
    		if (onMin.equals("death")) {
    			Log.debug("STAT: dealing death");
    			CombatPlugin.handleDeath(info);
    			/*if (maxStat != null && !maxStat.isEmpty()) {
    				int statMax = info.statGetCurrentValue(maxStat);
    				stat.setBaseValue(statMax);
    			} else {
    				stat.setBaseValue(max);
    			}*/
    		} else if (onMin.startsWith("effect")) {
    			int effectID = -1;
    			String[] vals = onMin.split(":");
    			if (vals.length > 1)
    				effectID = Integer.parseInt(vals[1]);
    			if(Log.loggingDebug)
    					Log.debug("STAT: effectID: " + effectID);
    			if (effectID != -1) {
    				AgisEffect effect = Agis.EffectManager.get(effectID);
    				if (effect != null) {
    					HashMap<String, Serializable> params = new HashMap<String, Serializable>();
    					params.put("result", 1);
    					params.put("skillType", -1);
    					params.put("hitRoll", 75);
    					params.put("dmgType", "");
						params.put("powerUp",0L);
    					AgisEffect.EffectState eState = AgisEffect.applyEffect(effect, info, info, -1, params);
    				}
    			}
    		}
    	}
	}
	
	void onMinLost(AgisStat stat, CombatInfo info) {
		//Log.debug("STAT: onMin lost for stat: " + name + " with action: " + onMin);
		if (onMin != null && onMin.startsWith("effect")) {
			int effectID = -1;
			String[] vals = onMin.split(":");
			if (vals.length > 1)
				effectID = Integer.parseInt(vals[1]);
			Log.debug("STAT: effectID: " + effectID);
			if (effectID != -1) {
				AgisEffect effect = Agis.EffectManager.get(effectID);
				if (effect != null) {
					AgisEffect.removeEffectByID(info, effectID);
				}
			}
		}
	}
	
	/**
	 * Determines if we want to do a stat shift based on whether the stat is maxed or min-ed out, and
	 * that is meets any other requirements 
	 * @return
	 */
	public int getShiftDirection(AgisStat stat, CombatInfo info) {
		int shiftDirection = 1;
		// Check requirements
		for (StatShiftRequirement shiftReq : shiftRequirements) {
			//Log.debug("STAT: " + name + " checking req: " + shiftReq.requirement + " with reqState: " + shiftReq.reqState 
			//		+ " against actual value: " + info.getBooleanProperty(shiftReq.requirement));
			if (shiftReq.reqState && !info.getBooleanProperty(shiftReq.requirement)) {
				// We need the property set to true, but the info does not have it set to true
				// Check for reverse direction
				if (shiftReq.setReverse)
					shiftDirection = -1;
				else
					return 0;
			} else if (!shiftReq.reqState && info.getBooleanProperty(shiftReq.requirement)) {
				// The requirements say it doesn't want it, but the info has it set to true
				if (shiftReq.setReverse)
					shiftDirection = -1;
				else
					return 0;
			} 
		}
		
		//Log.debug("SHIFT: checking if can shift");
		if (shiftDirection == 1) {
			if (shiftValue == 0) {
				// If the shift is 0 there is no point in running it
				return 0;
			} else if (shiftValue > 0 && stat.current >= stat.max) {
				// Can't increase anymore so return false
				if (onMax != null)
					onMaxHit(stat, info);
				return 0;
			} else if (shiftValue < 0 && stat.current <= stat.min) {
				// Can't decrease the stat any further so return false
				if (onMin != null)
					onMinHit(stat, info);
				return 0;
			}
		} else if (shiftDirection == -1) {
			if(Log.loggingDebug)
				Log.debug("SHIFT: got reversShift with value: " + reverseShiftValue);
			if (reverseShiftValue == 0) {
				// If the shift is 0 there is no point in running it
				return 0;
			} else if (reverseShiftValue > 0 && stat.current >= stat.max) {
				// Can't increase anymore so return false
				if (onMax != null)
					onMaxHit(stat, info);
				return 0;
			} else if (reverseShiftValue < 0 && stat.current <= stat.min) {
				// Can't decrease the stat any further so return false
				if (onMin != null)
					onMinHit(stat, info);
				return 0;
			}
		}
		
		return shiftDirection;
	}
	
	public boolean checkShiftTarget(CombatInfo info) {
		if ((shiftTarget == 1 && !info.isUser()) || (shiftTarget == 2 && !info.isMob())) {
			// Don't run the notifyFlags when the target type isn't right
			return false;
		}
		return true;
	}
	
	public void setMin(int min) {
		this.min = min;
	}
	public int getMin() {
		return min;
	}
	
	public void setMax(int max) {
		this.max = max;
	}
	public int getMax() {
		return max;
	}
	
	public String getMaxStat() {
		return maxStat;
	}
	
	public void setShiftModStat(String stat) {
		shiftModStat = stat;
	}
	public String getShiftModStat() {
		return shiftModStat;
	}
	
	public void setShiftTarget(int shiftTarget) {
		this.shiftTarget = shiftTarget;
	}
	public int getShiftTarget() {
		return shiftTarget;
	}
	
	public void setOnMaxHit(String onMax) {
		this.onMax = onMax;
	}
	public String getOnMaxHit() {
		return onMax;
	}
	
	public void setOnThresholdHit(String hit) {
		this.onThreshold = hit;
	}
	public String getOnThresholdHit() {
		return onThreshold;
	}
	
	public void setOnThreshold2Hit(String hit) {
		this.onThreshold2 = hit;
	}
	public String getOnThreshold2Hit() {
		return onThreshold2;
	}

	public void setOnThreshold3Hit(String hit) {
		this.onThreshold3 = hit;
	}
	public String getOnThreshold3Hit() {
		return onThreshold3;
	}
	public void setOnThreshold4Hit(String hit) {
		this.onThreshold4 = hit;
	}
	public String getOnThreshold4Hit() {
		return onThreshold4;
	}
	public void setOnThreshold5Hit(String hit) {
		this.onThreshold5 = hit;
	}
	public String getOnThreshold5Hit() {
		return onThreshold5;
	}
	
	public void setOnMinHit(String onMin) {
		this.onMin = onMin;
	}
	public String getOnMinHit() {
		return onMin;
	}
	
	public void setShiftInterval(int interval) {
		this.shiftInterval = interval;
	}
	public int getShiftInterval() {
		return shiftInterval;
	}
	
	public void setShiftValue(int shiftValue) {
		this.shiftValue = shiftValue;
	}
	public int getShiftValue() {
		return shiftValue;
	}
	
	public void setReverseShiftValue(int reverseShiftValue) {
		this.reverseShiftValue = reverseShiftValue;
	}
	public int getReverseShiftValue() {
		return reverseShiftValue;
	}
	
	public void isShiftPercent(boolean isShiftPercent) {
		this.isShiftPercent = isShiftPercent;
	}
	public boolean getIsShiftPercent() {
		return isShiftPercent;
	}
	
	public void setStartPercent(int startPercent) {
		this.startPercent = startPercent;
	}
	public int getStartPercent() {
		return startPercent;
	}
	
	public void setDeathResetPercent(int deathResetPercent) {
		this.deathResetPercent = deathResetPercent;
	}
	public int getDeathResetPercent() {
		return deathResetPercent;
	}
	
	public void setReleaseResetPercent(int releaseResetPercent) {
		this.releaseResetPercent = releaseResetPercent;
	}
	public int getReleaseResetPercent() {
		return releaseResetPercent;
	}
	
	public void addShiftRequirement(String req, boolean reqTrue, boolean setReverse) {
		StatShiftRequirement shiftReq = new StatShiftRequirement(req, reqTrue, setReverse);
		shiftRequirements.add(shiftReq);
	}
	
	class StatShiftRequirement {
		// The string to check against
		String requirement;
		// Should the result be true for this requirement to be met
		boolean reqState;
		// If the requirement fails, should the reverse stat be enabled
		boolean setReverse;
	
		public StatShiftRequirement(String req, boolean reqState, boolean setReverse) {
			requirement = req;
			this.reqState = reqState;
			this.setReverse = setReverse;
		}
	}
}