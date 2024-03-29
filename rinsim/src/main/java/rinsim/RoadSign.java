package rinsim;

public class RoadSign implements Comparable<RoadSign>{
	private Station endStation = null;
	private double strength = 1.0d;
	private int hops = 5;
	

	public RoadSign() {}

	@Override
	public int compareTo(RoadSign rs) {
		if(this.getStrength() > rs.getStrength())
			return 1;
		else if(this.getStrength() < rs.getStrength())
			return -1;
		else
			return 0;
	}

	/**
	 * Getters and setters.
	 */
	
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
	
	public Station getEndStation() {
		return endStation;
	}

	public void setEndStation(Station endStation) {
		this.endStation = endStation;
	}

}
