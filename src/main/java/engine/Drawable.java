package engine;

import camera.Camera;
import shader.Program;

import java.nio.FloatBuffer;

public interface Drawable {

    default void draw(Camera camera, FloatBuffer modelMatrix, int entityIndex) { draw(camera, modelMatrix, entityIndex, true); }
    default void draw(Camera camera, FloatBuffer modelMatrix, int entityIndex, boolean isVisible) { draw(camera, modelMatrix, entityIndex, isVisible, false); }
    default void draw(Camera camera, FloatBuffer modelMatrix, int entityIndex, boolean isVisible, boolean isSelected) { draw(camera, modelMatrix, getFirstPassProgram(), entityIndex, isVisible, isSelected);}
    default void draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex) { draw(camera, modelMatrix, firstPassProgram, entityIndex, true, false); }
    void draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected);
    void drawDebug(Program program, FloatBuffer modelMatrix);
    Program getFirstPassProgram();
}
