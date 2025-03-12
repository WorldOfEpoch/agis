package atavism.agis.objects;

import java.io.*;
import java.util.*;

/**
 * The ClaimProfile class stores all the information needed about a limits
 * building object.
 * 
 */
public class ClaimProfile implements Serializable {
	protected int id;
	protected String name;
	protected HashMap<Integer, Integer> limits = new HashMap<Integer, Integer>();

	public ClaimProfile() {

	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public HashMap<Integer, Integer> getLimits() {
		return limits;
	}

	public Integer getLimit(Integer category) {
		if(!limits.containsKey(category))
			return -1;
		return limits.get(category);
	}

	public void setLimits(HashMap<Integer, Integer> limits) {
		this.limits = limits;
	}

	public void addLimit(Integer category, Integer limit) {
		this.limits.put(category, limit);
	}

	private static final long serialVersionUID = 1L;
}

