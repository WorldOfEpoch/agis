package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisMobClient;
import atavism.msgsys.Message;
import atavism.msgsys.MessageCallback;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.LockFactory;
import atavism.server.util.Log;

@Deprecated
public class DomeMember implements MessageCallback {

	protected OID oid;
	protected String name;
	protected OID instanceOid;
	protected int domeID;
	protected int timeRemaining;
	protected int permitID;
	protected ArenaWeapon mainHandWeapon;
	protected ArenaWeapon offHandWeapon;
	protected int hearts;
	protected int maxHearts;
	protected boolean regenerating;
	protected Point respawnLocation;
	protected int team;
	protected int score;
	protected int kills;
	protected int deaths;
	protected int damageDealt;
	protected int damageTaken;
	protected ArenaStats stats;
	protected HashMap<String, Serializable> properties;
	protected int[] abilities;
	protected Long cooldown;
	protected Long sub;
	protected boolean active;
	protected int base_speed;
	protected ScheduledFuture<?> schedule;
	transient protected Lock lock = null;
	
	public DomeMember(OID oid, String name, int team, int domeID, int permitID, int permitCount,
			Point respawnLocation) {
		this.oid = oid;
		this.name = name;
		this.team = team;
		this.domeID = domeID;
		this.permitID = permitID;
		this.timeRemaining = permitCount;
		this.respawnLocation = respawnLocation;
		this.properties = new HashMap<String, Serializable>();
		this.active = true;
		this.abilities = new int[numAbilities];
		this.cooldown = System.currentTimeMillis();
		this.base_speed = CombatPlugin.DEFAULT_MOVEMENT_SPEED;
		clearAbilities();
		sendMessage("dome_joined", null);
		DecrementTimeRemaining decrementTime = new DecrementTimeRemaining();
		schedule = Engine.getExecutor().scheduleAtFixedRate(decrementTime, 1, 1, TimeUnit.MINUTES);
		Log.error("DOME: created player: " + oid);
		lock = LockFactory.makeLock("DomeMemberLock");
		regenerating = false;
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "attackable", true);
		ExtensionMessage healthMsg = new ExtensionMessage(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS,
                null, oid);
        Engine.getAgent().sendBroadcast(healthMsg);
		
