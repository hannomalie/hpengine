package renderer.environmentsampler;

import camera.Camera;
import com.google.common.eventbus.Subscribe;
import component.ModelComponent;
import config.Config;
import container.EntitiesContainer;
import engine.AppContext;
import engine.PerEntityInfo;
import engine.model.Entity;
import event.MaterialChangedEvent;
import org.lwjgl.opengl.ARBBindlessTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.DeferredRenderer;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.GBuffer;
import renderer.drawstrategy.extensions.DrawLightMapExtension;
import renderer.light.AreaLight;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.rendertarget.CubeMapArrayRenderTarget;
import scene.EnvironmentProbeFactory;
import scene.Scene;
import shader.Program;
import shader.ProgramFactory;
import util.Util;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static renderer.constants.GlCap.DEPTH_TEST;
import static renderer.constants.GlDepthFunc.LEQUAL;
import static renderer.constants.GlTextureTarget.TEXTURE_2D;

public class LightmapEnvironmentSampler extends Camera {
    private static int currentVolumeIndex = 0;
    private final int volumeIndex;
    private final CubeMapArrayRenderTarget cubeMapArrayRenderTarget;
    private Program cubeMapProgram;
    transient private boolean drawnOnce = false;
	transient Set<Integer> sidesDrawn = new HashSet<>();
	private int cubeMapView;

	private int cubeMapFaceViews[] = new int[6];
    private long cubeMapViewHandle;

    public LightmapEnvironmentSampler(Vector3f position) throws Exception {
		super(0.1f, 5000f, 90f, 1f);
        volumeIndex = currentVolumeIndex;
        currentVolumeIndex++;
        setPosition(position);
		init();
        float far = 5000f;
		float near = 0.1f;
		float fov = 90f;
		Matrix4f projectionMatrix = Util.createPerpective(fov, 1, near, far);
		setFar(far);
		setNear(near);
		setFov(fov);
		setRatio(1f);
//		projectionMatrix = Util.createOrthogonal(position.x-width/2, position.x+width/2, position.y+height/2, position.y-height/2, near, far);
		Quaternion cubeMapCamInitialOrientation = new Quaternion();
		Quaternion.setIdentity(cubeMapCamInitialOrientation);
		setOrientation(cubeMapCamInitialOrientation);
//		rotate(new Vector4f(0, 1, 0, 90));
//		setPosition(position);

		ProgramFactory programFactory = ProgramFactory.getInstance();
		cubeMapProgram = programFactory.getProgram("lightmap_cubemap_vertex.glsl", "lightmap_cubemap_geometry.glsl", "lightmap_cubemap_fragment.glsl", true);

		cubeMapArrayRenderTarget = EnvironmentProbeFactory.getInstance().getLightMapCubeMapArrayRenderTarget();
		cubeMapView = OpenGLContext.getInstance().genTextures();

		DeferredRenderer.exitOnGLError("EnvironmentSampler before view creation");
        OpenGLContext.getInstance().execute(() -> {
            for (int z = 0; z < 6; z++) {
                cubeMapFaceViews[z] = OpenGLContext.getInstance().genTextures();
                GL43.glTextureView(cubeMapFaceViews[z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * volumeIndex + z, 1);
            }
            DeferredRenderer.exitOnGLError("EnvironmentSampler constructor A");
            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, Util.calculateMipMapCount(DrawLightMapExtension.PROBE_RESOLUTION), 6*volumeIndex, 6);

            cubeMapViewHandle = ARBBindlessTexture.glGetTextureHandleARB(cubeMapView);
            ARBBindlessTexture.glMakeTextureHandleResidentARB(cubeMapViewHandle);
            DeferredRenderer.exitOnGLError("EnvironmentSampler constructor B");

        });
		DeferredRenderer.exitOnGLError("EnvironmentSampler constructor");
	}

	public void drawCubeMap(boolean urgent, RenderExtract extract) {
		drawCubeMapSides(urgent, extract);
	}

	private void drawCubeMapSides(boolean urgent, RenderExtract extract) {
        Scene scene = AppContext.getInstance().getScene();
        if(scene == null) { return; }

        EntitiesContainer octree = scene.getEntitiesContainer();
		GPUProfiler.start("Cubemap render 6 sides");
		Quaternion initialOrientation = getOrientation();
		Vector3f initialPosition = getPosition();

		DirectionalLight light = scene.getDirectionalLight();
		EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray().bind(8);
		EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(0).bind(10);

		OpenGLContext.getInstance().enable(DEPTH_TEST);
		OpenGLContext.getInstance().depthFunc(LEQUAL);

        cubeMapArrayRenderTarget.use(false);
		bindProgramSpecificsPerCubeMap();

		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, this);

