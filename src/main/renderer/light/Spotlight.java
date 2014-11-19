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
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.renderer.rendertarget.RenderTarget;
import main.shader.Program;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Spotlight implements IEntity {
	
	private boolean castsShadows = false;

	private Camera camera;

	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	
	private RenderTarget renderTarget;
	private IEntity box;

	private Vector3f color = new Vector3f(1,1,1);

	private boolean selected;

	private Program directionalShadowPassProgram;

	private Renderer renderer;

	private float scatterFactor;
	
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
		

		directionalShadowPassProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "shadowmap_fragment.glsl", Entity.DEFAULTCHANNELS, true);
		
		renderTarget = new RenderTarget(1024, 1024, GL30.GL_RGBA16F, 1f, 1f, 1f, 1f, GL11.GL_NEAREST, 3);
		this.camera = camera;
		this.renderer = renderer;
		this.color = new Vector3f(1f, 0.76f, 0.49f);
		setScatterFactor(7f);
	}

	public void init(Renderer renderer) throws Exception {
//		camera =  new Camera(renderer, Util.createPerpective(60f, (float)Renderer.WIDTH / (float)Renderer.HEIGHT, 0.1f, 100f));
		camera =  new Camera(renderer, Util.createOrthogonal(-200f, 200f, 200f, -200f, -500f, 500f), Util.lookAt(new Vector3f(1,1,1), new Vector3f(0,0,0), new Vector3f(0, 1f, 0)), 0.1f, 500f);
		setPosition(new Vector3f(12f,80f,2f));
		Quaternion quat = new Quaternion(0.77555925f, 0.22686659f, 0.36588323f, 0.46171495f);
//		quat.setFromAxisAngle(new Vector4f(0,-1,0,0));
		setOrientation(quat);
		init(renderer, camera);
	}

	public void update(float seconds) {
		
		camera.updateShadow();

		box.setPosition(getPosition());
		box.setOrientation(getOrientation());
		box.update(seconds);
	}

	public void drawShadowMap(Octree octree) {
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		
		List<IEntity> visibles = octree.getEntities();//getVisible(getCamera());
		renderTarget.use(true);
		directionalShadowPassProgram.use();
		directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
//		directionalShadowPassProgram.setUniform("near", camera.getNear());
//		directionalShadowPassProgram.setUniform("far", camera.getFar());
		
		for (IEntity e : visibles) {
			entityBuffer.rewind();
			e.getModelMatrix().store(entityBuffer);
			entityBuffer.rewind();
			directionalShadowPassProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
			e.getMaterial().setTexturesActive((Entity) e, directionalShadowPassProgram);
			directionalShadowPassProgram.setUniform("hasDiffuseMap", e.getMaterial().hasDiffuseMap());
			directionalShadowPassProgram.setUniform("color", e.getMaterial().getDiffuse());

			e.getVertexBuffer().draw();
		}
	}

	public int getShadowMapId() {
		return renderTarget.getRenderedTexture();
	}
	public int getShadowMapWorldPositionId() {
		return renderTarget.getRenderedTexture(2);
	}
	public int getShadowMapColorMapId() {
		return renderTarget.getRenderedTexture(1);
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
		buffer.flip();
		return buffer;
	}

	public FloatBuffer getLightMatrixAsBuffer() {
		return getLightMatrix().asReadOnlyBuffer();
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

	public float getScatterFactor() {
		return scatterFactor;
	}
	public void setScatterFactor(float scatterFactor) {
		this.scatterFactor = scatterFactor;
	}

}
