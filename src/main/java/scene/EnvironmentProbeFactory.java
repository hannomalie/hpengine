package scene;

import config.Config;
import engine.AppContext;
import engine.model.DataChannels;
import engine.model.Entity;
import engine.model.VertexBuffer;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.light.DirectionalLight;
import renderer.rendertarget.CubeMapArrayRenderTarget;
import scene.EnvironmentProbe.Update;
import shader.AbstractProgram;
import shader.Program;
import texture.CubeMapArray;
import util.Util;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public class EnvironmentProbeFactory {
	public static final int MAX_PROBES = 25;
	public static final int RESOLUTION = 256;
	public static final int CUBEMAPMIPMAPCOUNT = Util.calculateMipMapCount(RESOLUTION);
	
	public static Update DEFAULT_PROBE_UPDATE = Update.DYNAMIC;
    private static EnvironmentProbeFactory instance;

    public static EnvironmentProbeFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call AppContext.init() before using it");
        }
        return instance;
    }
    public static void init() {
        instance = new EnvironmentProbeFactory();
    }

	private Renderer renderer;
	
	private List<EnvironmentProbe> probes = new ArrayList<>();

	private CubeMapArray environmentMapsArray;
	private CubeMapArray environmentMapsArray1;
	private CubeMapArray environmentMapsArray2;
	private CubeMapArray environmentMapsArray3;
	private CubeMapArrayRenderTarget cubeMapArrayRenderTarget;

	private FloatBuffer minPositions = BufferUtils.createFloatBuffer(0);
	private FloatBuffer maxPositions = BufferUtils.createFloatBuffer(0);
	private FloatBuffer weights = BufferUtils.createFloatBuffer(0);

	public EnvironmentProbeFactory() {
        renderer = Renderer.getInstance();
		this.environmentMapsArray = new CubeMapArray(renderer, MAX_PROBES, GL11.GL_LINEAR);
		this.environmentMapsArray1 = new CubeMapArray(renderer, MAX_PROBES, GL11.GL_LINEAR, GL11.GL_RGBA8);
		this.environmentMapsArray2 = new CubeMapArray(renderer, MAX_PROBES, GL11.GL_LINEAR, GL11.GL_RGBA8);
		this.environmentMapsArray3 = new CubeMapArray(renderer, MAX_PROBES, GL11.GL_LINEAR_MIPMAP_LINEAR);
		this.cubeMapArrayRenderTarget = new CubeMapArrayRenderTarget(EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, 1, environmentMapsArray, environmentMapsArray1, environmentMapsArray2, environmentMapsArray3);

//		DeferredRenderer.exitOnGLError("EnvironmentProbeFactory constructor");
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
		EnvironmentProbe probe = new EnvironmentProbe(AppContext.getInstance(), center, size, RESOLUTION, update, getProbes().size(), weight);
		probes.add(probe);
		updateBuffers();
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
		if(!Config.DRAW_PROBES) { return; }
		
		prepareProbeRendering();
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().
				filter(probe -> { return probe.update == Update.DYNAMIC; }).
				collect(Collectors.toList());
		
		for (int i = 1; i <= dynamicProbes.size(); i++) {
			EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
			//environmentProbe.draw(octree, light);
			renderer.addRenderProbeCommand(environmentProbe, urgent);
		}
	}
	
	public void drawAlternating(Octree octree, Entity camera, DirectionalLight light, int frameCount) {
		if(!Config.DRAW_PROBES) { return; }

		prepareProbeRendering();
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().
				filter(probe -> { return probe.update == Update.DYNAMIC; }).
				sorted(new Comparator<EnvironmentProbe>() {

					@Override
					public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
//						Vector3f center1 = o1.getCenter();
//						Vector3f center2 = o2.getCenter();
//						Vector4f center1InView = Matrix4f.transform(camera.getViewMatrix(), new Vector4f(center1.x, center1.y, center1.z, 1f), null);
//						Vector4f center2InView = Matrix4f.transform(camera.getViewMatrix(), new Vector4f(center2.x, center2.y, center2.z, 1f), null);
//						return Float.compare(-center1InView.z, -center2InView.z);
						return Float.compare(Vector3f.sub(o1.getCenter(), camera.getPosition().negate(null), null).lengthSquared(), Vector3f.sub(o2.getCenter(), camera.getPosition().negate(null), null).lengthSquared());
					}
				}).
				collect(Collectors.toList());
		
