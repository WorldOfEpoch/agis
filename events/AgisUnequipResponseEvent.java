package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.agis.objects.*;

// mob is unequiping obj
public class AgisUnequipResponseEvent extends Event {
    public AgisUnequipResponseEvent() {
	super();
    }

    public AgisUnequipResponseEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AgisUnequipResponseEvent(AgisMob unequipper, 
				    AgisItem objToUnequip, 
				    String slotName,
				    boolean status) {
	super(unequipper);
	setObjToUnequip(objToUnequip);
	setSlotName(slotName);
	setStatus(status);
    }

    public String getName() {
	return "UnequipResponseEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(200);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getObjToUnequip().getOid());
	buf.putString(getSlotName());
	buf.putBoolean(getStatus());
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	setUnequipper(AgisMob.convert(AOObject.getObject(buf.getOID())));
	/* int msgId = */ buf.getInt();
	setObjToUnequip(AgisItem.convert(AOObject.getObject(buf.getOID())));
	setSlotName(buf.getString());
	setStatus(buf.getBoolean());
    }

    public void setUnequipper(AgisMob mob) {
	setObject(mob);
    }

    public void setObjToUnequip(AgisItem obj) {
	objToUnequip = obj;
    }
    public AgisItem getObjToUnequip() {
	return objToUnequip;
    }

    public void setSlotName(String slotName) {
	this.slotName = slotName;
    }
    public String getSlotName() {
	return slotName;
    }

    public void setStatus(boolean status) {
	this.status = status;
    }
    public boolean getStatus() {
	return status;
    }

    private AgisItem objToUnequip = null;
    private String slotName = null;
    private boolean status = false;
}
