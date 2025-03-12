package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;

import atavism.agis.objects.BonusSettings;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;

public class BonusClient {
	/***
	 * send Update message to modules with list of active bonuses for player
	 * @param playerOid
	 * @param bonuses
	 */
	public static void sendBonusesUpdate(OID playerOid, HashMap<String, BonusSettings> bonuses) {
		BonusesUpdateMessage msg = new BonusesUpdateMessage(playerOid, bonuses);
    	Engine.getAgent().sendBroadcast(msg);
	}
	
	 public static class BonusesUpdateMessage extends SubjectMessage {
	        public BonusesUpdateMessage() {
	            super(MSG_TYPE_BONUSES_UPDATE);
	        }

	        public BonusesUpdateMessage(OID playerOid, HashMap<String, BonusSettings> bonuses) {
	            super(MSG_TYPE_BONUSES_UPDATE, playerOid);
	            setPlayerOid(playerOid);
	            setBonuses(bonuses);
	        }
	        
	        OID playerOid = null;
	        public OID getPlayerOid() {return playerOid;}
	        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}

	        HashMap<String, BonusSettings> bonuses = new HashMap<String, BonusSettings> ();
	        public  HashMap<String, BonusSettings> getBonuses() {return bonuses;}
	        public void setBonuses( HashMap<String, BonusSettings> bonuses) {this.bonuses = bonuses;}

