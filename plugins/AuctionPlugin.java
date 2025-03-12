package atavism.agis.plugins;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.core.Agis;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.AuthDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.ItemDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.*;
import atavism.agis.objects.Currency;
import atavism.agis.plugins.BonusClient.BonusesUpdateMessage;
import atavism.agis.plugins.BonusClient.GlobalEventBonusesUpdateMessage;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.objects.Template;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

/**
 * This class is responsible for catching the messages sent out by the ArenaClient 
 * then dealing with the data received appropriately. In particular, this class handles
 * the queuing for arenas and will create new arenas when enough players are queued.
 * @author Andrew Harrison
 * @param <var>
 *
 */
public class AuctionPlugin extends EnginePlugin {

	
	public AuctionPlugin() {
		super(AUCTION_PLUGIN_NAME);
		setPluginType("Auction");
	}

	public String getName() {
		return AUCTION_PLUGIN_NAME;
	}

	public static String AUCTION_PLUGIN_NAME = "Auction";

	protected static final Logger log = new Logger("Auction");

	public void onActivate() {
		log.debug("AuctionPlugin.onActivate()");
		registerHooks();
		MessageTypeFilter filter = new MessageTypeFilter();
	//	filter.addType(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
		filter.addType(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);
        filter.addType(BonusClient.MSG_TYPE_BONUSES_UPDATE);
        filter.addType(AuctionClient.MSG_TYPE_AUCTION_SELL);
        filter.addType(AuctionClient.MSG_TYPE_AUCTION_LIST);
        filter.addType(AuctionClient.MSG_TYPE_AUCTION_BUY);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_SEARCH);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_GET_FOR_GROUP);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_TAKE_ALL);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_OWNER_LIST);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_ORDER);
		filter.addType(AuctionClient.MSG_TYPE_AUCTION_CANCELL);
		filter.addType(DataLoggerClient.MSG_TYPE_CHARACTER_DELETED);
	    Engine.getAgent().createSubscription(filter, this);

		// Create responder subscription
		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);

		
		AuctionChecker auctionChecker = new AuctionChecker(this);
		Engine.getExecutor().scheduleAtFixedRate(auctionChecker, 10, 1, TimeUnit.MINUTES);
		
		AuctionOldChecker auctionOldChecker = new AuctionOldChecker();
		Engine.getExecutor().scheduleAtFixedRate(auctionOldChecker, 1, 1, TimeUnit.HOURS);
		
		loadData();
		log.debug("AUCTION_LOAD_DELAY=" + AUCTION_LOAD_DELAY);
		Engine.getExecutor().schedule(new AuctionDelayStart(), AUCTION_LOAD_DELAY, TimeUnit.SECONDS);
        
        Log.debug("Registering Auction plugin");
		Engine.registerStatusReportingPlugin(this);

		}

public class AuctionDelayStart implements Runnable {
		
		protected AuctionDelayStart auctionPlugin; 
		public AuctionDelayStart() {
		}
		
		@Override
		public void run() {
			if (Log.loggingDebug)
				log.debug("AuctionDelayStart Checker Tick");
				try {
					auctions = aDB.getAuctions();
			     	for (Auction a : auctions) {
					  if (a.GetItemOid()!=null) {
						  if(ObjectManagerClient.loadObject(a.GetItemOid())==null) {
							  log.error("Auction can't load item "+a.GetItemOid()+" for auction "+a.GetId());
						  }else{
							  log.debug("Auction loaded item "+a.GetItemOid()+" for auction "+a.GetId()); 
						  }
					  }else {
						  log.error("Auction item is null for auction "+a.GetId());
					  }
				  }
				  AuctionGroup();
			} catch (Exception e) {
					Log.dumpStack(e.getMessage());
				}
			if (Log.loggingDebug)
				log.debug("AuctionOldChecker Checker Tick End");
		}

}
	protected void registerHooks() {
		log.debug("AuctionPlugin.registerHooks() ");
		//	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME, new ServerTimeHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_BONUSES_UPDATE, new BonusesUpdateHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE, new GlobalEventBonusesUpdateHook());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_SELL, new CreateAuction());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_BUY, new BidAuction());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_ORDER, new OrderAuction());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_LIST, new SendAuctionList());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_OWNER_LIST, new SendAuctionOwnerList());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_SEARCH, new SendAuctionListSearcheSort());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_GET_FOR_GROUP, new GetAuctionForGroup());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_TAKE_ALL, new TakeAll());
		getHookManager().addHook(AuctionClient.MSG_TYPE_AUCTION_CANCELL, new CancelAuction());
		getHookManager().addHook(DataLoggerClient.MSG_TYPE_CHARACTER_DELETED, new CharacterDeletedHook());
	       // Hook to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());

		//loadAuctionsFromDatabase();
		log.debug("AuctionPlugin.registerHooks() End");
	}

	void loadData() {
		loadAuctionsFromDatabase();
		log.debug("AuctionPlugin: completed Plugin activation");
		ContentDatabase cDB = new ContentDatabase(false);
		cDB.loadEditorOptions();
		auctionProfiles = cDB.loadAuctionProfile();
		MobDatabase mDB = new MobDatabase(false);
		ArrayList<Currency> currencies = mDB.loadCurrencies(-1);
		for (Currency currency : currencies) {
			Agis.CurrencyManager.register(currency.getCurrencyID(), currency);
			log.debug("CURRENCY: currency Table: [" + currency.getCurrencyID() + ":" + currency.getCurrencyName() + "]");
		}
		aDB = new AccountDatabase(true);
		authDB = new AuthDatabase();
		log.debug("AuctionPlugin.onActivate() End");
	}

	class CharacterDeletedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			DataLoggerClient.CharacterDeletedMessage getMsg = (DataLoggerClient.CharacterDeletedMessage) msg;
			OID characterOID = getMsg.getSourceOid();
			OID accountID = getMsg.getAccountId();
			String characterName = getMsg.getCharacterName();
			log.debug("Deleting Auctions for deleted character Oid="+characterOID+"; name="+characterName+"; AccountId="+accountID);
			ArrayList<Auction> _auctions = new ArrayList<Auction>(auctions);
			for (Auction auction : _auctions) {

				if (auction.GetOwnerOid().equals(characterOID)) {
					auctions.remove(auction);
					aDB.deleteAuction(auction);
				}
			}
			AuctionGroup();
			return true;
		}
	}
	
	/**
	 * Reload definitions from database
	 */
	protected void ReloadTemplates(Message msg) {
		Log.debug("AuctionPlugin ReloadTemplates Start");
		loadData();
		Log.debug("AuctionPlugin ReloadTemplates End");
	}
	
	/**
     * Loads in item templates from the database and registers them.
     * @param iDB
     */
    public void loadItemsFromDatabase() {
    	ItemDatabase iDB = new ItemDatabase(false);
    	LinkedList<Integer> storeItems= new LinkedList<Integer>();
		ArrayList<Template> items = iDB.loadItemTemplates(storeItems);
        for (Template tmpl: items)
        	ObjectManagerClient.registerTemplate(tmpl);
        iDB.close();
    }
	
	public void loadAuctionsFromDatabase() {
		
		ContentDatabase coDB = new ContentDatabase(false);
		
		String worldTimeZone = coDB.loadGameSetting("WORLD_TIME_ZONE");
		if (worldTimeZone != null) {
			WORLD_TIME_ZONE = worldTimeZone;
			if (Log.loggingDebug)
				log.debug("GameSettings set WORLD_TIME_ZONE:" + WORLD_TIME_ZONE);
		}
	
		String auction_load_delay = coDB.loadGameSetting("AUCTION_LOAD_DELAY");
		if (auction_load_delay != null) {
			AUCTION_LOAD_DELAY = Integer.parseInt(auction_load_delay);
			if (Log.loggingDebug)
				log.debug("GameSettings set AUCTION_LOAD_DELAY:" + AUCTION_LOAD_DELAY);
		}
		
		String auction_npc_only = coDB.loadGameSetting("AUCTION_NPC_ONLY");
		if (auction_npc_only != null) {
			AUCTION_NPC_ONLY = Boolean.parseBoolean(auction_npc_only);
			if (Log.loggingDebug)
				log.debug("GameSettings set AUCTION_NPC_ONLY:" + AUCTION_NPC_ONLY);
		}
	//	coDB.close();
	}
	
	public class AuctionChecker implements Runnable {
		
		protected AuctionPlugin auctionPlugin; 
		public AuctionChecker(AuctionPlugin ap) {
			this.auctionPlugin = ap;
		}
		
		@Override
		public void run() {
			if (Log.loggingDebug)
				log.debug("Auction Checker Tick");
			TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
    	 	Calendar cal = Calendar.getInstance(tz);
    		try {
				for(Auction auction : auctionPlugin.auctions){
					 Date d = cal.getTime();
					 if (d.after(auction.GetExpirateDate())) {
						 log.debug("Checker: auction id:"+auction.GetId()+" Ctime:"+d+" after auctionExpireTime:"+auction.GetExpirateDate());
						 auction.SetStatus(3);
						 aDB.saveAuction(auction);
						 aDB.insertAuctionEnded(auction);
						 aDB.deleteAuction(auction);
							 String name = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
						 AgisInventoryClient.sendMail(auction.GetOwnerOid(), name, "Auction House transaction", "Auction of "+auction.GetItemName()+" is Expired ", 0, 0, false);
						 auctionPlugin.auctions.remove(auction);
					 }else {
						 log.debug("Checker: auction id:"+auction.GetId()+" Ctime:"+d+" before auctionExpireTime:"+auction.GetExpirateDate());
							
					 }
				}
			} catch (Exception e) {
				Log.dumpStack(e.getMessage());
			}
    		auctionPlugin.AuctionGroup();
			if (Log.loggingDebug)
				log.debug("Auction Checker Tick End");
		}
	}

	public class AuctionOldChecker implements Runnable {
		
		protected AuctionOldChecker auctionPlugin; 
		public AuctionOldChecker() {
		}
		
		@Override
		public void run() {
			if (Log.loggingDebug)
				log.debug("AuctionOldChecker Checker Tick");
				try {
						 aDB.deleteOldAuction();
			} catch (Exception e) {
				Log.dumpStack(e.getMessage());
			}
			if (Log.loggingDebug)
				log.debug("AuctionOldChecker Checker Tick End");
		}
	}

	class TakeAll implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			boolean buying = (boolean) message.getProperty("buying");
			boolean selling = (boolean) message.getProperty("selling");
			boolean bought = (boolean) message.getProperty("bought");
			boolean sold = (boolean) message.getProperty("sold");
			boolean expired = (boolean) message.getProperty("expired");
			log.debug("TakeAll Start buying:" + buying + " selling:" + selling + " bought:" + bought + " sold:" + sold + " expired:" + expired);
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
			
			synchronized(playerOid.toString().intern()) {
			ArrayList<Auction> _auctions = new ArrayList<Auction>();
			log.debug("TakeAll Start");
			HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
			_auctions = aDB.getSoldAuctions(playerOid);
			
			float vipModp = 0;
			long vipMod = 0;
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
				vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
      			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
      		}
			if(globalEventBonusesArray.containsKey("AuctionCost")) {
				vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
      			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
      		}
			log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
			
			long costPriceValue = profile.cost_price_value +  vipMod ;
			if(costPriceValue < 0)
				costPriceValue = 0l;
			float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp /100);
			if(costPriceValueP < 0)
				costPriceValueP = 0;
			for (Auction auc : _auctions) {
				if(auc.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				AgisInventoryClient.alterCurrency(playerOid, profile.currency, auc.GetBuyout() * auc.GetItemCount() - (costPriceValue +  Math.round(Math.ceil(auc.GetBuyout() * auc.GetItemCount() * costPriceValueP / 100f))));
				if (auc.GetStatus() == 2) {
					auc.SetStatus(4);
				} else if (auc.GetStatus() == 6) {
					auc.SetStatus(7);
				}
				aDB.saveAuctionEnded(auc);
			}

			_auctions = aDB.getWinAuctions(playerOid);

			for (Auction auc : _auctions) {
				if(auc.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				itemsToGenerate.clear();
				itemsToGenerate.put(auc.GetItemTemplateId(), auc.GetItemCount());
				if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
					Log.debug("TakeAll:getWinAuctions INVENTORY_FULL auction " + auc);
					sendTransactionsUpdate(playerOid, buying, selling, bought, sold, expired);
					return true;
				}
				Log.debug("TakeAll:getWinAuctions new item generated itemOid:" + auc.GetItemOid() + " from auction " + auc);
				if (auc.GetItemOid() != null) {
					if (ObjectManagerClient.loadObject(auc.GetItemOid()) == null) {
						log.debug("TakeAll Auction can't load item " + auc.GetItemOid() + " for auction " + auc.GetId());
					} else {
						log.debug("TakeAll Auction loaded item " + auc.GetItemOid() + " for auction " + auc.GetId());
					}
				} else {
					log.debug("TakeAll Auction item is null for auction " + auc.GetId());
				}
				OID itemOid2 = AgisInventoryClient.generateItemAsItem(playerOid, auc.GetItemOid(), auc.GetItemCount(),true);
				Log.debug("TakeAll:getWinAuctions new item generated itemOid:" + itemOid2 + " from auction " + auc);
				ObjectManagerClient.saveObject(itemOid2);
				if (auc.GetStatus() == 2) {
					auc.SetStatus(6);
				} else if (auc.GetStatus() == 4) {
					auc.SetStatus(7);
				}
				aDB.saveAuctionEnded(auc);
			}
			_auctions = aDB.getOwnExpiredAuctions(playerOid);
			for (Auction auc : _auctions) {
				if(auc.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				itemsToGenerate.clear();
				itemsToGenerate.put(auc.GetItemTemplateId(), auc.GetItemCount());
				if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToGenerate)) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
					Log.debug("TakeAll: getOwnExpierdAuctions INVENTORY_FULL auction " + auc);
					sendTransactionsUpdate(playerOid, buying, selling, bought, sold, expired);
					return true;
				}

				Log.debug("TakeAll  new item generated itemOid:" + auc.GetItemOid() + " from auction " + auc);
				if (auc.GetMode() == 0) {
					if (auc.GetItemOid() != null) {
						if (ObjectManagerClient.loadObject(auc.GetItemOid()) == null) {
							log.debug("TakeAll Auction can't load item " + auc.GetItemOid() + " for auction " + auc.GetId());
						} else {
							log.debug("TakeAll Auction loaded item " + auc.GetItemOid() + " for auction " + auc.GetId());
						}
					} else {
						log.debug("TakeAll Auction item is null for auction " + auc.GetId());
					}
					OID itemOid2 = AgisInventoryClient.generateItemAsItem(playerOid, auc.GetItemOid(), auc.GetItemCount(),true);
					Log.debug("TakeAll  new item generated itemOid:" + itemOid2 + " from auction " + auc);
					ObjectManagerClient.saveObject(itemOid2);
				} else if (auc.GetMode() == 1) {
					AgisInventoryClient.alterCurrency(playerOid, profile.currency, auc.GetBuyout() * auc.GetItemCount());
				}
				auc.SetStatus(7);
				aDB.saveAuctionEnded(auc);
			}
			}
			sendTransactionsUpdate(playerOid, buying, selling, bought, sold, expired);
			return true;
			
		}
	}

	void sendTransactionsUpdate(OID playerOid,boolean buying,boolean selling,boolean bought,boolean sold ,boolean expired ) {
		AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
		Engine.getAgent().sendBroadcast(invUpdateMsg);
		AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
		
		ArrayList<Auction> _auctions = new ArrayList<Auction>();
		if (buying) {
			_auctions = aDB.getOwnOrderAuctions(playerOid);
		}else if (selling) {
			_auctions = aDB.getOwnSellAuctions(playerOid);
		}else if (bought) {
			_auctions = aDB.getWinAuctions(playerOid);
		}else if (sold) {
			_auctions = aDB.getSoldAuctions(playerOid);
		}else if (expired) {
			_auctions = aDB.getOwnExpiredAuctions(playerOid);
		}
		 log.debug("sendTransactionsUpdate: _auctions:" + _auctions);
			
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "AuctionOwnerListUpdate");
		HashMap<Integer, Integer> itemCounts = new HashMap<Integer, Integer>();
		int numItems = 0;
		for (Auction auction : _auctions) {
			if(auction.GetAuctioneer() != playerAuctioneer.get(playerOid))
				continue;
			AgisItem item = AgisInventoryClient.getItem(auction.GetItemOid());
			if (item == null) {
				log.debug("sendTransactionsUpdate: item is null:" + item + ", oid=" + auction.GetItemOid());
				if (auction.GetItemOid() != null) {
					if (ObjectManagerClient.loadObject(auction.GetItemOid()) == null) {
						log.debug("sendTransactionsUpdate: Auction can't load item " + auction.GetItemOid() + " for auction " + auction.GetId());
					} else {
						log.debug("sendTransactionsUpdate: Auction loaded item " + auction.GetItemOid() + " for auction " + auction.GetId());
					}
				} else {
					log.debug("sendTransactionsUpdate: Auction item is null for auction " + auction.GetId());
				}

			}
			 log.debug("sendTransactionsUpdate: auction:" + auction+" numItems:"+numItems);
					boolean itemExists = true;
				 item = AgisInventoryClient.getItem(auction.GetItemOid());
				if (item == null) {
					 log.debug("sendTransactionsUpdate: item is null:"+item+", oid=" + auction.GetItemOid());
					itemExists = false;
				}
				if (itemExists) {
					props.put("auction_" + numItems + "Id", auction.GetId());
					props.put("auction_" + numItems + "ExpirateDate", auction.GetExpirateDate().toString());
					props.put("auction_" + numItems + "Buyout", auction.GetBuyout());
					props.put("auction_" + numItems + "Currency", auction.GetCurrency());
					props.put("auction_" + numItems + "Mode", auction.GetMode());
					props.put("item_" + numItems + "TemplateID", item.getTemplateID());
					props.put("item_" + numItems + "Name", item.getName());
					props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
					props.put("item_" + numItems + "Id", item.getOid());
					props.put("item_" + numItems + "Count", auction.GetItemCount());
					props.put("item_" + numItems + "Bound", false);

					if (item.getProperty("durability") != null) {
						props.put("item_" + numItems + "Durability", item.getProperty("durability"));
						props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
					} else {
						props.put("item_" + numItems + "MaxDurability", 0);
					}
					if (item.getProperty("resistanceStats") != null) {
						int numResist = 0;
						HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
						for (String resistance : resistances.keySet()) {
							props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
							props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
							numResist++;
						}
						props.put("item_" + numItems + "NumResistances", numResist);
					} else {
						props.put("item_" + numItems + "NumResistances", 0);
					}
					if (item.getProperty("bonusStats") != null) {
						int numStats = 0;
						HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
						for (String statName : stats.keySet()) {
							props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
							props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
							numStats++;
						}
						props.put("item_" + numItems + "NumStats", numStats);
					} else {
						props.put("item_" + numItems + "NumStats", 0);
					}
					// If it is a weapon, add damage/speed stats
					if (item.getItemType().equals("Weapon")) {
						props.put("item_" + numItems + "Delay", item.getProperty("delay"));
						props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
						props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
						props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
					}
					props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
					int enchantLevel = (int) item.getProperty("enchantLevel");
					props.put("item_" + numItems + "ELevel", enchantLevel);

					if (item.getProperty("enchantStats") != null) {
						int numStats = 0;
						HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
						HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
						for (String statName : stats.keySet()) {
							if (bstats.containsKey(statName)) {
								log.debug(item.getName() + " " + statName + " " + stats.get(statName) + " " + bstats.get(statName) + " "
										+ (stats.get(statName) - bstats.get(statName)) + " ?");
								if (stats.get(statName) - bstats.get(statName) != 0) {
									props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
									props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
									log.debug(item.getName() + " " + statName + " " + (stats.get(statName) - bstats.get(statName)));
									numStats++;
								}
							} else {
								props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
								props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
								log.debug(item.getName() + " " + statName + " " + (stats.get(statName)) + " |");
								numStats++;

							}
						}
						props.put("item_" + numItems + "NumEStats", numStats);
					} else {
						props.put("item_" + numItems + "NumEStats", 0);
					}
					if (item.getProperty("sockets") != null) {
						int numSocket = 0;
						HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
						for (Integer socket : sockets.keySet()) {
							if (sockets.get(socket).GetItemOid() != null) {
								AgisItem itemSoc = AgisInventoryClient.getItem(sockets.get(socket).GetItemOid());
								if (itemSoc != null) {
									props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
									props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
									} else {
									props.put("item_" + numItems + "socket_" + socket + "Item", -1);
									props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
									}
							} else {
								props.put("item_" + numItems + "socket_" + socket + "Item", -1);
							props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
							}
							props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
							props.put("item_" + numItems + "socket_" + socket + "Id", socket);
							numSocket++;
						}
						props.put("item_" + numItems + "NumSocket", numSocket);
					} else {
						props.put("item_" + numItems + "NumSocket", 0);
					}
					int setid = (int) item.getIntProperty("item_set");
					props.put("item_" + numItems + "NumOfSet", 0);

					numItems++;
					if (itemCounts.containsKey(item.getTemplateID())) {
						itemCounts.put(item.getTemplateID(), itemCounts.get(item.getTemplateID()) + item.getStackSize());
					} else {
						itemCounts.put(item.getTemplateID(), item.getStackSize());
					}
		
			}
			/*if (numItems >= profile.display_limit) {
				log.debug("sendTransactionsUpdate: Limiting auction list to "+profile.display_limit+" for player "+playerOid);
				//ExtendedCombatMessages.sendErrorMessage(playerOid, "The auction limit has been reached you can no longer list items");
				break;
			}*/
				
		}
		
	
		props.put("numItems", numItems);
		float vipModp = 0;
		long vipMod = 0;
		if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
			vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
  			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
  		}
		if(globalEventBonusesArray.containsKey("AuctionCost")) {
			vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
  			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
  		}
		log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
		long startPriceValue = profile.start_price_value + vipMod;
		if(startPriceValue < 0)
			startPriceValue = 0l;
		long costPriceValue = profile.cost_price_value + vipMod;
		if(costPriceValue < 0)
			costPriceValue = 0l;
		float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
		if (startPriceValueP < 0)
			startPriceValueP = 0;
		float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
		if (costPriceValueP < 0)
			costPriceValueP = 0;
	
		props.put("currency",profile.currency);
		props.put("sPriceVal",startPriceValue);
		props.put("SPricePerc",startPriceValueP); 
		props.put("cPriceVal",costPriceValue);
		props.put("cPricePerc",costPriceValueP);
