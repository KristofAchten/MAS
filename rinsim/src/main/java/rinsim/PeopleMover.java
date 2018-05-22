package rinsim;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

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
	public static final boolean DEBUGGING = true;
	// The number of pods in the simulation.
	private static final int NUM_PODS = 3;
	// The number of loading docks in the road model.
	private static final int NUM_LOADINGDOCKS = 3;
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
	
	//TEMP
	private static final Point[] startPos = {new Point(0, 0), new Point(7.2, 2.6), new Point(13.7, 7)};
	
	private ArrayList<Station> stations = new ArrayList<>();
	
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
		
		// Create a station on every vertex of the graph.
		for(Point p : gm.getGraph2().getNodes()) {
			Station s = new Station(p);
			addStation(s);
			simulator.register(s);
		}
		
		// Create a set amount of loading docks at random positions. TODO: check that these are not identical?
		for(int i = 0; i < NUM_LOADINGDOCKS; i++) {
			simulator.register(new LoadingDock(roadModel.getRandomPosition(r), MAX_CHARGECAPACITY));
		}
		
		// Set the neighbours for each station.
		for(Connection<?> c : gm.getGraph2().getConnections()) {
			Station s1 = getStationAtPoint(c.from());
			Station s2 = getStationAtPoint(c.to());
			
			s1.getNeighbours().add(s2);
			s2.getNeighbours().add(s1);
		}
		
		// Spawn in the original number of users.
		for(int i = 0;  i < NUM_USERS; i++) {
			addRandomUser(roadModel, r, simulator);
		}
		
		// Spawn in the predefined number of pods at random, but different, locations.
		for(int i = 0; i < NUM_PODS; i++) {
			//Point pos = roadModel.getRandomPosition(r);
			Point pos = startPos[i];
			while(getStationAtPoint(pos).getPod() != null)
				pos = roadModel.getRandomPosition(r);
			Station s = getStationAtPoint(pos);
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
						
						if(str < 0.0001) {
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
					
					// For each user currently at a station: send out feasability ants pointing towards the current station.
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
	 * Returns the station at a given point in the graph.
	 * 
	 * @param p - The point
	 * @return Station at position p
	 */
	public Station getStationAtPoint(Point p) {
		for(Station s : getStations()) {
			if(s.getPosition().equals(p)) {
				return s;
			}
		}
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
		Point startpos = roadModel.getRandomPosition(r);
		Point endpos = roadModel.getRandomPosition(r);
		Station start = getStationAtPoint(startpos);

		// Find a random endposition that is different from the starting position.
		while(startpos.equals(endpos))
			endpos = roadModel.getRandomPosition(r);
		
		User u = new User
				(Parcel.builder (startpos, endpos)
				.buildDTO(), 0, getStationAtPoint(endpos));
		
		// Add the new user to the station it spawned in.
		start.getPassengers().add(u);
		
		if(DEBUGGING)
			System.out.println("Added a user to station "+start+" at position "+startpos+". His destination is the station at position "+endpos);
		
		simulator.register(u);
	}
}
