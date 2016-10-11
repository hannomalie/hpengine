package engine;

import engine.model.Entity;
import engine.model.VertexBuffer;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.material.Material;
import shader.Program;

import java.nio.FloatBuffer;

public class PerEntityInfo {
    private FloatBuffer modelMatrix;
    private Program program;
    private int entityIndex;
    private int entityBaseIndex;
    private boolean isVisible;
    private boolean isSelected;
    private boolean drawLines;
    private Vector4f minWorld;
    private Vector4f maxWorld;
    private Vector3f cameraWorldPosition;
    private Material material;
    private int baseVertex;
    private boolean isInReachForTextureStreaming;
    private VertexBuffer vertexBuffer;
    private int instanceCount;
    private boolean visibleForCamera;
    private Entity.Update update;
    private int indexCount;
    private int indexOffset;

    public PerEntityInfo(FloatBuffer modelMatrix, Program program, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, Material material, boolean isInReachForTextureStreaming, VertexBuffer vertexBuffer, int instanceCount, boolean visibleForCamera, Entity.Update update, Vector4f minWorld, Vector4f maxWorld, int indexCount, int indexOffset, int baseVertex) {
        init(modelMatrix, program, entityIndex, entityBaseIndex, isVisible, isSelected, drawLines, cameraWorldPosition, material, isInReachForTextureStreaming, vertexBuffer, instanceCount, visibleForCamera, update, minWorld, maxWorld, indexCount, indexOffset, baseVertex);
    }

    public void init(FloatBuffer modelMatrix, Program program, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, Material material, boolean isInReachForTextureStreaming, VertexBuffer vertexBuffer, int instanceCount, boolean visibleForCamera, Entity.Update update, Vector4f minWorld, Vector4f maxWorld, int indexCount, int indexOffset, int baseVertex) {
        this.modelMatrix = modelMatrix;
        this.program = program;
        this.entityIndex = entityIndex;
        this.entityBaseIndex = entityBaseIndex;
        this.isVisible = isVisible;
        this.isSelected = isSelected;
        this.drawLines = drawLines;
        this.cameraWorldPosition = cameraWorldPosition;
        this.material = material;
        this.vertexBuffer = vertexBuffer;
        this.instanceCount = instanceCount;
        this.isInReachForTextureStreaming = isInReachForTextureStreaming;
        this.visibleForCamera = visibleForCamera;
        this.update = update;
        this.minWorld = minWorld;
        this.maxWorld = maxWorld;
        this.indexCount = indexCount;
        this.indexOffset = indexOffset;
        this.baseVertex = baseVertex;
    }

    public FloatBuffer getModelMatrix() {
        return modelMatrix;
    }

    public Program getProgram() {
        return program;
    }

    public int getEntityIndex() {
        return entityIndex;
    }

    public int getEntityBaseIndex() {
        return entityBaseIndex;
    }

    public boolean isVisible() {
        return isVisible;
    }
    public boolean isVisibleForCamera() {
        return visibleForCamera;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public boolean isDrawLines() {
        return drawLines;
    }

    public Vector3f getCameraWorldPosition() {
        return cameraWorldPosition;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isInReachForTextureLoading() {
        return isInReachForTextureStreaming;
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public Entity.Update getUpdate() {
        return update;
    }

    public Vector4f getMinWorld() {
        return minWorld;
    }

    public Vector4f getMaxWorld() {
        return maxWorld;
    }

    public boolean isInReachForTextureStreaming() {
        return isInReachForTextureStreaming;
    }


    public int getIndexCount() {
        return indexCount;
    }

    public int getIndexOffset() {
        return indexOffset;
    }

    public int getBaseVertex() {
        return baseVertex;
    }
}
