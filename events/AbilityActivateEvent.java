package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import java.util.concurrent.locks.*;

// Activate an ability

public class AbilityActivateEvent extends Event {
    public AbilityActivateEvent() {
	super();
    }

    public AbilityActivateEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AbilityActivateEvent(AgisMob obj, AgisAbility ability, AgisObject target, AgisItem item) {
	super();
	setObjOid(obj.getOid());
	setAbilityID(ability.getID());
	if (target != null) {
	    setTargetOid(target.getOid());
	}
	if (item != null) {
	    setItemOid(item.getOid());
	}
    }

    public String getName() {
	return "AbilityActivateEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());
	AOByteBuffer buf = new AOByteBuffer(200);

        lock.lock();
        try {
	    buf.putOID(objOid);
	    buf.putInt(msgId);
	    buf.putInt(abilityID);
	    buf.putOID(targetOid);
	    buf.putOID(itemOid);
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
	    setAbilityID(buf.getInt());
	    setTargetOid(buf.getOID());
	    setItemOid(buf.getOID());
        }
        finally {
            lock.unlock();
        }
    }

    public OID getObjOid() { return objOid; }
    public void setObjOid(OID oid) { objOid = oid; }
    protected OID objOid = null;

    public OID getTargetOid() { return targetOid; }
    public void setTargetOid(OID oid) { targetOid = oid; }
    protected OID targetOid = null;

    public int getAbilityID() { return abilityID; }
    public void setAbilityID(int id) { abilityID = id; }
    protected int abilityID;

    public OID getItemOid() { return itemOid; }
    public void setItemOid(OID oid) { itemOid = oid; }
    protected OID itemOid = null;

    transient Lock lock = LockFactory.makeLock("AbilityInfoEvent");
}
