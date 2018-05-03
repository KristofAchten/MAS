package rinsim;

import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;

class Pod extends Vehicle {

	private static final double SPEED = 1000d;
	
	protected Pod(Point startPos, int cap) {
		super(VehicleDTO.builder()
				.capacity(cap)
				.startPosition(startPos)
				.speed(SPEED)
				.build());
	}

	@Override
	protected void tickImpl(TimeLapse time) {
	}

}
