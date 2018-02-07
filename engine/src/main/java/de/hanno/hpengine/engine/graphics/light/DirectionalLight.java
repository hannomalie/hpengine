package de.hanno.hpengine.engine.graphics.light;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.InputControllerComponent;
import de.hanno.hpengine.engine.event.DirectionalLightHasMovedEvent;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.util.Util;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class DirectionalLight extends Camera {

	private boolean castsShadows = false;

	private Vector3f color = new Vector3f(1,1,1);
	private float scatterFactor = 1f;

	transient private Entity box;

	public DirectionalLight() {
		super(Util.createOrthogonal(-1000f, 1000f, 1000f, -1000f, -2500f, 2500f), -2500f, 2500f, 60, 16 / 9);
		setPerspective(false);
		setColor(new Vector3f(1f, 0.76f, 0.49f));
		setScatterFactor(1f);
		setWidth(1500);
		setHeight(1500);
		setFar(-5000);
		translation(new Vector3f(12f, 300f, 2f));
//		rotate(new AxisAngle4f(1,0,0, (float) Math.toRadians(90)));

//		try {
//			StaticMesh model = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
//			box = getWorld().getEntityManager().getEntity(getPosition(), "DefaultCube", model, white);
//			box.setScale(0.4f);
//			ModelComponent modelComponent = new ModelComponent(model);
//			modelComponent.init(world);
//			addComponent(modelComponent);
//			de.hanno.hpengine.camera.addComponent(modelComponent);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	@Override
	public void initialize() {
		super.initialize();
		initialized = false;

		initialized = true;
	}

	@Override
	public void update(Engine engine, float seconds) {
		if(hasMoved()) {
            Engine.getEventBus().post(new DirectionalLightHasMovedEvent());
        }
        super.update(engine, seconds);
	}

//	public void drawAsMesh(RenderState extract, Camera de.hanno.hpengine.camera) {
//		getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, getTransform().getTransformationBuffer(), 0);
//		});
//		de.hanno.hpengine.camera.getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, de.hanno.hpengine.camera.getTransform().getTransformationBuffer(), 0);
//		});
//	}

	@Override
	public String getName() {
		return "Directional lights";
	}

	public void drawDebug(Program program) {
        throw new IllegalStateException("Currently not implemented!");
//		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			modelComponent.drawDebug(program, getTransform().getTransformationBuffer());
//		});
	}

	public Vector3f getDirection () {
		return getViewDirection();
	}

	@Override
	public boolean isInFrustum(Camera camera) {
		return false;
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

	public void addInputController() {
		addComponent(new InputControllerComponent() {
			private static final long serialVersionUID = 1L;

			@Override public void update(Engine engine, float seconds) {

				float moveAmount = 100* seconds;
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
		});
	}
}
