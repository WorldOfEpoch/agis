package atavism.agis.server;

import java.util.concurrent.*;

import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.*;
import atavism.agis.server.combat.*;
//import atavism.agis.server.proxy.ProxyStarter;
import atavism.msgsys.DomainServer;
import atavism.server.engine.Engine;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.util.*;
import atavism.server.worldmgr.LoginPlugin;

public class ServerStarter {

    private static final ExecutorService DOMAIN_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void startDomain(String[] args) throws InterruptedException {
        DOMAIN_EXECUTOR.execute(() -> DomainServer.main(args));
        Thread.sleep(5000);
    }

    public static void startArena() {
        Engine.registerPlugin("atavism.agis.plugins.ArenaPlugin");
    }

    public static void startAuction() {
        Engine.registerPlugin("atavism.agis.plugins.AuctionPlugin");
    }

    public static void startBuilder() {
        Engine.registerPlugin("atavism.agis.plugins.VoxelPlugin");
    }

    public static void startChat() {
        Engine.registerPlugin("atavism.agis.plugins.ChatPlugin");
    }

    public static void startCombat() {
        Engine.registerPlugin("atavism.agis.plugins.ClassAbilityPlugin");
//        CombatPlugin.registerStat(new DmgBaseStat("dmg-base"));
//        CombatPlugin.registerStat(new DmgBaseStat("dmg-max"));
//        CombatPlugin.registerStat(new DmgModifierStat("dmg-dealt-mod"));
//        CombatPlugin.registerStat(new DmgModifierStat("dmg-taken-mod"));
//        CombatPlugin.registerStat(new ExpMaxStat("experience-max"));
//        CombatPlugin.registerStat(new ExperienceStat("experience"), false, "experience-max");
//        CombatPlugin.registerStat(new LevelBaseStat("level"), true);
//        CombatPlugin.registerStat(new GearScoreStat("gearScore"));
        Engine.registerPlugin("atavism.agis.plugins.CombatPlugin");
    }

    public static void startFaction() {
        Engine.registerPlugin("atavism.agis.plugins.FactionPlugin");
        Engine.registerPlugin("atavism.agis.plugins.GroupPlugin");
        // GroupPlugin.SetMaxGroupSize(5);
    }

    public static void startInstance() {
        Engine.registerPlugin(new InstancePlugin());
        Engine.getPlugin("Instance").setPluginAvailable(true);
    }

    public static void startMob() {
        WEObjFactory.registerBehaviorClass("BaseBehavior", "atavism.agis.behaviors.BaseBehavior");
        WEObjFactory.registerBehaviorClass("CombatBehavior", "atavism.agis.behaviors.CombatBehavior");
        WEObjFactory.registerBehaviorClass("RadiusRoamBehavior", "atavism.agis.behaviors.RadiusRoamBehavior");
        WEObjFactory.registerBehaviorClass("PatrolBehavior", "atavism.agis.behaviors.PatrolBehavior");
        Engine.registerPlugin("atavism.agis.plugins.AgisMobPlugin");
    }
    
    
    public static void startVehicle() {
    	Log.error("startVehicle: AgisVehiclePlugin");
        Engine.registerPlugin("atavism.agis.plugins.AgisVehiclePlugin");
        
    }
    
    public static void startVehicleManager() {
        VehicleManagerPlugin vehicleManager = new VehicleManagerPlugin();
        Engine.registerPlugin(vehicleManager);
    }


    public static void startObjectManager() {
        ObjectManagerPlugin objectManager = new ObjectManagerPlugin();
        Engine.registerPlugin(objectManager);
        Engine.registerPlugin("atavism.agis.plugins.AgisInventoryPlugin");
        Engine.registerPlugin("atavism.agis.plugins.CraftingPlugin");
        Engine.registerPlugin("atavism.agis.plugins.DataLoggerPlugin");
        setupGuild();
    }

