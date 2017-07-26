package de.hanno.hpengine.engine.model.loader.md5;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.Vertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.model.loader.md5.MD5Mesh.VALUES_PER_VERTEX;

public class AnimatedModel implements Model {

    private List<Mesh> meshes;

    private int currentFrame;
    
    private List<AnimatedFrame> frames;

    private List<Matrix4f> invJointMatrices;
    private Material material;

    public AnimatedModel(Mesh[] meshes, List<AnimatedFrame> frames, List<Matrix4f> invJointMatrices) {
        this.frames = frames;
        this.invJointMatrices = invJointMatrices;
        currentFrame = 0;
        this.meshes = Arrays.asList(meshes);
    }

    public List<AnimatedFrame> getFrames() {
        return frames;
    }

    public void setFrames(List<AnimatedFrame> frames) {
        this.frames = frames;
    }
    
    public AnimatedFrame getCurrentFrame() {
        return this.frames.get(currentFrame);
    }
    
    public AnimatedFrame getNextFrame() {
        int nextFrame = currentFrame + 1;    
        if ( nextFrame > frames.size() - 1) {
            nextFrame = 0;
        }
        return this.frames.get(nextFrame);
    }

    public void nextFrame() {
        int nextFrame = currentFrame + 1;    
        if ( nextFrame > frames.size() - 1) {
            currentFrame = 0;
        } else {
            currentFrame = nextFrame;
        }
    }    

    public List<Matrix4f> getInvJointMatrices() {
        return invJointMatrices;
    }


    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    @Override
    public List<Mesh> getMeshes() {
        return meshes;
    }

    @Override
    public float getBoundingSphereRadius() {
        return 0;
    }

    @Override
    public int getTriangleCount() {
        return 0;
    }

    @Override
    public float[] getVertexBufferValuesArray() {
        FloatArrayList floatList = new FloatArrayList();
        for(Mesh mesh : getMeshes()) {
            floatList.add(mesh.getVertexBufferValuesArray());
        }
        return floatList.toArray();
    }

    @Override
    public int[] getIndices() {
        IntArrayList intList = new IntArrayList();
        int currentIndexOffset = 0;
        for(Mesh mesh : getMeshes()) {
            int[] indexBufferValuesArray = Arrays.copyOf(mesh.getIndexBufferValuesArray(), mesh.getIndexBufferValuesArray().length);
            for(int i = 0; i < indexBufferValuesArray.length; i++) {
                indexBufferValuesArray[i] += currentIndexOffset;
            }
            int vertexCount = mesh.getVertexBufferValuesArray().length / VALUES_PER_VERTEX;
            currentIndexOffset += vertexCount;
            intList.add(indexBufferValuesArray);
        }
        return intList.toArray();
    }

    @Override
    public Vector4f[] getMinMax() {
        return minMax;
    }

    @Override
    public IntArrayList[] getMeshIndices() {
        List<IntArrayList> list = getMeshes().stream().map(mesh -> mesh.getIndexBufferValues()).collect(Collectors.toList());
        IntArrayList[] target = new IntArrayList[list.size()];
        list.toArray(target);
        return target;
    }

    @Override
    public void putToValueArrays() {

    }

    private static final Vector4f absoluteMaximum = new Vector4f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
    private static final Vector4f absoluteMinimum = new Vector4f(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
    private Vector4f min = new Vector4f(absoluteMinimum);
    private Vector4f max = new Vector4f(absoluteMaximum);
    private Vector4f[] minMax = new Vector4f[]{min, max};
    private Vector3f[] minMaxVec3 = new Vector3f[]{new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, max.y, max.z)};
    @Override
    public Vector3f[] getMinMax(Transform transform) {
        return minMaxVec3;
    }

    @Override
    public List<Vertex> getCompiledVertices() {
        List<Vertex> vertexList = new ArrayList<>();
        for(Mesh mesh : getMeshes()) {
            vertexList.addAll(mesh.getCompiledVertices());
        }
        return vertexList;
    }
}
