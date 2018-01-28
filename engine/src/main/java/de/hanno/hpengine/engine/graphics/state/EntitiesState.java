package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import de.hanno.hpengine.engine.scene.BatchKey;
import de.hanno.hpengine.engine.scene.Vertex;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;

import java.nio.ByteBuffer;
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
    public VertexIndexBuffer<Vertex> vertexIndexBufferStatic = new VertexIndexBuffer<>(10,10, ModelComponent.DEFAULTCHANNELS);
    public VertexIndexBuffer<AnimatedVertex> vertexIndexBufferAnimated = new VertexIndexBuffer<>(10,10, ModelComponent.DEFAULTANIMATEDCHANNELS);
    public GPUBuffer<Entity> entitiesBuffer = new PersistentMappedBuffer(8000);
    public GPUBuffer<BufferableMatrix4f> jointsBuffer = new PersistentMappedBuffer(8000);
    public GPUBuffer<Material> materialBuffer = new PersistentMappedBuffer(8000);
    public List<BufferableMatrix4f> joints;

    public EntitiesState() {
    }

}