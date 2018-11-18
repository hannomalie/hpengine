package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.engine.model.texture.Texture;
import de.hanno.hpengine.engine.model.texture.TextureManager;

public class AddTextureCommand implements Command<TextureResult> {

	String path;
	boolean srgba = false;
	protected TextureManager textureManager;

	public AddTextureCommand(String path, TextureManager textureManager) {
		this(path, false, textureManager);
	}
	
	public AddTextureCommand(String path, boolean srba, TextureManager textureManager) {
		this.path = path;
		this.srgba = srba;
		this.textureManager = textureManager;
	}
	
	@Override
	public TextureResult execute() {
		Texture texture = null;
		try {
            texture = textureManager.getTexture(path, srgba);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
	
	public static class TextureResult extends Result {
		Texture texture;
		private boolean successful = false;
		
		public TextureResult(Texture texture) {
			this.texture = texture;
			if(texture != null) {
				successful = true;
			}
		}

		@Override
		public boolean isSuccessful() {
			return successful;
		}
	}

}
