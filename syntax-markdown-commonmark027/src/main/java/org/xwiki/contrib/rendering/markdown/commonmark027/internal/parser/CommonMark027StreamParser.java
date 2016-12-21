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
package org.xwiki.contrib.rendering.markdown.commonmark027.internal.parser;

import javax.inject.Named;

import org.xwiki.contrib.rendering.markdown.common.internal.parser.AbstractMarkdownStreamParser;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.parser.ParserEmulationFamily;

import static org.xwiki.contrib.rendering.markdown.commonmark027.internal.parser.CommonMark027Parser.COMMONMARKDOWN_0_27;

/**
 * Commons Mark 0.27 Streaming Parser.
 *
 * @version $Id$
 * @since 8.4
 */
@Named("commonmark/0.27")
public class CommonMark027StreamParser extends AbstractMarkdownStreamParser
{
    @Override
    public Syntax getSyntax()
    {
        return COMMONMARKDOWN_0_27;
    }

    @Override
    protected ParserEmulationFamily getParserEmulationFamily()
    {
        return ParserEmulationFamily.COMMONMARK;
    }
}
