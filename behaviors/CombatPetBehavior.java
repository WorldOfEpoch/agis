package atavism.agis.behaviors;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.msgsys.*;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.server.plugins.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.agis.plugins.*;
import atavism.agis.objects.*;

public class CombatPetBehavior extends Behavior implements Runnable {

    public CombatPetBehavior() {
	}

	public CombatPetBehavior(SpawnData data) {
		super(data);
		String value = (String) data.getProperty("combat.reactionRadius");
		if (value != null) {
			setReactionRadius(Integer.valueOf(value));
		}
		value = (String) data.getProperty("combat.movementSpeed");
		if (value != null) {
			setMovementSpeed(Integer.valueOf(value));
		}
	}

	public void initialize() {
		Log.debug("CombatPetBehavior.initialize: "+obj.getOid());
        SubscriptionManager.get().subscribe(this, obj.getOid(), CombatClient.MSG_TYPE_DAMAGE, PropertyMessage.MSG_TYPE_PROPERTY,
                CombatClient.MSG_TYPE_COMBAT_LOGOUT, AgisMobClient.MSG_TYPE_PET_COMMAND_UPDATE,
                ObjectTracker.MSG_TYPE_NOTIFY_AGGRO_RADIUS, ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS,
                CombatClient.MSG_TYPE_AUTO_ATTACK_COMPLETED);
		// Another filter to pick up when a players faction rep changes
        SubscriptionManager.get().subscribe(this, ownerOid, CombatClient.MSG_TYPE_DAMAGE, PropertyMessage.MSG_TYPE_PROPERTY,
                CombatClient.MSG_TYPE_FACTION_UPDATE);
	}

