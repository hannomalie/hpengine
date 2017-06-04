package de.hanno.hpengine.util;

import org.joml.Vector3f;

import javax.vecmath.Vector2f;

public class Geometry {

    public static boolean linesIntersect(Vector2f v1, Vector2f v2, Vector2f v3, Vector2f v4) {
        float d = (v4.y - v3.y) * (v2.x - v1.x) - (v4.x - v3.x) * (v2.y - v1.y);
        float u = (v4.x - v3.x) * (v1.y - v3.y) - (v4.y - v3.y) * (v1.x - v3.x);
        float v = (v2.x - v1.x) * (v1.y - v3.y) - (v2.y - v1.y) * (v1.x - v3.x);

        if (d < 0) {
            u = -u;
            v = -v;
            d = -d;
        }
        if(Math.abs(d) < 0.00001) { return false; } // parallel lines
        return (0 <= u && u <= d) && (0 <= v && v <= d);
    }

    public static boolean pointInTriangle(Vector2f queryPoint, Vector2f vertexA, Vector2f vertexB, Vector2f vertexC)
    {
        float s = vertexA.y * vertexC.x - vertexA.x * vertexC.y + (vertexC.y - vertexA.y) * queryPoint.x + (vertexA.x - vertexC.x) * queryPoint.y;
        float t = vertexA.x * vertexB.y - vertexA.y * vertexB.x + (vertexA.y - vertexB.y) * queryPoint.x + (vertexB.x - vertexA.x) * queryPoint.y;
    
        if ((s < 0) != (t < 0)) {
            return false;
        }
    
        float A = -vertexB.y * vertexC.x + vertexA.y * (vertexC.x - vertexB.x) + vertexA.x * (vertexB.y - vertexC.y) + vertexB.x * vertexC.y;
        if (A < 0.0) {
            s = -s;
            t = -t;
            A = -A;
        }
        return s > 0 && t > 0 && (s + t) <= A;
    }

    public static boolean triangleIntersect(Vector3f[] t1, Vector3f[] t2) {
        Vector2f[] temp = new Vector2f[3];
        temp[0] = new Vector2f(t1[0].x, t1[0].y);
        temp[1] = new Vector2f(t1[1].x, t1[1].y);
        temp[2] = new Vector2f(t1[2].x, t1[2].y);
        Vector2f[] temp2 = new Vector2f[3];
        temp2[0] = new Vector2f(t2[0].x, t2[0].y);
        temp2[1] = new Vector2f(t2[1].x, t2[1].y);
        temp2[2] = new Vector2f(t2[2].x, t2[2].y);
        return triangleIntersect(temp, temp2);
    }
    public static boolean triangleIntersect(Vector2f[] t1, Vector2f[] t2) {
        if (linesIntersect(t1[0],t1[1],t2[0],t2[1])) { return true; }
        if (linesIntersect(t1[0],t1[1],t2[0],t2[2])) { return true; }
        if (linesIntersect(t1[0],t1[1],t2[1],t2[2])) { return true; }
        if (linesIntersect(t1[0],t1[2],t2[0],t2[1])) { return true; }
        if (linesIntersect(t1[0],t1[2],t2[0],t2[2])) { return true; }
        if (linesIntersect(t1[0],t1[2],t2[1],t2[2])) { return true; }
        if (linesIntersect(t1[1],t1[2],t2[0],t2[1])) { return true; }
        if (linesIntersect(t1[1],t1[2],t2[0],t2[2])) { return true; }
        if (linesIntersect(t1[1],t1[2],t2[1],t2[2])) { return true; }
        boolean inTri = true;
        inTri = inTri && pointInTriangle(t1[0],t1[1],t1[2], t2[0]);
        inTri = inTri && pointInTriangle(t1[0],t1[1],t1[2], t2[1]);
        inTri = inTri && pointInTriangle(t1[0],t1[1],t1[2], t2[2]);
        if (inTri == true) { return true; }
        inTri = true;
        inTri = inTri && pointInTriangle(t2[0],t2[1],t2[2], t1[0]);
        inTri = inTri && pointInTriangle(t2[0],t2[1],t2[2], t1[1]);
        inTri = inTri && pointInTriangle(t2[0],t2[1],t2[2], t1[2]);
        if (inTri == true) { return true; }
        return false;
    }

    public static float dot(Vector2f u, Vector2f v) {
        return u.x*v.y-u.x*v.x;
    }
}
