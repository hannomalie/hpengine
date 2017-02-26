package de.hanno.hpengine.renderer.light;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.component.InputControllerComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.event.DirectionalLightHasMovedEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.util.Util;

import java.nio.FloatBuffer;

public class DirectionalLight extends Entity {

	private boolean castsShadows = false;

	private Vector3f color = new Vector3f(1,1,1);
	private float scatterFactor = 1f;

	transient private Entity box;
	private Camera camera;

	public DirectionalLight() {
		setColor(new Vector3f(1f, 0.76f, 0.49f));
		setScatterFactor(1f);
		Matrix4f projectionMatrix = Util.createOrthogonal(-1000f, 1000f, 1000f, -1000f, -2500f, 2500f);
		camera = new Camera(projectionMatrix, 0.1f, 500f, 60, 16 / 9);
		camera.setParent(this);
		camera.setPerspective(false);
		camera.setWidth(1500);
		camera.setHeight(1500);
		camera.setFar(-5000);
		camera.setPosition(new Vector3f(12f, 300f, 2f));
		camera.rotate(new Vector4f(1,0,0, 90));

//		try {
//			Model model = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
//			box = getWorld().getEntityFactory().getEntity(getPosition(), "DefaultCube", model, white);
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
	public void init() {
		super.init();
		initialized = false;

        setHasMoved(true);
		initialized = true;
	}

	@Override
	public void update(float seconds) {
		if(hasMoved()) {
            Engine.getEventBus().post(new DirectionalLightHasMovedEvent());
        }
        super.update(seconds);
	}

//	public void drawAsMesh(RenderState extract, Camera de.hanno.hpengine.camera) {
//		getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, getTransform().getTransformationBuffer(), 0);
//		});
//		de.hanno.hpengine.camera.getComponentOption(ModelComponent.class).ifPresent(de.hanno.hpengine.component -> {
//			de.hanno.hpengine.component.draw(extract, de.hanno.hpengine.camera, de.hanno.hpengine.camera.getTransform().getTransformationBuffer(), 0);
//		});
//	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	@Override
	public String getName() {
		return "Directional light";
	}

	public void drawDebug(Program program) {
        throw new IllegalStateException("Currently not implemented!");
//		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			modelComponent.drawDebug(program, getTransform().getTransformationBuffer());
//		});
	}

	public Vector3f getDirection () {
		return camera.getViewDirection();
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

	public FloatBuffer getViewProjectionMatrixAsBuffer() {
		return camera.getViewProjectionMatrixAsBuffer();
	}

	public void addInputController() {
		addComponent(new InputControllerComponent() {
			private static final long serialVersionUID = 1L;

			@Override public void update(float seconds) {

				float moveAmount = 100* seconds;
				float rotateAmount = 100*seconds;

				if (Input.isKeyPressed(Keyboard.KEY_UP)) {
					getEntity().rotate(new Vector3f(0, 0, 1), rotateAmount * 45 / 40);
				}
				if (Input.isKeyPressed(Keyboard.KEY_DOWN)) {
					getEntity().rotate(new Vector3f(0, 0, 1), rotateAmount * -45 / 40);
				}
				if (Input.isKeyPressed(Keyboard.KEY_LEFT)) {
					getEntity().rotate(new Vector3f(1, 0, 0), rotateAmount * 45 / 40);
				}
				if (Input.isKeyPressed(Keyboard.KEY_RIGHT)) {
					getEntity().rotate(new Vector3f(1, 0, 0), rotateAmount * -45 / 40);
				}
				if (Input.isKeyPressed(Keyboard.KEY_NUMPAD8)) {
					getEntity().move(new Vector3f(0, -moveAmount, 0));
				}
				if (Input.isKeyPressed(Keyboard.KEY_NUMPAD2)) {
					getEntity().move(new Vector3f(0, moveAmount, 0));
				}
				if (Input.isKeyPressed(Keyboard.KEY_NUMPAD4)) {
					getEntity().move(new Vector3f(-moveAmount, 0, 0));
				}
				if (Input.isKeyPressed(Keyboard.KEY_NUMPAD6)) {
					getEntity().move(new Vector3f(moveAmount, 0, 0));
				}
				getEntity().getTransform().recalculate(); // TODO: Fix this
			}
		});
	}
}
