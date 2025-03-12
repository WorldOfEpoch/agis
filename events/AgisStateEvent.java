package atavism.agis.events;

import atavism.server.events.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.objects.*;

public class AgisStateEvent extends StateEvent {
    public AgisStateEvent() {
	super();
    }

    public AgisStateEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AgisStateEvent(AgisMob agisMob, boolean fullState) {
	super();
	setObject(agisMob);
	if (fullState) {
	    addState(AgisStates.Dead.toString(), (agisMob.isDead() ? 1 : 0));

            boolean combatState = (agisMob.getAutoAttackTarget() != null);
	    addState(AgisStates.Combat.toString(), (combatState ? 1 : 0));
            addState(AgisStates.Attackable.toString(),
		     (agisMob.attackable() ? 1 : 0));
            addState(AgisStates.Stunned.toString(),
		     (agisMob.isStunned() ? 1 : 0));

            if (Log.loggingDebug)
                Log.debug("AgisStateEvent: added state of mob " +
                          agisMob.getName() + 
                          ", deadstate=" + (agisMob.isDead() ? 1 : 0) +
                          ", combatState=" + combatState);
	}
    }

    public String getName() {
	return "AgisStateEvent";
    }
}