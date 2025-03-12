package atavism.agis.behaviors;

import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

/**
 * Teleports players/mobs that come within the reaction radius.
 * @author Andrew
 *
 */
public class TeleporterBehavior extends Behavior {
    public TeleporterBehavior() {
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
// 	    Log.debug("TeleporterBehavior: myOid=" + obj.getOid() + " objOid=" + nMsg.getObjOid()
// 		      + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());
	    if (nMsg.getInRadius()) {
		reaction(nMsg);
	    }
	}
    }

    public void reaction(ObjectTracker.NotifyReactionRadiusMessage nMsg) {
	BasicWorldNode wnode = new BasicWorldNode();
	wnode.setLoc(destination);
        // tell the worldmanager we've moved
        // this should update everyone near me
        TargetedExtensionMessage teleportBegin =
            new TargetedExtensionMessage(nMsg.getSubject(), nMsg.getSubject());
        teleportBegin.setExtensionType("ao.SCENE_BEGIN");
        teleportBegin.setProperty("action","teleport");
        TargetedExtensionMessage teleportEnd =
            new TargetedExtensionMessage(nMsg.getSubject(), nMsg.getSubject());
        teleportEnd.setExtensionType("ao.SCENE_END");
        teleportEnd.setProperty("action","teleport");
        WorldManagerClient.updateWorldNode(nMsg.getSubject(), wnode, true,
            teleportBegin, teleportEnd);
    }

    public void setRadius(int radius) {
	this.radius = radius;
    }
    public int getRadius() {
	return radius;
    }

    public void setDestination(Point loc) {
	destination = loc;
    }
    public Point getDestination() {
	return destination;
    }

    protected int radius = 0;
    protected Point destination;
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
