package io.nimbly.i18n.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.nimbly.i18n.util.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

class MassTranslationTableTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp(); // throws java.lang.NoClassDefFoundError: kotlin/jdk7/AutoCloseableKt
    temporaryFolder.create();
  }
  /**
   * This folder and the files created in it will be deleted after
   * tests are run, even in the event of failures or exceptions.
   */
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  void testCsvExportAndImport() {
    final String fileName = "csvExport.csv";
    validateExportAndImport(fileName);
  }
  @Test
  void testXliffExportAndImport() {
    final String fileName = "xliffExport.xliff";
    validateExportAndImport(fileName);
  }

  private void validateExportAndImport(final String fileName) {
    final String[][] testDataExportImport = {
      {"test.Key1", "My test","Mein Test"},
      {"test.Key2", "My comma test, easy", "Mein Kommatest, einfach"},
      {"test.Key3", "File\\: {0}", "Datei\\: {0}"},
      {"test.Key4", "First line<br/>second line", "Erste Zeile<br/>Zweite Zeile"},
      {"test.Key5", "First line\nsecond line", "Erste Zeile\nZweite Zeile"},
      {"test.Key6", "", ""}
    };
    final MyMassTranslationTable table = new MyMassTranslationTable(testDataExportImport);
    final File file = getTemporaryFile(fileName);
    Assertions.assertNotNull(file);
    final long fileLengthBefore = file.length();
    final Logger logger = LoggerFactory.getInstance(this.getClass());
    table.exportToFile(file, logger);
    final long fileLengthAfter = file.length();
    Assertions.assertNotSame(fileLengthBefore, fileLengthAfter, "File length unchanged after export");
    initTranslations(table);
    table.importFromFile(file, logger);
    assertTableHasSameData(table,testDataExportImport);
  }

  @Test
  void testCsvImport() {
    final String fileName = "testImport.csv";
    validateImport(fileName);
  }
  @Test
  void testCsvImportInclKeyUpdate() {
    final String fileName = "testImportKeyUpdate.csv";
    validateImport(fileName, true);
  }
  @Test
  void testXliffImport() {
    final String fileName = "testImport.xliff";
    validateImport(fileName);
  }

  private void validateImport(final String fileName) {
    validateImport(fileName,false);
  }
  private void validateImport(final String fileName, final boolean updateKeys) {
    final String[][] testData = new String[][]{
        {"test.Key1", "", "Mein Test"},
        {"test.Key2", "", "Mein Kommatest, einfach"}};
    final MyMassTranslationTable table = new MyMassTranslationTable(testData);
    initTranslations(table);
    table.importFromFile(new File(getTestDataPath()+File.separator+ fileName), LoggerFactory.getInstance(MassTranslationTableTest.class));
    if (updateKeys) {
      final String[] updKeys = new String[] {"test.UpdKey1","test.UpdKey2"};
      for (int i = 0 ; i<testData.length ;++i) testData[i][0] = updKeys[i];
    }
    assertTableHasSameData(table,testData);
  }

  private void assertTableHasSameData(final MyMassTranslationTable table, final String[][] testDataExportImport) {
    try {
      SwingUtilities.invokeAndWait(() ->
        table.assertHasSameData(testDataExportImport));
    } catch (InterruptedException|InvocationTargetException e) {
      e.printStackTrace();
      Assertions.assertNull(e);
    }
  }

  private void initTranslations(final MassTranslationTable table) {
    for (int row = 0; row < table.getRowCount() ; row++) {
      table.setValueAt("", row, MassTranslationTable.COLUMN_VALUE_TGT);
    }
  }

  private File getTemporaryFile(final String fileName) {
    try {
      return temporaryFolder.newFile( fileName );
    }
    catch( IOException e ) {
      System.err.println(
          "error creating temporary test file in " +
              this.getClass().getSimpleName() );
      Assertions.assertNull(e);
      return null;
    }
  }
  static class MyMassTranslationTable extends MassTranslationTable {
    public MyMassTranslationTable(@NotNull String[][] data) {
      super();
      // Initialize table with selected target language and provided data (incl. keys)
      tgtPopupMenu.removeChangeListenerAll();
      tgtPopupMenu.add("de");
      tgtPopupMenu.getSelectionModel().setSelectedIndex(0);

      tableModel.setRowCount(0);
      if (data.length<=0) {
        return;
      }
      Arrays.stream(data).forEach(line -> {
          if (line.length != 3) return;
          langKeyList.add(line[0]);
          tableModel.addRow(line);
        }
      );
    }

    public void assertHasSameData(String[][] data) {
      Assertions.assertNotNull(data,"No data provided");
      final int rowCount = this.getRowCount();
      final int columnCount = this.getColumnCount();
      Assertions.assertEquals(rowCount, data.length,"Row count not matching");
      Assertions.assertEquals(columnCount, data[0].length,"Column count not matching");
      for (int row = 0; row < rowCount; row++) {
        for (int col = 0; col < columnCount; col++) {
          Assertions.assertEquals(data[row][col], this.getValueAt(row, col),"Mismatch at ["+row+"]["+col+"]");
        }
      }
    }
  }
}