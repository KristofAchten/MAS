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
	// The threshold  on which the pod will go recharge.
	private static final double BATTERY_THRESHOLD = 40;
	
	private ArrayList<Reservation> desire = new ArrayList<>();
	private ArrayList<LinkedHashMap<Station, Long>> intentions = new ArrayList<LinkedHashMap<Station, Long>>();
	private ArrayList<User> passengers = new ArrayList<>();
	private double battery = 100;
	
	private Station currentStation; 
	private LoadingDock currentLoadingDock;
	private Queue<Point> movingQueue = new LinkedList<Point>();
	private TimeWindow currentWindow = null;
	
	private long lastRefresh = System.currentTimeMillis();
	private long lastMove = System.currentTimeMillis();
	
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
		
		// Only move if there is a next hop and our reservation time is respected, and the battery is not zero.
		if(!movingQueue.isEmpty() && currentWindow.isIn(System.currentTimeMillis()) && getBattery() > 0) {
			rm.followPath(this, movingQueue, time);
			setLastMove(System.currentTimeMillis());
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
				Reservation r = getCurrentLoadingDock().leave(this);
				currentWindow = r.getTime();
				movingQueue.add(r.getStation().getPosition());			
			}
			return;
		}	
			
		// If no desire is active and we're done moving, or the pod has been inactive for 10 seconds: send out exploration ants using roadsign info
		// Only do this each 1.5s instead of every tick for performance.
		if((((System.currentTimeMillis() - getLastMove()) > 10000) || (getDesire().isEmpty() && movingQueue.isEmpty())) && (System.currentTimeMillis() - getLastRefresh() > 1500)) {
			Station dest = null;
			setLastRefresh(System.currentTimeMillis());
			
			// Reset the desire if the pod is retrying because of inactivity.
			if(System.currentTimeMillis() - getLastMove() > 10000)
				setDesire(null);

			// If a certain threshold is reached, start moving towards a loadingdock.
			if(getBattery() < BATTERY_THRESHOLD) {
				if(getCurrentStation().getLoadingDocks().isEmpty())
					dest = null;
				else {
					movingQueue.add(getCurrentStation().getLoadingDocks().get(0).getPosition());
					return;
				}
			}
			
			// If there are no passengers but there are roadsigns: explore using the most prominent roadsign.
			else if(currentStation.getPassengers().isEmpty() && !currentStation.getRoadsigns().isEmpty()) {
				ArrayList<RoadSign> rs = currentStation.getRoadsigns();
				Collections.sort(rs);
				dest = rs.get(0).getEndStation();
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants using the roadsign "+rs.get(0)+" which points to " + dest 
							+ " at " +dest.getPosition()+".");
			// Else if there are passengers at the current station: get the one that arrived first and explore to his destination.
			// Use either the optimized task planning algorithm for this, or the first-come-first-served version.
			} else if(!currentStation.getPassengers().isEmpty()) {
				if(PeopleMover.ADVANCED_PLANNING) {
					getIntentions().clear();
					for(User u : getCurrentStation().getPassengers()) {
						currentStation.receiveExplorationAnt(new LinkedHashMap<Station,Long>(), u.getDestination(), START_HOP_COUNT, this);
					}
					LinkedHashMap<Station, Long> curBest = findBestRouteAdvanced();
					
					if(curBest != null) {
						if(PeopleMover.DEBUGGING) {
							System.out.print("The pod has determined the most optimal route to be: (");
							for(Station s: curBest.keySet()) {
								System.out.print(s.getPosition()+ ", ");
							}
							System.out.println("). Making reservations now...");
						}
						
						makeReservations(curBest);
						improvedRouting = true;
					}
				} else {				
					User u = currentStation.getPassengers().get(0);
					dest = u.getDestination();
					if(PeopleMover.DEBUGGING)
						System.out.println("Pod "+this+" has sent out exploration ants using the destination" + dest +" at " + 
								dest.getPosition() + " of a passenger.");
				}				
			// Else: just try to get to a random neighbour and hope there's something to do there.
			} else {
				int n = r.nextInt(currentStation.getNeighbours().size());
				dest = getCurrentStation().getNeighbours().get(n);
				if(PeopleMover.DEBUGGING)
					System.out.println("Pod "+this+" has sent out exploration ants to a random neighbour" + dest +" at " + 
							dest.getPosition() + ". He's currently at " + rm.getPosition(this));
			}
			
			

			// Send out the ants, fetch the intentions to the destination and make the shortest one in size the desire of this pod.
			// Only do this when the improved task planning hasn't been used this tick.
			if(dest != currentStation && !improvedRouting) {
				getIntentions().clear();
				currentStation.receiveExplorationAnt(new LinkedHashMap<Station,Long>(), dest, START_HOP_COUNT, this);
				
				if(!getIntentions().isEmpty()) {
					LinkedHashMap<Station, Long> curBest = getIntentions().get(0);
					for(LinkedHashMap<Station, Long> i : getIntentions()) {
						Iterator<Long> it = i.values().iterator();
						Long val = it.next();
						while(it.hasNext()) {val = it.next();}
						
						it = curBest.values().iterator();
						Long bestVal = it.next();
						while(it.hasNext()) {bestVal = it.next();}
						if(val < bestVal) {
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
				double delay = u.getDeadline() - System.currentTimeMillis();
				
				if(PeopleMover.EXPERIMENTING)
					if(delay < 0)
						PeopleMover.getDelays().add(delay);
					else
						PeopleMover.setUsersOnTime(PeopleMover.getUsersOnTime() + 1);

				if(PeopleMover.DEBUGGING) {
					System.out.print("Ik zie een driekwartsbroek... User with destination " + u.getDeliveryLocation() + "has arrived at "
							+ rm.getPosition(this));
				}
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
		
		// If there are no planned moves, and there is a desire: add a move from the desire.
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

	/**
	 * Find the route that can take the most users (based on the users at the current station) at once.
	 * 
	 * @return LinkedHashMap<Station, Long> = stations and earliest possible reservation start times for that station.
	 */
	private LinkedHashMap<Station, Long> findBestRouteAdvanced() {
		LinkedHashMap<Station, Long> best = null;
		int bestNumPass = 0;
		ArrayList<Station> passengerDestinations = new ArrayList<>();
		
		// Create a list of all passenger destinations.
		for(User p : getCurrentStation().getPassengers()) {
			passengerDestinations.add(p.getDestination());
		}
		
		// Determine the best intention.
		for(LinkedHashMap<Station, Long> intention : getIntentions()) {
			int numPass = calculateDestinationsOnRoute(intention, passengerDestinations);
			if(numPass > bestNumPass) {
				bestNumPass = numPass;
				best = intention;
			}
			
		}
		return best;
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
		
		// Send this list to the current station, which will propagate it for the reservations to be filled in.
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
}
