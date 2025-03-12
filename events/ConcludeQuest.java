package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.server.util.*;

/**
 * the client is turning in a quest
 */
public class ConcludeQuest extends Event {
    public ConcludeQuest() {
	super();
    }

    public ConcludeQuest(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public ConcludeQuest(AgisMob player, 
                         AgisMob questNpc) {
	super(player);
	setQuestNpcOid(questNpc.getOid());
    }

    public String getName() {
	return "ConcludeQuest";
    }

    public AgisMob getQuestNpc() {
	try {
	    return AgisMob.convert(AOObject.getObject(questNpcOid));
	}
	catch(AORuntimeException e) {
	    throw new RuntimeException("concludequest", e);
	}
    }
    public OID getQuestNpcOid() {
	return this.questNpcOid;
    }
    public void setQuestNpcOid(OID questNpcOid) {
	this.questNpcOid = questNpcOid;
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(20);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getQuestNpc().getOid());
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	OID playerId = buf.getOID();
	setObjectOid(playerId);
	/* int msgId = */ buf.getInt();
	OID questNpcId = buf.getOID();
	setQuestNpcOid(questNpcId);
    }

    protected OID questNpcOid = null;
}
