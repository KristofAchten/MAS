package rinsim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;

public class Station extends Depot {
	
	// The time that each reservation should last.
	public static long RESERVATION_TIME = 100000;
	// The time between each reservation in the sequence.
	public static long BUFFER_TIME = 10000;

	
	private ArrayList<Reservation> reservations = new ArrayList<>();
	private ArrayList<RoadSign> roadsigns = new ArrayList<>();
	private ArrayList<Station> neighbours = new ArrayList<>();
	private ArrayList<User> passengers = new ArrayList<>();
	private ArrayList<LoadingDock> loadingDocks = new ArrayList<>();
	
	private Pod pod = null;
	private Point position;

	public Station(Point position) {
		super(position);		
		setPosition(position);
		setCapacity(1);
	}
	
	// Process all incoming ants.
	public void receiveExplorationAnt(LinkedHashMap<Station,Long> prev, Station dest, int hop, Pod pod, long time) {
		forwardExploration(prev, dest, hop, pod, time);
	}
	public void receiveReservationAnt(ArrayList<Reservation> res) {
		makeReservation(res);
	}
	public void receiveRoadSignAnt(RoadSign prev) {
		makeRoadsign(prev);
	}
	
	/**
	 * Adds itself to the list of visited stations and forwards to each neighbour until it reaches
	 * the destination. Then it will start sending back the information by going through the chain of stations again.
	 * If the ant doesn't arrive at the correct destination, a loop is detected or the hops run out: 
	 * the chain is cancelled, and nothing is returned.
	 * 
	 * @param prev - The list of previously visited stations
	 * @param dest - The destination Station. If this is null, the pod is trying to route to a loadingdock.
	 * @param hop - The current hop-count. -1 indicates that the returning process is ongoing.
	 */
	private void forwardExploration(LinkedHashMap<Station,Long> prev, Station dest, int hop, Pod pod, long currentTime) {
		if(dest == null && !getLoadingDocks().isEmpty())
			dest = this;
		
		// If the hops have run out or loop has been detected (station is already in prev): kill the chain.
		if((hop == 0 && this != dest) || (prev.keySet().contains(this) && hop != -1))
			return;
		
		long prevTime = currentTime;
		Iterator<Long> iterator = prev.values().iterator();
		while (iterator.hasNext()) { 
			prevTime = iterator.next(); 
		}
		
		// If the ant has arrived, add this station to the list and continue the method.
		if(this == dest) {
			long resTime = checkPossibleReservationTime(prevTime + RESERVATION_TIME).begin();
			if(resTime > 9999999999999999L)
				return;
			else
				prev.put(this, resTime);
		}

		// If the ant has arrived or the hop count is set to -1: start returning.
		if(this == dest || hop == -1) {
			// If we've reached the end of the chain, the current pod should be informed.
			if(this.equals(prev.keySet().iterator().next())) {
				getPod().receiveExplorationResult(prev);
				return;
			}
			// If not: send to the next previous station, but use a copy.
			Station prevStation = null;
			Iterator<Station> it = prev.keySet().iterator();
			Station curStation = it.next();

			while(curStation != this) {
				prevStation = curStation;
				curStation = it.next();
			}
			prevStation.receiveExplorationAnt(new LinkedHashMap<Station, Long>(prev), dest, -1, pod, currentTime);
		
		// Else: Add this station and forward to each neighbour a copy of the current list (to avoid concurrent modifications).	
		} else {
			if(getPod() == pod)
				prev.put(this, currentTime + RESERVATION_TIME);
			else {
				long resTime = checkPossibleReservationTime(prevTime + RESERVATION_TIME).begin();
				// If the ant reaches a station at which a pod is waiting: kill the chain.
				// This is one of the methods that ensures that pods cannot be at the same station at once.
				if(resTime > 99999999999999L)
					return;
				else
					prev.put(this, resTime);
			}
			for(Station s : getNeighbours()) {
				s.receiveExplorationAnt(new LinkedHashMap<Station, Long>(prev), dest, hop - 1, pod, currentTime);
			}
		}
	}

	/**
	 * Forward a reservation ant to the next station in the sequence.
	 * 
	 * @param res - The list of (incomplete) reservations
	 * @param preferredTime - The preferred time a next reservation should be made.
	 * @param refreshing - Indicates whether or not a new reservation is being made, or a current one is being refreshed
	 */
	public void sendReservationAnt(ArrayList<Reservation> res, long preferredTime) {
		Station receiver = res.get(0).getStation();
		receiver.receiveReservationAnt(res);
	}
	
