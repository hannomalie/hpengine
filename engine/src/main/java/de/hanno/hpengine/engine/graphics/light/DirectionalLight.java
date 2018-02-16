package de.hanno.hpengine.engine.graphics.light;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.event.DirectionalLightHasMovedEvent;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.entity.Entity;
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
	public void update(Engine engine, float seconds) {
		if(entity.hasMoved()) {
            engine.getEventBus().post(new DirectionalLightHasMovedEvent());
        }
        super.update(engine, seconds);
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

	public InputControllerComponent addInputController() {
		InputControllerComponent component = new InputControllerComponent(entity) {
			private static final long serialVersionUID = 1L;

			@Override
			public void update(Engine engine, float seconds) {

				float moveAmount = 100 * seconds;
				float degreesPerSecond = 45;
				float rotateAmount = (float) Math.toRadians(degreesPerSecond) * seconds;

				if (engine.getInput().isKeyPressed(GLFW_KEY_UP)) {
					getEntity().rotateAround(new Vector3f(0, 1, 0), rotateAmount, new Vector3f());
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_DOWN)) {
					getEntity().rotateAround(new Vector3f(0, 1, 0), -rotateAmount, new Vector3f());
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_LEFT)) {
					getEntity().rotateAround(new Vector3f(1, 0, 0), rotateAmount, new Vector3f());
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_RIGHT)) {
					getEntity().rotateAround(new Vector3f(1, 0, 0), -rotateAmount, new Vector3f());
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_8)) {
					getEntity().translate(new Vector3f(0, -moveAmount, 0));
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_2)) {
					getEntity().translate(new Vector3f(0, moveAmount, 0));
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_4)) {
					getEntity().translate(new Vector3f(-moveAmount, 0, 0));
					getEntity().recalculate(); // TODO: Fix this
				}
				if (engine.getInput().isKeyPressed(GLFW_KEY_6)) {
					getEntity().translate(new Vector3f(moveAmount, 0, 0));
					getEntity().recalculate(); // TODO: Fix this
				}
			}
		};
		entity.addComponent(component);
		return component;
	}

	public void translate(Vector3f offset) {
		entity.translate(offset);
	}

}
