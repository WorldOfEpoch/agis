package atavism.agis.server;

import static atavism.agis.server.ServerProperties.setGlobalProperties;
import static atavism.agis.server.ServerProperties.setProxyProperties;
import static atavism.agis.server.ServerProperties.setWorldProperties;
import static atavism.agis.server.ServerStarter.*;

import java.io.IOException;

import atavism.agis.server.events.EventInitializer;
import atavism.agis.server.messages.MessageInitializer;
import atavism.server.engine.Engine;

public class AllInOneServer {

    public static void main(String[] args) throws IOException, InterruptedException {
        AdvertisementFileMerger.merge("all_in_one", "arena", "auction", "builder", "chat", "combat",
                "faction", "instance", "login_manager", "mobserver", "objmgr", "prefab_manager", "quest", "weather",
                "wmgr");
        startDomain(args);
        Engine.main(args, AllInOneServer::preScript, AllInOneServer::postScript);
    }

    private static void preScript() {
        EventInitializer.init();
        MessageInitializer.init();
        setWorldProperties();
        setProxyProperties();
    }

    private static void postScript() {
        setGlobalProperties();
        startArena(); // no deps
        startBuilder(); // no deps
        startFaction(); // no deps
        startObjectManager(); // no deps
        startPrefab(); // no deps
        startQuest(); // no deps
        startCombat(); // no deps

        startAuction(); // dep: Inventory
        startWeather(); // dep: Combat
        startWorldManager(); // dep: Combat
        startMob(); // dep: ObjectManager,WorldManager,Inventory,Quest,Social
        startVehicle();
        startInstance(); // dep: ObjectManager,Quest,MobManager,Inventory,WorldManager,Combat
        startLogin(); // dep: ObjectManager,Instance
        // startProxy(); // dep: Instance
        startChat(); // dep: Proxy
    }

}