			GPUProfiler.start("side " + i);
            cubeMapProgram.setUniform("layer", 6 * volumeIndex + i);
            //TODO Fix color clearing....
//            OpenGLContext.getInstance().clearDepthAndColorBuffer();
            OpenGLContext.getInstance().clearDepthBuffer();
            drawEntities(cubeMapProgram, octree.getEntities(), getViewMatrixAsBuffer(), getProjectionMatrixAsBuffer());
			GPUProfiler.end();
		}
		setPosition(initialPosition);
		setOrientation(initialOrientation);
		GPUProfiler.end();
	}

	private void registerSideAsDrawn(int i) {
		sidesDrawn.add(i);
		if(sidesDrawn.size() == 6) {
			drawnOnce = true;
		}
	}
	
	public void resetDrawing() {
		sidesDrawn.clear();
		drawnOnce = false;
	}
	
	@Subscribe
	public void handle(MaterialChangedEvent e) {
		resetDrawing();
	}

	private void bindProgramSpecificsPerCubeMap() {
		cubeMapProgram.use();
		cubeMapProgram.setUniform("firstBounceForProbe", GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
		cubeMapProgram.setUniform("activePointLightCount", AppContext.getInstance().getScene().getPointLights().size());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightPositions", LightFactory.getInstance().getPointLightPositions());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightColors", LightFactory.getInstance().getPointLightColors());
		cubeMapProgram.setUniformFloatArrayAsFloatBuffer("pointLightRadiuses", LightFactory.getInstance().getPointLightRadiuses());
		
		cubeMapProgram.setUniform("activeAreaLightCount", AppContext.getInstance().getScene().getAreaLights().size());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightPositions", LightFactory.getInstance().getAreaLightPositions());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightColors", LightFactory.getInstance().getAreaLightColors());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightWidthHeightRanges", LightFactory.getInstance().getAreaLightWidthHeightRanges());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightViewDirections", LightFactory.getInstance().getAreaLightViewDirections());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightUpDirections", LightFactory.getInstance().getAreaLightUpDirections());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightRightDirections", LightFactory.getInstance().getAreaLightRightDirections());
		
		for(int i = 0; i < Math.min(AppContext.getInstance().getScene().getAreaLights().size(), LightFactory.MAX_AREALIGHT_SHADOWMAPS); i++) {
			AreaLight areaLight =AppContext.getInstance().getScene().getAreaLights().get(i);
			OpenGLContext.getInstance().bindTexture(9 + i, TEXTURE_2D, LightFactory.getInstance().getDepthMapForAreaLight(areaLight));
			cubeMapProgram.setUniformAsMatrix4("areaLightShadowMatrices[" + i + "]", LightFactory.getInstance().getShadowMatrixForAreaLight(areaLight));
		}
		
		cubeMapProgram.setUniform("probeIndex", volumeIndex);
		EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(cubeMapProgram);
	}

	private void drawEntities(Program program, List<Entity> visibles, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer, projectionMatrixAsBuffer);

		GPUProfiler.start("Cubemapside draw entities");
		for (Entity e : visibles) {
			if(!e.isInFrustum(this)) { continue; }
			e.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
				program.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
				modelComponent.getMaterial().setTexturesActive(program);
				program.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
				program.setUniform("hasNormalMap", modelComponent.getMaterial().hasNormalMap());
				program.setUniform("color", modelComponent.getMaterial().getDiffuse());
				program.setUniform("metallic", modelComponent.getMaterial().getMetallic());
				program.setUniform("roughness", modelComponent.getMaterial().getRoughness());
				modelComponent.getMaterial().setTexturesActive(program);

                DrawStrategy.draw(new PerEntityInfo(null, program, AppContext.getInstance().getScene().getEntityIndexOf(e), AppContext.getInstance().getScene().getEntityIndexOf(e), true, false, false, null, modelComponent.getMaterial(), true, e.getInstanceCount(), true, e.getUpdate(), e.getMinMaxWorld()[0], e.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex()));
			});
		}
		GPUProfiler.end();
	}
	
	private void bindShaderSpecificsPerCubeMapSide(FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		GPUProfiler.start("Matrix uniforms");
		DirectionalLight light = AppContext.getInstance().getScene().getDirectionalLight();
		cubeMapProgram.setUniform("lightDirection", light.getViewDirection());
		cubeMapProgram.setUniform("lightDiffuse", light.getColor());
		cubeMapProgram.setUniform("lightAmbient", Config.AMBIENT_LIGHT);
		cubeMapProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("shadowMatrix", light.getViewMatrixAsBuffer());
		GPUProfiler.end();
	}

	private void rotateForIndex(int i, Entity camera) {
		float deltaNear = 0.0f;
		float deltaFar = 100.0f;
		Vector3f position = camera.getPosition();//.negate(null); // TODO: AHHhhhh, kill this hack
//		Matrix4f projectionMatrix = Util.createOrthogonal(position.z-width/2, position.z+width/2, position.y+height/2, position.y-height/2, getCamera().getNear(), getCamera().getFar());
//		Transform oldTransform = camera.getTransform();
//		camera = new Camera(renderer, projectionMatrix, getCamera().getNear(), getCamera().getFar(), 90, 1);
//		camera.setPerspective(false);
//		camera.setTransform(oldTransform);
//		camera.updateShadow();

		switch (i) {
		case 0:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0,1,0, -90));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			break;
		case 1:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0, 1, 0, 90));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			break;
		case 2:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(1, 0, 0, 90));
			camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			break;
		case 3:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(1, 0, 0, -90));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			break;
		case 4:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0, 1, 0, -180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			break;
		case 5:
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
//			camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			break;
		default:
			break;
		}
	}

	public int getCubeMapView() {
		return cubeMapView;
	}

	public int[] getCubeMapFaceViews() {
		return cubeMapFaceViews;
	}

    public long getCubeMapViewHandle() {
        return cubeMapViewHandle;
    }
}
