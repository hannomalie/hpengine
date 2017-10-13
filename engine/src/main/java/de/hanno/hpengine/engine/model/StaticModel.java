package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;

public class StaticModel<T extends Bufferable> extends AbstractModel<T> {

    private ArrayList<Vector3f> vertices = new ArrayList<>();
    private ArrayList<Vector2f> texCoords = new ArrayList<>();
    private ArrayList<Vector3f> normals = new ArrayList<>();

    public StaticModel() {
        super(new ArrayList<>());
    }

    public void addVertex(Vector3f vertex) {
        vertices.add(vertex);
    }

    public void addTexCoords(Vector2f texCoords) {
        this.texCoords.add(texCoords);
    }

    public void addNormal(Vector3f normal) {
        normals.add(normal);
    }

    public ArrayList<Vector3f> getVertices() {
        return vertices;
    }

    public ArrayList<Vector2f> getTexCoords() {
        return texCoords;
    }

    public ArrayList<Vector3f> getNormals() {
        return normals;
    }

    public void init() {
        meshIndices = new IntArrayList[meshes.size()];
        triangleCount = 0;
        for(int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            mesh.init();
            meshIndices[i] = new IntArrayList();
            meshIndices[i].add(mesh.getIndexBufferValuesArray());
            triangleCount += mesh.getTriangleCount();
        }
        super.init();
    }

    public Mesh getMesh(int i) {
        return meshes.get(i);
    }

    public void addMesh(Mesh mesh) {
        meshes.add(mesh);
    }


    @Override
    public Vector3f getCenterWorld(Transform transform) {
        return super.getCenterWorld(transform);
    }

    @Override
    public Vector3f[] getMinMaxWorld(Transform transform) {
        return super.getMinMaxWorld(transform);
    }

    @Override
    public float getBoundingSphereRadius(Transform transform) {
        return super.getBoundingSphereRadius(transform);
    }
}
