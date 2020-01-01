layout(std430, binding=4) buffer _probePositions {
    vec4 probePositions[];
};

layout(binding=8) uniform samplerCubeArray probeCubeMaps;
uniform int probeCount = 64; // TODO: How to pass that into this shader
uniform vec3 probeDimensions = vec3(50);
uniform int probeIndex = 0;

uniform sampler2D renderedTexture;
uniform vec3 diffuseColor = vec3(1,1,0);

in vec2 pass_TextureCoord;
in vec4 pass_WorldPosition;
in vec3 normal_world;
in vec3 normal_view;

layout(location=0)out vec4 out_color;

void main()
{
    vec3 positionWorld = pass_WorldPosition.xyz;
    vec3 normalWorld = normal_world;
    vec3 probeDimensionsHalf = probeDimensions * 0.5f;

    vec4 result = vec4(0,0,0,1);
    result.rgb += textureLod(probeCubeMaps, vec4(normalWorld, probeIndex),0).rgb;
    out_color = result;
}
