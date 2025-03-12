package atavism.agis.objects;

import java.io.*;
import java.util.ArrayList;

import atavism.server.engine.OID;

public class Mail implements Serializable {
    public Mail() {
    }
    
    public Mail(int id, boolean isAccountMail, OID recipientOID, String recipientName, OID senderOID, String senderName, String subject,
    		String message, int currencyType, long currencyAmount, ArrayList<OID> items, int category, boolean CoD) {
    	this.id = id;
    	this.isAccountMail = isAccountMail;
    	this.recipientOID = recipientOID;
    	this.recipientName = recipientName;
    	this.senderOID = senderOID;
    	this.senderName = senderName;
    	this.subject = subject;
    	this.message = message;
    	this.currencyType = currencyType;
    	this.currencyAmount = currencyAmount;
    	this.items = items;
    	this.CoD = CoD;
    	mailRead = false;
    	mailArchive = false;
    }
    
    public void addItem(OID itemOid, int pos) {
    	if (pos < items.size())
    		items.add(pos, itemOid);
    }
    
    public void itemTaken(int pos) {
    	items.set(pos, null);
    }
    
    public String toString() {
    	return"[Mail: "+id+" isAccountMail="+isAccountMail+" recipientOID="+recipientOID+" recipientName="+recipientName+" senderOID="+senderOID+" senderName="+senderName+
    			" subject="+subject+" currencyType="+currencyType+" currencyAmount="+currencyAmount+" items="+items+"]";
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getRecipientName() { return recipientName;}
    public void setRecipientName(String recipientName) {
    	this.recipientName = recipientName;
    }
    
    public boolean isAccountMail() { return isAccountMail;}
    public void isAccountMail(boolean isAccountMail) {
    	this.isAccountMail = isAccountMail;
    }
    
    public OID getRecipientOID() { return recipientOID;}
    public void setRecipientOID(OID recipientOID) {
    	this.recipientOID = recipientOID;
    }
    
    public String getSenderName() { return senderName;}
    public void setSenderName(String senderName) {
    	this.senderName = senderName;
    }
    
    public OID getSenderOID() { return senderOID;}
    public void setSenderOID(OID senderOID) {
    	this.senderOID = senderOID;
    }
    
    public String getSubject() { return subject;}
    public void setSubject(String subject) {
    	this.subject = subject;
    }
    
    public String getMessage() { return message;}
    public void setMessage(String message) {
    	this.message = message;
    }
    
    public int getCurrencyType() { return currencyType;}
    public void setCurrencyType(int currencyType) {
    	this.currencyType = currencyType;
    }
    
    public long getCurrencyAmount() { return currencyAmount;}
    public void setCurrencyAmount(long currencyAmount) {
    	this.currencyAmount = currencyAmount;
    }
    public void setCurrencyAmount(Integer currencyAmount) {
    	this.currencyAmount = (long)currencyAmount;
    }
    
    public ArrayList<OID> getItems() { return items;}
    public void setItems(ArrayList<OID> items) {
    	this.items = items;
    }
    
    public int getItemCategory() { return itemCategory;}
    public void setItemCategory(int itemCategory) {
    	this.itemCategory = itemCategory;
    }
    
    public boolean getCoD() { return CoD;}
    public void setCoD(boolean CoD) {
    	this.CoD = CoD;
    }
    
    public boolean getMailRead() { return mailRead;}
    public void setMailRead(boolean mailRead) {
    	this.mailRead = mailRead;
    }
    
    public boolean getMailArchive() { return mailArchive;}
    public void setMailArchive(boolean mailArchive) {
    	this.mailArchive = mailArchive;
    }

    int id;
    String recipientName;
    boolean isAccountMail;
    OID recipientOID;
    String senderName;
    OID senderOID;
    String subject;
    String message;
    int currencyType;
    long currencyAmount;
    ArrayList<OID> items;
    int itemCategory;
    boolean CoD;
    boolean mailRead;
    boolean mailArchive;

    private static final long serialVersionUID = 1L;
}
