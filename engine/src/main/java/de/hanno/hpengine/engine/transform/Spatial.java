package de.hanno.hpengine.engine.transform;

import de.hanno.hpengine.engine.camera.Camera;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static java.lang.Float.MAX_VALUE;

public interface Spatial {
    Vector3fc MIN = new Vector3f(MAX_VALUE, MAX_VALUE, MAX_VALUE);
    Vector3fc MAX = new Vector3f(-MAX_VALUE, -MAX_VALUE, -MAX_VALUE);

    static boolean isInFrustum(Camera camera, Vector3f centerWorld, Vector3f minWorld, Vector3f maxWorld) {
        Vector3f tempDistVector = new Vector3f();
        new Vector3f(minWorld).sub(maxWorld, tempDistVector);

//		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
//			de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
//		if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
//		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
//				|| de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
        if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, tempDistVector.length()/2)) {
            return true;
        }
        return false;
    }

    Vector3f getCenterWorld(Transform transform);

    AABB getMinMaxWorld(Transform transform);

    AABB getMinMax();

    float getBoundingSphereRadius(Transform transform);

    Vector3f getCenterWorld();

    float getBoundingSphereRadius();

    AABB getMinMaxWorld();

    default void update(float deltaSeconds) {}
}
