package rinsim;

import java.util.ArrayList;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.geom.Point;

public class LoadingDock extends Depot {
	
	private Point position;
	private Pod pod;
	ArrayList<Station> neighbours = new ArrayList<>();

	public LoadingDock(Point position, int cap) {
		super(position);
		setPosition(position);
		setCapacity(cap);
		
	}

	public Point getPosition() {
		return position;
	}

	public void setPosition(Point position) {
		this.position = position;
	}
	

}
