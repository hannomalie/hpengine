package engine;

import camera.Camera;
import engine.model.Entity;
import shader.Program;

import java.nio.FloatBuffer;

public interface Drawable {

    default void draw(Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex) { draw(cameraEntity, modelMatrix, entityIndex, true); }
    default void draw(Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, boolean isVisible) { draw(cameraEntity, modelMatrix, entityIndex, isVisible, false); }
    default void draw(Camera cameraEntity, FloatBuffer modelMatrix, int entityIndex, boolean isVisible, boolean isSelected) { draw(cameraEntity, modelMatrix, AppContext.getInstance().getRenderer().getProgramFactory().getFirstpassDefaultProgram(), entityIndex, isVisible, isSelected);}
    default void draw(Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex) { draw(cameraEntity, modelMatrix, firstPassProgram, entityIndex, true, false); }
    void draw(Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected);
    void draw(Camera cameraEntity, FloatBuffer modelMatrix, Program firstPassProgram);
    void draw(Camera cameraEntity);

    void drawDebug(Program program, FloatBuffer modelMatrix);
}
