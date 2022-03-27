/*
 * I18N
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.nimbly.i18n.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jetbrains.annotations.NotNull;


/**
 * MassTranslationSnapView
 * User: frigoref
 * Date: 28/02/2022
 */
public class MassTranslationSnapView extends AbstractI18nSnapView {

  private final MassTranslationTable massTable = new MassTranslationTable();

  /**
   * MassTranslationSnapView
   *
   */
  public MassTranslationSnapView() {
    super(new GridLayoutManager(2, 1));
    massTable.setTranslationModel(model);
  }

  @Override
  public void initComponentsFixSpecific() {

    translationWindow = new JPanel();
    translationWindow.setLayout(new GridLayoutManager(5, 1, JBUI.insetsBottom(10), -1, -1));

    JScrollPane scroll = ScrollPaneFactory.createScrollPane(massTable, true);
    scroll.setPreferredSize(new Dimension(massTable.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 5, -1));
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setMinimumSize(new Dimension(-1, 50));

    translationWindow.add(scroll, new GridConstraints(1, 0, 1, 1,
        GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW ,
        GridConstraints.SIZEPOLICY_CAN_GROW,
        null, null,
        null));

    //---- Bottom ----
    JPanel bottom = new JPanel();
    bottom.setLayout(new GridBagLayout());  // TIPS : Use java.awt.GridBagLayout because IntelliJ layout manger went to silly spaces...

    resourcesGroup.setMinimumAndPreferredWidth(120);
    bottom.add(resourcesGroup, new GridBagConstraints(0, 0, 1, 1,
        1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,  JBUI.insets(0, 50, 0, 0), 0, 0));

    JButton exportButton = new JButton("Export");
    updateFontSize(exportButton);
    exportButton.setIcon(AllIcons.Actions.Download);
    exportButton.addActionListener(
        actionEvent -> {
          // prepare and start file chooser for export while enforcing file extension
          final String csvExtension = "csv";
          final JFileChooser fileChooser = getTableFileChooser();
          final String[] allowedExtensions =
              ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions();
          final String defaultExtension =
              (allowedExtensions.length > 0 ? allowedExtensions[0] : csvExtension);
          final String defaultFileName =
              "translation_"
                  + massTable.getSrcSelectedEntry()
                  + "_to_"
                  + massTable.getTgtSelectedEntry()
                  + "."
                  + defaultExtension;
          fileChooser.setSelectedFile(new File(defaultFileName));
          fileChooser.setDialogTitle("Export translations");
          int userSelection = fileChooser.showSaveDialog(translationWindow);

          if (userSelection == JFileChooser.APPROVE_OPTION) {
            // enforce extension
            final String selectedFileName = fileChooser.getSelectedFile().getName();
            final String[] selectedExtensions =
                ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions();
            final String usedFileExtension =
                Arrays.stream(selectedExtensions)
                    .filter(s -> selectedFileName.toLowerCase(Locale.ROOT).endsWith(s))
                    .findAny()
                    .orElse(null);
            if (usedFileExtension == null) {
              String forcedExtension;
              if (selectedExtensions.length >0) forcedExtension = selectedExtensions[0];
              else forcedExtension = csvExtension;
              fileChooser.setSelectedFile(new File(fileChooser.getSelectedFile() + "." + forcedExtension));
            }
            massTable.exportToFile(fileChooser.getSelectedFile(), LOG);
          }
        });
    bottom.add(exportButton, new GridBagConstraints(1, 0, 1, 1,
        0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,  JBUI.insets(0, 5, 0, 0), 0, 0));

    JButton importButton = new JButton("Import");
    importButton.setIcon(AllIcons.Actions.Upload);
    importButton.addActionListener(actionEvent -> {
      // prepare and start file chooser for import
      final JFileChooser fileChooser = getTableFileChooser();
      int userSelection = fileChooser.showOpenDialog(translationWindow);

      if (userSelection == JFileChooser.APPROVE_OPTION) {
        runIfNoBlockI18n(()->
          massTable.importFromFile(fileChooser.getSelectedFile(),LOG));
      }
    });
    bottom.add(importButton, new GridBagConstraints(2, 0, 1, 1,
        0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,  JBUI.insets(0, 3, 0, 43), 0, 0));

    translationWindow.add(bottom, new GridConstraints(2, 0, 1, 1,
        GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED,
        null, null, null));
  }

  @Override
  protected void updateUiComponents(final String i18nKey) {
    massTable.setTranslationModel(model);
    updateResourcesGroupButton(null);
  }

  @Override
  protected void initializeTranslationWindow() {
    if (translationWindow != null)
      this.remove(translationWindow);
  }

  @Override
  public void initComponentsDynamic() {
    // massTable is updated automatically // TODO: check whether this is also true for multiple properties files
  }

  @NotNull
  private JFileChooser getTableFileChooser() {
    final FileNameExtensionFilter xliffFilter = new FileNameExtensionFilter("XML Localization Interchanges", "xliff");
    final FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV UTF-8", "csv");

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.addChoosableFileFilter(xliffFilter);
    fileChooser.addChoosableFileFilter(csvFilter);
    fileChooser.setFileFilter(csvFilter);
    return fileChooser;
  }

