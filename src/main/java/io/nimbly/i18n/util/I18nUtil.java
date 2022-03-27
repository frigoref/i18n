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
package io.nimbly.i18n.util;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SlowOperations;
import com.intellij.util.net.HttpConfigurable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import kotlin.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * I18nHelper
 * User: Maxime HAMM
 * Date: 26/12/2016
 */
public class I18nUtil {

    /**
     * The constant TEST_I18N_FOLDER.
     */
    public static final VirtualFile TEST_I18N_FOLDER = null;
    public static final Logger LOG = LoggerFactory.getInstance(I18nUtil.class);

    private static String preferredLanguage = null;
    private static String lastUsedLanguage = null;

    private static final Map<Module, I18nUtil> instances = new WeakHashMap<>();
    private final Module module;

    // TODO: Maxime to remove unused fields and methods
    private VirtualFile cacheI8nFolder = null;
    private long lastUpdated = -1;

    /**
     * Gets instance.
     *
     * @param module the module
     * @return the instance
     */
    public static I18nUtil getInstance(Module module) {
        return instances.computeIfAbsent(module, m ->
            new I18nUtil(module)
        );
    }

    /**
     * Instantiates a new 18 n util.
     *
     * @param module the module
     */
    public I18nUtil(Module module) {
        this.module = module;
    }

