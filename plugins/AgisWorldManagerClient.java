package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;

import atavism.agis.objects.TargetsInAreaEntity;
import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.PropertyMessage;


/**
 * AGIS-specific calls for sending/getting messages to the AgisWorldManagerPlugin
 */
public class AgisWorldManagerClient {


	/**
	 * Sends the SpawnTrapMessage.
	 * @param oid
	 * @param abilityId
	 * @param size
	 * @param time
	 * @param targetType
	 * @param activationtime
	 * @param loc
	 * @param model
	 */
	public static void SpawnTrap(OID oid, int abilityId, float size, float time,int targetType,float activationtime,Point loc, String model) {
		Log.debug("AWMC: SpawnTrap oid="+oid+", a="+abilityId+", s="+size+", t="+time+", tt="+targetType+", at="+activationtime+", loc="+loc+", model="+model);
		SpawnTrapMessage msg = new SpawnTrapMessage(oid, abilityId, size, time, targetType, activationtime, loc, model);
		Engine.getAgent().sendBroadcast(msg); 
		Log.debug("AWMC: SpawnTrap");
	}
	
	/**
	 * Sends the SetMovementStateMessage.
	 * @param oid
	 * @param movementState
	 */
	public static void sendSetMovementStateMessage(OID oid, int movementState) {
		SetMovementStateMessage msg = new SetMovementStateMessage(oid, movementState);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("MOVEMENT: setMovementState hit 2");
	}

	/**
	 * Message used to put a bag item from the players inventory into a bag slot. 
	 * Do not use this to move a bag from one slot to another.
	 * @author Andrew Harrison
	 *
	 */
	public static class SetMovementStateMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public SetMovementStateMessage() {
            super();
        }
		public SetMovementStateMessage(OID oid, int movementState) {
			super(oid);
			setMsgType(MSG_TYPE_SET_MOVEMENT_STATE);
			setMovementState(movementState);
			Log.debug("MOVEMENT: setMovementState hit 1");
		}
		
