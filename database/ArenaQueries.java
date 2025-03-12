package atavism.agis.database;

import java.io.*;
import java.util.Properties;

import atavism.server.engine.Engine;
import atavism.server.util.Log;

public class ArenaQueries extends Queries {

	public ArenaQueries() {
        super(true);
    }

    protected boolean loadStrings() {
		Properties p = new Properties();
		FileReader r;
		DATABASEDRIVER = Engine.getProperty("atavism.db_driver");
		DBTIMEZONE = Engine.getProperty("atavism.db_timezone");
		try {
			r = new FileReader("arena.ini");
		} catch (FileNotFoundException e) {
			System.out.println("ARENADB: File arena.ini could not be found, "
					+ "using default connection strings");
			PORT = 3306;
			USERNAME = "root";
			PASSWORD = "test";
			DATABASENAME = "admin";
			IPADDRESS =  "127.0.0.1";
			return true;
		}
		BufferedReader b = new BufferedReader(r);
		try {
			p.load(b);
		} catch (IOException e) {
			System.out.println("ARENADB: File arena.ini could not be read, "
					+ "using default connection strings");
			PORT = 3306;
			USERNAME = "root";
			PASSWORD = "test";
			DATABASENAME = "admin";
			IPADDRESS = "127.0.0.1";
			return true;
		}
		try {
			PORT = Integer.parseInt(p.getProperty("port"));
		} catch (NumberFormatException e) {
			PORT = 3306;
		}
		IPADDRESS = p.getProperty("ip");
		USERNAME = p.getProperty("username");
		PASSWORD = p.getProperty("password");
		DATABASENAME = p.getProperty("database");
		System.out.println("ARENADB: File arena.ini should have been successfully read");
		return true;
	}
	
	public void close() {
		Log.dumpStack("ArenaDababase.Close");
		super.close();
	}

}
