 package atavism.agis.objects;
 
 import atavism.server.engine.Namespace;
 import atavism.server.engine.OID;
 import atavism.server.math.Point;
 import atavism.server.math.Quaternion;
import atavism.server.objects.Entity;
import atavism.server.objects.SpawnData;

import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.Iterator;
 import java.util.Random;
 public class VehicleSpawnData extends SpawnData
 {
   protected HashMap<Integer, Integer> templateIDs;
   protected HashMap<Integer, Integer> templateAlterIDs;
   protected int templateID;
   private String templateName;
   private int category;
   private String factoryName;
   private String className;
   private OID instanceOid;
   private Point loc;
   private Quaternion orient;
   private Integer spawnRadius;
   private Integer numSpawns;
   private Integer respawnTime;
   private Integer respawnTimeMax;
   private Integer corpseDespawnTime;
   private static final long serialVersionUID = 1L;
   
   public VehicleSpawnData() {
	 this.templateIDs = new HashMap<>();
	 this.templateAlterIDs = new HashMap<>();
	 this.templateID = -1; setNamespace(Namespace.TRANSIENT); } public VehicleSpawnData(String name, String templateName, int category, String factoryName, OID instanceOid, Point loc, Quaternion orient, Integer spawnRadius, Integer numSpawns, Integer respawnTime) { super(name, factoryName, respawnTime, factoryName, instanceOid, loc, orient, respawnTime, respawnTime, respawnTime); this.templateIDs = new HashMap<>(); this.templateAlterIDs = new HashMap<>(); this.templateID = -1;
	 setNamespace(Namespace.TRANSIENT);
	 setTemplateName(templateName);
	 setCategory(category);
	 setFactoryName(factoryName);
	 setInstanceOid(instanceOid);
	 setLoc(loc);
	 setOrientation(orient);
	 setSpawnRadius(spawnRadius);
	 setNumSpawns(numSpawns);
	 setRespawnTime(respawnTime); 
     }
 
   
   public String toString() {
     return "[SpawnData: oid=" + getOid() + ", name=" + getName() + ", templateName=" + getTemplateName() + ", factoryName=" + getFactoryName() + ", instanceOid=" + getInstanceOid() + ", loc=" + getLoc() + ", orient=" + getOrientation() + ", numSpawns=" + getNumSpawns() + ", respawnTime=" + getRespawnTime() + ", corpseDespawnTime=" + getCorpseDespawnTime() + "]";
   }
   
   public void setClassName(String className) {
     this.className = className;
   }
   
   public String getClassName() {
     return this.className;
   }
   
   public void setTemplateID(int templateID) {
     this.templateID = templateID;
     this.templateIDs.put(Integer.valueOf(templateID), Integer.valueOf(100));
   }
   
   public int getTemplateID() {
     return this.templateID;
   }
   
   public void setTemplateIDs(HashMap<Integer, Integer> templateIDs) {
     this.templateIDs = templateIDs;
   }
   
   public HashMap<Integer, Integer> getTemplateIDs() {
     return this.templateIDs;
   }
   
   public ArrayList<Integer> getTemplates() {
     ArrayList<Integer> list = new ArrayList<>();
     for (Integer i : this.templateIDs.keySet())
       list.add(i); 
     return list;
   }
   
   public void addTemplateID(int templateID, int chance) {
     this.templateID = templateID;
     this.templateIDs.put(Integer.valueOf(templateID), Integer.valueOf(chance));
   }
   
   public int getRandomTemplateID() {
     log.debug("getRandomTemplateID templateIDs=" + this.templateIDs);
     if (this.templateIDs.size() == 1) {
       ArrayList<Integer> ids = getTemplates();
       log.debug("getRandomTemplateID ids=" + ids);
       this.templateID = ((Integer)ids.get(0)).intValue();
       log.debug("getRandomTemplateID templateID=" + this.templateID);
       return ((Integer)ids.get(0)).intValue();
     } 
     int totalChance = 0;
     for (Iterator<Integer> iterator1 = this.templateIDs.values().iterator(); iterator1.hasNext(); ) {
       int chance = ((Integer)iterator1.next()).intValue();
       totalChance += chance;
     } 
     Random rand = new Random();
     int roll = 0;
     if (totalChance > 0)
       roll = rand.nextInt(totalChance); 
     int currentChance = 0;
     for (Iterator<Integer> iterator2 = this.templateIDs.keySet().iterator(); iterator2.hasNext(); ) {
       int templateID = ((Integer)iterator2.next()).intValue();
       if (((Integer)this.templateIDs.get(Integer.valueOf(templateID))).intValue() + currentChance >= roll) {
         this.templateID = templateID;
         log.debug("getRandomTemplateID templateID=" + templateID);
         return templateID;
       } 
       currentChance += ((Integer)this.templateIDs.get(Integer.valueOf(templateID))).intValue();
     } 
     log.debug("getRandomTemplateID templateID=-1");
     return -1;
   }
   
   public void setTemplateAlterID(int templateID) {
     this.templateAlterIDs.put(Integer.valueOf(templateID), Integer.valueOf(100));
   }
   
   public void setTemplateAlterIDs(HashMap<Integer, Integer> templateAlterIDs) {
     this.templateAlterIDs = templateAlterIDs;
   }
   
   public HashMap<Integer, Integer> getTemplateAlterIDs() {
     return this.templateAlterIDs;
   }
   
   public ArrayList<Integer> getTemplatesAlter() {
     ArrayList<Integer> list = new ArrayList<>();
     for (Integer i : this.templateAlterIDs.keySet())
       list.add(i); 
     return list;
   }
   
   public void addTemplateAlterID(int templateID, int chance) {
     this.templateAlterIDs.put(Integer.valueOf(templateID), Integer.valueOf(chance));
   }
   
   public int getRandomTemplateAlterID() {
     if (this.templateAlterIDs.size() == 1) {
       ArrayList<Integer> ids = getTemplatesAlter();
       return ((Integer)ids.get(0)).intValue();
     } 
     int totalChance = 0;
     for (Iterator<Integer> iterator1 = this.templateAlterIDs.values().iterator(); iterator1.hasNext(); ) {
       int chance = ((Integer)iterator1.next()).intValue();
       totalChance += chance;
     } 
     Random rand = new Random();
     int roll = 0;
     if (totalChance > 0)
       roll = rand.nextInt(totalChance); 
     int currentChance = 0;
     for (Iterator<Integer> iterator2 = this.templateAlterIDs.keySet().iterator(); iterator2.hasNext(); ) {
       int templateID = ((Integer)iterator2.next()).intValue();
       if (((Integer)this.templateAlterIDs.get(Integer.valueOf(templateID))).intValue() + currentChance >= roll)
         return templateID; 
       currentChance += ((Integer)this.templateAlterIDs.get(Integer.valueOf(templateID))).intValue();
     } 
     return -1;
   }
   
   public void setTemplateName(String templateName) {
     this.templateName = templateName;
   }
   
   public String getTemplateName() {
     return this.templateName;
   }
   
   public void setCategory(int category) {
     this.category = category;
   }
   
   public int getCategory() {
     return this.category;
   }
   
   public void setFactoryName(String factoryName) {
     this.factoryName = factoryName;
   }
   
   public String getFactoryName() {
     return this.factoryName;
   }
   
   public OID getInstanceOid() {
     return this.instanceOid;
   }
   
   public void setInstanceOid(OID oid) {
     this.instanceOid = oid;
   }
   
   public void setLoc(Point loc) {
     this.loc = loc;
   }
   
   public Point getLoc() {
     return this.loc;
   }
   
   public void setOrientation(Quaternion orient) {
     this.orient = orient;
   }
   
   public Quaternion getOrientation() {
     return this.orient;
   }
   
   public void setSpawnRadius(Integer spawnRadius) {
     this.spawnRadius = spawnRadius;
   }
   
   public Integer getSpawnRadius() {
     return this.spawnRadius;
   }
   
   public void setNumSpawns(Integer numSpawns) {
     this.numSpawns = numSpawns;
   }
   
   public Integer getNumSpawns() {
     return this.numSpawns;
   }
   
   public void setRespawnTime(Integer respawnTime) {
     this.respawnTime = respawnTime;
   }
   
   public Integer getRespawnTime() {
     return this.respawnTime;
   }
   
   public void setRespawnTimeMax(Integer respawnTime) {
     this.respawnTimeMax = respawnTime;
   }
   
   public Integer getRespawnTimeMax() {
     return this.respawnTimeMax;
   }
   
   public void setCorpseDespawnTime(Integer time) {
     this.corpseDespawnTime = time;
   }
   
   public Integer getCorpseDespawnTime() {
     return this.corpseDespawnTime;
   }
 }

