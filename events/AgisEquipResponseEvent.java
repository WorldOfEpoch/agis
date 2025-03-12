package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.objects.*;

public class AgisEquipResponseEvent extends Event {

    public AgisEquipResponseEvent() {
	super();
    }

    public AgisEquipResponseEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AgisEquipResponseEvent(AgisMob equipper, 
				  AgisItem obj, 
				  String slotName,
				  boolean success) {
	super(equipper);
	setObjToEquip(obj);
	setSlotName(slotName);
	setSuccess(success);
    }

    public String getName() {
	return "AgisEquipResponseEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(200);
	buf.putOID(getObjectOid());
	buf.putInt(msgId);

	buf.putOID(getObjToEquip().getOid());
	buf.putString(getSlotName());
	buf.putInt(getSuccess()?1:0);
	
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	AOObject obj = AOObject.getObject(buf.getOID());
	if (! (obj.isMob())) {
	    throw new AORuntimeException("EquipResponseEvent.parseBytes: not a mob");
	}
	setEquipper(AgisMob.convert(obj));

	/* int msgId = */ buf.getInt();
	
	setObjToEquip(AgisItem.convert(AOObject.getObject(buf.getOID())));
	setSlotName(buf.getString());
	setSuccess(buf.getInt() == 1);
    }

    public void setEquipper(AgisMob mob) {
	setObject(mob);
    }

    public void setObjToEquip(AgisItem item) {
	objToEquip = item;
    }
    public AOObject getObjToEquip() {
	return objToEquip;
    }

    public void setSuccess(boolean success) {
	this.success = success;
    }
    public boolean getSuccess() {
	return success;
    }
    public void setSlotName(String slotName) {
	this.slotName = slotName;
    }
    public String getSlotName() {
	return slotName;
    }

    private AgisItem objToEquip = null;
    private boolean success = false;
    private String slotName = null;
}
