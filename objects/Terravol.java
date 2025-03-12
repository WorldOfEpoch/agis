package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;

import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;

public class Terravol implements Serializable {
    public Terravol() {
    }
    
    public Terravol(String mapName, String instance, OID instanceOid) {
    	this.mapName = mapName;
    	this.instance = instance;
    	this.instanceOid = instanceOid;
    }
    
    public TerravolAction addChange(int id, AOVector position, AOVector size, int actionDataType, int brush, ArrayList<AOVector> affectedVirtualChunks,
			String blockName, float isoValueToAdd, boolean force) {
    	TerravolAction change = new TerravolAction(id, position, size, actionDataType, brush, affectedVirtualChunks,
    			blockName, isoValueToAdd, force);
    	changes.add(change);
    	return change;
    }
    
    public void sendAllChangesToPlayer(OID playerOid) {
    	for (TerravolAction action : changes) {
    		TargetedExtensionMessage actionMessage =
	                new TargetedExtensionMessage(playerOid, playerOid);
    		actionMessage.setExtensionType("terravol_action");
    		actionMessage.setProperty("map_name", mapName);
    		//actionMessage.setProperty("action_id", action.id);
    		actionMessage.setProperty("position", action.position);
    		actionMessage.setProperty("size", action.size);
    		actionMessage.setProperty("actionDataType", action.actionDataType);
    		actionMessage.setProperty("brush", action.brush);
    		/*actionMessage.setProperty("numAffectedChunks", action.affectedVirtualChunks.size());
        	for (int i = 0; i < action.affectedVirtualChunks.size(); i++) {
        		actionMessage.setProperty("affectedChunk" + i, action.affectedVirtualChunks.get(i));
        	}*/
    		actionMessage.setProperty("blockName", action.blockName);
    		actionMessage.setProperty("isoValueToAdd", action.isoValueToAdd);
    		actionMessage.setProperty("force", action.force);
    		Engine.getAgent().sendBroadcast(actionMessage);
    		Log.debug("TERRA: sent change message to: " + playerOid);
    	}
    }
    
    public void sendBlockChangeToSubscribers(TerravolAction action) {
    	for (OID oid : subscribers) {
    		TargetedExtensionMessage actionMessage =
    	                new TargetedExtensionMessage(oid, oid);
    		actionMessage.setExtensionType("terravol_action");
    		actionMessage.setProperty("map_name", mapName);
    		//actionMessage.setProperty("action_id", action.id);
    		actionMessage.setProperty("position", action.position);
    		actionMessage.setProperty("size", action.size);
    		actionMessage.setProperty("actionDataType", action.actionDataType);
    		actionMessage.setProperty("brush", action.brush);
    		/*actionMessage.setProperty("numAffectedChunks", action.affectedVirtualChunks.size());
            for (int i = 0; i < action.affectedVirtualChunks.size(); i++) {
            	actionMessage.setProperty("affectedChunk" + i, action.affectedVirtualChunks.get(i));
            }*/
            actionMessage.setProperty("blockName", action.blockName);
            actionMessage.setProperty("isoValueToAdd", action.isoValueToAdd);
            actionMessage.setProperty("force", action.force);
    	    Engine.getAgent().sendBroadcast(actionMessage);
    	    Log.debug("TERRA: sent change message to: " + oid);
    	}
    }
    
    public void addSubscriber(OID subscriber) {
    	subscribers.add(subscriber);
    }
    
    public void removeSubscriber(OID subscriber) {
    	subscribers.remove(subscriber);
    }

	public String getMapName() { return mapName;}
    public void setMapName(String mapName) {
    	this.mapName = mapName;
    }
    
    public String getInstance() { return instance;}
    public void setInstance(String instance) {
    	this.instance = instance;
    }
    
    public OID getOwner() { return instanceOid;}
    public void setOwner(OID instanceOid) {
    	this.instanceOid = instanceOid;
    }
    
    public ArrayList<TerravolAction> getChanges() { return changes;}
    public void setChanges(ArrayList<TerravolAction> changes) {
    	this.changes = changes;
    }
    
    public ArrayList<OID> getSubscribers() { return subscribers;}
    
    String mapName;
    String instance;
    OID instanceOid;
    ArrayList<TerravolAction> changes = new ArrayList<TerravolAction>();
    ArrayList<OID> subscribers = new ArrayList<OID>();
    
    public class TerravolAction {
    	
    	public int id;
    	public AOVector position;
    	public AOVector size;
    	public int actionDataType;
    	public int brush;
    	public ArrayList<AOVector> affectedVirtualChunks;
    	public String blockName;
    	public float isoValueToAdd;
    	public boolean force;
    	
    	public TerravolAction(int id, AOVector position, AOVector size, int actionDataType, int brush, ArrayList<AOVector> affectedVirtualChunks,
    			String blockName, float isoValueToAdd, boolean force) {
    		this.id = id;
    		this.position = position;
    		this.size = size;
    		this.actionDataType = actionDataType;
    		this.brush = brush;
    		this.affectedVirtualChunks = affectedVirtualChunks;
    		this.blockName = blockName;
    		this.isoValueToAdd = isoValueToAdd;
    		this.force = force;
    	}
    }

    private static final long serialVersionUID = 1L;
}
