//include(globals_structs.glsl)
layout(std430, binding=2) buffer _drawCount {
	uint drawCount;
};

layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};

layout(std430, binding=4) buffer _offsetsSource {
	int offsetsSource[1000];
};

layout(std430, binding=5) buffer _drawCommandsSource {
	DrawCommand drawCommandsSource[1000];
};

layout(std430, binding=7) buffer _drawCommandsTarget {
	DrawCommand drawCommandsTarget[1000];
};

layout(std430, binding=8) buffer _offsetsTarget {
	int offsetsTarget[1000];
};
layout(std430, binding=9) buffer _drawCountAfterPhase1 {
	uint drawCountAfterPhase1;
};

uniform int maxDrawCommands;

void main()
{
    uint indexBefore = gl_VertexID;

    if(indexBefore < maxDrawCommands) {
        int offset = offsetsSource[indexBefore];
        DrawCommand sourceCommand = drawCommandsSource[indexBefore];
        Entity entity;
        if(sourceCommand.instanceCount > 1){
            entity = entities[offset]; // TODO: Question this
        } else {
            entity = entities[offset];
        }

        if(entity.visible > 0) {
            uint indexAfter = atomicAdd(drawCount, 1);
            drawCommandsTarget[indexAfter] = sourceCommand;
            offsetsTarget[indexAfter] = offset;
        }
    }

//    gl_Position = vec4(0,0,0,0);
}
