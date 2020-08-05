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
package org.xwiki.contrib.rendering.markdown.flavor.github.internal.parser;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.Markdown12Parser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Markdown Parser using <a href="https://github.com/vsch/flexmark-java">flexmark-java</a>.
 *
 * @version $Id$
 * @since 8.7
 */
@Component
@Named("markdown+github/1.0")
@Singleton
public class MarkdownGithubParser extends Markdown12Parser
{
    /**
     * The Syntax Key for GitHub Flavored Markdown.
     */
    public static final Syntax MARKDOWN_GITHUB = new Syntax(new SyntaxType("markdown+github",
            "GitHub Flavored Markdown"), "1.0");

    /**
     * Streaming Markdown Parser.
     */
    @Inject
    @Named("markdown+github/1.0")
    private StreamParser commonMarkStreamParser;

    @Override
    protected StreamParser getMarkdownStreamParser()
    {
        return this.commonMarkStreamParser;
    }

    @Override
    public Syntax getSyntax()
    {
        return MARKDOWN_GITHUB;
    }
}
