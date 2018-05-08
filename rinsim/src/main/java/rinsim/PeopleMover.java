package rinsim;

import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.View.Builder;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

public class PeopleMover {
	
	private static final int NUM_PODS = 5;
	private static final int NUM_LOADINGDOCKS = 3;
	private static final int NUM_USERS = 20;
	private static final int MAX_PODCAPACITY = 4;
	private static final int MAX_CHARGECAPACITY = 1; 
	
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
			simulator.register(new Station(p));
		}
		
		for(int i = 0; i < NUM_LOADINGDOCKS; i++) {
			simulator.register(new LoadingDock(roadModel.getRandomPosition(r), MAX_CHARGECAPACITY));
		}
		
		for(int i = 0;  i < NUM_USERS; i++) {
			simulator.register(new User
					(Parcel.builder (roadModel.getRandomPosition(r), 
					roadModel.getRandomPosition(r))
					.buildDTO(), 0, null));
		}
		
		for(int i = 0; i < NUM_PODS; i++) {
			simulator.register(new Pod(roadModel.getRandomPosition(r), MAX_PODCAPACITY, null));
		}
		System.out.println("running.");
		
		simulator.start();
	}

	  static View.Builder createGui() {
		    View.Builder view = View.builder()
		      .with(GraphRoadModelRenderer.builder())
		      .with(RoadUserRenderer.builder()		      
		    		  .withImageAssociation(User.class, "/littletom.png")
		    		  .withImageAssociation(Pod.class, "/graphics/flat/taxi-32.png")
		    		  .withImageAssociation(Station.class, "/graphics/flat/bus-stop-icon-32.png")
		    		  .withImageAssociation(LoadingDock.class, "/graphics/perspective/tall-building-64.png"))
		      .withTitleAppendix("People Mover 2000");
		    return view;
		  }
	
}
