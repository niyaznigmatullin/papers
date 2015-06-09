package ru.ifmo.steady.inds2;

/**
 * Created with IntelliJ IDEA.
 * User: niyaz
 * Date: 6/9/15
 * Time: 3:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class CrowdingPoint {
    double x;
    double y;

    static final CrowdingPoint ORIGIN = new CrowdingPoint(0, 0);


    public CrowdingPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double distanceSquared(CrowdingPoint p) {
        double dx = x - p.x;
        double dy = y - p.y;
        return dx * dx + dy * dy;
    }

    public CrowdingPoint subtract(CrowdingPoint p) {
        return new CrowdingPoint(x - p.x, y - p.y);
    }
}
