package rinsim;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Connection;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.View.Builder;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

public class PeopleMover {
	
	// Are we currently debugging? -> will enable informative printouts.
	public static final boolean DEBUGGING = false;
	// Show experiment results. Best to not use this together with the DEBUGGING flag enabled because of spam.
	public static final boolean EXPERIMENTING = false;
	// Are we currently using the sophisticated task planning algorithm?
	public static final boolean ADVANCED_PLANNING = false;
	// The number of users at the start of the simulation.
	private static final int NUM_USERS = 2;
	// The number of seats per pod.
	private static final int MAX_PODCAPACITY = 4;
	// The number of charging spaces per charging dock.
	private static final int MAX_CHARGECAPACITY = 1; 
	// The probability of a new user spawning.
	private static final double SPAWN_RATE = 0.01;
	// The maximal number of users on the graph at any time. Can be overridden by NUM_USERS.
	private static final int MAX_USERS = 5;
	// The delivery deadline that we should try to meet for each user.
	private static final int DELIVERY_DEADLINE = 120000; // 2 minutes
	
	// The starting positions that contain a loading dock and spawn a pod initially.
	private static final Point[] startPos = {new Point(0, 0), new Point(7.2, 2.6), new Point(13.7, 7)};
	
	private ArrayList<Station> stations = new ArrayList<>();
	private ArrayList<LoadingDock> loadingDocks = new ArrayList<>();
	public static int usersOnTime = 0;
	public static ArrayList<Double> delays = new ArrayList<>();
	
	
	
	public static void main(String[] args) throws URISyntaxException, IOException {
		PeopleMover pm = new PeopleMover();
		pm.run();
	}
	
	PeopleMover() {}
	
