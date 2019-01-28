package de.hanno.hpengine.util.gui.structure;

import com.alee.laf.rootpane.WebFrame;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.texture.FileBasedSimpleTexture;
import de.hanno.hpengine.engine.model.texture.Texture;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TextureTable extends JTable {

    private final Engine engine;
    private WebFrame textureView = new WebFrame("Texture");

    public TextureTable(Engine engine) {
        super(new TextureTableModel(engine));
        this.engine = engine;

        textureView.getContentPane().setLayout(new FlowLayout());
        textureView.getContentPane().removeAll();
        textureView.pack();
        textureView.setVisible(false);
        textureView.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        getSelectionModel().addListSelectionListener(event -> {
            textureView.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            textureView.getContentPane().removeAll();
            textureView.setSize(600, 600);
            textureView.pack(); // TODO: Reenable
            String path = (String) getModel().getValueAt(TextureTable.this.getSelectedRow(), 0);
            Texture<?> valueAt = engine.getTextureManager().getTexture(path);
//            showTexture(valueAt);
            textureView.setVisible(true);
        });
    }

//    public void showTexture(Texture<?> input) {
//        if(input.getClass().isAssignableFrom(FileBasedSimpleTexture.class)) {
//            FileBasedSimpleTexture texture = (FileBasedSimpleTexture) input;
//            textureView.getContentPane().removeAll();
//            BufferedImage bufferedImage = texture.getBackingTexture().getAsBufferedImage(engine.getGpuContext());
//            JLabel label = new JLabel(new ImageIcon(bufferedImage));
//            textureView.getContentPane().add(label);
//            textureView.setPreferredSize(new Dimension(texture.getDimension().getWidth(), texture.getDimension().getHeight()));
//            textureView.setTitle(texture.getPath());
//        }
//    }
}
