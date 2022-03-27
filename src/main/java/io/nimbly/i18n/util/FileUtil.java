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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EditorUtil
 * User: Maxime HAMM
 * Date: 06/01/2017
 */
public class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static Editor[] getEditors() {
        EditorFactory factory = EditorFactory.getInstance();
        if (factory == null)
            return new Editor[0];

        return factory.getAllEditors();
    }

    public static PsiFile getFile(Editor editor) {
        if (editor == null) return null;
        final Project project = editor.getProject();
        if (project == null) return null;
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }

    public static PsiFile getFile(Document document, Project project) {
        return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }

    public static PsiFile getFile(PsiElement element) {
        if (! element.isValid())
            return null;
        return element.getContainingFile();
    }

    @Nullable
    public static PsiFile getFile(VirtualFile file, Project project) {
        return PsiManager.getInstance(project).findFile(file);
    }

    @Nullable
    public static PsiDirectory getDirectory(@NotNull VirtualFile directory, @NotNull Project project) {
        return PsiManager.getInstance(project).findDirectory(directory);
    }

    public static Document getDocument(PsiElement psiElement) {
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null)
            return null;

        VirtualFile file = containingFile.getVirtualFile();
        if (file == null)
            return null;

        return FileDocumentManager.getInstance().getDocument(file);
    }
}
