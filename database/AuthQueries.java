package atavism.agis.database;

import java.sql.*;

import atavism.server.engine.Engine;
import atavism.server.util.*;

public class AuthQueries extends Queries {
	
	//boolean keepAliveStarted = false;
	
	public AuthQueries() {
	    super(true);
	}
	
	protected boolean loadStrings() {
		boolean differentSettings = Boolean.parseBoolean(Engine.getProperty("atavism.auth.db_differentsettings"));
		
		DATABASENAME = Engine.getProperty("atavism.auth.db_name");
		DBTIMEZONE = Engine.getProperty("atavism.db_timezone");
			if (differentSettings) {
			DATABASEDRIVER = Engine.getProperty("atavism.auth.db_driver");
			USERNAME = Engine.getProperty("atavism.auth.db_user");
			PASSWORD = Engine.getProperty("atavism.auth.db_password");
			IPADDRESS =  Engine.getProperty("atavism.auth.db_hostname");
		} else {
			DATABASEDRIVER = Engine.getProperty("atavism.db_driver");
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
	
	/**
     * Runs a select statement to make sure we can still talk to the
     * database.
     */
    public void ping() {
        Log.debug("AuthDatabase: ping");
            String sql = "SELECT 1 from world";
        try (Statement stmt = con.createStatement()) {
            stmt.executeQuery(sql);
        } catch (Exception e) {
            reconnect();
        }
    }
    
 }