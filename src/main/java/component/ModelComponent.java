package component;

import engine.DrawConfiguration;
import engine.model.DataChannels;
import engine.model.Model;
import engine.model.VertexBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLContext;
import renderer.constants.GlCap;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import shader.Program;
import texture.Texture;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.logging.Logger;

public class ModelComponent extends BaseComponent implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(ModelComponent.class.getName());
    private static final long serialVersionUID = 1L;
    public static final boolean USE_PRECOMPUTED_TANGENTSPACE = false;

    private Model model;

    public boolean instanced = false;

    transient protected VertexBuffer vertexBuffer;
    public float[] floatArray;
    private List<int[]> indices = new ArrayList<>();

    protected String materialName = "";

    public static EnumSet<DataChannels> DEFAULTCHANNELS = USE_PRECOMPUTED_TANGENTSPACE ? EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL,
            DataChannels.TANGENT,
            DataChannels.BINORMAL
            ) : EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL);
    public static EnumSet<DataChannels> DEPTHCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.NORMAL
    );
    public static EnumSet<DataChannels> SHADOWCHANNELS = EnumSet.of(
            DataChannels.POSITION3);
    public static EnumSet<DataChannels> POSITIONCHANNEL = EnumSet.of(
            DataChannels.POSITION3);

    public List<int[]> getLodLevels() {
        return Collections.unmodifiableList(lodLevels);
    }

    private volatile List<int[]> lodLevels = new ArrayList<>();

    public ModelComponent(Model model) {
        this(model, model.getMaterial());
    }
    public ModelComponent(Model model, Material material) {
        this(model, material.getName());
    }
    public ModelComponent(Model model, String materialName) {
        this.materialName = materialName;
        this.model = model;
        createFloatArray(model);
    }

    public static int staticDraw(DrawConfiguration drawConfiguration) {
        if(!drawConfiguration.isVisible()) {
            return 0;
        }

        if (drawConfiguration.getFirstPassProgram() == null) {
            return 0;
        }
        Program currentProgram = drawConfiguration.getFirstPassProgram();

        currentProgram.setUniform("entityIndex", drawConfiguration.getEntityIndex());
        currentProgram.setUniform("entityBaseIndex", drawConfiguration.getEntityBaseIndex());

        Material material = drawConfiguration.getMaterial();
        material.setTexturesActive(currentProgram, drawConfiguration.isInReachForTextureLoading());

        if(material.getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        }
        if (drawConfiguration.isDrawLines()) {
            return drawConfiguration.getVertexBuffer().drawDebug(2, 0);//ModelLod.ModelLodStrategy.DISTANCE_BASED.getIndexBufferIndex(drawConfiguration.getExtract(), this));
        } else {
            return drawConfiguration.getVertexBuffer().drawInstanced(drawConfiguration.getInstanceCount());
        }
    }

    private transient Vector3f distance = new Vector3f();

    private void setTexturesUsed() {
        for(Texture texture : getMaterial().getTextures()) {
            texture.setUsedNow();
        }
    }

    public int drawDebug(Program program, FloatBuffer modelMatrix) {

        if(!getEntity().isVisible()) {
            return 0;
        }

        program.setUniformAsMatrix4("modelMatrix", modelMatrix);

        model.getMaterial().setTexturesActive(program);
        vertexBuffer.drawDebug();
        return 0;
    }


    private transient WeakReference<Material> materialCache = null;
    public Material getMaterial() {
        if(materialCache != null && materialCache.get() != null && materialCache.get().getName().equals(materialName)) {
            return materialCache.get();
        }
        Material material = MaterialFactory.getInstance().get(materialName);
        materialCache = new WeakReference<>(material);
        if(material == null) {
            Logger.getGlobal().info("Material null, default is applied");
            return MaterialFactory.getInstance().getDefaultMaterial();
        }
        return material;
    }
    public void setMaterial(String materialName) {
        this.materialName = materialName;
        model.setMaterial(MaterialFactory.getInstance().get(materialName));
    }

    @Override
    public void init() {
        super.init();
        createVertexBuffer();
        initialized = true;
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
        return model.getFaces().size();
    }

    public List<Vector3f> getVertices() {
        return Collections.unmodifiableList(model.getVertices());
    }

    public void createFloatArray(Model model) {
        floatArray = model.getVertexBufferValuesArray();
        indices.add(model.getIndexBufferValuesArray());

        int sizeBeforeReduction = indices.get(0).length/3;
//        LodGenerator lodGenerator = new LodGenerator(this);
//        lodGenerator.bakeLods(LodGenerator.TriangleReductionMethod.PROPORTIONAL, 0.25f, 0.5f, 0.75f);
        lodLevels.add(indices.get(0));
        indices = lodLevels;
        LOGGER.fine("############## faces count before reduction: " + sizeBeforeReduction);
        LOGGER.fine("############## lodlevels calculated: " + lodLevels.size());
    }


    private Vector3f[] calculateTangentBitangent(Vector3f v1, Vector3f v2, Vector3f v3, Vector2f w1, Vector2f w2, Vector2f w3, Vector3f n1, Vector3f n2, Vector3f n3) {
        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();

        Vector3f edge1 = Vector3f.sub(v2, v1, null);
        Vector3f edge2 = Vector3f.sub(v3, v1, null);

        Vector2f deltaUV1 = Vector2f.sub(w2, w1, null);
        Vector2f deltaUV2 = Vector2f.sub(w3, w1, null);

        float DeltaU1 = deltaUV1.x;
        float DeltaV1 = deltaUV1.y;
        float DeltaU2 = deltaUV2.x;
        float DeltaV2 = deltaUV2.y;

        float f = 1.0F / (deltaUV1.x * deltaUV2.y - deltaUV1.y * deltaUV2.x);

        tangent = Vector3f.sub((Vector3f) new Vector3f(edge1).scale(DeltaV2), (Vector3f) new Vector3f(edge2).scale(DeltaV1), null);
        bitangent = Vector3f.sub((Vector3f) new Vector3f(edge2).scale(DeltaU1), (Vector3f) new Vector3f(edge1).scale(DeltaU2), null);

        tangent = (Vector3f) new Vector3f(tangent).scale(f);
        bitangent = (Vector3f) new Vector3f(bitangent).scale(f);

        Vector3f tangent1 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n1).scale(Vector3f.dot(n1, tangent))), null).normalise(null);
        Vector3f tangent2 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n2).scale(Vector3f.dot(n2, tangent))), null).normalise(null);
        Vector3f tangent3 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n3).scale(Vector3f.dot(n3, tangent))), null).normalise(null);

        if (Vector3f.dot(Vector3f.cross(n1, tangent1, null), bitangent) < 0.0f){
            tangent1.scale(-1.0f);
        }
        if (Vector3f.dot(Vector3f.cross(n2, tangent2, null), bitangent) < 0.0f){
            tangent2.scale(-1.0f);
        }
        if (Vector3f.dot(Vector3f.cross(n3, tangent3, null), bitangent) < 0.0f){
            tangent3.scale(-1.0f);
        }

        Vector3f[] result = new Vector3f[2*3];
        result[0] = tangent1;
        result[1] = bitangent;
        result[2] = tangent2;
        result[3] = bitangent;
        result[4] = tangent3;
        result[5] = bitangent;
        return result;
    }

    public void createVertexBuffer() {

        FloatBuffer verticesFloatBuffer = BufferUtils.createFloatBuffer(floatArray.length);
        verticesFloatBuffer.rewind();
        verticesFloatBuffer.put(floatArray);
        verticesFloatBuffer.rewind();

        List<IntBuffer> indexBuffers = new ArrayList<>();
        for(int[] indexArray : indices) {
            int[] indicesTemp = indexArray;
            LOGGER.fine(Arrays.toString(indicesTemp));
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indicesTemp.length);
            indexBuffers.add(indexBuffer);
            indexBuffer.rewind();
            indexBuffer.put(indicesTemp);
            indexBuffer.rewind();

        }
        vertexBuffer = new VertexBuffer(verticesFloatBuffer, DEFAULTCHANNELS, indexBuffers.toArray(new IntBuffer[0]));
        vertexBuffer.upload();
    }

    @Override
    public String getIdentifier() {
        return "ModelComponent";
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public Vector4f[] getMinMax() {
        return model.getMinMax();
    }

    public Vector3f getCenter() {
        return model.getCenter();
    }
}
