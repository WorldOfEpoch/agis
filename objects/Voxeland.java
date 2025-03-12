package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;

import atavism.server.engine.OID;

public class Voxeland implements Serializable {
    public Voxeland() {
    }
    
    public Voxeland(int id, String instance, OID instanceOid) {
    	this.id = id;
    	this.instance = instance;
    	this.instanceOid = instanceOid;
    }
    
    public void addChange(int x, int y, int z, int type) {
    	VoxelandChange change = new VoxelandChange(x, y, z, type);
    	changes.add(change);
    }
    
    public void addSubscriber(OID subscriber) {
    	subscribers.add(subscriber);
    }
    
    public void removeSubscriber(OID subscriber) {
    	subscribers.remove(subscriber);
    }

	public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getInstance() { return instance;}
    public void setInstance(String instance) {
    	this.instance = instance;
    }
    
    public OID getOwner() { return instanceOid;}
    public void setOwner(OID instanceOid) {
    	this.instanceOid = instanceOid;
    }
    
    public ArrayList<VoxelandChange> getChanges() { return changes;}
    public void setChanges(ArrayList<VoxelandChange> changes) {
    	this.changes = changes;
    }
    
    public ArrayList<OID> getSubscribers() { return subscribers;}
    
    int id;
    String instance;
    OID instanceOid;
    ArrayList<VoxelandChange> changes = new ArrayList<VoxelandChange>();
    ArrayList<OID> subscribers = new ArrayList<OID>();
    
    public class VoxelandChange {
    	public int x;
    	public int y;
    	public int z;
    	public int type;
    	
    	public VoxelandChange(int x, int y, int z, int type) {
    		this.x = x;
    		this.y = y;
    		this.z = z;
    		this.type = type;
    	}
    }

    private static final long serialVersionUID = 1L;
}
