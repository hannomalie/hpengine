package main;

import main.util.Util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;

public class DirectionalLight {
	
	private boolean castsShadows = false;
	private Vector3f direction = new Vector3f(-0.6f, -0.2f, 0);
//	private Vector3f direction = new Vector3f(0, 1, 0);

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
		renderTarget = new RenderTarget(256, 256);
//		camera.setProjectionMatrixLocation(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(),"projectionMatrix"));
//		camera.setViewMatrixLocation(GL20.glGetUniformLocation(ForwardRenderer.getShadowProgramId(), "viewMatrix"));
	}

	public void init(ForwardRenderer renderer) {
		camera =  new Camera(renderer, Util.createOrthogonal(-100, 100, -100, 100, -100, 200), Util.lookAt(new Vector3f(1,10,1), new Vector3f(0,0,0), new Vector3f(0, 1, 0)));
		//camera =  new Camera(renderer, Util.createPerpective(60f, (float)ForwardRenderer.WIDTH / (float)ForwardRenderer.HEIGHT, 0.001f, 100f));
		init(renderer, camera);
	}

	public void update() {
		camera.update();
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}
}
