package io.nimbly.i18n.view;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.ActionLink;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThrowableRunnable;
import io.nimbly.i18n.util.I18nUtil;
import io.nimbly.i18n.util.LoggerFactory;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import kotlin.Triple;
import org.jetbrains.annotations.Nullable;

/**
 * AbstractI18nSnapView
 * User: frigoref
 * Date: 28/02/2022
 */
public abstract class AbstractI18nSnapView extends AbstractSnapView  {
  public static final String BLOCK_REFRESH = "BLOCK_REFRESH";
  public static final String BLOCK_I18N = "BLOCK_I18N";
  protected static Logger LOG = LoggerFactory.getInstance(AbstractI18nSnapView.class);
  protected final ComboBox<MyPropertiesFileInfo> resourcesGroup = new ComboBox<>();

  transient TranslationModel model = TranslationModel.getInstance();
  protected ActionLink[] flags;
  protected JPanel translationWindow;
  protected boolean isSelected = false;

  private boolean isDirty = true;
  private boolean isInitDone = false;

  /**
   * Updates the translation text in the TranslationModel.
   * @param i18nView view containing the model to be updated
   * @param language language to be updated
   * @param translation new translation
   */
  protected static void updateTranslation(final AbstractI18nSnapView i18nView, final String language, final String translation) {
      i18nView.runIfNoBlockI18n(()-> PsiDocumentManager.getInstance(i18nView.model.getModule().getProject()).performLaterWhenAllCommitted(
          () -> {
              try {
                  SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) () ->
                      i18nView.model.updateTranslation(language, translation));
              } catch (Throwable ee) {
                  LOG.error("Error in updateTranslation", ee);
              }

          }
      ));
  }

  protected void runIfNoBlockI18n(final Runnable run) {
    final Boolean block = (Boolean) this.getClientProperty(BLOCK_I18N);
    if (!Boolean.TRUE.equals(block)) {
      run.run();
    }
  }

  public void setSelected(final boolean selected) {
    final boolean selectionChanged =  isSelected != selected;
    isSelected = selected;
    if (selectionChanged && isSelected) updateUiOrFlagDirty();
  }

  public static void updateFontSize(final JComponent component) {
    component.getFont().deriveFont(component.getFont().getStyle(),
        component.getFont().getSize() -2f);
  }

  public static boolean isPathFile(final String pathString) {
    if (pathString == null ) return false;
    return pathString.indexOf('.') > 0;
  }

  protected AbstractI18nSnapView(LayoutManager mgr) {
    super();
    // init UI
    setLayout(mgr);

    model.runWhenChange(this::updateUiOrFlagDirty);
    model.runWhenComplete(this::updateUiOrFlagDirty);
  }

  protected void updateUiOrFlagDirty() {
    if (isSelected && isDirty)
      uiLoadTranslationFromI18nKey(model.getSelectedKey());
    else
      setDirty();
  }

  protected void setDirty() {
    isDirty = true;
  }

  /**
   * Initialize fix components.
   * Executed only once.
   * Subclasses add their components via implementation of {@link AbstractI18nSnapView#initComponentsFixSpecific()}
   */
  private void initComponentsFix() {
    initComponentsFixI18n();
    initComponentsFixSpecific();
  }

  /**
   * Initialize fix components of subclasses to {@link AbstractI18nSnapView}.
   * Executed only once.
   * Class {@link AbstractI18nSnapView} uses for its  components {@link AbstractI18nSnapView#initComponentsFix()}
   */
  protected abstract void initComponentsFixSpecific();

  /**
   * Initializes fix components of {@link AbstractI18nSnapView}
   */
  private void initComponentsFixI18n() {
    resourcesGroup.setModel(new DefaultComboBoxModel<>());
    resourcesGroup.setRenderer(new DefaultListCellRenderer());

    updateFontSize(resourcesGroup);
    //TODO Maxime: check if needed
    // resourcesGroup.setBackground(deleteOrCreateKeyButton.getBackground());
    resourcesGroup.addItemListener(event -> {

      if (ItemEvent.SELECTED != event.getStateChange())
        return;

      if (Boolean.TRUE.equals(resourcesGroup.getClientProperty(BLOCK_REFRESH)))
        return;

      Object item = event.getItem();
      if (item instanceof MyPropertiesFileInfo) {
        MyPropertiesFileInfo info = (MyPropertiesFileInfo) item;
        PropertiesFile propertiesFile = info.getPropertiesFile();

        try {
              SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) () ->
              uiLoadTranslationFromFile(propertiesFile));
        } catch (Throwable ee) {
          LOG.error("Translation init error", ee);
        }

      }
    });
  }

  /**
   * Initialize dynamic components/contents
   */
  public abstract void initComponentsDynamic();

  /**
   * refreshWhenDocumentUpdated
   */
  protected void refreshWhenDocumentUpdated() {

    if (model == null)
      return;

    PsiDocumentManager.getInstance(model.getProject()).performWhenAllCommitted(
        () -> {
          try {
            SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) this::updateUiOrFlagDirty);
          } catch (Throwable ee) {
            LOG.error("Translation init error", ee);
          }
        }
    );
  }

  @Override
  protected void doEditorDocumentChanged(final Editor editor, final DocumentEvent event) {
    if (editor.isDisposed())
      return;

    if (! editor.getComponent().hasFocus())
      return;

    updateModelFromEditor(editor);
  }


  /**
   * Updates the {@link TranslationModel} from the provided editor
   */
  protected void updateModelFromEditor(final Editor editor) {
    if (editor.isDisposed()) {
      LOG.trace("updateModelFromEditor : editor is disposed - END");
      return;
    }

    Project project = editor.getProject();
    if (project == null) {
      LOG.trace("updateModelFromEditor : no project found - END");
      return;
    }

    Boolean block = (Boolean) getClientProperty(BLOCK_I18N);
    if (Boolean.TRUE.equals(block)) {
      LOG.trace("updateModelFromEditor : blocked - END");
      return;
    }

    PsiDocumentManager.getInstance(editor.getProject()).performForCommittedDocument(editor.getDocument(),
        () -> {
          Triple<String, PsiFile, Module> result = I18nUtil.findI18nKeyFromEditor(editor);
          model.selectI18nKey(result.getFirst(), result.getSecond(), result.getThird());
        });
  }

  /**
   * Load translations
   */
  private void uiLoadTranslationFromFile(PropertiesFile propertiesFile) {

    if (propertiesFile == null) {
      LOG.warn("loadTranslationFile called with null value for propertiesFile");
      return;
    }
    LOG.info("uiLoadTranslationFromFile '" + propertiesFile + "'");

    model.selectPropertiesFile(propertiesFile);

    //
    // Update UI if necessary
    List<String> moduleLanguages = model.getLanguages();
    if (moduleLanguages.isEmpty()) {
      LOG.trace("loadTranslationFile for key '" + propertiesFile + "' : NO LANGUAGE FOUND - STOP");
      return;
    }

    if (flags==null || flags.length != moduleLanguages.size()) {

      LOG.trace("uiLoadTranslationFromFile '" + propertiesFile + "' : update languages list");

      // clear resourcesGroup and listener
      if (translationWindow !=null)
        this.remove(translationWindow);

      // reload swing
      initComponentsDynamic();

      this.add(translationWindow, new GridConstraints(1, 0, 1, 1,
          GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_BOTH,
          GridConstraints.SIZEPOLICY_CAN_GROW,
          GridConstraints.SIZEPOLICY_CAN_GROW,
          new Dimension(100, 0), null, null));

      LOG.trace("loadTranslationFile for propertiesFile '" + propertiesFile + "' : update languages list - done");
    }

  }

  protected String getLanguage(int index) {
      List<String> languages = model.getLanguages();
      if (index > languages.size()-1)
          return null;
      return languages.get(index);
  }

  /**
   * openResourceBundleFile
   */
  private void openResourceBundleFile(final PropertiesFile propertiesFile, final String propertyKey) {

    if (propertiesFile == null || propertyKey == null)
          return;

    List<IProperty> properties = propertiesFile.findPropertiesByKey(propertyKey);
    if (!properties.isEmpty()) {
      PsiElement nav = properties.get(0).getPsiElement().getNavigationElement();
      if (nav instanceof Navigatable) {
        ((Navigatable) nav).navigate(true);
        return;
      }
    }

    ((Navigatable)propertiesFile.getContainingFile().getNavigationElement()).navigate(true);
  }

  @Nullable
  private PropertiesFile getPropertiesFileFromIndex(final int langIndex) {
    PropertiesFile propertiesFile;
    if (langIndex < 0) {
        MyPropertiesFileInfo selectedItem = (MyPropertiesFileInfo) resourcesGroup.getSelectedItem();
        if (selectedItem == null) return null;
        propertiesFile = selectedItem.getPropertiesFile();
    }
    else {
        String selectedLanguage = getLanguage(langIndex);
        propertiesFile = I18nUtil.getPsiPropertiesFile(model.getSelectedPropertiesFile().getResourceBundle(), selectedLanguage);
    }
    return propertiesFile;
  }

  /**
   * Load translations
   */
  private void uiLoadTranslationFromI18nKey(final String i18nKey) {
    if (!isInitDone) {
      initComponentsFix();
      isInitDone = true;
    }

    updateUiComponents(i18nKey);

    isDirty = false;
  }

  /**
   * Called when new i18n key is detected to allow view update.
   * @param i18nKey new i18n key
   */
  protected abstract void updateUiComponents(String i18nKey);

  /**
   * TODO: Check if needed; initializing should be incorporated in updateUiComponents
   */
  protected abstract void initializeTranslationWindow();

  /**
   * updateResourcesGroupButton
   * @param language language to update
   */
  protected void updateResourcesGroupButton(String language) {

    LOG.trace("updateResourcesGroupButton for language '" + (language == null ? "null" : language) + "'");
      if (language!=null && language.equals(model.getSelectedLanguage()))
          return;

      if (language !=null)
          model.setSelectedLanguage(language);

      // Update Resource selection list
    updateResourceSelectionList();

    selectResourceInGroup();

    resourcesGroup.setToolTipText(model.getSelectedPropertiesFileTooltip());
  }

  private void updateResourceSelectionList() {
    List<PropertiesFile> propertiesFiles = model.getPropertiesFiles();
    List<PropertiesFile> current = new ArrayList<>();
    for (int i=0; i<resourcesGroup.getItemCount(); i++) {
        MyPropertiesFileInfo item = resourcesGroup.getItemAt(i);
        current.add(item.getPropertiesFile());
    }
    if (! Arrays.equals(propertiesFiles.toArray(), current.toArray())) {
        try {
            resourcesGroup.putClientProperty(BLOCK_REFRESH, true);
            resourcesGroup.removeAllItems();
            for (PropertiesFile pf : propertiesFiles) {
                String label = model.getShortName(pf);
                MyPropertiesFileInfo info = new MyPropertiesFileInfo(label, pf, IntelliJLaf.class.getName());
                resourcesGroup.addItem(info);
            }
        }
        finally {
            resourcesGroup.putClientProperty(BLOCK_REFRESH, false);
        }
    }
  }

  private void selectResourceInGroup() {
    PropertiesFile selectedPropertiesFile = model.getSelectedPropertiesFile();
    if (selectedPropertiesFile!=null) {
        String bestPropetiesFilePath = selectedPropertiesFile.getVirtualFile().getPath();
        for (int i = 0; i < resourcesGroup.getItemCount(); i++) {
            MyPropertiesFileInfo item = resourcesGroup.getItemAt(i);
            String path = item.getPropertiesFile().getVirtualFile().getPath();
            if (bestPropetiesFilePath.equals(path)) {

                if (resourcesGroup.getSelectedIndex() != i)
                    resourcesGroup.setSelectedIndex(i);
                break;
            }
        }
    }
  }

  protected ActionLink getNewLanguageFlag(final int langIndex) {
      return getNewLanguageFlag(langIndex, null);
  }

  protected ActionLink getNewLanguageFlag(final int langIndex, final String propertyKey) {
      final ActionLink newActionLink = new ActionLink("", (ActionListener) actionEvent -> openResourceBundleFile(getPropertiesFileFromIndex(langIndex), propertyKey));
      newActionLink.setAlignmentY(0.0F);
      newActionLink.setMinimumSize(new Dimension(25, 20));
      newActionLink.setToolTipText("Open properties file...");
      newActionLink.setRolloverIcon(I18NIcons.MOVE_TO);
      return newActionLink;
  }

  /*******************************************$
   *  MyPropertiesFileInfo
   */
  protected static class MyPropertiesFileInfo extends UIManager.LookAndFeelInfo {

    private final PropertiesFile propertiesFile;

    public MyPropertiesFileInfo(String label, PropertiesFile propertiesFile, String className) {
      super(label, className);
      this.propertiesFile = propertiesFile;
    }

    public PropertiesFile getPropertiesFile() {
      return propertiesFile;
    }

    @Override
    public String toString() {
      return getName();
    }
  }
}