    /**
     * unicodeEscape
     *
     * @param s the s
     * @return the string
     */
    public static String unicodeEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >> 7) > 0) {
                sb.append("\\u");
                sb.append(hexChar[(c >> 12) & 0xF]); // append the hex character for the left-most 4-bits
                sb.append(hexChar[(c >> 8) & 0xF]);  // hex for the second group of 4-bits from the left
                sb.append(hexChar[(c >> 4) & 0xF]);  // hex for the third group
                sb.append(hexChar[c & 0xF]);         // hex for the last group, e.g., the right most 4-bits
            }
            else if (c == '\n' || c == '\r') {

                sb.append('\\').append(c == '\n' ? 'n' : 'r');
                if (i<s.length()-1 && s.charAt(i+1) != ' ')
                    sb.append(c);
            }
            else if (c == '\t') {
                sb.append("\\t");
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final char[] hexChar = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Gets user prefered language
     *
     * @return the prefered language
     */
    public static String getPreferredLanguage() {
        return preferredLanguage;
    }

    /**
     * Sets user prefered language
     *
     * @param preferredLanguage the prefered language
     */
    public static void setPreferredLanguage(String preferredLanguage) {
        I18nUtil.preferredLanguage = preferredLanguage;
        if (preferredLanguage != null) {
            setLastUsedLanguage(preferredLanguage);
        }
    }

    /**
     * Gets last used language
     *
     * @return The last used language
     */
    public static String getLastUsedLanguage() {
        return lastUsedLanguage;
    }

    /**
     * Sets last used language
     *
     * @param lastUsedLanguage the last used language
     */
    public static void setLastUsedLanguage(String lastUsedLanguage) {
        I18nUtil.lastUsedLanguage = lastUsedLanguage;
    }

    /**
     * Gets best language
     *
     * @param module the module
     * @return the best language
     */
    @SuppressWarnings("unused")
    public static String getBestLanguage(Module module) {
        return Locale.ENGLISH.getLanguage().toLowerCase();
    }

    /**
     * Gets module languages.
     *
     * @param resourceBundle the resource bundle
     * @return the module languages
     */
    public static List<String> getLanguages(ResourceBundle resourceBundle) {

        if (resourceBundle == null)
            return Collections.emptyList();

        List<String> languages = new ArrayList<>();
        for (PropertiesFile f : resourceBundle.getPropertiesFiles()) {

            String name = f.getName();
            int i = name.indexOf('_');
            if (i < 0 || i == name.length() - 1)
                continue;

            if (name.endsWith(".properties"))
                name = name.substring(0, name.length()-11);

            String lang = name.substring(i + 1);
            languages.add(lang);
        }

        if (languages.remove("de")) languages.add(0, "de");
        if (languages.remove("es")) languages.add(0, "es");
        if (languages.remove("fr")) languages.add(0, "fr");
        if (languages.remove("en")) languages.add(0, "en");

        return languages;

    }

    /**
     * get i18n languages from preference store
     *
     * @param module the module
     * @return the module languages
     */
    public static List<String> getLanguages(Module module) {


         List<String> languages = new ArrayList<>();
//        for (VirtualFile f : i18nFolder.getChildren()) {
//
//            if (f.isDirectory())
//                continue;
//
//            if (!f.getName().startsWith("Messages_"))
//                continue;
//
//            if (!"properties".equals(f.getExtension()))
//                continue;
//
//            String name = f.getNameWithoutExtension();
//            int i = name.indexOf('_');
//            if (i < 0 || i == name.length() - 1)
//                continue;
//
//            String lang = name.substring(i + 1);
//            languages.add(lang);
//        }

        return languages;
    }

    /**
     * Gets prefered translation
     *
     * @param key    the key
     * @param module the module
     * @return the prefered translation
     */
    public static String getPreferedTranslation(String key, Module module) {

        // gets prefered language
        String language = getPreferredLanguage();
        if (language == null)
            return null;

        return getPreferedTranslation(key, language, module);
    }

    /**
     * Gets prefered translation
     *
     * @param key      the key
     * @param language the language
     * @param module   the module
     * @return the prefered translation
     */
    public static String getPreferedTranslation(String key, String language, Module module) {



        String translation = getTranslation(key, language.toLowerCase(), module);
        if (translation != null)
            return translation;

        int i = key.indexOf('.');
        if (i < 0)
            return null;

        return getPreferedTranslation(key.substring(i + 1), language, module);
    }

    /**
     * Gets translations map
     *
     * @param key      the key
     * @param language the language
     * @param module   the module
     * @return the translation
     */
    public static String getTranslation(String key, String language, Module module) {

        List<IProperty> psiProperties = getPsiProperties(key, language, module);
        if (psiProperties.isEmpty())
            return null;

        return unescapeKeepCR(psiProperties.get(0).getValue());
    }

    /**
     * Unescape but take care of carriage return
     *
     * @param value the value
     * @return string string
     */
    public static String unescapeKeepCR(String value) {

//        String s = value.replace('\n', '\u0C04');
//        s = PropertyImpl.unescape(s);
//        s = s.replace('\u0C04', '\n');
//
//        return s;
        return PropertyImpl.unescape(value);
    }

    /**
     * Update translation
     *
     * @param key              the key
     * @param value            the value
     * @param file             the file
     * @param runAsWriteAction the run as write action
     */
    public static void doUpdateTranslation(String key, String value, @NotNull PropertiesFile file, boolean runAsWriteAction) {

        String escapedValue = I18nUtil.unicodeEscape(value);

        List<IProperty> properties = ApplicationManager.getApplication().runReadAction((Computable<List<IProperty>>) () ->
                file.findPropertiesByKey(key));

        Runnable runnable = () -> {
            if (!properties.isEmpty()) {
                try {
                    for (IProperty prop : properties) {
                        prop.setValue(escapedValue);
                    }
                } catch (Exception e) {
                    LOG.error("Update property error", e);
                }
            } else {

                // Create key
                insertProperty(file, key, value);
            }
        };

        ApplicationManager.getApplication().invokeLater(() -> {
            if (runAsWriteAction)
                executeWriteCommand(file.getProject(), "Update key '" + key + "'", runnable);
            else
                runnable.run();
        });
    }

    private static void insertProperty(PropertiesFile psiFile, String i18nKey, String value) {

        // find best position
        IProperty best = null;
        for (IProperty p : psiFile.getProperties()) {
            if (p.getUnescapedKey().compareTo(i18nKey) > 0)
                break;
            best = p;
        }

        // insert at postion
        if (best !=null) {
            IProperty p = PropertiesElementFactory.createProperty(psiFile.getProject(), i18nKey, value, null);
            psiFile.addPropertyAfter(p, best);
            //psiFile.addPropertyAfter(i18nKey, value, best2);
        } else {
            psiFile.addProperty(i18nKey, value);
        }
    }

    /**
     * Gets psi properties files.
     *
     * @param language the language
     * @param module   the module
     * @return the psi properties files
     */
    public static List<PropertiesFile> getPsiPropertiesFiles(@Nullable String language, @NotNull Module module) {

        List<PropertiesFile> list = new ArrayList<>();
        for (ResourceBundle bundle : getResourceBundles(module)) {

            for (PropertiesFile pf : bundle.getPropertiesFiles()) {

                if (language==null || language.equals(getLanguage(pf)))
                    list.add(pf);
            }
        }

        return list;
    }

    /**
     * Gets language.
     *
     * @param file the file
     * @return the language
     */
    @Nullable
    public static String getLanguage(@NotNull PropertiesFile file) {

        String name = file.getName();
        int i = name.lastIndexOf('_');
        if (i<0)
            return null;

        return name.substring(i +1, name.length()-11);
    }

    /**
     * Gets psi properties file.
     *
     * @param resourceBundle the resource bundle
     * @param language       the language
     * @return the psi properties file
     */
    public static PropertiesFile getPsiPropertiesFile(ResourceBundle resourceBundle, String language) {

         for (PropertiesFile f : resourceBundle.getPropertiesFiles()) {

            String name = f.getName();
            int i = name.indexOf('_');
            if (i < 0 || i == name.length() - 1)
                continue;

            if (name.endsWith(".properties"))
                name = name.substring(0, name.length()-11);

            String lang = name.substring(i + 1);
            if (language.equals(lang))
                return f;
        }

        return null;
    }


    /**
     * Gets all psi properties files.
     *
     * @param bundle the bundle
     * @param module the module
     * @return the all psi properties files
     */
    public static List<PropertiesFile> getAllPsiPropertiesFiles(String bundle, Module module) {
        if (TEST_I18N_FOLDER!=null) {
            VirtualFile[] child = TEST_I18N_FOLDER.getChildren();
            return Collections.singletonList((PropertiesFile) FileUtil.getFile(child[0], module.getProject()));
        }

        return innerGetPsiPropertiesFiles(bundle, null, module);
    }

    private static List<PropertiesFile> innerGetPsiPropertiesFiles(String bundle, @Nullable String language, Module module) {

        if (module == null) {
            return Collections.emptyList();
        }

        String message = "GetPsiPropertiesFiles for bundle '" + bundle + "', language '" + language + "', module '" + module.getName() + "'";
        LOG.trace(message);

        String packageName = bundle.substring(0, bundle.lastIndexOf('.'));
        PsiPackage pack = JavaUtil.findPackage(packageName, module);
        if (pack == null) {
            LOG.warn(message + " : package not found : '" + packageName + "' - STOP");
            return Collections.emptyList();
        }

        PsiFile[] psiFiles = pack.getFiles(JavaUtil.getJavaSearchScope(module));
        if (psiFiles.length == 0) {

            LOG.warn(message + " : no files found - STOP");
            return Collections.emptyList();
        }

        String searched = bundle.substring(bundle.lastIndexOf('.') + 1) + "_";
        if (language != null)
            searched += language + ".properties";

        LOG.trace(message + " : search file for : '" + searched + "'");
        List<PropertiesFile> ret = new ArrayList<>();
        for (PsiFile psiFile : psiFiles) {

            if (!(psiFile instanceof PropertiesFile)) {

                LOG.trace(message + " : file is not a PropertiesFile : '" + psiFile + "' - CONTINUE");
                continue;
            }

            if (language != null && psiFile.getName().equals(searched)) {
                ret.add((PropertiesFile) psiFile);
                return ret;
            } else if (psiFile.getName().startsWith(searched)) {
                ret.add((PropertiesFile) psiFile);
            }
        }

        LOG.trace(message + " : " + ret.size() + " files found");
        return ret;
    }

    /**
     * Gets resource bundles.
     *
     * @param module the module
     * @return the resource bundles
     */
    public static List<ResourceBundle> getResourceBundles(@NotNull Module module) {

        Collection<VirtualFile> files = FileTypeIndex.getFiles(PropertiesFileType.INSTANCE, GlobalSearchScope.projectScope(module.getProject()));

        Set<ResourceBundle> main = new HashSet<>();
        Set<ResourceBundle> secondary = new HashSet<>();

        for (VirtualFile vf : files) {

            PsiFile file = FileUtil.getFile(vf, module.getProject());
            if (file instanceof PropertiesFile) {

                String name = file.getName();
                if (!name.contains("_") || vf.getPath().contains("/target/"))
                    continue;

                if (module.equals(JavaUtil.getModule(file)))
                    main.add(((PropertiesFile) file).getResourceBundle());
                else
                    secondary.add(((PropertiesFile) file).getResourceBundle());
            }
        }

        List<ResourceBundle> lrw = new ArrayList<>(main);
        lrw.sort(Comparator.comparing(ResourceBundle::getBaseName));

        List<ResourceBundle> lro = new ArrayList<>(secondary);
        lro.sort(Comparator.comparing(ResourceBundle::getBaseName));

        lrw.addAll(lro);
        return lrw;
    }

    /**
     * Gets local psi properties files.
     *
     * @param module the module
     * @return the local psi properties files
     */
    public static List<PropertiesFile> getLocalPsiPropertiesFiles(Module module) {

        List<PropertiesFile> all = new ArrayList<>();
        return all;
    }

    /**
     * Gets psi properties.
     *
     * @param i18nKey  the 18 n key
     * @param language the language
     * @param module   the module
     * @return the psi properties
     */
    @NotNull
    public static List<IProperty> getPsiProperties(@Nullable String i18nKey, @Nullable String language, Module module) {
        return getPsiProperties(null, i18nKey, language, module);
    }

    /**
     * Gets psi properties.
     *
     * @param propertiesFile the properties file
     * @param i18nKey        the 18 n key
     * @param language       the language
     * @param module         the module
     * @return the psi properties
     */
    @NotNull
    public static List<IProperty> getPsiProperties(@Nullable PropertiesFile propertiesFile, @Nullable String i18nKey, @Nullable String language, Module module) {
        List<PropertiesFile> psiFiles;
        if (propertiesFile != null) {
            psiFiles = List.of((PropertiesFile) propertiesFile.getContainingFile());
        } else {
            psiFiles = getPsiPropertiesFiles(language, module);
        }
        if (psiFiles.isEmpty()) {
            return Collections.emptyList();
        }

        List<IProperty> properties = new ArrayList<>();
        for (PropertiesFile pf : psiFiles) {
            if (i18nKey != null) {
                List<IProperty> propertiesByKey = pf.findPropertiesByKey(i18nKey);
                properties.addAll(propertiesByKey);
            } else {
                properties.addAll(pf.getProperties());
            }
        }
        return properties;
    }

    /**
     * Create translation key
     *
     * @param bundle the bundle
     * @param key    the key
     * @param module the module
     */
    public static void doCreateTranslationKey(ResourceBundle bundle, String key, Module module) {

        List<PropertiesFile> files = bundle.getPropertiesFiles();

        executeWriteCommand(module.getProject(), "Create key '" + key + "'", () -> {
            try {
                for (PropertiesFile pf : files) {
                    insertProperty(pf, key, "");
                }
            } catch (Exception e) {
                LOG.error(e);
            }
        });
    }

    /**
     * Duplicate translation key
     *
     * @param key          the key
     * @param sourceBundle the source bundle
     * @param targetBundle the target bundle
     * @param propertyFile the property file
     * @return the string
     */
    public static String doDuplicateTranslationKey(String key,
           ResourceBundle sourceBundle,
           ResourceBundle targetBundle,
           PropertiesFile propertyFile) {

        // get target languages
        ResourceBundle resourceBundle = propertyFile.getResourceBundle();
        List<String> targetLanguages = getLanguages(resourceBundle);

        // load source translations
        Map<String, String> translations = new HashMap<>();
        for (String lang : targetLanguages) {

            PropertiesFile propertiesFile =  getPsiPropertiesFile(resourceBundle, lang);
            if (propertiesFile != null) {

              List<IProperty> psiProperties = getPsiProperties(propertiesFile, key, lang, null);
              if (!psiProperties.isEmpty())
                translations.put(lang, unescapeKeepCR(psiProperties.get(0).getValue()));
            }
        }

        // add suffix to key if target bundle is same as source bundle
        if (targetBundle.equals(sourceBundle))
            key += ".copy";

        // insert translations
        String finalKey = key;
        executeWriteCommand(propertyFile.getProject(), "Duplicate key '" + key + "'", () -> {
            for (Map.Entry<String, String> translation : translations.entrySet()) {

                PropertiesFile propertiesFile =  getPsiPropertiesFile(resourceBundle, translation.getKey());
                if (propertiesFile == null)
                    continue;

                doUpdateTranslation(finalKey, translation.getValue(), propertiesFile, true);
            }
        });

        return key;
    }

    /**
     * Delete translation key
     *
     * @param bundle the bundle
     * @param key    the key
     * @param module the module
     */
    public static void doDeleteTranslationKey(ResourceBundle bundle, String key, Module module) {

        List<PropertiesFile> files = bundle.getPropertiesFiles();
        executeWriteCommand(module.getProject(), "Delete key '" + key + "'", () -> {

            try {
                for (PropertiesFile pf : files) {
                    List<IProperty> properties = pf.findPropertiesByKey(key);
                    for (IProperty p : properties) {
                        p.getPsiElement().getNavigationElement().delete();
                    }
                }
            } catch (final Exception e) {
                LOG.error(e);
            }
        });

    }

    /**
     * doRenameI18nKey
     *
     * @param bundle the bundle
     * @param key    the key
     * @param newKey the new key
     * @param module the module
     */
    public static void doRenameI18nKey(ResourceBundle bundle, String key, String newKey, Module module) {
        if (key.equals(newKey))
            return;

        executeWriteCommand(module.getProject(), "Rename key '" + key + "' to '" + newKey + "'", () -> {

            String escapedNewKey = I18nUtil.unicodeEscape(newKey);
            for (PropertiesFile pf : bundle.getPropertiesFiles()) {

                for (IProperty p : pf.findPropertiesByKey(key)) {

                    try {
                        p.setName(escapedNewKey);
                    } catch (Exception e) {
                        LOG.error("Rename error", e);
                    }
                }
            }


//            List<String> moduleLanguages = getLanguages(module);
//            for (String language : moduleLanguages) {
//
//                String escapedNewKey = I18nUtil.unicodeEscape(newKey);
//
//                PropertiesFile pf = getPsiPropertiesFile(bundle, language, module);
//                if (pf == null)
//                    continue;
//
//                List<IProperty> properties = pf.findPropertiesByKey(key);
//                if (!properties.isEmpty()) {
//                    try {
//                        for (IProperty prop : properties) {
//                            prop.setName(escapedNewKey);
//                        }
//                    } catch (Exception e) {
//                        LOG.error("Rename error", e);
//                    }
//                }
//            }
        });
    }

    /**
     * Gets bundle.
     *
     * @param propertiesFile the properties file
     * @return the bundle
     */
    public static String getBundle(PropertiesFile propertiesFile) {

        Module module = JavaUtil.getModule(propertiesFile.getContainingFile());
        if (module == null)
            return null;
        
        String path = propertiesFile.getVirtualFile().getPath();
        int i = path.indexOf("!/");
        if (i > 0) {
            path = path.substring(i + 2);
        }
        else {
            String base;
            if (TEST_I18N_FOLDER!=null) {
                base = TEST_I18N_FOLDER.getPath();
            }
            else {
                base = JavaUtil.getModuleRootPath(module);
                if (base == null)
                    return null;
                base = base + "/src/main/resources/";
            }
            path = com.intellij.openapi.util.io.FileUtil.getRelativePath(base, path, '/');
            if (path == null)
                return null;
        }
        return path.substring(0, path.indexOf('_')).replace("/", ".");
    }

    /**
     * Gets psi properties sibling file.
     *
     * @param selectedPropertiesFile the selected properties file
     * @param siblingLanguage        the sibling language
     * @return the psi properties sibling file
     */
    public static PropertiesFile getPsiPropertiesSiblingFile(PropertiesFile selectedPropertiesFile, String siblingLanguage) {

        for (PropertiesFile f : selectedPropertiesFile.getResourceBundle().getPropertiesFiles()) {

            String name = f.getName();
            int i = name.indexOf('_');
            if (i < 0 || i == name.length() - 1)
                continue;

            if (name.endsWith(".properties"))
                name = name.substring(0, name.length()-11);

            String lang = name.substring(i + 1);
            if (siblingLanguage.equals(lang))
                return f;
        }

        return null;
    }

    /**
     * Gets best properties file
     * <p>
     * Go throw all bundles and select the first one having at least one translation
     *
     * @param i18nKey the 18 n key
     * @param module  the module
     * @return the best properties file
     */
    public static PropertiesFile getBestPropertiesFile(String i18nKey, Module module) {

        // Searched into each bundles
        PropertiesFile propertiesFile = null;
        for (ResourceBundle bundle : getResourceBundles(module)) {

            for (PropertiesFile pf : bundle.getPropertiesFiles()) {

                IProperty propertyByKey = pf.findPropertyByKey(i18nKey);
                if (propertyByKey == null)
                    continue;

                if (pf.getVirtualFile().isWritable())
                    return pf;

                propertiesFile = pf;
            }
        }

        return propertiesFile;
    }

    private static VirtualFile getI18nFolder(VirtualFile folder) {
        if (TEST_I18N_FOLDER != null) {
            return TEST_I18N_FOLDER;
        }

        if (null == folder) {
            return null;
        }
        if ("i18n".equals(folder.getName())) {
            return folder;
        }
        if (!folder.exists()) {
            return null;
        }

        VirtualFile[] resources = folder.getChildren();
        for (int i = 0; i < resources.length; i++) {

            VirtualFile subFolder = getI18nFolder(resources[i]);
            if (subFolder != null
                    && "i18n".equals(subFolder.getName())
                    && subFolder.getPath().contains("/src/main/resources/")) {
                return subFolder;
            }
        }
        return null;
    }

    /**
     * Prepare key for google translation string.
     *
     * @param key the key
     * @return the string
     */
    public static String prepareKeyForGoogleTranslation(String key) {

        StringBuilder sb = new StringBuilder();
        for (String s : key.split("[_\\-\\.]")) {

            if (sb.length()>0)
                sb.append(' ');

            if (s.toUpperCase().equals(s))
                sb.append(s.toLowerCase());
            else {
                String x = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, s);
                x = x.replace("_", " ");
                sb.append(x);
            }
        }

        return Strings.capitalize(sb.toString());
    }

    /**
     * Google translate
     *
     * @param key               the key
     * @param targetLanguage    the target language
     * @param sourceLanguage    the source language
     * @param sourceTranslation the source translation
     * @return the string
     * @throws IOException the io exception
     */
    public static String googleTranslate(String sourceLanguage, String targetLanguage, String sourceTranslation, String key) throws IOException {
        if (sourceLanguage.equals(targetLanguage)) return sourceTranslation;
        String translation = callUrlAndParseResult(sourceLanguage, targetLanguage, sourceTranslation);

        // Tips : if google return the same as source, try using the key directly...
        if (translation == null || translation.equals(sourceTranslation)) {

            String keyForTranslation = I18nUtil.prepareKeyForGoogleTranslation(key);
            if (! sourceTranslation.equals(keyForTranslation)) {
                translation = callUrlAndParseResult(Locale.ENGLISH.getLanguage(), targetLanguage, keyForTranslation);
            }
        }

        // Update translation
        if (translation == null || translation.isEmpty())
            return null;


        // Tune lower or uppercase according to source
        if (Character.isUpperCase(sourceTranslation.charAt(0)))
            translation = Strings.capitalize(translation);

        // TIPS : Keep space before punctuation at end
        if (translation.length()>2 && sourceTranslation.length()>2
                && sourceTranslation.charAt(sourceTranslation.length()-2) == ' '
                && translation.charAt(translation.length()-2) != ' ' && translation.charAt(translation.length()-1) == sourceTranslation.charAt(sourceTranslation.length()-1)) {

            translation = translation.substring(0, translation.length()-1) + ' ' + translation.substring(translation.length()-1);
        }


        return translation;
    }

    private static String callUrlAndParseResult(String langFrom, String langTo, String word) throws IOException {

        String url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + URLEncoder.encode(langFrom, StandardCharsets.UTF_8.name()) +
                "&tl=" + URLEncoder.encode(langTo, StandardCharsets.UTF_8.name()) +
                "&dt=t&q=" + URLEncoder.encode(word, StandardCharsets.UTF_8.name());

        URLConnection con = HttpConfigurable.getInstance().openConnection(url);

        con.setRequestProperty("User-Agent", "Mozilla/5.0");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return parseResult(response.toString());
    }

    private static String parseResult(String inputJson) {

        JsonElement elt = JsonParser.parseString(inputJson);
        if (elt == null || ! elt.isJsonArray())
            return null;

        JsonArray jsonArray = elt.getAsJsonArray();
        if (jsonArray.size()<1)
            return null;

        JsonElement elt2 = jsonArray.get(0);
        if ( ! elt2.isJsonArray())
            return null;

        JsonArray jsonArray2 = elt2.getAsJsonArray();
        if (jsonArray2.size()<1)
            return null;

        JsonElement elt3 = jsonArray2.get(0);
        if ( ! elt3.isJsonArray())
            return null;

        JsonArray jsonArray3 = elt3.getAsJsonArray();
        if (jsonArray3.size()<1)
            return null;

        JsonElement elt4 = jsonArray3.get(0);
        if (! elt4.isJsonPrimitive())
            return null;

        return elt4.getAsString();
    }

    public static void executeWriteCommand(Project project, String text, Runnable runnable) {

        CommandProcessor.getInstance().executeCommand(project, () -> {

                    Application application = ApplicationManager.getApplication();
                    application.runWriteAction(runnable::run);
                },
                text, "I18N+");
    }

    /**
     * findI18nKey
     */
    public static String findI18nKey(PsiElement target) {

        if (target instanceof Property) {

            Property property = (Property)target;
            return property.getFirstChild().getText().trim();
        }

        String text = target.getText();
        if (!text.startsWith("'") && !text.startsWith("\""))
            return null;

        return StringUtilExt.removeQuotes(target.getText());
    }

    /**
     * findI18nKey
     */
    public static Pair<String, PsiFile> findI18nKey(PsiReference target) {

        // multi references
        if (target instanceof PsiMultiReference) {

            PsiMultiReference mref = (PsiMultiReference)target;
            for (PsiReference ref : mref.getReferences()) {

                Pair<String, PsiFile> key = findI18nKey(ref);
                if (key != null)
                    return key;
            }

            return null;
        }

        return new Pair<>(StringUtilExt.removeQuotes(target.getCanonicalText()) ,target.getElement().getContainingFile());
    }

    /**
     * findTarget
     */
    public static PsiElement findTarget(Editor editor) {
        int offset = editor.getCaretModel().getOffset();

        Project editorProject = editor.getProject();
        if (editorProject == null)
            return null;

        //if (!editorProject.equals(projectCurrent))
        //    return null;

        Document document = editor.getDocument();

        PsiFile file = FileUtil.getFile(document, editorProject);
        if (file == null)
            return null;

        PsiElement element = file.findElementAt(offset);
        if (element == null)
            return null;

        Property property = SPsiUtils.findSurrounding(element, Property.class);
        if (property !=null) {
            return property.getNavigationElement();
        }

        if (element instanceof TreeElement) {
            TreeElement token = (TreeElement)element;
            if (JavaTokenType.STRING_LITERAL.equals(token.getElementType())) {
                return element;
            }
        }

        return null;
    }

    /**
     * findReferenceTarget
     */
    public static PsiReference findReferenceTarget(Editor editor) {

        int offset = editor.getCaretModel().getOffset();

        Project project = editor.getProject();
        if (project == null)
            return null;

        //if (!project.equals(projectCurrent))
        //    return null;

        Document document = editor.getDocument();

        PsiFile file = FileUtil.getFile(document, project);
        if (file == null)
            return null;

        PsiReference referenceAt = file.findReferenceAt(offset);
        if (referenceAt != null)
            return referenceAt;

        return file.getFirstChild().getReference();

    }

    @NotNull
    public static Triple<String, PsiFile, Module> findI18nKeyFromEditor(final Editor editor) {
        Module module = null;
        String i18nKey = null;
        PsiFile originFile = null;

        // Search for reference
        LOG.trace("findI18nKeyFromEditor : search for reference...");
        PsiReference referenceTarget = null;
        try {
            referenceTarget = SlowOperations.allowSlowOperations((ThrowableComputable<PsiReference, Throwable>) () ->
                findReferenceTarget(editor));
        } catch (Throwable e) {
            LOG.error("Translation init error", e);
        }
        LOG.trace("findI18nKeyFromEditor : reference : " + referenceTarget);
        if (referenceTarget != null) {
            module = JavaUtil.getModule(referenceTarget.getElement());
            final Pair<String, PsiFile> i18nKeyFile = findI18nKey(referenceTarget);
            if (i18nKeyFile != null) {
               i18nKey = i18nKeyFile.getFirst();
               originFile = i18nKeyFile.getSecond();
            }
        }

        // search by target of editor
        if (i18nKey == null) {

            LOG.trace("findI18nKeyFromEditor : key not found yet...");
            // Search for element
            PsiElement target = findTarget(editor);
            LOG.trace("findI18nKeyFromEditor : target : " + target);
            if (target != null) {
                module = JavaUtil.getModule(target);
                i18nKey = findI18nKey(target);
                originFile = target.getContainingFile();
            }
        }

        return new Triple<>(i18nKey,originFile,module);
    }
}