//		int counter = 0;
		for (int i = 1; i <= dynamicProbes.size(); i++) {
//			if (counter >= MAX_PROBES_PER_FRAME_DRAW_COUNT) { return; } else { counter++; }
			EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
//			environmentProbe.draw(octree, light);
			renderer.addRenderProbeCommand(environmentProbe);
		}
	}

	public void prepareProbeRendering() {
		OpenGLContext.getInstance().depthMask(true);
		OpenGLContext.getInstance().enable(DEPTH_TEST);
		OpenGLContext.getInstance().enable(CULL_FACE);
		cubeMapArrayRenderTarget.use(false);
	}
	
	public void drawDebug(Program program, Octree octree) {
		List<float[]> arrays = new ArrayList<>();
		
		for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
			probe.drawDebug(program);
//			arrays.add(probe.getBox().getPointsAsArray());

			Vector3f clipStart = Vector3f.add(probe.getCenter(), (Vector3f) probe.getRightDirection().scale(probe.getCamera().getNear()), null);
//			Vector3f clipEnd = Vector3f.add(probe.getCenter(), (Vector3f) probe.getCamera().getRightDirection().scale(probe.getCamera().getFar()), null);
//			renderer.batchLine(clipStart, clipEnd);

			program.setUniform("diffuseColor", new Vector3f(0,1,1));
		    renderer.drawLines(program);
		}
		
		// 72 floats per array
		float[] points = new float[arrays.size() * 72];
		for (int i = 0; i < arrays.size(); i++) {
			float[] array = arrays.get(i);
			for (int z = 0; z < 72; z++) {
				points[24*3*i + z] = array[z];
			}
		};
		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
		program.setUniform("diffuseColor", new Vector3f(0,1,0));
		buffer.drawDebug();
		octree.getEntities().stream().forEach(e -> {
			Optional<EnvironmentProbe> option = getProbeForEntity(e);
			option.ifPresent(probe -> {
				renderer.batchLine(probe.getCenter(), e.getPosition());
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
				return (Float.compare(Vector3f.sub(entity.getCenter(), o1.getCenter(), null).length(), Vector3f.sub(entity.getCenter(), o2.getCenter(), null).length()));
			}
		}).findFirst();
		
//		for (EnvironmentProbe environmentProbe : probes) {
//			if(environmentProbe.contains(entity.getMinMaxWorld())) {
//				System.out.println("Returning " + environmentProbe.getPosition());
//				return Optional.of(environmentProbe);
//			}
//		}
//		return Optional.empty();
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
				return (Float.compare(Vector3f.sub(entity.getCenter(), o1.getCenter(), null).length(), Vector3f.sub(entity.getCenter(), o2.getCenter(), null).length()));
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
		program.setUniform("activeProbeCount", EnvironmentProbeFactory.getInstance().getProbes().size());
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMin", EnvironmentProbeFactory.getInstance().getMinPositions());
		program.setUniformVector3ArrayAsFloatBuffer("environmentMapMax", EnvironmentProbeFactory.getInstance().getMaxPositions());
		program.setUniformFloatArrayAsFloatBuffer("environmentMapWeights", EnvironmentProbeFactory.getInstance().getWeights());

//		EnvironmentProbeFactory.getInstance().getProbes().forEach(probe -> {
//			int probeIndex = probe.getIndex();
//			program.setUniform(String.format("environmentMapMin[%d]", probeIndex), probe.getBox().getBottomLeftBackCorner());
//			program.setUniform(String.format("environmentMapMax[%d]", probeIndex), probe.getBox().getTopRightForeCorner());
//		});
	}

	public void clearProbes() {
		probes.forEach( p -> { AppContext.getEventBus().unregister(p.getSampler()); });
		probes.clear();
	}

}
