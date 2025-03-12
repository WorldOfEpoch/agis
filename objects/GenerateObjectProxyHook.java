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


public class GenerateObjectProxyHook implements ProxyExtensionHook
{
    
    public void processExtensionEvent(ExtensionMessageEvent event,
        Player player, ProxyPlugin proxy)
    {
        Map<String,Serializable> props = event.getPropertyMap();

        if (Log.loggingDebug) {
            String propStr = "";
            for (Map.Entry<String,Serializable> entry : props.entrySet()) {
                propStr += entry.getKey() + "=" + entry.getValue() + " ";
            }
            Log.debug("GenerateObjectProxyHook: " + player + " " + propStr);
        }

        int templateID = (Integer) props.get("template");

        BasicWorldNode objectLoc;
        BasicWorldNode playerLoc =
            WorldManagerClient.getWorldNode(player.getOid());

        String markerName = (String) props.get("marker");
        if (markerName != null) {
            Marker marker = InstanceClient.getMarker(
                playerLoc.getInstanceOid(), markerName);
            if (marker == null) {
                Log.error("GenerateObjectProxyHook: unknown marker="+markerName);
                return;
            }
            objectLoc = new BasicWorldNode();
            objectLoc.setInstanceOid(playerLoc.getInstanceOid());
            objectLoc.setLoc(marker.getPoint());
            objectLoc.setOrientation(marker.getOrientation());
            objectLoc.setDir(new AOVector(0,0,0));
        }
        else {
            objectLoc = playerLoc;
            objectLoc.setDir(new AOVector(0,0,0));
        }

        boolean persistent = false;
        if (props.get("persistent") != null) {
            Integer persInt = (Integer) props.get("persistent");
            if (persInt != 0)
                persistent = true;
        }

        Template override = new Template();
        override.put(WorldManagerClient.NAMESPACE,
            WorldManagerClient.TEMPL_INSTANCE, objectLoc.getInstanceOid());
        override.put(WorldManagerClient.NAMESPACE,
            WorldManagerClient.TEMPL_LOC, objectLoc.getLoc());
        override.put(WorldManagerClient.NAMESPACE, 
            WorldManagerClient.TEMPL_ORIENT, objectLoc.getOrientation());
        if (persistent)
            override.put(Namespace.OBJECT_MANAGER, 
                ObjectManagerClient.TEMPL_PERSISTENT, persistent);

        OID oid = ObjectManagerClient.generateObject(templateID,ObjectManagerPlugin.MOB_TEMPLATE, override);
        if (oid == null) {
            Log.error("GenerateObjectProxyHook: generateObject failed templateID="+templateID);
            return ;
        }

        if (Log.loggingDebug)
            Log.debug("GenerateObjectProxyHook: generateObject success templateID="+templateID + " oid="+oid);

        Integer result = WorldManagerClient.spawn(oid);
        if (result < 0) {
			Log.error("GenerateObjectProxyHook: spawn failed result=" + result + " oid=" + oid);
		       return ;
        }
    }

}

