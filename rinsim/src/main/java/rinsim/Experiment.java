package rinsim;

import java.io.IOException;
import java.net.URISyntaxException;

public class Experiment {
	
	public static final long DURATION = 72000000; // 20 hours in ms
	// Are we currently using the sophisticated task planning algorithm?
	public static final boolean ADVANCED_PLANNING = true;
	// The probability of a new user spawning.
	private static final double SPAWN_RATE = 0.05;
	// The maximal number of users on the graph at any time. Can be overridden by NUM_USERS.
	private static final int MAX_USERS = 100;
	// The delivery deadline that we should try to meet for each user.
	private static final int DELIVERY_DEADLINE = 7200000; // 2 hours
	
	public static void main(String[] args) throws URISyntaxException, IOException {
		// Set params
		PeopleMover.setADVANCED_PLANNING(ADVANCED_PLANNING);
		PeopleMover.setSPAWN_RATE(SPAWN_RATE);
		PeopleMover.setMAX_USERS(MAX_USERS);
		PeopleMover.setDELIVERY_DEADLINE(DELIVERY_DEADLINE);
		
		// Create object and run
		PeopleMover pm = new PeopleMover();
		pm.run(DURATION);


	}

}
