package de.hanno.hpengine.engine.model.loader.md5;

import de.hanno.hpengine.engine.model.Mesh;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GameItem {

    private List<Mesh> meshes;

    private final Vector3f position;

    private float scale;

    private final Quaternionf rotation;

    private int textPos;
            
    public GameItem() {
        position = new Vector3f(0, 0, 0);
        scale = 1;
        rotation = new Quaternionf();
        textPos = 0;
    }

    public GameItem(Mesh mesh) {
        this();
        this.meshes = Arrays.asList(mesh);
    }

    public GameItem(Mesh[] meshes) {
        this();
        this.meshes = Arrays.asList(meshes);
    }

    public Vector3f getPosition() {
        return position;
    }

    public int getTextPos() {
        return textPos;
    }

    public final void setPosition(float x, float y, float z) {
        this.position.x = x;
        this.position.y = y;
        this.position.z = z;
    }

    public float getScale() {
        return scale;
    }

    public final void setScale(float scale) {
        this.scale = scale;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public final void setRotation(Quaternionf q) {
        this.rotation.set(q);
    }

    public Mesh getMesh() {
        return meshes.get(0);
    }
    
    public List<Mesh> getMeshes() {
        return meshes;
    }

    public void setMeshes(List<Mesh> meshes) {
        this.meshes = meshes;
    }

    public void setMesh(Mesh mesh) {
        this.meshes = Arrays.asList(mesh);
    }
    
    public void setTextPos(int textPos) {
        this.textPos = textPos;
    }
}
