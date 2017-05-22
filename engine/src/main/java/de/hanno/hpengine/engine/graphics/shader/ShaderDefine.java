package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.model.material.Material.MAP;

import java.util.EnumSet;

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

		appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (Config.getInstance().getRainEffect() != 0.0));
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + Config.getInstance().isUseMultipleDiffuseSamples());
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + Config.getInstance().isUseMultipleDiffuseSamplesProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + Config.getInstance().isUseConetracingForDiffuse());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + Config.getInstance().isUseConetracingForDiffuseProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR = " + Config.getInstance().isUseConetracingForSpecular());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + Config.getInstance().isUseConetracingForSpecularProbes());
		appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + Config.getInstance().isUsePrecomputedRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + Config.getInstance().isScattering());
		appendWithSemicolonAndNewLine(builder, "const bool CALCULATE_ACTUAL_RADIANCE = " + Config.getInstance().isCalculateActualRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_FADE_TO_SCREEN_BORDERS = " + Config.getInstance().isSsrFadeToScreenBorders());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_TEMPORAL_FILTERING = " + Config.getInstance().isSsrTemporalFiltering());
		appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + Config.getInstance().isUseBloom());
		appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + Config.getInstance().isUsePcf());
		appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + Config.getInstance().isUseDpsm());
		
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
