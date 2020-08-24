package de.hanno.hpengine.engine.graphics.light.area;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.util.Util;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;


public class AreaLight implements Component, Bufferable {
	public static String COMPONENT_KEY = AreaLight.class.getSimpleName();

	public Camera camera;
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;
	private Entity entity;
	private Vector3f scale;

	public AreaLight(Entity entity, Vector3f color, Vector3f scale) {
		this.entity = entity;
		setScale(scale);
		setColor(color);
		float fovInDegrees = 90f;
		float far = 250f;
		float near = 1f;
		float ratio = 1f;
		camera = new Camera(entity, Util.createPerspective(fovInDegrees, ratio, near, far), near, far, fovInDegrees, ratio);
		entity.addComponent(camera);
	}

	@Override
	public void update(@NotNull CoroutineScope scope, Scene scene, float deltaSeconds) {
//		cameraEntity.update(deltaSeconds);
	}
	public void setColor(Vector3f color) {
		this.color = color;
	}

	public void setColor(Vector4f color) {
		this.color  = new Vector3f(color.x, color.y, color.z);
	}
	public Vector3f getColor() {
		return color;
	}
	private static String generateName() {
		return String.format("AreaLight_%d", System.currentTimeMillis());
	}

//	public void drawAsMesh(Camera de.hanno.hpengine.camera, RenderState extract) {
//		getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, getTransform().getTransformationBuffer(), 0);
//		});
//        for(Entity entity : getChildren()) {
//            entity.getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component ->{
//                de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera);
//            });
//        }
//	}

//	public void draw(Camera de.hanno.hpengine.camera, RenderState extract) {
//		getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, getTransform().getTransformationBuffer(), 0);
//		});
//	}

//	Matrix4f tempOrientationMatrix = new Matrix4f();
//	Matrix4f tempModelMatrixWithLowerScale = new Matrix4f();
//	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
//		tempModelMatrixWithLowerScale.identity();
//		Matrix4f.translate(getPosition(), tempModelMatrixWithLowerScale, tempModelMatrixWithLowerScale);
//		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), tempModelMatrixWithLowerScale, tempModelMatrixWithLowerScale);
//		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), tempModelMatrixWithLowerScale, tempModelMatrixWithLowerScale);
//		return tempModelMatrixWithLowerScale;
//	}
	
	public static float[] convert(List<AreaLight> list) {
		final int elementsPerLight = 10;
		int elementCount = list.size() * elementsPerLight;
		float[] result = new float[elementCount];
		
		for(int i = 0; i < list.size(); i++) {
			AreaLight light = list.get(i);
			result[i] = light.getEntity().getTransform().getPosition().x;
			result[i+1] = light.getEntity().getTransform().getPosition().y;
			result[i+2] = light.getEntity().getTransform().getPosition().z;
			
			result[i+3] = light.getScale().x;
			
			result[i+4] = light.getColor().x;
			result[i+5] = light.getColor().y;
			result[i+6] = light.getColor().z;
			
			result[i+7] = light.getColor().x;
			result[i+8] = light.getColor().y;
			result[i+9] = light.getColor().z;
		}
		return result;
	}

	public float getRange() {
		return getScale().z;
	}

	public void setRange(float range) {
		getScale().z = range;
	}
	
	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getEntity().getTransform().getPosition().x, getEntity().getTransform().getPosition().y, getEntity().getTransform().getPosition().z,getRange()*2)) {
			return true;
		}
		return false;
	}

	public float getWidth() {
		return (getScale().x);
	}
	public float getHeight() {
		return (getScale().y);
	}

	public FloatBuffer getViewProjectionMatrixAsBuffer() {
		return camera.getViewProjectionMatrixAsBuffer();
	}

	@Override
	public Entity getEntity() {
		return entity;
	}

    public void setScale(Vector3f scale) {
		this.scale = scale;
	}

	public Vector3f getScale() {
		return scale;
	}

	@Override
	public void putToBuffer(ByteBuffer buffer) {
		Transform transform = getEntity().getTransform();
		buffer.putFloat(transform.m00());
		buffer.putFloat(transform.m01());
		buffer.putFloat(transform.m02());
		buffer.putFloat(transform.m03());
		buffer.putFloat(transform.m10());
		buffer.putFloat(transform.m11());
		buffer.putFloat(transform.m12());
		buffer.putFloat(transform.m13());
		buffer.putFloat(transform.m20());
		buffer.putFloat(transform.m21());
		buffer.putFloat(transform.m22());
		buffer.putFloat(transform.m23());
		buffer.putFloat(transform.m30());
		buffer.putFloat(transform.m31());
		buffer.putFloat(transform.m32());
		buffer.putFloat(transform.m33());

		buffer.putFloat(getColor().x);
		buffer.putFloat(getColor().y);
		buffer.putFloat(getColor().z);
		buffer.putFloat(-1);

		buffer.putFloat(getWidth());
		buffer.putFloat(getHeight());
		buffer.putFloat(getRange());
		buffer.putFloat(-1);
	}

	@Override
	public int getBytesPerObject() {
		return getBytesPerInstance();
	}

	public static int getBytesPerInstance() {
		return Float.BYTES * 24;
	}
}
