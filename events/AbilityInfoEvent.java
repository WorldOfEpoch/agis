package atavism.agis.events;

import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.agis.core.*;
import java.util.*;
import java.util.concurrent.locks.*;

// Provide information about a specific ability

public class AbilityInfoEvent extends Event {
    public AbilityInfoEvent() {
	super();
    }

    public AbilityInfoEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf,con);
    }

    public AbilityInfoEvent(AgisAbility ability) {
	super();
	setAbilityID(ability.getID());
	setIcon(ability.getIcon());
	setDesc("");
	for (String cooldownID : ability.getCooldownMap().keySet()) {
	    addCooldown(cooldownID);
	}
	setProperty("targetType", ability.getTargetType().toString());
	setProperty("minRange", Integer.toString(ability.getMinRange()));
	setProperty("maxRange", Integer.toString(ability.getMaxRange()));
	setProperty("costProp", ability.getCostProperty());
        setProperty("cost", Integer.toString(ability.getActivationCost()));
    }

    public String getName() {
	return "AbilityInfoEvent";
    }

    public AOByteBuffer toBytes() {
	int msgId = Engine.getEventServer().getEventID(this.getClass());
	AOByteBuffer buf = new AOByteBuffer(400);

        lock.lock();
        try {
	    buf.putInt(-1); // dummy PlayerID
	    buf.putInt(msgId);
	
	    buf.putInt(abilityID);
	    buf.putString(icon);
	    buf.putString(desc);

            int size = cooldowns.size();
            buf.putInt(size);
            for(String cooldown : cooldowns) {
                buf.putString(cooldown);
            }
	    size = props.size();
	    buf.putInt(size);
	    for(Map.Entry<String, String> entry : props.entrySet()) {
		buf.putString(entry.getKey());
		buf.putString(entry.getValue());
	    }
        }
        finally {
            lock.unlock();
        }

	buf.flip();
	return buf;
    }

    public void parseBytes(AOByteBuffer buf) {
        lock.lock();
        try {
	    buf.rewind();

	    buf.getInt(); // dummy playerID
	    /* int msgId = */ buf.getInt();

	    setAbilityID(buf.getInt());
	    setIcon(buf.getString());
	    setDesc(buf.getString());

            int size = buf.getInt();
            cooldowns = new HashSet<String>(size);
            while (size-- > 0) {
                String cooldown = buf.getString();
		cooldowns.add(cooldown);
            }
	    size = buf.getInt();
	    props = new HashMap<String, String>(size);
	    while (size-- > 0) {
		String key = buf.getString();
		String value = buf.getString();
		setProperty(key, value);
	    }
        }
        finally {
            lock.unlock();
        }
    }

    public int getAbilityID() { return abilityID; }
    public void setAbilityID(int id) { abilityID = id; }
    protected int abilityID;

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    protected String icon;

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    protected String desc;

    public void addCooldown(String cooldownID) {
	lock.lock();
	try {
	    if (cooldowns == null) {
		cooldowns = new HashSet<String>();
	    }
	    cooldowns.add(cooldownID);
	}
	finally {
	    lock.unlock();
	}
    }
    public Set<String> getCooldowns() {
	lock.lock();
	try {
	    return new HashSet<String>(cooldowns);
	}
	finally {
	    lock.unlock();
	}
    }
    protected Set<String> cooldowns = null;

    public String getProperty(String key) { return props.get(key); }
    public void setProperty(String key, String value) {
	lock.lock();
	try {
	    if (props == null) {
		props = new HashMap<String, String>();
	    }
	    props.put(key, value);
	}
	finally {
	    lock.unlock();
	}
    }
    protected Map<String, String> props = null;

    transient Lock lock = LockFactory.makeLock("AbilityInfoEvent");
}
