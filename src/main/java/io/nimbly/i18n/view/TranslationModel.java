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
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThrowableRunnable;
import io.nimbly.i18n.util.FileUtil;
import io.nimbly.i18n.util.I18nUtil;
import io.nimbly.i18n.util.JavaUtil;
import io.nimbly.i18n.util.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;

/**
 * TranslationModel
 * User: Maxime HAMM
 * Date: 16/01/2017
 */
public class TranslationModel {

    private static final Logger LOG = LoggerFactory.getInstance(TranslationModel.class);

    private String keyPath;
    private Module module;

    private String selectedKey;
    private String selectedLanguage;

    private PropertiesFile selectedPropertiesFile;
    private boolean viewRefreshBlocked;

    private List<PropertiesFile> propertiesFiles = new ArrayList<>();

    boolean isInitComplete() {
        return initComplete;
    }

    private boolean initComplete = false;
    private PsiFile originFile;

    protected Project project;

    private static final TranslationModel instance = null;


    public static TranslationModel getInstance(){
        if (instance != null)
            return instance;
        return new TranslationModel();
    }
    private TranslationModel() {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                findI18nKeyAndInit();
            } else {
                SlowOperations.allowSlowOperations((ThrowableRunnable<Throwable>) this::findI18nKeyAndInit);
            }
        } catch (Throwable e) {
            LOG.error(e);
        }
    }

    public Project getProject() {
        return project;
    }

    public static @Nullable Project getProjectCurrent() {
        if (instance == null) return null;
        return instance.getProject();
    }

    /**
   * Finds potential string literal to load properties file
   * */
    protected void findI18nKeyAndInit() {
        try {
            viewRefreshBlocked = true;
            initComplete = false;
            for (Editor editor : FileUtil.getEditors()) {
                int offset = editor.getCaretModel().getOffset();
                if (offset >= 0) {
                    PsiElement target = I18nUtil.findTarget(editor);
                    if (target != null && JavaUtil.getModule(target) != null)  {
                    findI18nKeyAndInitFromTarget(target);
                    break;
                    }
                }
            }
        }
        finally {
            viewRefreshBlocked = false;
            initComplete = true;
            runWhenCompleteList.forEach(Runnable::run);
            runWhenCompleteList.clear();
        }
    }

    private void findI18nKeyAndInitFromTarget(final PsiElement target) {
        final Module moduleNew = JavaUtil.getModule(target);
        final String i18nKey = I18nUtil.findI18nKey(target);
        selectI18nKey(i18nKey, target.getContainingFile(),moduleNew, true);
        if (moduleNew != null) project = moduleNew.getProject();
        else project = target.getProject();
        originFile = target.getContainingFile();
        final String selectedLanguageOld = this.selectedLanguage;
        final PropertiesFile selectedPropertiesFileOld = this.selectedPropertiesFile;
        determineSelected(this.selectedPropertiesFile);
        if (module != null && selectedLanguage != null &&
            ( !module.equals(moduleNew) || !selectedLanguage.equals(selectedLanguageOld) ||
                selectedPropertiesFile != null && !selectedPropertiesFile.equals(selectedPropertiesFileOld))
        )
          updatePropertiesFiles();
    }

    public TranslationModel(String keyPath, PsiFile originFile, PropertiesFile selectedPropertiesFile, Module module) {

        LOG.debug("TranslationModel instanciation for key '" + keyPath + "'");
        this.module = module;
        this.keyPath = keyPath;
        this.originFile = originFile;

        determineSelected(selectedPropertiesFile);
    }

    private void determineSelected(PropertiesFile selectedPropertiesFile) {
        String defaultLanguage = I18nUtil.getPreferredLanguage();
        LOG.trace("TranslationModel instanciation for key '" + keyPath + "' - preferred language : '" + defaultLanguage + "'");

        if (defaultLanguage == null) {
            defaultLanguage = Locale.ENGLISH.getLanguage();
            I18nUtil.setLastUsedLanguage(defaultLanguage);
            assert defaultLanguage != null;
        }

        if (originFile instanceof PropertiesFile && originFile.isWritable()) {
            this.selectedPropertiesFile = (PropertiesFile) originFile;
            final String fileLanguage = I18nUtil.getLanguage((PropertiesFile) originFile);
            if (fileLanguage != null)
                defaultLanguage = fileLanguage;
        }
        else if (selectedPropertiesFile != null) {
            this.selectedPropertiesFile = selectedPropertiesFile;
        }
        else {
            this.selectedPropertiesFile = I18nUtil.getBestPropertiesFile(keyPath, module);
        }
        selectedLanguage = defaultLanguage.toLowerCase();
    }

    public void setKeyPath(final String keyPathNew) {
        this.keyPath = keyPathNew;
    }


    public String getKeyPath() {
        return keyPath;
    }

    public int getKeyCount() {
        return this.selectedPropertiesFile.getProperties().size();
    }

    public List<PropertiesFile> getPropertiesFiles() {
        if (propertiesFiles.isEmpty()) {
            updatePropertiesFiles();
        }
        return propertiesFiles;
    }

    private void updatePropertiesFiles() {
        List<PropertiesFile> files = new ArrayList<>();
        if (module != null) {
          files = I18nUtil.getPsiPropertiesFiles(selectedLanguage, module);
          if (files.isEmpty()
              && selectedPropertiesFile != null
              && !module.equals(JavaUtil.getModule(selectedPropertiesFile.getContainingFile()))) {

            files =
                I18nUtil.getPsiPropertiesFiles(
                    selectedLanguage, JavaUtil.getModule(selectedPropertiesFile.getContainingFile()));
          }
        }
        propertiesFiles = files;
    }



    /**
     * Select i18nKey and other core members of the model without forcing it
     */
    public void selectI18nKey(final String fullI18nKey, PsiFile originFile, final Module module) {
        selectI18nKey(fullI18nKey,originFile,module, false);
    }

    /**
     * Select i18nKey and other core members of the model and informing the change listeners
     */
    private void selectI18nKey(final String fullI18nKey, PsiFile originFileNew, final Module moduleNew, boolean force) {

        LOG.debug("updateI18nKey for key '" + fullI18nKey + "'");
        if (moduleNew == null) {
            LOG.trace("updateI18nKey for key '" + fullI18nKey + "' : no module found - STOP");
            return;
        }
        this.module = moduleNew;

        if (!force && fullI18nKey.equals(getKeyPath())) {
            LOG.trace("updateI18nKey for key '" + fullI18nKey + "' : same key already selected - STOP");
            return;
        }
        setSelectedKey(fullI18nKey);
        this.setKeyPath(fullI18nKey);

        if (originFileNew != this.originFile && originFileNew instanceof PropertiesFile) {
            this.selectPropertiesFile((PropertiesFile) originFileNew);
        }
        this.originFile = originFileNew;

        if (this.getSelectedPropertiesFile() == null) {
            this.selectDefaultPropertiesFile();
        }

        determineSelected(selectedPropertiesFile);

        // inform listeners about the changes
        runWhenChangeList.forEach(Runnable::run);
    }

    public String getSelectedKey() {
        return selectedKey;
    }

    public PsiFile getOriginFile() {
        return originFile;
    }

    public void updateTranslation(String language, String translation) {

        PropertiesFile targetFile = I18nUtil.getPsiPropertiesSiblingFile(getSelectedPropertiesFile(), language);
        if (targetFile != null)
          I18nUtil.doUpdateTranslation(selectedKey, translation, targetFile, true);
    }

    public List<String> getLanguages() {

        LOG.trace("getLanguages for key '" + keyPath + "'");
        if (selectedPropertiesFile == null) {

            LOG.warn("getLanguages for key '" + keyPath + "' - NO SELECTED PROPERTIES FILE - STOP");
            return Collections.emptyList();
        }

        return I18nUtil.getLanguages(selectedPropertiesFile.getResourceBundle());
    }

    public void setSelectedLanguage(String selectedLanguage) {
        this.selectedLanguage = selectedLanguage;

        if (selectedPropertiesFile!=null) {
            selectedPropertiesFile = I18nUtil.getPsiPropertiesSiblingFile(selectedPropertiesFile, selectedLanguage);
        }
    }

    public PropertiesFile getSelectedPropertiesFile() {
        return selectedPropertiesFile;
    }

    public List<IProperty> getPsiPropertiesAll(String language) {
        return getPsiProperties(language, null);
    }


    public List<IProperty> getPsiProperties(String language) {
        return getPsiProperties(language, selectedKey);
    }

    public List<IProperty> getPsiProperties(String language, @Nullable final String i18nKey) {
        if (selectedPropertiesFile == null)
            return Collections.emptyList();

        PropertiesFile propertiesFile = I18nUtil.getPsiPropertiesSiblingFile(selectedPropertiesFile, language);
        if (propertiesFile == null)
            return Collections.emptyList();
        return I18nUtil.getPsiProperties(propertiesFile, i18nKey, language, module);
    }

    public boolean hasAtLeastOneTranslation() {
        for (String lang : getLanguages()) {
            if (! getPsiProperties(lang).isEmpty())
                return true;
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public String getSelectedLanguage() {
        return selectedLanguage;
    }

    public void setSelectedKey(String newKey) {
        this.selectedKey = newKey;
        this.keyPath = newKey;
    }

    /**
     * Update selectedKey by "scrolling left" on keyPath (= extract part until last '.')
     * @return true if scrolling occurred and selectedKey was updated, else false
     */
    public boolean scrollLeftOnKeyPath() {
        if (selectedKey.length()<keyPath.length()) {
            setSelectedKeyLeftFromKeyPath();
            return true;
        }
        return false;
    }

    private void setSelectedKeyLeftFromKeyPath() {
        int i = keyPath.substring(0, keyPath.length()-selectedKey.length()-1).lastIndexOf('.');
        selectedKey = keyPath.substring(i+1);
    }

    /**
     * Update selectedKey by "scrolling right" (= shortening it from the start until after the first '.')
     * @return true if scrolling occurred and selectedKey was updated, else false
     */
    public boolean scrollRightOnSelectedKey() {
        int i = selectedKey.indexOf('.');
        if (i>0) {
            selectedKey = selectedKey.substring(i+1);
            return true;
        }
        return false;
    }

    public void selectPropertiesFile(PropertiesFile propertiesFile) {
        this.selectedPropertiesFile = propertiesFile;
    }

    public void selectDefaultPropertiesFile() {
        List<ResourceBundle> bundles = I18nUtil.getResourceBundles(module);
        if (! bundles.isEmpty()) {

            for (PropertiesFile pf : bundles.get(0).getPropertiesFiles()) {

                if (pf.getVirtualFile().isWritable() && Locale.ENGLISH.getLanguage().equals(I18nUtil.getLanguage(pf))) {

                    selectPropertiesFile(pf);
                    break;
                }
            }
        }
    }

    public void deleteKey() {

        try {
            viewRefreshBlocked = true;
            I18nUtil.doDeleteTranslationKey(selectedPropertiesFile.getResourceBundle(), selectedKey, module);
        }
        finally {
            viewRefreshBlocked = false;
        }

        PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();

        selectedPropertiesFile = I18nUtil.getBestPropertiesFile(keyPath, module);
    }

    /**
     * duplicateKey
     */
    public String duplicateKey() {

        String key = getSelectedKey();
        ResourceBundle sourceBundle = selectedPropertiesFile.getResourceBundle();

        String newKey;
        try {
            viewRefreshBlocked = true;
            newKey = I18nUtil.doDuplicateTranslationKey(key, sourceBundle, sourceBundle, getSelectedPropertiesFile());
        }
        finally {
            viewRefreshBlocked = false;
        }

        PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();

        //selectedPropertiesFile = I18nUtil.getBestPropertiesFile(newKey, module);

        return newKey;
    }

    public void createKey() {

        // check if writable
        PsiFile containingFile = getSelectedPropertiesFile().getContainingFile();
        boolean writable = containingFile.isWritable();
        if (!writable)
            return;

        try {
            viewRefreshBlocked = true;
            I18nUtil.doCreateTranslationKey(getSelectedPropertiesFile().getResourceBundle(), selectedKey, JavaUtil.getModule(containingFile));
        }
        finally {
            viewRefreshBlocked = false;
        }

        PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();
    }

    public void renameKey(final String oldKeyName, final String newKeyName) {

        try {
            viewRefreshBlocked = true;
            I18nUtil.doRenameI18nKey(getSelectedPropertiesFile().getResourceBundle(), oldKeyName, newKeyName, module);
        }
        finally {
            viewRefreshBlocked = false;
        }

        PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();

        if (keyPath.equals(oldKeyName))
            keyPath = newKeyName;
        else
            keyPath = newKeyName + keyPath.substring(selectedKey.length());

        selectedKey = newKeyName;
    }

    public void renameKey(String newKeyName) {
        renameKey(selectedKey, newKeyName);
    }

    public String getSelectedBundle() {
        if (selectedPropertiesFile == null)
            return null;
        return I18nUtil.getBundle(selectedPropertiesFile);
    }

    public String getSelectedPropertiesFileTooltip() {
        if (selectedPropertiesFile == null)
            return null;
       return getTooltip(selectedLanguage, selectedPropertiesFile, module);
    }

    public String getSelectedBundleTooltip() {
        if (selectedPropertiesFile == null)
            return null;
        return getTooltip("*", selectedPropertiesFile, module);
    }

    public static String getTooltip(String langOrStar, PropertiesFile propertiesFile, Module module) {

        String basePath = JavaUtil.getModuleRootPath(module);
        if (basePath == null) {
            return null;
        }

        String source = propertiesFile.getVirtualFile().getPath();
        int i = source.lastIndexOf("_");
        int j = source.indexOf(".", i + 1);
        source = source.substring(0, i + 1) + langOrStar + source.substring(j);

        int idx = source.indexOf("!/");
        if (idx <0) {
            return "./" + com.intellij.openapi.util.io.FileUtil.getRelativePath(basePath, source, '/');
        }
        else {
            String jar = source.substring(0, idx);
            jar = jar.substring(jar.lastIndexOf('/')+1);
            return "[" + jar + "] " + source.substring(idx+2);
        }
    }

    public String getShortName(PropertiesFile propertiesFile) {

        String name = propertiesFile.getVirtualFile().getName();
        int indexOfSplit = name.indexOf('_');
        if (indexOfSplit < 0) indexOfSplit = name.indexOf('.');
        if (indexOfSplit >= 0) name = name.substring(0, indexOfSplit);

        String source = propertiesFile.getVirtualFile().getPath();
        int idx = source.indexOf("!/");
        if (idx <0) {

            Module m = JavaUtil.getModule(propertiesFile.getContainingFile());
            if (m.equals(getModule())) {
                return name;
            }
            else {
                return name + " [" + m.getName() + "] ";
            }
        }
        else {
            String jar = source.substring(0, idx);
            jar = jar.substring(jar.lastIndexOf('/')+1);
            return name + " [" + jar + "] ";
        }
    }

    public boolean isViewRefreshBlocked() {
        return viewRefreshBlocked;
    }

    private final ArrayList<Runnable> runWhenCompleteList = new ArrayList<>();

    public void runWhenComplete(final Runnable runWhenComplete) {
        if (isInitComplete()) runWhenComplete.run();
        else runWhenCompleteList.add(runWhenComplete);
    }

    private final ArrayList<Runnable> runWhenChangeList = new ArrayList<>();

    public void runWhenChange(final Runnable runWhenChange) {
        if (isInitComplete()) runWhenChange.run();
        else runWhenChangeList.add(runWhenChange);
    }

}
