package component;

import camera.Camera;
import engine.AppContext;
import engine.Drawable;
import engine.model.DataChannels;
import engine.model.Face;
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
import shader.ProgramFactory;
import util.stopwatch.GPUProfiler;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ModelComponent extends BaseComponent implements Drawable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final boolean USE_PRECOMPUTED_TANGENTSPACE = false;

    private static ExecutorService service = Executors.newFixedThreadPool(4);

    private Model model;

    public boolean instanced = false;

    transient protected VertexBuffer vertexBuffer;
    private float[] floatArray;

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


    public ModelComponent(Model model) {
        this(model, model.getMaterial());
    }
    public ModelComponent(Model model, Material material) {
        this(model, material.getName());
    }
    public ModelComponent(Model model, String materialName) {
        this.materialName = materialName;
        this.model = model;
    }
    @Override
    public int draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram) {
        return draw(camera, modelMatrix, firstPassProgram, AppContext.getInstance().getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }
    @Override
    public int draw(Camera camera) {
        return draw(camera, getEntity().getModelMatrixAsBuffer(), ProgramFactory.getInstance().getFirstpassDefaultProgram(), AppContext.getInstance().getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }

    @Override
    public int draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected) {

        if(!isVisible) {
            return 0;
        }

        if (firstPassProgram == null) {
            return 0;
        }

//        GPUProfiler.start("Set local uniforms first pass");
        Program currentProgram = firstPassProgram;
        currentProgram.setUniform("isInstanced", instanced);
        currentProgram.setUniform("entityIndex", entityIndex);
        currentProgram.setUniform("materialIndex", MaterialFactory.getInstance().indexOf(MaterialFactory.getInstance().get(materialName)));
        currentProgram.setUniform("isSelected", isSelected);
//        currentProgram.setUniformAsMatrix4("modelMatrix", modelMatrix);
//        GPUProfiler.end();

        if(getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        } else {
            OpenGLContext.getInstance().enable(GlCap.CULL_FACE);
        }
        if(instanced) {
            return vertexBuffer.drawInstanced(10);
        } else {
            return vertexBuffer.draw();
        }
    }

    public void drawDebug(Program program, FloatBuffer modelMatrix) {

        if(!getEntity().isVisible()) {
            return;
        }

        program.setUniformAsMatrix4("modelMatrix", modelMatrix);

        model.getMaterial().setTexturesActive(program);
        vertexBuffer.drawDebug();
    }


    public Material getMaterial() {
        Material material = MaterialFactory.getInstance().get(materialName);
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
        long start = System.currentTimeMillis();
        createFloatArray(model);
        System.out.println("createFloatArray took " + (System.currentTimeMillis() - start));
        createVertexBuffer();
        initialized = true;
    }

    public void createFloatArray(Model model) {

        List<Vector3f> verticesTemp = model.getVertices();
        List<Vector2f> texcoordsTemp = model.getTexCoords();
        List<Vector3f> normalsTemp = model.getNormals();
        List<Face> facesTemp = model.getFaces();
//		LOGGER.log(Level.INFO, String.format("Faces: %d", facesTemp.size()));

        List<Float> values = new ArrayList<Float>();

        ////////////// INDICES -1 BECAUSE OBJ STARTS WITH 1 INSTEAD OF ZERO

        for (int i = 0; i < facesTemp.size(); i++) {
            Face face = facesTemp.get(i);

            int[] referencedVertices = face.getVertexIndices();
            int[] referencedNormals = face.getNormalIndices();
            int[] referencedTexcoords = face.getTextureCoordinateIndices();

            Vector3f[] tangentBitangent = null;
            if(USE_PRECOMPUTED_TANGENTSPACE) {
                boolean hasTexcoords = referencedTexcoords[0] != -1;
                if(hasTexcoords) {
                    tangentBitangent = calculateTangentBitangent(verticesTemp.get(referencedVertices[0]-1), verticesTemp.get(referencedVertices[1]-1), verticesTemp.get(referencedVertices[2]-1),
                            texcoordsTemp.get(referencedTexcoords[0]-1), texcoordsTemp.get(referencedTexcoords[1]-1), texcoordsTemp.get(referencedTexcoords[2]-1),
                            normalsTemp.get(referencedNormals[0]-1), normalsTemp.get(referencedNormals[1]-1), normalsTemp.get(referencedNormals[2]-1));
                } else {
                    tangentBitangent = new Vector3f[6];
                    Vector3f edge1 = Vector3f.sub(verticesTemp.get(referencedVertices[1]-1), verticesTemp.get(referencedVertices[0]-1), null);
                    Vector3f edge2 = Vector3f.sub(verticesTemp.get(referencedVertices[2]-1), verticesTemp.get(referencedVertices[0]-1), null);
                    tangentBitangent[0] = edge1;
                    tangentBitangent[1] = edge2;
                    tangentBitangent[2] = edge1;
                    tangentBitangent[3] = edge2;
                    tangentBitangent[4] = edge1;
                    tangentBitangent[5] = edge2;
                }
            }

            for (int j = 0; j < 3; j++) {
                Vector3f referencedVertex = verticesTemp.get(referencedVertices[j]-1);
                Vector2f referencedTexcoord = new Vector2f(0,0);
                try {
                    referencedTexcoord = texcoordsTemp.get(referencedTexcoords[j]-1);
                } catch (Exception e) {

                }
                Vector3f referencedNormal = normalsTemp.get(referencedNormals[j]-1);

                values.add(referencedVertex.x);
                values.add(referencedVertex.y);
                values.add(referencedVertex.z);
                values.add(referencedTexcoord.x);
                values.add(referencedTexcoord.y);
                values.add(referencedNormal.x);
                values.add(referencedNormal.y);
                values.add(referencedNormal.z);

                if(USE_PRECOMPUTED_TANGENTSPACE) {
                    values.add(tangentBitangent[2*j].x);
                    values.add(tangentBitangent[2*j].y);
                    values.add(tangentBitangent[2*j].z);
                    values.add(tangentBitangent[2*j+1].x);
                    values.add(tangentBitangent[2*j+1].y);
                    values.add(tangentBitangent[2*j+1].z);
                }
            }

        }

//		LOGGER.log(Level.INFO, String.format("Values: %d", values.size()));

        floatArray = new float[values.size()];

        for (int i = 0; i < values.size(); i++) {
            floatArray[i] = values.get(i);
        }

        verticesTemp = null;
        texcoordsTemp = null;
        normalsTemp = null;
        facesTemp = null;
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

        FloatBuffer verticesFloatBuffer = BufferUtils.createFloatBuffer(floatArray.length * 4);
        verticesFloatBuffer.rewind();
        verticesFloatBuffer.put(floatArray);
        verticesFloatBuffer.rewind();
//		LOGGER.log(Level.INFO, String.format("Bytes: %d", verticesFloatBuffer.capacity()));

        long start = System.currentTimeMillis();
        vertexBuffer = new VertexBuffer(verticesFloatBuffer, DEFAULTCHANNELS);
        System.out.println("Creating the VB took " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        vertexBuffer.upload();
        System.out.println("Uploading the VB took " + (System.currentTimeMillis() - start));
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
