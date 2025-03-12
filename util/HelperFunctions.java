package atavism.agis.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.util.Log;

/**
 * A collection of useful functions such as string checkers, form senders and file copiers.
 * @author Andrew
 *
 */
public class HelperFunctions {

	/**
	 * Checks whether the passed in string is alphanumeric.
	 * @param s
	 * @return
	 */
	public static boolean isAlphaNumeric(final String s) {
		final char[] chars = s.toCharArray();
		for (int x = 0; x < chars.length; x++) {      
			final char c = chars[x];
			if ((c >= 'a') && (c <= 'z')) continue; // lowercase
			if ((c >= 'A') && (c <= 'Z')) continue; // uppercase
			if ((c >= '0') && (c <= '9')) continue; // numeric
			return false;
		}  
		return true;
	}
	
	/**
	 * Checks whether the passed in string contains only letters, numbers,
	 * and spaces.
	 * @param s
	 * @return
	 */
	public static boolean isAlphaNumericWithSpaces(final String s) {
		final char[] chars = s.toCharArray();
		for (int x = 0; x < chars.length; x++) {      
			final char c = chars[x];
			if ((c >= 'a') && (c <= 'z')) continue; // lowercase
			if ((c >= 'A') && (c <= 'Z')) continue; // uppercase
			if ((c >= '0') && (c <= '9')) continue; // numeric
			if ((c == ' ')) continue; // spaces
			return false;
		}  
		return true;
	}
	
	/**
	 * Checks whether the passed in string contains only letters, numbers,
	 * spaces and apostrophes.
	 * @param s
	 * @return
	 */
	public static boolean isAlphaNumericWithSpacesAndApostrophes(final String s) {
		final char[] chars = s.toCharArray();
		for (int x = 0; x < chars.length; x++) {      
			final char c = chars[x];
			if ((c >= 'a') && (c <= 'z')) continue; // lowercase
			if ((c >= 'A') && (c <= 'Z')) continue; // uppercase
			if ((c >= '0') && (c <= '9')) continue; // numeric
			if ((c == ' ')) continue; // spaces
			if ((c == '\'')) continue; // apostrophes
			return false;
		}  
		return true;
	}
	
	/**
	 * Converts a UTF-8 encoded string presented as an array of bytes into a String object.
	 * Needed to read in UTF-8 encoded strings from MySQL.
	 * @param bytes
	 * @return
	 */
	public static String readEncodedString(byte[] bytes) {
		if (bytes != null) {
			try {
				return new String(bytes, "UTF-8");
			} catch (UnsupportedEncodingException e) {
			}
		}
		return null;
	}
	
	/**
	 * Sends out an HTML form with the data provided to the file provided. Used to copy the
	 * template files to a new island folder when a player creates a new island.
	 * @param url
	 * @param data
	 */
	public static void sendHtmlForm(String url, HashMap<String, String> data)
	  {
	    try
	    {
	      URL siteUrl = new URL( url );
	 
	      HttpURLConnection hConnection = (HttpURLConnection)
	                             siteUrl.openConnection();
	      HttpURLConnection.setFollowRedirects( true );
	 
	      hConnection.setDoOutput( true );
	      hConnection.setDoInput( true );
	      hConnection.setRequestMethod("POST");	
	 
	      /*PrintStream ps = new PrintStream( hConnection.getOutputStream() );
	      ps.print("template=" + template + "&amp;island_name= " + islandName);
	      ps.close();*/
	      DataOutputStream out = new DataOutputStream(hConnection.getOutputStream());
	      Set<String> keys = data.keySet();
	      Iterator<String> keyIter = keys.iterator();
	      String content = "";
	      for (int i = 0; keyIter.hasNext(); i++)
	      {
	    	  Object key = keyIter.next();
	    	  if (i != 0)
	    		  content += "&";
	    	  content += key + "=" + URLEncoder.encode(data.get(key), "UTF-8");
	      }
	      Log.debug("Sending html form with content: " + content + " to URL: " + url);
	      System.out.println(content);
	      out.writeBytes(content);
	      out.flush();
	      out.close();
	 
	      hConnection.connect();
	 
	      if( HttpURLConnection.HTTP_OK == hConnection.getResponseCode() )
	      {
	        InputStream is = hConnection.getInputStream();
	        OutputStream os = new FileOutputStream("output.html");
	        int dataBytes;
	        while((dataBytes=is.read()) != -1)
	        {
	          os.write(dataBytes);
	        }
	        is.close();
	        os.close();
	        hConnection.disconnect();
	      }
	    }
	    catch(Exception ex)
	    {
	      ex.printStackTrace();
	    }
	  }
	
