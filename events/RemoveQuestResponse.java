package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.agis.objects.*;

public class RemoveQuestResponse extends Event {
    public RemoveQuestResponse() {
	super();
    }

    public RemoveQuestResponse(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public RemoveQuestResponse(QuestState questState) {
	super();
        setPlayerOid(questState.getPlayerOid());
        //FIXME:
        //setQuestId(questState.getQuestId());
    }

    public String getName() {
	return "RemoveQuestResponse";
    }

    void setPlayerOid(OID id) {
        this.playerId = id;
    }
    void setQuestId(OID id) {
        this.questId = id;
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(20);
	buf.putOID(playerId); 
	buf.putInt(msgId);
	buf.putOID(questId);
	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	setPlayerOid(buf.getOID());
	/* int msgId = */ buf.getInt();
        setQuestId(buf.getOID());
    }

    OID playerId = null;
    OID questId = null;
}
