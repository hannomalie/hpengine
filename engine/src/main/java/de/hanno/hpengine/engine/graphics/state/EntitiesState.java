package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.GpuEntity;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import de.hanno.hpengine.engine.scene.BatchKey;
import de.hanno.hpengine.engine.scene.Vertex;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.hanno.hpengine.engine.graphics.renderer.RenderBatch.RenderBatches;

public class EntitiesState {
    public final Map<BatchKey, RenderBatch> cash = new HashMap<>();
    public long entityMovedInCycle;
    public long entityAddedInCycle;
    public RenderBatches renderBatchesStatic = new RenderBatches();
    public RenderBatches renderBatchesAnimated = new RenderBatches();
    public VertexIndexBuffer vertexIndexBufferStatic;
    public VertexIndexBuffer vertexIndexBufferAnimated;
    public GPUBuffer entitiesBuffer;
    public GPUBuffer jointsBuffer;
    public GPUBuffer materialBuffer;
    public List<BufferableMatrix4f> joints;

    public EntitiesState(GpuContext gpuContext) {
        vertexIndexBufferStatic = new VertexIndexBuffer(gpuContext, 10,10, ModelComponent.DEFAULTCHANNELS);
        vertexIndexBufferAnimated = new VertexIndexBuffer(gpuContext, 10,10, ModelComponent.DEFAULTANIMATEDCHANNELS);
        entitiesBuffer = new PersistentMappedBuffer(gpuContext, 8000);
        jointsBuffer = new PersistentMappedBuffer(gpuContext, 8000);
        materialBuffer = new PersistentMappedBuffer(gpuContext, 8000);
    }

}