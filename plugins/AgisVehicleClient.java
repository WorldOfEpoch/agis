package atavism.agis.plugins;

import atavism.agis.behaviors.BaseBehavior;
import atavism.agis.behaviors.ChestBehavior;
import atavism.agis.behaviors.CombatBehavior;
import atavism.agis.behaviors.NpcBehavior;
import atavism.agis.behaviors.OpenBehavior;
import atavism.agis.behaviors.PatrolBehavior;
import atavism.agis.behaviors.PlayerCorpseBehavior;
import atavism.agis.behaviors.RadiusRoamBehavior;
import atavism.agis.behaviors.ShopBehavior;
import atavism.agis.core.Agis;
import atavism.agis.objects.AgisGroup;
import atavism.agis.objects.AgisGroupMember;
import atavism.agis.objects.AgisVehicle;
import atavism.agis.objects.BehaviorTemplate;
import atavism.agis.objects.FactionStateInfo;
import atavism.agis.objects.LootTable;
import atavism.agis.plugins.ChatClient.TargetedComReqMessage;
import atavism.agis.plugins.GroupClient.GroupEventType;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.*;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.InstanceTemplate;
import atavism.server.objects.ObjectFactory;
import atavism.server.objects.SpawnData;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;

import atavism.server.util.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

public class AgisVehicleClient {

