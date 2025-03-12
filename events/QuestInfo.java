package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.server.util.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class QuestInfo extends Event {
    public QuestInfo() {
	super();
    }

    public QuestInfo(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

//    public QuestInfo(AgisMob player, 
//		     AgisMob questNpc,
//		     AgisQuest quest) {
//	super(player);
//	setQuestNpcOid(questNpc.getOid());
//	setTitle(quest.getName());
//	setDesc(quest.getDesc());
//	setObjective(quest.getObjective());
//        setQuestId(quest.getOid());
//
//        List<ItemTemplate> rewardTempls = quest.getRewards();
//        if ((rewardTempls == null) || (rewardTempls.isEmpty())) {
//            Log.debug("QuestInfo: rewardtemplate is null for quest " +
//                      getTitle());
//        }
//        else {
//            List<Reward> rewards = new LinkedList<Reward>();
//            for (ItemTemplate itemTempl : rewardTempls) {
//                Reward reward = new Reward(itemTempl.getName(),
//                                           itemTempl.getIcon(),
//                                           1);
//                rewards.add(reward);
//                setRewards(rewards);
//            }
//        }
//    }

    public String toString() {
	try {
	    return "[Event=QuestInfo: player=" +
		getObjectOid() +
		",npc=" + getQuestNpc().getName() +
		",questId=" + getQuestId() +
		",title=" + getTitle() +
		",desc=" + getDesc() +
		",objective=" + getObjective() +
		"]";
	}
	catch(AORuntimeException e) {
	    throw new RuntimeException("questinfo.tostring", e);
	}
    }

    public String getName() {
	return "QuestInfo";
    }

    public AgisMob getQuestNpc() {
	return AgisMob.convert(AOObject.getObject(questNpcOid));
    }
    public OID getQuestNpcOid() {
	return this.questNpcOid;
    }
    public void setQuestNpcOid(OID questNpcOid) {
	this.questNpcOid = questNpcOid;
    }


    public void setTitle(String s) {
	this.title = s;
    }
    public String getTitle() {
	return title;
    }
    String title = null;

    public void setDesc(String s) {
	this.desc = s;
    }
    public String getDesc() {
	return desc;
    }
    String desc = null;

    public void setObjective(String s) {
	this.objective = s;
    }
    public String getObjective() {
	return objective;
    }
    String objective = null;

    public void setQuestId(OID oid) {
        this.questId = oid;
    }
    public OID getQuestId() {
        return questId;
    }
    OID questId = null;

    public void setRewards(List<Reward> rewards) {
        lock.lock();
        try {
            this.rewards = new LinkedList<Reward>(rewards);
        }
        finally {
            lock.unlock();
        }
    }
    public List<Reward> getRewards() {
        lock.lock();
        try {
            return new LinkedList<Reward>(rewards);
        }
        finally {
            lock.unlock();
        }
    }

    public static class Reward {
        public Reward(String name, String icon, int count) {
            this.name = name;
            this.icon = icon;
            this.count = count;
        }
        public String name = null;
        public String icon = null;
        public int count = 0;
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(500);
	buf.putOID(getObjectOid()); 
	buf.putInt(msgId);
	buf.putOID(getQuestNpc().getOid());
        buf.putOID(getQuestId());
	buf.putString(getTitle());
	buf.putString(getDesc());
	buf.putString(getObjective());

        lock.lock();
        try {
            if (rewards == null) {
                buf.putInt(0);
            }
            else {
                int size = rewards.size();
                buf.putInt(size);
                Iterator<Reward> iter = rewards.iterator();
                while(iter.hasNext()) {
                    Reward reward = iter.next();
                    buf.putString(reward.name);
                    buf.putString(reward.icon);
                    buf.putInt(reward.count);
                }
            }
        }
        finally {
            lock.unlock();
        }
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
        setQuestId(buf.getOID());
	setTitle(buf.getString());
	setDesc(buf.getString());
	setObjective(buf.getString());

        lock.lock();
        try {
            this.rewards = new LinkedList<Reward>();
            int size = buf.getInt(); // num rewards
            while (size > 0) {
                String name = buf.getString();
                String icon = buf.getString();
                int count = buf.getInt();
                Reward reward = new Reward(name, icon, count);
                rewards.add(reward);
                size--;
            }
        }
        finally {
            lock.unlock();
        }
    }

    List<Reward> rewards = null;
    protected OID questNpcOid = null;
    transient Lock lock = LockFactory.makeLock("QuestInfoLock");
}
