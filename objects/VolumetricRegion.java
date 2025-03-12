package atavism.agis.objects;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.FactionClient;
import atavism.agis.plugins.FactionPlugin;
import atavism.agis.plugins.GroupClient;
import atavism.agis.plugins.QuestClient;
import atavism.msgsys.Message;
import atavism.msgsys.MessageCallback;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.MessageType;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.SubscriptionManager;
import atavism.server.objects.AOObject;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.EntityWithWorldNode;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.ObjectTracker;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.Template;
import atavism.server.physics.CollisionCapsule;
import atavism.server.physics.CollisionOBB;
import atavism.server.physics.CollisionShape;
import atavism.server.physics.CollisionSphere;
import atavism.server.plugins.MobManagerClient;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

/**
 * A ResourceNode is an object players can gather items from. The ResourceNode
 * randomly generates its items upon spawn from the items it has been given and
 * allows the player to gather them if they meet the requirements.
 * 
 * @author Andrew Harrison
 *
 */
public class VolumetricRegion implements MessageDispatch, Serializable, MessageCallback, Runnable {
	public VolumetricRegion() {
	}

	public VolumetricRegion(int id, AOVector loc, OID instanceOID) {
		if(Log.loggingDebug)
		Log.debug("REGION: Create id=" + id + " loc=" + loc + " inst=" + instanceOID);
		this.id = id;
		this.loc = loc;
		this.instanceOID = instanceOID;
	}

	public void addShape(String shapeType, AOVector loc, AOVector loc2, Quaternion orient, float size1, float size2, float size3) {
		if(Log.loggingDebug)
			Log.debug("REGION: VolumetricRegion: shapeType=" + shapeType + " loc=" + loc + " loc2=" + loc2 + " orient=" + orient + " size1=" + size1 + " size2=" + size2 + " size3=" + size3);
		if (shapeType.equals("sphere")) {
			shapes.add(new CollisionSphere(loc, size1));
		} else if (shapeType.equals("capsule")) {
			shapes.add(new CollisionCapsule(loc, loc2, size1));
		} else if (shapeType.equals("box")) {
			AOVector[] axes = new AOVector[3];
			axes[0] = orient.getXAxis();
			axes[1] = orient.getYAxis();
			axes[2] = orient.getZAxis();
			AOVector extents = new AOVector(size1 / 2, size2 / 2, size3 / 2);
			if(Log.loggingDebug)
				Log.debug("SHAPE: added new CollisionOBB with loc: " + loc + " and extents: " + extents);
			shapes.add(new CollisionOBB(loc, axes, extents));
		}
	}

