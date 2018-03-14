package de.hanno.hpengine.engine.scene;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import de.hanno.hpengine.engine.camera.Camera;

import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.log.ConsoleLogger;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class AABB implements Serializable {
	private static Logger LOGGER = ConsoleLogger.getLogger();

	private Vector3f absoluteMaximum = new Vector3f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE);
	private Vector3f absoluteMinimum = new Vector3f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE);

	// this is the point -x, y, z if you look in -z with opengl coords
	private Vector3f topRightForeCorner;
	private Vector3f bottomLeftBackCorner;
	private Vector3f center;
	public float sizeX;
	public float sizeY;
	public float sizeZ;

    public Vector3f getMin() { return bottomLeftBackCorner; }
    public Vector3f getMax() { return topRightForeCorner; }

	public AABB(Vector3f center, float size) {
		this(center, size, size, size);
	}
	
	public AABB(Vector3f center, float sizeX, float sizeY, float sizeZ) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.center = center;
		calculateCorners();
	}
	
	public void move(Vector3f amount) {
		center.add(amount);
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
		this.bottomLeftBackCorner = new Vector3f(center.x - halfX, center.y - halfY, center.z - halfZ);
		this.topRightForeCorner = new Vector3f(center.x + halfX, center.y + halfY, center.z + halfZ);
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

	public boolean contains(Vector3f position) {
		Vector4f point = new Vector4f(position.x, position.y, position.z, 0);
		return contains(point);
	}

	@Override
	public String toString() {
		return String.format("Box (%f %f %f) @ (%.2f, %.2f, %.2f)", sizeX, sizeY, sizeZ, center.x, center.y, center.z);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof AABB)) {
			return false;
		}
		AABB otherBox = (AABB) other;
		return (otherBox.center.equals(center)
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
		topRightForeCorner.add(bottomLeftBackCorner, centerWorld);
		centerWorld.mul(0.5f);
		
		//if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, size/2)) {
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
			float tempLength = new Vector3f(point).sub(pivot).length();
			length = tempLength <= length? tempLength : length;
		}
		
		return length;
	}
	private float largestDistance(List<Vector3f> points, Vector3f pivot) {
		float length = Float.MAX_VALUE;
		for (Vector3f point : points) {
			float tempLength = new Vector3f(point).sub(pivot).length();
			length = tempLength >= length? tempLength : length;
		}
		
		return length;
	}

	public Vector3f getCenter() {
		return center;
	}

	public void setCenter(Vector3f center) {
		this.center = center;
		calculateCorners();
	}

	public void calculateMinMax(List<Entity> entities) {
		if (entities.isEmpty()) {
			getMin().set(-1f, -1f, -1f);
			getMax().set(1f, 1f, 1f);
			return;
		}

		getMin().set(absoluteMaximum);
		getMax().set(absoluteMinimum);

		for (Entity entity : entities) {
			de.hanno.hpengine.engine.transform.AABB minMaxWorld = entity.getMinMaxWorld();
			Vector3f currentMin = minMaxWorld.getMin();
			Vector3f currentMax = minMaxWorld.getMax();
			getMin().x = (currentMin.x < getMin().x) ? currentMin.x : getMin().x;
			getMin().y = (currentMin.y < getMin().y) ? currentMin.y : getMin().y;
			getMin().z = (currentMin.z < getMin().z) ? currentMin.z : getMin().z;

			getMax().x = (currentMax.x > getMax().x) ? currentMax.x : getMax().x;
			getMax().y = (currentMax.y > getMax().y) ? currentMax.y : getMax().y;
			getMax().z = (currentMax.z > getMax().z) ? currentMax.z : getMax().z;
		}
	}
}
