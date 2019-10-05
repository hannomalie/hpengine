package de.hanno.hpengine.util.gui.structure;

import com.alee.laf.rootpane.WebFrame;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D;
import de.hanno.hpengine.engine.model.texture.Texture;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TextureTable extends JTable {

    private WebFrame textureView = new WebFrame("Texture");

    public TextureTable(Engine engine) {
        super(new TextureTableModel(engine));

        textureView.getContentPane().setLayout(new FlowLayout());
        textureView.getContentPane().removeAll();
        textureView.pack();
        textureView.setVisible(false);
        textureView.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        getSelectionModel().addListSelectionListener(event -> {
            textureView.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            textureView.getContentPane().removeAll();
            textureView.setSize(600, 600);
            textureView.pack();
            String path = (String) getModel().getValueAt(TextureTable.this.getSelectedRow(), 0);
            Texture<?> valueAt = engine.getTextureManager().getTexture(path, false, engine.getConfig().getDirectories().getGameDir());
            showTexture(valueAt);
            textureView.setVisible(true);
        });
    }

    public void showTexture(Texture<?> input) {
        if(input.getClass().isAssignableFrom(FileBasedTexture2D.class)) {
            FileBasedTexture2D texture = (FileBasedTexture2D) input;
            textureView.getContentPane().removeAll();
            try {
                BufferedImage bufferedImage = ImageIO.read(new File(texture.getPath()));
                JLabel label = new JLabel(new ImageIcon(bufferedImage));
                textureView.getContentPane().add(label);
                textureView.setPreferredSize(new Dimension(texture.getDimension().getWidth(), texture.getDimension().getHeight()));
                textureView.setTitle(texture.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
