package component;

import camera.Camera;
import config.Config;
import engine.AppContext;
import engine.Drawable;
import engine.model.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLThread;
import renderer.material.Material;
import shader.Program;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ModelComponent extends BaseComponent implements Drawable, Serializable {
    private static final long serialVersionUID = 1L;

    private static ExecutorService service = Executors.newFixedThreadPool(4);

    private Model model;

    public boolean instanced = false;

    transient protected VertexBuffer vertexBuffer;
    private float[] floatArray;

    protected String materialName = "";

    public static EnumSet<DataChannels> DEFAULTCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL
		,
		DataChannels.TANGENT,
		DataChannels.BINORMAL
    );
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
    public void draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram) {
        draw(camera, modelMatrix, firstPassProgram, appContext.getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }
    @Override
    public void draw(Camera camera) {
        draw(camera, getEntity().getModelMatrixAsBuffer(), appContext.getRenderer().getMaterialFactory().get(materialName).getFirstPassProgram(), appContext.getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }

    @Override
    public void draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected) {

        if(!isVisible) {
            return;
        }

        if (firstPassProgram == null) {
            return;
        }

        Program currentProgram = firstPassProgram;
//		if (!firstPassProgram.equals(renderer.getLastUsedProgram())) {
//			currentProgram = firstPassProgram;
//			renderer.setLastUsedProgram(currentProgram);
//			currentProgram.use();
//		}
//		currentProgram = renderer.getLastUsedProgram();
        currentProgram.use();
        currentProgram.setUniform("isInstanced", instanced);
        currentProgram.setUniform("useParallax", Config.useParallax);
        currentProgram.setUniform("useSteepParallax", Config.useSteepParallax);
        currentProgram.setUniform("useRainEffect", Config.RAINEFFECT == 0.0 ? false : true);
        currentProgram.setUniform("rainEffect", Config.RAINEFFECT);
        currentProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        currentProgram.setUniformAsMatrix4("lastViewMatrix", camera.getLastViewMatrixAsBuffer());
        currentProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        currentProgram.setUniform("eyePosition", camera.getPosition());
        currentProgram.setUniform("lightDirection", appContext.getScene().getDirectionalLight().getViewDirection());
        currentProgram.setUniform("near", camera.getNear());
        currentProgram.setUniform("far", camera.getFar());
        currentProgram.setUniform("time", (int)System.currentTimeMillis());
        currentProgram.setUniform("entityIndex", entityIndex);
        currentProgram.setUniform("isSelected", isSelected);
        currentProgram.setUniformAsMatrix4("modelMatrix", modelMatrix);
        appContext.getRenderer().getMaterialFactory().get(materialName).setTexturesActive(currentProgram);

//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
//		renderer.getEnvironmentMap().bind();

        if(instanced) {
            vertexBuffer.drawInstanced(10);
        } else {
            vertexBuffer.draw();
        }

//		material.setTexturesInactive();
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
        Material material = getMaterialFactory().get(materialName);
        if(material == null) {
            Logger.getGlobal().info("Material null, default is applied");
            return getMaterialFactory().getDefaultMaterial();
        }
        return material;
    }
    public void setMaterial(String materialName) {
        this.materialName = materialName;
        model.setMaterial(getRenderer().getMaterialFactory().get(materialName));
    };

    @Override
    public Program getFirstPassProgram() {
        return model.getMaterial().getFirstPassProgram();
    }

    @Override
    public void init(AppContext appContext) {
        super.init(appContext);
//        Thread thread = new OpenGLThread(getEntity().getName() + " model init") {
//            @Override
//            public void doRun() {
//                createFloatArray(model);
//                createVertexBuffer();
//                initialized = true;
//            }
//        };
//        service.submit(thread);
        createFloatArray(model);
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

			Vector3f[] tangentBitangent;
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

				values.add(tangentBitangent[2*j].x);
				values.add(tangentBitangent[2*j].y);
				values.add(tangentBitangent[2*j].z);
				values.add(tangentBitangent[2*j+1].x);
				values.add(tangentBitangent[2*j+1].y);
				values.add(tangentBitangent[2*j+1].z);
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

        vertexBuffer = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS);
        vertexBuffer.upload();

//		vertexBufferShadow = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS).upload();
    }

    @Override
    public String getIdentifier() {
        return "ModelComponent";
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }
}
