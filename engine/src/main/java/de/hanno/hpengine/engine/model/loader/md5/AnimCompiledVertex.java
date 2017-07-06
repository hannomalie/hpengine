package de.hanno.hpengine.engine.model.loader.md5;

import org.joml.Vector2f;
import org.joml.Vector3f;

public class AnimCompiledVertex {

    public Vector3f position;

    public Vector2f textCoords;

    public Vector3f normal;

    public float[] weights;

    public int[] jointIndices;

    public AnimCompiledVertex() {
        super();
        normal = new Vector3f(0, 0, 0);
    }
}
