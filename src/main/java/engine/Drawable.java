package engine;

import camera.Camera;
import renderer.RenderExtract;
import shader.Program;
import shader.ProgramFactory;

import java.nio.FloatBuffer;

public interface Drawable {

    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, int entityBaseIndex ) { draw(extract, cameraEntity, modelMatrix, entityIndex, entityBaseIndex, true); }
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, int entityBaseIndex, boolean isVisible) { draw(extract, cameraEntity, modelMatrix, entityIndex, entityBaseIndex, isVisible, false); }
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected) { draw(extract, cameraEntity, modelMatrix, ProgramFactory.getInstance().getFirstpassDefaultProgram(), entityIndex, entityBaseIndex, isVisible, isSelected);}
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected, boolean linesOnly) { draw(extract, cameraEntity, modelMatrix, ProgramFactory.getInstance().getFirstpassDefaultProgram(), entityIndex, entityBaseIndex, isVisible, isSelected);}
    default void draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, int entityBaseIndex) { draw(new DrawConfiguration(extract, cameraEntity, modelMatrix, firstPassProgram, entityIndex, entityBaseIndex, true, false, false, cameraEntity.getWorldPosition())); }
    int draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, int entityBaseIndex, boolean isVisible, boolean isSelected);
    int draw(RenderExtract extract, Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram);
    int draw(RenderExtract extract, Camera cameraEntity);

    int draw(DrawConfiguration drawConfiguration);

    int drawDebug(Program program, FloatBuffer modelMatrix);
}
