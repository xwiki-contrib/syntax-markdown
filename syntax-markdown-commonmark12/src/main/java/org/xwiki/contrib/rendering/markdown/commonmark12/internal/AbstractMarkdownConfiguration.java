/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.rendering.markdown.commonmark12.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

import com.vladsch.flexmark.ext.abbreviation.AbbreviationExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.xwiki.macros.MacroExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

/**
 * Common class to create markdown configurations for flexmark-based parsers.
 *
 * @version $Id$
 * @since 8.8
 */
public abstract class AbstractMarkdownConfiguration
{
    // Default extensions we want to have in all MD flavors for XWiki.
    private static final List<Class<?>> DEFAULT_EXTENSIONS = Arrays.asList(
        WikiLinkExtension.class,
        AutolinkExtension.class,
        DefinitionExtension.class,
        TablesExtension.class,
        StrikethroughSubscriptExtension.class,
        SuperscriptExtension.class,
        AbbreviationExtension.class,
        MacroExtension.class
    );

    protected abstract Logger getLogger();

    protected MutableDataHolder getDefaultOptions(ParserEmulationProfile parserEmulationProfile,
        List<Class<?>> additionalExtensionClasses)
    {
        // Configure Parser Family
        MutableDataHolder options = new MutableDataSet();
        options.setFrom(parserEmulationProfile);

        // Configure extensions
        List<Extension> extensions = new ArrayList<>();
        List<Class<?>> resolvedExensionClasses = new ArrayList<>();
        resolvedExensionClasses.addAll(DEFAULT_EXTENSIONS);
        resolvedExensionClasses.addAll(additionalExtensionClasses);
        for (Class<?> extensionClass : resolvedExensionClasses) {
            try {
                Method method = extensionClass.getMethod("create");
                Extension extension = (Extension) method.invoke(null);
                extensions.add(extension);
            } catch (Exception e) {
                // Invalid extension, skip it
                getLogger().warn("Invalid extension: [{}]. Root cause: [{}]", extensionClass.getName(),
                    ExceptionUtils.getRootCauseMessage(e));
            }
            options.set(Parser.EXTENSIONS, extensions);
        }

        return options;
    }
}
