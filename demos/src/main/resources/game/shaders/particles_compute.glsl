#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE) in;

uniform int maxThreads;
uniform float time;
uniform float deltaSeconds = 0.1f;
uniform int targetCount;

layout(std430, binding=5) buffer _positions {
    vec4 positions[2000]; // TODO: Higher max value here?
};
layout(std430, binding=6) buffer _velocities {
    vec4 velocities[2000]; // TODO: Higher max value here?
};
struct Attractor {
    vec3 position;
    float radius;
    float strength;

    float padding0;
    float padding1;
    float padding2;
};
layout(std430, binding=7) buffer _targets {
    Attractor targets[2000]; // TODO: Higher max value here?
};
layout(std430, binding=8) buffer _accelerations {
    vec4 accelerations[2000]; // TODO: Higher max value here?
};

void main(){

    uint index = gl_GlobalInvocationID.x + gl_GlobalInvocationID.y;
    float mass = 1.0f;
    float inverseMass = 1.0f/mass;

    float gravitation = 9.807f;

    float damping = 0.99f;

    if(index < maxThreads) {
        vec3 velocity = velocities[index].xyz;
        vec3 position = positions[index].xyz;

        vec3 acceleration;

        for(int i = 0; i < targetCount; i++) {
            Attractor target = targets[i];
            vec3 vectorToTarget = target.position - position;
            vec3 vectorToOrigin = -position;

            float distanceToTarget = length(vectorToTarget);
            float maxDistance = target.radius;

            bool isInAttractorRange = distanceToTarget < maxDistance;
            if(isInAttractorRange) {
                float factor = distanceToTarget/maxDistance;
                acceleration.xyz += factor * target.strength * normalize(vectorToTarget);
            } else {
                acceleration.xyz += 0.0001f * normalize(vectorToOrigin); // slowly move to world center so that they get picked up again
            }
        }

        vec3 newVelocity = velocity + acceleration;

        velocities[index].xyz = newVelocity;

        positions[index].xyz = position + deltaSeconds * newVelocity;

        velocities[index].xyz *= damping;
    }
}
