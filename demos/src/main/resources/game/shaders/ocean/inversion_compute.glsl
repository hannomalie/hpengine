layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0, rgba32f) writeonly uniform image2D displacement;
layout (binding = 1, rgba32f) uniform image2D tildeMap;
layout (binding = 2, rgba32f) uniform image2D pingPongMap;

uniform int pingpong;
uniform int N = 256;

void main(void)
{
    ivec2 x = ivec2(gl_GlobalInvocationID.xy);
//    ivec2 x = ivec2(gl_GlobalInvocationID.xy) - N/2;
    float perms[] = {1.0, -1.0};
    int index = int(mod(x.x+x.y, 2));
    float perm = 1.0f;//perms[index];

    if(pingpong == 0)
    {
        float h = imageLoad(tildeMap, x).r;
        imageStore(displacement, x, vec4(perm*(h/float(N*N)), perm*(h/float(N*N)), perm*(h/float(N*N)), 1));
    }
    else if(pingpong == 1)
    {
        float h = imageLoad(pingPongMap, x).r;
        imageStore(displacement, x, vec4(perm*(h/float(N*N)), perm*(h/float(N*N)), perm*(h/float(N*N)), 1));
    }
}
