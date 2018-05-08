package rinsim;

import java.util.ArrayList;
import java.util.Collections;

import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;

class Pod extends Vehicle {

	private ArrayList<Reservation> desire;
	private ArrayList<User> passengers;
	private Station current; 
	
	
	private static final double SPEED = 1000d;
	
	protected Pod(Point startPos, int cap, Station current) {
		super(VehicleDTO.builder()
				.capacity(cap)
				.startPosition(startPos)
				.speed(SPEED)
				.build());
		setDesire(new ArrayList<Reservation>());
		setCurrent(current);
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		
		if(current == null)
			return;
		
		if(getDesire().isEmpty() && current.getPassengers().isEmpty()) {
			ArrayList<RoadSign> rs = current.getRoadsigns();
			if(!rs.isEmpty()) {
				Collections.sort(rs);
				//current.receiveAnt("reservation", null, System.currentTimeMillis(), null);
				//setDesire(rs.get(0).getPreviousStations());
			} else {
				// Random buur
			}
		}
		
		// Remove users that have arrived.
		for(User u : getPassengers()) {
			if(u.getDestination() == current) {
				getPassengers().remove(u);
			}
		}
		
		// Embark new users
		for(User u : current.getPassengers()) {
			Station dest = u.getDestination();
			for(Reservation r : this.desire) {
				if(dest == r.getStation() && getPassengers().size() < getCapacity()) {
					getPassengers().add(u);
					current.embarkUser(u);
				}
			}
		}
	}

	public void confirmReservations(ArrayList<Reservation> res) {
		this.desire = res;
	}

	public ArrayList<Reservation> getDesire() {
		return desire;
	}

	public void setDesire(ArrayList<Reservation> desire) {
		this.desire = desire;
	}

	public Station getCurrent() {
		return current;
	}

	public void setCurrent(Station current) {
		this.current = current;
	}

	public ArrayList<User> getPassengers() {
		return this.passengers;
	}

	public void setPassengers(ArrayList<User> passengers) {
		this.passengers = passengers;
	}

	public void receiveExplorationResult(ArrayList<Station> prev) {
		
	}

}
