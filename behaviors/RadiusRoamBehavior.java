package atavism.agis.behaviors;

import java.util.Random;
import java.util.concurrent.*;

import atavism.agis.objects.CombatInfo;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.util.*;

public class RadiusRoamBehavior extends Behavior implements Runnable {
    public RadiusRoamBehavior() {
    }

    public RadiusRoamBehavior(SpawnData data) {
    	super(data);
    	setCenterLoc(data.getLoc());
    	setRadius(data.getSpawnRadius());
    }

    public void initialize() {
        SubscriptionManager.get().subscribe(this, obj.getOid(), Behavior.MSG_TYPE_EVENT, PropertyMessage.MSG_TYPE_PROPERTY);
		if(!randomLingerEachTime) {
			Random rand = new Random();
			if(maxLingerTime>minLingerTime) {
				lingerTime = minLingerTime + rand.nextInt((int) (maxLingerTime - minLingerTime));
			} else {
				lingerTime = minLingerTime;
			}
		}
    }

	public void activate() {
		if (Log.loggingDebug)
			Log.debug("RadiusRoamBehavior.activate " + obj.getOid());
		activated = true;
		inCombat = false;
		startRoam();
		if (Log.loggingDebug)
			Log.debug("RadiusRoamBehavior.activate End " + obj.getOid());
	}

    public void deactivate() {
    	lock.lock();
		try {
			activated = false;
			SubscriptionManager.get().unsubscribe(this);
	    	inCombat = false;
		}
		finally {
			lock.unlock();
		}
    }

