package main.renderer.light;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;

import main.Transform;
import main.World;
import main.camera.Camera;
import main.model.Entity;
import main.model.IEntity;
import main.model.Model;
import main.octree.Octree;
import main.renderer.RenderTarget;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.shader.Program;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Spotlight implements IEntity {
	
	private boolean castsShadows = false;

	private Camera camera;
	private Transform transform = new Transform();

	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	
	private RenderTarget renderTarget;
	private IEntity box;

	private Vector3f color = new Vector3f(1,1,1);

	private boolean selected;

	private Program directionalShadowPassProgram;

	private Renderer renderer;
	
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
	
	public void init(Renderer renderer, Camera camera) throws Exception {
		Material white = renderer.getMaterialFactory().getMaterial(new HashMap<MAP,String>(){{
																	put(MAP.DIFFUSE,"assets/textures/default.dds");
																}});

		try {
			Model model = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
			box = renderer.getEntityFactory().getEntity(camera.getPosition(), "DefaultCube", model, white);
			box.setScale(0.4f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		

		directionalShadowPassProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "shadowmap_fragment.glsl", Entity.POSITIONCHANNEL, false);
		
		renderTarget = new RenderTarget(2048, 2048, 1, 1, 1, 1);
		this.camera = camera;
		this.renderer = renderer;
	}

	public void init(Renderer renderer) throws Exception {
//		camera =  new Camera(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 100f));
		camera =  new Camera(renderer, Util.createOrthogonal(-200f, 200f, 200f, -200f, -500f, 500f), Util.lookAt(new Vector3f(1,1,1), new Vector3f(0,0,0), new Vector3f(0, 1f, 0)), 0.1f, 500f);
		setPosition(new Vector3f(12f,80f,2f));
		Quaternion quat = new Quaternion(0.44122884f, 0.5492834f, 0.5371881f, 0.46371746f);
//		quat.setFromAxisAngle(new Vector4f(0,-1,0,0));
		setOrientation(quat);
		init(renderer, camera);
	}

	public void update(float seconds) {
		
		camera.updateShadow();

		box.setPosition(camera.getPosition());
		box.setOrientation(camera.getOrientation());
		box.update(seconds);
		
	}

	public void drawShadowMap(Octree octree) {
		List<IEntity> visibles = octree.getVisible(getCamera());
		renderTarget.use(true);
		directionalShadowPassProgram.use();
		directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		directionalShadowPassProgram.setUniform("near", camera.getNear());
		directionalShadowPassProgram.setUniform("far", camera.getFar());
		
		for (IEntity e : visibles) {
			entityBuffer.rewind();
			e.getModelMatrix().store(entityBuffer);
			entityBuffer.rewind();
			directionalShadowPassProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
			e.getVertexBuffer().draw();
		}
	}
	
	public int getShadowMapId() {
		return renderTarget.getRenderedTexture();
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

	@Override
	public String getName() {
		return "Sportlight";
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	@Override
	public void drawDebug(Program program) {
		box.drawDebug(program);
	}

	public FloatBuffer getLightMatrix() {
		Matrix4f.mul(camera.getProjectionMatrix(), camera.getViewMatrix(), null).store(buffer);
//		camera.getViewMatrix().store(buffer);
		buffer.flip();
		return buffer;
	}

	@Override
	public Matrix4f getModelMatrix() {
		return null;
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


	@Override
	public boolean isSelected() {
		return selected;
	}

	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	@Override
	public Transform getTransform() {
		return camera.getTransform();
	}

	@Override
	public void setTransform(Transform transform) {
		camera.setTransform(transform);
	}

	public FloatBuffer getLightMatrixAsBuffer() {
		return getLightMatrix().asReadOnlyBuffer();
	}

}