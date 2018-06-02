package rinsim;

import java.util.ArrayList;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;

public class LoadingDock extends Depot {
	
	private Point position;
	private Pod pod;
	ArrayList<Station> neighbours = new ArrayList<>();

	public LoadingDock(Point position, int cap) {
		super(position);
		setPosition(position);
		setCapacity(cap);
		setPod(null);
		
	}
	
	/**
	 * Make a reservation (the quickest if there are multiple options) for the pod to leave the loadingdock.
	 * 
	 * @param pod
	 * @return Reservation
	 */
	public Reservation leave(Pod pod, TimeLapse time) {
		Station bestStation = getNeighbours().get(0);
		TimeWindow bestTime = bestStation.checkPossibleReservationTime(time.getTime());
		
		// Determine which neighbour is the quickest getaway.
		for(Station s : getNeighbours()) {
			if(s.checkPossibleReservationTime(time.getTime()).begin() < bestTime.begin())
				bestTime = s.checkPossibleReservationTime(time.getTime());
				bestStation = s;
		}
		
		// Make a reservation, and add it to the station.
		Reservation r = new Reservation(bestStation, null, bestTime, pod, bestTime.end());
		bestStation.getReservations().add(r);
		
		return r;
	}

	/**
	 * Getters and setters
	 */
	
	public ArrayList<Station> getNeighbours() {
		return neighbours;
	}

	public void setNeighbours(ArrayList<Station> neighbours) {
		this.neighbours = neighbours;
	}

	public Point getPosition() {
		return position;
	}

	public void setPosition(Point position) {
		this.position = position;
	}

	public Pod getPod() {
		return pod;
	}

	public void setPod(Pod pod) {
		this.pod = pod;
	}
	

}
