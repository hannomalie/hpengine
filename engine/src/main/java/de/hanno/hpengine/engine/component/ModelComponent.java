package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Matrix4f;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.model.ModelComponentSystemKt.getClusters;
import static de.hanno.hpengine.engine.model.ModelComponentSystemKt.getInstanceCount;
import static de.hanno.hpengine.engine.model.ModelComponentSystemKt.getInstances;


public class ModelComponent extends BaseComponent implements Serializable, Bufferable {
    public static final String COMPONENT_KEY = ModelComponent.class.getSimpleName();
    private static final Logger LOGGER = Logger.getLogger(ModelComponent.class.getName());
    private static final long serialVersionUID = 1L;

    private Model model;
    private AnimationController animationController;

    public boolean instanced = false;

    private int[] indicesCounts;
    private int[] baseVertices;

    protected String materialName = "";

    public static EnumSet<DataChannels> DEFAULTCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL);
    public static EnumSet<DataChannels> DEFAULTANIMATEDCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL,
            DataChannels.WEIGHTS,
            DataChannels.JOINT_INDICES);
    public static EnumSet<DataChannels> DEPTHCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.NORMAL
    );
    public static EnumSet<DataChannels> SHADOWCHANNELS = EnumSet.of(
            DataChannels.POSITION3);
    public static EnumSet<DataChannels> POSITIONCHANNEL = EnumSet.of(
            DataChannels.POSITION3);
    private VertexIndexOffsets vertexIndexOffsets;
    private int jointsOffset = 0;
    private Engine engine;
    public transient int componentIndex = -1;
    private int entityBufferIndex;

    public int getEntityBufferIndex() {
        return entityBufferIndex;
    }

    public void setEntityBufferIndex(int entityBufferIndex) {
        this.entityBufferIndex = entityBufferIndex;
    }

    public int getJointsOffset() {
        return jointsOffset;
    }

    public void setJointsOffset(int jointsOffset) {
        this.jointsOffset = jointsOffset;
    }

    public ModelComponent(Entity entity) {
        this.entity = entity;
    }
    public ModelComponent(Entity entity, Model model) {
        super();
        this.entity = entity;
        this.model = model;
        if(!model.isStatic()) {
            AnimatedModel animatedModel = (AnimatedModel) model;
            animationController = new AnimationController(animatedModel.getFrames().size(), animatedModel.getHeader().getFrameRate());
        }
        indicesCounts = new int[model.getMeshes().size()];
        baseVertices = new int[model.getMeshes().size()];
    }

    private transient WeakReference<Material> materialCache = null;
    public Material getMaterial(MaterialManager materialManager) {
        if(materialCache != null && materialCache.get() != null && materialCache.get().getName().equals(materialName)) {
            return materialCache.get();
        }
        Material material = materialManager.getMaterial(materialName);
        materialCache = new WeakReference<>(material);
        if(material == null) {
            Logger.getGlobal().info("Material null, default is applied");
            return materialManager.getDefaultMaterial();
        }
        return material;
    }
    public void setMaterial(MaterialManager materialManager, String materialName) {
        this.materialName = materialName;
        model.setMaterial(materialManager.getMaterial(materialName));
        for(Entity child : entity.getChildren()) {
            child.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(c -> c.setMaterial(materialManager, materialName));
        }
    }

    @Override
    public void init(Engine engine) {
        super.init(engine);
        this.engine = engine;
    }

    public VertexIndexOffsets putToBuffer(GpuContext gpuContext, VertexIndexBuffer vertexIndexBuffer, EnumSet<DataChannels> channels) {

        List compiledVertices = model.getCompiledVertices();

        int elementsPerVertex = DataChannels.totalElementsPerVertex(channels);
        int indicesCount = getIndices().length;
        vertexIndexOffsets = vertexIndexBuffer.allocate(compiledVertices.size(), indicesCount);

        int currentIndexOffset = vertexIndexOffsets.indexOffset;
        int currentVertexOffset = vertexIndexOffsets.vertexOffset;

        for(int i = 0; i < indicesCounts.length; i++) {
            Mesh mesh = (Mesh) model.getMeshes().get(i);
            indicesCounts[i] = currentIndexOffset;
            baseVertices[i] = currentVertexOffset;
            currentIndexOffset += mesh.getIndexBufferValuesArray().length;
            currentVertexOffset += mesh.getVertexBufferValuesArray().length/elementsPerVertex;
        }

        gpuContext.execute(() -> {
            vertexIndexBuffer.getVertexBuffer().put(vertexIndexOffsets.vertexOffset, compiledVertices);
            vertexIndexBuffer.getIndexBuffer().appendIndices(vertexIndexOffsets.indexOffset, getIndices());

            LOGGER.fine("Current IndexOffset: " + vertexIndexOffsets.indexOffset);
            LOGGER.fine("Current BaseVertex: " + vertexIndexOffsets.vertexOffset);
            vertexIndexBuffer.getVertexBuffer().upload();
        });

        return vertexIndexOffsets;
    }

    public float getBoundingSphereRadius() {
        return model.getBoundingSphereRadius();
    }

    public int[] getIndices() {
        return model.getIndices();
    }

    public int getTriangleCount() {
        return model.getTriangleCount();
    }

