package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import java.io.*;

/**
 * the mobserver made a new quest state, probably a user accepted a quest.
 * so now we serialize and give the world server the quest state object
 * so it can attach it to the user object
 */
public class NewQuestStateEvent extends Event {
    public NewQuestStateEvent() {
	super();
    }

    public NewQuestStateEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public NewQuestStateEvent(AgisMob player, 
                              QuestState questState) {
	super(player);
        try {
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(ba);
            os.writeObject(questState);
            setData(ba.toByteArray());
        }
        catch(IOException e) {
            throw new RuntimeException("newqueststateevent" , e);
        }
    }

    public String getName() {
	return "NewQuestStateEvent";
    }

    public byte[] getData() {
        return questStateData;
    }
    public void setData(byte[] questStateData) {
        this.questStateData = questStateData;
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(20);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);

	byte[] data = getData();
	if (data.length > 10000) {
	    throw new AORuntimeException("NewQuestStateEvent.toBytes: overflow");
	}
	buf.putInt(data.length);
	buf.putBytes(data, 0, data.length);

	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
	buf.rewind();
	OID playerId = buf.getOID();
	setObjectOid(playerId);
	/* int msgId = */ buf.getInt();

	// data length
	int dataLen = buf.getInt();
	byte[] data = new byte[dataLen];
	buf.getBytes(data, 0, dataLen);
	setData(data);
    }

    protected byte[] questStateData = null;
}
