package scene;

import static log.ConsoleLogger.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import camera.Camera;

import engine.Transform;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Box {
	private static Logger LOGGER = getLogger();
	// this is the point -x, y, z if you look in -z with opengl coords
	private Vector3f topRightForeCorner;
	private Vector3f bottomLeftBackCorner;
	public float sizeX;
	public float sizeY;
	public float sizeZ;
	public Transform transform;

	public Box(Transform transform, float size) {
		this(transform, size, size, size);
	}
	
	public Box(Transform transform, float sizeX, float sizeY, float sizeZ) {
		this.transform = transform;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		calculateCorners();
	}
	
	public void setSize(float size) {
		setSize(size, size, size);
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		calculateCorners();
	}
	private void calculateCorners() {
		float halfX = sizeX/2;
		float halfY = sizeY/2;
		float halfZ = sizeZ/2;
		Vector3f center = transform.getPosition();

		Vector3f rightDist = (Vector3f) transform.getRightDirection().scale(halfX);
		Vector3f upDist = (Vector3f) transform.getUpDirection().scale(halfY);
		Vector3f viewDist = (Vector3f) transform.getViewDirection().scale(-halfZ);

		this.bottomLeftBackCorner = Vector3f.sub(center, rightDist, null);
		this.bottomLeftBackCorner = Vector3f.sub(bottomLeftBackCorner, upDist, null);
		this.bottomLeftBackCorner = Vector3f.sub(bottomLeftBackCorner, viewDist, null);
		this.topRightForeCorner = Vector3f.add(center, rightDist, null);
		this.topRightForeCorner = Vector3f.add(topRightForeCorner, upDist, null);
		this.topRightForeCorner = Vector3f.add(topRightForeCorner, viewDist, null);
	}
	
	public List<Vector3f> getPoints() {
		List<Vector3f> result = new ArrayList<>();
		
		result.add(bottomLeftBackCorner);
		result.add(new Vector3f(bottomLeftBackCorner.x + sizeX, bottomLeftBackCorner.y, bottomLeftBackCorner.z));
		result.add(new Vector3f(bottomLeftBackCorner.x + sizeX, bottomLeftBackCorner.y, bottomLeftBackCorner.z + sizeZ));
		result.add(new Vector3f(bottomLeftBackCorner.x, bottomLeftBackCorner.y, bottomLeftBackCorner.z + sizeZ));
		
		result.add(topRightForeCorner);
		result.add(new Vector3f(topRightForeCorner.x - sizeX, topRightForeCorner.y, topRightForeCorner.z));
		result.add(new Vector3f(topRightForeCorner.x - sizeX, topRightForeCorner.y, topRightForeCorner.z - sizeZ));
		result.add(new Vector3f(topRightForeCorner.x, topRightForeCorner.y, topRightForeCorner.z - sizeZ));

		return result;
	}
	
	public float[] getPointsAsArray() {
		List<Vector3f> points = getPoints();
		List<Vector3f> pointsForLineDrawing = new ArrayList<>();
		pointsForLineDrawing.add(points.get(0));
		pointsForLineDrawing.add(points.get(1));
		pointsForLineDrawing.add(points.get(1));
		pointsForLineDrawing.add(points.get(2));
		pointsForLineDrawing.add(points.get(2));
		pointsForLineDrawing.add(points.get(3));
		pointsForLineDrawing.add(points.get(3));
		pointsForLineDrawing.add(points.get(0));
		
		pointsForLineDrawing.add(points.get(4));
		pointsForLineDrawing.add(points.get(5));
		pointsForLineDrawing.add(points.get(5));
		pointsForLineDrawing.add(points.get(6));
		pointsForLineDrawing.add(points.get(6));
		pointsForLineDrawing.add(points.get(7));
		pointsForLineDrawing.add(points.get(7));
		pointsForLineDrawing.add(points.get(4));

		pointsForLineDrawing.add(points.get(0));
		pointsForLineDrawing.add(points.get(6));
		pointsForLineDrawing.add(points.get(1));
		pointsForLineDrawing.add(points.get(7));
		pointsForLineDrawing.add(points.get(2));
		pointsForLineDrawing.add(points.get(4));
		pointsForLineDrawing.add(points.get(3));
		pointsForLineDrawing.add(points.get(5));

		float[] dest = new float[3* pointsForLineDrawing.size()];
		for (int i = 0; i < pointsForLineDrawing.size(); i++) {
			dest[3*i] = pointsForLineDrawing.get(i).x;
			dest[3*i+1] = pointsForLineDrawing.get(i).y;
			dest[3*i+2] = pointsForLineDrawing.get(i).z;
		}
		return dest;
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
		return String.format("Box (%f %f %f) @ (%.2f, %.2f, %.2f)", sizeX, sizeY, sizeZ, transform.getPosition().x, transform.getPosition().y, transform.getPosition().z);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Box)) {
			return false;
		}
		Box otherBox = (Box) other;
		return (otherBox.transform.equals(transform)
				&& otherBox.sizeX == sizeX
				&& otherBox.sizeY == sizeY
				&& otherBox.sizeZ == sizeZ);
	}

	public Vector3f getTopRightForeCorner() {
		return topRightForeCorner;
	}

	public Vector3f getBottomLeftBackCorner() {
		return bottomLeftBackCorner;
	}


	public boolean isInFrustum(Camera camera) {
		Vector3f centerWorld = new Vector3f();
		Vector3f.add(topRightForeCorner, bottomLeftBackCorner, centerWorld);
		centerWorld.scale(0.5f);
		
		//if (camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, size/2)) {
		if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, Math.max(sizeX, Math.max(sizeY, sizeZ))/2f)) {
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