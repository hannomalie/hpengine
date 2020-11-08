package de.hanno.hpengine.engine.scene;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.backend.EngineContextKt;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.environmentsampler.EnvironmentSampler;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.transform.AABB;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Random;

public class EnvironmentProbe implements Component {
	public static String COMPONENT_KEY = EnvironmentProbe.class.getSimpleName();

	private final EnvironmentProbeManager environmentProbeManager;
	private Entity entity;

	public enum Update {
		STATIC,
		DYNAMIC
	}
	
	private AABB box;
	private EnvironmentSampler sampler;
	protected Update update;
	private float weight;
	

	protected EnvironmentProbe(EngineContext engine, Entity entity, Vector3f center, Vector3f size, int resolution, Update update, int probeIndex, float weight, EnvironmentProbeManager environmentProbeManager) throws Exception {
		this.environmentProbeManager = environmentProbeManager;
		this.entity = entity;
		this.update = update;
		box = new AABB(center, size);
		sampler = new EnvironmentSampler(entity, this, center,
				resolution, resolution, probeIndex,
				this.environmentProbeManager, EngineContextKt.getProgramManager(engine),
				engine.getConfig(), EngineContextKt.getTextureManager(engine), null); // TODO: Pass scene not null here...
        this.setWeight(weight);
        EngineContextKt.getEventBus(engine).register(this);
    }

	public void draw(RenderState extract) {
		draw(false, extract);
	}
	public void draw(boolean urgent, RenderState extract) {
		sampler.drawCubeMap(urgent, extract);
	}

	public void move(Vector3f amount) {
		resetAllProbes();
        environmentProbeManager.updateBuffers();
		box.move(amount);
	}
	
	private void resetAllProbes() {
        environmentProbeManager.getProbes().forEach(probe -> {
			probe.getSampler().resetDrawing();
		});
	}

	public boolean contains(Vector3fc min, Vector3fc max) {
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

	public Vector3f getSize() {
		return new Vector3f(box.getExtents());
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

	@Override
	public Entity getEntity() {
		return entity;
	}

}
