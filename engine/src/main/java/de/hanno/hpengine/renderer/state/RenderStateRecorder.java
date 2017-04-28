package de.hanno.hpengine.renderer.state;

import java.util.List;

public interface RenderStateRecorder {
    boolean add(RenderState state);

    List<RenderState> getStates();
}