if (Log.loggingDebug)
			log.debug("sendTransactionsUpdate Auctions send:" + props);
		TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(tmsg);

	}
	
	
	
	
	class CancelAuction implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			int auctionId = (Integer) message.getProperty("auctionId");
			boolean buying = (boolean) message.getProperty("buying");
			boolean selling = (boolean) message.getProperty("selling");
			
			ArrayList<Auction> _auctions = new ArrayList<Auction>(auctions);
			log.debug("CancelAuction Start");
			
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
			
			for (Auction auc : _auctions) {
				if(auc.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				if (auc.GetOwnerOid().equals(playerOid)&& auc.GetId()==auctionId) {
					auc.SetStatus(5);
					aDB.insertAuctionEnded(auc);
					aDB.deleteAuction(auc);	
					auctions.remove(auc);
					
					ArrayList<Auction> _aucs = new ArrayList<Auction>();
					if(selling)
						_aucs = aDB.getOwnSellAuctions(playerOid);
					else if (buying)
						_aucs = aDB.getOwnOrderAuctions(playerOid);
					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("ext_msg_subtype", "AuctionOwnerListUpdate");
					HashMap<Integer, Integer> itemCounts = new HashMap<Integer, Integer>();
					int numItems = 0;
					for (Auction auction : _aucs) {
						AgisItem item = AgisInventoryClient.getItem(auction.GetItemOid());
						if (item == null) {
							log.debug("SendAuctionOwnerList: item is null:" + item + ", oid=" + auction.GetItemOid());
							if (auction.GetItemOid() != null) {
								if (ObjectManagerClient.loadObject(auction.GetItemOid()) == null) {
									log.debug("Auction can't load item " + auction.GetItemOid() + " for auction " + auction.GetId());
								} else {
									log.debug("Auction loaded item " + auction.GetItemOid() + " for auction " + auction.GetId());
								}
							} else {
								log.debug("Auction item is null for auction " + auction.GetId());
							}

						}
						 log.debug("SendAuctionOwnerList: auction:" + auction+" numItems:"+numItems);
								boolean itemExists = true;
							 item = AgisInventoryClient.getItem(auction.GetItemOid());
							if (item == null) {
								 log.debug("SendAuctionOwnerList: item is null:"+item+", oid=" + auction.GetItemOid());
								itemExists = false;
							}
							if (itemExists) {
								props.put("auction_" + numItems + "Id", auction.GetId());
								props.put("auction_" + numItems + "ExpirateDate", auction.GetExpirateDate().toString());
								props.put("auction_" + numItems + "Buyout", auction.GetBuyout());
								props.put("auction_" + numItems + "Currency", auction.GetCurrency());
								props.put("auction_" + numItems + "Mode", auction.GetMode());
								props.put("item_" + numItems + "TemplateID", item.getTemplateID());
								props.put("item_" + numItems + "Name", item.getName());
								props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
								props.put("item_" + numItems + "Id", item.getOid());
								props.put("item_" + numItems + "Count", auction.GetItemCount());
								props.put("item_" + numItems + "Bound", false);

								if (item.getProperty("durability") != null) {
									props.put("item_" + numItems + "Durability", item.getProperty("durability"));
									props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
								} else {
									props.put("item_" + numItems + "MaxDurability", 0);
								}
								if (item.getProperty("resistanceStats") != null) {
									int numResist = 0;
									HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
									for (String resistance : resistances.keySet()) {
										props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
										props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
										numResist++;
									}
									props.put("item_" + numItems + "NumResistances", numResist);
								} else {
									props.put("item_" + numItems + "NumResistances", 0);
								}
								if (item.getProperty("bonusStats") != null) {
									int numStats = 0;
									HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
									for (String statName : stats.keySet()) {
										props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
										props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
										numStats++;
									}
									props.put("item_" + numItems + "NumStats", numStats);
								} else {
									props.put("item_" + numItems + "NumStats", 0);
								}
								// If it is a weapon, add damage/speed stats
								if (item.getItemType().equals("Weapon")) {
									props.put("item_" + numItems + "Delay", item.getProperty("delay"));
									props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
									props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
									props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
								}
								props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
								int enchantLevel = (int) item.getProperty("enchantLevel");
								props.put("item_" + numItems + "ELevel", enchantLevel);

								if (item.getProperty("enchantStats") != null) {
									int numStats = 0;
									HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
									HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
									for (String statName : stats.keySet()) {
										if (bstats.containsKey(statName)) {
											log.debug(item.getName() + " " + statName + " " + stats.get(statName) + " " + bstats.get(statName) + " "
													+ (stats.get(statName) - bstats.get(statName)) + " ?");
											if (stats.get(statName) - bstats.get(statName) != 0) {
												props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
												props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
												log.debug(item.getName() + " " + statName + " " + (stats.get(statName) - bstats.get(statName)));
												numStats++;
											}
										} else {
											props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
											props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
											log.debug(item.getName() + " " + statName + " " + (stats.get(statName)) + " |");
											numStats++;

										}
									}
									props.put("item_" + numItems + "NumEStats", numStats);
								} else {
									props.put("item_" + numItems + "NumEStats", 0);
								}
								if (item.getProperty("sockets") != null) {
									int numSocket = 0;
									HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
									for (Integer socket : sockets.keySet()) {
										if (sockets.get(socket).GetItemOid() != null) {
											AgisItem itemSoc = AgisInventoryClient.getItem(sockets.get(socket).GetItemOid());
											if (itemSoc != null) {
												props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
												props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
												} else {
												props.put("item_" + numItems + "socket_" + socket + "Item", -1);
												props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
												}
										} else {
											props.put("item_" + numItems + "socket_" + socket + "Item", -1);
										props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
										}
										props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
										props.put("item_" + numItems + "socket_" + socket + "Id", socket);
										numSocket++;
									}
									props.put("item_" + numItems + "NumSocket", numSocket);
								} else {
									props.put("item_" + numItems + "NumSocket", 0);
								}
								int setid = (int) item.getIntProperty("item_set");
								props.put("item_" + numItems + "NumOfSet", 0);

								numItems++;
								if (itemCounts.containsKey(item.getTemplateID())) {
									itemCounts.put(item.getTemplateID(), itemCounts.get(item.getTemplateID()) + item.getStackSize());
								} else {
									itemCounts.put(item.getTemplateID(), item.getStackSize());
								}
					
						}
						if (numItems >= profile.display_limit) {
							log.debug("Limiting auction list to "+profile.display_limit+" for player "+playerOid);
						//	ExtendedCombatMessages.sendErrorMessage(playerOid, "The auction limit has been reached you can no longer list items");
							break;
						}
							
					}
				
					props.put("numItems", numItems);
					float vipModp = 0;
					long vipMod = 0;
					if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
						vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
		      			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
		      		}
					if(globalEventBonusesArray.containsKey("AuctionCost")) {
						vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
		      			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
		      		}
					log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
					long startPriceValue = profile.start_price_value + vipMod;
					if(startPriceValue < 0)
						startPriceValue = 0l;
					long costPriceValue = profile.cost_price_value + vipMod;
					if(costPriceValue < 0)
						costPriceValue = 0l;
					float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
					if (startPriceValueP < 0)
						startPriceValueP = 0;
					float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
					if (costPriceValueP < 0)
						costPriceValueP = 0;

				
					props.put("currency",profile.currency);
					props.put("sPriceVal",startPriceValue);
					props.put("SPricePerc",startPriceValueP); 
					props.put("cPriceVal",costPriceValue);
					props.put("cPricePerc",costPriceValueP);
					
					
					
			if (Log.loggingDebug)
						log.debug("Auction send:" + props);
					TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
					Engine.getAgent().sendBroadcast(tmsg);

				}
			}
			
				
			AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
			Engine.getAgent().sendBroadcast(invUpdateMsg);
			AuctionGroup();
			return true;
		}
	}
	
	
	
	class CreateAuction implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();

			log.debug("CreateAuction: auction create started for: " + playerOid);
			long startbid = (Integer) message.getProperty("startbid");
			long auctioneer = playerAuctioneer.get(playerOid);
			
			
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
				
			int bonusLimit = 0;
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCount")) {
				bonusLimit = bonusesArray.get(playerOid).get("AuctionCount").GetValue();
      		}
			if(globalEventBonusesArray.containsKey("AuctionCount")) {
				bonusLimit += globalEventBonusesArray.get("AuctionCount").GetValue();
      		}
			float vipModp = 0;
			long vipMod = 0;
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
				vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
      			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
      		}
			if(globalEventBonusesArray.containsKey("AuctionCost")) {
				vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
      			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
      		}
			log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
			long startPriceValue = profile.start_price_value + vipMod;
			if(startPriceValue < 0)
				startPriceValue = 0l;
			long costPriceValue = profile.cost_price_value + vipMod;
			if(costPriceValue < 0)
				costPriceValue = 0l;
			float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
			if (startPriceValueP < 0)
				startPriceValueP = 0;
			float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
			if (costPriceValueP < 0)
				costPriceValueP = 0;

			synchronized (playerOid.toString().intern()) {

				ArrayList<Auction> _aucs = aDB.getOwnSellAuctions(playerOid);
				log.debug("CreateAuction: playerOid:" + playerOid + " AUCTION_OWN_LIMIT:" + (profile.own_limit + bonusLimit) + " Player auctions:" + _aucs.size());
				if ((profile.own_limit + bonusLimit )<= _aucs.size()) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.AUCTION_OWN_LIMIT, 0, "");
					log.debug("CreateAuction: playerOid:" + playerOid + " AUCTION_OWN_LIMIT:" + (profile.own_limit + bonusLimit) + " Number of Player auctions:" + _aucs.size() + " Limit");

					return true;
				}
				int itemCount = (Integer) message.getProperty("item_count");
				
				HashMap<String, Long> currencyOffer = (HashMap<String, Long>) message.getProperty("buyout");
				log.debug("CreateAuction: playerOid:" + playerOid + " startbid:" + startbid + " auctioneer:" + auctioneer + " itemCount="+itemCount+" currencyOffer:" + currencyOffer);
				long buyout = 0;
				for (String currency : currencyOffer.keySet()) {
					int cType = Integer.parseInt(currency);
					long amount = currencyOffer.get(currency);
					Currency c = Agis.CurrencyManager.get(cType);
					log.debug("CreateAuction: convert Currency cType:" + cType + " amount:" + amount + " currency:" + currency + " c:" + c.getCurrencyID() + " convet to: " + c.getCurrencyThatConvertsToThis() + " getConversionAmountReq:"
							+ c.getConversionAmountReq());
					if (c != null)
						while (c.getCurrencyThatConvertsToThis() != null) {
							log.debug("CreateAuction: convert Currency cType:" + cType + " c:" + c.getCurrencyID() + " convet to: " + c.getCurrencyThatConvertsToThis().getCurrencyID() + " getConversionAmountReq:" + c.getConversionAmountReq());
							c = c.getCurrencyThatConvertsToThis();
							amount *= c.getConversionAmountReq();
						}
					buyout += amount;
				}
				if (buyout <= 0) {
					ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction for 0");
					log.debug("CreateAuction: playerOid:" + playerOid + " You can not put this item on the auction for 0");
					return true;
				}
				
				
				if (!AgisInventoryClient.checkCurrency(playerOid, profile.currency, startPriceValue + Math.round(Math.ceil(buyout *itemCount* startPriceValueP / 100)))) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
					log.debug("CreateAuction: playerOid:" + playerOid + " NOT_ENOUGH_CURRENCY");
					return true;
				}

				log.debug("CreateAuction: buyout after convertion " + buyout);
				String groupId = "";
				OID itemOid = (OID) message.getProperty("item_oid");
				if (itemCount < 1) {
					ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction with count 0");
					log.debug("CreateAuction: playerOid:" + playerOid + " You can not put this item on the auction with count under 1");
					return true;
				}
				String itemgroup = (String) message.getProperty("itemgroup");
				log.debug("CreateAuction: itemgroup " + itemgroup + " itemOid:" + itemOid + " itemCount:" + itemCount);

				if (itemgroup.length() == 0 && itemOid != null) {

					AgisItem ai = AgisInventoryClient.getItem(itemOid);
					String unicItem = ai.getTemplateID() + "";
					int enchantLevel = (int) ai.getProperty("enchantLevel");
					int templateId = (int) ai.getProperty("itemID");
					if (enchantLevel > 0)
						unicItem += "_E" + enchantLevel;
					HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) ai.getProperty("sockets");
					ArrayList<Long> socketItems = new ArrayList<Long>();
					for (Integer sId : itemSockets.keySet()) {
						if (itemSockets.get(sId).GetItemOid() != null) {
							socketItems.add(itemSockets.get(sId).GetItemOid().toLong());
						}
					}
					Collections.sort(socketItems);
					for (Long l : socketItems) {
						unicItem += "_S" + l;
					}
					itemgroup = unicItem;
				}
				groupId = itemgroup;
				// AgisItem item =null;
				ArrayList<OID> items = AgisInventoryClient.GetListItemsWithParam(playerOid, itemgroup);
				log.debug("CreateAuction: items[OID]:" + items);
				if (items == null || items.size() == 0) {
					ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction");
					log.debug("CreateAuction: playerOid:" + playerOid + " You can not put this item on the auction");
					return true;
				}

				int count = 0;
				ArrayList<OID> _items = new ArrayList<OID>(items);
				int templateId = -1;
				int enchantLevel = 0;
				HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
				for (OID itOid : items) {
					AgisItem ai = AgisInventoryClient.getItem(itOid);
					Boolean isBound = true;

					try {
						isBound = (Boolean) AgisInventoryClient.getItemProperty(playerOid, itOid, "boundToPlayer");
					} catch (Exception e) {
						log.dumpStack(e.getMessage());
					}
					log.debug("CreateAuction2 isBound:" + isBound);

					if (isBound != null && isBound) {
						
						_items.remove(itOid);
					}
					Boolean canBeSold = true;

					try {
						canBeSold = (Boolean) AgisInventoryClient.getItemProperty(playerOid, itOid, "auctionHouse");
					} catch (Exception e) {
						log.debug("CreateAuction Exception cant get param auctionHouse from item Oid:" + itOid + " canBeSold:" + canBeSold);
						log.dumpStack(e.getMessage());
					}
					log.debug("CreateAuction2 canBeSold:" + canBeSold);

					if (canBeSold != null && !canBeSold) {
						ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction");
						log.debug("CreateAuction: canBeSold:" + canBeSold + " You can not put this item on the auction");
						_items.remove(itOid);
						return true;
					}

					if (ai != null) {

						try {
							if (ai != null) {
								enchantLevel = (int) ai.getProperty("enchantLevel");
								templateId = (int) ai.getProperty("itemID");
								itemSockets = (HashMap<Integer, SocketInfo>) ai.getProperty("sockets");
							} else {
								enchantLevel = (int) AgisInventoryClient.getItemProperty(playerOid, itOid, "enchantLevel");
								templateId = (int) AgisInventoryClient.getItemProperty(playerOid, itOid, "itemID");
								itemSockets = (HashMap<Integer, SocketInfo>) AgisInventoryClient.getItemProperty(playerOid, itOid, "sockets");
							}
						} catch (Exception e) {
							log.dumpStack(e.getMessage());
						}
						count += ai.getStackSize();
						log.debug("CreateAuction Agis Item  " + itOid + " count:" + ai.getStackSize());
					} else {
						log.debug("CreateAuction Agis Item is null " + itOid);
					}

				}

					log.debug("CreateAuction: itemOid:" + itemOid + " itemCount:" + itemCount + " itemOid.long:" + itemOid.toLong());
				
				if (count < itemCount) {
						EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.CANNOT_SELL_ITEM, 0, "");
					log.debug("CreateAuction: playerOid:" + playerOid + " Dont have items");
					return true;
				}

				int selledNumber = 0;
				if (groupedAuctions.containsKey(groupId)) {
				
					ArrayList<Auction> _auctions = groupedAuctions.get(groupId);
					Collections.sort(_auctions, new Comparator<Auction>() {
						public int compare(Auction o1, Auction o2) {
							if (o1.GetBuyout() == o2.GetBuyout())
								return 0;
							return o1.GetBuyout() < o2.GetBuyout() ? -1 : 1;
						}
					});
					for (Auction auction : _auctions) {
						if (auction.GetMode() == 1) {
							// Order mode
							if (auction.GetBuyout() == buyout && auction.GetStatus() == 1) {
								log.debug("CreateAuction plyid:" + playerOid + " auction:" + auction + " auction.GetBuyout()==buyout && auction.GetStatus()==1");
								if (auction.GetItemCount() <= (itemCount - selledNumber)) {
									log.debug("CreateAuction plyid:" + playerOid + " auction:" + auction + " (auction.GetItemCount()<=(itemCount-selledNumber))");

									auction.SetBidderOid(playerOid);
									auction.SetStatus(2);
									Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
									String itemName = itemTemplate.getName();
									if (auction.GetItemEnchanteLevel() > 0)
										itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
									String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
									AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction", "You have bougth " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
									aDB.insertAuctionEnded(auction);
									// aDB.saveAuction(auction);
									aDB.deleteAuction(auction);
									auctions.remove(auction);

									selledNumber += auction.GetItemCount();
								} else if ((itemCount - selledNumber) > 0 && auction.GetStatus() == 1) {
									log.debug("CreateAuction plyid:" + playerOid + " auction:" + auction + " ((itemCount-selledNumber)>0 && auction.GetStatus()==1)");
									int itemsnum = auction.GetItemCount();
									auction.SetBidderOid(playerOid);
									auction.SetStatus(2);
									auction.SetItemCount(itemCount - selledNumber);
									aDB.insertAuctionEnded(auction);
									auction.SetBidderOid(null);
									auction.SetStatus(1);
									auction.SetItemCount(itemsnum - (itemCount - selledNumber));
									aDB.saveAuction(auction);

									Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
									String itemName = itemTemplate.getName();
									if (auction.GetItemEnchanteLevel() > 0)
										itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
									String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
									AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction",
											"You have partially bought " + (itemCount - selledNumber) + " of " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
									selledNumber += (itemCount - selledNumber);
								}
							}
						}
					}
				}

				log.debug("CreateAuction plyid:" + playerOid + " bid:" + startbid + " auctioneer:" + auctioneer + " item:" + itemOid + " count:" + itemCount + " boughtNumber:" + selledNumber);
				TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
				Calendar cal = Calendar.getInstance(tz);
				log.debug("CreateAuction CreateTime:" + cal.getTime());
				cal.add(Calendar.DAY_OF_MONTH, profile.duration);
				log.debug("CreateAuction 2 CreateTime:" + cal.getTime());
				if (selledNumber > 0) {
					Template itemTemplate = ObjectManagerClient.getTemplate(templateId, ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemName = itemTemplate.getName();
					if (enchantLevel > 0)
						itemName = "+" + enchantLevel + " " + itemName;
					String playerName = Engine.getDatabase().getObjectName(playerOid, WorldManagerClient.NAMESPACE);
					AgisInventoryClient.sendMail(playerOid, playerName, "Auction House transaction", "You have sold " + selledNumber + " of " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);

				}
				Auction auction = null;
				if (itemCount - selledNumber > 0) {
					auction = new Auction();
					auction.SetExpirateDate(cal.getTime());
					auction.SetStartBid(1);
					auction.SetOwnerOid(playerOid);
					if (startbid > 0)
						auction.SetStartBid(startbid);
					auction.SetAuctioneer(auctioneer);
					auction.SetCurrency(profile.currency);
					log.debug("CreateAuction2 enchantLevel:" + enchantLevel + " templateId:" + templateId + " itemSockets:" + itemSockets);
					auction.SetStatus(1);

					// auction.SetItemOid(itemOid);
					auction.SetItemCount(itemCount - selledNumber);
					auction.SetBuyout(buyout);
					auction.SetItemSockets(itemSockets);
					auction.SetItemEnchanteLevel(enchantLevel);
					auction.SetItemTemplateId(templateId);
					int inserted = aDB.InsertAuction(auction);
					auction.SetId(inserted);
				}
				OID itemOid2 = null;
				AgisInventoryClient.alterCurrency(playerOid, profile.currency, -(startPriceValue + Math.round(Math.ceil(buyout *itemCount* startPriceValueP / 100))));
				int itemCountTaken = 0;
			for (OID itOid : items) {
					AgisItem item = AgisInventoryClient.getItem(itOid);
					if (item != null) {
						log.debug("CreateAuction item prop keys:" + item.getPropertyMapRef().keySet());

						int stackSize = item.getStackSize();
						log.debug("CreateAuction: itemOid:" + itemOid + " itemCount:" + itemCount + " itemOid.long:" + itemOid.toLong() + " item.stack:" + item.getStackSize() + " stackSize:" + stackSize);

						if (stackSize <= itemCount - itemCountTaken) {
							if (auction != null) {
								auction.SetItemOid(itOid);
								aDB.saveAuction(auction);
							}
							InventoryClient.removeItem(playerOid, itOid);
							ObjectManagerClient.saveObject(itOid);
							itemCountTaken += stackSize;
						} else if (stackSize > itemCount - itemCountTaken && (itemCount - itemCountTaken) > 0) {
							AgisInventoryClient.AlterItemCount(playerOid, itOid, -(itemCount - itemCountTaken));
							Log.debug("CreateAuction:  itemOid:" + itOid + " stack:" + item.getStackSize());
								if (auction != null)
								if (auction.GetItemOid() == null) {
									itemOid2 = AgisInventoryClient.generateItemAsItem(playerOid, itOid, (itemCount - selledNumber - itemCountTaken), false);
									Log.debug("CreateAuction: new item generated itemOid:" + itemOid2);
									auction.SetItemOid(itemOid2);
									aDB.saveAuction(auction);
									InventoryClient.removeItem(playerOid, itemOid2);
									ObjectManagerClient.saveObject(itemOid2);
								}
							itemCountTaken += itemCount - itemCountTaken;
						}
					}
					Log.debug("CreateAuction:  itemCountTaken:" + itemCountTaken);
				}
				if (auction != null)
					auctions.add(auction);
			}
			AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
			Engine.getAgent().sendBroadcast(invUpdateMsg);
			log.debug("CreateAuction: auction create finished for: " + playerOid);
			AuctionGroup();
			return true;

		}

	}

	class BidAuction implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("BidAuction: started for: " + playerOid);
			int count = (Integer) message.getProperty("item_count");
			if(count < 1)
			 {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not bid this item on the auction with count 0");
	    	 	log.debug("CreateAuction: playerOid:" + playerOid + " You can not bid this item on the auction with count under 1" );
				return true;
			}
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
			
			String groupId = (String) message.getProperty("groupId");
			synchronized(playerOid.toString().intern()) {
				
			long buyout = 0;
			HashMap<String, Long> currencyOffer = (HashMap<String, Long>) message.getProperty("buyout");
			for (String currency : currencyOffer.keySet()) {
				int cType = Integer.parseInt(currency);
				long amount = currencyOffer.get(currency);
				
				Currency c = Agis.CurrencyManager.get(cType);
				if (c != null)
					while (c.getCurrencyThatConvertsToThis() != null) {
						c = c.getCurrencyThatConvertsToThis();
						amount *= c.getConversionAmountReq();
					}
				buyout += amount;
			}
			if(buyout<=0) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not bid this item on the auction for 0");
	    	 	log.debug("BidAuction: playerOid:" + playerOid + " You can not bid this item on the auction for 0" );
				return true;
			}
			
			if (!AgisInventoryClient.checkCurrency(playerOid,profile.currency,buyout*count)) {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
				log.debug("BidAuction: playerOid:" + playerOid + " NOT_ENOUGH_CURRENCY" );
				return true;
			}
			log.debug("BidAuction: playerOid:" + playerOid + " groupId:" + groupId + " count:" + count + " buyout:" + buyout);

			if (groupedAuctions.containsKey(groupId)) {
				int boughtNumber = 0;

				ArrayList<Auction> _auctions = groupedAuctions.get(groupId);
				log.debug("BidAuction: playerOid:" + playerOid + " _auctions:" + _auctions.size() );

				Collections.sort(_auctions, new Comparator<Auction>() {
					public int compare(Auction o1, Auction o2) {
						if (o1.GetBuyout() == o2.GetBuyout())
							return 0;
						return o1.GetBuyout() < o2.GetBuyout() ? -1 : 1;
					}
				});
				for (Auction auction : _auctions) {
					if(auction.GetAuctioneer() != playerAuctioneer.get(playerOid))
						continue;
					log.debug("BidAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" mode:"+auction.GetMode());

					if (auction.GetMode() == 0) {
						// Buy
						log.debug("BidAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" GetBuyout:"+auction.GetBuyout()+" "+buyout+" status:"+auction.GetStatus());
						log.debug("BidAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" GetItemCount:"+auction.GetItemCount()+" count:"+count+" boughtNumber:"+boughtNumber);

						if (auction.GetBuyout() <= buyout && auction.GetStatus() == 1) {
							if (auction.GetItemCount() <= (count - boughtNumber)) {
								auction.SetBidderOid(playerOid);
								auction.SetStatus(2);
								Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
								String itemName = itemTemplate.getName();
								if (auction.GetItemEnchanteLevel() > 0)
									itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
								String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
								AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction",
										"You have sold " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
								aDB.deleteAuction(auction);
								aDB.insertAuctionEnded(auction);
								auctions.remove(auction);
								AgisInventoryClient.alterCurrency(playerOid, profile.currency, -auction.GetBuyout()*auction.GetItemCount());
								boughtNumber += auction.GetItemCount();
							} else if ((count - boughtNumber) > 0 && auction.GetStatus() == 1) {
								int itemsnum = auction.GetItemCount();
								OID itemOIDorg = auction.GetItemOid();
								auction.SetBidderOid(playerOid);
								auction.SetStatus(2);
								auction.SetItemCount(count - boughtNumber);
								
								Template itemTemplate = ObjectManagerClient.getTemplate( auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
								OID	itemOid = AgisInventoryClient.generateItem(auction.GetOwnerOid(), auction.GetItemTemplateId(), itemTemplate.getName(), count - boughtNumber);
								auction.SetItemOid(itemOid);	
								AgisItem item = AgisInventoryClient.getItem(itemOid);
								item.setProperty("enchantLevel", auction.GetItemEnchanteLevel());
								item.setProperty("sockets", auction.GetItemSockets());
								InventoryClient.removeItem(auction.GetOwnerOid(), itemOid);
								ObjectManagerClient.saveObject(itemOid);
								aDB.insertAuctionEnded(auction);
								auction.SetItemOid(itemOIDorg);	
								auction.SetBidderOid(null);
								auction.SetStatus(1);
								auction.SetItemCount(itemsnum - (count - boughtNumber));
								aDB.saveAuction(auction);
								AgisInventoryClient.alterCurrency(playerOid, profile.currency, -auction.GetBuyout()*(count - boughtNumber));
								
								String itemName = itemTemplate.getName();
								if (auction.GetItemEnchanteLevel() > 0)
									itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
								String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
								AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction",
										"You have partial sold " + (count - boughtNumber) + " of " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
								boughtNumber += (count - boughtNumber);
								}
						}
					} else if (auction.GetMode() == 1) {
						// Oreder
					}
					
					
				}
				log.debug("BidAuction: _auctions:" + playerOid + " boughtNumber:" + boughtNumber );

				if (boughtNumber > 0 && count > boughtNumber) {
					Template itemTemplate = ObjectManagerClient.getTemplate(groupedAuctions.get(groupId).get(0).GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemName = itemTemplate.getName();

					String buyerName = Engine.getDatabase().getObjectName(playerOid, WorldManagerClient.NAMESPACE);
					AgisInventoryClient.sendMail(playerOid, buyerName, "Auction House transaction",
							"You bought " + boughtNumber + " of " + itemName + ", You can now go to the auction house to pick up the item you purchased", 0, 0, false);
			
				} else if (count == boughtNumber) {
					Template itemTemplate = ObjectManagerClient.getTemplate(groupedAuctions.get(groupId).get(0).GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemName = itemTemplate.getName();

					String buyerName = Engine.getDatabase().getObjectName(playerOid, WorldManagerClient.NAMESPACE);
					AgisInventoryClient.sendMail(playerOid, buyerName, "Auction House transaction",
							"The auction has ended successfully and You bought " + itemName + ", You can now go to the auction house to pick up the item you purchased", 0, 0, false);

				} 

			} else {
				// Auction not Found

			}
			}
			AuctionGroup();
			AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
			Engine.getAgent().sendBroadcast(invUpdateMsg);
			
			Search searchProps = new Search();;
			if (usersSearch.containsKey(playerOid))
				searchProps= usersSearch.get(playerOid);
			sortAuctionGroups( playerOid, searchProps);
			return true;
			
		}
	}
	
	class OrderAuction implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("OrderAuction: started for: " + playerOid);
			int count = (Integer) message.getProperty("item_count");
			if(count < 1)
			 {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction with count 0");
	    	 	log.debug("CreateAuction: playerOid:" + playerOid + " You can not put this item on the auction with count under 1" );
				return true;
			}
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
			synchronized(playerOid.toString().intern()) {
				String groupId = (String) message.getProperty("groupId");
			long buyout = 0;
			HashMap<String, Long> currencyOffer = (HashMap<String, Long>) message.getProperty("buyout");
			for (String currency : currencyOffer.keySet()) {
				int cType = Integer.parseInt(currency);
				Long amount = currencyOffer.get(currency);
				
				Currency c = Agis.CurrencyManager.get(cType);
				if (c != null)
					while (c.getCurrencyThatConvertsToThis() != null) {
						c = c.getCurrencyThatConvertsToThis();
						amount *= c.getConversionAmountReq();
					}
				buyout += amount;
			}
			if(buyout<=0) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You can not put this item on the auction for 0");
	    	 	log.debug("CreateAuction: playerOid:" + playerOid + " You can not put this item on the auction for 0" );
				return true;
			}
			
			ArrayList<Auction> _aucs = aDB.getOwnSellAuctions(playerOid);
			
			
			
			
			
		
			int vipLimit = 0;
		
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCount")) {
					vipLimit = bonusesArray.get(playerOid).get("AuctionCount").GetValue();
	  		}
			if(globalEventBonusesArray.containsKey("AuctionCount")) {
				vipLimit += globalEventBonusesArray.get("AuctionCount").GetValue();
      		}
		
			log.debug("CreateAuction: playerOid:" + playerOid + " AUCTION_OWN_LIMIT:"+(profile.own_limit + vipLimit)+" Player acucrions:"+_aucs.size() );
		    	if((profile.own_limit + vipLimit)<=_aucs.size()) {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.AUCTION_OWN_LIMIT, 0, "");
				log.debug("CreateAuction: playerOid:" + playerOid + " AUCTION_OWN_LIMIT:"+(profile.own_limit + vipLimit)+" Number of Player auctions:"+_aucs.size() +" Limit");
		    	
				return true;
			}
			
			
			
			if (!AgisInventoryClient.checkCurrency(playerOid,profile.currency,buyout*count)) {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
				log.debug("OrderAuction: playerOid:" + playerOid + " NOT_ENOUGH_CURRENCY" );
				return true;
			}
			log.debug("OrderAuction: playerOid:" + playerOid + " groupId:" + groupId + " count:" + count + " buyout:" + buyout);

			if (groupedAuctions.containsKey(groupId)) {
				int boughtNumber = 0;

				ArrayList<Auction> _auctions = groupedAuctions.get(groupId);
				log.debug("OrderAuction: playerOid:" + playerOid + " _auctions:" + _auctions.size() );

				Collections.sort(_auctions, new Comparator<Auction>() {
					public int compare(Auction o1, Auction o2) {
						if (o1.GetBuyout() == o2.GetBuyout())
							return 0;
						return o1.GetBuyout() < o2.GetBuyout() ? -1 : 1;
					}
				});
				
				for (Auction auction : _auctions) {
					if(auction.GetAuctioneer() != playerAuctioneer.get(playerOid))
						continue;
					log.debug("OrderAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" mode:"+auction.GetMode());

					if (auction.GetMode() == 0) {
						// Buy
						log.debug("OrderAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" GetBuyout:"+auction.GetBuyout()+" "+buyout+" status:"+auction.GetStatus());
						log.debug("OrderAuction: playerOid:" + playerOid + " auction:" + auction.GetId() +" GetItemCount:"+auction.GetItemCount()+" count:"+count+" boughtNumber:"+boughtNumber);

						if (auction.GetBuyout() <= buyout && auction.GetStatus() == 1) {
							if (auction.GetItemCount() <= (count - boughtNumber)) {
								auction.SetBidderOid(playerOid);
								auction.SetStatus(2);
								Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
								String itemName = itemTemplate.getName();
								if (auction.GetItemEnchanteLevel() > 0)
									itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
								String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
								AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction",
										"You have sold " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
								aDB.deleteAuction(auction);
								aDB.insertAuctionEnded(auction);
								auctions.remove(auction);

								boughtNumber += auction.GetItemCount();
							} else if ((count - boughtNumber) > 0 && auction.GetStatus() == 1) {
								int itemsnum = auction.GetItemCount();
								OID itemOIDorg = auction.GetItemOid();
								auction.SetBidderOid(playerOid);
								auction.SetStatus(2);
								auction.SetItemCount(count - boughtNumber);
								
								Template itemTemplate = ObjectManagerClient.getTemplate( auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
								OID	itemOid = AgisInventoryClient.generateItem(auction.GetOwnerOid(), auction.GetItemTemplateId(), itemTemplate.getName(), count - boughtNumber);
								auction.SetItemOid(itemOid);	
								AgisItem item = AgisInventoryClient.getItem(itemOid);
								item.setProperty("enchantLevel", auction.GetItemEnchanteLevel());
								item.setProperty("sockets", auction.GetItemSockets());
								InventoryClient.removeItem(auction.GetOwnerOid(), itemOid);
								ObjectManagerClient.saveObject(itemOid);
								aDB.insertAuctionEnded(auction);
								auction.SetItemOid(itemOIDorg);	
								auction.SetBidderOid(null);
								auction.SetStatus(1);
								auction.SetItemCount(itemsnum - (count - boughtNumber));
								aDB.saveAuction(auction);
								boughtNumber += (count - boughtNumber);

								String itemName = itemTemplate.getName();
								if (auction.GetItemEnchanteLevel() > 0)
									itemName = "+" + auction.GetItemEnchanteLevel() + " " + itemName;
								String ownerName = Engine.getDatabase().getObjectName(auction.GetOwnerOid(), WorldManagerClient.NAMESPACE);
								AgisInventoryClient.sendMail(auction.GetOwnerOid(), ownerName, "Auction House transaction",
										"You have partial sold " + (count - boughtNumber) + " of " + itemName + ", You can now go to the auction house to pick up reward", 0, 0, false);
							}
						}
					} else if (auction.GetMode() == 1) {
						// Oreder
					}
				}
				log.debug("OrderAuction: playerOid:" + playerOid + " boughtNumber:" + boughtNumber );

				AgisInventoryClient.alterCurrency(playerOid, profile.currency, -buyout*count);
				if (boughtNumber > 0 && count > boughtNumber) {
					Auction auc = groupedAuctions.get(groupId).get(0);
					Template itemTemplate = ObjectManagerClient.getTemplate(groupedAuctions.get(groupId).get(0).GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemName = itemTemplate.getName();

					String buyerName = Engine.getDatabase().getObjectName(playerOid, WorldManagerClient.NAMESPACE);
					AgisInventoryClient.sendMail(playerOid, buyerName, "Auction House transaction",
							"You bought " + boughtNumber + " of " + itemName + ", You can now go to the auction house to pick up the item you purchased", 0, 0, false);
					TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
					Calendar cal = Calendar.getInstance(tz);
					log.debug("OrderAuction: GetTime:" + cal.getTime());
					cal.add(Calendar.DAY_OF_MONTH, profile.duration);
					log.debug("OrderAuction: Time shift by :"+profile.duration + " to " + cal.getTime());
					OID itemOid = AgisInventoryClient.generateItemAsItem(playerOid, auc.GetItemOid(), (count - boughtNumber),false);
					
					Auction auction = new Auction();
					auction.SetExpirateDate(cal.getTime());
					auction.SetStartBid(1);
					auction.SetOwnerOid(playerOid);
					auction.SetCurrency(profile.currency);
					auction.SetMode(1);
					auction.SetAuctioneer(playerAuctioneer.get(playerOid));
					auction.SetStatus(1);
					auction.SetItemOid(itemOid);
					auction.SetItemCount(count - boughtNumber);
					auction.SetBuyout(buyout);
					auction.SetItemSockets(auc.GetItemSockets());
					auction.SetItemEnchanteLevel(auc.GetItemEnchanteLevel());
					auction.SetItemTemplateId(auc.GetItemTemplateId());
					log.debug("OrderAuction: playerOid:" + playerOid + " boughtNumber:" + boughtNumber +" boughtNumber > 0 && count > boughtNumber insert");
					int inserted = aDB.InsertAuction(auction);
					auction.SetId(inserted);
					auctions.add(auction);

				} else if (count == boughtNumber) {
					Template itemTemplate = ObjectManagerClient.getTemplate(groupedAuctions.get(groupId).get(0).GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
					String itemName = itemTemplate.getName();

					String buyerName = Engine.getDatabase().getObjectName(playerOid, WorldManagerClient.NAMESPACE);
					AgisInventoryClient.sendMail(playerOid, buyerName, "Auction House transaction",
							"The auction has ended successfully and You bought " + itemName + ", You can now go to the auction house to pick up the item you purchased", 0, 0, false);

				} else if (boughtNumber == 0) {
					Auction auc = groupedAuctions.get(groupId).get(0);
					TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
					Calendar cal = Calendar.getInstance(tz);
					log.debug("OrderAuction: GetTime:" + cal.getTime());
					cal.add(Calendar.DAY_OF_MONTH, profile.duration);
					log.debug("OrderAuction: Time shift by :" + profile.duration + " to " + cal.getTime());
					Auction auction = new Auction();
					auction.SetExpirateDate(cal.getTime());
					auction.SetStartBid(1);
					auction.SetOwnerOid(playerOid);
					auction.SetCurrency(profile.currency);
					auction.SetAuctioneer(playerAuctioneer.get(playerOid));
					auction.SetMode(1);
					auction.SetStatus(1);
					OID itemOid = AgisInventoryClient.generateItemAsItem(playerOid, auc.GetItemOid(), (count - boughtNumber),false);
					auction.SetItemOid(itemOid);	
					InventoryClient.removeItem(playerOid, itemOid);
					ObjectManagerClient.saveObject(itemOid);
					auction.SetItemCount(count - boughtNumber);
					auction.SetBuyout(buyout);
					auction.SetItemSockets(auc.GetItemSockets());
					auction.SetItemEnchanteLevel(auc.GetItemEnchanteLevel());
					auction.SetItemTemplateId(auc.GetItemTemplateId());
					log.debug("OrderAuction: playerOid:" + playerOid + " boughtNumber:" + boughtNumber +" boughtNumber == 0 insert");
					
					int inserted = aDB.InsertAuction(auction);
					auction.SetId(inserted);
					auctions.add(auction);

				} else {
					//
				}

			} else {
				// Auction not Found

			}
			}
			
			
			AuctionGroup();
			AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
			Engine.getAgent().sendBroadcast(invUpdateMsg);
    	 
			return true;
			
		}
	}
	void AuctionGroup() {
		ArrayList<Auction> _auctions = new ArrayList<Auction>(auctions);
		HashMap<String,ArrayList<Auction>> _groupedAuctions = new HashMap<String,ArrayList<Auction>>();
		log.debug("AuctionGroup: Start");
		for (Auction auc : _auctions) {
			String unicItem = auc.GetItemTemplateId()+"";
			if(auc.GetItemEnchanteLevel()>0)
				unicItem += "_E"+auc.GetItemEnchanteLevel();
			HashMap<Integer,SocketInfo> itemSockets = auc.GetItemSockets();
			ArrayList<Long> socketItems = new ArrayList<Long>();
			for (Integer sId : itemSockets.keySet()) {
				if (itemSockets.get(sId).GetItemOid()!=null) {
					socketItems.add(itemSockets.get(sId).GetItemOid().toLong());
				}
			}
			Collections.sort(socketItems);
			for(Long l : socketItems) {
				unicItem += "_S"+l;
			}
			if (_groupedAuctions.containsKey(unicItem)) {
				_groupedAuctions.get(unicItem).add(auc);
			}else {
				ArrayList<Auction> list = new ArrayList<Auction>();
				list.add(auc);
				_groupedAuctions.put(unicItem,list);
			}
		}
		groupedAuctions = _groupedAuctions;
		log.debug("AuctionGroup: End");
		
	}
	
	
	class SendAuctionListSearcheSort implements Hook {
		public boolean processMessage(Message msg, int flag) {
			log.debug("SendAuctionListSearcheSort Start ");
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			boolean sortCount = (boolean) message.getProperty("sortCount");
			boolean sortName = (boolean) message.getProperty("sortName");
			boolean sortPrice = (boolean) message.getProperty("sortPrice");
			boolean sortAsc = (boolean)message.getProperty("sortAsc");
			List<Integer> searchQuality = (List<Integer>) message.getProperty("searchQuality2");
			String searchClass = (String) message.getProperty("searchClass");
			String searchRace = (String) message.getProperty("searchRace");
			int searchLevelMin = (Integer) message.getProperty("searchLevelMin");
			int searchLevelMax = (Integer) message.getProperty("searchLevelMax");
			String searchCatType = (String) message.getProperty("searchCatType");
			String searchCat = (String) message.getProperty("searchCat");
			HashMap<String, String> searchCatDic = (HashMap<String, String>) message.getProperty("searchCatDic");
			List<String> searchText = (List<String>) message.getProperty("searchText");
			Search searchProps;
			if(!usersSearch.containsKey(playerOid)) {
				searchProps = new Search();
				searchProps.playerOid = playerOid;
				searchProps.sortCount = sortCount;
				searchProps.sortName = sortName;
				searchProps.sortPrice = sortPrice;
				searchProps.searchQuality = searchQuality;
				searchProps.searchClass = searchClass;
				searchProps.searchRace = searchRace;
				searchProps.searchLevelMin = searchLevelMin;
				searchProps.searchLevelMax = searchLevelMax;
				searchProps.searchCatType = searchCatType;
				searchProps.searchCat = searchCat;
				searchProps.searchText = searchText;
				searchProps.sortAsc = sortAsc;
				searchProps.searchCatDic = searchCatDic;
				usersSearch.put(playerOid, searchProps);
				}else {
					searchProps = usersSearch.get(playerOid);
					searchProps.playerOid = playerOid;
					searchProps.sortCount = sortCount;
					searchProps.sortName = sortName;
					searchProps.sortPrice = sortPrice;
					searchProps.searchQuality = searchQuality;
					searchProps.searchClass = searchClass;
					searchProps.searchRace = searchRace;
					searchProps.searchLevelMin = searchLevelMin;
					searchProps.searchLevelMax = searchLevelMax;
					searchProps.searchCatType = searchCatType;
					searchProps.searchCat = searchCat;
					searchProps.searchText = searchText;
					searchProps.sortAsc = sortAsc;
					searchProps.searchCatDic = searchCatDic;
					usersSearch.replace(playerOid, searchProps);
				}
			log.debug("SendAuctionListSearcheSort: sortCount:"+sortCount+" sortName:"+sortName+" sortPrice:"+sortPrice+" searchQuality:"+searchQuality+" searchClass:"+searchClass+
					" searchRace:"+searchRace+" searchLevelMin:"+searchLevelMin+" searchLevelMax:"+searchLevelMax+" searchCatType:"+searchCatType+" searchCat:"+searchCat+" searchText:"+searchText
					+" sortAsc:"+sortAsc);
			sortAuctionGroups( playerOid, searchProps);
		return true;	
		}
	}

	void sortAuctionGroups(OID playerOid,Search userSearch) {
	 log.debug("sortAuctionGroups");
		log.debug("SendAuctionListSearcheSort: sortCount:"+userSearch.sortCount+" sortName:"+userSearch.sortName+" sortPrice:"+userSearch.sortPrice
				+" searchQuality:"+userSearch.searchQuality+" searchClass:"+userSearch.searchClass+
				" searchRace:"+userSearch.searchRace+" searchLevelMin:"+userSearch.searchLevelMin+" searchLevelMax:"+userSearch.searchLevelMax+" searchCatType:"+userSearch.searchCatType+" searchCat:"+userSearch.searchCat+" searchText:"+userSearch.searchText+" sortAsc:"+userSearch.sortAsc);
		ArrayList<Integer> qualityList = new ArrayList<Integer>(userSearch.searchQuality);
		int searchRaceId = -1;
		int searchClassId = -1;
		if (!userSearch.searchRace.equals("Any")) {
			searchRaceId = RequirementChecker.getRaceID(userSearch.searchRace);
		}
		if (!userSearch.searchClass.equals("Any")) {
			searchClassId = RequirementChecker.getClassID(userSearch.searchClass);
		}
		log.debug("SendAuctionListSearcheSort: groupedAuctions:"+groupedAuctions.size());
		
		AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
		if(profile==null){
			log.error("AuctionPlugin.sortAuctionGroups: profile is null. Num profiles "+auctionProfiles.size()+" playerOid="+playerOid+" Num playerAuctioneer = "+playerAuctioneer.size()+" playerAuctioneer for player "+playerAuctioneer.get(playerOid));
			return ;
		}
		HashMap<String, ArrayList<Auction>> _groupedAuctions = new HashMap<String, ArrayList<Auction>>(groupedAuctions);
		log.debug("SendAuctionListSearcheSort: _groupedAuctions:"+_groupedAuctions.size()+" keys:"+_groupedAuctions.keySet());
		// for (Auction auc : _auctions) {
		ArrayList<String> todelete = new ArrayList<String>();
		Set<String> keys = _groupedAuctions.keySet();
		for (String key : keys) {
			Auction auc = _groupedAuctions.get(key).get(0);
			log.debug("SendAuctionListSearcheSort: key:"+key+" auc:"+auc);
			AgisItem item = null;
			if (auc.GetItemOid()!=null)
				item = AgisInventoryClient.getItem(auc.GetItemOid());
			log.debug("SendAuctionListSearcheSort: item:"+item+" ItemOid:"+auc.GetItemOid());
			
			
			if (item==null) {
				Template itemTemplate = ObjectManagerClient.getTemplate( auc.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
				
			OID	itemOid = AgisInventoryClient.generateItem(auc.GetOwnerOid(), auc.GetItemTemplateId(), itemTemplate.getName(), auc.GetItemCount());
			auc.SetItemOid(itemOid);	
			InventoryClient.removeItem(auc.GetOwnerOid(), itemOid);
			ObjectManagerClient.saveObject(itemOid);
			aDB.saveAuction(auc);
			item = AgisInventoryClient.getItem(itemOid);
			}
			
		
			HashMap<Integer, HashMap<String, Integer>> req = (HashMap<Integer, HashMap<String, Integer>>) item.getProperty("requirements");
			log.debug("SendAuctionListSearcheSort: req:" + req + " keySet:" + req.keySet());

			for (int requirementType : req.keySet()) {
				String requirementTypeText = RequirementChecker.getRequirementTypeText(requirementType);
				log.debug("SendAuctionListSearcheSort: requirementTypeText:" + requirementTypeText);

				if (requirementTypeText.equals("Race")) {
					log.debug("SendAuctionListSearcheSort: check key :" + key + " if Race match:");
					boolean raceMatched = false;
					if (searchRaceId == -1) {
						raceMatched = true;
					} else {
						for (String race : req.get(requirementType).keySet()) {
							int raceID = Integer.parseInt(race);
							log.debug("SendAuctionListSearcheSort: check key :" + key + " if Race match: searchRaceId: " + searchRaceId + " raceID:" + raceID);
							if (searchRaceId == raceID) {
								raceMatched = true;
								break;
							}
						}
					}
					if (!raceMatched) {
						log.debug("SendAuctionListSearcheSort: remove key :" + key + " no match race:" + _groupedAuctions.size());
						if (!todelete.contains(key))
							todelete.add(key);
						break;
					}
				} else if (requirementTypeText.equals("Class")) {
					log.debug("SendAuctionListSearcheSort: check key :" + key + " if Class match:");
					boolean classMatched = false;
					if (searchClassId == -1) {
						classMatched = true;
					} else {
						for (String aspect : req.get(requirementType).keySet()) {
							int classID = Integer.parseInt(aspect);
							log.debug("SendAuctionListSearcheSort: check key :" + key + " if Race match: searchClassId: " + searchClassId + " raceID:" + classID);
							if (searchClassId == classID) {
								classMatched = true;
								break;
							}
						}
					}
					if (!classMatched) {
						log.debug("SendAuctionListSearcheSort: remove key :" + key + " no math class:" + _groupedAuctions.size());
						if (!todelete.contains(key))
							todelete.add(key);
						break;
					}
				} else if (requirementTypeText.equals("Level")) {
					log.debug("SendAuctionListSearcheSort: check key :" + key + " if Level match:");
					for (int levelReq : req.get(requirementType).values()) {
						log.debug("SendAuctionListSearcheSort: check key :" + key + " if Race match: searchLevelMin: " + userSearch.searchLevelMin + " searchLevelMax:"
								+ userSearch.searchLevelMax + " levelReq:" + levelReq);
						if (levelReq < userSearch.searchLevelMin || levelReq > userSearch.searchLevelMax) {
							log.debug("SendAuctionListSearcheSort: remove key :" + key + " level no match:" + _groupedAuctions.size());
							if (!todelete.contains(key))
								todelete.add(key);

							break;
						}
					}
				}
			}
			log.debug("SendAuctionListSearcheSort: quality:"+qualityList+" _groupedAuctions:"+_groupedAuctions.size());
			if (qualityList.size()>0)
			if (!qualityList.contains(item.getIntProperty("itemGrade"))) {
				log.debug("SendAuctionListSearcheSort: not match quality "+key+" item quality:"+item.getIntProperty("itemGrade"));
				if(_groupedAuctions.containsKey(key)) {
					if (!todelete.contains(key))
						todelete.add(key);
					}else {
					log.debug("SendAuctionListSearcheSort: key "+key+" is not in _groupedAuctions "+_groupedAuctions.keySet());
				}
			}
			if (userSearch.searchText.size() > 0) {
				boolean textMatch = false;
				for (String searchText : userSearch.searchText) {
					if (searchText.length()==0) {
						textMatch = true;
					}else if (item.getName().toLowerCase().contains(searchText.toLowerCase())) {
						log.debug("SendAuctionListSearcheSort: searchText:" + searchText + " match name" + key);
						textMatch = true;
					}
				}
				if (!textMatch) {
					if (!todelete.contains(key))
						todelete.add(key);
				}
			}

			log.debug("SendAuctionListSearcheSort: searchCatType: " + userSearch.searchCatDic);
			for (String searchCatType : userSearch.searchCatDic.keySet()) {
				log.debug("SendAuctionListSearcheSort: searchCatType: " + searchCatType);
				if (searchCatType.equals("Type")) {
					log.debug("SendAuctionListSearcheSort:  key " + key + " item type:" + item.getItemType() + " searchCat:" + userSearch.searchCatDic.get(searchCatType));
					if (!item.getItemType().equals(userSearch.searchCatDic.get(searchCatType))) {
						log.debug("SendAuctionListSearcheSort: not match Type " + key + " type:" + item.getItemType());
						if (!todelete.contains(key))
							todelete.add(key);
					}
				} else if (searchCatType.equals("SubType")) {
					log.debug("SendAuctionListSearcheSort:  key " + key + " item subType:" + item.getProperty("subType") + " searchCat:" + userSearch.searchCatDic.get(searchCatType));
					if (!item.getProperty("subType").equals(userSearch.searchCatDic.get(searchCatType))) {
						log.debug("SendAuctionListSearcheSort: not match subType " + key + " subType:" + item.getProperty("subType"));
						if (!todelete.contains(key))
							todelete.add(key);
					}
				} else if (searchCatType.equals("Slot")) {
					log.debug("SendAuctionListSearcheSort:  key " + key + " item slot:" + item.getProperty("slot") + " searchCat:" + userSearch.searchCatDic.get(searchCatType));
					if (item.getPropertyMap().containsKey("slot")) {
						if (item.getProperty("slot") != null) {
							if (!item.getProperty("slot").equals(userSearch.searchCatDic.get(searchCatType))) {
								log.debug("SendAuctionListSearcheSort: not match slot " + key + " slot:" + item.getProperty("slot"));
								if (!todelete.contains(key))
									todelete.add(key);
							}
						} else {
							log.debug("SendAuctionListSearcheSort: not match slot " + key + " slot is null slot:" + item.getProperty("slot"));
							if (!todelete.contains(key))
								todelete.add(key);
						}
					} else {
						log.debug("SendAuctionListSearcheSort: not match slot " + key + " not slot in array:" + item.getProperty("slot"));
						if (!todelete.contains(key))
							todelete.add(key);
					}
				}
			}
			log.debug("SendAuctionListSearcheSort: end for _groupedAuctions:"+_groupedAuctions.size()+" keys:"+_groupedAuctions.keySet()+" todelete:"+todelete);
		}
		for (String s : todelete) {
			_groupedAuctions.remove(s);
		}
		log.debug("SendAuctionListSearcheSort: after delete keys _groupedAuctions:"+_groupedAuctions.size()+" keys:"+_groupedAuctions.keySet());
	
		ArrayList<String> sortedAuctionGroupKeys = new ArrayList<String>(_groupedAuctions.keySet());
		log.debug("sortedAuctionGroupKeys " + sortedAuctionGroupKeys + " sortCount:" + userSearch.sortCount + " sortPrice:" + userSearch.sortPrice + " sortName:" + userSearch.sortName + " _groupedAuctions:"
				+ _groupedAuctions.size());
		if (userSearch.sortCount) {
			sortedAuctionGroupKeys = SortAuctionsCount(_groupedAuctions);
		}

		else if (userSearch.sortPrice) {
			sortedAuctionGroupKeys = SortAuctionsPrice(_groupedAuctions);

		} else if (userSearch.sortName) {
			sortedAuctionGroupKeys = SortAuctionsName(_groupedAuctions);

		}
		if (!userSearch.sortAsc)
			Collections.reverse(sortedAuctionGroupKeys);

		//Prepare message 
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "AuctionListUpdate");
		HashMap<Integer, Integer> itemCounts = new HashMap<Integer, Integer>();
		int numItems = 0;
		for (String key : sortedAuctionGroupKeys) {
			if (userSearch.sortPrice) {
				Collections.sort(_groupedAuctions.get(key), new Comparator<Auction>(){
				     public int compare(Auction o1, Auction o2){
				         if(o1.GetBuyout() == o2.GetBuyout() )
				             return 0;
				         return o1.GetBuyout()  < o2.GetBuyout()  ? -1 : 1;
				     }
				});
			}
			
			
			ArrayList<Auction> _auctions = _groupedAuctions.get(key);
			Collections.sort(_auctions, new Comparator<Auction>(){
			     public int compare(Auction o1, Auction o2){
			         if(o1.GetBuyout() == o2.GetBuyout() )
			             return 0;
			         return o1.GetBuyout()  < o2.GetBuyout()  ? -1 : 1;
			     }
			});
			Auction auction = _auctions.get(0);
			
			int itemcount =0;
			for (Auction a : _groupedAuctions.get(key)) {
				if (a.GetMode()==0)
					itemcount+=a.GetItemCount();
			}
			HashMap<Long, Integer> pricesCountsOrder = new HashMap<Long, Integer>();
			HashMap<Long, Integer> pricesCountsSell = new HashMap<Long, Integer>();
			for (Auction auc : _groupedAuctions.get(key)) {
				if (auc.GetMode()==0) {
					if (pricesCountsSell.containsKey(auc.GetBuyout()))
						pricesCountsSell.replace(auc.GetBuyout(), (pricesCountsSell.get(auc.GetBuyout()) + auc.GetItemCount()));
					else
						pricesCountsSell.put(auc.GetBuyout(), auc.GetItemCount());
				}else if(auc.GetMode()==1) {
					if (pricesCountsOrder.containsKey(auc.GetBuyout()))
						pricesCountsOrder.replace(auc.GetBuyout(), (pricesCountsOrder.get(auc.GetBuyout()) + auc.GetItemCount()));
					else
						pricesCountsOrder.put(auc.GetBuyout(), auc.GetItemCount());
				}
			}
			boolean itemExists = true;
			AgisItem item = AgisInventoryClient.getItem(auction.GetItemOid());
			if (item == null) {
				// Log.warn("sendBagInvUpdate: item is null, oid=" + oid);
				itemExists = false;
			}
			if (itemExists) {
				if(auction.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				props.put("auction_" + numItems + "GroupId", key);
				props.put("auction_" + numItems + "ExpirateDate", auction.GetExpirateDate().toString());
				props.put("auction_" + numItems + "Buyout", auction.GetBuyout());
				props.put("auction_" + numItems + "Currency", auction.GetCurrency());
				props.put("auction_" + numItems + "CountsSell", pricesCountsSell.size());
				props.put("auction_" + numItems + "CountsOrder", pricesCountsSell.size());
					
				props.put("item_" + numItems + "TemplateID", item.getTemplateID());
				props.put("item_" + numItems + "Name", item.getName());
				props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
				props.put("item_" + numItems + "Id", item.getOid());
				props.put("item_" + numItems + "Count", itemcount/*item.getStackSize()*/);
				props.put("item_" + numItems + "Bound", false);

				if (item.getProperty("durability") != null) {
					props.put("item_" + numItems + "Durability", item.getProperty("durability"));
					props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
				} else {
					props.put("item_" + numItems + "MaxDurability", 0);
				}
				if (item.getProperty("resistanceStats") != null) {
					int numResist = 0;
					HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
					for (String resistance : resistances.keySet()) {
						props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
						props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
						numResist++;
					}
					props.put("item_" + numItems + "NumResistances", numResist);
				} else {
					props.put("item_" + numItems + "NumResistances", 0);
				}
				if (item.getProperty("bonusStats") != null) {
					int numStats = 0;
					HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
					for (String statName : stats.keySet()) {
						props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
						props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
						numStats++;
					}
					props.put("item_" + numItems + "NumStats", numStats);
				} else {
					props.put("item_" + numItems + "NumStats", 0);
				}
				// If it is a weapon, add damage/speed stats
				if (item.getItemType().equals("Weapon")) {
					props.put("item_" + numItems + "Delay", item.getProperty("delay"));
					props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
					props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
					props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
				}
				props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
				int enchantLevel = (int) item.getProperty("enchantLevel");
				props.put("item_" + numItems + "ELevel", enchantLevel);

				if (item.getProperty("enchantStats") != null) {
					int numStats = 0;
					HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
					HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
					for (String statName : stats.keySet()) {
						if (bstats.containsKey(statName)) {
							log.debug(item.getName() + " " + statName + " " + stats.get(statName) + " " + bstats.get(statName) + " " + (stats.get(statName) - bstats.get(statName))
									+ " ?");
							if (stats.get(statName) - bstats.get(statName) != 0) {
								props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
								props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
								log.debug(item.getName() + " " + statName + " " + (stats.get(statName) - bstats.get(statName)));
								numStats++;
							}
						} else {
							props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
							props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
							log.debug(item.getName() + " " + statName + " " + (stats.get(statName)) + " |");
							numStats++;

						}
					}
					props.put("item_" + numItems + "NumEStats", numStats);
				} else {
					props.put("item_" + numItems + "NumEStats", 0);
				}
				if (item.getProperty("sockets") != null) {
					int numSocket = 0;
					HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
					for (Integer socket : sockets.keySet()) {
						if (sockets.get(socket).GetItemOid() != null) {
							AgisItem itemSoc = AgisInventoryClient.getItem(sockets.get(socket).GetItemOid());
							if (itemSoc != null) {
								props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
								props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
									} else {
								props.put("item_" + numItems + "socket_" + socket + "Item", -1);
								props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
									}
						} else {
							props.put("item_" + numItems + "socket_" + socket + "Item", -1);
						props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
						}
						props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
						props.put("item_" + numItems + "socket_" + socket + "Id", socket);
						numSocket++;
					}
					props.put("item_" + numItems + "NumSocket", numSocket);
				} else {
					props.put("item_" + numItems + "NumSocket", 0);
				}
				int setid = (int) item.getIntProperty("item_set");
				props.put("item_" + numItems + "NumOfSet", 0);

				numItems++;
				if (itemCounts.containsKey(item.getTemplateID())) {
					itemCounts.put(item.getTemplateID(), itemCounts.get(item.getTemplateID()) + item.getStackSize());
				} else {
					itemCounts.put(item.getTemplateID(), item.getStackSize());
				}
			}
			if (numItems >= profile.display_limit) {
				log.debug("Limiting auction list to "+profile.display_limit+" for player "+playerOid);
				//ExtendedCombatMessages.sendErrorMessage(playerOid, "The auction limit has been reached you can no longer list items");
				break;
			}
				
		}
		
		props.put("numItems", numItems);
		props.put("currency",profile.currency);
		
		float vipModp = 0;
		int vipLimit = 0;
		long vipMod = 0;
		if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
			vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
  			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
  		}
		if(globalEventBonusesArray.containsKey("AuctionCost")) {
			vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
  			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
  		}
		if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCount")) {
			vipLimit = bonusesArray.get(playerOid).get("AuctionCount").GetValue();
  		}
		if(globalEventBonusesArray.containsKey("AuctionCount")) {
			vipLimit += globalEventBonusesArray.get("AuctionCount").GetValue();
  		}
		log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
		long startPriceValue = profile.start_price_value + vipMod;
		if(startPriceValue < 0)
			startPriceValue = 0l;
		long costPriceValue = profile.cost_price_value + vipMod;
		if(costPriceValue < 0)
			costPriceValue = 0l;
		float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
		if (startPriceValueP < 0)
			startPriceValueP = 0;
		float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
		if (costPriceValueP < 0)
			costPriceValueP = 0;

		int limit = profile.own_limit + vipLimit;
		if(limit < 0)
			limit = 0;
	
		props.put("currency",profile.currency);
		props.put("sPriceVal",startPriceValue);
		props.put("SPricePerc",startPriceValueP); 
		props.put("cPriceVal",costPriceValue);
		props.put("cPricePerc",costPriceValueP);
		props.put("auctionLimit",profile.display_limit );
		props.put("auctionOwnLimit",limit);
		
		if (Log.loggingDebug)
			log.debug("Auction send:"+props);
		TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(tmsg);
}
	
	
	public static ArrayList<String> SortAuctionsCount( HashMap<String, ArrayList<Auction>> auctionCountryMap ) 
		{

		    if (auctionCountryMap != null) {
		    	ArrayList<String> values = new ArrayList<String>();
		        values.addAll(auctionCountryMap.keySet());
		        Collections.sort(values, new Comparator<String>() {
		            public int compare(String o1, String o2) {
		            	 int oc1=0;
				    	 int oc2=0;
				    	 for (Auction a1 : auctionCountryMap.get(o1)) {
				    		 oc1+=a1.GetItemCount();
				    	 }
				    	 for (Auction a2 : auctionCountryMap.get(o2)) {
				    		 oc2+=a2.GetItemCount();
				    	 }
				         if(oc1 == oc2 )
				             return 0;
				         return oc1  < oc2  ? -1 : 1;
				     }
		        });
		        return values;
		    }
		    return null;
		}
	
	public static ArrayList<String> SortAuctionsPrice(HashMap<String, ArrayList<Auction>> auctionCountryMap) {
		if (auctionCountryMap != null) {
			ArrayList<String> values = new ArrayList<String>();
			values.addAll(auctionCountryMap.keySet());
			Collections.sort(values, new Comparator<String>() {
				public int compare(String sk1, String sk2) {
					Collections.sort(auctionCountryMap.get(sk1), new Comparator<Auction>() {
						public int compare(Auction o1, Auction o2) {
							if (o1.GetBuyout() == o2.GetBuyout())
								return 0;
							return o1.GetBuyout() < o2.GetBuyout() ? -1 : 1;
						}
					});
					Collections.sort(auctionCountryMap.get(sk2), new Comparator<Auction>() {
						public int compare(Auction o1, Auction o2) {
							if (o1.GetBuyout() == o2.GetBuyout())
								return 0;
							return o1.GetBuyout() < o2.GetBuyout() ? -1 : 1;
						}
					});
					if (auctionCountryMap.get(sk1).get(0).GetBuyout() == auctionCountryMap.get(sk2).get(0).GetBuyout())
						return 0;
					return auctionCountryMap.get(sk1).get(0).GetBuyout() < auctionCountryMap.get(sk2).get(0).GetBuyout() ? -1 : 1;
				}
			});

			return values;
		}
		return null;
	}
	

	public static ArrayList<String> SortAuctionsName(HashMap<String, ArrayList<Auction>> auctionCountryMap) {
		if (auctionCountryMap != null) {
			ArrayList<String> values = new ArrayList<String>();
			values.addAll(auctionCountryMap.keySet());
			Collections.sort(values, new Comparator<String>() {
				public int compare(String sk1, String sk2) {
					if (auctionCountryMap.get(sk1).get(0).GetItemName().equals(auctionCountryMap.get(sk2).get(0).GetItemName())) {
						if (auctionCountryMap.get(sk1).get(0).GetItemEnchanteLevel() == auctionCountryMap.get(sk2).get(0).GetItemEnchanteLevel())
							return 0;
						return auctionCountryMap.get(sk1).get(0).GetItemEnchanteLevel() < auctionCountryMap.get(sk2).get(0).GetItemEnchanteLevel() ? -1 : 1;
					}
					return auctionCountryMap.get(sk1).get(0).GetItemName().compareTo(auctionCountryMap.get(sk2).get(0).GetItemName());
				}
			});
			return values;
		}
		return null;
	}
	
	class SendAuctionList implements Hook {
		public boolean processMessage(Message msg, int flag) {
			log.debug("SendAuctionList Start");
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			long auctioneerID = 1;
			if (message.hasProperty("auctioneer"))
				auctioneerID = (long) message.getProperty("auctioneer");
			log.error("SendAuctionList AUCTION_NPC_ONLY=" + AUCTION_NPC_ONLY + " npc=" + (message.hasProperty("npc") ? message.getProperty("npc") : "BD"));
			if (AUCTION_NPC_ONLY && (!message.hasProperty("npc") || (message.hasProperty("npc") && !(boolean) message.getProperty("npc")))) {
				playerAuctioneer.put(playerOid, -1L);
				return true;
			}
			log.error("SendAuctionList auctioneerID="+auctioneerID);
			AuctionProfile profile = auctionProfiles.get(auctioneerID);
			log.error("SendAuctionList profile="+profile);
			
			playerAuctioneer.put(playerOid, auctioneerID);
			
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "AuctionList");
		
			float vipModp = 0;
			int vipLimit = 0;
			long vipMod = 0;
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
				vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
	  			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
	  		}
			if(globalEventBonusesArray.containsKey("AuctionCost")) {
				vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
	  			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
	  		}
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCount")) {
				vipLimit = bonusesArray.get(playerOid).get("AuctionCount").GetValue();
	  		}
			if(globalEventBonusesArray.containsKey("AuctionCount")) {
				vipLimit += globalEventBonusesArray.get("AuctionCount").GetValue();
	  		}
			log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
			long startPriceValue = profile.start_price_value + vipMod;
			if(startPriceValue < 0)
				startPriceValue = 0l;
			long costPriceValue = profile.cost_price_value + vipMod;
			if(costPriceValue < 0)
				costPriceValue = 0l;
			float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
			if (startPriceValueP < 0)
				startPriceValueP = 0;
			float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
			if (costPriceValueP < 0)
				costPriceValueP = 0;

		
			
			int limit = profile.own_limit + vipLimit;
			if(limit < 0)
				limit = 0;
		
			props.put("currency",profile.currency);
			props.put("sPriceVal",startPriceValue);
			props.put("SPricePerc",startPriceValueP); 
			props.put("cPriceVal",costPriceValue);
			props.put("cPricePerc",costPriceValueP);
			props.put("auctionLimit",profile.display_limit );
			props.put("auctionOwnLimit",limit);
	if (Log.loggingDebug)
				log.debug("Auction send:"+props);
			TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(tmsg);

			return true;
		}
	}
	
	class SendAuctionOwnerList implements Hook {
		public boolean processMessage(Message msg, int flag) {
			log.debug("SendAuctionOwnerList Start");
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			boolean buying = (boolean) message.getProperty("buying");
			boolean selling = (boolean) message.getProperty("selling");
			boolean bought = (boolean) message.getProperty("bought");
			boolean sold = (boolean) message.getProperty("sold");
			boolean expired = (boolean) message.getProperty("expired");
			log.debug("SendAuctionOwnerList Start buying:"+buying+ " selling:"+selling+" bought:"+bought+" sold:"+sold+" expired:"+expired);
			sendTransactionsUpdate(playerOid, buying, selling, bought, sold, expired);
			return true;
		}
	}
	
	class GetAuctionForGroup implements Hook {
		public boolean processMessage(Message msg, int flag) {
			log.debug("GetAuctionForGroup Start");
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			long itemOid = (long) message.getProperty("itemOid");
			String groupId = (String) message.getProperty("groupId");
			
			
			AuctionProfile profile = auctionProfiles.get(playerAuctioneer.get(playerOid));
			log.debug("GetAuctionForGroup: itemOid:"+itemOid+" groupId:"+groupId);
			if (itemOid>0) {
				AgisItem item = AgisInventoryClient.getItem(OID.fromLong(itemOid));
				if (item != null) {
					String unicItem = item.getTemplateID()+"";
					if((int)item.getProperty("enchantLevel")>0)
						unicItem += "_E"+item.getProperty("enchantLevel");
					HashMap<Integer,SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
					ArrayList<Long> socketItems = new ArrayList<Long>();
					for (Integer sId : itemSockets.keySet()) {
						if (itemSockets.get(sId).GetItemOid()!=null) {
							socketItems.add(itemSockets.get(sId).GetItemOid().toLong());
						}
					}
					Collections.sort(socketItems);
					for(Long l : socketItems) {
						unicItem += "_S"+l;
					}
					groupId = unicItem;
				}
				
			}
			log.debug("GetAuctionForGroup: 2 itemOid:"+itemOid+" groupId:"+groupId);
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "AuctionListForGorupUpdate");
			int numItemsSell = 0;
			int numItemsOrder = 0;
			HashMap<Long, Integer> pricesCountsOrder = new HashMap<Long, Integer>();
			HashMap<Long, Integer> pricesCountsSell = new HashMap<Long, Integer>();
			log.debug("GetAuctionForGroup:  itemOid:"+itemOid+" groupId:"+groupId+" groupedAuctions:"+groupedAuctions.keySet());
			if(groupedAuctions.containsKey(groupId)) {
			for (Auction auction : groupedAuctions.get(groupId)) {
				if(auction.GetAuctioneer() != playerAuctioneer.get(playerOid))
					continue;
				if (auction.GetMode()==0) {
					if (pricesCountsSell.containsKey(auction.GetBuyout()))
						pricesCountsSell.replace(auction.GetBuyout(), (pricesCountsSell.get(auction.GetBuyout()) + auction.GetItemCount()));
					else
						pricesCountsSell.put(auction.GetBuyout(), auction.GetItemCount());
				}else if(auction.GetMode()==1) {
					if (pricesCountsOrder.containsKey(auction.GetBuyout()))
						pricesCountsOrder.replace(auction.GetBuyout(), (pricesCountsOrder.get(auction.GetBuyout()) + auction.GetItemCount()));
					else
						pricesCountsOrder.put(auction.GetBuyout(), auction.GetItemCount());
				}
			}
			log.debug("GetAuctionForGroup: itemOid:"+itemOid+" groupId: "+groupId+" pricesCountsSell.size: "+pricesCountsSell.size()+" pricesCountsOrder.size: "+pricesCountsOrder.size());
			List<Long> sortedKeysSell=new ArrayList(pricesCountsSell.keySet());
			Collections.sort(sortedKeysSell);
		
			for (Long price : sortedKeysSell) {
				
				props.put("auctionSell_" + numItemsSell + "Count", pricesCountsSell.get(price));
				props.put("auctionSell_" + numItemsSell + "Price", price);
				props.put("auctionSell_" + numItemsSell + "Currency", groupedAuctions.get(groupId).get(0).GetCurrency());
					numItemsSell++;
			}
			props.put("numItemsSell", numItemsSell);
			
			
			List<Long> sortedKeysOrder=new ArrayList(pricesCountsOrder.keySet());
			//Collections.sort(sortedKeys);
			Collections.reverse(sortedKeysOrder);
			for (Long price : sortedKeysOrder) {
				
				props.put("auctionOrder_" + numItemsOrder + "Count", pricesCountsOrder.get(price));
				props.put("auctionOrder_" + numItemsOrder + "Price", price);
				props.put("auctionOrder_" + numItemsOrder + "Currency", groupedAuctions.get(groupId).get(0).GetCurrency());
				numItemsOrder++;
			}
			props.put("numItemsOrder", numItemsOrder);
			
			float vipModp = 0;
			long vipMod = 0;
			if(bonusesArray.containsKey(playerOid) && bonusesArray.get(playerOid).containsKey("AuctionCost")) {
				vipMod = bonusesArray.get(playerOid).get("AuctionCost").GetValue();
      			vipModp = bonusesArray.get(playerOid).get("AuctionCost").GetValuePercentage();
      		}
			if(globalEventBonusesArray.containsKey("AuctionCost")) {
				vipMod += globalEventBonusesArray.get("AuctionCost").GetValue();
	  			vipModp += globalEventBonusesArray.get("AuctionCost").GetValuePercentage();
	  		}
			log.debug("AuctionPlugin AuctionCost vipMod="+vipMod+" vipModp"+vipModp);
			long startPriceValue = profile.start_price_value + vipMod;
			if(startPriceValue < 0)
				startPriceValue = 0l;
			long costPriceValue = profile.cost_price_value + vipMod;
			if(costPriceValue < 0)
				costPriceValue = 0l;
			float startPriceValueP = profile.start_price_percentage + (profile.start_price_percentage * vipModp / 100);
			if (startPriceValueP < 0)
				startPriceValueP = 0;
			float costPriceValueP = profile.cost_price_percentage + (profile.cost_price_percentage * vipModp / 100);
			if (costPriceValueP < 0)
				costPriceValueP = 0;

		
			props.put("currency",profile.currency);
			props.put("sPriceVal",startPriceValue);
			props.put("SPricePerc",startPriceValueP); 
			props.put("cPriceVal",costPriceValue);
			props.put("cPricePerc",costPriceValueP);
			
			if (Log.loggingDebug)
				log.debug("GetAuctionForGroup Auctions send:"+props);
			TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(tmsg);
			}
			return true;
			}
	}
	
	
	/**
	 * The hook for when players login. This will reset their arenaID (in case there
	 * was a server crash) and teleport them back to the original world.
	 */
	
	class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();
			OID instanceOid = message.getInstanceOid();
			log.debug("LOGIN: auction login started for: " + playerOid+" instanceOid:" + instanceOid);
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			log.debug("LOGIN: auction login finished for: " + playerOid+" instanceOid:" + instanceOid);
				return true;
		}
	}

	/**
	 * The hook for when players logout (or disconnect). This will remove the player
	 * from any arenas and queues they are in.
	 *
	 */
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("LOGOUT: auction logout started for: " + playerOid);
			if(usersSearch.containsKey(playerOid))
				usersSearch.remove(playerOid);
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			log.debug("LOGOUT: auction logout finished for: " + playerOid);
			return true;
		}
	}

	/**
	 * Handles the ServerTimeMessage. Passes the server time to the spawn generator
	 * so it can enable or disable any spawn generators that are affected by the
	 * change in time.
	 * 
	 * @author Andrew Harrison
	 *
	 */
	class ServerTimeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisWorldManagerClient.ServerTimeMessage tMsg = (AgisWorldManagerClient.ServerTimeMessage) msg;
			log.debug("TIME: got server time message with hour: " + tMsg.getHour());
			serverTimeUpdate(tMsg.getHour(), tMsg.getMinute());
			return true;
		}
	}

	public void serverTimeUpdate(int wHour, int wMinute) {
		hour = wHour;
		minute = wMinute;
	}

	class Search {
		public OID playerOid;
		public boolean sortCount = false;
		public boolean sortName = false;
		public boolean sortPrice = false;
		public boolean sortAsc = true;
		public List<Integer> searchQuality = new ArrayList<Integer>(); 
		public String searchClass = "";
		public String searchRace = "";
		public int searchLevelMin = 1;
		public int searchLevelMax = 999;
		public String searchCatType = "";
		public String searchCat = "";
		public List<String> searchText = new ArrayList<String>(); 
		public HashMap<String, String> searchCatDic = new HashMap<String, String>(); 
		Search(){
			
		}
	}
	 /**
     * Hook to Update Global Events Bonuses 
     *
     */
    
    class GlobalEventBonusesUpdateHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	GlobalEventBonusesUpdateMessage message = (GlobalEventBonusesUpdateMessage) msg;
        	//OID playerOid = message.getSubject();
            Log.debug("GlobalEventBonusesUpdateHook: "+message.getBonuses());
          
            globalEventBonusesArray.clear();
            globalEventBonusesArray.putAll(message.getBonuses());
            Log.debug("GlobalEventBonusesUpdateHook:  End");
            return true;
        }
    }
    
    /**
     * Hook to Update Player Bonuses 
     *
     */
	class BonusesUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			BonusesUpdateMessage message = (BonusesUpdateMessage) msg;
	        	OID playerOid = message.getSubject();
	            Log.debug("BonusesUpdateHook: playerOid: " + playerOid +" "+message.getBonuses());
	                  if(bonusesArray.containsKey(playerOid))
	            	bonusesArray.replace(playerOid, message.getBonuses());
	            	else
	            		bonusesArray.put(playerOid, message.getBonuses());
		            	
	            Log.debug("BonusesUpdateHook: playerOid: " + playerOid);
	            return true;
	        }
	}
	private Map<OID, HashMap<String, BonusSettings>> bonusesArray = new ConcurrentHashMap<OID, HashMap<String, BonusSettings>>();
	private ConcurrentHashMap<String, BonusSettings> globalEventBonusesArray = new ConcurrentHashMap<String, BonusSettings>();
	   
	HashMap<OID,Search> usersSearch = new HashMap<OID,Search>();
	
	/*
	 * Variables for the Auction Plugin
	 */
	protected static AccountDatabase aDB;
	protected AuthDatabase authDB;
	protected static int hour = 0;
	protected static int minute = 0;

	protected static String WORLD_TIME_ZONE = "UTC";
	
	public ArrayList<Auction> auctions = new ArrayList<Auction>();
	HashMap<String,ArrayList<Auction>> groupedAuctions = new HashMap<String,ArrayList<Auction>>();
	private HashMap<Long, AuctionProfile> auctionProfiles =new HashMap<Long, AuctionProfile>();

	ConcurrentHashMap<OID,Long> playerAuctioneer = new ConcurrentHashMap<OID,Long>();
	//Game Settings
		protected static int AUCTION_LOAD_DELAY = 10;
	protected static boolean AUCTION_NPC_ONLY = false;
	/*public static String AUCTION_MAIL_TOPIC = "Auction House transaction";
	public static String AUCTION_MAIL_PARTIALLY_SOLD = "You have partially sold {0} of {1}, You can now go to the auction house to pick up reward";
	public static String AUCTION_MAIL_SOLD = "You have sold {0} of {1}, You can now go to the auction house to pick up reward";
	public static String AUCTION_MAIL_PARTIALLY_BOUGHT = "You have partially bought {0} of {1} , You can now go to the auction house to pick up reward";
	public static String AUCTION_MAIL_BOUGHT = "You have bougth {0}, You can now go to the auction house to pick up reward";
	public static String AUCTION_MAIL_EXPIRED = "Auction of {0} is Expired";
	*/
}
