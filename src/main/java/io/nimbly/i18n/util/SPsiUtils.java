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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * SPsiUtils
 * User: Maxime HAMM
 * Date: 07/06/2016
 */
public class SPsiUtils {

    private SPsiUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Find parent of type
     */
    public static <T extends PsiElement> T findSurrounding(PsiElement bean, Class<T> clazz) {
        PsiElement b = bean.getParent();
        while (b!=null && ! (b instanceof PsiFile)) {
            if (clazz.isAssignableFrom(b.getClass())) {
                return (T)b;
            }
            b = b.getParent();
        }
        return null;
    }
}
