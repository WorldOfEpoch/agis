package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.agis.objects.*;

// mob is unequiping obj
public class AgisUnequipEvent extends Event {
    public AgisUnequipEvent() {
	super();
    }

    public AgisUnequipEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AgisUnequipEvent(AgisMob unequipper, 
			    AgisItem objToUnequip, 
			    String slotName) {
	super(unequipper);
	setObjToUnequip(objToUnequip);
	setSlotName(slotName);
    }

    public String getName() {
	return "UnequipEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(200);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getObjToUnequip().getOid());
	buf.putString(getSlotName());
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	setUnequipper(AgisMob.convert(AOObject.getObject(buf.getOID())));
	/* int msgId = */ buf.getInt();
	setObjToUnequip(AgisItem.convert(AOObject.getObject(buf.getOID())));
	setSlotName(buf.getString());
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

    private AgisItem objToUnequip = null;
    private String slotName = null;
}
