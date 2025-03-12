package atavism.agis.database;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;

import atavism.server.engine.Engine;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;
import com.mysql.cj.jdbc.exceptions.CommunicationsException;

public class Queries {
	protected Connection con;
	protected int PORT;
	protected String DBTIMEZONE = "UTC";
	protected String USERNAME;
	protected String PASSWORD;
	protected String IPADDRESS;
	protected String DATABASENAME;
	protected String DATABASEDRIVER= "com.mysql.cj.jdbc.Driver";

	public Queries(boolean keepAlive) {
	    con = initConnection();
		if (keepAlive) {
			Log.debug("ContentDatabase: starting keepalive");
			Thread keepAliveThread = new Thread(new KeepAlive(this::ping), "DBKeepalive");
			keepAliveThread.start();
		}
	}
	
	protected boolean loadStrings() {
		boolean differentSettings = Boolean.parseBoolean(Engine.getProperty("atavism.content.db_differentsettings"));
		
		DATABASENAME = Engine.getProperty("atavism.content.db_name");
		DATABASEDRIVER = Engine.getProperty("atavism.content.db_driver");
		DBTIMEZONE = Engine.getProperty("atavism.db_timezone");
			if (differentSettings) {
			USERNAME = Engine.getProperty("atavism.content.db_user");
			PASSWORD = Engine.getProperty("atavism.content.db_password");
			IPADDRESS =  Engine.getProperty("atavism.content.db_hostname");
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
	
	public String getConnectionString() {
		return "jdbc:mysql://" + IPADDRESS + ":" + PORT + "/" + DATABASENAME + "?useUnicode=yes&characterEncoding=UTF-8&serverTimezone="+DBTIMEZONE+"&autoReconnect=true&tcpKeepAlive=true";
	}

	public Connection initConnection() {
		try {
			if (loadStrings()) { 
			    Class.forName(DATABASEDRIVER).newInstance();
			    //System.out.println("Connecting to db with info: " + getConnectionString());
			    return DriverManager.getConnection(getConnectionString(), USERNAME, PASSWORD);
			}
		} catch (SQLException e) {
			Log.error("Queries.initConnection  SQLException: "+e);
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Log.error("Queries.initConnection  ClassNotFoundException: "+e);
				e.printStackTrace();
		} catch (InstantiationException e) {
			Log.error("Queries.initConnection  InstantiationException: "+e);
				e.printStackTrace();
		} catch (IllegalAccessException e) {
			Log.error("Queries.initConnection  IllegalAccessException: "+e);
				e.printStackTrace();
		}
		return null;
	}

	protected void finalize() throws Throwable {
		con.close();
	}

	public void close() {
		try {
			//con.commit();
			con.close();
		} catch (SQLException e) {
			Log.error("Queries.close  SQLException: "+e);
				e.printStackTrace();
		}
	}

	public PreparedStatement prepare(String sql) {
		checkConnection();
		try {
			return con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		} catch (SQLException e) {
			Log.error("Queries.prepare  SQLException: "+e);
				e.printStackTrace();
			return null;
		}
	}

	public ResultSet executeSelect(PreparedStatement ps) {

		checkConnection();
		try {
			return ps.executeQuery();
		} catch (CommunicationsException e) {
			Log.error("Queries.executeSelect SQLException: "+e);
			try {
				return ps.executeQuery();
			} catch (CommunicationsException e1) {
				Log.error("Queries.executeSelect second try QLException: "+e1);
				return null;
			} catch (SQLException e1) {
				Log.error("Queries.executeSelect second try SQLException: "+e1);
				e.printStackTrace();
				return null;
			}
		} catch (SQLException e) {
			Log.error("Queries.executeSelect SQLException: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	public int executeInsert(String query) {
		checkConnection();
		try (Statement stmt = con.createStatement()) {
			stmt.execute(query, Statement.RETURN_GENERATED_KEYS);
			try (ResultSet rs = stmt.getGeneratedKeys()) {
    			int insertedKeyValue = -1;
    			if (rs.next()) {
    				insertedKeyValue = rs.getInt(1);
    			}
    			Log.debug("Executed insert and got key: " + insertedKeyValue);
    			return insertedKeyValue;
			}
		} catch (SQLException e) {
			Log.exception("Queries.executeInsert query= "+query+" SQLException: ",e);
				e.printStackTrace();
			return -1;
        }
	}

	public int executeInsert(PreparedStatement ps) {
		checkConnection();
		try {
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
    			int insertedKeyValue = -1;
    			if (rs.next()) {
    				insertedKeyValue = rs.getInt(1);
    			}
    			Log.debug("Executed insert and got key: " + insertedKeyValue);
    			return insertedKeyValue;
			}
		} catch (SQLException e) {
			Log.exception("Queries.executeInsert SQLException: ",e);
			e.printStackTrace();
			return -1;
        }
	}

	public int executeUpdate(String query) {
		checkConnection();
        try (Statement stmt = con.createStatement()) {
			return stmt.executeUpdate(query);
		} catch (SQLException e) {
			Log.error("Queries.executeUpdate query="+query+" SQLException: "+e);
				e.printStackTrace();
			return 0;
        }
	}

	public int executeUpdate(PreparedStatement ps) {
		checkConnection();
		try {
			return ps.executeUpdate();
		} catch (SQLException e) {
			Log.exception("Queries.executeUpdate SQLException: ",e);
			e.printStackTrace();
			return 0;
		}
	}
	
    protected void checkConnection() {
		try {
			if (con == null || con.isClosed()) {
				con = initConnection();
			}
		} catch (SQLException e) {
			Log.error("Queries.checkConnection  SQLException: "+e);
					e.printStackTrace();
		}
	}
	
	/**
     * Runs a select statement to make sure we can still talk to the
     * database.
     */
    protected void ping() {
        Log.debug("ContentDatabase: ping");
        try (Statement stmt = con.createStatement()) {
            String sql = "SELECT 1 from arena_categories";
            stmt.executeQuery(sql);
        } catch (Exception e) {
            reconnect();
        }
    }
    
    /**
     * Reestablish contact with the database, or throw an error if we
     * can't.
     */
    void reconnect() {
        // looks like the database connection went away, re-establish
        Log.info("ContentDatabase reconnect: url=" + Engine.getDBUrl());

        int failCount=0;
        try {
            while (true) {
                try {
                    con = initConnection();
                    Log.info("Database: reconnected to "+Engine.getDBUrl());
                    return;
                } catch (Exception e) {
                    try {
                        if (failCount == 0)
                            Log.exception("Database reconnect failed, retrying",e);
                        else if (failCount % 300 == 299)
                            Log.error("Database reconnect failed, retrying: "+e);
                        failCount++;
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        /* ignore */
                    }
                }
            }
        } finally {
        }
    }
	
	class KeepAlive implements Runnable {
	    
	    private final Runnable ping;
	    
        KeepAlive(Runnable ping) {
            this.ping = ping;
        }
        
        public void run() {
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Log.exception("AdminDatabase.KeepAlive: interrupted", e);
                }
                try {
                    if (con != null) {
                    	ping.run();
                    }
                } catch (AORuntimeException e) {
                    Log.exception("AdminDatabase.KeepAlive: ping caught exception", e);
                }
            }
        }
    }
}
