package atavism.agis.behaviors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.AgisMobPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.msgsys.*;
import atavism.server.objects.*;
import atavism.server.engine.Behavior;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.util.*;
import atavism.server.pathing.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.MobPathCorrectionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

public class BaseBehavior extends Behavior implements Runnable {
	public BaseBehavior() {
		super();
	}

	public BaseBehavior(SpawnData data) {
		super(data);
	}

	public void initialize() {
		lock = LockFactory.makeLock("BaseBehaviorLock");
		OID oid = obj.getOid();
		pathState = new PathState(oid, pathObjectTypeName, true);
        SubscriptionManager.get().subscribe(this, obj.getOid(), Behavior.MSG_TYPE_COMMAND,
                WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION, EnginePlugin.MSG_TYPE_SET_PROPERTY,
                EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK);
	}

	public void activate() {
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.activate: " + obj.getOid());
		activated = true;
	}

	public void deactivate() {
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.deactivate: " + obj.getOid());
		lock.lock();
		try {
			activated = false;
            SubscriptionManager.get().unsubscribe(this);
		} finally {
			lock.unlock();
		}
	}

	public void handleMessage(Message msg, int flags) {
		try {
			if (msg.getMsgType() == Behavior.MSG_TYPE_COMMAND) {
				Behavior.CommandMessage commandMsg = (Behavior.CommandMessage) msg;
				if (Log.loggingDebug)
					Log.debug("THREAT: received message: " + commandMsg.getCmd() + " for mob: " + obj.getOid());
			}
			lock.lock();
			if (activated == false)
				return; // return true;
			if (msg.getMsgType() == Behavior.MSG_TYPE_COMMAND) {

				Behavior.CommandMessage cmdMsg = (Behavior.CommandMessage) msg;
				String command = cmdMsg.getCmd();
				// Remove the executor, because anything we do will end the current execution.
				// Engine.getExecutor().remove(this);

				// if (task != null)
				// task.cancel(true);
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.onMessage: command = " + command + "; oid = " + obj.getOid() + "; name " + obj.getName());
				if (command.equals(MSG_CMD_TYPE_GOTO)) {
					if (Log.loggingDebug)
						Log.debug("BaseBehavior.onMessage: GotoCommandMessage oid=" + obj.getOid());
					if (task != null)
						task.cancel(true);
					GotoCommandMessage gotoMsg = (GotoCommandMessage) msg;
					Point destination = gotoMsg.getDestination();
					if (destination == null) {
						Log.debug("BaseBehavior: goto destination is null");
						return;
					}
					mode = MSG_CMD_TYPE_GOTO;
					roamingBehavior = true;
					gotoSetup(destination, gotoMsg.getSpeed());
				} else if (command.equals(MSG_CMD_TYPE_GOTO_ROAM)) {
					if (task != null)
						task.cancel(true);
					GotoRoamCommandMessage gotoMsg = (GotoRoamCommandMessage) msg;
					Point centerLoc = gotoMsg.getCenterLoc();
					float radius = gotoMsg.getRadius();
					mode = MSG_CMD_TYPE_GOTO;
					roamingBehavior = true;
					Point destination = Points.findNearby(centerLoc, radius);

					gotoSetup(destination, gotoMsg.getSpeed());
				} else if (command.equals(MSG_CMD_TYPE_STOP)) {
					
					if (task != null) {
						if (Log.loggingDebug)Log.debug("BaseBehavior.onMessage: command = "+ command + "; oid = " + obj.getOid() +" "+task.getDelay(TimeUnit.MICROSECONDS));
						task.cancel(true);
					}
					followTarget = null;
					pathState.clear();
					obj.getWorldNode().setDir(new AOVector(0, 0, 0));
					obj.updateWorldNode();
					if(mode==MSG_CMD_TYPE_GOTO) {
						gotoUpdate();
					}
					mode = MSG_CMD_TYPE_STOP;
					// If roamingBehavior is set, that means that we used formerly had a roaming
					// behavior, so send an ArrivedEventMessage so that the other
					// behavior starts up again.
					
					if (roamingBehavior) {
						try {
							Engine.getAgent().sendBroadcast(new ArrivedEventMessage(obj));
							cancelPathInterpolator(obj.getOid(), pathState.getEndLoc());
						} catch (Exception e) {
							Log.error("BaseBehavior.onMessage: Error sending ArrivedEventMessage, error was '" + e.getMessage() + "'");
							throw new RuntimeException(e);
						}
					}
				} else if (command.equals(BaseBehavior.MSG_CMD_TYPE_FOLLOW)) {
					if (task != null)
						task.cancel(true);
					FollowCommandMessage followMsg = (FollowCommandMessage) msg;
					if (followMsg.getTarget() != null) {
						mode = MSG_CMD_TYPE_FOLLOW;
						followSetup(followMsg.getTarget(), followMsg.getSpeed(), followMsg.getDistanceToFollowAt());
					} else {
						followTarget = null;
						pathState.clear();
						obj.getWorldNode().setDir(new AOVector(0, 0, 0));
						obj.updateWorldNode();
						mode = MSG_CMD_TYPE_STOP;
					}

				} else if (command.equals(BaseBehavior.MSG_CMD_TYPE_DISABLE)) {
					if (task != null)
						task.cancel(true);
					deactivate();
				} else if (command.contains(MSG_CMD_TYPE_MOVE_DEBUG_ON)) {
					if (Log.loggingDebug)
						Log.debug("BaseBehavior.handleMessage: command oid = " + obj.getOid() + " starting Debuging mob movement");

					moveDebug = true;
					String target = command.substring(MSG_CMD_TYPE_MOVE_DEBUG_ON.length());
					targetDebug = OID.fromString(target);
					if (Log.loggingDebug)
						Log.debug("BaseBehavior.handleMessage: command oid = " + obj.getOid() + " task=" + task + " " + task.isCancelled() + " " + task.isDone());

					if (sf == null) {
						MoveDebugTimer mdt = new MoveDebugTimer();
						sf = Engine.getExecutor().scheduleAtFixedRate(mdt, 0L, 150L, TimeUnit.MILLISECONDS);
					}
					if (Log.loggingDebug)
						Log.debug("BaseBehavior.handleMessage: command oid = " + obj.getOid() + " |task=" + task + " " + task.isCancelled() + " " + task.isDone());

				} else if (command.contains(MSG_CMD_TYPE_MOVE_DEBUG_OFF)) {
					if (Log.loggingDebug)
						Log.debug("BaseBehavior.handleMessage: command oid = " + obj.getOid() + " stoping Debuging mob movement");
					moveDebug = false;
					// String target = command.substring(MSG_CMD_TYPE_MOVE_DEBUG_OFF.length());
					targetDebug = null;
					if (sf != null) {
						sf.cancel(true);
						sf = null;
					}
				}
			} else if (msg.getMsgType() == WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION) {
				MobPathCorrectionMessage rMsg = (MobPathCorrectionMessage) msg;
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.handleMessage: oid = " + obj.getOid() + "; getMsgType=" + msg.getMsgType() + " == WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION  mobSpeed=" + mobSpeed + " Arrived="
							+ rMsg.getArrived());
				if (!rMsg.getArrived()) {
					Engine.getExecutor().remove(this);
					interpolatePath();
					interpolatingPath = false;
				}
			//	Point _loc = obj.getWorldNode().getLoc();

				if (Log.loggingDebug)
					Log.debug("BaseBehavior.handleMessage: oid = " + obj.getOid() +  " MSG_TYPE_MOB_PATH_CORRECTION End");
			} else if (msg.getMsgType() == EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK) {
				EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
				OID oid = rMsg.getSubject();
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.handleMessage: obj.oid = " + obj.getOid() + " oid=" + oid + " ; getMsgType=" + msg.getMsgType() + " == EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK  mobSpeed=" + mobSpeed
							+ " msg=" + rMsg);
				if (oid.equals(obj.getOid())) {

					if (rMsg.containsKey(WorldManagerClient.WORLD_PROP_NOMOVE)) {
						boolean oldnomove = nomove;
						nomove = (Boolean) rMsg.getProperty(WorldManagerClient.WORLD_PROP_NOMOVE);
						if (Log.loggingDebug)
							Log.debug("BaseBehavior.handleMessage: obj.oid = " + obj.getOid() + " oid=" + oid + " ; set nomove=" + nomove);
						if (!nomove && oldnomove) {
							boolean inCombat = false;
							PatrolBehavior pBehav = null;
							RadiusRoamBehavior rrBehav = null;
							for (Behavior behav : obj.getBehaviors()) {

								if (behav instanceof CombatBehavior) {
									CombatBehavior cBehav = (CombatBehavior) behav;
									inCombat = cBehav.getInCombat();
								}
								if (behav instanceof PatrolBehavior) {
									pBehav = (PatrolBehavior) behav;

								}
								if (behav instanceof RadiusRoamBehavior) {
									rrBehav = (RadiusRoamBehavior) behav;

								}
							}
							if (!inCombat) {
								if (pBehav != null) {
									// Restore Path
									pBehav.nextPatrol();
								}
								if (rrBehav != null) {
									// Restore Path
									rrBehav.startRoam();
								}

							}
						}
					}
				}

			} else if (msg.getMsgType() == EnginePlugin.MSG_TYPE_SET_PROPERTY) {
				EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
				OID oid = rMsg.getSubject();
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.handleMessage: obj.oid = " + obj.getOid() + " oid=" + oid + " ; getMsgType=" + msg.getMsgType() + " == EnginePlugin.MSG_TYPE_SET_PROPERTY  mobSpeed=" + mobSpeed + " msg="
							+ rMsg);

			}
			// return true;
		} finally {
			lock.unlock();
		}
	}

	public void gotoSetup(Point dest, float speed) {
		// calculate the vector to the destination

		Point myLoc = obj.getWorldNode().getLoc();
		AOVector newDir2 = null;

		if(AgisMobPlugin.isNavMeshForInstance(obj.getWorldNode().getInstanceOid())){
			if (Log.loggingDebug)Log.debug("BaseBehavior.gotoSetup oid=" + obj.getOid() + " navmesh is set fot instance skip");
			return;
		}

		if (myLoc != null) {
			if (destLoc != null) {
				newDir2 = new AOVector(destLoc).sub(myLoc);
				newDir2.normalize();
			}
		}
		if (Log.loggingDebug)Log.debug("goto oid=" + obj.getOid() + " myL=" + myLoc + " dstL=" + dest + " speed " + speed + " newDir2=" + newDir2+" interpolatingPath="+interpolatingPath);
		destLoc = dest;
		mobSpeed = speed;
		if (myLoc != null && dest != null) {
			Point myLoc2 = myLoc;
			OID oid = obj.getOid();
			if (interpolatingPath) {
				interpolatePath();
				myLoc = obj.getWorldNode().getLoc();
			} else {

			}
			if (Log.loggingDebug)Log.debug("goto oid=" + obj.getOid() + " myL=" + myLoc );
			if(myLoc==null)
				myLoc = myLoc2;
			if (Log.loggingDebug)Log.debug("goto oid=" + obj.getOid() + " myL=" + myLoc +" || ");

			if (myLoc != null) 

				if (newDir2 != null) {
					AOVector orgdir = obj.getWorldNode().getDir();
					AOVector orgdir2 = new AOVector(orgdir);
					orgdir2.normalize();
					AOVector newDir = new AOVector(dest).sub(myLoc);
					newDir.normalize();
					float angle = orgdir2.getAngleY(newDir);
					float angle2 = newDir2.getAngleY(newDir);
					float angle3 = newDir2.getAngleY(orgdir2);

					if (Log.loggingDebug)Log.debug("goto oid=" + oid + " myL=" + myLoc + " dstL=" + dest + " angle=" + angle + " angle2=" + angle2 + " angle3=" + angle3 + " dist=" + Point.distanceTo(myLoc, dest) + " sp=" + speed + " m="
							+ mode + " oDir=" + orgdir + " nDir=" + newDir + " nDir2=" + newDir2);
					Log.debug("!!!!!! BaseBehavior.gotoSetup: oid = " + oid + "; myLoc = " + myLoc + "; dest = " + dest + " mobSpeed=" + mobSpeed + " dist=" + Point.distanceTo(myLoc, dest) + " ");

				}
				if (Log.loggingDebug)Log.debug("goto oid=" + obj.getOid() + " myL=" + myLoc + " dstL=" + dest + " speed " + speed+" shedule");
				// if (Log.loggingDebug)
				scheduleMe(setupPathInterpolator(oid, myLoc, dest, false, 0, obj.getWorldNode().getFollowsTerrain()));
			
		}
	}

	public void gotoUpdate() {
		OID oid = obj.getOid();
		Point myLoc = null;
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.gotoUpdate: START oid = " + obj.getOid() + "; dest = " + destLoc + " mobSpeed=" + mobSpeed + " interpolatingPath=" + interpolatingPath);
		if (interpolatingPath) {
			interpolatePath();
			myLoc = obj.getWorldNode().getLoc();
		//	if (Log.loggingDebug)
			//	Log.error("BaseBehavior.gotoUpdate: oid = " + obj.getOid() + "; myLoc = " + myLoc + "; dest = " + destLoc + " mobSpeed=" + mobSpeed);
			if (!interpolatingPath) {
				mode = MSG_CMD_TYPE_STOP;
				//Log.error("********BaseBehavior.gotoUpdate: oid = " + obj.getOid() + "; myLoc = " + myLoc + "; dest = " + destLoc + " mobSpeed=" + mobSpeed +" Arrived");
				WorldManagerClient.MobPathCorrectionMessage correction = new WorldManagerClient.MobPathCorrectionMessage(oid, System.currentTimeMillis(), "linear", 0, "", new LinkedList<Point>(), true);
				Engine.getAgent().sendBroadcast(correction);
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.gotoUpdate sending ArrivedEventMessage: oid = " + oid + "; myLoc = " + myLoc + "; destLoc = " + destLoc);
				Engine.getAgent().sendBroadcast(new ArrivedEventMessage(obj));

				for (Behavior behav : obj.getBehaviors()) {
					if (behav instanceof CombatBehavior) {
						CombatBehavior cBehav = (CombatBehavior) behav;
						if (cBehav.evade) {
							cBehav.evade = false;
							CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET_END);
						}
						if (cBehav.goreturn) {
							cBehav.goreturn = false;
							cBehav.combatStartLoc = null;
							CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET_END);
						}
					}
				}

			}
		}
		if (interpolatingPath)
			scheduleMe(pathState.pathTimeRemaining() > 5 ? pathState.pathTimeRemaining() - 5 : 0);
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.gotoUpdate: END oid = " + obj.getOid() + "; myLoc = " + myLoc + "; dest = " + destLoc + " mobSpeed=" + mobSpeed + " interpolatingPath=" + interpolatingPath);

	}

	public void followSetup(EntityHandle target, float speed, float distance) {
		Log.debug(" !!!!!!!!!!!!!! followSetup !!!!!!!!!!!!!!!!");

		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followSetup: oid = " + obj.getOid() + "; myLoc = " + obj.getWorldNode().getLoc() + "; target = " + target + " speed" + speed);
		if(AgisMobPlugin.isNavMeshForInstance(obj.getWorldNode().getInstanceOid())){
			if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup oid=" + obj.getOid() + " navmesh is set fot instance skip");
			return;
		}
		followTarget = target;
		distanceToFollowAt = AgisMobPlugin.PET_FOLLOW_RANGE;
		mobSpeed = speed;
		InterpolatedWorldNode node = obj.getWorldNode();
		Point myLoc = node.getLoc();
		OID oid = obj.getOid();
		ObjectStub followObj = (ObjectStub) followTarget.getEntity(Namespace.MOB);
		float len = Point.distanceTo(prevTargetLoc, myLoc);
		float dist = Point.distanceTo(destLoc, myLoc);


		if (followObj == null || followObj.getWorldNode() == null|| (len < AgisMobPlugin.PET_FOLLOW_RANGE * 0.95f && dist < 0.1f)) {
			followTarget = null;
			pathState.clear();
			obj.getWorldNode().setDir(new AOVector(0, 0, 0));
			obj.updateWorldNode();
			mode = MSG_CMD_TYPE_STOP;
			Log.warn("FOLLOW: got null followObj: " + followTarget);
			return;
		}
		prevTargetLoc = followObj.getWorldNode().getLoc();
		destLoc = Points.findNearby(prevTargetLoc, AgisMobPlugin.PET_FOLLOW_RANGE);
//		destLoc = followLoc;
		float followDist = Point.distanceTo(myLoc, prevTargetLoc);
//		Point _followLoc = new Point(followLoc);
//		if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup: oid = " + oid +" _followLoc="+_followLoc+" ");
//		_followLoc.sub(myLoc);
//		if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup: oid = " + oid +" _followLoc="+_followLoc+" sub my loc ");
//		float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
//		_followLoc.multiply(1 / len);
//		if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup: oid = " + oid +" _followLoc="+_followLoc+" * 1/len ");
//		_followLoc.multiply(followDist - followTarget.getHitBox());
//		if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup: oid = " + oid +" _followLoc="+_followLoc+" * followDist - hitbox ");
//		_followLoc.add(myLoc);
//		if (Log.loggingDebug)Log.debug("BaseBehavior.followSetup: oid = " + oid + "; myLoc = " + myLoc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + followDist + " len=" + len + " target hitBox="
//				+ followTarget.getHitBox() + " speed=" + speed + " distance=" + distance);
//		destLoc = _followLoc;
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followSetup: oid = " + oid + "; myLoc = " + myLoc + "; followLoc=" + prevTargetLoc + "; destLoc="+destLoc+"; distanceToFollowAt = " + distanceToFollowAt + " mobSpeed=" + mobSpeed + " dist follow=" + followDist);
		scheduleMe(setupPathInterpolator(oid, myLoc, destLoc, true, AgisMobPlugin.PET_FOLLOW_RANGE, node.getFollowsTerrain()));

	}

	protected void scheduleMe(long timeToDest) {
		long ms = Math.min(250L, timeToDest);
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.scheduleMe: oid = " + obj.getOid() + " ms = " + ms + " task=" + task + " " + (task != null ? task.isCancelled() + "" : "") + " " + (task != null ? task.isDone() + "" : "")+" "+(task != null ? task.getDelay(TimeUnit.MILLISECONDS) + "" : ""));
		// if (task != null && !task.isDone())
		// task.cancel(true);
		task = Engine.getExecutor().schedule(this, ms, TimeUnit.MILLISECONDS);
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.scheduleMe: oid = " + obj.getOid() + " ms = " + ms + " task=" + task + " " + task.isCancelled() + " " + task.isDone()+" "+(task != null ? task.getDelay(TimeUnit.MILLISECONDS) + "" : ""));
	}

	public void followUpdate() {
		if (mobSpeed == 0)
			return;
		if (Log.loggingDebug)	Log.debug(" !!!!!!!!!!!!!!!!! followUpdate !!!!!!!!!!!!!!!!!!!");
		ObjectStub followObj = (ObjectStub) followTarget.getEntity(Namespace.MOB);
		if (interpolatingPath)
			interpolatePath();
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followUpdate: followTarget=" + followObj.getOid());
		Point followLoc = followObj.getWorldNode().getLoc();
		AOVector dir = followObj.getWorldNode().getDir();
		Log.debug("BaseBehavior.followUpdate: after get location of the target");

		InterpolatedWorldNode node = obj.getWorldNode();
		Point myLoc = node.getLoc();
		OID oid = obj.getOid();
		float fdist = Point.distanceTo(myLoc, destLoc);
		float pdist = Point.distanceTo(followLoc, prevTargetLoc);

//		float dist = Point.distanceTo(destLoc, myLoc);
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followUpdate: oid = " + oid + "; myLoc = " + myLoc + "; followLoc = " + followLoc + "; fdist = " + fdist + "; pdist = " + pdist + " interpolatingPath=" + interpolatingPath + " "
					+ pathState.pathTimeRemaining() + " Thread" + Thread.currentThread().getName());
		long msToSleep = 250L;
		// If the new target location is more than a meter from
		// the old one, create a new path.
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followUpdate: fdist=" + fdist + " pdist=" + pdist+" PET_FOLLOW_RANGE="+AgisMobPlugin.PET_FOLLOW_RANGE);
		if ( pdist > 0.1f ) {
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.followUpdate: fdist=" + fdist + " > 0.1f");

			/*
			 * if (interpolatingPath) interpolatePath();
			 */
			// Log.debug("BaseBehavior.followUpdate: after Interpolate path");
			Point destinationLoc = Points.findNearby(followLoc, AgisMobPlugin.PET_FOLLOW_RANGE);
			long msToDest = setupPathInterpolator(oid, node.getLoc(), destinationLoc, true, AgisMobPlugin.PET_FOLLOW_RANGE, node.getFollowsTerrain());
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.followUpdate: msToDest=" + msToDest);

			//destLoc = followLoc;
			destLoc = destinationLoc;
			prevTargetLoc = followLoc;
			msToSleep = msToDest == 0L ? 250L : Math.min(250L, msToDest);
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.followUpdate: msToDest=" + msToDest);

			if (moveDebug) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("x", destLoc.getX());
				props.put("y", destLoc.getY());
				props.put("z", destLoc.getZ());
				props.put("mob", obj.getOid());
				props.put("dx", dir.getX());
				props.put("dy", dir.getY());
				props.put("dz", dir.getZ());
				props.put("ext_msg_subtype", "debugMobMoveTarget");
				TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
				Engine.getAgent().sendBroadcast(TEmsg);
			}
			Log.debug("BaseBehavior.followUpdate: end fdist > 0.1f");
		}
		// Else if we're interpolating, interpolate the current path
		else if (interpolatingPath) {
			// interpolatePath();
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.followUpdate: oid = " + oid + "; interpolated myLoc = " + obj.getWorldNode().getLoc());
		}
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.followUpdate: setup time =" + (interpolatingPath ? msToSleep : pathState.pathTimeRemaining()));
		scheduleMe(interpolatingPath ? msToSleep : pathState.pathTimeRemaining());

	}

	protected long setupPathInterpolator(OID oid, Point myLoc, Point dest, boolean follow, float distanceToFollowAt, boolean followsTerrain) {
		long timeNow = System.currentTimeMillis();

		WorldManagerClient.MobPathReqMessage reqMsg = pathState.setupPathInterpolator(timeNow, myLoc, dest, mobSpeed, follow, distanceToFollowAt, followsTerrain);
		if (moveDebug) {
			try {
				if (Log.loggingDebug)
					Log.debug("MobDebug.setupPathInterpolator set Oid=" + oid + " loc=" + myLoc + " dest=" + dest);
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("x", reqMsg.getPathPoints().size() > 1 ? reqMsg.getPathPoints().get(reqMsg.getPathPoints().size() - 1).getX() : reqMsg.getPathPoints().get(0).getX());
				props.put("y", reqMsg.getPathPoints().size() > 1 ? reqMsg.getPathPoints().get(reqMsg.getPathPoints().size() - 1).getY() : reqMsg.getPathPoints().get(0).getY());
				props.put("z", reqMsg.getPathPoints().size() > 1 ? reqMsg.getPathPoints().get(reqMsg.getPathPoints().size() - 1).getZ() : reqMsg.getPathPoints().get(0).getZ());
				props.put("mob", obj.getOid());
				props.put("ext_msg_subtype", "mobdstposition");
				TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
				Engine.getAgent().sendBroadcast(TEmsg);
			} catch (Exception e) {
				Log.error("MobDebug.setupPathInterpolator: " + e);
				e.printStackTrace();
			}
		}
		if (Log.loggingDebug)
			Log.debug("setupPathInterpolator: reqMsg=" + reqMsg);
		if (reqMsg != null) {
			try {
				Engine.getAgent().sendBroadcast(reqMsg);
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.setupPathInterpolator: send MobPathReqMessage " + reqMsg);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			interpolatingPath = true;
			return pathState.pathTimeRemaining();
		} else {
			interpolatingPath = false;
			return 0;
		}
	}

	protected void cancelPathInterpolator(OID oid, Point currentLoc) {
		Log.debug("PATH: cancelling interpolation");
        WorldManagerClient.MobPathReqMessage cancelMsg = new WorldManagerClient.MobPathReqMessage(oid);
        try {
            Engine.getAgent().sendBroadcast(cancelMsg);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
		
	}

	protected boolean interpolatePath() {
		long timeNow = System.currentTimeMillis();

		PathLocAndDir locAndDir = pathState.interpolatePath(timeNow);
		OID oid = obj.getOid();
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.interpolatePath: oid=" + oid + " locAndDir=" + locAndDir + " nomove=" + nomove + " timeNow=" + timeNow);
		if (nomove) {
			Log.debug("BaseBehavior.interpolatePath: no move");

			obj.getWorldNode().setDir(new AOVector(0, 0, 0));
			Log.debug("BaseBehavior.interpolatePath: reset dir and update Traker");

			MobManagerPlugin.getTracker(obj.getInstanceOid()).updateEntity(obj);
			Log.debug("BaseBehavior.interpolatePath: return");
			pathState.clear();
			// Engine.getExecutor().remove(this);
			// interpolatePath();
			// interpolatingPath = false;
			return false;
		} else {
			locAndDir = pathState.interpolatePath(timeNow);
		}
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.interpolatePath: oid=" + oid + " || locAndDir=" + locAndDir + " nomove=" + nomove + " timeNow=" + timeNow);

		if (locAndDir == null) {
			Log.debug("BaseBehavior.interpolatePath: locAndDir is null");
			obj.getWorldNode().setDir(new AOVector(0, 0, 0));
			obj.getWorldNode().setPathInterpolatorValues(timeNow, new AOVector(0, 0, 0), pathState.getEndLoc(), Quaternion.Identity);
			// We have arrived - - turn off interpolation, and cancel that path
			if (interpolatingPath) {
				if (Log.loggingDebug)
					Log.debug("BaseBehavior.interpolatePath: cancelling path: oid = " + oid + "; myLoc = " + obj.getWorldNode().getLoc());
				cancelPathInterpolator(oid, pathState.getEndLoc());
				interpolatingPath = false;
			}
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.interpolatePath: loc=" + pathState.getEndLoc());
			// obj.updateWorldNode();
		} else {
			Log.debug("BaseBehavior.interpolatePath: locAndDir is not null ");
			obj.getWorldNode().setPathInterpolatorValues(timeNow, locAndDir.getDir(), locAndDir.getLoc(), locAndDir.getOrientation());
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.interpolatePath: loc=" + locAndDir.getLoc());
			MobManagerPlugin.getTracker(obj.getInstanceOid()).updateEntity(obj);
		}
		return interpolatingPath;
	}

	int count = 0;

	public void run() {
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.run " + obj.getOid() + " activated=" + activated);
		long start = System.currentTimeMillis();
		try {
			lock.lock();
			if (activated == false) {
				return;
			}
			if (Log.loggingDebug)
				Log.debug("BaseBehavior.run || " + mode);
			try {
				if (mode == MSG_CMD_TYPE_GOTO) {
					gotoUpdate();
				} else if (mode == MSG_CMD_TYPE_FOLLOW) {
					followUpdate();
				} else if (mode == MSG_CMD_TYPE_STOP) {
				} else {
					Log.error("BaseBehavior.run: invalid mode");
				}

				if (count > 40) {
					WorldManagerClient.MobPathMessage reqMsg = pathState.UpdatePathMessage(obj.getWorldNode().getLoc());
					if (reqMsg != null) {
						Engine.getAgent().sendBroadcast(reqMsg);
					}
					count = 0;
				} else {
					count++;
				}

			} catch (Exception e) {
				Log.exception("BaseBehavior.run caught exception raised during run for mode = " + mode, e);
				throw new RuntimeException(e);
			}
		} finally {
			lock.unlock();
		}
		long end = System.currentTimeMillis();
		if (Log.loggingDebug)
			Log.debug("BaseBehavior.run || End " + (end - start) + " ms");
	}

	ScheduledFuture sf = null;

	public class MoveDebugTimer implements Runnable {

		public MoveDebugTimer() {
		}

		@Override
		public void run() {
			Log.debug("MoveDebugTimer");
			// if (moveDebug) {
			// interpolatePath();
			Point loc = obj.getWorldNode().getLoc();
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("x", loc.getX());
			props.put("y", loc.getY());
			props.put("z", loc.getZ());
			props.put("mob", obj.getOid());
			props.put("ext_msg_subtype", "mobposition");
			if (Log.loggingDebug)Log.debug("MoveDebugTimer: mode="+mode+" props="+props);
			TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
			Engine.getAgent().sendBroadcast(TEmsg);
			// }

		}
	}

	public static class GotoCommandMessage extends Behavior.CommandMessage {

		public GotoCommandMessage() {
			super(MSG_CMD_TYPE_GOTO);
		}

		public GotoCommandMessage(ObjectStub obj, Point dest, float speed) {
			super(obj, MSG_CMD_TYPE_GOTO);
		//	Log.dumpStack("GotoCommandMessage "+obj.getOid());
			if (Log.loggingDebug)Log.debug("GotoCommandMessage oid="+obj+" loc="+dest+" speed="+speed);
			setDestination(dest);
			setSpeed(speed);
		}

		public Point getDestination() {
			return dest;
		}

		public void setDestination(Point dest) {
			this.dest = dest;
		}

		public float getSpeed() {
			return speed;
		}

		public void setSpeed(float speed) {
			this.speed = speed;
		}

		private Point dest;
		private float speed;

		private static final long serialVersionUID = 1L;
	}

	public static class GotoRoamCommandMessage extends Behavior.CommandMessage {

		public GotoRoamCommandMessage() {
			super(MSG_CMD_TYPE_GOTO_ROAM);
		}

		public GotoRoamCommandMessage(ObjectStub obj, Point centerLoc, float radius, float speed) {
			super(obj, MSG_CMD_TYPE_GOTO_ROAM);
			if (Log.loggingDebug)Log.debug("GotoRoamCommandMessage oid="+obj+" centerLoc="+centerLoc+" radius="+radius+" speed="+speed);
			setCenterLoc(centerLoc);
			setSpeed(speed);
			setRadius(radius);
		}

		public Point getCenterLoc() {
			return centerLoc;
		}

		public void setCenterLoc(Point centerLoc) {
			this.centerLoc = centerLoc;
		}

		public float getSpeed() {
			return speed;
		}

		public void setSpeed(float speed) {
			this.speed = speed;
		}

		public float getRadius() {
			return radius;
		}

		public void setRadius(float radius) {
			this.radius = radius;
		}

		private Point centerLoc;
		private float radius;
		private float speed;

		private static final long serialVersionUID = 1L;
	}

	public static class FollowCommandMessage extends Behavior.CommandMessage {

		public FollowCommandMessage() {
			super(MSG_CMD_TYPE_FOLLOW);
		}

		public FollowCommandMessage(ObjectStub obj, EntityHandle target, Float speed, Float distanceToFollowAt) {
			super(obj, MSG_CMD_TYPE_FOLLOW);
			if (Log.loggingDebug)Log.debug("FollowCommandMessage oid="+obj+" target="+target+" distanceToFollowAt="+distanceToFollowAt+" speed="+speed);
			setTarget(target);
			setSpeed(speed);
			setDistanceToFollowAt(distanceToFollowAt);
		}

		public EntityHandle getTarget() {
			return target;
		}

		public void setTarget(EntityHandle target) {
			this.target = target;
		}

		public Float getSpeed() {
			return speed;
		}

		public void setSpeed(Float speed) {
			this.speed = speed;
		}

		public Float getDistanceToFollowAt() {
			return distanceToFollowAt;
		}

		public void setDistanceToFollowAt(Float distanceToFollowAt) {
			this.distanceToFollowAt = distanceToFollowAt;
		}

		private EntityHandle target;
		private Float speed;
		private Float distanceToFollowAt;

		private static final long serialVersionUID = 1L;
	}

	public static class StopCommandMessage extends Behavior.CommandMessage {

		public StopCommandMessage() {
			super(MSG_CMD_TYPE_STOP);
		}

		public StopCommandMessage(OID objOid) {
			super(objOid, MSG_CMD_TYPE_STOP);
			if (Log.loggingDebug)Log.debug("StopCommandMessage oid="+objOid);
		}

		public StopCommandMessage(ObjectStub obj) {
			super(obj, MSG_CMD_TYPE_STOP);
			if (Log.loggingDebug)Log.debug("StopCommandMessage oid="+obj.getOid());
		}

		private static final long serialVersionUID = 1L;
	}

	public static class DisableCommandMessage extends Behavior.CommandMessage {

		public DisableCommandMessage() {
			super(MSG_CMD_TYPE_DISABLE);
		}

		public DisableCommandMessage(OID objOid) {
			super(objOid, MSG_CMD_TYPE_DISABLE);
		}

		public DisableCommandMessage(ObjectStub obj) {
			super(obj, MSG_CMD_TYPE_DISABLE);
		}

		private static final long serialVersionUID = 1L;
	}

	public static class ArrivedEventMessage extends Behavior.EventMessage {

		public ArrivedEventMessage() {
			super();
			setEvent(MSG_EVENT_TYPE_ARRIVED);
		}

		public ArrivedEventMessage(OID objOid) {
			super(objOid);
			setEvent(MSG_EVENT_TYPE_ARRIVED);
			if (Log.loggingDebug)
			Log.debug("ArrivedEventMessage oid="+objOid);
		}

		public ArrivedEventMessage(ObjectStub obj) {
			super(obj);
			setEvent(MSG_EVENT_TYPE_ARRIVED);
			if (Log.loggingDebug)
			Log.debug("ArrivedEventMessage oid="+obj.getOid());
		}

		private static final long serialVersionUID = 1L;
	}

	protected String getPathObjectTypeName() {
		return pathObjectTypeName;
	}

	// ??? How does this get initialized? It should be a property of
	// the mob. For now, I'll just set it to "Generic"
	String pathObjectTypeName = "Generic";

	Point destLoc = null;
	Point prevTargetLoc;
	long arriveTime = 0;

	// The state of the pathing system for this mob
	PathState pathState = null;

	EntityHandle followTarget = null;
	float distanceToFollowAt = 0;
	float mobSpeed = 0;
	boolean nomove = false;
	boolean interpolatingPath = false;

	boolean moveDebug = false;
	OID targetDebug = null;
	transient protected Lock lock = null;

	protected String mode = MSG_CMD_TYPE_STOP;
	protected boolean roamingBehavior = false;
	public boolean activated = false;
	protected ScheduledFuture task;

	public static final String MSG_CMD_TYPE_GOTO = "goto";
	public static final String MSG_CMD_TYPE_GOTO_ROAM = "roam";
	public static final String MSG_CMD_TYPE_FOLLOW = "follow";
	public static final String MSG_CMD_TYPE_STOP = "stop";
	public static final String MSG_CMD_TYPE_DISABLE = "disable"; // Used to disable the old base behavior movement system
	public static final String MSG_CMD_TYPE_MOVE_DEBUG_ON = "moveDebugOn";
	public static final String MSG_CMD_TYPE_MOVE_DEBUG_OFF = "moveDebugOff";

	public static final String MSG_EVENT_TYPE_ARRIVED = "arrived";

	private static final long serialVersionUID = 1L;
}
