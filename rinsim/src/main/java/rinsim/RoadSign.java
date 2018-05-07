package rinsim;

import java.util.ArrayList;

public class RoadSign {
	private ArrayList<Station> previousStations;
	private double strength;
	private int hops;
	

	public RoadSign() {
		setStrength(1.0);
		setHops(5);
		previousStations = new ArrayList<Station>();
	}
	
	public ArrayList<Station> getPreviousStations() {
		return previousStations;
	}

	public void setPreviousStations(ArrayList<Station> previousStations) {
		this.previousStations = previousStations;
	}

	public double getStrength() {
		return strength;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}

	public int getHops() {
		return hops;
	}

	public void setHops(int hops) {
		this.hops = hops;
	}

}
