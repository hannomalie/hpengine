layout(triangles) in;
layout(triangle_strip, max_vertices = 18) out;

in vec4 vs_pass_WorldPosition[3];
in vec4 vs_pass_ProjectedPosition[3];
in float vs_clip[3];
in vec2 vs_pass_texCoord[3];

uniform mat4[6] viewProjectionMatrices;
uniform mat4[6] viewMatrices;
uniform mat4[6] projectionMatrices;

uniform int lightIndex = 0;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 viewProjectionMatrix;
uniform vec3 pointLightPositionWorld;

out vec4 pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out float clip;

mat4 rotationMatrix(vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

const mat4 rotationMatrices[6] = {
  rotationMatrix(vec3(0,0,1), 180) * rotationMatrix(vec3(0,1,0), -90),
  rotationMatrix(vec3(0,0,1), 180) * rotationMatrix(vec3(0,1,0), 90),
  rotationMatrix(vec3(0,0,1), 180) * rotationMatrix(vec3(1,0,0), 90) * rotationMatrix(vec3(0,1,0), 180),
  rotationMatrix(vec3(0,0,1), 180) * rotationMatrix(vec3(1,0,0), -90),
  rotationMatrix(vec3(0,0,1), 180) * rotationMatrix(vec3(0,1,0), -180),
  rotationMatrix(vec3(0,0,1), 180)
};

void main() {

  for(int layer = 0; layer < 6; layer++) {
    gl_Layer = 6*lightIndex + layer;

    for(int i = 0; i < 3; i++) { // You used triangles, so it's always 3
      vec3 positionWorld = vs_pass_WorldPosition[i].xyz;
//      vec4 projectedPosition = viewProjectionMatrices[layer] * vec4(positionWorld,1);
//      vec4 projectedPosition = projectionMatrix * viewMatrix * vec4(vs_pass_WorldPosition[i].xyz,1);
      vec4 projectedPosition = projectionMatrices[layer] * viewMatrices[layer] * vec4(positionWorld,1);
      pass_WorldPosition = vec4(positionWorld,1);
      pass_ProjectedPosition = projectedPosition;
//      pass_ProjectedPosition = projectionMatrix *(positionViewSpace);

      gl_Position = projectedPosition;
      clip = 1.0;
      EmitVertex();
    }

    EndPrimitive();
  }
}