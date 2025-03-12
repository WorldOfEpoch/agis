package atavism.agis.plugins;

import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

//import org.python.parser.ast.Call;

import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.InstanceClient.InstanceInfo;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.agis.core.Agis;
import atavism.agis.core.BuildingResourceAcquireHook;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.*;
import atavism.agis.objects.Claim.ClaimObject;
import atavism.agis.objects.Claim.ClaimUpgrade;
import atavism.agis.plugins.CombatClient.interruptAbilityMessage;
import atavism.agis.plugins.VoxelClient.BuildingAoETargetsInAreaMessage;
import atavism.agis.plugins.VoxelClient.BuildingDamageMessage;
import atavism.agis.plugins.VoxelClient.GetBuildingIsAttackableMessage;
import atavism.agis.plugins.VoxelClient.GetBuildingPositionMessage;
import atavism.agis.plugins.VoxelClient.GetBuildingTemplateMessage;
import atavism.agis.plugins.VoxelClient.GetChestStorageOidMessage;
import atavism.agis.plugins.VoxelClient.SendClaimUpdateMessage;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementChecker;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

/**
 * the voxel plugin tracks changes in a voxel-based world
 */
public class VoxelPlugin extends atavism.server.engine.EnginePlugin {
	
	public VoxelPlugin() {
		super("Voxel");
		setPluginType("Voxel");
	}

	public static String VOXEL_PLUGIN_NAME = "Voxel";

	protected static final Logger log = new Logger("VoxelPlugin");
	protected static Lock lock = LockFactory.makeLock("VoxelPlugin");

	public void onActivate() {
		log.debug("VoxelPlugin.onActivate()");

		// register message hooks
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_LOADED, new InstanceLoadedHook());
		
		getHookManager().addHook(VoxelClient.MSG_TYPE_CREATE_CLAIM, new CreateClaimHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_EDIT_CLAIM, new EditClaimHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_UPGRADE_CLAIM, new UpgradeClaimHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_PURCHASE_CLAIM, new PurchaseClaimHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_DELETE_CLAIM, new DeleteClaimHook());
		
		getHookManager().addHook(VoxelClient.MSG_TYPE_CLAIM_PERMISSION, new ClaimPermissionHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_CLAIM_ACTION, new ClaimActionHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_PLACE_CLAIM_OBJECT, new PlaceClaimObjectHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_EDIT_CLAIM_OBJECT, new EditClaimObjectHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_UPGRADE_BUILDING_OBJECT, new UpgradeClaimObjectHook());

		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_RESOURCES, new GetBuildingResourcesHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_NO_BUILD_CLAIM_TRIGGER, new NoBuildClaimTriggerHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_BUILDING_TEMPLATE, new GetBuildObjectTemplateHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_CLAIM_OBJECT_INFO, new GetClaimObjectInfoHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_INTERRUPT_ABILITY, new InterruptHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_ATTACK_BUILDING_OBJECT, new AttackBuildingObjectHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_TAKE_CLAIM_RESOURCE, new TakeClaimResourceHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_CHEST_STORAGE_OID, new GetChestStorageOidHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_SEND_UPDATE_CLAIM, new SendClimeUpdateHook());
		
		getHookManager().addHook(VoxelClient.MSG_TYPE_PAY_TAX_CLAIM, new ClaimTaxPaymentHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_TARGET_TYPE, new TargetTypeUpdateHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_BUILDING_LOC, new GetBuildObjectLocHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_BUILDING_DAMAGE, new BuildingDamageHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_BUILDING_IN_AREA, new CheckBuildingsInAreaHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_GET_BUILDING_IS_ATTACKABLE, new GetBuildObjectIsAttackableHook());
		
		
		getHookManager().addHook(EnginePlugin.MSG_TYPE_SET_PROPERTY, new SetPropertyHook());
		getHookManager().addHook(EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK, new SetPropertyHook());
		
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		
		// setup message filters
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		filter.addType(InstanceClient.MSG_TYPE_INSTANCE_LOADED);
		filter.addType(VoxelClient.MSG_TYPE_CREATE_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_EDIT_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_UPGRADE_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_PURCHASE_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_SELL_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_DELETE_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_PAY_TAX_CLAIM);
		filter.addType(VoxelClient.MSG_TYPE_CLAIM_PERMISSION);
		filter.addType(VoxelClient.MSG_TYPE_CLAIM_ACTION);
		filter.addType(VoxelClient.MSG_TYPE_PLACE_CLAIM_OBJECT);
		filter.addType(VoxelClient.MSG_TYPE_EDIT_CLAIM_OBJECT);
		filter.addType(VoxelClient.MSG_TYPE_UPGRADE_BUILDING_OBJECT);
		filter.addType(VoxelClient.MSG_TYPE_GET_RESOURCES);
		filter.addType(VoxelClient.MSG_TYPE_NO_BUILD_CLAIM_TRIGGER);
		filter.addType(VoxelClient.MSG_TYPE_GET_CLAIM_OBJECT_INFO);
		filter.addType(VoxelClient.MSG_TYPE_ATTACK_BUILDING_OBJECT);
		filter.addType(VoxelClient.MSG_TYPE_TAKE_CLAIM_RESOURCE);
		filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
		// Account login message - no idea if it should go here
		filter.addType(ProxyPlugin.MSG_TYPE_ACCOUNT_LOGIN);
		filter.addType(VoxelClient.MSG_TYPE_SEND_UPDATE_CLAIM);
		
		filter.addType(EnginePlugin.MSG_TYPE_SET_PROPERTY); // S
		filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
		filter.addType(EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK);
		
		filter.addType(VoxelClient.MSG_TYPE_BUILDING_DAMAGE);
		
		filter.addType(CombatClient.MSG_TYPE_TARGET_TYPE);
		Engine.getAgent().createSubscription(filter, this);

		// Create responder subscription
		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(VoxelClient.MSG_TYPE_GET_BUILDING_TEMPLATE);
		filter2.addType(VoxelClient.MSG_TYPE_GET_BUILDING_LOC);
		filter2.addType(VoxelClient.MSG_TYPE_GET_BUILDING_IN_AREA);
		filter2.addType(VoxelClient.MSG_TYPE_GET_BUILDING_IS_ATTACKABLE);
		filter2.addType(VoxelClient.MSG_TYPE_GET_CHEST_STORAGE_OID);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		
		// Connect to the Account Database and store the connection
		cDB = new AccountDatabase(true);
		// Also create a temporary connection to the Content Database
		loadData();
		
		
		
		
		
	
	//	cnDB.close();

		log.debug("VoxelPlugin.onActivate() completed");

		// Start claim tax tick
		Engine.getExecutor().scheduleAtFixedRate(claimTick, 10, 2, TimeUnit.SECONDS);
        Log.debug("Registering Builder plugin");
		Engine.registerStatusReportingPlugin(this);

	}
	void loadData() {
		ContentDatabase cnDB = new ContentDatabase(false);
		String removeOnFail = cnDB.loadGameSetting("REMOVE_ITEM_ON_BUILD_FAIL");
		if (removeOnFail != null) {
			REMOVE_ITEM_ON_BUILD_FAIL = Boolean.parseBoolean(removeOnFail);
			log.debug("Game Settings Loaded REMOVE_ITEM_ON_BUILD_FAIL="+REMOVE_ITEM_ON_BUILD_FAIL);
			}
		String canBuildFail = cnDB.loadGameSetting("BUILD_CAN_FAIL");
		if (canBuildFail != null) {
			BUILD_CAN_FAIL = Boolean.parseBoolean(canBuildFail);
			log.debug("Game Settings Loaded BUILD_CAN_FAIL="+BUILD_CAN_FAIL);
					}
		String onlyUpgradeWithAllItems = cnDB.loadGameSetting("ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS");
		if (onlyUpgradeWithAllItems != null) {
			ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS = Boolean.parseBoolean(onlyUpgradeWithAllItems);
			log.debug("Game Settings Loaded ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS="+ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS);
			}
		String useClaimResources = cnDB.loadGameSetting("USE_CLAIM_RESOURCES");
		if (useClaimResources != null) {
			USE_CLAIM_RESOURCES = Boolean.parseBoolean(useClaimResources);
			log.debug("Game Settings Loaded USE_CLAIM_RESOURCES="+USE_CLAIM_RESOURCES);
			}
		String useTaxSystem = cnDB.loadGameSetting("USE_TAX_SYSTEM");
		if (useTaxSystem != null) {
			USE_TAX_SYSTEM = Boolean.parseBoolean(useTaxSystem);
			log.debug("Game Settings Loaded USE_TAX_SYSTEM="+USE_TAX_SYSTEM);
			}
		String useAutoPayTaxSystem = cnDB.loadGameSetting("AUTO_PAY_TAX_SYSTEM");
		if (useAutoPayTaxSystem != null) {
			AUTO_PAY_TAX_SYSTEM = Boolean.parseBoolean(useAutoPayTaxSystem);
			log.debug("Game Settings Loaded AUTO_PAY_TAX_SYSTEM="+AUTO_PAY_TAX_SYSTEM);
			}
		String distanceReqBetweenClaims = cnDB.loadGameSetting("DISTANCE_REQ_BETWEEN_CLAIMS");
		if (distanceReqBetweenClaims != null) {
			DISTANCE_REQ_BETWEEN_CLAIMS = Integer.parseInt(distanceReqBetweenClaims);
			log.debug("Game Settings Loaded DISTANCE_REQ_BETWEEN_CLAIMS="+DISTANCE_REQ_BETWEEN_CLAIMS);
			}
		String upgItemfromInvent = cnDB.loadGameSetting("UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY");
		if (upgItemfromInvent != null) {
			UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY =  Boolean.parseBoolean(upgItemfromInvent);
			log.debug("Game Settings Loaded UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY="+UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY);
		}
		String devMode = cnDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
		if (devMode != null) {
			EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
			log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
		}
		loadStatsData();
		// Load in the build object templates
		buildObjectTemplates = cnDB.loadBuildObjectTemplates();
		claimProfiles = cnDB.loadClaimProfiles();
		cnDB.loadEditorOptions();
		// TODO: make this more efficient, not all the tables need to be loaded in
		HashMap<Integer, LootTable> lootTables = cnDB.loadLootTables(-1);
		for (LootTable lTbl : lootTables.values()) {
			Agis.LootTableManager.register(lTbl.getID(), lTbl);
			Log.debug("LOOT: loaded loot Table: [" + lTbl.getName() + "]");
		}
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("QuestPlugin ReloadTemplates Start");
			loadData();
			Entity[] objects = EntityManager.getAllEntitiesByNamespace(Namespace.QUEST);
			for(Entity e : objects){
				QuestStateInfo qsi = (QuestStateInfo)e;
				qsi.ReloadQuests(1);
			}
		Log.error("QuestPlugin ReloadTemplates End");
	}
	
	
