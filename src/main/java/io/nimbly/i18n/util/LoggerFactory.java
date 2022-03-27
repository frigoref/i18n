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

import com.intellij.openapi.diagnostic.Logger;

/**
 * Logger
 * User: Maxime HAMM
 * Date: 17/03/2017
 */
public class LoggerFactory {


    private LoggerFactory() {
        throw new IllegalStateException("Utility class");
    }

    public static Logger getInstance(Class<?> clazz) {
        return getInstance(clazz, null);
    }

    public static Logger getInstance(Class<?> clazz, String suffix) {
        String canonicalName = clazz.getName();
        if (canonicalName.startsWith("io.nimly.i18n"))
            canonicalName = "i18n.." + clazz.getSimpleName();
        if (suffix != null) {
            canonicalName += "." + suffix;
        }

        return Logger.getInstance(canonicalName);
    }
}
