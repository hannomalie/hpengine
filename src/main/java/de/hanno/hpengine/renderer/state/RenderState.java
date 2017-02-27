package de.hanno.hpengine.renderer.state;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class RenderState {

    public final DirectionalLightState directionalLightState = new DirectionalLightState();
    public final EntitiesState entitiesState = new EntitiesState();

    private DrawResult latestDrawResult;
    public Camera camera = new Camera();
    public long pointlightMovedInCycle;
    public long directionalLightHasMovedInCycle;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();

    private long cycle = 0;

    /**
     * Copy constructor
     * @param source
     */
    public RenderState(RenderState source) {
        init(source.getVertexBuffer(), source.getIndexBuffer(), source.camera, source.entitiesState.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.latestDrawResult, source.getCycle(), source.directionalLightState.directionalLightViewMatrixAsBuffer, source.directionalLightState.directionalLightProjectionMatrixAsBuffer, source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightState.directionalLightScatterFactor, source.directionalLightState.directionalLightDirection, source.directionalLightState.directionalLightColor);
    }

    public RenderState() {
    }

    public RenderState init(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, Camera camera, long entityMovedInCycle, long directionalLightHasMovedInCycle, long pointLightMovedInCycle, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, DrawResult latestDrawResult, long cycle, FloatBuffer directionalLightViewMatrixAsBuffer, FloatBuffer directionalLightProjectionMatrixAsBuffer, FloatBuffer directionalLightViewProjectionMatrixAsBuffer, float directionalLightScatterFactor, Vector3f directionalLightDirection, Vector3f directionalLightColor) {
        this.entitiesState.vertexBuffer = vertexBuffer;
        this.entitiesState.indexBuffer = indexBuffer;
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
        this.directionalLightHasMovedInCycle = directionalLightHasMovedInCycle;
        this.pointlightMovedInCycle = pointLightMovedInCycle;
        this.sceneInitiallyDrawn = sceneInitiallyDrawn;
        this.sceneMin = sceneMin;
        this.sceneMax = sceneMax;
        this.entitiesState.perEntityInfos.clear();
        this.latestDrawResult = latestDrawResult;
        this.cycle = cycle;
        return this;
    }

    public List<PerEntityInfo> perEntityInfos() {
        return entitiesState.perEntityInfos;
    }

    public IndexBuffer getIndexBuffer() {
        return entitiesState.indexBuffer;
    }

    public VertexBuffer getVertexBuffer() {
        return entitiesState.vertexBuffer;
    }

    public void setVertexBuffer(VertexBuffer vertexBuffer) {
        this.entitiesState.vertexBuffer = vertexBuffer;
    }

    public void setIndexBuffer(IndexBuffer indexBuffer) {
        this.entitiesState.indexBuffer = indexBuffer;
    }

    public void bufferEntites(List<Entity> entities) {
        entitiesState.entitiesBuffer.put(Util.toArray(entities, Entity.class));
    }

    public void bufferMaterial(Material material) {
        OpenGLContext.getInstance().execute(() -> {
            ArrayList<Material> materials = new ArrayList<>(MaterialFactory.getInstance().getMaterials().values());

            int offset = material.getElementsPerObject() * materials.indexOf(material);
            entitiesState.materialBuffer.put(offset, material);
        });
    }

    public void bufferMaterials() {
        OpenGLContext.getInstance().execute(() -> {
            ArrayList<Material> materials = new ArrayList<Material>(MaterialFactory.getInstance().getMaterials().values());
            entitiesState.materialBuffer.put(Util.toArray(materials, Material.class));
        });
    }

    public OpenGLBuffer getEntitiesBuffer() {
        return entitiesState.entitiesBuffer;
    }

    public OpenGLBuffer getMaterialBuffer() {
        return entitiesState.materialBuffer;
    }

    public void add(PerEntityInfo info) {
        entitiesState.perEntityInfos.add(info);
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
}
