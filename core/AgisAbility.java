package atavism.agis.core;

import atavism.agis.effects.SpawnEffect;
import atavism.msgsys.NoRecipientsException;
import atavism.server.engine.*;
import atavism.server.objects.Template;
import atavism.server.util.*;
import atavism.server.math.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.telemetry.Prometheus;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.*;
import atavism.agis.database.*;

/**
 * The AgisAbility object describes an action that a mob can perform.
 * <p>
 * When an ability is triggered, a AgisAbility.State object is
 * generated that represents the current state of that instance of the
 * ability. It progresses through a sequence of states, dependent on
 * the configuration of the AgisAbility object.
 * <p>
 * As each state is entered or exited, a method is called that can be
 * overridden to create different types of abilities.
 */
public class AgisAbility {
    public AgisAbility(String name) {
        setName(name);
    }

    /**
     * Returns the string describing this ability, useful for logging.
     * 
     * @return string describing ability
     */
    public String toString() {
        return "[AgisAbility: " + getName() + "]";
    }

    /**
     * Returns if two objects are the same - tested by comparing the ability name.
     * null objects are never equal to any other object including other null objects.
     *
     * @return true if abilities match
     */
    public boolean equals(Object other) {
        AgisAbility otherAbility = (AgisAbility) other;
        boolean val = getName().equals(otherAbility.getName());
        return val;
    }

    /**
     * Returns a hash of the ability name
     *
     * @return hash value of the object's ability name
     */
    public int hashCode() {
        int hash = getName().hashCode();
        return hash;
    }

    /**
     * AgisAbility lock
     */
    transient protected Lock lock = LockFactory.makeLock("AgisAbilityLock");

    /**
     * Sets the name of the ability. This is used to identify the
     * ability, so it should be unique.
     *
     * @param name name for this ability.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the name of the ability.
     *
     * @return name for this ability.
     */
    public String getName() {
        return name;
    }
    String name = null;
    
    public void setID(int id) {
    	this.id = id;
    }
    public int getID() {
    	return id;
    }
    int id = -1;
    
    
    public void setSkillType(int skillType) {
    	this.skillType = skillType;
    }
    public int getSkillType() {
    	return skillType;
    }
    protected int skillType = -1;
    
    public void setDamageType(String damageType) {
    	this.damageType = damageType;
    }
    public String getDamageType() {
    	return damageType;
    }
    protected String damageType = "";
    
    /*
     * 1: Active
     * 2: Passive
     * 3: Profession
     */
    public void setAbilityType(int abilityType) {
    	this.abilityType = abilityType;
    }
    public int getAbilityType() {
    	return abilityType;
    }
    int abilityType = -1;
    
    public void setDuelID(int duelID) {
    	this.duelID = duelID;
    }
    public int getDuelID() {
    	return duelID;
    }
    int duelID = -1;

    public void setCombatState(int combatState) {
        this.combatState = combatState;
    }

    public int getCombatState(){
        return combatState;
    }
    /**
     * Required Combat State to run ability
     * 0 - in combat
     * 1 - outside combat
     * 2 - both
     */
    protected int combatState = 0;

    public enum TargetType {
        UNINIT,
        NONE,
        GROUP,
        LOCATION,
        ANY,
        AREA,
        SINGLE_TARGET,
        OTHER
    }

    public enum TargetSubType {
        UNINIT,
        NONE,
        ENEMY,
        FRIEND_NOT_SELF,
        FRIEND_OR_ENEMY,
        FRIEND,
        SELF,
        ANY,
        OTHER
    }

    public enum AoeType {
    	PLAYER_RADIUS,
    	TARGET_RADIUS,
    	LOCATION_RADIUS,
    	NONE
    }
    
    public enum TargetSpecies {
    	UNINIT,
    	ANY,
    	BEAST,
    	HUMANOID,
    	ELEMENTAL,
    	UNDEAD,
    	PLAYER,
    	NONPLAYER,
    	DRAGON
    }

	/**
	 * Return whether buildings can be attacked by this ability
	 * 
	 * @return
	 */
	public boolean getAttackBuilding() {
		return attackBuilding;
	}

	/**
	 * Set whether buildings can be attacked by this ability
	 * 
	 * @param v
	 */
	public void setAttackBuilding(boolean v) {
		attackBuilding = v;
	}
	protected boolean attackBuilding = false;

	/**
	 * Returns the time the ability takes to activate.
	 *
	 * @return time in ms to activate the ability.
	 */
    public long getActivationDelay() { return activationDelay; }

    /**
     * Sets the time the ability takes to activate.
     *
     * @param time time in ms that the ability takes to activate.
     */
    public void setActivationDelay(long time) { activationDelay = time; }
    protected long activationDelay = 0;
 
    /**
     * Returns the time the ability takes to activate.
     *
     * @return time in ms to activate the ability.
     */
    public long getActivationTime() { return activationTime; }

    /**
     * Sets the time the ability takes to activate.
     *
     * @param time time in ms that the ability takes to activate.
     */
    public void setActivationTime(long time) { activationTime = time; }

    /**
     * Returns if the ability has 0 activation time.
     *
     * @return true if activate time is 0.
     */
    public boolean isInstant() { return activationTime == 0; }
    protected long activationTime = 0;


    /**
     * Returns  the time the ability mob will wait to make next move if ability is not casting in run and have activation time greater then 0.
     *
     * @return time in ms to activate the ability.
     */
    public long getAttackTime() { return attackTime; }

    /**
     * Sets the time the ability mob will wait to make next move if ability is not casting in run and have activation time greater then 0.
     *
     * @param time in ms
     */
    public void setAttackTime(long time) { attackTime = time; }
    protected long attackTime = 0;

    /**
     * Returns the stat cost for successfully activating the ability.
     *
     * @return stat cost for activating the ability.
     */
    public int getActivationCost() { return activationCost; }

    /**
     * Sets the stat cost for successfully activating the ability.
     *
     * @param cost stat cost for activating the ability.
     */
    public void setActivationCost(int cost) { activationCost = cost; }
    protected int activationCost = 0;

    /**
     * Returns percentage of the cost for successfully activating the ability.
     *
     * @return cost for activating the ability.
     */
    public float getActivationCostPercentage() { return activationCostPercentage; }

    /**
     * Sets percentage of the cost for successfully activating the ability.
     *
     * @param cost cost for activating the ability.
     */
    public void setActivationCostPercentage(float cost) { activationCostPercentage = cost; }
    protected float activationCostPercentage = 0;

    
    
    
    /**
     * Returns the name of the property that stat costs are deducted from.
     *
     * @return name of the property that stat costs are deducted from.
     */
    public String getCostProperty() { return costProp; }

    /**
     * Sets the name of the property that stat costs are deducted from.
     *
     * @param name name of the property that stat costs are deducted from.
     */
    public void setCostProperty(String name) { costProp = name; }
    protected String costProp = null;

    /**
     * Returns the time in ms for each pulse of a channelled ability.
     *
     * @return time in ms for each pulse of a channelled ability.
     */
    public long getChannelPulseTime() { return channelPulseTime; }

    /**
     * Sets the time in ms for each pulse of a channelled ability.
     *
     * @param time time in ms for each pulse of a channelled ability.
     */
    public void setChannelPulseTime(long time) { channelPulseTime = time; }
    protected long channelPulseTime = 0;

    /**
     * Returns the number of pulses during the channelled phase.
     *
     * @return number of pulses during the channelled phase for the ability.
     */
    public int getChannelPulses() { return channelPulses; }

    /**
     * Sets the number of pulses during the channelled phase.
     *
     * @param pulses number of pulses during the channelled phase for the ability.
     */
    public void setChannelPulses(int pulses) { channelPulses = pulses; }
    protected int channelPulses = 1;

    /**
     * Returns the stat cost charged for each channelling pulse.
     *
     * @return stat cost charged for each channelling pulse.
     */
    public int getPulseCost() { return pulseCost; }

    /**
     * Sets the stat cost charged for each channelling pulse.
     *
     * @param cost stat cost charged for each channelling pulse.
     */
    public void setPulseCost(int cost) { pulseCost = cost; }
    protected int pulseCost = 0;

    /**
     * Returns the name of the property that stat costs are deducted from.
     *
     * @return name of the property that stat costs are deducted from.
     */
    public String getPulseCostProperty() { return pulseCostProp; }

    /**
     * Sets the name of the property that stat costs are deducted from.
     *
     * @param name name of the property that stat costs are deducted from.
     */
    public void setPulseCostProperty(String name) { pulseCostProp = name; }
    protected String pulseCostProp = null;
    
    
    /**
     * Returns percentage of the cost for pulse of  the ability.
     *
     * @return cost for pulse of the ability.
     */
    public float getPulseCostPercentage() { return pulseCostPercentage; }

    /**
     * Sets percentage of the cost for successfully activating the ability.
     *
     * @param cost cost for activating the ability.
     */
    public void setPulseCostPercentage(float cost) { pulseCostPercentage = cost; }
    protected float pulseCostPercentage = 0;

    /**
     * Returns the time in ms for each pulse of the active phase.
     *
     * @return time in ms for each pulse of the active phase.
     */
    public long getActivePulseTime() { return activePulseTime; }

    /**
     * Set the time in ms for each pulse of the active phase.
     *
     * @param time time in ms for each pulse of the active phase.
     */
    public void setActivePulseTime(long time) { activePulseTime = time; }
    protected long activePulseTime = 0;

    /**
     * Returns the stat cost charged for each pulse of the active phase.
     *
     * @return stat cost charged for each pulse of the active phase.
     */
    public int getActiveCost() { return activePulseCost; }
    public int getActivePulseCost() { return activePulseCost; }

    /**
     * Sets the stat cost charged for each pulse of the active phase.
     *
     * @param cost stat cost charged for each pulse of hte active phase.
     */
    public void setActiveCost(int cost) { activePulseCost = cost; }
    public void setActivePulseCost(int cost) { activePulseCost = cost; }
    protected int activePulseCost = 0;

    /**
     * Returns the icon name for this ability.
     *
     * @return icon name for this ability.
     */
    public String getIcon() { return icon; }

    /**
     * Sets the icon name for this ability.
     *
     * @param icon icon name for this ability.
     */
    public void setIcon(String icon) { this.icon = icon; }
    protected String icon = null;
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    protected String displayName = null;
    
