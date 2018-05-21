package rinsim;

import com.github.rinde.rinsim.geom.Connection;
import com.github.rinde.rinsim.geom.ConnectionData;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.TableGraph;

public class GraphModel {
	
	public GraphModel() {};
	
	public Graph<ConnectionData> getGraph1() {
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
	
	public Graph<ConnectionData> getGraph2() {
		Point p1 = new Point(0, 0);
		Point p2 = new Point(0, 3.5);
		Point p3 = new Point(0, 6.8);
		Point p4 = new Point(1.2, 2);
		Point p5 = new Point(1.2, 5);
		Point p6 = new Point(2.4, 1);
		Point p7 = new Point(2.3, 3.6);
		Point p8 = new Point(2.2, 6.1);
		Point p9 = new Point(4.1, 0.1);
		Point p10 = new Point(4.1, 1.5);
		Point p11 = new Point(4.4, 3);
		Point p12 = new Point(4.3, 4.4);
		Point p13 = new Point(4, 5.8);
		Point p14 = new Point(5.6, 1.5);
		Point p15 = new Point(6.1, 3.8);
		Point p16 = new Point(5.5, 5.2);
		Point p17 = new Point(6.1, 6.5);
		Point p18 = new Point(7.6, 0.9);
		Point p19 = new Point(7.2, 2.6);
		Point p20 = new Point(7, 5.1);
		Point p21 = new Point(8.1, 6.5);
		Point p22 = new Point(9.8, 0.3);
		Point p23 = new Point(9.4, 2.1);
		Point p24 = new Point(8.8, 3.9);
		Point p25 = new Point(9.4, 5.7);
		Point p26 = new Point(11.5, 0.1);
		Point p27 = new Point(11.3, 1.8);
		Point p28 = new Point(10.6, 3.4);
		Point p29 = new Point(10.9, 4.9);
		Point p30 = new Point(10.9, 6.3);
		Point p31 = new Point(12.6, 1.4);
		Point p32 = new Point(12.5, 3.5);
		Point p33 = new Point(12.4, 6.3);
		Point p34 = new Point(13.6, 0.2);
		Point p35 = new Point(13.7, 5.6);
		Point p36 = new Point(13.7, 7);
		
		Graph<ConnectionData> g = new TableGraph<>();
		g.addConnection(p1, p2);
		g.addConnection(p1, p4);
		g.addConnection(p1, p6);
		g.addConnection(p2, p4);
		g.addConnection(p2, p7);
		g.addConnection(p2, p5);
		g.addConnection(p2, p3);
		g.addConnection(p3, p5);
		g.addConnection(p3, p8);
		g.addConnection(p4, p6);
		g.addConnection(p5, p7);
		g.addConnection(p5, p8);
		g.addConnection(p6, p9);
		g.addConnection(p6, p10);
		g.addConnection(p6, p11);
		g.addConnection(p7, p11);
		g.addConnection(p7, p12);
		g.addConnection(p8, p12);
		g.addConnection(p9, p10);
		g.addConnection(p9, p14);
		g.addConnection(p10, p14);
		g.addConnection(p10, p11);
		g.addConnection(p11, p12);
		g.addConnection(p12, p13);
		g.addConnection(p12, p15);
		g.addConnection(p13, p16);
		g.addConnection(p13, p17);
		g.addConnection(p14, p18);
		g.addConnection(p15, p16);
		g.addConnection(p15, p19);
		g.addConnection(p15, p20);
		g.addConnection(p17, p20);
		g.addConnection(p17, p21);
		g.addConnection(p18, p19);
		g.addConnection(p18, p22);
		g.addConnection(p20, p21);
		g.addConnection(p21, p25);
		g.addConnection(p22, p23);
		g.addConnection(p22, p26);
		g.addConnection(p23, p24);
		g.addConnection(p23, p27);
		g.addConnection(p24, p25);
		g.addConnection(p24, p28);
		g.addConnection(p25, p30);
		g.addConnection(p26, p34);
		g.addConnection(p26, p31);
		g.addConnection(p27, p31);
		g.addConnection(p27, p28);
		g.addConnection(p28, p29);
		g.addConnection(p28, p32);
		g.addConnection(p29, p32);
		g.addConnection(p29, p30);
		g.addConnection(p30, p33);
		g.addConnection(p31, p34);
		g.addConnection(p33, p35);
		g.addConnection(p33, p36);
		
		// Make the roads bidirectional
		for(Connection<ConnectionData> c : g.getConnections()) {
			g.addConnection(c.to(), c.from());
		}
		return g;
	}
}
