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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * AbstractSnapView
 * User: Maxime HAMM
 * Date: 14/01/2017
 */
public abstract class AbstractSnapView extends JPanel {

    private final MyDocumentListener myDocumentListener = new MyDocumentListener();
    private final Map<Document, Boolean> myMonitoredDocuments  = CollectionFactory.createWeakMap();
    private final CaretListener myCaretListener = new MyCaretListener();

    protected AbstractSnapView() {
        EditorFactory factory = EditorFactory.getInstance();
        if (factory != null) {
            factory.addEditorFactoryListener(new MyEditorFactoryListener(), ApplicationManager.getApplication());
            for (Editor editor : factory.getAllEditors()) {
                registerListeners(editor);
            }
        }
    }

    /**
     * Do editor document changed
     */
    protected abstract void doEditorDocumentChanged(Editor editor, DocumentEvent event);

    /**
     * Do caret position changed
     */
    protected abstract void doCaretPositionChanged(CaretEvent e);

    /**
     *
     */
    private void registerListeners(@NotNull Editor editor) {
        editor.getCaretModel().addCaretListener(myCaretListener);

        Document document = editor.getDocument();
        if (myMonitoredDocuments.put(document, Boolean.TRUE) == null) {
            document.addDocumentListener(myDocumentListener);
        }
    }

    /**
     * unRegisterListeners
     */
    private void unRegisterListeners(@NotNull Editor editor) {
        editor.getCaretModel().removeCaretListener(myCaretListener);

        Document document = editor.getDocument();
        if (myMonitoredDocuments.remove(document) != null) {
            document.removeDocumentListener(myDocumentListener);
        }
    }

    /*************************************
     * MyEditorFactoryListener
     */
    private class MyEditorFactoryListener implements EditorFactoryListener {
        @Override
        public void editorCreated(@NotNull EditorFactoryEvent event) {
            registerListeners(event.getEditor());
        }

        @Override
        public void editorReleased(@NotNull EditorFactoryEvent event) {
            unRegisterListeners(event.getEditor());
        }
    }

    /*************************************
     * MyCaretListener
     */
    private class MyCaretListener extends CaretAdapter {
        @Override
        public void caretPositionChanged(CaretEvent e) {
            doCaretPositionChanged(e);
            myDocumentListener.setEditor(e.getEditor());
        }
    }

    /*************************************
     * MyDocumentListener
     */
    private class MyDocumentListener extends DocumentAdapter {

        private Editor editor = null;

        public void setEditor(Editor editor) {
            this.editor = editor;
        }

        @Override
        public void documentChanged(DocumentEvent e) {
            if (editor == null)
                return;
            Project project = editor.getProject();
            if (project == null || project.isDisposed())
                return;
            PsiDocumentManager.getInstance(project).performForCommittedDocument(e.getDocument(),
                () -> doEditorDocumentChanged(editor, e));

        }
    }
}
