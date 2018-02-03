package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.renderer.*;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import de.hanno.hpengine.engine.scene.Vertex;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.util.Util;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RenderState {
    public final DrawResult latestDrawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());

    public final DirectionalLightState directionalLightState = new DirectionalLightState();
    public final EntitiesState entitiesState = new EntitiesState();

    public final CommandOrganization commandOrganizationStatic = new CommandOrganization();
    public final CommandOrganization commandOrganizationAnimated = new CommandOrganization();

    public Camera camera = new Camera();
    public long pointlightMovedInCycle;
    public long directionalLightHasMovedInCycle;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();
    public List<Pipeline> pipelines = new ArrayList<Pipeline>();

    private long cycle = 0;
    private volatile long gpuCommandSync;

    /**
     * Copy constructor
     * @param source
     */
    public RenderState(RenderState source) {
        init(source.entitiesState.vertexIndexBufferStatic, source.entitiesState.vertexIndexBufferAnimated, source.entitiesState.joints, source.camera, source.entitiesState.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.getCycle(), source.directionalLightState.directionalLightViewMatrixAsBuffer, source.directionalLightState.directionalLightProjectionMatrixAsBuffer, source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightState.directionalLightScatterFactor, source.directionalLightState.directionalLightDirection, source.directionalLightState.directionalLightColor, source.entitiesState.entityAddedInCycle);
        this.entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic);
        this.entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated);
//        TODO: This could be problematic. Copies all buffer contents to the copy's buffers
//        this.entitiesState.entitiesBuffer.putValues(source.entitiesState.entitiesBuffer.getValuesAsFloats());
//        this.entitiesState.materialBuffer.putValues(source.entitiesState.materialBuffer.getValuesAsFloats());
//        this.entitiesState.vertexBuffer.putValues(source.getVertexBuffer().getValuesAsFloats());
//        this.entitiesState.indexBuffer.put(source.getIndexBuffer().getValues());
    }

    public RenderState() {
    }

    public void init(VertexIndexBuffer<Vertex> vertexIndexBufferStatic, VertexIndexBuffer vertexIndexBufferAnimated, List<BufferableMatrix4f> joints, Camera camera, long entityMovedInCycle, long directionalLightHasMovedInCycle, long pointLightMovedInCycle, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, long cycle, FloatBuffer directionalLightViewMatrixAsBuffer, FloatBuffer directionalLightProjectionMatrixAsBuffer, FloatBuffer directionalLightViewProjectionMatrixAsBuffer, float directionalLightScatterFactor, Vector3f directionalLightDirection, Vector3f directionalLightColor, long entityAddedInCycle) {
        this.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic;
        this.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated;
        bufferJoints(joints);
        this.entitiesState.joints = joints; // TODO: Fixme
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
        this.entitiesState.renderBatchesStatic.clear();
        this.entitiesState.renderBatchesAnimated.clear();
        this.latestDrawResult.set(latestDrawResult);
//        this.cycle = cycle;
    }

    public List<RenderBatch> getRenderBatchesStatic() {
        return entitiesState.renderBatchesStatic;
    }
    public List<RenderBatch> getRenderBatchesAnimated() {
        return entitiesState.renderBatchesAnimated;
    }

    public VertexIndexBuffer getVertexIndexBufferStatic() {
        return entitiesState.vertexIndexBufferStatic;
    }
    public VertexIndexBuffer getVertexIndexBufferAnimated() {
        return entitiesState.vertexIndexBufferAnimated;
    }

    public void bufferEntities(List<Entity> entities) {
        entitiesState.entitiesBuffer.put(Util.toArray(entities.stream().filter(entity -> entity.hasComponent(ModelComponent.COMPONENT_KEY)).collect(Collectors.toList()), Entity.class));
    }
    public void bufferJoints(List<BufferableMatrix4f> joints) {
        entitiesState.jointsBuffer.put(Util.toArray(joints, BufferableMatrix4f.class));
    }

//    TODO: Reimplement this
//    public void bufferMaterial(Material materials) {
//        GraphicsContext.getInstance().execute(() -> {
//            ArrayList<Material> materials = new ArrayList<>(MaterialFactory.getInstance().getMaterials());
//
//            int offset = materials.getElementsPerObject() * materials.indexOf(materials);
//            entitiesState.materialBuffer.put(offset, materials);
//        });
//    }

    public void bufferMaterials() {
        Engine.getInstance().getGpuContext().execute(() -> {
            ArrayList<Material> materials = new ArrayList<Material>(Engine.getInstance().getMaterialFactory().getMaterials());
            entitiesState.materialBuffer.put(Util.toArray(materials, Material.class));
        });
    }

    public GPUBuffer getEntitiesBuffer() {
        return entitiesState.entitiesBuffer;
    }

    public GPUBuffer getMaterialBuffer() {
        return entitiesState.materialBuffer;
    }

    public void addStatic(RenderBatch batch) {
        entitiesState.renderBatchesStatic.add(batch);
    }
    public void addAnimated(RenderBatch batch) {
        entitiesState.renderBatchesAnimated.add(batch);
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
            throw new IllegalArgumentException("GPUFrustumCulledPipeline could somehow not be added to state");
        }
    }

    public <T extends Pipeline> T get(TripleBuffer.PipelineRef<T> index) {
        return (T) pipelines.get(index.getIndex());
    }

    public List<Pipeline> getPipelines() {
        return pipelines;
    }

    public long getGpuCommandSync() {
        return gpuCommandSync;
    }

    public void setGpuCommandSync(long gpuCommandSync) {
        this.gpuCommandSync = gpuCommandSync;
//        glFlush();
    }

    public void setVertexIndexBufferStatic(VertexIndexBuffer<Vertex> vertexIndexBuffer) {
        this.entitiesState.vertexIndexBufferStatic = vertexIndexBuffer;
    }

    public void setVertexIndexBufferAnimated(VertexIndexBuffer<AnimatedVertex> vertexIndexBufferAnimated) {
        this.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated;
    }

    public void setCycle(long cycle) {
        this.cycle = cycle;
    }
}
