package de.hanno.hpengine.engine.scene;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.Octree;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.ProbeAddedEvent;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.shader.AbstractProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.manager.Manager;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.scene.EnvironmentProbe.Update;
import de.hanno.hpengine.util.Util;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;

public class EnvironmentProbeManager implements Manager{
	public static final int MAX_PROBES = 25;
	public static final int RESOLUTION = 256;
	public static final int CUBEMAPMIPMAPCOUNT = Util.calculateMipMapCount(RESOLUTION);
	
	public static Update DEFAULT_PROBE_UPDATE = Update.DYNAMIC;
	private final Engine engine;

	private List<EnvironmentProbe> probes = new ArrayList<>();

	private RenderProbeCommandQueue renderProbeCommandQueue = new RenderProbeCommandQueue();

	private CubeMapArray environmentMapsArray;
	private CubeMapArray environmentMapsArray1;
	private CubeMapArray environmentMapsArray2;
	private CubeMapArray environmentMapsArray3;
    private CubeMapArrayRenderTarget cubeMapArrayRenderTarget;

	private FloatBuffer minPositions = BufferUtils.createFloatBuffer(0);
	private FloatBuffer maxPositions = BufferUtils.createFloatBuffer(0);
	private FloatBuffer weights = BufferUtils.createFloatBuffer(0);

	public EnvironmentProbeManager(Engine engine) {
    	this.engine = engine;
		this.environmentMapsArray = new CubeMapArray(engine.getGpuContext(), MAX_PROBES, GL11.GL_LINEAR, RESOLUTION);
		this.environmentMapsArray1 = new CubeMapArray(engine.getGpuContext(), MAX_PROBES, GL11.GL_LINEAR, GL11.GL_RGBA8, RESOLUTION);
		this.environmentMapsArray2 = new CubeMapArray(engine.getGpuContext(), MAX_PROBES, GL11.GL_LINEAR, GL11.GL_RGBA8, RESOLUTION);
		this.environmentMapsArray3 = new CubeMapArray(engine.getGpuContext(), MAX_PROBES, GL11.GL_LINEAR_MIPMAP_LINEAR, RESOLUTION);
        this.cubeMapArrayRenderTarget = new CubeMapArrayRenderTarget(engine.getGpuContext(), EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION, 1, environmentMapsArray, environmentMapsArray1, environmentMapsArray2, environmentMapsArray3);

//		DeferredRenderer.exitOnGLError("EnvironmentProbeManager constructor");
	}

	public EnvironmentProbe getProbe(Vector3f center, float size) throws Exception {
		return getProbe(center, size, DEFAULT_PROBE_UPDATE, 1.0f);
	}
	public EnvironmentProbe getProbe(Vector3f center, float size, float weight) throws Exception {
		return getProbe(center, size, DEFAULT_PROBE_UPDATE, weight);
	}

	public EnvironmentProbe getProbe(Vector3f center, float size, Update update, float weight) throws Exception {
		return getProbe(center, new Vector3f(size, size, size), update, weight);
	}
	public EnvironmentProbe getProbe(Vector3f center, Vector3f size, Update update, float weight) throws Exception {
		EnvironmentProbe probe = new EnvironmentProbe(engine, center, size, RESOLUTION, update, getProbes().size(), weight);
		probes.add(probe);
		updateBuffers();
        engine.getEventBus().post(new ProbeAddedEvent(probe));
		return probe;
	}
	
	public void updateBuffers() {
		minPositions = BufferUtils.createFloatBuffer(100*3);
		maxPositions = BufferUtils.createFloatBuffer(100*3);
		weights = BufferUtils.createFloatBuffer(100);
		float[] srcMinPositions = new float[100*3];
		float[] srcMaxPositions = new float[100*3];
		float[] srcWeights = new float[100];
		
		for(int i = 0; i < probes.size(); i++) {
			AABB box = probes.get(i).getBox();
			Vector3f min = box.getBottomLeftBackCorner();
			Vector3f max = box.getTopRightForeCorner();
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
		if(!Config.getInstance().isDrawProbes()) { return; }
		
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
		if(!Config.getInstance().isDrawProbes()) { return; }

		prepareProbeRendering();
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().
				filter(probe -> { return probe.update == Update.DYNAMIC; }).
				sorted(new Comparator<EnvironmentProbe>() {

					@Override
					public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
//						Vector3f center1 = o1.getCenter();
//						Vector3f center2 = o2.getCenter();
//						Vector4f center1InView = new Matrix4f().(de.hanno.hpengine.camera.getViewMatrix(), new Vector4f(center1.x, center1.y, center1.z, 1f), null);
//						Vector4f center2InView = new Matrix4f().(de.hanno.hpengine.camera.getViewMatrix(), new Vector4f(center2.x, center2.y, center2.z, 1f), null);
//						return Float.compare(-center1InView.z, -center2InView.z);
						return Float.compare(new Vector3f(o1.getCenter()).sub(camera.getPosition().negate()).lengthSquared(), new Vector3f(o2.getCenter()).sub(camera.getPosition().negate()).lengthSquared());
					}
				}).
				collect(Collectors.toList());
		
//		int counter = 0;
		for (int i = 1; i <= dynamicProbes.size(); i++) {
//			if (counter >= MAX_PROBES_PER_FRAME_DRAW_COUNT) { return; } else { counter++; }
			EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
//			environmentProbe.draw(octree, lights);
            addRenderProbeCommand(environmentProbe);
		}
	}

