#version 420

layout(binding=0) uniform sampler2D renderedTexture;
layout(binding=1) uniform sampler2D normalDepthTexture;
layout(binding=3) uniform sampler2D motionMap; // motionVec

uniform bool usePostProcessing = false;

in vec2 pass_TextureCoord;
out vec4 out_color;

float calculateMotionBlur(vec2 uv) {
	return length(texture2D(motionMap, uv).xy);
}

///////////////////////////////////////
// http://facepunch.com/showthread.php?t=1401594
///////////////////////////////////////
///////////////////////////////////////
// DANKE AN CorentinF, SUPER GEIL!!!!!!
///////////////////////////////////////
#define PI  3.14159265
//uniform variables from external script
const float focalDepth = 10;  //focal distance value in meters, but you may use autofocus option below
const float focalLength = 12.0; //focal length in mm
const float fstop = 1.1;//1.4; //f-stop value
const bool showFocus = false; //show debug focus point and focal range (red = focal point, green = focal range)

/* 
make sure that these two values are the same for your camera, otherwise distances will be wrong.
*/

const float znear = 0; //camera clipping start
const float zfar = 5000; //camera clipping end
const float zScaleLinear = znear / zfar;
const float zScaleLinearRev = zfar / znear;

//------------------------------------------
//user variables

const int samples = 3; //samples on the first ring
const int rings = 4; //ring count

const bool manualdof = false; //manual dof calculation
const float ndofstart = 24.0; //near dof blur start
const float ndofdist = 128.0; //near dof blur falloff distance
const float fdofstart = 128.0; //far dof blur start
const float fdofdist = 512.0; //far dof blur falloff distance

const float CoC = 0.02;//circle of confusion size in mm (35mm film = 0.03mm)

const bool vignetting = false; //use optical lens vignetting?
const float vignout = 1.5; //vignetting outer border
const float vignin = 0.0; //vignetting inner border
const float vignfade = 22.0; //f-stops till vignete fades

const bool autofocus = true; //use autofocus in shader? disable if you use external focalDepth value
const vec2 focus = vec2(0.5,0.5); // autofocus point on screen (0.0,0.0 - left lower corner, 1.0,1.0 - upper right)
const float maxblur = 1.0; //clamp value of max blur (0.0 = no blur,1.0 default)

const float threshold = 0.9; //highlight threshold;
const float gain = 64.0; //highlight gain;

const float bias = 0.8; //bokeh edge bias
const float fringe = 0.7; //bokeh chromatic aberration/fringing

const bool noise = false; //use noise instead of pattern for sample dithering
const float namount = 0.0004; //dither amount

const bool depthblur = false; //blur the depth buffer?
const float dbsize = 1; //depthblursize

const float exponential = 7.0;

/*
next part is experimental
not looking good with small sample and ring count
looks okay starting from samples = 4, rings = 4
*/

const bool pentagon = false; //use pentagon as bokeh shape?
const float feather = 0.5; //pentagon shape feather

//------------------------------------------
float readDepth(in vec2 coord, sampler2D tex)
{
	return texture2D(tex, coord).a * zScaleLinear;
}
float readDepth(float linDepth)
{
	return linDepth * zScaleLinear;
}

