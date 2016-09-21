package engine;

import camera.Camera;
import engine.model.VertexBuffer;
import org.lwjgl.util.vector.Vector3f;
import renderer.RenderExtract;
import renderer.material.Material;
import shader.Program;

import java.nio.FloatBuffer;

public class DrawConfiguration {
    private final RenderExtract extract;
    private final Camera camera;
    private final FloatBuffer modelMatrix;
    private final Program firstPassProgram;
    private final int entityIndex;
    private final int entityBaseIndex;
    private final boolean isVisible;
    private final boolean isSelected;
    private final boolean drawLines;
    private Vector3f cameraWorldPosition;
    private Material material;
    private boolean inReachForTextureLoading;
    private VertexBuffer vertexBuffer;
    private int instanceCount;

    public DrawConfiguration(RenderExtract extract, Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines, Vector3f cameraWorldPosition, Material material, boolean isInReachForTextureStreaming, VertexBuffer vertexBuffer, int instanceCount) {
        this.extract = extract;
        this.camera = camera;
        this.modelMatrix = modelMatrix;
        this.firstPassProgram = firstPassProgram;
        this.entityIndex = entityIndex;
        this.entityBaseIndex = entityBaseIndex;
        this.isVisible = isVisible;
        this.isSelected = isSelected;
        this.drawLines = drawLines;
        this.cameraWorldPosition = cameraWorldPosition;
        this.material = material;
        this.vertexBuffer = vertexBuffer;
        this.instanceCount = instanceCount;
    }

    public RenderExtract getExtract() {
        return extract;
    }

    public Camera getCamera() {
        return camera;
    }

    public FloatBuffer getModelMatrix() {
        return modelMatrix;
    }

    public Program getFirstPassProgram() {
        return firstPassProgram;
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
        return inReachForTextureLoading;
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public int getInstanceCount() {
        return instanceCount;
    }
}
