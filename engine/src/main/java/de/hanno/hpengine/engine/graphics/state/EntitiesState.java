package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.model.PerMeshInfo;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntitiesState {
    public final Map<Mesh, PerMeshInfo> cash = new HashMap<>();
    public long entityMovedInCycle;
    public long entityAddedInCycle;
    public List<PerMeshInfo> perMeshInfos = new ArrayList<>();
    public VertexIndexBuffer vertexIndexBuffer = new VertexIndexBuffer(100, 90);
    public GPUBuffer<Entity> entitiesBuffer = new PersistentMappedBuffer(16000);
    public GPUBuffer<Material> materialBuffer = new PersistentMappedBuffer(20000);

    public EntitiesState() {
    }
}