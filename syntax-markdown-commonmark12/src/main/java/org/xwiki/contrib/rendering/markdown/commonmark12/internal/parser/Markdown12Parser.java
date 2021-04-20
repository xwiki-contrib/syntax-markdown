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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.parser;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.CommonMark12SyntaxProvider;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.syntax.Syntax;

/**
 * CommonMark Markdown Parser using <a href="https://github.com/vsch/flexmark-java">flexmark-java</a>.
 *
 * @version $Id$
 * @since 8.4
 */
@Component
@Named("markdown/1.2")
@Singleton
public class Markdown12Parser extends AbstractMarkdownParser
{
    /**
     * Streaming Markdown Parser.
     */
    @Inject
    @Named("markdown/1.2")
    private StreamParser commonMarkStreamParser;

    @Override
    protected StreamParser getMarkdownStreamParser()
    {
        return this.commonMarkStreamParser;
    }

    @Override
    public Syntax getSyntax()
    {
        return CommonMark12SyntaxProvider.MARKDOWN_COMMON_1_2;
    }
}
