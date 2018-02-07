package de.hanno.hpengine.engine.model.loader.md5;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.StaticMesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MD5Mesh implements Mesh {

    private static final Pattern PATTERN_SHADER = Pattern.compile("\\s*shader\\s*\\\"([^\\\"]+)\\\"");

    private static final Pattern PATTERN_VERTEX = Pattern.compile("\\s*vert\\s*(\\d+)\\s*\\(\\s*("
            + MD5Utils.FLOAT_REGEXP + ")\\s*(" + MD5Utils.FLOAT_REGEXP + ")\\s*\\)\\s*(\\d+)\\s*(\\d+)");

    private static final Pattern PATTERN_TRI = Pattern.compile("\\s*tri\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)");

    private static final Pattern PATTERN_WEIGHT = Pattern.compile("\\s*weight\\s*(\\d+)\\s*(\\d+)\\s*" +
            "(" + MD5Utils.FLOAT_REGEXP + ")\\s*" + MD5Utils.VECTOR3_REGEXP );
    public static final int VALUES_PER_VERTEX = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS);
    private List<AnimatedVertex> compiledVertices;
    private float[] positionsArr;
    private float[] textCoordsArr;
    private float[] normalsArr;
    private int[] indicesArr;
    private int[] jointIndicesArr;
    private float[] weightsArr;

    private String diffuseTexture;

    private List<MD5Vertex> vertices;

    private List<MD5Triangle> triangles;

    private List<MD5Weight> weights;
    private float[] vertexBufferValuesArray;
    private IntArrayList indicesList = new IntArrayList();
    private Material material;
    private String name;
    private AnimatedModel model;

    public MD5Mesh(float[] positionsArr, float[] textCoordsArr, float[] normalsArr, int[] indicesArr, int[] jointIndicesArr, float[] weightsArr) {
        this.positionsArr = positionsArr;
        this.textCoordsArr = textCoordsArr;
        this.normalsArr = normalsArr;
        this.indicesArr = indicesArr;
        this.jointIndicesArr = jointIndicesArr;
        this.weightsArr = weightsArr;

        int counter = 0;

        int vertexCount = positionsArr.length / 3;
        vertexBufferValuesArray = new float[vertexCount * VALUES_PER_VERTEX];
        for(int i = 0; i < vertexCount; i+=1) {
            int currentIndex = i;

            int vec3BaseIndex = 3*currentIndex;
            vertexBufferValuesArray[counter++] = positionsArr[vec3BaseIndex];
            vertexBufferValuesArray[counter++] = positionsArr[vec3BaseIndex +1];
            vertexBufferValuesArray[counter++] = positionsArr[vec3BaseIndex +2];

            vertexBufferValuesArray[counter++] = textCoordsArr[2*currentIndex];
            vertexBufferValuesArray[counter++] = textCoordsArr[2*currentIndex+1];

            vertexBufferValuesArray[counter++] = normalsArr[vec3BaseIndex];
            vertexBufferValuesArray[counter++] = normalsArr[vec3BaseIndex +1];
            vertexBufferValuesArray[counter++] = normalsArr[vec3BaseIndex +2];

            vertexBufferValuesArray[counter++] = 0;
            vertexBufferValuesArray[counter++] = 0;
            vertexBufferValuesArray[counter++] = 0;

        }
        compiledVertices = null;
    }

    public MD5Mesh() {
        this.vertices = new ArrayList<>();
        this.triangles = new ArrayList<>();
        this.weights = new ArrayList<>();
        compiledVertices = null;
    }

    public MD5Mesh(float[] positionsArr, float[] textCoordsArr, float[] normalsArr, int[] indicesArr, int[] jointIndicesArr, float[] weightsArr, List<AnimCompiledVertex> vertices) {
        this(positionsArr, textCoordsArr, normalsArr, indicesArr, jointIndicesArr, weightsArr);
        this.compiledVertices = vertices.stream().map(in -> convert(in)).collect(Collectors.toList());
    }

    private AnimatedVertex convert(AnimCompiledVertex in) {
        float[] weights1 = new float[4];
        for(int i = 0; i < 4; i++) {
            weights1[i] = 0;
        }
        for(int i = 0; i < in.weights.length && i < 4; i++) {
            weights1[i] = in.weights[i];
        }
        Vector4f weights = new Vector4f(weights1[0], weights1[1], weights1[2], weights1[3]);
        int[] jointIndices1 = new int[4];
        for(int i = 0; i < 4; i++) {
            jointIndices1[i] = 0;
        }
        for(int i = 0; i < in.jointIndices.length && i < 4; i++) {
            jointIndices1[i] = in.jointIndices[i];
        }
        Vector4i jointIndices = new Vector4i(jointIndices1[0], jointIndices1[1], jointIndices1[2], jointIndices1[3]);
        return new AnimatedVertex("Vertex", in.position, in.textCoords, in.normal, weights, jointIndices);
    }

    public List<AnimatedVertex> getCompiledVertices() {
        return compiledVertices;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("mesh [" + System.lineSeparator());
        str.append("diffuseTexture: ").append(diffuseTexture).append(System.lineSeparator());

        str.append("vertices [").append(System.lineSeparator());
        for (MD5Vertex vertex : vertices) {
            str.append(vertex).append(System.lineSeparator());
        }
        str.append("]").append(System.lineSeparator());

        str.append("triangles [").append(System.lineSeparator());
        for (MD5Triangle triangle : triangles) {
            str.append(triangle).append(System.lineSeparator());
        }
        str.append("]").append(System.lineSeparator());

        str.append("weights [").append(System.lineSeparator());
        for (MD5Weight weight : weights) {
            str.append(weight).append(System.lineSeparator());
        }
        str.append("]").append(System.lineSeparator());

        return str.toString();
    }

    public static MD5Mesh parse(File modelFileBaseDir, List<String> meshBlock) {
        MD5Mesh mesh = new MD5Mesh();
        List<MD5Vertex> vertices = mesh.getVertices();
        List<MD5Triangle> triangles = mesh.getTriangles();
        List<MD5Weight> weights = mesh.getWeights();

        for (String line : meshBlock) {
            if (line.contains("shader")) {
                Matcher textureMatcher = PATTERN_SHADER.matcher(line);
                if (textureMatcher.matches()) {
                    String textureFileName = textureMatcher.group(1);
                    mesh.setDiffuseTexture(modelFileBaseDir.getAbsolutePath() + "/" + textureFileName);
                    mesh.setName(textureFileName);
                }
            } else if (line.contains("vert")) {
                Matcher vertexMatcher = PATTERN_VERTEX.matcher(line);
                if (vertexMatcher.matches()) {
                    MD5Vertex vertex = new MD5Vertex();
                    vertex.setIndex(Integer.parseInt(vertexMatcher.group(1)));
                    float x = Float.parseFloat(vertexMatcher.group(2));
                    float y = Float.parseFloat(vertexMatcher.group(3));
                    vertex.setTextCoords(new Vector2f(x, y));
                    vertex.setStartWeight(Integer.parseInt(vertexMatcher.group(4)));
                    vertex.setWeightCount(Integer.parseInt(vertexMatcher.group(5)));
                    vertices.add(vertex);
                }
            } else if (line.contains("tri")) {
                Matcher triMatcher = PATTERN_TRI.matcher(line);
                if (triMatcher.matches()) {
                    MD5Triangle triangle = new MD5Triangle();
                    triangle.setIndex(Integer.parseInt(triMatcher.group(1)));
                    triangle.setVertex0(Integer.parseInt(triMatcher.group(2)));
                    triangle.setVertex1(Integer.parseInt(triMatcher.group(4)));
                    triangle.setVertex2(Integer.parseInt(triMatcher.group(3)));
//                    triangle.setVertex0(Integer.parseInt(triMatcher.group(2)));
//                    triangle.setVertex1(Integer.parseInt(triMatcher.group(3)));
//                    triangle.setVertex2(Integer.parseInt(triMatcher.group(4)));
                    triangles.add(triangle);
                }
            } else if (line.contains("weight")) {
                Matcher weightMatcher = PATTERN_WEIGHT.matcher(line);
                if (weightMatcher.matches()) {
                    MD5Weight weight = new MD5Weight();
                    weight.setIndex(Integer.parseInt(weightMatcher.group(1)));
                    weight.setJointIndex(Integer.parseInt(weightMatcher.group(2)));
                    weight.setBias(Float.parseFloat(weightMatcher.group(3)));
                    float x = Float.parseFloat(weightMatcher.group(4));
                    float y = Float.parseFloat(weightMatcher.group(5));
                    float z = Float.parseFloat(weightMatcher.group(6));
                    weight.setPosition(new Vector3f(x, y, z));
                    weights.add(weight);
                }
            }
        }

        mesh.setTriangles(triangles);
        mesh.setVertices(vertices);
        mesh.setWeights(weights);
        return mesh;
    }

    public String getDiffuseTexture() {
        return diffuseTexture;
    }

    public void setDiffuseTexture(String texture) {
        this.diffuseTexture = texture;
    }

    public List<MD5Vertex> getVertices() {
        return vertices;
    }

    public void setVertices(List<MD5Vertex> vertices) {
        this.vertices = vertices;
    }

    public List<MD5Triangle> getTriangles() {
        return triangles;
    }

    public void setTriangles(List<MD5Triangle> triangles) {
        this.triangles = triangles;
    }

    public List<MD5Weight> getWeights() {
        return weights;
    }

    public void setWeights(List<MD5Weight> weights) {
        this.weights = weights;
    }

    @Override
    public float[] getVertexBufferValuesArray() {
        return vertexBufferValuesArray;
    }

    @Override
    public int[] getIndexBufferValuesArray() {
        return indicesArr;
    }

    @Override
    public int getTriangleCount() {
        return 0;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public void init(MaterialFactory materialFactory) {

    }

    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    @Override
    public void putToValueArrays() {

    }

    @Override
    public AABB getMinMax(Transform transform) {
        return model.getMinMax(transform);
    }

    @Override
    public Vector3f getCenter(Entity entity) {
        ModelComponent component = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
        return entity.transformPosition(centerTemp.set(((AnimatedModel)component.getModel()).getCurrentBoundInfo(component.getAnimationController().getCurrentFrameIndex()).getCenterWorld(entity)));
    }
    Vector3f centerTemp = new Vector3f();

    @Override
    public float getBoundingSphereRadius() {
        return model.getBoundingSphereRadius();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<StaticMesh.CompiledFace> getFaces() {
        return null;
    }

    @Override
    public IntArrayList getIndexBufferValues() {
        indicesList.clear();
        indicesList.add(indicesArr, 0, indicesArr.length-1);
        return indicesList;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public float[] getPositionsArray() {
        return positionsArr;
    }

    public void setModel(AnimatedModel model) {
        this.model = model;
    }

    public static class MD5Vertex {

        private int index;

        private Vector2f textCoords;

        private int startWeight;

        private int weightCount;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Vector2f getTextCoords() {
            return textCoords;
        }

        public void setTextCoords(Vector2f textCoords) {
            this.textCoords = textCoords;
        }

        public int getStartWeight() {
            return startWeight;
        }

        public void setStartWeight(int startWeight) {
            this.startWeight = startWeight;
        }

        public int getWeightCount() {
            return weightCount;
        }

        public void setWeightCount(int weightCount) {
            this.weightCount = weightCount;
        }

        @Override
        public String toString() {
            return "[index: " + index + ", textCoods: " + textCoords
                    + ", startWeight: " + startWeight + ", weightCount: " + weightCount + "]";
        }
    }

    public static class MD5Triangle {

        private int index;

        private int vertex0;

        private int vertex1;

        private int vertex2;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getVertex0() {
            return vertex0;
        }

        public void setVertex0(int vertex0) {
            this.vertex0 = vertex0;
        }

        public int getVertex1() {
            return vertex1;
        }

        public void setVertex1(int vertex1) {
            this.vertex1 = vertex1;
        }

        public int getVertex2() {
            return vertex2;
        }

        public void setVertex2(int vertex2) {
            this.vertex2 = vertex2;
        }

        @Override
        public String toString() {
            return "[index: " + index + ", vertex0: " + vertex0
                    + ", vertex1: " + vertex1 + ", vertex2: " + vertex2 + "]";
        }
    }

    public static class MD5Weight {

        private int index;

        private int jointIndex;

        private float bias;

        private Vector3f position;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getJointIndex() {
            return jointIndex;
        }

        public void setJointIndex(int jointIndex) {
            this.jointIndex = jointIndex;
        }

        public float getBias() {
            return bias;
        }

        public void setBias(float bias) {
            this.bias = bias;
        }

        public Vector3f getPosition() {
            return position;
        }

        public void setPosition(Vector3f position) {
            this.position = position;
        }

        @Override
        public String toString() {
            return "[index: " + index + ", jointIndex: " + jointIndex
                    + ", bias: " + bias + ", position: " + position + "]";
        }
    }
}
