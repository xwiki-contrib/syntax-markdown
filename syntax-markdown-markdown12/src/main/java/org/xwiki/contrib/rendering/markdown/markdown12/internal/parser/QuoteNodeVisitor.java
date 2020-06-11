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

import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

/**
 * Handle quote events.
 *
 * @version $Id$
 * @since 8.4
 */
public class QuoteNodeVisitor extends AbstractNodeVisitor
{
    static <V extends QuoteNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(BlockQuote.class, node -> visitor.visit(node))
        };
    }

    public QuoteNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public void visit(BlockQuote node)
    {
        getListener().beginQuotation(Collections.emptyMap());

        // XWiki only supports paragraph in quotations, see http://jira.xwiki.org/browse/XRENDERING-259.
        // We replace Paragraph events with QuotationLine events.
        QuoteListener quoteListener = new QuoteListener();
        quoteListener.setWrappedListener(getListener());
        pushListener(quoteListener);
        getVisitor().visitChildren(node);
        popListener();
        quoteListener.closeOpenedQuotationLines();

        getListener().endQuotation(Collections.emptyMap());
    }
}
