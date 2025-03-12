package atavism.agis.objects;

import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.agis.behaviors.BaseBehavior;
import atavism.agis.behaviors.CombatPetBehavior;
import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.*;
import atavism.agis.objects.MobFactory;
import atavism.msgsys.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TamedPet extends Pet implements Serializable, MessageCallback
{
    public TamedPet() {
    }

    public TamedPet(String entityName, int mobTemplateID, String mobName, OID ownerOid, int skillType) {
    	super(entityName, mobTemplateID, mobName, ownerOid);
    	//this.name = entityName;
    	this.mobTemplateID = mobTemplateID;
    	this.mobName = mobName;
    	this.ownerOid = ownerOid;
    	this.skillType = skillType;
    	createNewTemplate();
    }
    
    /**
     * Grabs the base template for this mob then makes some edits to match this new pet.
     */
    private void createNewTemplate() {
    	Template mobTemplate = ObjectManagerClient.getTemplate(mobTemplateID, ObjectManagerPlugin.MOB_TEMPLATE);
    	// First rename the pet
    	//String ownerName = (String) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "charactername");
    	AccountDatabase aDB = new AccountDatabase(false);
    	String ownerName = aDB.getCharacterNameByOid(ownerOid);
		//String ownerName = WorldManagerClient.getObjectInfo(ownerOid).name;
    	if (ownerName.endsWith("s")) {
    		ownerName = ownerName.concat("'");
    	} else {
    		ownerName = ownerName.concat("'s");
    	}
    	String petSpecies = (String) mobTemplate.get(WorldManagerClient.NAMESPACE, "subSpecies");
    	mobName = ownerName + " " + petSpecies;
    	mobTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_NAME, mobName);
    	mobTemplate.setName(mobName);
    	// Set the pet's attitude to rep-based and set the faction to the players
    	String playersRace = (String) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "race");
    	mobTemplate.put(WorldManagerClient.NAMESPACE, "attitude", 0);
    	mobTemplate.put(Namespace.FACTION, FactionStateInfo.FACTION_PROP, playersRace);
    	ObjectManagerClient.registerTemplate(mobTemplate);
    	//propMap = mobTemplate.getPropertyMap();
    	//propMap = mobTemplate.getPropertyMapRef();
    	propMapWM = mobTemplate.getSubMap(WorldManagerClient.NAMESPACE);
    	String gender = (String) propMapWM.get("genderOptions");
    	if (gender.equals("Either")) {
    		Random random = new Random();
    		if (random.nextInt(2) == 0)
    			gender = "Male";
    		else
    			gender = "Female";
    	}
    	propMapWM.put("gender", gender);
    	// Add a property for this entity key
    	propMapWM.put("objectKey", getName());
    	propMapC = mobTemplate.getSubMap(CombatClient.NAMESPACE);
    	propMapC.put("petOwner", ownerOid);
    	Log.debug("PET: mobName: " + mobName + " has props: " + propMapWM);
    	saveObject();
    	//setupFactory();
    }
    
    /**
     * Creates the factory for the pet
     */
    /*private void setupFactory() {
    	MobFactory mobFactory = new MobFactory(mobName);
    	mobFactory.addBehav(new BaseBehavior());
    	CombatPetBehavior ncpBehav = new CombatPetBehavior();
    	ncpBehav.setOwnerOid(ownerOid);
    	mobFactory.addBehav(ncpBehav);
    	//summonPet();
    }*/
    
    public void loadPetData() {
    	Log.debug("PET: registering pet template: " + mobName + " with props: " + propMapWM);
    	//boolean templateExists = ObjectManagerClient.registerTemplate(mobTemplate);
    	/*if (!templateExists) {
    		Log.debug("PET: got false on the registering of the pet template");
    	}*/
    	Template tmpl = new Template(mobName);
    	for (String key: propMapWM.keySet())
    	    tmpl.put(WorldManagerClient.NAMESPACE, key, propMapWM.get(key));
    	for (String key: propMapC.keySet())
    	    tmpl.put(CombatClient.NAMESPACE, key, propMapC.get(key));
    	ObjectManagerClient.registerTemplate(tmpl);
    	/*if (isSpawned == true) {
    		isSpawned = false;
    		summonPet();
    	}*/
    }
    
    /**
     * Spawns a copy of the pet at the owners location.
     */