    public void handleMessage(Message msg, int flags) {
        if (!activated) {
            return;
        }

        if (msg.getMsgType() == Behavior.MSG_TYPE_EVENT) {
            String event = ((Behavior.EventMessage) msg).getEvent();
            if (event.equals(BaseBehavior.MSG_EVENT_TYPE_ARRIVED)) {
                if (!inCombat) {
                    if (randomLingerEachTime) {
                        int bound = (int) (maxLingerTime - minLingerTime);
                        if (bound > 0) {
                            lingerTime = minLingerTime + rand.nextInt(bound);
                        } else {
                            lingerTime = minLingerTime;
                            Log.warn("RadiusRoamBehavior: maxLingerTime - minLingerTime <= 0. Setting lingerTime to minLingerTime: " + lingerTime);
                        }
                    }
                    Engine.getExecutor().schedule(this, lingerTime, TimeUnit.MILLISECONDS);
                }
            }
        } else if (msg instanceof PropertyMessage) {
            PropertyMessage propMsg = (PropertyMessage) msg;
            Boolean combat = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_COMBATSTATE);
            if (combat != null && propMsg.getSubject().equals(obj.getOid())) {
                if (combat) {
                    Log.debug("RadiusRoamBehavior.onMessage: mob is in combat");
                    inCombat = true;
                } else {
                    Log.debug("RadiusRoamBehavior.onMessage: mob is not in combat");
                    inCombat = false;
                    if (randomLingerEachTime) {
                        int bound = (int) (maxLingerTime - minLingerTime);
                        if (bound > 0) {
                            lingerTime = minLingerTime + rand.nextInt(bound);
                        } else {
                            lingerTime = minLingerTime;
                            Log.warn("RadiusRoamBehavior: maxLingerTime - minLingerTime <= 0. Setting lingerTime to minLingerTime: " + lingerTime);
                        }
                    }
                    Engine.getExecutor().schedule(this, lingerTime, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    public void setCenterLoc(Point loc) {
	centerLoc = loc;
    }
    public Point getCenterLoc() {
	return centerLoc;
    }
    protected Point centerLoc = null;

    public void setRadius(int radius) {
	this.radius = radius;
    }
    public int getRadius() {
	return radius;
    }
    protected int radius = 0;

    public void setLingerTime(long time) {
        lingerTime = time;
    }
    public long getLingerTime() {
        return lingerTime;
    }
    protected long lingerTime = 5000;

	public long getMinLingerTime() {return minLingerTime;}
	public void setMinLingerTime(long minLingerTime) {
		this.minLingerTime = minLingerTime;
		Log.debug("RadiusRoamBehavior.setMinLingerTime: " + minLingerTime);
		// Ensure maxLingerTime >= minLingerTime + 1
		if (this.maxLingerTime < this.minLingerTime + 1) {
			this.maxLingerTime = this.minLingerTime + 1;
			Log.warn("RadiusRoamBehavior: maxLingerTime adjusted to " + this.maxLingerTime + " to be greater than minLingerTime " + this.minLingerTime);
		}
	}protected long minLingerTime = 5000;

	public long getMaxLingerTime() {return maxLingerTime;}
	public void setMaxLingerTime(long maxLingerTime) {
		if (maxLingerTime > this.minLingerTime) {
			this.maxLingerTime = maxLingerTime;
			Log.debug("RadiusRoamBehavior.setMaxLingerTime: " + maxLingerTime);
		} else {
			this.maxLingerTime = this.minLingerTime + 1;
			Log.warn("RadiusRoamBehavior.setMaxLingerTime: Adjusted to " + this.maxLingerTime + " to ensure it is greater than minLingerTime " + this.minLingerTime);
		}
	}

	protected long maxLingerTime = 5000;

	public boolean getRandomLingerEachTime() {return randomLingerEachTime;}
	public void setRandomLingerEachTime(boolean randomLingerEachTime) {this.randomLingerEachTime = randomLingerEachTime;}
	protected boolean randomLingerEachTime = false;

    public void setMovementSpeed(float speed) {
        this.speed = speed;
    }
    public float getMovementSpeed() {
        return speed;
    }
    protected float speed = 2.2f;

    public void startRoam() {
    	Engine.getExecutor().schedule(this, 1000L, TimeUnit.MILLISECONDS);
    }

	protected void nextRoam() {
		Point roamPoint = Points.findNearby(centerLoc, radius);
		if (Log.loggingDebug)
			Log.debug("Got next roam Point: " + roamPoint + " speed=" + speed);

		InterpolatedWorldNode wnode = obj.getWorldNode();
		Point loc = null;
		if (wnode != null) {
			loc = wnode.getCurrentLoc();
		}
		if (Log.loggingDebug)Log.debug("RadiusRoamBehavior.sendCommand GoToRoamCommand " + obj.getOid() + " loc=" + loc + " dist=" + (loc != null && centerLoc != null ? Point.distanceTo(loc, centerLoc) : "bd") + " centerLoc=" + centerLoc
				+ " radius=" + radius + " speed=" + speed);

		Engine.getAgent().sendBroadcast(new BaseBehavior.GotoRoamCommandMessage(obj, centerLoc, radius, speed));
	}

    public void run() {
	    if (activated == false) {
	    	if(Log.loggingDebug)Log.debug("RadiusRoamBehavior.run "+obj.getOid()+" not activated");
	        return;
	    }
	    
	    boolean inCombat = false;
		for (Behavior behav : obj.getBehaviors()) {
			if (behav instanceof CombatBehavior) {
				CombatBehavior cBehav = (CombatBehavior) behav;
				inCombat = cBehav.getInCombat();
				if(cBehav.selectedBehavior!=null && cBehav.selectedBehavior.behaviorType==3) {
					inCombat = true;
				} else if (cBehav.selectedBehavior != null && cBehav.selectedBehavior.behaviorType == 5 && cBehav.currentTarget != null) {
					inCombat = true;
				} else if(cBehav.selectedBehavior == null) {
					inCombat = false;
				}
			}
			
		}
		if (inCombat) {
			Engine.getExecutor().schedule(this, 1000L, TimeUnit.MILLISECONDS);
			wasInCombat = true;
			return;
		}
		if (Log.loggingDebug)Log.debug("PATH: start calculate new roam point wasInCombat="+wasInCombat+" for "+obj.getOid());
	    if (!inCombat) {
	    	wasInCombat = false;
            nextRoam();
	    }else {
	    	if(Log.loggingDebug)Log.debug("RadiusRoamBehavior.run "+obj.getOid()+" in combat");
	    }
    }
    public boolean wasInCombat = false;
    protected boolean inCombat = false;
	Random rand = new Random();
    protected boolean activated = false;

    private static final long serialVersionUID = 1L;
}
