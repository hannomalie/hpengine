package renderer.light;

import camera.Camera;
import component.InputControllerComponent;
import component.ModelComponent;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.constants.CullMode;
import renderer.material.Material;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import shader.Program;
import shader.ProgramFactory;
import util.Util;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.List;

import static renderer.constants.CullMode.BACK;
import static renderer.constants.CullMode.FRONT;
import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public class DirectionalLight extends Entity {
	public static final int SHADOWMAP_RESOLUTION = 2048;

	private boolean castsShadows = false;
	transient FloatBuffer entityBuffer;

	private Vector3f color = new Vector3f(1,1,1);
	private float scatterFactor = 1f;

	transient private RenderTarget renderTarget;
	transient private Entity box;
	transient private Program directionalShadowPassProgram;
	private Camera camera;

	private transient boolean needsShadowMapRedraw = true;

	public DirectionalLight() {
		this(true);
	}

	public DirectionalLight(boolean castsShadows) {
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

		addComponent(new InputControllerComponent() {
			private static final long serialVersionUID = 1L;

			@Override public void update(float seconds) {

				float moveAmount = 100* seconds;
				float rotateAmount = 100*seconds;

				if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
					getEntity().rotate(new Vector3f(0, 0, 1), rotateAmount * 45 / 40);
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
					getEntity().rotate(new Vector3f(0, 0, 1), rotateAmount * -45 / 40);
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
					getEntity().rotate(new Vector3f(1, 0, 0), rotateAmount * 45 / 40);
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
					getEntity().rotate(new Vector3f(1, 0, 0), rotateAmount * -45 / 40);
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD8)) {
					getEntity().move(new Vector3f(0, -moveAmount, 0));
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)) {
					getEntity().move(new Vector3f(0, moveAmount, 0));
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)) {
					getEntity().move(new Vector3f(-moveAmount, 0, 0));
				}
				if (Keyboard.isKeyDown(Keyboard.KEY_NUMPAD6)) {
					getEntity().move(new Vector3f(moveAmount, 0, 0));
				}
			}
		});

//		try {
//			Model model = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
//			box = getWorld().getEntityFactory().getEntity(getPosition(), "DefaultCube", model, white);
//			box.setScale(0.4f);
//			ModelComponent modelComponent = new ModelComponent(model);
//			modelComponent.init(world);
//			addComponent(modelComponent);
//			camera.addComponent(modelComponent);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
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
	public void init() {
		super.init();
		initialized = false;
		entityBuffer = BufferUtils.createFloatBuffer(16);

		directionalShadowPassProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "shadowmap_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);

		renderTarget = new RenderTargetBuilder()
							.setWidth(SHADOWMAP_RESOLUTION)
							.setHeight(SHADOWMAP_RESOLUTION)
							.setClearRGBA(1f, 1f, 1f, 1f)
							.add(3, new ColorAttachmentDefinition()
									.setInternalFormat(GL30.GL_RGBA32F)
									.setTextureFilter(GL11.GL_NEAREST))
							.build();

        setHasMoved(true);
		initialized = true;
	}

	@Override
	public void update(float seconds) {
		if(hasMoved()) {setNeedsShadowMapRedraw(true);}
//        System.out.println(hasMoved());
        super.update(seconds);
	}

	public void drawAsMesh(Camera camera) {
		getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(camera, getTransform().getTransformationBuffer(), 0);
		});
		camera.getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(camera, camera.getTransform().getTransformationBuffer(), 0);
		});
	}

	public void drawShadowMap(Octree octree) {
		if(!isInitialized()) { return; }
		if(!needsShadowMapRedraw) { return; }
		OpenGLContext.getInstance().depthMask(true);
		OpenGLContext.getInstance().enable(DEPTH_TEST);
		OpenGLContext.getInstance().cullFace(BACK);
		OpenGLContext.getInstance().enable(CULL_FACE);
		
		List<Entity> visibles = octree.getEntities();//getVisible(getCamera());
		renderTarget.use(true);
		directionalShadowPassProgram.use();
		directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());

		for (Entity e : visibles) {
			e.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {

				if (modelComponent.getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
					OpenGLContext.getInstance().disable(CULL_FACE);
				} else {
					OpenGLContext.getInstance().enable(CULL_FACE);
				}
				directionalShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
				modelComponent.getMaterial().setTexturesActive(directionalShadowPassProgram);
				directionalShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
				directionalShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

				modelComponent.getVertexBuffer().draw();
			});
		}
//		OpenGLContext.getInstance().enable(CULL_FACE);
		setNeedsShadowMapRedraw(false);
	}

	public int getShadowMapId() {
		if(!initialized) { return -1; }
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
	public String getName() {
		return "Directional light";
	}

	public void drawDebug(Program program) {
		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			modelComponent.drawDebug(program, getTransform().getTransformationBuffer());
		});
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

	public boolean isNeedsShadowMapRedraw() {
		return needsShadowMapRedraw;
	}

	public void setNeedsShadowMapRedraw(boolean needsShadowMapRedraw) {
		this.needsShadowMapRedraw = needsShadowMapRedraw;
	}
}