	private void run() throws URISyntaxException, IOException {
	
		// Create the graph.
		GraphModel gm = new GraphModel();
		Builder view = createGui();

		final Simulator simulator = Simulator.builder()
			      .addModel(RoadModelBuilders.staticGraph(gm.getGraph2()))
			      .addModel(DefaultPDPModel.builder())
			      .addModel(view)
			      .build();
		
		final RandomGenerator r = simulator.getRandomGenerator();
		final RoadModel roadModel = simulator.getModelProvider().getModel(RoadModel.class);
		
		// Create a station on every vertex of the graph, except for the loading dock positions.
		for(Point p : gm.getGraph2().getNodes()) {
			if(!Arrays.asList(startPos).contains(p)) {
				Station s = new Station(p);
				addStation(s);
				simulator.register(s);
			}
		}
		
		// Create a set amount of loading docks at predefined positions (in the startPos array).
		for(int i = 0; i < startPos.length; i++) {
			LoadingDock l = new LoadingDock(startPos[i], MAX_CHARGECAPACITY);
			getLoadingDocks().add(l);
			simulator.register(l);
		}
		
		// Set the neighbours for each station. Assume that each station has maximally one loading dock as neighbour.
		for(Connection<?> c : gm.getGraph2().getConnections()) {
			Station s1 = getStationAtPoint(c.from());
			Station s2 = getStationAtPoint(c.to());
			LoadingDock d1 = getLoadingDockAtPoint(c.from());
			LoadingDock d2 = getLoadingDockAtPoint(c.to());

			if(s1 == null) {
				d1.getNeighbours().add(s2);
				s2.getLoadingDocks().add(d1);
			} else if(s2 == null) {
				d2.getNeighbours().add(s1);
				s1.getLoadingDocks().add(d2);
			} else {	
				s1.getNeighbours().add(s2);
				s2.getNeighbours().add(s1);
			}
		}
		
		// Spawn in the original number of users.
		for(int i = 0;  i < NUM_USERS; i++) {
			addRandomUser(roadModel, r, simulator);
		}
		
		// Spawn in the pods at the predefined locations.
		for(int i = 0; i < startPos.length; i++) {
			Point pos = startPos[i];
			LoadingDock s = getLoadingDockAtPoint(pos);
			Pod p = new Pod(pos, MAX_PODCAPACITY, s);
			s.setPod(p);
			simulator.register(p);
		}
		
		// Handle ticks
		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse timeLapse) {
				
				// Spawn a new user (sometimes).
				if(r.nextDouble() < SPAWN_RATE && roadModel.getObjectsOfType(User.class).size() < MAX_USERS) {
					addRandomUser(roadModel, r, simulator);
				}
				
				// Per station:
				for(Station s : getStations()) {
					
					// Update strengths and remove expired roadsigns.
					ArrayList<RoadSign> toRemoveRs = new ArrayList<>();
					for(RoadSign rs : s.getRoadsigns()) {
						double str = rs.getStrength();
						
						if(str < 0.001) {
							toRemoveRs.add(rs);
						} else {
							rs.setStrength(str/2);
						}
					}
					s.getRoadsigns().removeAll(toRemoveRs);
					
					// Remove expired reservations
					ArrayList<Reservation> toRemoveRes = new ArrayList<>();
					for(Reservation r : s.getReservations()) {
						if(r.getExpirationTime() < System.currentTimeMillis()) {
							toRemoveRes.add(r);
						}
					}
					s.getReservations().removeAll(toRemoveRes);
					
					// For each user currently at a station: send out feasability ants pointing towards the station that the user is at.
					if(!s.getPassengers().isEmpty()) {
						for(int i = 0; i < s.getPassengers().size(); i++) {
							RoadSign rs = new RoadSign();
							rs.setEndStation(s);
							s.receiveRoadSignAnt(rs);
						}
					}
				}		
		}			
			@Override
			public void afterTick(TimeLapse timeLapse) {}
		});
		
		simulator.start();
	}

	/**
	 * Create a GUI for ease of mind.
	 * 
	 * @return The builder-instance used to create the GUI.
	 */
	 public static View.Builder createGui() {
		    View.Builder view = View.builder()
		      .with(GraphRoadModelRenderer.builder())
		      .with(RoadUserRenderer.builder()		      
		    		  .withImageAssociation(User.class, "/littlewouter.png")
		    		  .withImageAssociation(Pod.class, "/graphics/flat/taxi-32.png")
		    		  .withImageAssociation(Station.class, "/graphics/flat/bus-stop-icon-32.png")
		    		  .withImageAssociation(LoadingDock.class, "/graphics/perspective/tall-building-64.png"))
		      .withTitleAppendix("People Mover 2000");
		    return view;
		  }

	public ArrayList<Station> getStations() {
		return stations;
	}

	public void addStation(Station station) {
		this.stations.add(station);
	}
	
	/**
	 * Returns the station at a given point in the graph (if there is one).
	 * 
	 * @param p - The point
	 * @return Station at position p
	 */
	public Station getStationAtPoint(Point p) {
		for(Station s : getStations()) 	
			if(s.getPosition().equals(p)) 
				return s;
		
		return null;
	}
	
	/**
	 * Returns the loading dock at a given point in the graph (if there is one).
	 * @param p - The point
	 * @return LoadingDock at position p
	 */
	public LoadingDock getLoadingDockAtPoint(Point p) {
		for(LoadingDock d : getLoadingDocks())
			if(d.getPosition().equals(p))
				return d;
		
		return null;
	}
	
	/**
	 * Add a user at a random position in the roadmodel. Update all necessary elements.
	 * 
	 * @param roadModel - The roadModel to add the user in
	 * @param r - The random generator used by the roadModel
	 * @param simulator - The specific simulation instance
	 */
	public void addRandomUser(RoadModel roadModel, RandomGenerator r, Simulator simulator) {
		Point startPosition = roadModel.getRandomPosition(r);
		
		// Assure that the user does not spawn on a loadingdock, and that the station where it spawns does not have a pod on it.
		while (Arrays.asList(startPos).contains(startPosition) || getStationAtPoint(startPosition).getPod() != null) {
			startPosition = roadModel.getRandomPosition(r);
		}
		Station start = (Station) getStationAtPoint(startPosition);

		// Find a random endposition that is different from the starting position and is not a loadingdock.
		Point endpos = roadModel.getRandomPosition(r);
		while(startPosition.equals(endpos) && Arrays.asList(startPos).contains(endpos))
			endpos = roadModel.getRandomPosition(r);
		
		User u = new User
				(Parcel.builder (startPosition, endpos)
				.buildDTO(), System.currentTimeMillis() + DELIVERY_DEADLINE, (Station) getStationAtPoint(endpos));
		
		// Add the new user to the station it spawned in.
		start.getPassengers().add(u);
		
		if(DEBUGGING)
			System.out.println("Added a user to station "+start+" at position "+startPosition+". His destination is the station at position "+endpos);
		
		simulator.register(u);
	}

	public ArrayList<LoadingDock> getLoadingDocks() {
		return loadingDocks;
	}

	public void setLoadingDocks(ArrayList<LoadingDock> loadingDocks) {
		this.loadingDocks = loadingDocks;
	}
	
	public static ArrayList<Double> getDelays() {
		return delays;
	}
	
	public static int getUsersOnTime() {
		return usersOnTime;
	}

	public static void setUsersOnTime(int usersOnTime) {
		PeopleMover.usersOnTime = usersOnTime;
	}
}
