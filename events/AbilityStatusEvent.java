package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

// Provide information about a specific ability

public class AbilityStatusEvent extends Event {

    protected OID oid = null;
    protected Boolean activated = null;
    protected Long activationId = null;
    protected Integer duration = null;
    protected String abilityType = null;
    protected String abilityName = null;
    protected Map<String, Serializable> propertyMap = new HashMap<String, Serializable>();

    public AbilityStatusEvent() {
	super();
    }

    public AbilityStatusEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public String getName() {
	return "AbilityStatusEvent";
    }

    public String toString() {
	return "[AbilityStatusEvent]";
    }

    public AOByteBuffer toBytes() {
        lock.lock();
        try {
	    int msgId = Engine.getEventServer().getEventID(this.getClass());
	    AOByteBuffer buf = new AOByteBuffer(512);
	    buf.putOID(null);  // oid
	    buf.putInt(msgId);
	    buf.putBoolean(activated);
	    buf.putLong(activationId);
	    buf.putInt(duration);
	    buf.putString(abilityType);
	    buf.putString(abilityName);
	    buf.putPropertyMap(propertyMap);
	    buf.flip();
	    return buf;
        } finally {
            lock.unlock();
        }
    }

    public void parseBytes(AOByteBuffer buf) {
        lock.lock();
        try {
	    buf.rewind();
	    setObjectOid(buf.getOID());     // oid
	    buf.getInt();                    // msgId
	    activated = buf.getBoolean();
	    activationId = buf.getLong();
	    duration = buf.getInt();
	    abilityType = buf.getString();
	    abilityName = buf.getString();
	    propertyMap = buf.getPropertyMap();
        }
        finally {
            lock.unlock();
        }
    }

    public Boolean getActivated() { return activated; }
    public void setActivated(Boolean activated) { this.activated = activated; }

    public Long getActivationId() { return activationId; }
    public void setActivationId(Long activationId) { this.activationId = activationId; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getAbilityType() { return abilityType; }
    public void setAbilityType(String abilityType) { this.abilityType = abilityType; }

    public String getAbilityName() { return abilityName; }
    public void setAbilityName(String abilityName) { this.abilityName = abilityName; }

    public Map<String, Serializable> getPropertyMap() { return propertyMap; }
    public void setPropertyMap(Map<String, Serializable> propertyMap) { this.propertyMap = propertyMap; }

    public Serializable getProperty(String key) { return propertyMap.get(key); }
    public void setProperty(String key, String value) {
	lock.lock();
	try {
	    if (propertyMap == null) {
		propertyMap = new HashMap<String, Serializable>();
	    }
	    propertyMap.put(key, value);
	}
	finally {
	    lock.unlock();
	}
    }
    transient Lock lock = LockFactory.makeLock("AbilityStatusEvent");
}
