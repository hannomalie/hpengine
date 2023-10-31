#define M_PI 3.1415926535897932384626433832795

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0, rgba32f) readonly uniform image2D twiddlesIndices;
layout (binding = 1, rgba32f) uniform image2D tildeMap;
layout (binding = 2, rgba32f) uniform image2D pingPongMap;

uniform int stage;
uniform int pingpong;
uniform int direction;

struct complex
{
    float real;
    float im;
};

complex mul(complex c0, complex c1)
{
    complex c;
    c.real = c0.real * c1.real - c0.im * c1.im;
    c.im   = c0.real * c1.im + c0.im * c1.real;
    return c;
}

complex add(complex c0, complex c1)
{
    complex c;
    c.real = c0.real + c1.real;
    c.im   = c0.im   + c1.im;
    return c;
}


void horizontalButterflies()
{
    complex H;
    ivec2 x = ivec2(gl_GlobalInvocationID.xy);

    if(pingpong == 0)
    {
        vec4 data = imageLoad(twiddlesIndices, ivec2(stage, x.x)).rgba;
        vec2 p_ = imageLoad(tildeMap, ivec2(data.z, x.y)).rg;
        vec2 q_ = imageLoad(tildeMap, ivec2(data.w, x.y)).rg;
        vec2 w_ = vec2(data.x, data.y);

        complex p = complex(p_.x,p_.y);
        complex q = complex(q_.x,q_.y);
        complex w = complex(w_.x,w_.y);

        //Butterfly operation
        H = add(p,mul(w,q));

        imageStore(pingPongMap, x, vec4(H.real, H.im, 0, 1));
        // imageStore(pingPongMap, x, vec4(p_,0,1)); // debug
    }
    else if(pingpong == 1)
    {
        vec4 data = imageLoad(twiddlesIndices, ivec2(stage, x.x)).rgba;
        vec2 p_ = imageLoad(pingPongMap, ivec2(data.z, x.y)).rg;
        vec2 q_ = imageLoad(pingPongMap, ivec2(data.w, x.y)).rg;
        vec2 w_ = vec2(data.x, data.y);

        complex p = complex(p_.x,p_.y);
        complex q = complex(q_.x,q_.y);
        complex w = complex(w_.x,w_.y);

        //Butterfly operation
        H = add(p,mul(w,q));

        imageStore(tildeMap, x, vec4(H.real, H.im, 0, 1));
        // imageStore(tildeMap, x, vec4(p_,0,1)); // debug
    }
}

void verticalButterflies()
{
    complex H;
    ivec2 x = ivec2(gl_GlobalInvocationID.xy);

    vec4 data = imageLoad(twiddlesIndices, ivec2(stage, x.y)).rgba;
    if(pingpong == 0)
    {
        vec2 p_ = imageLoad(tildeMap, ivec2(x.x, data.z)).rg;
        vec2 q_ = imageLoad(tildeMap, ivec2(x.x, data.w)).rg;
        vec2 w_ = vec2(data.x, data.y);

        complex p = complex(p_.x,p_.y);
        complex q = complex(q_.x,q_.y);
        complex w = complex(w_.x,w_.y);

        //Butterfly operation
        H = add(p,mul(w,q));

        imageStore(pingPongMap, x, vec4(H.real, H.im, 0, 1));
        // imageStore(pingPongMap, x, vec4(p_,0,1)); // debug
    }
    else if(pingpong == 1)
    {
        vec2 p_ = imageLoad(pingPongMap, ivec2(x.x, data.z)).rg;
        vec2 q_ = imageLoad(pingPongMap, ivec2(x.x, data.w)).rg;
        vec2 w_ = vec2(data.x, data.y);

        complex p = complex(p_.x,p_.y);
        complex q = complex(q_.x,q_.y);
        complex w = complex(w_.x,w_.y);

        //Butterfly operation
        H = add(p,mul(w,q));

        imageStore(tildeMap, x, vec4(H.real, H.im, 0, 1));
        // imageStore(tildeMap, x, vec4(p_,0,1)); // debug
    }
}

void main(void)
{
    if(direction == 0)
        horizontalButterflies();
    else if(direction == 1)
        verticalButterflies();
}
