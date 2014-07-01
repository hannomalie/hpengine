package main.camera;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import main.octree.Box;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector3f;

public class Frustum {
	private static Logger LOGGER = getLogger();
	public static final int RIGHT   = 0;            // The RIGHT side of the frustum
    public static final int LEFT    = 1;            // The LEFT      side of the frustum
    public static final int BOTTOM  = 2;            // The BOTTOM side of the frustum
    public static final int TOP     = 3;            // The TOP side of the frustum
    public static final int BACK    = 4;            // The BACK     side of the frustum
    public static final int FRONT   = 5;            // The FRONT side of the frustum
    
    /**
     * The X value of the plane's normal
     */
    public static final int A = 0;
    /**
     * The Y value of the plane's normal
     */
    public static final int B = 1;
    /**
     * The Z value of the plane's normal
     */
    public static final int C = 2;
    /**
     * The distance the plane is from the origin
     */
    public static final int D = 3;
    
    /**
     * Pass side of the frustum and x,y,z,w selector to get value
     */
    public float[][] values = new float[6][4];
	
	Vector3f fc = new Vector3f(); // far plane center
	
	private FloatBuffer buffer = BufferUtils.createFloatBuffer(4*6);

	public Frustum(Camera camera) {
		calculate(camera);
		
	}
	
