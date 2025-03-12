package atavism.agis.objects;

import java.io.Serializable;

public class VipData implements Serializable {
	protected long expire =0l;
	protected int level =0;
	protected long points=0l;
	public VipData() {}
	public VipData(int level,long expire, long points) {
		this.expire = expire;
		this.level = level;
		this.points = points;
	}
	
	public void setExpire(long expire){
		this.expire = expire;
	}
	public long getExpire(){
		return expire;
	}

	public void setLevel(int level){
		this.level = level;
	}
	public int getLevel(){
		return level;
	}
	public void setPoints(long points){
		if (points < 0)
			points = 0;
		this.points = points;
	}
	public long getPoints(){
		return points;
	}

	
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
}
