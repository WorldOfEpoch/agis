package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.plugins.AgisMobPlugin;
import org.recast4j.detour.crowd.*;

import atavism.agis.behaviors.BaseBehavior;
import atavism.agis.behaviors.CombatBehavior;
import atavism.agis.behaviors.BaseBehavior.ArrivedEventMessage;
import atavism.agis.behaviors.BaseBehavior.FollowCommandMessage;
import atavism.agis.behaviors.BaseBehavior.GotoCommandMessage;
import atavism.agis.behaviors.BaseBehavior.GotoRoamCommandMessage;
import atavism.agis.plugins.AgisMobClient;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.CombatClient;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.MessageType;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Behavior;
import atavism.server.engine.Engine;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.PropertyMessage;
import atavism.server.messages.SubscriptionManager;
import atavism.server.objects.EntityHandle;
import atavism.server.objects.EntityWithWorldNode;
import atavism.server.objects.ObjectStub;
import atavism.server.pathing.PathLocAndDir;
import atavism.server.pathing.PathState;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.CheckVisibilityMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class DetourActor implements MessageDispatch {
	
	public DetourActor(OID oid, ObjectStub obj) {
		Log.debug("DetourActor: Create Start oid:"+ oid+" obj:"+obj+" ");
		this.oid = oid;
		this.obj = obj;
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		lastDir = node.getDir();
		lastLoc = node.getCurrentLoc();
		lastTargetLoc = node.getCurrentLoc();
        
        // Create Params
        params = new CrowdAgentParams();
        params.collisionQueryRange = 6f;
        params.height = 2f;
        params.maxAcceleration = 50f;
        params.maxSpeed = 50f;
        params.obstacleAvoidanceType= 3;
        params.pathOptimizationRange = 18f;
        params.radius = 0.4f;
        params.separationWeight = 2.0f;
        params.updateFlags = 0;
       // SubscriptionManager.start();
      
	}
	
	/**
	 * Sets up the subscription to receive messages.
	 */
	public void activate() {
	    Log.debug("DetourActor: activate Start oid:"+ oid+" obj:"+obj+" ");
	    SubjectFilter filter = new SubjectFilter(oid);
        filter.addType(Behavior.MSG_TYPE_COMMAND);
        filter.addType(WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION);
        
        commandSub = Engine.getAgent().createSubscription(filter, this);

        Engine.getAgent().sendBroadcast(new ArrivedEventMessage(oid));
        pathState = new PathState(oid, "Generic", true);
        // Disable the base behavior message handler
        Engine.getAgent().sendBroadcast(new BaseBehavior.DisableCommandMessage(oid));
        Log.debug("DetourActor: activate End oid:"+ oid+" obj:"+obj+" ");
	}
	
	/**
	 * Removes the subscription to messages.
	 */
	public void deactivate() {
		if (commandSub != null) {
	        Engine.getAgent().removeSubscription(commandSub);
	        commandSub = null;
	    }
		if(commandSubResp!=null) {
			// SubscriptionManager.get(MessageAgent.RESPONDER).unsubscribe(this, oid, mobRPCFilterTypes);
			Engine.getAgent().removeSubscription(commandSubResp);
			commandSubResp = null;
		}
			
	     Log.debug("DetourActor: deactivate End oid:"+ oid+" obj:"+obj+" ");
	}	
	
	@Override
	/**
	 * Handles the messages this instance receives. Gets the message type and works out
	 * what needs to be done.
	 */
	public void handleMessage(Message msg, int flags) {
		//  if (Log.loggingDebug)
              Log.debug("DetourActor.onMessage: getMsgType = " + msg.getMsgType() + "; oid = " + oid);
		if (msg.getMsgType() == Behavior.MSG_TYPE_COMMAND) {
            Behavior.CommandMessage cmdMsg = (Behavior.CommandMessage)msg;
            String command = cmdMsg.getCmd();
          //  if (Log.loggingDebug)
                Log.debug("DetourActor.onMessage: command = " + command + "; oid = " + oid);
            if (command.equals(BaseBehavior.MSG_CMD_TYPE_GOTO)) {
                GotoCommandMessage gotoMsg = (GotoCommandMessage)msg;
                Point destination = gotoMsg.getDestination();
               // mode = BaseBehavior.MSG_CMD_TYPE_GOTO;
                roamingBehavior = true;
                setupGoto(destination, gotoMsg.getSpeed());
            }
            else if (command.equals(BaseBehavior.MSG_CMD_TYPE_STOP)) {
                followTarget = null;
                pathState.clear();
                interpolatingPath = false;
                InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
                node.setDir(new AOVector(0,0,0));
                WorldManagerClient.updateWorldNode(oid, new BasicWorldNode(node));
                mode = BaseBehavior.MSG_CMD_TYPE_STOP;
                // If roamingBehavior is set, that means that we formerly had a roaming behavior, so send
                // an ArrivedEventMessage so that the other behavior starts up again.
                if (roamingBehavior) {
                    try {
                        Engine.getAgent().sendBroadcast(new ArrivedEventMessage(oid));
                        cancelPathInterpolator(oid);
                    }
                    catch (Exception e) {
                        Log.error("BaseBehavior.onMessage: Error sending ArrivedEventMessage, error was '" + e.getMessage() + "'");
                        throw new RuntimeException(e);
                    }
                }
            }
            else if (command.equals(BaseBehavior.MSG_CMD_TYPE_FOLLOW)) {
                FollowCommandMessage followMsg = (FollowCommandMessage)msg;
                mode = BaseBehavior.MSG_CMD_TYPE_FOLLOW;
                followTarget = followMsg.getTarget();
                target = (ObjectStub)followTarget.getEntity(Namespace.MOB);
                speed = followMsg.getSpeed();
                distanceToFollowAt = followMsg.getDistanceToFollowAt();
				setupFollow(lastLoc);
            } 
            else if (command.equals(BaseBehavior.MSG_CMD_TYPE_GOTO_ROAM)) {
            	GotoRoamCommandMessage gotoMsg = (GotoRoamCommandMessage)msg;
            	  Point centerLoc = gotoMsg.getCenterLoc();
            	  float  radius = gotoMsg.getRadius();
            	//  Log.debug("DetourActor: GotoRoam centerLoc:"+centerLoc+" radius:"+radius);
              	  mode = BaseBehavior.MSG_CMD_TYPE_GOTO;
                  roamingBehavior = true;
                 // setupGoto(destination, gotoMsg.getSpeed());
                  Point point = centerLoc;
                  try {
					point = navMeshManager.findRandomPointAroundCircle(centerLoc, radius);
				} catch (Exception e) {
					Log.debug("DetourActor: find point to roam Exception "+e.getMessage()+" "+e.getLocalizedMessage());
				}
                  Log.debug("DetourActor: GotoRoam centerLoc:"+centerLoc+" radius:"+radius+" point:"+point);
                  setupGoto(point, gotoMsg.getSpeed());
            } else if (command.contains(BaseBehavior.MSG_CMD_TYPE_MOVE_DEBUG_ON)) {
				Log.error("BaseBehavior.handleMessage: command oid = " + obj.getOid()  + " starting Debuging mob movement");
				
				moveDebug = true;
				String target = command.substring(BaseBehavior.MSG_CMD_TYPE_MOVE_DEBUG_ON.length());
				targetDebug = OID.fromString(target);
				//Log.error("BaseBehavior.handleMessage: command oid = " + obj.getOid()  + " task="+task+" "+task.isCancelled()+" "+task.isDone());
				
				if(sf==null) {
					MoveDebugTimer mdt = new MoveDebugTimer();
					sf = Engine.getExecutor().scheduleAtFixedRate(mdt, 0L, 500L, TimeUnit.MILLISECONDS);
				}
				//Log.error("BaseBehavior.handleMessage: command oid = " + obj.getOid()  + " |task="+task+" "+task.isCancelled()+" "+task.isDone());
				
				
				
			} else if (command.contains(BaseBehavior.MSG_CMD_TYPE_MOVE_DEBUG_OFF)) {
				Log.error("BaseBehavior.handleMessage: command oid = " + obj.getOid()  + " stoping Debuging mob movement");
				moveDebug = false;
				//String target = command.substring(MSG_CMD_TYPE_MOVE_DEBUG_OFF.length());
				targetDebug = null;
				if(sf!=null) {
					sf.cancel(true);
					sf =null;
				}
			}
        }
        else if (msg.getMsgType() == WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION) {
            // Do we need to do anything here?
        }else if (msg.getMsgType() == WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY) {
        	   if (Log.loggingTrace)	Log.trace("DetourActor: Got meaasge CheckVisibilityMessage ");
        	CheckVisibilityMessage check_msg = (CheckVisibilityMessage)msg;
        	Point pos = check_msg.getPoint();
        	 if (Log.loggingTrace)Log.trace("DetourActor: Got meaasge CheckVisibilityMessage pos:"+pos);
        	Engine.getAgent().sendObjectResponse(msg, checkVisibility( pos));
		} else if (msg.getMsgType() == AgisWorldManagerClient.MSG_TYPE_MOB_FIND_NEAREST_POINT) {
			// Do we need to do anything here?
			PropertyMessage _msg = (PropertyMessage) msg;
			Point p = (Point) _msg.getProperty("point");
			Point pos = navMeshManager.findNearestPoint(p);
			if (Log.loggingTrace)
				Log.trace("DetourActor: Got meaasge findNearestPoint pos:" + pos);
			Engine.getAgent().sendObjectResponse(msg, pos);

		}
	}
	
	public boolean checkVisibility(Point pos) {
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		 if (Log.loggingDebug)	Log.debug("DetourActor.checkVisibility: Start");
        return navMeshManager.checkVisibility(node.getCurrentLoc(), pos);	
	}
	public boolean checkVisibilityNew(Point pos) {
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		 if (Log.loggingDebug)	Log.debug("DetourActor.checkVisibility: Start");
        return !navMeshManager.checkVisibilityNew(node.getCurrentLoc(), pos).isPresent();
	}
	
	public Point findNearestPoint(Point pos) {
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		 if (Log.loggingDebug)	Log.debug("DetourActor.findNearestPoint: Start");
        return navMeshManager.findNearestPoint(pos);	
	}
	
	
	public float getActorSpeed(OID oid) {
		if (Log.loggingDebug) Log.debug("DetourActor.getActorSpeed: Start");
		return navMeshManager.getActorSpeed(oid);
	}
	
	public ArrayList<AOVector> GetPath(Point pos) {
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		if (Log.loggingDebug)
			Log.debug("DetourActor.GetPath: Start");
		ArrayList<AOVector> path = null;
		try {
			path = navMeshManager.GeneratePathDragonsan(node.getCurrentLoc(), pos);
		} catch (Exception e) {
			Log.exception("GetPath", e);
		}
		if (path == null) {
			if (Log.loggingDebug)
				Log.debug("GetPath path is null add start point and end point ");
			path = new ArrayList<AOVector>();
			path.add(new AOVector(node.getCurrentLoc()));
			path.add(new AOVector(pos));
		}
		return path;
	}
	
	void setupGoto(Point destination, float speed) {
		if (mode != null && mode.equals(BaseBehavior.MSG_CMD_TYPE_GOTO)) {
			if (interpolatingPath)
				updateInterpolation();
		}
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		
		/*LinkedList<OID> players = AgisMobClient.GetPlayersOnline(navMeshManager.getInstanceOid());
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "NavPoints");
    	props.put("current_X", node.getCurrentLoc().getX());
        props.put("current_Y", node.getCurrentLoc().getY());
        props.put("current_Z", node.getCurrentLoc().getZ());
        props.put("destination_X", destination.getX());
        props.put("destination_Y", destination.getY());
        props.put("destination_Z", destination.getZ());
        props.put("obj", oid.toLong());
         for (OID player : players) {
        	 if (Log.loggingTrace)	Log.trace("DetourActor: setupGoto Sending Path to "+player);
        	TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props);
        	Engine.getAgent().sendBroadcast(msg);
        	}
        	*/
		
		
        // navMeshManager.ShowPolyToPlayer();
		ArrayList<AOVector> generatedPath = null;
		try {
			generatedPath = navMeshManager.GeneratePathDragonsan(node.getCurrentLoc(), destination);
		} catch (Exception e) {
			Log.debug("DetourActor: setupGoto: " + e.getMessage() + " " + e.getLocalizedMessage());
		} 
		// ArrayList<AOVector> generatedPath = navMeshManager.GeneratePath(node.getCurrentLoc(), destination);
		if (Log.loggingDebug)
			Log.trace("DetourActor: setupGoto  path: " + generatedPath);
		if (generatedPath == null) {
			if(Log.loggingDebug)Log.debug("DetourActor: setupGoto PATH: null for "+oid+ " current position "+node.getCurrentLoc()+" dest "+destination);
			AgisMobClient.sendInvalidPath(oid);
			return;
		}
    	//Map<String, Serializable> props = new HashMap<String, Serializable>();
	
		 mode = BaseBehavior.MSG_CMD_TYPE_GOTO;
		//
        setupPathInterpolator(node.getCurrentLoc(), targetLoc, speed, generatedPath);	
        //setupPathInterpolator(node.getCurrentLoc(), targetLoc, speed, generatedPath2);	
          	
		navMeshManager.setActorTarget(oid, destination);
        navMeshManager.setActorSpeed(oid, speed);
        targetLoc = new Point(destination.getX(), destination.getY(), destination.getZ());
        
        if (Log.loggingTrace)  Log.trace("DetourActor: setupGoto  Start Sending Path");
        
        
        
        
        	/*	props.put("ext_msg_subtype", "pathPoints");
    	int pos = 0;
        String pathPoints = "PATH: ";
        for (AOVector point : generatedPath) {
        	pathPoints += point.toString() + "; ";
        	props.put("path_" + pos + "X", point.getX());
        	props.put("path_" + pos + "Y", point.getY());
        	props.put("path_" + pos + "Z", point.getZ());
			pos++;
        }
        if (Log.loggingTrace) Log.trace("DetourActor: setupGoto "+pathPoints+ " count:"+generatedPath.size());
        //
        props.put("numPoints", pos);
        props.put("obj", oid.toLong());
    	*/
       // LinkedList<OID> players = AgisMobClient.GetPlayersOnline();
      /*  for (OID player : players) {
            if (Log.loggingTrace)	Log.trace("DetourActor: setupGoto Sending Path to "+player);
            TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props);
        	Engine.getAgent().sendBroadcast(msg);// 000000000001d989 121225
        }*/
        if (Log.loggingTrace)	Log.trace("DetourActor: setupGoto Sended Path");
            
	}
	
	/**
	 * Update the target for the Actor by getting the current position of the target object.
	 */
	void setupFollow(Point pos) {
	    if (Log.loggingDebug)Log.debug("DetourActor: setupFollow hit");
	    if (target==null) {
	    	Log.debug("DetourActor.setupFollow target is null");
	    	return;
	    }
	    if (target.getWorldNode()==null) {
	    	Log.debug("DetourActor.setupFollow target.getWorldNode() is null");
	    	return;
	    }

		prevTargetLoc = target.getWorldNode().getLoc();
		float len = Point.distanceTo(prevTargetLoc, pos);
		float dist = Point.distanceTo(lastTargetLoc, pos);

	if(Log.loggingDebug)Log.debug("DetourActor.setupFollow oid"+getOid()+" lastTargetLoc="+lastTargetLoc+" len="+len+" distanceToFollowAt="+distanceToFollowAt+" dist="+dist);
//		float fdist = Point.distanceTo(followLoc, lastTargetLoc);

     	if (len < AgisMobPlugin.PET_FOLLOW_RANGE * 0.95f && dist < 0.1f) {
     		navMeshManager.resetActorTarget(oid);
     		lastDir = AOVector.Zero;
     		navMeshManager.setActorSpeed(oid, 0);
			if(Log.loggingDebug)Log.debug("DetourActor.setupFollow oid"+getOid()+" STOP");
     		return;
     	}
    	InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
    	 ArrayList<AOVector> generatedPath = null;
//		 Point destPoint = node.getCurrentLoc();
    	 try {
			 try {
				 lastTargetLoc = navMeshManager.findRandomPointAroundCircle(prevTargetLoc, AgisMobPlugin.PET_FOLLOW_RANGE);
			 } catch (Exception e) {
				 Log.debug("DetourActor: find point to roam Exception "+e.getMessage()+" "+e.getLocalizedMessage());
			 }
			generatedPath = navMeshManager.GeneratePathDragonsan(node.getCurrentLoc(), lastTargetLoc);
		} catch (Exception e) {
			Log.debug("DetourActor: setupFollow: "+e.getMessage()+" "+e.getLocalizedMessage());
		}
        if (generatedPath == null) {
        	AgisMobClient.sendInvalidPath(oid);
        	return;
        }
        
        String generatedPathString = "";
        for (AOVector loc : generatedPath) {
        	generatedPathString += "\nPoint: " + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "; ";
        }
        
        Log.debug("DetourActor.setupFollow oid="+oid+" loc="+node.getCurrentLoc()+" target loc="+lastTargetLoc+" org generatedPathString="+generatedPathString);
    
        // Do fancy calculation to change the position to be distanceToFollowAt away from the targets actual position
     	// This is currently not quite getting the position correctly. The mob is going a bit too far
        AOVector lastPos = generatedPath.get(generatedPath.size()-1);
        AOVector secondLastPos = lastPos;
        if (generatedPath.size() > 1)
        	secondLastPos = generatedPath.get(generatedPath.size()-2);
        
        len = AOVector.distanceTo(lastPos, secondLastPos);
        if (len < distanceToFollowAt && generatedPath.size() > 2) {
        	generatedPath.remove(generatedPath.size()-1);
        } else if (len > distanceToFollowAt){
        	len -= (distanceToFollowAt);
        	AOVector newp2 = new AOVector(lastPos);
        	newp2.sub(secondLastPos);
        	newp2.normalize();
        	newp2.multiply(len);
        	newp2.add(secondLastPos);
        	generatedPath.set(generatedPath.size()-1, newp2);
        }
        
        String generatedPathString2 = "";
        for (AOVector loc : generatedPath) {
        	generatedPathString2 += "\nPoint: " + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "; ";
        }
        
        Log.debug("DetourActor.setupFollow oid="+oid+" loc="+node.getCurrentLoc()+" target loc="+lastTargetLoc+" generatedPathString="+generatedPathString2);
        	      
        lastPos = generatedPath.get(generatedPath.size()-1);
        
        setupPathInterpolator(pos, new Point(lastPos.getX(), lastPos.getY(), lastPos.getZ()), speed, generatedPath);
        //Log.debug("PATH: generatedPath: " + generatedPathString);
    	
		navMeshManager.setActorTarget(oid, new Point(lastPos.getX(), lastPos.getY(), lastPos.getZ()));
        navMeshManager.setActorSpeed(oid, speed);
        targetLoc = new Point(lastPos.getX(), lastPos.getY(), lastPos.getZ());
        //Log.debug("DETOUR: targetLoc: " + targetLoc + " from followLoc: " + lastTargetLoc + " and currentPos: " + pos);
        
    	
      Map<String, Serializable> props = new HashMap<String, Serializable>();
    	
    	props.put("ext_msg_subtype", "pathPoints");
    	int posid = 0;
        String pathPoints = "PATH: ";
        for (AOVector point : generatedPath) {
        	pathPoints += point.toString() + "; ";
        	props.put("p" + posid + "X", point.getX());
        	props.put("p" + posid + "Y", point.getY());
        	props.put("p" + posid + "Z", point.getZ());
        	posid++;
        }
        if (Log.loggingDebug)  Log.debug("DetourActor: setupFollow "+pathPoints+ " count:"+generatedPath.size());
        //
        props.put("numPoints", posid);
        props.put("obj", oid.toLong());
        if (Log.loggingDebug)  Log.debug("DetourActor: setupFollow Start Sending Path");
         if (Log.loggingDebug) Log.debug("DetourActor: setupFollow Sent Path");
         
	}
	
	/**
	 * Called each update of the InstanceNavMesh. Checks if the location or direction has changed much and if so
	 * sends an update to the clients.
	 * @param dir
	 * @param pos
	 */
	public void updateDirLoc(AOVector dir, Point pos) {
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		
		if (mode != null && mode.equals(BaseBehavior.MSG_CMD_TYPE_FOLLOW)) {
			if(speed==0)
				return;
			Point followLoc = node.getLoc();
			if (target!=null && target.getWorldNode()!=null) {
				followLoc = target.getWorldNode().getLoc();
			}
			Point myLoc = node.getLoc();
			float fdist = Point.distanceTo(followLoc, lastTargetLoc);
			float pdist = Point.distanceTo(followLoc, prevTargetLoc);

		    float dist = Point.distanceTo(lastTargetLoc, myLoc);
		    if (Log.loggingDebug)
				Log.debug("BaseBehavior.followUpdate: oid = " + oid + "; myLoc = " + myLoc + "; followLoc = " + followLoc + "; fdist = " + fdist + "; dist = " + dist);
		    
		    // If the new target location is more than a meter from
		    // the old one, create a new path.
			if ((dist > 0.3f || fdist > distanceToFollowAt*0.9f )&& pdist > 0.1f) {
		    	if (interpolatingPath)
		    		updateInterpolation();
		    	setupFollow(myLoc);
		    	if (moveDebug) {
					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("x", myLoc.getX());
					props.put("y", myLoc.getY());
					props.put("z", myLoc.getZ());
					props.put("mob", obj.getOid());
					props.put("dx", dir.getX());
					props.put("dy", dir.getY());
					props.put("dz", dir.getZ());
					props.put("ext_msg_subtype", "debugMobMoveTarget");
					TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
					Engine.getAgent().sendBroadcast(TEmsg);
				}
		    } else {
		    	if (!interpolatingPath)
					return;
				updateInterpolation();
		    }
		} else {
			
			if (!interpolatingPath)
				return;
			updateInterpolation();
			if (!interpolatingPath) {
	            Engine.getAgent().sendBroadcast(new ArrivedEventMessage(obj));
	            if (Log.loggingDebug)
	                Log.debug("BaseBehavior.gotoUpdate sending ArrivedEventMessage: oid = " + oid);
	            mode = BaseBehavior.MSG_CMD_TYPE_STOP;
	            WorldManagerClient.MobPathCorrectionMessage correction = new WorldManagerClient.MobPathCorrectionMessage(oid, System.currentTimeMillis(), "linear", 0, "", new LinkedList<Point>(), true);
				Engine.getAgent().sendBroadcast(correction);
				for (Behavior behav : obj.getBehaviors()) {
					if (behav instanceof CombatBehavior) {
						CombatBehavior cBehav = (CombatBehavior) behav;
						if(cBehav.evade) {
							cBehav.evade = false;
							CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET_END);
						}
						if(cBehav.goreturn) {
							cBehav.goreturn = false;
							cBehav.combatStartLoc = null;
							CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET_END);
						}
					}
				}
				
			}
		}
	
		
	}
	
	protected long setupPathInterpolator(Point myLoc, Point dest, float speed, ArrayList<AOVector> points) {
        long timeNow = System.currentTimeMillis();
        if (pathState==null)
        	 pathState = new PathState(oid, "Generic", true);
        if(Log.loggingDebug)Log.debug("setupPathInterpolator: " + obj.getOid() + " myLoc="+myLoc+" dest="+dest+" speed="+speed);
		WorldManagerClient.MobPathReqMessage reqMsg = pathState.setupDetourPathInterpolator(timeNow, myLoc, dest, speed, points);
    	if (moveDebug) {
			try {
				Log.debug("MobDebug.setupPathInterpolator set Oid="+oid+" loc="+myLoc+" dest="+dest);
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("x", reqMsg.getPathPoints().size()>1?reqMsg.getPathPoints().get(reqMsg.getPathPoints().size()-1).getX():reqMsg.getPathPoints().get(0).getX());
				props.put("y", reqMsg.getPathPoints().size()>1?reqMsg.getPathPoints().get(reqMsg.getPathPoints().size()-1).getY():reqMsg.getPathPoints().get(0).getY());
				props.put("z", reqMsg.getPathPoints().size()>1?reqMsg.getPathPoints().get(reqMsg.getPathPoints().size()-1).getZ():reqMsg.getPathPoints().get(0).getZ());
				props.put("mob", obj.getOid());
				props.put("ext_msg_subtype", "mobdstposition");
				TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
				Engine.getAgent().sendBroadcast(TEmsg);
			} catch (Exception e) {
				Log.error("MobDebug.setupPathInterpolator: "+e);
				e.printStackTrace();
			}
		}
    	
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
        }
        else {
            interpolatingPath = false;
            return 0;
        }
    }
	
	public void updateInterpolation() {
		//Log.debug("PATH: updating interpolation: " + interpolatingPath);
		if (interpolatingPath) {
			long timeNow = System.currentTimeMillis();
		    PathLocAndDir locAndDir = pathState.interpolatePath(timeNow);
		    OID oid = obj.getOid();
		    InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		     
		    if (locAndDir == null) {
		        // We have arrived - - turn off interpolation, and cancel that path
		        if (interpolatingPath) {
		        	if (Log.loggingDebug)
		                Log.debug("BaseBehavior.interpolatePath: cancelling path: oid = " + oid + "; myLoc = " + node.getLoc());
		           // cancelPathInterpolator(oid);
		            interpolatingPath = false;
		        }
		        	
		        node.setDir(new AOVector(0,0,0));
			} else {
				node.setPathInterpolatorValues(timeNow, locAndDir.getDir(), locAndDir.getLoc(), locAndDir.getOrientation());
				MobManagerPlugin.getTracker(node.getInstanceOid()).updateEntity((EntityWithWorldNode) obj);
			}
		}
	}
	
	protected void cancelPathInterpolator(OID oid) {
		Log.debug("PATH: cancelling interpolation");
		InterpolatedWorldNode node = (InterpolatedWorldNode) obj.getWorldNode();
		 Point p = node.getCurrentLoc();	
		 ArrayList<Point> l = new ArrayList<Point>();
		 l.add(p);
        WorldManagerClient.MobPathReqMessage cancelMsg = new WorldManagerClient.MobPathReqMessage(oid);//, l);
        try {
            Engine.getAgent().sendBroadcast(cancelMsg);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
	
	public CrowdAgentParams getParams() {
		return params;
	}
	
	public void addToNavMeshManager(InstanceNavMeshManager navMeshManager, CrowdAgent agent) {
		this.navMeshManager = navMeshManager;
		this.agent = agent;
	}
	
	public OID getOid() {
		return oid;
	}
	
	public CrowdAgent getAgent() {
		return agent;
	}

	public void setAgent(CrowdAgent agent) {
		this.agent = agent;
	}
	
	OID oid;
	ObjectStub obj;
	ObjectStub target;
	Point lastTargetLoc;
	Point prevTargetLoc;
	Point targetLoc;
	Point lastLoc;
	AOVector lastDir;
	Quaternion lastOrient;
	float speed;
	CrowdAgentParams params;
	InstanceNavMeshManager navMeshManager;
	private CrowdAgent agent;
	
	// The state of the pathing system for this mob
    PathState pathState = null;
    boolean interpolatingPath = false;
	
	EntityHandle followTarget;
	float distanceToFollowAt = 0;
	protected String mode = BaseBehavior.MSG_CMD_TYPE_STOP;
    protected boolean roamingBehavior = false;
	Long commandSub = null;
	Long commandSubResp =null;
	protected final Set<MessageType> mobRPCFilterTypes = new HashSet<>();
	boolean moveDebug = false;
	OID targetDebug = null;
	ScheduledFuture sf =null;
	
	 public class MoveDebugTimer implements Runnable {
	    	
			
	    	public MoveDebugTimer() {
	    	}
	    	
			@Override
			public void run() {
				Log.debug("MoveDebugTimer");
				//if (moveDebug) {
					//interpolatePath();
					Point loc = obj.getWorldNode().getLoc();
					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("x", loc.getX());
					props.put("y", loc.getY());
					props.put("z", loc.getZ());
					props.put("mob", obj.getOid());
					props.put("ext_msg_subtype", "mobposition");
					TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetDebug, targetDebug, false, props);
					Engine.getAgent().sendBroadcast(TEmsg);
			//	}
				
				
			}
	    }
	    
}