package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.component.ModelComponent;
import org.apache.commons.lang.NotImplementedException;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.scene.LightmapManager;
import de.hanno.hpengine.util.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Model implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private List<Vector3f> vertices = new ArrayList<>();
    private List<Vector2f> texCoords = new ArrayList<>();
    private List<Vector3f> normals = new ArrayList<>();
    private List<Vector3f> lightmapTexCoords = new ArrayList<>();
    private List<Face> faces = new ArrayList<>();
	private String name = "";
	private Material material;
    private List<Integer> indexBufferValues;
    private List<Float[]> vertexBufferValues;
    private int[] indexBufferValuesArray;

    public float[] getVertexBufferValuesArray() {
        return vertexBufferValuesArray;
    }

    private float[] vertexBufferValuesArray;

    public boolean hasTextureCoordinates() {
        return getTexCoords().size() > 0;
    }

    public boolean hasNormals() {
        return getNormals().size() > 0;
    }

    public List<Vector3f> getVertices() {
        return vertices;
    }

    public List<Vector2f> getTexCoords() {
        return texCoords;
    }

    public List<Vector3f> getNormals() {
        return normals;
    }

    public List<Vector3f> getLightmapTexCoords() {
        return lightmapTexCoords;
    }

    public List<Face> getFaces() {
        return faces;
    }

	public void setName(String name) {
		this.name  = name;
	}

	public void setVertices(ArrayList<Vector3f> vertices) {
		this.vertices = vertices;
	}

	public void setNormals(ArrayList<Vector3f> normals) {
		this.normals = normals;
	}

	public void setTexCoords(ArrayList<Vector2f> texCoords) {
		this.texCoords = texCoords;
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


    private transient Vector3f min;
    private transient Vector3f max;
    private transient Matrix4f lastUsedModelMatrix = null;
    private float boundSphereRadius = -1;

    public Model(String name, List<Vector3f> vertices, List<Vector2f> texCoords, List<Vector3f> normals) {
        this.name = name;
        this.vertices = vertices;
        this.texCoords = texCoords;
        this.normals = normals;
    }

    public void init() {

        List<Float> values = new ArrayList<>(faces.size() * 5);
        List<Vector3f[]> allLightMapCoords = new ArrayList<>(faces.size());

        for(Face face : faces) {
            int[] referencedVertices = face.getVertices();
            Vector3f[] referencedVerticesAsVec3 = new Vector3f[3];
            referencedVerticesAsVec3[0] = vertices.get(referencedVertices[0]-1);
            referencedVerticesAsVec3[1] = vertices.get(referencedVertices[1]-1);
            referencedVerticesAsVec3[2] = vertices.get(referencedVertices[2]-1);
            Vector3f[] lightmapCoords = getLightMapCoords(referencedVerticesAsVec3);

            allLightMapCoords.add(lightmapCoords);
        }

        List<Vector3f[]> finalLightmapCoords = LightmapManager.getInstance().add(allLightMapCoords);
        for(Vector3f[] face : finalLightmapCoords) {
            lightmapTexCoords.add(face[0]);
            lightmapTexCoords.add(face[1]);
            lightmapTexCoords.add(face[2]);
        }

        for(int i = 0; i < faces.size(); i++) {
            Face face = faces.get(i);

            int[] referencedVertices = face.getVertices();
            int[] referencedNormals = face.getNormalIndices();
            int[] referencedTexcoords = face.getTextureCoordinateIndices();
            Vector3f[] referencedVerticesAsVec3 = new Vector3f[3];
            referencedVerticesAsVec3[0] = vertices.get(referencedVertices[0]-1);
            referencedVerticesAsVec3[1] = vertices.get(referencedVertices[1]-1);
            referencedVerticesAsVec3[2] = vertices.get(referencedVertices[2]-1);

            for (int j = 0; j < 3; j++) {
                Vector3f referencedVertex = vertices.get(referencedVertices[j]-1);
                Vector2f referencedTexcoord = new Vector2f(0,0);
                try {
                    referencedTexcoord = texCoords.get(referencedTexcoords[j]-1);
                } catch (Exception e) {

                }
                Vector3f referencedNormal = normals.get(referencedNormals[j]-1);

                values.add(referencedVertex.x);
                values.add(referencedVertex.y);
                values.add(referencedVertex.z);
                values.add(referencedTexcoord.x);
                values.add(referencedTexcoord.y);
                values.add(referencedNormal.x);
                values.add(referencedNormal.y);
                values.add(referencedNormal.z);

                values.add(finalLightmapCoords.get(i)[j].getX());
                values.add(finalLightmapCoords.get(i)[j].getY());
                values.add(finalLightmapCoords.get(i)[j].getZ());

                if(ModelComponent.USE_PRECOMPUTED_TANGENTSPACE) {
                    throw new NotImplementedException("Implement former logic from ModelComponent here");
                }
            }

        }

        int valuesPerVertex = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS);//ModelComponent.USE_PRECOMPUTED_TANGENTSPACE ? 14 : 8;
        vertexBufferValues = new ArrayList<>();
        vertices = new ArrayList<>();
        texCoords = new ArrayList<>();
        normals = new ArrayList<>();

        indexBufferValues = new ArrayList<>();
        for(int i = 0; i < values.size(); i+=valuesPerVertex) {
            Float[] vertexValues = new Float[valuesPerVertex];
            for(int arrayIndex = 0; arrayIndex < valuesPerVertex; arrayIndex++) {
                vertexValues[arrayIndex] = values.get(i+arrayIndex);
            }

            boolean containsVertex = false;
            int indexOfAlreadyContainedVertex = -1;
            for(int z = 0; z < vertexBufferValues.size(); z++) {
                Float[] currentVertex = vertexBufferValues.get(z);
                boolean currentVertexEquals = Arrays.equals(vertexValues, currentVertex);
                if(currentVertexEquals) {
                    containsVertex = true;
                    indexOfAlreadyContainedVertex = z;
                    break;
                }
            }
            if(containsVertex) {
                indexBufferValues.add(indexOfAlreadyContainedVertex);
            } else {
                vertexBufferValues.add(vertexValues);
                vertices.add(new Vector3f(vertexValues[0],vertexValues[1],vertexValues[2]));
                texCoords.add(new Vector2f(vertexValues[3],vertexValues[4]));
                normals.add(new Vector3f(vertexValues[5],vertexValues[6],vertexValues[7]));
                indexBufferValues.add(vertexBufferValues.size()-1);
            }
        }

        faces.clear();
        for(int i = 0; i < indexBufferValues.size(); i+=3) {
            int[] currentIndices = new int[3];
            currentIndices[0] = indexBufferValues.get(i)+1;
            currentIndices[1] = indexBufferValues.get(i+1)+1;
            currentIndices[2] = indexBufferValues.get(i+2)+1;
            faces.add(new Face(currentIndices, currentIndices, currentIndices));
        }

        vertexBufferValuesArray = new float[vertexBufferValues.size()*valuesPerVertex];
        for(int vertexIndex = 0; vertexIndex < vertexBufferValues.size(); vertexIndex++) {
            Float[] currentVertexValues = vertexBufferValues.get(vertexIndex);
            for(int attributeIndex = 0; attributeIndex < currentVertexValues.length; attributeIndex++) {
                vertexBufferValuesArray[vertexIndex*currentVertexValues.length+attributeIndex] = currentVertexValues[attributeIndex];
            }
        }

        indexBufferValuesArray = new int[indexBufferValues.size()];
        for(int indexIndex = 0; indexIndex < indexBufferValues.size(); indexIndex++) {
            indexBufferValuesArray[indexIndex] = indexBufferValues.get(indexIndex);
        }
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

    public Vector4f[] getMinMax() {
        return getMinMax(null);
    }

    public float getBoundingSphereRadius() {
        if(boundSphereRadius == -1) {
            getMinMax();
        }
        return boundSphereRadius;
    }

    private transient Vector3f center;
    public Vector3f getCenter() {
        if(center == null) {
            getMinMax();
            center = (Vector3f) Vector3f.add(min, Vector3f.sub(max, min, null), null).scale(0.5f);
        }
        return new Vector3f(center);
    }
    public void setCenter(Vector3f center) {
        this.center = center;
    }

    public int[] getIndexBufferValuesArray() {
        return indexBufferValuesArray;
    }

    public Vector4f[] getMinMax(Matrix4f modelMatrix) {
        if(!(lastUsedModelMatrix == null && modelMatrix == null) || !Util.equals(lastUsedModelMatrix, modelMatrix))
        {
            min = new Vector3f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
            max = new Vector3f(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);

            for (int i = 0; i < faces.size(); i++) {
                Face face = faces.get(i);

                int[] referencedVertices = face.getVertices();

                for (int j = 0; j < 3; j++) {
                    Vector3f positionV3 = getVertices().get(referencedVertices[j] - 1);
                    Vector4f position = new Vector4f(positionV3.x, positionV3.y, positionV3.z, 1);
                    if(modelMatrix != null) {
                        Matrix4f.transform(modelMatrix, position, position);
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

        boundSphereRadius = getBoundingSphereRadius(min, max);
        lastUsedModelMatrix = modelMatrix;

        return new Vector4f[] {new Vector4f(min.x, min.y, min.z, 1), new Vector4f(max.x, max.y, max.z, 1)};
    }

    public static float getBoundingSphereRadius(Vector3f min, Vector3f max) {
        return (Vector3f.sub(max, min, null).scale(0.5f)).length();
    }
    public static float getBoundingSphereRadius(Vector4f min, Vector4f max) {
        return getBoundingSphereRadius(new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, max.y, max.z));
    }
}
