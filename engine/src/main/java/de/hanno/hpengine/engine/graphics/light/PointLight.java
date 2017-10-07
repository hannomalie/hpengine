package de.hanno.hpengine.engine.graphics.light;

import java.io.Serializable;
import java.nio.ByteBuffer;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.StaticModel;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.shader.Program;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;


public class PointLight extends Entity implements Serializable, Bufferable
{
	private static final long serialVersionUID = 1L;
	
	public static float DEFAULT_RANGE = 1f;
	private Vector4f color;
	
	protected PointLight(Vector3f position, StaticModel model, Vector4f colorIntensity, float range) {
		super(position, generateName(), model);
		setColor(colorIntensity);
		scale(range);
		initialize();
	}
	
	private static String generateName() {
		return String.format("PointLight_%d", System.currentTimeMillis());
	}

	public void setColor(Vector4f color) {
		this.color  = color;
	}

	public Vector4f getColor() {
		return color;
	}

//	public void drawAsMesh(Camera de.hanno.hpengine.camera, RenderState extract) {
//		if(!isInitialized()) { return; }
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			modelComponent.draw(extract, de.hanno.hpengine.camera, getTransform().getTransformationBuffer(), 0);
//		});
//	}

	public void draw(Program program) {
        throw new IllegalStateException("Currently not implemented!");
//		if(!isInitialized()) { return; }
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().draw();
//		});
	}

	public void drawAgain(IndexBuffer indexBuffer, Program program) {
        throw new IllegalStateException("Currently not implemented!");
//		if(!isInitialized()) { return; }
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().drawAgain(indexBuffer);
//		});
	}

	private Matrix4f tempOrientationMatrix = new Matrix4f();
//	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
//		Matrix4f temp = new Matrix4f();
//		Matrix4f.translate(getPosition(), temp, temp);
//		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
//		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
//		return temp;
//	}
	
	public float getRadius() {
        return this.getScale().x;
	}
	
	@Override
	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z, getRadius())) {
//		if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, getRadius())) {
			return true;
		}
		return false;
	}

	@Override
	public void putToBuffer(ByteBuffer buffer) {

		Vector3f worldPosition = getPosition();
		buffer.putDouble(worldPosition.x);
		buffer.putDouble(worldPosition.y);
		buffer.putDouble(worldPosition.z);
		buffer.putDouble(getRadius());
		Vector4f color = getColor();
		buffer.putDouble(color.x);
		buffer.putDouble(color.y);
		buffer.putDouble(color.z);
		buffer.putDouble(-1);
	}
}
