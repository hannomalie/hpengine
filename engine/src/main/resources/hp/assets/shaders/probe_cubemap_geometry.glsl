layout(triangles) in;
layout(triangle_strip, max_vertices = 18) out; // 6 faces * 3 vertices per cubemap

//include(globals_structs.glsl)

in vec4 vs_pass_WorldPosition[3];
in vec4 vs_pass_ProjectedPosition[3];
in vec3 vs_pass_normal_world[3];
in float vs_clip[3];
in vec2 vs_pass_texCoord[3];
flat in int vs_entityIndex[3];
//flat in Entity vs_entity[3];
//flat in Material vs_material[3];

uniform mat4[6] viewProjectionMatrices;
uniform mat4[6] viewMatrices;
uniform mat4[6] projectionMatrices;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 viewProjectionMatrix;
uniform vec3 probePositionWorld;

out vec4 pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out vec3 pass_normal_world;
out float clip;
out vec2 texCoord;
flat out int pass_entityIndex;
//flat out Entity pass_entity;
//flat out Material pass_material;

void main() {

  for(int layer = 0; layer < 6; layer++) {
    gl_Layer = layer;
    /////////////// FRUSTUM AND BACKFACE CULLING
    vec4 vertex[3];
    int outOfBound[6] = {0, 0, 0, 0, 0, 0};
    for (int i=0; i<3; ++i) {
        vertex[i] = projectionMatrices[layer] * viewMatrices[layer] * vec4(vs_pass_WorldPosition[i].xyz, 1);
        if (vertex[i].x > +vertex[i].w) ++outOfBound[0];
        if (vertex[i].x < -vertex[i].w) ++outOfBound[1];
        if (vertex[i].y > +vertex[i].w) ++outOfBound[2];
        if (vertex[i].y < -vertex[i].w) ++outOfBound[3];
        if (vertex[i].z > +vertex[i].w) ++outOfBound[4];
        if (vertex[i].z < -vertex[i].w) ++outOfBound[5];
    }
    bool inFrustum = true;
    for (int i=0; i<6; ++i) {
        if (outOfBound[i] == 3) inFrustum = false;
    }

    vec3 normal = cross(vs_pass_WorldPosition[2].xyz - vs_pass_WorldPosition[0].xyz, vs_pass_WorldPosition[0].xyz - vs_pass_WorldPosition[1].xyz);
    vec3 view = probePositionWorld - vs_pass_WorldPosition[0].xyz;
    bool frontFace = dot(normal, view) > 0.f;
    ////////////////////

// frustum culling seems to make it worse if no instancing present
//if(inFrustum && frontFace) {
    if(frontFace)
    {
        for(int i = 0; i < 3; i++) { // You used triangles, so it's always 3
              vec3 positionWorld = vs_pass_WorldPosition[i].xyz;
              vec4 projectedPosition = projectionMatrices[layer] * viewMatrices[layer] * vec4(positionWorld,1);
              pass_WorldPosition = vec4(positionWorld,1);
              pass_ProjectedPosition = projectedPosition;
              pass_normal_world = vs_pass_normal_world[i];
              pass_entityIndex = vs_entityIndex[i];
//              pass_entity = vs_entity[i];
//              pass_material = vs_material[i];
              texCoord = vs_pass_texCoord[i];

              gl_Position = projectedPosition;
              clip = 1.0;
              EmitVertex();
            }

            EndPrimitive();
          }
    }
}