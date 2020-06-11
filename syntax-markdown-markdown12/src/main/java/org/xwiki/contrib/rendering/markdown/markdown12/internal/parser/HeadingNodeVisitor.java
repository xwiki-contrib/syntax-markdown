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

import java.util.Collections;
import java.util.Deque;

import org.xwiki.rendering.listener.CompositeListener;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.QueueListener;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.util.IdGenerator;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

/**
 * Handle heading events.
 *
 * @version $Id$
 * @since 8.4
 */
public class HeadingNodeVisitor extends AbstractNodeVisitor
{
    static <V extends HeadingNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(Heading.class, node -> visitor.visit(node))
        };
    }

    /**
     * Used to generate a unique id for Headings.
     */
    private IdGenerator idGenerator = new IdGenerator();

    private PrintRendererFactory plainRendererFactory;

    public HeadingNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners, PrintRendererFactory plainRendererFactory)
    {
        super(visitor, listeners);
        this.plainRendererFactory = plainRendererFactory;
    }

    public void visit(Heading node)
    {
        // Heading needs to have an id generated from a plaintext representation of its content, so the header start
        // event will be sent at the end of the header, after reading the content inside and generating the id.
        // For this:
        // buffer all events in a queue until the header ends, and also send them to a print renderer to generate the ID
        CompositeListener composite = new CompositeListener();
        QueueListener queueListener = new QueueListener();
        composite.addListener(queueListener);
        PrintRenderer plainRenderer = this.plainRendererFactory.createRenderer(new DefaultWikiPrinter());
        composite.addListener(plainRenderer);

        // These 2 listeners will receive all events from now on until the header ends
        pushListener(composite);

        getVisitor().visitChildren(node);

        // Restore default listener
        popListener();

        String id = this.idGenerator.generateUniqueId("H", plainRenderer.getPrinter().toString());

        HeaderLevel level = HeaderLevel.parseInt(node.getLevel());
        getListener().beginHeader(level, id, Collections.emptyMap());

        // Send all buffered events to the 'default' listener
        queueListener.consumeEvents(getListener());

        getListener().endHeader(level, id, Collections.emptyMap());
    }
}
