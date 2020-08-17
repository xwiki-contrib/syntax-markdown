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
import java.util.Deque;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.InlineFilterListener;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.vladsch.flexmark.ast.util.ReferenceRepository;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;

public abstract class AbstractNodeVisitor
{
    /**
     * HTML title attribute.
     */
    protected static final String TITLE_ATTRIBUTE = "title";

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
}