		public void setMovementState(int state) {
			this.movementState = state;
		}
		public int getMovementState() {
			return this.movementState;
		}
		int movementState;
	}

	/**
	 * Sends the WaterRegionTransitionMessage.
	 * @param oid
	 * @param regionID
	 * @param entering
	 */
	public static void sendWaterRegionTransitionMessage(OID oid, int regionID, boolean entering) {
		WaterRegionTransitionMessage msg = new WaterRegionTransitionMessage(oid, regionID, entering);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("MOVEMENT: setMovementState hit 2");
	}

	/**
	 * Message used to put a bag item from the players inventory into a bag slot. 
	 * Do not use this to move a bag from one slot to another.
	 * @author Andrew Harrison
	 *
	 */
	public static class WaterRegionTransitionMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public WaterRegionTransitionMessage() {
            super();
        }
		public WaterRegionTransitionMessage(OID oid, int regionID, boolean entering) {
			super(oid);
			setMsgType(MSG_TYPE_WATER_REGION_TRANSITION);
			setRegionID(regionID);
			entering(entering);
			Log.debug("MOVEMENT: setMovementState hit 1");
		}
		
		public void setRegionID(int regionID) {
			this.regionID = regionID;
		}
		public int getRegionID() {
			return this.regionID;
		}
		int regionID;
		
		public void entering(boolean entering) {
			this.entering = entering;
		}
		public boolean entering() {
			return this.entering;
		}
		boolean entering;
	}

	/**
	 * Sends the ChangeInstanceMessage.
 	 * @param playerOid
	 * @param instanceID
	 * @param loc
	 */
	public static void sendChangeInstance(OID playerOid, int instanceID, Point loc) {
		ChangeInstanceMessage msg = new ChangeInstanceMessage(playerOid, instanceID, null, loc, null);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: ChangeInstance hit 2");
	}
	
	public static void sendChangeInstance(OID playerOid, int instanceID, Point loc, String suffix) {
		ChangeInstanceMessage msg = new ChangeInstanceMessage(playerOid, instanceID, null, loc, suffix);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: ChangeInstance hit 2");
	}
	
	public static void sendChangeInstance(OID playerOid, int instanceID, String marker) {
		ChangeInstanceMessage msg = new ChangeInstanceMessage(playerOid, instanceID, marker, null, null);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: ChangeInstance hit 2");
	}
	
	public static void sendChangeInstance(OID playerOid, int instanceID, String marker, String suffix) {
		ChangeInstanceMessage msg = new ChangeInstanceMessage(playerOid, instanceID, marker, null, suffix);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: ChangeInstance hit 2");
	}

	public static class ChangeInstanceMessage extends  GenericMessage {
		private static final long serialVersionUID = 1L;
        public ChangeInstanceMessage() {
            super();
        }
		public ChangeInstanceMessage(OID playerOid, int instanceID, String marker, Point loc, String suffix) {
			setMsgType(MSG_TYPE_CHANGE_INSTANCE);
			setPlayerOid(playerOid);
			setInstanceID(instanceID);
			setMarker(marker);
			setLoc(loc);
			setSuffix(suffix);
			Log.debug("INSTANCE: ChangeInstance hit 1");
		}
		
		public void setPlayerOid(OID playerOid) {
			this.playerOid = playerOid;
		}
		public OID getPlayerOid() {
			return this.playerOid;
		}
		OID playerOid;
		
		public void setInstanceID(int instanceID) {
			this.instanceID = instanceID;
		}
		public int getInstanceID() {
			return this.instanceID;
		}
		int instanceID;
		
		public void setMarker(String marker) {
			this.marker = marker;
		}
		public String getMarker() {
			return this.marker;
		}
		String marker;
		
		public void setLoc(Point loc) {
			this.loc = loc;
		}
		public Point getLoc() {
			return this.loc;
		}
		Point loc;
		
		public void setSuffix(String suffix) {
			this.suffix = suffix;
		}
		public String getSuffix() {
			return this.suffix;
		}
		String suffix;
	}
	
	public static void returnToLastInstance(OID playerOid) {
		ReturnToLastInstanceMessage msg = new ReturnToLastInstanceMessage(playerOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: ChangeInstance hit 2");
	}
	
	public static class ReturnToLastInstanceMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public ReturnToLastInstanceMessage() {
            super();
        }
		public ReturnToLastInstanceMessage(OID playerOid) {
			setMsgType(MSG_TYPE_RETURN_TO_LAST_INSTANCE);
			setPlayerOid(playerOid);
			Log.debug("INSTANCE: ReturnInstance hit 1");
		}
		
		public void setPlayerOid(OID playerOid) {
			this.playerOid = playerOid;
		}
		public OID getPlayerOid() {
			return this.playerOid;
		}
		OID playerOid;
	}
	
	public static void sendServerTime(int hour, int minute, int day, int month, int year) {
		ServerTimeMessage msg = new ServerTimeMessage(hour, minute, day, month, year);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static class ServerTimeMessage extends GenericMessage {

        public ServerTimeMessage() {
            super(MSG_TYPE_SERVER_TIME);
        }

        public ServerTimeMessage(int hour, int minute, int day, int month, int year) {
            super(MSG_TYPE_SERVER_TIME);
            setYear(year);
            setMonth(month);
            setDay(day);
            setHour(hour);
            setMinute(minute);
            
        }
        
        int hour;
        int minute;
        int day;
        int month;
        int year;
        
        public int getYear() {
            return year;
        }
        public void setYear(int year) {
            this.year = year;
        }
        public int getMonth() {
            return month;
        }
        public void setMonth(int month) {
            this.month = month;
        }
        public int getDay() {
            return day;
        }
        public void setDay(int day) {
            this.day = day;
        }
        public int getHour() {
            return hour;
        }
        public void setHour(int hour) {
            this.hour = hour;
        }
        
        public int getMinute() {
            return minute;
        }
        public void setMinute(int minute) {
            this.minute = minute;
        }

        private static final long serialVersionUID = 1L;
    }
	
	public static ArrayList<OID> checkIfTargetsInArea(OID obj,ArrayList<OID> targetsToCheck, Point loc, float radius,float angle,Quaternion quaternion, float minRadius) {
		CheckIfTargetsInAreaMessage msg = new CheckIfTargetsInAreaMessage(obj,targetsToCheck, loc, radius, angle, quaternion, minRadius);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInArea hit 1");
		try {
			ArrayList<OID> statValue = (ArrayList<OID>) Engine.getAgent().sendRPCReturnObject(msg);
			Log.debug("CLASSABILITY CLIENT: checkIfTargetsInArea hit 2");
			return statValue;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.exception(e);
			e.printStackTrace();
		}
		return new ArrayList<OID>();
	}
	public static ArrayList<OID> checkIfTargetsInArea(OID obj,ArrayList<OID> targetsToCheck, Point loc, float radius,float angle,Quaternion quaternion, float minRadius,OID objLoc) {
		CheckIfTargetsInAreaMessage msg = new CheckIfTargetsInAreaMessage(obj,targetsToCheck, loc, radius, angle, quaternion, minRadius);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInArea hit 1");
		msg.setObjectLocOid(objLoc);
		try {
			ArrayList<OID> statValue = (ArrayList<OID>) Engine.getAgent().sendRPCReturnObject(msg);
			Log.debug("CLASSABILITY CLIENT: checkIfTargetsInArea hit 2");
			return statValue;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.exception(e);
			e.printStackTrace();
		}
		return new ArrayList<OID>();
	}

	@SuppressWarnings("unchecked")
	public static HashMap<OID,TargetsInAreaEntity> checkIfTargetsInAreaGetWithDistance(OID obj,ArrayList<OID> targetsToCheck, Point loc, float radius,float angle,Quaternion quaternion, float minRadius) {
		CheckIfTargetsInAreaMessage msg = new CheckIfTargetsInAreaMessage(obj,targetsToCheck, loc, radius, angle, quaternion, minRadius);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInAreaGetWithDistance hit 1");
		msg.setDistance(true);
		HashMap<OID, TargetsInAreaEntity> statValue = (HashMap<OID,TargetsInAreaEntity>) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInAreaGetWithDistance hit 2");
		return statValue;
	}
	@SuppressWarnings("unchecked")
	public static HashMap<OID,TargetsInAreaEntity> checkIfTargetsInAreaGetWithDistance(OID obj,ArrayList<OID> targetsToCheck, Point loc, float radius,float angle,Quaternion quaternion, float minRadius,OID objLoc) {
		CheckIfTargetsInAreaMessage msg = new CheckIfTargetsInAreaMessage(obj,targetsToCheck, loc, radius, angle, quaternion, minRadius);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInAreaGetWithDistance hit 1");
		msg.setDistance(true);
		msg.setObjectLocOid(objLoc);
		HashMap<OID,TargetsInAreaEntity> statValue = (HashMap<OID,TargetsInAreaEntity>) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("CLASSABILITY CLIENT: checkIfTargetsInAreaGetWithDistance hit 2");
		return statValue;
	}

	
	public static class CheckIfTargetsInAreaMessage extends SubjectMessage {

        public CheckIfTargetsInAreaMessage() {
            super(MSG_TYPE_CHECK_IF_TARGETS_IN_AREA);
        }

        public CheckIfTargetsInAreaMessage(OID obj,ArrayList<OID> targetsToCheck, Point loc, float radius,float angle, Quaternion quaternion, float minRadius) {
            super(MSG_TYPE_CHECK_IF_TARGETS_IN_AREA,obj);
            setTargetsToCheck(targetsToCheck);
            setLoc(loc);
            setRadius(radius);
            setAngle(angle);
            setQuaternion( quaternion);
            setMinRadius(minRadius);
            }
        
        ArrayList<OID> targetsToCheck;
        Point loc;
        float radius;
        float minRadius;
        float angle;
        Quaternion quaternion;
        boolean distance = false; 
        OID objOid ;
        
        public ArrayList<OID> getTargetsToCheck() {
            return targetsToCheck;
        }
        public void setTargetsToCheck(ArrayList<OID> targetsToCheck) {
            this.targetsToCheck = targetsToCheck;
        }
        
        public Point getLoc() {
            return loc;
        }
        public void setLoc(Point loc) {
            this.loc = loc;
        }
        
        public float getRadius() {
            return radius;
        }
        public void setRadius(float radius) {
            this.radius = radius;
        }
        public float getMinRadius() {
            return minRadius;
        }
        public void setMinRadius(float radius) {
            this.minRadius = radius;
        }
        public float getAngle() {
            return angle;
        }
        public void setAngle(float angle) {
            this.angle = angle;
        }

        public Quaternion getQuaternion() {
            return quaternion;
        }
        public void setQuaternion(Quaternion quaternion) {
            this.quaternion = quaternion;
        }
        public boolean getDistance() {
            return distance;
        }
        public void setDistance(boolean val) {
            this.distance = val;
        }

        public OID getObjectLocOid() {
            return objOid;
        }
        public void setObjectLocOid(OID val) {
            this.objOid = val;
        }

        private static final long serialVersionUID = 1L;
    }
	
	
	public static class SpawnTrapMessage extends SubjectMessage {

        public SpawnTrapMessage() {
            super(MSG_TYPE_SPAWN_TRAP);
        }

        public SpawnTrapMessage(OID oid,int abilityId, float size, float time,int targetType, float activationtime, Point loc, String model) {
            super(MSG_TYPE_SPAWN_TRAP,oid);
            setAbilityId(abilityId);
            setSize(size);
            setTime(time);
            setTargetType(targetType);
            setActivationTime(activationtime);
            setLoc(loc);
            setModel(model);
        }
        
		int abilityId = 0;
		int targetType = 0;
		float size = 1;
		float time = 0;
		float activationTime = 0;
		Point loc = null;
		String model="";
		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public Point getLoc() {
			return loc;
		}

		public void setLoc(Point loc) {
			this.loc = loc;
		}

		public float getSize() {
			return size;
		}

		public void setSize(float size) {
			this.size = size;
		}

		public float getTime() {
			return time;
		}

		public void setTime(float time) {
			this.time = time;
		}

		public float getActivationTime() {
			return activationTime;
		}

		public void setActivationTime(float activationTime) {
			this.activationTime = activationTime;
		}

		public int getAbilityId() {
			return abilityId;
		}

		public void setAbilityId(int abilityId) {
			this.abilityId = abilityId;
		}

		public int getTargetType() {
			return targetType;
		}

		public void setTargetType(int targetType) {
			this.targetType = targetType;
		}
        
        private static final long serialVersionUID = 1L;
    }
	
	public static void sendRevived(OID objOid) {
		RevivedMessage msg = new RevivedMessage(objOid);
		Log.debug("WorldManagerClient.sendRevived");
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static class RevivedMessage extends SubjectMessage {
		public RevivedMessage() {
			super(MSG_TYPE_REVIVE);
		}

		public RevivedMessage(OID objOid) {
			super(MSG_TYPE_REVIVE,objOid);
			setObjectOid(objOid);
			}
		
		 public OID getObjectOid() {
	            return objOid;
	        }
	        public void setObjectOid(OID val) {
	            this.objOid = val;
	        }
	        protected OID objOid = null;
		private static final long serialVersionUID = 1L;
	}
	
	public static Point sendFindNearestPoint(OID oid, Point point, OID InstanceOID) {
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_MOB_FIND_NEAREST_POINT, oid);
		msg.setProperty("point", point);
		msg.setProperty("instanceOID", InstanceOID);
		Log.debug("AgisWorldManagerClient.sendFindNearestPoint " +oid +" point "+point);
		return (Point) Engine.getAgent().sendRPCReturnObject(msg);
	}
	
	public static ArrayList<AOVector> sendGetPath(OID oid, Point point, OID InstanceOID) {
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_MOB_GET_PATH, oid);
		msg.setProperty("point", point);
		msg.setProperty("instanceOID", InstanceOID);
		Log.debug("AgisWorldManagerClient.sendGetPath " +oid +" point "+point);
		return (ArrayList<AOVector>) Engine.getAgent().sendRPCReturnObject(msg);
	}
	
	 public static final MessageType MSG_TYPE_SPAWN_TRAP = MessageType.intern("ao.SPAWN_TRAP");
	   
	public static MessageType MSG_TYPE_REVIVE = MessageType.intern("ao.REVIVE");
	    
    public static final MessageType MSG_TYPE_SET_MOVEMENT_STATE = MessageType.intern("ao.SET_MOVEMENT_STATE");
    public static final MessageType MSG_TYPE_WATER_REGION_TRANSITION = MessageType.intern("ao.WATER_REGION_TRANSITION");
    public static final MessageType MSG_TYPE_SET_UNDERWATER = MessageType.intern("ao.SET_UNDERWATER");
    public static final MessageType MSG_TYPE_CHANGE_INSTANCE = MessageType.intern("ao.CHANGE_INSTANCE");
    public static final MessageType MSG_TYPE_RETURN_TO_LAST_INSTANCE = MessageType.intern("ao.RETURN_TO_LAST_INSTANCE");
    public static final MessageType MSG_TYPE_SERVER_TIME = MessageType.intern("ao.SERVER_TIME");
    public static final MessageType MSG_TYPE_LOGOUT_REQUEST = MessageType.intern("ao.LOGOUT_REQUEST");
    public static final MessageType MSG_TYPE_CANCEL_LOGOUT_REQUEST = MessageType.intern("ao.CANCEL_LOGOUT_REQUEST");
    public static final MessageType MSG_TYPE_CHECK_IF_TARGETS_IN_AREA = MessageType.intern("ao.CHECK_IF_TARGETS_IN_AREA");
    
    public static final MessageType MSG_TYPE_GLOBAL_CHAT = MessageType.intern("ao.GLOBAL_CHAT");
    public static final MessageType MSG_TYPE_LEAVE_INSTANCE = MessageType.intern("ao.leaveInstance");
    public static final MessageType MSG_TYPE_CHANGE_INSTANCE_REQUEST = MessageType.intern("ao.CHANGE_INSTANCE_REQUEST");
    public static final MessageType MSG_TYPE_MOB_FIND_NEAREST_POINT = MessageType.intern("ao.MOB_FIND_NEAREST_POINT");
    public static final MessageType MSG_TYPE_MOB_GET_PATH = MessageType.intern("ao.MOB_GET_PATH");

}