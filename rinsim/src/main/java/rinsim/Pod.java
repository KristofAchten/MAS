package rinsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;

class Pod extends Vehicle {
	
	// Number of hops the exploration ants are maximally going to take before being returned. 
	private static final int START_HOP_COUNT = 5;
	// The maximal time left of a reservation before it is refreshed. 
	private static final int RESERVATION_DIF = 20000; 
	// The pod speed.
	private static final double SPEED = 100d;

	private ArrayList<Reservation> desire = new ArrayList<>();
	private ArrayList<ArrayList<Station>> intentions = new ArrayList<>();
	private ArrayList<User> passengers = new ArrayList<>();
	
	private Station current; 
	private Queue<Point> movingQueue = new LinkedList<Point>();
	private TimeWindow currentWindow = null;
	
	Random r = new Random();
	
	
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
		RoadModel rm = getRoadModel();
		PDPModel pm = getPDPModel();
		
		// Only move if there is a next hop and our reservation time is respected.
		if(!movingQueue.isEmpty() && currentWindow.isIn(System.currentTimeMillis())) {
			rm.followPath(this, movingQueue, time);
		}
		
		// If arrived at station, set the current station. Else, reset.
		if(!rm.getObjectsAt(this, Station.class).isEmpty()) {
			setCurrent(rm.getObjectsAt(this, Station.class).iterator().next());
			current.setPod(this);
		} else if(current != null){
			current.setPod(null);
			setCurrent(null);
			return;
		} else {
			return;
		}
		
		// If no desire is active and we're done moving: send out exploration ants using roadsign info
		if(getDesire().isEmpty() && movingQueue.isEmpty()) {
			Station dest = null;
			// If there are no passengers but there are roadsigns: explore using the most prominent roadsign.
			if(current.getPassengers().isEmpty() && !current.getRoadsigns().isEmpty()) {
				ArrayList<RoadSign> rs = current.getRoadsigns();
				Collections.sort(rs);
				dest = rs.get(0).getEndStation();
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the roadsign "+rs.get(0)+" which points to " + dest 
							+ " at " +dest.getPosition()+".");
			// Else if there are passengers: get the one that arrived first and explore to his destination.		
			} else if(!current.getPassengers().isEmpty()) {
				User u = current.getPassengers().get(0);
				dest = u.getDestination();
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the destination" + dest +" at " + 
							dest.getPosition() + " of a passenger.");
			// Else: just try to get to a random neighbour and hope there's something to do there.
			} else {
				int n = r.nextInt(current.getNeighbours().size());
				dest = current.getNeighbours().get(n);
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants to a random neighbour" + dest +" at " + 
							dest.getPosition() + ". He's currently at " + rm.getPosition(this));
			}

			// Send out the ants, fetch the intentions to the destination and make a make the shortest one in size the desire of this pod.
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
					
					if(PeopleMover.DEBUGGING) {
						System.out.print("The best intention is: (");
						for(Station s: curBest) {
							System.out.print(s.getPosition()+ ", ");
						}
						System.out.println("). Making reservations now...");
					}
					makeReservations(curBest);
				}
			}
		}
		
		// Remove users that have arrived.
		ArrayList<User> toRemove = new ArrayList<>();
		for(User u : getPassengers()) {
			if(u.getDestination() == current) {
				toRemove.add(u);
				pm.deliver(this, u, time);
				if(PeopleMover.DEBUGGING)
					System.out.println("Ik zie een driekwartsbroek... User with destination " + u.getDeliveryLocation() + "has arrived at "
							+ rm.getPosition(this));
			}
		}
		getPassengers().removeAll(toRemove);
		
		// Embark new users, but only if their destination is in the current desire.
		ArrayList<User> toEmbark = new ArrayList<>();
		for(User u : current.getPassengers()) {
			Station dest = u.getDestination();
			for(Reservation r : getDesire()) {
				if(getPassengers().size() < getCapacity() && r.getStation() == dest) {
					getPassengers().add(u);
					toEmbark.add(u);
				}
			}
		}
		for(User us : toEmbark) {
			pm.pickup(this, us, time);
			current.embarkUser(us);
			if(PeopleMover.DEBUGGING)
				System.out.println("Picking up " + us + " at " + rm.getPosition(this));
		}
		
		// If there are no planned moves, and there is a desire, add a move from the desire.
		if(movingQueue.isEmpty() && !getDesire().isEmpty()) {
			
			// If the reservation for the next hop is closer than RESERVATION_DIF away, refresh everything!
			if(getDesire().get(0).getTime().end() < System.currentTimeMillis() + RESERVATION_DIF) {
				refreshReservations();
			} 
			
			// Pop the highest point in the sequence.
			Reservation r = getDesire().remove(0);
			
			if(PeopleMover.DEBUGGING)
				System.out.println("Added "+r.getStation().getPosition() + " to the movingQueue of Pod " + this);
			
			// Set the current fields
			currentWindow = r.getTime();
			movingQueue.add(r.getStation().getPosition());
		}
	}

	/**
	 * Make reservations for the stations that the exploration ants return.
	 * 
	 * @param curBest - The exploration result
	 */
	private void makeReservations(ArrayList<Station> curBest) {
		ArrayList<Reservation> res = new ArrayList<Reservation>();
		Station prev = null;
		
		// Initialize a list of empty reservations per station.
		for(Station s : curBest) {
			res.add(new Reservation(s, prev, null, 0, this));
			prev = s;
		}
		current.receiveReservationAnt(res, System.currentTimeMillis(), false);
	}

	/**
	 * Confirm a reservation sequence and change the desire to it.
	 * 
	 * @param res - The sequence of reservations, in reverse order.
	 */
	public void confirmReservations(ArrayList<Reservation> res) {
		Collections.reverse(res);
		setDesire(res);
	}
	
	/**
	 * Refresh the current reservations that are part of the desire.
	 */
	public void refreshReservations() {
		getDesire().add(0, new Reservation(current, null, currentWindow, 0, this));
		current.receiveReservationAnt(getDesire(), System.currentTimeMillis(), true);
	}
	
	/**
	 * Add any of the exploration results to the intentions list.
	 * 
	 * @param stations - An arraylist of stations.
	 */
	public void receiveExplorationResult(ArrayList<Station> stations) {
		this.getIntentions().add(stations);
	}

	/**
	 * GETTERS AND SETTERS.
	 */
	
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

	public ArrayList<ArrayList<Station>> getIntentions() {
		return intentions;
	}

	public void setIntentions(ArrayList<ArrayList<Station>> intentions) {
		this.intentions = intentions;
	}

}
