package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;

/**
 * object is dropping the a diff obj from its inventory
 */
public class DropResponseEvent extends Event {
    public DropResponseEvent() {
	super();
    }

    public DropResponseEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public DropResponseEvent(AOObject dropper, 
			     AOObject obj, 
			     String slot, 
			     boolean status) {
	super(obj);
	setDropper(dropper);
	setSlotName(slot);
	setStatus(status);
    }

    public String getName() {
	return "DropEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(200);
	buf.putOID(getDropper().getOid()); 
	buf.putInt(msgId);
	
	buf.putOID(getObjectOid());
	buf.putString(getSlotName());
	buf.putInt(getStatus()?1:0);
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();

	OID playerId = buf.getOID();
	setDropper(AOObject.getObject(playerId));

	/* int msgId = */ buf.getInt();

	OID objId = buf.getOID();
	setObjectOid(objId);

	setSlotName(buf.getString());
	setStatus(buf.getInt() == 1);
    }

    public void setDropper(AOObject dropper) {
	this.dropper = dropper;
    }
    public AOObject getDropper() {
	return dropper;
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

    private AOObject dropper = null;
    private String slotName = null;
    private boolean status;
}
