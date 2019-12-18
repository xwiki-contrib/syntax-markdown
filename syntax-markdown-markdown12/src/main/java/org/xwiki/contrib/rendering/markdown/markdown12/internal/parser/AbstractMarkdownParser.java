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

import java.io.Reader;

import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.parser.XDOMGeneratorListener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.parser.StreamParser;

/**
 * Base class for Markdown Streaming Parsers for the various Markdown flavors. Implemented using the
 * <a href="https://github.com/vsch/flexmark-java">Flexmark Java Parser</a>.
 *
 * @version $Id$
 * @since 8.4
 */
public abstract class AbstractMarkdownParser implements Parser
{
    protected abstract StreamParser getMarkdownStreamParser();

    @Override
    public XDOM parse(Reader source) throws ParseException
    {
        XDOMGeneratorListener xdomGeneratorListener = new XDOMGeneratorListener();
        getMarkdownStreamParser().parse(source, xdomGeneratorListener);
        return xdomGeneratorListener.getXDOM();
    }
}
