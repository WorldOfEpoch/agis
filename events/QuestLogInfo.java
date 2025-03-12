package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class QuestLogInfo extends Event {
    public QuestLogInfo() {
	super();
    }

    public QuestLogInfo(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

//    public QuestLogInfo(AgisMob agisMob, AgisQuest quest) {
//	super();
//        setPlayerOid(agisMob.getOid());
//        setQuestId(quest.getOid());
//        setTitle(quest.getName());
//        setDesc(quest.getDesc());
//        setObjective(quest.getObjective());
//
//        List<QuestInfo.Reward> rewards = new LinkedList<QuestInfo.Reward>();
//        List<ItemTemplate> rewardTempls = quest.getRewards();
//        for (ItemTemplate itemTempl : rewardTempls) {
//            QuestInfo.Reward reward = 
//                new QuestInfo.Reward(itemTempl.getName(),
//                                     itemTempl.getIcon(),
//                                     1);
//            rewards.add(reward);
//        }
//        setRewards(rewards);
//    }

    public String getName() {
	return "QuestLogInfo";
    }

    // i use a long here so i dont have to lock around it
    void setPlayerOid(OID id) {
        this.playerId = id;
    }
    void setQuestId(OID id) {
        this.questId = id;
    }
    void setTitle(String title) {
        this.title = title;
    }
    void setDesc(String desc) {
        this.desc = desc;
    }
    void setObjective(String obj) {
        this.obj = obj;
    }

    public void setRewards(List<QuestInfo.Reward> rewards) {
        lock.lock();
        try {
            this.rewards = new LinkedList<QuestInfo.Reward>(rewards);
        }
        finally {
            lock.unlock();
        }
    }
    public List<QuestInfo.Reward> getRewards() {
        lock.lock();
        try {
            return new LinkedList<QuestInfo.Reward>(rewards);
        }
        finally {
            lock.unlock();
        }
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());

	AOByteBuffer buf = new AOByteBuffer(500);
	buf.putOID(playerId); 
	buf.putInt(msgId);
	
	buf.putOID(questId);
	buf.putString(title);
	buf.putString(desc);
	buf.putString(obj);

        lock.lock();
        try {
            int size = rewards.size();
            buf.putInt(size);
            Iterator<QuestInfo.Reward> iter = rewards.iterator();
            while(iter.hasNext()) {
                QuestInfo.Reward reward = iter.next();
                buf.putString(reward.name);
                buf.putString(reward.icon);
                buf.putInt(reward.count);
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
	setPlayerOid(buf.getOID());
	/* int msgId = */ buf.getInt();
        setQuestId(buf.getOID());
        setTitle(buf.getString());
        setDesc(buf.getString());
        setObjective(buf.getString());

        lock.lock();
        try {
            this.rewards = new LinkedList<QuestInfo.Reward>();
            int size = buf.getInt(); // num rewards
            while (size > 0) {
                String name = buf.getString();
                String icon = buf.getString();
                int count = buf.getInt();
                QuestInfo.Reward reward = 
                    new QuestInfo.Reward(name, icon, count);
                rewards.add(reward);
                size--;
            }
        }
        finally {
            lock.unlock();
        }
    }

    OID playerId;
    OID questId;
    String title;
    String desc;
    String obj;
    List<QuestInfo.Reward> rewards = null;
    transient Lock lock = LockFactory.makeLock("QuestLogInfo");
}
