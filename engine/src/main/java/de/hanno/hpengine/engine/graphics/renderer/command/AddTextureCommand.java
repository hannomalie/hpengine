package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.engine.model.texture.ITexture;
import de.hanno.hpengine.engine.model.texture.Texture;

public class AddTextureCommand implements Command<TextureResult> {

	String path;
	boolean srgba = false;

	public AddTextureCommand(String path) {
		this(path, false);
	}
	
	public AddTextureCommand(String path, boolean srba) {
		this.path = path;
		this.srgba = srba;
	}
	
	@Override
	public TextureResult execute(Engine engine) {
		ITexture texture = null;
		try {
            texture = engine.getTextureManager().getTexture(path, srgba);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new TextureResult(texture);
	}
	
	public static class TextureResult extends Result {
		ITexture texture;
		private boolean successful = false;
		
		public TextureResult(ITexture texture) {
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
