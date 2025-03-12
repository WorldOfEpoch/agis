package atavism.agis.database;

import java.sql.*;

import atavism.server.engine.Engine;
import atavism.server.util.Log;

public class AdminQueries extends Queries {
	
	//boolean keepAliveStarted = false;
	
	public AdminQueries(boolean keepAlive) {
	    super(keepAlive);
		if (keepAlive) {
			Log.debug("AdminDatabase: starting keepalive");
		}
	}
	
	protected boolean loadStrings() {
		boolean differentSettings = Boolean.parseBoolean(Engine.getProperty("atavism.admin.db_differentsettings"));
		
		DATABASENAME = Engine.getProperty("atavism.admin.db_name");
		DATABASEDRIVER = Engine.getProperty("atavism.admin.db_driver");
		DBTIMEZONE = Engine.getProperty("atavism.db_timezone");
		if (differentSettings) {
			USERNAME = Engine.getProperty("atavism.admin.db_user");
			PASSWORD = Engine.getProperty("atavism.admin.db_password");
			IPADDRESS =  Engine.getProperty("atavism.admin.db_hostname");
		} else {
			USERNAME = Engine.getProperty("atavism.db_user");
			PASSWORD = Engine.getProperty("atavism.db_password");
			IPADDRESS =  Engine.getProperty("atavism.db_hostname");
		}
		
		if (IPADDRESS.contains(":")) {
			String[] ipdetails = IPADDRESS.split(":");
			IPADDRESS = ipdetails[0];
			PORT = Integer.parseInt(ipdetails[1]);
		} else {
			PORT = 3306;
		}
		
		return true;
	}
	
	public void close() {
		Log.dumpStack("AdminDababase.Close");
		super.close();
	}

	/**
     * Runs a select statement to make sure we can still talk to the
     * database.
     */
    public void ping() {
        Log.debug("AdminDatabase: ping");
        try {
            String sql = "SELECT 1 from server_version";
            try (Statement stmt = con.createStatement()) {
                stmt.executeQuery(sql);
            }
        } catch (Exception e) {
            reconnect();
        }
    }
    
}
