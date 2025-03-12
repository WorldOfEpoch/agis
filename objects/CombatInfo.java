package atavism.agis.objects;

import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.telemetry.Prometheus;
import atavism.server.math.Point;
import atavism.server.messages.*;
import atavism.agis.core.*;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.plugins.*;
import atavism.msgsys.NoRecipientsException;
import io.micrometer.core.instrument.Timer;

import java.beans.*;
import java.time.Duration;
import java.util.*;
//import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.*;
import java.util.function.Function;

import java.util.Map.Entry;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toMap;


/**
 * Information related to the combat system. Any object that wants to be involved
 * in combat needs one of these. Contains the SkillInfo, list of Abilities, ActionBar settings,
 * Cooldowns and other combat properties.
 */
public class CombatInfo extends Entity implements Runnable, Cooldown.CooldownObject {
	public CombatInfo() {
		super();
		setNamespace(Namespace.COMBAT);
	}

	public CombatInfo(OID objOid, int id) {
		super(objOid);
		setNamespace(Namespace.COMBAT);
		setState("");
		this.id = id;
	}

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() +":"+getID()+ "]";
    }

    public ObjectType getType() {
        return ObjectTypes.combatInfo;
    }
    
    public void overrideAutoAttackAbility(int abilityID) {
    	//Log.debug("AUTO: setting auto attack ability to: " + abilityID);
    	setProperty(COMBAT_PROP_AUTOATTACK_ABILITY, abilityID);
    }
    
    public void resetAutoAttackAbility() {
    	//Log.debug("AUTO: setting auto attack back to: " + getProperty(COMBAT_PROP_AUTOATTACK_BASE));
    	setProperty(COMBAT_PROP_AUTOATTACK_ABILITY, getProperty(COMBAT_PROP_AUTOATTACK_BASE));
    }
    
    public int getAutoAttackAbility() {
    	return (Integer)getProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY);
    }

    public void setAutoAttack(OID newTarget) {
    	if (Log.loggingDebug)  Log.debug("AUTO: "+getOid()+" getting new target "+newTarget);
        OID oldTarget = target;
        
        if (oldTarget != null && oldTarget.equals(newTarget)) {
            Log.debug("AUTO: old target = new target");
            return;
        }
        lock.lock();
		try {
			target = newTarget;
			if(Log.loggingDebug)
					Log.debug("AUTO: got new target: " + target);
			if (oldTarget == null) {
				setCombatState(true);
				if (!scheduled) {
					Log.debug("AUTO: scheduling auto attack for new target");
					schedule(/*getAttackDelay()*/500);
				}
			}
			else {
				CombatPlugin.removeAttacker(oldTarget, getOwnerOid());
			}
			if (target == null) {
				//setCombatState(false);
				Log.debug("AUTO: new target is null");
			}
			else {
				CombatPlugin.addAttacker(target, getOwnerOid());
				//if (!recovering) {
				//	CombatPlugin.resolveAutoAttack(this);
				//}
			}
			Log.debug("AUTO: finished set auto attack");
		}
		finally {
			lock.unlock();
		}
	}
    public void stopAutoAttack() {
    	if (Log.loggingDebug) Log.debug("AUTO: "+getOid()+" Stop autoAttack target="+target);
		if(target == null){
			return;
		}
        lock.lock();
        try {
            if (target != null) {
                CombatPlugin.removeAttacker(target, getOwnerOid());
            }
            //setCombatState(false);
            target = null;
         //   cancel();
        }
        finally {
            lock.unlock();
        }
        Long petOwner = (Long) getProperty("petOwner");
        if (petOwner != null) {
            AgisMobClient.petTargetLost(getOid());
        }
        if (Log.loggingDebug)  Log.debug("AUTO: "+getOid()+" Stop autoAttack End");
    }
    public OID getAutoAttackTarget() {
		return target;
	}
	protected volatile OID target = null;
	boolean scheduled = false;

    public long getAttackDelay() {
    	//Log.debug("AUTO: Getting attack delay");
        return statGetCurrentValue(CombatPlugin.ATTACK_SPEED_STAT);
    }
  //  protected ScheduledFuture scheduledExecutioner;
    protected void schedule(long delay) {
		if (Log.loggingDebug)
			Log.debug("CombatInfo.schedule: scheduling obj=" + this + " for delay=" + delay);
//		scheduledExecutioner = 
				Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);
		scheduled = true;
	}
	protected void cancel() {
	/*	log.error("ZBDS CombatInfo cancel scheduledExecutioner "+scheduledExecutioner);
		if (scheduledExecutioner!=null) {
			scheduledExecutioner.cancel(true);
			scheduledExecutioner = null;
			*/
			Engine.getExecutor().remove(this);
		//	}
		scheduled = false;
	}
	
	/**
	 * Called when an ability fails, this function checks to see if it was the auto attack and whether this is a mob.
	 * If both are true it will reset the auto attack delay so it tries to attack again much sooner
	 * @param failedAbilityID
	 */
	public void abilityFailed(int failedAbilityID) {
		int abilityID = (Integer)getProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY);
		if (isMob() && abilityID == failedAbilityID) {
			abilityFailed = true;
		}
	}

	public void run() {
	    if (dead()) {
            target = null;
            scheduled = false;
	        return;
	    }
	    long tStart = System.nanoTime();
		lock.lock();
		try {
			Log.debug("AUTO: combat info - run "+getOid()+" target="+target);
			int abilityID = (Integer)getProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY);
			if (target == null) {
				scheduled = false;
				Log.debug("AUTO: combat info - run stop");
			}
			else if (abilityID > 0 && getAttackDelay() > 0) {
				// Try get entity
				Entity e = EntityManager.getEntityByNamespace(target, Namespace.COMBAT);
				if (e == null) {
					if (Log.loggingDebug)log.debug("AUTO: combat info is null for target="+target);
					target = null;
					scheduled = false;
					Log.debug("AUTO: combat info - run stop");
				} else {
					Log.debug("AUTO: About to resolve auto attack");
					CombatPlugin.resolveAutoAttack(this);
					combatTimeout = System.currentTimeMillis() + CombatPlugin.COMBAT_TIMEOUT;
					
					//Log.debug("AUTO: About to schedule next auto attack");
					if (isMob()) {
						if (abilityFailed) {
							abilityFailed = false;
							schedule(250);
						} else {
							Random rand = new Random();
							// Add slight variance to attack delay to prevent all mobs attacking at exactly the same time and then freezing
							long delay = getAttackDelay() - 100;
							delay += rand.nextInt(200);
							schedule(delay);
						}
					} else {
						schedule(getAttackDelay());
					}
					
					Log.debug("AUTO: scheduled next auto attack");
				}
				
			} else {
				if (Log.loggingDebug)log.debug("AUTO: combat info abilityID="+abilityID +" or attack_speed "+getAttackDelay()+" is 0 for target="+target);
				target = null;
				scheduled = false;
			}
		}
		catch (Exception e) {
			Log.exception("CombatInfo.run: got exception", e);
			Log.debug("CombatInfo.run: got exception: " + e);
		}
		finally {
			lock.unlock();
			Log.debug("AUTO: finished combat info - run");
            Timer.builder("combat_info_run").register(Prometheus.registry())
                    .record(Duration.ofNanos(System.nanoTime() - tStart));
		}
	}
	
	public int getID() {
		return id;
	}
	public void setID(int id) {
		this.id = id;
	}
	private int id;
	
	/**
	 * Category control
	 * Each category has its own collection of abilities.
	 */
	public int getCurrentCategory() {
		return currentCategory;
	}
	public void setCurrentCategory(int category) {
		if(Log.loggingDebug)
			Log.debug("QSI: setting current category to: " + category + " from: " + currentCategory);
		this.currentCategory = category;
	}
	public boolean categoryUpdated(int category) {
		boolean createSkills = false;
		if (!skills.containsKey(category)) {
			createSkills = true;
		}
		this.currentCategory = category;
		Engine.getPersistenceManager().setDirty(this);
		return createSkills;
	}
	protected int currentCategory;
	
	/*
	 * Skill Functions - used to get and set the SkillInfo for the object
	 */
	
	public SkillInfo getCurrentSkillInfo() {
        return getSkillInfo(currentCategory);
    }
	public void setCurrentSkillInfo(SkillInfo skills) {
        this.skills.put(currentCategory, skills);
	}
	public HashMap<Integer, SkillInfo> getSkillInfos() {
        return new HashMap<Integer, SkillInfo>(skills);
    }
	public void setSkillInfos(HashMap<Integer, SkillInfo> skills) {
        this.skills = new ConcurrentHashMap<>(skills);
	}
	public SkillInfo getSkillInfo(int category) {
	    return skills.computeIfAbsent(category, new Function<Integer, SkillInfo>() {
            @Override
            public SkillInfo apply(Integer t) {
                return new SkillInfo(category);
            }
	    });
    }
	protected transient ConcurrentHashMap<Integer, SkillInfo> skills = new ConcurrentHashMap<>();
	
	/*
	 * Ability functions - used to get and set the list of abilities the object knows
	 */

	public Collection<Integer> getCurrentAbilities() {
        return getAbilities(currentCategory);
    }
	public void setCurrentAbilities(Collection<Integer> abilities) {
			Set<Integer> set = ConcurrentHashMap.newKeySet();
			set.addAll(abilities);
			this.abilities.put(currentCategory,  set);
	}
	public Map<Integer, Collection<Integer>> getAbilities() {
		return abilities.entrySet().stream().collect(toMap(Entry::getKey, e -> new ArrayList<>(e.getValue())));
    }
	public void setAbilities(HashMap<Integer, Collection<Integer>> abilities) {
		this.abilities = abilities.entrySet().stream()
				.collect(toConcurrentMap(Entry::getKey, e -> {
					Set<Integer> set = ConcurrentHashMap.newKeySet();
					set.addAll(e.getValue());
					return set;
				}));
	}
	public Collection<Integer> getAbilities(int category) {
			return abilities.computeIfAbsent(category, new Function<Integer, Collection<Integer>>() {
				@Override
					public Collection<Integer> apply(Integer t) {
						return ConcurrentHashMap.newKeySet();
            }
        });
	}
	protected transient Map<Integer, Collection<Integer>> abilities = new ConcurrentHashMap<>();
	
	/*
	 * Action functions - used to get and set the list of actions on the players action bar
	 */
	
	public void addAbilityAction(int abilityNum) {
    	ArrayList<String> currentActions = getCurrentActionsOnCurrentBar();
    	Log.debug("add Abiliti to bar");
		for (int i = 0; i < 10; i++) {
			if (currentActions.get(i).equals("")) {
				if(Log.loggingDebug)
					Log.debug("ACTION: adding new action to spot " + i);
				currentActions.set(i, "a" + abilityNum);
				return;
			}
		}
    }
    public void removeAbilityAction(int abilityNum) {
    	ArrayList<String> currentActions = getCurrentActionsOnCurrentBar();
		for (int i = 0; i < currentActions.size(); i++) {
			if (currentActions.get(i).equals("a" + abilityNum)) {
				if(Log.loggingDebug)
					Log.debug("ACTION: removing action at spot " + i);
				currentActions.set(i, "");
				return;
			}
		}
    }
    
    public ArrayList<String> getCurrentActionsOnCurrentBar() {
        lock.lock();
        try {
        	while (getActions(currentCategory).size() <= currentActionBar) {
        		this.actions.get(currentCategory).add(new ArrayList<String>());
        	}
            return getActions(currentCategory).get(currentActionBar);
        } finally {
            lock.unlock();
        }
    }
	public ArrayList<ArrayList<String>> getCurrentActions() {
        return getActions(currentCategory);
    }
	public void setCurrentActionsOnCurrentBar(ArrayList<String> actions) {
        lock.lock();
        try {
        	while (getActions(currentCategory).size() <= currentActionBar) {
        		this.actions.get(currentCategory).add(new ArrayList<String>());
        	}
        	this.actions.get(currentCategory).set(currentActionBar, actions);
        	
        } finally {
            lock.unlock();
        }
        Engine.getPersistenceManager().setDirty(this);
	}
	public void setCurrentActions(ArrayList<ArrayList<String>> actions) {
        this.actions.put(currentCategory, actions);
        Engine.getPersistenceManager().setDirty(this);
	}
	public HashMap<Integer, ArrayList<ArrayList<String>>> getActions() {
        return new HashMap<Integer, ArrayList<ArrayList<String>>>(actions);
    }
	public void setActions(HashMap<Integer, ArrayList<ArrayList<String>>> actions) {
        this.actions = new ConcurrentHashMap<>(actions);
        Engine.getPersistenceManager().setDirty(this);
	}
	public ArrayList<ArrayList<String>> getActions(int category) {
        return actions.computeIfAbsent(category, new Function<Integer, ArrayList<ArrayList<String>>>() {
            @Override
            public ArrayList<ArrayList<String>> apply(Integer t) {
                return new ArrayList<ArrayList<String>>();
            }
        });
    }
	protected ConcurrentHashMap<Integer, ArrayList<ArrayList<String>>> actions = new ConcurrentHashMap<>();
	
	public void setCurrentActionBar(int currentActionBar) {
		this.currentActionBar = currentActionBar;
	}
	public int getCurrentActionBar() {
		return currentActionBar;
	}
	protected int currentActionBar = 0;

	/*
	 * Cooldown functions - used to get and set the cooldown state of the object
	 */
	
	public void addCooldownState(Cooldown.State state) {
        cooldownMap.put(state.getID(), state);
    }
	public void removeCooldownState(Cooldown.State state) {
		cooldownMap.remove(state.getID());
	}
	public Cooldown.State getCooldownState(String id) {
        return cooldownMap.get(id);
    }
	public Map<String, Cooldown.State> getCooldownMap() { return cooldownMap; }
	protected ConcurrentHashMap<String, Cooldown.State> cooldownMap = new ConcurrentHashMap<String, Cooldown.State>();

	public void setCurrentAction(AgisAbilityState action) { currentAction = action; }
	public AgisAbilityState getCurrentAction() { return currentAction; }
	protected transient AgisAbilityState currentAction;

	//public void currentRunAbilities(HashMap<Integer,AgisAbilityState> actions) { currentRunAbilities = actions; }
	public Map<Integer,AgisAbilityState> currentRunAbilities() { return currentRunAbilities; }
	protected transient Map<Integer,AgisAbilityState> currentRunAbilities = new ConcurrentHashMap<>();

	/*
	public void addActiveAbility(AgisAbilityState abilityState) {
      //  lock.lock();
        try {
            activeAbilities.add(abilityState);
        } finally {
           // lock.unlock();
        }
	}
	
	public void removeActiveAbility(AgisAbilityState abilityState) {
       // lock.lock();
        try {
            activeAbilities.remove(abilityState);
        } finally {
          //  lock.unlock();
        }
	}
	//protected transient Set<AgisAbilityState> activeAbilities = new HashSet<AgisAbilityState>();
	protected transient List<AgisAbilityState> activeAbilities = new CopyOnWriteArrayList<AgisAbilityState>();
*/
	/*
	 * Effect Functions
	 */
	
	public Set<EffectState> getCurrentEffects() {
        return getEffects(currentCategory);
    }
	public void addEffect(EffectState effectState) {
		log.debug("CobatInfo.addEffect start ");
        lock.lock();
        try {
            if (Log.loggingDebug)
                log.debug("CobatInfo.addEffect effects="+getEffects(currentCategory)+" effectState="+effectState);
        	getEffects(currentCategory).add(effectState);
            if (Log.loggingDebug)
                log.debug("CobatInfo.addEffect effects="+getEffects(currentCategory));
             } catch (Exception e) {
			Log.exception("addEffect: Exception ", e);
        }
        finally {
            lock.unlock();
        }
    	log.debug("CobatInfo.addEffect send update ");
        updateEffectsProperty();
    	log.debug("CobatInfo.addEffect End ");
    }
	public void removeEffect(EffectState effectState) {
		
		log.debug("CobatInfo.removeEffect start ");
        lock.lock();
        try {
        	HashSet<EffectState> currentEffects = (HashSet<EffectState>) getEffects(currentCategory);
        	currentEffects.remove(effectState);
        	effects.put(currentCategory, currentEffects);
        } finally {
            lock.unlock();
        }
        
        log.debug("CobatInfo.removeEffect update effects");
        updateEffectsProperty();
        log.debug("CobatInfo.removeEffect End");
    }
	public HashMap<Integer, HashSet<EffectState>> getEffects() {
        return new HashMap<Integer, HashSet<EffectState>>(effects);
    }
    public void setEffects(HashMap<Integer, HashSet<EffectState>> effects) {
        this.effects = new ConcurrentHashMap<>(effects);
    }	
	public Set<EffectState> getEffects(int category) {
        return effects.computeIfAbsent(category, new Function<Integer, HashSet<EffectState>>() {
            @Override
            public HashSet<EffectState> apply(Integer t) {
                return new HashSet<EffectState>();
            }
        });
    }
	protected ConcurrentHashMap<Integer, HashSet<EffectState>> effects = new ConcurrentHashMap<>();
	
	public void updateEffectsProperty() {
    	LinkedList<String> effectsProp = new LinkedList<String>();
    	 Set<EffectState> effects = new HashSet<EffectState>(getEffects(currentCategory)); 
    	Log.debug("updateEffectsProperty: effects="+effects);
    	for (EffectState eState : effects) {
    		if (eState != null) {
    			if(eState.getEffect() != null) { 
    				String effectData = eState.getEffectID() + "," 
		    				+ eState.getCurrentStack() + "," 
		    				+ eState.getEffect().isBuff() + ","  
		    				+ eState.getEndTime() + ","  
		    				+ eState.getTimeUntilEnd() + "," 
		    				+ eState.isActive()+","
		    				+ eState.getEffect().getDuration() + ","
		    				+ eState.getEffect().isPassive() + ","
		    				+ eState.getEffect().isStackTime()+ ","
				    		+ eState.getStartTime()+","
				    		+ eState.getId();
    				if(Log.loggingDebug)
    						log.debug("CombarInfo.updateEffectsProperty "+effectData);
		    		effectsProp.add(effectData);
    			}else {
    				if(Log.loggingDebug)
    						Log.debug("COMBATINFO: EffectState not null of getEffect is null ->"+eState.getEffect());	
    	    		}
    		}else {
    			if(Log.loggingDebug)
    				Log.debug("COMBATINFO: EffectState null  ->"+eState);	
    		}
    	}
    	
    	setProperty("effects", effectsProp);
    	PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
        propMsg.setProperty("effects", effectsProp);
        Engine.getAgent().sendBroadcast(propMsg);
        Engine.getPersistenceManager().setDirty(this);
    }
	
	/* 
	 * Target tracking
	 */
	public void addFriendlyTarget(OID oid, TargetInfo info) {
    	friendlyTargets.put(oid, info);
    }
	public void removeFriendlyTarget(OID oid) {
    	friendlyTargets.remove(oid);
    }
	public Map<OID, TargetInfo> getFriendlyTargets() {
        return friendlyTargets;
    }
	public void setFriendlyTargets(HashMap<OID, TargetInfo> targets) {
        this.friendlyTargets = targets;
    }
	public void addAttackableTarget(OID oid, TargetInfo info) {
    	attackableTargets.put(oid, info);
    }
	public void removeAttackableTarget(OID oid) {
    	attackableTargets.remove(oid);
    }
	public Map<OID, TargetInfo> getAttackableTargets() {
        return attackableTargets;
    }
	public void setAttackableTargets(HashMap<OID, TargetInfo> targets) {
        this.attackableTargets = new ConcurrentHashMap<OID, TargetInfo>(targets);
    }
	
	// Lists containing the mobs/players that are currently either healing/buffable
	// and attackable. These are used by AgisAbility to determine if a target is
	// acceptable for the ability in question.
	protected transient Map<OID, TargetInfo> friendlyTargets = new ConcurrentHashMap<OID, TargetInfo>();
	protected transient Map<OID, TargetInfo> attackableTargets = new ConcurrentHashMap<OID, TargetInfo>();
	
	// Not currently used
	public void addTargetInCombat(OID oid) {
    	targetsInCombat.add(oid);
    }
	public void removeTargetInCombat(OID oid) {
    	targetsInCombat.remove(oid);
    }
	public ArrayList<OID> getTargetsInCombat() {
        return new ArrayList<OID>(targetsInCombat);
    }
	public void setTargetsInCombat(ArrayList<OID> targetsInCombat) {
	    Set<OID> tic = ConcurrentHashMap.newKeySet();
	    tic.addAll(targetsInCombat);
        this.targetsInCombat = tic;
    }
	protected Set<OID> targetsInCombat = ConcurrentHashMap.newKeySet();
	
	/**
	 * Does the CombatInfo belong to a players character.
	 * @return
	 */
	public boolean isUser() { return getBooleanProperty(COMBAT_PROP_USERFLAG); }
	public boolean isMob() { return getBooleanProperty(COMBAT_PROP_MOBFLAG); }
	public boolean attackable() { return getBooleanProperty(COMBAT_PROP_ATTACKABLE); }
	public boolean dead() { return getBooleanProperty(COMBAT_PROP_DEADSTATE); }
	public int aspect() { return getIntProperty(COMBAT_PROP_ASPECT); }
	public String team() { return getStringProperty(COMBAT_PROP_TEAM); }
	public void team(String newTeam) {
		setProperty(COMBAT_PROP_TEAM, newTeam);
		PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
		propMsg.setProperty(COMBAT_PROP_TEAM, newTeam);
		Engine.getAgent().sendBroadcast(propMsg);
	}
	public OID getOwnerOid() { return getOid(); }
    public boolean inCombat() { return getBooleanProperty(COMBAT_PROP_COMBATSTATE); }

    public void setCombatState(boolean state) {
        setProperty(COMBAT_PROP_COMBATSTATE, state);
        PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
        propMsg.setProperty(COMBAT_PROP_COMBATSTATE, state);
        Engine.getAgent().sendBroadcast(propMsg);
        if (state == false) {
        	if (!dead()) {
        		setProperty(COMBAT_TAG_OWNER, null);
        	}
        	combatTimeout = -1;
        	
        	setProperty(COMBAT_PROP_WEAPON_STATE, state);
            propMsg = new PropertyMessage(getOwnerOid());
            propMsg.setProperty(COMBAT_PROP_WEAPON_STATE, true);
            Engine.getPersistenceManager().setDirty(this);
        } else {
        	combatTimeout = System.currentTimeMillis() + CombatPlugin.COMBAT_TIMEOUT;
        	propMsg = new PropertyMessage(getOwnerOid());
            propMsg.setProperty(COMBAT_PROP_WEAPON_STATE, false);
            Engine.getPersistenceManager().setDirty(this);
        }
    }
    
    public void setCombatTimeout(long timeInMillis) {
    	this.combatTimeout = timeInMillis;
    }
    public long getCombatTimeout() {
    	return combatTimeout;
    }
    long combatTimeout = -1;

    public void setDeadState(boolean state) {
		setProperty(COMBAT_PROP_DEADSTATE, state);
        Engine.getPersistenceManager().setDirty(this);
		PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
        propMsg.setProperty(COMBAT_PROP_DEADSTATE, state);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public String getState() {
    	return getStringProperty(COMBAT_PROP_STATE); 
    }
    
    public void setState(String state) {
    	setProperty(COMBAT_PROP_STATE, state);
    	PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
        propMsg.setProperty(COMBAT_PROP_STATE, state);
        Engine.getPersistenceManager().setDirty(this);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public void clearState(String state) {
    	String currentState = (String) getProperty(COMBAT_PROP_STATE);
    	if (currentState != null && currentState.equals(state)) {
    		setProperty(COMBAT_PROP_STATE, "");
    		PropertyMessage propMsg = new PropertyMessage(getOwnerOid());
            propMsg.setProperty(COMBAT_PROP_STATE, "");
            Engine.getPersistenceManager().setDirty(this);
            Engine.getAgent().sendBroadcast(propMsg);
    	}
    }

    public void sendStatusUpdate() {
    }
    
    public int getRespawnInstance() {
    	return respawnInstance;
    }
    public void setRespawnInstance(int respawnInstance) {
    	this.respawnInstance = respawnInstance;
    }
    protected int respawnInstance = -1;
    
    public Point getRespawnPosition() {
    	return respawnPosition;
    }
    public void setRespawnPosition(Point respawnPosition) {
    	this.respawnPosition = respawnPosition;
    }
    protected Point respawnPosition;

    /**
     * Not currently used.
     * @return
     */
    public InterpolatedWorldNode getWorldNode() { return node; }
    public void setWorldNode(InterpolatedWorldNode node) { this.node = node; }
    InterpolatedWorldNode node;
    
    protected transient boolean abilityFailed = false;

    /**
     * Modifies the base value of the specified stat. It is highly recommended that 
     * statAddModifier is used instead unless reversing the modification is not needed.
     * @param statName
     * @param delta
     */
    public void statModifyBaseValue(String statName, int delta) {
		if(delta == 0)
			return;
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				Log.warn("CombatInfo.statModifyBaseValue: statName=" + statName + " does not exist for this=" + this);
				return;
			}
			//int v = stat.getCurrentValue();
			stat.modifyBaseValue(delta);
			AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
			statDef.update(stat, this);
			statSendUpdate(false);
			Engine.getPersistenceManager().setDirty(this);
			if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
				try {
					//	long start = System.nanoTime();
						EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statModifyBaseValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statModifyBaseValue "+statName,e);
				}
				try {
					//	long start = System.nanoTime();
						//EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statModifyBaseValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statModifyBaseValue "+statName,e);
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

    /**
     * Sets the base value of the specified stat. It is highly recommended that statAddModifier
     * is used instead unless reversing the change is not needed.
     * @param statName
     * @param value
     */
    public void statSetBaseValue(String statName, int value) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				Log.warn("CombatInfo.statSetBaseValue: statName=" + statName + " does not exist for this=" + this);
				return;
			}
		//	int v = stat.getCurrentValue();
			stat.setBaseValue(value);
			AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
			if(statDef==null)
				log.error("CombatInfo.statSetBaseValue: statName=" + statName +" stat def is null");
			statDef.update(stat, this);
			statSendUpdate(false);
			Engine.getPersistenceManager().setDirty(this);
			if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
				try {
					//	long start = System.nanoTime();
						EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statSetBaseValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (NoRecipientsException e) {
					Log.debug("Set statSetBaseValue "+statName+e);
				} catch (Exception e) {
					Log.exception("Set statSetBaseValue "+statName,e);
				}
				try {
					//	long start = System.nanoTime();
						//EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statSetBaseValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statSetBaseValue "+statName,e);
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

	public void statSetMaxValue(String statName, int value) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("statSetMaxValue Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				Log.warn("CombatInfo.statSetMaxValue: statName=" + statName + " does not exist for this=" + this);
				return;
			}
			//int v = stat.getCurrentValue();
			stat.setMaxValue(value);
            AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
            statDef.update(stat, this);
            statSendUpdate(false);
            Engine.getPersistenceManager().setDirty(this);
        	if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
				try {
					//	long start = System.nanoTime();
						EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statSetMaxValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statSetMaxValue "+statName,e);
				}
				try {
					//	long start = System.nanoTime();
					//	EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statSetMaxValue "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statSetMaxValue "+statName,e);
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Adds a stat modifier to the specified stat changing the value of the stat and recording the 
	 * change in a map so it can be removed at any time.
	 * @param statName
	 * @param id
	 * @param delta
	 */
	public void statAddModifier(String statName, Object id, int delta, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				Log.warn("CombatInfo.statAddModifier: statName=" + statName + " does not exist for this=" + this);
				return;
			}
			//else {
			//	int v = stat.getCurrentValue();
				stat.addModifier(id, delta);
				AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
				if (statDef == null) {
					Log.error("CombatInfo.statAddModifier: statName=" + statName + " is not registered with CombatPlugin.");
				} else {
					statDef.update(stat, this);
					if (sendUpdate) {
						statSendUpdate(false);
					}
					Engine.getPersistenceManager().setDirty(this);
					if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
						try {
							//	long start = System.nanoTime();
								EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statAddModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statAddModifier "+statName,e);
						}
						try {
							//	long start = System.nanoTime();
							//	EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statAddModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statAddModifier "+statName,e);
						}
					}
				}
			//}
		}
		finally {
			lock.unlock();
		}
	}
	
	/**
	 * Adds a stat modifier to the specified stat changing the value of the stat by the given percent and recording the 
	 * change in a map so it can be removed at any time.
	 * @param statName
	 * @param id
	 * @param percent
	 * @param sendUpdate
	 */
	public void statAddPercentModifier(String statName, Object id, float percent, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("statAddPercentModifier Property is null statName:"+statName+" Exception:"+e);;
			}
			
			if (stat == null) {
				Log.warn("CombatInfo.statAddPercentModifier: statName=" + statName + " does not exist for this=" + this);
				return;
			} 
			//else {
				//int v = stat.getCurrentValue();
				stat.addPercentModifier(id, percent);
				AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
				if (statDef == null) {
					Log.error("CombatInfo.statAddPercentModifier: statName=" + statName + " is not registered with CombatPlugin.");
				} else {
					statDef.update(stat, this);
					if (sendUpdate) {
						statSendUpdate(false);
					}
					Engine.getPersistenceManager().setDirty(this);
					if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
						try {
							//	long start = System.nanoTime();
							EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statAddPercentModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statAddPercentModifier "+statName,e);
						}
						try {
							//	long start = System.nanoTime();
							//	EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statAddPercentModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statAddPercentModifier "+statName,e);
						}
					}
				}
			//}
		}
		finally {
			lock.unlock();
		}
	}

	public void statRemoveModifier(String statName, Object id, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				log.warn("statRemoveModifier: stat "+statName+" is null");
				return;
			}
			int v = stat.getCurrentValue();
			//Log.error("CombatInfo.statRemoveModifier: statName=" + statName + " id=" + id+" sendUpdate="+sendUpdate+" this="+this+" stat="+stat);
			if(Log.loggingDebug)
					Log.debug("STAT: removing modifier with stat: " + statName + " before and current value: " + stat.getCurrentValue());
			boolean removed =  stat.removeModifier(id);
			AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
			if (statDef == null) {
				Log.error("CombatInfo.statRemoveModifier: statName=" + statName + " is not registered with CombatPlugin. "+id);
			} else {
				if (removed) {
					statDef.update(stat, this);
				}
				if (sendUpdate) {
					statSendUpdate(false);
				}

			}
			if(removed)
				Engine.getPersistenceManager().setDirty(this);
			if(Log.loggingDebug)
					Log.debug("STAT: removed modifier from stat: " + statName + " removed="+removed+" and current value: " + stat.getCurrentValue());
			if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
				try {
					//	long start = System.nanoTime();
						EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statRemoveModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statRemoveModifier "+statName,e);
				}
				try {
					//	long start = System.nanoTime();
						//EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statRemoveModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statRemoveModifier "+statName,e);
				}
			}
		}
		finally {
			lock.unlock();
		}
	}
	
	public void statRemovePercentModifier(String statName, Object id, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			if (stat == null) {
				log.warn("statRemovePercentModifier: stat "+statName+" is null");
				return;
			}
			int v = stat.getCurrentValue();
			if(Log.loggingDebug)
				Log.debug("STAT: removing percent modifier with stat: " + statName + " before and current value: " + stat.getCurrentValue());
			boolean removed = stat.removePercentModifier(id);
			AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
			if (statDef == null) {
				Log.error("CombatInfo.statRemovePercentModifier: statName=" + statName + " is not registered with CombatPlugin. "+id);
			} else {
				if (removed) {
					statDef.update(stat, this);
				}
					if (sendUpdate) {
						statSendUpdate(false);
					}

			}
			if(removed)
					Engine.getPersistenceManager().setDirty(this);


			Log.debug("STAT: removed percent modifier from stat: " + statName + " removed="+removed+" and current value: " + stat.getCurrentValue());
			if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
				try {
					//	long start = System.nanoTime();
						EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statRemovePercentModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statRemovePercentModifier "+statName,e);
				}
				try {
					//	long start = System.nanoTime();
						//EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
							//if(Log.loggingDebug) log.debug("Set statRemovePercentModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
				} catch (Exception e) {
					Log.exception("Set statRemovePercentModifier "+statName,e);
				}
			}
		}
		finally {
			lock.unlock();
		}
	}
	
	public void statReapplyModifier(String statName, Object id, int delta, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			//Log.error("CombatInfo.statReapplyModifier: statName=" + statName + " id=" + id+" delta="+delta+" sendUpdate="+sendUpdate+" this="+this+" stat="+stat);
			if (stat == null) {
				Log.debug("CombatInfo.statReapplyModifier: statName=" + statName + " does not exist for this=" + this);
			} else {
				//int v = stat.getCurrentValue();
				boolean removed = stat.removeModifier(id);
				boolean applied = stat.addModifier(id, delta);
				AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
				if (statDef == null) {
					Log.error("CombatInfo.statReapplyModifier: statName=" + statName + " is not registered with CombatPlugin.");
				} else {
					if(removed || applied) {
						statDef.update(stat, this);
					}
						if (sendUpdate) {
							statSendUpdate(false);
						}
					if(removed || applied)
						Engine.getPersistenceManager().setDirty(this);

					if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
						try {
							//	long start = System.nanoTime();
								EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statReapplyModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statReapplyModifier "+statName,e);
						}
						try {
							//	long start = System.nanoTime();
							//	EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statReapplyModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statReapplyModifier "+statName,e);
						}
					}
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

	
	public void statReapplyPercentModifier(String statName, Object id, float percent, boolean sendUpdate) {
		lock.lock();
		try {
			AgisStat stat = null;
			try {
				stat = (AgisStat) getProperty(statName);
			} catch (Exception e) {
				log.dumpStack("Property is null statName:"+statName+" Exception:"+e);;
			}
			//Log.error("CombatInfo.statReapplyModifier: statName=" + statName + " id=" + id+" delta="+delta+" sendUpdate="+sendUpdate+" this="+this+" stat="+stat);
			if (stat == null) {
				Log.debug("CombatInfo.statReapplyModifier: statName=" + statName + " does not exist for this=" + this);
			} else {
				//int v = stat.getCurrentValue();
				boolean removed = stat.removePercentModifier(id);
				boolean applied = stat.addPercentModifier(id, percent);
				AgisStatDef statDef = CombatPlugin.lookupStatDef(statName);
				if (statDef == null) {
					Log.error("CombatInfo.statReapplyModifier: statName=" + statName + " is not registered with CombatPlugin.");
				} else {
					statDef.update(stat, this);
					if (sendUpdate) {
						statSendUpdate(false);
					}
					if(removed || applied)
						Engine.getPersistenceManager().setDirty(this);
					if(CombatPlugin.WMGR_STATS != null && CombatPlugin.WMGR_STATS.contains(statName)) {
						try {
							//	long start = System.nanoTime();
								EnginePlugin.setObjectPropertyNoResponse(this.getOid(), WorldManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statReapplyModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statReapplyModifier "+statName,e);
						}
						try {
							//	long start = System.nanoTime();
							//	EnginePlugin.setObjectProperty(this.getOid(), MobManagerClient.NAMESPACE, statName, stat.getCurrentValue());
									//if(Log.loggingDebug) log.debug("Set statReapplyModifier "+statName+"=" + stat.getCurrentValue() + " for " + this.getOid() + " send to wmgr nanoseconds: " + (System.nanoTime() - start));
						} catch (Exception e) {
							Log.exception("Set statReapplyModifier "+statName,e);
						}
					}
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

	public int statGetCurrentValue(String statName) {
		try {
			AgisStat stat = (AgisStat) getProperty(statName);
			if(stat == null)
				return 0;
			return stat.getCurrentValue();
		} catch (Exception e) {
			log.exception("statGetCurrentValue: statName:" + statName + " get value", e);
		} finally {
			// System.out.println(Thread.currentThread().getName() + ": Lock released.");
			// lock.unlock();
			// done = true;
		}

		return 0;
	}
	public float statGetCurrentValueWithPrecision(String statName) {
		try {
			AgisStat stat = (AgisStat) getProperty(statName);
			if(stat == null)
				return 0F;
			return stat.getCurrentValueWithPrecision();
		} catch (Exception e) {
			log.exception("statGetCurrentValue: statName:" + statName + " get value", e);
		} finally {
			// System.out.println(Thread.currentThread().getName() + ": Lock released.");
			// lock.unlock();
			// done = true;
		}

		return 0;
	}

	public int statGetBaseValue(String statName) {
		//lock.lock();
		try {
			AgisStat stat = (AgisStat) getProperty(statName);
			if(stat == null)
				return 0;
			return stat.getBaseValue();
		}
		finally {
		//	lock.unlock();
		}
	}

	public int statGetMinValue(String statName) {
	//	lock.lock();
		try {
			AgisStat stat = (AgisStat) getProperty(statName);
			if(stat == null)
				return 0;
			return stat.getMinValue();
		}
		finally {
		//	lock.unlock();
		}
	}

	public int statGetMaxValue(String statName) {
	//	lock.lock();
		try {
			AgisStat stat = (AgisStat) getProperty(statName);
			if(stat == null)
				return 0;
			return stat.getMaxValue();
		}
		finally {
		//	lock.unlock();
		}
	}
	
	public int getExpProfile() {		
		Integer profile = (Integer) getProperty("xpProfile");			
		if (profile == null) {
			return -1;
		}
		return profile;
	}
	
	public void setExpProfile(Integer profile)
	{
		setProperty("xpProfile", profile);
	}

	public void statSendUpdate(boolean sendAll) {
		statSendUpdate(sendAll, null);
	}

	public void statSendUpdate(boolean sendAll, OID targetOid) {
		lock.lock();
		int count = 0;
		PropertyMessage propMsg = null;
	    TargetedPropertyMessage targetPropMsg = null;
		if(Log.loggingDebug)
			log.debug("STAT: statSendUpdate : sendAll="+sendAll+" for targetOid: "+targetOid);

		try {
	        if (targetOid == null) {
				propMsg = new PropertyMessage(getOwnerOid());
				propMsg.setProperty("statProfile",statProfile);
			}else {
				targetPropMsg = new TargetedPropertyMessage(targetOid, getOwnerOid());
				targetPropMsg.setProperty("statProfile",statProfile);
			}
	         for (Object value : getPropertyMap().values()) {
		    	if (value instanceof AgisStat) {
		    		AgisStat stat = (AgisStat) value;
		    		if (sendAll || stat.isDirty()) {
		    			if (stat.name.equals(CombatPlugin.HEALTH_STAT)) {
		    				if(Log.loggingDebug)
		    					log.debug("STAT: sending health stat update with value: "+stat.getCurrentValue()+" for object: "+this.name);
		    			}
		    			if (propMsg != null)
	                    	propMsg.setProperty(stat.getName(), stat.getCurrentValue());
	                	else
	                		targetPropMsg.setProperty(stat.getName(), stat.getCurrentValue());
	                	if (! sendAll)
	                		stat.setDirty(false);
	                	count++;
	                	/*try {
							if(stat.getName().equals(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED) ) {
								  long start = System.nanoTime();
							       EnginePlugin.setObjectProperty(this.getOid(), WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, stat.getCurrentValue());
								  log.debug("Set statSendUpdate movement_speed="+stat.getCurrentValue()+ " "+this.getOid()+" to wmgr nanoseconds: " + (System.nanoTime() - start));
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
						}*/
		    		}
		    	}
		    }
		
		}
		finally {
		    lock.unlock();
		}
		if(Log.loggingDebug)
			log.debug("STAT: statSendUpdate : sendAll="+sendAll+" for targetOid: "+targetOid+" count="+count);
	    if (count > 0) {
	    	Engine.getPersistenceManager().setDirty(this);
            if (propMsg != null)
                Engine.getAgent().sendBroadcast(propMsg);
            else
                Engine.getAgent().sendBroadcast(targetPropMsg);
	    }
	}
	
	/**
	 * Cycles through the vitalityStats map to see if any vitality stats should have the shift run.
	 */
	public void runCombatTick() {
		for (String statName : vitalityStats.keySet()) {
			// Has enough time elapsed for another shift update
			//Log.debug("COMBAT: comparing statUpdateTime: " + vitalityStats.get(statName) + " against current time: " + System.currentTimeMillis());
			if (vitalityStats.get(statName) < System.currentTimeMillis()) {
				VitalityStatDef statDef = (VitalityStatDef)CombatPlugin.lookupStatDef(statName);
				// Apply the shift
				applyStatShift(statName);
				// Reset the update time
				int updateInterval = statDef.getShiftInterval();
				vitalityStats.put(statName, System.currentTimeMillis() + (updateInterval * 1000));
			}
		}
		
		if (inCombat() && combatTimeout != -1) {
			// Check if users combat has timed out
			if (combatTimeout < System.currentTimeMillis()) {
				setCombatState(false);
			}
		}
	}
	
	private void applyStatShift(String statName) {
		AgisStat stat = (AgisStat) getProperty(statName);
		VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.lookupStatDef(statName);
		// Is there a shift to run?
		int shiftDirection = statDef.getShiftDirection(stat, this);
		// lock.lock();
		if (shiftDirection != 0) {
			try {
				float mod = 1f;
				if (statDef.getShiftModStat() != null && !statDef.getShiftModStat().isEmpty() && !statDef.getShiftModStat().equals("~ none ~")) {
					int modValue = statGetCurrentValue(statDef.getShiftModStat());
					mod = modValue / 100f;
				}
				int delta = stat.getShift(shiftDirection);
				if (Log.loggingDebug)
					Log.debug("SHIFT: applying shift for " + statName + " with delta: " + delta + " mod: " + mod);
				delta = Math.round(delta * mod);
				if (Log.loggingDebug)
					Log.debug("SHIFT: applying shift for " + statName + " with delta after mod: " + delta);
				lock.lock();
				stat.modifyBaseValue(delta);
				statDef.update(stat, this);
			} finally {
				lock.unlock();
			}
			statSendUpdate(false);
			Engine.getPersistenceManager().setDirty(this);
		}
	}

	public HashMap<String, Long> getVitalityStats() {
		return vitalityStats;
	}
	public void setVitalityStats(HashMap<String, Long> vitalityStats) {
		this.vitalityStats = vitalityStats;
	}
	
	public void addVitalityStat(AgisStat stat, long updateInterval) {
		if(Log.loggingDebug)
			Log.debug("COMBAT: adding vitality stat: " + stat.name+" updateInterval="+updateInterval);
		VitalityStatDef statDef = (VitalityStatDef)CombatPlugin.lookupStatDef(stat.name);
		stat.setBaseShiftValue(statDef.getShiftValue(), statDef.getReverseShiftValue(), statDef.getIsShiftPercent());
		vitalityStats.put(stat.name, System.currentTimeMillis() + (updateInterval * 1000));
	}
	HashMap<String, Long> vitalityStats = new HashMap<String, Long>();
    
	protected int deathcount = 0;
	public void setDeathCount(int deathcount){
		this.deathcount = deathcount;
	}

	public int getDeathCount(){
		return deathcount;
	}	
	
	protected boolean deathPermanently = false;
	
	public void setDeathPermanently(boolean deathPermanently){
		this.deathPermanently = deathPermanently;
	}

	public boolean getDeathPermanently(){
		return deathPermanently;
	}	
	/**
	 * Vip specytic data
	 */
	protected int vip_level = 0;
	
	public void setVipLevel(int level) {
		vip_level =level;
	}
	
	public int getVipLevel() {
		return vip_level;
	}
	
	protected long vip_expire = 0l;
	
	public void setVipExpire(long expire) {
		vip_expire = expire;
	}
	
	public long getVipExpire() {
		return vip_expire;
	}
	
	protected long vip_points = 0l;
	
	public void setVipPoints(long points) {
		vip_points = points;
	}
	
	public long getVipPoints() {
		return vip_points;
	}
	
	public void addVipExp(int points) {
		VipLevel vip = ObjectManagerClient.getVipLevel(vip_level);
		long poiontMax = vip.getPointsMax();
		if (poiontMax > 0)
			if (vip_points + points > poiontMax) {
				vip_level++;
				vip_points = vip_points + points - poiontMax;
			} else {
				vip_points += points;
			}
	}


	public long getComboTime(int id){
		return comboTime.getOrDefault(id,0L);
	}
	public void addComboTime( int abilityId, float time, int parenAbilityId) {
		if(comboTesk!=null){
			comboTesk.cancel(true);
			comboTesk = null;
		}
		if(comboTimer!=null){
			comboTimer.run();
			comboTimer=null;
		}
		comboTime.put(abilityId, System.currentTimeMillis() + (long) (time * 1000L));
		comboTimer = new ComboTimer(parenAbilityId, abilityId);
		comboTesk = Engine.getExecutor().schedule(comboTimer, (long) (time * 1000L), TimeUnit.MILLISECONDS);
	}
	public void removeComboTime(int abilityId){
		comboTime.remove(abilityId);
		if(comboTimer!=null){
			comboTimer.run();
			comboTimer=null;
		}
		if(comboTesk!=null){
			comboTesk.cancel(true);
			comboTesk = null;
		}
	}
	protected ConcurrentHashMap<Integer, Long> comboTime =new ConcurrentHashMap<Integer, Long>() ;
	transient ScheduledFuture<?> comboTesk = null;
	transient ComboTimer comboTimer=null;
	/*
     * Group specific data
     */

	protected OID groupOid = null;
	
	public void setGroupOid(OID groupOid){
		this.groupOid = groupOid;
	}

	public OID getGroupOid(){
		return groupOid;
	}	
	
	protected OID groupMemberOid = null;
	
	public void setGroupMemberOid(OID groupMemberOid){
		this.groupMemberOid = groupMemberOid;
	}
	
	public OID getGroupMemberOid(){
		return groupMemberOid;
	}	 
	
	public boolean isGrouped(){
		return groupOid != null;
	}
	
	transient protected boolean pendingGroupInvite = false;
	
	public void setPendingGroupInvite(boolean flag){
		this.pendingGroupInvite = flag;
	}
	
	public boolean isPendingGroupInvite(){
		return this.pendingGroupInvite;
	}
	transient protected boolean pendingSocialInvite = false;
	
	public void setPendingSocialInvite(boolean flag){
		this.pendingSocialInvite = flag;
	}
	
	public boolean isPendingSocialInvite(){
		return this.pendingSocialInvite;
	}
	/*
	 * Bonuses
	 */
	public HashMap<String, BonusSettings> getBonuses(){
		return bonuses;
	}
	public void setBonuses(HashMap<String, BonusSettings> bonuses) {
		this.bonuses =bonuses;
	}
	
	HashMap<String, BonusSettings> bonuses =new HashMap<String, BonusSettings>();

	public void removeRunAbilities(AgisAbilityState state){
		runAbilities.remove(state);
	}
	public AgisAbilityState getRunAbility(int id){
		for (AgisAbilityState aas :runAbilities ) {
			if(aas.getAbility().getID() == id){
				return aas;
			}
		}
		return null;
	}
	public void addRunAbilities(AgisAbilityState state) {
		this.runAbilities.add(state);
	}
	 transient List<AgisAbilityState> runAbilities = Collections.synchronizedList(new LinkedList<AgisAbilityState>());


	/*
	 * Final Static properties
	 */
	public final static String COMBAT_PROP_BACKREF_KEY = "combat.backref";
	public final static String COMBAT_PROP_USERFLAG = "combat.userflag";
	public final static String COMBAT_PROP_MOBFLAG = "combat.mobflag";

	public final static String COMBAT_PROP_AUTOATTACK_ABILITY = "combat.autoability";
	public final static String COMBAT_PROP_AUTOATTACK_BASE = "combat.autoabilitybase";
	public final static String COMBAT_PROP_REGEN_EFFECT = "combat.regeneffect";

	public final static String COMBAT_PROP_ENERGY = "energy";
	//public final static String COMBAT_PROP_HEALTH = "health";

	public final static String COMBAT_PROP_COMBATSTATE = "combatstate";
	public final static String COMBAT_PROP_COMBAT_TIMEOUT = "combattimeout";
	public final static String COMBAT_PROP_DEADSTATE = "deadstate";
	public final static String COMBAT_PROP_ATTACKABLE = "attackable";
	public final static String COMBAT_PROP_STATE = "state";
	public final static String COMBAT_PROP_WEAPON_STATE = "weaponsSheathed";
	public final static String COMBAT_PROP_FALLING_START_HEIGHT = "fallingStartHeight";
	
	public final static String COMBAT_PROP_ASPECT = "aspect";
	public final static String COMBAT_PROP_TEAM = "team";
	public final static String COMBAT_TAG_OWNER = "tagOwner";
	
	public final static String COMBAT_STATE_INCAPACITATED = "incapacitated";
	public final static String COMBAT_STATE_RESET = "reset";
	public final static String COMBAT_STATE_RESET_END = "resetEnd";
	public final static String COMBAT_STATE_EVADE = "evade";
	public final static String COMBAT_STATE_IMMUNE = "immune";
	public final static String COMBAT_STATE_SPIRIT = "spirit";
	
	public final static String COMBAT_AMMO_LOADED = "ammoloaded";
	public final static String COMBAT_AMMO_AMOUNT = "ammoamount";
	public final static String COMBAT_AMMO_TYPE = "ammotype";
	public final static String COMBAT_AMMO_DAMAGE = "ammodamage";
	public final static String COMBAT_AMMO_EFFECT = "ammoeffect";
	
	public final static String COMBAT_PROP_DUEL_ID = "duelID";
	public final static String COMBAT_PROP_ARENA_ID = "arenaID";
	
	public final static String HIGHEST_LEVEL_GAINED = "highestlevel";
	public final static String LOWEST_LEVEL_GAINED  = "lowestlevel";

	transient public ConcurrentHashMap<Integer, Long> powerUpAbilities = new ConcurrentHashMap<>();
	public final static int NUM_ACTIONS = 10;

	private static final long serialVersionUID = 1L;

	public void setPowerUpCoordEffectState(CoordinatedEffectState powerUpCoordEffectState) {
		this.powerUpCoordEffectState = powerUpCoordEffectState;
	}

	public CoordinatedEffectState getPowerUpCoordEffectState() {
		return powerUpCoordEffectState;
	}

	transient protected CoordinatedEffectState powerUpCoordEffectState;

	public void setStatProfile(int statProfile) {
		this.statProfile = statProfile;
	}

	public int getStatProfile() {
		return statProfile;
	}

	protected int statProfile = -1;
	public class ComboTimer implements Runnable {

		protected int parentAbilityId;
		protected int abilityId;
		//protected AgisInventoryPlugin group;

		public ComboTimer(int parentAbilityId, int abilityId) {
			this.parentAbilityId = parentAbilityId;
			this.abilityId = abilityId;
			//this.group = group;
			if(Log.loggingDebug) Log.debug("ComboTimer: Create");
		}

		@Override
		public void run() {
			// Check user still has items
			if(Log.loggingDebug) Log.debug("ComboTimer: running remove invite task for " + getOid());
			ExtendedCombatMessages.sendCombo(getOwnerOid(), parentAbilityId, abilityId,0, true, true, true);
			ExtendedCombatMessages.sendActions(getOwnerOid(), getCurrentActions(), getCurrentActionBar());
			if(comboTime.containsKey(abilityId)) {
				long time = comboTime.get(abilityId);
				if (time < System.currentTimeMillis()) {
					comboTime.remove(abilityId);
				}
			}
			if(Log.loggingDebug) 	Log.debug("ComboTimer: END");

		}
	}
	static {
		try {
			BeanInfo info = Introspector.getBeanInfo(CombatInfo.class);
			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
			for (int i = 0; i < propertyDescriptors.length; ++i) {
				PropertyDescriptor pd = propertyDescriptors[i];
				if (pd.getName().equals("currentAction")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("activeAbilities")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("friendlyTargets")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("attackableTargets")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("abilityFailed")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("pendingGroupInvite")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("pendingSocialInvite")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("cooldowns")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("cooldownMap")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("comboTime")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("powerUpAbilities")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("powerUpCoordEffectState")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("abilities")) {
					pd.setValue("transient", Boolean.TRUE);
				}
				log.debug("BeanInfo name="+pd.getName());
			}
		} catch (Exception e) {
			Log.error("failed beans initalization");
		}
	}
}
