package atavism.agis.objects;

import java.util.ArrayList;
import java.util.LinkedList;

import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.util.Log;

public class ArenaTeam {
    public ArenaTeam() {
    }
    
    protected int teamNum;
    protected String teamName;
    protected boolean teamActive;
    protected int teamScore;
    protected int teamGoal;
    protected int minMembers;
    protected Point spawnPoint;
    
    protected LinkedList<ArenaMember> teamMembers;
    protected LinkedList<ArenaMember> activeMembers;
    
    public void initialiseTeam(int teamNum, String teamName, int teamGoal, int minMembers, Point spawnPoint) {
    	this.teamNum = teamNum;
    	this.teamName = teamName;
    	this.teamGoal = teamGoal;
    	this.minMembers = minMembers;
    	this.teamActive = true;
    	this.teamScore = 0;
    	this.spawnPoint = spawnPoint;
    	this.teamMembers = new LinkedList<ArenaMember>();
    	this.activeMembers = new LinkedList<ArenaMember>();
    }
    
    public void addTeamMember(OID oid, String name, String race, int base_speed, boolean useWeapons, boolean useHealth) {
    	ArenaMember member = new ArenaMember(oid, name, teamNum, base_speed, useWeapons, useHealth);
    	member.setProperty("race", race);
    	teamMembers.add(member);
    	activeMembers.add(member);
    }
    
    public boolean hasMember(OID oid) {
    	for (ArenaMember member : teamMembers) {
    		if (member.getOid().equals(oid))
    			return true;
    	}
    	return false;
    }
    
    public ArenaMember getTeamMember(OID oid) {
    	for (ArenaMember member : teamMembers) {
    		if (member.getOid().equals(oid))
    			return member;
    	}
    	return null;
    }
    
    public ArenaMember getTeamMember(int pos) {
    	if (teamMembers.size() >= pos)
    		return teamMembers.get(pos);
    	return null;
    }
    
    public ArrayList<OID> getTeamMembersOids() {
    	ArrayList<OID> oids = new ArrayList<OID>();
    	for (ArenaMember member : teamMembers) {
    		oids.add(member.getOid());
    	}
    	return oids;
    }

	public boolean isActiveTeamMember(OID oid) {
		for (ArenaMember member : activeMembers) {
			if (member.getOid().equals(oid))
				return true;
		}
		return false;
	}
    public ArenaMember removePlayer(OID oid) {
    	for (ArenaMember member : activeMembers) {
    		if (member.getOid().equals(oid)) {
    			activeMembers.remove(member);
    			Log.debug("ARENA: removed player: " + oid + " from arena team");
    			if (activeMembers.size() == 0)
    				teamActive = false;
    			return member;
    		}
    	}
    	return null;
    }
    
    public void playTeamDeathAnimations() {
    	for (ArenaMember member : teamMembers) {
    		member.playDeathAnimation();
    	}
    }
    
    public void playTeamVictoryAnimations() {
    	for (ArenaMember member : teamMembers) {
    		member.playVictoryAnimation();
    	}
    }
    
    public void updateScore(int delta) {
    	this.teamScore += delta;
    }
    
    public int getTeamSize() {
    	return teamMembers.size();
    }
    
    public String getTeamName() {
    	return teamName;
    }
    public void setTeamActive(boolean active) {
    	this.teamActive = active;
    }
    public boolean getTeamActive() {
    	return teamActive;
    }
    public int getTeamScore() {
    	return teamScore;
    }
    public void setTeamScore(int teamScore) {
    	this.teamScore = teamScore;
    }
    public int getTeamGoal() {
    	return teamGoal;
    }
    public void setSpawnPoint(Point spawnPoint) {
    	this.spawnPoint = spawnPoint;
    }
    public Point getSpawnPoint() {
    	return spawnPoint;
    }
    
    public LinkedList<ArenaMember> getTeamMembers() {
    	return teamMembers;
    }
    public LinkedList<ArenaMember> getActiveMembers() {
    	return activeMembers;
    }
}