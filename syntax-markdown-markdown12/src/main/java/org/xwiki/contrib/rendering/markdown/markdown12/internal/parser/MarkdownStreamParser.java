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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.common.internal.parser.AbstractMarkdownStreamParser;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.parser.ParserEmulationFamily;

import static org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.MarkdownParser.MARKDOWN_1_2;

/**
 * Markdown.pl (original markdown from https://daringfireball.net/projects/markdown/syntax) stream parser based on the
 * <a href="https://github.com/vsch/flexmark-java">Flexmark Java Parser</a>.
 *
 * @version $Id$
 * @since 8.4
 */
@Component
@Named("markdown/1.2")
@Singleton
public class MarkdownStreamParser extends AbstractMarkdownStreamParser
{
    @Override
    public Syntax getSyntax()
    {
        return MARKDOWN_1_2;
    }

    @Override
    protected ParserEmulationFamily getParserEmulationFamily()
    {
        return ParserEmulationFamily.MARKDOWN;
    }
}
