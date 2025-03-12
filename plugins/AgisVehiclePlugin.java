package atavism.agis.plugins;

import atavism.server.engine.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.*;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.EntityManager;
import atavism.server.objects.InstanceTemplate;
import atavism.server.objects.ObjectFactory;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.SpawnData;
import atavism.server.objects.Template;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.behaviors.BaseBehavior;
import atavism.agis.behaviors.DotBehavior;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.VehicleDatabase;
import atavism.agis.database.VehicleDatabase.*;
import atavism.agis.objects.AgisVehicle;
import atavism.agis.objects.BehaviorTemplate;
import atavism.agis.objects.BonusSettings;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.DetourActor;
import atavism.agis.objects.InstanceNavMeshManager;
import atavism.agis.objects.MobFactory;
import atavism.agis.objects.SpawnGenerator;
import atavism.agis.objects.VehicleFactory;
import atavism.agis.plugins.BonusClient.GlobalEventBonusesUpdateMessage;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.HelperFunctions;
import atavism.msgsys.*;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import io.micrometer.core.instrument.Gauge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.CheckVisibilityMessage;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.telemetry.Prometheus;

public class AgisVehiclePlugin extends VehicleManagerPlugin {

	public VehicleDatabase vehicleDatabase;
	private AccountDatabase aDB;
	private ContentDatabase cDB;

	private OID ownerOid;
	private OID vehicleObj;
	private String vehicleName;
	private static final Logger log = new Logger("AgisVehiclePlugin");
	private static Map<String, Class> spawnGeneratorClasses = (Map) new HashMap<>();
	public String myPluginName;
	public static final int BASE_CATEGORY = 6;
	private static int numFactories = 0;
	private final Map<OID, AtomicInteger> vehiclesPerInstance = new ConcurrentHashMap<>();
	public static Map<OID, Map<OID, Integer>> objectsTargetType = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, BonusSettings> globalEventBonusesArray = new ConcurrentHashMap<String, BonusSettings>();

	private static Map<String, String> factoryCache = new HashMap<>();

