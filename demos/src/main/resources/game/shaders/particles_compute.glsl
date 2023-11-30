#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE) in;

uniform int maxThreads;
uniform float time;
uniform vec2 target;

layout(std430, binding=5) buffer _positions {
    vec4 positions[2000]; // TODO: Higher max value here?
};
layout(std430, binding=6) buffer _velocities {
    vec4 velocities[2000]; // TODO: Higher max value here?
};

void main(){

    uint index = gl_GlobalInvocationID.x + gl_GlobalInvocationID.y;

    if(index < maxThreads) {
        vec2 velocity = velocities[index].xz;
        vec2 vectorToTarget = (target - positions[index].xz);
        positions[index].xz = positions[index].xz + velocity;

        float distanceToTarget = length(vectorToTarget);
        if(distanceToTarget < 150) {
            float factor = 0.1f * min(max(distanceToTarget/150.0f, 0.0f), 10.0f);
            velocities[index].xz = factor * normalize(vectorToTarget);
        }
    }
}
