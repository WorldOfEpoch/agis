package atavism.agis.server;

import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.AgisPermissionFactory;
import atavism.server.engine.*;
import atavism.server.math.Geometry;
import atavism.server.objects.World;
import atavism.server.plugins.ProxyPlugin;
import atavism.server.util.Log;

public class ServerProperties {

    public static void setGlobalProperties() {
        World.addTheme("agis.toc");
        World.FollowsTerrainOverride = true;
    }

    public static void setWorldProperties() {
        Engine.setBasicInterpolatorInterval(5000);
        Geometry worldGeo = Geometry.maxGeometry();
        World.setGeometry(worldGeo);
        Geometry localGeo = Geometry.maxGeometry();
        if (Engine.getProperty("atavism.dualworldmanagers") == "1") {
            Log.debug("wmgr_local1.py: using dual world manager config");
            localGeo = new Geometry(-2147483647, -2, -2147483647, 2147483647);
        }
        World.setLocalGeometry(localGeo);
//        World.perceiverRadius = 75;
//
//        ContentDatabase cDB = new ContentDatabase(false);
//        String perceiverRadius = cDB.loadGameSetting("PLAYER_PERCEPTION_RADIUS");
//        if (perceiverRadius != null)
//            World.perceiverRadius = Integer.parseInt(perceiverRadius);
//        Log.debug("Game Settings PLAYER_PERCEPTION_RADIUS set World.perceiverRadius=" + World.perceiverRadius);


        // QuadTree.setMaxObjects(3)
        World.setLocTolerance(20);
//        World.DEBUG_OID = OID.fromLong(451765);
        World.setDefaultPermission(new AgisPermissionFactory());
    }

    public static void setProxyProperties() {
        ProxyPlugin.MaxConcurrentUsers = 1000;
        ProxyPlugin.SpeedhackCountToDisconect = 10;
        ProxyPlugin.SpeedhackChatMsg = "Detected Speedhack";
    }


}
