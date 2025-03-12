package atavism.agis.plugins;

import java.util.*;
import java.io.*;

import atavism.msgsys.*;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.cache.GuildCache;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisWorldManagerClient.SetMovementStateMessage;
import atavism.agis.plugins.AgisWorldManagerClient.SpawnTrapMessage;
import atavism.agis.plugins.AgisWorldManagerClient.WaterRegionTransitionMessage;
import atavism.agis.plugins.GroupClient.GroupMembersMessage;
import atavism.agis.util.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.engine.*;
import atavism.server.math.*;
import atavism.server.util.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;

/**
 * handles client traffic to the rest of the servers
 */
public class AgisWorldManagerPlugin extends WorldManagerPlugin {

    public AgisWorldManagerPlugin()
    {
        super();
        propertyExclusions.add(AgisObject.baseDCKey);
    }
    AccountDatabase aDB;
    GuildCache guildCache;
    public void onActivate() {
		super.onActivate();
		
		// Create responder subscription
	 	MessageTypeFilter filter = new MessageTypeFilter();
	    filter.addType(AgisWorldManagerClient.MSG_TYPE_LEAVE_INSTANCE);
	 	filter.addType(InstanceClient.MSG_TYPE_INSTANCE_LOADED);
	 	filter.addType(AgisMobClient.MSG_TYPE_ADD_TARGET_TO_CHECK);
	 	filter.addType(AgisMobClient.MSG_TYPE_REMOVE_TARGET_TO_CHECK);
	 //	filter.addType(AgisWorldManagerClient.MSG_TYPE_WATER_REGION_TRANSITION);
	 	filter.addType(AgisWorldManagerClient.MSG_TYPE_SET_MOVEMENT_STATE);
	 	//filter.addType(AgisMobClient.MSG_TYPE_SET_UNDERWATER);
	 	filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
	 	filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
	 	filter.addType(GroupClient.MSG_TYPE_GROUP_MEMBERS_UPDATE);
	 	Engine.getAgent().createSubscription(filter, this);
		
	 	mobFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_WATER_REGION_TRANSITION);
	 	mobFilterTypes.add(AgisMobClient.MSG_TYPE_PLAY_COORD_EFFECT);
	 	mobFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_SPAWN_TRAP);
//	 //	mobFilter.addType(AgisWorldManagerClient.MSG_TYPE_CHECK_IF_TARGETS_IN_AREA);
//		 
//	 	Engine.getAgent().removeSubscription(mobSubId);
//		mobSubId = Engine.getAgent().createSubscription(mobFilter, this);

		
		MessageTypeFilter filter2 = new MessageTypeFilter();
	 	filter2.addType(CombatClient.MSG_TYPE_GET_AOE_TARGETS);
	 	filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(ObjectManagerClient.MSG_TYPE_FIX_WNODE_REQ);
	 	//filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
	 	Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		
//	 	Engine.getAgent().removeSubscription(mobRPCSubId);
//        mobRPCFilter.addType(AgisWorldManagerClient.MSG_TYPE_CHECK_IF_TARGETS_IN_AREA);
	 	mobRPCFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_CHECK_IF_TARGETS_IN_AREA);
