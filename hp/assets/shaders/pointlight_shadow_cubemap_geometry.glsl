layout(triangles) in;
layout(triangle_strip, max_vertices = 18) out; // 6 faces * 3 vertices per cubemap

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