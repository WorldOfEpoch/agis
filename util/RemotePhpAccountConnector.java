package atavism.agis.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

import atavism.server.objects.RemoteAccountConnector;
import atavism.server.util.Log;

/**
 * Sends login details to a php file to verify their account. Called by the MasterServer if 
 * set in the auth_server.py script.
 * @author Andrew Harrison
 *
 */
public class RemotePhpAccountConnector extends RemoteAccountConnector {
    public RemotePhpAccountConnector() {
        super();
    }

	@Override
	public AccountLoginStatus verifyAccount(String accountName, String password, HashMap<String, Object> props) {
		Log.debug("CONNECTOR: verifying account with php connection");
		 try {
			 
			  	String data = "user=" + URLEncoder.encode(accountName, "UTF-8") 
	    		+ "&password=" + URLEncoder.encode(password, "UTF-8");
			 	String _url = url;
			  	if (!post) {
			 		_url+="?"+data;
			 	}
			  	URL obj = new URL(_url);
	            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
	            // optional default is GET
	           if(post)
	        	   con.setRequestMethod("POST");
	           else
	        	   con.setRequestMethod("GET");
	              
	            //add request header
	            con.setRequestProperty("User-Agent", "Mozilla/5.0");
			if (post) {
				con.setDoOutput(true);
				OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
				wr.write(data);
				wr.flush();
			}
	            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
	            String inputLine;
	            StringBuffer response = new StringBuffer();
	            while ((inputLine = in.readLine()) != null) {
	                response.append(inputLine);
	            }
	            in.close();
	            Log.debug("CONNECTOR: Response from website: account id = "+response.toString());
	            accountID = Integer.parseInt(response.toString());
	            AccountLoginStatus loginResponse = AccountLoginStatus.ServerError;
	            if(accountID > 0) {
	                loginResponse = AccountLoginStatus.Success;
	            } else {
	                switch (accountID) {
	                    case -1: //Invalid info
	                        loginResponse = AccountLoginStatus.InvalidPassword;
	                        break;
	 
	                    case -2: //Banned
	                        loginResponse = AccountLoginStatus.Banned;
	                        break;
	 
	                    case -3: //Not allowed to login
	                        loginResponse = AccountLoginStatus.NoAccess;
	                        break;
	                        
	                    case -4: // Not allowed to login
	                        loginResponse = AccountLoginStatus.DatabaseError;
	                        break;
	                        
	                    case -5: // Not allowed to login
	                        loginResponse = AccountLoginStatus.SubscriptionExpired;
	                        break;
	                        
	                    default: //Unvalid response from the server
	                        loginResponse = AccountLoginStatus.ServerError;
	                        break;
	                }
	            }
	 
	            return loginResponse;
	        } catch (Exception e) {
	            Log.debug("CONNECTOR: exception thrown by PHP connector verify account "+ e.getMessage());
	        }
	
		return AccountLoginStatus.ServerError;
	}
	
	@Override
	public AccountLoginStatus createAccount(String accountName, String email,
			String password, HashMap<String, Object> props) {
		// TODO Auto-generated method stub
		return AccountLoginStatus.NoAccess;
	}
	
	private String url = "http://yourdomain.com/verifyAccount.php";
	private boolean post = true;
	public void setPost(boolean v) {
		this.post=v;
	}
	public void setUrl(String url) {
		this.url = url;
	}
}