    public String getTooltip() { return tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
    protected String tooltip = null;

    public int getInterceptType() { return interceptType;}
    public void setInterceptType(int interceptType) { this.interceptType = interceptType; }
    protected int interceptType = 0;
    
    public boolean isToggle() { return toggle;}
    public void isToggle(boolean val) { this.toggle = val; }
    protected boolean toggle = false;
    
    protected int tag_to_disable = -1;
    public void setTagToDisable(int id) {
    	tag_to_disable = id;
    }
    public int getTagToDisable() {
    	return tag_to_disable;
    }
    
    protected int tag_to_disable_count = 1;
    public void setTagToDisableCount(int count) {
    	tag_to_disable_count = count;
    }
    public int getTagToDisableCount() {
    	return tag_to_disable_count;
    }
    
    
    
	protected ArrayList<Integer> tags = new ArrayList<Integer>();

	public ArrayList<Integer> getTags() {
		return tags;
	}

	public void addTag(int val) {
		tags.add(val);
	}

    
    /**
     * Returns the minimum range in mm for this ability.
     *
     * @return minimum range in mm for this ability.
     */
    public int getMinRange() { return minRange; }

    /**
     * Sets the minimum range in mm for this ability.
     *
     * @param range minimum range in mm for this ability.
     */
    public void setMinRange(int range) { minRange = range; }
    protected int minRange = 0;

    /**
     * Returns the maximum range in metres for this ability.
     *
     * @return maximum range in metres for this ability.
     */
    public int getMaxRange() { return maxRange; }

    /**
     * Sets the maximum range in metres for this ability.
     *
     * @param range Maximum range in metres for this ability.
     */
    public void setMaxRange(int range) { maxRange = range; }
    protected int maxRange = 0;
    /**
     * Return projectile speed in meters per second 
     * @return 
     */
    
    public float getSpeed() {
    	return speed;
    }
    /**
     * Set projectile speed in meters per second
     * @param speed
     */
    public void setSpeed(float speed) {
    	this.speed =speed;
    }
    protected float speed = 1;
    
    
    
    /**
     * Return part of range for check in meters 
     * @return 
     */
    
    public float getRangeChunkLength() {
    	return chunk_length;
    }
    /**
     * Set part of range for check in meters 
     * @param partRange
     */
    public void setRangeChunkLength(float partRange) {
    	this.chunk_length = partRange;
    }
    protected float chunk_length = 1f;

    
    
    
    
    public boolean getCastingInRun() {
    	return castingInRun ;
    }
    public void setCastingInRun(boolean value) {
    	castingInRun =value;
    }
    protected boolean castingInRun = false;
    /**
     * If the areaOfEffectRadius is greater than zero the system will check for all possible targets
     * in the region and try apply the effects to them.
     * @return area Of Effect Radius
     */
    public float getAreaOfEffectAngle() { return areaOfEffectAngle; }
    public void setAreaOfEffectAngle(float angle) { areaOfEffectAngle = angle; }
    protected float areaOfEffectAngle = 360;

    public float getAreaOfEffectRadius() { return areaOfEffectRadius; }
    public void setAreaOfEffectRadius(float radius) { areaOfEffectRadius = radius; }
    protected float areaOfEffectRadius = 0;
    
    public AoeType getAoEType() { return aoeType; }
    public void setAoETypeType(AoeType type) { aoeType = type; }
    protected AoeType aoeType = AoeType.NONE;
    
    public boolean getReqFacingTarget() { return reqFacingTarget; }
    public void setReqFacingTarget(boolean reqFacingTarget) { this.reqFacingTarget = reqFacingTarget; }
    public boolean reqFacingTarget = true;
    
    public boolean autoRotateToTarget() { return autoRotateToTarget; }
    public void autoRotateToTarget(boolean autoRotateToTarget) { this.autoRotateToTarget = autoRotateToTarget; }
    public boolean autoRotateToTarget = false;
   
    public int getSkillExp() { return skillExp; }
    public void setSkillExp(int skillExp) { this.skillExp = skillExp; }
    protected int skillExp = 0;
   
    // Does this ability require any special positionals?
    // 0 = none; 1 = front; 2 = sides; 3 = back
    public void setPositional(int positional) {
    	position = positional;
    }
    public int getPositional() {
        return position;
    }
    public int position = 0;

    /**
     * Adds a cooldown to this ability. If any of the ability's cooldowns are activate on
     * the mob attempting to activate the ability, it will not be able to activate.
     *
     * @param cd Cooldown to add to this ability.
     */
    public void addCooldown(Cooldown cd) {
	try {
	    lock.lock();
	    cooldownMap.put(cd.getID(), cd);
	}
	finally {
	    lock.unlock();
	}
    }

    /**
     * Removes a cooldown from this ability.
     *
     * @param id id of the cooldown to remove.
     */
    public void removeCooldown(String id) {
	try {
	    lock.lock();
	    cooldownMap.remove(id);
	}
	finally {
	    lock.unlock();
	}
    }

    public Map<String, Cooldown> getCooldownMap() {
	try {
	    lock.lock();
	    return new HashMap<String, Cooldown>(cooldownMap);
	}
	finally {
	    lock.unlock();
	}
    }
    public void setCooldownMap(Map<String, Cooldown> cooldownMap) {
	try {
	    lock.lock();
	    this.cooldownMap = new HashMap<String, Cooldown>(cooldownMap);
	}
	finally {
	    lock.unlock();
	}
    }
    protected Map<String, Cooldown> cooldownMap = new HashMap<String, Cooldown>();
    
    public boolean startCooldownsOnActivation() {
    	return startCooldownsOnActivation;
    }
    public void startCooldownsOnActivation(boolean startCooldowns) {
    	startCooldownsOnActivation = startCooldowns;
    }
    protected boolean startCooldownsOnActivation = false;
    
    
    public boolean getConsumeOnActivation() {
    	return startCooldownsOnActivation;
    }
    public void setConsumeOnActivation(boolean consumeOnActivation) {
    	this.consumeOnActivation = consumeOnActivation;
    }
    protected boolean consumeOnActivation = true;
    
    
    
    // Does this ability require a certain weapon?
    public void addWeaponReq(String weaponType) {
    	weaponReq.add(weaponType);
    }
    public ArrayList<String> getWeaponReq() {
        return weaponReq;
    }
    public ArrayList<String> weaponReq = new ArrayList<String>();
    
    public boolean getDecrementWeaponUses() {
    	return decrementWeaponUses;
    }
    public void setDecrementWeaponUses(boolean decrement) {
    	decrementWeaponUses = decrement;
    }
    protected boolean decrementWeaponUses = false;

    public AbilityComboData getCombo() {
       if(Log.loggingDebug) Log.debug("ability getCombo "+comboData.size());
        try {
            lock.lock();
            Random rand = new Random();
            double roll = rand.nextDouble() * 100D;
            ArrayList<AbilityComboData> list = new ArrayList<AbilityComboData>();
            for (AbilityComboData acd : comboData) {
                double chance = acd.getChance();
                if (roll <= chance) {
                    list.add(acd);
                }
            }
            if (list.size() > 1) {
                int i = rand.nextInt(list.size());
                return list.get(i);
            } else if (list.size() == 1) {
                return list.get(0);
            }
        } finally {
            lock.unlock();
        }
        if(Log.loggingDebug)  Log.debug("Combo not roll END "+comboData.size());
        return null;
    }

    public ArrayList<AbilityComboData> getCombos() {
        if(Log.loggingDebug) Log.debug("ability getCombo "+comboData.size());
        try {
            lock.lock();
            Random rand = new Random();
            double roll = rand.nextDouble() * 100D;
            ArrayList<AbilityComboData> list = new ArrayList<AbilityComboData>();
            for (AbilityComboData acd : comboData) {
                double chance = acd.getChance();
                if (roll <= chance) {
                    list.add(acd);
                }
            }
            if(Log.loggingDebug)  Log.debug("Combo not roll END "+comboData.size());
            return list;
        } finally {
            lock.unlock();
        }
    }

    /**
     *
     * @param acd
     */
    public void addComboData(AbilityComboData acd){
        comboData.add(acd);
    }

    /**
     *
     * @param acd
     * @return
     */
    public ArrayList<AbilityComboData> getComboData(AbilityComboData acd){
        return comboData;
    }

    /**
     *
     */
    protected ArrayList<AbilityComboData> comboData = new ArrayList<AbilityComboData>();


    public AbilityPowerUpData getPowerUpDefinition(Long time){
        if(Log.loggingDebug)  Log.debug("Ability.getPowerUpDefinition: "+id+" powerup.size()="+powerup.size());
        if(powerup.size()==1){
            for (Long t : powerup.keySet()) {
               return powerup.get(t);
            }
        }
        Long maxTime=Long.MAX_VALUE;
        for (Long t : powerup.keySet()) {
            if(Log.loggingDebug)  Log.debug("Ability.getPowerUpDefinition: "+id+" t="+t);
            if (t > time && t < maxTime)
                maxTime = t;
        }
        if(Log.loggingDebug)  Log.debug("Ability.getPowerUpDefinition: "+id+" time="+time+" maxTime="+maxTime+" hes?-"+powerup.containsKey(maxTime)+" count="+powerup.size());
        return powerup.get(maxTime);
    }
    public HashMap<Long, AbilityPowerUpData> getPowerUpDefinitions(){
        return powerup;
    }
    public void addPowerUpDefinition(AbilityPowerUpData apd){
        powerup.put(apd.getThresholdMaxTime(), apd);
    }
    protected HashMap<Long, AbilityPowerUpData> powerup = new HashMap<>();


    public void setPowerUpCoordEffect(CoordinatedEffect ce){
        powerUpCoordEffect = ce;
    }

    public CoordinatedEffect getPowerUpCoordEffect(){
        return powerUpCoordEffect;
    }
    protected CoordinatedEffect powerUpCoordEffect;


    /**
     *
     * @param v
     */
    public void setLineOfSight(boolean v){
        lineOfSight = v;
    }

    /**
     *
     * @return
     */
    public boolean getLineOfSight(){
        return lineOfSight;
    }

    /**
     * Param
     */
    protected boolean lineOfSight = false;
    /**
     * function return if that ability is child and cant be run if paren not roll it
     * @return
     */
    public boolean isChild(){
        return child;
    }

    public void setChild(boolean v){
        child = v;
    }
    protected boolean child = false;
    /**
     * function return if that ability must check if caster is busy
     * @return
     */
    public boolean getCheckBusy(){
        return checkBusy;
    }

    public void setCheckBusy(boolean v){
        checkBusy = v;
    }
    protected boolean checkBusy = false;
    /**
     * function return if that ability must make caster busy
     * @return
     */
    public boolean getMakeBusy(){
        return makeBusy;
    }

    public void setMakeBusy(boolean v){
        makeBusy = v;
    }
    protected boolean makeBusy = false;

    public boolean getWeaponMustBeDrawn(){
        return weaponMustBeDrawn;
    }

    public void setWeaponMustBeDrawn(boolean v){
        weaponMustBeDrawn = v;
    }
    protected boolean weaponMustBeDrawn = false;

    public boolean getDrawnWeaponBefore(){
        return drawnWeaponBefore;
    }

    public void setDrawnWeaponBefore(boolean v){
        drawnWeaponBefore = v;
    }
    protected boolean drawnWeaponBefore = false;


    public boolean getEnemyTargetChangeToSelf(){
        return enemyTargetChangeToSelf;
    }

    public void setEnemyTargetChangeToSelf(boolean v){
        enemyTargetChangeToSelf = v;
    }
    protected boolean enemyTargetChangeToSelf = false;







    /**
     * Adds a reagent requirement to this ability. Reagents are items that are required
     * to be present in inventory, and are consumed when the ability completes the
     * ACTIVATING phase.
     *
     * @param reagent name of the template the reagent was created from.
     * @param count number of reagents.
     * 
     */
    public void addReagent(Integer reagent, Integer count) {
	try {
	    lock.lock();
	    reagentList.put(reagent, count);
	}
	finally {
	    lock.unlock();
	}
    }
    public HashMap<Integer, Integer> getReagentList() {
	try {
	    lock.lock();
	    return new HashMap<Integer, Integer>(reagentList);
	}
	finally {
	    lock.unlock();
	}
    }
    public void setReagentList(HashMap<Integer, Integer> reagentList) {
	try {
	    lock.lock();
	    this.reagentList = new HashMap<Integer, Integer>(reagentList);
	}
	finally {
	    lock.unlock();
	}
    }
    protected HashMap<Integer, Integer> reagentList = new HashMap<Integer, Integer>();
    
    
    public void addConsumeReagent(Integer reagent, Integer count) {
    	try {
    	    lock.lock();
    	    consumeReagentList.put(reagent, count);
    	}
    	finally {
    	    lock.unlock();
    	}
        }
        public HashMap<Integer, Integer> getConsumeReagentList() {
    	try {
    	    lock.lock();
    	    return new HashMap<Integer, Integer>(consumeReagentList);
    	}
    	finally {
    	    lock.unlock();
    	}
        }
        public void setConsumeReagentList(HashMap<Integer, Integer> reagentList) {
    	try {
    	    lock.lock();
    	    this.consumeReagentList = new HashMap<Integer, Integer>(reagentList);
    	}
    	finally {
    	    lock.unlock();
    	}
        }
        protected HashMap<Integer, Integer> consumeReagentList = new HashMap<Integer, Integer>();

    
    
 /*   public boolean getConsumeReagents() {
    	return consumeReagents;
    }
    public void setConsumeReagents(boolean consume) {
    	consumeReagents = consume;
    }
    protected boolean consumeReagents = false;
*/
 /*   public boolean getConsumeReagents(Integer reagent) {
    	if(consumeReagents.containsKey(reagent))
    		return consumeReagents.get(reagent);
    	return false;
    }
    public HashMap<Integer, Boolean> getConsumeReagentsList() {
   		return consumeReagents;
    }
    
    public void setConsumeReagents(Integer reagent,boolean consume) {
    	
    	consumeReagents.put(reagent, consume);
    }
    protected HashMap<Integer, Boolean> consumeReagents = new  HashMap<Integer, Boolean>();
*/
    
    /**
     * Adds a reagent requirement to pulse in this ability. Reagents are items that are required
     * to be present in inventory, and are consumed when the ability completes the
     * CHANNELLING phase.
     *
     * @param reagent name of the template the reagent was created from.
     * @param count number of reagents.
     * 
     */
	public void addPulseReagent(Integer reagent, Integer count) {
		try {
			lock.lock();
			pulseReagentList.put(reagent, count);
		} finally {
			lock.unlock();
		}
	}

	public HashMap<Integer, Integer> getPulseReagentList() {
		try {
			lock.lock();
			return new HashMap<Integer, Integer>(pulseReagentList);
		} finally {
			lock.unlock();
		}
	}

	public void setPulseReagentList(HashMap<Integer, Integer> reagentList) {
		try {
			lock.lock();
			this.pulseReagentList = new HashMap<Integer, Integer>(reagentList);
		} finally {
			lock.unlock();
		}
	}
    protected HashMap<Integer, Integer> pulseReagentList = new HashMap<Integer, Integer>();
    
    
	public void addPulseConsumeReagent(Integer reagent, Integer count) {
		try {
			lock.lock();
			pulseConsumeReagentList.put(reagent, count);
		} finally {
			lock.unlock();
		}
	}

	public HashMap<Integer, Integer> getPulseConsumeReagentList() {
		try {
			lock.lock();
			return new HashMap<Integer, Integer>(pulseConsumeReagentList);
		} finally {
			lock.unlock();
		}
	}

	public void setPulseConsumeReagentList(HashMap<Integer, Integer> reagentList) {
		try {
			lock.lock();
			this.pulseConsumeReagentList = new HashMap<Integer, Integer>(reagentList);
		} finally {
			lock.unlock();
		}
	}
    protected HashMap<Integer, Integer> pulseConsumeReagentList = new HashMap<Integer, Integer>();
    
    

    
    /**
     * Adds a tool requirement to this ability. Tools are items that are required
     * to be present in inventory. They are not consumed.
     *
     * @param tool name of the template the tool was created from.
     */
    public void addTool(int tool) {
	try {
	    lock.lock();
	    toolList.add(tool);
	}
	finally {
	    lock.unlock();
	}
    }
    public ArrayList<Integer> getToolList() {
	try {
	    lock.lock();
	    return new ArrayList<Integer>(toolList);
	}
	finally {
	    lock.unlock();
	}
    }
    public void setToolList(ArrayList<Integer> toolList) {
	try {
	    lock.lock();
	    this.toolList = new ArrayList<Integer>(toolList);
	}
	finally {
	    lock.unlock();
	}
    }
    protected ArrayList<Integer> toolList = new ArrayList<Integer>();
    
    public int getAmmoReq() { return ammoReq; }
    public void setAmmoReq(int ammoReq) { this.ammoReq = ammoReq; }
    protected int ammoReq = 0;
    
    public int getPulseAmmoReq() { return pulseAmmoReq; }
    public void setPulseAmmoReq(int ammoReq) { this.pulseAmmoReq = ammoReq; }
    protected int pulseAmmoReq = 0;
    
    /**
     * Returns the vigor amount added or required for the ability.
     *
     * @return vigor amount.
     */
    public int getVigor() { return vigor; }
    
    /**
     * Sets the amount of vigor to be added or required to activate the ability
     * 
     * @param value the amount of vigor to be added or subtracted
     */
    public void setVigor(int value) { vigor = value; }
    protected int vigor = 0;
    
    /**
     * Returns the vigor amount added or required for the ability.
     *
     * @return vigor amount.
     */
    public String getStance() { return stanceReq; }
    
    /**
     * Sets the amount of vigor to be added or required to activate the ability
     * 
     * @param stance value the amount of vigor to be added or subtracted
     */
    public void setStance(String stance) { stanceReq = stance; }
    protected String stanceReq = "";
    

    /**
     * The casting Anim is for abilities that have a cast, it determines which animation
     * should be used.
     * @return casting anim.
     */
    public String getCastingAnim() { return castingAnim; }
    public void setCastingAnim(String anim) { castingAnim = anim; }
    protected String castingAnim = "";
    
    /**
     * The casting Affinity
     * @return casting Affinity.
     */
    public String getCastingAffinity() { return castingAffinity; }
    public void setCastingAffinity(String affinity) { castingAffinity = affinity; }
    protected String castingAffinity = "";
    
    /**
     * Returns whether a target is required for this ability to activate
     *
     * @return target type for this ability.
     */
    public boolean getReqTarget() { return reqTarget; }

    /**
     * Sets whether a target is required for this ability to activate
     *
     * @param req type target type for this ability.
     */
    public void setReqTarget(boolean req) { reqTarget = req; }
    protected boolean reqTarget = true;

    /**
     * Returns the target type for this ability.
     *
     * @return target type for this ability.
     */
    public TargetType getTargetType() { return targetType; }

    /**
     * Sets the target type for this ability.
     *
     * @param type target type for this ability.
     */
    public void setTargetType(TargetType type) { targetType = type; }
    protected TargetType targetType = TargetType.UNINIT;

    /**
     * Returns the target sub type for this ability.
     *
     * @return target sub type for this ability.
     */
    public TargetSubType getTargetSubType() { return targetSubType; }
    /**
     * Sets the target sub type for this ability.
     *
     * @param type target sub type for this ability.
     */
    public void setTargetSubType(TargetSubType type) { targetSubType = type; }
    protected TargetSubType targetSubType = TargetSubType.UNINIT;

    /**
     * Returns the list of acceptable specific targets for this ability.
     *
     * @return a list of specific targets for this ability.
     */
    public LinkedList<String> getSpecificTargets() { return specificTargets; }

    /**
     * Sets the list of acceptable specific targets for this ability.
     *
     * @param targets: a list of acceptable specific targets.
     */
    public void setSpecificTargets(LinkedList<String> targets) { specificTargets = targets; }
    public void addSpecificTarget(String name) {
    	if (specificTargets == null) {
    		specificTargets = new LinkedList<String>();
    	}
    	specificTargets.add(name);
    }
    protected LinkedList<String> specificTargets = null;
    
    /**
     * Returns the list of acceptable specific targets for this ability.
     *
     * @return a list of specific targets for this ability.
     */
    public LinkedList<TargetSpecies> getTargetableSpecies() { return targetableSpecies; }

    /**
     * Sets the list of acceptable specific targets for this ability.
     *
     * @param targets: a list of acceptable specific targets.
     */
    public void setTargetableSpecies(LinkedList<TargetSpecies> targets) { targetableSpecies = targets; }
    public void addTargetableSpecies(TargetSpecies species) {
    	targetableSpecies.add(species);
    }
    protected LinkedList<TargetSpecies> targetableSpecies = new LinkedList<TargetSpecies>();

    /**
     * Get parameter to check if the target can be dead to use the ability
     *
     * @return int - 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter
     */
    public int getTargetDeath() { return targetDeath; }

    /**
     * Sets parameter to check if the target can be dead to use the ability
     *
     * @param type - 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter
     */
    public void setTargetDeath(int type) { targetDeath = type; }

    /**
     * Parameter to check if the target can be dead to use the ability
     */
    protected int targetDeath = 1; // 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter

    /**
     * Get parameter to check if the caster can be dead to use the ability
     *
     * @return int - 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter
     */
    public int getCasterDeath() { return casterDeath; }

    /**
     * Sets parameter to check if the caster can be dead to use the ability
     *
     * @param type - 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter
     */
    public void setCasterDeath(int type) { casterDeath = type; }

    /**
     * Parameter to check if the caster can be dead to use the ability
     */
    protected int casterDeath = 1; // 0 for dead, 1 for alive, 2 for spirit , 3 for dead or alive, 4 for dead or spirit, 5 for alive and spirit, 6 for doesn't matter



    // Does this ability require any effects on the attacker?
    public void addAttackerEffectReq(int effectReq) {
    	attackerEffectReqs.add(effectReq);
    }
    public LinkedList<Integer> GetAttackerEffectReqs() {
        return attackerEffectReqs;
    }
    public LinkedList<Integer> attackerEffectReqs = new LinkedList<Integer>();
    
    // Does this ability require any effects on the attacker?
    public void addPulseAttackerEffectReq(int effectReq) {
    	pulseAttackerEffectReqs.add(effectReq);
    }
    public LinkedList<Integer> GetPulseAttackerEffectReqs() {
        return pulseAttackerEffectReqs;
    }
    public LinkedList<Integer> pulseAttackerEffectReqs = new LinkedList<Integer>();
    
    
    
    // Does this ability require any effects on the target?
    public void addTargetEffectReq(int effectReq) {
    	targetEffectReqs.add(effectReq);
    }
    public LinkedList<Integer> GetTargetEffectReqs() {
        return targetEffectReqs;
    }
    public LinkedList<Integer> targetEffectReqs = new LinkedList<Integer>();
    
    // Does this ability require any effects on the target?
    public void addPulseTargetEffectReq(int effectReq) {
    	pulseTargetEffectReqs.add(effectReq);
    }
    public LinkedList<Integer> GetPulseTargetEffectReqs() {
        return pulseTargetEffectReqs;
    }
    public LinkedList<Integer> pulseTargetEffectReqs = new LinkedList<Integer>();
    
    // Does this ability eat up any effects on the player?
    public void addAttackerEffectConsumption(int effectReq) {
    	attackerEffectConsumption.add(effectReq);
    }
    public LinkedList<Integer> GetAttackerEffectConsumption() {
        return attackerEffectConsumption;
    }
    public LinkedList<Integer> attackerEffectConsumption = new LinkedList<Integer>();
    
    // Does this ability eat up any effects on the player?
    public void addPulseAttackerEffectConsumption(int effectReq) {
    	pulseAttackerEffectConsumption.add(effectReq);
    }
    public LinkedList<Integer> GetPulseAttackerEffectConsumption() {
        return pulseAttackerEffectConsumption;
    }
    public LinkedList<Integer> pulseAttackerEffectConsumption = new LinkedList<Integer>();
   
    // Does this ability eat up any effects on the target?
    public void addTargetEffectConsumption(int effectReq) {
    	targetEffectConsumption.add(effectReq);
    }
    public LinkedList<Integer> GetTargetEffectConsumption() {
        return targetEffectConsumption;
    }
    public LinkedList<Integer> targetEffectConsumption = new LinkedList<Integer>();

    // Does this ability eat up any effects on the target?
    public void addPulseTargetEffectConsumption(int effectReq) {
    	pulseTargetEffectConsumption.add(effectReq);
    }
    public LinkedList<Integer> GetPulseTargetEffectConsumption() {
        return pulseTargetEffectConsumption;
    }
    public LinkedList<Integer> pulseTargetEffectConsumption = new LinkedList<Integer>();
   

    public boolean getUseGlobalCooldown() { return useGlobalCooldown; }
    public void setUseGlobalCooldown(boolean val) { useGlobalCooldown = val; }
    protected boolean useGlobalCooldown = true;

    public boolean getStationary() { return stationary; }
    public void setStationary(boolean val) { stationary = val; }
    protected boolean stationary = false;

    public boolean getChannelled() { return channelled; }
    public void setChannelled(boolean val) { channelled = val; }
    protected boolean channelled = false;
   
    public boolean getChannelledInRun() { return channelled_in_run; }
    public void setChannelledInRun(boolean val) { channelled_in_run = val; }
    protected boolean channelled_in_run = false;

    public boolean getPersistent() { return persistent; }
    public void setPersistent(boolean val) { persistent = val; }
    protected boolean persistent = false;

//    public boolean addCoordEffect(ActivationState state, CoordinatedEffect effect) {
//	Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
//	if (effectSet == null) {
//	    effectSet = new HashSet<CoordinatedEffect>();
//	    coordEffectMap.put(state, effectSet);
//	}
//	return effectSet.add(effect);
//    }
//    public boolean removeCoordEffect(ActivationState state, CoordinatedEffect effect) {
//	Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
//	if (effectSet == null) {
//	    return false;
//	}
//	return effectSet.remove(effect);
//    }
//    public Collection<CoordinatedEffect> getCoordEffects(ActivationState state) {
//	Set<CoordinatedEffect> effectSet = coordEffectMap.get(state);
//	if (effectSet == null) {
//	    effectSet = new HashSet<CoordinatedEffect>();
//	    coordEffectMap.put(state, effectSet);
//	}
//	return effectSet;
//    }
//    protected Map<ActivationState, Set<CoordinatedEffect>> coordEffectMap =
//	new HashMap<ActivationState, Set<CoordinatedEffect>>();
    
//    /*
//     * This method changes the Coordinated effect result parameter. This
//     * should be called when abilities still hit but the result is a bit
//     * different (i.e. block, partial resist)
//     */
//    protected void changeCoordinatedEffect(String result) {
//    	Set<CoordinatedEffect> effectSet = coordEffectMap.get(ActivationState.COMPLETED);
//    	if (effectSet == null)
//    		return;
//	    Iterator<CoordinatedEffect> iter = effectSet.iterator();
//	    while (iter.hasNext()) {
//	    	CoordinatedEffect effect = iter.next();
//	    	String argument = (String) effect.getArgument("result");
//	    	if (argument != null)
//	    		effect.putArgument("result", result);
//	    }
//    }

    public String getCompleteAnimation() { return completeAnimation; }
    public void setCompleteAnimation(String anim) { completeAnimation = anim; }
    protected String completeAnimation;

    public String getCompleteSound() { return completeSound; }
    public void setCompleteSound(String sound) { completeSound = sound; }
    protected String completeSound;
    
    protected boolean sendSkillUpChance = true;

    /**
     * This method gets the list of combat info objects we should lock.
     * The determination of this list must not lock any of the objects.
     * @param state of Ability
     * @return List of CombatInfo
     */
    public List<CombatInfo> getPotentialTargets(AgisAbilityState state) {
        List<CombatInfo> targets = new LinkedList<CombatInfo>();
        targets.add(state.getTarget());
        return targets;
    }
    
    public static HashMap<OID, TargetsInAreaEntity> sortByValue(HashMap<OID, TargetsInAreaEntity> hm)
    { 
        // Create a list from elements of HashMap 
        List<Map.Entry<OID, TargetsInAreaEntity> > list =
               new LinkedList<Map.Entry<OID, TargetsInAreaEntity> >(hm.entrySet());
  
        // Sort the list 
        Collections.sort(list, new Comparator<Map.Entry<OID, TargetsInAreaEntity> >() {
            public int compare(Map.Entry<OID, TargetsInAreaEntity> o1,
                               Map.Entry<OID, TargetsInAreaEntity> o2)
            { 
                return o1.getValue().getDistance().compareTo(o2.getValue().getDistance());
            } 
        }); 
          
        // put data from sorted list to hashmap  
        HashMap<OID, TargetsInAreaEntity> temp = new LinkedHashMap<OID, TargetsInAreaEntity>();
        for (Map.Entry<OID, TargetsInAreaEntity> aa : list) {
            temp.put(aa.getKey(), aa.getValue()); 
        } 
        return temp; 
    } 
    
    
    /**
     * Should this be merged up into the function above?
     * @param caster combat info of the caster
     * @param targetInfo combat info of the target
     * @param loc point from start serarch targets 
     * @return List Combat Info of the targets
     */
    public ArrayList<CombatInfo> getAoETargets(CombatInfo caster, CombatInfo targetInfo, Point loc , float minRange, float maxRange, AgisAbilityState state ) {
    	long start = System.nanoTime();
        if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets II Start targetType="+targetType);
    	ArrayList<CombatInfo> targetsObjectInArea = new ArrayList<CombatInfo>();
        ArrayList<OID> targetsToCheck = new ArrayList<OID>();
        Map<OID,TargetsInAreaEntity> targetsInArea = new HashMap<OID,TargetsInAreaEntity>();
    	BasicWorldNode casterNode = WorldManagerClient.getWorldNodeNoCache(caster.getOwnerOid());
        Log.error("Ability.getAoETargets id "+getID()+" caster"+caster.getOid()+" CasterYaw="+casterNode);

       // DebugAbility(casterNode, caster);
        //	Log.debug("AgisAbility.getAoETargets casterNode="+casterNode);
        Quaternion casterQuaternion = new Quaternion(casterNode.getOrientation());

        if(state.getDestLocation() != null){
            // Point cLoc = casterNode.getLoc();
            Point _loc = new Point(0f,1f,0f);
            _loc.add(casterNode.getLoc());
            AOVector reqDir = AOVector.sub(state.getDestLocation(), _loc);
            float vYaw = AOVector.getLookAtYaw(reqDir);
            float vPitch = AOVector.getLookAtPitch(reqDir);
            float vRoll = AOVector.getLookAtRoll(reqDir);
            if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets 1 vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll+" casterQuaternion="+casterQuaternion+" Y="+casterQuaternion.getYAxis()+" X="+casterQuaternion.getXAxis()+" Z="+casterQuaternion.getZAxis());
            casterQuaternion.setEulerAngles(vPitch,vYaw,0);
        }

		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			if (targetInfo != null) {
				if (caster.getAttackableTargets().containsKey(targetInfo.getOid())) {
					targetsToCheck.add(targetInfo.getOid());
				}
			}
			Log.debug("AgisAbility.getAoETargets 1");
			Set<OID> attackableTargets = new HashSet<OID>(caster.getAttackableTargets().keySet());
            if(targetSubType == TargetSubType.FRIEND_OR_ENEMY){
                attackableTargets.addAll(new HashSet<OID>(caster.getFriendlyTargets().keySet()));
            }
            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets attackableTargets" + attackableTargets);
			if (Log.loggingDebug)
				Log.debug("AOE: checking oids for aoe with possible target count: " + attackableTargets.size() + " minRange=" + minRange + " maxRange=" + maxRange);
			for (OID target : attackableTargets) {
				targetsToCheck.add(target);
			}
		//	if (state.SubState().get(state.NextSubState()).toRemove.size() > 0)
		//		targetsToCheck.removeAll(state.SubState().get(state.NextSubState()).toRemove);

            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets targets to check size="+targetsToCheck.size()+" prediction="+prediction+" aoeType="+aoeType+" sub state="+state.SubState().get(state.NextSubState()));
			long start1 = System.nanoTime();
			if (targetsToCheck.size() > 0) {
				if (prediction == 0) {
					if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange,caster.getOwnerOid());
					}else if (aoeType == AoeType.TARGET_RADIUS) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange,targetInfo.getOwnerOid());
					}else if (aoeType == AoeType.LOCATION_RADIUS ) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange);
					}
                    if(targetsInArea != null) {
                        if(getLineOfSight()) {
                            Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                            for (Map.Entry<OID, TargetsInAreaEntity> entry : targetsInArea.entrySet()) {
                                listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                            }
                            Point dLoc = loc;
                            if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                               dLoc = casterNode.getLoc();
                            }else if (aoeType == AoeType.TARGET_RADIUS) {
                                BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                dLoc = targetNode.getLoc();
                            }
                            ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), dLoc, listToCheck);
                            if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                            targetsInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                        }

                        targetsToCheck = new ArrayList<OID>(targetsInArea.keySet());
                    }
				} else {
					if(state.SubState().get(state.NextSubState()).targets.size() == 0) {
						if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
							state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0,caster.getOwnerOid());
						}else if (aoeType == AoeType.TARGET_RADIUS) {
							state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0,targetInfo.getOwnerOid());
						}else if (aoeType == AoeType.LOCATION_RADIUS ) {
							state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0);
						}
                        if(state.SubState().get(state.NextSubState()).targets != null) {
                            if(getLineOfSight()) {
                                Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                                for (Map.Entry<OID, TargetsInAreaEntity> entry : state.SubState().get(state.NextSubState()).targets.entrySet()) {
                                    listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                                }
                                Point dLoc = loc;
                                if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                    dLoc = casterNode.getLoc();
                                }else if (aoeType == AoeType.TARGET_RADIUS) {
                                    BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                    dLoc = targetNode.getLoc();
                                }
                                ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), dLoc, listToCheck);
                                if(Log.loggingDebug)  Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                                state.SubState().get(state.NextSubState()).targets.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                            }
                        }
						//targetsWithDistance =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation(), 0);
						if(aoe_targets_count_type != 2)
						state.SubState().get(state.NextSubState()).targets = sortByValue(state.SubState().get(state.NextSubState()).targets);

					}
					targetsToCheck.clear();
					for (Map.Entry<OID, TargetsInAreaEntity> entry : state.SubState().get(state.NextSubState()).targets.entrySet() ) {
						if(minRange <= entry.getValue().getDistance() && entry.getValue().getDistance() < maxRange) {
							targetsToCheck.add(entry.getKey());
						}
					}
				}
				// Convert back the target
			}
			if(attackBuilding) {
				
				//VoxelClient.getChestStorageOid(chestID)
				
				
			}
			if(aoe_targets_count_type==2)
				Collections.shuffle(targetsToCheck);
            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets targets to check size="+targetsToCheck.size()+" sub state="+state.SubState().get(state.NextSubState()));

			//state.SubState().get(state.NextSubState()).toRemove.addAll(targetsToCheck);
			
			long end1 = System.nanoTime();
			long microseconds = (end1 - start1) / 1000;
			if (Log.loggingDebug)
				Log.debug("AOE: getAoETargets: microseconds: " + microseconds );
			long start2 = System.nanoTime();
			if (Log.loggingDebug)
				Log.debug("AOE: checking oids for aoe with count: " + targetsToCheck.size());
			for (OID target : targetsToCheck) {
    			//Log.debug("AOE: checking oid: " + target);
    			/*if (targetInfo != null && targetInfo.getOid().equals(target)) {
    				continue;
    			}*/
    			CombatInfo ci = CombatPlugin.getCombatInfo(target);
    			if (ci == null) {
    				Log.debug("getAoETargets for target="+target+" CombatInfo is null");
    				continue; 
    			}
                if(Log.loggingDebug)Log.debug("AOE: combat info is not null for: " + target);
    		
    	        	targetsObjectInArea.add(ci);
    			
    		}
    		long end2 = System.nanoTime();
        	long microseconds2 = (end2 - start2) / 1000;
        	if(Log.loggingDebug)	Log.debug("AOE: getAoETargets: microseconds: "+microseconds2+ " for targetsToCheck:"+targetsToCheck);
        	
        /*	for (OID target : targetsToCheck) {
				targetsInArea.add(CombatPlugin.getCombatInfo(target));
			}*/
    		
    		
		} else if (targetType == TargetType.AREA && targetSubType == TargetSubType.FRIEND) {
			if (Log.loggingDebug)
				Log.debug("AOE: Friendly minRange=" + minRange + " maxRange=" + maxRange);

			if (targetInfo != null) {
				if (caster.getFriendlyTargets().containsKey(targetInfo.getOid())) {
                    targetsObjectInArea.add(targetInfo);
				}
			}
			// Check if caster is in range as well
			targetsToCheck.add(caster.getOwnerOid());
			Set<OID> friendlyTargets = new HashSet<OID>(caster.getFriendlyTargets().keySet());
			if (friendlyTargets.size() > 0) {

				for (OID target : friendlyTargets) {
					if (Log.loggingDebug)
						Log.debug("AOE: checking friendly oid: " + target);
				/*	if (targetInfo != null && targetInfo.getOid().equals(target)) {
						continue;
					}*/
                    if (CheckTargetState(CombatPlugin.getCombatInfo(target)) != AbilityResult.SUCCESS) {
					//if (CombatPlugin.getCombatInfo(target) == null || (CombatPlugin.getCombatInfo(target).dead() && targetDeath == 1)) {
						continue;
					}
					targetsToCheck.add(target);
				}
			}
			if (prediction == 0) {
				if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange,caster.getOwnerOid());
				}else if (aoeType == AoeType.TARGET_RADIUS) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange,targetInfo.getOwnerOid());
				}else if (aoeType == AoeType.LOCATION_RADIUS ) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange);
				}
                if(targetsInArea != null) {
                    if(getLineOfSight()) {
                        Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                        for (Map.Entry<OID, TargetsInAreaEntity> entry : targetsInArea.entrySet()) {
                            listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                        }
                        Point dLoc = loc;
                        if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                            dLoc = casterNode.getLoc();
                        }else if (aoeType == AoeType.TARGET_RADIUS) {
                            BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                            dLoc = targetNode.getLoc();
                        }
                        ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), dLoc, listToCheck);
                        if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                        targetsInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                    }

                    targetsToCheck = new ArrayList<OID>(targetsInArea.keySet());
                }
			} else {
				if(state.SubState().get(state.NextSubState()).targets.size() == 0) {
					if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
						state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0,caster.getOwnerOid());
					}else if (aoeType == AoeType.TARGET_RADIUS) {
						state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0,targetInfo.getOwnerOid());
					}else if (aoeType == AoeType.LOCATION_RADIUS ) {
						state.SubState().get(state.NextSubState()).targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0);
					}
                    if(state.SubState().get(state.NextSubState()).targets != null) {
                        if(getLineOfSight()) {
                            Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                            for (Map.Entry<OID, TargetsInAreaEntity> entry : state.SubState().get(state.NextSubState()).targets.entrySet()) {
                                listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                            }
                            Point dLoc = loc;
                            if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                dLoc = casterNode.getLoc();
                            }else if (aoeType == AoeType.TARGET_RADIUS) {
                                BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                dLoc = targetNode.getLoc();
                            }
                            ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), dLoc, listToCheck);
                            if(Log.loggingDebug)  Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                            state.SubState().get(state.NextSubState()).targets.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                        }
                    }
					//targetsWithDistance =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation(), 0);
					if(aoe_targets_count_type != 2)
					state.SubState().get(state.NextSubState()).targets = sortByValue(state.SubState().get(state.NextSubState()).targets);
				}
				targetsToCheck.clear();
				for (Map.Entry<OID, TargetsInAreaEntity> entry : state.SubState().get(state.NextSubState()).targets.entrySet() ) {
					if(minRange <= entry.getValue().getDistance() && entry.getValue().getDistance() < maxRange) {
						targetsToCheck.add(entry.getKey());
					}
				}
			}
			if(aoe_targets_count_type==2)
				Collections.shuffle(targetsToCheck);
			// Convert back the target
			for (OID target : targetsToCheck) {
				CombatInfo ci = CombatPlugin.getCombatInfo(target);
				if (ci == null)
					continue;
                targetsObjectInArea.add(ci);
			}
		}
		if (Log.loggingDebug)
			Log.debug("AOE: oids for aoe with count: " + targetsObjectInArea.size() + " math radius:" + areaOfEffectRadius + " and Angle:" + areaOfEffectAngle);
		long end = System.nanoTime();
		long microseconds = (end - start) / 1000;
		if (Log.loggingDebug)
			Log.debug("AOE: getAoETargets: END microseconds: " + microseconds + " targetsObjectInArea:" + targetsObjectInArea.size()+" minRange="+minRange+" maxRange="+maxRange);

		return targetsObjectInArea;
	}

    public void DebugAbility( CombatInfo caster, AgisAbilityPulsState state){
        if(CombatPlugin.ABILITY_DEBUG_PLAYER!=null) {
            Quaternion casterQuaternion = new Quaternion(state.casterQuaternion);
            Quaternion casterQuaternion1 = new Quaternion().setEulerAngles(state.casterQuaternion.getPitch(), state.casterQuaternion.getYaw() + areaOfEffectAngle / 2, state.casterQuaternion.getRoll());
            Quaternion casterQuaternion2 = new Quaternion().setEulerAngles(state.casterQuaternion.getPitch(), state.casterQuaternion.getYaw() - areaOfEffectAngle / 2, state.casterQuaternion.getRoll());

//            AOVector testLoc1 = new AOVector(state.casterLoc);
//            testLoc1.add(casterQuaternion.getXAxis().multiply(areaOfEffectRadius));
            AOVector testLoc2 = new AOVector(state.casterLoc);
            testLoc2.add(casterQuaternion.getZAxis().multiply(areaOfEffectRadius));

            AOVector testLoc1max = new AOVector(state.casterLoc);
            testLoc1max.add(casterQuaternion1.getZAxis().multiply(areaOfEffectRadius));
            AOVector testLoc1min = new AOVector(state.casterLoc);
            testLoc1min.add(casterQuaternion2.getZAxis().multiply(areaOfEffectRadius));

            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "ability_targets_aoe");
            props.put("targetType", targetType.toString());
            props.put("targetSubType", targetSubType.toString());
            props.put("minRange", minRange);
            props.put("maxRange", maxRange);
            props.put("areaOfEffectAngle", areaOfEffectAngle);
            props.put("areaOfEffectRadius", areaOfEffectRadius);
            props.put("orientationx", casterQuaternion.getX());
            props.put("orientationy", casterQuaternion.getY());
            props.put("orientationz", casterQuaternion.getZ());
            props.put("orientationw", casterQuaternion.getW());
            props.put("locX", state.casterLoc.getX());
            props.put("locY", state.casterLoc.getY());
            props.put("locZ", state.casterLoc.getZ());

            props.put("locX1", testLoc2.getX());
            props.put("locY1", testLoc2.getY());
            props.put("locZ1", testLoc2.getZ());

            props.put("locX2", testLoc1min.getX());
            props.put("locY2", testLoc1min.getY());
            props.put("locZ2", testLoc1min.getZ());

            props.put("locX3", testLoc1max.getX());
            props.put("locY3", testLoc1max.getY());
            props.put("locZ3", testLoc1max.getZ());

            TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, CombatPlugin.ABILITY_DEBUG_PLAYER, CombatPlugin.ABILITY_DEBUG_PLAYER, false, props);
            Engine.getAgent().sendBroadcast(extmsg);
            if(Log.loggingDebug)
                Log.debug("AgisAbility.getAoETargets " + caster.getOid() + " Loc=" + state.casterLoc + " casterQuaternion=" + casterQuaternion + " Y=" + casterQuaternion.getYAxis() + " X=" + casterQuaternion.getXAxis() + " Z=" + casterQuaternion.getZAxis() +
//                        " testLoc1=" + testLoc1 +
                        " testLoc2=" + testLoc2 + " testLoc1min=" + testLoc1min + " testLoc1max=" + testLoc1max);
        }
    }
    
    /**
     * Should this be merged up into the function above?
     * @param caster combat info of the caster
     * @param targetInfo combat info of the target
     * @param loc point from start serarch targets 
     * @return List Combat Info of the targets
     */
    public ArrayList<CombatInfo> getAoETargets(CombatInfo caster, CombatInfo targetInfo, Point loc , float minRange, float maxRange, AgisAbilityPulsState state ) {
    	long start = System.nanoTime();
        if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets Start | targetType="+targetType+" loc="+loc+" minRange=" + minRange + " maxRange=" + maxRange);
    	ArrayList<CombatInfo> targetsObjectsInArea = new ArrayList<CombatInfo>();
        ArrayList<OID> targetsToCheck = new ArrayList<OID>();
        Map<OID,TargetsInAreaEntity> targetsInArea = new HashMap<OID,TargetsInAreaEntity>();

if(state.casterLoc==null){
    BasicWorldNode casterNode = WorldManagerClient.getWorldNode(caster.getOwnerOid());
    if(Log.loggingDebug) Log.debug("Ability.getAoETargets 2 id " + getID() + " caster" + caster.getOid() + " CasterYaw=" + casterNode.getOrientation().getYaw());
    state.casterLoc = casterNode.getLoc();
    state.casterQuaternion = casterNode.getOrientation();
    state.casterInstance = casterNode.getInstanceOid();
}


    //	Log.debug("AgisAbility.getAoETargets casterNode="+casterNode); 
        if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets Enemy="+caster.getAttackableTargets()+" Friendly="+caster.getFriendlyTargets());
		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			if (targetInfo != null) {
				if (caster.getAttackableTargets().containsKey(targetInfo.getOid())) {
					targetsToCheck.add(targetInfo.getOid());
				}
			}
            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets 1");
			Set<OID> attackableTargets = new HashSet<OID>(caster.getAttackableTargets().keySet());
            if(targetSubType == TargetSubType.FRIEND_OR_ENEMY){
                attackableTargets.addAll(new HashSet<OID>(caster.getFriendlyTargets().keySet()));
            }
            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets attackableTargets" + attackableTargets);
			if (Log.loggingDebug)
				Log.debug("AOE: checking oids for aoe with possible target count: " + attackableTargets.size() + " minRange=" + minRange + " maxRange=" + maxRange);
			for (OID target : attackableTargets) {
				targetsToCheck.add(target);
			}


            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets to check size="+targetsToCheck.size()+" prediction="+prediction+" aoeType="+aoeType+" sub state="+state);
			long start1 = System.nanoTime();
			if (targetsToCheck.size() > 0) {
				if (prediction == 0) {//Real time

                        DebugAbility( caster, state);

                    if(state.state.getDestLocation() != null) {
                        Point dLoc = loc;
                        if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                            dLoc = state.casterLoc;
                        }else if (aoeType == AoeType.TARGET_RADIUS) {
                            BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                            dLoc = targetNode.getLoc();
                        }

                          Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(state.casterInstance, dLoc, state.state.getDestLocation());
                           if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation target visibilityResponse="+visibilityResponse);
                        // Point cLoc = casterNode.getLoc();
                        Point _loc = new Point(0f,0.8f,0f);
                        _loc.add(state.casterLoc);
                        AOVector reqDir = AOVector.sub(state.state.getDestLocation(), _loc);
                        float vYaw = AOVector.getLookAtYaw(reqDir);
                        float vPitch = AOVector.getLookAtPitch(reqDir);
                        float vRoll = AOVector.getLookAtRoll(reqDir);
                        if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation 2 vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll);
                        state.casterQuaternion.setEulerAngles(vPitch,vYaw,0);

                    }
					if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, state.state.getDestLocation()!=null?state.casterQuaternion:null, minRange,caster.getOwnerOid());
					}else if (aoeType == AoeType.TARGET_RADIUS) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null, minRange,targetInfo.getOwnerOid());
					}else if (aoeType == AoeType.LOCATION_RADIUS ) {
                        targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null, minRange);
					}
                    if(targetsInArea != null) {
                        if(getLineOfSight()) {
                            Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                            for (Map.Entry<OID, TargetsInAreaEntity> entry : targetsInArea.entrySet()) {
                                listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                            }
                            Point dLoc = loc;
                            if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                dLoc = state.casterLoc;
                            }else if (aoeType == AoeType.TARGET_RADIUS) {
                                BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                dLoc = targetNode.getLoc();
                            }
                            ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(state.casterInstance, dLoc, listToCheck);
                            if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                            targetsInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                        }

                        targetsToCheck = new ArrayList<OID>(targetsInArea.keySet());
                    }
				} else {//Predicted
					if(state.targets.size() == 0) {
                        DebugAbility( caster,state);

                        if(state.state.getDestLocation() != null) {
                            Point dLoc = loc;
                            if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                dLoc = state.casterLoc;
                            }else if (aoeType == AoeType.TARGET_RADIUS) {
                                BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                dLoc = targetNode.getLoc();
                            }

                              Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(state.casterInstance, dLoc, state.state.getDestLocation());
                               if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation target visibilityResponse="+visibilityResponse);
                            // Point cLoc = casterNode.getLoc();
                            Point _loc = new Point(0f,0.8f,0f);
                            _loc.add(state.casterLoc);
                            AOVector reqDir = AOVector.sub(state.state.getDestLocation(), _loc);
                            float vYaw = AOVector.getLookAtYaw(reqDir);
                            float vPitch = AOVector.getLookAtPitch(reqDir);
                            float vRoll = AOVector.getLookAtRoll(reqDir);
                            if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation 2 vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll);
                            state.casterQuaternion.setEulerAngles(vPitch,vYaw,0);

                        }
						if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
							state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  state.casterQuaternion, 0, caster.getOwnerOid());
						}else if (aoeType == AoeType.TARGET_RADIUS) {
							state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null, 0, targetInfo.getOwnerOid());
						}else if (aoeType == AoeType.LOCATION_RADIUS ) {
							state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null, 0);
						}
						//targetsWithDistance =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation(), 0);
                        if(state.targets != null) {
                            if(getLineOfSight()) {
                                Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                                for (Map.Entry<OID, TargetsInAreaEntity> entry : state.targets.entrySet()) {
                                    listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                                }
                                Point dLoc = loc;
                                if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                    dLoc = state.casterLoc;
                                }else if (aoeType == AoeType.TARGET_RADIUS) {
                                    BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                    dLoc = targetNode.getLoc();
                                }
                                ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(state.casterInstance, dLoc, listToCheck);
                                if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                                state.targets.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                            }
                        }
					}
					targetsToCheck.clear();
                    if(state.targets != null) {
                        if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets targets size=" + state.targets.size() + " sub state=" + state);
                        for (Map.Entry<OID, TargetsInAreaEntity> entry : state.targets.entrySet()) {
                            if (minRange <= entry.getValue().getDistance() && entry.getValue().getDistance() < maxRange) {
                                targetsToCheck.add(entry.getKey());
                            }
                        }
                    }else{
                        Log.debug("AgisAbility.getAoETargets targets is null");
                    }
				}
				if(aoe_targets_count_type==2)
					Collections.shuffle(targetsToCheck);
				// Convert back the target
			}
            if(Log.loggingDebug)Log.debug("AgisAbility.getAoETargets targets to check size="+targetsToCheck.size()+" sub state="+state);

		
			long end1 = System.nanoTime();
			long microseconds = (end1 - start1) / 1000;
			if (Log.loggingDebug)
				Log.debug("AOE: getAoETargets: microseconds: " + microseconds );
			long start2 = System.nanoTime();
			if (Log.loggingDebug)
				Log.debug("AOE: checking oids for aoe with count: " + targetsToCheck.size());
			for (OID target : targetsToCheck) {
    			//Log.debug("AOE: checking oid: " + target);
                if(aoe_targets_count_type > 0 && targetsObjectsInArea.size() >= aoe_targets_count - state.numAttackedTargets )
                    continue;
    			CombatInfo ci = CombatPlugin.getCombatInfo(target);
    			if (ci == null) {
    				Log.debug("getAoETargets for target="+target+" CombatInfo is null");
    				continue; 
    			}
                if(Log.loggingDebug)Log.debug("AOE: combat info is not null for: " + target);

                targetsObjectsInArea.add(ci);
    		
    		}
    		long end2 = System.nanoTime();
        	long microseconds2 = (end2 - start2) / 1000;
        	if(Log.loggingDebug)	Log.debug("AOE: getAoETargets: microseconds: "+microseconds2+ " for targetsToCheck:"+targetsToCheck);
        	
          		
    		
		} else if (targetType == TargetType.AREA && targetSubType == TargetSubType.FRIEND) {
			if (Log.loggingDebug)
				Log.debug("AOE: Friendly minRange=" + minRange + " maxRange=" + maxRange);

			/*if (targetInfo != null) {
				if (caster.getFriendlyTargets().containsKey(targetInfo.getOid())) {
					targetsObjectsInArea.add(targetInfo);
				}
			}*/
			// Check if caster is in range as well
			targetsToCheck.add(caster.getOwnerOid());
			Set<OID> friendlyTargets = new HashSet<OID>(caster.getFriendlyTargets().keySet());
			if (friendlyTargets.size() > 0) {

				for (OID target : friendlyTargets) {
					if (Log.loggingDebug)
						Log.debug("AOE: checking friendly oid: " + target);
					/*if (targetInfo != null && targetInfo.getOid().equals(target)) {
						continue;
					}*/
                    if (CheckTargetState(CombatPlugin.getCombatInfo(target)) != AbilityResult.SUCCESS) {
					//if (CombatPlugin.getCombatInfo(target) == null || (CombatPlugin.getCombatInfo(target).dead() && targetDeath == 1)) {
						continue;
					}
					targetsToCheck.add(target);
				}
			}
			
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getAoETargets AREA_FRIENDLY targets to check size="+targetsToCheck.size()+" targetsToCheck="+targetsToCheck+" prediction="+prediction+" aoeType="+aoeType+" sub state="+state);
			
			
			if (prediction == 0) {
//                BasicWorldNode casterNode = WorldManagerClient.getWorldNodeNoCache(caster.getOwnerOid());
//                Log.error("Ability.getAoETargets 2 id "+getID()+" caster"+caster.getOid()+" CasterYaw="+casterNode.getOrientation().getYaw());
//                Quaternion casterQuaternion = new Quaternion(casterNode.getOrientation());
                DebugAbility(caster,state);

                if(state.state.getDestLocation() != null) {
                    Point dLoc = loc;
                    if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                        dLoc = state.casterLoc;
                    }else if (aoeType == AoeType.TARGET_RADIUS) {
                        BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                        dLoc = targetNode.getLoc();
                    }

                      Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(state.casterInstance, dLoc, state.state.getDestLocation());
                       if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation target visibilityResponse="+visibilityResponse);
                    // Point cLoc = casterNode.getLoc();
                    Point _loc = new Point(0f,0.8f,0f);
                    _loc.add(state.casterLoc);
                    AOVector reqDir = AOVector.sub(state.state.getDestLocation(), _loc);
                    float vYaw = AOVector.getLookAtYaw(reqDir);
                    float vPitch = AOVector.getLookAtPitch(reqDir);
                    float vRoll = AOVector.getLookAtRoll(reqDir);
                    if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation 2 vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll);
                    state.casterQuaternion.setEulerAngles(vPitch,vYaw,0);

                }
				if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, state.casterQuaternion, minRange,caster.getOwnerOid());
				}else if (aoeType == AoeType.TARGET_RADIUS) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange,targetInfo.getOwnerOid());
				}else if (aoeType == AoeType.LOCATION_RADIUS ) {
                    targetsInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, maxRange, areaOfEffectAngle, null/*casterNode.getOrientation()*/, minRange);
				}
                if(targetsInArea != null) {
                    if(getLineOfSight()) {
                        Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                        for (Map.Entry<OID, TargetsInAreaEntity> entry : targetsInArea.entrySet()) {
                            listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                        }
                        Point dLoc = loc;
                        if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                            dLoc = state.casterLoc;
                        }else if (aoeType == AoeType.TARGET_RADIUS) {
                            BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                            dLoc = targetNode.getLoc();
                        }
                        ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(state.casterInstance, dLoc, listToCheck);
                        if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                        targetsInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                    }

                    targetsToCheck = new ArrayList<OID>(targetsInArea.keySet());
                }
			} else {
				if(state.targets.size() == 0) {
//                    BasicWorldNode casterNode = WorldManagerClient.getWorldNodeNoCache(caster.getOwnerOid());
//                    Log.error("Ability.getAoETargets 2 id "+getID()+" caster"+caster.getOid()+" CasterYaw="+casterNode.getOrientation().getYaw());
//                    Quaternion casterQuaternion = new Quaternion(casterNode.getOrientation());
                    DebugAbility(caster, state);
                    if(state.state.getDestLocation() != null) {
                        Point dLoc = loc;
                        if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                            dLoc = state.casterLoc;
                        }else if (aoeType == AoeType.TARGET_RADIUS) {
                            BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                            dLoc = targetNode.getLoc();
                        }

                          Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(state.casterInstance, dLoc, state.state.getDestLocation());
                           if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation target visibilityResponse="+visibilityResponse);
                        // Point cLoc = casterNode.getLoc();
                        Point _loc = new Point(0f,0.8f,0f);
                        _loc.add(state.casterLoc);
                        AOVector reqDir = AOVector.sub(state.state.getDestLocation(), _loc);
                        float vYaw = AOVector.getLookAtYaw(reqDir);
                        float vPitch = AOVector.getLookAtPitch(reqDir);
                        float vRoll = AOVector.getLookAtRoll(reqDir);
                        if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets DestLocation 2 vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll);
                        state.casterQuaternion.setEulerAngles(vPitch,vYaw,0);

                    }
					if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
						state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  state.casterQuaternion, 0,caster.getOwnerOid());
					}else if (aoeType == AoeType.TARGET_RADIUS) {
						state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0,targetInfo.getOwnerOid());
					}else if (aoeType == AoeType.LOCATION_RADIUS ) {
						state.targets =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(caster.getOid(),targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle,  null/*casterNode.getOrientation()*/, 0);
					}
					//targetsWithDistance =  AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(targetsToCheck, loc, areaOfEffectRadius, areaOfEffectAngle, casterNode.getOrientation(), 0);
					//state.SubState().get(state.NextSubState()).targets = sortByValue(state.SubState().get(state.NextSubState()).targets);
                    if(state.targets != null) {
                        if(getLineOfSight()) {
                            Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                            for (Map.Entry<OID, TargetsInAreaEntity> entry : state.targets.entrySet()) {
                                listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                            }
                            Point dLoc = loc;
                            if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
                                dLoc = state.casterLoc;
                            }else if (aoeType == AoeType.TARGET_RADIUS) {
                                BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetInfo.getOwnerOid());
                                dLoc = targetNode.getLoc();
                            }
                            ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(state.casterInstance, dLoc, listToCheck);
                            if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                            state.targets.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                        }
                    }
				}
				targetsToCheck.clear();
				for (Map.Entry<OID, TargetsInAreaEntity> entry : state.targets.entrySet() ) {
					if(minRange <= entry.getValue().getDistance() && entry.getValue().getDistance() < maxRange) {
						targetsToCheck.add(entry.getKey());
					}
				}
			}
			if (Log.loggingDebug)Log.debug("AgisAbility.getAoETargets AREA_FRIENDLY targets to check size="+targetsToCheck.size()+" targetsToCheck="+targetsToCheck+" prediction="+prediction+" aoeType="+aoeType+" sub state="+state);
			
			if(aoe_targets_count_type == 2)
				Collections.shuffle(targetsToCheck);
			// Convert back the target

			for (OID target : targetsToCheck) {
                if(aoe_targets_count_type > 0 && targetsObjectsInArea.size() >= aoe_targets_count - state.numAttackedTargets )
                    continue;
				CombatInfo ci = CombatPlugin.getCombatInfo(target);
				if (ci == null)
					continue;
                targetsObjectsInArea.add(ci);

			}
		}
		if (Log.loggingDebug)
			Log.debug("AOE: oids for aoe with count: " + targetsObjectsInArea.size() + " math radius:" + areaOfEffectRadius + " and Angle:" + areaOfEffectAngle);
		long end = System.nanoTime();
		long microseconds = (end - start) / 1000;
		if (Log.loggingDebug)
			Log.debug("AOE: getAoETargets: END microseconds: " + microseconds + " targetsObjectsInArea:" + targetsObjectsInArea.size()+" minRange="+minRange+" maxRange="+maxRange);

		return targetsObjectsInArea;
	}
    
    
    // begin activating the ability
	public void beginActivation(AgisAbilityState state) {
		if(Log.loggingDebug)Log.debug("AgisAbility.beginActivation: consumeOnActivation="+consumeOnActivation);
		// Activate cooldowns.
		//if (!skipChecks) {		
		CombatInfo combatInfo = state.getSource();
		if (useEnterCombatState)
			combatInfo.setCombatState(true);
	
			if (startCooldownsOnActivation) {
				Collection<Cooldown> cooldowns = state.getAbility().getCooldownMap().values();
				double cooldownMod = 100;
				if(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: CooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: CooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated: " + calculated);	
					cooldownMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated="+calculated+" mod="+cooldownMod);
					
					
				}
				double cooldownGlobalMod = 100;
				if(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: GlobalCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: GlobalCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: GlobalCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated: " + calculated);	
					cooldownGlobalMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated="+calculated+" mod="+cooldownGlobalMod);
					
					
				}
				double cooldownWeaponMod = 100;
				if(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: WeaponCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: WeaponCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: WeaponCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated: " + calculated);	
					cooldownWeaponMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated="+calculated+" mod="+cooldownWeaponMod);
					
					
				}
			
				Cooldown.activateCooldowns(cooldowns, combatInfo, cooldownGlobalMod, cooldownWeaponMod, cooldownMod, combatInfo.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT),true);
				if (Log.loggingDebug)
					Log.debug("AgisAbility.beginActivation: finished");
			}
	//	}
		if (consumeOnActivation) {
			
			if (!consumeReagentList.isEmpty() ) {
				
				if(Log.loggingDebug)	Log.debug("AgisAbility.beginActivation removeGenericItems reagents");
				AgisInventoryClient.removeGenericItems(combatInfo.getOid(), consumeReagentList, false);
			}

			
			if (costProp != null) {
				double percentage = 0;
				AgisStatDef statDef = CombatPlugin.lookupStatDef(costProp);
				if (statDef instanceof VitalityStatDef) {
					VitalityStatDef def = (VitalityStatDef) statDef;
					String maxStat = def.getMaxStat();
					double maxValue = combatInfo.statGetCurrentValueWithPrecision(maxStat);
					percentage = Math.round(maxValue * getActivationCostPercentage() / 100f);
				}
				double costMultiply = 1f;
				if (CombatPlugin.ABILITY_COST_MOD_STAT != null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COST_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COST_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COST_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COST_MOD_STAT);
						double pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CostMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: CostMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: CostMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						
						//  if (pointsCalculated < statValue) { 
						//		calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size())); 
						//}
						 
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod calculated: " + calculated);
					costMultiply = (calculated / 100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod calculated=" + calculated + " mod=" + costMultiply);

				}

				combatInfo.statModifyBaseValue(costProp, (int) -Math.round((activationCost + percentage) * costMultiply));
				combatInfo.sendStatusUpdate();
			}
		}
		
		
		
		
	}
	  public void completeActivation(AgisAbilityState state) {
		  
	  }
	
	  
	public void getTargets(AgisAbilityState state) {
		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: consumeOnActivation=" + consumeOnActivation);
		float minRange = 0;
		float maxRange = areaOfEffectRadius;
		ArrayList<OID> toRemove = new ArrayList<OID>();
		
		try {
			minRange = areaOfEffectRadius / state.MaxChank() * state.SubState().get(state.NextSubState()).lastChank;
			maxRange = areaOfEffectRadius / state.MaxChank() * (state.SubState().get(state.NextSubState()).lastChank + 1);
			
		} catch (Exception e) {
			Log.debug("getTargets"+ e+" "+e.getMessage()+" "+e.getLocalizedMessage());
		//	e.printStackTrace();
		}

        BasicWorldNode casterNode1 = WorldManagerClient.getWorldNode(state.getSourceOid());
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ability_targets");
        props.put("targetType", targetType.toString());
        props.put("targetSubType", targetSubType.toString());
        props.put("minRange", minRange);
        props.put("maxRange", maxRange);
        props.put("areaOfEffectAngle", areaOfEffectAngle);
        props.put("orientationx", casterNode1.getOrientation().getX());
        props.put("orientationy", casterNode1.getOrientation().getY());
        props.put("orientationz", casterNode1.getOrientation().getZ());
        props.put("orientationw", casterNode1.getOrientation().getW());

        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, state.getSourceOid(), state.getSourceOid(), false, props);
        Engine.getAgent().sendBroadcast(extmsg);


		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: targetType=" + targetType + " aoeType=" + aoeType);
		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
				Log.debug("AgisAbility.getTargets: AREA_ENEMY PLAYER_RADIUS");
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, state);
				if (attackBuilding) {
					BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSource().getOwnerOid());
					buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(),casterNode.getLoc(), casterNode.getOrientation(), minRange, maxRange, areaOfEffectAngle, -1, -1);
				}
			} else if (aoeType == AoeType.TARGET_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, state);
				if (attackBuilding) {
					if (state.getClaimID() > 0) {
						buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(),null, null, minRange, maxRange, areaOfEffectAngle, state.getClaimID(), state.getClaimObjID());
					} else {
						BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
						buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(),targetNode.getLoc(), targetNode.getOrientation(), minRange, maxRange, areaOfEffectAngle, -1, -1);
					}
				}
			} else if (aoeType == AoeType.LOCATION_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), state.getLocation(), minRange, maxRange, state);
				if (attackBuilding) {
					BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
					buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(),targetNode.getLoc(), null, minRange, maxRange, areaOfEffectAngle, -1, -1);
				}
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets+" and "+buildingTargets);
		} else if (targetType == TargetType.AREA && targetSubType == TargetSubType.FRIEND) {
			if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, state);
			} else if (aoeType == AoeType.TARGET_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, state);
			} else if (aoeType == AoeType.LOCATION_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), state.getLocation(), minRange, maxRange, state);
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets);
		} else if (targetType == TargetType.GROUP) {
			ArrayList<OID> groupMemners = new ArrayList<OID>(GroupClient.GetGroupMembers(state.getSourceOid(),false));
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			if(!groupMemners.contains(state.getSourceOid()))
				groupMemners.add(state.getSourceOid());
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: caster added groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			ArrayList<CombatInfo> targetsInArea = new ArrayList<CombatInfo>();
			if (Log.loggingDebug)
					Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " " + groupMemners + " minRange=" + minRange + " maxRange=" + maxRange);
			BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSourceOid());
			Map<OID, TargetsInAreaEntity> groupInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(state.getSourceOid(),groupMemners, casterNode.getLoc(), maxRange, areaOfEffectAngle, casterNode.getOrientation(),minRange);

            if(groupInArea != null) {
                if(getLineOfSight()) {
                    Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                    for (Map.Entry<OID, TargetsInAreaEntity> entry : groupInArea.entrySet()) {
                        listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                    }
                    ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), casterNode.getLoc(), listToCheck);
                    if(Log.loggingDebug)  Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                    groupInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                }
                groupMemners = new ArrayList<OID>(groupInArea.keySet());
            }

			if (Log.loggingDebug)
					Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			for (OID target : groupMemners) {
    			targetsInArea.add(CombatPlugin.getCombatInfo(target));
    		}
			targets = targetsInArea;
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets);
		}
		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: end");
	}
	
	
	public ArrayList<CombatInfo> getTargets(AgisAbilityState state, AgisAbilityPulsState pulsState) {
		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: consumeOnActivation=" + consumeOnActivation+" areaOfEffectRadius="+areaOfEffectRadius+" state.MaxChank="+state.MaxChank()+" pulsState.lastChank="+pulsState.lastChank);
		float minRange = 0;
		float maxRange = areaOfEffectRadius;
		ArrayList<OID> toRemove = new ArrayList<OID>();
		ArrayList<CombatInfo> targets = new ArrayList<CombatInfo>();
		try {
			if(state.MaxChank()>0) {
				if( pulsState.lastChank > 0)
					minRange = areaOfEffectRadius / state.MaxChank() * pulsState.lastChank;
				maxRange = areaOfEffectRadius / state.MaxChank() * (pulsState.lastChank + 1);
			}
			
		} catch (Exception e) {
			Log.exception("getTargets", e);
			e.printStackTrace();
		}

        BasicWorldNode casterNode1 = WorldManagerClient.getWorldNode(state.getSourceOid());
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ability_targets");
        props.put("targetType", targetType.toString());
        props.put("targetSubType", targetSubType.toString());
        props.put("minRange", minRange);
        props.put("maxRange", maxRange);
        props.put("areaOfEffectAngle", areaOfEffectAngle);
        props.put("orientationx", casterNode1.getOrientation().getX());
        props.put("orientationy", casterNode1.getOrientation().getY());
        props.put("orientationz", casterNode1.getOrientation().getZ());
        props.put("orientationw", casterNode1.getOrientation().getW());


        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, state.getSourceOid(), state.getSourceOid(), false, props);
        Engine.getAgent().sendBroadcast(extmsg);

		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: targetType=" + targetType);
		if (targetType == TargetType.AREA && (targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY)) {
			if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, pulsState);
				if (attackBuilding) {
					if (prediction == 0) {
						BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSource().getOwnerOid());
						buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), casterNode.getLoc(), casterNode.getOrientation(), minRange, maxRange, areaOfEffectAngle, -1, -1);
					} else {
						buildingTargets.clear();
						if (pulsState.buildingTargets.size() == 0) {
							BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSource().getOwnerOid());
							pulsState.buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), casterNode.getLoc(), casterNode.getOrientation(), 0, areaOfEffectRadius, areaOfEffectAngle,
									-1, -1);
						}
						for (CombatBuildingTarget entry : pulsState.buildingTargets) {
							if (minRange < entry.getDistance() && entry.getDistance() <= maxRange) {
								buildingTargets.add(entry);
							}
						}
					}
					if (aoe_targets_count_type == 2)
						Collections.shuffle(buildingTargets);
				}
			} else if (aoeType == AoeType.TARGET_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, pulsState);
				if (attackBuilding) {
					if (state.getClaimID() > 0) {
						if (prediction == 0) {
							buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), null, null, minRange, maxRange, areaOfEffectAngle, state.getClaimID(), state.getClaimObjID());
						} else {
							buildingTargets.clear();
							if (pulsState.buildingTargets.size() == 0) {
								pulsState.buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), null, null, 0, areaOfEffectRadius, areaOfEffectAngle, state.getClaimID(),
										state.getClaimObjID());
							}
                            if(pulsState!=null && pulsState.buildingTargets != null)
							for (CombatBuildingTarget entry : pulsState.buildingTargets) {
								if (minRange < entry.getDistance() && entry.getDistance() <= maxRange) {
									buildingTargets.add(entry);
								}
							}
						}
					} else {
						if (prediction == 0) {
							BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
							buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), targetNode.getLoc(), targetNode.getOrientation(), minRange, maxRange, areaOfEffectAngle, -1, -1);
						} else {
							buildingTargets.clear();
							if (pulsState.buildingTargets.size() == 0) {
								BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
								pulsState.buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), targetNode.getLoc(), targetNode.getOrientation(), 0, areaOfEffectRadius,
										areaOfEffectAngle, -1, -1);
							}
                            if(pulsState!=null && pulsState.buildingTargets != null)
							for (CombatBuildingTarget entry : pulsState.buildingTargets) {
								if (minRange < entry.getDistance() && entry.getDistance() <= maxRange) {
									buildingTargets.add(entry);
								}
							}
						}
					}
				}
			} else if (aoeType == AoeType.LOCATION_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), state.getLocation(), minRange, maxRange, pulsState);
				if (attackBuilding) {
					if (prediction == 0) {
						BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
						buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), targetNode.getLoc(), null, minRange, maxRange, areaOfEffectAngle, -1, -1);
					} else {
						buildingTargets.clear();
						if (pulsState.buildingTargets.size() == 0) {
							BasicWorldNode targetNode = WorldManagerClient.getWorldNode(state.getTarget().getOwnerOid());
							pulsState.buildingTargets = VoxelClient.getAoEBuildingTargetsInArea(state.getSource().getOwnerOid(), targetNode.getLoc(), null, 0, areaOfEffectRadius, areaOfEffectAngle, -1, -1);
						}
						for (CombatBuildingTarget entry : pulsState.buildingTargets) {
							if (minRange < entry.getDistance() && entry.getDistance() <= maxRange) {
								buildingTargets.add(entry);
							}
						}
					}
				}
			}
			
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets);
		} else if (targetType == TargetType.AREA && targetSubType == TargetSubType.FRIEND) {
			if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, pulsState);
			} else if (aoeType == AoeType.TARGET_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), null, minRange, maxRange, pulsState);
			} else if (aoeType == AoeType.LOCATION_RADIUS) {
				targets = getAoETargets(state.getSource(), state.getTarget(), state.getLocation(), minRange, maxRange, pulsState);
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets);
		} else if (targetType == TargetType.GROUP) {
			ArrayList<OID> groupMemners = new ArrayList<OID>(GroupClient.GetGroupMembers(state.getSourceOid(),false));
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			if(!groupMemners.contains(state.getSourceOid()))
				groupMemners.add(state.getSourceOid());
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: caster added groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			ArrayList<CombatInfo> targetsInArea = new ArrayList<CombatInfo>();
			if (Log.loggingDebug)
					Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " " + groupMemners + " minRange=" + minRange + " maxRange=" + maxRange);
			BasicWorldNode casterNode = WorldManagerClient.getWorldNode(state.getSourceOid());
			
            Map<OID, TargetsInAreaEntity> groupInArea = AgisWorldManagerClient.checkIfTargetsInAreaGetWithDistance(state.getSourceOid(),groupMemners, casterNode.getLoc(), maxRange, areaOfEffectAngle, casterNode.getOrientation(),minRange);

            if(groupInArea != null) {
                if(getLineOfSight()) {
                    Map<OID, Point> listToCheck = new HashMap<OID, Point>();
                    for (Map.Entry<OID, TargetsInAreaEntity> entry : groupInArea.entrySet()) {
                        listToCheck.put(entry.getKey(), entry.getValue().getLoc());
                    }
                    ArrayList<OID> visibilityResponse = WorldManagerClient.CheckTargetsVisibility(casterNode.getInstanceOid(), casterNode.getLoc(), listToCheck);
                    if(Log.loggingDebug) Log.debug("AgisAbility.getAoETargets AREA_ENEMY targets visibilityResponse="+visibilityResponse);
                    groupInArea.entrySet().removeIf(entry -> !visibilityResponse.contains(entry.getKey()));
                }
                groupMemners = new ArrayList<OID>(groupInArea.keySet());
            }


			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: groupMemners count=" + groupMemners.size() + " after range calculation  " + groupMemners);
			
			for (OID target : groupMemners) {
    			targetsInArea.add(CombatPlugin.getCombatInfo(target));
    		}
			targets = targetsInArea;
			if (Log.loggingDebug)
				Log.debug("AgisAbility.getTargets: got aoe targets: " + targets);
		}

		
		if (Log.loggingDebug)
			Log.debug("AgisAbility.getTargets: end");
		return targets;
	}
	
	
	 public void completeActivationTargetConsume(AgisAbilityState state) {
		 getTargets(state);
		 if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
				for (CombatInfo ci : targets) {
					if (targetEffectConsumption.size() > 0) {
						for (int i = 0; i < targetEffectConsumption.size(); i++) {
							int effectConsumed = targetEffectConsumption.get(i);
							for (EffectState existingState : ci.getCurrentEffects()) {
								if (effectConsumed == existingState.getEffect().getID()) {
									if (Log.loggingDebug)
										Log.debug("ABILITY: target effect being consumed - effect num/pos: " + existingState.getEffect().getIcon());
									AgisEffect.removeEffect(existingState);
									break;
								}
							}
						}
						// targetEffectC
					}
				}
			}
	 }
			
	
    /**
     * Consume reagents, stat on Caster 
     * @param state of ability
     */
    public boolean completeActivationCasterConsume(AgisAbilityState state) {
		if (Log.loggingDebug)
			Log.debug("AgisAbility.completeActivation: consumeOnActivation=" + consumeOnActivation);
		CombatInfo combatInfo = state.getSource();
	
		if (!consumeOnActivation) {
			AbilityResult result = checkReagent(combatInfo, null, state.state);
			if (Log.loggingDebug)
				Log.debug("AgisAbility.completeActivation: checkReagent state=" + state + " result=" + result);
			result = checkCost(combatInfo, null, state.state);
			if (Log.loggingDebug)
				Log.debug("AgisAbility.completeActivation: checkCost state=" + state + " result=" + result);
			if (result == AbilityResult.SUCCESS) {
				if (!consumeReagentList.isEmpty()) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility.completeActivation removeGenericItems reagents");
					AgisInventoryClient.removeGenericItems(combatInfo.getOid(), consumeReagentList, false);
				}
			} else {
				AgisAbility.interruptAbility(state, result);
				combatInfo.abilityFailed(id);
				return false;
			}
		
			if (result == AbilityResult.SUCCESS) {
				if (costProp != null) {
					double percentage=0;
					AgisStatDef statDef = CombatPlugin.lookupStatDef(costProp);
					if(statDef instanceof VitalityStatDef) {
						VitalityStatDef def = (VitalityStatDef)statDef;
						String maxStat = def.getMaxStat();
						double maxValue  = combatInfo.statGetCurrentValueWithPrecision(maxStat);
						percentage = Math.round(maxValue * getActivationCostPercentage()/100f);
					}
					double costMultiply =1f;
					if(CombatPlugin.ABILITY_COST_MOD_STAT!=null) {
						double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COST_MOD_STAT);
						if (Log.loggingDebug)
							Log.debug("AgisAbility: CostMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COST_MOD_STAT);
						double calculated = 0;
						if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COST_MOD_STAT)) {
							StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COST_MOD_STAT);
							double pointsCalculated = 0;
							for (int i = 1; i <= def.getPoints().size(); i++) {
								if (Log.loggingDebug)
									Log.debug("AgisAbility: CostMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
								if (statValue <= def.getThresholds().get(i)) {
									Log.debug("AgisAbility: CostMod statValue < th");
									if (statValue - pointsCalculated < 0)
										break;
									calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
									pointsCalculated += statValue - pointsCalculated;
								} else {
									Log.debug("AgisAbility: CostMod statValue > th");
									calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
									pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
								}
							}
							//if (pointsCalculated < statValue) {
							//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
							//}
						} else {
							calculated = statValue;
						}
						if (Log.loggingDebug)
							Log.debug("AgisAbility: CostMod calculated: " + calculated);	
						costMultiply = (calculated/100f);
						if (Log.loggingDebug)
							Log.debug("AgisAbility: CostMod calculated="+calculated+" mod="+costMultiply);
					}
					combatInfo.statModifyBaseValue(costProp, (int) -Math.round((activationCost + percentage) * costMultiply));
					combatInfo.sendStatusUpdate();
				}
			} else {
				AgisAbility.interruptAbility(state, result);
				combatInfo.abilityFailed(id);
				return false;
			}
		}
		/*
		 * Log.debug("ABILITY: vigor value: " + vigor); 
		 * if (vigor != 0) { combatInfo.statModifyBaseValue("vigor", vigor); combatInfo.sendStatusUpdate(); }
		 */
        
        // Here we remove any effects that are eaten up by the ability
        if (attackerEffectConsumption.size() > 0) {
    	    for (int i = 0; i < attackerEffectConsumption.size(); i++) {
    	    	int effectConsumed = attackerEffectConsumption.get(i);
    		    for (EffectState existingState : state.getSource().getCurrentEffects()) {
    			    if (effectConsumed == existingState.getEffect().getID()) {
    			    	if(Log.loggingDebug)Log.debug("ABILITY: attacker effect being consumed - effect num/pos: " 
    			    			+ existingState.getEffect().getIcon());
    			    	AgisEffect.removeEffect(existingState);
        			    break;
    			    } 
    		    }
    	    }
    	    //attackerEffectConsumption.clear();
        }
        
        if (targetEffectConsumption.size() > 0) {
    	    for (int i = 0; i < targetEffectConsumption.size(); i++) {
    	    	int effectConsumed = targetEffectConsumption.get(i);
    	    	for (EffectState existingState : state.getTarget().getCurrentEffects()) {
    			    if (effectConsumed == existingState.getEffect().getID()) {
    			    	if(Log.loggingDebug)	Log.debug("ABILITY: target effect being consumed - effect num/pos: " 
    			    			+ existingState.getEffect().getIcon());
    			    	AgisEffect.removeEffect(existingState);
        			    break;
    			    } 
    		    }
    	    }
    	    //targetEffectConsumption.clear();
        }
	
        if (ammoReq > 0) {
        	// Remove an ammo item from the caster
        	Integer ammoLoaded = (Integer) combatInfo.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
        	if (ammoLoaded != null && ammoLoaded > 0) {
        		AgisInventoryClient.removeGenericItem(combatInfo.getOwnerOid(), ammoLoaded, false, ammoReq);
        		
        	}
        }
        
        /* AgisItem item = */ state.getItem();
        
        // Send a skill used message
    	if(Log.loggingDebug)  Log.debug("SKILL: checking send ability skill up for skill: " + skillType + " with sendSkill: " + sendSkillUpChance);
        if (combatInfo.isUser() && skillType != -1 && sendSkillUpChance) {
        //	Log.debug("SKILL: sending ability Used");
        //   CombatClient.abilityUsed(combatInfo.getOwnerOid(), skillType,skillExp,1);
        }
        sendSkillUpChance = true;
        
        // Activate cooldowns. 
		//if (!skipChecks) {
			if (!startCooldownsOnActivation) {
				Collection<Cooldown> cooldowns = state.getAbility().getCooldownMap().values();
				double cooldownMod = 100;
				if(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: CooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: CooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated: " + calculated);	
					cooldownMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated="+calculated+" mod="+cooldownMod);
					
					
				}
				double cooldownGlobalMod = 100;
				if(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: GlobalCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: GlobalCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: GlobalCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated: " + calculated);	
					cooldownGlobalMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated="+calculated+" mod="+cooldownGlobalMod);
					
					
				}
				double cooldownWeaponMod = 100;
				if(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: WeaponCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: WeaponCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: WeaponCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated: " + calculated);	
					cooldownWeaponMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated="+calculated+" mod="+cooldownWeaponMod);
					
					
				}
			Cooldown.activateCooldowns(cooldowns, combatInfo, cooldownGlobalMod, cooldownWeaponMod, cooldownMod, combatInfo.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT),true);

				Log.debug("AgisAbility.completeActivation: finished");
			}
			Log.debug("AgisAbility.completeActivation: finished");

			// Send weapon used?
			if (state.item == null) {
				// Item Used message is not sent if the source is hitting their dueling partner
				// Integer duelID =
				// (Integer)state.getSource().getProperty(CombatInfo.COMBAT_PROP_DUEL_ID);
				// if (duelID == null || duelID < 0 || !state.getTarget().isUser())
				AgisInventoryClient.equippedItemUsed(state.getSourceOid(), "Attack", skillType);
			}
		//}
      /*  if (combatInfo.isUser() )
        AgisInventoryClient.equippedItemUsed(state.getSourceOid(), "Attack",skillType);
        */
        
        if(state.getSource().isUser()) {
        	CombatPlugin.addListsRankingData(state.getSourceOid(), AchievementsClient.USE_ABILITY, getID());
        }
        return true;
        
    }

    public void beginChannelling(AgisAbilityState state) {
        Log.debug("AgisAbility.beginChannelling:");
    }

    public void pulseChannelling(AgisAbilityState state) {
    	
    }
    public void pulseCaster(AgisAbilityState state) {
    	
    }
    
 public void pulseTarget(AgisAbilityState state) {
    	
    }

	public void pulseTarget(AgisAbilityState state, ArrayList<CombatInfo>  targets) {
 	
 }
 
    public void pulseConsumeTarget(AgisAbilityState state) {
		if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
			for (CombatInfo ci : targets) {
				if (pulseTargetEffectConsumption.size() > 0) {
					for (int i = 0; i < pulseTargetEffectConsumption.size(); i++) {
						int effectConsumed = pulseTargetEffectConsumption.get(i);
						for (EffectState existingState : ci.getCurrentEffects()) {
							if (effectConsumed == existingState.getEffect().getID()) {
								if (Log.loggingDebug)
									Log.debug("ABILITY: target effect being consumed - effect num/pos: " + existingState.getEffect().getIcon());
								AgisEffect.removeEffect(existingState);
								break;
							}
						}
					}
					// targetEffectC
				}
			}
		}

    }
    
    public void pulseConsumeTarget(AgisAbilityState state, ArrayList<CombatInfo>  targets) {
		if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
			for (CombatInfo ci : targets) {
				if (pulseTargetEffectConsumption.size() > 0) {
					for (int i = 0; i < pulseTargetEffectConsumption.size(); i++) {
						int effectConsumed = pulseTargetEffectConsumption.get(i);
						for (EffectState existingState : ci.getCurrentEffects()) {
							if (effectConsumed == existingState.getEffect().getID()) {
								if (Log.loggingDebug)
									Log.debug("ABILITY: target effect being consumed - effect num/pos: " + existingState.getEffect().getIcon());
								AgisEffect.removeEffect(existingState);
								break;
							}
						}
					}
					// targetEffectC
				}
			}
		}

    }

    
    public boolean pulseChecking(AgisAbilityState state) {
	/*	CombatInfo source = state.getSource();
        if (Log.loggingDebug)
            Log.debug("AgisAbility.pulseChannelling: cost=" + channelCost);
		if (costProp != null) {
			source.statModifyBaseValue(costProp, -channelCost);
			source.sendStatusUpdate();
		}*/
    	
		if (Log.loggingDebug)
			Log.debug("AgisAbility.pulseChecking: start ");
		//getTargets(state);
		
		CombatInfo combatInfo = state.getSource();
	
	//if (!consumeOnActivation) {
			AbilityResult result = checkPulseReagent(combatInfo, null, state.state);
			if(Log.loggingDebug)	Log.debug("AgisAbility.pulseChannelling: checkPulseReagent state="+state+" result="+result);
			result = checkPulseCost(combatInfo, null, state.state);
			if(Log.loggingDebug)	Log.debug("AgisAbility.pulseChannelling: checkPulseCost state="+state+" result="+result);
					
		if (result == AbilityResult.SUCCESS) {
			if(Log.loggingDebug)	Log.debug("AgisAbility.pulseChannelling: pulsConsumeReagentList="+pulseConsumeReagentList);
			if (!pulseConsumeReagentList.isEmpty()) {
				if (Log.loggingDebug)
					Log.debug("AgisAbility.pulseChannelling removeGenericItems reagents");
				AgisInventoryClient.removeGenericItems(combatInfo.getOid(), pulseConsumeReagentList, false);
			}
			if(Log.loggingDebug)	Log.debug("AgisAbility.pulseChannelling: pulseCostProp="+pulseCostProp);
			if (pulseCostProp != null && !pulseCostProp.equals("")) {
				double costMultiply =1f;
				if(CombatPlugin.ABILITY_COST_MOD_STAT!=null) {
					double statValue = combatInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COST_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COST_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COST_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COST_MOD_STAT);
						double pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CostMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: CostMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: CostMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						//if (pointsCalculated < statValue) {
						//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						//}
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod calculated: " + calculated);	
					costMultiply = (calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod calculated="+calculated+" mod="+costMultiply);
					
					
				}
				double pulsCostPercentage=0;
				AgisStatDef pulsStatDef = CombatPlugin.lookupStatDef(pulseCostProp);
				if(pulsStatDef instanceof VitalityStatDef) {
					VitalityStatDef def = (VitalityStatDef)pulsStatDef;
					String maxStat = def.getMaxStat();
					double pulsMaxValue  = combatInfo.statGetCurrentValueWithPrecision(maxStat);
					pulsCostPercentage = Math.round(pulsMaxValue * getPulseCostPercentage()/100f);
				}
				combatInfo.statModifyBaseValue(pulseCostProp, (int) -Math.round((pulseCost + pulsCostPercentage) * costMultiply));
				combatInfo.sendStatusUpdate();
			}
		} else {
			AgisAbility.interruptAbility(state, result);
			combatInfo.abilityFailed(id);
			return false;
		}
		result = checkPulseEffects(combatInfo,null,state.state,true);
		if(Log.loggingDebug)	Log.debug("AgisAbility.pulseChannelling: checkPulsEffects state="+state+" result="+result);

	
		//}
		/*
		 * Log.debug("ABILITY: vigor value: " + vigor); 
		 * if (vigor != 0) { combatInfo.statModifyBaseValue("vigor", vigor); combatInfo.sendStatusUpdate(); }
		 */
       // sada
        // Here we remove any effects that are eaten up by the ability
        if (pulseAttackerEffectConsumption.size() > 0) {
    	    for (int i = 0; i < pulseAttackerEffectConsumption.size(); i++) {
    	    	int effectConsumed = pulseAttackerEffectConsumption.get(i);
    		    for (EffectState existingState : state.getSource().getCurrentEffects()) {
    			    if (effectConsumed == existingState.getEffect().getID()) {
						if (Log.loggingDebug)
							Log.debug("ABILITY: attacker effect being consumed - effect num/pos: " + existingState.getEffect().getIcon());
						AgisEffect.removeEffect(existingState);
					    break;
    			    } 
    		    }
    	    }
    	    //attackerEffectConsumption.clear();
        }
        
        if (pulseTargetEffectConsumption.size() > 0) {
    	    for (int i = 0; i < pulseTargetEffectConsumption.size(); i++) {
    	    	int effectConsumed = pulseTargetEffectConsumption.get(i);
    	    	for (EffectState existingState : state.getTarget().getCurrentEffects()) {
    			    if (effectConsumed == existingState.getEffect().getID()) {
    			    	if(Log.loggingDebug)	Log.debug("ABILITY: target effect being consumed - effect num/pos: " 
    			    			+ existingState.getEffect().getIcon());
    			    	AgisEffect.removeEffect(existingState);
        			    break;
    			    } 
    		    }
    	    }
    	    //targetEffectConsumption.clear();
        }
	/*	if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.NONE || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
			for (CombatInfo ci : targets) {
				if (pulseTargetEffectConsumption.size() > 0) {
					for (int i = 0; i < pulseTargetEffectConsumption.size(); i++) {
						int effectConsumed = pulseTargetEffectConsumption.get(i);
						for (EffectState existingState : ci.getCurrentEffects()) {
							if (effectConsumed == existingState.getEffect().getID()) {
								if (Log.loggingDebug)
									Log.debug("ABILITY: target effect being consumed - effect num/pos: " + existingState.getEffect().getIcon());
								AgisEffect.removeEffect(existingState);
								break;
							}
						}
					}
					// targetEffectC
				}
			}
		}
*/
        if (pulseAmmoReq > 0) {
        	// Remove an ammo item from the caster
        	Integer ammoLoaded = (Integer) combatInfo.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
        	if (ammoLoaded != null && ammoLoaded > 0) {
        		AgisInventoryClient.removeGenericItem(combatInfo.getOwnerOid(), ammoLoaded, false, ammoReq);
        		
        	}
        }
        
        /* AgisItem item = */ state.getItem();
        
        // Send a skill used message
    	if(Log.loggingDebug)  Log.debug("SKILL: checking send ability skill up for skill: " + skillType + " with sendSkill: " + sendSkillUpChance);
        if (combatInfo.isUser() && skillType != -1 && sendSkillUpChance) {
        //	Log.debug("SKILL: sending ability Used");
        //   CombatClient.abilityUsed(combatInfo.getOwnerOid(), skillType,skillExp,1);
        }
        sendSkillUpChance = true;
        
        // Activate cooldowns. 
   /*    if (!startCooldownsOnActivation) {
        	Collection<Cooldown>cooldowns = state.getAbility().getCooldownMap().values();
        	Cooldown.activateCooldowns(cooldowns, combatInfo, 100);
        	
        	 Log.debug("AgisAbility.completeActivation: finished");
              }*/
        
        // Send weapon used?
    /*    if (state.item == null) {
        	// Item Used message is not sent if the source is hitting their dueling partner
        	Integer duelID = (Integer)state.getSource().getProperty(CombatInfo.COMBAT_PROP_DUEL_ID);
        	if (duelID == null || duelID < 0 || !state.getTarget().isUser())
        		AgisInventoryClient.equippedItemUsed(state.getSourceOid(), "Attack",skillType);
        }*/
      /*  if (combatInfo.isUser() )
        AgisInventoryClient.equippedItemUsed(state.getSourceOid(), "Attack",skillType);
        */
        
       /* if(state.getSource().isUser()) {
        	CombatPlugin.addListsRankingData(state.getSourceOid(), AchievementsClient.USE_ABILITY, getID());
        }*/
        
        Log.debug("AgisAbility.pulseChecking: finished");
        
    	return true;
    }

    public void completeChannelling(AgisAbilityState state) {
		Log.debug("AgisAbility.completeChannelling:");
    }

    public void beginActivated(AgisAbilityState state) {
		Log.debug("AgisAbility.beginActivated:");
    }

    public void pulseActivated(AgisAbilityState state) {
		Log.debug("AgisAbility.pulseActivated:");
		CombatInfo source = state.getSource();
		if (costProp != null) {
			source.statModifyBaseValue(costProp, -activePulseCost);
			source.sendStatusUpdate();
		}
    }

    public void endActivated(AgisAbilityState state) {
		Log.debug("AgisAbility.endActivated:");
    }

    public void interrupt(AgisAbilityState state) {
		Log.debug("AgisAbility.interrupt:");
    }

    /**
     * exposes a way for the client to execute ability with a slash command
     * @param slashCommand string
     */
    public void setSlashCommand(String slashCommand) {
        this.slashCommand = slashCommand;
    }
    public String getSlashCommand() {
        return slashCommand;
    }
    String slashCommand = null;

    public void setRequiredSkill(AgisSkill skill, int level) {
        requiredSkill = skill;
        requiredSkillLevel = level;
    }
    public AgisSkill getRequiredSkill() {
        return requiredSkill;
    }
    public int getRequiredSkillLevel() {
        return requiredSkillLevel;
    }
    AgisSkill requiredSkill = null;
    int requiredSkillLevel = -1;

    public enum AbilityResult {
        SUCCESS,
        OUT_OF_RANGE,
        INVALID_TARGET,
        NO_TARGET,
        DEAD,
        NOT_DEAD,
        UNKNOWN,
        PASSIVE,
        NOT_READY,
        TOO_CLOSE,
        OUT_OF_LOS,
        INSUFFICIENT_ENERGY,
        BAD_ASPECT,
        MISSING_REAGENT,
        MISSING_TOOL,
        MISSING_AMMO,
        BUSY,
        ABORT,
        INSUFFICIENT_VIGOR,
        NOT_IN_FRONT,
        NOT_BEHIND,
        NOT_BESIDE,
        EFFECT_MISSING,
        WRONG_STANCE,
        MISSING_WEAPON,
        INTERRUPTED,
        FORCE_INTERRUPTED,
        EFFECT_ON_CASTER,
        EFFECT_ON_TARGET,
        EFFECT_NOT_ON_CASTER,
        EFFECT_NOT_ON_TARGET,
        NOT_SPIRIT,
        IN_COMBAT,
        NOT_IN_COMBAT,
        PET_GLOBAL_LIMIT,
        PET_TYPE_LIMIT,

    }

    /**
     * Checks if the caster's current target is a valid target for the ability. 
     * @param caster CombatInfo of caster
     * @param target CombatInfo of target
     * @return AbilityResult.SUCCESS if it is a valid target otherwise AbilityResult.INVALID_TARGET
     */
    protected AbilityResult checkTarget(CombatInfo caster, CombatInfo target) {
        if (Log.loggingDebug)
            Log.debug( "AgisAbility.checkTarget: obj=" + caster + " isUser=" + caster.isUser() + " target="
		       + target + " attackable=" + ((target==null)?"N/A":target.attackable())
                    + " Friends:"+caster.getFriendlyTargets().size()
                    +" FFound="+((target==null)?"N/A":caster.getFriendlyTargets().containsKey(target.getOwnerOid()))
                    +" Attackable:"+caster.getAttackableTargets().size()
                    +" AFound="+((target==null)?"N/A":caster.getAttackableTargets().containsKey(target.getOwnerOid())));
//        Log.debug("ABILITY: target type is: " + targetType);
//        Log.debug("TARGET: friendly targets: " + caster.getFriendlyTargets());
//        Log.debug("TARGET: attackable targets: " + caster.getAttackableTargets());
        
        switch (targetType) {
        	case NONE:
        	case LOCATION:
        		return AbilityResult.SUCCESS;
        	case ANY:
        		return AbilityResult.SUCCESS;
        	case AREA:
                if(targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY) {
                    if (aoeType == AoeType.TARGET_RADIUS) {
                        if (!target.attackable()) {
                            return AbilityResult.INVALID_TARGET;
                        }
                        if ((caster.getAttackableTargets().containsKey(target.getOwnerOid()))) {
                            return AbilityResult.SUCCESS;
                        } else {
                            return AbilityResult.INVALID_TARGET;
                        }
                    }
                    return AbilityResult.SUCCESS;
                } else if(targetSubType == TargetSubType.FRIEND) {
                        if (aoeType == AoeType.TARGET_RADIUS) {
                            if (target.equals(caster) || caster.getFriendlyTargets().containsKey(target.getOwnerOid())) {
                                return AbilityResult.SUCCESS;
                            } else {
                                return AbilityResult.INVALID_TARGET;
                            }
                        }
                        return AbilityResult.SUCCESS;
                }
            case GROUP:
                //TODO: add a group check in here
                if (caster.isUser() && !caster.isGrouped())
                    return AbilityResult.INVALID_TARGET;
                else
                    return AbilityResult.SUCCESS;
            case SINGLE_TARGET:
                if(targetSubType == TargetSubType.FRIEND_NOT_SELF) {

                        if (target.equals(caster))
                            return AbilityResult.INVALID_TARGET;
                        if (caster.getFriendlyTargets().containsKey(target.getOwnerOid())) {
                            return AbilityResult.SUCCESS;
                        } else {
                            return AbilityResult.INVALID_TARGET;
                        }
                } else if(targetSubType == TargetSubType.FRIEND) {
                    if (target.equals(caster) || caster.getFriendlyTargets().containsKey(target.getOwnerOid())) {
                        return AbilityResult.SUCCESS;
                    } else {
                        return AbilityResult.INVALID_TARGET;
                    }
                } else if(targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY) {
                    Log.debug("AgisAbility.TargetCheck ENEMY");
                    if (target.equals(caster)) {
                        if (reqTarget) {
                            Log.debug("MISS: enemy equals caster, but a target is required");
                            return AbilityResult.INVALID_TARGET;
                        } else {
                            Log.debug("MISS: enemy equals caster, but a target is not required, so setting to miss");
                            return AbilityResult.SUCCESS;
                        }
                    }
                    if (/*caster.isUser() && */!target.attackable()) {
                        if (reqTarget) {
                            Log.debug("Ability: target no attackable and reqTarget");
                            return AbilityResult.INVALID_TARGET;
                        } else {
                            Log.debug("AgisAbility.TargetCheck not attackable and not reqTarget");
                            return AbilityResult.SUCCESS;
                        }
                    }
                    if (Log.loggingTrace)
                        Log.trace("AgisAbility.TargetCheck caster.getAttackableTargets" + caster.getAttackableTargets());

                    if ((caster.getAttackableTargets().containsKey(target.getOwnerOid()))) {
                        Log.debug("AgisAbility.TargetCheck not attackable and not reqTarget");
                        return AbilityResult.SUCCESS;
                    } else {
                        Log.debug("AgisAbility.TargetCheck target not in getAttackableTargets");
                        if (reqTarget) {
                            Log.debug("AgisAbility.TargetCheck target not in getAttackableTargets  and reqTarget");
                            return AbilityResult.INVALID_TARGET;
                        } else {
                            Log.debug("AgisAbility.TargetCheck target not in getAttackableTargets  and not reqTarget");
                            return AbilityResult.SUCCESS;
                        }
                    }
                }else if(targetSubType == TargetSubType.SELF){
                    return AbilityResult.SUCCESS;
                }
        		//return AbilityResult.SUCCESS;
        	case OTHER:
        		if (target.equals(caster))
        			return AbilityResult.INVALID_TARGET;
        		return AbilityResult.SUCCESS;
        	default:
        		return AbilityResult.INVALID_TARGET;
        }
    }
    
    protected AbilityResult checkTargetSpecies(CombatInfo caster, CombatInfo target) {
    	OID targetOid = target.getOwnerOid();
    	String targetSpecies = "";
    	if (caster.equals(target)) {
    		
    		return AbilityResult.SUCCESS;
    	}
    	if (caster.getAttackableTargets().containsKey(targetOid)) {
    		targetSpecies = caster.getAttackableTargets().get(targetOid).getSpecies();
    	} else if (caster.getFriendlyTargets().containsKey(targetOid)) {
    		targetSpecies = caster.getFriendlyTargets().get(targetOid).getSpecies();
    	}
        if(targetSpecies == null)
            targetSpecies = "";
    	if(Log.loggingDebug)Log.debug("AgisAbility.checkTargetSpecies:  targetSpecies:"+targetSpecies);
    	for (TargetSpecies species: targetableSpecies) {
    		switch (species) {
    		case BEAST:
    			if (!targetSpecies.equals("Beast")) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case HUMANOID:
    			if (!targetSpecies.equals("Humanoid")) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case ELEMENTAL:
    			if (!targetSpecies.equals("Elemental")) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case UNDEAD:
    			if (!targetSpecies.equals("Undead")) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case DRAGON:
    			if (!targetSpecies.equals("Dragon")) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case PLAYER:
    			if (!target.isUser()) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
    		case NONPLAYER:
    			if (target.isUser()) {
    				return AbilityResult.INVALID_TARGET;
    			}
    			break;
			default:
				break;
    		}
    	}
    	
    	return AbilityResult.SUCCESS;
    }
    
    protected AbilityResult checkSpecificTarget(CombatInfo caster, CombatInfo target) {
        if (Log.loggingDebug)
            Log.debug( "AgisAbility.checkSpecificTarget: obj=" + caster + " isUser=" + caster.isUser() + "target="
		       + target + " attackable=" + ((target==null)?"N/A":target.attackable()) );
        if (specificTargets != null) {
        	boolean acceptableTarget = false;
        	if(Log.loggingDebug)	Log.debug("Checking specific target names: " + target.getName());
        	for (int i = 0; i < specificTargets.size(); i++) {
        		if (target.getName().equals(specificTargets.get(i))) {
        			acceptableTarget = true;
        		}
        	}
        	if (acceptableTarget == false)
        		return AbilityResult.INVALID_TARGET;
        }
        
        return AbilityResult.SUCCESS;
    }
    
    protected AbilityResult checkState(CombatInfo obj, CombatInfo target) {
    	/*if (obj.getState() != null && obj.getState().equals(CombatInfo.COMBAT_STATE_INCAPACITATED)) {
    		return AbilityResult.BUSY;
    	} else if (obj.getState() != null && obj.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)) {
    		return AbilityResult.DEAD;
    	}*/
    	return AbilityResult.SUCCESS;
    }
    
    protected AbilityResult checkHasAbility(CombatInfo obj, CombatInfo target) {
    	if (obj.isUser() && (this.skillType > 0) && obj.getAutoAttackAbility() != id) {
    		if(Log.loggingDebug)	Log.debug("AGISABILITY: checking if player knows the ability: " + id);
            Collection<Integer> currentAbilities = obj.getCurrentAbilities();
    		if (!currentAbilities.contains(id)) {
    			if(Log.loggingDebug)	Log.debug("AGISABILITY: player does not know this ability: " + id);
    			return AbilityResult.UNKNOWN;
    		}
    	}
    	Log.debug("AGISABILITY: player knows the ability");
    	return AbilityResult.SUCCESS;
    }
    
    protected AbilityResult checkAbilityType(CombatInfo obj, CombatInfo target) {
    	if (abilityType == 2) {
    		Log.debug("AGISABILITY: ability is passive, cannot be activated");
    	    return AbilityResult.PASSIVE;
    	}
    	return AbilityResult.SUCCESS;
    }
    
    
	protected AbilityResult checkDeath(CombatInfo obj, CombatInfo target) {
		return checkDeath(obj, target, false, null);
	}


	protected AbilityResult checkDeath(CombatInfo obj, CombatInfo target, boolean abilityCheck, AgisAbilityState abilityState) {
		// First check if the player is alive

		if ((getCasterDeath() == 1) && obj.dead()) {
            // Ability requires a alive caster
			return AbilityResult.DEAD;
		}
        if ((getCasterDeath() == 0 || getCasterDeath() == 2 || getCasterDeath() == 4)&& !obj.dead()) {
            // Ability requires a dead caster
            return AbilityResult.NOT_DEAD;
        }
        if (((getCasterDeath() == 0 || getCasterDeath() == 1 || getCasterDeath() == 3) && obj.getState() != null && obj.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)) ||
                (getCasterDeath() == 2 || getCasterDeath() == 4|| getCasterDeath() == 5) && (obj.getState() == null || (obj.getState() != null && !obj.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)))
        ) {
            return AbilityResult.NOT_SPIRIT;
        }
        {
            // Ability requires a dead or alive caster
        }

		if (!reqTarget && abilityCheck) {
			return AbilityResult.SUCCESS;
		}
		switch (targetType) {
            case AREA:
                if (aoeType == AoeType.LOCATION_RADIUS) {
                    return AbilityResult.SUCCESS;
                }
            default:
                if (abilityState != null && abilityState.getClaimID() > 0) {
                    return AbilityResult.SUCCESS;
                }
                return CheckTargetState(target);
        }

    }

    protected AbilityResult CheckTargetState (CombatInfo target){
        if(target !=null){
            if (targetDeath == 0) {
                // Ability requires a dead target
                if (target.dead() && ((target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT))||target.getState() == null))
                    return AbilityResult.SUCCESS;
                else
                    return AbilityResult.INVALID_TARGET;
            } else if (targetDeath == 1) {
                // Ability requires an alive target
                if (target.dead())
                    return AbilityResult.INVALID_TARGET;
                else
                    return AbilityResult.SUCCESS;
            } else if (targetDeath == 2) {
                // Ability requires an alive target
                if (target.getState() == null||(target.getState() != null && !target.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)))
                    return AbilityResult.INVALID_TARGET;
                else
                    return AbilityResult.SUCCESS;
            } else if (targetDeath == 3) {
                // Ability requires an alive target
                if (target.getState() != null && target.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT))
                    return AbilityResult.INVALID_TARGET;
                else
                    return AbilityResult.SUCCESS;
            } else if (targetDeath == 4) {
                // Ability requires an dead or spirit target
                if (!target.dead())
                    return AbilityResult.INVALID_TARGET;
                else
                    return AbilityResult.SUCCESS;
            } else if (targetDeath == 5) {
                // Ability requires an alive or spirit target
                return AbilityResult.SUCCESS;
            } else {
                return AbilityResult.SUCCESS;
            }
        }
        return AbilityResult.SUCCESS;
    }

    /**
     * Check Line of Sight between caster and target
     * @param obj
     * @param target
     * @param casterWNode
     * @param abilityState
     * @return
     */
    protected  AbilityResult checkLineOfSight(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode , AgisAbilityState abilityState) {
        switch (targetType) {
            case SINGLE_TARGET:
                if (Log.loggingDebug)
                    Log.debug("checkLineOfSight: check? " + getLineOfSight() + " for " + (obj != null ? obj.getOwnerOid() : "N/A") + " " + (target != null ? target.getOwnerOid() : "N/A") + " cWNode=" + casterWNode + " abilityState=" + abilityState);
                if (getLineOfSight()) {
                    BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
                    if (casterWNode == null) {
                        if (Log.loggingDebug)
                            Log.debug("checkLineOfSight: wnode is null for caster: " + obj.getOwnerOid());
                        return AbilityResult.SUCCESS;
                    }
                    if (targetWNode == null) {
                        if (Log.loggingDebug)
                            Log.debug("checkLineOfSight: wnode is null for target: " + target.getOwnerOid());
                        return AbilityResult.SUCCESS;
                    }
                    Point casterLoc = casterWNode.getLoc();
                    Point targetLoc = targetWNode.getLoc();

                    boolean r = WorldManagerClient.CheckTargetVisibility(casterWNode.getInstanceOid(), casterLoc, targetLoc);
                    if (Log.loggingDebug)
                     Log.debug("checkLineOfSight: CheckTargetVisibility: " + r);
                    if (!r)
                        return AbilityResult.OUT_OF_LOS;
                }
                return AbilityResult.SUCCESS;
            case AREA:
                if (getLineOfSight()) {
                    if(aoeType.equals(AoeType.TARGET_RADIUS)) {
                        BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
                        if (casterWNode == null) {
                            if (Log.loggingDebug)
                                Log.debug("checkLineOfSight: wnode is null for caster: " + obj.getOwnerOid());
                            return AbilityResult.SUCCESS;
                        }
                        if (targetWNode == null) {
                            if (Log.loggingDebug)
                                Log.debug("checkLineOfSight: wnode is null for target: " + target.getOwnerOid());
                            return AbilityResult.SUCCESS;
                        }
                        Point casterLoc = casterWNode.getLoc();
                        Point targetLoc = targetWNode.getLoc();

                        boolean r = WorldManagerClient.CheckTargetVisibility(casterWNode.getInstanceOid(), casterLoc, targetLoc);
                        if (Log.loggingDebug)
                            Log.debug("checkLineOfSight: AREA TARGET_RADIUS CheckTargetVisibility: " + r);
                        if (!r)
                            return AbilityResult.OUT_OF_LOS;
                    }else if(aoeType.equals(AoeType.LOCATION_RADIUS)) {
                        BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
                        if (casterWNode == null) {
                            if (Log.loggingDebug)
                                Log.debug("checkLineOfSight: wnode is null for caster: " + obj.getOwnerOid());
                            return AbilityResult.SUCCESS;
                        }
                        Point casterLoc = casterWNode.getLoc();
                        boolean r = WorldManagerClient.CheckTargetVisibility(casterWNode.getInstanceOid(), casterLoc, abilityState.location);
                        if (Log.loggingDebug)
                            Log.debug("checkLineOfSight: AREA LOCATION_RADIUS CheckTargetVisibility: " + r);
                        if (!r)
                            return AbilityResult.OUT_OF_LOS;
                    }
                }
                return AbilityResult.SUCCESS;
            default:
                if (Log.loggingDebug) Log.debug("checkLineOfSight: no check for target type: " + targetType);
                return AbilityResult.SUCCESS;
        }

    }

    protected AbilityResult checkRange(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode, float rangeTolerance) {
    	  return checkRange( obj,  target,  casterWNode,  rangeTolerance,false,null) ;
    		  
    }

    protected AbilityResult checkRange(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode, float rangeTolerance,boolean abilityCheck) {
  	  return checkRange( obj,  target,  casterWNode,  rangeTolerance,abilityCheck,null) ;
  		  
  }

    protected AbilityResult checkRange(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode, float rangeTolerance,boolean abilityCheck,AgisAbilityState abilityState) {
    	double rangeMod = 0f;
		if(CombatPlugin.ABILITY_RANGE_MOD_STAT!=null) {
			double statValue = obj.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_RANGE_MOD_STAT);
			if (Log.loggingDebug)
				Log.debug("checkRange: rangeMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_RANGE_MOD_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_RANGE_MOD_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_RANGE_MOD_STAT);
				double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("checkRange: rangeMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("checkRange: rangeMod statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("checkRange: rangeMod statValue > th");
						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
				//if (pointsCalculated < statValue) {
				//	calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
				//}
			} else {
				calculated = statValue;
			}
			if (Log.loggingDebug)
				Log.debug("checkRange: rangeMod calculated: " + calculated);	
			rangeMod = calculated/100f;
			if (Log.loggingDebug)
				Log.debug("checkRange: rangeMod calculated="+calculated+" mod="+rangeMod);
		}


        if (Log.loggingDebug)
            Log.debug("checkRange: targetType="+targetType+" targetSubType="+targetSubType);

		switch (targetType) {
		case LOCATION:
			if (aoeType == AoeType.LOCATION_RADIUS && abilityCheck) {
				if (Log.loggingDebug)
					Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS");
				Point casterLoc = casterWNode.getLoc();
				int hitbox = (Integer) obj.getProperty(CombatPlugin.PROP_HITBOX) ;
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS casterLoc=" + casterLoc + " hitbox=" + hitbox);
				int distance = 0;
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS abilityState=" + abilityState);
				// Log.dumpStack("RANGE CHECK:");
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS location=" + abilityState.getLocation());

				if (CombatPlugin.RANGE_CHECK_VERTICAL) {
					distance = (int) Point.distanceTo(casterLoc, abilityState.getLocation()) - hitbox;
				} else {
					distance = (int) Point.distanceToXZ(casterLoc, abilityState.getLocation()) - hitbox;
				}
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS distance=" + distance + " min=" + getMinRange() + " max=" + getMaxRange() + " rangeTolerance=" + rangeTolerance);
				if(Log.loggingDebug)
					Log.debug("AgisAbility.checkRange:  AREA_ENEMY LOCATION_RADIUS  range=" + distance + " casterLoc=" + casterLoc + " targetLoc=" + abilityState.getLocation() + " maxRange=" + getMaxRange() + " minRange:" + getMinRange()
				+ " casterHitBox:" + hitbox  + " RANGE_CHECK_VERTICAL:" + CombatPlugin.RANGE_CHECK_VERTICAL 
				+ " targetType:" + targetType+" caclculated rangeMax="+((getMaxRange() * rangeTolerance)+(getMaxRange()*(rangeMod-1)))+" rangeMin="+((getMinRange() / rangeTolerance)-(getMinRange()*(rangeMod-1))));
	
				if (distance > (getMaxRange() * rangeTolerance + (getMaxRange() * (rangeMod-1)))) {
					return AbilityResult.OUT_OF_RANGE;
				}
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT OUT OF RANGE");
				if (distance < (getMinRange() / rangeTolerance) - (getMinRange()*(rangeMod-1)) && getMinRange() > 0) {
					return AbilityResult.TOO_CLOSE;
				}
				Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT TO CLOSE");
				return AbilityResult.SUCCESS;
			}
        case AREA:
            if(targetSubType == TargetSubType.ENEMY || targetSubType == TargetSubType.FRIEND_OR_ENEMY) {
                if (aoeType == AoeType.PLAYER_RADIUS && abilityCheck) {
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY PLAYER_RADIUS SUCCESS");
                    return AbilityResult.SUCCESS;
                }
                if (aoeType == AoeType.LOCATION_RADIUS && abilityCheck) {
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS");
                    Point casterLoc = casterWNode.getLoc();
                    int hitbox = (Integer) obj.getProperty(CombatPlugin.PROP_HITBOX);
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS casterLoc=" + casterLoc + " hitbox=" + hitbox);
                    int distance = 0;
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS abilityState=" + abilityState);
                    //Log.dumpStack("RANGE CHECK:");
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS location=" + abilityState.getLocation());

                    if (CombatPlugin.RANGE_CHECK_VERTICAL) {
                        distance = (int) Point.distanceTo(casterLoc, abilityState.getLocation()) - hitbox;
                    } else {
                        distance = (int) Point.distanceToXZ(casterLoc, abilityState.getLocation()) - hitbox;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS distance=" + distance + " min=" + getMinRange() + " max=" + getMaxRange() + " rangeTolerance=" + rangeTolerance);
                    if (Log.loggingDebug)
                        Log.debug("AgisAbility.checkRange: AREA_ENEMY LOCATION_RADIUS range=" + distance + " casterLoc=" + casterLoc + " targetLoc=" + abilityState.getLocation() + " maxRange=" + getMaxRange() + " minRange:" + getMinRange()
                                + " casterHitBox:" + hitbox + " RANGE_CHECK_VERTICAL:" + CombatPlugin.RANGE_CHECK_VERTICAL
                                + " targetType:" + targetType + " caclculated rangeMax=" + ((getMaxRange() * rangeTolerance) + (getMaxRange() * (rangeMod - 1))) + " rangeMin=" + ((getMinRange() / rangeTolerance) - (getMinRange() * (rangeMod - 1))));

                    if (distance > (getMaxRange() * rangeTolerance + (getMaxRange() * (rangeMod - 1)))) {
                        return AbilityResult.OUT_OF_RANGE;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT OUT OF RANGE");
                    if (distance < (getMinRange() / rangeTolerance) - (getMinRange() * (rangeMod - 1)) && getMinRange() > 0) {
                        return AbilityResult.TOO_CLOSE;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT TO CLOSE");
                    return AbilityResult.SUCCESS;
                }
            } else if(targetSubType == TargetSubType.FRIEND) {
                if (aoeType == AoeType.PLAYER_RADIUS && abilityCheck) {
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_FRIENDLY PLAYER_RADIUS SUCCESS");
                    return AbilityResult.SUCCESS;
                }
                if (aoeType == AoeType.LOCATION_RADIUS && abilityCheck) {
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS");
                    Point casterLoc = casterWNode.getLoc();
                    int hitbox = (Integer) obj.getProperty(CombatPlugin.PROP_HITBOX);
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS casterLoc=" + casterLoc + " hitbox=" + hitbox);
                    int distance = 0;
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS abilityState=" + abilityState);
                    Log.dumpStack("RANGE CHECK:");
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS location=" + abilityState.getLocation());

                    if (CombatPlugin.RANGE_CHECK_VERTICAL) {
                        distance = (int) Point.distanceTo(casterLoc, abilityState.getLocation()) - hitbox;
                    } else {
                        distance = (int) Point.distanceToXZ(casterLoc, abilityState.getLocation()) - hitbox;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS distance=" + distance + " min=" + getMinRange() + " max=" + getMaxRange() + " rangeTolerance=" + rangeTolerance);
                    if (Log.loggingDebug)
                        Log.debug("AgisAbility.checkRange: range=" + distance + " casterLoc=" + casterLoc + " targetLoc=" + abilityState.getLocation() + " maxRange=" + getMaxRange() + " minRange:" + getMinRange()
                                + " casterHitBox:" + hitbox + " RANGE_CHECK_VERTICAL:" + CombatPlugin.RANGE_CHECK_VERTICAL
                                + " targetType:" + targetType + " caclculated rangeMax=" + ((getMaxRange() * rangeTolerance) + (getMaxRange() * (rangeMod - 1))) + " rangeMin=" + ((getMinRange() / rangeTolerance) - (getMinRange() * (rangeMod - 1))));

                    if (distance > (getMaxRange() * rangeTolerance) + (getMaxRange() * (rangeMod - 1))) {
                        return AbilityResult.OUT_OF_RANGE;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT OUT OF RANGE");
                    if (distance < (getMinRange() / rangeTolerance) - (getMinRange() * (rangeMod - 1)) && getMinRange() > 0) {
                        return AbilityResult.TOO_CLOSE;
                    }
                    if (Log.loggingDebug)
                        Log.debug("RANGE CHECK: for caster: " + obj.getOwnerOid() + " AREA_ENEMY LOCATION_RADIUS NOT TO CLOSE");
                    return AbilityResult.SUCCESS;
                }
            }
        case SINGLE_TARGET:
        case GROUP:
        case NONE:
        case ANY:
                    BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
            Integer movementState = null;
	        if (casterWNode == null) {
	        	if(Log.loggingDebug)	Log.debug("RANGE CHECK: wnode is null for caster: " + obj.getOwnerOid());
            	return AbilityResult.SUCCESS;
            }
            if (targetWNode == null) {
            	if(Log.loggingDebug)	Log.debug("RANGE CHECK: wnode is null for target: " + target.getOwnerOid());
            	return AbilityResult.SUCCESS;
            }
            Point casterLoc = casterWNode.getLoc();
            Point targetLoc = targetWNode.getLoc();
            int hitbox = (Integer) obj.getProperty(CombatPlugin.PROP_HITBOX) ;
            int targetHitbox = (Integer) target.getProperty(CombatPlugin.PROP_HITBOX) ;
            int distance = 0;
            if (CombatPlugin.RANGE_CHECK_VERTICAL) {
            	distance = (int)Point.distanceTo(casterLoc, targetLoc) - hitbox - targetHitbox;
            } else {
                movementState = (Integer) EnginePlugin.getObjectProperty(target.getOwnerOid(), WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_STATE);
            	// If the target is flying do a 3 dimensional check
            	if (movementState != null && movementState == AgisWorldManagerPlugin.MOVEMENT_STATE_FLYING) {
            		distance = (int)Point.distanceTo(casterLoc, targetLoc) - hitbox - targetHitbox;
            	} else {
            		// Otherwise do a 2D check
            		distance = (int)Point.distanceToXZ(casterLoc, targetLoc) - hitbox - targetHitbox;
            		/*if (Math.abs(casterLoc.getY() - targetLoc.getY()) > 20) {
            			return AbilityResult.OUT_OF_RANGE;
            		}*/
            	}
            }
        	if(Log.loggingDebug)Log.debug("AgisAbility.checkRange: range=" + distance + " casterLoc=" + casterLoc + " targetLoc=" + targetLoc + " maxRange=" + getMaxRange() + " minRange:" + getMinRange()
					+ " casterHitBox:" + hitbox + " targetHitBox:" + targetHitbox + " RANGE_CHECK_VERTICAL:" + CombatPlugin.RANGE_CHECK_VERTICAL + " movementState:" + movementState
					+ " targetType:" + targetType+" rangeTolerance="+rangeTolerance+" caclculated rangeMax="+((getMaxRange() * rangeTolerance)+(getMaxRange()*(rangeMod-1)))+" rangeMin="+((getMinRange() / rangeTolerance)-(getMinRange()*(rangeMod-1))));
			 
			if (distance > (getMaxRange() * rangeTolerance)+(getMaxRange()*(rangeMod-1))) {
            	return AbilityResult.OUT_OF_RANGE;
            }
            if (distance < (getMinRange() / rangeTolerance)-(getMinRange()*(rangeMod-1)) && getMinRange() > 0) {
                return AbilityResult.TOO_CLOSE;
            }
            return AbilityResult.SUCCESS;
        default:
        	if(Log.loggingDebug)	Log.debug("RANGE CHECK: no check Range fot target type: " + targetType);
            return AbilityResult.SUCCESS;
        }
    }

	protected AbilityResult checkReady(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode) {
		AgisAbilityState aas = obj.getCurrentAction();
		if (Log.loggingDebug)
			Log.debug("BUSY: mob is busy with current action: " +aas + "|" + interceptType);
		if (aas != null && interceptType != 1) {
			if (aas != null)
				if (Log.loggingDebug)
					Log.debug("BUSY: mob is busy with current action: " + aas.getAbility() + "|" + aas.getTimeRemaining() + "|" + aas.getState() + "|");
			if (aas.getTimeRemaining() < -1000) {
				obj.setCurrentAction(null);
			/*	if (Log.loggingDebug)
					Log.debug("BUSY: mob is busy with current action: " + obj.getCurrentAction().getAbility() + "|" + (obj.getCurrentAction()!=null?obj.getCurrentAction().getTimeRemaining():"none") + "|" + (obj.getCurrentAction()!=null?obj.getCurrentAction().getState():"none")
							+ "| remainning time is lower then -100 reset agisAbilityState");*/

			} else {
				return AbilityResult.BUSY;
			}
		}

		// Also check if player is moving if the cast time is > 0
		if (!isInstant() && !castingInRun) {
			if (casterWNode.getDir().length() > 0.1) {
				Log.debug("BUSY: mob is moving");
				return AbilityResult.BUSY;
			}
		}

		return AbilityResult.SUCCESS;
	}

    protected AbilityResult checkCooldown(CombatInfo obj, CombatInfo target, BasicWorldNode casterWNode) {
        if (Log.loggingDebug)
            Log.debug("BUSY: Check cooldowns " + obj.getOid());
        if (!Cooldown.checkReady(cooldownMap.values(), obj)) {
            return AbilityResult.NOT_READY;
        }
        return AbilityResult.SUCCESS;
    }
	protected AbilityResult checkCost(CombatInfo obj, CombatInfo target, ActivationState state) {
		if (costProp == null || (getActivationCost() < 1 && getActivationCostPercentage()<=0)) {
			if (Log.loggingDebug)
				Log.debug("AgisAbility.checkCost: costProp=" + costProp);
			return AbilityResult.SUCCESS;
		}
		double costMultiply =1f;
		if(CombatPlugin.ABILITY_COST_MOD_STAT!=null) {
			double statValue = obj.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COST_MOD_STAT);
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COST_MOD_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COST_MOD_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COST_MOD_STAT);
				double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("AgisAbility: CostMod statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("AgisAbility: CostMod statValue > th");
						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
				/*if (pointsCalculated < statValue) {
					calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
				}*/
			} else {
				calculated = statValue;
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod calculated: " + calculated);	
			costMultiply = (calculated/100f);
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod calculated="+calculated+" mod="+costMultiply);
			
			
		}
		double costPercentage=0;
		double costValue = obj.statGetCurrentValueWithPrecision(costProp);
		AgisStatDef statDef = CombatPlugin.lookupStatDef(costProp);
		if(statDef instanceof VitalityStatDef) {
			VitalityStatDef def = (VitalityStatDef)statDef;
			String maxStat = def.getMaxStat();
			double maxMaxValue = obj.statGetCurrentValueWithPrecision(maxStat);
			costPercentage = Math.round(maxMaxValue * getActivationCostPercentage()/100f);
		}
		if (Log.loggingDebug)
			Log.debug("AgisAbility.checkCost: costProp=" + costProp + " value=" + costValue);
		switch (state) {
		case INIT:
			if ((getActivationCost() + costPercentage) * costMultiply> obj.statGetCurrentValueWithPrecision(costProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		case ACTIVATING:
			if (!consumeOnActivation) {
				if ((getActivationCost() + costPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(costProp)) {
					return AbilityResult.INSUFFICIENT_ENERGY;
				}
			}
			break;
		case CHANNELLING:
			if (pulseCostProp == null) {
				if (Log.loggingDebug)
					Log.debug("AgisAbility.checkCost: CHANNELLING pulseCostProp=" + pulseCostProp);
				return AbilityResult.SUCCESS;
			}
			double pulscostPercentage=0;
			
			//Integer pulscostValue = obj.statGetCurrentValue(pulseCostProp);
			AgisStatDef pulsstatDef = CombatPlugin.lookupStatDef(pulseCostProp);
			if(statDef instanceof VitalityStatDef) {
				VitalityStatDef def = (VitalityStatDef)pulsstatDef;
				String maxStat = def.getMaxStat();
				double pulsMaxValue = obj.statGetCurrentValueWithPrecision(maxStat);
				pulscostPercentage = Math.round(pulsMaxValue * getPulseCostPercentage()/100f);
			}
				if ((getPulseCost() + pulscostPercentage) * costMultiply> obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		case ACTIVATED:
			if(aoeType== AoeType.LOCATION_RADIUS) {
				if (pulseCostProp == null) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility.checkCost: CHANNELLING pulsCostProp=" + pulseCostProp);
					return AbilityResult.SUCCESS;
				}
				
				double pulsCostPercentage=0;
				AgisStatDef pulsStatDef = CombatPlugin.lookupStatDef(pulseCostProp);
				if(statDef instanceof VitalityStatDef) {
					VitalityStatDef def = (VitalityStatDef)pulsStatDef;
					String maxStat = def.getMaxStat();
					double pulsMaxValue  = obj.statGetCurrentValueWithPrecision(maxStat);
					pulsCostPercentage = Math.round(pulsMaxValue * getPulseCostPercentage()/100f);
				}
					if ((getPulseCost() + pulsCostPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
					return AbilityResult.INSUFFICIENT_ENERGY;
				}
				break;
				
			}
			
			if (getActiveCost()*costMultiply > obj.statGetCurrentValueWithPrecision(costProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		case COMPLETED:
			if (getActiveCost()*costMultiply > obj.statGetCurrentValueWithPrecision(costProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		default:
			break;
		}
		return AbilityResult.SUCCESS;
	}


	protected AbilityResult checkPulseCost(CombatInfo obj, CombatInfo target, ActivationState state) {
		if (pulseCostProp == null || (getPulseCost() < 1 && getPulseCostPercentage() <= 0)) {
			if (Log.loggingDebug)
				Log.debug("AgisAbility.checkPulseCost: pulseCostProp=" + pulseCostProp);
			return AbilityResult.SUCCESS;
		}
		double costMultiply =1f;
		if(CombatPlugin.ABILITY_COST_MOD_STAT!=null) {
			double statValue = obj.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COST_MOD_STAT);
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COST_MOD_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COST_MOD_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COST_MOD_STAT);
				double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CostMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("AgisAbility: CostMod statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("AgisAbility: CostMod statValue > th");
						calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
				/*if (pointsCalculated < statValue) {
					calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
				}*/
			} else {
				calculated = statValue;
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod calculated: " + calculated);	
			costMultiply = (calculated/100f);
			if (Log.loggingDebug)
				Log.debug("AgisAbility: CostMod calculated="+calculated+" mod="+costMultiply);
		}
		
		double costPercentage=0;
		double costValue = obj.statGetCurrentValueWithPrecision(pulseCostProp);
		AgisStatDef statDef = CombatPlugin.lookupStatDef(pulseCostProp);
		if(statDef instanceof VitalityStatDef) {
			VitalityStatDef def = (VitalityStatDef)statDef;
			String maxStat = def.getMaxStat();
			double maxMaxValue = obj.statGetCurrentValueWithPrecision(maxStat);
			costPercentage = Math.round(maxMaxValue * getPulseCostPercentage() / 100f);
		}
		if (Log.loggingDebug)
			Log.debug("AgisAbility.checkCost: pulseCostProp=" + pulseCostProp + " value=" + costValue+" costPercentage="+costPercentage);
		
		switch (state) {
		case ACTIVATING:
				if ((getPulseCost() + costPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
					return AbilityResult.INSUFFICIENT_ENERGY;
				}
			break;
		case CHANNELLING:
			if (pulseCostProp == null) {
				if (Log.loggingDebug)
					Log.debug("AgisAbility.checkCost: CHANNELLING pulseCostProp=" + pulseCostProp);
				return AbilityResult.SUCCESS;
			}
			if ((getPulseCost() + costPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		case ACTIVATED:
			if(aoeType== AoeType.LOCATION_RADIUS) {
				if (pulseCostProp == null) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility.checkCost: CHANNELLING pulsCostProp=" + pulseCostProp);
					return AbilityResult.SUCCESS;
				}
				
				if ((getPulseCost() + costPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
					return AbilityResult.INSUFFICIENT_ENERGY;
				}
				break;
			}
			break;
		case COMPLETED:
			if ((getPulseCost() + costPercentage) * costMultiply > obj.statGetCurrentValueWithPrecision(pulseCostProp)) {
				return AbilityResult.INSUFFICIENT_ENERGY;
			}
			break;
		default:
			break;
		}
		return AbilityResult.SUCCESS;
	}

    protected AbilityResult checkVigor(CombatInfo obj, CombatInfo target, ActivationState state) {
    	if (getVigor() > -1 || obj.isMob()) {
    		if(Log.loggingDebug)  Log.debug("AgisAbility.checkCost: vigor=" + vigor);
    	    return AbilityResult.SUCCESS;
    	}
    	double vigorcost = getVigor() * -1;
    	switch (state) {
    		case INIT:
    		case ACTIVATING:
    			if (vigorcost > obj.statGetCurrentValueWithPrecision("vigor")) {
    				if(Log.loggingDebug)Log.debug("ABILITY: vigorcost: " + vigorcost + "current vigor: " + obj.statGetCurrentValueWithPrecision("vigor"));
    				return AbilityResult.INSUFFICIENT_VIGOR;
    			}
    			break;
    		case CHANNELLING:
    			break;
    		case ACTIVATED:
    			break;
    		case INTERRUPTED:
    			break;
    		default:
    			break;
    	}
    	return AbilityResult.SUCCESS;
    }
    
    /**
     * This method checks if this ability has any effect requirements
     *
     * @param obj
     * @param target
     * @param state
     * @return
     *
     * @deprecated use {@link #checkEffectTags(CombatInfo,CombatInfo,ActivationState)} instead.
     */
    @Deprecated
	protected AbilityResult checkEffects(CombatInfo obj, CombatInfo target, ActivationState state) {
		return checkEffects(obj, target, state, false);
	}

    /**
     * This method checks if this ability has any effect requirements
     *
     * @param obj
     * @param target
     * @param state
     * @param abilityCheck
     * @return
     *
     * @deprecated use {@link #checkEffectTags(CombatInfo,CombatInfo,ActivationState,boolean)} instead.
     */
    @Deprecated
	protected AbilityResult checkEffects(CombatInfo obj, CombatInfo target, ActivationState state, boolean abilityCheck) {
	  	// First scroll through the players effects
    	/*Long playerOid = obj.getOwnerOid();*/
    	//LinkedList<Integer> effects = (LinkedList) obj.getProperty("effects");
    	
    	for (int i = 0; i < attackerEffectReqs.size(); i++) {
    		boolean effectPresent = false;
    	    /*for (int j = 0; j < effects.size(); j++) {
    		    if (attackerEffectReqs.get(i).equals(effects.get(j)))
    			    effectPresent = true;
    	    }*/
    		for (EffectState eState : obj.getCurrentEffects()) {
        		if (eState.getEffectID() == attackerEffectReqs.get(i)) {
        			effectPresent = true;
        		}
        	}
    	    if (!effectPresent) {
    	    	if(Log.loggingDebug) 	Log.debug("ABILITY: attacker missing effect: " + attackerEffectReqs.get(i));
    	    	if(Log.loggingDebug) Log.debug("ABILITY: attacker missing effect. Attacker effects: " + obj.getCurrentEffects());
    		    return AbilityResult.EFFECT_MISSING;
    	    }
        }
    	
    	if (!reqTarget && abilityCheck) {
			return AbilityResult.SUCCESS;
		}
    	    /*Long targetOid = target.getOwnerOid();*/
    	    //effects = (LinkedList) target.getProperty("effects");
    	    for (int i = 0; i < targetEffectReqs.size(); i++) {
    		    boolean effectPresent = false;
    		    for (EffectState eState : target.getCurrentEffects()) {
            		if (eState.getEffectID() == targetEffectReqs.get(i)) {
            			effectPresent = true;
            		}
            	}
    		    if (!effectPresent) {
    		    	if(Log.loggingDebug)Log.debug("ABILITY: target missing effect: " + targetEffectReqs.get(i));
    		    	if(Log.loggingDebug) Log.debug("ABILITY: target missing effect. Target effects: " + target.getCurrentEffects());
    			    return AbilityResult.EFFECT_MISSING;
    		    }
    	    }
        return AbilityResult.SUCCESS;
    }

    /**
     * This method checks if this ability has any effect with specified tags
     *
     * @param obj
     * @param target
     * @param state
     * @return
     */
    protected AbilityResult checkEffectTags(CombatInfo obj, CombatInfo target, ActivationState state) {
        return checkEffectTags(obj, target, state, false);
    }

    /**
     * This method checks if this ability has any effect with specified tags
     *
     * @param obj
     * @param target
     * @param state
     * @param abilityCheck
     * @return
     */

    protected AbilityResult checkEffectTags(CombatInfo obj, CombatInfo target, ActivationState state, boolean abilityCheck) {

        if(Log.loggingDebug) Log.debug("ABILITY:  checkEffectTags: " +getEffectTagsOnCaster()+" " + getEffectTagsNotOnCaster()+" "+getEffectTagsOnTarget()+" "+getEffectTagsNotOnTarget());
        //Checking effect tags on caster
        for (int i = 0; i < getEffectTagsOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(Log.loggingDebug) Log.debug("ABILITY:  checkEffectTags: effect tags " +eState.getEffect().getTags());
                if(eState.getEffect().getTags().contains(getEffectTagsOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags effect tag Present: " +effectPresent+" tag " + getEffectTagsOnCaster().get(i));
            if (!effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker missing effect tag : " + getEffectTagsOnCaster().get(i));
                return AbilityResult.EFFECT_NOT_ON_CASTER;
            }
        }
        if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags pass tags on caster");
        for (int i = 0; i < getEffectTagsNotOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(Log.loggingDebug) Log.debug("ABILITY:  checkEffectTags: effect tags " +eState.getEffect().getTags());
                if(eState.getEffect().getTags().contains(getEffectTagsNotOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags effect tag Present: " +effectPresent+" tag " + getEffectTagsNotOnCaster().get(i));
            if (effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker have effect tag : " + getEffectTagsNotOnCaster().get(i));
                return AbilityResult.EFFECT_ON_CASTER;
            }
        }
        if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags pass no tags on caster");

//        if (!reqTarget && abilityCheck) {
//            return AbilityResult.SUCCESS;
//        }

        //Checking effect tags on target
        for (int i = 0; i < getEffectTagsOnTarget().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : target.getCurrentEffects()) {
                if(Log.loggingDebug) Log.debug("ABILITY:  checkEffectTags: effect tags " +eState.getEffect().getTags());
                if(eState.getEffect().getTags().contains(getEffectTagsOnTarget().get(i))) {
                    effectPresent = true;
                }
            }
            if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags effect tag Present: " +effectPresent+" tag " + getEffectTagsOnTarget().get(i));
            if (!effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: target missing effect tag : " + getEffectTagsOnTarget().get(i));
                return AbilityResult.EFFECT_NOT_ON_TARGET;
            }
        }
        if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags pass tags on target");
        for (int i = 0; i < getEffectTagsNotOnTarget().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : target.getCurrentEffects()) {
                if(Log.loggingDebug) Log.debug("ABILITY:  checkEffectTags: effect tags " +eState.getEffect().getTags());
                if(eState.getEffect().getTags().contains(getEffectTagsNotOnTarget().get(i))) {
                    effectPresent = true;
                }
            }
            if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags effect tag Present: " +effectPresent+" tag " + getEffectTagsNotOnTarget().get(i));
            if (effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: target have effect tag : " + getEffectTagsNotOnTarget().get(i));
                return AbilityResult.EFFECT_ON_TARGET;
            }
        }
        if(Log.loggingDebug) Log.debug("ABILITY: checkEffectTags pass no tags on target");
        return AbilityResult.SUCCESS;
    }
    /**
     * This method checks if this ability has any effect with specified tags
     *
     * @param obj
     * @param target
     * @param state
     * @return
     */
    protected AbilityResult checkPulseEffectTags(CombatInfo obj, CombatInfo target, ActivationState state) {
        return checkPulseEffectTags(obj, target, state, false);
    }
    /**
     * This method checks if this ability has any effect with specified tags
     *
     * @param obj
     * @param target
     * @param state
     * @param abilityCheck
     * @return
     */
    protected AbilityResult checkPulseEffectTags(CombatInfo obj, CombatInfo target, ActivationState state, boolean abilityCheck) {

        //Checking effect tags on caster
        for (int i = 0; i < getEffectTagsOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getEffectTagsOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if (!effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker missing effect tag : " + getEffectTagsOnCaster().get(i));
                return AbilityResult.EFFECT_NOT_ON_CASTER;
            }
        }

        for (int i = 0; i < getEffectTagsNotOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getEffectTagsNotOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if (effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker have effect tag : " + getEffectTagsOnCaster().get(i));
                return AbilityResult.EFFECT_ON_CASTER;
            }
        }

//        if (!reqTarget && abilityCheck) {
//            return AbilityResult.SUCCESS;
//        }

        //Checking effect tags on target

        if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
            ArrayList<CombatInfo> targetsToRemove = new ArrayList<CombatInfo>();
            for (CombatInfo ci : targets) {


                for (int i = 0; i < getEffectTagsOnTarget().size(); i++) {
                    boolean effectPresent = false;
                    for (EffectState eState : ci.getCurrentEffects()) {
                        if (eState.getEffect().getTags().contains(getEffectTagsOnTarget().get(i))) {
                            effectPresent = true;
                        }
                    }
                    if (!effectPresent) {
                        if (Log.loggingDebug)
                            Log.debug("ABILITY: target missing effect tag : " + getEffectTagsOnTarget().get(i));
                        if(!targetsToRemove.contains(ci))
                        targetsToRemove.add(ci);
                    }
                }

                for (int i = 0; i < getEffectTagsNotOnTarget().size(); i++) {
                    boolean effectPresent = false;
                    for (EffectState eState : ci.getCurrentEffects()) {
                        if (eState.getEffect().getTags().contains(getEffectTagsNotOnTarget().get(i))) {
                            effectPresent = true;
                        }
                    }
                    if (effectPresent) {
                        if (Log.loggingDebug)
                            Log.debug("ABILITY: target have effect tag : " + getEffectTagsNotOnTarget().get(i));
                        if(!targetsToRemove.contains(ci))
                        targetsToRemove.add(ci);
                    }
                }
            }
            if(targetsToRemove.size()>0) {
                targets.removeAll(targetsToRemove);
            }

        } else {
            for (int i = 0; i < getEffectTagsOnTarget().size(); i++) {
                boolean effectPresent = false;
                for (EffectState eState : target.getCurrentEffects()) {
                    if (eState.getEffect().getTags().contains(getEffectTagsOnTarget().get(i))) {
                        effectPresent = true;
                    }
                }
                if (!effectPresent) {
                    if (Log.loggingDebug)
                        Log.debug("ABILITY: attacker missing effect tag : " + getEffectTagsOnTarget().get(i));
                    return AbilityResult.EFFECT_NOT_ON_TARGET;
                }
            }

            for (int i = 0; i < getEffectTagsNotOnTarget().size(); i++) {
                boolean effectPresent = false;
                for (EffectState eState : target.getCurrentEffects()) {
                    if (eState.getEffect().getTags().contains(getEffectTagsNotOnTarget().get(i))) {
                        effectPresent = true;
                    }
                }
                if (effectPresent) {
                    if (Log.loggingDebug)
                        Log.debug("ABILITY: attacker missing effect tag : " + getEffectTagsNotOnTarget().get(i));
                    return AbilityResult.EFFECT_ON_TARGET;
                }
            }
        }
        return AbilityResult.SUCCESS;
    }


    /**
     * This method checks if this ability has any effect requirements
     *
     * @param obj
     * @param target
     * @param state
     *
     * @deprecated use {@link #checkPulseEffectTags(CombatInfo,CombatInfo,ActivationState)} instead.
     */
    @Deprecated
	protected AbilityResult checkPulseEffects(CombatInfo obj, CombatInfo target, ActivationState state) {
		return checkEffects(obj, target, state, false);
	}

    /**
     * This method checks if this ability has any effect requirements
     * @param obj
     * @param target
     * @param state
     * @param abilityCheck
     * @return
     *
     * @deprecated use {@link #checkPulseEffectTags(CombatInfo,CombatInfo,ActivationState,boolean)} instead.
     */
    @Deprecated
	protected AbilityResult checkPulseEffects(CombatInfo obj, CombatInfo target, ActivationState state, boolean abilityCheck) {
        if(Log.loggingDebug) 	Log.debug("checkPulsEffects Start");
        // First scroll through the players effects
    	/*Long playerOid = obj.getOwnerOid();*/
    	//LinkedList<Integer> effects = (LinkedList) obj.getProperty("effects");

        //Checking effect tags on caster
        for (int i = 0; i < getPulseEffectTagsOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getPulseEffectTagsOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if (!effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker missing effect tag : " + getPulseEffectTagsOnCaster().get(i));
                return AbilityResult.EFFECT_NOT_ON_CASTER;
            }
        }

        for (int i = 0; i < getPulseEffectTagsNotOnCaster().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : obj.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getPulseEffectTagsNotOnCaster().get(i))) {
                    effectPresent = true;
                }
            }
            if (effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: attacker have effect tag : " + getPulseEffectTagsNotOnCaster().get(i));
                return AbilityResult.EFFECT_ON_CASTER;
            }
        }

//        if (!reqTarget && abilityCheck) {
//            return AbilityResult.SUCCESS;
//        }

        //Checking effect tags on target
        for (int i = 0; i < getPulseEffectTagsOnTarget().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : target.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getPulseEffectTagsOnTarget().get(i))) {
                    effectPresent = true;
                }
            }
            if (!effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: target missing effect tag : " + getPulseEffectTagsOnTarget().get(i));
                return AbilityResult.EFFECT_NOT_ON_TARGET;
            }
        }

        for (int i = 0; i < getPulseEffectTagsNotOnTarget().size(); i++) {
            boolean effectPresent = false;
            for (EffectState eState : target.getCurrentEffects()) {
                if(eState.getEffect().getTags().contains(getPulseEffectTagsNotOnTarget().get(i))) {
                    effectPresent = true;
                }
            }
            if (effectPresent) {
                if(Log.loggingDebug) Log.debug("ABILITY: target have effect tag : " + getPulseEffectTagsNotOnTarget().get(i));
                return AbilityResult.EFFECT_ON_TARGET;
            }
        }







//    	for (int i = 0; i < pulseAttackerEffectReqs.size(); i++) {
//    		boolean effectPresent = false;
//    	    /*for (int j = 0; j < effects.size(); j++) {
//    		    if (attackerEffectReqs.get(i).equals(effects.get(j)))
//    			    effectPresent = true;
//    	    }*/
//    		for (EffectState eState : obj.getCurrentEffects()) {
//        		if (eState.getEffectID() == pulseAttackerEffectReqs.get(i)) {
//        			effectPresent = true;
//        		}
//        	}
//    	    if (!effectPresent) {
//    	    	if(Log.loggingDebug) 	Log.debug("ABILITY: attacker missing effect: " + pulseAttackerEffectReqs.get(i));
//    	    	if(Log.loggingDebug) Log.debug("ABILITY: attacker missing effect. Attacker effects: " + obj.getCurrentEffects());
//    		    return AbilityResult.EFFECT_MISSING;
//    	    }
//        }
    	if(Log.loggingDebug) 	Log.debug("checkPulsEffects  |");
    	ArrayList<CombatInfo> targetsToRemove = new ArrayList<CombatInfo>();
		if (aoeType == AoeType.PLAYER_RADIUS || aoeType == AoeType.TARGET_RADIUS || aoeType == AoeType.LOCATION_RADIUS) {
			for (CombatInfo ci : targets) {
//				for (int i = 0; i < pulseTargetEffectReqs.size(); i++) {
//					boolean effectPresent = false;
//					for (EffectState eState : ci.getCurrentEffects()) {
//						if (eState.getEffectID() == pulseTargetEffectReqs.get(i)) {
//							effectPresent = true;
//						}
//					}
//					if (!effectPresent) {
//						targetsToRemove.add(ci);
//						if (Log.loggingDebug)
//							Log.debug("ABILITY: target missing effect: " + pulseTargetEffectReqs.get(i));
//						if (Log.loggingDebug)
//							Log.debug("ABILITY: target missing effect. Target effects: " + ci.getCurrentEffects());
//						//return AbilityResult.EFFECT_MISSING;
//					}
//				}
                //Checking effect tags on target
                for (int i = 0; i < getPulseEffectTagsOnTarget().size(); i++) {
                    boolean effectPresent = false;
                    for (EffectState eState : ci.getCurrentEffects()) {
                        if(eState.getEffect().getTags().contains(getPulseEffectTagsOnTarget().get(i))) {
                            effectPresent = true;
                        }
                    }
                    if (effectPresent) {
                        if(Log.loggingDebug) Log.debug("ABILITY: target missing effect tag : " + getPulseEffectTagsOnTarget().get(i));
                        targetsToRemove.add(ci);
                        //return AbilityResult.EFFECT_NOT_ON_TARGET;
                    }
                }

                for (int i = 0; i < getPulseEffectTagsNotOnTarget().size(); i++) {
                    boolean effectPresent = false;
                    for (EffectState eState : ci.getCurrentEffects()) {
                        if(eState.getEffect().getTags().contains(getPulseEffectTagsNotOnTarget().get(i))) {
                            effectPresent = true;
                        }
                    }
                    if (!effectPresent) {
                        if(!targetsToRemove.contains(ci))
                            targetsToRemove.add(ci);
                        if(Log.loggingDebug) Log.debug("ABILITY: target have effect tag : " + getPulseEffectTagsNotOnTarget().get(i));
                        //return AbilityResult.EFFECT_ON_TARGET;
                    }
                }
			}
			if(targetsToRemove.size()>0) {
				targets.removeAll(targetsToRemove);
			}
			return AbilityResult.SUCCESS;
    	} else {
    		if(Log.loggingDebug) 	
    			Log.debug("checkPulsEffects  else reqTarget="+reqTarget+" abilityCheck="+abilityCheck);
    	
    	if (!reqTarget && abilityCheck) {
    		
			return AbilityResult.SUCCESS;
		}
    	    /*Long targetOid = target.getOwnerOid();*/
    	    //effects = (LinkedList) target.getProperty("effects");
//    	    for (int i = 0; i < pulseTargetEffectReqs.size(); i++) {
//    		    boolean effectPresent = false;
//    		    for (EffectState eState : target.getCurrentEffects()) {
//            		if (eState.getEffectID() == pulseTargetEffectReqs.get(i)) {
//            			effectPresent = true;
//            		}
//            	}
//    		    if (!effectPresent) {
//    		    	if(Log.loggingDebug)Log.debug("ABILITY: target missing effect: " + pulseTargetEffectReqs.get(i));
//    		    	if(Log.loggingDebug) Log.debug("ABILITY: target missing effect. Target effects: " + target.getCurrentEffects());
//    			    return AbilityResult.EFFECT_MISSING;
//    		    }
//    	    }

            //Checking effect tags on target
            for (int i = 0; i < getPulseEffectTagsOnTarget().size(); i++) {
                boolean effectPresent = false;
                for (EffectState eState : target.getCurrentEffects()) {
                    if(eState.getEffect().getTags().contains(getPulseEffectTagsOnTarget().get(i))) {
                        effectPresent = true;
                    }
                }
                if (!effectPresent) {
                    if(Log.loggingDebug) Log.debug("ABILITY: target missing effect tag : " + getPulseEffectTagsOnTarget().get(i));
                    return AbilityResult.EFFECT_NOT_ON_TARGET;
                }
            }

            for (int i = 0; i < getPulseEffectTagsNotOnTarget().size(); i++) {
                boolean effectPresent = false;
                for (EffectState eState : target.getCurrentEffects()) {
                    if(eState.getEffect().getTags().contains(getPulseEffectTagsNotOnTarget().get(i))) {
                        effectPresent = true;
                    }
                }
                if (effectPresent) {
                    if(Log.loggingDebug) Log.debug("ABILITY: target have effect tag : " + getPulseEffectTagsNotOnTarget().get(i));
                    return AbilityResult.EFFECT_ON_TARGET;
                }
            }
    	}
		if(Log.loggingDebug) 	Log.debug("checkPulsEffects End ");
        return AbilityResult.SUCCESS;
    }
	
	
	
    
    protected AbilityResult checkStance(CombatInfo obj, CombatInfo target, ActivationState state) {
    	//OID playerOid = obj.getOwnerOid();
    	return AbilityResult.SUCCESS;
    }
    
    /*
     * This method checks to see if the ability requires the player to be at a certain position
     * relative to the target.
     */
    protected AbilityResult checkPosition(CombatInfo obj, CombatInfo target, ActivationState state, BasicWorldNode attackerNode) {
    	if(Log.loggingDebug)Log.debug("AgisAbility.checkPosition: reqFacingTarget="+reqFacingTarget+" obj.isUser="+obj.isUser());
    	if (aoeType == AoeType.LOCATION_RADIUS) {
			return AbilityResult.SUCCESS;
		}
    	
    	if (reqFacingTarget && obj.isUser()) {
    		//float facingAngle = CombatHelper.calculateValue(target, obj);
    		BasicWorldNode targetNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
    		AOVector reqDir = AOVector.sub(targetNode.getLoc(), attackerNode.getLoc());
    		//Quaternion q = Quaternion.
    		//AOVector orientAngle = attackerNode.getOrientation().toEulerAngles();
    		float playerYaw = attackerNode.getOrientation().getYaw();
    		float motionYaw = AOVector.getLookAtYaw(reqDir);
    		if(motionYaw < 0)
    			motionYaw += 360;
    		float yaw = playerYaw - motionYaw;
    		if(yaw < 360)
    			yaw += 360;
    		if(yaw >180)
    			yaw -= 360;
    	/*	if (playerYaw < 0)
    			playerYaw += 360;
    		if (motionYaw < 0)
    			motionYaw += 360;*/
    		if(Log.loggingDebug)Log.debug("ANGLE: player orient: " + playerYaw + " and motionYaw = " + motionYaw);
    		if (Math.abs(yaw) > 60) {
    			if (reqTarget) {
    				return AbilityResult.NOT_IN_FRONT;
    			} else {
    				target = obj;
    				return AbilityResult.SUCCESS;
    			}
    		}
    		
    	}
    	// If position is 0 no need to continue
    	if (position == 0)
    		return AbilityResult.SUCCESS;
    	
    	//TODO: This code was written by someone else, could do with improvements
    	PlayerAngle angle = new PlayerAngle(CombatHelper.calculateValue(obj, target));
    	switch (position) {
	    	case 1:
	    		// Attacker must be in front of the target
	    		if (!angle.is_within(315, 45, false))
	    			return AbilityResult.NOT_IN_FRONT;
	    		break;
	    	case 2:
	    		// Attacker must be to the side of the target
	    		if (!(angle.is_within(45, 135, false) || angle.is_within(225, 315, false)))
	    			return AbilityResult.NOT_BESIDE;
	    		break;
	    	case 3:
	    		// Attacker must be behind the target
	    		if (!(angle.is_within(135, 225, false)))
	    			return AbilityResult.NOT_BEHIND;
	    		break;
    	}
    	return AbilityResult.SUCCESS;
    }
    
    /*
     * This method checks to see if there are any equipment requirements such as having a sword or shield.
     */
    public AbilityResult checkEquip(CombatInfo obj, CombatInfo target, ActivationState state) {
    	//Long ioid = (Long)AgisInventoryClient.findItem(obj.getOid(), AgisEquipSlot.PRIMARYWEAPON);
    	//String weapType = "Unarmed";
    	/*String weapType = obj.getStringProperty("weaponType");
    	String weapType2 = obj.getStringProperty("weapon2Type");
    	if (weapType2 != null && !weapType2.equals("") && !weapType2.equals("null"))
    		weapType +=";"+obj.getStringProperty("weapon2Type");*/
    	ArrayList<String> weaponType = new ArrayList<String>();
		try {
			if(obj.getPropertyMap().containsKey("weaponType"))
					weaponType = (ArrayList<String>)obj.getProperty("weaponType");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (Log.loggingDebug)
			Log.debug("WEAPON: checking for weapon requirement: " + weaponReq + " against users weapon: " + weaponType);
		if (weaponReq.size() == 0)
			return AbilityResult.SUCCESS;
		for (String wt : weaponType) {
			if (weaponReq.contains(wt)) {
				return AbilityResult.SUCCESS;
			}
		}
		if (weaponReq.contains("Unarmed")) {
			if(weaponType.size() == 0) {
				return AbilityResult.SUCCESS;
			}
		}
		
		
		return AbilityResult.MISSING_WEAPON;

    }


    protected AbilityResult checkPetsLimit(CombatInfo obj, CombatInfo target, AgisAbilityState state){
        List<Integer> profiles = new ArrayList<>();
        boolean checkLimitOnCaster = false;
        for (int i = 0; i < getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().size(); i++) {
            AbilityEffectDefinition aed = getPowerUpDefinition(state.getPowerUptime()).getEffectDefinition().get(i);

            AgisEffect effect = Agis.EffectManager.get(aed.getEffectId());
            if(effect instanceof SpawnEffect){
                SpawnEffect e = (SpawnEffect) effect;
                if(e.getSpawnType() == 3){
                    checkLimitOnCaster =  aed.getTarget().equals("caster");
                    profiles.add(e.getMobID());
                }
            }
        }

        if(profiles.size() == 0) {
            return AbilityResult.SUCCESS;
        } else {
            CombatInfo objToCheck = obj;
            if(!checkLimitOnCaster)
                objToCheck = target;
            double globalCount = objToCheck.statGetCurrentValueWithPrecision(CombatPlugin.PET_GLOBAL_COUNT_STAT);

            Serializable _activePetList = objToCheck.getProperty("activePetList");
            if(_activePetList==null)
                _activePetList = new HashMap<OID,Integer>();
            HashMap<OID,Integer> activePetList = (HashMap<OID,Integer>) _activePetList;
            Log.debug("checkPetsLimit activePetList="+activePetList);
            int count = 0;
            HashMap<Integer, Integer> petProfiles = new HashMap<>();
            if(activePetList.size()>0) {
                for (int v : activePetList.values()) {
                    petProfiles.computeIfAbsent(v, __ -> 0);
                    petProfiles.put(v, petProfiles.get(v) + 1);
                    count++;
                }
            }
            if(count>=globalCount){
                return AbilityResult.PET_GLOBAL_LIMIT;
            } else {
                for (int pId : petProfiles.keySet()) {
                    PetProfile pp = Agis.PetProfile.get(pId);
                    AtavismDatabase adb = new AtavismDatabase();
                    int level = adb.loadPlayerPetLevel(objToCheck.getOid(), pId);
                    if (Log.loggingDebug) Log.debug("checkPetsLimit: PetProfile=" + pId + " level=" + level);

                    int mobTemplateID = -1;
                    if (level <= 0) {
                        PetProfileLevel ppl = pp.levels.get(1);
                        mobTemplateID = ppl.getTemplateId();
                    } else {
                        PetProfileLevel ppl = pp.levels.get(level);
                        mobTemplateID = ppl.getTemplateId();
                    }
                    if (Log.loggingDebug) Log.debug("PET: mobTemplateID=" + mobTemplateID);
                    if (mobTemplateID < 0) {
                        Log.error("PET: cant spawn mot template -1");
                        continue;
                    }
                    Template tmpl = ObjectManagerClient.getTemplate(mobTemplateID, ObjectManagerPlugin.MOB_TEMPLATE);
                    int petCountStat = (Integer) tmpl.get(CombatClient.NAMESPACE, "petCountStat");
                    if (Log.loggingDebug)
                        Log.debug("PET: mobTemplateID=" + mobTemplateID + " petCountStat=" + petCountStat);
                    if (petCountStat > 0) {
                        double petTypeCountLimit = objToCheck.statGetCurrentValueWithPrecision(CombatPlugin.STAT_NAMES.get(petCountStat));
                            if (Log.loggingDebug)
                                Log.debug("PET: type " + petCountStat + ":" + CombatPlugin.STAT_NAMES.get(petCountStat) + "limit " + petTypeCountLimit + " PET_ALLOW_TYPE_LIMIT_EXCEEDED=" + AgisMobPlugin.PET_ALLOW_TYPE_LIMIT_EXCEEDED);
                            if (!AgisMobPlugin.PET_ALLOW_TYPE_LIMIT_EXCEEDED && petTypeCountLimit <= petProfiles.get(pId)) {
                                Log.error("PET: type " + petCountStat + ":" + CombatPlugin.STAT_NAMES.get(petCountStat) + "limit reached ");
                                return AbilityResult.PET_TYPE_LIMIT;
                            }
                        }
                    }

                }

            }
        return AbilityResult.SUCCESS;
    }

    protected AbilityResult checkCombatState(CombatInfo obj, CombatInfo target, ActivationState state) {
        if(Log.loggingDebug)	Log.debug("AgisAbility.checkCombatState: state="+state+" combatState="+combatState);
        if (state == ActivationState.INIT) {
            if(obj.inCombat()){
                if(combatState == 0 || combatState == 2)
                    return AbilityResult.SUCCESS;
                return AbilityResult.IN_COMBAT;
            }else{
                if(combatState == 1 || combatState == 2)
                    return AbilityResult.SUCCESS;
                return AbilityResult.NOT_IN_COMBAT;
            }
        }
        return AbilityResult.SUCCESS;
    }

    protected AbilityResult checkReagent(CombatInfo obj, CombatInfo target, ActivationState state) {
    	if(Log.loggingDebug)	Log.debug("AgisAbility.checkReagent: state="+state+" reagentList="+reagentList);
        if (state == ActivationState.INIT || (state == ActivationState.ACTIVATING && !consumeOnActivation)) {
            if (!reagentList.isEmpty()) {
            	if(Log.loggingDebug)Log.debug("AgisAbility.checkReagent: state="+state+" no empty check size="+reagentList.size());
				if (!AgisInventoryClient.checkComponents(obj.getOwnerOid(), new LinkedList<Integer>(reagentList.keySet()), new LinkedList<Integer>(reagentList.values()))) {
				      
		            //List<OID> itemList = InventoryClient.findItems(obj.getOwnerOid(), reagentList.keySet());
                //if ((itemList == null) || itemList.contains(null)) {
                    return AbilityResult.MISSING_REAGENT;
                }
            }
        }
        return AbilityResult.SUCCESS;
    }

    protected AbilityResult checkPulseReagent(CombatInfo obj, CombatInfo target, ActivationState state) {
    	if(Log.loggingDebug)	Log.debug("AgisAbility.checkPulsReagent: state="+state+" pulsReagentList="+pulseReagentList);
       // if (state == ActivationState.INIT || (state == ActivationState.ACTIVATING )) {
            if (!pulseReagentList.isEmpty()) {
            	if(Log.loggingDebug)Log.debug("AgisAbility.checkPulsReagent: state="+state+" no empty check size="+pulseReagentList.size());
				if (!AgisInventoryClient.checkComponents(obj.getOwnerOid(), new LinkedList<Integer>(pulseReagentList.keySet()), new LinkedList<Integer>(pulseReagentList.values()))) {
                    return AbilityResult.MISSING_REAGENT;
                }
            }
      //  }
        return AbilityResult.SUCCESS;
    }

    protected AbilityResult checkTool(CombatInfo obj, CombatInfo target, ActivationState state) {
        if (state == ActivationState.INIT || state == ActivationState.ACTIVATING) {
            if (!toolList.isEmpty()) {
                List<OID> itemList = InventoryClient.findItems(obj.getOwnerOid(), toolList);
                if ((itemList == null) || itemList.contains(null)) {
                    return AbilityResult.MISSING_TOOL;
                }
            }
        }
        return AbilityResult.SUCCESS;
    }
    
    protected AbilityResult checkAmmo(CombatInfo obj, CombatInfo target, ActivationState state) {
        if (state == ActivationState.INIT || state == ActivationState.ACTIVATING) {
            if (ammoReq > 0) {
            	Integer ammoLoaded = (Integer) obj.getProperty(CombatInfo.COMBAT_AMMO_LOADED);
                	if (ammoLoaded == null || ammoLoaded < 1) {
            		return AbilityResult.MISSING_AMMO;
            	}
                int amoAmount = AgisInventoryClient.getCountGenericItem(obj.getOwnerOid(),ammoLoaded);
                if (amoAmount < ammoReq) {
            		return AbilityResult.MISSING_AMMO;
            	}
              	
            	OID itemFound = InventoryClient.findItem(obj.getOwnerOid(), ammoLoaded);
                if (itemFound == null) {
                    return AbilityResult.MISSING_AMMO;
                }
            }
        }
        return AbilityResult.SUCCESS;
    }

    public AbilityResult checkAbility(CombatInfo obj, CombatInfo target) {
    	return checkAbility(obj, target, ActivationState.INIT,null, false);
    }

    protected AbilityResult checkAbility(CombatInfo obj, CombatInfo target, ActivationState state, AgisAbilityState abilityState, boolean skipCheckCooldown) {
        long tStart = System.nanoTime();
        long tChecksMid0 = System.nanoTime();
        long tChecksMid1 = System.nanoTime();
        long tChecksMid2 = System.nanoTime();
        long tChecksMid3 = System.nanoTime();
        AbilityResult result = AbilityResult.SUCCESS;
        try {
    	// Request in the world node here to save requesting it multiple times
    	if(Log.loggingDebug)Log.debug("AgisAbility.checkAbility Start "+state+" skipChecks="+skipChecks);
        result = checkDeath(obj, target, true, abilityState);
		io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_death")
		.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        if (result != AbilityResult.SUCCESS) {
            Log.debug("ABILITY: checkDeath failed");
            // If this ability is the auto attack, stop it
            if (id == obj.getAutoAttackAbility()) {
                long tStopAutoAttackStart = System.nanoTime();
                obj.stopAutoAttack();
                io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "stop_auto_attack")
                .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStopAutoAttackStart));
            }
            return result;
        }
    	BasicWorldNode casterWNode;
        long tChecksGetNode = System.nanoTime();
    	try {
    		casterWNode = WorldManagerClient.getWorldNode(obj.getOwnerOid());
    		io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "get_wnode")
    		.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tChecksGetNode));
    	} catch (NoRecipientsException e) {
    		io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "get_wnode")
    		.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tChecksGetNode));
    		return AbilityResult.UNKNOWN;
    	}
		if (state == ActivationState.INIT) {
			if(!skipChecks) {
				long t0 = System.nanoTime();
                if(getCheckBusy()) {
                    result = checkReady(obj, target, casterWNode);
                    io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_ready")
                            .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
                    if (result != AbilityResult.SUCCESS)
                        return result;
                }

                if(!skipCheckCooldown) {
                    result = checkCooldown(obj, target, casterWNode);
                    io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_cooldown")
                            .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
                    if (result != AbilityResult.SUCCESS)
                        return result;
                }
                //Check if caster is in combat
                t0 = System.nanoTime();
                result = checkCombatState(obj, target, state);
                io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_combat_state")
                        .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
                if (result != AbilityResult.SUCCESS)
                    return result;
			}

            long t2 = System.nanoTime();
            result = checkPetsLimit(obj, target, abilityState);
            io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_pet")
                    .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t2));
            if (result != AbilityResult.SUCCESS)
                return result;


			if (reqTarget && abilityState.getClaimID()>0 && attackBuilding) {
				long t0 = System.nanoTime();
				boolean building_attackable = VoxelClient.getBuildingIsAttackable(abilityState.getClaimID(), abilityState.getClaimObjID(), abilityState.getSourceOid());
				io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_building")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
				if (!building_attackable)
					return AbilityResult.INVALID_TARGET;
			}
			
			if (reqTarget && abilityState.getClaimID()==-1) {

				long t0 = System.nanoTime();
				result = checkTarget(obj, target);
				io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_target")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
				if (result != AbilityResult.SUCCESS) {
					Log.debug("ABILITY: checkTarget failed");
					// If this ability is the auto attack, stop it
					if (id == obj.getAutoAttackAbility()) {
						long tStopAutoAttackStart = System.nanoTime();
						obj.stopAutoAttack();
						io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "stop_auto_attack")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStopAutoAttackStart));
					}
					return result;
				}
				t0 = System.nanoTime();
				result = checkTargetSpecies(obj, target);
				io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_target_species")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
				if (result != AbilityResult.SUCCESS) {
					Log.debug("ABILITY: checkTargetSpecies failed");
					// If this ability is the auto attack, stop it
					if (id == obj.getAutoAttackAbility()) {
						long tStopAutoAttackStart = System.nanoTime();
						obj.stopAutoAttack();
						io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "stop_auto_attack")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStopAutoAttackStart));
					}
					return result;
				} else {
					Log.debug("ABILITY: checkTargetSpecies success");
				}
				t0 = System.nanoTime();
				result = checkSpecificTarget(obj, target);
				io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_specific_target")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
				if (result != AbilityResult.SUCCESS) {
					Log.debug("ABILITY: checkSpecificTarget failed");
					// If this ability is the auto attack, stop it
					if (id == obj.getAutoAttackAbility()) {
						long tStopAutoAttackStart = System.nanoTime();
						obj.stopAutoAttack();
						io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "stop_auto_attack")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStopAutoAttackStart));
					}
					return result;
				} else {
					Log.debug("ABILITY: checkSpecificTarget success");
				}
			}
			if (!skipChecks) {
				long t0 = System.nanoTime();
				result = checkState(obj, target);
				io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_state")
						.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
				if (result != AbilityResult.SUCCESS) {
					Log.debug("ABILITY: checkState failed");
					// If this ability is the auto attack, stop it
					/*
					 * if (id == obj.getAutoAttackAbility()) { obj.stopAutoAttack(); }
					 */
					return result;
				}

			}
			long t0 = System.nanoTime();
            result = checkEffectTags(obj, target, state, true);
			io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_effects")
					.register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - t0));
			if (result != AbilityResult.SUCCESS)
				return result;

		}
        tChecksMid0 = System.nanoTime();
		
		if (!skipChecks) {
			result = checkHasAbility(obj, target);
			if (result != AbilityResult.SUCCESS)
				return result;
		}
		result = checkAbilityType(obj, target);
    	if (result != AbilityResult.SUCCESS)
    		return result;

    	result = checkTool(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;
    	
    	result = checkEquip(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;

    	result = checkReagent(obj, target, state);
    	if(Log.loggingDebug)Log.debug("AgisAbility.checkAbiklity: checkReagent state="+state+" result="+result);
    	if (result != AbilityResult.SUCCESS)
    	    return result;
    	
    	result = checkAmmo(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;

    	result = checkCost(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;
    	
    	// Check for vigor
    	result = checkVigor(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;
    	
    	// Check for stance
    	/*result = checkStance(obj, target, state);
    	if (result != AbilityResult.SUCCESS)
    	    return result;*/

        tChecksMid1 = System.nanoTime();
    	if (!skipChecks) {
			if (state == ActivationState.INIT) {
				result = checkRange(obj, target, casterWNode, 1.0f, true, abilityState);
			} else {
				// Add a 20% tolerance for distance when the ability is finishing.
				result = checkRange(obj, target, casterWNode, 1.2f, true, abilityState);
			}
            if (result != AbilityResult.SUCCESS)
                return result;

            result = checkLineOfSight(obj, target, casterWNode,abilityState);
            if (result != AbilityResult.SUCCESS)
                return result;
		}

        tChecksMid2 = System.nanoTime();

     	//Check angle target to player
     	//if(abilityState.getClaimID() > 0)
     		result = checkPosition(obj, target, state, casterWNode);
        tChecksMid3 = System.nanoTime();
    	if (result != AbilityResult.SUCCESS) {
    		ExtendedCombatMessages.sendAbilityFailMessage(obj, result, id,costProp);
    	    return result;
    	}
    	
        if (Log.loggingDebug)
            Log.debug("AgisAbility.checkAbility End result=" + result);
    } finally {
        io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_ability")
        .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_ability_mid1")
        .register(Prometheus.registry()).record(Duration.ofNanos(tChecksMid1 - tChecksMid0));
        io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_ability_mid2")
        .register(Prometheus.registry()).record(Duration.ofNanos(tChecksMid2 - tChecksMid1));
        io.micrometer.core.instrument.Timer.builder("agis_ability_check").tag("phase", "check_ability_mid3")
        .register(Prometheus.registry()).record(Duration.ofNanos(tChecksMid3 - tChecksMid2));
    }
        
        return result;
    }

    protected AgisAbilityState generateState(CombatInfo source, CombatInfo target, AgisItem item, Point loc, int claimId, int claimObjId, Point destLoc) {
		return new AgisAbilityState(this, source, target, item, loc, claimId, claimObjId, destLoc);
    }
    
    public static boolean startAbility(AgisAbility ability, CombatInfo source, CombatInfo target, AgisItem item) {
		return startAbility(ability, source, target, item, null,-1,-1, null,0L);
    }

    public static boolean startAbility(AgisAbility ability, CombatInfo source, CombatInfo target, AgisItem item, Point loc, int claimId, int claimObjId, Point destLoc, Long powerUptime) {
        long t0 = System.currentTimeMillis();
		if (Log.loggingTrace) {
			StackTraceElement[] elements = Thread.currentThread().getStackTrace();
			for (int i = 1; i < elements.length; i++) {
				StackTraceElement s = elements[i];
				Log.debug("startAbility " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
			}
		}
		if (Log.loggingDebug) {
			Log.debug("AgisAbility.startAbility ability=" + ability.getName() + " source=" + source + " target=" + target + " item=" + item + " loc=" + loc+" destLoc="+destLoc);
		}
		if (!ability.getSkipChecks()) {
			// First check cooldowns before going any further
			if (!Cooldown.checkReady(ability.cooldownMap.values(), source)) {
				if (Log.loggingDebug)
					Log.debug("AgisAbility.startAbility cooldowns are not ready yet");
				ExtendedCombatMessages.sendErrorMessage(source.getOid(), "cooldownNoEnd");

				Prometheus.registry().timer("start_ability_cooldowns", "ability",
						ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
				return false;
			}
		}
		
		  if (Log.loggingDebug)Log.debug("AgisAbility.startAbility cooldowns ready ");
		Prometheus.registry().timer("start_ability_cooldowns", "ability",
				ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
      
        long tStateStart = System.currentTimeMillis();
        AgisAbilityState state = ability.generateState(source, target, item, loc, claimId, claimObjId, destLoc);
        state.setPowerUptime(powerUptime);
        source.addRunAbilities(state);
        Prometheus.registry().timer("start_ability_generate_state", "ability",
                ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - tStateStart));
        Prometheus.registry().timer("start_ability_before_update_state", "state", state.state.name(), "ability",
                ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
        if (Log.loggingDebug) Log.debug("AgisAbility.startAbility generated state");
        long t1 = System.currentTimeMillis();
        boolean result = state.updateState();
        Prometheus.registry().timer("start_ability_update_state", "state", state.state.name(), "ability",
                ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t1));
        return result;
    }
    public static AgisAbilityState startAbilityDelay(AgisAbility ability, CombatInfo source, CombatInfo target, AgisItem item, Point loc, int claimId, int claimObjId, Long delay) {
        if (Log.loggingDebug) Log.debug("AgisAbility.startAbilityDelay start");
        long t0 = System.currentTimeMillis();
        if(delay>0L) {
            long tStateStart = System.currentTimeMillis();
            AgisAbilityState state = ability.generateState(source, target, item, loc, claimId, claimObjId, null);
            Prometheus.registry().timer("start_ability_generate_state", "ability", ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - tStateStart));
            state.setDelayed();
            state.scheduleDelay(delay);
            return state;
        }else{
            if (!ability.getSkipChecks()) {
                // First check cooldowns before going any further
                if (!Cooldown.checkReady(ability.cooldownMap.values(), source)) {
                    if (Log.loggingDebug)
                        Log.debug("AgisAbility.startAbility cooldowns are not ready yet");
                    ExtendedCombatMessages.sendErrorMessage(source.getOid(), "cooldownNoEnd");

                    Prometheus.registry().timer("start_ability_cooldowns", "ability",
                            ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
                    return null;
                }
            }

            if (Log.loggingDebug)Log.debug("AgisAbility.startAbility cooldowns ready ");
            Prometheus.registry().timer("start_ability_cooldowns", "ability",
                    ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));

            long tStateStart = System.currentTimeMillis();
            AgisAbilityState state = ability.generateState(source, target, item, loc, claimId, claimObjId, null);
            Prometheus.registry().timer("start_ability_generate_state", "ability",
                    ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - tStateStart));
            Prometheus.registry().timer("start_ability_before_update_state", "state", state.state.name(), "ability",
                    ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
            if (Log.loggingDebug) Log.debug("AgisAbility.startAbility generated state");
            long t1 = System.currentTimeMillis();
            boolean result = state.updateState();
            Prometheus.registry().timer("start_ability_update_state", "state", state.state.name(), "ability",
                    ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t1));
            return state;
        }
//        boolean result = state.updateState();

    }
    
    public static void abortAbility(AgisAbilityState state) {
		interruptAbility(state, AbilityResult.ABORT);

        CombatInfo combatInfo = state.getSource();
        Collection<Cooldown>cooldowns = state.getAbility().getCooldownMap().values();
        Cooldown.abortAllCooldowns(cooldowns, combatInfo);
		return;
    }

	public static void deactivateAbility(AgisAbilityState state) {
		state.setState(ActivationState.COMPLETED);
		// interruptAbility(state, AbilityResult.ABORT);
		CombatInfo combatInfo = state.getSource();
		combatInfo.getLock().lock();
		try {
			if (state.getAbility().isToggle()) {
				if (state.source.currentRunAbilities().get(state.ability.getID()) == state)
					
				state.getSource().currentRunAbilities().remove(state.ability.getID());
				sendToggleInfo(state);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.exception(e);
			e.printStackTrace();
		} finally {
			combatInfo.getLock().unlock();
		}
		Engine.getExecutor().remove(state);
		return;
	}

	
	public static void sendToggleInfo(AgisAbilityState state) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "activeToggle");
		Set<Integer> keys = state.getSource().currentRunAbilities().keySet();
		props.put("num", keys.size());
		int i = 0;
		for (Integer key : keys) {
			props.put("a" + (i++), key);
		}
		if (Log.loggingDebug)
			Log.debug("activeToggle " + props);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, state.getSource().getOid(), state.getSource().getOid(), false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	
    public static void interruptAbility(AgisAbilityState state, AbilityResult reason) {
        if (Log.loggingDebug)
            Log.debug("AgisAbility.interruptAbility: reason=" + reason + " state=" + state.getState()+" CastingInRun="+state.getAbility().getCastingInRun());
        if(state.getAbility().getCastingInRun() && reason.equals(AbilityResult.INTERRUPTED)) {
        	if (Log.loggingDebug)
                Log.debug("AgisAbility.interruptAbility: reason=" + reason + " state=" + state.getState()+" getCastingInRun:"+state.getAbility().getCastingInRun()+" Break");
        	return;
        }
		if (state.getState() != ActivationState.INIT) {
			Engine.getExecutor().remove(state);
			 if (state.getSource().getCurrentAction() == state) {
				state.getSource().setCurrentAction(null);
				EnginePlugin.setObjectPropertyNoResponse(state.getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "casting", "");
				EnginePlugin.setObjectPropertyNoResponse(state.getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingAffinity", "");
				EnginePlugin.setObjectPropertyNoResponse(state.getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingParam", -1);
                 for (CoordinatedEffectState ceState: state.getCoordinatedEffectStates()) {
                     if(ceState!=null)
                         ceState.invokeCancel();
                 }
				// ExtendedCombatMessages.sendCastingCancelledMessage(state.getSource());
			//	Log.error("AgisAbility: Interrupt Ability cancel casting bar");
				EventMessageHelper.SendCombatEvent(state.getSourceOid(), state.getTargetOid(), EventMessageHelper.COMBAT_CASTING_CANCELLED, state.getAbility().getID(), -1, -1, -1);
			}
			if (state.getState() == ActivationState.COMPLETED)
				return;
		}

		state.getAbility().interrupt(state);

		// XXX do something here about adjusting the time remaining
		// XXX up or down for cast or channelled abilities
		return;
    }

//    public int CalculateTriggers(TriggerProfile.Type eventType, ArrayList<Integer> tag, int value, CombatInfo caster, CombatInfo target) {
//        if(Log.loggingDebug)
//            Log.debug("AgisAbility.CalculateTriggers  Ability "+getID()+" "+getName()+" eventType=" + eventType + " tags=" + tags + " value=" + value + " caster=" + caster + " target=" + target+" triggerProfiles="+triggerProfiles.size());
//        if(Log.loggingDebug)
//            Log.debug("AgisAbility.CalculateTriggers "+caster.getProperty("race")+" "+caster.getProperty("aspect"));
//        int race = 0;
//        if(caster.getProperty("race") != null) {
//            race = caster.getIntProperty("race");
//        }else {
//            try {
//                race = (int)EnginePlugin.getObjectProperty(caster.getOid(), WorldManagerClient.NAMESPACE, "race");
//                caster.setProperty("race", race);
//            } catch (Exception e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
//        }
//        int _class = 0;
//        if(caster.getProperty("aspect") != null)
//            _class = caster.getIntProperty("aspect");
//        if(Log.loggingDebug)
//            Log.debug("AgisAbility.CalculateTriggers race="+race+" calss="+_class);
//        boolean dealt = true;
////        if (state.getTargetOid().equals(caster.getOid()))
////            dealt = true;
//        for (TriggerProfile tp : triggerProfiles) {
//            if(Log.loggingDebug)
//                Log.debug("AgisAbility.CalculateTriggers TriggerProfile="+tp);
//            //if (taken)
//            if (dealt)
//                value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.DEALT, value, caster, target);
//            else
//                value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);
//
//            if (dealt && target!=null && caster.getOid().equals(target.getOid()))
//                value = tp.Execute(eventType, tag, race, _class, TriggerProfile.ActionType.RECEIVED, value, caster, target);
//            if(Log.loggingDebug)
//                Log.debug("AgisAbility.CalculateTriggers TriggerProfile END");
//        }
//        if(Log.loggingDebug)
//            Log.debug("AgisAbility.CalculateTriggers END value="+value);
//        return value;
//    }
//
//    ArrayList<TriggerProfile> triggerProfiles = new ArrayList<TriggerProfile>();
//
//    public void addTriggerProfile(TriggerProfile tp) {
//        triggerProfiles.add(tp);
//    }
	public enum ActivationState {
		INIT, ACTIVATING, CHANNELLING, ACTIVATED, COMPLETED, CANCELLED, INTERRUPTED, FAILED, AOE_LOCATION, ABILITY_PULSE
	}

	public AgisEffect getResultVal(String result, boolean caster) {
		if (Log.loggingDebug)
			Log.debug("RESULT: getting effect for result: " + result);

		return null;
	}

    /**
     * Set effect tags that must be in effects on caster
     * @param tags
     */
    public void setEffectTagsOnCaster(ArrayList<Integer> tags){
        effect_tags_on_caster = tags;
    }

    /**
     * Get effect tags that must be in effects on caster
     * @return
     */
    public ArrayList<Integer> getEffectTagsOnCaster(){
        return effect_tags_on_caster;
    }

    /**
     * List of effect tags that must be in effects on caster
     */
    protected ArrayList<Integer> effect_tags_on_caster = new ArrayList<Integer>();

    /**
     * Set effect tags that must be in effects on caster for pulse
     * @param tags
     */
    public void setPulseEffectTagsOnCaster(ArrayList<Integer> tags){
        pulse_effect_tags_on_caster = tags;
    }

    /**
     * Get effect tags that must be in effects on caster for pulse
     * @return
     */
    public ArrayList<Integer> getPulseEffectTagsOnCaster(){
        return pulse_effect_tags_on_caster;
    }

    /**
     * List of effect tags that must be in effects on caster for pulse
     */
    protected ArrayList<Integer> pulse_effect_tags_on_caster = new ArrayList<Integer>();

    /**
     * Set effect tags that must be in effects on target
     * @param tags
     */
    public void setEffectTagsOnTarget(ArrayList<Integer> tags){
        effect_tags_on_target = tags;
    }

    /**
     * Get effect tags that must be in effects on target
     * @return
     */
    public ArrayList<Integer> getEffectTagsOnTarget(){
        return effect_tags_on_target;
    }

    /**
     * List of effect tags that must be in effects on target
     */
    protected ArrayList<Integer> effect_tags_on_target = new ArrayList<Integer>();

    /**
     * Set effect tags that must be in effects on target for pulse
     * @param tags
     */
    public void setPulseEffectTagsOnTarget(ArrayList<Integer> tags){
        pulse_effect_tags_on_target = tags;
    }

    /**
     * Get effect tags that must be in effects on target for pulse
     * @return
     */
    public ArrayList<Integer> getPulseEffectTagsOnTarget(){
        return pulse_effect_tags_on_target;
    }

    /**
     * List of effect tags that must be in effects on target for pulse
     */
    protected ArrayList<Integer> pulse_effect_tags_on_target = new ArrayList<Integer>();


    /**
     * Set effect tags that must not be in effects on caster
     * @param tags
     */
    public void setEffectTagsNotOnCaster(ArrayList<Integer> tags){
        effect_tags_not_on_caster = tags;
    }

    /**
     * Get effect tags that must not be in effects on caster
     * @return
     */
    public ArrayList<Integer> getEffectTagsNotOnCaster(){
        return effect_tags_not_on_caster;
    }

    /**
     * List of effect tags that must be in effects on caster
     */
    protected ArrayList<Integer> effect_tags_not_on_caster = new ArrayList<Integer>();

    /**
     * Set effect tags that must not be in effects on caster for pulse
     * @param tags
     */
    public void setPulseEffectTagsNotOnCaster(ArrayList<Integer> tags){
        pulse_effect_tags_not_on_caster = tags;
    }

    /**
     * Get effect tags that must not be in effects on caster for pulse
     * @return
     */
    public ArrayList<Integer> getPulseEffectTagsNotOnCaster(){
        return pulse_effect_tags_not_on_caster;
    }

    /**
     * List of effect tags that must be in effects on caster for pulse
     */
    protected ArrayList<Integer> pulse_effect_tags_not_on_caster = new ArrayList<Integer>();

    /**
     * Set effect tags that must not be in effects on target
     * @param tags
     */
    public void setEffectTagsNotOnTarget(ArrayList<Integer> tags){
        effect_tags_not_on_target = tags;
    }

    /**
     * Get effect tags that must not be in effects on target
     * @return
     */
    public ArrayList<Integer> getEffectTagsNotOnTarget(){
        return effect_tags_not_on_target;
    }

    /**
     * List of effect tags that must not be in effects on target
     */
    protected ArrayList<Integer> effect_tags_not_on_target = new ArrayList<Integer>();
    /**
     * Set effect tags that must not be in effects on target for pulse
     * @param tags
     */
    public void setPulseEffectTagsNotOnTarget(ArrayList<Integer> tags){
        pulse_effect_tags_not_on_target = tags;
    }

    /**
     * Get effect tags that must not be in effects on target for pulse
     * @return
     */
    public ArrayList<Integer> getPulseEffectTagsNotOnTarget(){
        return pulse_effect_tags_not_on_target;
    }

    /**
     * List of effect tags that must not be in effects on target for pulse
     */
    protected ArrayList<Integer> pulse_effect_tags_not_on_target = new ArrayList<Integer>();

    protected int aoe_targets_count = 5;
	/***
	 * Set the maximum number of targets on which the effects will be applied 
	 * @param value
	 */
	public void setAoeTargetsCount(int value) {
		aoe_targets_count = value;
	}
	/***
	 * Get the maximum number of targets on which the effects will be applied 
	 */
	public int getAoeTargtetsCount() {
		return aoe_targets_count;
	}

	protected int aoe_targets_count_type = 0;
	/***
	 * Set the type of searche 
	 * @param value
	 */
	public void setAoeTargetsCountType(int value) {
		aoe_targets_count_type = value;
	}

	public int getAoeTargtetsCountType() {
		return aoe_targets_count_type;
	}
	
	//protected boolean usedAbility = false;
	protected ArrayList<CombatInfo> targets = new ArrayList<CombatInfo>();
	protected ArrayList<CombatBuildingTarget> buildingTargets = new ArrayList<CombatBuildingTarget>();
	protected HashMap<OID,Float> targetsWithDistance = new HashMap<OID,Float>();

	public void setPredictionMode(int val) {
		prediction=val;
	}
	protected int prediction = 0;
	
	public boolean skipChecks = false;
	public boolean getSkipChecks() { return skipChecks;}
	
	public void setSkipChecks(boolean value) {
		Log.debug("setSkipChecks "+value);
		skipChecks = value;
	}
    
	public boolean stealthReduce = true;
	public boolean getStealthReduce() { return stealthReduce;}
	public void setStealthReduce(boolean value) { stealthReduce = value;}

    /**
     * Set amount that will reduce stealth
     * @param value
     */
    public void setStealthReductionAmount(int value){
        stealthReductionAmount = value;
    }
    /**
     * Get amount that will reduce stealth
     * @return
     */
    public int getStealthReductionAmount(){
        return stealthReductionAmount;
    }
    /**
     *
     */
    protected int stealthReductionAmount = 0;

    /**
     * Set percentage that will reduce stealth
     * @param value
     */
    public void setStealthReductionPercentage(float value){
        stealthReductionPercentage = value;
    }
    /**
     * Get percentage that will reduce stealth
     * @return
     */
    public float getStealthReductionPercentage(){
        return stealthReductionPercentage;
    }
    /**
     *
     */
    protected float stealthReductionPercentage = 0;


    /**
     * Set time that after reduced stealth wil be lifted
     * @param value
     */
    public void setStealthReductionTimeout(long value){
        stealthReductionTimeout = value;
    }
    /**
     * Get time that after reduced stealth wil be lifted
     * @return
     */
    public long getStealthReductionTimeout(){
        return stealthReductionTimeout;
    }
    /**
     *
     */
    protected long stealthReductionTimeout = 0;

    /**
     * Set miss Chance
     * @param missChance
     */
    public void setMissChance(float missChance) {
        this.missChance = missChance;
    }

    /**
     * Get miss chance
     * @return
     */
    public float getMissChance() {
        return missChance;
    }

    protected float missChance = 5f;

    /**
     * Get skill up= chance
     * @return
     */
    public int getSkillUpChance() {
        return skillUpChance;
    }

    /**
     * Set skill up chance
     * @param skillUpChance
     */
    public void setSkillUpChance(int skillUpChance) {
        this.skillUpChance = skillUpChance;
    }

    protected int skillUpChance = 80;
    /**
     * From what I can tell, this class is pointless. - Andrew
     *
     */
	public static class Entry {
		public Entry() {
		}

		public Entry(String abilityName, String icon, String category) {
			setAbilityName(abilityName);
			setIcon(icon);
			setCategory(category);
		}

		public Entry(AgisAbility ability, String category) {
			setAbilityName(ability.getName());
			setIcon(ability.getIcon());
			setCategory(category);
		}

		public String getAbilityName() {
			return abilityName;
		}

		public void setAbilityName(String abilityName) {
			this.abilityName = abilityName;
		}

		protected String abilityName;

		public int getAbilityID() {
			return abilityID;
		}

		public void setAbilityID(int abilityID) {
			this.abilityID = abilityID;
		}

		protected int abilityID;

		public String getIcon() {
			return icon;
		}

		public void setIcon(String icon) {
			this.icon = icon;
		}

		protected String icon;

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		protected String category;

		public AgisAbility getAbility() {
			return Agis.AbilityManager.get(abilityID);
		}
	}
    
    
    /**
     * Not currently used - needs to be implemented in the core AgisAbility class in the near future.
     * @author Andrew
     *
     */
    private class PlayerAngle {
    	
    	/**
    	 * Must be between 0 and 360
    	 */
    	private float facing;
    	
    	protected PlayerAngle(float angle) {
    		while (angle < 0)
    			angle += 360;
    		while (angle >= 360)
    			angle -= 360;
    		this.facing = angle;
    	}
    	
    	protected boolean is_within(float min, float max, boolean anticlockwise) {
    		if (min < max) {
	    		if (anticlockwise) {
	    			return (this.facing < min || this.facing > max);
	    		} else {
	    			return (this.facing > min && this.facing < max);
	    		}
    		} else {
    			if (anticlockwise) {
	    			return (this.facing < min && this.facing > max);
	    		} else {
	    			return (this.facing > min || this.facing < max);
	    		}
    		}
    	}
    }
    
    /**
     * -Experience system component - Not currently used
     * 
     * This variable is used for setting up how much experience each successful
     * use of an ability is.
     */
    int exp_per_use = 0;

    /**
     * -Experience system component - Not currently used
     * 
     * Returns the amount of experience should be gained by successful use of
     * this ability.
     */
    public int getExperiencePerUse() {
        return exp_per_use;
    }

    /**
     * -Experience system component - Not currently used
     * 
     * Sets the amount of experience should be gained by successful use of this
     * ability.
     */
    public void setExperiencePerUse(int xp) {
        exp_per_use = xp;
    }

    LevelingMap lm = new LevelingMap();

    public void setLevelingMap(LevelingMap lm) {
        this.lm = lm;
    }

    public LevelingMap getLevelingMap() {
        return this.lm;
    }

    int exp_max = 100;

    /**
     * -Experience system component - Not currently used
     * 
     * Returns the default max experience that will be needed before the ability
     * gains a level.
     */
    public int getBaseExpThreshold() {
        return exp_max;
    }

    /**
     * -Experience system component - Not currently used
     * 
     * Sets the default max experience that will be needed before the ability
     * gains a level.
     */
    public void setBaseExpThreshold(int max) {
        exp_max = max;
    }

    int rank_max = 3;

    /**
     * -Experience system component - Not currently used
     * 
     * Returns the max rank that an ability may achieve.
     */
    public int getMaxRank() {
        return rank_max;
    }

    /**
     * -Experience system component - Not currently used
     * 
     * Sets the max rank that an ability may achieve.
     */
    public void setMaxRank(int rank) {
        rank_max = rank;
    }
    
    
    /**
     * the function returns whether the ability is interruptible
     * @return
     */
    public boolean isInterruptible() {
    	return interruptible;
    }
    
    /**
     * function to set ability is interruptible
     * @param val
     */
    public void setInterruptible(boolean val) {
    	interruptible =val;
    }
    
    boolean interruptible =false;
    
    /**
     * the function returns interruption chance
     * @return
     */
    public float getInterruptChance() {
    	return interrupt_chance;
    }
    
    /**
     * function to set ability interruption chance
     * @param val
     */
    public void setInterruptChance(float val) {
    	interrupt_chance =val;
    }
    
    
    float interrupt_chance = 100f;
    
    
    public boolean getUseEnterCombatState() {
    	return useEnterCombatState;
    }

    public void setUseEnterCombatState(boolean value) {
    	useEnterCombatState = value;
    }
    
    boolean useEnterCombatState = false;
    
    // Ability Result Values
    public static final int RESULT_HIT = 1;
    public static final int RESULT_CRITICAL = 2;
    public static final int RESULT_MISSED = 3;
    public static final int RESULT_PARRIED = 4;
    public static final int RESULT_DODGED = 5;
    public static final int RESULT_BLOCKED = 6;
    public static final int RESULT_EVADED = 10;
    public static final int RESULT_IMMUNE = 11;



}



