package de.hanno.hpengine.renderer;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderState {
    public final Map<ModelComponent, PerEntityInfo> cash = new HashMap<>();

    private DrawResult latestDrawResult;
    public long entityMovedInCycle;
    public Camera camera = new Camera();
    public long pointlightMovedInCycle;
    public long directionalLightHasMovedInCycle;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();
    private Map properties = new HashMap<>();
    private List<PerEntityInfo> perEntityInfos = new ArrayList<>();
    private IndexBuffer indexBuffer = new IndexBuffer();
    private VertexBuffer vertexBuffer = new VertexBuffer(BufferUtils.createFloatBuffer(1), ModelComponent.DEFAULTCHANNELS);
    private OpenGLBuffer entitiesBuffer = new PersistentMappedBuffer(16000);
    private OpenGLBuffer materialBuffer = new PersistentMappedBuffer(20000);

    private long cycle = 0;
    private FloatBuffer directionalLightViewMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    private FloatBuffer directionalLightProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    private FloatBuffer directionalLightViewProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    public Vector3f directionalLightDirection = new Vector3f();
    public Vector3f directionalLightColor = new Vector3f();
    public float directionalLightScatterFactor;

    /**
     * Copy constructor
     * @param source
     */
    public RenderState(RenderState source) {
        init(source.getVertexBuffer(), source.getIndexBuffer(), source.camera, source.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.latestDrawResult, source.getCycle(), source.directionalLightViewMatrixAsBuffer, source.directionalLightProjectionMatrixAsBuffer, source.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightScatterFactor, source.directionalLightDirection, source.directionalLightColor);
    }

    public RenderState() {
    }

    public RenderState init(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, Camera camera, long entityMovedInCycle, long directionalLightHasMovedInCycle, long pointLightMovedInCycle, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, DrawResult latestDrawResult, long cycle, FloatBuffer directionalLightViewMatrixAsBuffer, FloatBuffer directionalLightProjectionMatrixAsBuffer, FloatBuffer directionalLightViewProjectionMatrixAsBuffer, float directionalLightScatterFactor, Vector3f directionalLightDirection, Vector3f directionalLightColor) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.camera.init(camera);

        this.directionalLightViewMatrixAsBuffer = directionalLightViewMatrixAsBuffer;
        this.directionalLightViewMatrixAsBuffer.rewind();
        this.directionalLightProjectionMatrixAsBuffer = directionalLightProjectionMatrixAsBuffer;
        this.directionalLightProjectionMatrixAsBuffer.rewind();
        this.directionalLightViewProjectionMatrixAsBuffer = directionalLightViewProjectionMatrixAsBuffer;
        this.directionalLightViewProjectionMatrixAsBuffer.rewind();
        this.directionalLightDirection.set(directionalLightDirection);
        this.directionalLightColor.set(directionalLightColor);
        this.directionalLightScatterFactor = directionalLightScatterFactor;

        this.entityMovedInCycle = entityMovedInCycle;
        this.directionalLightHasMovedInCycle = directionalLightHasMovedInCycle;
        this.pointlightMovedInCycle = pointLightMovedInCycle;
        this.sceneInitiallyDrawn = sceneInitiallyDrawn;
        this.sceneMin = sceneMin;
        this.sceneMax = sceneMax;
        if(latestDrawResult != null) {
            this.properties.putAll(latestDrawResult.getProperties());
        }
        this.perEntityInfos.clear();
        this.latestDrawResult = latestDrawResult;
        this.cycle = cycle;
        return this;
    }

    public List<PerEntityInfo> perEntityInfos() {
        return perEntityInfos;
    }

    public IndexBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public void setVertexBuffer(VertexBuffer vertexBuffer) {
        this.vertexBuffer = vertexBuffer;
    }

    public void setIndexBuffer(IndexBuffer indexBuffer) {
        this.indexBuffer = indexBuffer;
    }

    public void bufferEntites(List<Entity> entities) {
        entitiesBuffer.put(Util.toArray(entities, Entity.class));
    }

    public void bufferMaterial(Material material) {
        OpenGLContext.getInstance().execute(() -> {
            ArrayList<Material> materials = new ArrayList<>(MaterialFactory.getInstance().getMaterials().values());

            int offset = material.getElementsPerObject() * materials.indexOf(material);
            materialBuffer.put(offset, material);
        });
    }

    public void bufferMaterials() {
        OpenGLContext.getInstance().execute(() -> {
            ArrayList<Material> materials = new ArrayList<Material>(MaterialFactory.getInstance().getMaterials().values());
            materialBuffer.put(Util.toArray(materials, Material.class));
        });
    }

    public OpenGLBuffer getEntitiesBuffer() {
        return entitiesBuffer;
    }

    public OpenGLBuffer getMaterialBuffer() {
        return materialBuffer;
    }

    public void add(PerEntityInfo info) {
        perEntityInfos.add(info);
    }

    public long getCycle() {
        return cycle;
    }

    public FloatBuffer getDirectionalLightViewMatrixAsBuffer() {
        return directionalLightViewMatrixAsBuffer;
    }

    public FloatBuffer getDirectionalLightProjectionMatrixAsBuffer() {
        return directionalLightProjectionMatrixAsBuffer;
    }

    public FloatBuffer getDirectionalLightViewProjectionMatrixAsBuffer() {
        return directionalLightViewProjectionMatrixAsBuffer;
    }
}
