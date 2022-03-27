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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

/**
 * JavaUtils
 * User: Maxime HAMM
 * Date: 10/06/2016
 *
 * @see com.intellij.psi.util.MethodSignatureUtil
 */
public class JavaUtil {

  public static final String TEST_MODULE_PATH = null;

    private JavaUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get's module scope
     */
    public static GlobalSearchScope getJavaSearchScope(Module module) {
        return module.getModuleWithDependenciesAndLibrariesScope(true);
    }

    /**
     * Get's module
     */
    public static Module getModule(PsiElement element) {
        if (element == null)
            return null;

        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module == null) {
            PsiFile file = element.getContainingFile();
            if (file !=null)
                module = ModuleUtilCore.findModuleForPsiElement(file);
        }
        return module;
    }

    /**
     * Get's module root path
     */
    @Nullable
    public static String getModuleRootPath(Module module) {
        if (TEST_MODULE_PATH != null)
            return TEST_MODULE_PATH;

        VirtualFile moduleRoot = getModuleRoot(module);
        return moduleRoot != null ? moduleRoot.getPath() : null;
    }

    /**
     * Get's module root path
     */
    @Nullable
    public static VirtualFile getModuleRoot(Module module) {
         if (module == null)
            return null;

        String path = module.getModuleFile().getPath();
        return VirtualFileManager.getInstance().findFileByUrl("file://" + path.substring(0, path.lastIndexOf('/')));
    }

    /**
     * Find package
     */
    public static PsiPackage findPackage(String qualifieName, Module module) {
        if (module == null)
            return null;
        return JavaPsiFacade.getInstance(module.getProject()).findPackage(qualifieName);
    }

}
