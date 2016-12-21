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
package org.xwiki.contrib.rendering.markdown12.internal.parser;

import java.io.Reader;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationFamily;
import com.vladsch.flexmark.util.options.MutableDataHolder;
import com.vladsch.flexmark.util.options.MutableDataSet;

import static org.xwiki.contrib.rendering.markdown12.internal.parser.MarkdownParser.MARKDOWN_1_2;

/**
 * Markdown Streaming Parser.
 *
 * @version $Id$
 * @since 8.4
 */
@Component
@Named("markdown/1.2")
@Singleton
public class MarkdownStreamParser implements StreamParser
{
    @Inject
    private Provider<FlexmarkNodeVisitor> visitorProvider;

    @Override
    public Syntax getSyntax()
    {
        return MARKDOWN_1_2;
    }

    @Override
    public void parse(Reader source, Listener listener) throws ParseException
    {
        Node document;
        try {
            // See https://github.com/vsch/flexmark-java/wiki/Usage
            MutableDataHolder options = new MutableDataSet();
//            options.setFrom(ParserEmulationFamily.COMMONMARK.getOptions());
//            options.setFrom(ParserEmulationFamily.KRAMDOWN.getOptions());
//            options.setFrom(ParserEmulationFamily.FIXED_INDENT.getOptions());
            options.setFrom(ParserEmulationFamily.MARKDOWN.getOptions());
            Parser parser = Parser.builder(options).build();
            document = parser.parse(IOUtils.toString(source));
        } catch (Exception e) {
            throw new ParseException("Failed to parse Markdown content 1.2", e);
        }

        this.visitorProvider.get().visit(document, listener);
    }
}
