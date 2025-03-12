package atavism.agis.events;

import atavism.server.engine.*;
import atavism.agis.objects.*;
import atavism.server.network.*;

public abstract class AgisEvent extends Event {
    public AgisEvent() {
	super();
    }

    public AgisEvent(AgisObject obj) {
	super();
	setObject(obj);
    }

    public AgisEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

}
