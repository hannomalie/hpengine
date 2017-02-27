package de.hanno.hpengine.renderer.state;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntitiesState {
    public final Map<ModelComponent, PerEntityInfo> cash = new HashMap<ModelComponent, PerEntityInfo>();
    public long entityMovedInCycle;
    public List<PerEntityInfo> perEntityInfos = new ArrayList<PerEntityInfo>();
    public IndexBuffer indexBuffer = new IndexBuffer();
    public VertexBuffer vertexBuffer;
    public OpenGLBuffer entitiesBuffer = new PersistentMappedBuffer(16000);
    public OpenGLBuffer materialBuffer = new PersistentMappedBuffer(20000);

    public EntitiesState() {
        this.vertexBuffer = new VertexBuffer(BufferUtils.createFloatBuffer(1), ModelComponent.DEFAULTCHANNELS);
    }
}