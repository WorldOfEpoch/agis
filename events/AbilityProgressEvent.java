package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.core.*;
import java.util.concurrent.locks.*;

// Update progress for the activation of an ability

public class AbilityProgressEvent extends Event {
    public AbilityProgressEvent() {
	super();
    }

    public AbilityProgressEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AbilityProgressEvent(AgisAbilityState state) {
	super();
	setObjOid(state.getTarget().getOid());
	setAbilityID(state.getAbility().getID());
	setState(state.getState().toString());
	setDuration(state.getDuration());
	setEndTime(calculateEndTime(state));
    }

    protected long calculateEndTime(AgisAbilityState state) {
	AgisAbility ability = state.getAbility();

	switch (state.getState()) {
	case ACTIVATING:
	    return state.getNextWakeupTime();
	case CHANNELLING:
	    int pulsesRemaining = ability.getChannelPulses() - state.getNextPulse() - 1;
	    long endTime = state.getNextWakeupTime() + (pulsesRemaining * ability.getChannelPulseTime());
	    return endTime;
	default:
	    return 0;
	}
    }

    public String getName() {
	return "AbilityProgressEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());
	AOByteBuffer buf = new AOByteBuffer(400);

        lock.lock();
        try {
	    buf.putOID(objOid);
	    buf.putInt(msgId);
	    buf.putInt(abilityID);
	    buf.putString(state);
	    buf.putLong(duration);
	    buf.putLong(endTime);
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
	    setState(buf.getString());
	    setDuration(buf.getLong());
	    setEndTime(buf.getLong());
        }
        finally {
            lock.unlock();
        }
    }

    public OID getObjOid() { return objOid; }
    public void setObjOid(OID oid) { objOid = oid; }
    protected OID objOid;

    public int getAbilityID() { return abilityID; }
    public void setAbilityID(int id) { abilityID = id; }
    protected int abilityID;

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    protected String state;

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    protected long duration;

    public long getEndTime() { return endTime; }
    public void setEndTime(long time) { endTime = time; }
    protected long endTime;

    transient Lock lock = LockFactory.makeLock("AbilityInfoEvent");
}
