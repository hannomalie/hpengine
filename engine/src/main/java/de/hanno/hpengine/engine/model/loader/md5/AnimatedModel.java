package de.hanno.hpengine.engine.model.loader.md5;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.model.AbstractModel;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.model.loader.md5.MD5Mesh.VALUES_PER_VERTEX;

public class AnimatedModel extends AbstractModel {

    private List<Mesh<AnimatedVertex>> meshes;
    private List<AnimatedFrame> frames;
    private MD5BoundInfo boundInfo;
    private List<Matrix4f> invJointMatrices;

    public AnimatedModel(MD5Mesh[] meshes, List<AnimatedFrame> frames, MD5BoundInfo boundInfo, List<Matrix4f> invJointMatrices) {
        this.frames = frames;
        this.boundInfo = boundInfo;
        this.invJointMatrices = invJointMatrices;
        this.meshes = Arrays.asList(meshes);
        for(MD5Mesh mesh : meshes) {
            mesh.setModel(this);
        }
    }

    public List<AnimatedFrame> getFrames() {
        return frames;
    }

    public void setFrames(List<AnimatedFrame> frames) {
        this.frames = frames;
    }
    
    public List<Matrix4f> getInvJointMatrices() {
        return invJointMatrices;
    }


    @Override
    public void setMaterial(Material material) {
        for (Mesh mesh : meshes) {
            mesh.setMaterial(material);
        }
    }

    @Override
    public List<Mesh<AnimatedVertex>> getMeshes() {
        return meshes;
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
        return new Vector4f[0];
    }

    @Override
    public IntArrayList[] getMeshIndices() {
        List<IntArrayList> list = getMeshes().stream().map(mesh -> mesh.getIndexBufferValues()).collect(Collectors.toList());
        IntArrayList[] target = new IntArrayList[list.size()];
        list.toArray(target);
        return target;
    }

    @Override
    public List<AnimatedVertex> getCompiledVertices() {
        List<AnimatedVertex> vertexList = new ArrayList<>();
        for(Mesh<AnimatedVertex> mesh : getMeshes()) {
            vertexList.addAll(mesh.getCompiledVertices());
        }
        return vertexList;
    }

    @Override
    public boolean isStatic() {return false;}

    @Override
    public float getBoundingSphereRadius(Mesh mesh, AnimationController controller) {
        return getCurrentBoundInfo(controller.getCurrentFrameIndex()).getBoundingSphereRadius();
    }

    public MD5BoundInfo.MD5Bound getCurrentBoundInfo(int frame) {
        return boundInfo.getBounds().get(frame);
    }
}
