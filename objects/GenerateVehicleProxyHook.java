package atavism.agis.objects;

import java.util.Map;
import java.io.Serializable;

import atavism.server.objects.ProxyExtensionHook;
import atavism.server.objects.Template;
import atavism.server.objects.Player;
import atavism.server.objects.Marker;
import atavism.server.events.ExtensionMessageEvent;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.ProxyPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.InstanceClient;
import atavism.server.util.Log;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;

public class GenerateVehicleProxyHook implements ProxyExtensionHook {

    public void processExtensionEvent(ExtensionMessageEvent event, Player player, ProxyPlugin proxy) {
        Map<String, Serializable> props = event.getPropertyMap();

        if (Log.loggingDebug) {
            String propStr = "";
            for (Map.Entry<String, Serializable> entry : props.entrySet()) {
                propStr += entry.getKey() + "=" + entry.getValue() + " ";
            }
            Log.debug("GenerateVehicleProxyHook: " + player + " " + propStr);
        }

        int templateName = (Integer) props.get("template");

        BasicWorldNode vehicleLoc;
        BasicWorldNode playerLoc = WorldManagerClient.getWorldNode(player.getOid());

        String markerName = (String) props.get("marker");
        if (markerName != null) {
            Marker marker = InstanceClient.getMarker(playerLoc.getInstanceOid(), markerName);
            if (marker == null) {
                Log.error("GenerateVehicleProxyHook: unknown marker=" + markerName);
                return;
            }
            vehicleLoc = new BasicWorldNode();
            vehicleLoc.setInstanceOid(playerLoc.getInstanceOid());
            vehicleLoc.setLoc(marker.getPoint());
            vehicleLoc.setOrientation(marker.getOrientation());
            vehicleLoc.setDir(new AOVector(0, 0, 0));
        } else {
            vehicleLoc = playerLoc;
            vehicleLoc.setDir(new AOVector(0, 0, 0));
        }

        boolean persistent = false;
        if (props.get("persistent") != null) {
            Integer persInt = (Integer) props.get("persistent");
            persistent = persInt != 0;
        }

        Template override = new Template();
        override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, vehicleLoc.getInstanceOid());
        override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_LOC, vehicleLoc.getLoc());
        override.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ORIENT, vehicleLoc.getOrientation());
        if (persistent) {
            override.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);
        }

        // Use a specific template type for vehicles, if applicable
        OID oid = ObjectManagerClient.generateObject(templateName, "vehicle", override);
        if (oid == null) {
            Log.error("GenerateVehicleProxyHook: generateObject failed for templateName=" + templateName);
            return;
        }

        if (Log.loggingDebug) {
            Log.debug("GenerateVehicleProxyHook: generateObject success for templateName=" + templateName + " oid=" + oid);
        }

        Integer result = WorldManagerClient.spawn(oid);
        if (result < 0) {
            Log.error("GenerateVehicleProxyHook: spawn failed for oid=" + oid + " with result=" + result);
        }
    }
}
