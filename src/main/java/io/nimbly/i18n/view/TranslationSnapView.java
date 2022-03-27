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

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.JBUI;
import io.nimbly.i18n.util.FileUtil;
import io.nimbly.i18n.util.I18nUtil;
import io.nimbly.i18n.util.IconUtil;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TranslationView
 * User: Maxime HAMM
 * Date: 14/01/2017
 */
public class TranslationSnapView extends AbstractI18nSnapView {

    public static final String NIMBLY = "Nimbly";

    public static final String DELETE_KEY = "Delete";
    public static final String CREATE_KEY = "Create key";

    private final transient ActionToolbar editActionToolBar;

  private final JTextField keyTextField =
      new JTextField() {
        @Override
        public void setEditable(boolean b) {
          super.setEditable(b);

          JComponent fake = b ? new JTextField() : new JLabel();
          setBorder(fake.getBorder());
          setBackground(fake.getBackground());
          setFont(
              fake.getFont()
                  .deriveFont(
                      fake.getFont().getStyle() | Font.BOLD, fake.getFont().getSize() + 0f));
        }
      };

    private JTextComponent[] translations;
    private ActionLink[] languages;

    private final JPanel translationPanel = new JPanel();
    private final JButton duplicateButton = new JButton();
    private final JButton deleteOrCreateKeyButton = new JButton();

    private transient MyTranslationPaneAdapter[] translationsPaneAdaptors = null;
    private final transient Map<Document, MyPropertiesFileAdapter> propertiesFileAdaptors = new HashMap<>();

    private final transient ToggleAction editAction = new ToggleAction("Edit Key") {

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return keyTextField.isEditable();
        }

