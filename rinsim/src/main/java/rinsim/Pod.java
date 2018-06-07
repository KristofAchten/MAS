package rinsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
	// The reservation time at an end station.
	private static final long END_STATION_TIME = 999999999999999999L;
	// The pod speed.
	private static final double SPEED = 200d;
	// The amount a battery gets drained per tick if its moving.
	private static final double BATTERY_DRAIN = 0.01;
	// The amount a battery gets charged per tick when at a loading dock.
	private static final double BATTERY_GAIN = 1;
	// The threshold  on which the pod will go recharge.
	private static final double BATTERY_THRESHOLD = 35;
	
	
	// List of reservations for which the pod is currently routing.
	private ArrayList<Reservation> desire = new ArrayList<>();
	// List of Maps that show each earliest reservation time per station. This is used to determine the desire (cfr BDI model)
	private ArrayList<LinkedHashMap<Station, Long>> intentions = new ArrayList<LinkedHashMap<Station, Long>>(); private ArrayList<User> passengers = new ArrayList<>();
	// List of destinations that have been tried, but for which no suitable route was found or the destination is unavailable.
	private ArrayList<Station> failedDestinations = new ArrayList<>();
	
	
	private double battery = 100;
	private Station currentStation; 
	private LoadingDock currentLoadingDock;
	private Queue<Point> movingQueue = new LinkedList<Point>();
	private TimeWindow currentWindow = null;
	
	private long lastRefresh = 0;
	private long lastMove = 0;
		
	Random r = new Random();
	
	
	protected Pod(Point startPos, int cap, LoadingDock start) {
		super(VehicleDTO.builder()
				.capacity(cap)
				.startPosition(startPos)
				.speed(SPEED)
				.build());
		setCurrentLoadingDock(start);
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		boolean improvedRouting = false;
		RoadModel rm = getRoadModel();
		PDPModel pm = getPDPModel();
		
		if(getBattery() <= 0) { 
			System.err.println("FAIL: The pod at position " + rm.getPosition(this) + " has run out of juice! :-(");
			System.exit(1);
		}
		
		// Only move if there is a next hop, our reservation time is respected and the battery is not zero.
		if(!movingQueue.isEmpty() && currentWindow.isIn(time.getTime()) && getBattery() > 0) {
			rm.followPath(this, movingQueue, time);
			setLastMove(time.getTime());
			setBattery(getBattery() - BATTERY_DRAIN);
		}
		
		// If arrived at station, set the current station. Same for loadingdocks. Else: reset.
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
			return;
		} else {
			return;
		}
		
		// If the pod is at a loading dock, charge it. If it's full: try to leave.
		if(getCurrentLoadingDock() != null) {
			setBattery(getBattery() + BATTERY_GAIN);
			if(getBattery() >= 100 && movingQueue.isEmpty()) {
				Reservation r = getCurrentLoadingDock().leave(this, time);
				currentWindow = r.getTime();
				movingQueue.add(r.getStation().getPosition());			
			}
			return;
		}	
		// If no desire is active and we're done moving, or the pod has been inactive for 5 minutes: send out exploration ants using roadsign info
		// Only do this each 1.5s instead of every tick for performance.
		
		long currentTime = time.getTime();
		if((((currentTime - getLastMove()) > 600000) || (getDesire().isEmpty() && movingQueue.isEmpty())) && (currentTime - getLastRefresh() > 90000)) {
			Station dest = null;
			setLastRefresh(currentTime);
			
			// Reset the desire if the pod is retrying because of inactivity. This for possible deadlock resolution.
			if(currentTime - getLastMove() > 10000)
				getDesire().clear();

			// If a certain threshold is reached, start moving towards a loadingdock.
			if(getBattery() < BATTERY_THRESHOLD && getPassengers().isEmpty()) {
				if(getCurrentStation().getLoadingDocks().isEmpty())
					dest = null;
				else {
					movingQueue.add(getCurrentStation().getLoadingDocks().get(0).getPosition());
					return;
				}
			}
			
			// Else if there are passengers at the current station: get the one that arrived first and explore to his destination.
			else if(!currentStation.getPassengers().isEmpty()) {
				
				// If we're using the optimized task planning:
				if(PeopleMover.ADVANCED_PLANNING) {
					
					// Clear the current list of intentions (these are outdated).
					getIntentions().clear();
					
					// Send out exploration ants for each passenger at the current station.
					for(User u : getCurrentStation().getPassengers())
						currentStation.receiveExplorationAnt(new LinkedHashMap<Station,Long>(), u.getDestination(), START_HOP_COUNT, this, time.getTime());
					
					// Find the best route out of the newly found intentions.
					LinkedHashMap<Station, Long> curBest = findBestIntentionAdvanced();
					
					if(curBest != null) {
						if(PeopleMover.DEBUGGING) {
							System.out.print("The pod has determined the most optimal route to be: (");
							for(Station s: curBest.keySet()) {
								System.out.print(s.getPosition()+ ", ");
							}
							System.out.println("). Making reservations now...");
						}
						
						// Make it the desire, and let the remainder of this method know that we've already done this.
						
						
						makeReservations(curBest);
						improvedRouting = true;
					} else {
						// If no intentions were found because of destination unavailability: return
						if(PeopleMover.DEBUGGING) 
							System.err.println("The pod at " + rm.getPosition(this) + " was unable to find intentions using the destination of the passengers"
									+ " at the station.");
						
						return;
					}
				// If we're using the FCFS task planning:	
				} else {
					User u = currentStation.getPassengers().get(0);
					dest = u.getDestination();
					if(PeopleMover.DEBUGGING)
						System.out.println("Pod "+this+" has sent out exploration ants using the destination " + dest +" at " + 
								dest.getPosition() + " of a passenger.");
				}				
			}
			// If there are no passengers but there are roadsigns: explore using the most prominent roadsign.
			else if(currentStation.getPassengers().isEmpty() && !currentStation.getRoadsigns().isEmpty()) {
				ArrayList<RoadSign> rs = currentStation.getRoadsigns();
				Collections.sort(rs);
				
				// Select the most prominent roadsign that has not yet lead to finding no intentions (if such roadsign exists).
				for(RoadSign sign : rs) {
					if(!getFailedDestinations().contains(sign.getEndStation())) {
						dest = sign.getEndStation();
						break;
					}
				} 
				
				// If no such roadsign was found, pick a random neighbour to resolve deadlock.
				if(dest == null) {
					dest = getRandomNeighbour();
					
					if(PeopleMover.DEBUGGING)
						System.out.println("Pod at location " + rm.getPosition(this) + " tried to follow a roadsign, but was unable to. It's now routing towards "
								 + dest +" at " + dest.getPosition() + ".");
				}
				
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the roadsign "+rs.get(0)+" which points to " + dest 
							+ " at " +dest.getPosition()+". He's currently at " + rm.getPosition(this));
			// Else: just try to get to a random neighbour and hope there's something to do there.
			}  else {
				dest = getRandomNeighbour();
				
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants to a random neighbour " + dest +" at " + 
							dest.getPosition() + ". He's currently at " + rm.getPosition(this));
			}

			
			// Send out the ants to the destination selected above, fetch the intentions to the destination and 
			// make the shortest one in size the desire of this pod.
			// Only do this when the improved task planning hasn't been used this tick.
			if(dest != currentStation && !improvedRouting) {
				
				// Clear the current intentions, and provide the ant to the first station (the one this pod is currently on).
				// This will process, and fill up the intentions.
				getIntentions().clear();
				currentStation.receiveExplorationAnt(new LinkedHashMap<Station,Long>(), dest, START_HOP_COUNT, this, time.getTime());
				
				// If atleast one intention has been found:
				if(!getIntentions().isEmpty()) {
					
					// Reset the list of failed destinations, as we're now moving...
					getFailedDestinations().clear();
					
					// Find the best intention.
					LinkedHashMap<Station, Long> curBest = findBestIntentionBasic();
					
					if(PeopleMover.DEBUGGING) {
						System.out.print("The best intention is: (");
						for(Entry<Station, Long> e: curBest.entrySet()) {
							System.out.print(e.getKey()+ ", " + e.getValue()+"; ");
						}
						System.out.println("). Making reservations now...");
					}
					
					// Make reservations for the best intention.
					makeReservations(curBest);
					
				// If no intentions had been found and there are no passengers at the current station: add this destination to the failed list.
				} else if (currentStation.getPassengers().isEmpty()){
					if(PeopleMover.DEBUGGING) 
						System.err.println("The pod at " + rm.getPosition(this) + " was unable to find intentions outwards to " + dest.getPosition() +".");
					getFailedDestinations().add(dest);
					return;
				}
					
			}
			
			
		}
		
		// Remove users that have arrived.
		ArrayList<User> toRemove = new ArrayList<>();
		for(User u : getPassengers()) {
			if(u.getDestination() == currentStation) {
				toRemove.add(u);
				pm.deliver(this, u, time);
				
				double delay = time.getTime() - u.getDeadline();
				if(delay > 0)
					PeopleMover.getDelays().add(delay);
				else
					PeopleMover.setUsersOnTime(PeopleMover.getUsersOnTime() + 1);
				
				if(PeopleMover.DEBUGGING) {
					System.out.print("Ik zie een driekwartsbroek... User with destination " + u.getDeliveryLocation() + "has arrived at "
							+ rm.getPosition(this));
				}
			}
		}
		if(!toRemove.isEmpty() ) {
			getPassengers().removeAll(toRemove);
			return;
		}
		
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
				System.out.println("Picking up " + us + " at " + rm.getPosition(this) + "amount of users left: "+ getCurrentStation().getPassengers().size());
		}
		
		// If there are no planned moves, and there is a desire: add a move from the desire.
		if(movingQueue.isEmpty() && !getDesire().isEmpty()) {

			// Pop the highest point in the sequence.
			Reservation r = getDesire().remove(0);
			
			if(PeopleMover.DEBUGGING)
				System.out.println("Added "+r.getStation().getPosition() + " to the movingQueue of Pod " + this +" at " + rm.getPosition(this) + " " + currentStation
						+ " at " + time.getTime());
			
			// Set the current fields
			currentWindow = r.getTime();
			movingQueue.add(r.getStation().getPosition());
		}
	}

	/**
	 * Find the route that can take the most users (based on the users at the current station) at once.
	 * 
	 * @return LinkedHashMap<Station, Long> = stations and earliest possible reservation start times for that station.
	 */
	private LinkedHashMap<Station, Long> findBestIntentionAdvanced() {
		LinkedHashMap<Station, Long> best = null;
		int bestNumPass = 0;
		long deliveryTime = Long.MAX_VALUE;
		ArrayList<Station> passengerDestinations = new ArrayList<>();
		
		// Create a list of all passenger destinations.
		for(User p : getCurrentStation().getPassengers()) {
			passengerDestinations.add(p.getDestination());
		}
		
		// Determine the best intention.
		for(LinkedHashMap<Station, Long> intention : getIntentions()) {
			int numPass = calculateDestinationsOnRoute(intention, passengerDestinations);
			long delivery = intention.values().iterator().next();
			if((numPass > bestNumPass) || (numPass == bestNumPass && delivery < deliveryTime)) {
				bestNumPass = numPass;
				deliveryTime = delivery;
				best = intention;
			}
			
		}
		return best;
	}
	
	/**
	 * Find the quickest route that can take the user that arrived first in the current station.
	 * 
	 * @return LinkedHashMap<Station, Long> = stations and earliest possible reservation start times for that station.
	 */
	private LinkedHashMap<Station, Long> findBestIntentionBasic() {
		
		LinkedHashMap<Station, Long> curBest = new LinkedHashMap<>();
		long BestTime = Long.MAX_VALUE;
		
		// For each intention...
		for(LinkedHashMap<Station, Long> i : getIntentions()) {
			// Iterate through the intention to get the latest reservation time.
			Iterator<Long> it = i.values().iterator();
			long time = it.next();
			while(it.hasNext()) 
				time = it.next();
			
			// If this reservation time is better than the current one, update the results.
			if(time < BestTime) {
				BestTime = time;
				curBest = i;
			}
		}
		
		return curBest;
	}

	/**
	 * Determine the intersection of the provided lists.
	 * 
	 * @param intention - LinkedHashMap<Station, Long> = hashmap of stations and their earliest possible reservation times.
	 * @param passengerDestinations - ArrayList<Station> = list of passenger destinations for this station.
	 * @return int n = the number of elements that exist in both lists.
	 */
	private int calculateDestinationsOnRoute(LinkedHashMap<Station, Long> intention, ArrayList<Station> passengerDestinations) {
		ArrayList<Station> rest = new ArrayList<Station>(passengerDestinations);
		rest.retainAll(intention.keySet());
		return rest.size();
	}

	/**
	 * Remove the reservations for the current pod from the current station.
	 */
	private void removeCurrentReservation() {
		ArrayList<Reservation> toRemove = new ArrayList<>();
		for(Reservation r : currentStation.getReservations())
			if(r.getPod() == this) {
				toRemove.add(r);
			}
		currentStation.getReservations().removeAll(toRemove);
	}

	/**
	 * Make reservations for the stations that the exploration ants return.
	 * 
	 * @param curBest - The exploration result
	 */
	public void makeReservations(LinkedHashMap<Station, Long> curBest) {
		ArrayList<Reservation> res = new ArrayList<Reservation>();
		Reservation prev = null;
		
		// Initialize a list of empty reservations per station to be passed on and filled in.
		for(Entry<Station, Long> e : curBest.entrySet()) {
			
			Reservation r = null;
			
			if(prev != null) {
				r = new Reservation(e.getKey(), prev.getStation(), null, this, 0);
				prev.setTime(TimeWindow.create(curBest.get(prev.getStation()), e.getValue()));
				prev.setExpirationTime(e.getValue());
			} else {
				r = new Reservation(e.getKey(), null, null, this, 0);
			}
			
			prev = r;
			res.add(r);			
		}
		
		prev.setTime(TimeWindow.create(curBest.get(prev.getStation()), curBest.get(prev.getStation()) + END_STATION_TIME));
		prev.setExpirationTime(curBest.get(prev.getStation()));
		
		// Send this list to the current station, which will propagate it for the reservations to be filled in.
		currentStation.receiveReservationAnt(res);
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
	 * Add any of the exploration results to the intentions list.
	 * 
	 * @param stations - An arraylist of stations.
	 */
	public void receiveExplorationResult(LinkedHashMap<Station,Long> stations) {
		this.getIntentions().add(stations);
	}
	
	/**
	 * Pick a random neighbour that is not in the failed stations list.
	 * 
	 * @return Station neighbour
	 */
	private Station getRandomNeighbour() {
		int n = r.nextInt(currentStation.getNeighbours().size());
		Station ret = getCurrentStation().getNeighbours().get(n);
		
		// If all neighbours have been tried before: reset the list so they can be tried again.
		if(getFailedDestinations().containsAll(getCurrentStation().getNeighbours()))
			getFailedDestinations().clear();
		
		// Keep searching until a neighbour has been found that is not in the list.
		while (getFailedDestinations().contains(ret)) {
			n = r.nextInt(currentStation.getNeighbours().size());
			ret = getCurrentStation().getNeighbours().get(n);
		}
		
		return ret;
	}

	/**
	 * GETTERS AND SETTERS.
	 */
	
	public TimeWindow getCurrentWindow() {
		return currentWindow;
	}

	public void setCurrentWindow(TimeWindow currentWindow) {
		this.currentWindow = currentWindow;
	}

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
	
	public long getLastMove() {
		return lastMove;
	}

	public void setLastMove(long lastMove) {
		this.lastMove = lastMove;
	}

	public long getLastRefresh() {
		return lastRefresh;
	}

	public void setLastRefresh(long lastRefresh) {
		this.lastRefresh = lastRefresh;
	}

	public ArrayList<Station> getFailedDestinations() {
		return failedDestinations;
	}

	public void setFailedDestinations(ArrayList<Station> failedDestinations) {
		this.failedDestinations = failedDestinations;
	}
}
