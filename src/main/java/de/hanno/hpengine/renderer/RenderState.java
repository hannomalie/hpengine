package de.hanno.hpengine.renderer;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.light.DirectionalLight;

import java.util.*;

public class RenderState {
    private DrawResult latestDrawResult;
    public boolean anEntityHasMoved;
    public boolean directionalLightNeedsShadowMapRender;
    public Camera camera = new Camera();
    public DirectionalLight directionalLight = new DirectionalLight();
    public boolean anyPointLightHasMoved;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();
    private Map properties = new HashMap<>();
    private List<PerEntityInfo> perEntityInfos = new ArrayList<>();
    private IndexBuffer indexBuffer = new IndexBuffer();
    private VertexBuffer vertexBuffer = new VertexBuffer(BufferUtils.createFloatBuffer(1), ModelComponent.DEFAULTCHANNELS);
    private OpenGLBuffer entitiesBuffer = new PersistentMappedBuffer(16000);
    private OpenGLBuffer materialBuffer = new PersistentMappedBuffer(20000);

    /**
     * Copy constructor
     * @param source
     */
    public RenderState(RenderState source) {
        init(source.getVertexBuffer(), source.getIndexBuffer(), source.camera, source.directionalLight, source.anEntityHasMoved, source.directionalLightNeedsShadowMapRender, source.anyPointLightHasMoved, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.latestDrawResult, source.perEntityInfos);
    }

    public RenderState() {
    }

    public RenderState init(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, Camera camera, DirectionalLight directionalLight, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender, boolean anyPointLightHasMoved, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, DrawResult latestDrawResult, List<PerEntityInfo> perEntityInfos) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.camera.init(camera);
        this.directionalLight = directionalLight;
        this.anEntityHasMoved = anEntityHasMoved;
        this.directionalLightNeedsShadowMapRender = directionalLightNeedsShadowMapRender;
        this.anyPointLightHasMoved = anyPointLightHasMoved;
        this.sceneInitiallyDrawn = sceneInitiallyDrawn;
        this.sceneMin = sceneMin;
        this.sceneMax = sceneMax;
        if(latestDrawResult != null) {
            this.properties.putAll(latestDrawResult.getProperties());
        }
        this.perEntityInfos.clear();
        this.perEntityInfos.addAll(perEntityInfos);
        this.latestDrawResult = latestDrawResult;
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
}
