package atavism.agis.objects;
import java.util.function.Supplier;


import java.util.LinkedList;
import java.io.*;

import atavism.server.engine.Behavior;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.objects.ObjectFactory;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.SpawnData;
import atavism.server.objects.Template;
import atavism.server.util.Log;
import java.util.function.BiFunction;

public class MobFactory extends ObjectFactory implements Serializable {
	public MobFactory(int templateID) {
		super(templateID);
	}
		
    public ObjectStub makeObject(SpawnData spawnData, OID instanceOid, Point loc) {
        ObjectStub obj = super.makeObject(spawnData, instanceOid, loc);

        Log.debug("MOBFACTORY: makeObject; adding behaviors via suppliers.");
        for (BiFunction<ObjectStub, SpawnData, Behavior> behavSupplier : behavSuppliers) {
            Behavior behav = behavSupplier.apply(obj, spawnData);
            obj.addBehavior(behav);
            Log.debug("MOBFACTORY: makeObject; added new behavior instance: " + behav);
        }
		
        return obj;
    }

    public ObjectStub makeObject(SpawnData spawnData, OID instanceOid, Point loc, Template override) {
        ObjectStub obj = super.makeObject(spawnData, instanceOid, loc, override);

        Log.debug("MOBFACTORY: makeObject; adding behaviors via suppliers.");
        for (BiFunction<ObjectStub, SpawnData, Behavior> behavSupplier : behavSuppliers) {
            Behavior behav = behavSupplier.apply(obj, spawnData);
            obj.addBehavior(behav);
            Log.debug("MOBFACTORY: makeObject; added new behavior instance: " + behav);
        }

        return obj;
    }

    public void addBehavSupplier(BiFunction<ObjectStub, SpawnData, Behavior> behavSupplier) {
        behavSuppliers.add(behavSupplier);
    }

    private LinkedList<BiFunction<ObjectStub, SpawnData, Behavior>> behavSuppliers = new LinkedList<>();
		
	public void addBehav(Behavior behav) {
		behavs.add(behav);
	}
	public void setBehavs(LinkedList<Behavior> behavs) {
		this.behavs = behavs;
	}
	public LinkedList<Behavior> getBehavs() {
		return behavs;
	}
	private LinkedList<Behavior> behavs = new LinkedList<Behavior>();
		
	private static final long serialVersionUID = 1L;



		
	
}
