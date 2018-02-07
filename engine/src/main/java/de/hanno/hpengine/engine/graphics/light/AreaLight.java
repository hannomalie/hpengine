package de.hanno.hpengine.engine.graphics.light;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.camera.Frustum;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.model.StaticModel;
import de.hanno.hpengine.util.Util;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;


public class AreaLight extends Camera {
	
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;
	transient private RenderTarget renderTarget;

	protected AreaLight(Vector3f position, StaticModel model, Vector3f color, Vector3f scale) {
		super(position, generateName(), model);
        projectionMatrix = Util.createPerspective(getFov(), getRatio(), getNear(), getFar());
        frustum = new Frustum(this);
		setColor(color);
		scale(scale);
		setNear(1f);
        setFar(5000f);
        setFov(180f);
        setRatio(1);
		initialize();
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
			result[i] = light.getPosition().x;
			result[i+1] = light.getPosition().y;
			result[i+2] = light.getPosition().z;
			
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
	
	@Override
	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z,getRange()*2)) {
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
}
