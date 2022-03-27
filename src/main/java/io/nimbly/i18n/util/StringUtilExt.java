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

/**
 * StringUtil
 * User: Maxime HAMM
 * Date: 12/06/2016
 */
public class StringUtilExt extends com.intellij.openapi.util.text.StringUtil {

    /**
     * Remove quotes
     */
    public static String removeQuotes(String s) {
        StringBuilder sb = new StringBuilder(s);
        int length = sb.length();
        if (length >0 && (sb.charAt(0) == '\'' || sb.charAt(0) == '\"')) {
            sb.deleteCharAt(0);
            length--;
        }
        if (length>0 && (sb.charAt(length-1) == '\'' || sb.charAt(length-1) == '\"')) {
            sb.deleteCharAt(length-1);
        }
        return sb.toString();
    }

}