	public void onActivate() {
		super.onActivate();
		Log.error("AgisVehiclePlugin: activated");

		// Register message hooks
		Log.error("AgisVehiclePlugin: Registering message hooks");
		Log.error("AgisVehiclePlugin: Registering SpawnVehicleHook");
		getHookManager().addHook(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE, new GlobalEventBonusesUpdateHook());
		WorldManagerClient.visibilityCheckFunction = this::checkVisibility;
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY, new CheckVisibilityHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_SPAWN_INSTANCE_VEHICLES, new SpawnInstanceVehiclesHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_CREATE_VEHICLE_SPAWN, new SpawnVehicleHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN,
				new DeleteVehicleSpawnGeneratorHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME, new ServerTimeHook());
		// Hooks to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_DESPAWNED_VEHICLE, new DespawnedHook());
		// Now register the new vehicle-specific hooks:
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_INTERACT_WITH_VEHICLE, new InteractWithVehicleHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_VERIFY_ISLAND_ACCESS, new VerifyIslandAccessHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_REQUEST_DEVELOPER_ACCESS,
				new RequestIslandDeveloperAccessHook());
		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_LOADED, new LoadInstanceObjectsHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_VEHICLE_GET_ACTOR_SPEED, new GetActorSpeedHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_UPDATE_LOCATION, new UpdateLocationHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGERS_LOCATION, new UpdateLocationHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGER_COUNT, new UpdatePassengerCountHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_CONTROL_VEHICLE_MOVEMENT, new ControlVehicleMovementHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_SET_DRIVER, new SetDriverHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_DRIVE_VEHICLE, new DriveVehicleHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_STOP_VEHICLE, new StopVehicleHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_ENTER_VEHICLE, new EnterVehicleHook());
		getHookManager().addHook(AgisVehicleClient.MSG_TYPE_EXIT_VEHICLE, new ExitVehicleHook());

		// --- Rebuild the vehicle filter list (analogous to vehicleFilterTypes) ---
		vehicleFilterTypes.clear();
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_SPAWN_INSTANCE_VEHICLES);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_SPAWN_VEHICLE);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_DESPAWNED_VEHICLE);
		// vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_INTERACT_WITH_VEHICLE);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_CREATE_VEHICLE_SPAWN);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_UPDATE_LOCATION);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGERS_LOCATION);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGER_COUNT);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_CONTROL_VEHICLE_MOVEMENT);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_SET_DRIVER);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_DRIVE_VEHICLE);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_STOP_VEHICLE);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_ENTER_VEHICLE);
		vehicleFilterTypes.add(AgisVehicleClient.MSG_TYPE_EXIT_VEHICLE);
		vehicleFilterTypes.add(InstanceClient.MSG_TYPE_INSTANCE_LOADED);

		Log.error("AgisVehiclePlugin: Creating subscription");
		// --- Set up RPC filters (aligned with the Vehicle RPC filters) ---
		vehicleRPCFilterTypes.clear();
		vehicleRPCFilterTypes.add(WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY);
		//vehicleRPCFilterTypes.add(AgisVehicleClient.MSG_TYPE_VEHICLE_GET_ACTOR_SPEED);
		Log.error("AgisVehiclePlugin: Creating vehicleRPCFilterTypes");
		// --- Create the subscription filter (aligned with AgisVehicleClient) ---
		MessageTypeFilter vehicleSubscriptionFilter = new MessageTypeFilter();
		vehicleSubscriptionFilter.addType(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);
		vehicleSubscriptionFilter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		vehicleSubscriptionFilter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		vehicleSubscriptionFilter.addType(AgisVehicleClient.MSG_TYPE_INTERACT_WITH_VEHICLE);
		vehicleSubscriptionFilter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
		vehicleSubscriptionFilter.addType(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
		// vehicleSubscriptionFilter.addType(CombatClient.MSG_TYPE_TARGET_TYPE);
		// vehicleSubscriptionFilter.addType(AgisVehicleClient.MSG_TYPE_DEBUG_VEHICLE);
		Log.error("AgisVehiclePlugin: Creating vehicleSubscriptionFilter");
		Engine.getAgent().createSubscription(vehicleSubscriptionFilter, this);

		Log.error("AgisVehiclePlugin: Creating responderFilter");
		// --- Create the responder filter (same as in the vehicle plugin) ---
		MessageTypeFilter vehicleResponderFilter = new MessageTypeFilter();
		//vehicleResponderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
		//vehicleResponderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_UPDATE_LOCATION);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGERS_LOCATION);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGER_COUNT);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_CONTROL_VEHICLE_MOVEMENT);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_SET_DRIVER);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_DRIVE_VEHICLE);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_STOP_VEHICLE);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_ENTER_VEHICLE);
		vehicleResponderFilter.addType(AgisVehicleClient.MSG_TYPE_EXIT_VEHICLE);
		
		Engine.getAgent().createSubscription(vehicleResponderFilter, this, MessageAgent.RESPONDER);

		// --- Initialize databases and load templates ---
		vehicleDatabase = new VehicleDatabase();
		cDB = new ContentDatabase(true);
		aDB = new AccountDatabase(true);

		Log.error("AgisVehiclePlugin: Activation completed");
		Engine.registerStatusReportingPlugin(this);
		
		createFactories();
		registerTemplates();
		loadInstanceTemplates();
	}

	class GetActorSpeedHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
				Log.debug("AgisVehiclePlugin: GetActorSpeedHook: Start");
			PropertyMessage _msg = (PropertyMessage) msg;
			OID obj = _msg.getSubject();
			OID instanceOid = (OID) _msg.getProperty("instanceOID");
			float speed = Float.NaN;
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					speed = da.getActorSpeed(obj);
				} else {
						Log.debug("AgisVehiclePlugin: DeturActor is null no NavMesh");
				}
			} else {
					Log.debug("AgisVehiclePlugin: GetActorSpeedHook:  instance not found");
			}
			if (Float.isNaN(speed)) {
				log.error("AgisVehiclePlugin: GetActorSpeedHook speed is NaN for oid=" + obj);
			}
			Engine.getAgent().sendObjectResponse(msg, speed);
				Log.debug("AgisVehiclePlugin: GetActorSpeedHook: End");

			return true;
		}
	}

	class PropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage propMsg = (PropertyMessage) msg;
			OID objOid = propMsg.getSubject();
				log.debug("AgisVehiclePlugin: PropertyHook: for " + objOid + " " + propMsg);
			Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			Log.debug("AgisVehiclePlugin: DEAD: got propertyMessage for vehicle: " + objOid + " with dead? " + dead);
			if (dead != null && dead) {
				Log.debug("AgisVehiclePlugin: DEAD: got propertyMessage for vehicle: " + objOid + " remove vehicle from NavMesh ");
				for (OID instanceOid : instances) {
					if (instanceNavMeshes.containsKey(instanceOid)) {
						instanceNavMeshes.get(instanceOid).removeActor(objOid);
					}
				}
			}

			return true;
		}
	}

	/**
	 * Loads in the NavMesh and other world information. Sends out the
	 * SpawnInstanceObjects message when finished.
	 */
	class LoadInstanceObjectsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SubjectMessage message = (SubjectMessage) msg;
			log.debug("AgisVehiclePlugin.LoadInstanceObjectsHook");
			OID instanceOid = message.getSubject();
				log.debug("AgisVehiclePlugin.LoadInstanceObjectsHook " + instanceOid);
			int instanceID = (Integer) InstanceClient.getInstanceInfo(instanceOid,
					InstanceClient.FLAG_TEMPLATE_ID).templateID;
			InstanceTemplate iTmpl = InstanceClient.getInstanceTemplate(instanceID);
			// AgisVehicleClient.getInstanceTemplate(instanceID);
			// String instanceName = iTmpl.getName();
			// instanceTemplates.get(instanceID).getName();
			// InstanceNavMeshManager navMeshManager = new
			// InstanceNavMeshManager(instanceName, instanceOid);
			// instanceNavMeshes.put(instanceOid, navMeshManager);
			// if (Log.loggingTrace) log.trace("AgisVehicleClient.LoadInstanceObjectsHook
			// instanceName:"+instanceName+" ");

			AgisVehicleClient.spawnInstanceVehicles(iTmpl, instanceOid);
			log.debug("AgisVehiclePlugin.LoadInstanceObjectsHook END");
			return true;
		}
	}

	class RequestIslandDeveloperAccessHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage requestAccessMessage = (ExtensionMessage) msg;
			OID oid = requestAccessMessage.getSubject();
			OID instanceOid = WorldManagerClient.getObjectInfo(oid).instanceOid;
			String world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_NAME).templateName;
			OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
			int worldId = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "world");
				Log.debug("AgisVehiclePlugin: RequestIslandDeveloperAccess hit with instanceOid: " + instanceOid + " world: " + world
						+ " and account id: " + accountID);

			// Determine whether the user has access to the specified island - and if yes,
			// do they
			// have developer access?
			boolean hasAccess = true;
			InstanceTemplate island = InstanceClient.getInstanceTemplate(worldId);
			// instanceTemplates.get(world);
			if (island == null)
				return true;
			if (!island.getIsPublic() && !island.getAdministrator().equals(accountID)
					&& !island.getDevelopers().contains(accountID)) {
				hasAccess = false;
			}

			TargetedExtensionMessage verifyResponse = new TargetedExtensionMessage(oid, oid);
			verifyResponse.setExtensionType("world_developer_response");
			boolean isDeveloper = false;
			boolean isAdmin = false;
			if (island.getAdministrator().equals(accountID) && hasAccess)
				isAdmin = true;
			if (island.getDevelopers().contains(accountID) && hasAccess)
				isDeveloper = true;
			verifyResponse.setProperty("isDeveloper", isDeveloper);
			verifyResponse.setProperty("isAdmin", isAdmin);
			Engine.getAgent().sendBroadcast(verifyResponse);
			if (isDeveloper)
				sendIslandBuildingData(oid);
			return true;
		}
	}

	/**
	 * Checks if the player has Developer Access to the world, allowing them to
	 * modify spawns, and other world objects.
	 * 
	 * @param accountID
	 * @param world
	 * @return
	 */
	public static boolean accountHasDeveloperAccess(OID characterOID, OID accountID, int world) {
		// First check if the account is an admin account
		int adminLevel = (Integer) EnginePlugin.getObjectProperty(characterOID, WorldManagerClient.NAMESPACE,
				"adminLevel");
		if (adminLevel == AgisLoginPlugin.ACCOUNT_ADMIN) {
			return true;
		}
		// If not, check if they are on the list of island developers
		InstanceTemplate island = InstanceClient.getInstanceTemplate(world);
		// instanceTemplates.get(world);
		if (island == null) {
			Log.debug("ACCESS: world: " + world + " does not exist");
			return false;
		}
		if (!island.getAdministrator().equals(accountID) && !island.getDevelopers().contains(accountID)) {
			Log.debug("AgisVehiclePlugin: ACCESS: player " + accountID.toString() + " does not have access to world: " + world);
			return false;
		}
		return true;
	}

	/**
	 * Sends data down about the island the user is currently building on. Contains
	 * information such as number of spawns and the limit, whether the island is
	 * public etc.
	 * 
	 * @param oid
	 */
	private void sendIslandBuildingData(OID oid) {
		int world = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "world");
		OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
			Log.debug("AgisVehiclePlugin: SendIslandBuildingData hit with world: " + world);

		// Determine whether the user has developer access
		InstanceTemplate island = InstanceClient.getInstanceTemplate(world);
		// instanceTemplates.get(world);
		if (island == null)
			return;
		if (!accountHasDeveloperAccess(oid, accountID, world))
			return;

		TargetedExtensionMessage markerResponse = new TargetedExtensionMessage(oid, oid);
		markerResponse.setExtensionType("island_building_data");
		markerResponse.setProperty("name", island.getName());
		markerResponse.setProperty("isPublic", island.getIsPublic());
		markerResponse.setProperty("content_packs", island.getContentPacks());
		markerResponse.setProperty("subscription", island.getSubscriptionActive());
		markerResponse.setProperty("vehicleSpawns", vehicleDatabase.getSpawnCount(island.getID()));

		Engine.getAgent().sendBroadcast(markerResponse);
	}

	class VerifyIslandAccessHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage verifyIslandAccessMessage = (ExtensionMessage) msg;
			OID oid = verifyIslandAccessMessage.getSubject();
			int world = (Integer) verifyIslandAccessMessage.getProperty("world");
			String password = (String) verifyIslandAccessMessage.getProperty("password");
			OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
				Log.debug("AgisVehiclePlugin: VerifyIslandAccess hit with world: " + world);

			TargetedExtensionMessage verifyResponse = new TargetedExtensionMessage(oid, oid);
			verifyResponse.setExtensionType("world_access_response");

			// Determine whether the user has access to the specified island - and if yes,
			// do they
			// have developer access?
			boolean hasAccess = true;
			boolean isDeveloper = false;
			boolean isAdmin = false;
			InstanceTemplate island = InstanceClient.getInstanceTemplate(world);
			// instanceTemplates.get(world);
			if (!island.getIsPublic() && !island.getAdministrator().equals(accountID)
					&& !island.getDevelopers().contains(accountID)) {
				hasAccess = false;
			}
			if (!island.getPassword().equals(password))
				hasAccess = false;
			verifyResponse.setProperty("world", world);
			verifyResponse.setProperty("hasAccess", hasAccess);
			verifyResponse.setProperty("isDeveloper", isDeveloper);
			verifyResponse.setProperty("isAdmin", isAdmin);
			Engine.getAgent().sendBroadcast(verifyResponse);
			return true;
		}
	}

	class SpawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
				log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " instances=" + instances);
			OID instanceOid = spawnedMsg.getInstanceOid();
				log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " instanceOid=" + instanceOid);
			if (!instances.contains(instanceOid)) {
				return true;
			}
			ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
				log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " objInfo:" + objInfo + " objInfo.objType:"
						+ objInfo.objType);
			if (objInfo != null && objInfo.objType == ObjectTypes.player) {
				// Set the players world property
					Log.debug("AgisVehiclePlugin: SPAWNED: setting world for player: " + objOid);

				int world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
				EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "world", world);
				// Get the water height of the instance
				float globalWaterHeight = InstanceClient.getInstanceTemplate(world).getGlobalWaterHeight();
				EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "waterHeight", globalWaterHeight);
			} else if (objInfo != null && objInfo.objType == ObjectTypes.vehicle) {
				vehiclesPerInstance.computeIfAbsent(instanceOid, __ -> {
					Gauge.builder("live_vehicles",
							() -> vehiclesPerInstance.getOrDefault(instanceOid, new AtomicInteger()).get())
							.tag("instance_id", instanceOid.toString()).register(Prometheus.registry());
					return new AtomicInteger();
				}).incrementAndGet();
				// OID instanceOid = spawnedMsg.getInstanceOid();
				subscribeForVehicle(objOid);
					log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " objInfo:" + objInfo + " instanceOid:" + instanceOid);
				if (instanceNavMeshes.containsKey(instanceOid)) {
					ObjectStub objStub = (ObjectStub) EntityManager.getEntityByNamespace(objOid, Namespace.VEHICLE);
					// EntityManager.registerEntityByNamespace(obj, Namespace.VEHICLE);
					if (objStub != null && objStub.getWorldNode() != null) {
							log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " objInfo:" + objInfo + " instanceOid:"
									+ instanceOid + " objStub:" + objStub + " objStub.getWorldNode():"
									+ objStub.getWorldNode());
						DetourActor actor = new DetourActor(objOid, objStub);
						instanceNavMeshes.get(instanceOid).addActor(objOid, objInfo.loc, actor);
					}
				} else {
						log.debug("AgisVehiclePlugin: SpawnedHook: objOid:" + objOid + " objInfo:" + objInfo + " instanceOid:"
								+ instanceOid + " no NavMesh for instance");

				}
			}

			return true;
		}
	}

	class DespawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();

				Log.debug("AgisVehiclePlugin: DespawnedHook, " + objOid + " start");
			OID instanceOid = despawnedMsg.getInstanceOid();
			unsubscribeForVehicle(objOid);
			if (despawnedMsg.getType() != null && despawnedMsg.getType() == ObjectTypes.vehicle) {
				vehiclesPerInstance.computeIfAbsent(instanceOid, __ -> new AtomicInteger()).decrementAndGet();
			}
			if (instanceNavMeshes.containsKey(instanceOid)) {
				instanceNavMeshes.get(instanceOid).removeActor(objOid);
			}

				Log.debug("AgisVehiclePlugin: DespawnedHook, " + objOid + " objectsTargetType " + objectsTargetType.containsKey(objOid));
			objectsTargetType.remove(objOid);
			objectsTargetType.forEach((k, v) -> {
				v.remove(objOid);
			});
			Gauge.builder("objectsTargetType", () -> objectsTargetType.size()).register(Prometheus.registry());
			return true;
		}
	}

	class CheckVisibilityHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
			log.debug("AgisVehiclePlugin: CheckVisibilityHook: Got meaasge CheckVisibilityMessage ");
			CheckVisibilityMessage check_msg = (CheckVisibilityMessage) msg;
				Log.debug("AgisVehiclePlugin: CheckVisibilityHook Subject=" + check_msg.getSubject() + " Instance="
						+ check_msg.getInstance() + " Point=" + check_msg.getPoint() + " Points="
						+ check_msg.getPoints() + " Source Point=" + check_msg.getSourcePoint() + " Voxel="
						+ check_msg.getVoxel() + " ObjOid=" + check_msg.getObjOid());
				Log.debug("AgisVehiclePlugin: CheckVisibilityHook Subject=" + check_msg.getSubject() + " check_msg=" + check_msg);
			if (check_msg.getVoxel()) {
				boolean respon = checkVisibilityVoxel(check_msg);
			} else {
				boolean respon = checkVisibility(check_msg);
				Engine.getAgent().sendObjectResponse(msg, respon);
			}
			log.debug("AgisVehiclePlugin: CheckVisibilityHook: CheckVisibilityMessage END");
			return true;
		}

		// Log.error("DetourActor.checkVisibility: Start");
		// return navMeshManager.checkVisibility(node.getCurrentLoc(), pos);

	}

	private boolean checkVisibilityVoxel(CheckVisibilityMessage check_msg) {
			Log.debug("AgisVehiclePlugin: CheckVisibilityHook.checkVisibilityVoxel Subject=" + check_msg.getSubject() + " check_msg="
					+ check_msg);

		Point pos = check_msg.getPoint();
		OID instanceOid = check_msg.getInstance();
		// OID obj = check_msg.getSubject();
		OID obj = check_msg.getObjOid();
			log.debug("AgisVehiclePlugin: checkVisibilityVoxel: Point=" + pos + " instanceOid=" + instanceOid + " obj=" + obj + " sPoint="
					+ check_msg.getSourcePoint() + " Points=" + check_msg.getPoints());
		boolean respon = true;
		if (check_msg.getSubject().equals(instanceOid)) {
			log.debug("AgisVehiclePlugin: checkVisibilityVoxel: subject is instance");
			if (check_msg.getPoint() != null) {
				log.debug("AgisVehiclePlugin: checkVisibilityVoxel: target point");
				if (instanceNavMeshes.containsKey(instanceOid)) {
					Optional<Float> hit = instanceNavMeshes.get(instanceOid)
							.checkVisibilityNew(check_msg.getSourcePoint(), pos);
					if (check_msg.getReturnHitPoint()) {
						if (hit.isPresent()) {
							float[] startPos = new float[] { check_msg.getSourcePoint().getX(),
									check_msg.getSourcePoint().getY() + 1f, check_msg.getSourcePoint().getZ() };
							float[] endPos = new float[] { pos.getX(), pos.getY() + 1f, pos.getZ() };
							float[] raycastHitPos = hit
									.map(t -> new float[] { startPos[0] + t * (endPos[0] - startPos[0]),
											startPos[1] + t * (endPos[1] - startPos[1]),
											startPos[2] + t * (endPos[2] - startPos[2]) })
									.orElse(endPos);
							log.debug("AgisVehiclePlugin: checkVisibilityVoxel: check result hit"
									+ (new Point(raycastHitPos[0], raycastHitPos[1] - 1f, raycastHitPos[2])));
							Engine.getAgent().sendObjectResponse(check_msg,
									new Point(raycastHitPos[0], raycastHitPos[1] - 1f, raycastHitPos[2]));
						} else {
							log.debug("AgisVehiclePlugin: checkVisibilityVoxel: check result no hit");
							Engine.getAgent().sendObjectResponse(check_msg, new Point());
						}
						return respon;
					} else {
						respon = !hit.isPresent();
					}
					log.debug("AgisVehiclePlugin: checkVisibilityVoxel: check result=" + respon);
				} else {
					log.debug("AgisVehiclePlugin: checkVisibilityVoxel: not found instance");
				}

			} else if (check_msg.getPoints() != null) {
				log.debug("AgisVehiclePlugin: checkVisibilityVoxel: targets points");
				ArrayList<OID> list = new ArrayList<OID>();
				HashMap<OID, Point> list2 = new HashMap<OID, Point>();
				check_msg.getPoints().forEach((k, v) -> {
					if (instanceNavMeshes.containsKey(instanceOid)) {
						Optional<Float> hit = instanceNavMeshes.get(instanceOid)
								.checkVisibilityNew(check_msg.getSourcePoint(), v);
							log.debug("AgisVehiclePlugin: checkVisibilityVoxel: list check oid=" + k + " result=" + (!hit.isPresent()));
						if (check_msg.getReturnHitPoint()) {
							if (hit.isPresent()) {
								float[] startPos = new float[] { check_msg.getSourcePoint().getX(),
										check_msg.getSourcePoint().getY() + 1f, check_msg.getSourcePoint().getZ() };
								float[] endPos = new float[] { pos.getX(), pos.getY() + 1f, pos.getZ() };
								float[] raycastHitPos = hit
										.map(t -> new float[] { startPos[0] + t * (endPos[0] - startPos[0]),
												startPos[1] + t * (endPos[1] - startPos[1]),
												startPos[2] + t * (endPos[2] - startPos[2]) })
										.orElse(endPos);
								list2.put(k, new Point(raycastHitPos[0], raycastHitPos[1] - 1f, raycastHitPos[2]));
							} else {
								list2.put(k, new Point());
							}
						} else {
							if (!hit.isPresent()) {
								list.add(k);
							}
						}
					} else {
						log.debug("AgisVehiclePlugin: checkVisibilityVoxel: not found instance");
					}
				});
				if (check_msg.getReturnHitPoint()) {
					log.debug("AgisVehiclePlugin: checkVisibilityVoxel: check result hit list=" + list2);

					Engine.getAgent().sendObjectResponse(check_msg, list2);
				} else {
					log.debug("AgisVehiclePlugin: checkVisibilityVoxel: check result oid list=" + list);
					Engine.getAgent().sendObjectResponse(check_msg, list);
				}
				log.debug("checkVisibilityVoxel: targets end");
				return respon;
			}
		} else {
			log.debug("AgisVehiclePlugin: checkVisibilityVoxel: subject is not instance");
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					respon = da.checkVisibility(pos);
				}
			} else {
				log.debug("AgisVehiclePlugin: checkVisibilityVoxel: not found instance");
			}
			Engine.getAgent().sendObjectResponse(check_msg, respon);
			return respon;
		}
		Engine.getAgent().sendObjectResponse(check_msg, respon);
		log.debug("AgisVehiclePlugin: checkVisibilityVoxel: end");
		return respon;
	}

	class GlobalEventBonusesUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GlobalEventBonusesUpdateMessage message = (GlobalEventBonusesUpdateMessage) msg;
			// OID playerOid = message.getSubject();
			Log.debug("AgisVehiclePlugin: GlobalEventBonusesUpdateHook: " + message.getBonuses());

			globalEventBonusesArray.clear();
			globalEventBonusesArray.putAll(message.getBonuses());
			Log.debug("AgisVehiclePlugin: GlobalEventBonusesUpdateHook:  End");
			return true;
		}
	}

	private boolean checkVisibility(CheckVisibilityMessage check_msg) {
		Point pos = check_msg.getPoint();
		OID instanceOid = check_msg.getInstance();
		// OID obj = check_msg.getSubject();
		OID obj = check_msg.getObjOid();
			log.error("AgisVehiclePlugin: CheckVisibilityHook: Got meaasge CheckVisibilityMessage Point:" + pos + " instanceOid="
					+ instanceOid + " sPoint=" + check_msg.getSourcePoint() + " voxel=" + check_msg.getVoxel() + " obj="
					+ obj);
		boolean respon = true;
		if (check_msg.getVoxel()) {
			if (check_msg.getPoint() != null) {
				if (instanceNavMeshes.containsKey(instanceOid)) {
					respon = !instanceNavMeshes.get(instanceOid).checkVisibilityNew(check_msg.getSourcePoint(), pos)
							.isPresent();
				}
			}
		} else {
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					respon = da.checkVisibility(pos);
				}
			}
		}
		return respon;
	}

	private void registerTemplates() {
		ArrayList<Template> templates = vehicleDatabase.loadVehicleTemplates(1);
		for (Template tmpl : templates) {
			boolean isRegistered = ObjectManagerClient.registerTemplate(tmpl);
			Log.error("AgisVehiclePlugin: Vehicle: loaded template: [" + tmpl.getName() + "] : ID: [" + tmpl.getTemplateID()
					+ "] : Registered : " + isRegistered);
		}
	}

	class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();
			try {
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "vehicle", null);

			} catch (Exception e) {
				log.error("AgisVehiclePlugin: LoginHook Exception: " + e);
			}

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}

	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}

	class ServerTimeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisWorldManagerClient.ServerTimeMessage tMsg = (AgisWorldManagerClient.ServerTimeMessage) msg;
				log.error("AgisVehiclePlugin: TIME: got server time message with hour: " + tMsg.getHour());
			SpawnGenerator.serverTimeUpdate(tMsg.getHour(), tMsg.getMinute());
			return true;
		}
	}

	protected void registerHooks() {

	}

	// ***** 1. Spawn Instance Vehicles Hook *****
	// This hook listens for an instance load message (vehicle‐specific)
	// and schedules the loading of vehicle spawns.
	public class SpawnInstanceVehiclesHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			// We assume a vehicle instance spawn message exists in AgisVehicleClient:
			AgisVehicleClient.SpawnInstanceVehiclesMessage svMsg = (AgisVehicleClient.SpawnInstanceVehiclesMessage) msg;
			OID instanceOid = svMsg.instanceOid;
			if (instances.contains(instanceOid)) {
					Log.debug("AgisVehiclePlugin: SpawnInstanceVehiclesHook: Scheduling vehicle spawns for instance " + instanceOid);
				// The template (vehicle template) is assumed to provide a method to schedule
				// spawn loading
				svMsg.tmpl.scheduleSpawnLoading(svMsg.instanceOid);
			} else {
				Log.debug("AgisVehiclePlugin: SpawnInstanceVehiclesHook: Instance " + instanceOid + " not found.");
			}
			return true;
		}
	}

	// ***** 3. Delete Spawn Generator Hook *****
	// This hook removes the vehicle spawn generator when requested.
	public class DeleteVehicleSpawnGeneratorHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.debug("AgisVehiclePlugin: DeleteVehicleSpawnGeneratorHook: Processing delete vehicle spawn generator message");
			AgisVehicleClient.DeleteSpawnGeneratorMessage delMsg = (AgisVehicleClient.DeleteSpawnGeneratorMessage) msg;
			// Remove the spawn generator based on instance OID and spawn ID.
			SpawnGenerator.removeSpawnGeneratorByID(delMsg.getSubject(), delMsg.getSpawnId());
			return true;
		}
	}

	// ***** 5. Interact With Vehicle Hook *****
	// This hook handles vehicle interactions such as boarding or exiting.
	public class InteractWithVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			// Expect a vehicle interaction message (vehicle-specific)
			if (!(msg instanceof AgisVehicleClient.InteractWithVehicleMessage)) {
				Log.error("AgisVehiclePlugin: InteractWithVehicleHook: Unexpected message type: " + msg.getClass());
				return true;
			}
			AgisVehicleClient.InteractWithVehicleMessage intMsg = (AgisVehicleClient.InteractWithVehicleMessage) msg;
			OID playerOid = intMsg.getSubject();
			OID vehicleOid = intMsg.getVehicleOid();
			String action = intMsg.getAction();
			Log.debug("AgisVehiclePlugin: InteractWithVehicleHook: Player " + playerOid + " interacting with vehicle " + vehicleOid
					+ " action: " + action);
			// Retrieve the vehicle entity (implement getVehicleEntity() as needed)
			AgisVehicle vehicle = getVehicleEntity(vehicleOid);
			if (vehicle != null) {
				if ("board".equalsIgnoreCase(action)) {
					vehicle.enterVehicle(playerOid);
				} else if ("exit".equalsIgnoreCase(action)) {
					vehicle.exitVehicle(playerOid);
				} else {
					// Additional actions (e.g., repair, customize) can be handled here.
					Log.debug("AgisVehiclePlugin: InteractWithVehicleHook: Unknown action " + action);
				}
			} else {
				Log.error("AgisVehiclePlugin: InteractWithVehicleHook: Vehicle " + vehicleOid + " not found.");
			}
			return true;
		}
	}

	public static AgisVehicle getVehicleEntity(OID vehicleOid) {
		Log.debug("AgisVehiclePlugin: getVehicleEntity: Looking up vehicle with OID " + vehicleOid);
		Object entity = EntityManager.getEntityByNamespace(vehicleOid, Namespace.VEHICLE);
		if (entity instanceof AgisVehicle) {
			return (AgisVehicle) entity;
		} else {
			Log.error("AgisVehiclePlugin: getVehicleEntity: Entity with OID " + vehicleOid + " is not an AgisVehicle or is not found.");
			return null;
		}
	}

	protected void sendVehicleTemplates(OID playerOid) {
		log.error("Spawner sendVehicleTemplates");
		Map<String, Serializable> props = new HashMap<>();
		props.put("ext_msg_subtype", "vehicleTemplates");
		int world = ((Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "world"))
				.intValue();

		ArrayList<HashMap<String, Serializable>> vehicleTemplates = vehicleDatabase.getVehicleTemplates(1);
		int pos = 0;

		for (HashMap<String, Serializable> tmpl : vehicleTemplates) {
			props.put("vehicle_" + pos + "Name", tmpl.get("name"));
			props.put("vehicle_" + pos + "ID", tmpl.get("id"));
			props.put("vehicle_" + pos + "Model", tmpl.get("model"));
			props.put("vehicle_" + pos + "SpeedMin", tmpl.get("speedMin"));
			props.put("vehicle_" + pos + "SpeedMax", tmpl.get("speedMax"));
			props.put("vehicle_" + pos + "IsAttackable", tmpl.get("isAttackable"));
			props.put("vehicle_" + pos + "IsTargetable", tmpl.get("isTargetable"));
			// ... add other relevant fields for vehicles ...

			pos++;
		}

		props.put("numTemplates", Integer.valueOf(pos));
		log.error("AgisVehiclePlugin: Spawner sendVehicleTemplates " + props);

		WorldManagerClient.TargetedExtensionMessage msg = new WorldManagerClient.TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, Boolean.FALSE, props);
		Engine.getAgent().sendBroadcast((Message) msg);
	}

	private int generateRandomTemplateID() {
		int halfMaxValue = Integer.MAX_VALUE / 2;
		return halfMaxValue + (int) (Math.random() * (halfMaxValue));
	}
	
    
	private void createFactories() {
	    Log.debug("BEHAV: creating factory for Dot");

	    // Create MobFactory
	    VehicleFactory cFactory = new VehicleFactory(500);

	    // Add BaseBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());

	    // Add DotBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> {
	        DotBehavior behav = new DotBehavior();
	        behav.setRadius(1500);
	        return behav;
	    });

	    String factoryName = "DotFactory";
	    Log.debug("BEHAV: registering factory for Dot");
	    ObjectFactory.register(factoryName, cFactory);
	}

	private void loadData() {

		Log.error("AgisVehiclePlugin: loadData: Starting to load vehicle data");

		if (vehicleDatabase == null) {
			Log.error("AgisVehiclePlugin: loadData: vehicleDatabase is null. Cannot load vehicle data.");
			return;
		}

		try {
			Log.error("AgisVehiclePlugin: loadData: Fetching all vehicles from the database");
			List<Vehicle> vehicles = vehicleDatabase.getAllVehicles();
			Log.error("AgisVehiclePlugin: loadData: Number of vehicles fetched: " + vehicles.size());

			for (Vehicle vehicle : vehicles) {
				Log.error("AgisVehiclePlugin: loadData: Processing vehicle: " + vehicle.getClassname());

				try {
					Point location = HelperFunctions.stringToPoint(vehicle.getWorldspace());
					Quaternion orientation = HelperFunctions.stringToQuaternion(vehicle.getWorldspace());

					Log.error("AgisVehiclePlugin: loadData: Creating template for vehicle: " + vehicle.getClassname());
					Template tmpl = new Template(vehicle.getClassname(), vehicle.getObjectId(), "vehicle");

					tmpl.put(WorldManagerClient.NAMESPACE, "model", vehicle.getClassname());
					DisplayContext dc = new DisplayContext(vehicle.getClassname(), true);
					tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
					tmpl.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, location);
					// Also put vehicle-specific properties into a dedicated namespace (e.g.
					// Namespace.VEHICLE)
					tmpl.put(Namespace.VEHICLE, "displays", new LinkedList<String>()); // add your display list here
					tmpl.put(Namespace.VEHICLE, "vehicleType", "default"); // or whatever vehicle-specific properties
																			// are needed

					Log.error(
							"AgisVehiclePlugin: loadData: Registering template with ObjectManager for vehicle: " + vehicle.getClassname());
					boolean isRegistered = ObjectManagerClient.registerTemplate(tmpl);

					Log.error("AgisVehiclePlugin: loadData: Creating template in the world: " + isRegistered);
					Log.error("AgisVehiclePlugin: loadData: Spawning templateID into the world: " + vehicle.getObjectId());
					Log.error(
							"AgisVehiclePlugin: loadData: Creating vehicle object and setting properties for: " + vehicle.getClassname());

					SpawnData spawnData = new SpawnData();
					spawnData.setTemplateID(vehicle.getObjectId());
					spawnData.setClassName(vehicle.getClassname());
					spawnData.setName("Spawn_" + location);
					spawnData.setInstanceOid(OIDGenerator.generateOID()); // Use OIDGenerator here
					spawnData.setLoc(location);
					spawnData.setOrientation(orientation);
					spawnData.setNumSpawns(1);
					Log.error("AgisVehiclePlugin: loadData: Creating SpawnData object and setting properties for: "
							+ spawnData.getClassName());

					String factoryName = createVehicleFactory(spawnData);
					if (!factoryName.isEmpty()) {
						spawnData.setFactoryName(factoryName);
						AgisVehicleClient.createSpawnGenerator(spawnData);
					}

					Log.error("AgisVehiclePlugin: loadData: Loaded and registered vehicle: " + vehicle.getClassname());
				} catch (Exception e) {
					Log.error("AgisVehiclePlugin: loadData: Error processing vehicle: " + vehicle.getClassname() + ", Error: "
							+ e.getMessage());
				}
			}
		} catch (Exception e) {
			Log.error("AgisVehiclePlugin: loadData: Error loading vehicle data: " + e.getMessage());
		}

		Log.error("AgisVehiclePlugin: loadData: Finished loading vehicle data");
	}

	private static HashMap<Integer, InstanceTemplate> instanceTemplates2 = new HashMap<>();

	private void loadInstanceTemplates() {
		Log.error("AgisVehiclePlugin: Vehicle: about to load in island data from the database");
		AccountDatabase aDB = new AccountDatabase(false);
		instanceTemplates2 = aDB.loadInstanceTemplateData();
		for (InstanceTemplate island : instanceTemplates2.values()) {
			island.setVehicleSpawns(vehicleDatabase.loadInstanceSpawnData(island.getID()));
		}
		Log.error("AgisVehiclePlugin: Vehicle: finished loading in island data from the database");
	}

	public static class OIDGenerator {
		private static final AtomicLong COUNTER = new AtomicLong(1);

		public static OID generateOID() {
			long id = COUNTER.getAndIncrement();
			return new OID().fromLong(id); // Assuming OID class has a constructor accepting a long
		}
	}

	class UpdateLocationHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: UpdateLocationHook: Processing message");

			AgisVehicleClient.UpdateLocationMessage updateMsg = (AgisVehicleClient.UpdateLocationMessage) msg;
			OID vehicleOid = updateMsg.getSubject();
			Serializable newLocation = updateMsg.getProperty("location");

			// Logic to update the vehicle's location
			// e.g., find the vehicle entity and update its location
			Log.error("AgisVehiclePlugin: UpdateLocationHook: Message processed");

			return true;
		}
	}

	class UpdatePassengerCountHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: UpdatePassengerCountHook: Processing message");

			AgisVehicleClient.UpdatePassengerCountMessage countMsg = (AgisVehicleClient.UpdatePassengerCountMessage) msg;
			OID vehicleOid = countMsg.getSubject();
			int newPassengerCount = (int) countMsg.getProperty("passengerCount");

			// Logic to update the vehicle's passenger count
			// e.g., find the vehicle entity and update its passenger count property
			Log.error("AgisVehiclePlugin: UpdatePassengerCountHook: Message processed");

			return true;
		}
	}

	class ControlVehicleMovementHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: ControlVehicleMovementHook: Processing message");

			AgisVehicleClient.ControlVehicleMovementMessage movementMsg = (AgisVehicleClient.ControlVehicleMovementMessage) msg;

			// Extract the vehicle and movement details from the message
			OID vehicleOid = movementMsg.getVehicleOid();
			Serializable movementCommand = movementMsg.getMovementCommand();

			// Get current vehicle position
			BasicWorldNode currentWorldNode = WorldManagerClient.getWorldNode(vehicleOid);
			Point currentPosition = currentWorldNode.getLoc();

			// Calculate new position based on movement command
			// This is a simple example; actual implementation might involve more complex
			// physics and game logic
			float speed = 0f; // movementCommand.getSpeed(); // <_ THIS RIGHT GOD DAMNED FUKCING HERE
			Point direction = new Point(0f, 0f, 0f);// = movementCommand.getDirection(); // <_ THIS RIGHT GOD DAMNED
													// FUKCING HERE
			float deltaTime = 1.0f; // This should be the time since the last update, replace with actual

			Point newPosition = new Point(currentPosition.getX() + direction.getX() * speed * deltaTime,
					currentPosition.getY() + direction.getY() * speed * deltaTime,
					currentPosition.getZ() + direction.getZ() * speed * deltaTime);

			// Update the vehicle's position
			currentWorldNode.setLoc(newPosition);
			WorldManagerClient.updateWorldNode(vehicleOid, currentWorldNode);

			// Broadcast the updated position to the clients (players)
			WorldManagerClient.ObjectInfo objectInfo = new WorldManagerClient.ObjectInfo();

			WorldManagerClient.TargetedPropertyMessage propMsg = new WorldManagerClient.TargetedPropertyMessage();
			Engine.getAgent().sendBroadcast(propMsg);
			Log.error("AgisVehiclePlugin: ControlVehicleMovementHook: Message processed");

			return true;
		}
	}

	class SetDriverHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: SetDriverHook: Processing message");

			// Implement location update logic
			Log.error("AgisVehiclePlugin: SetDriverHook: Message processed");

			return true;
		}
	}

	class DriveVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: DriveVehicleHook: Processing message");

			// Implement passenger count update logic
			Log.error("AgisVehiclePlugin: DriveVehicleHook: Message processed");

			return true;
		}
	}

	class EnterVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: EnterVehicleHook: Processing message");

			AgisVehicleClient.EnterVehicleMessage enterMsg = (AgisVehicleClient.EnterVehicleMessage) msg;
			OID vehicleOid = enterMsg.getVehicleOid();
			OID passengerOid = enterMsg.getPassengerOid();

			AgisVehicle vehicle = (AgisVehicle) getWorldManagerEntity(vehicleOid);
			vehicle.enterVehicle(passengerOid);
			// Additional logic for entering the vehicle
			Log.error("AgisVehiclePlugin: EnterVehicleHook: Message processed");

			return true;
		}

		private AgisVehicle getWorldManagerEntity(OID vehicleOid) {
			Log.error("AgisVehiclePlugin: AgisVehicle: Processing message");

			// TODO Auto-generated method stub
			Log.error("AgisVehiclePlugin: AgisVehicle: Message processed");

			return null;
		}
	}

	class ExitVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: ExitVehicleHook: Processing message");

			AgisVehicleClient.ExitVehicleMessage exitMsg = (AgisVehicleClient.ExitVehicleMessage) msg;
			OID vehicleOid = exitMsg.getVehicleOid();
			OID passengerOid = exitMsg.getPassengerOid();

			AgisVehicle vehicle = (AgisVehicle) getWorldManagerEntity(vehicleOid);
			vehicle.exitVehicle(passengerOid);
			// Additional logic for exiting the vehicle
			Log.error("AgisVehiclePlugin: ExitVehicleHook: Message processed");

			return true;
		}

		private AgisVehicle getWorldManagerEntity(OID vehicleOid) {
			Log.error("AgisVehiclePlugin: AgisVehicle: Processing message");

			// TODO Auto-generated method stub
			return null;
		}
	}

	class StopVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("AgisVehiclePlugin: StopVehicleHook: Processing message");

			// Implement vehicle movement control logic
			Log.error("AgisVehiclePlugin: StopVehicleHook: Message processed");

			return true;
		}
	}

	class SpawnVehicleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {

			Log.error("AgisVehiclePlugin: SpawnVehicleHook: Processing message");

			if (!(msg instanceof AgisVehicleClient.SpawnedMessage)) {
				Log.error("AgisVehiclePlugin: SpawnVehicleHook: Received unexpected message type: " + msg.getClass());
				return true;
			}
			AgisVehicleClient.SpawnedMessage spawnMsg = (AgisVehicleClient.SpawnedMessage) msg;
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;

				log.error("AgisVehiclePlugin: SpawnVehicleHook sd=" + spawnMsg.spawnData + " " + spawnMsg.spawnDataID);

			SpawnData spawnData = vehicleDatabase.loadSpecificSpawnData(spawnMsg.spawnDataID);
			spawnData.setName("Spawn_" + spawnMsg.location);
			spawnData.setInstanceOid(spawnMsg.getMsgInstanceOid());
			spawnData.setLoc(spawnMsg.location);
			spawnData.setOrientation(spawnMsg.orientation);
			spawnData.setNumSpawns(1);

			String vehicleType = (String) spawnMsg.getProperty("vehicleType");
			Serializable location = spawnMsg.getProperty("location");
			// Create a vehicle factory

			String factoryName = createVehicleFactory(spawnData);
			if (!factoryName.isEmpty()) {
				spawnMsg.spawnData.setFactoryName(factoryName);
				AgisVehicleClient.createSpawnGenerator(spawnData);
			}

			// Logic to spawn a new vehicle
			// e.g., create a new vehicle entity of the specified type at the given location
			Log.error("AgisVehiclePlugin: SpawnVehicleHook: Message processed");

			return true;
		}
	}

	
	private static String generateFactoryKey(int templateID, SpawnData sd) {
	    BehaviorTemplate behavTmpl = (BehaviorTemplate) sd.getProperty("behaviourTemplate");
	    StringBuilder keyBuilder = new StringBuilder();
	    keyBuilder.append(templateID);

	    keyBuilder.append("_hasCombat=").append(behavTmpl.getHasCombat());
	    keyBuilder.append("_isChest=").append(behavTmpl.getIsChest());
	    keyBuilder.append("_pickupItem=").append(behavTmpl.getPickupItem());
	    keyBuilder.append("_baseAction=").append(behavTmpl.getBaseAction());

	    keyBuilder.append("_otherActions=").append(behavTmpl.getOtherActions().hashCode());
	    keyBuilder.append("_baseAction=").append(behavTmpl.getBaseAction());

	    String keyString = keyBuilder.toString();
	    int keyHash = keyString.hashCode();

	    return templateID + "_" + keyHash;
	}
	
	
