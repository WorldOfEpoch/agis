package atavism.agis.objects;

import java.io.*;

public class AuctionProfile implements Serializable {
	public long id;
	public String name;
	public int cost_price_value;
	public float cost_price_percentage;
	public int currency;
	public int duration;
	public int display_limit;
	public int own_limit;
	public int start_price_value;
	public float start_price_percentage;
	
	public String toString() {
		return "[AuctionProfile: id="+id+" name="+name+"]";
	}
	private static final long serialVersionUID = 1L;
}