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
    private MarkdownEscapeHandler escapeHandler;

    private String lastPrinted;

    private String nextText;

    public MarkdownEscapeWikiPrinter(WikiPrinter printer, XWikiSyntaxListenerChain listenerChain)
    {
        super(printer);

        this.escapeHandler = new MarkdownEscapeHandler();
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
    public void print(String text)
    {
        // Remember what is printed right after the delayed text, since the escaping can depend on it.
        this.nextText = text;
        try {
            super.print(text);
        } finally {
            this.nextText = null;
        }
    }

    @Override
    public void println(String text)
    {
        this.nextText = text + '\n';
        try {
            super.println(text);
        } finally {
            this.nextText = null;
        }
    }

    @Override
    public void flush()
    {
        if (getBuffer().length() > 0) {
            this.escapeHandler.escape(getBuffer(), this.nextText);
            super.flush();
        }
    }

    /**
     * Print the delayed text.
     *
     * @param nextText the content that will be printed right after the delayed text
     * @since 8.10.0
     */
    public void flush(String nextText)
    {
        this.nextText = nextText;
        try {
            flush();
        } finally {
            this.nextText = null;
        }
    }

    /**
     * @param html some inline HTML content to print
     * @return the HTML content where the text located between the HTML tags is escaped
     * @since 8.10.0
     */
    public String escapeInlineHTML(String html)
    {
        return this.escapeHandler.escapeInlineHTML(html);
    }

    /**
     * @param html some HTML content to print at the beginning of a block
     * @return the HTML content where the parts that are parsed as Markdown are escaped
     * @since 8.10.0
     */
    public String escapeBlockHTML(String html)
    {
        return this.escapeHandler.escapeBlockHTML(html);
    }

    /**
     * @param raw some raw content to print
     * @return the raw content where the pipes that would be considered as table cell separators are escaped
     * @since 8.10.0
     */
    public String escapeRawPipes(String raw)
    {
        return this.escapeHandler.escapeRawPipes(raw);
    }

    public void setOnNewLine(boolean onNewLine)
    {
        this.escapeHandler.setOnNewLine(onNewLine);
    }

    /**
     * @param inLinkLabel true if the printed text is located inside a link label
     * @since 8.10.0
     */
    public void setInLinkLabel(boolean inLinkLabel)
    {
        this.escapeHandler.setInLinkLabel(inLinkLabel);
    }

    /**
     * @param inTableCell true if the printed text is located inside a table cell
     * @since 8.10.0
     */
    public void setInTableCell(boolean inTableCell)
    {
        this.escapeHandler.setInTableCell(inTableCell);
    }

    public boolean isOnNewLine()
    {
        return this.escapeHandler.isOnNewLine();
    }
}
