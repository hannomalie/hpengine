package de.hanno.hpengine.util.gui.structure;

import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.util.gui.MaterialView;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class MaterialTable extends JTable {

    private WebFrame materialViewFrame = new WebFrame("Material");

    public MaterialTable(Engine engine) {
        super(new MaterialTableModel(engine));
        MaterialTable self = this;

        getSelectionModel().addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event) {
                materialViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                materialViewFrame.getContentPane().removeAll();
                materialViewFrame.pack();
                materialViewFrame.setSize(600, 600);
                WebScrollPane scrollPane = new WebScrollPane(new MaterialView(engine, (Material) getModel().getValueAt(self.getSelectedRow(), 0)));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                materialViewFrame.add(scrollPane);
                materialViewFrame.setVisible(true);
            }
        });
    }
}
