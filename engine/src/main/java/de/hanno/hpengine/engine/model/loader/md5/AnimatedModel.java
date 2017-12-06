package de.hanno.hpengine.engine.model.loader.md5;

import com.carrotsearch.hppc.FloatArrayList;
import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.model.AbstractModel;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.model.loader.md5.MD5Mesh.VALUES_PER_VERTEX;

public class AnimatedModel extends AbstractModel<AnimatedVertex> {

    private List<AnimatedFrame> frames;
    private MD5BoundInfo boundInfo;
    private MD5AnimHeader header;
    private List<Matrix4f> invJointMatrices;
    private boolean invertTexCoordy;

    public AnimatedModel(MD5Mesh[] meshes, List<AnimatedFrame> frames, MD5BoundInfo boundInfo, MD5AnimHeader header, List<Matrix4f> invJointMatrices, boolean invertTexCoordY) {
        super(Arrays.asList(meshes));
        this.frames = frames;
        this.boundInfo = boundInfo;
        this.header = header;
        this.invJointMatrices = invJointMatrices;
        for(MD5Mesh mesh : meshes) {
            mesh.setModel(this);
        }
        this.invertTexCoordy = invertTexCoordY;
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
        for (Mesh<AnimatedVertex> mesh : meshes) {
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

    @Override
    public Vector3f[] getMinMax(Transform transform, Mesh mesh, AnimationController animationController) {
        return getCurrentBoundInfo(animationController.getCurrentFrameIndex()).getMinMaxWorld(transform);
    }
    @Override
    public Vector3f[] getMinMax(Mesh mesh, AnimationController animationController) {
        return getCurrentBoundInfo(animationController.getCurrentFrameIndex()).getMinMax();
    }

    public MD5BoundInfo.MD5Bound getCurrentBoundInfo(int frame) {
        return boundInfo.getBounds().get(frame);
    }

    public MD5AnimHeader getHeader() {
        return header;
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

    @Override
    public boolean isInvertTexCoordY() {
        return invertTexCoordy;
    }
}
