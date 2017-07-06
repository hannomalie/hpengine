package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets;
import org.joml.Vector3f;
import org.joml.Vector4f;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.model.texture.Texture;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.logging.Logger;

public class ModelComponent extends BaseComponent implements Serializable {
    public static final String COMPONENT_KEY = ModelComponent.class.getSimpleName();
    private static final Logger LOGGER = Logger.getLogger(ModelComponent.class.getName());
    private static final long serialVersionUID = 1L;

    private Model model;

    public boolean instanced = false;

    public float[] floatArray;
    protected List<int[]> indices = new ArrayList<>();
    private int[] indicesCounts;
    private int[] baseVertices;

    protected String materialName = "";

    public static EnumSet<DataChannels> DEFAULTCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL,
            DataChannels.LIGHTMAP_TEXCOORD);
    public static EnumSet<DataChannels> DEPTHCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.NORMAL
    );
    public static EnumSet<DataChannels> SHADOWCHANNELS = EnumSet.of(
            DataChannels.POSITION3);
    public static EnumSet<DataChannels> POSITIONCHANNEL = EnumSet.of(
            DataChannels.POSITION3);
    private VertexIndexOffsets vertexIndexOffsets;
    protected AnimatedModel animGameItem;

    public List<int[]> getLodLevels() {
        return Collections.unmodifiableList(lodLevels);
    }

    protected volatile List<int[]> lodLevels = new ArrayList<>();

    public ModelComponent(Model model) {
        super();
        this.model = model;
        createFloatArray();
    }

    private transient Vector3f distance = new Vector3f();

    private void setTexturesUsed() {
        for(Texture texture : getMaterial().getTextures()) {
            texture.setUsedNow();
        }
    }

    private transient WeakReference<Material> materialCache = null;
    public Material getMaterial() {
        if(materialCache != null && materialCache.get() != null && materialCache.get().getName().equals(materialName)) {
            return materialCache.get();
        }
        Material material = MaterialFactory.getInstance().getMaterial(materialName);
        materialCache = new WeakReference<>(material);
        if(material == null) {
            Logger.getGlobal().info("Material null, default is applied");
            return MaterialFactory.getInstance().getDefaultMaterial();
        }
        return material;
    }
    public void setMaterial(String materialName) {
        this.materialName = materialName;
        model.setMaterial(MaterialFactory.getInstance().getMaterial(materialName));
        for(Entity child : entity.getChildren()) {
            child.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(c -> c.setMaterial(materialName));
        }
    }

    @Override
    public void init() {
        super.init();
        initialized = true;
    }

    @Override
    public void registerInScene(Scene scene) {
        VertexIndexBuffer vertexIndexBuffer = scene.getVertexIndexBuffer();
        putToBuffer(vertexIndexBuffer);
    }

    public VertexIndexOffsets putToBuffer(VertexIndexBuffer vertexIndexBuffer) {

        int totalElementsPerVertex = DataChannels.totalElementsPerVertex(DEFAULTCHANNELS);
        int vertexElementsCount = floatArray.length / totalElementsPerVertex;
        int indicesCount = getIndices().length;
        vertexIndexOffsets = vertexIndexBuffer.allocate(vertexElementsCount, indicesCount);

        indicesCounts = new int[model.getMeshes().size()];
        baseVertices = new int[model.getMeshes().size()];

        int currentIndexOffset = vertexIndexOffsets.indexOffset;
        int currentVertexOffset = vertexIndexOffsets.vertexOffset;

        for(int i = 0; i < indicesCounts.length; i++) {
            Mesh mesh = model.getMeshes().get(i);
            indicesCounts[i] = currentIndexOffset;
            baseVertices[i] = currentVertexOffset;
            currentIndexOffset += mesh.getIndexBufferValuesArray().length;
            currentVertexOffset += mesh.getVertexBufferValuesArray().length/totalElementsPerVertex;
        }

        this.getModel().putToValueArrays();
        this.createFloatArray();

        GraphicsContext.getInstance().execute(() -> {
            vertexIndexBuffer.getVertexBuffer().putValues(vertexIndexOffsets.vertexOffset*totalElementsPerVertex, floatArray);
            vertexIndexBuffer.getIndexBuffer().appendIndices(vertexIndexOffsets.indexOffset, getIndices());
            LOGGER.fine("Current IndexOffset: " + vertexIndexOffsets.indexOffset);
            LOGGER.fine("Current BaseVertex: " + vertexIndexOffsets.vertexOffset);
            vertexIndexBuffer.getVertexBuffer().upload();
        });

        return vertexIndexOffsets;
    }

    public void setLodLevels(List<int[]> lodLevels) {
        this.lodLevels = lodLevels;
    }

    public float getBoundingSphereRadius() {
        return model.getBoundingSphereRadius();
    }

    public int[] getIndices() {
        return indices.get(0);
    }

    public int getTriangleCount() {
        return model.getTriangleCount();
    }

    public void createFloatArray() {
        floatArray = model.getVertexBufferValuesArray();
        indices.add(model.getIndexBufferValuesArray());

        lodLevels.add(indices.get(0));
        indices = lodLevels;
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

    public Vector4f[] getMinMax() {
        return model.getMinMax();
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

    public Vector3f[] getMinMax(Transform transform) {
        return model.getMinMax(transform);
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
}
