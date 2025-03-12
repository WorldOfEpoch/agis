package atavism.agis.objects;

import java.util.Map;
import java.io.*;
import java.util.HashMap;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.agis.core.AgisAbilityState;

/**
 * Use this class to configure and invoke coordinated effects scripts 
 * from the server.
 *
 */
public class CoordinatedEffect {
    public CoordinatedEffect(String effectName) {
        setEffectName(effectName);
    }

    /**
     * Invokes a client coordinated effect script, originating with the sourceOid object and targetted at the targetOid object.
     * @param sourceOid - Object ID of the object from which this coordinated effect originates.
     * @param targetOid - Object ID of the target object.
     * @return A CoordinatedEffect.State object that can be used to cancel or update the coordinated effect (not yet implemented).
     */
    public CoordinatedEffectState invoke(OID sourceOid, OID targetOid) {
        CoordinatedEffectState state = generateStateObject(sourceOid, targetOid, null, null, null);
        state.invoke();
        return state;
    }

    public CoordinatedEffectState invoke(OID sourceOid, OID targetOid, String iconName, Boolean displayIcon) {
        CoordinatedEffectState state = generateStateObject(sourceOid, targetOid, null, null, null);
        state.invoke(iconName, displayIcon);
        return state;
    }

    // raf: added a reference to the ability state object
    public CoordinatedEffectState invoke(OID sourceOid, OID targetOid, Point loc, AgisAbilityState abilityState, Point destLoc) {
        CoordinatedEffectState state = generateStateObject(sourceOid, targetOid, loc, abilityState, destLoc);
        state.invoke();
        return state;
    }

    public CoordinatedEffectState invoke(OID sourceOid, OID targetOid, Point loc, AgisAbilityState abilityState, String iconName, Boolean displayIcon) {
        CoordinatedEffectState state = generateStateObject(sourceOid, targetOid, loc, abilityState, null);
        state.invoke(iconName, displayIcon);
        return state;
    }
    
    /**
     * this is an object factory. generates the CoordinatedEffectState object which is used when 
     * invoke() is called.
     * override this method to have customized state objects.
     * abilityState can be null, this just means that the effectstate object
     * will have no reference back to the abilityState.
     * 
     * @return new CoordinatedEffectState
     */
    public CoordinatedEffectState generateStateObject(OID sourceOid, OID targetOid, Point loc, AgisAbilityState abilityState, Point destLoc) {
        CoordinatedEffectState state = new CoordinatedEffectState(this, sourceOid, targetOid, loc, abilityState, destLoc);
        return state;
    }
    
    /**
     * Sets the name of the coordinated effect script to invoke.
     * @param effectName - Name of the coordinated effect script.
     */
    public void setEffectName(String effectName) { this.effectName = effectName; }
    
    /**
     * Get the name of the coordinated effect script to invoke.
     * @return - the name of the coordinated effect script invoked by this CoordinatedEffect..
     */
    public String getEffectName() { return effectName; }
    protected String effectName;

    /**
     * Adds an argument that will be passed to the effects script when it is invoked on the client.
     * @param argName - Name of the script argument.
     * @param argValue - Value of the argument. Must be one of: String, Boolean, Integer, Long, Float, Point, Quaternion.
     */
    public void putArgument(String argName, Serializable argValue) { argMap.put(argName, argValue); }
    
    /**
     * Get the value of an argument that will be passed to the effects script when it is invoked on the client.
     * @param argName - Name of the script argument.
     * @return - Value of the argument.
     */
    public Object getArgument(String argName) { return argMap.get(argName); }
    
    public HashMap<String, Serializable> copyArgMap() {
        return new HashMap<String,Serializable>(argMap);
    }
    
    protected Map<String, Serializable> argMap = new HashMap<String, Serializable>();

    /**
     * Sets whether to send the sourceOid parameter to the client coordinated effect script.
     * @param val - Whether or not to send the sourceOid parameter to the client coordinated effect script.
     */
    public void sendSourceOid(boolean val) { sendSrcOid = val; }
    
    /**
     * Sets whether to send the sourceOid parameter to the client coordinated effect script.
     * @return whether or not to send the sourceOid parameter to the client coordinated effect script.
     */
    public boolean sendSourceOid() { return sendSrcOid; }
    
    protected boolean sendSrcOid = false;

    /**
     * Sets whether to send the targetOid parameter to the client coordinated effect script.
     * @param val - true if you want to send the taregetOid parameter to the client coordinated effect script. 
     */
    public void sendTargetOid(boolean val) { sendTargOid = val; }
    
    /**
     * Gets whether the targetOid parameter will be sent to the client coordinated effect script.
     * @return - true if you want to send the targetOid parameter to the client coordinated effect script; false otherwise.
     */
    public boolean sendTargetOid() { return sendTargOid; }
    protected boolean sendTargOid = false;
}