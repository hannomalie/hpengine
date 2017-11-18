package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;

public class DrawDescription {
    private final RenderState renderState;
    private final Program program;
    private final CommandOrganization commandOrganization;
    private final VertexIndexBuffer vertexIndexBuffer;

    public DrawDescription(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer) {
        this.renderState = renderState;
        this.program = program;
        this.commandOrganization = commandOrganization;
        this.vertexIndexBuffer = vertexIndexBuffer;
    }

    public RenderState getRenderState() {
        return renderState;
    }

    public Program getProgram() {
        return program;
    }

    public CommandOrganization getCommandOrganization() {
        return commandOrganization;
    }

    public VertexIndexBuffer getVertexIndexBuffer() {
        return vertexIndexBuffer;
    }
}