//    private Vector3f[] calculateTangentBitangent(Vector3f v1, Vector3f v2, Vector3f v3, Vector2f w1, Vector2f w2, Vector2f w3, Vector3f n1, Vector3f n2, Vector3f n3) {
//        Vector3f tangent = new Vector3f();
//        Vector3f bitangent = new Vector3f();
//
//        Vector3f edge1 = new Vector3f(v2).sub(v1);
//        Vector3f edge2 = new Vector3f(v3).sub(v1);
//
//        Vector2f deltaUV1 = new Vector2f(w2).sub(w1);
//        Vector2f deltaUV2 = new Vector2f(w3).sub(w1);
//
//        float DeltaU1 = deltaUV1.x;
//        float DeltaV1 = deltaUV1.y;
//        float DeltaU2 = deltaUV2.x;
//        float DeltaV2 = deltaUV2.y;
//
//        float f = 1.0F / (deltaUV1.x * deltaUV2.y - deltaUV1.y * deltaUV2.x);
//
//        tangent = Vector3f.sub((Vector3f) new Vector3f(edge1).scale(DeltaV2), (Vector3f) new Vector3f(edge2).scale(DeltaV1), null);
//        bitangent = Vector3f.sub((Vector3f) new Vector3f(edge2).scale(DeltaU1), (Vector3f) new Vector3f(edge1).scale(DeltaU2), null);
//
//        tangent = (Vector3f) new Vector3f(tangent).scale(f);
//        bitangent = (Vector3f) new Vector3f(bitangent).scale(f);
//
//        Vector3f tangent1 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n1).scale(Vector3f.dot(n1, tangent))), null).normalise(null);
//        Vector3f tangent2 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n2).scale(Vector3f.dot(n2, tangent))), null).normalise(null);
//        Vector3f tangent3 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n3).scale(Vector3f.dot(n3, tangent))), null).normalise(null);
//
//        if (Vector3f.dot(Vector3f.cross(n1, tangent1, null), bitangent) < 0.0f){
//            tangent1.scale(-1.0f);
//        }
//        if (Vector3f.dot(Vector3f.cross(n2, tangent2, null), bitangent) < 0.0f){
//            tangent2.scale(-1.0f);
//        }
//        if (Vector3f.dot(Vector3f.cross(n3, tangent3, null), bitangent) < 0.0f){
//            tangent3.scale(-1.0f);
//        }
//
//        Vector3f[] result = new Vector3f[2*3];
//        result[0] = tangent1;
//        result[1] = bitangent;
//        result[2] = tangent2;
//        result[3] = bitangent;
//        result[4] = tangent3;
//        result[5] = bitangent;
//        return result;
//    }

    @Override
    public String getIdentifier() {
        return COMPONENT_KEY;
    }

    public int getIndexCount() {
        return indicesCounts[0];

    }

    // TODO: Remove this
    public int getIndexOffset() {
        return vertexIndexOffsets.indexOffset;
    }

    // TODO: Remove this
    public int getBaseVertex() {
        return vertexIndexOffsets.vertexOffset;
    }

    public AABB getMinMax(Transform transform) {
        return model.getMinMax(transform);
    }
    public AABB getMinMax() {
        if(!isStatic()) {
            AnimationController animationController = getAnimationController();
            return getMinMax(animationController);
        }
        return model.getMinMax();
    }

    public AABB getMinMax(AnimationController animationController) {
        if(isStatic() || animationController == null) {
            return getModel().getMinMax();
        }
        return getModel().getMinMax(null, animationController);
    }

    public Model getModel() {
        return model;
    }

    public List<Mesh> getMeshes() {
        return model.getMeshes();
    }

    public int getIndexCount(int i) {
        return model.getMeshIndices()[i].size();
    }

    public int getIndexOffset(int i) {
        return indicesCounts[i];
    }

    public int getBaseVertex(int i) {
        return baseVertices[i];
    }

    public boolean isStatic() {
        if(model == null) {
            return true;
        }
        return model.isStatic();
    }

    public int getBaseJointIndex() {
        return jointsOffset;
    }


    @Override
    public void update(Engine engine, float seconds) {
        if(model instanceof AnimatedModel) {
            animationController.update(engine, seconds);
        }
    }

    public int getAnimationFrame0() {
        if(model instanceof AnimatedModel) {
            return animationController.getCurrentFrameIndex();
        }
        return 0;
    }

    public void setHasUpdated(boolean value) {
        if(!isStatic()) {
            animationController.setHasUpdated(value);
        }
    }

    public boolean isHasUpdated() {
        if(!isStatic()) {
            return animationController.isHasUpdated();
        }
        return false;
    }

    public AnimationController getAnimationController() {
        return animationController;
    }

    public float getBoundingSphereRadius(Mesh mesh) {
        return model.getBoundingSphereRadius(mesh, animationController);
    }

    public AABB getMinMax(Transform transform, Mesh mesh) {
        return model.getMinMax(transform, mesh, animationController);
    }

    public boolean isInvertTexCoordY() {
        return model.isInvertTexCoordY();
    }

    public List<Material> getMaterials() {
        return getMeshes().stream().map(Mesh::getMaterial).collect(Collectors.toList());
    }

    @Override
    public void putToBuffer(ByteBuffer buffer) {

        int meshIndex = 0;
        List<Mesh> meshes = getMeshes();
        for(Mesh mesh : meshes) {
            int materialIndex = mesh.getMaterial().getMaterialIndex();
            {
                putValues(buffer, entity.getTransformation(), meshIndex, materialIndex, getAnimationFrame0(), getMinMax(entity, mesh));
            }

            for(Cluster cluster : getClusters(entity)) {
                for(int i = 0; i < cluster.size(); i++) {
                    Instance instance = cluster.get(i);
                    Matrix4f instanceMatrix = instance.getTransformation();
                    int instanceMaterialIndex = instance.getMaterials().get(meshIndex).getMaterialIndex();
                    putValues(buffer, instanceMatrix, meshIndex, instanceMaterialIndex, instance.getAnimationController().getCurrentFrameIndex(), instance.getMinMaxWorld());
                }
            }

            // TODO: This has to be the outer loop i think?
            if(entity.hasParent()) {
                for(Instance instance : getInstances(entity)) {
                    Matrix4f instanceMatrix = instance.getTransformation();
                    putValues(buffer, instanceMatrix, meshIndex, materialIndex, instance.getAnimationController().getCurrentFrameIndex(), entity.getMinMaxWorld());
                }
            }
            meshIndex++;
        }
    }

    private void putValues(ByteBuffer buffer, Matrix4f mm, int meshIndex, int materialIndex, int animationFrame0, AABB minMaxWorld) {
        buffer.putFloat(mm.m00());
        buffer.putFloat(mm.m01());
        buffer.putFloat(mm.m02());
        buffer.putFloat(mm.m03());
        buffer.putFloat(mm.m10());
        buffer.putFloat(mm.m11());
        buffer.putFloat(mm.m12());
        buffer.putFloat(mm.m13());
        buffer.putFloat(mm.m20());
        buffer.putFloat(mm.m21());
        buffer.putFloat(mm.m22());
        buffer.putFloat(mm.m23());
        buffer.putFloat(mm.m30());
        buffer.putFloat(mm.m31());
        buffer.putFloat(mm.m32());
        buffer.putFloat(mm.m33());

        buffer.putInt(entity.isSelected() ? 1 : 0);
        buffer.putInt(materialIndex);
        buffer.putInt((int) entity.getUpdate().getAsDouble());
        buffer.putInt(entityBufferIndex + meshIndex);

        buffer.putInt(entity.getIndex());
        buffer.putInt(meshIndex);
        buffer.putInt(getBaseVertex(meshIndex));
        buffer.putInt(getBaseJointIndex());

        buffer.putInt(animationFrame0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        buffer.putInt(isInvertTexCoordY() ? 1 : 0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        buffer.putFloat(minMaxWorld.getMin().x);
        buffer.putFloat(minMaxWorld.getMin().y);
        buffer.putFloat(minMaxWorld.getMin().z);
        buffer.putFloat(1);

        buffer.putFloat(minMaxWorld.getMax().x);
        buffer.putFloat(minMaxWorld.getMax().y);
        buffer.putFloat(minMaxWorld.getMax().z);
        buffer.putFloat(1);
    }

    @Override
    public int getBytesPerObject() {
        return getBytesPerInstance() * getMeshes().size() * getInstanceCount(entity);
    }

    public static int getBytesPerInstance() {
        return 16 * Float.BYTES + 16 * Integer.BYTES + 8 * Float.BYTES;
    }
}
