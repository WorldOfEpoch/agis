package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

import atavism.server.engine.OID;

public class Auction implements Serializable {
	protected int id;
	protected int currency;
	protected long startBid=-1;
	protected long bid=-1;
	protected long buyout=-1;
	protected long auctioneer = 0;
	protected OID ownerOid;
	protected OID bidderOid;
	protected OID itemOid;
	protected int itemCount=1;
	protected int itemTemplateId=-1;
	protected int itemEnchanteLevel=0;
	protected int mode=0;//0:SELL, 1:ORDER
	protected int status=0; //1:ACTIVE, 2:SUCCESSFUL, 3:EXPIRED, 4:received owner, 5: Canceled 6: received buyer 7:received owner & buyer
	protected Date expirateDate;
	protected ArrayList<Integer> races;
	HashMap<Integer,SocketInfo> itemSockets = new HashMap<Integer,SocketInfo>();
	protected String itemName = "";
	
	public Auction() {
	}
	public Auction(int id) {
		this.id = id;
	}
	
	public int GetId() {
		return id;
	}
	public void SetId(int v) {
		this.id = v;
	}
	
	public void SetCurrency(int  v){
		this.currency = v;
	}
	public int GetCurrency(){
		return currency;
	}
	
	public void SetStartBid(long  v){
		this.startBid = v;
	}
	public long GetStartBid(){
		return startBid;
	}
	
	public void SetBid(long  v){
		this.bid = v;
	}
	public long GetBid(){
		return bid;
	}
	
	public void SetBuyout(long  v){
		this.buyout = v;
	}
	public long GetBuyout(){
		return buyout;
	}
	
	public void SetAuctioneer(long  v){
		this.auctioneer = v;
	}
	public long GetAuctioneer(){
		return auctioneer;
	}
	public void SetOwnerOid(OID  v){
		this.ownerOid = v;
	}
	public OID GetOwnerOid(){
		return ownerOid;
	}
	public void SetBidderOid(OID  v){
		this.bidderOid = v;
	}
	public OID GetBidderOid(){
		return bidderOid;
	}
	
	public void SetItemOid(OID  v){
		this.itemOid = v;
	}
	public OID GetItemOid(){
		return itemOid;
	}
	public void SetItemCount(int  v){
		this.itemCount = v;
	}
	public int GetItemCount(){
		return itemCount;
	}
	public void SetItemTemplateId(int  v){
		this.itemTemplateId = v;
	}
	public int GetItemTemplateId(){
		return itemTemplateId;
	}
	public void SetItemEnchanteLevel(int  v){
		this.itemEnchanteLevel = v;
	}
	public int GetItemEnchanteLevel(){
		return itemEnchanteLevel;
	}
	
	public void SetMode(int  v){
		this.mode = v;
	}
	public int GetMode(){
		return mode;
	}
	public void SetStatus(int  v){
		this.status = v;
	}
	public int GetStatus(){
		return status;
	}
	
	public void SetExpirateDate(Date  v){
		this.expirateDate = v;
	}
	public Date GetExpirateDate(){
		return expirateDate;
	}
	
	public void SetItemName(String  v){
		this.itemName = v;
	}
	public String GetItemName(){
		return itemName;
	}
	
	public void SetRaces(ArrayList<Integer>  v){
		this.races = v;
	}
	public ArrayList<Integer> GetRaces(){
		return races;
	}
	
	public void SetItemSockets(HashMap<Integer,SocketInfo> v){
		this.itemSockets = v;
	}
	public HashMap<Integer,SocketInfo> GetItemSockets(){
		return itemSockets;
	}
	
	 public String toString() {
	        return "[Auction: id:" + GetId() + "; status:" + GetStatus() +"; mode:"+GetMode()+"; itemOid=" + GetItemOid() +"; ItemName:"+GetItemName()+ "; EnchantLevel:"+GetItemEnchanteLevel()
	        +"; ItemCount:"+GetItemCount()+ "; sockets:"+GetItemSockets()+"; ExpirateDate:"+GetExpirateDate()+"]";
	    }

	/*for (int ii=0;ii<numberSockets;ii++) {
		itemSockets.put(itemSockets.size(),new SocketInfo(itemSockets.size(),type));
	} */
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}

class SortByCount implements Comparator<Auction> 
{ 
    // Used for sorting in ascending order of 
    // roll number 
    public int compare(Auction a, Auction b) 
    { 
        return a.GetItemCount() - b.GetItemCount(); 
    } 
} 

class SortByBuyout implements Comparator<Auction> 
{ 
    // Used for sorting in ascending order of 
    // roll number 
    public int compare(Auction a, Auction b) 
    { 
        return (a.GetBuyout() < b.GetBuyout())? -1:
        	(a.GetBuyout() > b.GetBuyout())? 1 : 0; 
    } 
} 