public static String createVehicleFactory(SpawnData sd) { 
    try {
        log.error("AgisVehiclePlugin: createVehicleFactory START");
        
        // Dump SpawnData details
        log.error("SpawnData Details: " + sd.toString());
        log.error("SpawnData - TemplateIDs: " + sd.getTemplateIDs());
        log.error("SpawnData - RandomTemplateID: " + sd.getRandomTemplateID());
        log.error("SpawnData - Location: " + sd.getLoc());
        log.error("SpawnData - Orientation: " + sd.getOrientation());
        log.error("SpawnData - NumSpawns: " + sd.getNumSpawns());
        log.error("SpawnData - RespawnTime: " + sd.getRespawnTime());
        
        // Generate factory key
        int templateID = sd.getRandomTemplateID();
        String factoryKey = generateFactoryKey(templateID, sd);
        log.error("Factory Key generated: " + factoryKey);
        
        // Check if factory already exists
        String factoryName = factoryCache.get(factoryKey);
        if (factoryName != null) {
            log.error("Factory already exists for key " + factoryKey + ": " + factoryName);
            return factoryName;
        }
        log.error("No existing factory found for key " + factoryKey + ", proceeding with template loading");
        
        // Load and register templates
        for (int tID : sd.getTemplateIDs().keySet()) {
            log.error("Loading template with ID: " + tID);
            Template tmpl = ObjectManagerClient.getTemplate(tID, ObjectManagerPlugin.VEHICLE_TEMPLATE);
            if (tmpl == null) {
                log.error("Template not found for template ID: " + tID);
                return "";
            }
            log.error("Template loaded for ID " + tID + ": " + tmpl.toString());
            
            // Get display information
            String meshName = "";
            LinkedList<String> displays = (LinkedList<String>) tmpl.get(WorldManagerClient.NAMESPACE, "displays");
            if (displays != null && !displays.isEmpty()) {
                meshName = displays.get(0);
                log.error("Display mesh found for template " + tID + ": " + meshName);
            } else {
                log.error("No display mesh found for template " + tID);
            }
            
            log.error("Creating DisplayContext with meshName: " + meshName);
            DisplayContext dc = new DisplayContext(meshName, true);
            dc.addSubmesh(new DisplayContext.Submesh("", ""));
            log.error("DisplayContext created: " + dc.toString());
            
            tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
            tmpl.put(WorldManagerClient.NAMESPACE, "model", meshName);
            log.error("Updated template " + tID + " with display context and model: " + tmpl.toString());
            
            ObjectManagerClient.registerTemplate(tmpl);
            log.error("Template registered for ID " + tID);
        }
        log.error("Finished loading templates, proceeding to create factory");
        // Create new factory
        log.error("Creating vehicle factory for template: " + templateID);
        Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.VEHICLE_TEMPLATE);
        if (tmpl == null) {
            log.error("Final template load failed for templateID: " + templateID);
            return "";
        }
        log.error("Final template loaded: " + tmpl.toString());
        
        VehicleFactory vFactory = new VehicleFactory(templateID);
	    // Add behaviors using suppliers that accept per-instance data
        vFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());
	
	    // Get behavior template
	    BehaviorTemplate behavTmpl = (BehaviorTemplate) sd.getProperty("behaviourTemplate");
        
        
	    
	    // Add BaseBehavior
	    vFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());
				
		if (behavTmpl.getBaseAction() != null) {
			sd.setProperty("baseAction", behavTmpl.getBaseAction());
		}
		
	    // Set spawn properties
	    HashMap<String, Serializable> spawnProps = new HashMap<>();
	    spawnProps.put("weaponsSheathed", behavTmpl.getWeaponsSheathed());
	    spawnProps.put("otherUse", behavTmpl.getOtherUse());
	    spawnProps.put("spawnId", sd.getProperty("id"));
	    sd.setProperty("props", spawnProps);
	    
        
        factoryName = "Factory_" + factoryKey;
        log.error("Instantiated VehicleFactory with templateID " + templateID);
        log.error("Registering factory with name: " + factoryName);
        
        ObjectFactory.register(factoryName, vFactory);
        numFactories++;
        
        if (Log.loggingDebug)
            Log.debug("VEHICLE: Finished creating vehicle factory for template: " + templateID);
        
        log.debug("createVehicleFactory END");
        // Cache the factory
        factoryCache.put(factoryKey, factoryName);
        log.error("Factory cached with key " + factoryKey + ": " + factoryName);
        
        return factoryName;
    } catch (Exception e) {
        log.error("AgisVehiclePlugin: createVehicleFactory : Exception occurred: " + e.getMessage());
        return "";
    }
}


	private static HashMap<OID, InstanceNavMeshManager> instanceNavMeshes = new HashMap<OID, InstanceNavMeshManager>();

}
