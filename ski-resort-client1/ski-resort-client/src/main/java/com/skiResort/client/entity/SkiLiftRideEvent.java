package com.skiResort.client.entity;

import lombok.Data;

@Data
public class SkiLiftRideEvent {

	private long skierID;
	private long resortID;
	private int liftID;
	private int seasonID;
	private int dayID;
	private int time;
	
	public long getSkierID() {
		return skierID;
	}
	public void setSkierID(long skierID) {
		this.skierID = skierID;
	}
	public long getResortID() {
		return resortID;
	}
	public void setResortID(long resortID) {
		this.resortID = resortID;
	}
	public int getLiftID() {
		return liftID;
	}
	public void setLiftID(int liftID) {
		this.liftID = liftID;
	}
	public int getSeasonID() {
		return seasonID;
	}
	public void setSeasonID(int seasonID) {
		this.seasonID = seasonID;
	}
	public int getDayID() {
		return dayID;
	}
	public void setDayID(int dayID) {
		this.dayID = dayID;
	}
	public int getTime() {
		return time;
	}
	public void setTime(int time) {
		this.time = time;
	}
	
}
