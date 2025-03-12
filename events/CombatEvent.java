package atavism.agis.events;

import atavism.agis.objects.*;
import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.util.*;

public class CombatEvent extends Event {
    public CombatEvent() {
	super();
    }

    public CombatEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public CombatEvent(AgisMob attacker, 
		       AgisObject target, 
		       String attackType) {
	super(target);
	setAttackType(attackType);
	setAttacker(attacker);
    }

    public String getName() {
	return "CombatEvent";
    }

    public AOByteBuffer toBytes() {
	throw new AORuntimeException("not implemented");
    }

    public void parseBytes(AOByteBuffer buf) {
	throw new AORuntimeException("not implemented");
    }

    public void setAttacker(AgisMob attacker) {
	this.attacker = attacker;
    }
    public AgisMob getAttacker() {
	return attacker;
    }

    public void setAttackType(String attackType) {
	this.attackType = attackType;
    }
    public String getAttackType() {
	return attackType;
    }

    private String attackType = null;
    private AgisMob attacker = null;
}