    private static void setupGuild() {
        Engine.registerPlugin("atavism.agis.plugins.GuildPlugin");
        // Define default ranks in order
        GuildPlugin.AddGuildRank("Guild Master",
                new String[] { GuildPlugin.PERMISSION_INVITE, GuildPlugin.PERMISSION_KICK,
                        GuildPlugin.PERMISSION_PROMOTE, GuildPlugin.PERMISSION_DEMOTE, GuildPlugin.PERMISSION_SET_MOTD,
                        GuildPlugin.PERMISSION_CHAT, GuildPlugin.PERMISSION_ADD_RANK, GuildPlugin.PERMISSION_EDIT_RANK,
                        GuildPlugin.PERMISSION_DISBAND, GuildPlugin.PERMISSION_DEL_RANK,
                        GuildPlugin.PERMISSION_ADD_CLAIM, GuildPlugin.PERMISSION_EDIT_CLAIM,
                        GuildPlugin.PERMISSION_ACTION_CLAIM, GuildPlugin.PERMISSION_LEVEL_UP,
                        GuildPlugin.PERMISSION_WAREHOUSE_ADD, GuildPlugin.PERMISSION_WAREHOUSE_GET });
        GuildPlugin.AddGuildRank("Officer", new String[] { GuildPlugin.PERMISSION_INVITE, GuildPlugin.PERMISSION_KICK,
                GuildPlugin.PERMISSION_PROMOTE, GuildPlugin.PERMISSION_DEMOTE, GuildPlugin.PERMISSION_CHAT });
        GuildPlugin.AddGuildRank("Captain",
                new String[] { GuildPlugin.PERMISSION_INVITE, GuildPlugin.PERMISSION_CHAT });
        GuildPlugin.AddGuildRank("Peasant", new String[] { GuildPlugin.PERMISSION_CHAT });
    }

    public static void startLogin() {
        LoginPlugin.SecureToken = true;
        addMasterPublicKeys();
        Engine.registerPlugin("atavism.agis.plugins.AgisLoginPlugin");
        setupCharacterFactory();
    }

    private static void addMasterPublicKeys() {
        String pubkey = "AAAAAAAAAAwAAAADRFNBMIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBALJagda9mnhVzF1Z+p0XcU9uzzjtNe+J3MoYiioRKlXlk3KV1oX+L3uj27bBnZHWYe6hi7pN9aex+AjC7FjnPtyyBE+Fv7HHp1/YOcVqn2oq9VglOSJV/qhn+FVaRCF5s9tYPyyIVvwMi0oLZgNysRRrVoqpHfmftqbYFhYoEfYp";
        SecureTokenManager.getInstance().registerMasterPublicKey(Base64.decode(pubkey));
        pubkey = "AAAAAAAAAAEAAAADRFNBMIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAI1obFDPxAHhfzeYpMSxJhplwKldDVBpG3TNAj18FaoqMsWq2mjI1VN2i9jLjhhbl7DgFIWvXBqaJ8BO75uGMQL+uEvlGQaQ7ClgGpWn0YLxUd1Hja+Q7SmKnkWmrhMYiq84O/2GP6hTfmidVd7STy3PoXuSf50Ph2tumuaiUyZ5";
        SecureTokenManager.getInstance().registerMasterPublicKey(Base64.decode(pubkey));
        pubkey = "AAAAAAAAAAcAAAADRFNBMIIBtzCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYQAAoGADI9k/sOlM8WiR4HYAX92O1+/9MRHsnZ34IpnZD6fSyOIPN/NkPOPTVCS915GVOSDeELAQ7kznTDKx6eOJU7eO4Tnsdctb4MAiwvuCjdleGmFTLiK8OLA5GJWseDu8dNsh3tyJI1Tn595M2X9ise4TYQ+fJj8k1WDuWAds1mmVd8=";
        SecureTokenManager.getInstance().registerMasterPublicKey(Base64.decode(pubkey));
    }

    private static void setupCharacterFactory() {
        Template player = new Template("DefaultPlayer", -1, ObjectManagerPlugin.MOB_TEMPLATE);
        player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.player);
        player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_RUN_THRESHOLD, 5000f);
        player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75000);
        player.put(CombatClient.NAMESPACE, "combat.userflag", true);
        player.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE, false);
        ObjectManagerClient.registerTemplate(player);
    }

    public static void startPrefab() {
      //  addMasterPublicKeys();
        Engine.registerPlugin("atavism.agis.plugins.PrefabPlugin");
    }

    public static void startProxy() {
        //ProxyStarter.start();
    }

    public static void startQuest() {
        Engine.registerPlugin("atavism.agis.plugins.QuestPlugin");
        Engine.registerPlugin("atavism.agis.plugins.SocialPlugin");
        SocialPlugin.loginmessage = "Welcome to Atavism Online!";
    }

    public static void startWeather() {
        Engine.registerPlugin("atavism.agis.plugins.WeatherPlugin");
        Engine.registerPlugin("atavism.agis.plugins.BonusPlugin");
        Engine.registerPlugin("atavism.agis.plugins.AchievementsPlugin");
        Engine.registerPlugin("atavism.agis.plugins.RankingPlugin");
    }

    public static void startWorldManager() {
        Engine.registerPlugin(new AgisWorldManagerPlugin());
    }

}
