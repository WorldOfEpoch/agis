package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.agis.objects.*;
import java.util.*;

public class QuestStateInfo extends Event {
    public QuestStateInfo() {
	super();
    }

    public QuestStateInfo(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public QuestStateInfo(AgisMob agisMob, QuestState questState) {
	super();
        setPlayerOid(agisMob.getOid());
        // FIXME:
        //setQuestId(questState.getQuestId());
        setObjectiveStatus(questState.getObjectiveStatus().get(0));
    }

    public String getName() {
	return "QuestStateInfo";
    }

    // i use a long here so i dont have to lock around it
    void setPlayerOid(OID id) {
        this.playerId = id;
    }
    void setQuestId(OID id) {
        this.questId = id;
    }
    void setObjectiveStatus(List<String> objStatus) {
        this.objStatus = objStatus;
    }

    public AOByteBuffer toBytes() {
    	int msgId = Engine.getEventServer().getEventID(this.getClass());
    
    	AOByteBuffer buf = new AOByteBuffer(500);
    	buf.putOID(playerId); 
    	buf.putInt(msgId);
    	
    	buf.putOID(questId);
        buf.putInt(objStatus.size());
        Iterator<String> iter = objStatus.iterator();
        while (iter.hasNext()) {
            buf.putString(iter.next());
        }
    	buf.flip();
    	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
    	buf.rewind();
    	setPlayerOid(buf.getOID());
    	/* int msgId = */ buf.getInt();
        setQuestId(buf.getOID());

        // read in the obj status list
        List<String> l = new LinkedList<String>();
        int len = buf.getInt();
        while (len>0) {
            l.add(buf.getString());
            len--;
        }
        setObjectiveStatus(l);
    }

    OID playerId = null;
    OID questId = null;
    List<String> objStatus = null;
}
