package de.hanno.hpengine.engine.graphics.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector3f;

import java.nio.FloatBuffer;

public class DirectionalLightState {
    public FloatBuffer directionalLightViewMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    public FloatBuffer directionalLightProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    public FloatBuffer directionalLightViewProjectionMatrixAsBuffer = BufferUtils.createFloatBuffer(16);
    public Vector3f directionalLightDirection = new Vector3f();
    public Vector3f directionalLightColor = new Vector3f();
    public float directionalLightScatterFactor;

    public DirectionalLightState() {
    }
}