/**
 * Load Stats and Stats Tresholds
 */
	void loadStatsData() {
		CombatDatabase db = new CombatDatabase(false);

		if(!Engine.isAIO()) {
			CombatPlugin.STAT_LIST = db.LoadStats();
			CombatPlugin.STAT_THRESHOLDS = db.LoadStatThresholds();
			CombatPlugin.STAT_PROFILES = db.LoadStatProfiles();
		}
	}
	
	// Log the logout information and send a response. No longer used.
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			Log.debug("LOGOUT: voxel logout started for: " + playerOid);

			Iterator<ConcurrentHashMap.Entry<Integer, Claim>> itr = claims.entrySet().iterator();
			while (itr.hasNext()) {
				ConcurrentHashMap.Entry<Integer, Claim> entry = itr.next();
				if (entry.getValue() != null) {
					entry.getValue().interruptBuildTask(playerOid);
				}
			}

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			Log.debug("LOGOUT: voxel logout finished for: " + playerOid);
			return true;
		}
	}

	class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();

			try {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "builder_settings");
				int count = 0;
				HashMap<Integer, String> chaice = RequirementChecker.getEditorOptionChoice("Claim Type");
				if (chaice != null) {
					for (Integer id : chaice.keySet()) {
						props.put("cTName" + count, chaice.get(id));
						props.put("cTId" + count, id);
						count++;
					}
				}
				props.put("cTypeNum", count);
				count = 0;
				chaice = RequirementChecker.getEditorOptionChoice("Claim Object Category");
				if (chaice != null) {
					for (Integer id : chaice.keySet()) {
						props.put("cName" + count, chaice.get(id));
						props.put("cId" + count, id);
						count++;
					}
				}
				props.put("catNum", count);

				Log.debug("sendObjectInfo props=" + props);
				TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(tmsg);
			} catch (Exception e) {
				e.printStackTrace();
				log.exception(e);
			}
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}
	
	class PropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage rMsg = (PropertyMessage) msg;
			OID oid = rMsg.getSubject();
			//log.error("PropertyHook: msg=" + rMsg);
			if (rMsg.containsKey(VoxelPlugin.BUILD_SPEED_STAT)) {
			
				Integer value = (Integer) rMsg.getProperty(VoxelPlugin.BUILD_SPEED_STAT);
				log.debug("PropertyHook: stat=" + VoxelPlugin.BUILD_SPEED_STAT+" "+value);
				Iterator<ConcurrentHashMap.Entry<Integer, Claim>> itr = claims.entrySet().iterator();
				while (itr.hasNext()) {
					ConcurrentHashMap.Entry<Integer, Claim> entry = itr.next();
					if (entry.getValue() != null) {
						entry.getValue().changeStatBuildTask(oid, value);
					}
				}
			} else if (rMsg.containsKey(CombatInfo.COMBAT_PROP_COMBATSTATE)) {
				boolean value = (boolean) rMsg.getProperty(CombatInfo.COMBAT_PROP_COMBATSTATE);
				log.debug("SetPropertyHook Props " + CombatInfo.COMBAT_PROP_COMBATSTATE + " = " + value);
				if (value) {
					Iterator<ConcurrentHashMap.Entry<Integer, Claim>> itr = claims.entrySet().iterator();
					while (itr.hasNext()) {
						ConcurrentHashMap.Entry<Integer, Claim> entry = itr.next();
						if (entry.getValue() != null) {
							entry.getValue().interruptBuildTask(oid);
						}
					}
				}
			}
			return true;
		}
	}
	
	
	class SetPropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
			// if (Log.loggingDebug)
			OID oid = rMsg.getSubject();
			if (Log.loggingDebug) {
				String sss = "";
				for (String ss : rMsg.getPropMap().keySet()) {
					sss += " | " + ss + "=" + rMsg.getProperty(ss);
				}
				log.debug("SetPropertyHook: props=" + sss + " for " + oid);
			}
			if (rMsg.containsKey(VoxelPlugin.BUILD_SPEED_STAT)) {
				//log.error("SetPropertyHook: msg=" + rMsg);
					Integer value = (Integer) rMsg.getProperty(VoxelPlugin.BUILD_SPEED_STAT);
					log.debug("SetPropertyHook: stat=" + VoxelPlugin.BUILD_SPEED_STAT+" "+value);
				Iterator<ConcurrentHashMap.Entry<Integer, Claim>> itr = claims.entrySet().iterator();
				while (itr.hasNext()) {
					ConcurrentHashMap.Entry<Integer, Claim> entry = itr.next();
					if (entry.getValue() != null) {
						entry.getValue().changeStatBuildTask(oid, value);
					}
				}
			} else if (rMsg.containsKey(CombatInfo.COMBAT_PROP_COMBATSTATE)) {
				boolean value = (boolean) rMsg.getProperty(CombatInfo.COMBAT_PROP_COMBATSTATE);
				log.debug("SetPropertyHook Props " + CombatInfo.COMBAT_PROP_COMBATSTATE + " = " + value);
				if (value) {
					Iterator<ConcurrentHashMap.Entry<Integer, Claim>> itr = claims.entrySet().iterator();
					while (itr.hasNext()) {
						ConcurrentHashMap.Entry<Integer, Claim> entry = itr.next();
						if (entry.getValue() != null) {
							entry.getValue().interruptBuildTask(oid);
						}
					}
				}
			}
			return true;
		}
	}

	class SendClimeUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SendClaimUpdateMessage gmMsg = (SendClaimUpdateMessage) msg;
			int guildId = (Integer) gmMsg.getProperty("guildId");
			
			for (Claim claim : claims.values()) {
				log.debug("SendClimeUpdateHook: cId="+claim.getID()+" igId="+claim.getInstanceGuild()+" gId="+guildId);
				if (claim.getInstanceGuild() == guildId) {
					claim.claimUpdated();
				}
			}
			return true;
		}
	}
	
	class InstanceLoadedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SubjectMessage message = (SubjectMessage) msg;
			OID instanceOid = message.getSubject();
			if(Log.loggingDebug)Log.debug("VOXEL: got instance loaded message with oid: " + instanceOid);
			InstanceInfo inst = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_ALL_INFO);
			if(Log.loggingDebug)log.debug("VOXEL: InstanceLoadedHook InstanceInfo: tmplId=" + inst.templateID + " tmplName=" + inst.templateName + " groupOid=" + inst.groupOid + " plyOid=" + inst.playerOid + " guildId=" + inst.guildId + " intType=" + inst.inctanceType);
			// if (inst.terrainConfig.)
			long ply = 0;
			if (inst.playerOid != null)
				ply = inst.playerOid.toLong();
			long guild = -1;
			if (inst.guildId > 0)
				guild = inst.guildId;

			int instanceID = (Integer) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			if(Log.loggingDebug)log.debug("InstanceLoadedHook claims:" + claims);
			List<Integer> toExcludeClaim = new ArrayList<Integer>();
			List<Integer> claimSpawned = new ArrayList<Integer>();
			log.debug("InstanceLoadedHook I Loading claims with parent id > 0");
			if (inst.inctanceType == 3 || inst.inctanceType == 5) {
				//Spawn only claims with parent
				claims.putAll(cDB.loadClaims(instanceID, ply, guild, true));
				ArrayList<Claim> claimList = new ArrayList<Claim>(claims.values());
				if(Log.loggingDebug)log.debug("InstanceLoadedHook I claimList:" + claimList);

				for (Claim claim : claimList) {
					if(Log.loggingDebug)log.debug("InstanceLoadedHook I claims " + claim.getID() + " " + claim.getInstanceID() + " == " + instanceID + " && " + claim.getInstanceOwner() + " == " + ply + " && " + claim.getInstanceGuild() + " == " + guild);
					if (claim.getInstanceID() == instanceID && claim.getInstanceOwner() == ply && claim.getInstanceGuild() == guild && claim.getParentId() > 0) {
						if(Log.loggingDebug)log.debug("InstanceLoadedHook I spawn :" + instanceOid + " ID:" + claim.getID() + " " + claim.getName());
						if ((ply > 0 || guild > 0) && claim.getParentId() > 0) {
							claim.spawn(instanceOid);
							claimSpawned.add(claim.getID());
							if(Log.loggingDebug)	log.debug("InstanceLoadedHook I spwan claim.id()=" + claim.getID() + " claim.getParentId():" + claim.getParentId());
							if (!toExcludeClaim.contains(claim.getParentId())) {
								toExcludeClaim.add(claim.getParentId());
							}
						} else if (ply == 0 && guild == -1) {
							if(Log.loggingDebug)	log.debug("InstanceLoadedHook I 1 spwan claim.id()=" + claim.getID() + " claim.getParentId():" + claim.getParentId());
							claim.spawn(instanceOid);
							claimSpawned.add(claim.getID());
						}
					}
				}
			}
			if(Log.loggingDebug)log.debug("InstanceLoadedHook II toExcludeClaim:" + toExcludeClaim);
			log.debug("InstanceLoadedHook II Loading claims parent id =-1");

			claims.putAll(cDB.loadClaims(instanceID, ply, guild, false));
			ArrayList<Claim> claimList = new ArrayList<Claim>(claims.values());
			for (Claim claim : claimList) {
				if(Log.loggingDebug)log.debug("InstanceLoadedHook II claims " + claim.getID() + " " + claim.getInstanceID() + " == " + instanceID + " && " + claim.getInstanceOwner() + " == " + ply + " && " + claim.getInstanceGuild() + " == " + guild);
				if (claim.getInstanceID() == instanceID && claim.getInstanceOwner() == ply && claim.getInstanceGuild() == guild && claim.getParentId() == -1) {
					if(Log.loggingDebug)log.debug("InstanceLoadedHook II spawn :" + instanceOid + " ID:" + claim.getID() + " " + claim.getName());
					if (!toExcludeClaim.contains(claim.getID()) && !claimSpawned.contains(claim.getID())) {
						claim.spawn(instanceOid);
						claimSpawned.add(claim.getID());
						if(Log.loggingDebug)log.debug("InstanceLoadedHook II spwan claim.id()=" + claim.getID() + " claim.getParentId():" + claim.getParentId());
						if (!toExcludeClaim.contains(claim.getParentId())) {
							toExcludeClaim.add(claim.getParentId());
						}
					}
				}
			}
			if(Log.loggingDebug)	log.debug("InstanceLoadedHook III toExcludeClaim:" + toExcludeClaim);
			log.debug("InstanceLoadedHook III Loading claims for all guild adn private instance and not bought");
			if (inst.inctanceType == 3 || inst.inctanceType == 5) {
				if(Log.loggingDebug)log.debug("InstanceLoadedHook III claims:" + claims);
				claims.putAll(cDB.loadClaims(instanceID, 0, -1, false));
				ArrayList<Claim> newclaimList = new ArrayList<Claim>(claims.values());
				if(Log.loggingDebug)log.debug("InstanceLoadedHook III newclaimList:" + newclaimList);
				for (Claim claim : newclaimList) {
					if(Log.loggingDebug)log.debug("InstanceLoadedHook III claims " + claim.getID() + " " + claim.getInstanceID() + " == " + instanceID);
					if (claim.getInstanceID() == instanceID && claim.getInstanceOwner() == 0 && claim.getInstanceGuild()==-1 && !toExcludeClaim.contains(claim.getID()) && !claimSpawned.contains(claim.getID())) {
						if(Log.loggingDebug)log.debug("InstanceLoadedHook III spawn :" + instanceOid + " ID:" + claim.getID() + " Name:" + claim.getName());
						claim.spawn(instanceOid);
					}
				}
			}
			log.debug("InstanceLoadedHook end loading claims");

			return true;
		}
	}

	class SpawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			if(Log.loggingDebug)	Log.debug("SPAWNED: getting claims for player: " + objOid);
			
			if (spawnedMsg.getType() != null && spawnedMsg.getType().isPlayer()) {
				// Set the players world property
				if(Log.loggingDebug)Log.debug("SPAWNED: getting claims for player: " + objOid);
				OID instanceOid = spawnedMsg.getInstanceOid();
			//	int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
				// OID accountID = (OID) EnginePlugin.getObjectProperty(objOid, WorldManagerClient.NAMESPACE, "accountId");
				OID accountID = Engine.getDatabase().getAccountOid(objOid);
				InstanceInfo inst = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_ALL_INFO);
				int instanceID = inst.templateID;
				long ply = 0;
				if (inst.playerOid != null)
					ply = inst.playerOid.toLong();
				// long guild =0;
				// if (inst.guildOid!=null)guild = inst.guildOid.toLong();

				if(Log.loggingDebug)	Log.debug("SPAWNED: getting claims for player: " + objOid + " " + instanceOid + " " + instanceID + " " + accountID);
				// Check for claims within their range at login
				ObjectInfo oi = WorldManagerClient.getObjectInfo(objOid);
				if(oi == null){
					Log.debug("VoxelPlugin SPAWNED: can't get ObjectInfo for player: " + objOid + " " + instanceOid + " " + instanceID + " " + accountID);
					return true;
				}
				Point p = oi.loc;
				ArrayList<Claim> claimsInRange = new ArrayList<Claim>();
				Claim closestClaim = null;
				float closestRange = Float.MAX_VALUE;
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_own");
				int count=0;
				for (Claim claim : claims.values()) {
					Log.debug("SPAWNED: instance=" + claim.getInstanceID() + " id=" + claim.getID() + " owner=" + claim.getOwner() + " player=" + objOid+" accountID="+accountID);
					if (claim.getOwner() != null && (claim.getOwner().equals(objOid) || claim.getOwner().equals(accountID))) {
						props.put("id" + count, claim.getID());
						props.put("name" + count, claim.getName());
						props.put("time" + count, claim.getTaxPaidUntil() - System.currentTimeMillis());
						int taxCurrency = claim.getTaxCurrency();
						long taxAmount = claim.getTaxAmount();
						long taxInterval = claim.getTaxInterval();
						long taxPeriodPay = claim.getTaxPeriodPay();
						long taxPeriodSell = claim.getTaxPeriodSell();
						if (claim.getUpgrade() > 0) {
							ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade());
							taxCurrency = cu.taxCurrency;
							taxAmount = cu.taxAmount;
							taxInterval = cu.taxInterval;
							taxPeriodPay = cu.taxPeriodPay;
							taxPeriodSell = cu.taxPeriodSell;
						}
						props.put("taxCurrency" + count, taxCurrency);
						props.put("taxAmount" + count, taxAmount);
						props.put("taxInterval" + count, taxInterval);
						props.put("taxPeriodPay" + count, taxPeriodPay);
						props.put("taxPeriodSell" + count, taxPeriodSell);
							
						// props.put("id"+count, claim.getID());
						count++;
					}
					if (claim.getInstanceID() != instanceID || claim.getInstanceOwner() != ply || claim.getInstanceGuild() != inst.guildId)
						continue;
					// if (claim.getInstanceOwner() !=ply && claim.getInstanceGuild() != guild)
					float distance = Point.distanceToXZ(p, new Point(claim.getLoc()));
					if (distance < CLAIM_DRAW_RADIUS) {
						claimsInRange.add(claim);
						if (distance < closestRange) {
							closestRange = distance;
							closestClaim = claim;
						}
					}
					// Send down claim if player owns it
					if (claim.getOwner() != null && claim.getOwner().equals(accountID)) {
						claim.sendClaimData(objOid);
					}
				}
				if(Log.loggingDebug)	Log.debug("SPAWNED: getting claims for player: " + objOid + " " + claimsInRange + " " + closestClaim);
				// Load the closest claim first
				if (closestClaim != null) {
					closestClaim.addPlayer(objOid);
				}
				for (Claim claimInRange : claimsInRange) {
					if (!claimInRange.equals(closestClaim)) {
						claimInRange.addPlayer(objOid);
					}
				}
				props.put("num", count);
				Log.debug("SpawnedHook props=" + props);
				TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
				Engine.getAgent().sendBroadcast(msgExt);
				
			}

			return true;
		}
	}

	class DespawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage spawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			if(Log.loggingDebug)	Log.debug("DESPAWNED: getting claims for player: " + objOid);
			
			// OID instanceOid = spawnedMsg.getInstanceOid();
			// String world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_NAME).templateName;
			// Check if there are any voxelands in their world