	/**
	 * Make a reservation.
	 * Refreshing does not always extend the time of the current reservation.
	 * 
	 * @param res - A list reservations that already exist. This list is used to iterate through all reservations
	 * @param preferredTime - The preferred time a reservation should be made
	 * @param refreshing - Indicates whether or not a new reservation is being made, or a current one is being refreshed
	 */
	private void makeReservation(ArrayList<Reservation> res) {
		Reservation current = res.remove(0);		

		getReservations().add(current);
		if(PeopleMover.DEBUGGING)
			System.out.println("Made a reservation for pod " + current.getPod() + " with timewindow " + current.getTime() + ".\n"
					+ "Number of reservations for this station at " + this.getPosition() +": "+ this.getReservations().size());
		
		// If we've reached the end in the reservationlist (= this station is the intended destination): Start rebuilding a list
		// of actual reservations for the pod to know about. Inform the pod at the end (when no previous station is available).
		if(res.isEmpty()) {
			ArrayList<Reservation> ret = new ArrayList<>();
			ret.add(current);
			// If there is a previous station, forward the sequence
			if(current.getPrevStation() != null)
				current.getPrevStation().sendConfirmation(ret);
			// If not: the list only contains one station: it's current position.
			else {
				current.getStation().getPod().confirmReservations(ret);
				if(PeopleMover.DEBUGGING)
					System.err.println("Exploring else-branch. Shouldn't happen?");
			}
		// Forward this ant.	
		} else {
			sendReservationAnt(res, current.getTime().begin() + BUFFER_TIME);	
		}
	}
	
	/**
	 * Rebuild the list of reservations to be sent back to the pod.
	 * 
	 * @param res - The list of reservations to be completed
	 */
	public void sendConfirmation(ArrayList<Reservation> res) {
		
		// Get the pod this reservation sequence is intended for and find the reservation for it in this station.
		Pod p = res.get(0).getPod();
		Reservation correctReservation = null;
		for(Reservation r : getReservations()) {
			if (r.getPod() == p) {
				correctReservation = r;
			}
		}
		res.add(correctReservation);
		
		// If we have arrived: forward the reservations on to the pod and terminate.
		if(correctReservation.getPod() == getPod()) {
			getPod().confirmReservations(res);
			return;
		}
		
		// Forward the list on to the previous station in the sequence.
		correctReservation.getPrevStation().sendConfirmation(res);
	}
	
	/**
	 * Create a RoadSign using a feasibility ant, and forward the ant onto a random neighbour,
	 * but only if there are any hops left.
	 * 
	 * @param previous - The RoadSign issued by the previous station.
	 */
	private void makeRoadsign(RoadSign previous) {
		RoadSign sign = null;
		int hops = previous.getHops();
		boolean updated = false;
		
		// If a roadsign already exists with the same endStation: reset it's strength and update the hops correctly. 
		for(RoadSign rs : getRoadsigns()) {
			if(rs.getEndStation() == previous.getEndStation()) {
				rs.setStrength(1);
				if(rs.getHops() < hops) {
					hops = rs.getHops();
				}
				updated  = true;
			}
		}
		
		// Set the details
		sign = new RoadSign();
		sign.setHops(hops - 1);
		sign.setEndStation(previous.getEndStation());
		
		// If none was updated, add it to the current list of RoadSigns for this station.
		if(!updated)
			getRoadsigns().add(sign);

		// If there are any hops left: forward.
		if(hops > 0) {
			for(Station s : getNeighbours()){
				s.receiveRoadSignAnt(sign);
			}
		}
	}

	/**
	 * Retrieve a timewindow that is usable for a new reservation as close to the specified time.
	 * 
	 * @param time - The time at which a pod wishes to visit.
	 * @return TimeWindow - The timewindow that can be reserved.
	 */
	public TimeWindow checkPossibleReservationTime(long time) {
		if(getReservations().isEmpty())
			return TimeWindow.create(time, time + RESERVATION_TIME);
		Reservation lastRes = getReservations().get(getReservations().size() - 1);
		if(time > lastRes.getTime().end())
			return TimeWindow.create(time, time + RESERVATION_TIME);
		else 
			return TimeWindow.create(lastRes.getTime().end(), lastRes.getTime().end() + RESERVATION_TIME);	
	}

	/**
	 * GETTERS AND SETTERS.
	 */
	
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

	public ArrayList<LoadingDock> getLoadingDocks() {
		return loadingDocks;
	}

	public void setLoadingDocks(ArrayList<LoadingDock> loadingDocks) {
		this.loadingDocks = loadingDocks;
	}	
}
