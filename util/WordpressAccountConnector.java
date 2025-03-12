package atavism.agis.util;

import java.util.*;

import atavism.server.objects.RemoteAccountConnector;
import atavism.server.util.Log;

/*import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;*/

/**
 * Test AccountConnector that was not completed. Only use if you can finish the code.
 * @author Andrew
 *
 */
public class WordpressAccountConnector extends RemoteAccountConnector {
    public WordpressAccountConnector() {
        super();
    }

	@Override
	public AccountLoginStatus verifyAccount(String accountName, String password, HashMap<String, Object> props) {
		// TODO Auto-generated method stub
		Log.debug("CONNECTOR: verifying account with wordpress connection");
		try {
			//XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
			//config.setServerURL(new URL(xmlRpcUrl));
			
			//XmlRpcClient client = new XmlRpcClient();
		    //client.setConfig(config);
		    
		    //TODO: work out the correct call to make to wordpress and get a response
		    /*Object[] params = new Object[] {
			        apiKey,
			        1,
			        accountName,
			        password,
			        contents,
			        postType.booleanValue()
			    };
			client.execute(POST_METHOD_NAME, params);*/
		    
		    // put a return here based on the result of the wordpress call
		   
		} catch (Exception e) {
		}
		return AccountLoginStatus.ServerError;
	}
	
	@Override
	public AccountLoginStatus createAccount(String accountName, String email,
			String password, HashMap<String, Object> props) {
		// TODO Auto-generated method stub
		return AccountLoginStatus.NoAccess;
	}
	
	//private String apiKey;
	//private String xmlRpcUrl;
	private String url = "http://yourdomain.com/verifyWordpressAccount.php";
	
	//private XmlRpcClient client;
	
	public void setUrl(String url) {
		this.url = url;
	}
}