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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.renderer;

import org.xwiki.rendering.internal.renderer.xwiki20.XWikiSyntaxListenerChain;
import org.xwiki.rendering.renderer.printer.LookaheadWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

/**
 * A Wiki printer that knows how to escape characters that would otherwise mean something different in Markdown
 * syntax. For example if we have "**" as special symbols (and not as a Bold Format block) we need to escape them to
 * "\*\*" as otherwise they'd be considered bold after being rendered.
 *
 * @version $Id: 21544c379e02639ba59cfc175218da4ce3449591 $
 * @since 8.1RC1
 */
public class MarkdownEscapeWikiPrinter extends LookaheadWikiPrinter
{
    private XWikiSyntaxListenerChain listenerChain;

    private MarkdownEscapeHandler escapeHandler;

    private String lastPrinted;

    public MarkdownEscapeWikiPrinter(WikiPrinter printer, XWikiSyntaxListenerChain listenerChain)
    {
        super(printer);

        this.escapeHandler = new MarkdownEscapeHandler();

        this.listenerChain = listenerChain;
    }

    @Override
    protected void printInternal(String text)
    {
        super.printInternal(text);

        int length = text.length();

        if (length > 0) {
            this.escapeHandler.setOnNewLine(text.charAt(length - 1) == '\n');
        }

        this.lastPrinted = text;
    }

    @Override
    protected void printlnInternal(String text)
    {
        super.printlnInternal(text);

        this.escapeHandler.setOnNewLine(true);

        this.lastPrinted = "\n";
    }

    @Override
    public void flush()
    {
        if (getBuffer().length() > 0) {
            this.escapeHandler.escape(getBuffer(), this.listenerChain);
            super.flush();
        }
    }

    public void setOnNewLine(boolean onNewLine)
    {
        this.escapeHandler.setOnNewLine(onNewLine);
    }

    public boolean isOnNewLine()
    {
        return this.escapeHandler.isOnNewLine();
    }
}
