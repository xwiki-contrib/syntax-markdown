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

import java.io.StringReader;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.renderer.MarkdownEscapeHandler;
import org.xwiki.rendering.listener.InlineFilterListener;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.vladsch.flexmark.ast.util.ReferenceRepository;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.sequence.Escaping;

public abstract class AbstractNodeVisitor
{
    /**
     * HTML title attribute.
     */
    protected static final String TITLE_ATTRIBUTE = "title";

    private static final char ESCAPE_CHAR = MarkdownEscapeHandler.ESCAPE_CHAR.charAt(0);

    private NodeVisitor visitor;

    private Deque<Listener> listeners;

    private ReferenceRepository referenceRepository;

    private PrintRendererFactory plainRendererFactory;

    private StreamParser plainTextStreamParser;

    public AbstractNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        this(visitor, listeners, null);
    }

    public AbstractNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners,
        PrintRendererFactory plainRendererFactory)
    {
        this(visitor, listeners, plainRendererFactory, null);
    }

    public AbstractNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners,
        PrintRendererFactory plainRendererFactory, StreamParser plainTextStreamParser)
    {
        this.visitor = visitor;
        this.listeners = listeners;
        this.plainRendererFactory = plainRendererFactory;
        this.plainTextStreamParser = plainTextStreamParser;
    }

    /**
     * @return the top listener on the stack
     */
    protected Listener getListener()
    {
        return this.listeners.peek();
    }

    protected void pushListener(Listener listener)
    {
        this.listeners.push(listener);
    }

    protected void popListener()
    {
        this.listeners.pop();
    }

    protected NodeVisitor getVisitor()
    {
        return this.visitor;
    }

    public void setReferenceRepository(ReferenceRepository referenceRepository)
    {
        this.referenceRepository = referenceRepository;
    }

    protected ReferenceRepository getReferenceRepository()
    {
        return this.referenceRepository;
    }

    /**
     * Add a title parameter.
     *
     * @param parameters the map to which to add the title parameter
     * @param title the title parameter value to add
     */
    protected void addTitle(Map<String, String> parameters, String title)
    {
        if (StringUtils.isNotEmpty(title)) {
            parameters.put(TITLE_ATTRIBUTE, title);
        }
    }

    protected String extractText(Node node)
    {
        WikiPrinter printer = new DefaultWikiPrinter();
        pushListener(this.plainRendererFactory.createRenderer(printer));
        getVisitor().visitChildren(node);
        popListener();
        return printer.toString();
    }

    /**
     * @param text the text to parse and for which to return XWiki events
     */
    protected void parseInline(String text)
    {
        try {
            WrappingListener inlineListener = new InlineFilterListener();
            inlineListener.setWrappedListener(getListener());
            this.plainTextStreamParser.parse(new StringReader(text), inlineListener);
        } catch (ParseException e) {
            throw new RuntimeException(String.format("Error parsing content [%s]", text), e);
        }
    }

    /**
     * @param node the node to check
     * @return {@code true} if the passed node is located inside a table cell, {@code false} otherwise
     */
    protected static boolean isInTableCell(Node node)
    {
        return node.getAncestorOfType(TableCell.class) != null;
    }

    /**
     * Same as GitHub Flavored Markdown, an escaped pipe located in a table cell (which doesn't split the cell) stands
     * for a pipe, including inside some syntax. In particular, the separators of the wiki link and wiki image
     * syntaxes located inside some emphasis must be escaped since a pipe ends the emphasis.
     *
     * @param text the content of some syntax located in a table cell
     * @return the content in which the escaped pipes are replaced by pipes (the escaped backslashes being kept)
     */
    protected static String unescapeTableCellPipes(String text)
    {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ESCAPE_CHAR && i + 1 < text.length()) {
                i++;
                char escaped = text.charAt(i);
                if (escaped != '|') {
                    builder.append(c);
                }
                builder.append(escaped);
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * @param text some text
     * @return {@code true} if the passed text ends with a backslash escaping the following character (i.e. it ends with
     *     an odd number of backslashes), {@code false} otherwise
     */
    protected static boolean endsWithEscape(CharSequence text)
    {
        int count = 0;
        for (int i = text.length() - 1; i >= 0 && text.charAt(i) == ESCAPE_CHAR; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    /**
     * Decode a wiki link reference or a wiki image reference or alternative text, in which the characters that would
     * break the wiki link and wiki image syntaxes (e.g. a pipe) are written as HTML entities. The closing brackets
     * escaped with a backslash (as written by older versions) are unescaped too, the other backslashes being kept
     * (e.g. they escape the special characters of the document references).
     *
     * @param text the text to decode
     * @return the decoded text
     */
    protected static String decodeWikiLinkText(String text)
    {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ESCAPE_CHAR && i + 1 < text.length()) {
                // Keep the escaped characters (including the escaped backslashes) except for the closing brackets.
                i++;
                char escaped = text.charAt(i);
                if (escaped != ']') {
                    builder.append(c);
                }
                builder.append(escaped);
            } else {
                builder.append(c);
            }
        }
        // The HTML entities are decoded last so that a decoded backslash doesn't escape the following character.
        return Escaping.unescapeHtml(builder);
    }

    protected void generateHTMLMacro(String html, boolean inline)
    {
        // We output a HTML macro (and not Raw events) in order to benefit from the security features of that Macro.
        // We don't clean the HTML since it's possible to intermix MD syntax with HTML and thus has not well-formed
        // HTML content.
        getListener().onMacro("html", Collections.singletonMap("clean", "false"), html, inline);
    }
}
