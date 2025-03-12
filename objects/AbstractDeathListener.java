package atavism.agis.objects;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.util.*;
import atavism.agis.events.*;

import java.rmi.*;

public abstract class AbstractDeathListener extends AbstractEventListener {

    public AbstractDeathListener() throws RemoteException {
	super();
    }

    public AbstractDeathListener(String name) throws RemoteException {
	super();
	this.name = name;
    }

    protected String name = "";

    public String getName() {
	return name;
    }

    protected boolean isDead = false;

    // handleDeath is called when the mob the listener is attached to dies
    protected abstract void handleDeath(Event event, AOObject target);
   
    // handleEvent will be called by multiple threads, so you must
    // make it thread-safe
    public void handleEvent(Event event, AOObject target) {
	AgisStateEvent stateEvent = (AgisStateEvent)event;
	OID eventObjOid = stateEvent.getObjectOid();
        if (Log.loggingDebug)
            Log.debug("AbstractDeathListener: handleEvent target=" + target + " eventobj=" + eventObjOid);
	if (eventObjOid.equals(target.getOid())) {
	    Integer dead = stateEvent.getStateMap().get(AgisStates.Dead);
	    if (dead != null) {
		if ((dead == 1) && !isDead) {
		    isDead = true;
		    Log.debug("AbstractDeathListener: handleEvent object is dead");
		    handleDeath(event, target);
		}
	    }
	}
    }
}