package engine;

import camera.Camera;
import engine.model.Entity;
import engine.model.VertexBuffer;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.material.Material;
import shader.Program;

import java.nio.FloatBuffer;

public class PerEntityInfo {
    private final Camera camera;
    private final FloatBuffer modelMatrix;
    private final Program program;
    private final int entityIndex;
    private final int entityBaseIndex;
    private final boolean isVisible;
    private final boolean isSelected;
    private final boolean drawLines;
    private final Vector4f minWorld;
    private final Vector4f maxWorld;
    private Vector3f cameraWorldPosition;
    private Material material;
    private boolean isInReachForTextureStreaming;
    private VertexBuffer vertexBuffer;
    private int instanceCount;
    private boolean visibleForCamera;
    private Entity.Update update;

    public PerEntityInfo(Camera camera, FloatBuffer modelMatrix, Program program, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, Material material, boolean isInReachForTextureStreaming, VertexBuffer vertexBuffer, int instanceCount, boolean visibleForCamera, Entity.Update update, Vector4f minWorld, Vector4f maxWorld) {
        this.camera = camera;
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
    }

    public Camera getCamera() {
        return camera;
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
}
