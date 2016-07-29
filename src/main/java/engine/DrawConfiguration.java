package engine;

import camera.Camera;
import renderer.RenderExtract;
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

    public DrawConfiguration(RenderExtract extract, Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean drawLines) {
        this.extract = extract;
        this.camera = camera;
        this.modelMatrix = modelMatrix;
        this.firstPassProgram = firstPassProgram;
        this.entityIndex = entityIndex;
        this.entityBaseIndex = entityBaseIndex;
        this.isVisible = isVisible;
        this.isSelected = isSelected;
        this.drawLines = drawLines;
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
}
