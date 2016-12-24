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
package org.xwiki.contrib.rendering.markdown.markdown12.internal;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.rendering.markdown.markdown12.MarkdownConfiguration;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.parser.ParserEmulationFamily;

@Component
@Singleton
public class DefaultMarkdownConfiguration implements MarkdownConfiguration
{
    private static final List<Class> DEFAULT_EXTENSIONS = Arrays.asList(
        WikiLinkExtension.class,
        AutolinkExtension.class,
        DefinitionExtension.class,
        TablesExtension.class
    );

    private static final ParserEmulationFamily DEFAULT_FAMILY = ParserEmulationFamily.valueOf("COMMONMARK");

    private static final String PREFIX = "rendering.markdown.";

    private static final String EXTENSIONS_KEY = PREFIX + "extensions";

    private static final String FAMILY_KEY = PREFIX + "family";

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configurationSource;

    @Override
    public ParserEmulationFamily getEmulationFamily()
    {
        ParserEmulationFamily family = DEFAULT_FAMILY;
        String familyAsString = this.configurationSource.getProperty(FAMILY_KEY);
        if (familyAsString != null) {
            family = ParserEmulationFamily.valueOf(familyAsString);
        }
        return family;
    }

    @Override
    public List<Class> getExtensionClasses()
    {
        return this.configurationSource.getProperty(EXTENSIONS_KEY, DEFAULT_EXTENSIONS);
    }
}
