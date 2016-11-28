
layout(binding=0) uniform sampler2D position;
layout(binding=1) uniform sampler2D direction;
layout(binding=2) uniform sampler2D albedo;
layout(binding=3) uniform sampler2D lighting;

uniform layout(binding = 4, rgba16f) image2D destTex;

const float PI = 3.14159265359;

layout (local_size_x = 16, local_size_y = 16) in;

uniform int width = 256;
uniform int height = 256;

//TODO: Use this and alternate between frames
uniform int count = 1;
uniform int currentCounter = 0;

struct Surfel
{
    vec4 pos;
    vec3 dir;
    vec3 alb;
    vec3 shad;
};

Surfel getSurfel(vec2 p)
{
    Surfel s;
    s.pos = textureLod(position, p, 0);
    s.dir = textureLod(direction, p, 0).rgb;
    s.alb = textureLod(albedo, p, 0).rgb;
    s.shad = textureLod(lighting, p, 0).rgb;

    return s;
}

float radiance( float cosE, float cosR, float a, float d) // Element to element radiance transfer
{
    return a*( max(cosE,0.) * max(cosR,0.) ) / ( PI*d*d + a );
}

void main()
{
    ivec2 texSize = textureSize(position, 0);
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	vec2 st = vec2(storePos) / vec2(width, height);

    vec2 p = vec2(storePos*2+1)/vec2(texSize*2.);

    Surfel rec = getSurfel(p);

    if(rec.pos.a==0.) //Are we on a real face ?
    {
        imageStore(destTex, storePos, vec4(0.));
        return;
    }

    vec3 gi = vec3(0.);

    for(int x = 0; x < width; x+=1)
    for(int y = 0; y < height; y+=1)
    {
        //Little hack to get the center of the texel
        vec2 p = vec2(float(x*2+1)/float(texSize.x*2.), float(y*2+1)/float(texSize.y*2.));

        vec2 currentSt = p / vec2(width, height);
        float mipLevel = 3.0;
        vec4 boundingSphere = textureLod(position, currentSt, mipLevel);
        float dist = length(rec.pos.xyz - boundingSphere.xyz);
        if(dist < boundingSphere.a + 25) {
//            x += 150;
//            y += 150;
//            x += pow(2, mipLevel)-1.0;
//            y += pow(2, mipLevel)-1.0;
//            continue;
        }

        Surfel em = getSurfel( p ); //Get emitter info
        if( em.pos.a == 0. ) //It is a real emitter ?
                continue;

        vec3 v = em.pos.xyz - rec.pos.xyz; // vector from the emitter to the receiver
        float d = length(v) + 1e-16; //avoid 0 to the distance squared area
        v /= d;

        float cosE = dot( -v, em.dir.xyz );
        float cosR = dot( v, rec.dir.xyz );

//        gi += radiance(cosE, cosR, 1./float(width/4/4),d) * em.alb * em.shad;
        gi += radiance(cosE, cosR, 1./float(width/1),d) * em.alb * em.shad;
    }

    vec4 col = 4*3*vec4(rec.alb*gi, 1.);
//    col = vec4(1,0,0,1);
//    col = vec4(getSurfel(p).alb.xyz,1);
//    col = vec4(100*gi,1);
    imageStore(destTex, storePos, imageLoad(destTex, storePos)+col);
}
