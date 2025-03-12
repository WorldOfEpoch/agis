package atavism.agis.core;

import atavism.server.engine.*;
import atavism.server.network.AOByteBuffer;
import atavism.server.network.ClientSerializable;
import atavism.server.util.*;
import atavism.agis.core.Cooldown.State;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatClient.CooldownMessage;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.ExtendedCombatMessages;

import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class Cooldown implements Serializable, ClientSerializable  {
    public Cooldown() {
    }

    public Cooldown(String id) {
        setID(id);
    }

    public Cooldown(String id, long duration) {
            setID(id);
        setDuration(duration);
    }
    public Cooldown(String id, long duration, long startTime) {
        setID(id);
        setDuration(duration);
        setStartTime(startTime);
      //  if (startTime!=0)
        	
     }
	 public Cooldown(Cooldown cooldown) {
		setID(cooldown.getID());
		setDuration(cooldown.getDuration());
		setStartTime(cooldown.getStartTime());
	 }
    public String toString() {
        return "[Cooldown: Id=" + getID() + " Duration=" + getDuration() + " StartTime="+getStartTime()+" diffTime="+(System.currentTimeMillis() - getStartTime())+"]";
    }

	public boolean equals(Object c){
		return getID().equals(((Cooldown)c).getID());
	}

	public long getDuration() { return duration; }
    public void setDuration(long dur) { duration = dur; }
    protected long duration = 0;

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime() {
		startTime = System.currentTimeMillis();
		if(Log.loggingDebug)
			Log.debug("Cooldown SetStartTime " + System.currentTimeMillis());
	}

	public void setStartTime(long time) {
		startTime = time;
		if(Log.loggingDebug)
			Log.debug("Cooldown SetStartTime time=" + time + " " + System.currentTimeMillis());
	}

	protected long startTime = 0;

	public String getID() {
		return id;
	}

	public void setID(String id) {
		this.id = id;
	}

	protected String id;

	public static void activateCooldown(Cooldown cd, CooldownObject obj) {
		State state = new State(cd.getID(), cd.getDuration(), obj);
		state.start();
		Engine.getAgent().sendBroadcast(new CombatClient.CooldownMessage(state));
	}

	public static void activateCooldowns(Collection<Cooldown> cooldowns, CooldownObject obj, double cGlobalMod, double cWeaponMod, double cMod, int attack_speed) {
		activateCooldowns(cooldowns, obj, cGlobalMod, cWeaponMod, cMod, attack_speed, false);
	}

    public static void activateCooldowns(Collection<Cooldown> cooldowns, CooldownObject obj, double cGlobalMod, double cWeaponMod, double cMod , int attack_speed, boolean cNew) {
        CooldownMessage msg = new CooldownMessage(obj.getOid());
      //  LinkedList<Cooldown> cooldownList = (LinkedList<Cooldown>)CombatPlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "cooldowns");
        LinkedList<Cooldown> cooldownList = CombatPlugin.getCooldowns(obj.getOid());
        if(Log.loggingDebug)
    		 if (cooldownList!=null)
    			 Log.debug("Cooldown.activateCooldowns cooldownList size "+cooldownList.size());
        for (Cooldown cd : cooldowns) {
        	if(Log.loggingDebug)
        		Log.debug("Cooldown.activateCooldowns "+cd);
        	 for (int i=cooldownList.size()-1; i>=0;--i) {
        		 if ((cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration() ) < System.currentTimeMillis()) {
        			 if(Log.loggingDebug)
        					 Log.debug("Cooldown.activateCooldowns remove "+cooldownList.get(i));
        			 if(Log.loggingDebug)
        					Log.debug("Cooldown.activateCooldowns remove St:"+cooldownList.get(i).getStartTime() +" d:"+cooldownList.get(i).getDuration()+" E=>"+ (cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration() )+"  sysT: "+System.currentTimeMillis()+ " R:"+(System.currentTimeMillis()-(cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration() )));
        			 cooldownList.remove(i);
        			 if(Log.loggingDebug)
        					Log.debug("Cooldown.activateCooldowns remove "+i+" element");
              	   	 } else 
        		 if (cooldownList.get(i).getID().equals(cd.getID() ) ) {
        			 if (cd.getStartTime() > 0 && !cNew) {
        				 cooldownList.get(i).setStartTime(cd.getStartTime());
        			 }else {
        				 cooldownList.get(i).setStartTime();
        			 }
        			 if(Log.loggingDebug)
        					 Log.debug("Cooldown.activateCooldowns Set Time Start "+cd +" cd.getStartTime()="+cd.getStartTime());
                }/*else {
                	    	if (cd.getStartTime()==0) cd.setStartTime();
        			 Log.error("Cooldown.activateCooldowns add "+cd);
        	        		 cooldownList.add(cd);
        		 }*/
        	 }
			double cdDur = cd.getDuration();
			double mod = cMod;
			if (cd.getID().equals("GLOBAL"))
				mod = cGlobalMod;
			if (cd.getID().equals("WEAPON")) {
				mod = cWeaponMod;
				if (CombatPlugin.ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED) {
					cdDur = attack_speed;
				}
			}
			Cooldown ncd = new Cooldown(cd);
			double length = cdDur * (mod / 100d);
			int duration = (int) length;
			ncd.setDuration(duration);
			if(Log.loggingDebug)Log.debug("Cooldown.activateCooldowns: cGlobalMod="+cGlobalMod+" cWeaponMod="+cWeaponMod+" cMod="+cMod+" cdDur="+cdDur+" mod="+mod);
			if(cNew) {
				ncd.setStartTime();
			}
			if (ncd.getStartTime() == 0)
				ncd.setStartTime();
			if (ncd.getStartTime() + ncd.getDuration() < System.currentTimeMillis())
				ncd.setStartTime();// Test

			if(Log.loggingDebug)Log.debug("Cooldown.activateCooldowns: duration="+duration+" length="+length);
			
			State state = new State(cd.getID(), duration, obj);
			if(Log.loggingDebug)
					Log.debug("Cooldown.activateCooldowns getStartTime " + cd.getStartTime());

			if (ncd.getStartTime() > 0) {
				state.setStartTime(ncd.getStartTime());
			}
			state.start();
			if (!cooldownList.contains(ncd)) {
				if(Log.loggingDebug)
						Log.debug("Cooldown.activateCooldowns cooldownList not contains " + cd);
				if (ncd.getStartTime() == 0) {
					ncd.setStartTime();
				}

				cooldownList.add(ncd);
			} else {
				if(Log.loggingDebug)
					Log.debug("Cooldown.activateCooldowns cooldownList contains " + cd);

			}
			msg.addCooldown(state);
			if(Log.loggingDebug)
				Log.debug("Cooldown.activateCooldowns cooldownList size " + cooldownList.size() + " " + cooldownList);

		}
        if(Log.loggingDebug)
    		Log.debug("Cooldown.activateCooldowns set list before " + cooldownList.size() + " " + cooldownList);
		CombatPlugin.saveCooldowns(obj.getOid(), cooldownList);
		// EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), CombatClient.NAMESPACE, "cooldowns", cooldownList);
		Log.debug("Cooldown.activateCooldowns set list after ");
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("Cooldown.activateCooldowns set msg after ");
	}

    // returns true if none of the Cooldowns in cdset are active, false otherwise
    public static boolean checkReady(Collection<Cooldown> cdset, CooldownObject obj) {
    	if (obj == null) {
    		return true;
    	}
        for (Cooldown cd : cdset) {
        	if(Log.loggingDebug)
        		Log.debug("Cooldown.checkReady "+cd.toString());
            if (cd != null && obj.getCooldownState(cd.getID()) != null) {
            	if(Log.loggingDebug)
            		Log.debug("Cooldown.checkReady state not null TimeRemaining:"+obj.getCooldownState(cd.getID()).getTimeRemaining());
            	if (obj.getCooldownState(cd.getID()).getTimeRemaining()>0)	
            		return false;
            }
            
        }
        return true;
    }

    public static boolean checkReady(Collection<Cooldown> cdset, Map<String, State> cooldown) {
    	if (cooldown == null) {
    		return true;
    	}
        for (Cooldown cd : cdset) {
        	if(Log.loggingDebug)
        		Log.debug("Cooldown.checkReady "+cd.toString());
            if (cd != null && cooldown.get(cd.getID()) != null) {
            	if(Log.loggingDebug)
            		Log.debug("Cooldown.checkReady state not null TimeRemaining:"+cooldown.get(cd.getID()).getTimeRemaining());
            	if (cooldown.get(cd.getID()).getTimeRemaining()>0)	
            		return false;
            }
        }
        return true;
    }
    
    public static void resumeCooldowns(CooldownObject obj, Collection<State> cooldowns) {
        for (State state : cooldowns) {
            state.resume();
        }
    }

    public static void abortCooldown(Collection<Cooldown> cooldowns, CooldownObject obj, String cdID) {
	CooldownMessage msg = new CooldownMessage(obj.getOid());
	for (Cooldown cd: cooldowns) {
	    if (cdID.equals(cd.getID())) {
		State state = obj.getCooldownState(cdID);
		if (state != null) {
		    state.cancel();
		}
		msg.addCooldown(state);
	    }
	}
	Engine.getAgent().sendBroadcast(msg);
    }

    public static void abortAllCooldowns(Collection<Cooldown> cooldowns, CooldownObject obj) {
	CooldownMessage msg = new CooldownMessage(obj.getOid());
	for (Cooldown cd: cooldowns) {
	    String cdID = cd.getID();
	    State state = obj.getCooldownState(cdID);
	    if (state != null) {
		state.cancel();
	    }
	    msg.addCooldown(state);
	}
	Engine.getAgent().sendBroadcast(msg);
    }

    public static class State implements Runnable, Serializable {
        public State() {
        }

        public State(String id, long duration, CooldownObject obj) {
            setID(id);
            setDuration(duration);
            setObject(obj);
        }

        public String getID() { return id; }
        public void setID(String id) { this.id = id; }
        protected String id = "UNINIT";

        public CooldownObject getObject() { return obj; }
        public void setObject(CooldownObject obj) { this.obj = obj; }
        protected transient CooldownObject obj = null;

        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        protected long duration = 0;

        public long getTimeRemaining() { return endTime - System.currentTimeMillis(); }
        public void setTimeRemaining(long time) { if (startTime == 0) endTime = System.currentTimeMillis() + time; else endTime = startTime + time;}
        
        public long getEndTime() { return endTime; }
        protected long endTime = 0;
        public void setStartTime(long time) { startTime = time ; }
        public void setStartTime() { startTime = System.currentTimeMillis() ; }
         public long getStartTime() { return startTime; }
           protected long startTime = 0;
        
        public String toString() {
        	return "[Cooldown.State: "+id+" duration="+duration+" TimeRemaining="+getTimeRemaining()+"]";
        }
		protected transient ScheduledFuture<?> future = null;
		protected transient boolean running = false;

		public void start() {
			if (running != false) {
				Log.error("Cooldown.State.start: already running");
				return;
			}
			if (Log.loggingDebug)
				Log.debug("Cooldown.State.start: " + this.toString());
			setTimeRemaining(duration);
			obj.addCooldownState(this);
			future = Engine.getExecutor().schedule(this, duration, TimeUnit.MILLISECONDS);
			running = true;
			if (Log.loggingDebug)
				Log.debug("Cooldown.State.start: Send message " + this.getID());
			ExtendedCombatMessages.sendCooldownMessage(this.getObject().getOid(), this.getID(), this.getDuration(), this.getTimeRemaining());
			if (Log.loggingDebug)
				Log.debug("Cooldown.State.start: Send message " + this.getID() + " after");
		}

        public void resume() {
            if (Log.loggingDebug)
                Log.debug("Cooldown.State.resume: resuming cooldown " + id);
            if (running != false) {
                Log.debug("Cooldown.State.resume: already running");
                return;
            }
            running = true;
            Engine.getExecutor().schedule(this, duration, TimeUnit.MILLISECONDS);
        }

        public void run() {
            if (running != true) {
                Log.error("Cooldown.State.run: not running");
                return;
            }
            Log.debug("Cooldown.State.run: "+this.toString());
                try {
                obj.removeCooldownState(this);
            }
            catch (Exception e) {
                Log.exception("Cooldown.State.run", e);
            }
            running = false;
        }

        public void cancel() {
            if (running != true) {
                Log.error("Cooldown.State.cancel: not running");
                return;
            }
            if(Log.loggingDebug)
        		Log.debug("Cooldown.State.cancel: "+this.toString());
            
            running = false;
            obj.removeCooldownState(this);
            future.cancel(false);
        }
        
        public void timeAdjustment(Long adjustment) {
        	if (running != true) {
                Log.error("Cooldown.State.run: not running");
                return;
            }
        	if(Log.loggingDebug)
        		  Log.debug("Cooldown.State.timeAdjustment: "+this.toString());
            
        	Long timeLeft = endTime - System.currentTimeMillis();
        	if (adjustment == -1)
        		timeLeft = duration;
        	else
        		timeLeft += adjustment;
        	Engine.getExecutor().remove(this);
        	Engine.getExecutor().schedule(this, duration, TimeUnit.MILLISECONDS);
        	ExtendedCombatMessages.sendCooldownMessage(this.getObject().getOid(), this.getID(), this.getDuration(),this.getStartTime());
        }

        private static final long serialVersionUID = 1L;
    }

    public static interface CooldownObject {
        public void addCooldownState(State state);
        public void removeCooldownState(State state);
        public State getCooldownState(String id);
        public OID getOid();
    }

	@Override
	public void encodeObject(AOByteBuffer buffer) {
		// TODO Auto-generated method stub
		
	}
}
