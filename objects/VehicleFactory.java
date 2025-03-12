package atavism.agis.objects;

import java.util.LinkedList;
import java.util.function.BiFunction;
import java.io.*;

import atavism.server.engine.Behavior;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.ObjectFactory;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.SpawnData;
import atavism.server.objects.Template;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

public class VehicleFactory extends ObjectFactory implements Serializable {
	public VehicleFactory(int templateID) {
		super(templateID);
	}
		
	public ObjectStub makeObject(SpawnData spawnData, OID instanceOid, Point loc) {
		ObjectStub obj = super.makeVehicleObject(spawnData, instanceOid, loc);
	        
		Log.error("VehicleFactory: makeObject; adding behavs: " + behavs);
		for (Behavior behav: behavs) {
			if (!obj.getBehaviors().contains(behav)) {
				obj.addBehavior(behav);
				Log.error("VehicleFactory: makeObject; adding behav: " + behav);
			}
		}

	    return obj;
	}
		
	public void addBehav(Behavior behav) {
		behavs.add(behav);
	}
	
	public void setBehavs(LinkedList<Behavior> behavs) {
		this.behavs = behavs;
	}
	
	public LinkedList<Behavior> getBehavs() {
		return behavs;
	}
	

    public void addBehavSupplier(BiFunction<ObjectStub, SpawnData, Behavior> behavSupplier) {
        behavSuppliers.add(behavSupplier);
    }
	
    private LinkedList<BiFunction<ObjectStub, SpawnData, Behavior>> behavSuppliers = new LinkedList<>();

   
	private LinkedList<Behavior> behavs = new LinkedList<Behavior>();
		
	private static final long serialVersionUID = 1L;
}

