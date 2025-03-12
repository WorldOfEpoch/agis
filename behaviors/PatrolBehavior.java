package atavism.agis.behaviors;

import java.util.*;
import java.util.concurrent.*;

import atavism.msgsys.*;
import atavism.server.objects.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.util.*;
import atavism.server.engine.*;

public class PatrolBehavior extends Behavior implements Runnable {
    public PatrolBehavior() {
    }

    public PatrolBehavior(SpawnData data) {
    }

    public void initialize() {
        SubscriptionManager.get().subscribe(this, obj.getOid(), Behavior.MSG_TYPE_EVENT);
    }

    public void activate() {
    	if(Log.loggingDebug)
			Log.debug("PatrolBehavior.activate " + obj.getOid());
        activated = true;
        startPatrol();
        if(Log.loggingDebug)
			Log.debug("PatrolBehavior.activate " + obj.getOid()+" End");
    }
    public void deactivate() {
        activated = false;
        SubscriptionManager.get().unsubscribe(this);
    }

    public void handleMessage(Message msg, int flags) {
        if (!activated) {
            return;
        }
        if (msg.getMsgType() == Behavior.MSG_TYPE_EVENT) {
	        String event = ((Behavior.EventMessage)msg).getEvent();
	        Log.debug("PatrolBehavior: handleMessage: event="+event+" oid="+obj.getOid());
	        if (event.equals(BaseBehavior.MSG_EVENT_TYPE_ARRIVED)) {
		        Engine.getExecutor().schedule(this, (long)(getLingerTimes().get(nextWaypoint)>0?getLingerTimes().get(nextWaypoint)*1000:50), TimeUnit.MILLISECONDS);
            }else {
            	
            }
        }
        //return true;
    }

    public void addWaypoint(Point wp) {
        waypoints.add(wp);
    }
    protected List<Point> waypoints = new ArrayList<Point>();

    public void addLingerTime(float time) {
    	lingerTimes.add(time);
    }
    public List<Float> getLingerTimes() {
        return lingerTimes;
    }
    protected List<Float> lingerTimes = new ArrayList<Float>();

    public void setMovementSpeed(float speed) {
        this.speed = speed;
    }
    public float getMovementSpeed() {
        return speed;
    }
    protected float speed = 3;
    
   /* 
    public void setSwinging(boolean v) {
    	swinging =v;
    }
    public boolean getSwinging() {
    	return swinging;
    }
    protected boolean swinging = false;
    protected boolean forwardDirection = true;
    */
    
    /**
     * Calculate the current position of the mob and work out where along the chain they are
     */
    protected void startPatrol() {
    	if(Log.loggingDebug)
			Log.debug("PatrolBehavior.startPatrol " + obj.getOid() +" num behav "+obj.getBehaviors().size());
    	Point currentLoc = obj.getWorldNode().getLoc();
    	float minDistance = Point.distanceTo(currentLoc, waypoints.get(0));
    	nextWaypoint = 0;
    	for (int i = 1; i < waypoints.size(); i++) {
    		float distanceFromMarker = Point.distanceTo(currentLoc, waypoints.get(i));
    		if (distanceFromMarker < minDistance) {
    			minDistance = distanceFromMarker;
    			nextWaypoint = i;
    			Log.debug("PATROL: first waypoint is now: " + nextWaypoint);
    		}
    	}
    	
    	Engine.getExecutor().schedule(this, 1000L, TimeUnit.MILLISECONDS);
    	if(Log.loggingDebug)
			Log.debug("PatrolBehavior.startPatrol " + obj.getOid() +" End");

    }

    protected void sendMessage(Point waypoint, float speed) {
    	Log.debug("PATH: sending patrol point: " + waypoint + " with next: " + nextWaypoint+" for "+obj.getOid());
    	InterpolatedWorldNode wnode = obj.getWorldNode();
		Point loc = null;
		if (wnode != null) {
			loc = wnode.getCurrentLoc();
		}
		if(Log.loggingDebug)
			Log.debug("PatrolBehavior.sendCommand GoToCommand " + obj.getOid() + " loc=" + loc + " dist="+(loc!=null && waypoint !=null?Point.distanceTo(loc, waypoint):"bd")+ " waypoint=" + waypoint + " speed=" + speed);
        Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, waypoint, speed));
    }

    public void nextPatrol() {
    	Log.debug("PATROL: nextPatrol");
    	sendMessage(waypoints.get(nextWaypoint), getMovementSpeed());
    }

	public boolean wasInCombat = false;

	public void run() {
		// Check if the target is in combat
		if(Log.loggingDebug)
			Log.debug("PatrolBehavior.run " + obj.getOid() +" num behav "+obj.getBehaviors().size());
		boolean inCombat = false;
		for (Behavior behav : obj.getBehaviors()) {
			if (behav instanceof CombatBehavior) {
				CombatBehavior cBehav = (CombatBehavior) behav;
				inCombat = cBehav.getInCombat();
				if (cBehav.selectedBehavior != null && cBehav.selectedBehavior.behaviorType == 3) {
					inCombat = true;
				} else if (cBehav.selectedBehavior != null && cBehav.selectedBehavior.behaviorType == 5 && cBehav.currentTarget != null) {
					inCombat = true;
				}else if (cBehav.selectedBehavior == null) {
					inCombat = false;
				}
			}

		}
		if(Log.loggingDebug)
			Log.debug("PatrolBehavior.run " + obj.getOid() +" "+inCombat);

		if (inCombat) {
			Engine.getExecutor().schedule(this, 1000L, TimeUnit.MILLISECONDS);
			wasInCombat = true;
			return;
		}
		Log.debug("PATH: start calculate dest: " + nextWaypoint + " wasInCombat="+wasInCombat+" for "+obj.getOid());
		if (!wasInCombat) {
			nextWaypoint++;
			if (nextWaypoint == waypoints.size()) {
				nextWaypoint = 0;
			}
		}
		wasInCombat = false;
		nextPatrol();
	}

    int nextWaypoint = 0;
    private boolean activated;
    private static final long serialVersionUID = 1L;

}