	public void prepareProbeRendering() {
        engine.getGpuContext().depthMask(true);
        engine.getGpuContext().enable(DEPTH_TEST);
        engine.getGpuContext().enable(CULL_FACE);
		cubeMapArrayRenderTarget.use(false);
	}
	
	public void drawDebug(Program program, Octree octree) {
		List<float[]> arrays = new ArrayList<>();

		for (EnvironmentProbe probe : engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes()) {
			probe.drawDebug(program);
//			arrays.add(probe.getBox().getPointsAsArray());

//			Vector3f clipStart = new Vector3f(probe.getCenter(), (Vector3f) probe.getRightDirection().mul(probe.getCamera().getNear()), null);
//			Vector3f clipEnd = new Vector3f(probe.getCenter(), (Vector3f) probe.getCamera().getRightDirection().scale(probe.getCamera().getFar()), null);
//			renderer.batchLine(clipStart, clipEnd);

			program.setUniform("diffuseColor", new Vector3f(0,1,1));
            engine.getRenderer().drawLines(program);
		}
		
		// 72 floats per array
		float[] points = new float[arrays.size() * 72];
		for (int i = 0; i < arrays.size(); i++) {
			float[] array = arrays.get(i);
			for (int z = 0; z < 72; z++) {
				points[24*3*i + z] = array[z];
			}
		};
		VertexBuffer buffer = new VertexBuffer(engine.getGpuContext(), points, EnumSet.of(DataChannels.POSITION3));
		buffer.upload();
		program.setUniform("diffuseColor", new Vector3f(0,1,0));
		buffer.drawDebug();
		octree.getEntities().stream().forEach(e -> {
			Optional<EnvironmentProbe> option = getProbeForEntity(e);
			option.ifPresent(probe -> {
                engine.getRenderer().batchLine(probe.getCenter(), e.getPosition());
			});
		});
		buffer.delete();
	}
	
	public<T extends Entity> Optional<EnvironmentProbe> getProbeForEntity(T entity) {
		return probes.stream().filter(probe -> {
			return probe.contains(entity.getMinMaxWorld());
		}).sorted(new Comparator<EnvironmentProbe>() {
			@Override
			public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
				return (Float.compare(entity.getCenter().distance(o1.getCenter()), entity.getCenter().distance(o2.getCenter())));
			}
		}).findFirst();
		
	}
	
	public List<EnvironmentProbe> getProbes() {
		return probes;
	}

	public void drawInitial(Octree octree) {
		draw();
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
		return probes.stream().filter(probe -> {
			return probe.contains(entity.getMinMaxWorld());
		}).sorted(new Comparator<EnvironmentProbe>() {
			@Override
			public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
				return (Float.compare(entity.getCenter().distance(o1.getCenter()), entity.getCenter().distance(o2.getCenter())));
			}
		}).collect(Collectors.toList());
	}

	public boolean remove(EnvironmentProbe probe) {
		return probes.remove(probe);
	}

	public CubeMapArrayRenderTarget getCubeMapArrayRenderTarget() {
		return cubeMapArrayRenderTarget;
	}

	public void bindEnvironmentProbePositions(AbstractProgram program) {
		program.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMin", engine.getSceneManager().getScene().getEnvironmentProbeManager().getMinPositions());
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMax", engine.getSceneManager().getScene().getEnvironmentProbeManager().getMaxPositions());
		program.setUniformFloatArrayAsFloatBuffer("environmentMapWeights", engine.getSceneManager().getScene().getEnvironmentProbeManager().getWeights());

//		EnvironmentProbeManager.getInstance().getProbes().forEach(probe -> {
//			int probeIndex = probe.getIndex();
//			program.setUniform(String.format("environmentMapMin[%d]", probeIndex), probe.getBox().getBottomLeftBackCorner());
//			program.setUniform(String.format("environmentMapMax[%d]", probeIndex), probe.getBox().getTopRightForeCorner());
//		});
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

	public void update(Float deltaSeconds) {
//		TODO: Render Probes here
	}

	public void clearProbes() {
		probes.forEach( p -> { engine.getEventBus().unregister(p.getSampler()); });
		probes.clear();
	}

	@Override
	public void clear() {
		clearProbes();
	}

	@Override
	public void update(float deltaSeconds) {
		probes.forEach(p -> p.update(deltaSeconds));
	}

	@Override
	public void onEntityAdded() {

	}
}
