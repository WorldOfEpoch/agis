package atavism.agis.server.messages;

import atavism.agis.plugins.*;
import atavism.management.Management;
import atavism.msgsys.MessageCatalog;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.objects.ObjectTracker;
import atavism.server.plugins.*;

public class MessageInitializer {

    public static void init() {
        MessageCatalog aoMessageCatalog = MessageCatalog.addMsgCatalog("aoMessageCatalog", 1, 500);
        // PropertyMessage
        aoMessageCatalog.addMsgTypeTranslation(PropertyMessage.MSG_TYPE_PROPERTY);
        // LoginMessage
        aoMessageCatalog.addMsgTypeTranslation(LoginMessage.MSG_TYPE_LOGIN);
        // LogoutMessage
        aoMessageCatalog.addMsgTypeTranslation(LogoutMessage.MSG_TYPE_LOGOUT);
        // Add the WorldManagerClient messages
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_ANIMATION);
        aoMessageCatalog.addMsgTypeTranslation(ChatClient.MSG_TYPE_COM);
        aoMessageCatalog.addMsgTypeTranslation(ChatClient.MSG_TYPE_COM_REQ);
        aoMessageCatalog.addMsgTypeTranslation(ChatClient.MSG_TYPE_COM_TARGET_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DC_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DESPAWNED);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DESPAWN_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DETACH);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DIR_LOC_ORIENT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_DISPLAY_CONTEXT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_EXTENSION);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_FOG);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_FREE_REMOTE_OBJ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_GETWNODE_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_MOB_PATH);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_MOB_PATH_CORRECTION);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_MOB_PATH_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_MODIFY_DC);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_NEW_DIRLIGHT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_FREE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_NEW_REGION);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_NEW_REMOTE_OBJ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_OBJINFO_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_GET_OBJECTS_IN);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_ORIENT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_ORIENT_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_PERCEIVER_REGIONS);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_REFRESH_WNODE);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_REPARENT_WNODE_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_ROAD);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_FREE_ROAD);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SETWNODE_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SET_AMBIENT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SOUND);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SPAWNED);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SPAWN_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_SYS_CHAT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_TARGETED_PROPERTY);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_UPDATEWNODE);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_UPDATEWNODE_REQ);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_WNODECORRECT);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_P2P_EXTENSION);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_PERCEPTION_INFO);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_PERCEPTION);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_HOST_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_PLAYER_PATH_WM_REQ);
        // Add the messages for the ObjectManager
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SET_PERSISTENCE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SET_SUBPERSISTENCE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_MODIFY_NAMESPACE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_LOAD_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_LOAD_SUBOBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_LOAD_OBJECT_DATA);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SAVE_OBJECT_DATA);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SAVE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SAVE_SUBOBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GENERATE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GENERATE_SUB_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_SUB_OBJECT_DEPS_READY);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_REGISTER_TEMPLATE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_TEMPLATE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_TEMPLATE_NAMES);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_ENCHANT_PROFILE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_REGISTER_ENCHANT_PROFILE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_REGISTER_QUALITY_INFO);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_QUALITY_INFO);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_REGISTER_ITEM_SET_PROFILE);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_ITEM_SET_PROFILE);

        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_VIP_LEVEL);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_REGISTER_VIP_LEVEL);

        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_UNLOAD_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_UNLOAD_SUBOBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_DELETE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_DELETE_SUBOBJECT);

        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_FIX_WNODE_REQ);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_NAMED_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_MATCHING_OBJECTS);
        aoMessageCatalog.addMsgTypeTranslation(ObjectManagerClient.MSG_TYPE_GET_OBJECT_STATUS);

        // Add InventoryClient messages
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_ADD_ITEM);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_CREATE_INV);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_INV_UPDATE);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_ACTIVATE);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_LOOTALL);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_INV_FIND);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_INV_REMOVE);
        aoMessageCatalog.addMsgTypeTranslation(InventoryClient.MSG_TYPE_DESTROY_ITEM);

        // Add ObjectTracker message
        aoMessageCatalog.addMsgTypeTranslation(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
        aoMessageCatalog.addMsgTypeTranslation(ObjectTracker.MSG_TYPE_NOTIFY_AGGRO_RADIUS);

        // Add EnginePlugin messages
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_DUMP_ALL_THREAD_STACKS);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_GET_PROPERTY);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_GET_PROPERTY_NAMES);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_PLUGIN_STATE);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_SET_PROPERTY);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_TRANSFER_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(EnginePlugin.MSG_TYPE_RELOAD_TEMPLATES);

        // Add Behavior messages

        aoMessageCatalog.addMsgTypeTranslation(Behavior.MSG_TYPE_COMMAND);
        aoMessageCatalog.addMsgTypeTranslation(Behavior.MSG_TYPE_EVENT);

        // Add Quest messages

        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_REQ_QUEST_INFO);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_REQ_CONCLUDE_QUEST);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_INFO);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_GET_QUEST_STATUS);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_RESP);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_NEW_QUESTSTATE);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_CONCLUDE_QUEST);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_LOG_INFO);
        // aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_HISTORY_LOG_INFO);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_STATE_INFO);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_REMOVE_QUEST_RESP);
        aoMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_REQ_RESET_QUESTS);

        // Add AgisInventory message

        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_SETS_EQUIPED);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_AGIS_INV_FIND);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_START);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_COMPLETE);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_OFFER_REQ);
        aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_OFFER_UPDATE);
        // aoMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SWAP_ITEM);

        // Add CombatClient messages

        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_AUTO_ATTACK);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_START_ABILITY);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_START_ABILITY_RESPONSE);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COOLDOWN);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ABILITY_PROGRESS);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_DAMAGE);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_RELEASE_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ABILITY_STATUS);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ABILITY_UPDATE);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ADD_SKILL);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_TRAINING_FAILED);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SKILL_UPDATE);
        aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_GET_ABILITY);
        // aoMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ADD_ABILITY);

        // Add AnimationClient messages

        aoMessageCatalog.addMsgTypeTranslation(AnimationClient.MSG_TYPE_INVOKE_EFFECT);

        // Add InstanceClient messages

        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_REGISTER_INSTANCE_TEMPLATE);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_CREATE_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_GET_INSTANCE_INFO);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_INSTANCE_ENTRY_REQ);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_GET_REGION);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_LOAD_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_UNLOAD_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_DELETE_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_LOAD_INSTANCE_CONTENT);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_INSTANCE_UNLOADED);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_INSTANCE_DELETED);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_GET_ENTITY_OIDS);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_LOAD_INSTANCE_BY_ID);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_INSTANCE_LOADED);
        aoMessageCatalog.addMsgTypeTranslation(InstanceClient.MSG_TYPE_SET_INSTANCE_GROUP);

        // mob manager (temporary);
        aoMessageCatalog.addMsgTypeTranslation(MobManagerClient.MSG_TYPE_REMOVE_SPAWN_GEN);
        aoMessageCatalog.addMsgTypeTranslation(MobManagerClient.MSG_TYPE_CREATE_SPAWN_GEN);
        aoMessageCatalog.addMsgTypeTranslation(MobManagerClient.MSG_TYPE_SET_AGGRO_RADIUS);
        aoMessageCatalog.addMsgTypeTranslation(MobManagerClient.MSG_TYPE_SET_REACTION_RADIUS);

        // Add Proxy messages
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_VOICE_PARMS);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_PLAYER_PATH_REQ);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_UPDATE_PLAYER_IGNORE_LIST);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_RELAY_UPDATE_PLAYER_IGNORE_LIST);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_GET_MATCHING_PLAYERS);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_PLAYER_IGNORE_LIST);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_PLAYER_IGNORE_LIST_REQ);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_GET_PLAYER_LOGIN_STATUS);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_LOGOUT_PLAYER);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_ADD_STATIC_PERCEPTION);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_REMOVE_STATIC_PERCEPTION);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_LOGIN_SPAWNED);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_ACCOUNT_LOGIN);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_SERVER_SHUTDOWN);
        aoMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_SERVER_SHUTDOWN_MESSAGE);

        // Search messages
        aoMessageCatalog.addMsgTypeTranslation(SearchMessage.MSG_TYPE_SEARCH);

        // Add TrainerClient messages

        aoMessageCatalog.addMsgTypeTranslation(TrainerClient.MSG_TYPE_REQ_TRAINER_INFO);
        aoMessageCatalog.addMsgTypeTranslation(TrainerClient.MSG_TYPE_REQ_SKILL_TRAINING);
        aoMessageCatalog.addMsgTypeTranslation(TrainerClient.MSG_TYPE_TRAINING_INFO);

        // Add ClassAbilityClient Messages
        aoMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_STAT_XP_UPDATE);
        aoMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_HANDLE_EXP);

        // Add GroupClient messages
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_INVITE);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_INVITE_RESPONSE);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_CHAT);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_REQUEST_GROUP_INFO);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_INFO_RESPONSE);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_SET_ALLOWED_SPEAKER);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_MUTE_VOICE_CHAT);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_VOICE_CHAT_STATUS);

        // Add VoiceClient messages
        aoMessageCatalog.addMsgTypeTranslation(VoiceClient.MSG_TYPE_VOICECLIENT);
        aoMessageCatalog.addMsgTypeTranslation(VoiceClient.MSG_TYPE_VOICE_MEMBER_ADDED);
        aoMessageCatalog.addMsgTypeTranslation(VoiceClient.MSG_TYPE_VOICE_MEMBER_REMOVED);

        // atavism.management
        aoMessageCatalog.addMsgTypeTranslation(Management.MSG_TYPE_GET_PLUGIN_STATUS);

        // Add VendorClient messages
        aoMessageCatalog.addMsgTypeTranslation(VendorClient.MSG_TYPE_ITEM_PURCHASE);
        aoMessageCatalog.addMsgTypeTranslation(VendorClient.MSG_TYPE_VENDOR_BALANCE);
        aoMessageCatalog.addMsgTypeTranslation(VendorClient.MSG_TYPE_VENDOR_GET_BALANCE);
        aoMessageCatalog.addMsgTypeTranslation(VendorClient.MSG_TYPE_VENDOR_INFO_REQ);

        // Add BillingClient messages
        aoMessageCatalog.addMsgTypeTranslation(BillingClient.MSG_TYPE_BILLING_BALANCE);
        aoMessageCatalog.addMsgTypeTranslation(BillingClient.MSG_TYPE_DECREMENT_TOKEN_BALANCE);
        aoMessageCatalog.addMsgTypeTranslation(BillingClient.MSG_TYPE_GET_TOKEN_BALANCE);
        aoMessageCatalog.addMsgTypeTranslation(BillingClient.MSG_GET_PLAYER);

        // aoMessageCatalog.addMsgTypeTranslation(CurrencyClient.MSG_TYPE_GET_BALANCE);
        // aoMessageCatalog.addMsgTypeTranslation(CurrencyClient.MSG_TYPE_UPDATE_BALANCE);
        // aoMessageCatalog.addMsgTypeTranslation(CurrencyClient.MSG_TYPE_LIST_CURRENCIES);

        aoMessageCatalog.addMsgTypeTranslation(DataLoggerClient.MSG_TYPE_DATA_LOG);

        MessageCatalog worldMessageCatalog = MessageCatalog.addMsgCatalog("worldMessageCatalog", 501, 500);

        //
        // Add your world-specific messages here. Each call to addMsgTypeTranslation
        // adds the message type which is the second argument to the world message
        // catalog. Each message type must be defined in YourWorldModule by a call
        // to MessageType.intern(message_type_string);
        //

        worldMessageCatalog.addMsgTypeTranslation(ProxyPlugin.MSG_TYPE_SERVER_RELOAD);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_MEMBERS_UPDATE);

        worldMessageCatalog.addMsgTypeTranslation(Behavior.MSG_TYPE_LINKED_AGGRO);

        worldMessageCatalog.addMsgTypeTranslation(RankingClient.MSG_TYPE_GET_RANKING);
        worldMessageCatalog.addMsgTypeTranslation(RankingClient.MSG_TYPE_GET_RANKING_LIST);

        worldMessageCatalog.addMsgTypeTranslation(AchievementsClient.MSG_TYPE_GET_ACHIEVEMENTS);
        worldMessageCatalog.addMsgTypeTranslation(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENTS_TITLE);
        worldMessageCatalog.addMsgTypeTranslation(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENT);
        worldMessageCatalog.addMsgTypeTranslation(AchievementsClient.MSG_TYPE_RANGING_DATA);

        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_GET_ALL_VIP);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_GET_VIP);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_EXTEND_VIP);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_BONUS_ADD);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_BONUS_REMOVE);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_BONUSES_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);

