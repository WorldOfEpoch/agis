package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;

import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.util.Log;

public class BehaviorTemplate implements Serializable {
    public BehaviorTemplate() {
    }
    
    public BehaviorTemplate(int id, String name) {
    	this.id = id;
    	this.name = name;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public String getBaseAction() { return baseAction;}
    public void setBaseAction(String baseAction) {
    	this.baseAction = baseAction;
    }
    
    public boolean getWeaponsSheathed() { return weaponsSheathed;}
    public void setWeaponsSheathed(boolean weaponsSheathed) {
    	this.weaponsSheathed = weaponsSheathed;
    }
    
    public int getRoamRadius() { return roamRadius;}
    public void setRoamRadius(int roamRadius) {
    	this.roamRadius = roamRadius;
    }

    public long getRoamDelayMin() {return roamDelayMin;}
    public void setRoamDelayMin(long roamDelayMin) {this.roamDelayMin = roamDelayMin;}

    public long getRoamDelayMax() {return roamDelayMax;}
    public void setRoamDelayMax(long roamDelayMax) {this.roamDelayMax = roamDelayMax;}

    public boolean isRoamRollTimeEachTime() {return roamRollTimeEachTime;}
    public boolean getRoamRollTimeEachTime() {return roamRollTimeEachTime;}
    public void setRoamRollTimeEachTime(boolean roamRollTimeEachTime) {this.roamRollTimeEachTime = roamRollTimeEachTime;}

    public int getPatrolPathID() { return patrolPathID;}
    public void setPatrolPathID(int patrolPathID) {
    	this.patrolPathID = patrolPathID;
    }
    
 /*   public boolean getPatrolSwinging() { return patrolSwinging;}
    public void setPatrolSwinging(boolean patrolSwinging) {
    	this.patrolSwinging = patrolSwinging;
    }
*/
    public ArrayList<Point> getPatrolPoints() { return patrolPoints;}
    public void setPatrolPoints(ArrayList<Point> patrolPoints) {
    	this.patrolPoints = patrolPoints;
    }
    public void addPatrolPoint(PatrolPoint point) {
    	patrolPoints.add(point.loc);
    	patrolPauses.add(point.lingerTime);
    }
    
    public void travelReverse() {
    	for (int i = patrolPoints.size() - 2; i > 0; i--) {
    		patrolPoints.add(patrolPoints.get(i));
    		patrolPauses.add(patrolPauses.get(i));
    	}
    	
    	String patrolString = "";
    	for (int i = 0; i < patrolPoints.size(); i++) {
    		patrolString += i + " delay: " + patrolPauses.get(i) + ", ";
    	}
    	Log.debug("PATROL: " + patrolString);
    }
    
    public ArrayList<Float> getPatrolPauses() { return patrolPauses;}
    public void setPatrolPauses(ArrayList<Float> patrolPauses) {
    	this.patrolPauses = patrolPauses;
    }
    
    public boolean getHasCombat() { return hasCombat;}
    public void setHasCombat(boolean hasCombat) {
    	this.hasCombat = hasCombat;
    }
    
    public int getAggroRadius() { return aggroRadius;}
    public void setAggroRadius(int aggroRadius) {
    	this.aggroRadius = aggroRadius;
    }
    
    public ArrayList<Integer> getStartsQuests() { return startsQuests;}
    public void setStartsQuests(ArrayList<Integer> startsQuests) {
    	this.startsQuests = startsQuests;
    }
    public ArrayList<Integer> getEndsQuests() { return endsQuests;}
    public void setEndsQuests(ArrayList<Integer> endsQuests) {
    	this.endsQuests = endsQuests;
    }
    
    public ArrayList<Integer> getStartsDialogues() { return startsDialogues;}
    public void setStartsDialogues(ArrayList<Integer> startsDialogues) {
    	this.startsDialogues = startsDialogues;
    }
    
    public int getMerchantTable() { return merchantTable;}
    public void setMerchantTable(int merchantTable) {
    	this.merchantTable = merchantTable;
    }
    
    public ArrayList<String> getOtherActions() { return otherActions;}
    public void setOtherActions(ArrayList<String> otherActions) {
    	this.otherActions = otherActions;
    }
    
    public int getQuestOpenLoot() { return questOpenLoot;}
    public void setQuestOpenLoot(int questOpenLoot) {
    	this.questOpenLoot = questOpenLoot;
    }
    
    public boolean getIsChest() { return isChest;}
    public void setIsChest(boolean isChest) {
    	this.isChest = isChest;
    }
    
    public int getPickupItem() { return pickupItem;}
    public void setPickupItem(int pickupItem) {
    	this.pickupItem = pickupItem;
    }
    
    public boolean getIsPlayerCorpse() { return isPlayerCorpse;}
    public void setIsPlayerCorpse(boolean isPlayerCorpse) {
    	this.isPlayerCorpse = isPlayerCorpse;
    }
    
    public boolean getIsPlayerShop() { return isPlayerShop;}
    public void setIsPlayerShop(boolean isPlayerShop) {
    	this.isPlayerShop = isPlayerShop;
    }
    
    public String getOtherUse() { return otherUse;}
    public void setOtherUse(String otherUse) {
    	this.otherUse = otherUse;
    }
    
    public String toString() {
    	return id + ":" + name;
    }
    
    
    public void setShopOid(OID shopOid) {
		this.shopOid = shopOid;
	}

	public OID getShopOid() {
		return this.shopOid;
	}

	OID shopOid;
    
    
    int id;
    String name;
    String baseAction = "";
    boolean weaponsSheathed = false;
    int roamRadius = 0;
    long roamDelayMin = 0;
    long roamDelayMax = 0;
    boolean roamRollTimeEachTime = true;
    int patrolPathID = -1;
  //  boolean patrolSwinging =false;
    ArrayList<Point> patrolPoints = new ArrayList<Point>();
    ArrayList<Float> patrolPauses = new ArrayList<Float>();
    boolean hasCombat = false;
    int aggroRadius = 0;
    ArrayList<Integer> startsQuests = new ArrayList<Integer>();
    ArrayList<Integer> endsQuests = new ArrayList<Integer>();
    ArrayList<Integer> startsDialogues = new ArrayList<Integer>();
    int merchantTable = -1;
    ArrayList<String> otherActions = new ArrayList<String>();
    int questOpenLoot = -1;
    boolean isChest = false;
    int pickupItem = -1;
    boolean isPlayerCorpse = false;
    String otherUse = null;
    boolean isPlayerShop = false;
    
    private static final long serialVersionUID = 1L;


}