float penta(vec2 coords) //pentagonal shape
{
	float scale = float(rings) - 1.3;
	vec4  HS0 = vec4( 1.0,         0.0,         0.0,  1.0);
	vec4  HS1 = vec4( 0.309016994, 0.951056516, 0.0,  1.0);
	vec4  HS2 = vec4(-0.809016994, 0.587785252, 0.0,  1.0);
	vec4  HS3 = vec4(-0.809016994,-0.587785252, 0.0,  1.0);
	vec4  HS4 = vec4( 0.309016994,-0.951056516, 0.0,  1.0);
	vec4  HS5 = vec4( 0.0        ,0.0         , 1.0,  1.0);
	
	vec4  one = vec4( 1.0,1.0,1.0,1.0 );
	
	vec4 P = vec4((coords),vec2(scale, scale)); 
	
	vec4 dist = vec4(0.0,0.0,0.0,0.0);
	float inorout = -4.0;
	
	dist.x = dot( P, HS0 );
	dist.y = dot( P, HS1 );
	dist.z = dot( P, HS2 );
	dist.w = dot( P, HS3 );
	
	dist = smoothstep( -feather, feather, dist );
	
	inorout += dot( dist, one );
	
	dist.x = dot( P, HS4 );
	dist.y = HS5.w - abs( P.z );
	
	dist = smoothstep( -feather, feather, dist );
	inorout += dist.x;
	
	return clamp(inorout, 0, 1);
}
float bdepth(vec2 coords, vec2 texelSize, sampler2D color_depth) //blurring depth
{
	float d = 0.0;
	float kernel[9];
	float offset[9];
	float offset1[9];
	
	vec2 wh = vec2(texelSize.x, texelSize.y) * dbsize;

	offset[0] = -wh.x;
	offset[1] = 0.0;
	offset[2] = wh.x;
	
	offset[3] = wh.x;
	offset[4] = 0.0;
	offset[5] = wh.x;
	
	offset[6] = wh.x;
	offset[7] = 0.0;
	offset[8] = wh.x;

	offset1[0] = -wh.y;
	offset1[1] = -wh.y;
	offset1[2] = -wh.y;
	
	offset1[3] = 0.0;
	offset1[4] = 0.0;
	offset1[5] = 0.0;
	
	offset1[6] = wh.y;
	offset1[7] = wh.y;
	offset1[8] = wh.y;

	float seize = 16.0;

	kernel[0] = 1.0/seize;   kernel[1] = 2.0/seize;   kernel[2] = 1.0/seize;
	kernel[3] = 2.0/seize;   kernel[4] = 4.0/seize;   kernel[5] = 2.0/seize;
	kernel[6] = 1.0/seize;   kernel[7] = 2.0/seize;   kernel[8] = 1.0/seize;
	
	
	for( int i=0; i<9; i++ )
	{
		//float tmp = texture2D(color_depth, coords + offset[i]).r;
		float tmp = texture2D(normalDepthTexture, coords + vec2(offset[i],offset1[i])).a;
		d += tmp * kernel[i];
	}
	
	return d;
}


vec3 color(vec2 coords,float blur, vec2 texelSize, sampler2D color_depth) //processing the sample
{
	vec3 col = vec3(0.0,0.0,0.0);
	
	col.g = texture2D(color_depth,coords + vec2(0.0,1.0)*texelSize*fringe*blur).g;
	col.b = texture2D(color_depth,coords + vec2(-0.866,-0.5)*texelSize*fringe*blur).b;
	col.r = texture2D(color_depth,coords + vec2(0.866,-0.5)*texelSize*fringe*blur).r;
	
	vec3 lumcoeff = vec3(0.299,0.587,0.114);
	float lum = dot(col.rgb, lumcoeff);
	float thresh = max((lum-threshold)*gain, 0.0);
	return col+mix(vec3(0.0,0.0,0.0),col,thresh*blur);
}

vec2 rand(vec2 coord, vec2 size) //generating noise/pattern texture for dithering
{
	float noiseX = ((fract(1.0-coord.x*(size.x/2.0))*0.25)+(fract(coord.y*(size.y/2.0))*0.75))*2.0-1.0;
	float noiseY = ((fract(1.0-coord.x*(size.x/2.0))*0.75)+(fract(coord.y*(size.y/2.0))*0.25))*2.0-1.0;
	
	if (noise)
	{
		noiseX = fract(sin(dot(coord ,vec2(12.9898,78.233))) * 43758.5453) * 2.0-1.0;
		noiseY = fract(sin(dot(coord ,vec2(12.9898,78.233)*2.0)) * 43758.5453) * 2.0-1.0;
	}
	return vec2(noiseX,noiseY);
}

vec3 debugFocus(vec3 col, float blur, float depth)
{
	float edge = 0.002*depth; //distance based edge smoothing
	float m = clamp(smoothstep(0.0,edge,blur), 0, 1);
	float e = clamp(smoothstep(1.0-edge,1.0,blur), 0, 1);
	
	col = mix(col,vec3(1.0,0.5,0.0),(1.0-m)*0.6);
	col = mix(col,vec3(0.0,0.5,1.0),((1.0-e)-(1.0-m))*0.2);

	return col;
}