	public void calculate(Camera camera) {
		FloatBuffer buf = BufferUtils.createFloatBuffer(16);
		camera.getProjectionMatrix().store(buf);
		float[] proj = new float[16];
		buf.rewind();
		buf.get(proj);

		buf.rewind();
		camera.getViewMatrix().store(buf);
		float[] modl = new float[16];
		buf.rewind();
		buf.get(modl);
		
		float[] clip = new float[16];
		
		clip[ 0] = modl[ 0] * proj[ 0] + modl[ 1] * proj[ 4] + modl[ 2] * proj[ 8] + modl[ 3] * proj[12];
        clip[ 1] = modl[ 0] * proj[ 1] + modl[ 1] * proj[ 5] + modl[ 2] * proj[ 9] + modl[ 3] * proj[13];
        clip[ 2] = modl[ 0] * proj[ 2] + modl[ 1] * proj[ 6] + modl[ 2] * proj[10] + modl[ 3] * proj[14];
        clip[ 3] = modl[ 0] * proj[ 3] + modl[ 1] * proj[ 7] + modl[ 2] * proj[11] + modl[ 3] * proj[15];

        clip[ 4] = modl[ 4] * proj[ 0] + modl[ 5] * proj[ 4] + modl[ 6] * proj[ 8] + modl[ 7] * proj[12];
        clip[ 5] = modl[ 4] * proj[ 1] + modl[ 5] * proj[ 5] + modl[ 6] * proj[ 9] + modl[ 7] * proj[13];
        clip[ 6] = modl[ 4] * proj[ 2] + modl[ 5] * proj[ 6] + modl[ 6] * proj[10] + modl[ 7] * proj[14];
        clip[ 7] = modl[ 4] * proj[ 3] + modl[ 5] * proj[ 7] + modl[ 6] * proj[11] + modl[ 7] * proj[15];

        clip[ 8] = modl[ 8] * proj[ 0] + modl[ 9] * proj[ 4] + modl[10] * proj[ 8] + modl[11] * proj[12];
        clip[ 9] = modl[ 8] * proj[ 1] + modl[ 9] * proj[ 5] + modl[10] * proj[ 9] + modl[11] * proj[13];
        clip[10] = modl[ 8] * proj[ 2] + modl[ 9] * proj[ 6] + modl[10] * proj[10] + modl[11] * proj[14];
        clip[11] = modl[ 8] * proj[ 3] + modl[ 9] * proj[ 7] + modl[10] * proj[11] + modl[11] * proj[15];

        clip[12] = modl[12] * proj[ 0] + modl[13] * proj[ 4] + modl[14] * proj[ 8] + modl[15] * proj[12];
        clip[13] = modl[12] * proj[ 1] + modl[13] * proj[ 5] + modl[14] * proj[ 9] + modl[15] * proj[13];
        clip[14] = modl[12] * proj[ 2] + modl[13] * proj[ 6] + modl[14] * proj[10] + modl[15] * proj[14];
        clip[15] = modl[12] * proj[ 3] + modl[13] * proj[ 7] + modl[14] * proj[11] + modl[15] * proj[15];
        

        // This will extract the RIGHT side of the frustum
        values[RIGHT][A] = clip[ 3] - clip[ 0];
        values[RIGHT][B] = clip[ 7] - clip[ 4];
        values[RIGHT][C] = clip[11] - clip[ 8];
        values[RIGHT][D] = clip[15] - clip[12];

        // Now that we have a normal (A,B,C) and a distance (D) to the plane,
        // we want to normalize that normal and distance.

        // Normalize the RIGHT side
        normalizePlane(values, RIGHT);

        // This will extract the LEFT side of the frustum
        values[LEFT][A] = clip[ 3] + clip[ 0];
        values[LEFT][B] = clip[ 7] + clip[ 4];
        values[LEFT][C] = clip[11] + clip[ 8];
        values[LEFT][D] = clip[15] + clip[12];

        // Normalize the LEFT side
        normalizePlane(values, LEFT);

        // This will extract the BOTTOM side of the frustum
        values[BOTTOM][A] = clip[ 3] + clip[ 1];
        values[BOTTOM][B] = clip[ 7] + clip[ 5];
        values[BOTTOM][C] = clip[11] + clip[ 9];
        values[BOTTOM][D] = clip[15] + clip[13];

        // Normalize the BOTTOM side
        normalizePlane(values, BOTTOM);

        // This will extract the TOP side of the frustum
        values[TOP][A] = clip[ 3] - clip[ 1];
        values[TOP][B] = clip[ 7] - clip[ 5];
        values[TOP][C] = clip[11] - clip[ 9];
        values[TOP][D] = clip[15] - clip[13];

        // Normalize the TOP side
        normalizePlane(values, TOP);

        // This will extract the BACK side of the frustum
        values[BACK][A] = clip[ 3] - clip[ 2];
        values[BACK][B] = clip[ 7] - clip[ 6];
        values[BACK][C] = clip[11] - clip[10];
        values[BACK][D] = clip[15] - clip[14];

        // Normalize the BACK side
        normalizePlane(values, BACK);

        // This will extract the FRONT side of the frustum
        values[FRONT][A] = clip[ 3] + clip[ 2];
        values[FRONT][B] = clip[ 7] + clip[ 6];
        values[FRONT][C] = clip[11] + clip[10];
        values[FRONT][D] = clip[15] + clip[14];

        // Normalize the FRONT side
        normalizePlane(values, FRONT);
	}

	public void normalizePlane(float[][] frustum, int side) {
            // Here we calculate the magnitude of the normal to the plane (point A B C)
            // Remember that (A, B, C) is that same thing as the normal's (X, Y, Z).
            // To calculate magnitude you use the equation:  magnitude = sqrt( x^2 + y^2 + z^2)
            float magnitude = (float)Math.sqrt( frustum[side][A] * frustum[side][A] + 
                                            frustum[side][B] * frustum[side][B] + frustum[side][C] * frustum[side][C] );

            // Then we divide the plane's values by it's magnitude.
            // This makes it easier to work with.
            frustum[side][A] /= magnitude;
            frustum[side][B] /= magnitude;
            frustum[side][C] /= magnitude;
            frustum[side][D] /= magnitude; 
    }

    // The code below will allow us to make checks within the frustum.  For example,
    // if we want to see if a point, a sphere, or a cube lies inside of the frustum.
    // Because all of our planes point INWARDS (The normals are all pointing inside the frustum)
    // we then can assume that if a point is in FRONT of all of the planes, it's inside.

    ///////////////////////////////// POINT IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    /////
    /////   This determines if a point is inside of the frustum
    /////
    ///////////////////////////////// POINT IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*

