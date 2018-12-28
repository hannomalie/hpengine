package de.hanno.hpengine.engine.graphics.state;

import de.hanno.hpengine.engine.backend.EngineContext;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;

public class SimpleRenderStateRecorder implements RenderStateRecorder {
    private CopyOnWriteArrayList<RenderState> states = new CopyOnWriteArrayList();
    private EngineContext engineContext;

    public SimpleRenderStateRecorder(EngineContext engineContext) {
        this.engineContext = engineContext;
    }

    @Override
    public boolean add(RenderState state) {
        if(engineContext.getInput().isKeyPressed(GLFW_KEY_R)) {
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
