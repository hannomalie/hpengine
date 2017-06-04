package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.Pipeline;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.opengl.GLSync;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.glFlush;

public class RenderState {
    public final DrawResult latestDrawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());

    public final DirectionalLightState directionalLightState = new DirectionalLightState();
    public final EntitiesState entitiesState = new EntitiesState();

    public Camera camera = new Camera();
    public long pointlightMovedInCycle;
    public long directionalLightHasMovedInCycle;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();
    public List<Pipeline> pipelines = new ArrayList<>();

    private long cycle = 0;
    private volatile GLSync gpuCommandSync;

    /**
     * Copy constructor
     * @param source
     */
    public RenderState(RenderState source) {
        init(source.entitiesState.vertexIndexBuffer, source.camera, source.entitiesState.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.getCycle(), source.directionalLightState.directionalLightViewMatrixAsBuffer, source.directionalLightState.directionalLightProjectionMatrixAsBuffer, source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightState.directionalLightScatterFactor, source.directionalLightState.directionalLightDirection, source.directionalLightState.directionalLightColor, source.entitiesState.entityAddedInCycle);
        this.entitiesState.renderBatches.addAll(source.entitiesState.renderBatches);
//        TODO: This could be problematic. Copies all buffer contents to the copy's buffers
//        this.entitiesState.entitiesBuffer.putValues(source.entitiesState.entitiesBuffer.getValuesAsFloats());
//        this.entitiesState.materialBuffer.putValues(source.entitiesState.materialBuffer.getValuesAsFloats());
//        this.entitiesState.vertexBuffer.putValues(source.getVertexBuffer().getValuesAsFloats());
//        this.entitiesState.indexBuffer.put(source.getIndexBuffer().getValues());
    }

    public RenderState() {
    }

    public void init(VertexIndexBuffer vertexIndexBuffer, Camera camera, long entityMovedInCycle, long directionalLightHasMovedInCycle, long pointLightMovedInCycle, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, long cycle, FloatBuffer directionalLightViewMatrixAsBuffer, FloatBuffer directionalLightProjectionMatrixAsBuffer, FloatBuffer directionalLightViewProjectionMatrixAsBuffer, float directionalLightScatterFactor, Vector3f directionalLightDirection, Vector3f directionalLightColor, long entityAddedInCycle) {
        this.entitiesState.vertexIndexBuffer = vertexIndexBuffer;
        this.camera.init(camera);
        this.directionalLightState.directionalLightViewMatrixAsBuffer = directionalLightViewMatrixAsBuffer;
        this.directionalLightState.directionalLightViewMatrixAsBuffer.rewind();
        this.directionalLightState.directionalLightProjectionMatrixAsBuffer = directionalLightProjectionMatrixAsBuffer;
        this.directionalLightState.directionalLightProjectionMatrixAsBuffer.rewind();
        this.directionalLightState.directionalLightViewProjectionMatrixAsBuffer = directionalLightViewProjectionMatrixAsBuffer;
        this.directionalLightState.directionalLightViewProjectionMatrixAsBuffer.rewind();
        this.directionalLightState.directionalLightDirection.set(directionalLightDirection);
        this.directionalLightState.directionalLightColor.set(directionalLightColor);
        this.directionalLightState.directionalLightScatterFactor = directionalLightScatterFactor;

        this.entitiesState.entityMovedInCycle = entityMovedInCycle;
        this.entitiesState.entityAddedInCycle = entityAddedInCycle;
        this.directionalLightHasMovedInCycle = directionalLightHasMovedInCycle;
        this.pointlightMovedInCycle = pointLightMovedInCycle;
        this.sceneInitiallyDrawn = sceneInitiallyDrawn;
        this.sceneMin = sceneMin;
        this.sceneMax = sceneMax;
        this.entitiesState.renderBatches.clear();
        this.latestDrawResult.set(latestDrawResult);
        this.cycle = cycle;
    }

    public List<RenderBatch> perEntityInfos() {
        return entitiesState.renderBatches;
    }

    public VertexIndexBuffer getVertexIndexBuffer() {
        return entitiesState.vertexIndexBuffer;
    }

    public IndexBuffer getIndexBuffer() {
        return entitiesState.vertexIndexBuffer.getIndexBuffer();
    }

    public VertexBuffer getVertexBuffer() {
        return entitiesState.vertexIndexBuffer.getVertexBuffer();
    }

    public void setVertexBuffer(VertexBuffer vertexBuffer) {
        this.entitiesState.vertexIndexBuffer.setVertexBuffer(vertexBuffer);
    }

    public void setIndexBuffer(IndexBuffer indexBuffer) {
        this.entitiesState.vertexIndexBuffer.setIndexBuffer(indexBuffer);
    }

    public void bufferEntites(List<Entity> entities) {
        entitiesState.entitiesBuffer.put(Util.toArray(entities, Entity.class));
    }

//    TODO: Reimplement this
//    public void bufferMaterial(Material material) {
//        GraphicsContext.getInstance().execute(() -> {
//            ArrayList<Material> materials = new ArrayList<>(MaterialFactory.getInstance().getMaterials());
//
//            int offset = material.getElementsPerObject() * materials.indexOf(material);
//            entitiesState.materialBuffer.put(offset, material);
//        });
//    }

    public void bufferMaterials() {
        GraphicsContext.getInstance().execute(() -> {
            ArrayList<Material> materials = new ArrayList<Material>(MaterialFactory.getInstance().getMaterials());
            entitiesState.materialBuffer.put(Util.toArray(materials, Material.class));
        });
    }

    public GPUBuffer getEntitiesBuffer() {
        return entitiesState.entitiesBuffer;
    }

    public GPUBuffer getMaterialBuffer() {
        return entitiesState.materialBuffer;
    }

    public void add(RenderBatch batch) {
        entitiesState.renderBatches.add(batch);
    }

    public long getCycle() {
        return cycle;
    }

    public FloatBuffer getDirectionalLightViewMatrixAsBuffer() {
        return directionalLightState.directionalLightViewMatrixAsBuffer;
    }

    public FloatBuffer getDirectionalLightProjectionMatrixAsBuffer() {
        return directionalLightState.directionalLightProjectionMatrixAsBuffer;
    }

    public FloatBuffer getDirectionalLightViewProjectionMatrixAsBuffer() {
        return directionalLightState.directionalLightViewProjectionMatrixAsBuffer;
    }

    public boolean preventSwap(RenderState currentStaging, RenderState currentRead) {
        return currentStaging.cycle < currentRead.cycle;
    }

    public int addPipeline(Pipeline pipeline) {
        if(pipelines.add(pipeline)) {
            return pipelines.indexOf(pipeline);
        } else {
            throw new IllegalArgumentException("Pipeline could somehow not be added to state");
        }
    }

    public Pipeline get(int index) {
        return pipelines.get(index);
    }

    public List<Pipeline> getPipelines() {
        return pipelines;
    }

    public GLSync getGpuCommandSync() {
        return gpuCommandSync;
    }

    public void setGpuCommandSync(GLSync gpuCommandSync) {
        this.gpuCommandSync = gpuCommandSync;
        glFlush();
    }

    public void setVertexIndexBuffer(VertexIndexBuffer vertexIndexBuffer) {
        this.entitiesState.vertexIndexBuffer = vertexIndexBuffer;
    }
}
