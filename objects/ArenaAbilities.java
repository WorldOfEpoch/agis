package atavism.agis.objects;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import atavism.agis.arenas.Arena;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.DisplayContext;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;

/**
 * A class for handling Arena Abilities. It's very poor code design, but I'm doing it to save time.
 * @author Andrew
 *
 */
public class ArenaAbilities {
    public ArenaAbilities() {
    }
    
    public static boolean TargetInRange(OID caster, OID target, int range) {
		BasicWorldNode node = WorldManagerClient.getWorldNode(caster);
		if (node == null) {
			Log.error("DOME: player node was null");
		}
		Point loc = node.getLoc();
		BasicWorldNode targetNode = WorldManagerClient.getWorldNode(target);
		if (targetNode == null) {
			Log.error("DOME: target node was null");
		}
		Point targetLoc = targetNode.getLoc();
		if (Point.distanceTo(loc, targetLoc) > range) {
			ExtendedCombatMessages.sendErrorMessage(caster, "Target is too far away.");
			return false;
		}
    	return true;
    }
    
    public static void ApplyDamage(OID caster, OID target, int domeID, int damage) {
    	// Get level difference
    	int levelAlteration = 0;
    	// Randomise number
    	Random random = new Random();
    	int value = random.nextInt(100);
    	String damageType = "";
    	// Determine damage amount
    	if (value > 90) {
    		// miss
    		damage = 0;
    		damageType = "(Miss)";
    	} else if (value > (60 + levelAlteration)) {
    		damage = damage * 1;
    		damageType = "(Powerful)";
    	} else if (value > (30 + levelAlteration)) {
    		damage = damage / 2;
    		damageType = "";
    	} else {
    		damage = damage / 4;
    		damageType = "(Weak)";
    	}
    	ExtensionMessage heartMsg = new ExtensionMessage(CombatClient.MSG_TYPE_ALTER_HEARTS, null, target);
		heartMsg.setProperty("amount", -damage);
		heartMsg.setProperty("caster", caster);
		heartMsg.setProperty("domeID", domeID);
        Engine.getAgent().sendBroadcast(heartMsg);
		ExtendedCombatMessages.sendCombatText(target, "" + damage + " " + damageType, 1);
		ExtendedCombatMessages.sendCombatText2(caster, target, "" + damage + " " + damageType, 1);
    }
    
