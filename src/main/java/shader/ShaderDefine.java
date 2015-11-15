package shader;

import config.Config;
import renderer.material.Material.MAP;

import java.util.EnumSet;
import java.util.Set;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.OCCLUSION),
	HEIGHT(MAP.HEIGHT),
	REFLECTION(MAP.REFLECTION),
	ROUGHNESS(MAP.ROUGHNESS);

	private String defineString;
	private MAP map;
	
	public String getDefineText() {
		return defineString;
	};
	
	private ShaderDefine(MAP map) {
		this.map = map;
		this.defineString = "#define use_" + map.shaderVariableName;
	}
	
	public static String getDefineString(EnumSet<ShaderDefine> defines) {
		if (defines == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		
		for (ShaderDefine shaderDefine : defines) {
			builder.append(shaderDefine.getDefineText());
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getGlobalDefinesString() {
		StringBuilder builder = new StringBuilder();

		appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (Config.RAINEFFECT != 0.0));
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + Config.MULTIPLE_DIFFUSE_SAMPLES);
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + Config.MULTIPLE_DIFFUSE_SAMPLES_PROBES);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + Config.USE_CONETRACING_FOR_DIFFUSE);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + Config.USE_CONETRACING_FOR_DIFFUSE_PROBES);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR = " + Config.USE_CONETRACING_FOR_SPECULAR);
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + Config.USE_CONETRACING_FOR_SPECULAR_PROBES);
		appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + Config.PRECOMPUTED_RADIANCE);
		appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + Config.SCATTERING);
		appendWithSemicolonAndNewLine(builder, "const bool CALCULATE_ACTUAL_RADIANCE = " + Config.CALCULATE_ACTUAL_RADIANCE);
		appendWithSemicolonAndNewLine(builder, "const bool SSR_FADE_TO_SCREEN_BORDERS = " + Config.SSR_FADE_TO_SCREEN_BORDERS);
		appendWithSemicolonAndNewLine(builder, "const bool SSR_TEMPORAL_FILTERING = " + Config.SSR_TEMPORAL_FILTERING);
		appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + Config.USE_BLOOM);
		appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + Config.USE_PCF);
		appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + Config.USE_DPSM);
		
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
