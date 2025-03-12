package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;

public class AgisDamageEvent extends Event {
    public AgisDamageEvent() {
	super();
    }

    public AgisDamageEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public AgisDamageEvent(AOObject src, AOObject target, int dmg) {
	super(target);
	setDmg(dmg);
	setDmgSrc(src);
    }

    public String getName() {
	return "AgisDamageEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(100);
	buf.putOID(getDmgSrc().getOid()); 
	buf.putInt(msgId);
	
	buf.putOID(getObjectOid());
	buf.putString("stun");
	buf.putInt(getDmg());
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();

	setDmgSrc(AOObject.getObject(buf.getOID()));
	/* int msgId = */ buf.getInt();
	setObjectOid(buf.getOID());
	/* String dmgType = */ buf.getString();
	setDmg(buf.getInt());
    }

    public void setDmgSrc(AOObject dmgSrc) {
	this.dmgSrc = dmgSrc;
    }
    public AOObject getDmgSrc() {
	return dmgSrc;
    }

    public void setDmg(int dmg) {
	this.dmg = dmg;
    }
    public int getDmg() {
	return dmg;
    }

    private int dmg = 0;
    private AOObject dmgSrc = null;
}
