package de.hanno.hpengine.engine.graphics.light.directional;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.shader.Program;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class DirectionalLight extends Camera {

	private boolean castsShadows = false;
	private Vector3f color = new Vector3f(1,1,1);
	private float scatterFactor = 1f;

	@NotNull
	public Entity entity;

	public DirectionalLight(Entity entity) {
		super(entity);
		this.entity = entity;
		setPerspective(false);
		setColor(new Vector3f(1f, 0.76f, 0.49f));
		setScatterFactor(1f);
		setWidth(1500);
		setHeight(1500);
		setFar(-5000);
		entity.translation(new Vector3f(12f, 300f, 2f));
	}

	@Override
	public void update(float seconds) {
        super.update(seconds);
	}

	public void drawDebug(Program program) {
        throw new IllegalStateException("Currently not implemented!");
//		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			modelComponent.drawDebug(program, getTransform().getTransformationBuffer());
//		});
	}

	public Vector3f getDirection () {
		return entity.getViewDirection();
	}

	public Vector3f getColor() {
		return color ;
	}
	public void setColor(Vector3f color) {
		this.color = color;
	}

	public float getScatterFactor() {
		return scatterFactor;
	}
	public void setScatterFactor(float scatterFactor) {
		this.scatterFactor = scatterFactor;
	}


	public void translate(Vector3f offset) {
		entity.translate(offset);
	}

	public static class DirectionalLightController extends InputControllerComponent {
		private static final long serialVersionUID = 1L;
		private EngineContext engine;

		public DirectionalLightController(EngineContext engine, @NotNull Entity entity) {
			super(entity);
			this.engine = engine;
		}

		@Override
		public void update(float seconds) {

			float moveAmount = 100 * seconds;
			float degreesPerSecond = 45;
			float rotateAmount = (float) Math.toRadians(degreesPerSecond) * seconds;

			if (engine.getInput().isKeyPressed(GLFW_KEY_UP)) {
				getEntity().rotateAround(new Vector3f(0, 1, 0), rotateAmount, new Vector3f());
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_DOWN)) {
				getEntity().rotateAround(new Vector3f(0, 1, 0), -rotateAmount, new Vector3f());
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_LEFT)) {
				getEntity().rotateAround(new Vector3f(1, 0, 0), rotateAmount, new Vector3f());
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_RIGHT)) {
				getEntity().rotateAround(new Vector3f(1, 0, 0), -rotateAmount, new Vector3f());
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_8)) {
				getEntity().translate(new Vector3f(0, -moveAmount, 0));
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_2)) {
				getEntity().translate(new Vector3f(0, moveAmount, 0));
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_4)) {
				getEntity().translate(new Vector3f(-moveAmount, 0, 0));
			}
			if (engine.getInput().isKeyPressed(GLFW_KEY_6)) {
				getEntity().translate(new Vector3f(moveAmount, 0, 0));
			}
		}
	}
}