	/**
	 * Copies the templates files into a new folder for the island a player is creating. 
	 * @param templateName
	 * @param islandName
	 * @return
	 */
	public static boolean CopyTemplateFiles(String templateName, String islandName)
	  {
		try{
			String source = "../island_templates/" + templateName;
			String target = "../config/iow/" + islandName;
			// Create one directory
			new File(target).mkdir();
			// Read files in source folder
			File dir = new File(source);
			String[] children = dir.list();
			for (int i=0; i<children.length; i++) {
		        // Get filename of file or directory
		        String filename = children[i];
		        String newFilename = filename.replace(templateName, islandName);
		        Log.debug("Copying file: " + filename + " to: " + newFilename);
		        File fromFile = new File(source + "/" + filename);
		        File toFile = new File(target + "/" + newFilename);
		        FileReader in = new FileReader(fromFile);
		        FileWriter out = new FileWriter(toFile);
		        int c;

		        while ((c = in.read()) != -1)
		          out.write(c);

		        in.close();
		        out.close();
		        // Modify file contents if it is a aow or mmf file
		        if (filename.contains(".aow") || filename.contains(".mmf"))
		        {
		        	if (!ModifyWorldFile(templateName, islandName, target + "/" + newFilename))
		        		return false;
		        }
		    }
		  
		}catch (Exception e){//Catch exception if any
			return false;
		}
		return true;
	}
	
	private static boolean ModifyWorldFile(String templateName, String islandName, String fileName)
	{
		try {
			File file = new File(fileName);
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line = "", oldtext = "";
	        while((line = reader.readLine()) != null)
	        {
	            oldtext += line + "\r\n";
	        }
	        reader.close();
	        // replace a word in a file
	        String newtext = oldtext.replaceAll(templateName, islandName);
	        FileWriter writer = new FileWriter(fileName);
            writer.write(newtext);
            writer.close();
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
        
		return true;
	}

	public static Point stringToPoint(String worldspace) {
	    // Split the worldspace string into Point and Quaternion parts
	    String[] parts = worldspace.split("\\),\\(");
	    if (parts.length != 2) {
	        throw new IllegalArgumentException("Invalid worldspace format. Expected format: (x,y,z),(x,y,z,w)");
	    }

	    // Extract and split the Point part
	    String pointPart = parts[0].trim().replaceAll("[()]", "");
	    String[] pointValues = pointPart.split(",");
	    if (pointValues.length != 3) {
	        throw new IllegalArgumentException("Invalid format for Point. Expected format: x,y,z");
	    }

	    // Parse the string parts to float values
	    float x = Float.parseFloat(pointValues[0].trim());
	    float y = Float.parseFloat(pointValues[1].trim());
	    float z = Float.parseFloat(pointValues[2].trim());

	    // Create and return the Point object
	    return new Point(x, y, z);
	}

	public static Quaternion stringToQuaternion(String worldspace) {
	    // Split the worldspace string into Point and Quaternion parts
	    String[] parts = worldspace.split("\\),\\(");
	    if (parts.length != 2) {
	        throw new IllegalArgumentException("Invalid worldspace format. Expected format: (x,y,z),(x,y,z,w)");
	    }

	    // Extract and split the Quaternion part
	    String quatPart = parts[1].trim().replaceAll("[()]", "");
	    String[] quatValues = quatPart.split(",");
	    if (quatValues.length != 4) {
	        throw new IllegalArgumentException("Invalid format for Quaternion. Expected format: x,y,z,w");
	    }

	    // Parse the string parts to float values
	    float x = Float.parseFloat(quatValues[0].trim());
	    float y = Float.parseFloat(quatValues[1].trim());
	    float z = Float.parseFloat(quatValues[2].trim());
	    float w = Float.parseFloat(quatValues[3].trim());

	    // Create and return the Quaternion object
	    return new Quaternion(x, y, z, w);
	}

}