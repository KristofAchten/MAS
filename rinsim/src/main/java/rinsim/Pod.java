package rinsim;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.measure.unit.SystemOfUnits;

import org.omg.CORBA.Current;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;

class Pod extends Vehicle {
	private static final int START_HOP_COUNT = 5;
	private static final int RESERVATION_DIF = 20000; 

	private ArrayList<Reservation> desire = new ArrayList<>();
	private ArrayList<ArrayList<Station>> intentions = new ArrayList<>();
	private ArrayList<User> passengers = new ArrayList<>();
	private Station current; 
	private Queue<Point> movingQueue = new LinkedList<Point>();
	private TimeWindow currentWindow = null;
	Random r = new Random();
	
	private static final double SPEED = 100d;
	
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
		
		if(!movingQueue.isEmpty() && currentWindow.isIn(System.currentTimeMillis())) {
			//System.out.println(movingQueue +", "+rm.getPosition(this));
			rm.followPath(this, movingQueue, time);
		}
		
		// If at station, set current station
		if(!rm.getObjectsAt(this, Station.class).isEmpty()) {
			current = rm.getObjectsAt(this, Station.class).iterator().next();
			current.setPod(this);
		} else {
			if (current != null)
				current.setPod(null);
			current = null;
			return;
		}
		
		// If no desire is active and there are no passengers: send out exploration ends using roadsign info
		if(getDesire().isEmpty() && movingQueue.isEmpty()) {
			//System.out.println("here is movingQueue: " + movingQueue);
			Station dest = null;
			if(current.getPassengers().isEmpty() && !current.getRoadsigns().isEmpty()) {
				//System.out.println("jappens 1");
				ArrayList<RoadSign> rs = current.getRoadsigns();
				if(!rs.isEmpty()) {
					Collections.sort(rs);
					dest = rs.get(0).getEndStation();
				}
			} else if(!current.getPassengers().isEmpty()) {
				System.out.println("jappens 2");
				User u = current.getPassengers().get(0);
				dest = u.getDestination();
			} else {
				System.out.println("jappens 3");
				int n = r.nextInt(current.getNeighbours().size());
				dest = current.getNeighbours().get(n);
			}
			
			//System.out.println("destination: " + dest.getPosition());
			//System.out.println("pod position: " + rm.getPosition(this));
			// Fetch the intentions to the destination and make a desire from the shortest one.
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
					//System.out.println("intention");
					for(Station s: curBest) {
						//System.out.println(s.getPosition()+ ", ");
					}
					makeReservations(curBest);
				}
			}
		}
		
		// If there are no planned moves, and there is a desire, add a move from the desire.
		if(movingQueue.isEmpty() && !getDesire().isEmpty()) {
			if(getDesire().get(0).getTime().end() < System.currentTimeMillis() + RESERVATION_DIF) {
				refreshReservations();
			} 
			Reservation r = getDesire().remove(0);
			//System.out.println("movingqueue: "+r.getStation().getPosition());
			currentWindow = r.getTime();
			movingQueue.add(r.getStation().getPosition());
			current.setPod(null);
			current = null;
			return;
		}
		
		// Remove users that have arrived.
		ArrayList<User> toRemove = new ArrayList<>();
		for(User u : getPassengers()) {
			if(u.getDestination() == current) {
				toRemove.add(u);
				pm.deliver(this, u, time);
				System.out.println("Ik zie een driekwartsbroek...");
			}
		}
		getPassengers().removeAll(toRemove);
		
		// Embark new users
		ArrayList<User> toEmbark = new ArrayList<>();
		for(User u : current.getPassengers()) {
			Station dest = u.getDestination();
			for(Reservation r : this.desire) {
				if(getPassengers().size() < getCapacity() && r.getStation() == dest) {
					getPassengers().add(u);
					toEmbark.add(u);
				}
			}
		}
		for(User us : toEmbark) {
			pm.pickup(this, us, time);
			current.embarkUser(us);
		}
	}

	private void makeReservations(ArrayList<Station> curBest) {
		ArrayList<Reservation> res = new ArrayList<Reservation>();
		Station prev = null;
		for(Station s : curBest) {
			res.add(new Reservation(s, prev, null, 0, this));
			prev = s;
		}
		current.receiveReservationAnt(res, System.currentTimeMillis(), false);
	}

	public void confirmReservations(ArrayList<Reservation> res) {
		Collections.reverse(res);
		setDesire(res);
	}
	
	public void refreshReservations() {
		getDesire().add(0, new Reservation(current, null, currentWindow, 0, this));
		System.out.println("refreshing for desire: ");
		for(Reservation r: getDesire()) {
			System.out.println(r.getStation().getPosition());
		}
		System.out.println("current station: " + current.getPosition());
		current.receiveReservationAnt(getDesire(), System.currentTimeMillis(), true);
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
