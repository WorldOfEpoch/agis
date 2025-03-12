package atavism.agis.util;

import atavism.server.engine.MasterServer;
import atavism.server.objects.RemoteAccountConnector;
import atavism.server.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends login details to a Unity Game Services to verify their account. Called by the MasterServer if
 * set atavism.ugs.project_id in the auth.property script.
 * @author Rafal Dorobisz
 *
 */
public class RemoteUgsAccountConnector extends RemoteAccountConnector {
	public RemoteUgsAccountConnector() {
		super();
		allowAccountCreation = true;
	}

	@Override
	public AccountLoginStatus verifyAccount(String accountName, String password, HashMap<String, Object> props) {
		Log.debug("UGS CONNECTOR: verifying account with UGS connection");
		try {

			String data = "{\"username\": \"" + accountName + "\",\"password\": \"" + password + "\"}";
			String url = "https://player-auth.services.api.unity.com/v1/authentication/usernamepassword/sign-in";

			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("POST");
			//add request header
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("ProjectId", MasterServer.getUGSProjectId());
			if (Log.loggingTrace) {
				Map heder1 = con.getRequestProperties();
				System.out.println(heder1);
				System.out.println(data);
			}
			con.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
			wr.write(data);
			wr.flush();
			//System.out.println(con.getRequestProperties());
			int r_code = con.getResponseCode();
			if (Log.loggingTrace) {
				System.out.println(con.getResponseCode());
				System.out.println(con.getResponseMessage());
				Map heder = con.getHeaderFields();
				System.out.println(heder);
			}
			if (Log.loggingDebug) Log.debug("UGS CONNECTOR: verifyAccount Response from UGS: Code = " + r_code);
			StringBuffer response = new StringBuffer();
			if (r_code != HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
				String inputLine;

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				System.out.println(response);
				con.disconnect();
				return AccountLoginStatus.InvalidPassword;
			} else {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();

			}
			if (Log.loggingTrace) {
				System.out.println(response);
			}
			//in.close();
			if (Log.loggingDebug) Log.error("UGS CONNECTOR: Response from UGS: account id = " + response.toString());
			GsonBuilder builder = new GsonBuilder();
			builder.setPrettyPrinting();
			Gson gson = builder.create();
			ResponseUgsSignInUp res = gson.fromJson(response.toString(), ResponseUgsSignInUp.class);

			accountExternalID = res.getUserId();

			return AccountLoginStatus.Success;


		} catch (java.io.FileNotFoundException e) {
			Log.error("UGS CONNECTOR: exception thrown by UGS connector verify account " + e.getMessage());
			Log.exception("UGS CONNECTOR: verifyAccount Exception", e);
			e.printStackTrace();

		} catch (Exception e) {
			Log.error("UGS CONNECTOR: exception thrown by UGS connector verify account " + e.getMessage());
			Log.exception("UGS CONNECTOR: verifyAccount Exception", e);
			e.printStackTrace();

		}

		return AccountLoginStatus.ServerError;
	}

	@Override
	public AccountLoginStatus createAccount(String accountName, String email,
											String password, HashMap<String, Object> props) {
		Log.debug("UGS CONNECTOR: create account with UGS connection");
		// TODO Auto-generated method stub

		try {
			String data = "{\"username\": \"" + accountName + "\",\"password\": \"" + password + "\"}";
			String url = "https://player-auth.services.api.unity.com/v1/authentication/usernamepassword/sign-up";

			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("POST");
			//add request header
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("ProjectId", MasterServer.getUGSProjectId());
			//con.setRequestProperty("Authorization", "Bearer " + token);
			if (Log.loggingTrace) {
				Map heder1 = con.getRequestProperties();
				System.out.println(heder1);
				System.out.println(data);
			}
			con.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
			wr.write(data);
			wr.flush();
			int r_code = con.getResponseCode();
			if (Log.loggingTrace) {
				System.out.println(con.getResponseCode());
				System.out.println(con.getResponseMessage());
				Map heder = con.getHeaderFields();
				System.out.println(heder);
			}
			if (Log.loggingDebug) Log.debug("UGS CONNECTOR: create account Response from UGS: Code = " + r_code);
			StringBuffer response = new StringBuffer();
			if (r_code != HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				System.out.println(response);
				con.disconnect();
				return AccountLoginStatus.InvalidPassword;
			} else {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
			}

			if (Log.loggingTrace) System.out.println(response);
			if (Log.loggingDebug)
				Log.debug("UGS CONNECTOR: createAccount Response from UGS: account id = " + response.toString());
			GsonBuilder builder = new GsonBuilder();
			Gson gson = builder.create();
			ResponseUgsSignInUp res = gson.fromJson(response.toString(), ResponseUgsSignInUp.class);
			con.disconnect();
			accountExternalID = res.getUserId();

			return AccountLoginStatus.Success;

		} catch (Exception e) {
			Log.exception("UGS CONNECTOR: createAccount Exception", e);
			e.printStackTrace();
		}
		return AccountLoginStatus.NoAccess;
	}


	public class ResponseUgsSignInUp {
		public ResponseUgsSignInUp() {
		}

		private int expiresIn;
		private String idToken;
		private String sessionToken;
		private Player user;
		private String userId;

		public int getExpiresIn() {
			return expiresIn;
		}

		public void setExpiresIn(int expiresIn) {
			this.expiresIn = expiresIn;
		}

		public String getIdToken() {
			return idToken;
		}

		public void setIdToken(String idToken) {
			this.idToken = idToken;
		}

		public String getSessionToken() {
			return sessionToken;
		}

		public void setSessionToken(String sessionToken) {
			this.sessionToken = sessionToken;
		}

		public Player getUser() {
			return user;
		}

		public void setUser(Player user) {
			this.user = user;
		}

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}
	}

	public class Player {
		public Player() {
		}

		private boolean disabled = false;
		private ExternalIds[] externalIds;
		private String id;
		private String username;

		public boolean getDisabled() {
			return disabled;
		}

		public void setDisabled(boolean disabled) {
			this.disabled = disabled;
		}

		public ExternalIds[] getEexternalIds() {
			return externalIds;
		}

		public void setExternalIds(ExternalIds[] externalIds) {
			this.externalIds = externalIds;
		}


		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}


		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

	}

	public class ExternalIds {
		private String externalId;
		private String providerId;

		public ExternalIds() {
		}

		public String getexternalId() {
			return externalId;
		}

		public void setexternalId(String externalId) {
			this.externalId = externalId;
		}

		public String getProviderId() {
			return providerId;
		}

		public void setProviderId(String providerId) {
			this.providerId = providerId;
		}

	}
}