  @Override
  protected void doCaretPositionChanged(final CaretEvent e) {
    // nothing to do when caret position changes
  }
/*
  class MyEditableTableModel extends AbstractTableModel
  {
    transient String[] columnTitles;
    transient Object[][] dataEntries;
    int rowCount;
    public MyEditableTableModel(String[] columnTitles, Object[][] dataEntries)
    {
      this.columnTitles = columnTitles;
      this.dataEntries = dataEntries;
    }
    @Override
    public int getRowCount()
    {
      return dataEntries.length;
    }
    @Override
    public int getColumnCount()
    {
      return columnTitles.length;
    }
    public Object getValueAt(int row, int column)
    {
      return dataEntries[row][column];
    }

    @Override
    public String getColumnName(int column) {
      return columnTitles[column];
    }

    @Override
    public Class getColumnClass(int column) {
      return getValueAt(0, column).getClass();
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      dataEntries[row][column] = value;
    }
  }

  static class MyTableHeaderRendererMenuButton extends JPanel implements TableCellRenderer {

    private int     column  = -1;
    private JTable  table   = null;
    private JPopupMenu b;

    public static MyTableHeaderRendererMenuButton getInstance(JComboBox<String> comboBox) {
      final JPopupMenu menu = new JPopupMenu("Language Menu");
      for (int i = 0; i<comboBox.getModel().getSize();i++) {
        menu.add(new JMenuItem(comboBox.getModel().getElementAt(i)));
      }
      return new MyTableHeaderRendererMenuButton("Language", menu);
    }
    public MyTableHeaderRendererMenuButton(String name, JPopupMenu menu) {
      super(new BorderLayout());
      b = menu;
      b.setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
      JLabel l = new JLabel(name);
      l.setFont(l.getFont().deriveFont(Font.PLAIN));
      l.setBorder(BorderFactory.createEmptyBorder(1,5,1,1));
      add(b, BorderLayout.WEST);
      add(l, BorderLayout.CENTER);
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {

      if (table != null && this.table != table) {
        this.table = table;
        final JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());

          header.addMouseListener(new MouseAdapter() {

            @Override
            public void  mouseClicked(MouseEvent e) {
              int col = header.getTable().columnAtPoint(e.getPoint());
              if (col != column || col == -1) return;

              int index = header.getColumnModel().getColumnIndexAtX(e.getPoint().x);
              if (index == -1) return;

              setBounds(header.getHeaderRect(index));
              header.add(MyTableHeaderRendererMenuButton.this);
              validate();

              MouseEvent e2 = SwingUtilities.convertMouseEvent(header, e, b);
              b.dispatchEvent(e2);

              b.processMouseEvent(e);
              //b.doClick();

              header.remove(MyTableHeaderRendererMenuButton.this);

              header.repaint();
            }
          });
        }
      }
      column = col;
      return this;
    }
  }

  class MyTableCellRenderer implements TableCellRenderer {

    private JTable table = null;
    private MyMouseEventReposter reporter = null;
    private JComponent editor;

    MyTableCellRenderer(JComponent editor) {
      this.editor = editor;
      this.editor.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
      if (table != null && this.table != table) {
        this.table = table;
        final JTableHeader header = table.getTableHeader();
        if (header != null) {
          this.editor.setForeground(header.getForeground());
          this.editor.setBackground(header.getBackground());
          this.editor.setFont(header.getFont());
          reporter = new MyMouseEventReposter(header, col, this.editor);
          header.addMouseListener(reporter);
        }
      }

      if (reporter != null) reporter.setColumn(col);

      return this.editor;
    }

  }
  public class MyMouseEventReposter extends MouseAdapter {

    private Component dispatchComponent;
    private JTableHeader header;
    private int column  = -1;
    private Component editor;

    public MyMouseEventReposter(JTableHeader header, int column, Component editor) {
      this.header = header;
      this.column = column;
      this.editor = editor;
    }

    public void setColumn(int column) {
      this.column = column;
    }

    private void setDispatchComponent(MouseEvent e) {
      int col = header.getTable().columnAtPoint(e.getPoint());
      if (col != column || col == -1) return;

      Point p = e.getPoint();
      Point p2 = SwingUtilities.convertPoint(header, p, editor);
      dispatchComponent = SwingUtilities.getDeepestComponentAt(editor, p2.x, p2.y);
    }

    private boolean repostEvent(MouseEvent e, boolean released) {
      if (dispatchComponent == null || released) {
        return false;
      }
      MouseEvent e2 = SwingUtilities.convertMouseEvent(header, e, dispatchComponent);
      dispatchComponent.dispatchEvent(e2);
      return true;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (header.getResizingColumn() == null) {
        Point p = e.getPoint();

        int col = header.getTable().columnAtPoint(p);
        if (col != column || col == -1) return;

        int index = header.getColumnModel().getColumnIndexAtX(p.x);
        if (index == -1) return;

        editor.setBounds(header.getHeaderRect(index));
        header.add(editor);
        editor.validate();
        setDispatchComponent(e);
        repostEvent(e, false);
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      repostEvent(e, false);
      dispatchComponent = null;
      header.remove(editor);
    }
  }
*/
}
