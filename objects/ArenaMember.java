package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.Marker;
import atavism.server.objects.Template;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class ArenaMember implements MessageDispatch {
	
	protected OID oid;
	protected String name;
	protected OID instanceOid;
	protected int team;
	protected int score;
	protected int kills;
	protected int deaths;
	protected int damageDealt;
	protected int damageTaken;
	protected ArenaStats stats;
	protected HashMap<String, Serializable> properties;
	protected boolean useWeapons;
	protected boolean useHealth;
	protected int[] abilities;
	protected ArenaWeapon mainHandWeapon;
	protected ArenaWeapon offHandWeapon;
	protected Long sub;
	protected boolean active;
	protected int base_speed;
	
	public ArenaMember(OID oid, String name, int team, int base_speed, boolean useWeapons, boolean useHealth) {
		this.oid = oid;
		this.name = name;
		this.team = team;
		this.properties = new HashMap<String, Serializable>();
		this.active = true;
		this.useWeapons = useWeapons;
		if (useWeapons) {
			this.abilities = new int[NUM_ABILITIES_WITH_WEAPONS];
			// Clear their current display settings
			HashMap<String, Serializable> propMap = new HashMap<String, Serializable>();
			propMap.put("primaryItem", -1);
        	propMap.put("secondaryItem", -1);
        	propMap.put("playerAppearance", 0);
        	EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, propMap);
		} else {
			this.abilities = new int[NUM_ABILITIES];
		}
		clearAbilities();
		this.useHealth = useHealth;
		this.base_speed = base_speed;
		/*if (useHealth) {
			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "attackable", true);
			ExtensionMessage healthMsg = new ExtensionMessage(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS,
	                null, oid);
	        Engine.getAgent().sendBroadcast(healthMsg);
		}*/
		initialize();
	}
	
	public void initialize() {
        SubjectFilter filter = new SubjectFilter(oid);
        //filter.addType(AgisMobClient.MSG_TYPE_ACTIVATE_DOME_ABILITY);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter.addType(CombatClient.MSG_TYPE_DECREMENT_WEAPON_USES);
        //filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
        sub = Engine.getAgent().createSubscription(filter, this);
    }
	
	public void deactivate() {
		active = false;
		Log.debug("ARENA: deactivating player: " + oid);
		if (sub != null) {
	        Engine.getAgent().removeSubscription(sub);
	        sub = null;
	    }
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE, false);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "attackable", true);//FIXME ZBDS Changeed from False To True 
		if (useWeapons) {
			// Reset the weapon display
			HashMap<String, Serializable> propMap = new HashMap<String, Serializable>();
			Integer weaponID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "equippedItem");
			Integer offhandID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "offHand");
			if (weaponID != -1) {
				Template itemTemplate = ObjectManagerClient.getTemplate(weaponID, ObjectManagerPlugin.ITEM_TEMPLATE);
				int displayVal = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "displayVal");
				propMap.put("primaryItem", displayVal);
			}
			if (offhandID != -1) {
				Template itemTemplate = ObjectManagerClient.getTemplate(offhandID, ObjectManagerPlugin.ITEM_TEMPLATE);
				int displayVal = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "displayVal");
				propMap.put("secondaryItem", displayVal);
			}
			propMap.put("playerAppearance", 0);
        	EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, propMap);
		}
	}
	
	public void handleMessage(Message msg, int flags) {
	    if (active == false)
		    return;
	    if (msg instanceof PropertyMessage) {
		    PropertyMessage propMsg = (PropertyMessage) msg;
		    Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
		    if (dead != null && dead) {
		        if (propMsg.getSubject().equals(oid)) {
		        	playerDied();
			        Log.debug("ARENAMEMBER: player " + oid + " died");
		        }
		    }
	    } else if (msg.getMsgType() == CombatClient.MSG_TYPE_DECREMENT_WEAPON_USES) {
    		ExtensionMessage eMsg = (ExtensionMessage) msg;
    		int abilityID = (Integer)eMsg.getProperty("abilityID");
    		weaponUsed(abilityID);
    	}
    }
	
	protected void weaponUsed(int abilityID) {
        if (mainHandWeapon.getAbilityID() == abilityID) {
        	if (mainHandWeapon.weaponUsed() == 0) {
    			setAbility(primaryWeaponAbilitySlot, ArenaAbilities.ABILITY_UNARMED_ATTACK);
    		} else {
    			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    			props.put("slot", primaryWeaponAbilitySlot);
    			props.put("uses", mainHandWeapon.getUses());
    			sendMessage("ability_uses", props);
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
	
	public void weaponPickedUp(int weaponID, String weaponType) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("slot", 0);
    	if (weaponType.equals(ArenaObject.ARENA_OBJECT_MELEE_WEAPON)) {
    		mainHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_MELEE_ATTACK, weaponType, 188);
        	setPlayerProperty("primaryItem", 188);
        	setPlayerProperty("playerAppearance", -1);
        	setAbility(0, ArenaAbilities.ABILITY_MELEE_ATTACK);
        	props.put("uses", mainHandWeapon.getUses());
    	} else {
    		mainHandWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_RANGED_ATTACK, ArenaWeapon.ARENA_WEAPON_RANGED, 195);
        	setPlayerProperty("primaryItem", 195);
        	setPlayerProperty("playerAppearance", -1);
        	setAbility(0, ArenaAbilities.ABILITY_RANGED_ATTACK);
        	props.put("uses", mainHandWeapon.getUses());
    	}
    	sendMessage("arena_abilities", null);
		sendMessage("ability_uses", props);
	}

	public void setAbility(int slot, int abilityID) {
		this.abilities[slot] = abilityID;
	}

	public void clearAbilities() {
		for (int i = 0; i < abilities.length; i++) {
			if (useWeapons && (i == primaryWeaponAbilitySlot || i == secondaryWeaponAbilitySlot)) {
				continue;
			}
			abilities[i] = -1;
		}
	}

	public void playerDied() {
		this.active = false;
		clearAbilities();
		playDeathAnimation();
		// Schedule death teleport in a couple seconds
		//DeathTeleport teleportTimer = new DeathTeleport();
		//Engine.getExecutor().schedule(teleportTimer, 4, TimeUnit.SECONDS);
	}

	public class DeathTeleport implements Runnable {
		public void run() {
			active = true;
			Log.debug("ARENA: teleporting dead player: " + oid);
			HashMap<String, Serializable> propMap = new HashMap<String, Serializable>();
			propMap.put(WorldManagerClient.WORLD_PROP_NOMOVE, false);
        	propMap.put(WorldManagerClient.WORLD_PROP_NOTURN, false);
        	EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, propMap);
			// Next move the victim back to their base
			BasicWorldNode tnode = new BasicWorldNode();
			String markerName = "team" + team + "Spawn"; //"Death";
			if (instanceOid == null) {
				Log.error("ARENA: instance Oid is null");
				instanceOid = WorldManagerClient.getObjectInfo(oid).instanceOid;
			}
			Log.debug("ARENA: teleporting dead player to instance: " + instanceOid);
			Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
			Log.debug("ARENA: got marker: " + spawn);
			tnode.setLoc(spawn.getPoint());
			tnode.setDir(new AOVector());
			Log.debug("ARENA: set respawn loc");
			WorldManagerClient.updateWorldNode(oid, tnode, true);
			WorldManagerClient.refreshWNode(oid);
			Log.debug("ARENA: updated world node");
			// Reset the players speed
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, base_speed);
		     /*if (useHealth) {
            	Log.error("ARENA: setting dead state to false and resetting health props");
            	EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE, false);
            	ExtensionMessage healthMsg = new ExtensionMessage(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS, null, oid);
                Engine.getAgent().sendBroadcast(healthMsg);
            }*/
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
		if (msgType.equals("message_text")) {
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
	
	public void setInstanceOid(OID instanceOid) {
		this.instanceOid = instanceOid;
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
	
	public static final int NUM_ABILITIES = 3;
	public static final int NUM_ABILITIES_WITH_WEAPONS = 5;
	public static final int primaryWeaponAbilitySlot = 0;
	public static final int secondaryWeaponAbilitySlot = 4;
}