public void summonPet() {
    // Do we need to make sure the template has been registered?
    Log.debug("PET: summon pet hit");
    if (isSpawned == true) {
        Log.debug("PET: pet is already spawned");
        boolean wasSpawned = despawnPet();
        // Optionally return if pet was already spawned
    }
    // Make sure the player has the skill required to summon the pet
    currentLevel = 1;
    // TODO: get current skill level
    loadPetData();
    Log.debug("PET: summoning pet with props: " + propMapWM);

    // Create MobFactory
    MobFactory mobFactory = new MobFactory(mobTemplateID);

    // Add BaseBehavior using a supplier
    mobFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());

    // Add CombatPetBehavior using a supplier
    mobFactory.addBehavSupplier((obj, spawnData) -> {
        CombatPetBehavior ncpBehav = new CombatPetBehavior();
        ncpBehav.setOwnerOid(ownerOid);
        return ncpBehav;
    });

    // Get the owner's world node
    BasicWorldNode bwNode = WorldManagerClient.getWorldNode(ownerOid);

    // Prepare spawn data
    SpawnData spawnData = new SpawnData();

    // Spawn the pet
    ObjectStub obj = mobFactory.makeObject(spawnData, bwNode.getInstanceOid(), bwNode.getLoc());
    obj.spawn();
    InterpolatedWorldNode iwNode = obj.getWorldNode();

    Log.debug("PET: pet " + mobName + " spawned at: " + iwNode.getLoc() + " in instance: " + iwNode.getInstanceOid());
    Log.debug("PET: owner is at: " + bwNode.getLoc() + " in instance: " + bwNode.getInstanceOid());

    // Set the pet's display
    String gender = (String) propMapWM.get("gender");
    isSpawned = true;
    mobObj = obj.getOid();
    AgisMobPlugin.setDisplay(mobObj, gender);

    // Update pet commands
    AgisMobClient.petCommandUpdate(mobObj, attitude, null);
    AgisMobClient.petCommandUpdate(mobObj, currentCommand, null);

    // Activate the pet
    boolean activated = activate();

    // Save the pet object
    saveObject();

    // Set owner properties
    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet", mobObj);
    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "combatPet", this.getName());
    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "aP", mobObj);

    // Set the pet's factions
    String faction = (String) EnginePlugin.getObjectProperty(ownerOid, Namespace.FACTION, FactionStateInfo.FACTION_PROP);
    String tempFaction = (String) EnginePlugin.getObjectProperty(ownerOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
    EnginePlugin.setObjectProperty(mobObj, Namespace.FACTION, FactionStateInfo.FACTION_PROP, faction);
    EnginePlugin.setObjectProperty(mobObj, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, tempFaction);

    // Update pet combat stats
    updatePetCombatStats();
}

    private void updatePetCombatStats() {
    	AgisMobClient.updatePetStats(ownerOid, mobObj, currentLevel, 80);
    }
    
    /**
     * Despawns the pet.
     */
    public boolean despawnPet() {
    	boolean wasSpawned = false;
    	//mobObj.despawn();
    	Log.debug("PET: despawn hit with isSpawned: " + isSpawned);
    	if (isSpawned) {
    		try {
    			Log.debug("PET: despawning pet: " + mobObj);
    	        WorldManagerClient.despawn(mobObj);
    	        Log.debug("PET: despawned pet: " + mobObj);
    		} catch (NoRecipientsException e) {
    			Log.debug("PET: no recipients found for despawn pet.");
    		}
    		isSpawned = false;
    		boolean deactivated = deactivate();
    	    saveObject();
    	    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet", null);
    	    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "combatPet", null);
    	    wasSpawned = true;
    	}
    	return wasSpawned;
    }
    
    public void saveObject() {
    	//EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "CombatPet", this);
    	Log.debug("PET: saving pet object: " + getName());
    	ObjectManagerClient.saveObjectData(this.getName(), this, WorldManagerClient.NAMESPACE);
    }

    public boolean activate() {
        Log.debug("PET: in activate: this " + this);
        // Clear the old subscribers
        sub = null;
        sub2 = null;
        // subscribe for some messages
        SubjectFilter filter = new SubjectFilter(mobObj);
        //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter.addType(AgisMobClient.MSG_TYPE_PET_TARGET_LOST);
        sub = Engine.getAgent().createSubscription(filter, this);
        if (sub == null)
        	Log.debug("PET: sub is null");
        
        SubjectFilter filter2 = new SubjectFilter(ownerOid);
        filter2.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter2.addType(AgisMobClient.MSG_TYPE_SEND_PET_COMMAND);
        //sub2 = Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
        sub2 = Engine.getAgent().createSubscription(filter2, this);
        Log.debug("PET: set up subscription for pet owner: " + ownerOid);
        return true;
    }

   public boolean deactivate() {
	   Log.debug("PET: deactivating");
        if (sub != null) {
            Engine.getAgent().removeSubscription(sub);
            sub = null;
            Log.debug("PET: removing sub");
	    }
        if (sub2 != null) {
            Engine.getAgent().removeSubscription(sub2);
            sub2 = null;
            Log.debug("PET: removing sub 2");
	    }
        return true;
    }
    
    /**
     * process network messages
     */
    public void handleMessage(Message msg, int flags) {
    	//Log.debug("PET: got message with type: " + msg.getMsgType());
    	if (msg instanceof PropertyMessage) {
    		PropertyMessage propMsg = (PropertyMessage) msg;
    		OID subject = propMsg.getSubject();
    		if (subject.equals(mobObj)) {
    			handlePetPropertyUpdate(propMsg);
    		} else if (subject.equals(ownerOid)){
    			handleOwnerPropertyUpdate(propMsg);
    		}
    	} else if (msg.getMsgType() == AgisMobClient.MSG_TYPE_SEND_PET_COMMAND) {
    		Log.debug("PET: got send pet command message");
    		AgisMobClient.sendPetCommandMessage spcMsg = (AgisMobClient.sendPetCommandMessage) msg;
    		String command = spcMsg.getCommand();
    		OID targetOid = spcMsg.getTargetOid();
    		handleCommand(command, targetOid);
    	} else if (msg.getMsgType() == AgisMobClient.MSG_TYPE_PET_TARGET_LOST) {
    		Log.debug("PET: got send pet command message");
    		AgisMobClient.petTargetLostMessage spcMsg = (AgisMobClient.petTargetLostMessage) msg;
    		handleTargetLost();
    	} else {
            log.error("PET: unknown msg: " + msg);
        }
        //return true;
    }
    
    /**
     * Processes commands, which will either update the pets attitude, the current command, or activate
     * an ability that the pet has.
     * @param command
     */
    public void handleCommand(String command, OID targetOid) {
    	if (!isSpawned) {
    		Log.debug("PET: command issued to pet that is not spawned");
    		return;
    	}
    	Log.debug("PET: issuing pet command: " + command);
    	if (command.equals("passive")) {
    		updateAttitude(1);
    	} else if (command.equals("defensive")) {
    		updateAttitude(2);
    	} else if (command.equals("aggressive")) {
    		updateAttitude(3);
    	} else if (command.equals("stay")) {
    		updateCommand(-1, targetOid);
    	} else if (command.equals("follow")) {
    		updateCommand(-2, targetOid);
    	} else if (command.equals("attack")) {
    		if (targetOid.equals(ownerOid) || targetOid.equals(mobObj)) {
    			ExtendedCombatMessages.sendErrorMessage(ownerOid, "Your pet cannot attack that target");
    		} else {
    			updateCommand(-3, targetOid);
    		}
    	}
    }
    
    /**
     * Sets the pet to follow the owner if its current command was set to attack.
     */
    private void handleTargetLost() {
    	Log.debug("PET: pet has lost target, checking current command: " + currentCommand);
    	if (currentCommand == -3) {
    		updateCommand(-2, null);
    	}
    }
    
    /**
     * Deals with the different property updates that have occurred for the pet.
     * @param propMsg
     */
    protected void handlePetPropertyUpdate(PropertyMessage propMsg) {
    	Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
	    if (dead != null && dead) {
	    	Log.debug("PET: got pet death, despawning");
	    	//despawnPet();
	    }
    }
    
    /**
     * Deals with the different property updates that have occurred for the owner of the pet.
     * @param propMsg
     */
    protected void handleOwnerPropertyUpdate(PropertyMessage propMsg) {
    	Log.debug("");
    	Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
	    if (dead != null && dead) {
	    	Log.debug("PET: got owner death, despawning");
	    	//despawnPet();
	    	DespawnPet despawnPet = new DespawnPet();
	    	Engine.getExecutor().schedule(despawnPet, 2000, TimeUnit.MILLISECONDS);
	    	return;
	    }
	    LinkedList<Integer> skillValues = (LinkedList)propMsg.getProperty("skillValue");
	    if (skillValues != null) {
	    	Log.debug("PET: got skill value update");
	    	//TODO: get some way of getting skill level
	    	/*if (skillLevel != currentLevel) {
	    		updatePetCombatStats();
	    	}*/
	    	return;
	    }
    }
    
    class DespawnPet implements Runnable, Serializable {
    	public void run() {
    		if (isSpawned) {
        		try {
        			Log.debug("PET: despawning pet: " + mobObj);
        	        WorldManagerClient.despawn(mobObj);
        	        Log.debug("PET: despawned pet: " + mobObj);
        		} catch (NoRecipientsException e) {
        			Log.debug("PET: no recipients found for despawn pet.");
        		}
        		isSpawned = false;
        		boolean deactivated = deactivate();
        	    saveObject();
        	    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet", null);
        	    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "combatPet", null);
        	}
    	}
    	private static final long serialVersionUID = 1L;
    }

    static final Logger log = new Logger("CombatPet");

    public void updateAttitude(int attitude) {
    	this.attitude = attitude;
    	AgisMobClient.petCommandUpdate(mobObj, attitude, null);
    }
    public void updateCommand(int command, OID target) {
    	this.currentCommand = command;
    	AgisMobClient.petCommandUpdate(mobObj, currentCommand, target);
    }
    
    public String getMobName() {
    	return mobName;
    }
    public void setMobName(String mobName) {
    	this.mobName = mobName;
    }
    /*public Template getMobTemplate() {
    	return mobTemplate;
    }
    public void setMobTemplate(Template mobTemplate) {
    	this.mobTemplate = mobTemplate;
    }*/
    public OID getMobObj() {
    	return mobObj;
    }
    public void setMobObj(OID mobObj) {
    	this.mobObj = mobObj;
    }
    public boolean getSpawned() {
    	return isSpawned;
    }
    public void setSpawned(boolean isSpawned) {
    	this.isSpawned = isSpawned;
    }
    public OID getOwnerOid() {
    	return ownerOid;
    }
    public void setOwnerOid(OID ownerOid) {
    	this.ownerOid = ownerOid;
    }
    public int getAttitude() {
    	return attitude;
    }
    public void setAttitude(int attitude) {
    	this.attitude = attitude;
    }
    public int getCurrentCommand() {
    	return currentCommand;
    }
    public void setCurrentCommand(int currentCommand) {
    	this.currentCommand = currentCommand;
    }
    public int getSkillType() {
    	return skillType;
    }
    public void setSkillType(int skillType) {
    	this.skillType = skillType;
    }
    public int getCurrentLevel() {
    	return currentLevel;
    }
    public void setCurrentLevel(int currentLevel) {
    	this.currentLevel = currentLevel;
    }
    /*public MobFactory getMobFactory() {
    	return mobFactory;
    }
    public void setMobFactory(MobFactory mobFactory) {
    	this.mobFactory = mobFactory;
    }*/
    public Map<String, Serializable> getPropMapWM() {
    	return propMapWM;
    }
    public void setPropMapWM(Map<String, Serializable> propMapWM) {
    	this.propMapWM = propMapWM;
    }
    public Map<String, Serializable> getPropMapC() {
    	return propMapC;
    }
    public void setPropMapC(Map<String, Serializable> propMapC) {
    	this.propMapC = propMapC;
    }
    public Long getSub() {
    	return sub;
    }
    public void setSub(Long sub) {
    	this.sub = sub;
    }
    public Long getSub2() {
    	return sub2;
    }
    public void setSub2(Long sub2) {
    	this.sub2 = sub2;
    }

    private String mobName;
    //private Template mobTemplate = null;
    private OID mobObj;
    private boolean isSpawned;
    private OID ownerOid;
    private int attitude = 2; // 1: Passive, 2: Defensive, 3: Aggressive
    private int currentCommand = -2; // -1: Stay, -2: Follow, -3: Attack
    private int skillType = 0; // The type of skill this pet is related to
    private int currentLevel = 0;
    //private MobFactory mobFactory = null;
    private Map<String, Serializable> propMapWM = new HashMap<String, Serializable>();
    private Map<String, Serializable> propMapC = new HashMap<String, Serializable>();
    private Long sub;
    private Long sub2;
    
    private static final long serialVersionUID = 1L;
}
