package de.hanno.hpengine.engine.graphics.renderer;

import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.ARBImaging.GL_TABLE_TOO_LARGE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION;

public class GLU {

    public static String gluErrorString(int error_code) {
        switch(error_code) {
            case 100900:
                return "Invalid enum (glu)";
            case 100901:
                return "Invalid value (glu)";
            case 100902:
                return "Out of memory (glu)";
            default:
                return de.hanno.hpengine.util.Util.translateGLErrorString(error_code);
        }
    }

    public static String translateGLErrorString(int error_code) {
        switch (error_code) {
            case GL11.GL_NO_ERROR:
                return "No error";
            case GL_INVALID_ENUM:
                return "Invalid enum";
            case GL_INVALID_VALUE:
                return "Invalid value";
            case GL_INVALID_OPERATION:
                return "Invalid operation";
            case GL_STACK_OVERFLOW:
                return "Stack overflow";
            case GL_STACK_UNDERFLOW:
                return "Stack underflow";
            case GL_OUT_OF_MEMORY:
                return "Out of memory";
            case GL_TABLE_TOO_LARGE:
                return "Table too large";
            case GL_INVALID_FRAMEBUFFER_OPERATION:
                return "Invalid framebuffer operation";
            default:
                return null;
        }
    }
}
