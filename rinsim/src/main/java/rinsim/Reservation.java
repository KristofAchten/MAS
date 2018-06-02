package rinsim;

import com.github.rinde.rinsim.util.TimeWindow;

public class Reservation {
	
	private Station prevStation;
	private Station station;
	private TimeWindow time;
	private long expirationTime;
	private Pod pod;
	
	public Reservation(Station station, Station prevStation, TimeWindow time, Pod pod, long expirationTime) {
		this.setStation(station);
		this.setPrevStation(prevStation);
		this.setTime(time);
		this.setPod(pod);
		this.setExpirationTime(expirationTime);
	}

	public Station getStation() {
		return station;
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public TimeWindow getTime() {
		return time;
	}

	public void setTime(TimeWindow time) {
		this.time = time;
	}

	public Pod getPod() {
		return pod;
	}

	public void setPod(Pod pod) {
		this.pod = pod;
	}

	public Station getPrevStation() {
		return prevStation;
	}

	public void setPrevStation(Station prevStation) {
		this.prevStation = prevStation;
	}

	public long getExpirationTime() {
		return expirationTime;
	}

	public void setExpirationTime(long expirationTime) {
		this.expirationTime = expirationTime;
	}
}
