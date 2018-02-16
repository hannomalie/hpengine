package de.hanno.hpengine.engine.scene;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.entity.Entity;
import org.joml.Vector3f;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.environmentsampler.EnvironmentSampler;
import de.hanno.hpengine.engine.graphics.shader.Program;

import java.util.List;
import java.util.Random;

public class EnvironmentProbe extends Entity {

	private final EnvironmentProbeManager environmentProbeManager;

	public enum Update {
		STATIC,
		DYNAMIC
	}
	
	private String name = "Probe_" + System.currentTimeMillis();
	private Renderer renderer;
	private AABB box;
	private EnvironmentSampler sampler;
	protected Update update;
	private float weight;
	

	protected EnvironmentProbe(Engine engine, Vector3f center, Vector3f size, int resolution, Update update, int probeIndex, float weight) throws Exception {
        this.renderer = engine.getRenderer();
        this.environmentProbeManager = engine.getEnvironmentProbeManager();
		this.update = update;
		box = new AABB(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(engine.getEntityManager().getEntity(), engine, this, center, resolution, resolution, probeIndex);
		sampler.initialize();
		this.setWeight(weight);
		super.initialize();
	}

	public void draw(RenderState extract) {
		draw(false, extract);
	}
	public void draw(boolean urgent, RenderState extract) {
		sampler.drawCubeMap(urgent, extract);
	}
	
	public void drawDebug(Program program) {
		List<Vector3f> points = box.getPoints();
		for (int i = 0; i < points.size() - 1; i++) {
			renderer.batchLine(points.get(i), points.get(i + 1));
		}

		renderer.batchLine(points.get(3), points.get(0));
		renderer.batchLine(points.get(7), points.get(4));

		renderer.batchLine(points.get(0), points.get(6));
		renderer.batchLine(points.get(1), points.get(7));
		renderer.batchLine(points.get(2), points.get(4));
		renderer.batchLine(points.get(3), points.get(5));

		renderer.batchLine(getSampler().getPosition(), new Vector3f(getSampler().getPosition()).add(new Vector3f(5, 0, 0)));
		renderer.batchLine(getSampler().getPosition(), new Vector3f(getSampler().getPosition()).add(new Vector3f(0, 5, 0)));
		renderer.batchLine(getSampler().getPosition(), new Vector3f(getSampler().getPosition()).add(new Vector3f(0, 0, -5)));

		float temp = (float)getIndex()/10;
		program.setUniform("diffuseColor", new Vector3f(temp,1-temp,0));
	    renderer.drawLines(program);
		
//		renderer.batchLine(box.getBottomLeftBackCorner(), sampler.getCamera().getPosition());
	}

	public void move(Vector3f amount) {
		super.translate(amount);
		resetAllProbes();
        environmentProbeManager.updateBuffers();
		box.move(amount);
	}
	
	private void resetAllProbes() {
        environmentProbeManager.getProbes().forEach(probe -> {
			probe.getSampler().resetDrawing();
		});
	}

	@Override
	public String getName() {
		return name ;
	}

	@Override
	public boolean isSelected() {
		return false;
	}

	@Override
	public void setSelected(boolean selected) {
	}

	public boolean contains(Vector3f min, Vector3f max) {
		return box.contains(min) && box.contains(max);
	}

	public boolean contains(de.hanno.hpengine.engine.transform.AABB minMaxWorld) {
		return contains(minMaxWorld.getMin(), minMaxWorld.getMax());
	}

	public AABB getBox() {
		return box;
	}

	public void setUpdate(Update update) {
		this.update = update;
	}

	public Update getProbeUpdate() {
		return update;
	}

	public void setSize(float size) {
		resetAllProbes();
		box.setSize(size);
        environmentProbeManager.updateBuffers();
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		resetAllProbes();
		box.setSize(sizeX, sizeY, sizeZ);
        environmentProbeManager.updateBuffers();
	}

	public Vector3f getSize() {
		return new Vector3f(box.sizeX, box.sizeY, box.sizeZ);
	}

	public Camera getCamera(){
		return sampler.getCamera();
	}

	public int getTextureUnitIndex(GpuContext gpuContext) {
		int index = getIndex();
        return gpuContext.getMaxTextureUnits() - index - 1;
	}

	public int getIndex() {
        return environmentProbeManager.getProbes().indexOf(this);
	}

	public Vector3f getDebugColor() {
        float colorHelper = (float)getIndex()/(float) environmentProbeManager.getProbes().size();
		Random randomGenerator = new Random();
		randomGenerator.setSeed((long)colorHelper);
		float random = randomGenerator.nextFloat();
		return new Vector3f(1- colorHelper * colorHelper, (1-colorHelper) * (1 - colorHelper), colorHelper*colorHelper);
	}

	public EnvironmentSampler getSampler() {
		return sampler;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}
}
