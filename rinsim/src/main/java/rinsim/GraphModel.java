package rinsim;

import com.github.rinde.rinsim.geom.Connection;
import com.github.rinde.rinsim.geom.ConnectionData;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.TableGraph;

public class GraphModel {
	
	public GraphModel() {};
	
	public Graph<ConnectionData> getGraph() {
		Point p1 = new Point(0, 2);
		Point p2 = new Point(5, 3);
		Point p3 = new Point(13, 5);
		Point p4 = new Point(1, 8);
		Point p5 = new Point(4, 7);
		Point p6 = new Point(7, 8);
		Point p7 = new Point(10, 6);
		Point p8 = new Point(14, 9);
		Point p9 = new Point(6, 10);
		Point p10 = new Point(7, 11);
		Point p11 = new Point(10, 10);
		Point p12 = new Point(16, 12);
		Point p13 = new Point(10, 15);
		Point p14 = new Point(11, 16);
		Point p15 = new Point(14, 15);
		Point p16 = new Point(14, 19);
		Point p17 = new Point(11, 18);
		Point p18 = new Point(8, 5);
		Point p19 = new Point(4, 15);
		Point p20 = new Point(6, 14);
		
		
		Graph<ConnectionData> g = new TableGraph<>();
		g.addConnection(p1, p2);
		g.addConnection(p1, p4);
		g.addConnection(p2, p4);
		g.addConnection(p2, p5);
		g.addConnection(p2, p6);
		g.addConnection(p2, p18);
		g.addConnection(p2, p3);
		g.addConnection(p3, p18);
		g.addConnection(p3, p8);
		g.addConnection(p4, p9);
		g.addConnection(p5, p9);
		g.addConnection(p6, p9);
		g.addConnection(p6, p7);
		g.addConnection(p6, p8);
		g.addConnection(p7, p8);
		g.addConnection(p8, p11);
		g.addConnection(p8, p15);
		g.addConnection(p8, p12);
		g.addConnection(p9, p10);
		g.addConnection(p10, p11);
		g.addConnection(p11, p13);
		g.addConnection(p12, p15);
		g.addConnection(p12, p16);
		g.addConnection(p13, p14);
		g.addConnection(p14, p15);
		g.addConnection(p15, p16);
		g.addConnection(p15, p17);
		g.addConnection(p16, p17);
		g.addConnection(p4, p19);
		g.addConnection(p19, p17);
		g.addConnection(p20, p4);
		g.addConnection(p20, p17);
		
		// Make the roads bidirectional
		for(Connection<ConnectionData> c : g.getConnections()) {
			g.addConnection(c.to(), c.from());
		}
		return g;
	}
}
