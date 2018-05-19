package rinsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.generator.TimeWindows;
import com.github.rinde.rinsim.util.TimeWindow;
import com.sun.jmx.remote.opt.util.GetPropertyAction;

public class Station extends Depot {
	
	private static long RESERVATION_TIME = 20000;
	private static long EXPIRATION_TIME = 20000;
	private static long BUFFER_TIME = 1000;

	
	private ArrayList<Reservation> reservations = new ArrayList<>();
	private ArrayList<RoadSign> roadsigns = new ArrayList<>();
	private ArrayList<Station> neighbours = new ArrayList<>();
	private ArrayList<User> passengers = new ArrayList<>();
	private Pod pod = null;
	private Random rand = new Random();
	private Point position;

	public Station(Point position) {
		super(position);		
		setPosition(position);
		setCapacity(1);
	}
	
	public void receiveExplorationAnt(ArrayList<Station> prev, Station dest, int hop) {
		forwardExploration(prev, dest, hop);
	}
	public void receiveReservationAnt(ArrayList<Reservation> res, long preferredTime, boolean refreshing) {
		makeReservation(res, preferredTime, refreshing);
	}
	public void receiveRoadSignAnt(RoadSign prev) {
		makeRoadsign(prev);
	}
	
	
	
	private void forwardExploration(ArrayList<Station> prev, Station dest, int hop) {
		
		if((hop == 0 && this != dest) || (prev.contains(this) && hop != -1))
			return;
		
		if(this == dest) {
			prev.add(this);
		}

		if(this == dest || hop == -1) {
			int index = prev.indexOf(this);
			if(index == 0) {
				getPod().receiveExplorationResult(prev);
				return;
			}
			prev.get(index-1).receiveExplorationAnt(new ArrayList<Station>(prev), dest, -1);
		} else {
			prev.add(this);
			for(Station s : getNeighbours()) {
				s.receiveExplorationAnt(new ArrayList<Station>(prev), dest, hop - 1);
			}
		}
	}

	public void sendReservationAnt(ArrayList<Reservation> res, long preferredTime, boolean refreshing) {
		Station receiver = res.get(0).getStation();
		receiver.receiveReservationAnt(res, preferredTime, refreshing);
	}
	
	private void makeReservation(ArrayList<Reservation> res, long preferredTime, boolean refreshing) {
		Reservation current = res.remove(0);		
		
		if(refreshing) {
			Reservation toRemove = null;
			for(Reservation r: reservations) {
				if(r.getPod() == current.getPod())
					toRemove = r;
					break;
			}
			reservations.remove(toRemove);
		}

		TimeWindow result = checkPossibleReservationTime(preferredTime);
		this.reservations.add(current);
		current.setTime(result);
		current.setExpirationTime(System.currentTimeMillis() + EXPIRATION_TIME);

		if(res.isEmpty()) {
			ArrayList<Reservation> ret = new ArrayList<>();
			ret.add(current);
			if(current.getPrevStation() != null)
				current.getPrevStation().sendConfirmation(ret);
			else
				current.getStation().getPod().confirmReservations(ret);
		} else {
			sendReservationAnt(res, current.getTime().begin() + BUFFER_TIME, refreshing);	
		}
	}
	
	public void sendConfirmation(ArrayList<Reservation> res) {
		Pod p = res.get(0).getPod();
		Reservation correctReservation = null;
		for(Reservation r : this.reservations) {
			if (r.getPod() == p) {
				correctReservation = r;
			}
		}
		res.add(correctReservation);
		
		//System.out.println(this.getPosition() + "," + getRoadModel().getPosition(correctReservation.getPod()) + "," + this.getPod());
		if(correctReservation.getPod() == getPod()) {
			assert(this.pod == correctReservation.getPod());
			this.pod.confirmReservations(res);
			return;
		}
		correctReservation.getPrevStation().sendConfirmation(res);
	}
	
	private void makeRoadsign(RoadSign previous) {
		RoadSign sign = null;
		int hops = previous.getHops();
		boolean updated = false;
		
		for(RoadSign rs : getRoadsigns()) {
			if(rs.getEndStation() == previous.getEndStation()) {
				rs.setStrength(1);
				if(rs.getHops() < hops) {
					hops = rs.getHops();
				}
				updated  = true;
			}
		}
		
		sign = new RoadSign();
		sign.setHops(hops - 1);
		sign.setEndStation(previous.getEndStation());
		
		
		if(!updated) {
			this.roadsigns.add(sign);
		}

		if(hops >= 0) {
			int  n = rand.nextInt(this.neighbours.size());
			this.neighbours.get(n).receiveRoadSignAnt(sign);
		}
	}
	
	public void refreshReservation(ArrayList<Reservation> res, long preferredTime) {
		Reservation r = res.remove(0);
		for(Reservation r2 : getReservations()) {
			if(r.getPod() == r2.getPod()) {
				r2.setTime(checkPossibleReservationTime(preferredTime));
				r2.setExpirationTime(System.currentTimeMillis() + EXPIRATION_TIME);
				break;
			}
		}
		res.get(0).getStation().refreshReservation(res, preferredTime + BUFFER_TIME);
	}
	
	private TimeWindow checkPossibleReservationTime(long time) {
		TimeWindow ret = TimeWindow.create(time, time + RESERVATION_TIME);
		for(Reservation res : this.reservations)
			if(res.getTime().isIn(time) || res.getTime().isIn(time + RESERVATION_TIME)) {
				time = res.getTime().end();
				ret = checkPossibleReservationTime(time);
			}
		return ret;		
	}

	public void embarkUser(User u) {
		this.passengers.remove(u);
	}
	
	public ArrayList<User> getPassengers() {
		return passengers;
	}

	public void setPassengers(ArrayList<User> passengers) {
		this.passengers = passengers;
	}

	public ArrayList<Reservation> getReservations() {
		return reservations;
	}

	public void setReservations(ArrayList<Reservation> reservations) {
		this.reservations = reservations;
	}

	public ArrayList<RoadSign> getRoadsigns() {
		return roadsigns;
	}

	public void setRoadsigns(ArrayList<RoadSign> roadsigns) {
		this.roadsigns = roadsigns;
	}

	public ArrayList<Station> getNeighbours() {
		return neighbours;
	}

	public void setNeighbours(ArrayList<Station> neighbours) {
		this.neighbours = neighbours;
	}

	public Pod getPod() {
		return pod;
	}

	public void setPod(Pod pod) {
		this.pod = pod;
	}

	public Point getPosition() {
		return position;
	}

	public void setPosition(Point position) {
		this.position = position;
	}	
}
