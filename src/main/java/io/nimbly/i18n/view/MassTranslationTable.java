package io.nimbly.i18n.view;

import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import io.nimbly.i18n.util.LoggerFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.MenuElement;
import javax.swing.SingleSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MassTranslationTable extends JTable {
  protected static final int COLUMN_KEY = 0;
  protected static final int COLUMN_VALUE_SRC = 1;
  protected static final int COLUMN_VALUE_TGT = 2;
  protected static final int COLUMN_KEY_NEW_CSV = 3;
  private static final Logger LOG =  LoggerFactory.getInstance(MassTranslationTable.class);
  final MyPopupMenu srcPopupMenu = new MyPopupMenu();
  final MyPopupMenu tgtPopupMenu = new MyPopupMenu();
  boolean inSyncWithTranslationModel = false;
  protected transient TranslationModel model = null;
  transient Map<Integer, Color> rowColor = new HashMap<>();
  protected ArrayList<String> langKeyList = new ArrayList<>();
  final DefaultTableModel tableModel =
      new DefaultTableModel(0, 3) {
        @Override
        public boolean isCellEditable(final int row, final int column) {
          return column == 2; // make columns 0 and 1 read only
        }
      };

  public MassTranslationTable() {
    super();

    this.setModel(tableModel);

    // Header
    final TableColumnModel tableHeaderColumnModel = this.getTableHeader().getColumnModel();
    final TableColumn columnHeaderSourceKey = tableHeaderColumnModel.getColumn(0);
    columnHeaderSourceKey.setHeaderValue("Source Key");
    final DefaultTableCellRenderer headerCellRenderer = new DefaultTableCellRenderer();
    headerCellRenderer.setHorizontalAlignment(SwingConstants.LEFT);
    columnHeaderSourceKey.setHeaderRenderer(headerCellRenderer);
    final TableColumn columnHeaderSourceValue = tableHeaderColumnModel.getColumn(1);
    columnHeaderSourceValue.setHeaderValue("Source Value");
    columnHeaderSourceValue
        .setHeaderRenderer(new MenuButtonTableHeaderRenderer("Source Value: ", srcPopupMenu));
    final TableColumn columnHeaderTargetValue = tableHeaderColumnModel.getColumn(2);
    columnHeaderTargetValue.setHeaderValue("Target Value");
    columnHeaderTargetValue
        .setHeaderRenderer(new MenuButtonTableHeaderRenderer("Target Value: ", tgtPopupMenu));
    // Change Listener for popup menus
    srcPopupMenu.addChangeListener(
        changeEvent -> buildTableModelFromPropertiesFiles()
        );
    tgtPopupMenu.addChangeListener(
        changeEvent -> buildTableModelFromPropertiesFiles()
        );

    MyTableCellListener.register(this, new AbstractAction()
        {
          public void actionPerformed(ActionEvent e)
          {
            MyTableCellListener tcl = (MyTableCellListener)e.getSource();
            final Object newCellValue = tcl.getNewValue();
            if (tcl.getOldValue().equals(newCellValue))
              return; // no cell value change

            LOG.info("Key   : " + langKeyList.get(tcl.getRow())+System.lineSeparator()+
                "Old   : " + tcl.getOldValue()+System.lineSeparator()+
                "New   : " + tcl.getNewValue());

            final int column = tcl.getColumn();
            switch (column) {
              case COLUMN_KEY ->
                model.renameKey((String) tcl.getOldValue(), (String)  newCellValue);
              case COLUMN_VALUE_TGT ->
                model.updateTranslation(tgtPopupMenu.getSelectedEntry(),(String) newCellValue);
              default -> {
                return; // no relevant column changed (should not occur)
              }
            }

            inSyncWithTranslationModel = false;
          }
        }
    );
  }

  private void buildTableModelFromPropertiesFiles() {
    final String srcLang = getSrcSelectedEntry();
    final String tgtLang = getTgtSelectedEntry();
    if (srcLang == null || tgtLang == null)
      return;
    final List<PropertyImpl> srcProperties = (List<PropertyImpl>)(List<?>) model.getPsiPropertiesAll(srcLang);

    Map<String, String> tgtKeyValue = getTgtHashMap();

    langKeyList.clear();
    tableModel.setRowCount(0);
    rowColor.clear();
    srcProperties.forEach(
        srcProperty -> {
          String[] lineData = new String[3];
          lineData[COLUMN_KEY] = srcProperty.getKey();
          lineData[COLUMN_VALUE_SRC] = srcProperty.getValue();
          lineData[COLUMN_VALUE_TGT] = tgtKeyValue.get(srcProperty.getKey());
          if (lineData[COLUMN_VALUE_SRC].equals(lineData[COLUMN_VALUE_TGT])) rowColor.put(tableModel.getRowCount(), JBColor.BLACK);
          if (lineData[COLUMN_VALUE_TGT] == null) lineData[COLUMN_VALUE_TGT] = "<translation needed>";
          tableModel.addRow(lineData);
          langKeyList.add(lineData[COLUMN_KEY]);
        });
    tableModel.fireTableDataChanged();
    inSyncWithTranslationModel = true;
  }

  public String getTgtSelectedEntry() {
    return tgtPopupMenu.getSelectedEntry();
  }

  public String getSrcSelectedEntry() {
    return srcPopupMenu.getSelectedEntry();
  }

  @Override
  public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
  {
    Component c = super.prepareRenderer(renderer, row, column);

    if (!isRowSelected(row))
    {
      Color color = rowColor.get( row );
      c.setBackground(color == null ? getBackground() : color);
    }

    return c;
  }

  protected void setTranslationModel(@NotNull final TranslationModel translationModel) {
    if (translationModel.equals(model))
      return; // no new model
    this.model = translationModel;

    srcPopupMenu.removeAll();
    tgtPopupMenu.removeAll();
    model
        .getLanguages()
        .forEach(
            lang -> {
              srcPopupMenu.add(lang);
              tgtPopupMenu.add(lang);
            });

    final SingleSelectionModel srcSelectionModel = srcPopupMenu.getSelectionModel();
    if (srcPopupMenu.getComponents().length > 0) {
      if (tgtPopupMenu.getComponents().length > 1)
        tgtPopupMenu.getSelectionModel().setSelectedIndex(1);
      else
        tgtPopupMenu.getSelectionModel().setSelectedIndex(0);
      srcSelectionModel.setSelectedIndex(0);
    }
    inSyncWithTranslationModel = true;
  }
  public void importFromFile(final File fileForImport, final Logger logger) {
    try {

      if (fileForImport.getName().endsWith(".xliff"))
        fillFromFileXliff(fileForImport);
      else if (fileForImport.getName().endsWith(".csv"))
        fillFromFileCsv(fileForImport);
      else
        throw new IOException("Import only from CSV or XLIFF files supported");

      logger.info("Import from file: " + fileForImport.getAbsolutePath());
      SwingUtilities.invokeAndWait(() -> {});
      TimeUnit.SECONDS.sleep(1);
    } catch (IOException e) {
      logger.error("Import from file failed: " + fileForImport.getAbsolutePath(),e);
    } catch (InvocationTargetException|InterruptedException e) {
      e.printStackTrace();
      Thread.currentThread().interrupt();
    }
  }

  private void fillFromFileXliff(final File fileForImport) throws IOException {
    final MyXliffParsingHandler parsingHandler = new MyXliffParsingHandler();
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    try {
      saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // no DOCTYPE declarations needed in xliff files
      final SAXParser saxParser = saxParserFactory.newSAXParser();
      saxParser.parse(fileForImport, parsingHandler);
    } catch (SAXException|ParserConfigurationException e) {
      throw new IOException(e);
    }

    final Map<Integer, String> newTranslations = parsingHandler.getNewTranslations();
    if (newTranslations.size() != langKeyList.size())
      throw new IOException("Only " + newTranslations.size() + " translations provided for "+langKeyList.size()+" existing keys");

    updateTgtColumn(newTranslations);
  }


  private void fillFromFileCsv(final File fileForImport) throws IOException {

    Map<Integer, String> newTranslations = new HashMap<>();
    Map<Integer, String> newKeys = new HashMap<>();

    InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(fileForImport));
    try( Scanner scanner = new Scanner(inputStreamReader)) {
      if (scanner.hasNextLine())
        getFullCsvLineFromScanner(scanner); // ignore header line
      while (scanner.hasNextLine()) {
        parseCsvLine(getFullCsvLineFromScanner(scanner), newTranslations, newKeys);
      }

      if (newTranslations.size() != langKeyList.size())
        throw new IOException("Only " + newTranslations.size() + " translations provided for "+langKeyList.size()+" existing keys");

      if (!newKeys.isEmpty() && newKeys.size() != langKeyList.size())
          throw new IOException("Only " + newKeys.size() + " new keys provided for "+langKeyList.size()+" existing keys");

      updateTgtColumn(newTranslations);
      if (!newKeys.isEmpty())
        updateKeyColumn(newKeys);

    }
  }

  /**
   * Collects full line that gets potentially split by {@link Scanner#nextLine()}
   * and adds \n back again. A line is considered full if the number of " is even.
   * @param scanner Scanner object
   * @return Full line including \n values again
   */
  private String getFullCsvLineFromScanner(final Scanner scanner) {
    StringBuilder csvLine = new StringBuilder();
    csvLine.append(scanner.nextLine());
    while(StringUtils.countMatches(csvLine.toString(), "\"") % 2 != 0 && scanner.hasNextLine()) {
      csvLine.append( "\n");
      csvLine.append(scanner.nextLine());
    }
    return csvLine.toString();
  }

  /**
   * Expects a line in either of the 2 formats<br/>
   * (1) &lt;known key&gt;,&lt;source translation&gt;,&lt;target translation&gt;<br/>
   * (2) &lt;known key&gt;,&lt;source translation&gt;,&lt;target translation&gt;,&lt;new key&gt;<br/>
   * to update determine 2 maps to update the current table model.
   * <p>The &lt;known key&gt; is used to determine the row number in the current table model.</p>
   * <p>The &lt;target translation&gt; is put into map {@code newTranslations}.</p>
   * <p>The &lt;new translation&gt; is, if present, put into map {@code newKeys}.</p>
   * @param csvLine Line to be parsed
   * @param newTranslations Map of line number->translation for the table
   * @param newKeys Map of line number->new key for the table
   * @throws IOException Own exceptions when line does not match the expected pattern.
   */
  private void parseCsvLine(final String csvLine, final Map<Integer, String> newTranslations, final Map<Integer, String> newKeys) throws IOException {
    final List<String> csvStrings =
        Arrays.asList(csvLine.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)", -1)); // \s*,\s*
    
    final int csvStringsSize = csvStrings.size();
    if (csvStringsSize >= 3) { // at least 3 columns expected
      final String fileKey = csvStrings.get(COLUMN_KEY);
      final int rowOfKey = langKeyList.indexOf(fileKey);
      if (rowOfKey >= 0) { // only existing keys will be handled, so new keys will be omitted
        if (newTranslations.put(rowOfKey, StringEscapeUtils.unescapeCsv(csvStrings.get(COLUMN_VALUE_TGT))) != null)
          throw new IOException("Multiple updates for key " + fileKey + " (line: "+ csvLine +")");
        if (csvStringsSize > 3)
          newKeys.put(rowOfKey, csvStrings.get(COLUMN_KEY_NEW_CSV));
      }
      else
        throw new IOException("Key "+fileKey+" not found in CSV line: " + csvLine);
    }
    else
      throw new IOException("Insufficient number of columns ("+csvStringsSize+") available in CSV line: " + csvLine);
  }

  private void updateTgtColumn(final Map<Integer, String> columnRows) {
    updateColumn(columnRows, COLUMN_VALUE_TGT);
  }

  private void updateKeyColumn(final Map<Integer, String> columnRows) {
    updateColumn(columnRows, COLUMN_KEY);
  }

  private void updateColumn(final Map<Integer, String> columnRows, final int colum) {
    SwingUtilities.invokeLater(() -> {
      for (Map.Entry<Integer, String> row : columnRows.entrySet())
        this.setValueAt(row.getValue(),row.getKey(), colum);
    });
  }

  @NotNull
  public Map<String, String> getSrcHashMap() {
    return getHashMapFromPopupMenu(srcPopupMenu);
  }

  @NotNull
  public Map<String, String> getTgtHashMap() {
    return getHashMapFromPopupMenu(tgtPopupMenu);
  }

  @NotNull
  public Map<String, String> getHashMapFromPopupMenu(final MyPopupMenu popupMenu) {
    final HashMap<String, String> langKeyValue = new HashMap<>();
    if (model != null && popupMenu != null) {
      final String lang = popupMenu.getSelectedEntry();
      if (lang != null) {
        final List<PropertyImpl> langProperties = new ArrayList<>((List<PropertyImpl>) (List<?>) model.getPsiPropertiesAll(lang));
        langProperties.forEach(
            srcProperty -> langKeyValue.put(srcProperty.getKey(), srcProperty.getValue()));
      }
    }
    return langKeyValue;
  }

  public void exportToFile(final File fileToExport, final Logger logger) {
    try {

      if (fileToExport.getName().endsWith(".xliff"))
        writeFileXliff(fileToExport);
      else if (fileToExport.getName().endsWith(".csv"))
        writeFileCsv(fileToExport);
      else
        throw new IOException("Export only as CSV or XLIFF files supported");

      logger.info("Export to file: " + fileToExport.getAbsolutePath());
    } catch (IOException e) {
      logger.error("Export to file failed: " + fileToExport.getAbsolutePath(),e);
      JOptionPane.showMessageDialog(null,"Export to file failed: " + fileToExport.getAbsolutePath(), "Export failed", JOptionPane.WARNING_MESSAGE);
    }
  }

  /**
   * The method writes an XLIFF file in the format of XLIFF Version 1.2
   * http://docs.oasis-open.org/xliff/v1.2/os/xliff-core.html
   * @param fileToExport file to export the content to
   * @throws IOException In case the file writing is experiencing some problems
   */
  private void writeFileXliff(final File fileToExport) throws IOException {
    try(final FileWriter fileWriter = new FileWriter(fileToExport)) {
      final String lineSeparator = System.lineSeparator();
      // header
      fileWriter.write(
          "<xliff version='1.2' xmlns='urn:oasis:names:tc:xliff:document:1.2'>"
              + lineSeparator
              + "<file original='"
              + fileToExport.getName()
              + "' source-language='"+srcPopupMenu.getSelectedEntry()
              +"' target-language='"+tgtPopupMenu.getSelectedEntry()+"'"
              + lineSeparator
              + "       datatype='plaintext'>"
              + lineSeparator
              + "<body>"
              + lineSeparator);

      // body content
      for (int row = 0; row < this.getRowCount(); row++) {
        for (int col = 0; col < this.getColumnCount(); col++) {
          final String cellString = this.getValueAt(row, col).toString();
          switch (col) {
            case COLUMN_KEY -> fileWriter.write("<trans-unit id='" + cellString + "'>" + lineSeparator);
            case COLUMN_VALUE_SRC -> fileWriter.write("<source>" + StringEscapeUtils.escapeHtml(cellString) + "</source>" + lineSeparator);
            case COLUMN_VALUE_TGT -> fileWriter.write("<target>" + StringEscapeUtils.escapeHtml(cellString) + "</target>" + lineSeparator);
            default -> {
            }
          }

        }
        fileWriter.write("</trans-unit>"+lineSeparator);
      }
      // footer
      fileWriter.write("</body>" + System.lineSeparator() + "</file>" + System.lineSeparator()+ "</xliff>");
      }
  }

  private void writeFileCsv(final File fileToExport) throws IOException {

    try(final FileWriter fileWriter = new FileWriter(fileToExport)) {
      final String separator = ",";
      // header
      final TableColumnModel columnModel = this.getTableHeader().getColumnModel();
      for (int curCol = 0; curCol < columnModel.getColumnCount(); curCol++) {
        fileWriter.write(StringEscapeUtils
            .escapeCsv((String) columnModel.getColumn(curCol).getHeaderValue())+ separator);
      }
      fileWriter.write(System.lineSeparator());
      // content
      for (int curRow = 0; curRow < this.getRowCount(); curRow++) {
        final int lastCol = this.getColumnCount() - 1;
        for (int curCol = 0; curCol < lastCol; curCol++) {
          fileWriter.write(StringEscapeUtils.escapeCsv(this.getValueAt(curRow, curCol).toString()) + separator);
          //fileWriter.write(StringEscapeUtils.escapeJava(StringEscapeUtils.escapeCsv(this.getValueAt(curRow, curCol).toString())) + separator);
          //StringEscapeUtils.escapeCsv(fileWriter,this.getValueAt(i, j).toString());
          //fileWriter.write(separator);
        }
        fileWriter.write(StringEscapeUtils.escapeCsv(this.getValueAt(curRow, lastCol).toString()));
        fileWriter.write(System.lineSeparator());
      }
    }
  }

  static class MyPopupMenu extends JPopupMenu {
    protected EventListenerList eventListenerList = new EventListenerList();

    private String selectedEntry = null;

    public MyPopupMenu() {
      super();
      final MenuElement[] menuElem = MyPopupMenu.this.getSubElements();
      for (final MenuElement menuElement : menuElem) {
        if (menuElement instanceof JMenuItem) {
          ((JMenuItem) menuElement)
              .addActionListener(e -> {
                JMenuItem menuItem = (JMenuItem) e.getSource();
                if (!menuItem.getText().equals(selectedEntry)) {
                  selectedEntry = menuItem.getText();
                  MyPopupMenu.this.setSelected(menuItem);
                  fireStateChanged();
                }
              });
        }
      }

      getSelectionModel()
          .addChangeListener(
              changeEvent -> {
                final int selectedIndex = this.getSelectionModel().getSelectedIndex();
                if (selectedIndex < 0) return;
                JMenuItem menuItem = (JMenuItem) MyPopupMenu.this.getComponent(selectedIndex);
                selectedEntry = menuItem.getText();
                fireStateChanged();
              });

    }

    public String getSelectedEntry() {
      return selectedEntry;
    }

    protected void fireStateChanged() {
      Object[] listeners = this.eventListenerList.getListenerList();
      final ChangeEvent changeEvent = new ChangeEvent(this);
      for (int i = listeners.length - 2; i >= 0; i -= 2) {
        if (listeners[i] == ChangeListener.class) {
          ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
        }
      }
    }

    public void addChangeListener(ChangeListener l) {
      this.eventListenerList.add(ChangeListener.class, l);
    }

    public void removeChangeListener(ChangeListener l) {
      this.eventListenerList.remove(ChangeListener.class, l);
    }
    public void removeChangeListenerAll() {
      Object[] listeners = this.eventListenerList.getListenerList();
      for (int i = listeners.length - 2; i >= 0; i -= 2) {
        if (listeners[i] == ChangeListener.class) {
          removeChangeListener((ChangeListener) listeners[i + 1]);
        }
      }
    }
  }

  class MenuButtonTableHeaderRenderer extends JPanel implements TableCellRenderer {

    private final MenuButton b;
    private int column = -1;
    private JTable table = null;

    MenuButtonTableHeaderRenderer(final String name, final JPopupMenu menu) {
      super(new BorderLayout());

      final JLabel l = new JLabel(name);
      l.setFont(l.getFont().deriveFont(Font.PLAIN));
      l.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 1));
      add(l, BorderLayout.WEST);

      b = new MenuButton(name, menu);
      b.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      add(b, BorderLayout.EAST);

      menu.addPopupMenuListener(
          new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
              MenuButtonTableHeaderRenderer.this.repaint();
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
              //final String selectedEntry = ((MyPopupMenu) menu).getSelectedEntry();
              //if (selectedEntry == null) return;
              //b.setText();
              //b.updateTextFromSelected();
              MenuButtonTableHeaderRenderer.this.repaint();
            }

            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
              MenuButtonTableHeaderRenderer.this.repaint();
            }
          });
    }

    @Override
    public Component getTableCellRendererComponent(
        final JTable table,
        final Object value,
        final boolean isSelected,
        final boolean hasFocus,
        final int row,
        final int col) {

      if (table != null && this.table != table) {
        this.table = table;
        final JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());

          header.addMouseListener(
              new MouseAdapter() {

                @Override
                public void mouseClicked(final MouseEvent e) {
                  final int col = header.getTable().columnAtPoint(e.getPoint());
                  if (col != column || col == -1) return;

                  final int index = header.getColumnModel().getColumnIndexAtX(e.getPoint().x);
                  if (index == -1) return;

                  setBounds(header.getHeaderRect(index));
                  header.add(MenuButtonTableHeaderRenderer.this);
                  validate();

                  b.doClick();

                  header.remove(MenuButtonTableHeaderRenderer.this);

                  header.repaint();
                }
              });
        }
      }
      column = col;
      return this;
    }
  }

  public class MenuButton extends JToggleButton {
    final JPopupMenu popup;
    public MenuButton(final String name, final JPopupMenu menu) {
      super(name);
      this.popup = menu;
      final MenuElement[] menuElem = popup.getSubElements();
      for (final MenuElement menuElement : menuElem) {
        if (menuElement instanceof JMenuItem) {
          ((JMenuItem) menuElement)
              .addActionListener(
                  e -> {
                    //popup.setSelected((Component) e.getSource());
                    //updateTextFromSelected();
                  });
        }
      }
      addActionListener(
          ev -> {
            final JToggleButton b = MenuButton.this;
            if (b.isSelected()) {
              popup.show(b, 0, b.getBounds().height);
            } else {
              popup.setVisible(false);
            }
          });
      ((MyPopupMenu) popup)
          .addChangeListener(
              changeEvent -> {
                final String selectedEntry = ((MyPopupMenu) menu).getSelectedEntry();
                if (selectedEntry != null) MenuButton.this.setText(selectedEntry);
                if (popup.getParent() != null) {
                  popup.getParent().repaint(); // cause header to be repainted
                  if (popup.getParent().getParent() != null) {
                    popup.getParent().getParent().repaint(); // cause header to be repainted
                  }
                }
              });
      popup.addPopupMenuListener(
          new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
              // nothing to do when popup becomes visible
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
              MenuButton.this.setSelected(false);
            }

            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
              // nothing to do when popup is canceled
            }
          });
    }

    public void updateTextFromSelected() {
      final int selectedIndex = popup.getSelectionModel().getSelectedIndex();
      if (selectedIndex < 0) return;
      final Component selectedComponent = popup.getComponent(selectedIndex);
      if (selectedComponent instanceof JMenuItem) {
        this.setText(((JMenuItem) selectedComponent).getText());
        popup.getParent().getParent().repaint(); // cause header to be repainted
      }
    }
  }

  /*
   *  This class listens for changes made to the data in the table via the
   *  TableCellEditor. When editing is started, the value of the cell is saved
   *  When editing is stopped the new value is saved. When the old and new
   *  values are different, then the provided Action is invoked.
   *
   *  The source of the Action is a TableCellListener instance.
   */
  public static class MyTableCellListener implements PropertyChangeListener, Runnable
  {
    private final JTable table;
    private Action action;

    private int row;
    private int column;
    private Object oldValue;
    private Object newValue;

    /**
     *  Create a TableCellListener.
     *
     *  @param table   the table to be monitored for data changes
     *  @param action  the Action to invoke when cell data is changed
     */
    private MyTableCellListener(JTable table, Action action)
    {
      this.table = table;
      this.action = action;
      this.table.addPropertyChangeListener( this );
    }

    public static void register(JTable table, Action action) {
      final MyTableCellListener tcl = new MyTableCellListener(table, action);
      table.addPropertyChangeListener( tcl );
    }

    /**
     *  Create a TableCellListener with a copy of all the data relevant to
     *  the change of data for a given cell.
     *
     *  @param row  the row of the changed cell
     *  @param column  the column of the changed cell
     *  @param oldValue  the old data of the changed cell
     *  @param newValue  the new data of the changed cell
     */
    private MyTableCellListener(JTable table, int row, int column, Object oldValue, Object newValue)
    {
      this.table = table;
      this.row = row;
      this.column = column;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    /**
     *  Get the column that was last edited
     *
     *  @return the column that was edited
     */
    public int getColumn()
    {
      return column;
    }

    /**
     *  Get the new value in the cell
     *
     *  @return the new value in the cell
     */
    public Object getNewValue()
    {
      return newValue;
    }

    /**
     *  Get the old value of the cell
     *
     *  @return the old value of the cell
     */
    public Object getOldValue()
    {
      return oldValue;
    }

    /**
     *  Get the row that was last edited
     *
     *  @return the row that was edited
     */
    public int getRow()
    {
      return row;
    }

    /**
     *  Get the table of the cell that was changed
     *
     *  @return the table of the cell that was changed
     */
    public JTable getTable()
    {
      return table;
    }
    //
//  Implement the PropertyChangeListener interface
//
    @Override
    public void propertyChange(PropertyChangeEvent e)
    {
      //  A cell has started/stopped editing
      if ("tableCellEditor".equals(e.getPropertyName()))
      {
        if (table.isEditing())
          processEditingStarted();
        else
          processEditingStopped();
      }
    }

    /*
     *  Save information of the cell about to be edited
     */
    private void processEditingStarted()
    {
      //  The invokeLater is necessary because the editing row and editing
      //  column of the table have not been set when the "tableCellEditor"
      //  PropertyChangeEvent is fired.
      //  This results in the "run" method being invoked
      SwingUtilities.invokeLater( this );
    }
    /*
     *  See above.
     */
    @Override
    public void run()
    {
      row = table.convertRowIndexToModel( table.getEditingRow() );
      column = table.convertColumnIndexToModel( table.getEditingColumn() );
      oldValue = table.getModel().getValueAt(row, column);
      newValue = null;
    }

    /*
     *	Update the Cell history when necessary
     */
    private void processEditingStopped()
    {
      newValue = table.getModel().getValueAt(row, column);

      //  The data has changed, invoke the supplied Action
      if (! newValue.equals(oldValue))
      {
        //  Make a copy of the data in case another cell starts editing
        //  while processing this change

        MyTableCellListener myTableCellListener = new MyTableCellListener(
            getTable(), getRow(), getColumn(), getOldValue(), getNewValue());

        ActionEvent event = new ActionEvent(
            myTableCellListener,
            ActionEvent.ACTION_PERFORMED,
            "");
        action.actionPerformed(event);
      }
    }
  }

  private static final int TAG_INITIAL = 0;
  private static final int TAG_FILE = 1;
  private static final int TAG_TRANS_UNIT = 2;
  // private static final int TAG_SOURCE = 3;
  private static final int TAG_TARGET = 4;


  /**
   * Parser handler that start each specific element inside the provided document that is expected to look like:
   * <pre>{@code <xliff version='1.2' xmlns='urn:oasis:names:tc:xliff:document:1.2'>
   * <file original='hello.txt' source-language='en' target-language='fr'  datatype='plaintext'>
   * <body>
   * <trans-unit id='key.HelloWorld'>
   * <source>Hello world</source>
   * <target>Bonjour le monde</target>
   * <alt-trans>
   * <target xml:lang='es'>Hola mundo</target>
   * </alt-trans>
   * </trans-unit>
   * </body>
   * </file>
   * </xliff>}</pre>
   * Based on the key provided in {@code &lt;trans-unit&gt;} tag's attribute {@code id}
   * the row in the current table is identified and the content of {@code &lt;target&gt;}
   * is put as the new translation into map {@link #newTranslations}.
   */
  class MyXliffParsingHandler extends DefaultHandler
  {

    private final Map<Integer, String> newTranslations = new HashMap<>();
    private String currentKey = null;
    //private String currentTranslation = null;
    private StringBuilder currentTranslation = new StringBuilder();

    public Map<Integer, String> getNewTranslations() {
      return newTranslations;
    }

    private int tag = 0;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
      if (qName.equalsIgnoreCase("trans-unit"))
      {
        tag = TAG_TRANS_UNIT;
        currentKey = attributes.getValue("id");
      }
      else if (qName.equalsIgnoreCase("target"))
      {
        if (attributes.getLength() == 0 || attributes.getValue("xml:lang").equals(tgtPopupMenu.getSelectedEntry()))
          tag = TAG_TARGET;
      }
      else if(qName.equalsIgnoreCase("file"))
      {
        if (!attributes.getValue("target-language").equals(tgtPopupMenu.getSelectedEntry()))
          throw new SAXException("Expected tag file to have value '"+tgtPopupMenu.getSelectedEntry()+"' for attribute target-language, but '"+ attributes.getValue("target-language") + "' provided");
        tag = TAG_FILE;
      }
      else tag = TAG_INITIAL;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      switch (tag) {
        case TAG_TRANS_UNIT -> currentKey = null;
        case TAG_TARGET -> {
          final int rowOfKey = langKeyList.indexOf(currentKey);
          if (rowOfKey >= 0) { // new keys will be omitted
            if (newTranslations.put(rowOfKey, currentTranslation.toString()) != null)
              throw new SAXException("'" + currentTranslation + "' is second update for key " + currentKey + " (in tag target)");
          } else
            throw new SAXException("No key " + (currentKey == null ? "null" : currentKey) + " exists to add translation "
                + currentTranslation);
          currentTranslation = new StringBuilder();
        }
        default -> {}
      }
      tag = TAG_INITIAL;
    }

    /**
     * Reads the text value of the currently parsed element
     *
     * @param ch Character array
     * @param start Start index in ch
     * @param length Length of string in ch
     * @throws SAXException Exception in process (not expected)
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (tag == TAG_TARGET) {
        currentTranslation.append(new String(ch, start, length));
      }
    }
  }
}
