package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.input.Input;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;

public class SimpleRenderStateRecorder implements RenderStateRecorder {
    private CopyOnWriteArrayList<RenderState> states = new CopyOnWriteArrayList();

    public SimpleRenderStateRecorder() {
    }

    @Override
    public boolean add(RenderState state) {
        if(Input.isKeyPressed(GLFW_KEY_R)) {
            keyRWasPressed = true;
            System.out.println("Pressed");
        } else if(keyRWasPressed) {
            keyRWasPressed = false;
            System.out.println("Released");
            return states.add(new RenderState(state));
        }
        return false;
    }
    boolean keyRWasPressed = false;

    @Override
    public CopyOnWriteArrayList<RenderState> getStates() {
        return states;
    }
}
