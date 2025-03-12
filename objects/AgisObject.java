package atavism.agis.objects;

import atavism.server.engine.OID;
import atavism.server.objects.*;
import atavism.agis.core.*;
import atavism.server.util.*;
import java.util.*;

/**
 * Not even sure what the purpose of this class is. I don't believe most of the variables in here are ever actually used. - Andrew
 *
 */
public class AgisObject extends AOObject {

    public AgisObject() {
    }
    
    public AgisObject(OID oid) {
        super(oid);
    }

    /**
     * Checks if the object is a AGIS object and if it is, returns the reference
     * as a AgisObject.
     * @return AgisObject     
     */
    public static AgisObject convert(Entity obj) {
        if (! (obj instanceof AgisObject)) {
            throw new AORuntimeException("AgisObject.convert: obj is not a agisobject: " + obj);
        }
        return (AgisObject) obj;
    }

    public void setTemplateID(int templateID) {
        this.templateID = templateID;
    }
    public int getTemplateID() {
        return templateID;
    }
    protected int templateID = -1;
    
    /**
     * Base display context - the inventory plugin adds soft/hard attachments
     * to this base mesh.
     */
    public DisplayContext baseDC() {
        return (DisplayContext) getProperty(baseDCKey);
    }
    public void baseDC(DisplayContext dc) {
        setProperty(baseDCKey, dc);
    }
    public static String baseDCKey = "agisobj.basedc";

    public DCMap dcMap() {
        lock.lock();
        try {
            DCMap map = (DCMap)getProperty(dcMapKey);
            if (map == null) {
                map = new DCMap();
                dcMap(map);
            }
            return map;
        }
        finally {
            lock.unlock();
        }
    }
    public void dcMap(DCMap dcMap) {
        setProperty(dcMapKey, dcMap);
    }
    public static String dcMapKey = atavism.server.plugins.InventoryClient.TEMPL_DCMAP;

    // helper method
    public void addDCMapping(DisplayContext base, DisplayContext target) {
        DCMap dcMap = dcMap();
        dcMap.add(base, target);
    }
    /**
     * Returns a copy of the matching display context.
     */
    public DisplayContext getDCMapping(DisplayContext base) {
        return (DisplayContext)dcMap().get(base).clone();
    }    

    public int getDCV() {
        return 0;
    }

    public int getResistantPD() {
        return 0;
    }

    public int getPD() {
        return 0;
    }
        
    public void setBody(int body) {
        lock.lock();
        try {
            this.body = body;
            if (currentBody > body) {
                currentBody = body;
            }
        }
        finally {
            lock.unlock();
        }
    }
    public int getBody() {
        return body;
    }
    public void modifyBody(int delta) {
        lock.lock();
        try {
            int body = getBody();
            setBody(body + delta);
        }
        finally {
            lock.unlock();
        }
    }
    int body;
        
    public void setCurrentBody(int body) {
        currentBody = body;
    }
    public int getCurrentBody() {
        return currentBody;
    }
    public void modifyCurrentBody(int delta) {
        lock.lock();
        try {
            int body = getCurrentBody();
            setCurrentBody(body + delta);
        }
        finally {
            lock.unlock();
        }
    }
    int currentBody;

    /**
     * Sets whether this mob is attackable by a user.
     * Backends into AOObject.setState(AgisStates.attackable).
     */
    public void attackable(boolean val) {
        String stateName = AgisStates.Attackable.toString();
        setState(stateName, new BinaryState(stateName, val));
    }

    /**
     * Returns whether this mob is attackable by a user.
     * Backends into AOObject.getState(AgisStates.attackable).
     */
    public boolean attackable() {
        if (isDead()) {
            return false;
        }

        BinaryState attackable = 
            (BinaryState) getState(AgisStates.Attackable.toString());
        return ((attackable != null) && attackable.isSet());
    }

    public void isDead(boolean val) {
        setState(AgisStates.Dead.toString(), 
                 new BinaryState(AgisStates.Dead.toString(), val));
    }
    public boolean isDead() {
        BinaryState dead = (BinaryState)getState(AgisStates.Dead.toString());
        return ((dead!=null) && dead.isSet());
    }

    // sound - dont need to worry about the persistencedelegate for this one
    // because it uses the underlying property system
    public void setSound(String name, String value) {
        setProperty("agis.sound." + name, value);
    }
    public String getSound(String name) {
        return (String) getProperty("agis.sound." + name);
    }

    /**
     * @return the owner of this object, null if no owner.
     */
    public OID getOwnerOID() {
        return ownerOID;
    }
    public void setOwnerOID(OID ownerOID) {
        this.ownerOID = ownerOID;
    }
    OID ownerOID = null;

    public void addCooldownState(Cooldown.State cd) {
        lock.lock();
        try {
            if (Log.loggingDebug)
                Log.debug("AgisObject.addCooldownState id=" + cd.getID());
            Cooldown.State oldcd = cooldownStateMap.get(cd.getID());
            if (oldcd != null)
                oldcd.cancel();
            cooldownStateMap.put(cd.getID(), cd);
        }
        finally {
            lock.unlock();
        }
    }
    public Cooldown.State removeCooldownState(Cooldown.State cd) {
        lock.lock();
        try {
            return cooldownStateMap.remove(cd.getID());
        }
        finally {
            lock.unlock();
        }
    }
    public Cooldown.State getCooldownState(String id) {
        lock.lock();
        try {
            return cooldownStateMap.get(id);
        }
        finally {
            lock.unlock();
        }
    }
    public Map<String, Cooldown.State> getCooldownStateMap() {
        lock.lock();
        try {
            return new HashMap<String, Cooldown.State>(cooldownStateMap);
        }
        finally {
            lock.unlock();
        }
    }
    public void setCooldownStateMap(Map<String, Cooldown.State> map) {
        lock.lock();
        try {
            cooldownStateMap = new HashMap<String, Cooldown.State>(map);
        }
        finally {
            lock.unlock();
        }
    }
    protected Map<String, Cooldown.State> cooldownStateMap = new HashMap<String, Cooldown.State>();

    public int getStunCounter() { return stunCounter; }
    protected void setStunCounter(int cnt) { stunCounter = cnt; }
    public void addStun() { stunCounter++; };
    public void removeStun() { stunCounter--; };
    public boolean isStunned() { return (stunCounter > 0); }
    private int stunCounter = 0;

    private static final long serialVersionUID = 1L;
}
