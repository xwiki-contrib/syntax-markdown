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
package org.xwiki.contrib.rendering.markdown.common.internal.parser;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.common.MarkdownConfiguration;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;

import com.vladsch.flexmark.Extension;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationFamily;
import com.vladsch.flexmark.util.options.MutableDataHolder;
import com.vladsch.flexmark.util.options.MutableDataSet;

/**
 * Base class for Markdown Streaming Parsers for the various Markdown flavors. Implemented using the
 * <a href="https://github.com/vsch/flexmark-java">Flexmark Java Parser</a>.
 *
 * @version $Id$
 * @since 8.4
 */
@Component
@Singleton
public abstract class AbstractMarkdownStreamParser implements StreamParser
{
    @Inject
    private Provider<FlexmarkNodeVisitor> visitorProvider;

    @Inject
    private MarkdownConfiguration configuration;

    protected abstract ParserEmulationFamily getParserEmulationFamily();

    @Override
    public void parse(Reader source, Listener listener) throws ParseException
    {
        Node document;
        try {
            // Configure options
            MutableDataHolder options = new MutableDataSet();
            options.setFrom(getParserEmulationFamily().getOptions());

            // Configure extensions
            List<Extension> extensions = new ArrayList<>();
            for (Class extensionClass : this.configuration.getExtensionClasses()) {
                Method method = extensionClass.getMethod("create");
                Extension extension = (Extension) method.invoke(null);
                extensions.add(extension);
            }

            Parser parser = Parser.builder(options).extensions(extensions).build();
            document = parser.parse(IOUtils.toString(source));
        } catch (Exception e) {
            throw new ParseException(
                String.format("Failed to parse Markdown content for family [%s]", getParserEmulationFamily()), e);
        }

        this.visitorProvider.get().visit(document, listener, getSyntax());
    }
}
