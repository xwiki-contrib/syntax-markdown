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
package org.xwiki.contrib.rendering.markdown.flavor.github.internal;

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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.markdown12.MarkdownConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GitHub Flavored Markdown Configuration.
 *
 * @version $Id$
 * @since 8.7
 */
@Component
@Singleton
@Named("MarkdownConfigurationGithub")
public class DefaultMarkdownGithubConfiguration implements MarkdownConfiguration
{
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

    @Inject
    private Logger logger;

    @Override
    public MutableDataHolder getOptions()
    {
        return getDefaultOptions();
    }

    /**
     * Creates and returns options with GitHub ParserEmulationProfile.
     *
     * @return the configured options for GitHub Flavor.
     */
    public MutableDataHolder getDefaultOptions()
    {
        // Configure Parser Family
        MutableDataHolder options = new MutableDataSet();
        options.setFrom(ParserEmulationProfile.GITHUB);

        // Configure other options
        options.set(WikiLinkExtension.IMAGE_LINKS, true);

        // Configure extensions
        List<Extension> extensions = new ArrayList<>();
        for (Class<?> extensionClass : DEFAULT_EXTENSIONS) {
            try {
                Method method = extensionClass.getMethod("create");
                Extension extension = (Extension) method.invoke(null);
                extensions.add(extension);
            } catch (Exception e) {
                // Invalid extension, skip it
                this.logger.warn("Invalid extension: [{}]. Root cause: [{}]", extensionClass.getName(),
                        ExceptionUtils.getRootCauseMessage(e));
            }
            options.set(Parser.EXTENSIONS, extensions);
        }

        return options;
    }
}
