package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;

import atavism.agis.arenas.*;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;

public class ArenaFlagPlatform extends ArenaObject {
    public ArenaFlagPlatform() {
    }
    
    public ArenaFlagPlatform(int id, Point loc, OID instanceOID, String objectType, DisplayContext dc, 
    		HashMap<String, Serializable> props) {
    	this(id, loc, new Quaternion(0, 0, 0, 1), instanceOID, objectType, dc, props);
    }
    
    public ArenaFlagPlatform(int id, Point loc, Quaternion orientation, OID instanceOID, String objectType, 
    		DisplayContext dc, HashMap<String, Serializable> props) {
    	this.id = id;
    	this.loc = loc;
    	this.orientation = orientation;
    	this.props = props;
    	this.instanceOID = instanceOID;
    	this.objectType = objectType;
    	this.dc = dc;
    	this.teamToReactTo = -1;
    	this.flag = null;
    	spawn();
    }
    
    public ArenaFlag spawnFlag(CaptureTheFlagArena arena, int team) {
    	flag = new ArenaFlag(id, loc, instanceOID, ArenaObject.ARENA_OBJECT_FLAG, null, team, false, arena);
    	return flag;
    }
    
    public void flagTaken() {
    	flag = null;
    }
    
    public boolean hasFlag() {
    	if (flag == null)
    		return false;
    	return true;
    }

    private ArenaFlag flag;
    
    private static final long serialVersionUID = 1L;
}