	public void activate() {
		activated = true;
		Log.debug("CombatPetBehavior.activate: "+obj.getOid());
		MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), reactionRadius);
		scheduledTask = Engine.getExecutor().scheduleAtFixedRate(this, 10, 1, TimeUnit.SECONDS);
	}

	public void deactivate() {
		Log.debug("CombatPetBehavior.deactivate: "+obj.getOid());
		lock.lock();
		try {
			activated = false;
			SubscriptionManager.get().unsubscribe(this);
//			if (eventSub != null) {
//				Engine.getAgent().removeSubscription(eventSub);
//				eventSub = null;
//			}
//			if (eventSub2 != null) {
//				Engine.getAgent().removeSubscription(eventSub2);
//				eventSub2 = null;
//			}
			if (scheduledTask != null) {
			    scheduledTask.cancel(false);
			    scheduledTask = null;
			}
		} finally {
			lock.unlock();
		}
	}

	public void handleMessage(Message msg, int flags) {
	//	long start = System.nanoTime();
		lock.lock();
		try {
			if (activated == false)
				return;
			// Command handlers
			if (msg.getMsgType() == AgisMobClient.MSG_TYPE_PET_COMMAND_UPDATE) {
				AgisMobClient.petCommandUpdateMessage pcuMsg = (AgisMobClient.petCommandUpdateMessage) msg;
				int commandVal = pcuMsg.getCommand();
				Log.debug("CombatPetBehavior.handleMessage: " + obj.getOid() + " owner:" + ownerOid + " got petCommandUpdateMessage commandVal:" + commandVal);

				if (commandVal == -3) {
					attackTarget(pcuMsg.getTarget());
					currentCommand = -3;
				} else if (commandVal == -2) {
					Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, new EntityHandle(ownerOid), speed, hitBoxRange));
					currentCommand = -2;
				} else if (commandVal == -1) {
					Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj));
					currentCommand = -1;
				} else if (commandVal == 1) {
					CombatClient.autoAttack(obj.getOid(), null, false);
					attitude = 1;
				} else if (commandVal == 2) {
					attitude = 2;
				} else if (commandVal == 3) {
					attitude = 3;
				}
			}else if (msg instanceof CombatClient.DamageMessage) {
				CombatClient.DamageMessage dmgMsg = (CombatClient.DamageMessage) msg;
				OID attackerOid = dmgMsg.getAttackerOid();
				OID targetOid = dmgMsg.getTargetOid();
					Log.debug("CombatPetBehavior.handleMessage: " + obj.getOid() + " owner:" + ownerOid + " got dmg from " + dmgMsg.getAttackerOid() + " Subject:" + dmgMsg.getSubject());
				//No passive 
				if (attitude != 1 && !attackerOid.equals(obj.getOid()) && currentCommand != -3) {
					Log.debug("CombatPetBehavior.handleMessage: " + obj.getOid() + " owner:" + ownerOid + "  got dmg from " + dmgMsg.getAttackerOid() + " Subject:" + dmgMsg.getSubject() + " pet is no passive and not have attack command - Start Attack");
					attackTarget(attackerOid);
				}
			
			}else if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_AGGRO_RADIUS) {
				ObjectTracker.NotifyAggroRadiusMessage nMsg = (ObjectTracker.NotifyAggroRadiusMessage) msg;
				Log.debug("CombatPetBehavior.handleMessage: got in aggro range message=" + nMsg);
				OID subjectOid = nMsg.getSubject();
				// Check if target is dead
				if (!CombatPlugin.isPlayerAlive(subjectOid)) {
					attackTarget(null);
					return;
				}
				Log.debug("CombatPetBehavior.handleMessage: adding target to threat map");
				// check if target is Immune or a Spirit
				// TODO: Uncomment this - but also do it in a more efficient way
				/*
				 * String state = (String)EnginePlugin.getObjectProperty(subjectOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_STATE); 
				 * if (state == null || state.equals(CombatInfo.COMBAT_STATE_IMMUNE) ||  state.equals(CombatInfo.COMBAT_STATE_SPIRIT)) { return; }
				 */
				Log.debug("CombatPetBehavior.handleMessage: Aggro "+currentTarget+ " "+attitude);
				
				if (currentTarget == null && attitude == 3 ) {
					Log.debug("CombatPetBehavior.onMessage: Aggro: currentTarget == null && attitude == 3 - Start Attack");
							attackTarget(nMsg.getSubject());
				}
			}else if (msg instanceof PropertyMessage) {
				PropertyMessage propMsg = (PropertyMessage) msg;
				if (propMsg.getPropertyMapRef().containsKey(CombatInfo.COMBAT_PROP_DEADSTATE)) {
					Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
					if (dead != null && dead) {
						if (Log.loggingDebug)
							Log.debug("CombatPetBehavior.handleMessage: obj=" + obj + " got death=" + propMsg.getSubject() + " currentTarget=" + currentTarget);
						if (propMsg.getSubject().equals(obj.getOid())) {
							Log.debug("CombatPetBehavior.handleMessage: mob died, deactivating all behaviors");
							for (Behavior behav : obj.getBehaviors()) {
								behav.deactivate();
								obj.removeBehavior(behav);
							}
						} else if (propMsg.getSubject().equals(currentTarget)) {
							Log.debug("CombatPetBehavior.handleMessage: target is death set follow Owner ");
							long lastUseAbility = 0;
							int lastUsedAbility = -1;
							CombatBehavior cBehav = null;
							for (Behavior behav : obj.getBehaviors()) {
								if (behav instanceof CombatBehavior) {
									cBehav = (CombatBehavior) behav;
									if (Log.loggingDebug)Log.debug("CombatPetBehavior.handleMessage:  CombatBehavior ="+cBehav.inCombat+" "+cBehav.combatStartLoc);
									if (cBehav.inCombat) {
										cBehav.inCombat = false;
										CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET_END);
									}
									cBehav.combatStartLoc = null;
									lastUsedAbility = cBehav.lastUsedAbility;
									lastUseAbility = cBehav.lastUseAbility;
								}
							}

							DelayedReset delayedReset = new DelayedReset();
							if(lastUseAbility>0 && lastUsedAbility>0) {
								AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
								if (!lastAA.getCastingInRun()) {
									long castingTime = cBehav.calculateCastingTime(lastAA.getActivationTime());
									if (!lastAA.getCastingInRun() && lastAA.getActivationTime() > 0) {
										castingTime += lastAA.getAttackTime();
									}

									if (castingTime + lastUseAbility+200 <= System.currentTimeMillis()) {
										if (Log.loggingDebug)
											Log.debug("CombatPetBehavior.handleMessage: obj=" + obj + " got death=" + propMsg.getSubject() + " Run reset "+(System.currentTimeMillis() - (castingTime + lastUseAbility)));
										delayedReset.run();
									} else {
										if (Log.loggingDebug)
											Log.debug("CombatPetBehavior.handleMessage: obj=" + obj + " got death=" + propMsg.getSubject() + " Delay reset "+(System.currentTimeMillis() - (castingTime + lastUseAbility)));
										Engine.getExecutor().schedule(delayedReset, (System.currentTimeMillis() - (castingTime + lastUseAbility))+200L, TimeUnit.MILLISECONDS);
									}
								}
							}
						} else {
							if (Log.loggingDebug)
								Log.debug("CombatPetBehavior.handleMessage: obj=" + obj + " got death=" + propMsg.getSubject() + " currentTarget=" + currentTarget + " else");

						}
					}
				} else if (propMsg.getPropertyMapRef().containsKey(FactionStateInfo.TEMPORARY_FACTION_PROP)) {
					if (propMsg.getSubject().equals(ownerOid)) {
						String tempFaction = (String) propMsg.getProperty(FactionStateInfo.TEMPORARY_FACTION_PROP);
						Log.debug("CombatPetBehaviors.handleMessage PropertyMessage Faction property for Owner pet Oid" + obj.getOid() + " MsgObjOid=" + propMsg.getSubject()+" tempFaction="+tempFaction);
						
						EnginePlugin.setObjectProperty(obj.getOid(), Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, tempFaction);
					} else {
						Log.debug("CombatPetBehaviors.handleMessage PropertyMessage Faction property for not Owner pet Oid" + obj.getOid() + " MsgObjOid=" + propMsg.getSubject());
					}

				} else {
					Log.debug("CombatPetBehaviors.handleMessage PropertyMessage unknown property=" + propMsg.getPropertyMapRef().keySet());
				}

			}else if (msg.getMsgType() == CombatClient.MSG_TYPE_COMBAT_LOGOUT) {
				CombatClient.CombatLogoutMessage clMsg = (CombatClient.CombatLogoutMessage) msg;
				OID subjectOid = clMsg.getSubject();
				OID playerOid = clMsg.getPlayerOid();
				Log.debug("CombatPetBehavior.onMessage Logout reaction. Obj: " + obj.getOid() + "; target: " + playerOid + "; subject: " + subjectOid);
				targetsInRange.remove(playerOid);
				// CombatClient.updateRemoveAttitude(obj.getOid(), playerOid);
			}else {
				Log.debug("CombatPetBehavior.handleMessage got unknown Msgtype="+msg.getMsgType()+"  obj="+obj.getOid()+" owner="+ownerOid);
			}
