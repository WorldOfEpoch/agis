package atavism.agis.behaviors;

import atavism.agis.plugins.*;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.plugins.*;
import atavism.server.util.Log;

public class DotBehavior extends Behavior {
    public DotBehavior() {
    }

    public void initialize() {
        SubscriptionManager.get().subscribe(this, obj.getOid(), ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
    }

    public void activate() {
        activated = true;
        MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), radius);
    }

    public void deactivate() {
	    activated = false;
        SubscriptionManager.get().unsubscribe(this);
    }

    public void handleMessage(Message msg, int flags) {
	if (!activated) {
	    return;
	}
	if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
	    ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
	    if (nMsg.getInRadius()) {
	    	reaction(nMsg);
	    }
	}
    }

    public void reaction(ObjectTracker.NotifyReactionRadiusMessage nMsg) {
    	Log.debug("DOT: got reaction hit");
    	ArenaClient.dotScore(nMsg.getSubject());
    	CombatClient.startAbility(-500, nMsg.getTarget(), nMsg.getTarget(), null);
    	Log.debug("DOT: reaction hit finished");
    }

    public void setRadius(int radius) {
    	this.radius = radius;
    }
    public int getRadius() {
    	return radius;
    }

    protected int radius = 0;
    protected Point destination;
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
