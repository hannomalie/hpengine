package de.hanno.hpengine.engine.graphics.light;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.transform.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;


public class TubeLight implements Component {
	public static String COMPONENT_KEY = TubeLight.class.getSimpleName();
	
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;
	private Entity entity;
	private float radius;
	private float length;

	protected TubeLight(Entity entity, Vector3f colorIntensity, float length, float radius) {
		this.entity = entity;
		setColor(colorIntensity);
		setLength(length);
		setRadius(radius);
	}

	public void setColor(Vector3f color) {
		this.color  = color;
	}

	public void setColor(Vector4f color) {
		this.color  = new Vector3f(color.x, color.y, color.z);
	}
	public Vector3f getColor() {
		return color;
	}

	@Override
	public void destroy() {
	}

	@Override
	public Entity getEntity() {
		return entity;
	}

	@Override
	public void update(float seconds) {
	}

	@Override
	public String getIdentifier() {
		return COMPONENT_KEY;
	}

	public void draw(Program program) {
        throw new IllegalStateException("Currently not implemented");
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().draw();
//		});
	}

	Matrix4f tempOrientationMatrix = new Matrix4f();
//    private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
//		Matrix4f temp = new Matrix4f();
//		Matrix4f.translate(getPosition(), temp, temp);
//		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
//		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
//		return temp;
//	}
	
	public float getRadius() {
        return radius;
	}
	
	public static float[] convert(List<TubeLight> list) {
		final int elementsPerLight = 10;
		int elementCount = list.size() * elementsPerLight;
		float[] result = new float[elementCount];
		
		for(int i = 0; i < list.size(); i++) {
			TubeLight light = list.get(i);
			result[i] = light.getEntity().getPosition().x;
			result[i+1] = light.getEntity().getPosition().y;
			result[i+2] = light.getEntity().getPosition().z;
			
			result[i+3] = light.getEntity().getScale().x;
			
			result[i+4] = light.getColor().x;
			result[i+5] = light.getColor().y;
			result[i+6] = light.getColor().z;
			
			result[i+7] = light.getColor().x;
			result[i+8] = light.getColor().y;
			result[i+9] = light.getColor().z;
		}
		return result;
	}

	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getEntity().getPosition().x, getEntity().getPosition().y, getEntity().getPosition().z, getLength()/2)) {
			return true;
		}
		return false;
	}
	
	private float getOffset() {
		return (getLength()/2) - getRadius();
	}
	public Vector3f getStart() {
		return new Vector3f(getEntity().getPosition()).sub(new Vector3f(entity.getRightDirection()).mul(getOffset()));
	}
	public Vector3f getEnd() {
		return new Vector3f(getEntity().getPosition()).add( new Vector3f(entity.getRightDirection()).mul(getOffset()));
	}
	public Vector3f getOuterLeft() {
		return new Vector3f(entity.getPosition()).sub(new Vector3f(entity.getRightDirection()).mul((getLength()/2)));
	}
	public Vector3f getOuterRight() {
		return new Vector3f(entity.getPosition()).add(new Vector3f(entity.getRightDirection()).mul((getLength()/2)));
	}

	public float getLength() {
		return length;
	}
	public void setLength(float length) {
		this.length = length;
	}

	public void setRadius(float radius) {
		this.radius = radius;
	}

//	TODO do this properly
	AABB aabb = new AABB();
	public AABB getMinMaxWorld() {
		aabb.setMin(new Vector3f(-length));
		aabb.setMax(new Vector3f(length));
		aabb.transform(getEntity(), aabb);
		return aabb;
	}

}
