package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.renderer.*;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.engine.instancing.ClustersComponent;
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.ModelComponentSystem;
import de.hanno.hpengine.engine.model.ModelComponentSystemKt;
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

public class RenderState {
    private GpuContext gpuContext;

    public final DrawResult latestDrawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());

    public final DirectionalLightState directionalLightState = new DirectionalLightState();
    public final EntitiesState entitiesState;

    public final CommandOrganization commandOrganizationStatic;
    public final CommandOrganization commandOrganizationAnimated;

    public Camera camera = new Camera(new Entity());
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
        this(source.gpuContext);
        init(source.entitiesState.vertexIndexBufferStatic, source.entitiesState.vertexIndexBufferAnimated, source.entitiesState.joints, source.camera, source.entitiesState.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.getCycle(), source.directionalLightState.directionalLightViewMatrixAsBuffer, source.directionalLightState.directionalLightProjectionMatrixAsBuffer, source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightState.directionalLightScatterFactor, source.directionalLightState.directionalLightDirection, source.directionalLightState.directionalLightColor, source.entitiesState.entityAddedInCycle);
        this.entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic);
        this.entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated);
//        TODO: This could be problematic. Copies all buffer contents to the copy's buffers
//        this.entitiesState.entitiesBuffer.putValues(source.entitiesState.entitiesBuffer.getValuesAsFloats());
//        this.entitiesState.materialBuffer.putValues(source.entitiesState.materialBuffer.getValuesAsFloats());
//        this.entitiesState.vertexBuffer.putValues(source.getVertexBuffer().getValuesAsFloats());
//        this.entitiesState.indexBuffer.put(source.getIndexBuffer().getValues());
    }

    public RenderState(GpuContext gpuContext) {
        entitiesState = new EntitiesState(gpuContext);
        commandOrganizationStatic = new CommandOrganization(gpuContext);
        commandOrganizationAnimated = new CommandOrganization(gpuContext);
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

    public void bufferEntities(List<ModelComponent> modelComponents) {
        entitiesState.entitiesBuffer.put(Util.toArray(modelComponents, ModelComponent.class));
        entitiesState.entitiesBuffer.getBuffer().position(0);

//        for(ModelComponent modelComponent: modelComponents) {
//            for(int i = 0; i < ModelComponentSystemKt.getInstanceCount(modelComponent.getEntity()); i++) {
//                for(Mesh mesh: modelComponent.getMeshes()) {
//                    System.out.println("Entity " + modelComponent.getEntity().getName() + " - Mesh " + mesh.getName() + " - Instance " + i);
//                    ModelComponent.debugPrintFromBufferStatic(entitiesState.entitiesBuffer.getBuffer());
//                }
//            }
//        }
        entitiesState.entitiesBuffer.getBuffer().position(0);

    }
    public void bufferJoints(List<BufferableMatrix4f> joints) {
        entitiesState.jointsBuffer.put(Util.toArray(joints, BufferableMatrix4f.class));
    }

//    TODO: Reimplement this
//    public void bufferMaterial(Material materials) {
//        GpuContext.getInstance().execute(() -> {
//            ArrayList<Material> materials = new ArrayList<>(MaterialManager.getInstance().getMaterials());
//
//            int offset = materials.getElementsPerObject() * materials.indexOf(materials);
//            entitiesState.materialBuffer.put(offset, materials);
//        });
//    }

    public void bufferMaterials(Engine engine) {
        engine.getGpuContext().execute(() -> {
            List<Material> materials = engine.getMaterialManager().getMaterials();
            entitiesState.materialBuffer.put(Util.toArray(materials, Material.class));
            entitiesState.materialBuffer.getBuffer().position(0);
//            for(Material material: materials) {
//                System.out.println("Material: " + material.getName() + "(" + material.getMaterialIndex() + ")");
//                Material.debugPrintFromBufferStatic(entitiesState.materialBuffer.getBuffer());
//            }
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
