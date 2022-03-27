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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * I18nSnapWindowFactory
 * User: Maxime HAMM
 * Date: 14/01/2017
 *
 * See QuickDocOnMouseOverManager
 */
public class I18nSnapWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

        // Individual Translation tab in I18n window
        Content contentIndividualTranslation = contentFactory.createContent(new TranslationSnapView(), "Individual Translation", false);
        toolWindow.getContentManager().addContent(contentIndividualTranslation);
        // make sure view knows it is selected
        ((AbstractI18nSnapView) contentIndividualTranslation.getComponent())
              .setSelected(contentIndividualTranslation.isSelected());

        // Mass Translation tab in I18n window
        Content contentMassTranslation = contentFactory.createContent(new MassTranslationSnapView(), "Mass Translation", false);
        toolWindow.getContentManager().addContent(contentMassTranslation);

        // recognize changes in selected tab and forward the information to the view
        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                final Content changedContent = event.getContent();
                ((AbstractI18nSnapView) changedContent.getComponent()).setSelected(changedContent.isSelected());
            }
        });
    }

}