//			for (Claim claim : claims.values()) {
//				claim.removePlayer(objOid, true);
//			}
			claims.forEach((k,v)->{
				v.removePlayer(objOid,true);
				v.objectsStance.remove(objOid);
			});

			return true;
		}
	}

    /**
	 * Called when a player tries to create a claim. Checks if the claim can be placed (i.e. not too close to another claim).
	 * If the player can make the claim it will save the data to the database and spawn the claim.
	 * @author Andrew Harrison
	 *
	 */
	class CreateClaimHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            if (Log.loggingTrace) log.trace("CLAIM: got create building");
            String name = (String)gridMsg.getProperty("name");
            AOVector locVector = (AOVector)gridMsg.getProperty("loc");
            int size = (Integer)gridMsg.getProperty("size");
            boolean forSale = (Boolean)gridMsg.getProperty("forSale");
            long cost = (Long)gridMsg.getProperty("cost");
            int currency = (Integer)gridMsg.getProperty("currency");
			boolean owned = (Boolean) gridMsg.getProperty("owned");
			int claimType = RequirementChecker.getIdEditorOptionChoice("Claim Type", "Any");
			if (gridMsg.getPropertyMapRef().keySet().contains("claimType")) {
				claimType = (Integer) gridMsg.getProperty("claimType");
			} else {
				log.debug("CreateClaimHook not claimType in param message");
			}
			if (gridMsg.getPropertyMapRef().keySet().contains("c")) {
				String claimTypeName = (String) gridMsg.getProperty("claimTypeName");
				claimType = RequirementChecker.getIdEditorOptionChoice("Claim Type", claimTypeName);

			}
            
			 int taxCurrency = (Integer)gridMsg.getProperty("taxCurrency");
			 long taxAmount = (long)gridMsg.getProperty("taxAmount");
			 long taxInterval = (long)gridMsg.getProperty("taxInterval");
			 long taxTimePay = (long)gridMsg.getProperty("taxTimePay");
			 long taxTimeSell = (long)gridMsg.getProperty("taxTimeSell");
				
			
			
            
            OID itemOID = null;
            Serializable itemProp = gridMsg.getProperty("item");
            if (itemProp != null) {
            	itemOID = (OID) itemProp;
            }
            int itemID = -1;
            Integer claimTemplateItem = (Integer)gridMsg.getProperty("claimTemplateItem");
            if (claimTemplateItem != null) {
            	itemID = claimTemplateItem;
            }
           		
            // Check the player can place/update the building
            int adminLevel = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "adminLevel");
        	if (adminLevel != AgisLoginPlugin.ACCOUNT_ADMIN) {
        		// If the user isn't an admin - have they provided an item?
        		if (itemOID == null) {
        			return true;
        		}
        		if (playersInNoBuild.contains(playerOid)) {
        			ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot create a claim here");
        			return true;
        		}
        	/*	for (Claim claim : claims.values()) {
                	if (claim.getInstanceOID().equals(instanceOid)) {
                		// In the same instance, is it within the claim size limit?
						log.error("Claim: Position:" + locVector + " size:" + size + " claim.getLoc():" + claim.getLoc() + " claim.getSize():" + claim.getSize()
								+ " DISTANCE_REQ_BETWEEN_CLAIMS:" + DISTANCE_REQ_BETWEEN_CLAIMS + " distance:" + Point.distanceToXZ(new Point(locVector), new Point(claim.getLoc()))
								+ " " + (claim.getSize() + size + DISTANCE_REQ_BETWEEN_CLAIMS));
						if (Point.distanceToXZ(new Point(locVector), new Point(claim.getLoc())) < (claim.getSize() + size + DISTANCE_REQ_BETWEEN_CLAIMS)) {
                			ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot place a claim within " + DISTANCE_REQ_BETWEEN_CLAIMS 
                					+ " metres of another");
                			return true;
                		}
                	}
                }*/
        	}
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			int instanceID = (Integer) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			InstanceTemplate tmpl = InstanceClient.getInstanceTemplate(instanceID);

			OID instanceOwner = (OID) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_PLAYER_OID).playerOid;
			int guildId 	= (Integer) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_GUILD_OID).guildId;
			
			if(Log.loggingDebug)	log.debug("Create Claim: instanceOid= " + instanceOid+" instanceID="+instanceID+" instanceOwner="+instanceOwner+" guildId="+guildId);
			if (tmpl.getIslandType() != 0 && tmpl.getIslandType() != 3 && tmpl.getIslandType() != 5 && adminLevel != AgisLoginPlugin.ACCOUNT_ADMIN) {
				if(Log.loggingDebug)	log.debug("Claim: No admin User Can't Create Clime in instance type other than World, Single Private and Guild Private");
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot create a claim here");
				return true;
			}
			if(Log.loggingDebug)log.debug("Claim: claims="+claims.size());
			for (Claim claim : claims.values()) {
				if(Log.loggingDebug)log.debug("Create Claim: claim= " + claim);
				if (claim != null)
					if (claim.getInstanceID() == instanceID && claim.getInstanceGuild() == guildId && 
					((instanceOwner != null && claim.getInstanceOwner() == instanceOwner.toLong()) || (claim.getInstanceOwner() == 0 && instanceOwner == null))) {
						// In the same instance, is it within the claim size limit?
						AOVector vec = AOVector.sub(locVector, claim.getLoc());
						float angle = (float) Math.toDegrees(Math.atan2(vec.getY(), vec.getX()));

						if (angle < 0) {
							angle += 360;
						}
						// float angle = locVector.sub(claim.getLoc());
						float angle45 = (angle % 45f) / 45f;
						// FIXME Check distance between claims
						if(Log.loggingDebug)
						log.debug("Claim: "+claim.getName()+" "+ claim.getID()+" Position:" + locVector + " size:" + size + " sq:" + Math.sqrt(size / 2 * size / 2 + size / 2 * size / 2) + " claim.getLoc():" + claim.getLoc() + " claim.getSize():" + claim.getSize() + " sq:"
								+ Math.sqrt(claim.getSize() / 2 * claim.getSize() / 2 + claim.getSize() / 2 * claim.getSize() / 2) + " DISTANCE_REQ_BETWEEN_CLAIMS:" + DISTANCE_REQ_BETWEEN_CLAIMS + " distance:"
								+ Point.distanceToXZ(new Point(locVector), new Point(claim.getLoc())) + " "
								+ (claim.getSize() / 2 + (Math.sqrt(claim.getSize() / 2 + claim.getSize() / 2) - claim.getSize() / 2) * angle45 + (Math.sqrt(size / 2 + size / 2) - size / 2) * angle45 + size / 2 + DISTANCE_REQ_BETWEEN_CLAIMS)
								+ " angle45:" + angle45);

						if (Point.distanceToXZ(new Point(locVector), new Point(claim.getLoc())) < 
								(claim.getSize() / 2 + (Math.sqrt(claim.getSize() / 2 * claim.getSize() / 2 + claim.getSize() / 2 * claim.getSize() / 2) - claim.getSize() / 2) * angle45
										+ (Math.sqrt(size / 2 * size / 2 + size / 2 * size / 2) - size / 2) * angle45 + size / 2 + DISTANCE_REQ_BETWEEN_CLAIMS)) {
							ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot place a claim within " + DISTANCE_REQ_BETWEEN_CLAIMS + " metres of another");
							log.debug("Create Claim: new claim is to close to another claim");
							return true;
						}
					}
			}
            // Update grid both locally and in the database
            Claim newClaim = new Claim();
            newClaim.setName(name);
            newClaim.setLoc(locVector);
            newClaim.setSize(size);
            newClaim.setSizeZ(size);
            newClaim.setSizeY(size);
              newClaim.setSellerName(WorldManagerClient.getObjectInfo(playerOid).name);
            newClaim.setForSale(forSale);
            newClaim.setCost(cost);
            newClaim.setOrgCost(cost);
            newClaim.setCurrency(currency);
            newClaim.setOrgCurrency(currency);
            newClaim.setClaimItemTemplate(itemID);
            newClaim.setClaimType(claimType);
            newClaim.setInstanceID(instanceID);
            
            newClaim.setTaxCurrency(taxCurrency);
            newClaim.setTaxAmount(taxAmount);
            newClaim.setTaxInterval(taxInterval);
            newClaim.setTaxPeriodPay(taxTimePay);
            newClaim.setTaxPeriodSell(taxTimeSell);
            
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            if (owned) {
				newClaim.setTaxPaidUntil(System.currentTimeMillis()+ taxInterval * 3600L * 1000L);
				newClaim.setBondPaidUntil(System.currentTimeMillis()+ taxInterval * 3600L * 1000L);
            	newClaim.setOwner(accountID);
            }else {
            	newClaim.setOwner(OID.fromLong(0l));
				newClaim.setTaxPaidUntil(System.currentTimeMillis());
				newClaim.setBondPaidUntil(System.currentTimeMillis());
            }
            newClaim.setInstanceOID(instanceOid);
            
          //  int instanceID = (Integer)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
         //   OID instanceOwner = (OID)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_PLAYER_OID).playerOid;
         //   int guildOid = (Integer)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_GUILD_OID).guildOid;
            long ply =0;
            if (instanceOwner!=null)ply = instanceOwner.toLong();
          //  long guild =0;
           // if (guildOid!=null)guild = guildOid.toLong();
            
			int claimID = 0;
			 if (owned)
				 claimID = cDB.writeClaim(newClaim, instanceID, ply, guildId);
			 else
				 claimID = cDB.writeClaim(newClaim, instanceID, 0, -1);
      
            newClaim.setAccountDatabase(cDB);
            if(Log.loggingDebug)  log.debug("Claim: before put claims="+claims.size()+" claimID="+claimID);
            claims.put(claimID, newClaim);
            if(Log.loggingDebug) log.debug("Claim: after put claims="+claims.size());
            if (Log.loggingTrace) log.trace("CLAIM: updated database");
            // Spawn Claim
            newClaim.spawn();
            newClaim.sendClaimData(playerOid);
            
            // Send down claim result
            Map<String, Serializable> props = new HashMap<String, Serializable>();
    		props.put("ext_msg_subtype", "claim_made");
    		props.put("claimID", claimID);
    		props.put("claimName", name);
    		props.put("claimLoc", locVector);
    		props.put("claimArea", size);
    		props.put("forSale", forSale);
    		props.put("claimtype", claimType);
    		if (forSale) {
    			props.put("cost", cost);
    			props.put("currency", currency);
    		}
    		
			TargetedExtensionMessage temsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	   		Engine.getAgent().sendBroadcast(temsg);
    		
    		// Send item remove message
    		if (itemOID != null)
    			AgisInventoryClient.removeSpecificItem(playerOid, itemOID, false, 1);
    		
    		//TODO: Generate claim stone and give it to the player
    		/*HashMap<String, Serializable> itemProps = new HashMap<String, Serializable>();
    		itemProps.put("teleportLoc", new Point(locVector));
    		String instanceName = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_NAME).templateName;
    		itemProps.put("teleportInstance", instanceName);
    		AgisInventoryClient.generateItem(playerOid, CLAIM_STONE_ITEM_ID, null, 1, itemProps);*/
            sendClaimListUpdate(playerOid);
            return true;
        }
	}
	/**
	 * Called when a player edits their claim, such as renaming it or setting it for sale.
	 * @author Andrew
	 *
	 */
	class UpgradeClaimHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got upgrade claim");
            int claimID = (Integer)gridMsg.getProperty("claimID");
            String action = (String)gridMsg.getProperty("action");
            Log.debug("CLAIM: got upgrade claim action="+action);
            // Check that the claim is for sale
            if (!claims.containsKey(claimID)) {
            	return true;
            }
            Claim claim = claims.get(claimID);
        
            OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			if(!claim.SpawnedInInstance(instanceOid)) {
				if(Log.loggingDebug)log.debug("Claim: "+claim.getID()+" Can not be Edited from instance where claim is not spawned");
				return false;
			}
		            
            // Verify the editor is the owner
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            if(Log.loggingDebug)log.debug("EditClaimHook perm "+claim.getPlayerPermission(playerOid,accountID)+" owner perm "+claim.getPlayerPermission(playerOid,accountID));
            if(claim.getPlayerPermission(playerOid,accountID)<Claim.PERMISSION_OWNER)
            //if (!claim.getOwner().equals(accountID)) 
            {
           		Log.debug("CLAIM: user cannot edit this claim");
        		ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
        		return true;
            }

			if (action.equals("get")) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_upgrade");
				props.put("claimID", claim.getID());
				ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade() + 1);
				props.put("sizeX", cu.size.getX());
				props.put("sizeY", cu.size.getY());
				props.put("sizeZ", cu.size.getZ());
				props.put("locX", cu.loc.getX());
				props.put("locY", cu.loc.getY());
				props.put("locZ", cu.loc.getZ());
				props.put("cost", cu.cost);
				props.put("currency", cu.currency);
				props.put("items", cu.items);
				if (Log.loggingDebug)
					Log.debug("Claim sendClaimData: " + props);
				TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msgExt);
			} else if (action.equals("upgrade")) {
				ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade() + 1);
				if (cu.itemReqs.size() > 0) {
					OID itemOid = null;
					int usedTemplateID = -1;
					if (Log.loggingDebug)
						log.debug("playerOid:" + playerOid + ", Items:" + cu.itemReqs.size() + "");
					for (int templateID : cu.itemReqs) {
						itemOid = InventoryClient.findItem(playerOid, templateID);
						if (itemOid != null) {
							usedTemplateID = templateID;
							break;
						}
					}
					if (itemOid == null) {
						ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient funds to upgrade this claim");
						return true;
					}
					// claim.setClaimItemTemplate(usedTemplateID);
					// Remove the item
					AgisInventoryClient.removeGenericItem(playerOid, usedTemplateID, false, 1);
				}
				if (Log.loggingDebug)
					log.debug("playerOid:" + playerOid + ", Curency:" + cu.currency + ", cost: " + cu.cost);
				// Check if the user has enough of the currency to purchase it
				boolean canAfford = AgisInventoryClient.checkCurrency(playerOid, cu.currency, cu.cost);
				if (!canAfford) {
					ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient funds to upgrade this claim");
					return true;
				}

				AgisInventoryClient.alterCurrency(playerOid, cu.currency, -1 * cu.cost);
				
				claim.setUpgrade(claim.getUpgrade()+1);
				claim.setUpgradeDiffloc(cu.loc);
				EnginePlugin.setObjectPropertyNoResponse(claim.getObjectOID(), WorldManagerClient.NAMESPACE, "scale", cu.size);
				EnginePlugin.setObjectPropertyNoResponse(claim.getObjectOID(), WorldManagerClient.NAMESPACE, "diffLoc", cu.loc);
				AOVector nloc = AOVector.add(claim.getLoc(),cu.loc);
				EnginePlugin.setObjectPropertyNoResponse(claim.getObjectOID(), WorldManagerClient.NAMESPACE, "loc",nloc);
				cDB.updateClaim(claim);
				claim.claimUpdated(playerOid);
				/*Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_size_update");
				props.put("claimID", claim.getID());
				props.put("sizeX", cu.size.getX());
				props.put("sizeY", cu.size.getY());
				props.put("sizeZ", cu.size.getZ());
				props.put("locX", cu.loc.getX());
				props.put("locY", cu.loc.getY());
				props.put("locZ", cu.loc.getZ());
				if (Log.loggingDebug)
					Log.debug("Claim sendClaimData: " + props);
				TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msgExt);*/
				
			}
			
          //  cDB.updateClaim(claim);
            //claim.claimUpdated(playerOid);
            Log.debug("CLAIM: updated database");
            
            return true;
        }
	}
	
	/**
	 * Called when a player edits their claim, such as renaming it or setting it for sale.
	 * @author Andrew
	 *
	 */
	class EditClaimHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got edit claim");
            int claimID = (Integer)gridMsg.getProperty("claimID");
            String name = (String)gridMsg.getProperty("name");
            boolean forSale = (Boolean)gridMsg.getProperty("forSale");
            long cost = (long)gridMsg.getProperty("cost");
            int currency = (Integer)gridMsg.getProperty("currency");

            // Check that the claim is for sale
            if (!claims.containsKey(claimID)) {
            	return true;
            }
            Claim claim = claims.get(claimID);
        
            OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			if(!claim.SpawnedInInstance(instanceOid)) {
				if(Log.loggingDebug)log.debug("Claim: "+claim.getID()+" Can not be Edited from instance where claim is not spawned");
				return false;
			}
		            
            // Verify the editor is the owner
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
			if (Log.loggingDebug)
				log.debug("EditClaimHook perm " + claim.getPlayerPermission(playerOid, accountID) + " owner perm " + claim.getPlayerPermission(playerOid, accountID));
			if (claim.getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_OWNER)
			   //if (!claim.getOwner().equals(accountID)) 
            {
           		Log.debug("CLAIM: user cannot edit this claim");
        		ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
        		return true;
            }
            claim.setName(name);
            claim.setForSale(forSale);
            if(cost<1)
            	cost=1;
            claim.setCost(cost);
            claim.setCurrency(currency);
            if  (forSale) {
            	claim.setSellerName(WorldManagerClient.getObjectInfo(playerOid).name);
            }
            
            if (gridMsg.getProperty("taxDeedOID") != null) {
            	OID taxDeedID = (OID)gridMsg.getProperty("taxDeedOID");
            	int taxDeedTemplate = (Integer)gridMsg.getProperty("taxDeedTemplate");
            	claim.setBondItemTemplate(taxDeedTemplate);
            	
            	//TODO: get taxTime from item - for now just rely on client :)
            	int extraDays = (Integer)gridMsg.getProperty("days");
            	int extraHours = extraDays * 24;
            	if (claim.getClaimType() == 4) {
            		// Farm
            		extraHours *= 2;
            	} else if (claim.getClaimType() == 8) {
            		// Castle
            		extraHours /= 8;
            	} else {
            		// Residential, check deed
            		if (claim.getClaimItemTemplate() == 140) {
            			extraHours /= 4;
            		} else if(claim.getClaimItemTemplate() == 86) {
            			extraHours /= 2;
            		}
            	}
            	// Time visualization
            	//   new bond time(extraHours)  tax time before bond is added(get before adding new hours)
            	//        start | bondPaidUntil    |         taxPaidUntil
            	//         v    V      v           V             v
            	//         |<--------->|<----------------------->|
            	//                 v final time after bond v 
            	//          <----------------------------------->
            	// Want bondPaidUntil so subtract tax time from final time.
            	Calendar cal = Calendar.getInstance();
            	cal.setTimeInMillis(claim.getTaxPaidUntil());
            	// taxTime before adding time
            	long taxTime = cal.getTimeInMillis();
            	cal.add(Calendar.HOUR, extraHours);
            	// save final time with new hours
            	long totalTime = cal.getTimeInMillis();
            	// new bond time = now + added hours
            	cal = Calendar.getInstance();
            	cal.add(Calendar.HOUR, extraHours);
            	long bondTime = cal.getTimeInMillis();
            	claim.setBondPaidUntil(bondTime);
            	claim.setTaxPaidUntil(totalTime);
            	AgisInventoryClient.removeSpecificItem(playerOid, taxDeedID, false, 1);
            }
            
            cDB.updateClaim(claim);
            claim.claimUpdated(playerOid);
            Log.debug("CLAIM: updated database");
            
            return true;
        }
	}
	
	
	
	class ClaimTaxPaymentHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            Log.debug("ClaimTaxPaymentHook: got claim Tax Payment");
            OID playerOid = gridMsg.getSubject();
            int claimID = (Integer)gridMsg.getProperty("claimID");
         
            Log.debug("ClaimTaxPaymentHook: playerOid="+playerOid+" claimID="+claimID+" ");
            
            // Check that the claim is for sale
            if (!claims.containsKey(claimID)) {
            	 Log.debug("ClaimTaxPaymentHook: claim not found");
            	return true;
            }
            Claim claim = claims.get(claimID);
         
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            
            if(Log.loggingDebug)log.debug("ClaimTaxPaymentHook perm "+claim.getPlayerPermission(playerOid,accountID)+" owner perm "+claim.getPlayerPermission(playerOid,accountID));
			if (claim.getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_OWNER)  {
           		Log.debug("ClaimTaxPaymentHook: user cannot pay for this claim");
        		ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
        		return true;
            }

			int taxCurrency = claim.getTaxCurrency();
			long taxAmount = claim.getTaxAmount();
			long taxInterval = claim.getTaxInterval();
			long taxPeriodPay = claim.getTaxPeriodPay();
			if (claim.getUpgrade() > 0) {
				ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade());
				taxCurrency = cu.taxCurrency;
				taxAmount = cu.taxAmount;
				taxInterval = cu.taxInterval;
				taxPeriodPay = cu.taxPeriodPay;
			}
			if (claim.getTaxPaidUntil() - System.currentTimeMillis() > taxPeriodPay * 3600L * 1000L) {
				Log.debug("ClaimTaxPaymentHook: user cannot pay for this claim - time limit");
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You can't pay tax yet");
				return true;
			}
			
		     String action = (String)gridMsg.getProperty("action");
	            Log.debug("ClaimTaxPaymentHook: got upgrade claim action="+action);
	         

			if (action.equals("get")) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_tax_pay");
				props.put("claimID", claim.getID());
				props.put("cost", taxAmount);
				props.put("currency", taxCurrency);
				props.put("time", taxInterval);

				if (Log.loggingDebug)
					Log.debug("ClaimTaxPaymentHook: get " + props);
				TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msgExt);
			} else if (action.equals("pay")) {
				
				boolean canAfford = AgisInventoryClient.checkCurrency(playerOid, taxCurrency, taxAmount);
				if (!canAfford) {
					Log.debug("ClaimTaxPaymentHook: Insufficient funds to pay tax for this claim");
					ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient funds to pay tax for this claim");
					return true;
				}
				AgisInventoryClient.alterCurrency(playerOid, taxCurrency, -taxAmount);
				long time = 0L;
				if (claim.getTaxPaidUntil() < System.currentTimeMillis()) {
					time = System.currentTimeMillis() + taxInterval * 3600L * 1000L;
				} else {
					time = claim.getTaxPaidUntil() + taxInterval * 3600L * 1000L;
				}
				claim.setTaxPaidUntil(time);
	            cDB.updateClaim(claim);
	            claim.claimUpdated(playerOid);

	            
	            Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_own");
				int count=0;
				for (Claim _claim : claims.values()) {
					Log.debug("ClaimTaxPaymentHook: instance=" + _claim.getInstanceID() + " id=" + _claim.getID() + " owner=" + _claim.getOwner() + " player=" + playerOid+" accountID="+accountID);
					if (_claim.getOwner() != null && (_claim.getOwner().equals(playerOid) || _claim.getOwner().equals(accountID))) {
						props.put("id" + count, _claim.getID());
						props.put("name" + count, _claim.getName());
						props.put("time" + count, _claim.getTaxPaidUntil() - System.currentTimeMillis());
						  taxCurrency = _claim.getTaxCurrency();
						 taxAmount = _claim.getTaxAmount();
						 taxInterval = _claim.getTaxInterval();
						 taxPeriodPay = _claim.getTaxPeriodPay();
						long taxPeriodSell = _claim.getTaxPeriodSell();
						if (_claim.getUpgrade() > 0) {
							ClaimUpgrade cu = _claim.upgrades.get(_claim.getUpgrade());
							taxCurrency = cu.taxCurrency;
							taxAmount = cu.taxAmount;
							taxInterval = cu.taxInterval;
							taxPeriodPay = cu.taxPeriodPay;
							taxPeriodSell = cu.taxPeriodSell;
						}
						props.put("taxCurrency" + count, taxCurrency);
						props.put("taxAmount" + count, taxAmount);
						props.put("taxInterval" + count, taxInterval);
						props.put("taxPeriodPay" + count, taxPeriodPay);
						props.put("taxPeriodSell" + count, taxPeriodSell);
						// props.put("id"+count, claim.getID());
						count++;
					}
				}
				props.put("num", count);
				Log.debug("ClaimTaxPaymentHook pay props=" + props);
				TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msgExt);
			}
           
            Log.debug("ClaimTaxPaymentHook: End");
            
            return true;
        }
	}
	
	
	/**
	 * Called when a player tries to purchase a claim. Checks if the claim is for sale and that they are capable to cover the cost.
	 * If the player can buy the claim it will update the data in the database and claim.
	 * @author Andrew
	 *
	 */
	class PurchaseClaimHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got purchase claim");
            int claimID = (Integer)gridMsg.getProperty("claimID");

            // Check that the claim is for sale
            if (!claims.containsKey(claimID)) {
            	return true;
            }
            Claim claim = claims.get(claimID);
            
            if (!claim.getForSale()) {
            	return true;
            }
            
            int taxCurrency = claim.getTaxCurrency();
			long taxAmount = claim.getTaxAmount();
			long taxInterval = claim.getTaxInterval();
			long taxPeriodPay = claim.getTaxPeriodPay();
			long taxPeriodSell = claim.getTaxPeriodSell();
			if (claim.getUpgrade() > 0) {
				ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade());
				taxCurrency = cu.taxCurrency;
				taxAmount = cu.taxAmount;
				taxInterval = cu.taxInterval;
				taxPeriodPay = cu.taxPeriodPay;
				taxPeriodSell = cu.taxPeriodSell;
			}
			if(claim.getTaxPaidUntil() - System.currentTimeMillis() > 0 && claim.getTaxPaidUntil() - System.currentTimeMillis() < taxPeriodSell) {
				if(Log.loggingDebug)	log.debug("Claim: "+claim.getID()+" You cannot buy the claim just before the end of the tax paid ");
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot buy the claim just before the end of the tax paid");
				return false;
			}
            
            
            
        	OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			if(!claim.SpawnedInInstance(instanceOid)) {
				if(Log.loggingDebug)	log.debug("Claim: "+claim.getID()+" Can not be purchased from instance where claim is not spawned");
				return false;
			}
				
            if (claim.getPurchaseItemReqs().size() > 0) {
            	OID itemOid = null;
            	int usedTemplateID = -1;
            	if(Log.loggingDebug)log.debug("playerOid:"+ playerOid+", Items:"+claim.getPurchaseItemReqs()+"");
                for (int templateID : claim.getPurchaseItemReqs()) {
            		itemOid = InventoryClient.findItem(playerOid, templateID);
                	if (itemOid != null) {
                		usedTemplateID = templateID;
                		break;
                	}
            	}
            	if (itemOid == null) {
            		ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient funds to purchase this claim");
                	return true;
            	}
            	claim.setClaimItemTemplate(usedTemplateID);
            	// Remove the item
            	AgisInventoryClient.removeGenericItem(playerOid, usedTemplateID, false, 1);
            } else {
            	if(Log.loggingDebug)log.debug("playerOid:"+ playerOid+", Curency:"+claim.getCurrency()+", cost: "+claim.getCost());
            	// Check if the user has enough of the currency to purchase it
                boolean canAfford = AgisInventoryClient.checkCurrency(playerOid, claim.getCurrency(), claim.getCost());
                if (!canAfford) {
                	ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient funds to purchase this claim");
                	return true;
                }
                
                // Send a mail item to take the money from the player and give it to the seller
                if (claim.getSellerName() != null && !claim.getSellerName().equals("")) {
                	Log.debug("MAIL: going to send claim purchased");
                	String message = WorldManagerClient.getObjectInfo(playerOid).name + " has purchased your claim: " 
                		+ claim.getName() + ". Your payment is attached.";
                	AgisInventoryClient.sendMail(playerOid, claim.getSellerName(), "Claim sold", message, claim.getCurrency(), claim.getCost(), false);
                } else {
                	AgisInventoryClient.alterCurrency(playerOid, claim.getCurrency(), -1 * claim.getCost());
                }
            }
           // claim.getInstanceID()
            InstanceTemplate tmpl = InstanceClient.getInstanceTemplate(claim.getInstanceID());

          
			
            
            
            //Check if instanceType is SingleInstance (3) or GuildInstance (5)
			if (tmpl.getIslandType() == 3 || tmpl.getIslandType() == 5) {
			int guildId = (Integer) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_GUILD_OID).guildId;
				if (tmpl.getIslandType() == 5) {
					if(cDB.checkGuildBuyClaim(claim.getID(),guildId)) {
					   	ExtendedCombatMessages.sendErrorMessage(playerOid, "This guild claim has already been bought");
						return false;
					}
				}
				int instanceID = (Integer) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
				OID instanceOwner = (OID) InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_PLAYER_OID).playerOid;
			
				
				Claim newClaim = new Claim();
				newClaim.setName(claim.getName());
				newClaim.setLoc(claim.getLoc());
				newClaim.setSize(claim.getSize());
				newClaim.setSizeZ(claim.getSizeZ());
				newClaim.setSizeY(claim.getSizeY());
				newClaim.setSellerName(WorldManagerClient.getObjectInfo(playerOid).name);
				newClaim.setForSale(false);
				newClaim.setCost(claim.getCost());
				newClaim.setCurrency(claim.getCurrency());
				newClaim.setClaimItemTemplate(claim.getClaimItemTemplate());
				newClaim.setClaimType(claim.getClaimType());
				newClaim.setTaxPaidUntil(System.currentTimeMillis());
				newClaim.setBondPaidUntil(System.currentTimeMillis());
				//OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
				 OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		        if(tmpl.getIslandType() == 3)
		        	newClaim.setOwner(accountID);
				newClaim.setInstanceOID(instanceOid);
				newClaim.setParentId(claim.getID());
				
				long ply = 0;
				if (instanceOwner != null)
					ply = instanceOwner.toLong();

				/*
				  Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(System.currentTimeMillis());
				cal.add(Calendar.DATE, 7);
				
				newClaim.setTaxPaidUntil(cal.getTimeInMillis());
				*/
				long time = 0L;
				//if (claim.getTaxPaidUntil() < System.currentTimeMillis()) {
					time = System.currentTimeMillis() + taxInterval * 3600L * 1000L;
				//} else {
			//		time = claim.getTaxPaidUntil() + taxInterval * 3600L * 1000L;
			//	}
				newClaim.setTaxPaidUntil(time);
				newClaim.setBondPaidUntil(Calendar.getInstance().getTimeInMillis());
				newClaim.setBondItemTemplate(-1);
				newClaim.setInstanceGuild(guildId);
				newClaim.setInstanceOwner(ply);
				int cID = cDB.writeClaim(newClaim, instanceID, ply, guildId);
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_deleted");
				props.put("claimID", claim.getID());
				
				for (OID pOid : claim.getPlayerInRange(instanceOid)) {
					TargetedExtensionMessage temsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, pOid, pOid, false, props);
					Engine.getAgent().sendBroadcast(temsg);
				}
		
				newClaim.setAccountDatabase(cDB);
				claims.put(cID, newClaim);
				if (Log.loggingTrace)
					log.trace("CLAIM: updated database");
				// Spawn Claim
				newClaim.spawn(instanceOid);
				newClaim.sendClaimData(playerOid);
				claim.Despawn(instanceOid);
				// Send down claim result
				 props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "claim_made");
				props.put("claimID", cID);
				props.put("claimName", newClaim.getName());
				props.put("claimLoc", newClaim.getLoc());
				props.put("claimArea", newClaim.getSize());
				props.put("forSale", newClaim.getForSale());
				props.put("claimtype", newClaim.getClaimType());
				if (newClaim.getForSale()) {
					props.put("cost", newClaim.getCost());
					props.put("currency", newClaim.getCurrency());
				}
				for (OID pOid : claim.getPlayerInRange(instanceOid)) {
				TargetedExtensionMessage temsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, pOid, pOid, false, props);
				Engine.getAgent().sendBroadcast(temsg);
				}
			} else {
				// Give the user a free week of a claim
				/*Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(System.currentTimeMillis());
				cal.add(Calendar.DATE, 7);
				claim.setTaxPaidUntil(cal.getTimeInMillis());
				*/
				long time = 0L;
				
				if (claim.getTaxPaidUntil() < System.currentTimeMillis()) {
					time = System.currentTimeMillis() + taxInterval * 3600L * 1000L;
					claim.setTaxPaidUntil(time);
				} else {
					
				}
				
				
				claim.setBondPaidUntil(Calendar.getInstance().getTimeInMillis());
				claim.setBondItemTemplate(-1);
				// Change ownership of the claim
				//OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
				 OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		           	claim.changeClaimOwner(playerOid, accountID, null);
				// Save to the database
				cDB.updateClaim(claim);
			}
		      
            Log.debug("CLAIM: updated database");
            EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.CLAIM_PURCHASED, 0,"");
           
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "claim_own");
			int count=0;
			for (Claim _claim : claims.values()) {
				Log.debug("ClaimTaxPaymentHook: instance=" + _claim.getInstanceID() + " id=" + _claim.getID() + " owner=" + _claim.getOwner() + " player=" + playerOid+" accountID="+accountID);
				if (_claim.getOwner() != null && (_claim.getOwner().equals(playerOid) || _claim.getOwner().equals(accountID))) {
					props.put("id" + count, _claim.getID());
					props.put("name" + count, _claim.getName());
					props.put("time" + count, _claim.getTaxPaidUntil() - System.currentTimeMillis());
					 taxCurrency = _claim.getTaxCurrency();
					 taxAmount = _claim.getTaxAmount();
					 taxInterval = _claim.getTaxInterval();
					 taxPeriodPay = _claim.getTaxPeriodPay();
					 taxPeriodSell = _claim.getTaxPeriodSell();
					if (_claim.getUpgrade() > 0) {
						ClaimUpgrade cu = _claim.upgrades.get(_claim.getUpgrade());
						taxCurrency = cu.taxCurrency;
						taxAmount = cu.taxAmount;
						taxInterval = cu.taxInterval;
						taxPeriodPay = cu.taxPeriodPay;
						taxPeriodSell = cu.taxPeriodSell;
					}
					props.put("taxCurrency" + count, taxCurrency);
					props.put("taxAmount" + count, taxAmount);
					props.put("taxInterval" + count, taxInterval);
					props.put("taxPeriodPay" + count, taxPeriodPay);
					props.put("taxPeriodSell" + count, taxPeriodSell);
					// props.put("id"+count, claim.getID());
					count++;
				}
			}
			props.put("num", count);
			Log.debug("ClaimTaxPaymentHook pay props=" + props);
			TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msgExt);
          
            
            return true;
        }
	}
	
	/**
	 * Called when a player tries to delete a claim. Checks if the claim can be placed (i.e. not too close to another claim).
	 * If the player can make the claim it will save the data to the database and spawn the claim.
	 * @author Andrew
	 *
	 */
	class DeleteClaimHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
			Log.debug("CLAIM: got delete claim");
			int claimID = (Integer) gridMsg.getProperty("claimID");
			if(Log.loggingDebug)	Log.debug("CLAIM: got delete claim ClaimId=" + claimID+" playerOid="+playerOid);
			if (!claims.containsKey(claimID)) {
				// No claim exists with that ID
				log.error("CLAIM: No claim exists with that ID " + claimID);
				return true;
			}
			
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			if(!claims.get(claimID).SpawnedInInstance(instanceOid)) {
				if(Log.loggingDebug)	log.debug("Claim: "+claimID+" Can not be deleted from instance where claim is not spawned instanceOid="+instanceOid);
				return false;
			}
		
	         // Check the player can place/update the building
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            int adminLevel = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "adminLevel");
        	if (adminLevel != AgisLoginPlugin.ACCOUNT_ADMIN && !(claims.get(claimID).getPlayerPermission(playerOid, accountID) == Claim.PERMISSION_OWNER)) {
        		// The player does not own this claim, and they do not have admin rights
        		Log.debug("CLAIM: user cannot delete this claim");
        		ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
                return true;
        	}
        	
        	// If abandon property exists, don't delete the claim, just remove owner
        	if (gridMsg.getProperty("abandon") != null) {
        		boolean abandon = (Boolean) gridMsg.getProperty("abandon");
        		if (abandon) {
        			claims.get(claimID).changeClaimOwner(null, null, playerOid);
                    cDB.updateClaim(claims.get(claimID));
                    return true;
        		}
        	}
        	if(claims.get(claimID).getPermanent()==true) {
        		claims.get(claimID).changeClaimOwner(null, null, playerOid);
        		claims.get(claimID).resetCost();
                cDB.updateClaim(claims.get(claimID));
                
        		return true;
        	}
        	int itemID = claims.get(claimID).getClaimItemTemplate();
        	if (itemID != -1)
        		AgisInventoryClient.generateItem(playerOid, itemID, "", 1, null);
            
        	//OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
        	if(claims.get(claimID).getParentId()>0) {
        		if(Log.loggingDebug)log.debug("Claim deleted "+claimID+" spawn parent "+claims.get(claimID).getParentId()+" for instance "+instanceOid);
        		claims.get(claims.get(claimID).getParentId()).spawn(instanceOid);
        		claims.get(claims.get(claimID).getParentId()).claimUpdated();
    			
        	}
        	
        	claims.get(claimID).claimDeleted(instanceOid);
        	claims.remove(claimID);
            cDB.deleteClaim(claimID);
			sendClaimListUpdate(playerOid);
            return true;
        }
	}
	
	/**
	 * Hook for the ClaimPermission Extension Message. Called when a player wants to 
	 * add or remove a permission for their claim.
	 * @author Andrew Harrison
	 *
	 */
	class ClaimPermissionHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage claimMsg = (ExtensionMessage)msg;
            OID playerOid = claimMsg.getSubject();
            Log.debug("CLAIM: got get claim object data");
            int claimID = (Integer)claimMsg.getProperty("claimID");
            String playerName = (String)claimMsg.getProperty("playerName");
            OID targetOid = Engine.getDatabase().getOidByName(playerName, WorldManagerClient.NAMESPACE);
            if (targetOid == null) {
            	ExtendedCombatMessages.sendErrorMessage(playerOid, "Player named " + playerName + " could not be found maybe is offline.");
            	return true;
            }
            if(Log.loggingTrace)
            	log.debug("CLAIM: ClaimPermissionHook targetOid:"+targetOid);
            OID TargetAccountID = Engine.getDatabase().getAccountOid(targetOid);
            		//(OID) EnginePlugin.getObjectProperty(targetOid, WorldManagerClient.NAMESPACE, "accountId");
            if(Log.loggingTrace)
            	log.debug("CLAIM: ClaimPermissionHook TargetAccountID:"+TargetAccountID);
            
            if (claims.get(claimID).getOwner().equals(TargetAccountID)) {
            	ExtendedCombatMessages.sendErrorMessage(playerOid, "Can't add owner to permissions.");
            	log.debug("CLAIM: ClaimPermissionHook Can't add owner to permissions.");
            	return true;
            }
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
             if(Log.loggingTrace)
            	log.debug("CLAIM: ClaimPermissionHook accountID:"+accountID);
       
            String action = (String)claimMsg.getProperty("action");
            if (action.equals("Add")) {
            	int permissionLevel = (Integer)claimMsg.getProperty("permissionLevel");
            	claims.get(claimID).addPermission(playerOid, accountID, targetOid, playerName, permissionLevel);
            } else if (action.equals("Remove")) {
            	claims.get(claimID).removePermission(playerOid, accountID, targetOid);
            }
            
            return true;
        }
	}
	
	/**
	 * Called when a player wants to perform a claim action such as building, digging or placing a prefab.
	 * It first checks if the player has the right to perform an action in the claim then adds the action to
	 * the claim.
	 * @author Andrew
	 *
	 */
	class ClaimActionHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got create building");
            int claimID = (Integer)gridMsg.getProperty("claim");
            // Is the player performing the action the owner?
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
             if (claims.get(claimID).getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_INTERACTION) {
            	return true;
            }
            String action = (String)gridMsg.getProperty("action");
            String type = (String)gridMsg.getProperty("type");
            AOVector size = (AOVector)gridMsg.getProperty("size");
            AOVector loc = (AOVector)gridMsg.getProperty("loc");
            AOVector normal = (AOVector)gridMsg.getProperty("normal");
            int material = (Integer)gridMsg.getProperty("mat");
            /*if (action.equals("build")) {
            	// Get resource being used
            	int resourceID = (Integer)gridMsg.getProperty("itemTemplateID");
            	String resource = "" + resourceID;
            	int count = (Integer)gridMsg.getProperty("count");
            	//AgisInventoryClient.removeGenericItem(playerOid, itemTemplateID, false, count);
            	HashMap<String, Integer> buildingResources = (HashMap)EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "buildingResources");
                if (buildingResources == null || !buildingResources.containsKey(resource) || buildingResources.get(resource) < count) {
                	// User does not have the required resources for this action
                	ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have enough resources to perform that action");
                	return true;
                } else {
                	int newCount = buildingResources.get(resource) - count;
                	buildingResources.put(resource, newCount);
                	EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "buildingResources", buildingResources);
                	BuildingResourceAcquireHook.sendBuildingResources(playerOid, buildingResources);
                }
            }*/
            if(Log.loggingDebug) Log.debug("CLAIM: got action for claim: " + claimID + " with action: " + action);
            // First check if it is an undo
            if (action.equals("undo")) {
            	claims.get(claimID).undoAction();
            } else {
            	claims.get(claimID).performClaimAction(action, type, size, loc, normal, material);
            }
            // Increase the masonry skill
            CombatClient.abilityUsed(playerOid, 16);
            return true;
        }
	}
	
	/**
	 * Called when a player wants to place a claim object.
	 * @author Andrew
	 *
	 */
	class PlaceClaimObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gridMsg = (ExtensionMessage) msg;
			OID playerOid = gridMsg.getSubject();
			log.debug("CLAIM: got place object");
			int claimID = (Integer) gridMsg.getProperty("claim");
			log.debug("CLAIM: got place object "+claimID);
			// Is the player performing the action the owner?
			//OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
			 OID accountID = Engine.getDatabase().getAccountOid(playerOid);
			 log.debug("CLAIM: got place object "+claims.containsKey(claimID)); 
	           if (claims.get(claimID).getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_ADD_ONLY) {
				log.debug("CLAIM: claim does not belong to the player");
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You don't have permission to build in this Claim.");
	          	return true;
			}
			int buildObjectTemplateID = (Integer) gridMsg.getProperty("buildObjectTemplateID");
			log.debug("CLAIM: Build: Building Object Template :"+buildObjectTemplateID );
			if (!buildObjectTemplates.containsKey(buildObjectTemplateID)) {
				if(Log.loggingDebug)	
					log.debug("CLAIM: Build: Building Object Template not found :"+buildObjectTemplateID );
            	ExtendedCombatMessages.sendErrorMessage(playerOid, "Can not build that object");
          		// TODO: Send error message
				return true;
			}
			if (( buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType().size() > 0 && !buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType().contains(claims.get(claimID).getClaimType()) 
					&& claims.get(claimID).getClaimType() != RequirementChecker.getIdEditorOptionChoice("Claim Type","Any") 
					&& !buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType().contains(RequirementChecker.getIdEditorOptionChoice("Claim Type","Any")))
					
					// || (claims.get(claimID).getClaimType() != buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType() && buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType() == 1)
					) 
			{
				if (Log.loggingDebug)
					log.debug("CLAIM: Build: Building Object is not valid for Claim type | claim:" + claimID + " ClaimType:" + claims.get(claimID).getClaimType() + " buildObjectTemplateID:" + buildObjectTemplateID
							+ " ValidClaimType:" + buildObjectTemplates.get(buildObjectTemplateID).getValidClaimType());
				ExtendedCombatMessages.sendErrorMessage(playerOid, "Building Object is not valid for Claim type");
				return false;
			}
			AOVector loc = (AOVector) gridMsg.getProperty("loc");
			Quaternion orient = (Quaternion) gridMsg.getProperty("orient");
			int parent = (Integer) gridMsg.getProperty("parent");
			String parents = (String) gridMsg.getProperty("parents");
			
			int itemID = (Integer) gridMsg.getProperty("itemID");
			OID itemOid = (OID) gridMsg.getProperty("itemOID");
			if(Log.loggingDebug)	
				log.debug("CLAIM: got object for claim: " + claimID + " with object: " + itemID);
			boolean addTask = claims.get(claimID).buildClaimObject(playerOid, buildObjectTemplates.get(buildObjectTemplateID), loc, orient, parent, itemID, itemOid,parents);
			if (addTask) {
				activeClaimTasks.put(playerOid, claimID);
			}
			return true;
	     }
	}
	
	/**
	 * Called when a player wants to edit a claim object.
	 * @author Andrew Harrison
	 *
	 */
	class EditClaimObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got edit object " );
            int claimID = (Integer)gridMsg.getProperty("claimID");
            int objectID = (Integer)gridMsg.getProperty("objectID");
            String action = (String)gridMsg.getProperty("action");
            if(Log.loggingDebug) 
            	Log.debug("CLAIM: got edit object playerOid="+playerOid+" claimID="+claimID+" objectID="+objectID+" action="+action );
            
            // Is the player performing the action the owner?
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            OID accountID = Engine.getDatabase().getAccountOid(playerOid);
            if (action.equals("state")) {
            	String state = (String)gridMsg.getProperty("state");
            	claims.get(claimID).updateClaimObjectState(objectID, state);
            	return true;
            } else if (action.equals("use")) {
            	claims.get(claimID).useClaimObject(playerOid, objectID);
            	return true;
            }else if (action.equals("useHelp")) {
            	claims.get(claimID).useHelpClaimObject(playerOid, objectID);
            	return true;
            }
              
            if (claims.get(claimID).getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_ADD_DELETE) {
            	Log.debug("CLAIM: Insufficient permissions");
              	ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
                return true;
            }
            if (action.equals("useRepair")) {
            	claims.get(claimID).repairClaimObject(playerOid, objectID);
            	return true;
            }
            if (action.equals("convert")) {
            	 boolean confirmed = (boolean)gridMsg.getProperty("confirmed");
                 
            	claims.get(claimID).removeClaimObject(playerOid, objectID,confirmed);
            } else if (action.equals("save")) {
            	AOVector loc = (AOVector)gridMsg.getProperty("loc");
                Quaternion orient = (Quaternion)gridMsg.getProperty("orient");
                int parent = (Integer)gridMsg.getProperty("parent");
                boolean confirmed = (boolean)gridMsg.getProperty("confirmed");
                claims.get(claimID).moveClaimObject(playerOid,objectID, loc, orient, parent,confirmed);
                
            }
            
            return true;
        }
	}
	
	/**
	 * Called when a player wants to upgrade a claim building object
	 * @author Andrew Harrison
	 *
	 */
	class UpgradeClaimObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            Log.debug("CLAIM: got upgrade object");
            OID playerOid = gridMsg.getSubject();
            
            int claimID = (Integer)gridMsg.getProperty("claimID");
            int objectID = (Integer)gridMsg.getProperty("objectID");
            Log.debug("CLAIM: got upgrade object playerOid="+playerOid+" claimID="+claimID+" objectID="+objectID);
            ArrayList<Integer> itemIDs = new ArrayList<Integer>();
            ArrayList<OID> itemOIDs = new ArrayList<OID>();
            ArrayList<Integer> counts = new ArrayList<Integer>();
            
            if (gridMsg.getProperty("itemsCount") != null) {
            	int itemsCount = (Integer)gridMsg.getProperty("itemsCount");
            	for (int i = 0; i < itemsCount; i++) {
            		int itemID = (Integer)gridMsg.getProperty("itemID" + i);
                    OID itemOid = (OID)gridMsg.getProperty("itemOID" + i);
                    int count = (Integer)gridMsg.getProperty("count" + i);
                    if(Log.loggingDebug)  Log.debug("CLAIM: Build Upgrade: ItemId:"+itemID+" itemOID:"+itemOid+" count:"+count);
                    // Verify they have the item they are trying to use
                    OID item = InventoryClient.findItem(playerOid, itemID);
                    if (item == null) {
                    	Log.debug("CLAIM: Build Upgrade: Item not found");
                    	ExtendedCombatMessages.sendErrorMessage(playerOid, "Item not found");
                    	return true;
                    }
                    
                    itemIDs.add(itemID);
                    itemOIDs.add(itemOid);
                    counts.add(count);
            	}
            } else {
            	int itemID = (Integer)gridMsg.getProperty("itemID");
                OID itemOid = (OID)gridMsg.getProperty("itemOID");
                int count = (Integer)gridMsg.getProperty("count");
                Log.debug("CLAIM: Build Upgrade: no itemsCount ItemId:"+itemID+" itemOID:"+itemOid+" count:"+count);
                // Verify they have the item they are trying to use
                OID item = InventoryClient.findItem(playerOid, itemID);
                if (item == null) {
                	Log.debug("CLAIM: Build Upgrade: Item not found");
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "Item not found");
                	return true;
                }
                
                itemIDs.add(itemID);
                itemOIDs.add(itemOid);
                counts.add(count);
            }
             
            // Is the player performing the action the owner?
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
            /*if (!claims.get(claimID).getOwner().equals(accountID)) {
            	Log.debug("CLAIM: claim does not belong to the player");
            	return true;
            }*/
            
            if(Log.loggingDebug) Log.debug("CLAIM: Build Upgrade: claimID:"+claimID+" objectID:"+objectID+" add Task");
            boolean addTask = claims.get(claimID).addItemToUpgradeClaimObject(playerOid, objectID, itemIDs, itemOIDs, counts);
            if (addTask) {
            	activeClaimTasks.put(playerOid, claimID);
            }
            
            return true;
        }
	}
	
	
	/**
	 * Called when a client has loaded up and it wants the list of building resources sent down
	 * @author Andrew Harrison
	 *
	 */
	class GetBuildingResourcesHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got get building resources");
            // Send down building resources
    	    HashMap<String, Integer> buildingResources = (HashMap)EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "buildingResources");
            if (buildingResources != null) {
            	Log.debug("RESOURCES: sending resources");
            	BuildingResourceAcquireHook.sendBuildingResources(playerOid, buildingResources);
            } else {
            	Log.debug("RESOURCES: player has no resources");
            }
            return true;
        }
	}
	
	
	class NoBuildClaimTriggerHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got noBuild message");
            // Update no build
            int noBuild = (Integer)gridMsg.getProperty("noBuild");
            if (noBuild == 1 && !playersInNoBuild.contains(playerOid)) {
            	playersInNoBuild.add(playerOid);
            	Log.debug("CLAIM: added player to no build list");
            } else if (noBuild == 0) {
            	playersInNoBuild.remove(playerOid);
            	Log.debug("CLAIM: removed player from no build list");
            }
    	    
            return true;
        }
	}
	
	/**
     * Hook for the GetBuildingTemplateMessage. Gets the Build Object Template that matches
     * the given templateID and sends it back to the remote call procedure that sent the 
     * message out.
     * @author Andrew Harrison
     *
     */
    class GetChestStorageOidHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		GetChestStorageOidMessage EBMsg = (GetChestStorageOidMessage) msg;
    		Log.debug("SKILL: got GetPlayerSkillLevelMessage");
    	    int chestID = EBMsg.getChestID();
    	    for (Claim claim : claims.values()) {
    	    	OID storageOid = claim.getChestStorageKey(chestID);
    	    	if (storageOid != null) {
    	    		Engine.getAgent().sendObjectResponse(msg, storageOid);
    	    		return true;
    	    	}
    	    }
    		
    	    Engine.getAgent().sendObjectResponse(msg, null);
    		return true;
    	}
    }
	
	/**
     * Hook for the GetBuildingTemplateMessage. Gets the Build Object Template that matches
     * the given templateID and sends it back to the remote call procedure that sent the 
     * message out.
     * @author Andrew Harrison
     *
     */
    class GetBuildObjectTemplateHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		GetBuildingTemplateMessage EBMsg = (GetBuildingTemplateMessage) msg;
    		Log.debug("SKILL: got GetPlayerSkillLevelMessage");
    	    int templateID = EBMsg.getTemplateID();
    	    if (buildObjectTemplates.containsKey(templateID)) {
    	    	Engine.getAgent().sendObjectResponse(msg, buildObjectTemplates.get(templateID));
    	    } else {
    	    	Engine.getAgent().sendObjectResponse(msg, null);
    	    }
    		
    		return true;
    	}
    }
    
    public static BuildObjectTemplate GetBuildObjectTemplate(int templateID) {
    	if (buildObjectTemplates.containsKey(templateID)) {
	    	return buildObjectTemplates.get(templateID);
	    } else {
	    	return null;
	    }
    }
    
    
    /**
	 * Called when a player wants to edit a claim object.
	 * @author Andrew Harrison 
	 *
	 */
	class GetClaimObjectInfoHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got get claim object data");
            int claimID = (Integer)gridMsg.getProperty("claimID");
            int objectID = (Integer)gridMsg.getProperty("objectID");
            
            claims.get(claimID).sendObjectInfo(playerOid, objectID);
            
            return true;
        }
	}
	
	/**
	 * Handles the InterruptAbility Message which is sent when the player moves. Will cancel any
	 * active tasks the player currently has.
	 * @author Andrew Harrison
	 *
	 */
	class InterruptHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	interruptAbilityMessage gridMsg = (interruptAbilityMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: interrupt");
        	if (activeClaimTasks.containsKey(playerOid)) {
            	if (claims.get(activeClaimTasks.get(playerOid)).interruptBuildTask(playerOid)) {
            		activeClaimTasks.remove(playerOid);
            	}
            }
        	for (Map.Entry<Integer, Claim> entry : claims.entrySet()) {
        		entry.getValue().interruptBuildTask(playerOid);
        	}
            
            return true;
        }
	}
	
	/**
	 * Called when a player wants to attack a claim object.
	 * @author Andrew Harrison
	 *
	 */
	class AttackBuildingObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            OID playerOid = gridMsg.getSubject();
            Log.debug("CLAIM: got attack object");
            int claimID = (Integer)gridMsg.getProperty("claimID");
            int objectID = (Integer)gridMsg.getProperty("objectID");
            
            claims.get(claimID).attackBuildObject(playerOid, objectID);
            
            return true;
        }
	}
	
	class CheckBuildingsInAreaHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			BuildingAoETargetsInAreaMessage message = (BuildingAoETargetsInAreaMessage) msg;
			OID subjectOid = message.getSubject();
			//OID instanceOid = message.getMsgInstanceOid();
			if (Log.loggingDebug)
				log.debug("CheckBuildingsInAreaHook: subjectOid=" + subjectOid + " msg=" + msg);
			long start = System.nanoTime();
			ArrayList<CombatBuildingTarget> claimObjects = new ArrayList<CombatBuildingTarget>();
			ObjectInfo oi = WorldManagerClient.getObjectInfo(subjectOid);
			if(oi == null){
				log.error("CheckBuildingsInAreaHook: subjectOid=" + subjectOid + " didn't get ObjectInfo from wmgr. Return empty list of the claim objects");
				Engine.getAgent().sendObjectResponse(message, claimObjects);
				return true;
			}
			try {
				OID instanceOid = oi.instanceOid;
				Point loc = message.getLoc();
				float minRange = message.getMinRange();
				float maxRange = message.getMaxRange();
				Quaternion quternion = message.getQuaternion();
				if (Log.loggingDebug)
					log.debug("CheckBuildingsInAreaHook: subjectOid=" + subjectOid + " instanceOid=" + instanceOid + " loc=" + loc + " quternion=" + quternion + " minRange=" + minRange + " maxRange=" + maxRange);


				for (Map.Entry<Integer, Claim> entry : claims.entrySet()) {
					Claim c = entry.getValue();
					if (c != null && c.getInstanceOID().equals(instanceOid)) {

						if (Log.loggingDebug)
							log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " " + c.objectsStance.containsKey(subjectOid) + " " + (c.objectsStance.containsKey(subjectOid) ? c.objectsStance.get(subjectOid) : "NA"));

						boolean attackable = false;
						if (c.objectsStance.containsKey(subjectOid) && c.objectsStance.get(subjectOid) == FactionPlugin.Attackable) {
							attackable = true;
						}
						if (!c.objectsStance.containsKey(subjectOid)) {
							if (Log.loggingDebug)
								log.debug("GetBuildObjectIsAttackableHook: claim " + c.getID() + " playerOID=" + subjectOid + " PlayerOwner=" + c.PlayerOwner());
							int stance = FactionClient.getStance(subjectOid, c.PlayerOwner());
							if (Log.loggingDebug)
								log.debug("GetBuildObjectIsAttackableHook: claim " + c.getID() + " stance=" + stance);
							if (stance < 0) {
								attackable = true;

							}
						}
						if (Log.loggingDebug)
							log.debug("GetBuildObjectIsAttackableHook: claim " + c.getID() + " attackable=" + attackable);
						if (attackable) {
							ClaimUpgrade cu = c.upgrades.get(c.getUpgrade());
							int sizeX = c.getSize();
							int sizeZ = c.getSizeZ();
							AOVector diff = AOVector.Zero;
							if (cu != null) {
								sizeX = (int) cu.size.getX();
								sizeZ = (int) cu.size.getZ();
								diff = cu.loc;
							}

							float cdist = Point.distanceTo(loc, new Point(AOVector.add(c.getLoc(), diff)));
							double claimSqrt = Math.sqrt(sizeZ * sizeZ / 4 + sizeX * sizeX / 4);
							if (Log.loggingDebug)
								log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " cdist=" + cdist + " claimSqrt=" + claimSqrt);
							if (cdist < maxRange + claimSqrt) {

								// ArrayList<Integer> objs = new ArrayList<Integer>();
								Iterator<ClaimObject> iterator = c.getClaimObjects().iterator();

								while (iterator.hasNext()) {
									ClaimObject co = iterator.next();
									if (Log.loggingDebug)
										log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " ClaimObject=" + co.id + " temp=" + co.templateId);
									BuildObjectTemplate buildTemplate = VoxelPlugin.GetBuildObjectTemplate(co.templateId);
									if (Log.loggingDebug)
										log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " ClaimObject=" + co.id + " temp=" + co.templateId + " attackable=" + buildTemplate.getAttackable());
									if (!buildTemplate.getAttackable()) {
										continue;
									}
									AOVector reqDir = AOVector.sub(AOVector.add(c.getLoc(), co.loc), new AOVector(loc));
									float dist = reqDir.length();
									if (Log.loggingDebug)
										log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " ClaimObject=" + co.id + " dist=" + dist);
									if (minRange < dist && dist <= maxRange) {
										float playerYaw = quternion.getYaw();
										float motionYaw = AOVector.getLookAtYaw(reqDir);
										if (motionYaw < 0)
											motionYaw += 360;
										float yaw = playerYaw - motionYaw;
										if (yaw < 360)
											yaw += 360;
										if (yaw > 180)
											yaw -= 360;
										if (Math.abs(yaw) < message.getAngle()) {
											if (Log.loggingDebug)
												log.debug("CheckBuildingsInAreaHook: claim " + c.getID() + " ClaimObject=" + co.id + " add to targets");
											CombatBuildingTarget cbt = new CombatBuildingTarget(c.getID(), co.id, dist);
											claimObjects.add(cbt);

											// objs.add(c.getID());
										}
									}
								}

								/*
								 * if (objs.size() > 0) { claimObjects.put(c.getID(), objs); }
								 */
							} else {
								if (Log.loggingDebug)log.debug(" Claim " + c.getID() + " out range");
								// out of range
							}
						} else {
							if (Log.loggingDebug)
								log.debug("CheckBuildingsInAreaHook: Claim " + c.getID() + " not attackable");
						}
					}

				}
			} catch (Exception e){
				Log.exception("CheckBuildingsInAreaHook Exception", e);
			}
			if (Log.loggingDebug)
				log.debug("CheckBuildingsInAreaHook: End  targets "+claimObjects);
			Engine.getAgent().sendObjectResponse(message, claimObjects);

			return true;
		}
	}
	
	
	class GetBuildObjectIsAttackableHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GetBuildingIsAttackableMessage bpMsg = (GetBuildingIsAttackableMessage) msg;
			Log.debug("GetBuildObjectIsAttackableHook: start");
			int claimID = bpMsg.getClaimID();
			int objectID = bpMsg.getObjectID();
			OID playerOID = bpMsg.getPlayerOID();
			Log.debug("GetBuildObjectIsAttackableHook: claimID=" + claimID + " objectID=" + objectID + " ");

			if (!claims.containsKey(claimID)) {
				Log.debug("GetBuildObjectIsAttackableHook: claim "+claimID+" not found");
				Engine.getAgent().sendObjectResponse(msg, null);
				return true;
			}
			Claim claim = claims.get(claimID);
			ClaimObject co = claim.getClaimObject(objectID);
			if (co != null) {
				BuildObjectTemplate buildTemplate = VoxelPlugin.GetBuildObjectTemplate(co.templateId);
				if (Log.loggingDebug)
					log.debug("GetBuildObjectIsAttackableHook: claim "+claimID+" ClaimObject="+co.id+" temp="+co.templateId+" attackable="+buildTemplate.getAttackable());
				if(!buildTemplate.getAttackable()) {
					Engine.getAgent().sendObjectResponse(msg, false);
					return true;
				}
				if (Log.loggingDebug)
					log.debug("GetBuildObjectIsAttackableHook: claim "+claimID+" ClaimObject="+co.id+" temp="+co.templateId+" FactionPlugin.Attackable="+FactionPlugin.Attackable+" claim.objectsStance.get(playerOID)="+claim.objectsStance.get(playerOID));
				if (claim.objectsStance.containsKey(playerOID) && claim.objectsStance.get(playerOID) == FactionPlugin.Attackable) {
					Engine.getAgent().sendObjectResponse(msg, true);
					return true;
				}
				if(!claim.objectsStance.contains(playerOID)) {
					if (Log.loggingDebug)
						log.debug("GetBuildObjectIsAttackableHook: claim "+claimID+" ClaimObject="+co.id+" temp="+co.templateId+" playerOID="+playerOID+" PlayerOwner="+claim.PlayerOwner());
					int stance = FactionClient.getStance(playerOID, claim.PlayerOwner());
					if (Log.loggingDebug)
						log.debug("GetBuildObjectIsAttackableHook: claim "+claimID+" ClaimObject="+co.id+" temp="+co.templateId+" stance="+stance);
					if(stance < 0) {
						Engine.getAgent().sendObjectResponse(msg, true);
						return true;
					}
				}
				
				
				Engine.getAgent().sendObjectResponse(msg, false);
			} else {
				Log.debug("GetBuildObjectIsAttackableHook: claim object "+objectID+" not found");
				Engine.getAgent().sendObjectResponse(msg, false);
			}
			Log.debug("GetBuildObjectIsAttackableHook: End");
			return true;
		}
	}
	
		class TargetTypeUpdateHook implements Hook {
			public boolean processMessage(Message msg, int flags) {
				CombatClient.TargetTypeMessage ttMsg = (CombatClient.TargetTypeMessage) msg;
				LinkedList<TargetType> _targetTypes = ttMsg.getTargetTypes();
			//	if(Log.loggingDebug)Log.error("TargetTypeUpdateHook: got target type update. _targetTypes: " + _targetTypes );
				
				try {
					if (_targetTypes == null) {

					} else {
						//if(Log.loggingDebug)	Log.error("TargetTypeUpdateHook: got target type update. _targetTypes: " + _targetTypes );
						
						for (int i = 0; i < _targetTypes.size(); i++) {
							for (Map.Entry<Integer, Claim> entry : claims.entrySet()) {
								try {
									TargetType tt = _targetTypes.get(i);
									if (tt.getSubjectOid().equals(entry.getValue().PlayerOwner())) {

										if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook: objectsStance hashmap "+entry.getValue().objectsStance+" "+_targetTypes.get(i).getTargetOid()+" "+_targetTypes.get(i).getTargetType());

										if(tt.getTargetType().equals(FactionPlugin.Deleted)) {
											entry.getValue().objectsStance.remove(tt.getTargetOid());
										} else {
											int v = entry.getValue().objectsStance.computeIfAbsent(tt.getTargetOid(), __ -> tt.getTargetType());
											entry.getValue().objectsStance.put(tt.getTargetOid(), tt.getTargetType());
											//entry.getValue().objectsStance.put(_targetTypes.get(i).getTargetOid(), _targetTypes.get(i).getTargetType());
											if (v != tt.getTargetType()) {
												entry.getValue().sendClaimData(_targetTypes.get(i).getTargetOid());
											}
											//entry.getValue().objectsStance.computeIfAbsent(tt.getTargetOid(),__-> tt.getTargetType());
										}
									}
								} catch (Exception e) {
									Log.error("TargetTypeUpdateHook: E objectsStance hashmap "+entry.getValue().objectsStance+" "+_targetTypes.get(i).getTargetOid()+" "+_targetTypes.get(i).getTargetType());
									Log.exception("TargetTypeUpdateHook: "+_targetTypes.get(i),e);
									if(entry.getValue().objectsStance.containsKey(_targetTypes.get(i).getTargetOid()))
										entry.getValue().sendClaimData(_targetTypes.get(i).getTargetOid());
								}
							}
						}
					}
				} catch (Exception e) {
					Log.exception("TargetTypeUpdateHook: E ",e);
				}
				Log.debug("TARGET: got target type update. END");
				//Log.error("TargetTypeUpdateHook: End");
				return true;
			}
		}
	
	/**
	 * Called when a player wants to take one of the claim resources and put it in their inventory.
	 * @author Andrew Harrison
	 *
	 */
	class TakeClaimResourceHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage rMsg = (ExtensionMessage)msg;
            OID playerOid = rMsg.getSubject();
            Log.debug("CLAIM: got attack object");
            int claimID = (Integer)rMsg.getProperty("claimID");
            int itemID = (Integer)rMsg.getProperty("itemID");
            
            claims.get(claimID).takeResource(playerOid, itemID);
            
            return true;
        }
	}
	
	class BuildingDamageHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			BuildingDamageMessage dMsg = (BuildingDamageMessage) msg;
			Log.debug("BuildingDamageHook: start");
			OID playerOid = dMsg.getAttacker();
			int claimID = dMsg.getClaimID();
			int objectID = dMsg.getObjectID();
			int damage = dMsg.getdamage();
			Log.debug("BuildingDamageHook: claimID=" + claimID + " objectID=" + objectID + " damage=" + damage);

			if (!claims.containsKey(claimID)) {
				Log.debug("BuildingDamageHook: claim " + claimID + " not found");
				Engine.getAgent().sendObjectResponse(msg, null);
				return true;
			}
			Claim claim = claims.get(claimID);
			ClaimObject co = claim.getClaimObject(objectID);
			if (co != null) {
				log.debug("BuildingDamageHook health="+co.health+" / "+co.maxHealth);
				if (co.health <= damage) {
					claim.cancelTask(co);
					claim.removeObject(co);
					claim.generateLoot(playerOid,co);
					
				} else {
					co.health -= damage;
					claim.updateHealth(co);
					claim.displayDamage(co,damage);
					claim.sendObject(co);
				}
			} else {
				log.debug("BuildingDamageHook Claim Object not exist");
			}
			Log.debug("BuildingDamageHook: End");
			return true;
		}
	}

	/**
	 * Colled to retrive location of the Cliam Object
	 * 
	 */
	
	class GetBuildObjectLocHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GetBuildingPositionMessage bpMsg = (GetBuildingPositionMessage) msg;
			Log.debug("GetBuildObjectLocHook: start");
			int claimID = bpMsg.getClaimID();
			int objectID = bpMsg.getObjectID();
			Log.debug("GetBuildObjectLocHook: claimID=" + claimID + " objectID=" + objectID + " ");

			if (!claims.containsKey(claimID)) {
				Log.error("GetBuildObjectLocHook: claim "+claimID+" not found");
				Engine.getAgent().sendObjectResponse(msg, null);
				return true;
			}
			Claim claim = claims.get(claimID);
			ClaimObject co = claim.getClaimObject(objectID);
			if (co != null) {
				AOVector aopoint = AOVector.add(co.loc, claim.getLoc());
				Engine.getAgent().sendObjectResponse(msg, new Point(aopoint));
			} else {
				Log.debug("GetBuildObjectLocHook: claim object "+objectID+" not found");
				Engine.getAgent().sendObjectResponse(msg, null);
			}
			Log.debug("GetBuildObjectLocHook: End");
			return true;
		}
	}
	
	
	// Temporary tick system
    class ClaimMessageTick implements Runnable {
    	
    	public void run() {
    		Log.debug("ClaimMessageTick Start");
			List<OID> players = new ArrayList<OID>();
			try {
				for (Claim claim : claims.values()) {
					claim.sendActionsToPlayers();
					if (USE_TAX_SYSTEM) {
						if (claim.getOwner() != null) {
							if (Log.loggingDebug)
								Log.debug("ClaimMessageTick: checking claim tax time: " + claim.getTaxPaidUntil() + " against: " + System.currentTimeMillis());
						}
						int taxCurrency = claim.getTaxCurrency();
						long taxAmount = claim.getTaxAmount();
						long taxInterval = claim.getTaxInterval();
						long taxPeriodPay = claim.getTaxPeriodPay();
						if (claim.getUpgrade() > 0) {
							ClaimUpgrade cu = claim.upgrades.get(claim.getUpgrade());
							taxCurrency = cu.taxCurrency;
							taxAmount = cu.taxAmount;
							taxInterval = cu.taxInterval;
							taxPeriodPay = cu.taxPeriodPay;
						}
						if (taxInterval > 0) {
							if (claim.getOwner() != null && claim.getTaxPaidUntil() < System.currentTimeMillis()) {
								if (Log.loggingDebug)
									Log.debug("ClaimMessageTick: claim tax expired: " + claim.getID()+" AUTO_PAY_TAX_SYSTEM="+AUTO_PAY_TAX_SYSTEM);
								if (AUTO_PAY_TAX_SYSTEM) {
									OID playerOid = Engine.getDatabase().getOidByName(claim.getSellerName(), Namespace.getNamespace("NS.master"));
									if (Log.loggingDebug)
										Log.debug("ClaimMessageTick: claim tax expired: " + claim.getID()+" playerOid="+playerOid);
									if (playerOid != null) {
										boolean canAfford = false;
										boolean offline = false;
										if (Log.loggingDebug)
											Log.debug("ClaimMessageTick: claim tax expired: " + claim.getID() + " playerOid=" + playerOid + " taxCurrency=" + taxCurrency + " taxAmount=" + taxAmount);

										try {
											canAfford = AgisInventoryClient.checkCurrency(playerOid, taxCurrency, taxAmount);
											players.add(playerOid);
										} catch (NoRecipientsException e) {
											canAfford = AgisInventoryClient.checkCurrencyOffline(playerOid, taxCurrency, taxAmount);
											offline = true;
										}
										if (Log.loggingDebug)
											Log.debug("ClaimMessageTick: canAfford=" + canAfford+" offline="+offline);

										if (canAfford) {
											if (offline) {
												AgisInventoryClient.alterCurrencyOffline(playerOid, taxCurrency, -taxAmount);
											} else {
												AgisInventoryClient.alterCurrency(playerOid, taxCurrency, -taxAmount);
											}
											long time = 0L;
											if (claim.getTaxPaidUntil() < System.currentTimeMillis()) {
												time = System.currentTimeMillis() + taxInterval * 3600L * 1000L;
											} else {
												time = claim.getTaxPaidUntil() + taxInterval * 3600L * 1000L;
											}
											claim.setTaxPaidUntil(time);
											cDB.updateClaim(claim);
											//claim.claimUpdated(playerOid);

										} else {
											Log.debug("ClaimMessageTick: Insufficient funds to pay tax for this claim");

											claim.changeClaimOwner(null, null, null);
											claim.setForSale(true);
											claim.resetCost();
											cDB.updateClaim(claim);
										}
									} else {
										claim.changeClaimOwner(null, null, null);
										claim.setForSale(true);
										claim.resetCost();
										cDB.updateClaim(claim);
									}
								} else {
									// Claim tax has not been paid, the claim will be abandoned
									claim.changeClaimOwner(null, null, null);
									claim.setForSale(true);
									claim.resetCost();
									cDB.updateClaim(claim);
								}
							}
						}
					}
				}
				for (OID playerOid : players) {
					try {
						Map<String, Serializable> props = new HashMap<String, Serializable>();
						OID accountID = Engine.getDatabase().getAccountOid(playerOid);
						props.put("ext_msg_subtype", "claim_own");
						int count = 0;
						for (Claim _claim : claims.values()) {
							if (Log.loggingDebug)
								Log.debug("ClaimMessageTick: instance=" + _claim.getInstanceID() + " id=" + _claim.getID() + " owner=" + _claim.getOwner() + " player=" + playerOid + " accountID=" + accountID);
							if (_claim.getOwner() != null && (_claim.getOwner().equals(playerOid) || _claim.getOwner().equals(accountID))) {
								props.put("id" + count, _claim.getID());
								props.put("name" + count, _claim.getName());
								props.put("time" + count, _claim.getTaxPaidUntil() - System.currentTimeMillis());
								int  taxCurrency = _claim.getTaxCurrency();
								long taxAmount = _claim.getTaxAmount();
								long taxInterval = _claim.getTaxInterval();
								long taxPeriodPay = _claim.getTaxPeriodPay();
								long taxPeriodSell = _claim.getTaxPeriodSell();
								if (_claim.getUpgrade() > 0) {
									ClaimUpgrade cu = _claim.upgrades.get(_claim.getUpgrade());
									taxCurrency = cu.taxCurrency;
									taxAmount = cu.taxAmount;
									taxInterval = cu.taxInterval;
									taxPeriodPay = cu.taxPeriodPay;
									taxPeriodSell = cu.taxPeriodSell;
								}
								props.put("taxCurrency" + count, taxCurrency);
								props.put("taxAmount" + count, taxAmount);
								props.put("taxInterval" + count, taxInterval);
								props.put("taxPeriodPay" + count, taxPeriodPay);
								props.put("taxPeriodSell" + count, taxPeriodSell);
								count++;
								_claim.claimUpdated(playerOid);
							}
						}
						props.put("num", count);
						if (Log.loggingDebug)
							Log.debug("ClaimMessageTick pay props=" + props);
						TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
						Engine.getAgent().sendBroadcast(msgExt);
					} catch (Exception e) {
						log.exception("ClaimMessageTick", e);
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				log.exception("ClaimMessageTick || ",e);
				e.printStackTrace();
			}
			Log.debug("ClaimMessageTick END");
		}
	}

	void sendClaimListUpdate(OID playerOid){
		try {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			OID accountID = Engine.getDatabase().getAccountOid(playerOid);
			props.put("ext_msg_subtype", "claim_own");
			int count = 0;
			for (Claim _claim : claims.values()) {
				if (Log.loggingDebug)
					Log.debug("ClaimMessageTick: instance=" + _claim.getInstanceID() + " id=" + _claim.getID() + " owner=" + _claim.getOwner() + " player=" + playerOid + " accountID=" + accountID);
				if (_claim.getOwner() != null && (_claim.getOwner().equals(playerOid) || _claim.getOwner().equals(accountID))) {
					props.put("id" + count, _claim.getID());
					props.put("name" + count, _claim.getName());
					props.put("time" + count, _claim.getTaxPaidUntil() - System.currentTimeMillis());
					int  taxCurrency = _claim.getTaxCurrency();
					long taxAmount = _claim.getTaxAmount();
					long taxInterval = _claim.getTaxInterval();
					long taxPeriodPay = _claim.getTaxPeriodPay();
					long taxPeriodSell = _claim.getTaxPeriodSell();
					if (_claim.getUpgrade() > 0) {
						ClaimUpgrade cu = _claim.upgrades.get(_claim.getUpgrade());
						taxCurrency = cu.taxCurrency;
						taxAmount = cu.taxAmount;
						taxInterval = cu.taxInterval;
						taxPeriodPay = cu.taxPeriodPay;
						taxPeriodSell = cu.taxPeriodSell;
					}
					props.put("taxCurrency" + count, taxCurrency);
					props.put("taxAmount" + count, taxAmount);
					props.put("taxInterval" + count, taxInterval);
					props.put("taxPeriodPay" + count, taxPeriodPay);
					props.put("taxPeriodSell" + count, taxPeriodSell);
					count++;
					_claim.claimUpdated(playerOid);
				}
			}
			props.put("num", count);
			if (Log.loggingDebug)
				Log.debug("ClaimMessageTick pay props=" + props);
			TargetedExtensionMessage msgExt = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msgExt);
		} catch (Exception e) {
			log.exception("ClaimMessageTick", e);
			e.printStackTrace();
		}
	}


    ClaimMessageTick claimTick = new ClaimMessageTick();
    List<OID> playersInNoBuild = new ArrayList<OID>();
	
	private static ConcurrentHashMap<Integer, Claim> claims = new ConcurrentHashMap<Integer, Claim>();
	private static HashMap<Integer, BuildObjectTemplate> buildObjectTemplates = new HashMap<Integer, BuildObjectTemplate>();
	public  static HashMap<Integer, ClaimProfile> claimProfiles = new HashMap<Integer, ClaimProfile>();
	public static  HashMap<OID, Integer> activeClaimTasks = new HashMap<OID, Integer>();
	
	public static int DISTANCE_REQ_BETWEEN_CLAIMS = 10;
	public static int CLAIM_DRAW_RADIUS = 300;
	public static final int CLAIM_STONE_ITEM_ID = 11;
	public static boolean REMOVE_ITEM_ON_BUILD_FAIL = false;
	public static boolean BUILD_CAN_FAIL = false;
	public static boolean ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS = false;
	public static boolean USE_CLAIM_RESOURCES = true;
	public static boolean USE_TAX_SYSTEM = false;
	public static boolean AUTO_PAY_TAX_SYSTEM = false;
	public static boolean UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY = false;
	public static boolean REPAIR_CLAIM_OBJECT_ITEMS_FROM_INVENTORY = false;
	
	public static String BUILD_SPEED_STAT = null;

	private AccountDatabase cDB;
}