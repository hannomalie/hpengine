package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleSpatial;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.scene.Vertex;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;

public class StaticMesh extends SimpleSpatial implements Serializable, Mesh {
    private static Logger LOGGER = getLogger();

	private static final long serialVersionUID = 1L;

	private UUID uuid = UUID.randomUUID();

	private List<Vector3f> positions = new ArrayList<>();
    private List<Vector2f> texCoords = new ArrayList<>();
    private List<Vector3f> normals = new ArrayList<>();
    private List<Vector3f> lightmapTexCoords = new ArrayList<>();
    private List<Face> indexFaces = new ArrayList<>();
    private List<CompiledFace> compiledFaces = new ArrayList<>();
	private String name = "";
	private Material material;
    private IntArrayList indexBufferValues = new IntArrayList();
    private FloatArrayList vertexBufferValues = new FloatArrayList();
    private int[] indexBufferValuesArray;
    private final int valuesPerVertex = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS);
    private final List<Vertex> compiledVertices = new ArrayList<>();
    private AABB minMax = new AABB(new Vector3f(), new Vector3f());

    @Override
    public float[] getVertexBufferValuesArray() {
        return vertexBufferValuesArray;
    }

    private float[] vertexBufferValuesArray;

    public List<CompiledFace> getFaces() {
        return compiledFaces;
    }

	public void setName(String name) {
		this.name  = name;
	}

    public void setMaterial(Material material) {
		this.material = material;
	}

    public Material getMaterial() {
		return material;
	}

	public String getName() {
		return name;
	}

    public StaticMesh(String name, List<Vector3f> positions, List<Vector2f> texCoords, List<Vector3f> normals) {
        this.name = name;
        this.positions = positions;
        this.texCoords = texCoords;
        this.normals = normals;
    }

    public void init() {

        if (material == null) {
            LOGGER.log(Level.INFO, "No material found!!!");
            material = MaterialFactory.getInstance().getDefaultMaterial();
        }
        compiledVertices.clear();

        FloatArrayList values = new FloatArrayList(indexFaces.size() * valuesPerVertex);
        List<Vector3f[]> allLightMapCoords = new ArrayList<>(indexFaces.size());

        for(Face face : indexFaces) {
            int[] referencedVertices = face.getVertices();
            Vector3f[] referencedVerticesAsVec3 = new Vector3f[3];
            referencedVerticesAsVec3[0] = positions.get(referencedVertices[0]-1);
            referencedVerticesAsVec3[1] = positions.get(referencedVertices[1]-1);
            referencedVerticesAsVec3[2] = positions.get(referencedVertices[2]-1);
            Vector3f[] lightmapCoords = getLightMapCoords(referencedVerticesAsVec3);

            allLightMapCoords.add(lightmapCoords);
        }

        List<Vector3f[]> finalLightmapCoords = allLightMapCoords;
        for (Vector3f[] face : finalLightmapCoords) {
            lightmapTexCoords.add(face[0]);
            lightmapTexCoords.add(face[1]);
            lightmapTexCoords.add(face[2]);
        }

        for (int i = 0; i < indexFaces.size(); i++) {
            Face face = indexFaces.get(i);

            int[] referencedVertices = face.getVertices();
            int[] referencedNormals = face.getNormalIndices();
            int[] referencedTexcoords = face.getTextureCoordinateIndices();
            Vector3f[] compiledPositions = new Vector3f[3];
            Vector2f[] compiledTexCoords = new Vector2f[3];
            Vector3f[] compiledNormals = new Vector3f[3];
            Vector3f[] compiledLightmapCoords = new Vector3f[3];

            for (int j = 0; j < 3; j++) {
                Vector3f referencedVertex = positions.get(referencedVertices[j] - 1);
                compiledPositions[j] = referencedVertex;
                Vector2f referencedTexcoord = new Vector2f(0, 0);
                try {
                    referencedTexcoord = texCoords.get(referencedTexcoords[j] - 1);
                } catch (Exception e) {
                }
                compiledTexCoords[j] = referencedTexcoord;
                Vector3f referencedNormal = normals.get(referencedNormals[j] - 1);
                compiledNormals[j] = referencedNormal;

                values.add(referencedVertex.x);
                values.add(referencedVertex.y);
                values.add(referencedVertex.z);
                values.add(referencedTexcoord.x);
                values.add(referencedTexcoord.y);
                values.add(referencedNormal.x);
                values.add(referencedNormal.y);
                values.add(referencedNormal.z);

                Vector3f lightmapCoords = finalLightmapCoords.get(i)[j];
                compiledLightmapCoords[j] = lightmapCoords;
                values.add(lightmapCoords.x());
                values.add(lightmapCoords.y());
                values.add(lightmapCoords.z());

                compiledVertices.add(new Vertex("Vertex", referencedVertex, referencedTexcoord, referencedNormal, lightmapCoords));

            }
            compiledFaces.add(new CompiledFace(compiledPositions, compiledTexCoords, compiledNormals, allLightMapCoords.get(i), compiledLightmapCoords));
        }

        List<CompiledVertex> uniqueVertices = new ArrayList<>();
        for (CompiledFace currentFace : compiledFaces) {
            for (int i = 0; i < 3; i++) {
                CompiledVertex currentVertex = currentFace.vertices[i];
                {
                    uniqueVertices.add(currentVertex);
                    positions.add(currentVertex.position);
                    texCoords.add(currentVertex.texCoords);
                    normals.add(currentVertex.normal);
                    lightmapTexCoords.add(currentVertex.lightmapCoords);
                    indexBufferValues.add(uniqueVertices.size() - 1);
                }
            }
        }

        putToValueArrays();
        calculateMinMax(null, minMax.getMin(), minMax.getMax(), compiledFaces);
    }

    public void putToValueArrays() {
        vertexBufferValues.clear();
        vertexBufferValuesArray = new float[compiledFaces.size() * 3 * valuesPerVertex];
        for (CompiledFace currentFace : compiledFaces) {
            for (int i = 0; i < 3; i++) {
                vertexBufferValues.add(currentFace.vertices[i].asFloats());
            }
        }
        vertexBufferValuesArray = vertexBufferValues.toArray();

        indexBufferValuesArray = new int[indexBufferValues.size()];
        for (int indexIndex = 0; indexIndex < indexBufferValues.size(); indexIndex++) {
            indexBufferValuesArray[indexIndex] = indexBufferValues.get(indexIndex);
        }
    }

    @Override
    public AABB getMinMax(Transform transform) {
        if(!isClean(transform)) {
            calculateMinMax(transform, minMax.getMin(), minMax.getMax(), compiledFaces);
        }
        return super.getMinMaxWorld(IDENTITY);
    }

    @Override
    public Vector3f getCenter(Entity transform) {
        return super.getCenterWorld(transform);
    }

    private Vector3f[] getLightMapCoords(Vector3f[] referencedVerticesAsVec3) {
        Vector3f[] result = new Vector3f[3];
        result[0] = new Vector3f();
        result[1] = new Vector3f();
        result[2] = new Vector3f();
        Vector3f poly_normal = Face.calculateFaceNormal(referencedVerticesAsVec3[0], referencedVerticesAsVec3[1], referencedVerticesAsVec3[2]);
        if (Math.abs(poly_normal.x) > Math.abs(poly_normal.y) && Math.abs(poly_normal.x) > Math.abs(poly_normal.z)) {
            int negativePlane = poly_normal.x < 0 ? 1 : 0;
            result[0].x = referencedVerticesAsVec3[0].y;
            result[0].y = referencedVerticesAsVec3[0].z;
            result[0].z = negativePlane;
            result[1].x = referencedVerticesAsVec3[1].y;
            result[1].y = referencedVerticesAsVec3[1].z;
            result[1].z = negativePlane;
            result[2].x = referencedVerticesAsVec3[2].y;
            result[2].y = referencedVerticesAsVec3[2].z;
            result[2].z = negativePlane;
        } else if (Math.abs(poly_normal.y) > Math.abs(poly_normal.x) && Math.abs(poly_normal.y) > Math.abs(poly_normal.z)) {
            int negativePlane = poly_normal.y < 0 ? 3 : 2;
            result[0].x = referencedVerticesAsVec3[0].x;
            result[0].y = referencedVerticesAsVec3[0].z;
            result[0].z = negativePlane;
            result[1].x = referencedVerticesAsVec3[1].x;
            result[1].y = referencedVerticesAsVec3[1].z;
            result[1].z = negativePlane;
            result[2].x = referencedVerticesAsVec3[2].x;
            result[2].y = referencedVerticesAsVec3[2].z;
            result[2].z = negativePlane;
        } else {
            int negativePlane = poly_normal.z < 0 ? 5 : 4;
            result[0].x = referencedVerticesAsVec3[0].x;
            result[0].y = referencedVerticesAsVec3[0].y;
            result[0].z = negativePlane;
            result[1].x = referencedVerticesAsVec3[1].x;
            result[1].y = referencedVerticesAsVec3[1].y;
            result[1].z = negativePlane;
            result[2].x = referencedVerticesAsVec3[2].x;
            result[2].y = referencedVerticesAsVec3[2].y;
            result[2].z = negativePlane;
        }

        return result;
    }

    @Override
    public int[] getIndexBufferValuesArray() {
        return indexBufferValuesArray;
    }

    public static void calculateMinMax(Matrix4f modelMatrix, Vector3f min, Vector3f max, List<CompiledFace> compiledFaces) {
        min.set(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);

        for (int i = 0; i < compiledFaces.size(); i++) {
            CompiledFace face = compiledFaces.get(i);

            CompiledVertex[] vertices = face.vertices;

            for (int j = 0; j < 3; j++) {
                Vector3f positionV3 = vertices[j].position;
                Vector4f position = new Vector4f(positionV3.x, positionV3.y, positionV3.z, 1);
                if(modelMatrix != null) {
                    position.mul(modelMatrix);
                }

                min.x = position.x < min.x ? position.x : min.x;
                min.y = position.y < min.y ? position.y : min.y;
                min.z = position.z < min.z ? position.z : min.z;

                max.x = position.x > max.x ? position.x : max.x;
                max.y = position.y > max.y ? position.y : max.y;
                max.z = position.z > max.z ? position.z : max.z;
            }
        }

    }
