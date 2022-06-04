//include(globals_structs.glsl)
layout(std430, binding=1) buffer _instanceCountForCommand {
	int instanceCountForCommand[2000];
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
layout(std430, binding=12) buffer _commandEntityOffsets {
	int commandEntityOffsets[2000];
};
layout(std430, binding=13) buffer _currentCompactedPointers {
	coherent int currentCompactedPointers[2000];
};
uniform int maxDrawCommands;

void main()
{
    uint commandIndex = gl_InstanceID;
    DrawCommand sourceCommand = drawCommandsSource[commandIndex];
    uint instanceIndex = gl_VertexID;

//    int visibilityBufferOffset = commandEntityOffsets[commandIndex];
    int targetCommandIndex = 0;
    int visibilityBufferOffset = 0;
    for(int i = 0; i < commandIndex; i++) {
        visibilityBufferOffset += drawCommandsSource[i].instanceCount;
        if(instanceCountForCommand[i] > 0) {
            targetCommandIndex++;
        }
    }

    if(instanceIndex == 0) {
        int noOfVisibleInstances = instanceCountForCommand[commandIndex];
        if(noOfVisibleInstances > 0) {
            atomicAdd(entitiesCompactedCounter, instanceCountForCommand[commandIndex]);
            atomicAdd(drawCount, 1);
            sourceCommand.instanceCount = noOfVisibleInstances;
            drawCommandsTarget[targetCommandIndex] = sourceCommand;
        }
    }

    bool visible = visibility[visibilityBufferOffset + instanceIndex] == 1;
    if(instanceIndex < sourceCommand.instanceCount && visible)
    {
        int compactedBufferOffset = 0;
        for(int i = 0; i < commandIndex; i++) {
            compactedBufferOffset += instanceCountForCommand[i];
        }

        uint entityBufferSource = offsetsSource[commandIndex] + instanceIndex;
        uint compactedPointer = atomicAdd(currentCompactedPointers[commandIndex], 1);
        uint compactedEntityBufferIndex = compactedBufferOffset + compactedPointer;

        entitiesCompacted[compactedEntityBufferIndex] = entities[entityBufferSource];

        if(instanceIndex == 0) {
            offsetsTarget[targetCommandIndex] = compactedBufferOffset;
        }
    }
}
