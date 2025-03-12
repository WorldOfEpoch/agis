package atavism.agis.events;

import java.io.*;
import java.util.*;
import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.messages.*;
import atavism.agis.plugins.*;
import atavism.msgsys.*;

public class AbilityStatusMessage extends PropertyMessage {

    protected OID oid = null;
    protected Boolean activated = null;
    protected Long activationId = null;
    protected Integer duration = null;
    protected String abilityType = null;
    protected String abilityName = null;

    public AbilityStatusMessage() {
	super(CombatClient.MSG_TYPE_ABILITY_STATUS);  // need to add to type list
    }

    public AbilityStatusMessage(OID objOid) {
	super(CombatClient.MSG_TYPE_ABILITY_STATUS, objOid);  // need to add to type list
    }

    public AbilityStatusMessage(MessageType msgType, String abilityType, String abilityName, OID objOid) {
	super(msgType, objOid);
	setAbilityType(abilityType);
	setAbilityType(abilityName);
    }

    public AbilityStatusMessage(MessageType msgType, OID objOid, Map<String, Serializable> propertyMap) {
	super(msgType, objOid);
	this.propertyMap = propertyMap;
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

    public AOByteBuffer toBuffer() {
        lock.lock();
        try {
	    int msgId = 84;
	    AOByteBuffer buf = new AOByteBuffer(512);
	    buf.putOID(getSubject());
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
    
    private static final long serialVersionUID = 1L;

}
