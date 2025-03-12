package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.server.util.*;

/**
 * the client is responding to a quest, saying it will accept or decline
 */
public class QuestResponse extends Event {
    public QuestResponse() {
	super();
    }

    public QuestResponse(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public QuestResponse(AgisMob player, 
                         AgisMob questNpc,
                         OID questId,
                         boolean response) {
	super(player);
        setQuestId(questId);
        setResponse(response);
        setQuestNpcOid(questNpc.getOid());
    }

    public String getName() {
	return "QuestResponse";
    }

    public void setQuestId(OID id) {
        this.questId = id;
    }
    public OID getQuestId() {
        return questId;
    }
    OID questId = null;

    public void setResponse(boolean response) {
        this.response = response;
    }
    public boolean getResponse() {
        return response;
    }
    boolean response;

    public AgisMob getQuestNpc() {
	try {
	    return AgisMob.convert(AOObject.getObject(questNpcOid));
	}
	catch(AORuntimeException e) {
	    throw new RuntimeException("QuestResponse", e);
	}
    }
    public OID getQuestNpcOid() {
	return questNpcOid;
    }
    public void setQuestNpcOid(OID questNpcOid) {
	this.questNpcOid = questNpcOid;
    }

    OID questNpcOid = null;
    

    public AOByteBuffer toBytes() {
    	int msgId = Engine.getEventServer().getEventID(this.getClass());
    
    	AOByteBuffer buf = new AOByteBuffer(32);
    	buf.putOID(getObjectOid()); 
    	buf.putInt(msgId);
        buf.putOID(getQuestNpc().getOid());
        buf.putOID(getQuestId());
        buf.putInt(getResponse() ? 1 : 0);
        buf.flip();
        return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	OID playerId = buf.getOID();
	setObjectOid(playerId);
	/* int msgId = */ buf.getInt();
        setQuestNpcOid(buf.getOID());
        setQuestId(buf.getOID());
        setResponse(buf.getInt() == 1);
    }
}
