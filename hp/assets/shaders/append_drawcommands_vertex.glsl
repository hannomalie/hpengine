//include(globals_structs.glsl)
layout(std430, binding=1) buffer _entityCounts {
	int entityCounts[2000];
};
layout(std430, binding=2) buffer _drawCount{
	int drawCount;
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
layout(std430, binding=10) buffer _entitiesCompacted {
	Entity entitiesCompacted[2000];
};
layout(std430, binding=11) buffer _entitiesCompactedCounter {
	int entitiesCompactedCounter;
};
uniform int maxDrawCommands;

void main()
{
    uint indexBefore = gl_VertexID;

//TODO: This doesnt work
    if(indexBefore < maxDrawCommands) {
        int offset = offsetsSource[indexBefore];
        int baseOffset = 0;
        for(int i = 0; i < indexBefore; i++) {
            baseOffset += entityCounts[i];
        }
        int noOfInstances = entityCounts[indexBefore];
        for(int i = 0; i < noOfInstances; i++) {
            int instanceIndex = baseOffset + i;

            bool visible = visibility[instanceIndex] == 1;

            if(visible)
            {
                uint indexAfter = atomicAdd(drawCount, 1);

                int compatedIndex = atomicAdd(entitiesCompactedCounter, 1);
                entitiesCompacted[compatedIndex] = entities[instanceIndex];
                DrawCommand sourceCommand = drawCommandsSource[indexBefore];
                sourceCommand.instanceCount = noOfInstances;
                drawCommandsTarget[indexAfter] = sourceCommand;
                offsetsTarget[indexAfter] = offset;
            }
        }
    }
}
