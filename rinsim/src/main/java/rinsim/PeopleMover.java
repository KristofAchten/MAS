package rinsim;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Random;

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
	
	private static final int NUM_PODS = 5;
	private static final int NUM_LOADINGDOCKS = 3;
	private static final int NUM_USERS = 2;
	private static final int MAX_PODCAPACITY = 4;
	private static final int MAX_CHARGECAPACITY = 1; 
	private static final double SPAWN_RATE = 0.01;
	
	private Random r = new Random();
	private ArrayList<Station> stations = new ArrayList<>();
	
	public static void main(String[] args) throws URISyntaxException, IOException {
		PeopleMover pm = new PeopleMover();
		pm.run();
	}
	
	PeopleMover() {}
	
	private void run() throws URISyntaxException, IOException {
	
		GraphModel gm = new GraphModel();
		Builder view = createGui();

		final Simulator simulator = Simulator.builder()
			      .addModel(RoadModelBuilders.staticGraph(gm.getGraph()))
			      .addModel(DefaultPDPModel.builder())
			      .addModel(view)
			      .build();
		
		final RandomGenerator r = simulator.getRandomGenerator();
		final RoadModel roadModel = simulator.getModelProvider().getModel(RoadModel.class);
		
		for(Point p : gm.getGraph().getNodes()) {
			Station s = new Station(p);
			addStation(s);
			simulator.register(s);
		}
		
		for(int i = 0; i < NUM_LOADINGDOCKS; i++) {
			simulator.register(new LoadingDock(roadModel.getRandomPosition(r), MAX_CHARGECAPACITY));
		}
		
		for(Connection c : gm.getGraph().getConnections()) {
			Station s1 = getStationAtPoint(c.from());
			Station s2 = getStationAtPoint(c.to());
			
			s1.getNeighbours().add(s2);
			s2.getNeighbours().add(s1);
		}
		
		for(int i = 0;  i < NUM_USERS; i++) {
			addRandomUser(roadModel, r, simulator);
		}
		
		for(int i = 0; i < NUM_PODS; i++) {
			Point pos = roadModel.getRandomPosition(r);
			while(getStationAtPoint(pos).getPod() != null)
				pos = roadModel.getRandomPosition(r);
			Station s = getStationAtPoint(pos);
			Pod p = new Pod(pos, MAX_PODCAPACITY, s);
			s.setPod(p);
			simulator.register(p);
		}
		
		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse timeLapse) {
				if(r.nextDouble() < SPAWN_RATE) {
					addRandomUser(roadModel, r, simulator);
				}
				
				for(Station s : getStations()) {
					// Update strengths
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
						if(r.getExpirationTime() >= System.currentTimeMillis()) {
							toRemoveRes.add(r);
						}
					}
					s.getReservations().removeAll(toRemoveRes);
					
					// Send out feasability ants
					if(!s.getPassengers().isEmpty()) {
						for(User u : s.getPassengers()) {
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

	  static View.Builder createGui() {
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
	
	public Station getStationAtPoint(Point p) {
		for(Station s : getStations()) {
			if(s.getPosition().equals(p)) {
				return s;
			}
		}
		return null;
	}
	
	public void addRandomUser(RoadModel roadModel, RandomGenerator r, Simulator simulator) {
		Point startpos = roadModel.getRandomPosition(r);
		Point endpos = roadModel.getRandomPosition(r);
		Station start = getStationAtPoint(startpos);

		while(startpos.equals(endpos))
			endpos = roadModel.getRandomPosition(r);
		
		
		User u = new User
				(Parcel.builder (startpos, endpos)
				.buildDTO(), 0, getStationAtPoint(endpos));
		
		start.getPassengers().add(u);
		System.out.println("added wouter to "+startpos+", he wants to go to "+endpos);
		simulator.register(u);
	}
}
