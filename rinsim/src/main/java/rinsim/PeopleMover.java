package rinsim;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.View.Builder;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

public class PeopleMover {
	
	private static final int NUM_PODS = 5;
	private static final int NUM_LOADINGDOCKS = 3;
	private static final int NUM_USERS = 30;
	private static final int MAX_PODCAPACITY = 4;
	private static final int MAX_CHARGECAPACITY = 1; 
	
	public static void main(String[] args) {
		PeopleMover pm = new PeopleMover();
		pm.run();
	}
	
	PeopleMover() {}
	
	private void run() {
		
		GraphModel gm = new GraphModel();
		Builder view = createGui();

		final Simulator simulator = Simulator.builder()
			      .addModel(RoadModelBuilders.staticGraph(gm.getGraph()))
			      .addModel(DefaultPDPModel.builder())
			      .addModel(view)
			      .build();
		
		final RandomGenerator r = simulator.getRandomGenerator();
		final RoadModel roadModel = simulator.getModelProvider().getModel(RoadModel.class);
		
		for(int i = 0; i < NUM_PODS; i++) {
			simulator.register(new Pod(roadModel.getRandomPosition(r), MAX_PODCAPACITY));
		}
		
		for(int i = 0; i < NUM_LOADINGDOCKS; i++) {
			simulator.register(new LoadingDock(roadModel.getRandomPosition(r), MAX_CHARGECAPACITY));
		}
		
		for(int i = 0;  i < NUM_USERS; i++) {
			simulator.register(new User
					(Parcel.builder (roadModel.getRandomPosition(r), 
					roadModel.getRandomPosition(r))
					.buildDTO()));
		}
		System.out.println("running.");
		
		simulator.start();
	}

	  static View.Builder createGui() {

		    View.Builder view = View.builder()
		      .with(GraphRoadModelRenderer.builder())
		      .with(RoadUserRenderer.builder())
		      .withTitleAppendix("People Mover 2000");
		    return view;
		  }
	
}
