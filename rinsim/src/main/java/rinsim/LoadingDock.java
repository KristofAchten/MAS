package rinsim;

import java.util.ArrayList;

import com.github.rinde.rinsim.core.model.pdp.Depot;
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
	public Reservation leave(Pod pod) {
		Station bestStation = getNeighbours().get(0);
		TimeWindow bestTime = bestStation.checkPossibleReservationTime(System.currentTimeMillis());
		
		for(Station s : getNeighbours()) {
			if(s.checkPossibleReservationTime(System.currentTimeMillis()).begin() < bestTime.begin())
				bestTime = s.checkPossibleReservationTime(System.currentTimeMillis());
				bestStation = s;
		}
		
		Reservation r = new Reservation(bestStation, null, bestTime, 999999999, pod);
		
		bestStation.getReservations().add(r);
		
		return r;
	}

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
