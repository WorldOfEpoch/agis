package atavism.agis.util;

import java.util.*;

import atavism.server.objects.RemoteAccountConnector;
import atavism.server.util.Log;

/**
 * Account Connector class that is incomplete. Needs Database connection code added.
 * @author Andrew
 *
 */
public class RemoteDatabaseAccountConnector extends RemoteAccountConnector {
    public RemoteDatabaseAccountConnector() {
        super();
    }

	@Override
	public AccountLoginStatus verifyAccount(String accountName, String password, HashMap<String, Object> props) {
		// TODO Auto-generated method stub
		Log.debug("CONNECTOR: verifying account with remote database");
		return AccountLoginStatus.Success;
	}

	@Override
	public AccountLoginStatus createAccount(String accountName, String email,
			String password, HashMap<String, Object> props) {
		// TODO Auto-generated method stub
		return AccountLoginStatus.NoAccess;
	}
}
