package main;

import java.util.logging.Level;

import main.util.Util;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;

public class DirectionalLight {
	
	private boolean castsShadows = false;
//	private Vector3f direction = new Vector3f(40f, -32f, 0);
	private Vector3f direction = new Vector3f(0, 1, 0);

	public int lightDirectionLocation = 0;

	private Camera camera;

	private RenderTarget renderTarget;
	
	public DirectionalLight(boolean castsShadows) {
		this.castsShadows = castsShadows;
	}
	
	public void setLightDirectionLocation(int programId) {
		lightDirectionLocation = GL20.glGetUniformLocation(programId, "lightDirection");
	}
	
	public boolean isCastsShadows() {
		return castsShadows;
	}
	private void setCastsShadows(boolean castsShadows) {
		this.castsShadows = castsShadows;
	}

	public Vector3f getDirection() {
		return direction;
	}

	public void setDirection(Vector3f direction) {
		this.direction = direction;
	}
	
	public RenderTarget getRenderTarget() {
		return renderTarget;
	}

	public void setRenderTarget(RenderTarget renderTarget) {
		this.renderTarget = renderTarget;
	}
	
	public void init(ForwardRenderer renderer, Camera camera) {
		renderTarget = new RenderTarget(4096, 4096, 1, 1, 1, 0);
		this.camera = camera;
		camera.setProjectionMatrixLocation(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(),"projectionMatrix"));
		camera.setViewMatrixLocation(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(), "viewMatrix"));
	}

	public void init(ForwardRenderer renderer) {
//		camera =  new Camera(renderer, Util.createPerpective(60f, (float)ForwardRenderer.WIDTH / (float)ForwardRenderer.HEIGHT, 0.1f, 40f));
		camera =  new Camera(renderer, Util.createOrthogonal(-20f, 20f, 20f, -20f, 0.1f, 100f), Util.lookAt(new Vector3f(1,1,1), new Vector3f(0,0,0), new Vector3f(0, 1f, 0)));
		camera.setPosition(new Vector3f(2f,-2f,2f));
		camera.setAngle(new Vector3f(46f,-3f,0f));
		init(renderer, camera);
	}

	public void update() {
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD8)) {
			camera.move(new Vector3f(0,-0.02f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)) {
			camera.move(new Vector3f(0,0.02f,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)) {
			camera.move(new Vector3f(-0.02f,0,0));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD6)) {
			camera.move(new Vector3f(0.02f,0,0));
		}
		
		ForwardRenderer.getShadowProgram().use();
		camera.setAngle(getDirection());
		camera.updateShadow();
		ForwardRenderer.getMaterialProgram().use();
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}
}
