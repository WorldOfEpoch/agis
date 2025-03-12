package atavism.agis.plugins;

import java.util.HashMap;
import java.util.LinkedList;

import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.messages.PropertyMessage;

//
// client for sending/getting messages to the FactionPlugin
//
public class FactionClient {
    private FactionClient() {
    }
    
    // get a mobs reaction towards a player
    public static void getAttitude(OID oid, OID targetOid) {
    	getAttitudeMessage msg = new getAttitudeMessage(oid, targetOid);
        Engine.getAgent().sendBroadcast(msg);
    }
	
    public static int getStance(OID oid, OID targetOid) {
    	getStanceMessage msg = new getStanceMessage(oid, targetOid);
    	 int returnStance = Engine.getAgent().sendRPCReturnInt(msg);
         return returnStance;
    }
    
    
    public static int getStance(OID oid, int stance) {
    	getStanceMessage msg = new getStanceMessage(oid, stance);
        int returnStance = Engine.getAgent().sendRPCReturnInt(msg);
        return returnStance;
    }
  
    public static HashMap<OID,Integer> getStance(OID oid, LinkedList<OID> targetOids) {
    	if(Log.loggingDebug)
    		Log.debug("FactionClient.getStance oid="+oid+" targets="+targetOids);
    	getStanceMessage msg = new getStanceMessage(oid, targetOids);
    	HashMap<OID,Integer> returnStance = (HashMap<OID,Integer>)Engine.getAgent().sendRPCReturnObject(msg);
         return returnStance;
    }
  
	public static class getAttitudeMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getAttitudeMessage() {
            super();
        }
        
        public getAttitudeMessage(OID oid, OID targetOid) {
        	super(oid);
        	setProperty("target", targetOid);
        	setMsgType(MSG_TYPE_GET_REACTION);
        	Log.debug("COMBAT CLIENT: getAttitudeMessage hit 1");
        }
	}
	
	public static class getStanceMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getStanceMessage() {
            super();
        }
        public getStanceMessage(OID oid, LinkedList<OID> targetOid) {
        	super(oid);
        	setTargetOids(targetOid);
        	setMsgType(MSG_TYPE_GET_STANCE_TARGETS);
        	Log.debug("FactionClient: getStanceMessage  targetOids hit 1");
        }
    
        
        public getStanceMessage(OID oid, OID targetOid) {
        	super(oid);
        	setTargetOid(targetOid);
        	setMsgType(MSG_TYPE_GET_STANCE);
        	Log.debug("FactionClient: getStanceMessage  targetOid hit 1");
        }
        public getStanceMessage(OID oid, int faction) {
        	super(oid);
        	setFaction(faction);
        	setMsgType(MSG_TYPE_GET_STANCE);
        	Log.debug("FactionClient: getStanceMessage Faction stance");
        }
        public void setFaction(int faction) {
        	this.faction = faction;
        }
        public int getFaction() {
        	return faction;
        }
        int faction;
        
        public void setTargetOid(OID targetOid) {
        	this.targetOid = targetOid;
        }
        public OID getTargetOid() {
        	return targetOid;
        }
  
        OID targetOid;
        
        public void setTargetOids( LinkedList<OID> targetOids) {
        	this.targetOids = targetOids;
        }
        public  LinkedList<OID> getTargetOids() {
        	return targetOids;
        }
        
        LinkedList<OID> targetOids;
        
	}
	
	
	// get a mobs reaction towards a player
    public static void alterReputation(OID oid, int faction, int repChange) {
    	AlterReputationMessage msg = new AlterReputationMessage(oid, faction, repChange);
        Engine.getAgent().sendBroadcast(msg);
    }
	
	public static class AlterReputationMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public AlterReputationMessage() {
            super();
        }
        
        public AlterReputationMessage(OID oid, int faction, int repChange) {
        	super(oid);
        	setFaction(faction);
        	setRepChange(repChange);
        	setMsgType(MSG_TYPE_ALTER_REPUTATION);
        	Log.debug("FACTION CLIENT: AlterReputationMessage hit 1");
        }
        
        public void setFaction(int faction) {
        	this.faction = faction;
        }
        public int getFaction() {
        	return faction;
        }
        int faction;
        
        public void setRepChange(int repChange) {
        	this.repChange = repChange;
        }
        public int getRepChange() {
        	return repChange;
        }
        int repChange;
	}
	
	public static final MessageType MSG_TYPE_SAVE_PVP_TIMER = MessageType.intern("faction.SAVE_PVP_TIMER");
	public static final MessageType MSG_TYPE_GET_REACTION = MessageType.intern("faction.GET_REACTION");
	public static final MessageType MSG_TYPE_GET_STANCE = MessageType.intern("faction.GET_STANCE");
	public static final MessageType MSG_TYPE_GET_STANCE_TARGETS = MessageType.intern("faction.GET_STANCE_TARGETS");
	public static final MessageType MSG_TYPE_ALTER_REPUTATION = MessageType.intern("faction.ALTER_REPUTATION");
	public static final MessageType MSG_TYPE_UPDATE_PVP_STATE = MessageType.intern("faction.UPDATE_PVP_STATE");

}