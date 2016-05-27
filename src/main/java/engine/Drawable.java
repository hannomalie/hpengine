package engine;

import camera.Camera;
import engine.model.Entity;
import renderer.RenderExtract;
import renderer.drawstrategy.extensions.RenderExtension;
import shader.Program;
import shader.ProgramFactory;

import java.nio.FloatBuffer;

public interface Drawable {

    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex) { draw(extract, cameraEntity, modelMatrix, entityIndex, true); }
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, boolean isVisible) { draw(extract, cameraEntity, modelMatrix, entityIndex, isVisible, false); }
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, boolean isVisible, boolean isSelected) { draw(extract, cameraEntity, modelMatrix, ProgramFactory.getInstance().getFirstpassDefaultProgram(), entityIndex, isVisible, isSelected);}
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, boolean isVisible, boolean isSelected, boolean linesOnly) { draw(extract, cameraEntity, modelMatrix, ProgramFactory.getInstance().getFirstpassDefaultProgram(), entityIndex, isVisible, isSelected);}
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex) { draw(extract, cameraEntity, modelMatrix, firstPassProgram, entityIndex, true, false); }
    int draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected);
    int draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram);
    int draw(RenderExtract extract, Camera cameraEntity);

    int draw(RenderExtract extract, Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected, boolean drawLines);

    int drawDebug(Program program, FloatBuffer modelMatrix);
}
