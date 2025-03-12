package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.network.*;

public class QuestAvailableEvent extends Event {
    public QuestAvailableEvent() {
	super();
    }

    public QuestAvailableEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public QuestAvailableEvent(AgisMob user,
                               AgisMob questGiver,
                               boolean isAvail,
                               boolean isConclude) {
	super(user);
	setQuestGiverOid(questGiver.getOid());
        isAvailable(isAvail);
        isConcludable(isConclude);
    }

    public String getName() {
	return "QuestAvailableEvent";
    }

    public void setQuestGiverOid(OID oid) {
	this.questGiverOid = oid;
    }
    public OID getQuestGiverOid() {
	return questGiverOid;
    }

    public void isAvailable(boolean flag) {
        this.isAvailableFlag = flag;
    }
    public boolean isAvailable() {
        return isAvailableFlag;
    }

    public void isConcludable(boolean flag) {
        this.isConcludableFlag = flag;
    }
    public boolean isConcludable() {
        return isConcludableFlag;
    }

    public AOByteBuffer toBytes() {
    	int msgId = Engine.getEventServer().getEventID(this.getClass());
    
    	AOByteBuffer buf = new AOByteBuffer(32);
    	buf.putOID(getObjectOid());
    	buf.putInt(msgId);
    	
    	buf.putOID(getQuestGiverOid());
    	buf.putBoolean(isAvailable());
        buf.putBoolean(isConcludable());
    	buf.flip();
    	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
    	buf.rewind();
    
    	OID userId = buf.getOID();
    	setObjectOid(userId);
    	/* int msgId = */ buf.getInt();
    
    	OID questGiverOid = buf.getOID();
    	setQuestGiverOid(questGiverOid);

        isAvailable(buf.getBoolean());
        isConcludable(buf.getBoolean());
    }

    private OID questGiverOid = null;
    private boolean isAvailableFlag = false;
    private boolean isConcludableFlag = false;
}