float linearize(float depth)
{
	// the provided texture is already linear, I'm so bad...
	return depth;
	//return -zfar * znear / (depth * (zfar - znear) - zfar);
}

float vignette(vec2 uv)
{
	float dist = distance(uv, vec2(0.5,0.5));
	dist = smoothstep(vignout+(fstop/vignfade), vignin+(fstop/vignfade), dist);
	return clamp(dist, 0, 1);
}
vec3 DoDOF(in vec2 uv, in vec2 texelSize, in sampler2D color_depth)
{
	//scene depth calculation

	float depthSample = texture2D(normalDepthTexture, uv ).a;
	float depth = linearize(depthSample);
	
	if (depthblur)
	{
		depth = bdepth(uv, texelSize, color_depth);
		depth = linearize(depth);
	}
	//focal plane calculation
	
	float fDepth = focalDepth;
	
	if (autofocus)
	{
		fDepth = texture2D(normalDepthTexture,focus).a;
		fDepth = linearize(fDepth);
		//fDepth = pow( (1.0f - fDepth), exponential );
	}
	//dof blur factor calculation
	
	float blur = 0.0;
	
	if (manualdof)
	{    
		float a = depth-fDepth; //focal plane
		float b = (a-fdofstart)/fdofdist; //far DoF
		float c = (-a-ndofstart)/ndofdist; //near Dof
		blur = (a>0.0)?b:c;
	}
	else
	{
		float f = focalLength; //focal length in mm
		float d = fDepth*1000.0; //focal plane in mm
		float o = depth*1000.0; //depth in mm
		
		float a = (o*f)/(o-f); 
		float b = (d*f)/(d-f); 
		float c = (d-f)/(d*fstop*CoC); 
		
		blur = abs(a-b)*c;
	}
	
	blur = max(calculateMotionBlur(uv), blur);
	blur = clamp(blur, 0, 1);
	
	// calculation of pattern for ditering
	
	vec2 noise = rand(uv,texelSize)*namount*blur;
	
	// getting blur x and y step factor

	float w = texelSize.x/clamp(depth,0.25,1.0)+(noise.x*(1.0-noise.x));
	float h = texelSize.y/clamp(depth,0.25,1.0)+(noise.y*(1.0-noise.y));
	w = w*blur*maxblur;
	h = h*blur*maxblur;
	
	// calculation of final color
	
	vec3 col = vec3(0.0,0.0,0.0);
	
	if(blur < 0.05) //some optimization thingy
	{
		col = texture2D(renderedTexture, uv).rgb;
	}
	else
	{
		col = texture2D(renderedTexture, uv).rgb;
		float s = 1.0;
		int ringsamples;
		
		for (int i = 1; i <= rings; i++)
		{   
			ringsamples = i * samples;
			
			for (int j = 0 ; j < ringsamples; j++)   
			{
				float step = PI*2.0 / float(ringsamples);
				float pw = (cos(float(j)*step)*float(i));
				float ph = (sin(float(j)*step)*float(i));
				float p = 1.0;
				if (pentagon)
				{ 
					p = penta(vec2(pw,ph));
				}
				col += color(uv + vec2(pw*w,ph*h),blur,texelSize,color_depth)*mix(1.0,(float(i))/(float(rings)),bias)*p;  
				s += 1.0*mix(1.0,(float(i))/(float(rings)),bias)*p;   
			}
		}
		col /= s; //divide by sample count
	}
	
	if (showFocus)
	{
		col = debugFocus(col, blur, depth);
	}
	
	if (vignetting)
	{
		col *= vignette(uv);
	}

	return col;
}

void main()
{
	if (usePostProcessing) {
		vec4 in_color = texture2D(renderedTexture, pass_TextureCoord);
		float depth = texture2D(normalDepthTexture, pass_TextureCoord).a;
	    in_color.rgb = DoDOF(pass_TextureCoord, vec2(0.025,0.025), renderedTexture);
	    
	    out_color = in_color;
	    //out_color.rgb = in_color.rgb;
	    out_color.a =1;
	} else {
		out_color = texture2D(renderedTexture, pass_TextureCoord);
	}
}