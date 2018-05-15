package rinsim;

import java.util.ArrayList;
import java.util.Collections;

import org.omg.CORBA.Current;

import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;

class Pod extends Vehicle {
	private static final int START_HOP_COUNT = 10;

	private ArrayList<Reservation> desire = new ArrayList<>();
	private ArrayList<ArrayList<Station>> intentions = new ArrayList<>();
	private ArrayList<User> passengers = new ArrayList<>();
	private Station current; 
	
	
	private static final double SPEED = 1000d;
	
	protected Pod(Point startPos, int cap, Station current) {
		super(VehicleDTO.builder()
				.capacity(cap)
				.startPosition(startPos)
				.speed(SPEED)
				.build());
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
				Station dest = rs.get(0).getEndStation();
				if(dest != current) {
					getIntentions().clear();
					current.receiveExplorationAnt(new ArrayList<Station>(), dest, START_HOP_COUNT);
					
					if(!getIntentions().isEmpty()) {
						ArrayList<Station> curBest = getIntentions().get(0);
						for(ArrayList<Station> i : getIntentions()) {
							if(i.size() < curBest.size()) {
								curBest = i;
							}
						}
						System.out.println("Making reservation for intention: "+curBest+ "with pod " + this);
						makeReservations(curBest);
					}
				}
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

	private void makeReservations(ArrayList<Station> curBest) {
		ArrayList<Reservation> res = new ArrayList<Reservation>();
		Station prev = null;
		for(Station s : curBest) {
			res.add(new Reservation(s, prev, null, 0, this));
			prev = s;
		}
		current.receiveReservationAnt(res, System.currentTimeMillis());
	}

	public void confirmReservations(ArrayList<Reservation> res) {
		Collections.reverse(res);
		setDesire(res);
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
		this.getIntentions().add(prev);
	}

	public ArrayList<ArrayList<Station>> getIntentions() {
		return intentions;
	}

	public void setIntentions(ArrayList<ArrayList<Station>> intentions) {
		this.intentions = intentions;
	}

}