	/**
	 * Subscribes the instance to receive certain relevant messages that are sent to
	 * the world object created by this instance.
	 */
	public void activate() {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " statrting activation " + name + " " + regionType + " objectOID=" + objectOID);
		SubjectFilter filter = new SubjectFilter(objectOID);
		filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
		eventSub = Engine.getAgent().createSubscription(filter, this);
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " name=" + name + " regionType=" + regionType + " time=" + time);
		if (regionType.equals("Trap") && time > 0f) {
			DespawnRegion task = new DespawnRegion();
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " start Timer");
			timer = Engine.getExecutor().schedule(task, (long) (time * 1000L), TimeUnit.MILLISECONDS);
		}
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " Timer " + timer);
		if (regionType.equals("Water") ) {
			sf = Engine.getExecutor().scheduleAtFixedRate(this, 1000, /* 250 */100L, TimeUnit.MILLISECONDS);
		}else if( regionType.equals("Trap")) {
			sf = Engine.getExecutor().scheduleAtFixedRate(this, 1000, /* 250 */100L, TimeUnit.MILLISECONDS);
		} else {
			MessageTypeFilter filter2 = new MessageTypeFilter();
			filter2.addType(WorldManagerClient.MSG_TYPE_UPDATEWNODE);
			filter2.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		//	filter2.addType(AgisWorldManagerClient.MSG_TYPE_REVIVE);
			sub = Engine.getAgent().createSubscription(filter2, this);
		}
		mobFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_REVIVE); // S
		if(Log.loggingDebug)
			Log.debug("REGION: activating region: " + id + " sf =" + sf);
		// Set the reaction radius tracker to alert the object if a player has entered
		// its draw radius
		// Get two furtherest points on the shape
		AOVector minLoc = loc;
		AOVector maxLoc = loc;
		float largestSize = 0;
		if(Log.loggingDebug)
			Log.debug("REGION: activating region: " + id + " shapes=" + shapes);
		for (CollisionShape shape : shapes) {
			if (shape.center.getX() < minLoc.getX()) {
				minLoc.setX(shape.center.getX());
			} else if (shape.center.getX() > maxLoc.getX()) {
				maxLoc.setX(shape.center.getX());
			}

			if (shape.center.getZ() < minLoc.getZ()) {
				minLoc.setZ(shape.center.getZ());
			} else if (shape.center.getZ() > maxLoc.getZ()) {
				maxLoc.setZ(shape.center.getZ());
			}

			if (shape instanceof CollisionOBB) {
				CollisionOBB obb = (CollisionOBB) shape;
				if (obb.extents.getX() > largestSize) {
					largestSize = obb.extents.getX();
				}
				if (obb.extents.getZ() > largestSize) {
					largestSize = obb.extents.getZ();
				}
			} else if (shape.radius > largestSize) {
				largestSize = shape.radius;
			}
			if(Log.loggingDebug)
				Log.debug("REGION: activating region: " + id + " shape=" + shape + " shape.center=" + shape.center + " radius=" + shape.radius);
		}

		float distance = AOVector.distanceTo(minLoc, maxLoc);
		if(Log.loggingDebug)
			Log.debug("REGION: got distance: " + distance + " and largestSize: " + largestSize + " id=" + id);
		reactionRadius = distance + largestSize + 10;
		if (regionType.equals("Trap")) {
			MobManagerClient.setReactionRadius(instanceOID, objectOID, (int) (getReactionRadius()));
		}
		MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, (int) (getReactionRadius()));
		active = true;
		if(Log.loggingDebug)
			Log.debug("REGION: node with oid: " + objectOID + " id=" + id + " activated with radius: " + (getReactionRadius()));
	}

	public boolean deactivate() {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " statrting deactivate " + name + " " + regionType + " objectOID=" + objectOID);

		active = false;
		if (sf != null)
			sf.cancel(false);
		Log.debug("REGION: deactivating");
		MobManagerPlugin.getTracker(instanceOID).removeReactionRadius(objectOID);
		if (sub != null) {
			Engine.getAgent().removeSubscription(sub);
			sub = null;
			Log.debug("REGION: removing sub");
		}
		if (eventSub != null) {
			Engine.getAgent().removeSubscription(eventSub);
			eventSub = null;
			Log.debug("REGION: removing sub 2");
		}
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " END deactivate " + name + " " + regionType + " objectOID=" + objectOID);

		return true;
	}

	/**
	 * Deals with the messages the instance has picked up.
	 */
	public void handleMessage(Message msg, int flags) {
		if(Log.loggingDebug)
			Log.debug("REGION: handleMessage msg.getMsgType()=" + msg.getMsgType() + " active=" + active + " id=" + id + " instanceOID=" + instanceOID);

		if (active == false) {
			return;
		}
		if(Log.loggingDebug)
			Log.debug("REGION: handleMessage id=" + id + " handleMessage msg.getMsgType()=" + msg.getMsgType());
		if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
			ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage) msg;
			if(Log.loggingDebug)
				Log.debug("REGION: handleMessage REACTION id=" + id + " myOid=" + objectOID + " objOid=" + nMsg.getSubject() + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());
			if (nMsg.getInRadius()) {
				addPlayer(nMsg.getSubject());
			} else {
				// Remove subject from targets in range
				removePlayer(nMsg.getSubject());
			}
		} else if (msg instanceof WorldManagerClient.UpdateWorldNodeMessage) {
			WorldManagerClient.UpdateWorldNodeMessage updateMsg = (WorldManagerClient.UpdateWorldNodeMessage) msg;
			processUpdateMsg(updateMsg);
		} else if (msg instanceof AgisWorldManagerClient.RevivedMessage) {
			AgisWorldManagerClient.RevivedMessage rMsg = (AgisWorldManagerClient.RevivedMessage) msg;
			OID playerOid = rMsg.getObjectOid();
			if(Log.loggingDebug)
				Log.debug("REGION: VolumetricRegion: id=" + id + "  plyOid=" +playerOid+" RevivedMessage");
						
			if (playersInRegion.contains(playerOid)) {
				playerEnteredRegion(playerOid);
			}
		} else if (msg instanceof WorldManagerClient.DespawnedMessage) {
			WorldManagerClient.DespawnedMessage updateMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID plyOid = updateMsg.getSubject();
			OID instOid = updateMsg.getInstanceOid();
			if(instOid.equals(instanceOID)) {
				playerLeftRegion(plyOid);
			}else {
				
			}
			//processUpdateMsg(updateMsg);
		}
		if(Log.loggingDebug)
			Log.debug("REGION:  id=" + id + " handleMessage END");
	}

	public void processUpdateMsg(WorldManagerClient.UpdateWorldNodeMessage msg) {
		OID playerOid = msg.getSubject();
		if(Log.loggingTrace)
			Log.debug("REGION: id=" + id + " got updateMsg for: " + playerOid + " playersInRange=" + playersInRange);

		if (!playersInRange.contains(playerOid)) {
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " does not contain: " + playerOid);
			return;
		}

		boolean playerInShape = false;
		for (CollisionShape shape : shapes) {
			if (shape.pointInside(new AOVector(msg.getWorldNode().getLoc()))) {
				playerInShape = true;
				break;
			}
		}

		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " playerOid=" + playerOid + " playerInShape: " + playerInShape + " with loc: " + msg.getWorldNode().getLoc());
		if (regionType.equals("Trap")) {
			if(Log.loggingDebug)
				Log.debug("REGION: Trap processUpdateMsg id=" + id + " playerOid=" + playerOid + " playerInShape: " + playerInShape + " with loc: " + msg.getWorldNode().getLoc());
		}
		if (playersInRegion.contains(playerOid)) {
			// Is the player still in the region
			if (!playerInShape) {
				playerLeftRegion(playerOid);
			}
		} else {
			// Has the player now moved into the region
			if (playerInShape) {
				playerEnteredRegion(playerOid);
			}
		}
	}

	/**
	 * An external call to spawn a world object for the claim.
	 * 
	 * @param instanceOID
	 */
	public void spawn(OID instanceOID) {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " spawn instanceOID: " + instanceOID);
		this.instanceOID = instanceOID;
		spawn();
	}

	/**
	 * Spawn a world object for the claim.
	 */
	public void spawn() {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " spawn instanceOID: " + instanceOID + " Start");

		Template markerTemplate = new Template();
		if (regionType.equals("Trap")) {
			markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, name + "_" + id);
		} else {
			markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, name + "_ign_region" + id);
		}
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
		markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
		// markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
		DisplayContext dc = new DisplayContext("", true);
		if (regionType.equals("Trap")) {
			markerTemplate.put(Namespace.WORLD_MANAGER, "model", "");
			dc = new DisplayContext("", true);
		} else {
			markerTemplate.put(Namespace.WORLD_MANAGER, "model", "");
			// dc = new DisplayContext("", true);

		}
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);

		// Put in any additional props
		if (props != null) {
			for (String propName : props.keySet()) {
				markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
			}
		}
		// Create the object
		objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID, ObjectManagerClient.BASE_TEMPLATE, markerTemplate);

		if (objectOID != null) {
			// Need to create an interpolated world node to add a tracker/reaction radius to
			// the claim world object
			BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objectOID);
			InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
			resourceNodeEntity = new ResourceNodeEntity(objectOID, iwNode);
			EntityManager.registerEntityByNamespace(resourceNodeEntity, Namespace.MOB);
			MobManagerPlugin.getTracker(instanceOID).addLocalObject(objectOID, 100);
			// MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, 100);//addLocalObject(objectOID, 100);

			WorldManagerClient.spawn(objectOID);
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " spawned resource at : " + loc + " activationTime=" + activationTime + " " + reactionRadius);

			if (regionType.equals("Trap")) {
				MobManagerClient.setReactionRadius(instanceOID, objectOID, (int) (getReactionRadius()));
				OID oid = OID.fromString(actionData1);
				/*
				 * cE = new CoordinatedEffect(name); 
				 * cE.sendSourceOid(true);
				 * cE.sendTargetOid(true); 
				 * cE.putArgument("point",loc); 
				 * cE.invoke(oid, objectOID);
				 */
				TargetedExtensionMessage trapMsg = new TargetedExtensionMessage();
				trapMsg.setExtensionType("Trap");
				trapMsg.setProperty("point", loc);
				trapMsg.setProperty("name", name + "_" + id);
				trapMsg.setProperty("activationTime", activationTime);
				trapMsg.setProperty("oid", objectOID);
				trapMsg.setProperty("model", actionData3);
				LinkedList<OID> list = GroupClient.GetGroupMembers(oid, false);
				if(Log.loggingDebug)
					Log.debug("!!!!!!!!!!!!!!! Group List " + list);
				trapMsg.setTarget(oid);
				Engine.getAgent().sendBroadcast(trapMsg);
				for (OID oi : list) {
					if (!oi.equals(oid)) {
						trapMsg.setTarget(oi);
						Engine.getAgent().sendBroadcast(trapMsg);
					}
				}
			}
			if (activationTime > 0) {
				ActivateRegion ar = new ActivateRegion();
				Engine.getExecutor().schedule(ar, (long) (activationTime * 1000L), TimeUnit.MILLISECONDS);
			} else {
				activate();
			}
		}

		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " spawn instanceOID: " + instanceOID + " END");
	}

	public void despawn() {
		if(Log.loggingDebug)
			Log.debug("REGION: VolumetricRegion: id=" + id + " despawn objectOID=" + objectOID);

		if (objectOID != null) {
			deactivate();
			if (regionType.equals("Trap")) {
				MobManagerClient.setReactionRadius(instanceOID, objectOID, -1);
			}
			MobManagerPlugin.getTracker(instanceOID).removeLocalObject(objectOID);
			WorldManagerClient.despawn(objectOID);

			EntityManager.removeEntityByNamespace(resourceNodeEntity, Namespace.MOB);
			ObjectManagerClient.deleteObject(objectOID);

		}
		if(Log.loggingDebug)
			Log.debug("REGION: VolumetricRegion: id=" + id + " despawn END");
	}

	/**
	 * Add a player to the update list for this ResourceNode. The player will
	 * receive data about the node and any updates that occur.
	 * 
	 * @param playerOID
	 */
	public void addPlayer(OID playerOid) {
		if(Log.loggingTrace)
			Log.debug("REGION: id=" + id + " added player: " + playerOid + " playersInRange=" + playersInRange);
		// Send down the state to the player

		if (!playersInRange.contains(playerOid)) {
			playersInRange.add(playerOid);
			SubscriptionManager.get().subscribe(this, playerOid, mobFilterTypes);
			
		}

		/*
		 * BasicWorldNode casterWNode = WorldManagerClient.getWorldNode(playerOid);
		 * float distance = AOVector.distanceTo(loc, new
		 * AOVector(casterWNode.getLoc())); // Has the player now moved into the region
		 * if (distance < size1) { playerEnteredRegion(playerOid); }
		 */
		if(Log.loggingTrace)
			Log.debug("REGION: id=" + id + " END added player: " + playerOid + " playersInRange=" + playersInRange);

	}

	/**
	 * Removes a player from the ResourceNode. They will no longer receive updates.
	 * 
	 * @param playerOID
	 * @param removeLastID
	 */
	public void removePlayer(OID playerOid) {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " removed player: " + playerOid);
		if (playersInRange.contains(playerOid)) {
			playersInRange.remove(playerOid);
			 SubscriptionManager.get().unsubscribe(this, playerOid, mobFilterTypes);
		} else {
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " removePlayer player is not in playersInRange");
		}

		if (playersInRegion.contains(playerOid)) {
			playerLeftRegion(playerOid);
		} else {
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " removePlayer player is not in playersInRegion");
		}

		if (playersUnderwater.contains(playerOid)) {
			playersUnderwater.remove(playerOid);
			if (!regionType.equals("Trap")) {
				if (actionID > 0)
					CombatClient.removeEffect(playerOid, actionID, true);
			}
		} else {
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " removePlayer player is not in playersUnderwater");
		}
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " END removed player: " + playerOid);

	}

	void playerEnteredRegion(OID playerOid) {

		if(!active) {
			if(Log.loggingDebug)
				Log.debug("REGION: id=" + id + " adding player to region: " + playerOid +" region is not active skip player");
				
			return;
		}
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " adding player to region: " + playerOid);
		playersInRegion.add(playerOid);
		if(Log.loggingDebug)
			Log.debug("REGION: VolumetricRegion: id=" + id + "  " + regionType + " Player " + playerOid + " Enter Region");

		if (regionType.equals("Water")) {
			AgisWorldManagerClient.sendWaterRegionTransitionMessage(playerOid, id, true);
		} else if (regionType.equals("Dismount")) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(CombatClient.MSG_TYPE_DISMOUNT, playerOid, props);
			Engine.getAgent().sendBroadcast(eMsg);
		} else if (regionType.equals("Teleport")) {
			AgisWorldManagerClient.sendChangeInstance(playerOid, actionID, new Point(Float.parseFloat(actionData1), Float.parseFloat(actionData2), Float.parseFloat(actionData3)));
		} else if (regionType.equals("CompleteTask")) {
			QuestClient.TaskUpdateMessage msg = new QuestClient.TaskUpdateMessage(playerOid, actionID, 1);
			Engine.getAgent().sendBroadcast(msg);
		} else if (regionType.equals("StartQuest")) {
			LinkedList<Integer> quests = new LinkedList<Integer>();
			quests.add(actionID);
			QuestClient.offerQuestToPlayer(playerOid, objectOID, quests, false);
		} else if (regionType.equals("PvP")) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("pvpState", true);
			ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, playerOid, props);
			Engine.getAgent().sendBroadcast(eMsg);
		} else if (regionType.equals("Sanctuary")) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("pvpState", false);
			ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, playerOid, props);
			Engine.getAgent().sendBroadcast(eMsg);
		} else if (regionType.equals("Trap")) {
			// actionData1
			OID oid = OID.fromString(actionData1);
			int targetType = Integer.parseInt(actionData2);

			if(Log.loggingDebug)
				Log.debug("REGION: Trap VolumetricRegion: Trap objectOID=" + objectOID + " owner oid=" + oid + " targetType= " + targetType + " " + (targetType == 0 ? "Friendly" : "Enemy") + " Entered player=" + playerOid);
			if (!playerOid.equals(oid)) {
				LinkedList<OID> targets = new LinkedList<OID>();
				targets.add(playerOid);
				HashMap<OID, Integer> map = FactionClient.getStance(oid, targets);
				if(Log.loggingDebug)
					Log.debug("REGION: Trap VolumetricRegion: map=" + map);
				for (OID oOid : map.keySet()) {
					if (map.get(oOid) > FactionPlugin.Neutral && map.get(oOid) != FactionPlugin.Unknown && targetType == 0) {
						despawn();
						Point _loc = new Point(loc);
						if(Log.loggingDebug)
							Log.debug("REGION: Trap VolumetricRegion: id=" + id + " Trap " + " loc=" + _loc + " owner=" + oid + " target =" + oOid + " startAbility=" + actionID + " for friendly");
						CombatClient.startAbility(actionID, oid, playerOid, null, _loc);

						return;
					} else if ((map.get(oOid) <= FactionPlugin.Neutral || map.get(oOid) == FactionPlugin.Unknown) && targetType == 1) {
						despawn();
						Point _loc = new Point(loc);
						if(Log.loggingDebug)
							Log.debug("REGION: Trap VolumetricRegion: id=" + id + " Trap " + " loc=" + _loc + " owner=" + oid + " target =" + oOid + " startAbility=" + actionID + " for enemy,neutral");
						CombatClient.startAbility(actionID, oid, playerOid, null, _loc);
						// despawn();
						return;
					} else {
						Log.debug("REGION: Trap VolumetricRegion: ELSE !!!!!!!!!!!!");
					}
				}
				
			}
		} else {
			if (actionID > 0)
				CombatClient.applyEffect(playerOid, actionID);
		}
	}

	void playerLeftRegion(OID playerOid) {
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " removing player left region: " + playerOid);
		playersInRegion.remove(playerOid);
		if(Log.loggingDebug)
			Log.debug("REGION: VolumetricRegion: " + regionType + " Player " + playerOid + " Left Region");

		if (regionType.equals("Water")) {
			AgisWorldManagerClient.sendWaterRegionTransitionMessage(playerOid, id, false);

			if (playersUnderwater.contains(playerOid)) {
				playersUnderwater.remove(playerOid);
				CombatClient.removeEffect(playerOid, actionID,true);
			}
		} else if (regionType.equals("PvP")) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("pvpState", false);
			ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, playerOid, props);
			Engine.getAgent().sendBroadcast(eMsg);
		} else if (regionType.equals("Sanctuary")) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("pvpState", true);
			ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, playerOid, props);
			Engine.getAgent().sendBroadcast(eMsg);
		} else if (regionType.equals("Trap")) {

		} else {
			if (actionID > 0)
				CombatClient.removeEffect(playerOid, actionID);
		}
		if(Log.loggingDebug)
			Log.debug("REGION: id=" + id + " END removing player from region: " + playerOid);

	}

	@Override
	public void run() {
		// if(Log.loggingNet)
		if (regionType.equals("Trap"))
			if(Log.loggingTrace)
				Log.debug("REGION: Trap id=" + id + " run  Start playersInRange=" + playersInRange + " active=" + active);

		if (!active) {
			// activate();
			return;
		}

		// Do Check of all players to see if they are now in or out of the region
		try {
			LinkedList<OID> list = new LinkedList<OID>(playersInRange);
			if (regionType.equals("Trap"))
				if(Log.loggingDebug)
					Log.debug("REGION: Trap id=" + id + " run  list=" + list + " active=" + active);
			
			for (OID playerOid : list) {
				if (regionType.equals("Trap"))
					if(Log.loggingDebug)
						Log.debug("REGION: Trap id=" + id + " run  playerOid=" + playerOid);
				boolean playerInShape = false;
				boolean playerUnderWater = false;
				for (CollisionShape shape : shapes) {
					Entity entity = EntityManager.getEntityByNamespace(playerOid, Namespace.WORLD_MANAGER);
					if (entity == null) {
						Log.error("VolumetricRegion: WMGR entity is null for " + playerOid);
						if (playersInRange.contains(playerOid))
							playersInRange.remove(playerOid);
						break;
					}
					// get the object's current world node
					AOObject obj = (AOObject) entity;
					InterpolatedWorldNode curWnode = (InterpolatedWorldNode) obj.worldNode();
					// BasicWorldNode wNode = WorldManagerClient.getWorldNode(playerOid);
					if (Log.loggingNet)
						Log.debug("REGION: id=" + id + " playerOid=" + playerOid + " plyloc=" + curWnode.getInterpLoc() + " loc=" + loc + " name=" + name);
					if (shape.pointInside(new AOVector(curWnode.getInterpLoc()))) {
						playerInShape = true;
						// Also check if they are a a full metre underwater for breath
						AOVector abovePosition = new AOVector(curWnode.getInterpLoc().getX(), curWnode.getInterpLoc().getY() + 1, curWnode.getInterpLoc().getZ());
						if (shape.pointInside(abovePosition)) {
							playerUnderWater = true;
						}
						break;
					}
				}
				// if(Log.loggingNet)
				if (regionType.equals("Trap")) {
					if(Log.loggingDebug)
						Log.debug("REGION: Trap id=" + id + " playerOid=" + playerOid + " playerInShape=" + playerInShape + " playerUnderWater=" + playerUnderWater + " playersInRegion.contains="
							+ playersInRegion.contains(playerOid));
				}
				if (playersInRegion.contains(playerOid)) {
					// Is the player still in the region
					if (!playerInShape) {
						playerLeftRegion(playerOid);
					}
				} else {
					// Has the player now moved into the region
					if (playerInShape) {
						playerEnteredRegion(playerOid);
					}
				}
				// if(Log.loggingNet)
				if (regionType.equals("Trap")) {
					//Log.debug("REGION: Trap id=" + id + " run END");
					continue;
					// Log.debug("REGION: id=" + id + " playerOid=" + playerOid + " playerInShape=" + playerInShape + " playerUnderWater=" + playerUnderWater + " name=" + name);
				}
				if (playersUnderwater.contains(playerOid)) {
					// Is the player still in the region
					if (!playerUnderWater && actionID > 0) {
						playersUnderwater.remove(playerOid);
						CombatClient.removeEffect(playerOid, actionID, true);
					}
				} else {
					// Has the player now moved into the region
					if (playerUnderWater && actionID > 0) {
						if (Log.loggingNet)
							Log.debug("UNDER: player is now under water");
						playersUnderwater.add(playerOid);
						CombatClient.applyEffect(playerOid, actionID);
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.error("REGION: id=" + id + " Run Exception=" + e + " \n\n" + e.getMessage() + "\n\n" + e.getLocalizedMessage());
		}
		if (Log.loggingNet)
			Log.trace("REGION: id=" + id + " Run END");
		if (regionType.equals("Trap")) {
			if(Log.loggingDebug)
				Log.debug("REGION: Trap id=" + id + " Run END");
		}
			

	}

	public float getReactionRadius() {
		return reactionRadius;
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public AOVector getLoc() {
		return loc;
	}

	public void setLoc(AOVector loc) {
		this.loc = loc;
	}

	public HashMap<String, Serializable> getProps() {
		return props;
	}

	public void setProps(HashMap<String, Serializable> props) {
		this.props = props;
	}

	public OID getInstanceOID() {
		return instanceOID;
	}

	public void setInstanceOID(OID instanceOID) {
		this.instanceOID = instanceOID;
	}

	public OID getObjectOID() {
		return objectOID;
	}

	public void setObjectOID(OID objectOID) {
		this.objectOID = objectOID;
	}

	public float getSize1() {
		return size1;
	}

	public void setSize1(float size1) {
		this.size1 = size1;
	}

	public String getRegionType() {
		return regionType;
	}

	public void setRegionType(String regionType) {
		this.regionType = regionType;
	}

	public int getActionID() {
		return actionID;
	}

	public void setActionID(int actionID) {
		this.actionID = actionID;
	}

	public String getActionData1() {
		return actionData1;
	}

	public void setActionData1(String actionData1) {
		this.actionData1 = actionData1;
	}

	public String getActionData2() {
		return actionData2;
	}

	public void setActionData2(String actionData2) {
		this.actionData2 = actionData2;
	}

	public String getActionData3() {
		return actionData3;
	}

	public void setActionData3(String actionData3) {
		this.actionData3 = actionData3;
	}

	public boolean getActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public float getTime() {
		return time;
	}

	public void setTime(float time) {
		this.time = time;
	}

	float time = 0f;

	public float getActivationTime() {
		return activationTime;
	}

	public void setActivationTime(float activationTime) {
		this.activationTime = activationTime;
	}

	float activationTime = 0f;
	CoordinatedEffect cE;
	int id;
	String name = "";
	String regionType;
	AOVector loc;
	float reactionRadius;
	OID instanceOID;
	OID objectOID;
	HashMap<String, Serializable> props;
	ArrayList<CollisionShape> shapes = new ArrayList<CollisionShape>();
	float size1;
	float size2;
	float size3;
	int actionID;
	String actionData1;
	String actionData2;
	String actionData3;
	boolean active;
	Long eventSub = null;
	Set<OID> playersInRange = ConcurrentHashMap.newKeySet();
	Set<OID> playersInRegion = ConcurrentHashMap.newKeySet();
	Set<OID> playersUnderwater = ConcurrentHashMap.newKeySet();
	static ScheduledFuture<?> sf = null;
	static ScheduledFuture<?> timer = null;
	protected Set<MessageType> mobFilterTypes = new HashSet<>();
	Long sub = null;
	ResourceNodeEntity resourceNodeEntity;

	class DespawnRegion implements Runnable, Serializable {
		public DespawnRegion() {
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion DespawnRegion Create id=" + id);
		}

		public void run() {
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion DespawnRegion Run id=" + id);
			despawn();
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion DespawnRegion Run END id=" + id);
		}

		private static final long serialVersionUID = 1L;
	}

	class ActivateRegion implements Runnable, Serializable {
		public ActivateRegion() {
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion ActivateRegion Create id=" + id);
		}

		public void run() {
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion ActivateRegion Run id=" + id);
			activate();
			if(Log.loggingDebug)
				Log.debug("VolumetricRegion ActivateRegion Run END id=" + id);
		}

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Sub-class needed for the interpolated world node so a perceiver can be
	 * created.
	 * 
	 * @author Andrew
	 *
	 */
	public class ResourceNodeEntity extends ObjectStub implements EntityWithWorldNode {

		public ResourceNodeEntity(OID oid, InterpolatedWorldNode node) {
			setWorldNode(node);
			setOid(oid);
		}

	
		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;

}