//	 	mobRPCSubId = Engine.getAgent().createSubscription(mobRPCFilter, this, MessageAgent.RESPONDER);
	 	
	 	CombatDatabase cbDB = new CombatDatabase(false);
		if(!Engine.isAIO())
			cbDB.LoadStats();
	 	//cbDB.close();
	 	ContentDatabase cDB = new ContentDatabase(false);
        String worldTimeSpeed = cDB.loadGameSetting("WORLD_TIME_SPEED");
        if (worldTimeSpeed != null)
        	WORLD_TIME_SPEED = Float.parseFloat(worldTimeSpeed);
        String worldTimeZone = cDB.loadGameSetting("WORLD_TIME_ZONE");
        if (worldTimeZone != null)
        	WORLD_TIME_ZONE = worldTimeZone;
	
	 /*	serverStartTime = System.currentTimeMillis();
	 	Calendar cal = Calendar.getInstance();
	 	serverSeconds = (cal.get(Calendar.HOUR_OF_DAY) * 3600) + (cal.get(Calendar.MINUTE) * 60) + cal.get(Calendar.SECOND);
	 	serverSeconds *= WORLD_TIME_SPEED;
	 	serverSeconds = serverSeconds % 86400;
	 	serverHour = serverSeconds / 3600;
	 	serverSeconds -= serverHour * 3600;
	 	serverMinute = serverSeconds / 60;
	 	serverSeconds -= serverMinute * 60;*/
	 	aDB = new AccountDatabase(true);
	 	guildCache = new GuildCache(aDB);
        
	    HashMap<String, Integer> time =  aDB.LoadWorldTime();
        if (time.containsKey("year"))
       	 serverYear = time.get("year");
        if (time.containsKey("month"))
       	 serverMonth = time.get("month");
        if (time.containsKey("day"))
       	 serverDay = time.get("day");
        if (time.containsKey("hour"))
       	 serverHour = time.get("hour");
        if (time.containsKey("minute"))
       	 serverMinute = time.get("minute");
        if (time.containsKey("second"))
       	 serverSeconds = time.get("second");
	 	
        LinkedList<String> props = new LinkedList<String>();
	 	if(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED != null) {
	 		props.add(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
	 	}
	 	if(CombatPlugin.STEALTH_STAT != null) {
	 		props.add(CombatPlugin.STEALTH_STAT);
	 	}
	 	if(CombatPlugin.PERCEPTION_STEALTH_STAT != null) {
	 		props.add(CombatPlugin.PERCEPTION_STEALTH_STAT);
	 	}
	 	CombatClient.SendSetWmgrStats(props);
	 	// Run WorldTimeTick every minute of "world" time
	 	//WorldTimeTick timeTick = new WorldTimeTick();
	 //	float updateSpeed = (60f / (float)WORLD_TIME_SPEED) * 1000f;
	 	//Engine.getExecutor().scheduleAtFixedRate(timeTick, 30 * 1000, (int)updateSpeed, TimeUnit.MILLISECONDS);
	}

    protected void registerHooks() {
        super.registerHooks();
    	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SPAWN_TRAP, new SpawnTrapHook());
    	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_LEAVE_INSTANCE, new LeaveInstanceHook());
    	getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_LOADED, new LoadInstanceObjectsHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SETWNODE_REQ, new SetWNodeReqHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_UPDATEWNODE_REQ, new UpdateWNodeReqHook());
		getHookManager().addHook(EnginePlugin.MSG_TYPE_SET_PROPERTY, new NoMovePropertyHook());
		getHookManager().addHook(EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK, new NoMovePropertyHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_REPARENT_WNODE_REQ, new ReparentWNodeReqHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_GET_AOE_TARGETS, new GetTargetsInAreaHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_WATER_REGION_TRANSITION, new WaterRegionTransitionHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SET_MOVEMENT_STATE, new SetMovementStateHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_CHECK_IF_TARGETS_IN_AREA, new CheckIfTargetsInAreaHook());
        /*getHookManager().addHook(AgisMobClient.MSG_TYPE_SET_UNDERWATER,  new SetUnderwaterHook());*/
		getHookManager().addHook(AgisMobClient.MSG_TYPE_PLAY_COORD_EFFECT, new PlayCoordinatedEffectHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_MEMBERS_UPDATE, new GroupMembersUpdateHook());

		getHookManager().addHook(ObjectManagerClient.MSG_TYPE_FIX_WNODE_REQ, new FixWorldNodeHook());
    }
    
    
    protected Set<OID> getGuildMembers(int guildId) {
		return guildCache.getGuildMambersOid(guildId);
		
	}
    
    /**
   	 * The hook for when players click leave. 
   	 *
   	 */
	class GroupMembersUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GroupMembersMessage Msg = (GroupMembersMessage) msg;
			OID groupId  = Msg.getGroupOid();
			Set<OID> members = new HashSet<>(Msg.getMembers());
			if (Log.loggingDebug)
					log.debug("GroupMembersUpdateHook: groupId="+groupId+" members="+members);
			_groupList.put(groupId, members);
		
			if (Log.loggingDebug)
					log.debug("GroupMembersUpdateHook: "+_groupList);		
			for (OID oid : members) {
				AOObject obj = (AOObject) getWorldManagerEntity(oid);
				if (obj != null) {
					WMWorldNode curWnode = (WMWorldNode) obj.worldNode();
					if (curWnode.getWorldSpace() != null) {
						curWnode.getWorldSpace().updatePerceiver(curWnode);
					} else
						log.debug("NoMovePropertyHook: node not spawned");
				}
			}
			if (Log.loggingDebug)
				log.debug("GroupMembersUpdateHook: groupId="+groupId+" END");
			return true;
		}
	}
       
    
    /**
   	 * The hook for when players click leave. 
   	 *
   	 */
	class SpawnTrapHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SpawnTrapMessage Msg = (SpawnTrapMessage) msg;
			OID playerOid = Msg.getSubject();
			if (Log.loggingDebug)
				log.debug("INSTANCE: SpawnTrapHook : " + playerOid+" msg="+msg);

			BasicWorldNode wnode = WorldManagerClient.getWorldNode(playerOid);
			AOVector loc = new AOVector(wnode.getLoc());
			if(Msg.getLoc()!=null) {
				loc = new AOVector(Msg.getLoc());
			}
			int id = new Random().nextInt(1000); 
			if (Log.loggingDebug)
				log.debug("INSTANCE: SpawnTrapHook ganerated id="+id);
			VolumetricRegion obj = new VolumetricRegion(id,loc, wnode.getInstanceOid());	
			obj.setName(playerOid+"_Tr");
			obj.setRegionType("Trap");
			obj.setActionID(Msg.getAbilityId());
			obj.setActionData1(playerOid.toString());
			obj.setActionData2(Msg.getTargetType()+"");
			obj.setActionData3(Msg.getModel());
			obj.setActivationTime(Msg.getActivationTime());
			obj.addShape("sphere", loc, new AOVector(), new Quaternion(), Msg.getSize(), 0f, 0f);
			obj.setTime(Msg.getTime());
			obj.spawn();
			
			if (Log.loggingDebug)
				log.debug("INSTANCE: SpawnTrapHook finished for: " + playerOid);
			return true;
		}
	}
       
    
    
    
    class PlayCoordinatedEffectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage gridMsg = (ExtensionMessage)msg;
        	OID playerOid = gridMsg.getSubject();
        	String coordEffect = (String)gridMsg.getProperty("coordEffect");
        	OID targetOid = null;
        	boolean hasTarget = (Boolean)gridMsg.getProperty("hasTarget");
        	if (hasTarget) {
        		long targetOID = (Long)gridMsg.getProperty("targetOid");
        		targetOid = OID.fromLong(targetOID);
        	}
        	// Send down coord effect
        	CoordinatedEffect effect = new CoordinatedEffect(coordEffect);
            effect.sendSourceOid(true);
            
            for(String key : gridMsg.keySet()) {
            	if(!key.equals("coordEffect") && !key.equals("hasTarget") && !key.equals("targetOid")) {
            		effect.putArgument(key, gridMsg.getProperty(key));
            	}
            }
            
            if (hasTarget) {
            	effect.sendTargetOid(true);
            	effect.invoke(playerOid, targetOid);
            } else {
            	effect.invoke(playerOid, playerOid);
            }
            return true;
        }
	}
    /**
   	 * The hook for when players click leave. 
   	 *
   	 */
	class LeaveInstanceHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage leaveMsg = (ExtensionMessage) msg;
			OID playerOid = leaveMsg.getSubject();
			if (Log.loggingTrace)
				log.trace("INSTANCE: click leave : " + playerOid);
			AgisWorldManagerClient.returnToLastInstance(playerOid);
			if (Log.loggingTrace)
				log.trace("INSTANCE: leave click finished for: " + playerOid);
			return true;
		}
	}
       
 

    /**
     * Override this method to change what kind of object is created
     * for the sub object hook.
     * @return AOObject representing the generated sub-object.
     */
	protected AOObject generateWorldManagerSubObject(Template template, OID masterOid) {
		log.debug("generateWorldManagerSubObject: masterOid="+masterOid+" template="+template);
		// get the object type
		ObjectType objType = (ObjectType) template.get(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE);
		AOObject obj = null;

		// generate the subobject
		if (Log.loggingDebug) {
			Log.debug("AgisWorldManagerPlugin: generateWorldManagerSubObject: objectType=" + objType + ", template=" + template);
		}
		if (objType == null) {
			Log.warn("AgisWorldManagerPlugin: generateSubObject: no object type, using structure");
			obj = new AgisObject(masterOid);
			obj.setType(ObjectTypes.structure);
		} else if (objType == ObjectTypes.mob || objType == ObjectTypes.player) {
			obj = new AgisMob(masterOid);
			obj.setType(objType);
		} else if (objType == ObjectTypes.structure) {
			obj = new AgisObject(masterOid);
			obj.setType(ObjectTypes.structure);
		} else if (objType == ObjectTypes.light) {
			Light l = new Light(masterOid);
			LightData ld = (LightData) template.get(Namespace.WORLD_MANAGER, Light.LightDataPropertyKey);
			l.setLightData(ld);
			obj = l;
		} else {
			obj = new AgisObject(masterOid);
			obj.setType(objType);
		}

		Map<String, Serializable> props = template.getSubMap(Namespace.WORLD_MANAGER);
		if (props == null) {
			Log.warn("AgisWorldManagerPlugin.generateSubObject: no props in ns " + Namespace.WORLD_MANAGER);
			return null;
		}
		Log.debug("AgisWorldManagerPlugin.generateSubObject: props="+props.size()+" in ns " + Namespace.WORLD_MANAGER);
		Log.debug("AgisWorldManagerPlugin.generateSubObject: props="+props.keySet()+" in ns " + Namespace.WORLD_MANAGER);
		
		// copy properties from template to object
		for (Map.Entry<String, Serializable> entry : props.entrySet()) {
			String key = entry.getKey();
			Serializable value = entry.getValue();
			if (!key.startsWith(":")) {
				obj.setProperty(key, value);
			}
		}
		
		HashMap<String, Integer> statOverrides = null;
		if (props.containsKey(":statOverrides"))
			statOverrides = (HashMap) props.get(":statOverrides");
		Log.debug("generateWorldManagerSubObject statOverrides=" + statOverrides);
		if (statOverrides != null)
			for (String stat : statOverrides.keySet()) {
				obj.setProperty(stat, statOverrides.get(stat));
			}
	
		if (obj.isUser() || obj.isMob() || obj.isStructure()) {
			AgisObject agisObj = (AgisObject) obj;

			// set the base display context for the object
			DisplayContext dc = (DisplayContext) props.get(WorldManagerClient.TEMPL_DISPLAY_CONTEXT);
			Log.debug("AgisWorldManagerPlugin.generateSubObject: DisplayContext:"+dc);
			if (dc == null) {
				if (objType != ObjectTypes.terrainDecal)
					Log.warn("AgisWorldManagerPlugin.generateSubObject: obj has no display context, oid=" + masterOid);
			} else {
				dc = (DisplayContext) dc.clone();
				dc.setObjRef(agisObj.getOid());
				agisObj.baseDC(dc);
				agisObj.displayContext(dc);
			}
		}
		log.debug("generateWorldManagerSubObject: END masterOid="+masterOid+" obj="+obj);
		
		return obj;
	}

    /**
     * Loads in the NavMesh and other world information. Sends out the SpawnInstanceObjects message when finished.
     */
    class LoadInstanceObjectsHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		SubjectMessage message = (SubjectMessage) msg;
            OID instanceOid = message.getSubject();
    	    
    	    int instanceID = (Integer)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
    	    
    	    // Load in interactive objects
    	    ContentDatabase cDB = new ContentDatabase(false);
    	    regions.put(instanceOid, cDB.loadRegions(instanceID, instanceOid));
    	    
    	    // Load in global water height
    	    
    	    return true;
    	}
    }
    
	class SetWNodeReqHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SetWorldNodeReqMessage setNodeMsg = (WorldManagerClient.SetWorldNodeReqMessage) msg;

			BasicWorldNode wnode = (BasicWorldNode) setNodeMsg.getWorldNode();
			OID oid = setNodeMsg.getSubject();
			Entity entity = getWorldManagerEntity(oid);

			boolean rv = false;

			do {
				if (entity == null) {
					log.warn("SetWNodeReqHook: cannot find entity oid=" + oid);
					break;
				}

				if (entity instanceof AOObject) {
					AOObject obj = (AOObject) entity;
					if (obj.worldNode().isSpawned()) {
						log.warn("SetWNodeReqHook: cannot set worldnode, object currently spawned oid=" + oid);
						break;
					}

					// If the new world node does not have orientation then
					// keep the existing orientation.
					Quaternion currentOrient = null;
					if (obj.worldNode() != null)
						currentOrient = obj.worldNode().getOrientation();
					WMWorldNode newWnode = new WMWorldNode(wnode);
					if (newWnode.getOrientation() == null)
						newWnode.setOrientation(currentOrient);
					newWnode.setPerceptionRadius(((WMWorldNode) obj.worldNode()).getPerceptionRadius());
					if (Log.loggingDebug)
						log.debug("SetWNodeReqHook: obj=" + obj + ", newWnode=" + newWnode + ", perceiver=" + obj.perceiver());

					obj.worldNode(newWnode);
					newWnode.setObject(obj);
					if ((setNodeMsg.getFlags() & WorldManagerClient.SAVE_NOW) != 0)
						Engine.getPersistenceManager().persistEntity(obj);
					else
						Engine.getPersistenceManager().setDirty(obj);

					if (Log.loggingDebug)
						log.debug("SetWNodeReqHook: done oid=" + oid + ", wnode=" + obj.worldNode());

					rv = true;
				} else {
					log.debug("SetWNodeReqHook: not aoobject oid=" + oid);
				}
				break;
			} while (false);

			Engine.getAgent().sendBooleanResponse(msg, rv);

			return true;
        }
    }

    /**
     * Handles the UpdateWorldNodeReq Message. Called when a mob/player is requesting to 
     * update its world node (it moved or is moving now)
     */
    class UpdateWNodeReqHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.UpdateWorldNodeReqMessage updateMsg = (WorldManagerClient.UpdateWorldNodeReqMessage) msg;
			BasicWorldNode wnode = updateMsg.getWorldNode();
			OID masterOid = updateMsg.getSubject();
			Entity entity = getWorldManagerEntity(masterOid);
			Boolean updateOverride = updateMsg.getOverride();
		//	log.dumpStack("UpdateWNodeReqHook");
			if(Log.loggingDebug)log.debug("UpdateWNodeReqHook "+wnode.cid+" "+Thread.currentThread().getName()+" wnode "+wnode+" updateOverride="+updateOverride);
			if (entity == null) {
				log.error("UpdateWNodeReqHook: could not find entity, masterOid=" + masterOid);
				return false;
			}
			// log.debug("UpdateWNodeReqHook: entity=" + entity);

			if (!(entity instanceof AOObject)) {
				log.error("UpdateWNodeReqHook: entity is not an obj: " + entity);
				return false;
			}

	        // FIXME/TODO: check if already spawned
            // ((AOObject)entity).getWorldNode().isSpawned();

            // loc from locatable
            // orient from wnode

            // get the object's current world node
            AOObject obj = (AOObject) entity;
            InterpolatedWorldNode curWnode = (InterpolatedWorldNode) obj.worldNode();
			if(Log.loggingDebug)log.debug("UpdateWNodeReqHook masterOid="+masterOid+" cid="+wnode.cid+"  curWnode="+curWnode);
            // check for restrictions
            boolean nomove = obj.getBooleanProperty(WorldManagerClient.WORLD_PROP_NOMOVE);
            boolean noturn = obj.getBooleanProperty(WorldManagerClient.WORLD_PROP_NOTURN);
          //  long start = System.nanoTime();
			AgisStatDef asd = CombatPlugin.lookupStatDef(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
            float movement_speed = 0F;
            try {
				movement_speed =    obj.getIntProperty(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
				if(asd.getPrecision()>1)
				movement_speed = movement_speed/asd.getPrecision();
			} catch (Exception e) {
				try {
					movement_speed = CombatClient.getPlayerStatValue(masterOid, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED) ;
				} catch (Exception e1) {
					log.error("AWMP Cant get movement stat value");
					movement_speed = 7;
				}
			}
           // log.error("UpdateWNodeReqHook: wmgr movement_speed="+movement_speed+ " " + masterOid + " nanoseconds: " + (System.nanoTime() - start));
           // start = System.nanoTime();
          //  movement_speed = CombatClient.getPlayerStatValue(masterOid, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED) ;
           // log.error("UpdateWNodeReqHook: combat movement_speed="+movement_speed+ " " + masterOid + " nanoseconds: " + (System.nanoTime() - start));
                
			boolean sendCorrection = false;
			long time = System.currentTimeMillis();
			long lastInterp = curWnode.getLastUpdate();
			long timeDelta = time - lastInterp;
			float deltaTimeS = timeDelta / 1000f;
			long ctime = wnode.time;
			long lastctime = curWnode.getClientLastUpdate();
			long ctimeDelta = ctime - lastctime;
			long PrevServerClinetTimeDelta = lastInterp - lastctime;
			long CurServerClinetTimeDelta = time - ctime;

			float deltat = (timeDelta + (Math.abs(timeDelta - ctimeDelta) / 2f)) / 1000f;
			int mid = wnode.mid;
			long cid = wnode.cid;
            // Get the current location before setting the direction.
            // This will complete the interpolation based on the old direction.
        	Point oldLoc = curWnode.getLoc();
			Point oldRawLoc = curWnode.getRawLoc();
			AOVector oldDir = curWnode.getDir();
			
			Point newLoc = wnode.getLoc();
			AOVector newDir = wnode.getDir();
			if(newLoc==null){
				if(Log.loggingWarn)log.warn("UpdateWNodeReqHook: cid="+cid+" masterOid="+masterOid+" new current position sent is null");
				return true;
			}
			/*Serializable uo = obj.getProperty("updateOverride");
			long lastUpdateOverride = 0L;
			if(uo!=null)
				lastUpdateOverride = (long)uo;
			
			if(lastUpdateOverride!=0) {
				if(lastUpdateOverride +100 > System.currentTimeMillis()) {
					
				}
			}*/
			if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" MovementSpeedUpdate="+obj.MovementSpeedUpdate+" oldLoc="+oldLoc+" oldRawLoc="+oldRawLoc+" oldDir="+oldDir+" newLoc="+newLoc+" newDir="+newDir+
					" time="+time+" lastInterp="+lastInterp+" timeDelta="+timeDelta+" lastctime="+lastctime+" ctimeDelta="+ctimeDelta);
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd kk:mm:ss.SSS");    
			Date resultdate = new Date(System.currentTimeMillis());
			//System.out.println(sdf.format(resultdate));
			if (!updateOverride &&/* Point.distanceTo(oldRawLoc, oldLoc) == 0 && */ oldDir.length() > 0 && ctimeDelta > 0) {
				if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" MovementSpeedUpdate="+obj.MovementSpeedUpdate+" "+lastInterp);
				if (obj.MovementSpeedUpdate > 0 && obj.MovementSpeedUpdate > lastInterp && oldDir.length()!=newDir.length()) {
					// Calculate position with old speed
					long dt = obj.MovementSpeedUpdate - lastInterp;
					float daltatime1 = dt / 1000f;
					if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" dt="+dt+" oldDir="+oldDir.length());
					AOVector oldDir2 = new AOVector(oldDir);
					AOVector _dist = oldDir2.multiply(daltatime1);
					Point p = new Point(oldRawLoc);
					p.add(_dist.toPoint());
					if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" dt="+dt+" oldDir="+_dist.length()+" p="+p);
					// calculate with new speed
					long dt2 = timeDelta - dt;
					float daltatime2 = dt2 / 1000f;
					AOVector oldDir3 = new AOVector(oldDir);
					oldDir3.normalize();
					if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" dt2="+dt2+" dir="+oldDir3.length());
					oldDir3.multiply(daltatime2 * movement_speed);
					p.add(oldDir3.toPoint());
					if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" dir="+oldDir3.length()+" p="+p);
					oldLoc = p;
				} else {
					if (Log.loggingDebug)Log.debug("UpdateWNodeReqHook cid="+cid+" obj.MovementSpeedUpdate < lastInterp");
					float daltaCtimeS = ctimeDelta / 1000f;
					AOVector oldDir3 = new AOVector(oldDir);
					AOVector _dist = oldDir3.multiply(daltaCtimeS);
						Point p = new Point(oldRawLoc);
					// log.error("UpdateWNodeReqHook: cid=" + cid + " oldDist = 0 Dir > 0 ctimeDelta
					// > 0");
					p.add(_dist.toPoint());
					if (Log.loggingDebug) log.debug("UpdateWNodeReqHook: cid=" + cid + " daltaCtimeS="+daltaCtimeS+" _dist="+_dist+" p="+p);
					oldLoc = p;
				}
			}
		

			if (Log.loggingDebug)
				Log.debug("UpdateWNodeReqHook: oldLoc=" + oldLoc + " oldRawLoc=" + oldRawLoc + " oldDir=" + oldDir + " nomove=" + nomove + " noturn=" + noturn + " movement_speed=" + movement_speed + " newLoc=" + newLoc
						+ " newDir=" + newDir);

			BasicWorldNode newNode = new BasicWorldNode(curWnode);

			// update the object's current world node
			Quaternion orient = wnode.getOrientation();

			if (orient != null) {
				if (Log.loggingDebug) {
					Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", cid=" + cid + " Quaternion , orient " + orient + ", curWnode.getOrientation() " + curWnode.getOrientation() + " orient.Yaw=" + orient.getYaw() + " curWnode.Yaw=" + curWnode.getOrientation().getYaw()+" updateOverride="+updateOverride+" noturn="+noturn);
				}
				if (!updateOverride && noturn) {
					if (!curWnode.getOrientation().equals(orient)) {
						orient = curWnode.getOrientation();
						newNode.setOrientation(orient);
						sendCorrection = true;
					}
				} else {
					if (updateOverride) {
						newNode.setOrientation(orient);
						sendCorrection = true;
					}
					curWnode.setOrientation(orient);
				}
				if (Log.loggingDebug) {
					Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", cid=" + cid + " Quaternion mod , orient " + orient + ", curWnode.getOrientation() " + curWnode.getOrientation() + " orient.Yaw=" + orient.getYaw() + " curWnode.Yaw=" + curWnode.getOrientation().getYaw());
				}
			}
			if (Log.loggingDebug) {
				Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", cid=" + cid + " Quaternion after mod , orient " + orient + ", curWnode.getOrientation() " + curWnode.getOrientation() + " orient.Yaw=" + (orient!=null?orient.getYaw():"nd") + " curWnode.Yaw=" + curWnode.getOrientation().getYaw());
			}
			AOVector dir = wnode.getDir();
			if (dir != null) {
				if (nomove) {
					if (!dir.isZero()) {
						dir = new AOVector(0, 0, 0);
						newNode.setDir(dir);
						sendCorrection = true;
						//System.out.println(sdf.format(resultdate)+" send correction position 1 no move dir not zero "+obj.getOid()+":"+obj.getName()+":"+cid);  
					}
				}
				AOVector dirNorm = dir.cloneAOVector().normalize();
				dirNorm.multiply(movement_speed);
				if (Log.loggingDebug) {
					Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", dirNorm.length()= " + dirNorm.length() + ", dir.length()=" + dir.length());
				}
				// Correction position movement speed too high
				if (dirNorm.length() + 0.1f < dir.length()) {
					dir = dirNorm;
					sendCorrection = true;
					Log.debug("UpdateWNodeReqHook: sendCorrection Vector");
					//System.out.println(sdf.format(resultdate)+" send correction position 1 correction dir over speed "+obj.getOid()+":"+obj.getName()+":"+cid);  
				}
				curWnode.setDir(dir);
			}
			if (Log.loggingDebug) {
				Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", oldLoc " + oldLoc + ", newLoc " + newLoc + ", override " + updateOverride + " dir=" + dir + " sendCorrection=" + sendCorrection);
			}
			if (dir != null && dir.isZero() && !updateOverride) {
				if (Log.loggingDebug) {
					Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + " dir is 0 set new loc as null");
				}
				float d = Point.distanceTo(oldLoc, newLoc);
				if (d < 0.05f)
					newLoc = null;
			}
			
			if (Log.loggingDebug) {
				Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", oldLoc " + oldLoc + ", newLoc " + newLoc + ", override " + updateOverride + " dir=" + dir + " sendCorrection=" + sendCorrection);
			}
			if (newLoc != null) {
				if (Log.loggingDebug && dir != null) {
					Log.debug("UpdateWNodeReqHook: cid="+cid+" mid=" + mid + "; mOid=" + masterOid + "; oLoc=" + oldLoc + "; nLoc=" + newLoc + "; ov=" + updateOverride + "; dist=" + Point.distanceTo(oldLoc, newLoc) + "; maxDist="
							+ (movement_speed * deltaTimeS) + "; maxDist2=" + (movement_speed * deltaTimeS * 1.25f) + "; dt=" + timeDelta + "; t=" + time + "; ct=" + ctime + "; cdt=" + ctimeDelta + "; PSCTD="
							+ PrevServerClinetTimeDelta + "; CSCTD=" + CurServerClinetTimeDelta + ";deltat=" + deltat + "; mD3=" + (movement_speed * deltat));
				}
				AOVector dirCopy = null;
				if (dir != null)
					dirCopy = new AOVector(dir);

				// dirCopy.scale((float) (timeDelta / 1000.0));
				if (Log.loggingDebug && dir != null) {
					Log.debug("UpdateWNodeReqHook: masterOid " + masterOid + ", oldLoc " + oldLoc + ", newLoc " + newLoc + ", override " + updateOverride + " Point.distanceTo(oldLoc, newLoc)="
							+ Point.distanceTo(oldLoc, newLoc) + " maxDistance" + (movement_speed * deltaTimeS) + " dirCopy=" + dirCopy + " dirCopy.length=" + dirCopy.length() + " timeDelta=" + timeDelta);
				}
				if (!updateOverride && nomove) {
					if (Point.distanceToXZ(oldLoc, newLoc) > 0.001f) {
						if (Log.loggingDebug)log.debug("UpdateWNodeReqHook: sendCorrection "+obj.getOid()+":"+obj.getName()+" oldLoc=" + oldLoc + " newLoc=" + newLoc + "no move D=" +String.format("%.04f", Point.distanceTo(oldLoc, newLoc)));
						newLoc = oldLoc;
						newNode.setLoc(newLoc);
						sendCorrection = true;
						// System.out.println(" send correction position 1 no move");
						
						//System.out.println(sdf.format(resultdate)+" send correction position 1 no move "+obj.getOid()+":"+obj.getName()+":"+cid+" correction to "+newLoc);
					}
				}
        		//FIXME Na czas testow wylaczono korekcje pozycji zewzgledu na dysttans przeskoku
        		else if (cid > 0 && !updateOverride && !Points.isClose(oldLoc, newLoc, World.getLocTolerance()) && Point.distanceTo(oldRawLoc, oldLoc)<Point.distanceTo(oldRawLoc, newLoc)) {
                	// 05/23/07 Stryker Correct with the last location we got from the client, 
                	// rather than the last interpolated location, since the last interpolated 
                	// location may end up transporting the player inside a collision volume.
        			if (Log.loggingDebug)	
        				Log.debug("UpdateWNodeReqHook: correction position 2 cid="+cid+" mid=" + mid + "; mOid=" + masterOid + "; oLoc=" + oldLoc + "; nLoc=" + newLoc + "; ov=" + updateOverride + "; dist=" + Point.distanceTo(oldLoc, newLoc) + "; maxDist="
							+ (movement_speed * deltaTimeS) + "; maxDist2=" + (movement_speed * deltaTimeS * 1.25f) + "; dt=" + timeDelta + "; t=" + time + "; ct=" + ctime + "; cdt=" + ctimeDelta + "; PSCTD="
							+ PrevServerClinetTimeDelta + "; CSCTD=" + CurServerClinetTimeDelta + ";deltat=" + deltat + "; mD3=" + (movement_speed * deltat));
        			if (Log.loggingDebug)
        			Log.debug("UpdateWNodeReqHook: oldLoc=" + oldLoc + " oldRawLoc=" + oldRawLoc + " oldDir=" + oldDir +" oldDir.length="+oldDir.length()+ " olddist="+ Point.distanceTo(oldRawLoc, oldLoc)+
        					" nomove=" + nomove + " noturn=" + noturn + " movement_speed=" + movement_speed + " newLoc=" + newLoc
        						+ " newDir=" + newDir+" newDir.length="+newDir.length()+" dist="+ Point.distanceTo(oldRawLoc, newLoc));
        			if (Log.loggingDebug)log.debug("UpdateWNodeReqHook: sendCorrection oldLoc=" + oldLoc + " newLoc=" + newLoc + " MD=" + World.getLocTolerance() + " D=" + Point.distanceTo(oldLoc, newLoc));
				//	newLoc = curWnode.getRawLoc();
					System.out.println(sdf.format(resultdate)+" send correction position 2 distance "+obj.getOid()+":"+obj.getName()+":"+cid+" correction from "+newLoc+" to "+oldLoc);
					newNode.setLoc(oldLoc);
					newNode.setOrientation(orient);
					sendCorrection = true;
					newLoc = oldLoc;
				} /*else if (!updateOverride && dir != null && Point.distanceToXZ(oldLoc, newLoc) > dirCopy.length()) {
					//if (Log.loggingDebug) {
					//	Log.debug("UpdateWNodeReqHook.ZBDS mOid=" + masterOid + "; dist=" + Point.distanceToXZ(oldLoc, newLoc) + "; maxDist=" + (movement_speed * deltaTimeS) + "; maxDist2="
					//			+ (movement_speed * deltaTimeS * 1.4f) + "; r=" + ((movement_speed * deltaTimeS * 1.4f) - Point.distanceToXZ(oldLoc, newLoc)) + "; timeDelta=" + timeDelta + ";"
					//			+ (movement_speed * deltat * 1.4f));
					newLoc = curWnode.getRawLoc();
					newLoc.add((int) dirCopy.getX(), (int) dirCopy.getY(), (int) dirCopy.getZ());
					newNode.setLoc(newLoc);
					sendCorrection = true;
					System.out.println(" send correction position 3 "+obj.getOid());
				} */
				else if (cid > 0 && !obj.getType().equals(ObjectTypes.mob) && !updateOverride && Point.distanceToXZ(oldLoc, newLoc) > (movement_speed * deltat * 2.5f) && timeDelta > 150/* dirCopy.length() */) {
					if (Log.loggingDebug) {
						Log.debug("UpdateWNodeReqHook.ZBDS mOid=" + masterOid + "; correction position 4 Speed Type ="+obj.getType()+"  dist2D=" + Point.distanceToXZ(oldLoc, newLoc) + "; dist3D=" + Point.distanceTo(oldLoc, newLoc) +"; maxDist=" + (movement_speed * deltaTimeS) + "; maxDist2="
								+ (movement_speed * deltaTimeS * 2.5f) + "; r=" + ((movement_speed * deltaTimeS * 2.5f) - Point.distanceToXZ(oldLoc, newLoc)) + "; timeDelta=" + timeDelta + ";"
								+ (movement_speed * deltat * 2.5f)+" |2D= "+(Point.distanceToXZ(oldLoc, newLoc)/(movement_speed * deltaTimeS))+"  |3D "+(Point.distanceTo(oldLoc, newLoc)/(movement_speed * deltaTimeS)));

					/*	if (sendDebugCorrectionPoints) {
							Map<String, Serializable> props = new HashMap<String, Serializable>();
							props.put("ext_msg_subtype", "AWMPPoints");
							props.put("nX", newLoc.getX());
							props.put("nY", newLoc.getY());
							props.put("nZ", newLoc.getZ());
							props.put("cX", oldLoc.getX());
							props.put("cY", oldLoc.getY());
							props.put("cZ", oldLoc.getZ());
							TargetedExtensionMessage temsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, masterOid, masterOid, false, props);
							Engine.getAgent().sendBroadcast(temsg);
						}*/
					}
			/*		HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
					logData.put("oldLoc", oldLoc);
					logData.put("newLoc", newLoc);
					logData.put("dist", Point.distanceTo(oldLoc, newLoc));
					logData.put("maxDist", (movement_speed * deltaTimeS));
					logData.put("maxDistWithMarg", (movement_speed * deltaTimeS * 1.25f));
					logData.put("timeDeltaMs", timeDelta);
					DataLoggerClient.logData("SPEEDHACK", masterOid, null, null, logData);*/
					AOVector dirNorm = dir.cloneAOVector().normalize();
					dirNorm.multiply(movement_speed);
					dirNorm.multiply(deltaTimeS);
					newLoc = curWnode.getRawLoc();
					newLoc.add(dirNorm.getX(), dirNorm.getY(), dirNorm.getZ());
					// newLoc = oldLoc;
					newNode.setLoc(newLoc);
					sendCorrection = true;
					System.out.println(sdf.format(resultdate)+" send correction position 4 Speed "+obj.getOid()+":"+obj.getName()+":"+cid+" correction to "+newLoc);  	
        			
                    Log.debug("UpdateWNodeReqHook: sendCorrection distance ");
                } else {
                	if (updateOverride) {
                		log.debug("UpdateWNodeReqHook: sendCorrection oldLoc="+oldLoc+" newLoc="+newLoc+" updateOverride D="+Point.distanceTo(oldLoc, newLoc));
                        newNode.setLoc(newLoc);
                        dir = new AOVector(0, 0, 0);
						newNode.setDir(dir);
						curWnode.setDir(dir);
                        sendCorrection = true;
                    	System.out.println(sdf.format(resultdate)+" send correction position 5 Teleport "+obj.getOid()+":"+obj.getName()+":"+cid+" correction from "+oldLoc+" to "+newLoc+" instance="+wnode.getInstanceOid()+" "+curWnode.getInstanceOid());
                    	obj.setProperty("updateOverride", System.currentTimeMillis());
                           }
                }
            }

            if (Log.loggingDebug) {
                log.debug("UpdateWNodeReqHook: set world node, entity=" + entity + ", new wnode=" + curWnode);
            }
          	curWnode.setClientLastUpdate(ctime);
            
            if (sendCorrection) {
                if (Log.loggingDebug) {
                    log.debug("UpdateWNodeReqHook: sending world node correction " + newNode);
                }
                WorldManagerClient.correctWorldNode(masterOid, newNode);
            }

            if (Log.loggingDebug) {
                log.debug("UpdateWNodeReqHook: make message updateMsg.getPreMessage():"+updateMsg.getPreMessage());
            }

	if (updateMsg.getPreMessage() != null) {
		Engine.getAgent().sendBroadcast(updateMsg.getPreMessage());
	}
	if (newLoc != null && !newLoc.equals(oldLoc)) {
		curWnode.setLoc(newLoc);
	}
	if (Log.loggingDebug) {
		log.debug("UpdateWNodeReqHook: make message updateMsg.getPostMessage():" + updateMsg.getPostMessage());
	}

	if (updateMsg.getPostMessage() != null) {
		Engine.getAgent().sendBroadcast(updateMsg.getPostMessage());
	}
	if (oldRawLoc != null && newLoc != null && oldDir != null && newDir != null) {
		if (Log.loggingDebug)
			if (oldRawLoc != null && newLoc != null && oldDir != null && newDir != null) {
				log.debug("UpdateWNodeReqHook: Point.distanceTo(oldRawLoc, newLoc):" + Point.distanceTo(oldRawLoc, newLoc) + " " + AOVector.distanceTo(oldDir, newDir));
			}

		if (Point.distanceTo(oldRawLoc, newLoc) > 0.1f || AOVector.distanceTo(oldDir, newDir) > 0.1f) {
			// Send some message to interrupt any actions that are currently in casting
			CombatClient.interruptAbilityMessage interruptMsg = new CombatClient.interruptAbilityMessage(masterOid);
			interruptMsg.setMove(true);
			Engine.getAgent().sendBroadcast(interruptMsg);
		}
	}
	// make a wnodeupdatemsg - make a copy of the curWnode
	// but as basic world node since the WMWNode currently
	// has some serialization problems
	if (Log.loggingDebug) {
		log.debug("UpdateWNodeReqHook: make message UpdateWorldNodeMessage masterOid:" + masterOid);
	}
	BasicWorldNode updateNode = new BasicWorldNode(curWnode);
	updateNode.cid = wnode.cid;
	WorldManagerClient.UpdateWorldNodeMessage upMsg = new WorldManagerClient.UpdateWorldNodeMessage(masterOid, updateNode);

	if(Log.loggingDebug)log.debug("UpdateWNodeReqHook: send message UpdateWorldNodeMessage masterOid:" + masterOid+" upMsg="+upMsg);
	Engine.getAgent().sendBroadcast(upMsg);
	if (Log.loggingDebug) {
		log.debug("UpdateWNodeReqHook: end ");
	}
         
            return true;
        }
    }

	class ReparentWNodeReqHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.ReparentWNodeReqMessage rMsg = (WorldManagerClient.ReparentWNodeReqMessage) msg;

			OID oid = rMsg.getSubject();
			OID parentOid = rMsg.getParentOid();

			if (Log.loggingDebug)
				log.debug("ReparentWNodeReqHook: oid=" + oid + " parent=" + parentOid);
			// Entity entity = Entity.getEntity(oid);
			Entity entity = getWorldManagerEntity(oid);
			InterpolatedWorldNode parentWnode = null;

			if (entity == null) {
				log.error("ReparentWNodeReqHook: could not find entity: " + oid);
				return false;
			}
			if (!(entity instanceof AOObject)) {
				log.error("ReparentWNodeReqHook: entity is not an obj: " + entity);
				return false;
			}

			// get the object's current world node
			AOObject obj = (AOObject) entity;
			InterpolatedWorldNode wnode = (InterpolatedWorldNode) obj.worldNode();

			if (parentOid != null) {
				// Entity parent = Entity.getEntity(parentOid);
				Entity parent = getWorldManagerEntity(parentOid);
				if (parent == null) {
					log.error("ReparentWNodeReqHook: could not find parent: " + parent);
					return false;
				}
				if (!(parent instanceof AOObject)) {
					log.error("ReparentWNodeReqHook: parent is not an obj: " + parent);
					return false;
				}
				AOObject parentObj = (AOObject) parent;
				parentWnode = (InterpolatedWorldNode) parentObj.worldNode();
			}

			InterpolatedWorldNode oldParentWnode = (InterpolatedWorldNode) wnode.getParent();
			if (oldParentWnode != null) {
				oldParentWnode.removeChild(wnode);
			}
			wnode.setParent(parentWnode);
			if (parentWnode != null) {
				parentWnode.addChild(wnode);
				wnode.setLoc(parentWnode.getLoc());
				wnode.setDir(parentWnode.getDir());
				wnode.setOrientation(parentWnode.getOrientation());
			}

			BasicWorldNode bwnode = new BasicWorldNode(wnode);
			WorldManagerClient.UpdateWorldNodeMessage updateMsg = new WorldManagerClient.UpdateWorldNodeMessage(oid, bwnode);
			Engine.getAgent().sendBroadcast(updateMsg);
			WorldManagerClient.WorldNodeCorrectMessage correctMsg = new WorldManagerClient.WorldNodeCorrectMessage(oid, bwnode);
			Engine.getAgent().sendBroadcast(correctMsg);
			return true;
		}
	}

    class NoMovePropertyHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
			if(Log.loggingDebug)log.debug("NoMovePropertyHook: msg=" + rMsg);
			OID oid = rMsg.getSubject();
			if (Log.loggingDebug) {
				String sss = "";
				for (String ss : rMsg.getPropMap().keySet()) {
					sss += " | " + ss + "="+rMsg.getProperty(ss);
				}
				log.debug("NoMovePropertyHook: props=" + sss + " for "+oid);
			}
            AOObject obj = (AOObject) getWorldManagerEntity(oid);
            WMWorldNode curWnode = (WMWorldNode) (obj != null ? obj.worldNode() : null);
            WorldSpace<WMWorldNode> tree = curWnode != null ? curWnode.getWorldSpace() : null;
            if(rMsg.containsKey(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED)) {
            	//int move = (int) rMsg.getProperty(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
            	obj.MovementSpeedUpdate = System.currentTimeMillis();
            }
			if (rMsg.containsKey(WorldManagerClient.WORLD_PROP_NOMOVE)) {
				Boolean noMove = (Boolean) rMsg.getProperty(WorldManagerClient.WORLD_PROP_NOMOVE);
				if(Log.loggingDebug)log.debug("NoMovePropertyHook: obj="+obj+" noMove="+noMove);

				// If we're stopping, and we're a mob, and we have a MobPath
				if (noMove == true && obj != null && obj.getType().isMob() && obj.getProperty(WorldManagerClient.MOB_PATH_PROPERTY) != null) {
					log.debug("NoMovePropertyHook: stopping object");

					WorldManagerClient.MobPathCorrectionMessage correction = new WorldManagerClient.MobPathCorrectionMessage(oid, System.currentTimeMillis(), "linear", 0, "", new LinkedList<Point>());
					Engine.getAgent().sendBroadcast(correction);
					if(Log.loggingDebug)log.debug("NoMovePropertyHook: send path correction object "+correction);

					WorldManagerClient.MobPathMessage cancellation = new WorldManagerClient.MobPathMessage(oid, System.currentTimeMillis(), "linear", 0, "", new LinkedList<Point>(),"NoMovePropertyHook");
					Engine.getAgent().sendBroadcast(cancellation);
					if(Log.loggingDebug)log.debug("NoMovePropertyHook: MobPathMessage send empty path "+cancellation);

					BasicWorldNode wnode = obj.baseWorldNode();
					wnode.setDir(new AOVector(0, 0, 0));

					// make a wnodeupdatemsg - make a copy of the curWnode but as basic world node since the WMWNode currently has some serialization problems
					WorldManagerClient.UpdateWorldNodeMessage upMsg = new WorldManagerClient.UpdateWorldNodeMessage(oid, wnode);
					Engine.getAgent().sendBroadcast(upMsg);
					if(Log.loggingDebug)log.debug("NoMovePropertyHook: send UpdateWorldNodeMessage  wnode="+wnode+" upMsg="+upMsg);
				}
		     }
			if (CombatPlugin.PERCEPTION_STEALTH_STAT != null && rMsg.containsKey(CombatPlugin.PERCEPTION_STEALTH_STAT)) {
				if (obj != null) {
					obj.setProperty(CombatPlugin.PERCEPTION_STEALTH_STAT, rMsg.getProperty(CombatPlugin.PERCEPTION_STEALTH_STAT));
					if (tree != null) {
						// curWnode.getPerceiver()
                        tree.updatePerceiver(curWnode);
						Collection<WMWorldNode> perceivables = tree.getElementPerceivables(curWnode);
						for (WMWorldNode gainNode : perceivables) {
							if(	curWnode.getObject().getType().isPlayer() && gainNode.getPerceiver() != null)
                                tree.updatePerceiver(gainNode);
							if(Log.loggingDebug)log.debug("NoMovePropertyHook: PERCEPTION_STEALTH_STAT update node " + gainNode);
						}
						// updateElement(curWnode, (Point)curWnode.getLoc().clone());

					} else
						Log.debug("NoMovePropertyHook: node not spawned");
				}
			}
			if (CombatPlugin.STEALTH_STAT != null && rMsg.containsKey(CombatPlugin.STEALTH_STAT)) {
				if (obj != null) {
					obj.setProperty(CombatPlugin.STEALTH_STAT, rMsg.getProperty(CombatPlugin.STEALTH_STAT));
                    if (tree != null) {
						// curWnode.getPerceiver()
						tree.updatePerceiver(curWnode);
						/*
						 * Set<WMWorldNode> perceivables =
						 * curWnode.getQuadNode().getTree().getElements(curWnode.getLoc(),
						 * curWnode.getPerceptionRadius()); for (WMWorldNode gainNode : perceivables) {
						 * log.debug("NoMovePropertyHook: STEALTH_STAT update node "+gainNode);
						 * curWnode.getQuadNode().getTree().updatePerceiver(gainNode.getPerceiver()); }
						 */
						
					
						Collection<WMWorldNode> perceivables2 = tree.getElementPerceivables(curWnode);
						for (WMWorldNode gainNode : perceivables2) {
							log.debug("NoMovePropertyHook: STEALTH_STAT update node " + gainNode);
                            if( curWnode.getObject().getType().isPlayer() && gainNode.getPerceiver() != null)
								tree.updatePerceiver(gainNode);
						}
						// updateElement(curWnode, (Point)curWnode.getLoc().clone());

					} else
						Log.debug("NoMovePropertyHook: node not spawned");
				}
			}
			if (rMsg.containsKey("groupOid")) {
				if (obj != null) {
					obj.setProperty("groupOid", rMsg.getProperty("groupOid"));
                    if (tree != null) {
						// curWnode.getPerceiver()
						tree.updatePerceiver(curWnode);
						Collection<WMWorldNode> perceivables = tree.getElementPerceivables(curWnode);
						for (WMWorldNode gainNode : perceivables) {
							log.debug("NoMovePropertyHook: update node " + gainNode);
							//curWnode.getQuadNode().getTree().updatePerceiver(gainNode.getPerceiver());
                            if( curWnode.getObject().getType().isPlayer() && gainNode.getPerceiver() != null)
								tree.updatePerceiver(gainNode);
						}
						// updateElement(curWnode, (Point)curWnode.getLoc().clone());

					} else
						Log.debug("NoMovePropertyHook: node not spawned");
				}
			}
			
			if (rMsg.containsKey(GuildPlugin.GUILD_PROP)) {
				if (obj != null) {
					obj.setProperty(GuildPlugin.GUILD_PROP, rMsg.getProperty(GuildPlugin.GUILD_PROP));
                    if (tree != null) {
						// curWnode.getPerceiver()
						tree.updatePerceiver(curWnode);
						Collection<WMWorldNode> perceivables = tree.getElementPerceivables(curWnode);
						for (WMWorldNode gainNode : perceivables) {
							if(Log.loggingDebug)log.debug("NoMovePropertyHook: update node " + gainNode);
							//curWnode.getQuadNode().getTree().updatePerceiver(gainNode.getPerceiver());
                            if( curWnode.getObject().getType().isPlayer() && gainNode.getPerceiver() != null)
								tree.updatePerceiver(gainNode);
						}
						// updateElement(curWnode, (Point)curWnode.getLoc().clone());

					} else
						Log.debug("NoMovePropertyHook: node not spawned");
				}
			}
			
			
			
			
			
			 log.debug("NoMovePropertyHook: END");
            return true;
        }
    }
    
    /**
	 * The hook for when players login. This will reset their arenaID (in case there was a 
	 * server crash) and teleport them back to the original world.
	 *
	 */
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            //OID instanceOid = message.getInstanceOid();
            // Make sure the model is set correctly
    	    //String race = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "race");
    	    //String newMesh = getDisplayMesh(race);
    	    //DisplayContext dc = new DisplayContext(playerOid, newMesh);
    	    //sendDCMessage(playerOid, dc, true);
    	    //AOObject obj = (AOObject) getWorldManagerEntity(playerOid);
    	    //obj.displayContext(dc);
            
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    
	/**
	 * The hook for when players logout (or disconnect). This will remove the player from
	 * any arenas and queues they are in.
	 *
	 */
    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            OID playerOid = message.getSubject();
			if(Log.loggingDebug) Log.debug("LogoutHook: playerOid=" + playerOid);
            
            // Check if they are in an arena
            int arenaID = -1;
            try {
            	arenaID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
            } catch (NullPointerException e) {
            	Log.warn("ARENA PLUGIN: player " + playerOid + " does not have an arenaID property");
            }
            if (arenaID != -1)
                ArenaClient.removePlayer(playerOid);
            // Remove the player from any queues they may have been in
            HashMap<Integer, ArenaQueue> queues = (HashMap<Integer, ArenaQueue>) ArenaPlugin.getArenaQueues();
            //ArenaClient.leaveQueue(playerOid, arenaType);
            for (int k = 0; k < queues.size(); k++) {
				queues.get(k).removePlayer(playerOid);
			}
            
            // Check if they were in a Duel Challenge
            int challengeID = -1;
            try {
            	challengeID = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "duelChallengeID");
            } catch (NullPointerException e) {
            }
            if (challengeID != -1) {
            	//Map<Integer, DuelChallenge> duelChallenges = ArenaPlugin.getDuelChallenges();
            	//DuelChallenge challenge = duelChallenges.get(challengeID);
            	//challenge.playerDeclined(playerOid);
            	EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "factionOverride", "");
            	String name = WorldManagerClient.getObjectInfo(playerOid).name;
            	Log.debug("ARENA PLUGIN: removing player who is logging out from their duel");
            	ArenaClient.duelChallengeDisconnect(playerOid, name, challengeID);
            }
            
            // Check if they were in a Duel Challenge
            Log.debug("ARENA PLUGIN: checking duelID for player logging out");
            int duelID = -1;
            try {
            	duelID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID);
            } catch (NullPointerException e) {
            }
			if(Log.loggingDebug)Log.debug("ARENA PLUGIN: checking duelID for player logging out; ID is " + duelID);
            if (duelID != -1) {
            	//Map<Integer, Duel> duels = ArenaPlugin.getDuels();
            	//Duel duel = duels.get(duelID);
            	EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "factionOverride", "");
            	String name = WorldManagerClient.getObjectInfo(playerOid).name;
            	Log.debug("ARENA PLUGIN: removing player who is logging out from their duel");
            	ArenaClient.duelDisconnect(playerOid, name, duelID);
            }
            
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    
    class SpawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            OID instanceOid = spawnedMsg.getInstanceOid();
			try {
				ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
				if (objInfo.objType == ObjectTypes.player) {

					// Send down server current time
					long timeDif = (int) (System.currentTimeMillis() - serverStartTime) / 1000;
					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("ext_msg_subtype", "server_time");
					props.put("server_time_running", timeDif);
					Calendar cal = Calendar.getInstance();
					props.put("cyear", cal.get(Calendar.YEAR));
					props.put("cmonth", cal.get(Calendar.MONTH)+1);
					props.put("cday", cal.get(Calendar.DAY_OF_MONTH));
					props.put("year", serverYear);
					props.put("month", serverMonth);
					props.put("day", serverDay);
					props.put("hour", serverHour);
					props.put("minute", serverMinute);
					props.put("second", (int) serverSeconds);
					props.put("worldTimeSpeed", WORLD_TIME_SPEED);

					TargetedExtensionMessage teMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
					Engine.getAgent().sendBroadcast(teMsg);
				}
				Map<Integer, VolumetricRegion> regs = regions.get(instanceOid);
				if (regs != null) {
					Point p = WorldManagerClient.getObjectInfo(objOid).loc;
					for (VolumetricRegion region : regs.values()) {
						float distance = Point.distanceToXZ(p, new Point(region.getLoc()));
						if (distance < region.getReactionRadius()) {
							region.addPlayer(objOid);
						}
					}
				}

				int world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
				float globalWaterHeight = InstanceClient.getInstanceTemplate(world).getGlobalWaterHeight();

				objInfo.setProperty("waterHeight", globalWaterHeight);
				// EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "waterHeight", globalWaterHeight);
			} catch (Exception e) {

			}
            
            return true;
    	}
    }
    
    /**
	 * Called when a player or mob despawns. 
	 */
	class DespawnedHook implements Hook	 {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();
			OID instanceOid = despawnedMsg.getInstanceOid();
			
			if (regions.containsKey(instanceOid)) {
				for (VolumetricRegion region : regions.get(instanceOid).values()) {
					region.removePlayer(objOid);
				}
			}
			return true;
		}
	}
	
	/**
     * Updates the movement state for the player/mob specified. Will update the follow terrain
     * property as well based on the new movement state.
     * @author Andrew
     *
     */
    class WaterRegionTransitionHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		WaterRegionTransitionMessage spawnMsg = (WaterRegionTransitionMessage)msg;
    		OID playerOid = spawnMsg.getSubject();
    		Integer waterRegion = spawnMsg.getRegionID();
			if(Log.loggingDebug)Log.debug("WATER: handling water region transition for region " + waterRegion + " for " + playerOid);
    		//LinkedList<Integer> waterRegions = (LinkedList<Integer>) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "waterRegions");
    		//if (waterRegions == null)
    		//	waterRegions = new LinkedList<Integer>();
    		
    		if (spawnMsg.entering() /*&& !waterRegions.contains(waterRegion)*/) {
    			Log.debug("WATER: player entering new waterRegion");
    			//if (waterRegions.size() == 0) {
    				Log.debug("WATER: setting player to swimming");
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_MOVEMENT_STATE, MOVEMENT_STATE_SWIMMING);
    				boolean followTerrain = false;
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_FOLLOW_TERRAIN, followTerrain);
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "vr", waterRegion);
    				Entity entity = getWorldManagerEntity(playerOid);
    	    		// get the object's current world node
    	            AOObject obj = (AOObject) entity;
    	            InterpolatedWorldNode curWnode = (InterpolatedWorldNode) obj.worldNode();
    	            curWnode.setFollowsTerrain(followTerrain);
    			//}
    			//waterRegions.add(waterRegion);
    		} else if (!spawnMsg.entering()) {
    			//waterRegions.remove(waterRegion);
    			Log.debug("WATER: player leaving waterRegion");
    			//if (waterRegions.size() == 0) {
    				Log.debug("WATER: setting player to walking");
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_MOVEMENT_STATE, MOVEMENT_STATE_RUNNING);
    				boolean followTerrain = true;
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_FOLLOW_TERRAIN, followTerrain);
    				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "vr", -1);
    				Entity entity = getWorldManagerEntity(playerOid);
    	    		// get the object's current world node
    	            AOObject obj = (AOObject) entity;
    	            InterpolatedWorldNode curWnode = (InterpolatedWorldNode) obj.worldNode();
    	            curWnode.setFollowsTerrain(followTerrain);
    			//}
    		}
    		//EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "waterRegions", waterRegions);
            
    		return true;
    	}
    }
    
    /**
     * Updates the movement state for the player/mob specified. Will update the follow terrain
     * property as well based on the new movement state.
     * @author Andrew
     *
     */
    class SetMovementStateHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
			if(Log.loggingDebug)log.debug("SetMovementStateHook: "+msg);
    		SetMovementStateMessage spawnMsg = (SetMovementStateMessage)msg;
    		OID playerOid = spawnMsg.getSubject();
    		int movementState = spawnMsg.getMovementState();
			if(Log.loggingDebug)Log.debug("STATE: setting movement state to " + movementState + " for " + playerOid);
    		EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_MOVEMENT_STATE, movementState);
    		// Set the follow terrain property based on the movement state
    		boolean followTerrain = true;
    		if (movementState != 1) {
    			followTerrain = false;
    		}
    		EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, PROP_FOLLOW_TERRAIN, followTerrain);
    		Entity entity = getWorldManagerEntity(playerOid);
    		// get the object's current world node
            AOObject obj = (AOObject) entity;
            InterpolatedWorldNode curWnode = (InterpolatedWorldNode) obj.worldNode();
            curWnode.setFollowsTerrain(followTerrain);
            
    		return true;
    	}
    }
    
    /**
     * Updates the movement state for the player/mob specified. Will update the follow terrain
     * property as well based on the new movement state.
     * @author Andrew
     *
     */
    class SetUnderwaterHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage spawnMsg = (ExtensionMessage)msg;
    		OID playerOid = spawnMsg.getSubject();
    		boolean underwater = (Boolean) spawnMsg.getProperty("underwater");
			if(Log.loggingDebug)Log.debug("STATE: setting underwater to " + underwater + " for " + playerOid);
    		EnginePlugin.setObjectProperty(playerOid, CombatClient.NAMESPACE, "underwater", underwater);
            //CombatPlugin.getCombatInfo(playerOid);
    		return true;
    	}
    }

	class FixWorldNodeHook implements Hook {
		public boolean processMessage(Message msg, int flags)
		{
			ObjectManagerClient.FixWorldNodeMessage message = (ObjectManagerClient.FixWorldNodeMessage) msg;
			BasicWorldNode worldNode = message.getWorldNode();
			if(Log.loggingDebug)log.debug("FixWorldNodeHook: start oid=" + message.getOid() + " wnode=" + worldNode);
			Entity entity = null;
			try {
				entity = (Entity) Engine.getDatabase().loadEntity(
						message.getOid(), WorldManagerClient.NAMESPACE);
			}
			catch (AORuntimeException e) {
				Log.error("FixWorldNodeHook: loadEntity oid="+message.getOid()+" Exception "+e);
				Engine.getAgent().sendBooleanResponse(msg, false);
				return false;
			}

			if (entity == null) {
				Log.error("FixWorldNodeHook: unknown oid="+message.getOid());
				Engine.getAgent().sendBooleanResponse(msg, false);
				return false;
			}
			if (! (entity instanceof AOObject)) {
				Log.error("FixWorldNodeHook: not instanceof AOObject oid="+
						message.getOid() + " class="+entity.getClass().getName());
				Engine.getAgent().sendBooleanResponse(msg, false);
				return false;
			}

			AOObject obj = (AOObject) entity;
			WMWorldNode wnode = (WMWorldNode) obj.worldNode();
			wnode.setInstanceOid(worldNode.getInstanceOid());
			if (worldNode.getLoc() != null)
				wnode.setLoc(worldNode.getLoc());
			if (worldNode.getOrientation() != null)
				wnode.setOrientation(worldNode.getOrientation());
			if (worldNode.getDir() != null)
				wnode.setDir(worldNode.getDir());

			Engine.getPersistenceManager().persistEntity(obj);

			if (Log.loggingDebug)
				log.debug("FixWorldNodeHook: done oid=" + message.getOid() + " wnode=" + obj.worldNode());

			Engine.getAgent().sendBooleanResponse(msg, true);
			return true;
		}
	}

    /**
	 * Used for AoE abilities to find out which mobs/players are in an area.
	 *
	 */
    class GetTargetsInAreaHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	CombatClient.getAoeTargetsMessage message = (CombatClient.getAoeTargetsMessage) msg;
        	OID subjectOid = message.getSubject();
            
            BasicWorldNode subjectWorldNode = ((AOObject)getWorldManagerEntity(subjectOid)).baseWorldNode();
            OID instanceOid = subjectWorldNode.getInstanceOid();
            List<OID> objectsIn = null;
            if (instanceOid != null) {
                objectsIn = getInstanceObjectsIn(instanceOid, message.getLoc(), message.getRadius(), message.getObjectType());
            } else {
                objectsIn = getObjectsIn(message.getLoc(), message.getRadius(), message.getObjectType());
            }
            Engine.getAgent().sendObjectResponse(message, objectsIn);

            return true;
        }
    }
    
    private List<OID> getInstanceObjectsIn(OID instanceOid, Point loc, Integer radius, ObjectType objectType) {
    	Entity[] entities = EntityManager.getAllEntitiesByNamespace(Namespace.WORLD_MANAGER);
    	List<OID> objectsIn = new ArrayList<OID>();
    	if (objectType != null) {
    		for (Entity entity : entities) {
    			OID entityOid = entity.getOid();
    			AOObject obj = (AOObject)getWorldManagerEntity(entityOid);
    			if(obj != null){
    				BasicWorldNode entityWorldNode = obj.baseWorldNode();
    				if ((entity.getType().getTypeId() == objectType.getTypeId()) && (instanceOid.equals(entityWorldNode.getInstanceOid()))) {
    					Log.debug("[CYC][1] entityType: " + entity.getType() + ", objectType: " + objectType);
    					Point entityLoc = entityWorldNode.getLoc();
    					Log.debug("[CYC][1] loc: " + loc + ", entityLoc: " + entityLoc + ", entityName: " + entity.getName());
    					if (Math.round(Point.distanceTo(loc, entityLoc)) <= radius) {
    						objectsIn.add(entityOid);
    					}
    					Log.debug("[CYC][1] distance: " + Math.round(Point.distanceToXZ(loc, entityLoc)) + ", radius: " + radius);
    				}
    			}
    		}
    	} else {
    		for (Entity entity : entities) {
    			OID entityOid = entity.getOid();
    			BasicWorldNode entityWorldNode = ((AOObject)getWorldManagerEntity(entityOid)).baseWorldNode();
    			if (instanceOid.equals(entityWorldNode.getInstanceOid())) {
    				Log.debug("[CYC][2] entityType: " + entity.getType());
    				Point entityLoc = entityWorldNode.getLoc();
    				Log.debug("[CYC][2] loc: " + loc + ", entityLoc: " + entityLoc + ", entityName: " + entity.getName());
    				if (Math.round(Point.distanceTo(loc, entityLoc)) <= radius) {
    					objectsIn.add(entityOid);
    				}
    				Log.debug("[CYC][2] distance: " + Math.round(Point.distanceToXZ(loc, entityLoc)) + ", radius: " + radius);
    			}
    		}
    	}
    	return objectsIn;
    }
    
    private List<OID> getObjectsIn(Point loc, Integer radius, ObjectType objectType) {
    	Entity[] entities = EntityManager.getAllEntitiesByNamespace(Namespace.WORLD_MANAGER);
    	List<OID> objectsIn = new ArrayList<OID>();
    	if (objectType != null) {
    		for (Entity entity : entities) {
    			if (entity.getType().getTypeId() == objectType.getTypeId()) {
    				OID entityOid = entity.getOid();
    				BasicWorldNode entityWorldNode = ((AOObject)getWorldManagerEntity(entityOid)).baseWorldNode();
    				Point entityLoc = entityWorldNode.getLoc();
    				if (Math.round(Point.distanceTo(loc, entityLoc)) <= radius) {
    					objectsIn.add(entityOid);
    				}
    			}
    		}
    	} else {
    		for (Entity entity : entities) {
    			OID entityOid = entity.getOid();
    			BasicWorldNode entityWorldNode = ((AOObject)getWorldManagerEntity(entityOid)).baseWorldNode();
    			Point entityLoc = entityWorldNode.getLoc();
    			if (Math.round(Point.distanceTo(loc, entityLoc)) <= radius) {
    				objectsIn.add(entityOid);
    			}
    		}
    	}
    	return objectsIn;
    }
    
    class CheckIfTargetsInAreaHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	AgisWorldManagerClient.CheckIfTargetsInAreaMessage message = (AgisWorldManagerClient.CheckIfTargetsInAreaMessage) msg;
        	OID subjectOid = message.getSubject();
        	if (Log.loggingDebug)
				log.debug("CheckIfTargetsInAreaHook: subjectOid=" + subjectOid+" msg="+msg);
			long start = System.nanoTime();
			Point loc  = message.getLoc();
			OID	objLocOid = message.getObjectLocOid();
			Quaternion quternion = Quaternion.Identity;
			boolean isQuaternion =false;
			if(message.getQuaternion() != null){
				quternion = message.getQuaternion();
				isQuaternion =true;
			}
			if (objLocOid != null) {
				AOObject obj = (AOObject) getWorldManagerEntity(objLocOid);
				if (obj == null) {
					log.error("CheckIfTargetsInAreaHook: subjectOid=" + subjectOid + " not found AOObject for objLocOid=" + objLocOid + " return");
					if (message.getDistance()) {
						Engine.getAgent().sendObjectResponse(message, new HashMap<OID, TargetsInAreaEntity>());
					} else {
						Engine.getAgent().sendObjectResponse(message, new ArrayList<OID>());
					}
					return true;
				}
				BasicWorldNode bwNode = obj.baseWorldNode();
				loc = bwNode.getLoc();
				if (Log.loggingDebug)
					log.debug("CheckIfTargetsInAreaHook: player Quaternion Yaw " + bwNode.getOrientation().getYaw());
				if (quternion.equals(Quaternion.Identity)) {
					if (Log.loggingDebug)
						log.debug("CheckIfTargetsInAreaHook: get player Quaternion Yaw " + bwNode.getOrientation().getYaw());
					quternion = new Quaternion(bwNode.getOrientation());
				}
				if (Log.loggingDebug) log.debug("CheckIfTargetsInAreaHook: Yaw " + quternion.getYaw());
			}
			ArrayList<OID> targetsInArea = new ArrayList<OID>();
			HashMap<OID, TargetsInAreaEntity> targetsInAreaWithDist = new HashMap<OID, TargetsInAreaEntity>();
			ArrayList<OID> targetsToCheck = message.getTargetsToCheck();
			if (Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: targetsToCheck=" + targetsToCheck);


			for (OID targetToCheck : targetsToCheck) {
				AOObject obj = (AOObject) getWorldManagerEntity(targetToCheck);
				if (obj == null) {
					log.debug("CheckIfTargetsInAreaHook: could not find obj for oid=" + targetToCheck);
					continue;
				}
				BasicWorldNode bwNode = obj.baseWorldNode();
				//float dist = Point.distanceToXZ(loc, bwNode.getLoc()); 
				float dist = Point.distanceTo(loc, bwNode.getLoc());
				if (Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: isQuaternion=" + isQuaternion+" loc="+loc+" dist="+dist+" ?");
//				if(isQuaternion){
//					dist= dist-10f;
//					if (Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: isQuaternion=" + isQuaternion+" loc="+loc+" dist="+dist+" -10f");
//
//				}
				if (Log.loggingDebug)
					log.debug("CheckIfTargetsInAreaHook: player orient: " + loc + " targetToCheck:" + targetToCheck + " loc:" + bwNode.getLoc() + " distance:" + dist + " Radius:" + message.getRadius());
				if (message.getMinRadius() <= dist && dist < message.getRadius()) {
					Point sLoc = loc;
					if(isQuaternion && message.getAngle() < 20) {
						AOVector Loc2 = new AOVector(loc);
						Loc2.add(quternion.getZAxis().multiply(-10f));
						if (Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: isQuaternion=" + isQuaternion+" "+loc+" "+Loc2.toPoint()+" -10f");
						sLoc = Loc2.toPoint();
					}

					//BasicWorldNode targetNode = WorldManagerClient.getWorldNode(target.getOwnerOid());
					Point tLoc = bwNode.getLoc();
					//tLoc.add(new Point(0,0.5f,0));
					AOVector reqDir1 = AOVector.sub(tLoc, sLoc);
            		AOVector reqDir = AOVector.sub(tLoc, sLoc);
            		//AOVector reqDir2 = AOVector.sub(message.getLoc(), bwNode.getLoc());
            		//Log.debug("CheckIfTargetsInAreaHook: reqDir="+reqDir+"; reqDir2="+reqDir2);
            		//Log.debug("CheckIfTargetsInAreaHook: reqDir atan2="+Math.atan2(reqDir.getX(), reqDir.getZ())+"; reqDir2 atan2="+Math.atan2(reqDir2.getX(), reqDir2.getZ()));
            		//Log.debug("CheckIfTargetsInAreaHook: reqDir degrees="+Math.toDegrees(Math.atan2(reqDir.getX(), reqDir.getZ()))+"; reqDir2 degrees="+Math.toDegrees(Math.atan2(reqDir2.getX(), reqDir2.getZ())));
                    //Quaternion q = Quaternion.
            		//AOVector orientAngle = attackerNode.getOrientation().toEulerAngles();
					float playerYaw = quternion.getYaw();//attackerNode.getOrientation().getYaw();
					float playerPitch = quternion.getPitch();//attackerNode.getOrientation().getYaw();
					float motionYaw1 = AOVector.getLookAtYaw(reqDir1);
					float motionYaw = AOVector.getLookAtYaw(reqDir);
					float motionPitch = AOVector.getLookAtPitch(reqDir);
					if (Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: player orient: " + playerYaw + " and playerPitch = " + playerPitch + " motionYaw=" + motionYaw + " Check angle:" + message.getAngle()+" motionPitch="+motionPitch);
					if(motionYaw < 0)
						motionYaw += 360;
					if(motionYaw1 < 0)
						motionYaw1 += 360;
					float yaw = playerYaw - motionYaw;
					float yaw1 = playerYaw - motionYaw1;
					float pitch = playerPitch - motionPitch;
					if(yaw < 360)
            			yaw += 360;
            		if(yaw >180)
            			yaw -= 360;
					if(yaw1 < 360)
						yaw1 += 360;
					if(yaw1 >180)
						yaw1 -= 360;
					if (Log.loggingDebug)
						log.debug("CheckIfTargetsInAreaHook: player orient: " + playerYaw + " and motionYaw = " + motionYaw + " yaw=" + yaw +" yaw org=" + yaw1 + " Check angle:" + message.getAngle()+" pitch="+pitch+
								"Math.abs(playerYaw - motionYaw)"+ Math.abs(yaw));
					if ( Math.abs(yaw) < message.getAngle() && (!isQuaternion || (isQuaternion && Math.abs(pitch) < message.getAngle()*((message.getAngle() <10 && dist < 3)?4.5f:(message.getAngle() <20)?1.7f:1f)))) {
						if (Log.loggingDebug)
							log.debug("CheckIfTargetsInAreaHook: adding " + targetToCheck + " to list");
						if (!targetsInArea.contains(targetToCheck))
							targetsInArea.add(targetToCheck);
						targetsInAreaWithDist.put(targetToCheck, new TargetsInAreaEntity(targetToCheck,dist, tLoc));
					}

				//	targetsInArea.add(targetToCheck);
				}
			}
			long end = System.nanoTime();
			long microseconds = (end - start) / 1000;
			if(message.getDistance()) {
			if(Log.loggingDebug)log.debug("CheckIfTargetsInAreaHook: microseconds: " + microseconds + " targetsInAreaWithDist:"+targetsInAreaWithDist+" targetsToCheck:" + targetsToCheck);
				Engine.getAgent().sendObjectResponse(message, targetsInAreaWithDist);
			} else {
			Engine.getAgent().sendObjectResponse(message, targetsInArea);
			}
			return true;
        }
	}

    // start running at 2.0m/sec
    public static Float defaultRunThreshold = 2.0f;
    
    /**
     * creates a DisplayContextMessage with notifyOid set as its MSG_OID.
     */
    protected void sendDCMessage(AOObject obj, boolean forceInstantLoad) {
        if (Log.loggingDebug)
            log.debug("sendDCMessage: obj=" + obj);

        if (!(obj instanceof AgisObject)) {
//            log.error("sendDCMessage: not a agisobj: " + obj);
            return;
        }
        DisplayContext dc = obj.displayContext();
        sendDCMessage(obj.getOid(), dc, forceInstantLoad);
    }
    
    /**
     * creates a DisplayContextMessage with notifyOid set as its MSG_OID.
     */
    protected void sendDCMessage(OID oid, DisplayContext dc, boolean forceInstantLoad) {
        if (Log.loggingDebug)
            log.debug("sendDCMessage: obj=" + oid);

        if (dc == null) {
            log.warn("sendDCMessage: obj has no dc: " + oid);
            return;
        }

        WorldManagerClient.DisplayContextMessage dcMsg = new WorldManagerClient.DisplayContextMessage(oid, dc);
        dcMsg.setForceInstantLoad(forceInstantLoad);
        Engine.getAgent().sendBroadcast(dcMsg);
    }

    /**
     * sends over health, int, str, etc.
     */
    protected void sendPropertyMessage(OID notifyOid, AOObject updateObj) {
        if (! (updateObj instanceof AgisObject)) {
            if (Log.loggingDebug)
                log.debug("AgisWorldManagerPlugin.sendPropertyMessage: skipping, obj is not agisobject: "
		          + updateObj);
            return;
        }
        AgisObject mObj = (AgisObject) updateObj;
        OID updateOid = updateObj.getMasterOid();

    	PropertyMessage propMessage = new PropertyMessage(updateOid, notifyOid);
    	for (String key : mObj.getPropertyMap().keySet()) {
            if (propertyExclusions.contains(key))
                continue;
            propMessage.setProperty((String)key, mObj.getProperty(key));
    	}

        // send the message
    	Log.debug("AgisWorldManagerPlugin.sendPropertyMessage: sending property message for obj=" + updateObj + " to=" + notifyOid + " msg=" + propMessage);
        Engine.getAgent().sendBroadcast(propMessage);
    }

    protected void sendTargetedPropertyMessage(OID targetOid, AOObject updateObj)
    {
        if (! (updateObj instanceof AgisObject)) {
            if (Log.loggingDebug)
                log.debug("AgisWorldManagerPlugin.sendTargetedPropertyMessage: skipping, obj is not agisobject: "
		          + updateObj);
            return;
        }
        AgisObject mObj = (AgisObject) updateObj;
        OID updateOid = updateObj.getMasterOid();

        TargetedPropertyMessage propMessage =
            new TargetedPropertyMessage(targetOid, updateOid);
        for (String key : mObj.getPropertyMap().keySet()) {
            if (propertyExclusions.contains(key))
                continue;
            propMessage.setProperty((String)key, mObj.getProperty(key));
        }

        // send the message
        Log.debug("AgisWorldManagerPlugin.sendTargetedPropertyMessage: subject=" + updateObj + " target=" + targetOid + " msg=" + propMessage);
        Engine.getAgent().sendBroadcast(propMessage);
    }

    /**
     * gets the current display context - used in the base world manager plugin
     * when it needs to send the display context to the proxy - this gets called
     * by the wmgr via the proxy upon logging in
     */
    protected DisplayContext getDisplayContext(OID objOid) {
	Entity entity = getWorldManagerEntity(objOid);
        if (entity == null) {
            return null;
        }
        if (!(entity instanceof AOObject)) {
            return null;
        }
        AOObject obj = (AOObject) entity;
        if (!(obj instanceof AgisObject)) {
            // its base object type, just send over its stored
            // displaycontext
            return obj.displayContext();
        }

        DisplayContext dc = AgisDisplayContext.createFullDisplayContext((AgisObject) obj);
        if (Log.loggingDebug)
            log.debug("AgisWorldManagerPlugin: get dc = " + dc);
        return dc;
    }
    
    HashMap<OID, Float> instanceGlobalWaterHeights = new HashMap<OID, Float>();
    HashMap<OID, HashMap<Integer, VolumetricRegion>> regions = new HashMap<OID, HashMap<Integer, VolumetricRegion>>();
    protected int waterHeight = Integer.MIN_VALUE;
    
    private long serverStartTime;
    private long currentSecondsRunning = 0;
    protected int serverYear=1;
 	protected int serverMonth=1;
 	protected int serverDay=1;
 	private int serverHour=0;
    private int serverMinute=0;
    private float serverSeconds=0;
    private static float WORLD_TIME_SPEED = 1f;
    public static String WORLD_TIME_ZONE = "UTC";
    
    
    public static final int MOVEMENT_STATE_RUNNING = 1;
    public static final int MOVEMENT_STATE_SWIMMING = 2;
    public static final int MOVEMENT_STATE_FLYING = 3;
    
    public static final String PROP_FOLLOW_TERRAIN = "follow_terrain";
    public static final String PROP_MOVEMENT_STATE = "movement_state";
    public static String PROP_MOVEMENT_SPEED = "movement_speed";
    public static final String PROP_ACTION_STATE = "action_state";
    
}
