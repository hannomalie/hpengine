package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.Vertex;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;

import java.util.HashMap;
import java.util.Map;

import static de.hanno.hpengine.engine.graphics.renderer.RenderBatch.RenderBatches;

public class EntitiesState {
    public final Map<Mesh, RenderBatch> cash = new HashMap<>();
    public long entityMovedInCycle;
    public long entityAddedInCycle;
    public RenderBatches renderBatchesStatic = new RenderBatches();
    public RenderBatches renderBatchesAnimated = new RenderBatches();
    public VertexIndexBuffer<Vertex> vertexIndexBufferStatic = new VertexIndexBuffer<>(10,10);
    public VertexIndexBuffer<Vertex> vertexIndexBufferAnimated = new VertexIndexBuffer<>(10,10);;
    public GPUBuffer<Entity> entitiesBuffer = new PersistentMappedBuffer(8000);
    public GPUBuffer<Material> materialBuffer = new PersistentMappedBuffer(8000);

    public EntitiesState() {
    }
}