//TODO: Move this away from here
    public static void calculateMinMax(Vector3f targetMin, Vector3f targetMax, AABB candidate) {
        targetMin.x = candidate.getMin().x < targetMin.x ? candidate.getMin().x : targetMin.x;
        targetMin.y = candidate.getMin().y < targetMin.y ? candidate.getMin().y : targetMin.y;
        targetMin.z = candidate.getMin().z < targetMin.z ? candidate.getMin().z : targetMin.z;

        targetMax.x = candidate.getMax().x > targetMax.x ? candidate.getMax().x : targetMax.x;
        targetMax.y = candidate.getMax().y > targetMax.y ? candidate.getMax().y : targetMax.y;
        targetMax.z = candidate.getMax().z > targetMax.z ? candidate.getMax().z : targetMax.z;
    }
    public static void calculateMin(Vector3f old, Vector3f candidate) {
        old.x = candidate.x < old.x ? candidate.x : old.x;
        old.y = candidate.y < old.y ? candidate.y : old.y;
        old.z = candidate.z < old.z ? candidate.z : old.z;
    }
    public static void calculateMax(Vector3f old, Vector3f candidate) {
        old.x = candidate.x > old.x ? candidate.x : old.x;
        old.y = candidate.y > old.y ? candidate.y : old.y;
        old.z = candidate.z > old.z ? candidate.z : old.z;
    }

    public static void calculateMinMax(Matrix4f transform, Vector3f min, Vector3f max, AABB current) {
        current.setMin(transform.transformPosition(current.getMin()));
        current.setMax(transform.transformPosition(current.getMax()));
        min.x = current.getMin().x < min.x ? current.getMin().x : min.x;
        min.y = current.getMin().y < min.y ? current.getMin().y : min.y;
        min.z = current.getMin().z < min.z ? current.getMin().z : min.z;

        max.x = current.getMax().x > max.x ? current.getMax().x : max.x;
        max.y = current.getMax().y > max.y ? current.getMax().y : max.y;
        max.z = current.getMax().z > max.z ? current.getMax().z : max.z;
    }

    public static float getBoundingSphereRadius(Vector3f target, Vector3f min, Vector3f max) {
        return target.set(max).sub(min).mul(0.5f).length();
    }
    public static float getBoundingSphereRadius(Vector3f target, Vector4f min, Vector4f max) {
        return getBoundingSphereRadius(target, new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, max.y, max.z));
    }

    public List<Face> getIndexedFaces() {
        return indexFaces;
    }

    @Override
    public int getTriangleCount() {
        return indexBufferValues.size()/3;
    }

    @Override
    public Vector3f getCenterWorld(Transform transform) {
        if(!isClean(transform)) {
            calculateMinMax(transform, minMax.getMin(), minMax.getMax(), compiledFaces);
        }
        return super.getCenterWorld(transform);
    }

    @Override
    public AABB getMinMaxWorld(Transform transform) {
        return getMinMax(transform);
    }

    @Override
    public float getBoundingSphereRadius(Transform transform) {
        if(!isClean(transform)) {
            calculateMinMax(transform, minMax.getMin(), minMax.getMax(), compiledFaces);
        }
        return super.getBoundingSphereRadius(transform);
    }

    public UUID getUuid() {
        return uuid;
    }

    public static class CompiledVertex {
        public final Vector3f position;
        public final Vector2f texCoords;
        public final Vector3f normal;
        public final Vector3f initialLightmapCoords;
        public Vector3f lightmapCoords;

        public CompiledVertex(Vector3f position, Vector2f texCoords, Vector3f normal, Vector3f initialLightmapCoords, Vector3f lightmapCoords) {
            this.position = position;
            this.texCoords = texCoords;
            this.normal = normal;
            this.initialLightmapCoords = initialLightmapCoords;
            this.lightmapCoords = lightmapCoords;
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof  CompiledVertex)) {
                return false;
            }

            CompiledVertex otherVertex = (CompiledVertex) other;
            return this.position.equals(otherVertex.position) &&
                this.texCoords.equals(otherVertex.texCoords) &&
                this.normal.equals(otherVertex.normal) &&
                this.lightmapCoords.equals(otherVertex.lightmapCoords);
        }

        public float[] asFloats() {
            return new float[] {position.x, position.y, position.z, texCoords.x, texCoords.y, normal.x, normal.y, normal.z, lightmapCoords.x, lightmapCoords.y, lightmapCoords.z};
        }
    }
    public static class CompiledFace {

        public final CompiledVertex[] vertices = new CompiledVertex[3];
        private final Vector3f[] positions;

        public CompiledFace(Vector3f[] position, Vector2f[] texCoords, Vector3f[] normal, Vector3f[] allLightMapCoords, Vector3f[] lightmapCoords) {
            for(int i = 0; i < 3; i++) {
                vertices[i] = new CompiledVertex(position[i], texCoords[i], normal[i], allLightMapCoords[i], lightmapCoords[i]);
            }
            positions = position;
        }

        @Override
        public boolean equals(Object other) {
            if(other instanceof CompiledFace) {
                CompiledFace otherFace = (CompiledFace) other;
                return Arrays.equals(this.vertices, otherFace.vertices);
            }
            return false;
        }

        public Vector3f[] getPositions() {
            return positions;
        }
    }

    @Override
    public IntArrayList getIndexBufferValues() {
        return indexBufferValues;
    }

    public boolean equals(Object other) {
        if (!(other instanceof StaticMesh)) {
            return false;
        }

        StaticMesh b = (StaticMesh) other;

        return b.getUuid().equals(uuid);
    }

    @Override
    public int hashCode() {
        return getUuid().hashCode();
    }

    @Override
    public List<Vertex> getCompiledVertices() {
        return compiledVertices;
    }

    @Override
    public AABB getMinMax() {
        return minMax;
    }
}
