package rinsim;

import java.awt.AWTException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class Experiment {
	
	public static final long DURATION = 72000000; // 20 hours in ms
	// Are we currently using the sophisticated task planning algorithm?
	public static final boolean ADVANCED_PLANNING = false;
	// The maximal number of users on the graph at any time. Can be overridden by NUM_USERS.
	private static final int MAX_USERS = 100;
	// The delivery deadline that we should try to meet for each user.
	private static final int DELIVERY_DEADLINE = 7200000; // 2 hours
	// The path where results should be written to.
	private static final String path = "results_basic_planning.txt";
	// The lowest spawn rate for the experiment
	private static final double lowRate = 0.001;
	// The highest spawn rate for the experiment
	private static final double highRate = 0.05;
	// Step size for the experiment
	private static final double stepSize = 0.005;
	
	public static void main(String[] args) throws URISyntaxException, IOException, AWTException {
		
		// Set params
		PeopleMover.setADVANCED_PLANNING(ADVANCED_PLANNING);
		PeopleMover.setMAX_USERS(MAX_USERS);
		PeopleMover.setDELIVERY_DEADLINE(DELIVERY_DEADLINE);
		PeopleMover.setEXPERIMENTING(true);
		
		// Create object and run
		for(double i = lowRate; i <= highRate + 0.001; i += stepSize) {
			PeopleMover.setUsersOnTime(0);
			PeopleMover.setDelays(new ArrayList<Double>());
			if(i != lowRate)
				PeopleMover.setSPAWN_RATE(i - 0.001);
			else
				PeopleMover.setSPAWN_RATE(i);
		
			PeopleMover pm = new PeopleMover();
			pm.run(DURATION, path);
		}
	}
}
