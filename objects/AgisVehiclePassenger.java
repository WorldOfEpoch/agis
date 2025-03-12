package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.CombatClient;
import atavism.msgsys.NoRecipientsException;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.objects.Entity;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class AgisVehiclePassenger extends Entity {
    // properties
    private static final long serialVersionUID = 1L;
    private OID passengerOid;
    private String passengerName;
    private int passengerLevel;
    private boolean isInVehicle;
    private static final Logger _log = new Logger("Passenger");
    private Map<String, Serializable> _entryStats = new ConcurrentHashMap<String, Serializable>();

    // constructor
    public AgisVehiclePassenger(OID oid) {
        super("");
        this.passengerOid = oid;
		AccountDatabase aDB = new AccountDatabase(false);
		this.passengerName = aDB.getCharacterNameByOid(oid);
        isInVehicle = false;
    }
    
  public int GetPassengerLevel() {
    	
    	if (Log.loggingDebug) {
            _log.error("AgisVehiclePassenger.GetGroupMemberLevel : " +passengerName+" "+_entryStats.get("level"));
        }
    	int level = -1;
		try {
			LinkedList<String> param = new LinkedList<String>();
			param.add("level");
			HashMap<String,Serializable> objecParams = CombatClient.getCombatInfoParams(passengerOid, param );
			if (Log.loggingDebug) {
			    _log.error("AgisGroup.GetGroupMemberLevel : " +objecParams);
			}
			level = (Integer)objecParams.get("level");
		} catch (NullPointerException e) {
			Log.warn("GroupMember: player " + passengerOid + "|" + passengerName + " does not have an level property");
			if(_entryStats.containsKey("level"))
				level = (int)_entryStats.get("level");
		} catch (NoRecipientsException e) {
			Log.warn("GroupMember: player " + passengerOid + "|" + passengerName + " cant get level property");
			if(_entryStats.containsKey("level"))
				level = (int)_entryStats.get("level");
		}

		 _entryStats.put("level",level);
     	if (Log.loggingDebug) {
            _log.error("AgisGroup.GetGroupMemberLevel END : " +passengerName+" "+_entryStats.get("level"));
        }
    	return level;
    }

	public String GetPassengerPortrait() {

		if (Log.loggingDebug) {
			_log.error("AgisVehiclePassenger.GetGroupMemberPortrait : "+passengerName+" " + _entryStats.get("portrait"));
		}
		String portrait = null;
		try {
			portrait = (String) EnginePlugin.getObjectProperty(passengerOid, WorldManagerClient.NAMESPACE, "portrait");
		} catch (NullPointerException e) {
			Log.warn("GroupMember: player " + passengerOid + "|" + passengerName + " does not have an portrait property");
			if(_entryStats.containsKey("portrait"))
				portrait = (String)_entryStats.get("portrait");
		} catch (NoRecipientsException e) {
			Log.warn("GroupMember: player " + passengerOid + "|" + passengerName + " does not have an portrait property");
			if(_entryStats.containsKey("portrait"))
				portrait = (String)_entryStats.get("portrait");
		}
		if(portrait != null)
			_entryStats.put("portrait",portrait);
		return portrait;

	}

	
       // methods
    public OID getPassengerOid() {
        return this.passengerOid;
    }

    public String getPassengerName() {
        return this.passengerName;
    }
    
    public int getPassengerLevel() {
        return this.passengerLevel;
    }

    public boolean isInVehicle() {
        return isInVehicle;
    }

    public void setInVehicle(boolean inVehicle) {
        this.isInVehicle = inVehicle;
    }

    // Additional methods can be added as needed, 
    // e.g., for updating passenger status, position, etc.
}
