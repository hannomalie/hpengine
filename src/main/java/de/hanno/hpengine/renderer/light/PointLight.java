package de.hanno.hpengine.renderer.light;

import java.io.Serializable;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.shader.Bufferable;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.util.Util;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class PointLight extends Entity implements Serializable, Bufferable
{
	private static final long serialVersionUID = 1L;
	
	public static float DEFAULT_RANGE = 1f;
	private Vector4f color;
	
	protected PointLight(MaterialFactory materialFactory, Vector3f position, Model model, Vector4f colorIntensity, float range, String materialName) {
		super(position, generateName(), model, materialName);
		setColor(colorIntensity);
		setScale(range);
		init();
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
	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
		Matrix4f temp = new Matrix4f();
		Matrix4f.translate(getPosition(), temp, temp);
		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
		return temp;
	}
	
	public float getRadius() {
		return getTransform().getScale().x;
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
	public double[] get() {
		double[] doubles = new double[7];
		int index = 0;
		Vector3f worldPosition = getPosition();
		doubles[index++] = worldPosition.x;
		doubles[index++] = worldPosition.y;
		doubles[index++] = worldPosition.z;
		doubles[index++] = getRadius();
		Vector4f color = getColor();
		doubles[index++] = color.x;
		doubles[index++] = color.y;
		doubles[index++] = color.z;

		return doubles;
	}
}