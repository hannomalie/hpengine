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

layout(std430, binding=9) buffer _visibility {
	int visibility[1000];
};

uniform int maxDrawCommands;

void main()
{
    uint indexBefore = gl_VertexID;

    if(indexBefore < maxDrawCommands) {
        int offset = offsetsSource[indexBefore];
        DrawCommand sourceCommand = drawCommandsSource[indexBefore];
        bool visible = visibility[indexBefore] == 1;

        if(visible)
        {
            uint indexAfter = atomicAdd(drawCount, 1);
            drawCommandsTarget[indexAfter] = sourceCommand;
            offsetsTarget[indexAfter] = offset;
        }
    }

//    gl_Position = vec4(0,0,0,0);
}
