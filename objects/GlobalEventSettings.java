package atavism.agis.objects;

import java.io.*;
import java.util.*;


public class GlobalEventSettings implements Serializable {
	public GlobalEventSettings() {
	}

	public String toString() {
		return "[GlobalEventSettings: id=" + id + " name=" + name + ";  start=" + start_year + "/" + start_month + "/" + start_day + " " + start_hour + ":" + start_minute + "; end=" + end_year + "/" + end_month + "/"
				+ end_day + " " + end_hour + ":" + end_minute + "; bonuses=" + bonuses + "; icon=" + icon + "]";
	}

	protected int id = -1;
	protected Integer start_year = -1;
	protected Integer start_month = -1;
	protected Integer start_day = -1;
	protected Integer start_hour = 0;
	protected Integer start_minute = 0;
	protected Integer end_year = -1;
	protected Integer end_month = -1;
	protected Integer end_day = -1;
	protected Integer end_hour = 0;
	protected Integer end_minute = 0;
	protected ArrayList<BonusSettings> bonuses = new ArrayList<BonusSettings>();
	protected String name = "";
	protected String description = "";
	protected String icon = "";
	protected String iconData = "";

	public void setBonuses(ArrayList<BonusSettings> bonuses) {
		this.bonuses = bonuses;
	}

	public ArrayList<BonusSettings> getBonuses() {
		return bonuses;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getStartYear() {
		return start_year;
	}

	public void setStartYear(Integer start_year) {
		this.start_year = start_year;
	}

	public Integer getStartMonth() {
		return start_month;
	}

	public void setStartMonth(Integer start_month) {
		this.start_month = start_month;
	}

	public Integer getStartDay() {
		return start_day;
	}

	public void setStartDay(Integer start_day) {
		this.start_day = start_day;
	}

	public Integer getStartHour() {
		return start_hour;
	}

	public void setStartHour(Integer start_hour) {
		this.start_hour = start_hour;
	}

	public Integer getStartMinute() {
		return start_minute;
	}

	public void setStartMinute(Integer start_minute) {
		this.start_minute = start_minute;
	}

	public Integer getEndYear() {
		return end_year;
	}

	public void setEndYear(Integer end_year) {
		this.end_year = end_year;
	}

	public Integer getEndMonth() {
		return end_month;
	}

	public void setEndMonth(Integer end_month) {
		this.end_month = end_month;
	}

	public Integer getEndDay() {
		return end_day;
	}

	public void setEndDay(Integer end_day) {
		this.end_day = end_day;
	}

	public Integer getEndHour() {
		return end_hour;
	}

	public void setEndHour(Integer end_hour) {
		this.end_hour = end_hour;
	}

	public Integer getEndMinute() {
		return end_minute;
	}

	public void setEndMinute(Integer end_minute) {
		this.end_minute = end_minute;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public String getIconData() {
		return iconData;
	}

	public void setIconData(String iconData) {
		this.iconData = iconData;
	}

	private static final long serialVersionUID = 1L;
}
