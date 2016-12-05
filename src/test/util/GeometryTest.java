package util;

import org.junit.Assert;
import org.junit.Test;

import javax.vecmath.Vector2f;

public class GeometryTest {

    @Test
    public void testLinesIntersect() {
        Vector2f a = new Vector2f(0, 0);
        Vector2f b = new Vector2f(0, 5);

        Vector2f c = new Vector2f(-2, 2);
        Vector2f d = new Vector2f(2, 2);

        Vector2f e = new Vector2f(5, 0);
        Vector2f f = new Vector2f(5, 5);

        Assert.assertTrue(Geometry.linesIntersect(a, b, c, d));
        Assert.assertFalse(Geometry.linesIntersect(a, b, e, f));
    }
    @Test
    public void testPointInTriangle() {
        Vector2f a = new Vector2f(0, 0);
        Vector2f b = new Vector2f(0, 5);
        Vector2f c = new Vector2f(5, 0);

        Assert.assertTrue(Geometry.pointInTriangle(new Vector2f(0.0001f, 0.0001f), a, b, c));
        Assert.assertTrue(Geometry.pointInTriangle(new Vector2f(0.0001f, 4.99f), a, b, c));
        Assert.assertTrue(Geometry.pointInTriangle(new Vector2f(0.0001f, 4.99f), a, b, c));
        Assert.assertTrue(Geometry.pointInTriangle(new Vector2f(1, 1), a, b, c));

        Assert.assertFalse(Geometry.pointInTriangle(new Vector2f(-1, 0.0001f), a, b, c));
        Assert.assertFalse(Geometry.pointInTriangle(new Vector2f(6, 0.0001f), a, b, c));
    }

    @Test
    public void testTriangleIntersect() {
        Vector2f[] a = { new Vector2f(0, 0),
                                 new Vector2f(0, 5),
                                 new Vector2f(5, 0) };

        Vector2f[] b = { new Vector2f(0, 4),
                                 new Vector2f(0, 9),
                                 new Vector2f(5, 4) };

        Vector2f[] c = { new Vector2f(0, 6),
                                 new Vector2f(0, 11),
                                 new Vector2f(5, 6) };

        Assert.assertTrue(Geometry.triangleIntersect(a, b));
        Assert.assertFalse(Geometry.triangleIntersect(a, c));
    }
}
