package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.network.*;

/**
 * the client is asking for what quests this questnpc has for the user
 * see serverrequestinfo for what the world server sends to the
 * mobserver to find out
 */
public class RequestQuestInfo extends Event {
    public RequestQuestInfo() {
	super();
    }

    public RequestQuestInfo(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public RequestQuestInfo(AgisMob player, 
			    AgisMob questNpc) {
	super(player);
	setQuestNpcOid(questNpc.getOid());
    }

    public String getName() {
	return "RequestQuestInfo";
    }

    public OID getQuestNpcOid() {
	return questNpcOid;
    }
    public void setQuestNpcOid(OID questNpcOid) {
	this.questNpcOid = questNpcOid;
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(20);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getQuestNpcOid());
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