//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_CURR_ICON);
//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_SKILL_ICON);
//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_EFFECT_ICON);
//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ABILITY_ICON);
//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_BUILD_OBJ_ICON);
//        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_CRAFTING_RECIPE_ICON);
        // worldMessageCatalog.addMsgTypeTranslation(YourWorldModule.MSG_TYPE_YOUR_MESSAGE_TYPE)MSG_TYPE_GET_ITEM


        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_COUNT_GENERIC_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ITEMS_WITH_PARAM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SET_ITEM_COUNT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SET_ITEM_PROPERTY);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_AS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ENCHANTING_DETAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ENCHANT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET_DETAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_RESPONSE_OID);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ITEM_PROPERTY);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SOCKETING_DETAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_INSERT_TO_SOCKET);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ITEM_ACQUIRE_STATUS_CHANGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ITEM_EQUIP_STATUS_CHANGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_REQ_OPEN_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_REMOVE_SPECIFIC_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_REMOVE_GENERIC_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_SPECIFIC_ITEM_DATA);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_GENERIC_ITEM_DATA);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_NEEDS_RESPONSE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PLACE_BAG);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_MOVE_BAG);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_REMOVE_BAG);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_MOVE_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_LOOT_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_LOOT_ALL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_LOOT_ROLL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_LOOT_GROUND_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DROP_GROUND_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_EFFECT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_LOOT_LIST);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_MERCHANT_LIST);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SELL_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PICKUP_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_QUEST_ITEMS_LIST);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SEND_INV_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_MAIL_READ);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_MAIL_TAKE_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_RETURN_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DELETE_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SEND_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SEND_PURCHASE_MAIL);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_CHECK_CURRENCY);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ALTER_CURRENCY);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_SKINS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_RELOAD_ITEMS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SET_WEAPON);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PURCHASE_SKIN);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SET_SKIN_COLOUR);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ACCOUNT_ITEM_COUNT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_USE_ACCOUNT_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_ITEM_ACTIVATED);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_EQUIPPED_ITEM_USED);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_REPAIR_ITEMS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_CREATE_STORAGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_BANK_ITEMS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_STORE_ITEM_IN_BANK);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_RETRIEVE_ITEM_FROM_BANK);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_OPEN_STORAGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_STORAGE_CONTENTS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_ITEM_STORE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PURCHASE_STORE_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_SLOT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_PET_SLOT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ_RESPONSE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_UPDATE_STORAGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_START_PLAYER_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_START_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_STOP_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_OPEN_PLAYER_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_CANCEL_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_PLAYER_SHOP_BUY);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DELETE_PLAYER_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_FROM_LOOT_TABLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_EQUIP_ITEMS);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_UNEQUIP_ITEM);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_LOOT_ALL_F);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_SWITCH_WEAPON);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON_CLIENT);
        worldMessageCatalog.addMsgTypeTranslation(AgisInventoryClient.MSG_TYPE_GET_PET_INVENTORY);
        // Class Ability Client
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_INCREASE);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_DECREASE);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_RESET);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_ALTER_CURRENT);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_GET_SKILL);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_COMBAT_GET_PLAYER_SKILL_LEVEL);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_LEARN_ABILITY);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_SET_SKILL_STATE);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_SKILL_LEVEL_CHANGE);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_ADD_SKILL_POINT);
        worldMessageCatalog.addMsgTypeTranslation(ClassAbilityClient.MSG_TYPE_UNLEARN_ABILITY);
        // Quest Client
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_OFFER_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_START_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_CAN_PLAYER_START_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_ITEM_REQS);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_ITEM_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_TASK_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_REQ_QUEST_PROGRESS);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_COMPLETE_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_QUEST_CONCLUDE_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(QuestClient.MSG_TYPE_ABANDON_QUEST);
        // Combat Client
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SET_COMBAT_INFO_STATE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_MOB_DEATH);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_ABILITY_USED);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_STOP_AUTO_ATTACK);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_TARGET_TYPE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_LOGOUT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_FACTION_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_GET_AOE_TARGETS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_UPDATE_ACTIONBAR);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_APPLY_EFFECT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_REMOVE_EFFECT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ALTER_EXP);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_UPDATE_BREATH);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_UPDATE_FATIGUE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ALTER_HEARTS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_KNOCKED_OUT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_REGEN_HEALTH_MANA);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_DECREMENT_WEAPON_USES);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_DISMOUNT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_GET_PLAYER_STAT_VALUE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_REMOVE_BUFF);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_AUTO_ATTACK_COMPLETED);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ALTER_THREAT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ARENA_RELEASE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_FALLING_EVENT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_CLIENT_LEVEL_LOADED);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_SKILL_DIFF);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_GET_COMBAT_OBJECT_PARAMS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_STATS_MODIFY);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_DEL_STATS_MODIFY);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SET_WMGR_STATS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_SPRINT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_RESET_ATTACKER);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_STATE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_COMBAT_EVENT);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_GET_PLAYER_COOLDOWNS);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_DODGE);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_ABILITY_VECTOR);
        worldMessageCatalog.addMsgTypeTranslation(CombatClient.MSG_TYPE_DEBUG_ABILITY);
        // Arena Client
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_LEAVE_ARENA);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_GET_ARENA_LIST);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_GET_ARENA_STATS);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_GET_ARENA_TYPES);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_JOIN_QUEUE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_GROUP_JOIN_QUEUE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_LEAVE_QUEUE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_GROUP_LEAVE_QUEUE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_REMOVE_PLAYER);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_ARENA_KILL);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_PICKUP_FLAG);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DROP_FLAG);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_ACTIVATE_MACHINE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DOT_SCORE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_START_ARENA_CHECK);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_ADD_CREATURE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DESPAWN_GATES);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_END_ARENA);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_CHALLENGE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_ACCEPT_CHALLENGE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_DECLINE_CHALLENGE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_DISCONNECT);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_REMOVE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_START);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_DEFEAT);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_DISCONNECT);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_DUEL_REMOVE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_REMOVE_EFFECTS);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_ACTIVATE_ARENA_ABILITY);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_COMPLETE_TUTORIAL);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_CHANGE_RACE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_SELECT_RACE);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_ALTER_EXP);
        worldMessageCatalog.addMsgTypeTranslation(ArenaClient.MSG_TYPE_RELEASE_REQUEST);
        // Group Client
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_LEAVE);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_INVITE_BY_NAME);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GET_PLAYER_BY_NAME);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_CREATE_GROUP);
        // worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GET_FRIENDS);
        // worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_ADD_FRIEND);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_PROMOTE_LEADER);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GET_GROUP_MEMBERS);
        worldMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_GROUP_SETTINGS);

        // Agis Mob Client
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DIALOG_CHECK);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_GET_PLAYERS_ONLINE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_GET_INSTANCE_TEMPLATE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_INSTANCE_MOBS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_ARENA_CREATURE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_PET);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_TAME_BEAST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_PET_COMMAND_UPDATE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SEND_PET_COMMAND);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_UPDATE_PET_STATS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_PET_TARGET_LOST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_TARGET_IN_REACTION_RANGE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_ADD_TARGET_TO_CHECK);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_REMOVE_TARGET_TO_CHECK);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_INTERACT_WITH_OBJECT);
        
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_ADD_NM_OBJECT);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DEBUG_NM);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_GET_TEMPLATES);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_MOB_SPAWN);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_LOOT_TABLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_EDIT_QUEST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_GET_ISLANDS_DATA);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_VERIFY_ISLAND_ACCESS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_ENTER_WORLD);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_REQUEST_DEVELOPER_ACCESS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_ISLAND);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_VIEW_MARKERS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_REQUEST_SPAWN_DATA);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_EDIT_SPAWN_MARKER);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DELETE_SPAWN_MARKER);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_EDIT_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_FACTION);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_EDIT_FACTION);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CATEGORY_UPDATED);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_DOME_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DOME_ENQUIRY);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DOME_ENTRY_REQUEST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DOME_LEAVE_REQUEST);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_ACTIVATE_DOME_ABILITY);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_MOB_KILLED);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_OBJECT_ACTIVATED);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_GET_INTERACTION_OPTIONS);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_START_INTERACTION);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DIALOGUE_OPTION_CHOSEN);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_START_DIALOGUE);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_INVALID_PATH);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_PLAY_COORD_EFFECT);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DELETE_SPAWN_GENERATOR);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_CREATE_SHOP_SPAWN);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DELETE_SHOP_SPAWN);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DESPAWN_PLAYER_SHOP);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_MOB_GET_ACTOR_SPEED);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_DEBUG_MOB);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_SPAWN_INTERACTIVE_OBJECT);
        worldMessageCatalog.addMsgTypeTranslation(AgisMobClient.MSG_TYPE_PET_LEVELUP);

        // Faction Client
        worldMessageCatalog.addMsgTypeTranslation(FactionClient.MSG_TYPE_GET_REACTION);
        worldMessageCatalog.addMsgTypeTranslation(FactionClient.MSG_TYPE_GET_STANCE);
        worldMessageCatalog.addMsgTypeTranslation(FactionClient.MSG_TYPE_GET_STANCE_TARGETS);
        aoMessageCatalog.addMsgTypeTranslation(FactionClient.MSG_TYPE_UPDATE_PVP_STATE);
        aoMessageCatalog.addMsgTypeTranslation(FactionClient.MSG_TYPE_ALTER_REPUTATION);

        // Social Client
        // worldMessageCatalog.addMsgTypeTranslation(SocialClient.MSG_TYPE_CHANNEL_CHANGE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_GLOBAL_CHAT);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_IS_ON_BLOCK_LIST);
        // aoMessageCatalog.addMsgTypeTranslation( SocialClient.MSG_TYPE_BLOCK_LIST);
        // aoMessageCatalog.addMsgTypeTranslation( SocialClient.MSG_TYPE_DEL_FRIEND);
        // aoMessageCatalog.addMsgTypeTranslation( SocialClient.MSG_TYPE_GET_FRIENDS);
        aoMessageCatalog.addMsgTypeTranslation(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE);
        aoMessageCatalog.addMsgTypeTranslation(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE_RESPONSE);

        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_SOCIAL_ADD_FRIEND);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_SOCIAL_DEL_FRIEND);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_SOCIAL_GET_FRIENDS);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_ADD_BLOCK);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_TYPE_DEL_BLOCK);
        aoMessageCatalog.addMsgTypeTranslation(GroupClient.MSG_SOCIAL_INVITE_RESPONSE);

        // Data Logger
        // aoMessageCatalog.addMsgTypeTranslation( DataLoggerClient.MSG_TYPE_DATA_LOG);
        aoMessageCatalog.addMsgTypeTranslation(DataLoggerClient.MSG_TYPE_CHARACTER_CREATED);
        aoMessageCatalog.addMsgTypeTranslation(DataLoggerClient.MSG_TYPE_CHARACTER_DELETED);
        // aoMessageCatalog.addMsgTypeTranslation( DataLoggerClient.MSG_TYPE_EVENT_LOG);

        // Crafting Client
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_HARVEST_RESOURCE);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_GATHER_RESOURCE);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_CRAFTING_CRAFT_ITEM);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_CRAFTING_GRID_UPDATED);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_GET_BLUEPRINTS);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_CREATE_RESOURCE_NODE_FROM_MOB);
        aoMessageCatalog.addMsgTypeTranslation(CraftingClient.MSG_TYPE_DESTROY_MOB_RESOURCE_NODE);

        // Voxel Client
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_CREATE_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_EDIT_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_PURCHASE_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_SELL_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_DELETE_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_CLAIM_PERMISSION);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_CLAIM_ACTION);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_PLACE_CLAIM_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_EDIT_CLAIM_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_RESOURCES);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_NO_BUILD_CLAIM_TRIGGER);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_UPGRADE_BUILDING_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_BUILDING_TEMPLATE);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_CLAIM_OBJECT_INFO);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_ATTACK_BUILDING_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_TAKE_CLAIM_RESOURCE);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_CHEST_STORAGE_OID);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_SEND_UPDATE_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_ADD_DYNAMIC_NM_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_BUILDING_LOC);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_BUILDING_DAMAGE);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_ADD_DYNAMIC_NM_OBJECT);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_BUILDING_IN_AREA);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_UPGRADE_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_PAY_TAX_CLAIM);
        aoMessageCatalog.addMsgTypeTranslation(VoxelClient.MSG_TYPE_GET_BUILDING_IS_ATTACKABLE);

        // Agis Proxy Client
        // aoMessageCatalog.addMsgTypeTranslation( AgisProxyClient.MSG_TYPE_GET_ALL_PLAYER_OIDS);

        // Agis World Manager Client
        worldMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_CHANGE_INSTANCE);
        worldMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_LEAVE_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_SET_MOVEMENT_STATE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_WATER_REGION_TRANSITION);
        // aoMessageCatalog.addMsgTypeTranslation( AgisWorldManagerClient.MSG_TYPE_CHANGE_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_RETURN_TO_LAST_INSTANCE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_LOGOUT_REQUEST);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_CANCEL_LOGOUT_REQUEST);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_CHECK_IF_TARGETS_IN_AREA);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_SET_UNDERWATER);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_SPAWN_TRAP);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_REVIVE);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_MOB_FIND_NEAREST_POINT);
        aoMessageCatalog.addMsgTypeTranslation(AgisWorldManagerClient.MSG_TYPE_MOB_GET_PATH);

        // Guild Plugin
        aoMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_CREATE_GUILD);
        aoMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_INVITE_RESPONSE);
        aoMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GUILD_COMMAND);
        aoMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE);
        worldMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GUILD_CLAIM_PERMISSION);
        worldMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GET_GUILD_WAREHOUSE);
        worldMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GUILD_WAREHOUSE_PERMISSION);
        worldMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GET_GUILD_MERCHANT);
        aoMessageCatalog.addMsgTypeTranslation(GuildClient.MSG_TYPE_GUILD_ADD_RESOURCES);

        // Weather Plugin
        aoMessageCatalog.addMsgTypeTranslation(WeatherClient.MSG_TYPE_SET_WORLD_TIME);
        aoMessageCatalog.addMsgTypeTranslation(WeatherClient.MSG_TYPE_SET_WEATHER_PROFILE);
        aoMessageCatalog.addMsgTypeTranslation(WeatherClient.MSG_TYPE_GET_WEATHER_PROFILE);
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_GET_WEATHER);
        // NavMesh
        aoMessageCatalog.addMsgTypeTranslation(WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY);
        // Auction Plugin
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_SELL);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_BUY);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_LIST);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_OWNER_LIST);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_SEARCH);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_GET_FOR_GROUP);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_TAKE_ALL);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_GET_AUCTIONS_LIST);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_ORDER);
        aoMessageCatalog.addMsgTypeTranslation(AuctionClient.MSG_TYPE_AUCTION_CANCELL);
        
        
        //Vehicle Plugin
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_SPAWN_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_UPDATE_LOCATION);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGERS_LOCATION);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_UPDATE_PASSENGER_COUNT);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_CONTROL_VEHICLE_MOVEMENT);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_SET_DRIVER);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_DRIVE_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_STOP_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_ENTER_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_EXIT_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_INTERACT_WITH_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_DEBUG_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_REMOVE_VEHICLE_SPAWN);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_CREATE_VEHICLE_SPAWN);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_SPAWN_INSTANCE_VEHICLES);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_LOAD_INSTANCE_VEHICLES);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_VEHICLE_DESPAWNED);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_DESPAWNED_VEHICLE);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_REQUEST_DEVELOPER_ACCESS);
        //worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_VEHICLE_GET_ACTOR_SPEED);
        worldMessageCatalog.addMsgTypeTranslation(AgisVehicleClient.MSG_TYPE_VERIFY_ISLAND_ACCESS);

    }
}