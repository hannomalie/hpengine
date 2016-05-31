package util.gui.structure;

import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import renderer.material.Material;
import util.gui.MaterialView;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

public class MaterialTable extends JTable {

    private static AbstractTableModel tableModel = new MaterialTableModel();

    private WebFrame materialViewFrame = new WebFrame("Material");

    public MaterialTable() {
        super(tableModel);
        MaterialTable self = this;

        getSelectionModel().addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event) {
                materialViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                materialViewFrame.getContentPane().removeAll();
                materialViewFrame.pack();
                materialViewFrame.setSize(600, 600);
                WebScrollPane scrollPane = new WebScrollPane(new MaterialView((Material) tableModel.getValueAt(self.getSelectedRow(), 0)));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                materialViewFrame.add(scrollPane);
                materialViewFrame.setVisible(true);
            }
        });
    }
}