    public static void CompleteAbility(OID mobOid, OID targetOid, int damage, int domeID, int abilityID) {
    	ApplyDamage(mobOid, targetOid, domeID, damage);
    	/*ExtensionMessage heartMsg = new ExtensionMessage(CombatClient.MSG_TYPE_ALTER_HEARTS, null, targetOid);
		heartMsg.setProperty("amount", damage * -1);
		heartMsg.setProperty("caster", mobOid);
		heartMsg.setProperty("domeID", domeID);
        Engine.getAgent().sendBroadcast(heartMsg);
		ExtendedCombatMessages.sendCombatText(targetOid, "" + damage, 1);
		ExtendedCombatMessages.sendCombatText2(mobOid, targetOid, "" + damage, 1);*/
		// Send Coordinated effect
		ArenaAbilities.sendAbilityCoordinatedEffect(mobOid, targetOid, abilityID);
		
		//EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_COMBATSTATE, true);
		PropertyMessage propMsg = new PropertyMessage(mobOid);
        propMsg.setProperty(CombatInfo.COMBAT_PROP_COMBATSTATE, true);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public static void CompleteAbility(OID mobOid, ArrayList<OID> targetOids, int damage, int domeID, int abilityID) {
    	ArenaAbilities.sendAbilityCoordinatedEffect(mobOid, null, abilityID);
    	for (OID targetOid : targetOids) {
    		ApplyDamage(mobOid, targetOid, domeID, damage);
    	}
		
    	ArenaAbilities.sendAbilityCoordinatedEffect(mobOid, mobOid, abilityID);
		//EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_COMBATSTATE, true);
		PropertyMessage propMsg = new PropertyMessage(mobOid);
        propMsg.setProperty(CombatInfo.COMBAT_PROP_COMBATSTATE, true);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    /**
     * Handles the activation of an arena ability.
     * @param slot
     * @param player
     * @param target
     * @param arena
     */
    public static void ActivateAbility(int slot, ArenaMember player, ArenaMember target, Arena arena) {
    	int state = (Integer) EnginePlugin.getObjectProperty(player.getOid(), WorldManagerClient.NAMESPACE, "state");
    	if (state == 1) {
    		ExtendedCombatMessages.sendErrorMessage(player.getOid(), "You cannot activate an ability while immune.");
    		return;
    	}
    	if (player.getActive() == false)
    		return;
    	int playerabilities[] = player.getAbilities();
    	int abilityID = playerabilities[slot];
    	if (abilityID != -1) {
    		if (abilityID == ArenaAbilities.ABILITY_MELEE_ATTACK) {
    			CombatClient.startAbility(abilityID, player.getOid(), target.getOid(), null);
	            return;
    		} else if (playerabilities[slot] == ArenaAbilities.ABILITY_RANGED_ATTACK) {
    			CombatClient.startAbility(abilityID, player.getOid(), target.getOid(), null);
	            return;
    		} else if (playerabilities[slot] == ArenaAbilities.ABILITY_CREATE_TRAP) {
    			ArenaObject aObject = ArenaAbilities.CreateTrap(player.getOid(), arena.getArenaInstanceOid());
	            int playerTeam = arena.getPlayerTeam(player.getOid());
    			int enemyTeam = 0;
    			if (enemyTeam == playerTeam)
    				enemyTeam++;
	            aObject.setTeamToReactTo(enemyTeam);
	            arena.addArenaObject(aObject);
    		} else if (playerabilities[slot] == ArenaAbilities.ABILITY_SLOW_GO) {
    			LinkedList<Integer> enemyTeams = arena.getOpposingTeams(arena.getPlayerTeam(player.getOid()));
    			if (enemyTeams.isEmpty())
    				return;
    			for (ArenaMember enemyPlayer : arena.getTeam(enemyTeams.get(0)).getActiveMembers()) {
    				if (enemyPlayer.getActive())
    					CombatClient.startAbility(playerabilities[slot], player.getOid(), enemyPlayer.getOid(), null);
    			}
    			String sourceName = player.getName();
    			arena.sendMessageAll("Arena_event", sourceName + " used Slow Goo");
    		} else if (playerabilities[slot] == ArenaAbilities.ABILITY_SWAP_PLACES) {
    			int playerTeam = arena.getPlayerTeam(player.getOid());
    			int enemyTeam = 0;
    			if (enemyTeam == playerTeam)
    				enemyTeam++;
    			//SwitchPositions(teams[enemyTeam].getTeamMembersOids(), playerOid);
    		} else if (playerabilities[slot] == ArenaAbilities.ABILITY_GOBSTOPPER) {
    			int playerTeam = arena.getPlayerTeam(player.getOid());
    			int enemyTeam = 0;
    			if (enemyTeam == playerTeam)
    				enemyTeam++;
    			BasicWorldNode node = WorldManagerClient.getWorldNode(player.getOid());
    			Point loc = node.getLoc();
    			OID targetOid = ArenaAbilities.GobStopper(arena.getTeam(enemyTeam).getTeamMembersOids(), loc);
    			CombatClient.startAbility(playerabilities[slot], player.getOid(), targetOid, null);
    			String sourceName = arena.getArenaPlayer(player.getOid()).getName();
    			String targetName = arena.getArenaPlayer(targetOid).getName();
    			arena.sendMessageAll("Arena_event", targetName + " was hit by " + sourceName + "'s Gobstopper");
    		} else {
    			// It's a normal combat ability, doesn't require any special arena code.
    			CombatClient.startAbility(playerabilities[slot], player.getOid(), player.getOid(), null);
    			String sourceName = player.getName();
    			arena.sendMessageAll("Arena_event", sourceName + " used " + ArenaAbilities.getAbilityName(playerabilities[slot]));
    		}
    		playerabilities[slot] = -1;
    		arena.sendAbilities(player.getOid());
    	}
    }
    
    public static boolean checkAbility() {
    	return true;
    }
    
    /**
     * Randomly chooses someone from the enemy team to swap places with.
     * @param arena
     * @param activator
     */
    public static void SwitchPositions(ArrayList<OID> enemyTeam, OID activator) {
    	BasicWorldNode activatorNode = WorldManagerClient.getWorldNode(activator);
		Point activatorLoc = activatorNode.getLoc();
		Quaternion activatorOrient = activatorNode.getOrientation();
		AOVector activatorDir = activatorNode.getDir();
		Random random = new Random();
    	OID targetOid = enemyTeam.get(random.nextInt(enemyTeam.size()));
		BasicWorldNode targetNode = WorldManagerClient.getWorldNode(targetOid);
		Point targetLoc = targetNode.getLoc();
		Quaternion targetOrient = targetNode.getOrientation();
		AOVector targetDir = targetNode.getDir();
		// Now set the nodes to each others properties
		activatorNode.setLoc(targetLoc);
		activatorNode.setOrientation(targetOrient);
		activatorNode.setDir(targetDir);
		targetNode.setLoc(activatorLoc);
		targetNode.setOrientation(activatorOrient);
		targetNode.setDir(activatorDir);
		WorldManagerClient.updateWorldNode(activator, activatorNode, true);
		WorldManagerClient.updateWorldNode(targetOid, targetNode, true);
		WorldManagerClient.refreshWNode(activator);
		WorldManagerClient.refreshWNode(targetOid);
    }
    
    /**
     * Finds the closest Smoo and activates the GobStopper ability.
     * @param arena
     * @param activator
     */
    public static OID GobStopper(ArrayList<OID> enemyTeam, Point activatorPosition) {
    	OID closestTarget = null;
    	Point closestPosition = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		for (int j = 0; j < enemyTeam.size(); j++) {
			OID oid = enemyTeam.get(j);
			BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
			Point loc = node.getLoc();
			if (Point.distanceToSquared(loc, activatorPosition) < Point.distanceToSquared(closestPosition, loc)) {
				closestPosition = loc;
				closestTarget = oid;
			}
		}
		return closestTarget;
    }
    
    public static ArenaObject CreateTrap(OID playerOid, OID instanceOid) {
    	BasicWorldNode node = WorldManagerClient.getWorldNode(playerOid);
		Point loc = node.getLoc();
		DisplayContext dc = new DisplayContext("Star.mesh", true);	  
    	dc.setDisplayID(-4);
    	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("StaticAnim", "idle");
    	return CreateObject(loc, instanceOid, ArenaObject.ARENA_OBJECT_TRAP, dc, props);
    }
    
    public static ArenaObject CreateObject(Point loc, OID instanceOid, String objectType, DisplayContext dc, 
    		HashMap<String, Serializable> props) {
    	return new ArenaObject((int)System.currentTimeMillis(), loc, instanceOid, objectType, dc, props);
    }
    
    public static ArrayList<ArenaMember> GetEnemiesInRange(Point loc, ArenaTeam[] teams, LinkedList<Integer> opposingTeams, int range) {
    	ArrayList<ArenaMember> playersInRange = new ArrayList<ArenaMember>();
    	for (int i = 0; i < teams.length; i++) {
    		if (opposingTeams.contains(i)) {
    			for (ArenaMember member : teams[i].getActiveMembers()) {
    				if (PlayerInRange(loc, member.getOid(), range))
    					playersInRange.add(member);
    			}
    		}
    	}
    	return playersInRange;
    }
    
    public static ArrayList<ArenaMember> GetPlayersInRange(Point loc, ArenaTeam[] teams, int range) {
    	ArrayList<ArenaMember> playersInRange = new ArrayList<ArenaMember>();
    	for (int i = 0; i < teams.length; i++) {
    		for (ArenaMember member : teams[i].getActiveMembers()) {
    			if (PlayerInRange(loc, member.getOid(), range))
    				playersInRange.add(member);
    		}
    	}
    	return playersInRange;
    }
    
    public static boolean PlayerInRange(Point loc, OID player, int range) {
    	BasicWorldNode targetNode = WorldManagerClient.getWorldNode(player);
		Point targetLoc = targetNode.getLoc();
		if (Point.distanceTo(loc, targetLoc) < range) {
			return true;
		}
		return false;
    }
    
    public static void sendAbilityCoordinatedEffect(OID activator, OID target, int abilityID) {
    	String effectName = ARENA_MELEE_ATTACK_EFFECT;
    	String mobType = "Normal";
    	String attackType = "Normal";
    	if (abilityID == ABILITY_RANGED_ATTACK || abilityID == ABILITY_MOB_RANGED_ATTACK) {
    		effectName = ARENA_RANGED_ATTACK_EFFECT;
    	} else if (abilityID == ABILITY_BOSS_AOE_ATTACK) {
    		//effectName = ARENA_AOE_ATTACK_EFFECT;
    		attackType = "AoE";
    	} else if (abilityID == ABILITY_BOSS_POWER_ATTACK) {
    		attackType = "Power";
    	}
    	if (abilityID == ABILITY_BOSS_MELEE_ATTACK || abilityID <= ABILITY_BOSS_POWER_ATTACK
    			|| abilityID <= ABILITY_BOSS_AOE_ATTACK)
    		mobType = "Boss";
    	
    	CoordinatedEffect cE = new CoordinatedEffect(effectName);
    	cE.sendSourceOid(true);
		cE.sendTargetOid(true);
		cE.putArgument("weapon", "");
		cE.putArgument("mobType", mobType);
		cE.putArgument("attackType", attackType);
    	cE.invoke(activator, target);
    }
    
    /**
     * Gets the name of the ability ID passed in.
     * @param abilityID
     * @return
     */
    public static String getAbilityName(int abilityID) {
    	if (abilityID == ABILITY_SPEED_BOOST) {
    		return "Speed Boost";
    	} else if (abilityID == ABILITY_IMMUNITY) {
    		return "Immunity";
    	} else if (abilityID == ABILITY_CREATE_TRAP) {
    		return "Create Trap";
    	} else if (abilityID == ABILITY_SWAP_PLACES) {
    		return "Swap Places";
    	} else if (abilityID == ABILITY_SLOW_GO) {
    		return "Slow Goo";
    	} else if (abilityID == ABILITY_HUNGER) {
    		return "Enrage";
    	} else if (abilityID == ABILITY_GOBSTOPPER) {
    		return "Pacify";
    	} else if (abilityID == ABILITY_TRAP) {
    		return "Trap";
    	} else if (abilityID == ABILITY_BOMB) {
    		return "Bomb";
    	} else if (abilityID == ABILITY_REMOTE_DETONATION) {
    		return "Remote Detonation";
    	} else if (abilityID == ABILITY_MELEE_ATTACK) {
    		return "Melee Attack";
    	} else if (abilityID == ABILITY_RANGED_ATTACK) {
    		return "Ranged Attack";
    	}
    	return null;
    }

    // Arena Abilities
    public static final int ABILITY_SPEED_BOOST = -801;
    public static final int ABILITY_IMMUNITY = -802;
    public static final int ABILITY_CREATE_TRAP = -803;
    public static final int ABILITY_SWAP_PLACES = -804;
    public static final int ABILITY_SLOW_GO = -805;
    public static final int ABILITY_HUNGER = -601;
    public static final int ABILITY_GOBSTOPPER = -701;
    public static final int ABILITY_TRAP = -901;
    public static final int ABILITY_BOMB = -1001;
    public static final int ABILITY_REMOTE_DETONATION = -1002;
    public static final int ABILITY_MELEE_ATTACK = 1;
    public static final int ABILITY_RANGED_ATTACK = 2;
    public static final int ABILITY_UNARMED_ATTACK = 3;
    
    // Mob Abilities
    public static final int ABILITY_MOB_MELEE_ATTACK = -5101;
    public static final int ABILITY_MOB_RANGED_ATTACK = -5102;
    public static final int ABILITY_MOB_AOE_ATTACK = -5103;
    public static final int ABILITY_BOSS_MELEE_ATTACK = -5111;
    public static final int ABILITY_BOSS_POWER_ATTACK = -5112;
    public static final int ABILITY_BOSS_AOE_ATTACK = -5113;
    
    // Coordinated Effects
    public static final String ARENA_MELEE_ATTACK_EFFECT = "ArenaAttackEffect";
    public static final String ARENA_RANGED_ATTACK_EFFECT = "ArenaRangedAttackEffect";
    public static final String ARENA_AOE_ATTACK_EFFECT = "";
}
