package de.hanno.hpengine.renderer.state;

import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.scene.VertexIndexBuffer;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntitiesState {
    public final Map<Mesh, PerMeshInfo> cash = new HashMap<>();
    public long entityMovedInCycle;
    public List<PerMeshInfo> perMeshInfos = new ArrayList<>();
    public VertexIndexBuffer vertexIndexBuffer = new VertexIndexBuffer(100, 90);
    public OpenGLBuffer entitiesBuffer = new PersistentMappedBuffer(16000);
    public OpenGLBuffer materialBuffer = new PersistentMappedBuffer(20000);

    public EntitiesState() {
    }
}