/*			if (msg.getMsgType() == CombatClient.MSG_TYPE_FACTION_UPDATE) {
				CombatClient.FactionUpdateMessage clMsg = (CombatClient.FactionUpdateMessage) msg;
				OID subjectOid = clMsg.getSubject();
			
				
				//EnginePlugin.setObjectProperty(obj.getOid(), Namespace.FACTION, FactionStateInfo.FACTION_PROP, faction);
				EnginePlugin.setObjectProperty(obj.getOid(), Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, tempFaction);
				
			}*/
			
			// return true;
		} finally {
			lock.unlock();
		}
		//long end = System.nanoTime();
		//Log.error("COMBAT PET BEHAVIOR: handleMessage Time " + start + " " + end + " " + (end - start) + " ns "+ obj.getOid());

	}


	class DelayedReset implements Runnable {
		public void run() {
			Log.debug("CombatPetBehavior.DelayedReset.run");
			Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, new EntityHandle(ownerOid), speed, hitBoxRange));
			PropertyMessage pMsg = new PropertyMessage(obj.getOid());
			pMsg.setProperty("facing", ownerOid);
			Engine.getAgent().sendBroadcast(pMsg);
			Log.debug("Set Follow Owner target death1");
			resetTarget();
			currentCommand = -2;

		}
	}
	protected void resetTarget(){
		if (Log.loggingDebug)
			Log.debug("CombatPetBehavior.resetTarget: obj=" + obj );
		if (targetSub != null) {
			SubscriptionManager.get().unsubscribe(this, targetSub, PropertyMessage.MSG_TYPE_PROPERTY);
			targetSub = null;
		}
		currentTarget = null;
		if(currentCommand!=-2)
			Engine.getAgent().sendBroadcast(new BaseBehavior.ArrivedEventMessage(obj));
		inCombat = false;
	}

	protected void attackTarget(OID targetOid) {
		if (Log.loggingDebug)
			Log.debug("CombatPetBehavior.attackTarget: obj=" + obj + " targetOid=" + targetOid);
		if (targetSub != null) {
		    SubscriptionManager.get().unsubscribe(this, targetSub, PropertyMessage.MSG_TYPE_PROPERTY);
		    targetSub = null;
		}
		OID prevTarget = currentTarget;
		currentTarget = targetOid;

		if (currentTarget != null) {
			SubscriptionManager.get().subscribe(this, targetOid, PropertyMessage.MSG_TYPE_PROPERTY);
			targetSub = targetOid;

		//	Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, new EntityHandle(currentTarget), speed, hitBoxRange));
			//CombatClient.autoAttack(obj.getOid(), currentTarget, true);

			CombatClient.sendAlterThreat(obj.getOid(), currentTarget,10);


			inCombat = true;
			Engine.getExecutor().schedule(cdt, 1000, TimeUnit.MILLISECONDS);
			// TODO: Update attitude to hated

			// Update facing
			/*
			 * Quaternion newOrientation; AOVector newDir = new AOVector(destLoc);
			 * newDir.sub(myLoc); if (!newDir.isZero()){ newDir.normalize(); newOrientation
			 * = Quaternion.fromVectorRotation(AOVector.UnitZ, newDir);
			 * newDir.multiply(mobSpeed); InterpolatedWorldNode curWnode =
			 * obj.getWorldNode(); BasicWorldNode updateNode = new BasicWorldNode(curWnode);
			 * updateNode.setDir(newDir); updateNode.setOrientation(newOrientation);
			 * WorldManagerClient.UpdateWorldNodeMessage upMsg = new
			 * WorldManagerClient.UpdateWorldNodeMessage(obj.getOid(), updateNode);
			 * 
			 * Engine.getAgent().sendBroadcast(upMsg); }
			 */
		} else {
			//CombatClient.autoAttack(obj.getOid(), null, false);
			CombatClient.sendAlterThreat(obj.getOid(), prevTarget,-1000);
			if(currentCommand!=-2)
				Engine.getAgent().sendBroadcast(new BaseBehavior.ArrivedEventMessage(obj));
			inCombat = false;
		}
	}

	public void run() {
		if (activated == false) {
			return;
		}
	}

	/**
	 * A runnable class to check if the mob has chased a target too far from its
	 * original location.
	 * 
	 * @author Andrew
	 *
	 */
	class CheckDistanceTravelled implements Runnable, Serializable {
		public void run() {
			checkDistance();
		}

		private void checkDistance() {
			if (centerLoc == null) {
				return;
			}
			InterpolatedWorldNode wnode = obj.getWorldNode();
			if (wnode == null) {
				Log.error("CombatPetBehavior.AGGRO: got null wnode during distance check for oid: " + obj.getOid());
				return;
			}
			Point loc = wnode.getCurrentLoc();
			float distance = Point.distanceTo(loc, centerLoc);
			// Log.debug("COMBAT: distance from centerLoc: " + distance);
			if ((distance > chaseDistance) && (evade == false)) {
				Log.debug("CombatPetBehavior: mob has exceeded max distance: " + chaseDistance);
				// Add in some call to stop attacking
				CombatClient.autoAttack(obj.getOid(), null, false);
				Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, centerLoc, speed));
				// Set evade to true for 10 seconds
				Log.debug("CombatPetBehavior:Evade set to true 1");
				evade = true;
				inCombat = false;
				Engine.getExecutor().schedule(this, 15000, TimeUnit.MILLISECONDS);
				Log.debug("CombatPetBehavior:Evade set to true 2");
			} else if (evade == true) {
				evade = false;
				Log.debug("CombatPetBehavior:Evade set to false");
			} else {
				Engine.getExecutor().schedule(this, 1000, TimeUnit.MILLISECONDS);
			}
		}

		private static final long serialVersionUID = 1L;
	}

	public void setMovementSpeed(float speed) {
		this.speed = speed;
	}

	public float getMovementSpeed() {
		return speed;
	}

	protected float speed = 12f;

	public void setReactionRadius(int radius) {
		reactionRadius = radius;
	}

	public int getReactionRadius() {
		return reactionRadius.intValue();
	}

	protected Integer reactionRadius = 100;

	public void setCenterLoc(Point loc) {
		centerLoc = loc;
	}

	public Point getCenterLoc() {
		return centerLoc;
	}

	protected Point centerLoc = null;

	public void setchaseDistance(int distance) {
		chaseDistance = distance;
	}

	public int getchaseDistance() {
		return chaseDistance;
	}

	int chaseDistance = 60; // How far the mob will chase a player in millimeters

	public void setHitBoxRange(float radius) {
		hitBoxRange = radius;
	}

	public float getHitBoxRange() {
		return hitBoxRange;
	}

	float hitBoxRange = 5;

	public void setOwnerOid(OID ownerOid) {
		this.ownerOid = ownerOid;
	}

	public OID getOwnerOid() {
		return ownerOid;
	}

	OID ownerOid = null;
	CheckDistanceTravelled cdt = new CheckDistanceTravelled();
	LinkedList<OID> targetsInRange = new LinkedList<OID>();
	int attitude = 2; // 1: Passive, 2: Defensive, 3: Aggressive
	int currentCommand = -2; // -1: Stay, -2: Follow, -3: Attack
	int aggroRange = 15;
	boolean evade = false;
	boolean inCombat = false;
	protected OID currentTarget = null;
    private OID targetSub = null;
	protected boolean activated = false;
    private ScheduledFuture<?> scheduledTask;
	private static final long serialVersionUID = 1L;
}
