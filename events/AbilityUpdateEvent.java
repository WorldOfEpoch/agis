package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import java.util.*;
import java.util.concurrent.locks.*;

// Update the list of abilities that this object knows

/**
 * Don't think this class is ever used. It is all handled by the CombatInfo class instead.
 *
 */
public class AbilityUpdateEvent extends Event {
    public AbilityUpdateEvent() {
	super();
    }

    public AbilityUpdateEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AbilityUpdateEvent(AgisObject obj) {
	super(obj);
	setObjOid(obj.getOid());
	/*for(AgisAbility.Entry entry : obj.getAbilityMap().values()) {
	    addAbilityEntry(entry);
	}*/
    }

    public String getName() {
	return "AbilityUpdateEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());
	AOByteBuffer buf = new AOByteBuffer(500);

        lock.lock();
        try {
	    buf.putOID(objOid);
	    buf.putInt(msgId);
	
	    int size = abilityEntrySet.size();
	    buf.putInt(size);
	    for(AgisAbility.Entry entry : abilityEntrySet) {
		buf.putString(entry.getAbilityName());
		buf.putString(entry.getIcon());
		buf.putString(entry.getCategory());
	    }
        }
        finally {
            lock.unlock();
        }

	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
        lock.lock();
        try {
	    buf.rewind();

	    setObjOid(buf.getOID());
	    /* int msgId = */ buf.getInt();

            int size = buf.getInt();
	    abilityEntrySet = new HashSet<AgisAbility.Entry>(size);
	    while (size-- > 0) {
		String name = buf.getString();
		String icon = buf.getString();
		String category = buf.getString();
		addAbilityEntry(new AgisAbility.Entry(name, icon, category));
	    }
        }
        finally {
            lock.unlock();
        }
    }

    public OID getObjOid() { return objOid; }
    public void setObjOid(OID oid) { objOid = oid; }
    protected OID objOid;

    public void addAbilityEntry(AgisAbility.Entry entry) {
	lock.lock();
	try {
	    abilityEntrySet.add(entry);
	}
	finally {
	    lock.unlock();
	}
    }
    public Set<AgisAbility.Entry> getAbilityEntrySet() {
	lock.lock();
	try {
	    return new HashSet<AgisAbility.Entry>(abilityEntrySet);
	}
	finally {
	    lock.unlock();
	}
    }
    public void setAbilityEntrySet(Set<AgisAbility.Entry> set) {
	lock.lock();
	try {
	    abilityEntrySet = new HashSet<AgisAbility.Entry>(set);
	}
	finally {
	    lock.unlock();
	}
    }
    protected Set<AgisAbility.Entry> abilityEntrySet = null;

    transient Lock lock = LockFactory.makeLock("AbilityInfoEvent");
}
