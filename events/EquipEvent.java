package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.agis.objects.*;

/**
 * mob is equiping obj
 */
public class EquipEvent extends Event {
    public EquipEvent() {
	super();
    }

    public EquipEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public EquipEvent(AgisMob equipper, AgisItem equipObj, String slotName) {
	super(equipper);
	setObjToEquipId(equipObj.getOid());
	setSlotName(slotName);
    }

    public String getName() {
	return "EquipEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(200);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getObjToEquip().getOid());
	buf.putString(getSlotName());
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	setObjectOid(buf.getOID());
	/* int msgId = */ buf.getInt();
	setObjToEquipId(buf.getOID());
	setSlotName(buf.getString());
    }

    public void setObjToEquipId(AgisItem obj) {
	this.objToEquipId = obj.getOid();
    }
    public void setObjToEquipId(OID id) {
	this.objToEquipId = id;
    }
    public OID getObjToEquipId() {
	return objToEquipId;
    }
    public AgisItem getObjToEquip() {
	return AgisItem.convert(AOObject.getObject(objToEquipId));
    }

    public void setSlotName(String slotName) {
	this.slotName = slotName;
    }
    public String getSlotName() {
	return slotName;
    }

    private OID objToEquipId = null;
    private String slotName = null;
}