	        private static final long serialVersionUID = 1L;
	    }
	 
	 
	 
	public static void sendGlobalEventBonusesUpdate(HashMap<String, BonusSettings> bonuses) {
		GlobalEventBonusesUpdateMessage msg = new GlobalEventBonusesUpdateMessage(bonuses);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static class GlobalEventBonusesUpdateMessage extends GenericMessage {
		public GlobalEventBonusesUpdateMessage() {
			super(MSG_TYPE_GLOBAL_EVENT_UPDATE);
		}

		public GlobalEventBonusesUpdateMessage(HashMap<String, BonusSettings> bonuses) {
			super(MSG_TYPE_GLOBAL_EVENT_UPDATE);

			setBonuses(bonuses);
		}

		HashMap<String, BonusSettings> bonuses = new HashMap<String, BonusSettings>();

		public HashMap<String, BonusSettings> getBonuses() {
			return bonuses;
		}

		public void setBonuses(HashMap<String, BonusSettings> bonuses) {
			this.bonuses = bonuses;
		}

		private static final long serialVersionUID = 1L;
	} 
	 
	 
	 
	 
	 
	 
	 
	 /***
	  * send Message to activate bonus for player
	  * @param playerOid
	  * @param bonus
	  */
	 public static void sendBonusAdd(OID playerOid,   ArrayList<BonusSettings>  bonuses, String obj) {
		 Log.debug("sendBonusAdd playerOid="+playerOid+" bonuses="+bonuses+" obj="+obj);
		 AddBonusMessage msg = new AddBonusMessage(playerOid, bonuses, obj);
	    	Engine.getAgent().sendBroadcast(msg);
		}
	 
	 public static class AddBonusMessage extends SubjectMessage {
	        public AddBonusMessage() {
	            super(MSG_TYPE_BONUS_ADD);
	        }

	        public AddBonusMessage(OID playerOid,  ArrayList<BonusSettings>  bonuses, String obj) {
	            super(MSG_TYPE_BONUS_ADD, playerOid);
	            setPlayerOid(playerOid);
	            setBonuses(bonuses);
	            setObj(obj);
	        }
	        
	        OID playerOid = null;
	        public OID getPlayerOid() {return playerOid;}
	        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}

	        ArrayList<BonusSettings> bonuses ;
	        public  ArrayList<BonusSettings> getBonuses() {return bonuses;}
	        public void setBonuses(ArrayList<BonusSettings> bonuses) {this.bonuses = bonuses;}

	        String obj;
	        public  String getObj() {return obj;}
	        public void setObj(String obj) {this.obj = obj;}
	        private static final long serialVersionUID = 1L;
	    }
	 /**
	  * remove Bonus from player
	  * @param playerOid
	  * @param bonus
	  */
	 public static void sendBonusRemove(OID playerOid,    ArrayList<BonusSettings>  bonuses, String obj) {
		 RemoveBonusMessage msg = new RemoveBonusMessage(playerOid, bonuses, obj);
	    	Engine.getAgent().sendBroadcast(msg);
		}
	 
	 public static class RemoveBonusMessage extends SubjectMessage {
	        public RemoveBonusMessage() {
	            super(MSG_TYPE_BONUS_REMOVE);
	        }

	        public RemoveBonusMessage(OID playerOid,   ArrayList<BonusSettings>  bonuses, String obj) {
	            super(MSG_TYPE_BONUS_REMOVE, playerOid);
	            setPlayerOid(playerOid);
	            setBonuses(bonuses);
	            setObj(obj);
	        }
	        
	        OID playerOid = null;
	        public OID getPlayerOid() {return playerOid;}
	        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}

	        ArrayList<BonusSettings>  bonuses ;
	        public    ArrayList<BonusSettings>  getBonuses() {return bonuses;}
	        public void setBonuses(  ArrayList<BonusSettings>  bonuses) {this.bonuses = bonuses;}
	        String obj;
	        public  String getObj() {return obj;}
	        public void setObj(String obj) {this.obj = obj;}
	        private static final long serialVersionUID = 1L;
	    }
	 
	 
    public static void extendVip(OID playerOid, int points, int time){
    	ExtendVipMessage msg = new ExtendVipMessage(playerOid, points, time);
    	Engine.getAgent().sendBroadcast(msg);
    }
    /**
     	float bonusModp = 0;
			long bonusMod = 0;
	        InventoryInfo iInfo = getInventoryInfo(oid);
      		if(iInfo.getBonuses().containsKey("PriceMerchant")) {
      			bonusMod =iInfo.getBonuses().get("PriceMerchant").GetValue();
      			bonusModp =iInfo.getBonuses().get("PriceMerchant").GetValuePercentage();
      		}
     */
   /**
    *  Message used to try add Point or extend time of vip.
    * 
    */
   
    public static class ExtendVipMessage extends SubjectMessage {
        public ExtendVipMessage() {
            super(MSG_TYPE_EXTEND_VIP);
        }

        public ExtendVipMessage(OID playerOid, int points, int time) {
            super(MSG_TYPE_EXTEND_VIP);
            setPlayerOid(playerOid);
            setPoints(points);
            setTime(time);
        }
        
        OID playerOid = null;
        public OID getPlayerOid() {return playerOid;}
        public void setPlayerOid(OID playerOid) {this.playerOid = playerOid;}
        
        int points = 0;
        public int getPoints() {return points;}
        public void setPoints(int points) {this.points = points;}

        int time = 0;
        public int getTime() {return time;}
        public void setTime(int time) {this.time = time;}

        private static final long serialVersionUID = 1L;
    }

    // Enumerated values of QuestStatus
    
    public static final MessageType MSG_TYPE_BONUS_ADD = MessageType.intern("ao.BONUS_ADD");
    public static final MessageType MSG_TYPE_BONUS_REMOVE = MessageType.intern("ao.BONUS_REMOVE");
    public static final MessageType MSG_TYPE_BONUSES_UPDATE = MessageType.intern("ao.BONUSES_UPDATE");
    public static final MessageType MSG_TYPE_GLOBAL_EVENT_UPDATE = MessageType.intern("ao.GLOBAL_EVENT_UPDATE");
     public static final MessageType MSG_TYPE_EXTEND_VIP = MessageType.intern("ao.EXTEND_VIP");
    public static final MessageType MSG_TYPE_GET_VIP = MessageType.intern("ao.GET_VIP");
    public static final MessageType MSG_TYPE_GET_ALL_VIP = MessageType.intern("ao.GET_ALL_VIP");
    public static final String EXTMSG_VIP_UPDATE = "ao.VIP_UPDATE";
    public static final String EXTMSG_ALL_VIP_UPDATE = "ao.ALL_VIP_UPDATE";
    
    
}
