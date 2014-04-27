package main;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;

import main.util.OBJLoader;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Spotlight implements IEntity {
	
	private boolean castsShadows = false;

	private Camera camera;

	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	
	private RenderTarget renderTarget;
	private IEntity box;
	
	public Spotlight(boolean castsShadows) {
		this.castsShadows = castsShadows;
	}

	public boolean isCastsShadows() {
		return castsShadows;
	}
	private void setCastsShadows(boolean castsShadows) {
		this.castsShadows = castsShadows;
	}

	public RenderTarget getRenderTarget() {
		return renderTarget;
	}

	public void setRenderTarget(RenderTarget renderTarget) {
		this.renderTarget = renderTarget;
	}
	
	public void init(Renderer renderer, Camera camera) {
		Material stone = new Material(renderer, "", "stone_diffuse.png", "stone_normal.png",
				"stone_specular.png", "stone_occlusion.png",
				"stone_height.png");

		try {
			Model model = OBJLoader.loadTexturedModel(new File("C:\\cube.obj")).get(0);
			box = new Entity(renderer, model, camera.getPosition(), stone, true);
			box.setScale(0.4f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		renderTarget = new RenderTarget(256, 256, 1, 1, 1, 1);
		this.camera = camera;
	}

	public void init(Renderer renderer) {
		camera =  new Camera(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 100f));
//		camera =  new Camera(renderer, Util.createOrthogonal(-20f, 20f, 20f, -20f, 0.1f, 100f), Util.lookAt(new Vector3f(1,1,1), new Vector3f(0,0,0), new Vector3f(0, 1f, 0)));
		camera.setPosition(new Vector3f(12f,2f,2f));
		camera.rotate(new Vector4f(0.3f, 1, 0.1f, 0.5f));
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
		
		camera.updateShadow();

		box.setPosition(camera.getPosition());
		box.setOrientation(camera.getOrientation());
		box.update();
		
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	@Override
	public void destroy() {
		
	}

	public Vector3f getPosition() {
		return camera.getPosition();
	}

	@Override
	public void rotate(Vector4f axisAngle) {
		Quaternion rot = new Quaternion();
		rot.setFromAxisAngle(axisAngle);
		Quaternion.mul(camera.getOrientation(), rot, camera.getOrientation());
	}

	@Override
	public void move(Vector3f amount) {
		Vector3f.add(getPosition(), amount, getPosition());
	}

	@Override
	public String getName() {
		return "Sportlight";
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	@Override
	public Quaternion getOrientation() {
		return camera.getOrientation();
	}

	@Override
	public void rotate(Vector3f axis, float degree) {
		rotate(new Vector4f(axis.x, axis.y, axis.z, degree));
	}

	@Override
	public void drawDebug(Program program) {
		box.drawDebug(program);
	}

	@Override
	public void setScale(Vector3f scale) {
		camera.setScale(scale);
	}

	@Override
	public void setPosition(Vector3f position) {
		camera.setPosition(position);
	}

	@Override
	public void setOrientation(Quaternion orientation) {
		camera.setOrientation(orientation);
	}

	@Override
	public void setScale(float scale) {
		setScale(new Vector3f(scale,scale,scale));
	}

	public FloatBuffer getLightMatrix() {
//		box.getModelMatrix().store(buffer);
		camera.getViewMatrix().store(buffer);
		buffer.flip();
		return buffer;
	}

	@Override
	public Matrix4f getModelMatrix() {
		return null;
	}

	@Override
	public boolean isInFrustum(Camera camera) {
		// TODO Auto-generated method stub
		return false;
	}

}
