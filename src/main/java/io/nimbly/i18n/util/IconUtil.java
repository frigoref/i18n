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

import static com.intellij.util.IconUtil.textToIcon;

import com.intellij.ui.LayeredIcon;
import com.intellij.ui.scale.JBUIScale;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;

/**
 * IconUtils
 * User: Maxime HAMM
 * Date: 10/01/2017
 */
public class IconUtil {

    public static final int SPACING = 10;

    private IconUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Add text
     */
    @NotNull
    public static Icon addText(@NotNull Icon base, @NotNull String text, float size, int position) {
        LayeredIcon icon = new LayeredIcon(2);
        icon.setIcon(base, 0);
        icon.setIcon(textToIcon(text, new JLabel(), JBUIScale.scale(size)), 1, position);
        return icon;
    }
}
