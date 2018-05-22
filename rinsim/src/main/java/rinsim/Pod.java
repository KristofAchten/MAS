package rinsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
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
	private static final int START_HOP_COUNT = 10;
	// The maximal time left of a reservation before it is refreshed. 
	private static final int RESERVATION_DIF = 500;
	// The reservation time at an end station.
	private static final int END_STATION_TIME = 999999999;
	// The pod speed.
	private static final double SPEED = 200d;
	// The amount a battery gets drained per tick if its moving.
	private static final double BATTERY_DRAIN = 0.1;
	// The amount a battery gets charged per tick when at a loading dock.
	private static final double BATTERY_GAIN = 0.5;
	
	private ArrayList<Reservation> desire = new ArrayList<>();
	private ArrayList<LinkedHashMap<Station, Long>> intentions = new ArrayList<LinkedHashMap<Station, Long>>();
	private ArrayList<User> passengers = new ArrayList<>();
	private double battery = 100;
	
	private Station currentStation; 
	private LoadingDock currentLoadingDock;
	private Queue<Point> movingQueue = new LinkedList<Point>();
	private TimeWindow currentWindow = null;
	
	Random r = new Random();
	
	
	protected Pod(Point startPos, int cap, Station current) {
		super(VehicleDTO.builder()
				.capacity(cap)
				.startPosition(startPos)
				.speed(SPEED)
				.build());
		setCurrentStation(current);
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		System.out.println(this+": "+getBattery());
		RoadModel rm = getRoadModel();
		PDPModel pm = getPDPModel();
		
		// Only move if there is a next hop and our reservation time is respected.
		if(!movingQueue.isEmpty() && currentWindow.isIn(System.currentTimeMillis()) && getBattery() > 0) {
			rm.followPath(this, movingQueue, time);
			setBattery(getBattery() - BATTERY_DRAIN);
		}
		
		// If arrived at station, set the current station. Else, reset.
		if(!rm.getObjectsAt(this, Station.class).isEmpty()) {
			setCurrentStation(rm.getObjectsAt(this, Station.class).iterator().next());
			getCurrentStation().setPod(this);
		} else if (!rm.getObjectsAt(this, LoadingDock.class).isEmpty()) {
			setCurrentLoadingDock(rm.getObjectsAt(this,  LoadingDock.class).iterator().next());
		} else if(getCurrentStation() != null){
			removeCurrentReservation();
			getCurrentStation().setPod(null);
			setCurrentStation(null);
			return;
		} else if (getCurrentLoadingDock() != null) {
			setCurrentLoadingDock(null);
		} else {
			return;
		}
		
		if(getCurrentLoadingDock() != null)
			setBattery(getBattery() + BATTERY_GAIN); 
		
		// If no desire is active and we're done moving: send out exploration ants using roadsign info
		if(getDesire().isEmpty() && movingQueue.isEmpty()) {
			Station dest = null;
			// If there are no passengers but there are roadsigns: explore using the most prominent roadsign.
			if(currentStation.getPassengers().isEmpty() && !currentStation.getRoadsigns().isEmpty()) {
				ArrayList<RoadSign> rs = currentStation.getRoadsigns();
				Collections.sort(rs);
				dest = rs.get(0).getEndStation();
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the roadsign "+rs.get(0)+" which points to " + dest 
							+ " at " +dest.getPosition()+".");
			// Else if there are passengers: get the one that arrived first and explore to his destination.		
			} else if(!currentStation.getPassengers().isEmpty()) {
				User u = currentStation.getPassengers().get(0);
				dest = u.getDestination();
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the destination" + dest +" at " + 
							dest.getPosition() + " of a passenger.");
			// Else: just try to get to a random neighbour and hope there's something to do there.
			} else {
				int n = r.nextInt(currentStation.getNeighbours().size());
				dest = currentStation.getNeighbours().get(n);
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants to a random neighbour" + dest +" at " + 
							dest.getPosition() + ". He's currently at " + rm.getPosition(this));
			}
			
			

			// Send out the ants, fetch the intentions to the destination and make a make the shortest one in size the desire of this pod.
			if(dest != currentStation) {
				getIntentions().clear();
				currentStation.receiveExplorationAnt(new LinkedHashMap<Station,Long>(), dest, START_HOP_COUNT, this);

				
				if(!getIntentions().isEmpty()) {
					LinkedHashMap<Station, Long> curBest = getIntentions().get(0);
					for(LinkedHashMap<Station, Long> i : getIntentions()) {
						if(i.get(dest) < curBest.get(dest)) {
							curBest = i;
						}
					}

					if(PeopleMover.DEBUGGING) {
						System.out.print("The best intention is: (");
						for(Station s: curBest.keySet()) {
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
			if(u.getDestination() == currentStation) {
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
		for(User u : currentStation.getPassengers()) {
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
			currentStation.embarkUser(us);
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
				System.out.println("Added "+r.getStation().getPosition() + " to the movingQueue of Pod " + this +" at " + rm.getPosition(this) + " " + currentStation
						+ " at " + System.currentTimeMillis());
			
			// Set the current fields
			currentWindow = r.getTime();
			movingQueue.add(r.getStation().getPosition());
		}
	}

	private void removeCurrentReservation() {
		ArrayList<Reservation> toRemove = new ArrayList<>();
		for(Reservation r : currentStation.getReservations())
			if(r.getPod() == this) {
				toRemove.add(r);
			}
		
		currentStation.getReservations().removeAll(toRemove);
	}
	
	public TimeWindow getCurrentWindow() {
		return currentWindow;
	}

	public void setCurrentWindow(TimeWindow currentWindow) {
		this.currentWindow = currentWindow;
	}

	/**
	 * Make reservations for the stations that the exploration ants return.
	 * 
	 * @param curBest - The exploration result
	 */
	public void makeReservations(LinkedHashMap<Station, Long> curBest) {
		ArrayList<Reservation> res = new ArrayList<Reservation>();
		Reservation prev = null;
		// Initialize a list of empty reservations per station.
		for(Entry<Station, Long> e : curBest.entrySet()) {
			
			Reservation r = null;
			if(prev != null) {
				r = new Reservation(e.getKey(), prev.getStation(), null, 0, this);
				prev.setTime(TimeWindow.create(curBest.get(prev.getStation()), e.getValue()));
			} else {
				r = new Reservation(e.getKey(), null, null, 0, this);
			}
			
			prev = r;
			res.add(r);			
		}
		
		prev.setTime(TimeWindow.create(curBest.get(prev.getStation()), curBest.get(prev.getStation()) + END_STATION_TIME));
		
		currentStation.receiveReservationAnt(res, false);
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
		getDesire().add(0, new Reservation(currentStation, null, currentWindow, 0, this));
		currentStation.receiveReservationAnt(getDesire(), true);
	}
	
	/**
	 * Add any of the exploration results to the intentions list.
	 * 
	 * @param stations - An arraylist of stations.
	 */
	public void receiveExplorationResult(LinkedHashMap<Station,Long> stations) {
		
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

	public Station getCurrentStation() {
		return currentStation;
	}

	public void setCurrentStation(Station current) {
		this.currentStation = current;
	}

	public ArrayList<User> getPassengers() {
		return this.passengers;
	}

	public void setPassengers(ArrayList<User> passengers) {
		this.passengers = passengers;
	}

	public ArrayList<LinkedHashMap<Station, Long>> getIntentions() {
		return intentions;
	}

	public void setIntentions(ArrayList<LinkedHashMap<Station, Long>> intentions) {
		this.intentions = intentions;
	}

	public double getBattery() {
		return battery;
	}

	public void setBattery(double battery) {
		this.battery = battery;
	}

	public LoadingDock getCurrentLoadingDock() {
		return currentLoadingDock;
	}

	public void setCurrentLoadingDock(LoadingDock currentLoadingDock) {
		this.currentLoadingDock = currentLoadingDock;
	}

}
