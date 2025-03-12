package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;

import atavism.agis.core.Agis;
import atavism.server.util.Log;

/**
 * Contains the template information for a currency. This object is not stored on the player, it is
 * only used to assist in calculations and conversions.
 * @author Andrew Harrison
 *
 */
public class Currency implements Serializable {
    public Currency() {
    }
    
    public Currency(int id, String name, String icon, String description, int maximum) {
    	this.id = id;
    	this.name = name;
    	this.icon = icon;
    	this.description = description;
    	this.maximumAmount = maximum;
    }

	public Currency(Currency tmpl) {
		this.id = tmpl.id;
		this.name = tmpl.name;
		this.icon = tmpl.icon;
		this.description = tmpl.description;
		this.maximumAmount = tmpl.maximumAmount;
	}
	
	public void addConversionOption(int currencyToID, int amountReq, boolean autoConversion) {
		Log.debug("CURRENCY: added conversion option for currency: " + id + " to: " + currencyToID + " and amountReq: " + amountReq);
		conversionOptions.put(currencyToID, amountReq);
		if (autoConversion)
			autoConversionCurrency = currencyToID;
	}
	
	/*public int getConversionCurrency() {
		if (conversionOptions.size() > 0) {
			return conversionOptions.keySet().iterator().next();
		}
		return -1;
	}*/
	
	/**
	 * Returns the amount of currency required to convert to one of the auto conversion currency.
	 * @return
	 */
	public long getConversionAmountReq() {
		if (conversionOptions.containsKey(autoConversionCurrency)) {
			return conversionOptions.get(autoConversionCurrency);
		} 
		return 0l;
	}
	
	/**
	 * Returns the amount of currency required to convert to one of the specified conversion currency.
	 * @return
	 */
	public long getConversionAmountReq(int currencyID) {
		if (conversionOptions.containsKey(currencyID)) {
			return conversionOptions.get(currencyID);
		} 
		return 0l;
	}
	
	/**
	 * Finds the currency that will auto convert into this currency. Returns null if there are no currencies that do so.
	 * @return
	 */
	public Currency getCurrencyThatConvertsToThis() {
		for (Currency c : Agis.CurrencyManager.values()) {
			if (c.getAutoConversionCurrency() == id) {
				return c;
			}
		}
		return null;
	}

	public int getCurrencyID() { return id;}
    public void setCurrencyID(int id) {
    	this.id = id;
    }
    
    public String getCurrencyName() { return name;}
    public void setCurrencyName(String name) {
    	this.name = name;
    }
    
    public String getCurrencyIcon() { return icon;}
    public void setCurrencyIcon(String icon) {
    	this.icon = icon;
    }
    
    public String getCurrencyDescription() { return description;}
    public void setCurrencyDescription(String description) {
    	this.description = description;
    }
    
    public long getCurrencyMax() { return maximumAmount;}
    public void setCurrencyMax(long maximumAmount) {
    	this.maximumAmount = maximumAmount;
    }
    
    public boolean getExternal() { return external;}
    public void setExternal(boolean external) {
    	this.external = external;
    }
    
    public int getAutoConversionCurrency() { return autoConversionCurrency;}
    public void setAutoConversionCurrency(int autoConversionCurrency) {
    	this.autoConversionCurrency = autoConversionCurrency;
    }
    
    public HashMap<Integer, Integer> getConversionOptions() { return conversionOptions;}
    public void setConversionOptions(HashMap<Integer, Integer> conversionOptions) {
    	this.conversionOptions = conversionOptions;
    }

    int id;
    String name;
    String icon;
    String description;
    long maximumAmount;
    boolean external;
    int autoConversionCurrency = -1;
    HashMap<Integer, Integer> conversionOptions = new HashMap<Integer, Integer>();

    private static final long serialVersionUID = 1L;
}
