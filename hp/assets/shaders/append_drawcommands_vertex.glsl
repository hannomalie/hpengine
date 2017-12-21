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
        DrawCommand sourceCommand = drawCommandsSource[indexBefore];
        int entityBufferOffset = offsetsSource[indexBefore];

        int baseInstanceOffset = 0;

        for(int i = 0; i < indexBefore; i++) {
            baseInstanceOffset += entityCounts[i];
        }
        int noOfInstances = entityCounts[indexBefore];
        int[2000] entityBufferIndices;
        int entityBufferIndicesIndex = 0;

        for(int i = 0; i < sourceCommand.instanceCount; i++) {
            int instanceIndex = entityBufferOffset + i;

            bool visible = visibility[i] == 1;

            if(visible)
            {
                entityBufferIndices[entityBufferIndicesIndex] = instanceIndex;
                entityBufferIndicesIndex++;
            }
        }

        if(noOfInstances > 0) {
            int drawCommandIndex = atomicAdd(drawCount, 1);
            atomicAdd(entitiesCompactedCounter, noOfInstances);
            for(int i = 0; i < noOfInstances; i++) {
                entitiesCompacted[baseInstanceOffset+i] = entities[entityBufferIndices[i]];
//                entitiesCompacted[0] = entities[16];
            }
            sourceCommand.instanceCount = noOfInstances;
            drawCommandsTarget[drawCommandIndex] = sourceCommand;
            offsetsTarget[drawCommandIndex] = baseInstanceOffset;
        }
    }
}
