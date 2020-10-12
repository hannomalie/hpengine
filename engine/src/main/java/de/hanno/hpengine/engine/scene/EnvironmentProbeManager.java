package de.hanno.hpengine.engine.scene;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.backend.OpenGlBackendKt;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.ProbeAddedEvent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter;
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.shader.AbstractProgram;
import de.hanno.hpengine.engine.graphics.state.EnvironmentProbeState;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderSystem;
import de.hanno.hpengine.engine.manager.Manager;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.model.texture.TextureDimension;
import de.hanno.hpengine.engine.model.texture.TextureDimension3D;
import de.hanno.hpengine.engine.scene.EnvironmentProbe.Update;
import de.hanno.hpengine.util.Util;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;
import static de.hanno.hpengine.engine.vertexbuffer.VertexBufferExtensionsKt.drawLines;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;

public class EnvironmentProbeManager implements Manager, RenderSystem {
	public static final int MAX_PROBES = 25;
	public static final int RESOLUTION = 256;
	public static final int CUBEMAP_MIPMAP_COUNT = Util.calculateMipMapCount(RESOLUTION);
	
	public static Update DEFAULT_PROBE_UPDATE = Update.DYNAMIC;
	private final EngineContext engine;

	private List<EnvironmentProbe> probes = new ArrayList<>();

	private RenderProbeCommandQueue renderProbeCommandQueue = new RenderProbeCommandQueue();

	private CubeMapArray environmentMapsArray;
	private CubeMapArray environmentMapsArray1;
	private CubeMapArray environmentMapsArray2;
	private CubeMapArray environmentMapsArray3;
    private CubeMapArrayRenderTarget cubeMapArrayRenderTarget;

	private FloatBuffer minPositions = BufferUtils.createFloatBuffer(100*3);
	private FloatBuffer maxPositions = BufferUtils.createFloatBuffer(100*3);
	private FloatBuffer weights = BufferUtils.createFloatBuffer(100*3);