        @Override
        public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
            keyTextField.setEditable(state);
            if (state)
                keyTextField.grabFocus();
            else if (keyTextField.hasFocus())
                keyTextField.transferFocus();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            if  (deleteOrCreateKeyButton.getText().equals(CREATE_KEY)) {
                e.getPresentation().setIcon(I18NIcons.FIND);
                e.getPresentation().setText("Select Another Key or Create a New Key...", false);
            }
            else {
                e.getPresentation().setIcon(I18NIcons.EDIT);
                e.getPresentation().setText("Rename Key...", false);
            }
        }
    };

    /**
     * TranslationSnapView
     */
    public TranslationSnapView() {
        super(new GridLayoutManager(2, 1));

        ActionGroup editActionGroup = createEditActionGroup();
        editActionToolBar = ActionManager.getInstance().createActionToolbar("EDIT", editActionGroup, true);

    }

    /**
     * duplicateKey
     */
    private void duplicateKey() {

        boolean editable = translations[0].isEditable();

        String newKey = model.duplicateKey();
        model.setSelectedKey(newKey);
        updateUiOrFlagDirty();

        if (editable) {
            editAction.setSelected(null, true); //standard class has @NotNull AnActionEvent, but own implementation doesn't need this parameter
        }

    }

    /**
     * doCaretPositionChanged
     */
    @Override
    protected  void doCaretPositionChanged(CaretEvent event) {

        LOG.debug("doCaretPositionChanged !");
        Boolean block = (Boolean) getClientProperty(BLOCK_I18N);
        if (Boolean.TRUE.equals(block)) {
            LOG.trace("doCaretPositionChanged : blocked - STOP");
            return;
        }

        if (model!=null && model.isViewRefreshBlocked()) {
            LOG.trace("doCaretPositionChanged : view refresh blocked - STOP");
            return;
        }

        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) () ->
                        updateModelFromEditor(event.getEditor()));
            } catch (Throwable e) {
                LOG.error("doCaretPositionChanged", e);
            }
        });

    }


    @Override
    protected void updateUiComponents(final String i18nKey) {
        initComponentsDynamic();
        updateTranslations(i18nKey);

        //
        // Update "open ressource button"
        LOG.trace("updateUiComponents for key '" + i18nKey);
        updateResourcesGroupButton(null);
    }

    private void updateTranslations(final String i18nKey) {
        // Load psi translations
        final List<String> moduleLanguages = model.getLanguages();
        boolean isWritable = false;
        boolean atLeasOneTranslations = false;
        IProperty[] translationProperties = new IProperty[this.flags.length];
        for (int i=0; i<this.flags.length; i++) {

            final String lang = moduleLanguages.get(i);

            List<IProperty> psiProperties = model.getPsiProperties(lang);
            IProperty prop = psiProperties.isEmpty() ? null : psiProperties.get(0);

            if (prop !=null) {

                //
                // if property file not part of current model, do not edit it !
                // This is possible when using another module (i.e. not jars)
                PsiFile containingFile = prop.getPropertiesFile().getContainingFile();
                boolean w = containingFile.isWritable();

                isWritable |= w;
                atLeasOneTranslations = true;

            }
            translationProperties[i] = prop;
            LOG.trace("loadTranslation for key '" + i18nKey + "' : find translation properties for language '" + lang + "'");
        }

        updateTranslationComponents(i18nKey, moduleLanguages, isWritable, atLeasOneTranslations, translationProperties);

        //
        // Sets key
        LOG.trace("loadTranslation for key '" + i18nKey + "' : setup text");
        this.keyTextField.setText(i18nKey);
        this.keyTextField.setEditable(false);
        LOG.trace("loadTranslation for key '" + i18nKey + "' : setup text - done");

        // Update create or delete key button
        LOG.trace("loadTranslation for key '" + i18nKey + "' : update CRUD buttons'");
        updateCRUDButtons(atLeasOneTranslations);
    }

    private void updateTranslationComponents(final String i18nKey, final List<String> moduleLanguages, final boolean isWritable, final boolean atLeasOneTranslations, final IProperty[] translationProperties) {
        for (int i=0; i<this.flags.length; i++) {

            final String lang = moduleLanguages.get(i);

            // adjust listeners if flags/languages sequence is not matching
            Icon ico = IconUtil.addText(I18NIcons.TRANSPARENT, lang.toUpperCase(), 12f, SwingConstants.CENTER);
            if (!ico.equals(flags[i].getIcon())) {
                this.flags[i].setIcon(ico);

                this.languages[i].setIcon(ico);
                this.languages[i].setDisabledIcon(ico);

                this.translationsPaneAdaptors[i] = new MyTranslationPaneAdapter(i);
                this.translations[i].getDocument().addDocumentListener(translationsPaneAdaptors[i]);
                this.translations[i].addFocusListener(translationsPaneAdaptors[i]);
            }


            // disable fields if no translation at all
            this.translations[i].setBackground(atLeasOneTranslations && isWritable ? new JTextField().getBackground() : translationWindow.getBackground());
            this.translations[i].setEnabled(atLeasOneTranslations);
            this.translations[i].setEditable(isWritable);

            String tr = translationProperties[i] != null ? I18nUtil.unescapeKeepCR(translationProperties[i].getValue()) : "";
            LOG.trace("loadTranslation for key '" + i18nKey + "' : setup translation '" + tr + "'");
            setTranslationNoEvents(this.translations[i], tr);

            // add psi element modification listener
            if (translationProperties[i] != null) {

                Document doc = FileUtil.getDocument(translationProperties[i].getPsiElement());
                if (doc != null && !propertiesFileAdaptors.containsKey(doc)) {

                    MyPropertiesFileAdapter listener = new MyPropertiesFileAdapter();
                    doc.addDocumentListener( listener);

                    propertiesFileAdaptors.put(doc, listener);
                }
            }

            this.languages[i].setEnabled(isWritable);
        }
    }

    @Override
    protected void initializeTranslationWindow() {
        if (translationWindow != null)
            this.remove(translationWindow);

        if (this.translations != null) {
            for (int i = 0; i < this.translations.length; i++) {
                translations[i].getDocument().removeDocumentListener(translationsPaneAdaptors[i]);
                translations[i].removeFocusListener(translationsPaneAdaptors[i]);
            }
        }

        for (Map.Entry<Document, MyPropertiesFileAdapter> propertiesFileAdaptor : propertiesFileAdaptors.entrySet()) {
            MyPropertiesFileAdapter listener = propertiesFileAdaptor.getValue();
            if (listener!=null)
                propertiesFileAdaptor.getKey().removeDocumentListener(listener);
        }
        propertiesFileAdaptors.clear();
    }

    /**
     * updateCRUDButtons
     */
    private void updateCRUDButtons(boolean atLeasOneTranslations) {

        boolean isWritable = translations[0].isEditable();

        deleteOrCreateKeyButton.setText(isWritable && atLeasOneTranslations ? DELETE_KEY : CREATE_KEY);
        deleteOrCreateKeyButton.setIcon(isWritable && atLeasOneTranslations ? I18NIcons.DELETE : I18NIcons.ADD);

        deleteOrCreateKeyButton.setEnabled(model.getSelectedPropertiesFile().getVirtualFile().isWritable());

        duplicateButton.setVisible(atLeasOneTranslations && isWritable);

        String tooltip = isWritable ? model.getSelectedBundleTooltip() : TranslationModel.getTooltip("*", I18nUtil.getLocalPsiPropertiesFiles(model.getModule()).get(0), model.getModule());
        deleteOrCreateKeyButton.setToolTipText(tooltip);
        duplicateButton.setToolTipText(tooltip);

        editActionToolBar.getComponent().setVisible(model.getSelectedPropertiesFile().getVirtualFile().isWritable());
    }

    /**
     * setTranslationNoEvents
     */
    private void setTranslationNoEvents(JTextComponent textComponent, String translation) {

        if (textComponent.getText().equals(translation))
            return;

        try {
            this.putClientProperty(BLOCK_I18N, true);
            textComponent.setText(translation);
        }
        finally {
            this.putClientProperty(BLOCK_I18N, false);
        }
    }

    /**
     * createEditActionGroup
     */
    protected ActionGroup createEditActionGroup() {
        DefaultActionGroup group = new DefaultActionGroup();

        group.add(editAction);

        return group;
    }

    /**
     * createPreviousNextActionGroup
     */
    protected ActionGroup createPreviousNextActionGroup() {
        DefaultActionGroup group = new DefaultActionGroup();

        // left
        AnAction leftAction = new AnAction("Left") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (model.scrollLeftOnKeyPath())
                    updateUiOrFlagDirty();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(isPathFile(model.getKeyPath()));
                e.getPresentation().setEnabled(model.getSelectedKey().length() < model.getKeyPath().length());
            }
        };
        leftAction.getTemplatePresentation().setIcon(I18NIcons.LEFT);
        leftAction.getTemplatePresentation().setText("Previous in dot notation", false);
        group.add(leftAction);

        // right
        AnAction rightAction = new AnAction("Right") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (model.scrollRightOnSelectedKey())
                    updateUiOrFlagDirty();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(isPathFile(model.getKeyPath()));
                e.getPresentation().setEnabled(isPathFile(model.getSelectedKey()));
            }
        };
        rightAction.getTemplatePresentation().setIcon(I18NIcons.RIGHT);
        rightAction.getTemplatePresentation().setText("Next in Dot Notation", false);
        group.add(rightAction);


        return group;
    }




    /*****************************************************
     * initComponentsOnce
     */
    @Override
    public void initComponentsFixSpecific() {
        deleteOrCreateKeyButton.setText(DELETE_KEY);
        deleteOrCreateKeyButton.addActionListener(e -> {
            if (DELETE_KEY.equals(deleteOrCreateKeyButton.getText())) {
                String key = model.getSelectedKey();
                try {
                    SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) () -> {
                        model.deleteKey();
                        model.setSelectedKey(key);
                        //uiInitWithTranslationKey(key, true, model.getOriginFile(), model.getModule());
                    });
                } catch (Throwable ee) {
                    LOG.error("initComponentsFixSpecific on button delete or create ", ee);
                }
            }
            else {
                model.createKey();
            }

            refreshWhenDocumentUpdated();
        });
        updateFontSize(deleteOrCreateKeyButton);

        duplicateButton.addActionListener(e -> {
            try {
                SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) this::duplicateKey);
            } catch (Throwable ee) {
                LOG.error("initComponentsFixSpecific on button duplicate", ee);
            }
        });
        duplicateButton.setIcon(I18NIcons.DUPLICATE);
        updateFontSize(duplicateButton);
        duplicateButton.setText("Duplicate key");

        editActionToolBar.setMinimumButtonSize(new Dimension(25, 20));
        editActionToolBar.setTargetComponent(this);

        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner Evaluation license - Maxime HAMM
        translationWindow = new JPanel();
        keyTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (keyTextField.isEditable()) {
                    keyTextField.setEditable(false);
                    model.renameKey(keyTextField.getText());
                    refreshWhenDocumentUpdated();
                }
            }
        });
        keyTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                int k = evt.getKeyCode();
                if (k == KeyEvent.VK_ENTER)
                    keyTextField.transferFocus();
            }
        });

        // JFormDesigner evaluation mark
        translationWindow.setLayout(new GridLayoutManager(5, 1, JBUI.insetsBottom(10), -1, -1));

        JPanel topPanel = getTopPanel();

        translationWindow.add(topPanel, new GridConstraints(0, 0, 1, 1,
            GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW |  GridConstraints.SIZEPOLICY_CAN_SHRINK,
            GridConstraints.SIZEPOLICY_FIXED,
            new Dimension(36, 30), null, null));

        JScrollPane scroll = ScrollPaneFactory.createScrollPane(translationPanel, true);
        scroll.setPreferredSize(new Dimension(translationPanel.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 5, -1));
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

        bottom.add(duplicateButton, new GridBagConstraints(1, 0, 1, 1,
            0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,  JBUI.insets(0, 5, 0, 0), 0, 0));


        bottom.add(deleteOrCreateKeyButton, new GridBagConstraints(2, 0, 1, 1,
            0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,  JBUI.insets(0, 3, 0, 43), 0, 0));

        translationWindow.add(bottom, new GridConstraints(2, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null, null, null));
    }

    @Override
    public void initComponentsDynamic() {

        List<String> langs = ( model == null ? Collections.emptyList() : model.getLanguages());

        final int newArraySize = langs.size();
        if (flags.length == newArraySize)
            return; // sufficient dynamic components are already available
        //---- flag ----
        translationPanel.removeAll();
        flags = new ActionLink[newArraySize];
        translations = new JTextComponent[newArraySize];
        languages = new ActionLink[newArraySize];
        translationsPaneAdaptors = new MyTranslationPaneAdapter[newArraySize];

        if (!langs.isEmpty()) {
            translationPanel.setLayout(new GridLayoutManager(langs.size(), 3, JBUI.insets(10, 15, 10, 5), -1, -1));

            for (int i = 0; i < langs.size(); i++) {

                int finalI = i;

                flags[i] = getNewLanguageFlag(i, keyTextField.getText());

                translationPanel.add(flags[i], new GridConstraints(i, 0, 1, 1,
                    GridConstraints.ANCHOR_NORTHEAST, GridConstraints.FILL_NONE,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK,
                    null, null, null));

                //---- translation ----
                translations[i] = new JTextPane();
                translations[i].setPreferredSize(new Dimension(-1, 37));
                translations[i].setBorder(new EtchedBorder());
                translations[i].setAutoscrolls(true);
                translations[i].setFont(flags[i].getFont());
                JBScrollPane scroller = new JBScrollPane(translations[i]); // TIPS : Use scroller otherwise the textarea will not shrink !!
                translationPanel.add(scroller, new GridConstraints(i, 1, 1, 1,
                    GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_BOTH,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK,
                    null, null, null));

                //---- lang ----
                languages[i] = new ActionLink("", (ActionListener) actionEvent ->
                    googleTranslation(finalI)
                );

                languages[i].setMinimumSize(new Dimension(30, 20));
                languages[i].setHorizontalTextPosition(SwingConstants.LEFT);
                translationPanel.add(languages[i], new GridConstraints(i, 2, 1, 1,
                    GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK,
                    GridConstraints.SIZEPOLICY_FIXED,
                    null, null, null));
                languages[i].setToolTipText("Google translate...");
                languages[i].setRolloverIcon(I18NIcons.GOOGLE_TRANSALTE);
            }
        }
    }

    @NotNull
    private JPanel getTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridLayoutManager(1, 3, JBUI.insets(0,13, 0,7), -1, -1));

        //--- edit button ---
        JPanel p = new JPanel();
        p.add(editActionToolBar.getComponent());  // wrap the edit button to keep layout will hiding it

        topPanel.add(p, new GridConstraints(0, 0, 1, 1,
                GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                new Dimension(35, 35),  new Dimension(35, 35), new Dimension(35, 35)));

        //---- key ----
        keyTextField.setEditable(false);
        topPanel.add(keyTextField, new GridConstraints(0, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW |  GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_FIXED,
                new Dimension(30, 25), null, null, 0));

        //--- key next and previous buttons ---
        ActionGroup prevNextActionGroup = createPreviousNextActionGroup();
        ActionToolbar prevNextActionToolBar = ActionManager.getInstance().createActionToolbar("PN", prevNextActionGroup, true);
        prevNextActionToolBar.setMinimumButtonSize(new Dimension(14, 20));
        prevNextActionToolBar.setTargetComponent(this);

        topPanel.add(prevNextActionToolBar.getComponent(), new GridConstraints(0, 2, 1, 1,
                GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, new Dimension(45, 20), null));
        return topPanel;
    }

    private void googleTranslation(int index) {

        if (! this.translations[index].isEditable())
            return;

    ProgressManager.getInstance()
        .run(
            new MyGoogleTranslationRun(model.getProject(), "Google translate", true,
                index, this.keyTextField.getText()) ); // try to use best language
    }

    private class MyGoogleTranslationRun extends Backgroundable {

        private boolean canceled = false;

        final int index;
        final String key;

        public MyGoogleTranslationRun(@Nullable Project project,
                                     @NlsContexts.ProgressTitle @NotNull String title,
                                     boolean canBeCancelled, final int index, final String key ) {
            super(project, title, canBeCancelled);
            this.index = index;
            this.key = key;
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {

            Module module = model.getModule();
            String sourceLanguage = null;
            String sourceTranslation = null;

            // get target language
            String targetLanguage = getLanguage(index);

            // try using other languages
            List<IProperty> psiProperties =
                ApplicationManager.getApplication()
                    .runReadAction(
                        (Computable<List<IProperty>>)
                            () -> I18nUtil.getPsiProperties(key, null, module));

            for (IProperty property : psiProperties) {

                String translation = I18nUtil.unescapeKeepCR(property.getValue());
                if (translation != null && !translation.trim().isEmpty()) {

                    String lang = I18nUtil.getLanguage(property.getPropertiesFile());
                    if (lang != null && !targetLanguage.equals(lang)) {
                        sourceLanguage = lang;
                        sourceTranslation = translation;
                        break;
                    }
                }
            }

            // use the translation key if no best choice
            if (sourceLanguage == null) {

                sourceLanguage = Locale.ENGLISH.getLanguage();
                sourceTranslation = I18nUtil.prepareKeyForGoogleTranslation(key);
            }

            // do translation
            doTranslation(module, sourceLanguage, targetLanguage, sourceTranslation);
        }

        private void doTranslation(final Module module, final String sourceLanguage, final String targetLanguage, final String sourceTranslation) {

            // well no more suggestions !
            Project project = module.getProject();
            if (sourceLanguage == null) {

                Messages.showErrorDialog(project, "No source found for translation !", NIMBLY);
                return;
            }


            String translation;
            try {

                translation =
                    I18nUtil.googleTranslate( sourceLanguage, targetLanguage,
                        sourceTranslation, key);

            } catch (SocketTimeoutException | UnknownHostException e) {
                if (canceled) return;

                LOG.warn("Communication error", e);
                Messages.showErrorDialog(
                    module.getProject(),
                    "Communication error. Check your internet connection and proxy settings",
                    NIMBLY);

                return;

            } catch (Exception e) {
                if (canceled) return;

                LOG.error("Translation error", e);
                Messages.showErrorDialog(
                    module.getProject(),
                    "Translation error. See logs for more informations",
                    NIMBLY);

                return;
            }

            if (canceled) return;

            if (translation == null) {
                Messages.showInfoMessage(module.getProject(), "No translation found", NIMBLY);
                return;
            }

            // Do update key
            PropertiesFile targetFile =
                I18nUtil.getPsiPropertiesSiblingFile(
                    model.getSelectedPropertiesFile(), targetLanguage);
            if (targetFile != null) {
                I18nUtil.doUpdateTranslation(key, translation, targetFile, true);
                //                            ApplicationManager.getApplication().invokeLater(
                //                                    () -> I18nUtil.doUpdateTranslation(key,
                // translation, targetFile, true));
            }
        }

        @Override
        public boolean shouldStartInBackground() {
            return true;
        }

        @Override
        public void onCancel() {
            canceled = true;
        }
    }

    /*******************************************$
     *  MyTranslationPaneAdapter
     */
    private class MyTranslationPaneAdapter extends com.intellij.ui.DocumentAdapter implements FocusListener {

        private final int index;

        public MyTranslationPaneAdapter(int index) {
            this.index = index;
        }

        @Override
        protected void textChanged(javax.swing.event.@NotNull DocumentEvent e) {
            final String language = getLanguage(index);
            final String translation = translations[index].getText();
            updateTranslation(TranslationSnapView.this, language, translation);
        }

        @Override
        public void focusGained(FocusEvent e) {
            String language = getLanguage(index);
            if (language!=null) {
                try {
                    SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) () ->
                        updateResourcesGroupButton(language));
                } catch (Throwable ee) {
                    LOG.error("Translation init error", ee);
                }
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            // nothing to be done on lost focus
        }

    }


    /*******************************************$
     *  MyPropertiesFileAdapter
     */
    private class MyPropertiesFileAdapter implements DocumentListener {

        // private PropertyKeyImpl key = null;

        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent e) {
            // nothing to do (code kept for debugging purposes)
            /*
            if (model == null)
                return;

            PropertyKeyImpl k = findPropertyKey(e);
            if (k!=null && k.getText().equals(model.getSelectedKey()))
                key = k;
             */

        }

        @Override
        public void documentChanged(@NotNull DocumentEvent e) {

            if (model == null)
                return;

            if (model.isViewRefreshBlocked())
                return;

            Module module = model.getModule();
      PsiDocumentManager.getInstance(module.getProject())
          .performForCommittedDocument(
              e.getDocument(), TranslationSnapView.this::refreshWhenDocumentUpdated);
        }


        public PropertyKeyImpl findPropertyKey(DocumentEvent event) {
            PsiFile file = FileUtil.getFile(event.getDocument(), model.getModule().getProject());
            PsiElement elementAt = file.findElementAt(event.getOffset());
            if (! (elementAt instanceof PropertyKeyImpl))
                elementAt =  file.findElementAt(event.getOffset()-1);

            if (elementAt instanceof PropertyKeyImpl) {
                return (PropertyKeyImpl) elementAt;
            }
            return null;
        }
    }


}
