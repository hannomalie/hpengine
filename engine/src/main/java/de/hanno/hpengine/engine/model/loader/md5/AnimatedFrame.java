package de.hanno.hpengine.engine.model.loader.md5;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import org.joml.Matrix4f;

import java.util.Arrays;

public class AnimatedFrame {

    public static final int MAX_JOINTS = 150;
        
    private static final BufferableMatrix4f IDENTITY_MATRIX = new BufferableMatrix4f(new Matrix4f());
            
    private final BufferableMatrix4f[] localJointMatrices;

    private final BufferableMatrix4f[] jointMatrices;

    public AnimatedFrame() {
        localJointMatrices = new BufferableMatrix4f[MAX_JOINTS];
        Arrays.fill(localJointMatrices, IDENTITY_MATRIX);

        jointMatrices = new BufferableMatrix4f[MAX_JOINTS];
        Arrays.fill(jointMatrices, IDENTITY_MATRIX);
    }
    
    public Matrix4f[] getLocalJointMatrices() {
        return localJointMatrices;
    }

    public BufferableMatrix4f[] getJointMatrices() {
        return jointMatrices;
    }

    public void setMatrix(int pos, BufferableMatrix4f localJointMatrix, Matrix4f invJointMatrix) {
        localJointMatrices[pos] = localJointMatrix;
        BufferableMatrix4f mat = new BufferableMatrix4f(localJointMatrix);
        mat.mul(invJointMatrix);
        jointMatrices[pos] = mat;
    }
}
