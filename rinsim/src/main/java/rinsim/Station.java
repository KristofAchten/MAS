package rinsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.generator.TimeWindows;
import com.github.rinde.rinsim.util.TimeWindow;

public class Station extends Depot {
	
	private static long RESERVATION_TIME = 300000;
	private static long EXPIRATION_TIME = 300000;

	
	private ArrayList<Reservation> reservations = new ArrayList<>();
	private ArrayList<RoadSign> roadsigns = new ArrayList<>();
	private ArrayList<Station> neighbours = new ArrayList<>();
	private Pod pod = null;

	public Station(Point position) {
		super(position);
		setCapacity(1);
	}
	
	public void receiveAnt(String type, Station sender, long preferredTime, ArrayList<Reservation> res) {
		switch(type) {
		case "reservation":
			makeReservation(res, preferredTime);
			break;
		case "roadsign":
			makeRoadsign(sender);
			break;
		default:
		}
		
	}
	
	public void sendReservationAnt(ArrayList<Reservation> res, long preferredTime) {
		Station receiver = res.get(0).getStation();
		receiver.receiveAnt("reservation", this, preferredTime, res);
	}
	
	public void sendRoadsignAnt() {
		
	}
	
	private void makeReservation(ArrayList<Reservation> res, long preferredTime) {
		Reservation current = res.remove(0);
		Reservation existingReservation = null;
		
		for(Reservation r : this.reservations) {
			if(r.getPod() == current.getPod()) {
				existingReservation = r;
				break;
			}
		}
		
		if(existingReservation == null) {
			TimeWindow result = checkPossibleReservationTime(preferredTime);
			current.setTime(result);
			this.reservations.add(current);
		} else {
			current = existingReservation;
		}

		current.setExpirationTime(System.currentTimeMillis() + EXPIRATION_TIME);

		if(res.isEmpty()) {
			ArrayList<Reservation> ret = new ArrayList<>();
			ret.add(current);
			current.getPrevStation().sendConfirmation(ret);
		} else {
			sendReservationAnt(res, current.getTime().end());	
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
		
		if(correctReservation.getPrevStation() == null) {
			assert(this.pod == correctReservation.getPod());
			this.pod.confirmReservations(res);
			return;
		}
		
		correctReservation.getPrevStation().sendConfirmation(res);
	}
	
	private void makeRoadsign(Station sender) {

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

	
	
}
