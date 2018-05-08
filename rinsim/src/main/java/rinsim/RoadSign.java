package rinsim;

public class RoadSign implements Comparable<RoadSign>{
	private Station endStation;
	private double strength;
	private int hops;
	

	public RoadSign() {
		setStrength(1.0);
		setHops(5);
		setEndStation(null);
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

	@Override
	public int compareTo(RoadSign rs) {
		if(this.getStrength() > rs.getStrength())
			return 1;
		else if(this.getStrength() < rs.getStrength())
			return -1;
		else
			return 0;
	}

	public Station getEndStation() {
		return endStation;
	}

	public void setEndStation(Station endStation) {
		this.endStation = endStation;
	}

}
