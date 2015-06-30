package renderer.light;

import camera.Camera;
import component.CameraComponent;
import component.ModelComponent;
import engine.Transform;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import renderer.Renderer;
import renderer.material.Material;
import renderer.material.Material.MAP;
import renderer.rendertarget.RenderTarget;
import shader.Program;
import util.Util;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class DirectionalLight extends Entity {
	
	private boolean castsShadows = false;

	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	
	private RenderTarget renderTarget;
	private Entity box;

	private Vector3f color = new Vector3f(1,1,1);

	private boolean selected;

	private Program directionalShadowPassProgram;

	private float scatterFactor;
	
	public DirectionalLight(boolean castsShadows) {
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

	@Override
	public void init(World world) {
		super.init(world);
		Renderer renderer = world.getRenderer();

		Material white = renderer.getMaterialFactory().getMaterial(new HashMap<MAP,String>(){{
			put(MAP.DIFFUSE,"hp/assets/textures/default.dds");
		}});

		try {
			Model model = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
			box = renderer.getEntityFactory().getEntity(getPosition(), "DefaultCube", model, white);
			box.setScale(0.4f);
		} catch (Exception e) {
			e.printStackTrace();
		}
		

		directionalShadowPassProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "shadowmap_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);
		
		renderTarget = new RenderTarget(1024, 1024, GL30.GL_RGBA32F, 1f, 1f, 1f, 1f, GL11.GL_NEAREST, 3);
		Matrix4f projectionMatrix = Util.createOrthogonal(-300f, 300f, 300f, -300f, -500f, 500f);
		Matrix4f viewMatrix = Util.lookAt(new Vector3f(1,1,1), new Vector3f(0,0,0), new Vector3f(0, 1f, 0));
		Camera camera = new Camera(renderer, projectionMatrix, viewMatrix, 0.1f, 500f, 60, 16 / 9);
		addComponent(new CameraComponent(camera));
		setPosition(new Vector3f(12f, 80f, 2f));
		Quaternion quat = new Quaternion(0.77555925f, 0.22686659f, 0.36588323f, 0.46171495f);
//		quat.setFromAxisAngle(new Vector4f(0,-1,0,0));
		setOrientation(quat);

		this.color = new Vector3f(1f, 0.76f, 0.49f);
		setScatterFactor(1f);
	}

	public void drawShadowMap(Octree octree) {
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		
		List<Entity> visibles = octree.getEntities();//getVisible(getCamera());
		renderTarget.use(true);
		directionalShadowPassProgram.use();
		directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", getComponent(CameraComponent.class).getCamera().getViewMatrixAsBuffer());
		directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", getComponent(CameraComponent.class).getCamera().getProjectionMatrixAsBuffer());
//		directionalShadowPassProgram.setUniform("near", camera.getNear());
//		directionalShadowPassProgram.setUniform("far", camera.getFar());
		
		for (Entity e : visibles) {

			e.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
				entityBuffer.rewind();
				e.getModelMatrix().store(entityBuffer);
				entityBuffer.rewind();
				directionalShadowPassProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
				modelComponent.getMaterial().setTexturesActive(directionalShadowPassProgram);
				directionalShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
				directionalShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

				modelComponent.getVertexBuffer().draw();
			});
		}
		GL11.glEnable(GL11.GL_CULL_FACE);
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
		return getComponent(CameraComponent.class).getCamera();
	}

	public void setCamera(Camera camera) {
		getComponent(CameraComponent.class).setCamera(camera);
	}

	@Override
	public String getName() {
		return "Directional light";
	}

	public void drawDebug(Program program) {
		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			modelComponent.drawDebug(program, matrix44Buffer);
		});
	}

	public FloatBuffer getLightMatrix() {
		Matrix4f viewMatrix = getComponent(CameraComponent.class).getCamera().getViewMatrix();
		//Matrix4f withOffset = Matrix4f.mul(viewMatrix, new Matrix4f().translate(new Vector3f(0, 100, 0)), null);
		Matrix4f.mul(getComponent(CameraComponent.class).getCamera().getProjectionMatrix(), viewMatrix, null).store(buffer);
		buffer.flip();
		return buffer;
	}

	public FloatBuffer getLightMatrixAsBuffer() {
		return getLightMatrix().asReadOnlyBuffer();
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

}