	public EnvironmentProbeManager(EngineContext engineContext) {
    	this.engine = engineContext;
		TextureDimension3D dimension = TextureDimension.Companion.invoke(RESOLUTION, RESOLUTION, MAX_PROBES);
		TextureFilterConfig filterConfig = new TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR);
		int wrapMode = GL_REPEAT;
		GpuContext gpuContext = OpenGlBackendKt.getGpuContext(engineContext);
		this.environmentMapsArray = CubeMapArray.Companion.invoke(gpuContext, dimension, filterConfig, GL_RGBA32F, wrapMode);
		this.environmentMapsArray1 = CubeMapArray.Companion.invoke(gpuContext, dimension, filterConfig, GL_RGBA8, wrapMode);
		this.environmentMapsArray2 = CubeMapArray.Companion.invoke(gpuContext, dimension, filterConfig, GL_RGBA8, wrapMode);
		this.environmentMapsArray3 = CubeMapArray.Companion.invoke(gpuContext, dimension, filterConfig, GL_RGBA8, wrapMode);
        this.cubeMapArrayRenderTarget = CubeMapArrayRenderTarget.Companion.invoke(gpuContext, EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION, "CubeMapArrayRenderTarget", new Vector4f(0, 0, 0, 0), environmentMapsArray, environmentMapsArray1, environmentMapsArray2, environmentMapsArray3);

//		DeferredRenderer.exitOnGLError("EnvironmentProbeManager constructor");
	}


	@Override
	public void beforeSetScene(@NotNull Scene currentScene, @NotNull Scene scene) {
		clearProbes();
	}
	public EnvironmentProbe getProbe(Entity entity, Vector3f center, float size) throws Exception {
		return getProbe(entity, center, size, DEFAULT_PROBE_UPDATE, 1.0f);
	}
	public EnvironmentProbe getProbe(Entity entity, Vector3f center, float size, float weight) throws Exception {
		return getProbe(entity, center, size, DEFAULT_PROBE_UPDATE, weight);
	}

	public EnvironmentProbe getProbe(Entity entity, Vector3f center, float size, Update update, float weight) throws Exception {
		return getProbe(entity, center, new Vector3f(size, size, size), update, weight);
	}
	public EnvironmentProbe getProbe(Entity entity, Vector3f center, Vector3f size, Update update, float weight) throws Exception {
		EnvironmentProbe probe = new EnvironmentProbe(engine, entity, center, size, RESOLUTION, update, getProbes().size(), weight, this);
		probes.add(probe);
		updateBuffers();
        OpenGlBackendKt.getEventBus(engine).post(new ProbeAddedEvent(probe));
		return probe;
	}
	
	public void updateBuffers() {
		float[] srcMinPositions = new float[100*3];
		float[] srcMaxPositions = new float[100*3];
		float[] srcWeights = new float[100];
		
		for(int i = 0; i < probes.size(); i++) {
			AABB box = probes.get(i).getBox();
			box.move(new Vector3f(box.getMin()).add(box.getHalfExtents()).negate());
			box.move(probes.get(i).getEntity().getTransform().getPosition());
			Vector3f min = new Vector3f(box.getMin());
			Vector3f max = new Vector3f(box.getMax());
			float weight = probes.get(i).getWeight();
			
			srcMinPositions[3*i] = min.x;
			srcMinPositions[3*i+1] = min.y;
			srcMinPositions[3*i+2] = min.z;
			
			srcMaxPositions[3*i] = max.x;
			srcMaxPositions[3*i+1] = max.y;
			srcMaxPositions[3*i+2] = max.z;
			
			srcWeights[i] = weight;
		}
		
		minPositions.put(srcMinPositions);
		maxPositions.put(srcMaxPositions);
		weights.put(srcWeights);
		
		minPositions.rewind();
		maxPositions.rewind();
		weights.rewind();
	}

	public FloatBuffer getMinPositions() {
		return minPositions;
	}
	public FloatBuffer getMaxPositions() {
		return maxPositions;
	}
	public FloatBuffer getWeights() {
		return weights;
	}

	public void draw() {
		draw(false);
	}
	public void draw(boolean urgent) {
		if(!engine.getConfig().getQuality().isDrawProbes()) { return; }
		
		prepareProbeRendering();
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().
				filter(probe -> probe.update == Update.DYNAMIC).
				collect(Collectors.toList());
		
		for (int i = 1; i <= dynamicProbes.size(); i++) {
			EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
            addRenderProbeCommand(environmentProbe, urgent);
		}
	}
	
	public void drawAlternating(Entity camera) {
		if(!engine.getConfig().getQuality().isDrawProbes()) { return; }

		prepareProbeRendering();
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().
				filter(probe -> probe.update == Update.DYNAMIC).
				sorted((o1, o2) -> Float.compare(new Vector3f(o1.getEntity().getTransform().getCenter()).sub(camera.getTransform().getPosition().negate()).lengthSquared(), new Vector3f(o2.getEntity().getTransform().getCenter()).sub(camera.getTransform().getPosition().negate()).lengthSquared())).
				collect(Collectors.toList());
		
		for (int i = 1; i <= dynamicProbes.size(); i++) {
			EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
            addRenderProbeCommand(environmentProbe);
		}
	}

	public void prepareProbeRendering() {
        OpenGlBackendKt.getGpuContext(engine).setDepthMask(true);
        OpenGlBackendKt.getGpuContext(engine).enable(DEPTH_TEST);
        OpenGlBackendKt.getGpuContext(engine).enable(CULL_FACE);
		cubeMapArrayRenderTarget.use(OpenGlBackendKt.getGpuContext(engine), false);
	}


	public<T extends Entity> Optional<EnvironmentProbe> getProbeForEntity(T entity) {
		return probes.stream().filter(probe -> probe.contains(entity.getBoundingVolume())).sorted((o1, o2) -> (Float.compare(entity.getTransform().getCenter().distance(o1.getEntity().getTransform().getCenter()), entity.getTransform().getCenter().distance(o2.getEntity().getTransform().getCenter())))).findFirst();
	}
	
	public List<EnvironmentProbe> getProbes() {
		return probes;
	}

	public CubeMapArray getEnvironmentMapsArray() {
		return environmentMapsArray;
	}
	public CubeMapArray getEnvironmentMapsArray(int index) {
		switch (index) {
		case 0:
			return environmentMapsArray;
		case 1:
			return environmentMapsArray1;
		case 2:
			return environmentMapsArray2;
		case 3:
			return environmentMapsArray3;
		default:
			return null;
		}
	}

	public List<EnvironmentProbe> getProbesForEntity(Entity entity) {
		return probes.stream().filter(probe -> probe.contains(entity.getBoundingVolume())).sorted((o1, o2) -> (Float.compare(entity.getTransform().getCenter().distance(o1.getEntity().getTransform().getCenter()), entity.getTransform().getCenter().distance(o2.getEntity().getTransform().getCenter())))).collect(Collectors.toList());
	}

	public boolean remove(EnvironmentProbe probe) {
		return probes.remove(probe);
	}

	public CubeMapArrayRenderTarget getCubeMapArrayRenderTarget() {
		return cubeMapArrayRenderTarget;
	}

	public void bindEnvironmentProbePositions(AbstractProgram program) {
		bindEnvironmentProbePositions(program, getProbes().size(), getMinPositions(), getMaxPositions(), getWeights());
	}
	public static void bindEnvironmentProbePositions(AbstractProgram program, EnvironmentProbeState state) {
		bindEnvironmentProbePositions(program, state.getActiveProbeCount(), state.getEnvironmentMapMin(), state.getEnvironmentMapMax(), state.getEnvironmentMapWeights());
	}
	public static void bindEnvironmentProbePositions(AbstractProgram program, int activeProbeCount, FloatBuffer minPositions, FloatBuffer maxPositions, FloatBuffer weights) {
		program.setUniform("activeProbeCount", activeProbeCount);
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMin", minPositions);
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMax", maxPositions);
		program.setUniformFloatArrayAsFloatBuffer("environmentMapWeights", weights);
	}

	public void executeRenderProbeCommands(RenderState extract) {
		int counter = 0;

		renderProbeCommandQueue.takeNearest(extract.getCamera().getEntity()).ifPresent(command -> {
			command.getProbe().draw(command.isUrgent(), extract);
		});
		counter++;

		while(counter < RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL) {
			renderProbeCommandQueue.take().ifPresent(command -> {
				command.getProbe().draw(command.isUrgent(), extract);
			});
			counter++;
		}
	}

	public void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent) {
		renderProbeCommandQueue.addProbeRenderCommand(probe, urgent);
	}
	void addRenderProbeCommand(EnvironmentProbe probe) {
		addRenderProbeCommand(probe, false);
	}

	public void clearProbes() {
		probes.forEach( p -> { OpenGlBackendKt.getEventBus(engine).unregister(p.getSampler()); });
		probes.clear();
	}

	@Override
	public void clear() {
		clearProbes();
	}

	@Override
	public void update(@NotNull CoroutineScope scope, Scene scene, float deltaSeconds) {
		probes.forEach(p -> p.update(scope, scene, deltaSeconds));
//		TODO: This has to be completely recoded with new component design and stuff, in order to get entities from components
//		and entitymanager from scene etc.
//		probes.stream().filter(probe -> probe.getEntity().hasMoved()).findFirst().ifPresent(first -> updateBuffers());
	}

	@Override
	public void onEntityAdded(@NotNull List<Entity> entities) {

	}

	@Override
	public void render(@NotNull DrawResult result, @NotNull RenderState renderState) {
		executeRenderProbeCommands(renderState);
		drawAlternating(renderState.getCamera().getEntity());
	}

	@Override
	public void extract(@NotNull Scene scene, @NotNull RenderState renderState) {
		renderState.getEnvironmentProbesState().setEnvironmapsArray0Id(getEnvironmentMapsArray(0).getId());
		renderState.getEnvironmentProbesState().setEnvironmapsArray3Id(getEnvironmentMapsArray(3).getId());
		renderState.getEnvironmentProbesState().setActiveProbeCount(getProbes().size());
		renderState.getEnvironmentProbesState().setEnvironmentMapMin(getMinPositions());
		renderState.getEnvironmentProbesState().setEnvironmentMapMax(getMaxPositions());
		renderState.getEnvironmentProbesState().setEnvironmentMapWeights(getWeights());
	}
}
