package main.octree;

import static main.log.ConsoleLogger.getLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.camera.Camera;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Box {
	private static Logger LOGGER = getLogger();
	// this is the point -x, y, z if you look in -z with opengl coords
	private Vector3f topRightForeCorner;
	private Vector3f bottomLeftBackCorner;
	public Vector3f center;
	public float size;
	
	public Box(Vector3f center, float size) {
		this.size = size;
		this.center = center;
		calculateCorners();
	}
	
	private void calculateCorners() {
		float halfSize = size/2;
		this.bottomLeftBackCorner = new Vector3f(center.x - halfSize, center.y - halfSize, center.z - halfSize);
		this.topRightForeCorner = new Vector3f(center.x + halfSize, center.y + halfSize, center.z + halfSize);
	}
	
	public List<Vector3f> getPoints() {
		List<Vector3f> result = new ArrayList<>();
		
		result.add(bottomLeftBackCorner);
		result.add(new Vector3f(bottomLeftBackCorner.x + size, bottomLeftBackCorner.y, bottomLeftBackCorner.z));
		result.add(new Vector3f(bottomLeftBackCorner.x + size, bottomLeftBackCorner.y, bottomLeftBackCorner.z + size));
		result.add(new Vector3f(bottomLeftBackCorner.x, bottomLeftBackCorner.y, bottomLeftBackCorner.z + size));
		
		result.add(topRightForeCorner);
		result.add(new Vector3f(topRightForeCorner.x - size, topRightForeCorner.y, topRightForeCorner.z));
		result.add(new Vector3f(topRightForeCorner.x - size, topRightForeCorner.y, topRightForeCorner.z - size));
		result.add(new Vector3f(topRightForeCorner.x, topRightForeCorner.y, topRightForeCorner.z - size));

		return result;
	}

	public boolean contains(Vector4f point) {
		// max x of box = toprightforecorner.x
		// min x of box = bottomLeftBackCorner.x etc
		
		if (point.x >= bottomLeftBackCorner.x &&
			point.x <= topRightForeCorner.x &&
			
			point.y >= bottomLeftBackCorner.y &&
			point.y <= topRightForeCorner.y &&
			
			point.z >= bottomLeftBackCorner.z &&
			point.z <= topRightForeCorner.z) {
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("Box (%f) @ (%.2f, %.2f, %.2f)", size, center.x, center.y, center.z);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Box)) {
			return false;
		}
		Box otherBox = (Box) other;
		return (otherBox.center == center && otherBox.size == size);
	}

	public Vector3f getTopRightForeCorner() {
		return topRightForeCorner;
	}

	public Vector3f getBottomLeftBackCorner() {
		return bottomLeftBackCorner;
	}


	public boolean isInFrustum(Camera camera) {
		Vector3f[] minMaxWorld = new Vector3f[]{new Vector3f(bottomLeftBackCorner.x, bottomLeftBackCorner.y, bottomLeftBackCorner.z),
												new Vector3f(topRightForeCorner.x, topRightForeCorner.y, topRightForeCorner.z)};
		Vector3f minWorld = minMaxWorld[0];
		Vector3f maxWorld = minMaxWorld[1];
		Vector3f centerWorld = (Vector3f) (Vector3f.add(maxWorld, minWorld, null)).scale(0.5f);
		
		if (camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, size/2)) {
			return true;
		}
		return false;
	}
	
	// TODO: Fix
	public boolean containsOrIntersectsSphere(Vector3f position, float radius) {
		boolean result = false;
		if (this.contains(new Vector4f(position.x, position.y, position.z, 0))) {
			result = true;
			return result;
		}
		List<Vector3f> points = getPoints();
		float smallestDistance = smallestDistance(points, position);
		float largestDistance = largestDistance(points, position);
		if (largestDistance <= radius) {
			result = true;
		}
		return result;
	}

	private float smallestDistance(List<Vector3f> points, Vector3f pivot) {
		float length = Float.MAX_VALUE;
		for (Vector3f point : points) {
			float tempLength = Vector3f.sub(point, pivot, null).length();
			length = tempLength <= length? tempLength : length;
		}
		
		return length;
	}
	private float largestDistance(List<Vector3f> points, Vector3f pivot) {
		float length = Float.MAX_VALUE;
		for (Vector3f point : points) {
			float tempLength = Vector3f.sub(point, pivot, null).length();
			length = tempLength >= length? tempLength : length;
		}
		
		return length;
	}
}