	private static final Logger log = new Logger("AgisVehiclePlugin");

	
    public static void spawnVehicle(OID playerOid, String vehicleType, Serializable location) {
    	SpawnedMessage msg = new SpawnedMessage(playerOid, vehicleType, location);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static class SpawnedMessage extends PropertyMessage {
        public int spawnDataID;
		public AgisVehicle spawnData;
		public Point location;
		public Quaternion orientation;
		
    	private static final long serialVersionUID = 1L;
    	public SpawnData sd;
    	public OID instanceOid;
    	public boolean oneOffSpawn = false;
    	

		public SpawnedMessage(OID playerOid, String vehicleType, Serializable location) {
            super(MSG_TYPE_SPAWN_VEHICLE, playerOid);
            setProperty("vehicleType", vehicleType);
            setProperty("location", location);
        }
		
    	public SpawnedMessage() {
    		super(MSG_TYPE_SPAWN_VEHICLE);
    	}
    	
    	public SpawnedMessage(SpawnData sd) {
    		super(MSG_TYPE_SPAWN_VEHICLE);
    		Log.error("AgisVehicleClient: SpawnedMessage instance="+sd.getInstanceOid());
    		this.setSubject(sd.getInstanceOid());
    		this.sd = sd;
    	}
    	
    	public SpawnedMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid) {
    		super(MSG_TYPE_SPAWN_VEHICLE);
    		this.spawnDataID = spawnDataID;
    		this.location = loc;
    		this.orientation = orient;
    		this.instanceOid = instanceOid;
    		this.setSubject(instanceOid);
    	}
    	
    	public SpawnedMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid, boolean oneOff) {
    		super(MSG_TYPE_SPAWN_VEHICLE);
    		this.spawnDataID = spawnDataID;
    		this.location = loc;
    		this.orientation = orient;
    		this.instanceOid = instanceOid;
    		this.oneOffSpawn = oneOff;
    		this.setSubject(instanceOid);
    	}

    }
    
    
    public static void SendGroupEventMessage(GroupEventType eventType, AgisGroup group, OID subjectOid) {
        //Set Message to send to each group member
        AgisGroupMember subject = group.GetGroupMember(subjectOid);
        if(subject != null){
            String message = subject.GetGroupMemberName();
            switch(eventType){
            case JOINED:
                message += " has entered the vehicle.";
                break;
            case LEFT:
                message += " has left the vehicle.";
                break;
            case LEADERCHANGED:
                message += " is now the vehicle driver.";
                break;
            }

            MessageAgent agent = Engine.getAgent();
            TargetedComReqMessage groupEventMessage = new TargetedComReqMessage();
            groupEventMessage.setString(message);
            groupEventMessage.setChannel(4); //Group channel
            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();
            for (AgisGroupMember groupMember : groupMembers) {
                if (!groupMember.GetGroupMemberOid().equals(subjectOid) || eventType.equals(GroupEventType.LEADERCHANGED)) {
                    groupEventMessage.setSubject(groupMember.GetGroupMemberOid());
                    groupEventMessage.setTarget(groupMember.GetGroupMemberOid());
                    agent.sendBroadcast(groupEventMessage);
                }
            }
        } else {
			Log.error("AgisVehicleClient: GroupClient.SendGroupEventMessage - AgisGroup.GetGroupMember(" + subjectOid + ") returned null object");
	     }
    }

    
    public static void updatePlayerLocation(OID vehicleOid, Serializable location) {
        UpdateLocationMessage msg = new UpdateLocationMessage(vehicleOid, location);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void updatePassengerCount(OID vehicleOid, int passengerCount) {
        UpdatePassengerCountMessage msg = new UpdatePassengerCountMessage(vehicleOid, passengerCount);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void controlVehicleMovement(OID vehicleOid, Serializable movementCommand) {
        ControlVehicleMovementMessage msg = new ControlVehicleMovementMessage(vehicleOid, movementCommand);
        Engine.getAgent().sendBroadcast(msg);
    }


    // Inner class for UpdateLocationMessage
    public static class UpdateLocationMessage extends PropertyMessage {
        public UpdateLocationMessage(OID vehicleOid, Serializable location) {
            super(MSG_TYPE_UPDATE_LOCATION, vehicleOid);
            setProperty("location", location);
        }
        private static final long serialVersionUID = 1L;
    }

    // Inner class for UpdatePassengerCountMessage
    public static class UpdatePassengerCountMessage extends PropertyMessage {
        public UpdatePassengerCountMessage(OID vehicleOid, int passengerCount) {
            super(MSG_TYPE_UPDATE_PASSENGER_COUNT, vehicleOid);
            setProperty("passengerCount", passengerCount);
        }
        private static final long serialVersionUID = 1L;
    }

    public static class ControlVehicleMovementMessage extends PropertyMessage {
        private OID vehicleOid;
        private Serializable movementCommand;

        public ControlVehicleMovementMessage(OID vehicleOid, Serializable movementCommand) {
            super(MSG_TYPE_CONTROL_VEHICLE_MOVEMENT, vehicleOid);
            this.vehicleOid = vehicleOid;
            this.movementCommand = movementCommand;
            setProperty("movementCommand", movementCommand);
        }

        public OID getVehicleOid() {
            return vehicleOid;
        }

        public Serializable getMovementCommand() {
            return movementCommand;
        }

        private static final long serialVersionUID = 1L;
    }

    
    public static void enterVehicle(OID vehicleOid, OID passengerOid) {
        EnterVehicleMessage msg = new EnterVehicleMessage(vehicleOid, passengerOid);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void exitVehicle(OID vehicleOid, OID passengerOid) {
        ExitVehicleMessage msg = new ExitVehicleMessage(vehicleOid, passengerOid);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void updateVehiclePassengersLocation(OID vehicleOid, Serializable newLocation) {
        UpdateVehiclePassengersLocationMessage msg = new UpdateVehiclePassengersLocationMessage(vehicleOid, newLocation);
        Engine.getAgent().sendBroadcast(msg);
    }

    // Inner class for EnterVehicleMessage
    public static class EnterVehicleMessage extends PropertyMessage {
        public EnterVehicleMessage(OID vehicleOid, OID passengerOid) {
            super(MSG_TYPE_ENTER_VEHICLE, vehicleOid);
            setProperty("passengerOid", passengerOid);
        }
        private static final long serialVersionUID = 1L;
		public OID getVehicleOid() {
			// TODO Auto-generated method stub
			return null;
		}
		public OID getPassengerOid() {
			// TODO Auto-generated method stub
			return null;
		}
    }


    // Inner class for ExitVehicleMessage
    public static class ExitVehicleMessage extends PropertyMessage {
        public ExitVehicleMessage(OID vehicleOid, OID passengerOid) {
            super(MSG_TYPE_EXIT_VEHICLE, vehicleOid);
            setProperty("passengerOid", passengerOid);
        }
        private static final long serialVersionUID = 1L;
		public OID getVehicleOid() {
			// TODO Auto-generated method stub
			return null;
		}
		public OID getPassengerOid() {
			// TODO Auto-generated method stub
			return null;
		}
    }

    // Inner class for UpdateVehiclePassengersLocationMessage
    public static class UpdateVehiclePassengersLocationMessage extends PropertyMessage {
        public UpdateVehiclePassengersLocationMessage(OID vehicleOid, Serializable newLocation) {
            super(MSG_TYPE_UPDATE_PASSENGERS_LOCATION, vehicleOid);
            setProperty("newLocation", newLocation);
        }
        private static final long serialVersionUID = 1L;
    }

    public static void setDriver(OID vehicleOid, OID driverOid) {
        SetDriverMessage msg = new SetDriverMessage(vehicleOid, driverOid);
        Engine.getAgent().sendBroadcast(msg);
    }


    // Method to send a message to start driving a vehicle
    public static void driveVehicle(OID vehicleOid) {
        DriveVehicleMessage msg = new DriveVehicleMessage(vehicleOid);
        Engine.getAgent().sendBroadcast(msg);
    }


    public static void stopVehicle(OID vehicleOid) {
        StopVehicleMessage msg = new StopVehicleMessage(vehicleOid);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    
    // Inner class for SetDriverMessage
    public static class SetDriverMessage extends PropertyMessage {
        public SetDriverMessage(OID vehicleOid, OID driverOid) {
            super(MSG_TYPE_SET_DRIVER, vehicleOid);
            setProperty("driverOid", driverOid);
        }
        
        private static final long serialVersionUID = 1L;
    }

    // Inner class for DriveVehicleMessage
    public static class DriveVehicleMessage extends PropertyMessage {
        public DriveVehicleMessage(OID vehicleOid) {
            super(MSG_TYPE_DRIVE_VEHICLE, vehicleOid);
        }
        private static final long serialVersionUID = 1L;
    }


    // Inner class for StopVehicleMessage
    public static class StopVehicleMessage extends PropertyMessage {
        public StopVehicleMessage(OID vehicleOid) {
            super(MSG_TYPE_STOP_VEHICLE, vehicleOid);
        }
        private static final long serialVersionUID = 1L;
    }
    

    public static boolean createSpawnGenerator(SpawnData spawnData) {
         Log.error("AgisVehicleClient: createSpawnGenerator " + spawnData);
         CreateSpawnGeneratorMessage message = new CreateSpawnGeneratorMessage(spawnData);
         
         return Engine.getAgent().sendRPCReturnBoolean((Message)message).booleanValue();
       }
    
    	public static class CreateSpawnGeneratorMessage extends SubjectMessage { 
	
	     private  final long serialVersionUID = 1L;
		 	private SpawnData spawnData;

	     public void CreateSpawnGeneratorMessage() {}
	     
			public CreateSpawnGeneratorMessage(SpawnData spawnData) {
	         super(AgisVehicleClient.MSG_TYPE_CREATE_VEHICLE_SPAWN);
	         this.spawnData = spawnData;
	         setSubject(spawnData.getInstanceOid());
	         Log.error("AgisVehicleClient: CreateSpawnGeneratorMessage InstanceOid=" + spawnData.getInstanceOid());
	     }
	     
	     public void setSubject(OID instanceOid) {}
	
	
		public SpawnData getSpawnData() {
	       return this.spawnData;
	     }
	     
	     public void setSpawnData(SpawnData spawnData) {
	       this.spawnData = spawnData;
	     }
	}

	   
	public static boolean deleteSpawnGenerator(OID instanceOid, int spawnerId) {
		Log.error("AgisVehicleClient: deleteSpawnGenerator instanceOid=" + instanceOid + " spawnerId=" + spawnerId);
		    DeleteSpawnGeneratorMessage message = new DeleteSpawnGeneratorMessage(instanceOid, spawnerId);
		    
		    return Engine.getAgent().sendRPCReturnBoolean((Message)message).booleanValue();
		  }
		  public static class DeleteSpawnGeneratorMessage extends SubjectMessage { private int spawnerId;
		    private static final long serialVersionUID = 1L;
		    
		    public DeleteSpawnGeneratorMessage() {}
		    
		    public DeleteSpawnGeneratorMessage(OID instanceOid, int spawnerId) {
		      super(AgisVehicleClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN);
		      Log.error("AgisVehicleClient: DeleteSpawnGeneratorMessage InstanceOid=" + instanceOid);
		  
		  setSpawnId(spawnerId);
		  setSubject(instanceOid);
		}
		
		public int getSpawnId() {
		  return this.spawnerId;
		}
		
		public void setSpawnId(int spawnerId) {
		  this.spawnerId = spawnerId;
		}
	} 
		  
	public static Template getVehicleTemplate(int templateID) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
    /**
     * SpawnVehicleMessage is used to request the spawning of a single vehicle.
     */
    public static class SpawnVehicleMessage extends SubjectMessage {
        public int spawnDataID;
        public SpawnData sd;
        public Point loc;
        public Quaternion orient;
        public OID instanceOid;
        public boolean oneOffSpawn = false;

        public SpawnVehicleMessage() {
            super(MSG_TYPE_SPAWN_VEHICLE);
        }

        public SpawnVehicleMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid) {
            super(MSG_TYPE_SPAWN_VEHICLE);
            this.spawnDataID = spawnDataID;
            this.loc = loc;
            this.orient = orient;
            this.instanceOid = instanceOid;
            this.setSubject(instanceOid);
        }

        public SpawnVehicleMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid, boolean oneOffSpawn) {
            super(MSG_TYPE_SPAWN_VEHICLE);
            this.spawnDataID = spawnDataID;
            this.loc = loc;
            this.orient = orient;
            this.instanceOid = instanceOid;
            this.oneOffSpawn = oneOffSpawn;
            this.setSubject(instanceOid);
        }
    }

    /**
     * SpawnInstanceVehiclesMessage is sent when an instance loads and should
     * schedule vehicle spawns based on instance-specific templates.
     */
    public static class SpawnInstanceVehiclesMessage extends SubjectMessage {
        public InstanceTemplate tmpl;
        public OID instanceOid;

        public SpawnInstanceVehiclesMessage() {
            super(MSG_TYPE_SPAWN_INSTANCE_VEHICLES);
        }

        public SpawnInstanceVehiclesMessage(InstanceTemplate tmpl, OID instanceOid) {
            super(MSG_TYPE_SPAWN_INSTANCE_VEHICLES);
            this.tmpl = tmpl;
            this.instanceOid = instanceOid;
            this.setSubject(instanceOid);
        }
    }

    /**
     * LoadInstanceVehiclesMessage is used to load vehicle-specific objects (markers,
     * navmesh data, etc.) when an instance is loaded.
     */
    public static class LoadInstanceVehiclesMessage extends SubjectMessage {
        public OID instanceOid;

        public LoadInstanceVehiclesMessage() {
            super(MSG_TYPE_LOAD_INSTANCE_VEHICLES);
        }

        public LoadInstanceVehiclesMessage(OID instanceOid) {
            super(MSG_TYPE_LOAD_INSTANCE_VEHICLES);
            this.instanceOid = instanceOid;
            this.setSubject(instanceOid);
        }
    }

    /**
     * InteractWithVehicleMessage is used to signal an interaction with a vehicle.
     * For example, a player boarding or exiting a vehicle.
     */
    public static class InteractWithVehicleMessage extends SubjectMessage {
        private OID vehicleOid;
        private String action; // e.g., "board", "exit", etc.

        public InteractWithVehicleMessage() {
            super(MSG_TYPE_INTERACT_WITH_VEHICLE);
        }

        public InteractWithVehicleMessage(OID vehicleOid, String action) {
            super(MSG_TYPE_INTERACT_WITH_VEHICLE, vehicleOid);
            this.vehicleOid = vehicleOid;
            this.action = action;
        }

        public OID getVehicleOid() {
            return vehicleOid;
        }

        public String getAction() {
            return action;
        }
    }



    public static void spawnInstanceVehicles(InstanceTemplate tmpl, OID instanceOid) {
        SpawnInstanceVehiclesMessage msg = new SpawnInstanceVehiclesMessage(tmpl, instanceOid);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void loadInstanceVehicles(OID instanceOid) {
        LoadInstanceVehiclesMessage msg = new LoadInstanceVehiclesMessage(instanceOid);
        Engine.getAgent().sendBroadcast(msg);
    }

    public static void interactWithVehicle(OID vehicleOid, String action) {
        InteractWithVehicleMessage msg = new InteractWithVehicleMessage(vehicleOid, action);
        Engine.getAgent().sendBroadcast(msg);
    }
	
	
	
	public static Namespace NAMESPACE = null;
	

	
	public static final MessageType MSG_TYPE_REQUEST_DEVELOPER_ACCESS = MessageType.intern("ao.REQUEST_DEVELOPER_ACCESS");
	public static final MessageType MSG_TYPE_VEHICLE_GET_ACTOR_SPEED = MessageType.intern("ao.GET_ACTOR_SPEED");

	public static final MessageType MSG_TYPE_ENTER_VEHICLE = MessageType.intern("vehicle.ENTER_VEHICLE");
    public static final MessageType MSG_TYPE_EXIT_VEHICLE = MessageType.intern("vehicle.EXIT_VEHICLE");
    public static final MessageType MSG_TYPE_UPDATE_PASSENGERS_LOCATION = MessageType.intern("vehicle.UPDATE_PASSENGERS_LOCATION");
    public static final MessageType MSG_TYPE_CREATE_VEHICLE_SPAWN = MessageType.intern("vehicle.CREATE_VEHICLE_SPAWN");
    public static final MessageType MSG_TYPE_REMOVE_VEHICLE_SPAWN = MessageType.intern("vehicle.REMOVE_VEHICLE_SPAWN");
	public static final MessageType MSG_TYPE_VERIFY_ISLAND_ACCESS = MessageType.intern("ao.VERIFY_ISLAND_ACCESS");

    // Add more methods for other vehicle interactions like moving, stopping, entering, exiting etc.
    public static final MessageType MSG_TYPE_UPDATE_LOCATION = MessageType.intern("vehicle.UPDATE_LOCATION");
    public static final MessageType MSG_TYPE_UPDATE_PASSENGER_COUNT = MessageType.intern("vehicle.UPDATE_PASSENGER_COUNT");
    public static final MessageType MSG_TYPE_CONTROL_VEHICLE_MOVEMENT = MessageType.intern("vehicle.CONTROL_VEHICLE_MOVEMENT");
    public static final MessageType MSG_TYPE_SPAWN_VEHICLE = MessageType.intern("vehicle.SPAWN_VEHICLE");
    public static final MessageType MSG_TYPE_DESPAWNED_VEHICLE = MessageType.intern("vehicle.DESPAWNED_VEHICLE");
    
    // Define message type constants
    public static final MessageType MSG_TYPE_SET_DRIVER = MessageType.intern("vehicle.SET_DRIVER");
    public static final MessageType MSG_TYPE_DRIVE_VEHICLE = MessageType.intern("vehicle.DRIVE_VEHICLE");
    public static final MessageType MSG_TYPE_STOP_VEHICLE = MessageType.intern("vehicle.STOP_VEHICLE");
    public static final MessageType MSG_TYPE_SPAWN_INSTANCE_VEHICLES = MessageType.intern("vehicle.SPAWN_INSTANCE_VEHICLES");
    public static final MessageType MSG_TYPE_LOAD_INSTANCE_VEHICLES = MessageType.intern("vehicle.LOAD_INSTANCE_VEHICLES");
    public static final MessageType MSG_TYPE_INTERACT_WITH_VEHICLE = MessageType.intern("vehicle.INTERACT_WITH_VEHICLE");
    public static final MessageType MSG_TYPE_DEBUG_VEHICLE = MessageType.intern("ao.DEBUG_MOB");




}
