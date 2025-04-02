#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE) in;

uniform int maxThreads;
uniform float time;
uniform float deltaSeconds = 0.001f;
uniform int targetCount;
uniform vec3 worldMin = vec3(-100);
uniform vec3 worldMax = vec3(100);

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

// https://gist.github.com/patriciogonzalezvivo/670c22f3966e662d2f83
float mod289(float x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 mod289(vec4 x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 perm(vec4 x){return mod289(((x * 34.0) + 1.0) * x);}

float noise(vec3 p){
    vec3 a = floor(p);
    vec3 d = p - a;
    d = d * d * (3.0 - 2.0 * d);

    vec4 b = a.xxyy + vec4(0.0, 1.0, 0.0, 1.0);
    vec4 k1 = perm(b.xyxy);
    vec4 k2 = perm(k1.xyxy + b.zzww);

    vec4 c = k2 + a.zzzz;
    vec4 k3 = perm(c);
    vec4 k4 = perm(c + 1.0);

    vec4 o1 = fract(k3 * (1.0 / 41.0));
    vec4 o2 = fract(k4 * (1.0 / 41.0));

    vec4 o3 = o2 * d.z + o1 * (1.0 - d.z);
    vec2 o4 = o3.yw * d.x + o3.xz * (1.0 - d.x);

    return o4.y * d.y + o4.x * (1.0 - d.y);
}

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
                acceleration.xyz += 0.1f * normalize(vectorToOrigin); // slowly move to world center so that they get picked up again
            }
        }

        vec3 newVelocity = velocity + acceleration;

        velocities[index].xyz = newVelocity;

        vec3 resultPosition = position + deltaSeconds * newVelocity;

        resultPosition.x = resultPosition.x < worldMin.x ? worldMax.x : resultPosition.x;
        resultPosition.y = resultPosition.y < worldMin.y ? worldMax.y : resultPosition.y;
        resultPosition.z = resultPosition.z < worldMin.z ? worldMax.z : resultPosition.z;

        resultPosition.x = resultPosition.x > worldMax.x ? worldMin.x : resultPosition.x;
        resultPosition.y = resultPosition.y > worldMax.y ? worldMin.y : resultPosition.y;
        resultPosition.z = resultPosition.z > worldMax.z ? worldMin.z : resultPosition.z;

        positions[index].xyz = resultPosition;

        velocities[index].xyz *= damping;
    }
}
