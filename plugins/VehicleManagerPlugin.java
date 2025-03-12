package atavism.agis.plugins;

import atavism.agis.objects.SpawnGenerator;
import atavism.agis.plugins.VehicleManagerClient.CreateVehicleSpawnGeneratorMessage;
import atavism.agis.plugins.VehicleManagerClient.DeleteVehicleSpawnMessage;
import atavism.management.Management;
import atavism.msgsys.FilterUpdate;
import atavism.msgsys.IFilter;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageType;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.msgsys.SubjectMessage;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Behavior;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.engine.EnginePlugin.DeleteHook;
import atavism.server.engine.EnginePlugin.GenerateSubObjectHook;
import atavism.server.engine.EnginePlugin.LoadHook;
import atavism.server.engine.EnginePlugin.SubObjData;
import atavism.server.engine.EnginePlugin.UnloadHook;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.NamespaceFilter;
import atavism.server.messages.NamespaceSubscriptionManager;
import atavism.server.messages.PropertyMessage;
import atavism.server.messages.SubObjectFilter;
import atavism.server.messages.SubscriptionManager;
import atavism.server.network.ClientConnection.MessageCallback;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.ObjectFactory;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.ObjectTracker;
import atavism.server.objects.SpawnData;
import atavism.server.objects.Template;
import atavism.server.objects.WEObjFactory;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.ObjectManagerClient.GenerateSubObjectMessage;
import atavism.server.plugins.WorldManagerPlugin.HostInstanceFilter;
import atavism.server.plugins.WorldManagerPlugin.WorldManagerInstance;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleManagerPlugin extends EnginePlugin {
	// Initialize logger for VehicleManagerPlugin
	// -- Logging --
	protected static final Logger log = new Logger("VehicleManagerPlugin");

	// -- Message sets for normal vs. RPC calls. --
	protected final Set<MessageType> vehicleFilterTypes = new HashSet<>();
	protected final Set<MessageType> vehicleRPCFilterTypes = new HashSet<>();

	protected Set<MessageType> subObjectFilterTypes = new HashSet<>();
	protected Set<Namespace> subObjectFilterNamespaces = new HashSet<>();
	public List<OID> instances = new ArrayList<OID>();
	private static Map<String, Class> spawnGeneratorClasses = new HashMap<String, Class>();
	
	public VehicleManagerPlugin() {
		super(getPluginName());
		setPluginType("VehicleManager");
		SubscriptionManager.start(AgisVehicleClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN,
				AgisVehicleClient.MSG_TYPE_CREATE_VEHICLE_SPAWN,
				AgisVehicleClient.MSG_TYPE_ENTER_VEHICLE,
				AgisVehicleClient.MSG_TYPE_EXIT_VEHICLE, 
				AgisVehicleClient.MSG_TYPE_UPDATE_LOCATION,
				AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGERS_LOCATION, // HNG Check this 
				AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGER_COUNT,
				AgisVehicleClient.MSG_TYPE_CONTROL_VEHICLE_MOVEMENT,
				AgisVehicleClient.MSG_TYPE_SPAWN_VEHICLE, 
				AgisVehicleClient.MSG_TYPE_SET_DRIVER,
				AgisVehicleClient.MSG_TYPE_DRIVE_VEHICLE, 
				AgisVehicleClient.MSG_TYPE_STOP_VEHICLE,
				AgisVehicleClient.MSG_TYPE_SPAWN_INSTANCE_VEHICLES,
				AgisVehicleClient.MSG_TYPE_DESPAWNED_VEHICLE,
				AgisVehicleClient.MSG_TYPE_INTERACT_WITH_VEHICLE,
				AgisVehicleClient.MSG_TYPE_VEHICLE_GET_ACTOR_SPEED,
				Behavior.MSG_TYPE_EVENT, 
				Behavior.MSG_TYPE_LINKED_AGGRO, 
				Behavior.MSG_TYPE_COMMAND,
				CombatClient.MSG_TYPE_COMBAT_LOGOUT, 
				CombatClient.MSG_TYPE_DAMAGE,
				EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK, 
				EnginePlugin.MSG_TYPE_SET_PROPERTY,
				LogoutMessage.MSG_TYPE_LOGOUT, 
				PropertyMessage.MSG_TYPE_PROPERTY,
				WorldManagerClient.MSG_TYPE_DESPAWNED);
		log.error("VehicleManagerPlugin.WorldManagerPlugin() |");

	}

	private static String getPluginName() {
		String vmAgentName;
		try {
			vmAgentName = Engine.getAgent().getDomainClient().allocName("PLUGIN", "VehicleManager#");
		} catch (IOException e) {
			throw new RuntimeException("Could not allocate vehicle manager plugin name", e);
		}
		return vmAgentName;
	}

	public void onActivate() {
		try {
			log.error("VehicleManagerPlugin.onActivate()");
			// register for msgtype->hooks
			registerHooks();

			List<Namespace> namespaces = new LinkedList<Namespace>();
			namespaces.add(Namespace.VEHICLE);
			namespaces.add(Namespace.VEHICLE_INSTANCE);

			VehicleManagerFilter selectionFilter = new VehicleManagerFilter(getName());
			SubObjectFilter subObjectFilter = new SubObjectFilter();
			subObjectFilter.setMatchSubjects(true);
			log.error("VehicleManagerPlugin.onActivate() |");

			registerPluginNamespaces(namespaces, new VehicleManagerPluginGenerateSubObjectHook(), selectionFilter,
					subObjectFilter);
			// Remove the subscriptions as we will use SubscriptionManager instead
			Engine.getAgent().removeSubscription(subObjectSubscription);
			Engine.getAgent().removeSubscription(propertySubscription);

			subObjectFilterTypes.addAll(subObjectFilter.getMessageTypes());
			subObjectFilterNamespaces.addAll(subObjectFilter.getNamespaces());
			log.error("VehicleManagerPlugin.onActivate() ||");

			HostInstanceFilter hostInstanceFilter = new HostInstanceFilter(getName());
			hostInstanceFilter.addType(WorldManagerClient.MSG_TYPE_HOST_INSTANCE);
			Engine.getAgent().createSubscription(hostInstanceFilter, this, MessageAgent.RESPONDER);
			
			log.error("VehicleManagerPlugin.onActivate() |||");

			registerUnloadHook(Namespace.VEHICLE, new VehicleUnloadHook());
			registerDeleteHook(Namespace.VEHICLE, new VehicleDeleteHook());

			vehicleRPCFilterTypes.add(VehicleManagerClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN);
			vehicleRPCFilterTypes.add(VehicleManagerClient.MSG_TYPE_CREATE_VEHICLE_SPAWN);
			vehicleRPCFilterTypes.add(InstanceClient.MSG_TYPE_INSTANCE_DELETED);
			vehicleRPCFilterTypes.add(InstanceClient.MSG_TYPE_INSTANCE_UNLOADED);

			ObjectFactory.register("WEObjFactory", new WEObjFactory());
			log.debug("VehcileManagerPlugin.onActivate() |V");
			registerLoadHook(Namespace.VEHICLE, new VehicleLoadHook());

			registerLoadHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceLoadHook());
			registerUnloadHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceUnloadHook());
			registerDeleteHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceDeleteHook());

			createSubscriptions(); // Subscribe to relevant messages
		} catch (Exception e) {
			throw new RuntimeException("VehicleManagerPlugin activate failed", e);
		}
		log.error("Registering Vehicle Manager plugin");
		Engine.registerStatusReportingPlugin(this);
	}


	class VehicleUnloadHook implements UnloadHook {
		public void onUnload(Entity entity) {
			if (entity instanceof ObjectStub) {
				((ObjectStub) entity).unload();
			}
			unsubscribeForVehicle(entity.getOid());
		}
	}

	class VehicleDeleteHook implements DeleteHook {
		public void onDelete(Entity entity) {
			if (entity instanceof ObjectStub) {
				((ObjectStub) entity).unload();
			}
			unsubscribeForVehicle(entity.getOid());
		}

		public void onDelete(OID oid, Namespace namespace) {
		}
	}

	class VehicleManagerPluginGenerateSubObjectHook extends GenerateSubObjectHook {
		public VehicleManagerPluginGenerateSubObjectHook() {
			super(VehicleManagerPlugin.this);
		}

		public SubObjData generateSubObject(Template template, Namespace namespace, OID masterOid) {
				log.error("VehicleManagerGenerateSubObjectHook: masterOid=" + masterOid + " namespace=" + namespace
						+ " template=" + template);

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;
			SubObjData subObjData = new SubObjData();
			if (namespace == VehicleManagerClient.INSTANCE_NAMESPACE) {
				subObjData = generateInstanceSubObject(masterOid, persistent);
			}
			log.error("VehicleManagerPluginGenerateSubObjectHook: generateSubObject masterOid=" + masterOid + " persistent=" + persistent);

	        // Attempt to retrieve the vehicle sub-map.
	        Map<String, Serializable> props = template.getSubMap(Namespace.VEHICLE);
	        if (props == null) {
	            Log.error("VehicleManagerGenerateSubObjectHook: no props in ns " + Namespace.VEHICLE + ", creating empty map");
	            props = new HashMap<>();
	            template.putSubMap(Namespace.VEHICLE, props);
	        }

			subscribeForVehicle(masterOid);

			return subObjData;
		}

		public SubObjData generateInstanceSubObject(OID masterOid, Boolean persistent) {
			WorldManagerInstance instance = createInstanceEntity(masterOid);
			instance.setPersistenceFlag(persistent);
			log.error("VehicleManagerPlugin: generateInstanceSubObject masterOid=" + masterOid + " persistent=" + persistent);
			if (persistent)
				Engine.getPersistenceManager().persistEntity(instance);
			// initializeInstance(instance);
			subscribeForObject(masterOid);
			hostInstance(masterOid);

			// ##
			// WorldManagerClient.sendPerceiverRegionsMsg(0L,
			// Geometry.maxGeometry(), null);

			return new SubObjData();
		}
	} // end WorldManagerGenerateSubObjectHook

	WorldManagerInstance createInstanceEntity(OID instanceOid) {
		WorldManagerInstance instance = new WorldManagerInstance(instanceOid);
		log.error("VehicleManagerPlugin: createInstanceEntity instanceOid=" + instanceOid);
		initializeInstance(instance);

		EntityManager.registerEntityByNamespace(instance, VehicleManagerClient.INSTANCE_NAMESPACE);

		return instance;
	}

	void hostInstance(OID masterOid) {
		VehicleManagerFilter.InstanceGeometry instanceGeo = new VehicleManagerFilter.InstanceGeometry();
		instanceGeo.instanceOid = masterOid;

		// Update WM subscriptions with new instance
		FilterUpdate filterUpdate = new FilterUpdate();
		filterUpdate.addFieldValue(VehicleManagerFilter.FIELD_INSTANCES, instanceGeo);
		((VehicleManagerFilter) selectionFilter).applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(selectionSubscription, filterUpdate, MessageAgent.BLOCKING);
	}

	protected void registerHooks() {
		//getHookManager().addHook(VehicleManagerClient.MSG_TYPE_CREATE_VEHICLE_SPAWN, new CreateSpawnGenHook());

		//getHookManager().addHook(VehicleManagerClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN, new RemoveSpawnGenHook());

		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_DELETED, new InstanceUnloadedHook());
		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_UNLOADED, new InstanceUnloadedHook());
		
    	//getHookManager().addHook(VehicleManagerClient.MSG_TYPE_REMOVE_SPAWN_GEN, new RemoveSpawnGenHook());
    	//getHookManager().addHook(VehicleManagerClient.MSG_TYPE_CREATE_SPAWN_GEN, new CreateSpawnGenHook());



	}

	// Used for both instance unloaded and instance deleted
	class InstanceUnloadedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SubjectMessage message = (SubjectMessage) msg;
			OID instanceOid = message.getSubject();

			SpawnGenerator.cleanupInstance(instanceOid);
			removeTracker(instanceOid);

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}

	public static void removeTracker(OID instanceOid) {
		trackers.remove(instanceOid);
	}

	class InstanceUnloadHook implements UnloadHook {
		public void onUnload(Entity entity) {
			instances.remove(entity.getOid());
			log.debug("VehicleManagerPlugin: InstanceUnloadHook entity=" + entity.getOid() + " inst oid=" + entity.getOid());
			unsubscribeForVehicle(entity.getOid());
			unhostInstance(entity.getOid());
		}
	}

	class InstanceDeleteHook implements DeleteHook {
		public void onDelete(Entity entity) {
			instances.remove(entity.getOid());
			log.debug("VehicleManagerPlugin: InstanceDeleteHook entity=" + entity.getOid() + " inst oid=" + entity.getOid());
			unsubscribeForVehicle(entity.getOid());
			unhostInstance(entity.getOid());
		}

		public void onDelete(OID oid, Namespace namespace) {
			// n/a
		}
	}

	protected void unsubscribeForVehicle(OID oid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
			log.debug("VehicleManagerPlugin: unsubscribeForObject: oid=" + oid);

		SubscriptionManager.get().unsubscribe(this, oid, vehicleFilterTypes);
		SubscriptionManager.get(MessageAgent.RESPONDER).unsubscribe(this, oid, vehicleRPCFilterTypes);
		unsubscribeForObject(oid);
	}

	protected void unsubscribeForObject(OID oid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
			log.debug("VehicleManagerPlugin: unsubscribeForObject: oid=" + oid);

		NamespaceSubscriptionManager.get(MessageAgent.RESPONDER, subObjectFilterNamespaces).unsubscribe(this, oid);
		NamespaceSubscriptionManager.get(subObjectFilterNamespaces).unsubscribe(this, oid);
	}

	class RemoveSpawnGenHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			VehicleManagerClient.DeleteSpawnGeneratorMessage message = (VehicleManagerClient.DeleteSpawnGeneratorMessage) msg;
				Log.error("VehicleManagerPlugin: RemoveSpawnGenHook: " + message.getSubject() + " " + message.getSpawnId());
			SpawnGenerator.removeSpawnGeneratorByID(message.getSubject(), message.getSpawnId());
			Engine.getAgent().sendBooleanResponse((Message) message, Boolean.valueOf(true));
			return true;
		}
	}

	class CreateSpawnGenHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			VehicleManagerClient.CreateSpawnGeneratorMessage message = (VehicleManagerClient.CreateSpawnGeneratorMessage) msg;
			Log.error("VehicleManagerPlugin: CreateSpawnGenHook");
			SpawnData spawnData = message.getSpawnData();
				Log.error("VehicleManagerPlugin: CreateSpawnGenHook spawnData=" + spawnData);
			ObjectFactory factory = ObjectFactory.getFactory(spawnData.getFactoryName());
			if (factory == null) {
				String factoryName = AgisVehiclePlugin.createVehicleFactory(spawnData);
				if (!factoryName.equals("")) {
					spawnData.setFactoryName(factoryName);
					factory = ObjectFactory.getFactory(spawnData.getFactoryName());
				}
			}
			if (factory == null) {
				Engine.getAgent().sendBooleanResponse((Message) message, Boolean.valueOf(false));
					Log.error("VehicleManagerPlugin: CreateSpawnGenHook: unknown factory=" + spawnData.getFactoryName());
				return true;
			}
			SpawnGenerator spawnGen = null;
			String spawnGenClassName = (String) spawnData.getProperty("className");
			if (spawnGenClassName == null)
				spawnGenClassName = spawnData.getClassName();
			if (spawnGenClassName == null) {
				spawnGen = new SpawnGenerator(spawnData);
			} else {
				try {
					Class<SpawnGenerator> spawnGenClass = (Class) spawnGeneratorClasses.get(spawnGenClassName);
					if (spawnGenClass == null)
						throw new AORuntimeException("spawn generator class not registered");
					spawnGen = spawnGenClass.newInstance();
					spawnGen.initialize(spawnData);
				} catch (Exception ex) {
					Log.exception("VehicleManagerPlugin: CreateSpawnGenHook: failed instantiating class " + spawnGenClassName, ex);
					Engine.getAgent().sendBooleanResponse((Message) message, Boolean.valueOf(false));
					return true;
				}
			}
			spawnGen.setObjectFactory(factory);
			spawnGen.activate();
			Engine.getAgent().sendBooleanResponse((Message) message, Boolean.valueOf(true));
			Log.error("VehicleManagerPlugin: CreateSpawnGenHook End");
			return true;
		}
	}

	/**
	 * Create the necessary subscriptions for normal broadcast messages and/or RPC
	 * messages (RESPONDER).
	 */
	protected void createSubscriptions() {
		// For RPC calls that expect a return value:
		vehicleRPCFilterTypes.add(VehicleManagerClient.MSG_TYPE_CREATE_VEHICLE_SPAWN);
		vehicleRPCFilterTypes.add(VehicleManagerClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN);
		Engine.getAgent().createSubscription(new MessageTypeFilter(vehicleRPCFilterTypes), this,
				MessageAgent.RESPONDER);

		ObjectFactory.register("WEObjFactory", new WEObjFactory());
		log.error("VehicleManagerClient.onActivate() |V");
		registerLoadHook(Namespace.VEHICLE, new VehicleLoadHook());

		registerLoadHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceLoadHook());
		registerUnloadHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceUnloadHook());
		registerDeleteHook(VehicleManagerClient.INSTANCE_NAMESPACE, new InstanceDeleteHook());
	}

	class VehicleLoadHook implements LoadHook {
		public void onLoad(Entity entity) {
			subscribeForVehicle(entity.getOid());
		}
	}

	protected void subscribeForObject(OID masterOid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
			log.error("VehicleManagerPlugin: subscribeForObject: oid=" + masterOid);

		NamespaceSubscriptionManager.get(MessageAgent.RESPONDER, subObjectFilterNamespaces).subscribe(this, masterOid,
				subObjectFilterTypes);
		NamespaceSubscriptionManager.get(subObjectFilterNamespaces).subscribe(this, masterOid,
				MSG_TYPE_SET_PROPERTY_NONBLOCK);
	}

	class InstanceLoadHook implements LoadHook {
		public void onLoad(Entity entity) {
			WorldManagerInstance instance = (WorldManagerInstance) entity;
			log.error("VehicleManagerPlugin: InstanceLoadHook entity=" + entity.getOid() + " inst oid=" + instance.getOid());
			initializeInstance(instance);
			subscribeForVehicle(entity.getOid());
		}
	}

	void unhostInstance(OID oid) {
		// Update WM subscriptions with removed instance
		FilterUpdate filterUpdate = new FilterUpdate();
		filterUpdate.removeFieldValue(VehicleManagerFilter.FIELD_INSTANCES, oid);
		((VehicleManagerFilter) selectionFilter).applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(selectionSubscription, filterUpdate, MessageAgent.BLOCKING);

		/*
		 * filterUpdate = new FilterUpdate();
		 * filterUpdate.removeFieldValue(WorldManagerFilter.FIELD_INSTANCES, oid);
		 * newRegionFilter.applyFilterUpdate(filterUpdate);
		 * Engine.getAgent().applyFilterUpdate(newRegionSub, filterUpdate,
		 * MessageAgent.BLOCKING);
		 */ }

	void initializeInstance(WorldManagerInstance instance) {
		log.error("VehicleManagerPlugin: initializeInstance instance=" + instance + " OID:" + instance.getOid() + " " + instances);
		instances.add(instance.getOid());
		log.error("VehicleManagerPlugin: initializeInstance 2 instance=" + instance + " OID:" + instance.getOid() + " " + instances);

	}
	
	public static ObjectStub createObject(int templateID, OID instanceOid, Point loc, Quaternion orient) {
		return createObject(templateID, instanceOid, loc, orient, true);
	}

	public static ObjectStub createObject(int templateID, OID instanceOid, Point loc, Quaternion orient, boolean followsTerrain) {
			log.debug("VEHICLE createObject: template=" + templateID + ", point=" + loc + ", calling into objectmanager to generate");
		Template override = new Template();
		override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
		override.put(CombatClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
		override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_LOC, loc);
		if (orient != null)
			override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ORIENT, orient);
		override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_FOLLOWS_TERRAIN, followsTerrain);
		return createObject(templateID, override, null);
	}

	public static ObjectStub createObject(int templateID, Template override, OID instanceOid) {
			log.debug("VEHICLE createObject: template=" + templateID + ", override=" + override + ", instanceOid=" + instanceOid + " calling into objectmanager to generate");

		if (instanceOid != null) {
			override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
			override.put(CombatClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
			override.put(Namespace.VEHICLE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
				}
		OID objId = ObjectManagerClient.generateObject(templateID, ObjectManagerPlugin.VEHICLE_TEMPLATE, override);

			log.debug("VEHICLE generated object oid=" + objId);

		if (objId == null) {
			Log.warn("VEHICLE ManagerPlugin: oid is null, skipping");
			return null;
		}

		BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objId);
		InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
		ObjectStub obj = new ObjectStub(objId, iwNode, templateID);
		EntityManager.registerEntityByNamespace(obj, Namespace.VEHICLE);
		//WorldManagerClient.LoadSubObjectMessage loadSubMsg = new WorldManagerClient.LoadSubObjectMessage(objId, Namespace.MOB,new Point(),instanceOid);
		//Engine.getAgent().sendRPCReturnBoolean(loadSubMsg);
			log.debug("VEHICLE createObject: obj=" + obj);
		return obj;
	}

	// --------------------------------------------------------------------
	// HOOK IMPLEMENTATIONS
	// --------------------------------------------------------------------

	/**
	 * Hook for handling a "CREATE_VEHICLE_SPAWN" message.
	 *//*
	class CreateVehicleSpawnHook implements Hook {
		@Override
		public boolean processMessage(Message msg, int flags) {
			// Typically, we check if the message is an instance of
			// the specialized message class:
			if (!(msg instanceof CreateVehicleSpawnGeneratorMessage)) {
				log.error("CreateVehicleSpawnHook received unknown msg type: " + msg);
				Engine.getAgent().sendBooleanResponse(msg, false);
				return true;
			}

			CreateVehicleSpawnGeneratorMessage createMsg = (CreateVehicleSpawnGeneratorMessage) msg;
			SpawnData spawnData = (SpawnData) createMsg.getSpawnData();
			log.error("CreateVehicleSpawnHook: spawnData=" + spawnData);

			// Attempt to grab an ObjectFactory for the spawn if needed
			ObjectFactory factory = ObjectFactory.getFactory(spawnData.getFactoryName());
			if (factory == null) {
				// If no factory, we can dynamically create one
				// or return an error.
				log.error("VehicleManagerPlugin: Unknown vehicle factory: " + spawnData.getFactoryName());
				Engine.getAgent().sendBooleanResponse(msg, false);
				return true;
			}

			// Create or load a new spawn generator
			SpawnGenerator spawnGen;
			String spawnGenClassName = spawnData.getClassName();
			if (spawnGenClassName == null) {
				// Default generic spawn generator
				spawnGen = new SpawnGenerator(spawnData);
			} else {
				try {
					// If you have a custom spawn generator registry:
					Class<? extends SpawnGenerator> sgClass = (Class<? extends SpawnGenerator>) getVehicleSpawnGeneratorClass(
							spawnGenClassName);
					if (sgClass == null)
						throw new AORuntimeException(
								"Vehicle spawn generator class not registered: " + spawnGenClassName);

					spawnGen = sgClass.newInstance();
					spawnGen.initialize(spawnData);
				} catch (Exception ex) {
					log.exception("VehicleManagerPlugin: Failed instantiating class " + spawnGenClassName, ex);
					Engine.getAgent().sendBooleanResponse(msg, false);
					return true;
				}
			}

			// Set the factory for the new spawn generator
			spawnGen.setObjectFactory(factory);
			spawnGen.activate(); // Start spawning

			// Return success to the caller
			Engine.getAgent().sendBooleanResponse(msg, true);
			log.error("CreateVehicleSpawnHook: completed creation");
			return true;
		}
	}
*/
	/**
	 * Hook for handling �REMOVE_VEHICLE_SPAWN� messages.
	 *//*
	class RemoveVehicleSpawnHook implements Hook {
		@Override
		public boolean processMessage(Message msg, int flags) {
			if (!(msg instanceof DeleteVehicleSpawnMessage)) {
				log.error("RemoveVehicleSpawnHook: unknown message type: " + msg);
				Engine.getAgent().sendBooleanResponse(msg, false);
				return true;
			}

			DeleteVehicleSpawnMessage delMsg = (DeleteVehicleSpawnMessage) msg;
			OID instanceOid = delMsg.getSubject(); // The instance
			int spawnerId = delMsg.getSpawnId(); // ID of the spawn generator to remove

			log.error("RemoveVehicleSpawnHook: Removing spawn generator spawnerId=" + spawnerId + " in instanceOid="
					+ instanceOid);

			SpawnGenerator.removeSpawnGeneratorByID(instanceOid, spawnerId);

			// Let the caller know we succeeded.
			Engine.getAgent().sendBooleanResponse(msg, true);
			return true;
		}
	}*/

	// --------------------------------------------------------------------
	// You can call these methods when you first create or load a vehicle.
	// --------------------------------------------------------------------

	protected void subscribeForVehicle(OID vehicleOid) {
		if (Log.loggingDebug)
			log.error("VehicleManagerPlugin: subscribeForVehicle: vehicleOid=" + vehicleOid);

		// For normal broadcast messages:
		SubscriptionManager.get().subscribe(this, vehicleOid, vehicleFilterTypes);

		// For RPC/responder messages:
		SubscriptionManager.get(MessageAgent.RESPONDER).subscribe(this, vehicleOid, vehicleRPCFilterTypes);
	}

	// --------------------------------------------------------------------
	// OPTIONAL: If you do instance-based logic, or if you track geometry,
	// you can replicate something like "VehicleManagerFilter" with a
	// "VehicleManagerFilter" class and store instance sets, trackers, etc.
	// --------------------------------------------------------------------

	/**
	 * Example method if you have different spawn generator classes for vehicles as
	 * in �VehicleManagerPlugin.registerSpawnGeneratorClass(...)�. If not,
	 * remove or adapt this.
	 */
	public static Map<String, Class<? extends SpawnGenerator>> vehicleSpawnGeneratorClasses = new HashMap<>();

	public static void registerVehicleSpawnGeneratorClass(String name, Class<? extends SpawnGenerator> clazz) {
		vehicleSpawnGeneratorClasses.put(name, clazz);
	}

	public static Class<? extends SpawnGenerator> getVehicleSpawnGeneratorClass(String name) {
		return vehicleSpawnGeneratorClasses.get(name);
	}

	public Map<String, String> getStatusMap() {
		Map<String, String> status = new HashMap<String, String>();
		status.put("instances", Integer.toString(instances.size()));
		status.put("entities", Integer.toString(EntityManager.getEntityCount()));
		status.put("EntList", EntityManager.getEntityNamespaceCount());
		return status;
	}

	public static class VehicleManagerFilter extends NamespaceFilter {
		public VehicleManagerFilter() {
			super();
		}

		public VehicleManagerFilter(String pluginName) {
			super();
			setPluginName(pluginName);
		}

		public String getPluginName() {
			return pluginName;
		}

		public void setPluginName(String pluginName) {
			this.pluginName = pluginName;
		}

		public void addInstance(OID instanceOid) {
			// ## check for duplicates
			instances.add(instanceOid);
		}

		public void removeInstance(OID instanceOid) {
			instances.remove(instanceOid);
		}

		public boolean getInstance(OID instanceOid) {
			return instances.contains(instanceOid);
		}

		public synchronized boolean matchRemaining(Message message) {
			OID instanceOid = null;
			Point location = null;
			MessageType type = message.getMsgType();
			Namespace namespace = null;

			if (!super.matchRemaining(message)) {
				if (type != WorldManagerClient.MSG_TYPE_NEW_REGION
						&& type != WorldManagerClient.MSG_TYPE_PLAYER_PATH_WM_REQ)
					return false;
			}
				log.error("VehicleManagerFilter: type: " + type + " message=" + message);

			// generate sub-object: match on instance-oid and location
			// load sub-object: match on instance-oid and location

			if (type == ObjectManagerClient.MSG_TYPE_GENERATE_SUB_OBJECT
					&& message instanceof GenerateSubObjectMessage) {
				GenerateSubObjectMessage genMsg = (GenerateSubObjectMessage) message;
				Template template = genMsg.getTemplate();
					log.error("VehicleManagerFilter: GENERATE: getting template: " + template);

				String targetPlugin = (String) template.get(Namespace.VEHICLE_INSTANCE,
						VehicleManagerClient.TEMPL_VEHICLE_NAME);
				if (targetPlugin != null) {
					if (targetPlugin.equals(pluginName))
						return true;
					else
						return false;
				}
				log.error("VehicleManagerFilter: GENERATE: getting template: " + template);

				instanceOid = (OID) template.get(Namespace.VEHICLE, WorldManagerClient.TEMPL_INSTANCE);
				if (instanceOid == null) {
					Log.error("VehicleManagerFilter: generate msg has null instanceOid, oid=" + genMsg.getSubject());
					return false;
				}
			} else if (type == ObjectManagerClient.MSG_TYPE_LOAD_SUBOBJECT) {
				if (message instanceof WorldManagerClient.LoadSubObjectMessage) {
					WorldManagerClient.LoadSubObjectMessage loadMsg = (WorldManagerClient.LoadSubObjectMessage) message;
					instanceOid = loadMsg.getInstanceOid();
					location = loadMsg.getLocation();
				} else if (message instanceof ObjectManagerClient.LoadSubObjectMessage) {
					ObjectManagerClient.LoadSubObjectMessage loadMsg = (ObjectManagerClient.LoadSubObjectMessage) message;
					instanceOid = loadMsg.getSubject();
					namespace = loadMsg.getNamespace();
				}
			} else
			/*
			 * if (type == WorldManagerClient.MSG_TYPE_NEW_REGION) { NewRegionMessage
			 * regionMsg = (NewRegionMessage) message; instanceOid =
			 * regionMsg.getInstanceOid(); List<Geometry> localGeometry =
			 * instanceGeometry.get(instanceOid); if (localGeometry == null) return false;
			 * 
			 * // ## GAK! Must intersect region with instance geometry return true; } else
			 */
			if (type == WorldManagerClient.MSG_TYPE_PLAYER_PATH_WM_REQ) {
				WorldManagerClient.PlayerPathWMReqMessage reqMsg = (WorldManagerClient.PlayerPathWMReqMessage) message;
				instanceOid = reqMsg.getInstanceOid();
			} else {
					log.error("VehicleManagerFilter else");
			}
			if (instanceOid != null) {
				if (instances.contains(instanceOid))
					return true;

				return false;
			}

			return false;
		}

		public synchronized boolean applyFilterUpdate(FilterUpdate update) {
			List<FilterUpdate.Instruction> instructions = update.getInstructions();

			for (FilterUpdate.Instruction instruction : instructions) {
				switch (instruction.opCode) {
				case FilterUpdate.OP_ADD:
					if (instruction.fieldId == FIELD_INSTANCES) {
						InstanceGeometry instanceGeo = (InstanceGeometry) instruction.value;
							log.error("VehicleManagerFilter ADD INSTANCE " + instruction.value + " instanceGeo="
									+ instanceGeo);
						instances.add(instanceGeo.instanceOid);
					} else
						Log.error("VehicleManagerFilter: invalid fieldId " + instruction.fieldId);
					break;
				case FilterUpdate.OP_REMOVE:
					if (instruction.fieldId == FIELD_INSTANCES) {
							log.error("VehicleManagerFilter REMOVE INSTANCE " + instruction.value);
						instances.remove((OID) instruction.value);
					} else
						Log.error("VehicleManagerFilter: invalid fieldId " + instruction.fieldId);
					break;
				case FilterUpdate.OP_SET:
					Log.error("VehicleManagerFilter: OP_SET is not supported");
					break;
				default:
					Log.error("VehicleManagerFilter: invalid opCode " + instruction.opCode);
					break;
				}
			}
			return false;
		}

		public String toString() {
			return "[VehicleManagerFilter " + toStringInternal() + "]";
		}

		protected String toStringInternal() {
			return super.toStringInternal() + " pluginName=" + pluginName + " instances=" + instances.size();
		}

		public static final int FIELD_INSTANCES = 1;

		public static class InstanceGeometry {
			OID instanceOid;

			public String toString() {
				return "[InstanceGeometry instanceOid=" + instanceOid + "]";
			}

		}

		private String pluginName;
		private List<OID> instances = new ArrayList<OID>();
	}

	public static void registerSpawnGeneratorClass(String name, Class spawnGenClass) {
		synchronized (spawnGeneratorClasses) {
			spawnGeneratorClasses.put(name, spawnGenClass);
		}
	}

	/**
	 * Get a registered spawn generator class.
	 */
	public static Class getSpawnGeneratorClass(String name) {
		return spawnGeneratorClasses.get(name);
	}

	private static Map<OID, ObjectTracker> trackers = new ConcurrentHashMap<>();

}
