package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.directory.GameDirectory;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.engine.model.texture.Texture;
import de.hanno.hpengine.engine.model.texture.TextureManager;

public class AddTextureCommand implements Command<TextureResult> {

	String path;
	boolean srgba = false;
	protected TextureManager textureManager;
	protected GameDirectory gameDir;

	public AddTextureCommand(String path, TextureManager textureManager, GameDirectory gameDir) {
		this(path, false, textureManager, gameDir);
	}
	
	public AddTextureCommand(String path, boolean srba, TextureManager textureManager, GameDirectory gameDir) {
		this.path = path;
		this.srgba = srba;
		this.textureManager = textureManager;
		this.gameDir = gameDir;
	}
	
	@Override
	public TextureResult execute() {
		Texture texture = null;
		try {
            texture = textureManager.getTexture(path, srgba, gameDir);
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
