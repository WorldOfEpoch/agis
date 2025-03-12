package atavism.agis.objects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.AgisAbilityState;
import atavism.agis.plugins.AnimationClient.InvokeEffectMessage;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.util.Log;

public class CoordinatedEffectState {
    public CoordinatedEffectState(CoordinatedEffect coordinatedEffect, OID sourceOid, OID targetOid, Point loc, AgisAbilityState abilityState,  Point destLoc) {
        this.sourceOid = sourceOid;
        this.targetOid = targetOid;
        this.abilityState = abilityState;
        this.loc = loc;
        this.destLoc = destLoc;
        this.coordinatedEffect = coordinatedEffect;
        this.coordEffectOid  = Engine.getCoordOIDManager().getNextOid();
    }
    
    protected CoordinatedEffect coordinatedEffect = null;
    protected AgisAbilityState abilityState = null;
    
    protected OID coordEffectOid;
    protected OID sourceOid, targetOid;

    protected Point loc = null;
    protected Point destLoc = null;

    /**
     * override to add custom information to the message
     * @return
     */
    public InvokeEffectMessage generateInvokeMessage() {
		Log.debug("[CYC] generateInvokeMessage for " + coordinatedEffect.getEffectName());
        InvokeEffectMessage msg = new InvokeEffectMessage(sourceOid, coordinatedEffect.getEffectName());
        msg.setProperty("ceId", coordEffectOid.toLong());
        if (coordinatedEffect.sendSourceOid()) {
            msg.setProperty("sourceOID", sourceOid);
        }
        if (coordinatedEffect.sendTargetOid()) {
            msg.setProperty("targetOID", targetOid);
        }
        if (this.loc != null) {
            msg.setProperty("point", loc);
        }
        if (this.destLoc != null) {
            msg.setProperty("dpoint", new AOVector(destLoc));
        }

        for (Map.Entry<String, Serializable> entry : coordinatedEffect.copyArgMap().entrySet()) {
            msg.setProperty(entry.getKey(), entry.getValue());
        }
        //msg.setProperty("hasIcon", false);
        return msg;
    }
    
    public void invoke() {
        InvokeEffectMessage msg = generateInvokeMessage();
        Engine.getAgent().sendBroadcast(msg);
		Log.debug("[CYC] CoordinatedEffectState.invoke(): " + msg);
    }

    // displayIcon == true -> add icon; false -> remove icon
    public void invoke(String iconName, Boolean displayIcon) {
        InvokeEffectMessage msg = generateInvokeMessage();
        if (iconName != null) {
            msg.setProperty("hasIcon", true);
            msg.setProperty("iconName", iconName);
            msg.setProperty("displayIcon", displayIcon);
        }
        Engine.getAgent().sendBroadcast(msg);
		Log.debug("[CYC] CoordinatedEffectState.invoke(): " + msg);
    }

    public void invokeCancel() {
        InvokeEffectMessage msg = generateInvokeMessage();
        msg.setProperty("cancel", true);
        Engine.getAgent().sendBroadcast(msg);
        Log.debug("[CYC] CoordinatedEffectState.invoke(): " + msg);
    }
    public AgisAbilityState getAbilityState() {
        return this.abilityState;
    }
}
