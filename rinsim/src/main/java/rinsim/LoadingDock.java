package rinsim;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.geom.Point;

public class LoadingDock extends Depot {

	public LoadingDock(Point position, int cap) {
		super(position);
		setCapacity(cap);
	}

}