		// Get weapon ID and determine weapon type
		setWeapons();
		// Set dome ID
		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "domeID", domeID);
		initialize();
		RegenerateHealth healthRegen = new RegenerateHealth();
		Engine.getExecutor().schedule(healthRegen, 5, TimeUnit.SECONDS);
	}
	
	public void initialize() {
        SubjectFilter filter = new SubjectFilter(oid);
        //filter.addType(AgisMobClient.MSG_TYPE_ACTIVATE_DOME_ABILITY);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter.addType(CombatClient.MSG_TYPE_DECREMENT_WEAPON_USES);
        //filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
        sub = Engine.getAgent().createSubscription(filter, this);
    }
	
	public void deactivate(boolean stillOnline) {
		active = false;
		Log.error("DOME: deactivating player: " + oid);
		// Stop the time decrementing
		schedule.cancel(true);
		schedule = null;
		if (stillOnline) {
			sendMessage("dome_left", null);
			//setPlayerProperty("hearts", 0);
			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "hearts", 0);
			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "max_hearts", 0);
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "domeID", -1);
			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "attackable", false);
		}
		if (sub != null) {
	        Engine.getAgent().removeSubscription(sub);
	        sub = null;
	    }
	}
	
	public void handleMessage(Message msg, int flags) {
	    if (active == false)
		    return;
	    lock.lock();
	    try {
	    	if (msg.getMsgType() == AgisMobClient.MSG_TYPE_ACTIVATE_DOME_ABILITY) {
	    		ExtensionMessage eMsg = (ExtensionMessage) msg;
	    		int slot = (Integer)eMsg.getProperty("slot");
	    		OID targetOid = OID.fromLong((Long)eMsg.getProperty("targetOid"));
	    		activateAbility(slot, targetOid);
	    	} else if (msg.getMsgType() == CombatClient.MSG_TYPE_DECREMENT_WEAPON_USES) {
	    		ExtensionMessage eMsg = (ExtensionMessage) msg;
	    		int abilityID = (Integer)eMsg.getProperty("abilityID");
	    		weaponUsed(abilityID);
	    	} else if (msg instanceof PropertyMessage) {
			    PropertyMessage propMsg = (PropertyMessage) msg;
			    Integer equippedItem = (Integer)propMsg.getProperty("equippedItem");
			    if (equippedItem != null) {
			    	Log.error("DOME: got new equippedItem: " + equippedItem);
			    	SetWeapon setWeap = new SetWeapon();
			    	Engine.getExecutor().schedule(setWeap, 100, TimeUnit.MILLISECONDS);
			    } else {
			    	equippedItem = (Integer)propMsg.getProperty("offHand");
				    if (equippedItem != null) {
				    	Log.error("DOME: got new equippedItem: " + equippedItem);
				    	SetWeapon setWeap = new SetWeapon();
				    	Engine.getExecutor().schedule(setWeap, 100, TimeUnit.MILLISECONDS);
				    }
			    }
			    Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			    if (dead != null && dead) {
			        if (propMsg.getSubject().equals(oid)) {
			        	playerDied();
				        Log.error("DOMEMEMBER: player " + oid + " died");
			        }
			    }
		    }
	    } finally {
	    	lock.unlock();
	    }
    }
	
	/**
	 * Terrible hack
	 * @author Andrew
	 *
	 */
	public class SetWeapon implements Runnable {
		public void run() {
			setWeapons();
		}
	}
	
	/**
	 * Gets the players weapon properties and updates their abilities based on the weapons equipped
	 */
	protected void setWeapons() {
		Integer weaponID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "equippedItem");
		Integer offhandID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "offHand");
		Log.error("DOME: weaponID: " + weaponID + " offhandID: " + offhandID);
		if (weaponID == null || weaponID == -1) {
			mainHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_UNARMED_ATTACK, ArenaWeapon.ARENA_WEAPON_UNARMED, -1);
			// Give player melee attack ability
			setAbility(primaryWeaponAbilitySlot, ArenaAbilities.ABILITY_UNARMED_ATTACK);
			Log.error("DOME: got no main hand item");
		} else {
			Template itemTmpl = ObjectManagerClient.getTemplate(weaponID, ObjectManagerPlugin.ITEM_TEMPLATE);
			String itemSubType = (String) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "subType");
			Log.error("DOME: itemSubType: " + itemSubType);
			int displayVal = (Integer) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "displayVal");
			int weaponAbility = (Integer) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "abilityID");
			if (itemSubType.equals("Ranged Weapon")) {
				mainHandWeapon = new ArenaWeapon(weaponAbility, ArenaWeapon.ARENA_WEAPON_RANGED, displayVal);
			} else if (itemSubType.equals("Melee Weapon")) {
				mainHandWeapon = new ArenaWeapon(weaponAbility, ArenaWeapon.ARENA_WEAPON_MELEE, displayVal);
			} else {
				mainHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_UNARMED_ATTACK, ArenaWeapon.ARENA_WEAPON_UNARMED, -1);
				setAbility(primaryWeaponAbilitySlot, ArenaAbilities.ABILITY_UNARMED_ATTACK);
			}
			mainHandWeapon.setWeaponID(weaponID);
			int uses = AgisInventoryClient.getAccountItemCount(oid, weaponID);
			mainHandWeapon.setUses(uses);
			setAbility(primaryWeaponAbilitySlot, weaponAbility);
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("slot", primaryWeaponAbilitySlot);
			props.put("uses", mainHandWeapon.getUses());
			sendMessage("ability_uses", props);
		}
		// Off hand
		if (offhandID == null || offhandID == -1) {
			offHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_UNARMED_ATTACK, ArenaWeapon.ARENA_WEAPON_UNARMED, -1);
			// Give player melee attack ability
			setAbility(secondaryWeaponAbilitySlot, -1);
			Log.error("DOME: got no off hand item");
		} else {
			Template itemTmpl = ObjectManagerClient.getTemplate(offhandID, ObjectManagerPlugin.ITEM_TEMPLATE);
			String itemSubType = (String) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "subType");
			Log.error("DOME: itemSubType: " + itemSubType);
			int displayVal = (Integer) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "displayVal");
			int weaponAbility = (Integer) itemTmpl.get(InventoryClient.ITEM_NAMESPACE, "abilityID");
			if (itemSubType.equals("Ranged Weapon")) {
				offHandWeapon = new ArenaWeapon(weaponAbility, ArenaWeapon.ARENA_WEAPON_RANGED, displayVal);
			} else if (itemSubType.equals("Melee Weapon")) {
				offHandWeapon = new ArenaWeapon(weaponAbility, ArenaWeapon.ARENA_WEAPON_MELEE, displayVal);
			} else if (itemSubType.equals("Shield")) {
				offHandWeapon = new ArenaWeapon(weaponAbility, ArenaWeapon.ARENA_WEAPON_SHIELD, displayVal);
			} else {
				offHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_UNARMED_ATTACK, ArenaWeapon.ARENA_WEAPON_UNARMED, -1);
				setAbility(secondaryWeaponAbilitySlot, -1);
			}
			offHandWeapon.setWeaponID(offhandID);
			int uses = AgisInventoryClient.getAccountItemCount(oid, offhandID);
			offHandWeapon.setUses(uses);
			setAbility(secondaryWeaponAbilitySlot, weaponAbility);
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("slot", secondaryWeaponAbilitySlot);
			props.put("uses", offHandWeapon.getUses());
			sendMessage("ability_uses", props);
		}
	}
	
	public void activateAbility(int slot, OID targetOid) {
		if (!active)
			return;
    	int abilityID = abilities[slot];
    	Log.error("DOME: got activate ability message with slot: " + slot + " which gives abilityID: " + abilityID);
    	if (abilityID != -1) {
    		CombatClient.startAbility(abilityID, oid, targetOid, null);
    	}
	}
	
	protected void weaponUsed(int abilityID) {
		//Long currentTime = System.currentTimeMillis();
		// Set cooldown
		/*int weaponCooldown = mainHandWeapon.getCooldown();
        cooldown = currentTime + weaponCooldown;
        Log.debug("ATTACK: setting attack cooldown to: " + cooldown
        		+ " with current time: " + currentTime);
		HashMap<String, Serializable> cooldownProps = new HashMap<String, Serializable>();
        cooldownProps.put("abilityID", abilityID);
        cooldownProps.put("length", mainHandWeapon.getCooldown());
        sendMessage("attack_cooldown", cooldownProps);*/
		
        if (mainHandWeapon.getAbilityID() == abilityID) {
        	if (mainHandWeapon.weaponUsed() == 0) {
    			setAbility(primaryWeaponAbilitySlot, ArenaAbilities.ABILITY_UNARMED_ATTACK);
    		} else {
    			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    			props.put("slot", primaryWeaponAbilitySlot);
    			props.put("uses", mainHandWeapon.getUses());
    			sendMessage("ability_uses", props);
    		}
    		// Send item count altered message (if not unarmed)
    		if (mainHandWeapon.getWeaponID() != -1) {
    			ExtensionMessage itemMsg = new ExtensionMessage(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT,
    	                null, oid);
    			itemMsg.setProperty("itemID", mainHandWeapon.getWeaponID());
    			itemMsg.setProperty("count", -1);
    	        Engine.getAgent().sendBroadcast(itemMsg);
    		}
        } else if (offHandWeapon.getAbilityID() == abilityID) {
        	if (offHandWeapon.weaponUsed() == 0) {
    			setAbility(secondaryWeaponAbilitySlot, -1);
    		} else {
    			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    			props.put("slot", secondaryWeaponAbilitySlot);
    			props.put("uses", offHandWeapon.getUses());
    			sendMessage("ability_uses", props);
    		}
    		// Send item count altered message (if not unarmed)
    		if (offHandWeapon.getWeaponID() != -1) {
    			ExtensionMessage itemMsg = new ExtensionMessage(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT,
    	                null, oid);
    			itemMsg.setProperty("itemID", offHandWeapon.getWeaponID());
    			itemMsg.setProperty("count", -1);
    	        Engine.getAgent().sendBroadcast(itemMsg);
    		}
        }
	}
	
	/**
	 * 
	 * @param change
	 * @param caster
	 * @return
	 */
	public boolean alterHearts(int change, OID caster) {
		if (!active)
			return true;
		hearts += change;
		if (hearts > maxHearts)
			hearts = maxHearts;
		//setPlayerProperty("hearts", hearts);
		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "hearts", hearts);
		if (hearts <= 0) {
			playerDied();
		} else if (!regenerating) {
			RegenerateHealth healthRegen = new RegenerateHealth();
			Engine.getExecutor().schedule(healthRegen, 5, TimeUnit.SECONDS);
			regenerating = true;
		}
		return false;
	}
	
	public class RegenerateHealth implements Runnable {
		public void run() {
			if (!active)
				return;
			ExtensionMessage regenMsg = new ExtensionMessage(CombatClient.MSG_TYPE_REGEN_HEALTH_MANA,
	                null, oid);
			regenMsg.setProperty("amount", 1);
	        Engine.getAgent().sendBroadcast(regenMsg);
			Engine.getExecutor().schedule(this, 5, TimeUnit.SECONDS);
			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "hearts", hearts);
			/*if  (hearts < maxHearts) {
				// Schedule another regen in 5 seconds
				Engine.getExecutor().schedule(this, 5, TimeUnit.SECONDS);
			} else {
				regenerating = false;
			}*/
		}
	}
	
	public class DecrementTimeRemaining implements Runnable {
		public void run() {
			timeRemaining--;
			if (timeRemaining == 0) {
				// This "should" remove the players permit item. This should be changed to remove the item?
				ExtensionMessage itemMsg = new ExtensionMessage(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT,
		                null, oid);
				itemMsg.setProperty("itemID", permitID);
				itemMsg.setProperty("count", -1);
		        Engine.getAgent().sendBroadcast(itemMsg);
		        // Kick player out of Dome Hunt mode
		        ExtensionMessage leaveMsg = new ExtensionMessage(AgisMobClient.MSG_TYPE_DOME_LEAVE_REQUEST,
		                null, oid);
		        leaveMsg.setProperty("domeID", domeID);
		        Engine.getAgent().sendBroadcast(leaveMsg);
			} else {
				// Send message to reduce the users permit count
				ExtensionMessage itemMsg = new ExtensionMessage(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT,
		                null, oid);
				itemMsg.setProperty("itemID", permitID);
				itemMsg.setProperty("count", -1);
		        Engine.getAgent().sendBroadcast(itemMsg);
		        sendMessage("dome_time_remaining", null);
			}
			Log.error("DOME: decrementing time for  player: " + oid + " with time remaining: " + timeRemaining);
		}
	}

	public void setProperty(String propName, Serializable value) {
		properties.put(propName, value);
	}

	public Serializable getProperty(String propName) {
		return properties.get(propName);
	}

	public void updateScore(int delta) {
		this.score += delta;
	}

	public void addKill() {
		this.kills++;
	}

	public void addDeath() {
		this.deaths++;
	}

	public void addDamageDealt(int damageDealt) {
		this.damageDealt += damageDealt;
	}

	public void addDamageTaken(int damageTaken) {
		this.damageTaken += damageTaken;
	}

	public void setAbility(int slot, int abilityID) {
		this.abilities[slot] = abilityID;
		sendMessage("arena_abilities", null);
	}

	public void clearAbilities() {
		// No need to reset the first attack ability
		for (int i = 0; i < abilities.length; i++) {
			if (i != primaryWeaponAbilitySlot && i != secondaryWeaponAbilitySlot)
				abilities[i] = -1;
		}
		sendMessage("arena_abilities", null);
	}

	public void playerDied() {
		this.active = false;
		regenerating = false;
		clearAbilities();
		//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
		playDeathAnimation();
		// Schedule death teleport in a couple seconds
		DeathTeleport teleportTimer = new DeathTeleport();
		Engine.getExecutor().schedule(teleportTimer, 4, TimeUnit.SECONDS);
		//ExtensionMessage koMsg = new ExtensionMessage(CombatClient.MSG_TYPE_KNOCKED_OUT, null, oid);
        //Engine.getAgent().sendBroadcast(koMsg);
	}

	public class DeathTeleport implements Runnable {
		public void run() {
			Log.debug("ARENA: cleaning up the Arena");
			// Next move the victim back to their base
			BasicWorldNode tnode = new BasicWorldNode();
			tnode.setLoc(respawnLocation);
			tnode.setDir(new AOVector());
			WorldManagerClient.updateWorldNode(oid, tnode, true);
			WorldManagerClient.refreshWNode(oid);
			// Reset the players speed
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE,
					AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, base_speed);
			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE, false);
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
    		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
    		ExtensionMessage healthMsg = new ExtensionMessage(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS, null, oid);
            Engine.getAgent().sendBroadcast(healthMsg);
			//hearts = maxHearts;
			//setPlayerProperty("hearts", hearts);
			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "hearts", hearts);
			active = true;
			// Reset the players speed
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE,
					AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, base_speed);
			RegenerateHealth healthRegen = new RegenerateHealth();
			Engine.getExecutor().schedule(healthRegen, 5, TimeUnit.SECONDS);
		}
	}

	public void playDeathAnimation() {
		CoordinatedEffect cE = new CoordinatedEffect("DeathEffect");
		cE.sendSourceOid(true);
		cE.invoke(oid, oid);
	}

	public void playVictoryAnimation() {
		CoordinatedEffect cE = new CoordinatedEffect("VictoryEffect");
		cE.sendSourceOid(true);
		cE.invoke(oid, oid);
	}

	public void queueReactivation(int seconds) {
		Reactivate reactivateTimer = new Reactivate();
		Engine.getExecutor().schedule(reactivateTimer, seconds,
				TimeUnit.SECONDS);
	}

	public class Reactivate implements Runnable {
		public void run() {
			active = true;
		}
	}
	
	public void setPlayerProperty(String prop, Serializable value) {
    	PropertyMessage propMsg = new PropertyMessage(oid, oid);
    	propMsg.setProperty(prop, value);
		Engine.getAgent().sendBroadcast(propMsg);
    }
	
	/**
     * Sends an extension message of the specified type to the specified player
     * @param msgType: the message type
     * @param oid: the oid to send the message to
     * @param data: some form of data to be sent
     */
    public boolean sendMessage(String msgType, Serializable data) {
    	boolean handled = false;
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", msgType);
		// Check for type and fill props here
		if (msgType.equals("dome_joined")) {
			props.put("timeRemaining", timeRemaining);
			props.put("domeID", domeID);
			handled = true;
		} else if (msgType.equals("dome_left")) {
			handled = true;
		} else if (msgType.equals("dome_time_remaining")) {
			props.put("timeRemaining", timeRemaining);
			handled = true;
		} else if (msgType.equals("message_text")) {
			String value = (String) data;
			props.put("message", value);
			handled = true;
		} else if (msgType.equals("arena_abilities")) {
			props.put("numAbilities", abilities.length);
			for (int i = 0; i < abilities.length; i++) {
				int abilityID = abilities[i];
				props.put("ability" + i + "ID", abilityID);
			}
		} else if (msgType.equals("ability_uses")) {
			HashMap<String, Serializable> inProps = (HashMap)data;
			props.put("slot", inProps.get("slot"));
			props.put("uses", inProps.get("uses"));
			handled = true;
		} else if (msgType == "attack_cooldown") {
			HashMap<String, Serializable> map = (HashMap) data;
			props.put("cooldown_length", map.get("length"));
			props.put("ability_id", map.get("abilityID"));
			handled = true;
		}
		
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
			WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
			oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
		return handled;
    }

	public OID getOid() {
		return oid;
	}

	public String getName() {
		return name;
	}

	public int getTeam() {
		return team;
	}

	public int getScore() {
		return score;
	}

	public int getKills() {
		return kills;
	}

	public int getDeaths() {
		return deaths;
	}

	public int getDamageDealt() {
		return damageDealt;
	}

	public int getDamageTaken() {
		return damageTaken;
	}

	public ArenaStats getStats() {
		return stats;
	}

	public HashMap<String, Serializable> getProperties() {
		return this.properties;
	}

	public int[] getAbilities() {
		return abilities;
	}

	public void setSub(Long sub) {
		this.sub = sub;
	}

	public Long getSub() {
		return sub;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean getActive() {
		return active;
	}
	
	public static final int numAbilities = 5;
	public static final int primaryWeaponAbilitySlot = 0;
	public static final int secondaryWeaponAbilitySlot = 4;
}