    public boolean pointInFrustum( float x, float y, float z )
    {
            // Go through all the sides of the frustum
            for(int i = 0; i < 6; i++ )
            {
                    // Calculate the plane equation and check if the point is behind a side of the frustum
                    if(values[i][A] * x + values[i][B] * y + values[i][C] * z + values[i][D] <= 0)
                    {
                            // The point was behind a side, so it ISN'T in the frustum
                            return false;
                    }
            }

            // The point was inside of the frustum (In front of ALL the sides of the frustum)
            return true;
    }

    ///////////////////////////////// SPHERE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
    /////
    /////   This determines if a sphere is inside of our frustum by it's center and radius.
    /////
    ///////////////////////////////// SPHERE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*

    public boolean sphereInFrustum( float x, float y, float z, float radius ) {
            // Go through all the sides of the frustum
            for(int i = 0; i < 6; i++ ) {
                    // If the center of the sphere is farther away from the plane than the radius
                    if( values[i][A] * x + values[i][B] * y + values[i][C] * z + values[i][D] <= -radius ) {
                            // The distance was greater than the radius so the sphere is outside of the frustum
                            return false;
                    }
            }
            
            // The sphere was inside of the frustum!
            return true;
    }
    
	public boolean cubeInFrustum(Vector3f center, float size ) {
	        return cubeInFrustum( center.x, center.y, center.z, size );
	}
	///////////////////////////////// CUBE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*
	/////
	/////   This determines if a cube is in or around our frustum by it's center and 1/2 it's length
	/////
	///////////////////////////////// CUBE IN FRUSTUM \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*

	public boolean cubeInFrustum( float x, float y, float z, float size ) {
	// This test is a bit more work, but not too much more complicated.
	// Basically, what is going on is, that we are given the center of the cube,
	// and half the length.  Think of it like a radius.  Then we checking each point
	// in the cube and seeing if it is inside the frustum.  If a point is found in front
	// of a side, then we skip to the next side.  If we get to a plane that does NOT have
	// a point in front of it, then it will return false.
	
	// *Note* - This will sometimes say that a cube is inside the frustum when it isn't.
	// This happens when all the corners of the bounding box are not behind any one plane.
	// This is rare and shouldn't effect the overall rendering speed.

	for(int i = 0; i < 6; i++ )
	{
		if(values[i][A] * (x - size) + values[i][B] * (y - size) + values[i][C] * (z - size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x + size) + values[i][B] * (y - size) + values[i][C] * (z - size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x - size) + values[i][B] * (y + size) + values[i][C] * (z - size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x + size) + values[i][B] * (y + size) + values[i][C] * (z - size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x - size) + values[i][B] * (y - size) + values[i][C] * (z + size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x + size) + values[i][B] * (y - size) + values[i][C] * (z + size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x - size) + values[i][B] * (y + size) + values[i][C] * (z + size) + values[i][D] > 0)
		continue;
		if(values[i][A] * (x + size) + values[i][B] * (y + size) + values[i][C] * (z + size) + values[i][D] > 0)
		continue;
		
		// If we get here, it isn't in the frustum
		return false;
	}
	
	return true;
	}
	
	FloatBuffer toFloatBuffer() {
		for (int i = 0; i < 6; i++) {
			for (int z = 0; z < 4; z++) {
				buffer.put(values[i][z]);
			}
		}
		
		buffer.rewind();
		return buffer;
	}

	public boolean boxInFrustum(Box aabb) {
		
		return cubeInFrustum(aabb.center, aabb.size);
//		return sphereInFrustum(aabb.center.x, aabb.center.y, aabb.center.z, aabb.size/2);
//		return (pointInFrustum(aabb.getBottomLeftBackCorner().x, aabb.getBottomLeftBackCorner().y, aabb.getBottomLeftBackCorner().z) ||
//				pointInFrustum(aabb.getTopRightForeCorner().x, aabb.getTopRightForeCorner().y, aabb.getTopRightForeCorner().z));
	}
}
