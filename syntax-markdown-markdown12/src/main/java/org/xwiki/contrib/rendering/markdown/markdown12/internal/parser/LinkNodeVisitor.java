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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.parser.StreamParser;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.VisitHandler;
import com.vladsch.flexmark.ast.Visitor;
import com.vladsch.flexmark.ext.wikilink.WikiLink;

/**
 * Handle link events.
 *
 * @version $Id$
 * @since 8.4
 */
public class LinkNodeVisitor extends AbstractNodeVisitor
{
    static <V extends LinkNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(AutoLink.class, new Visitor<AutoLink>()
                {
                    @Override
                    public void visit(AutoLink node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(MailLink.class, new Visitor<MailLink>()
                {
                    @Override
                    public void visit(MailLink node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(Link.class, new Visitor<Link>()
                {
                    @Override
                    public void visit(Link node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(LinkRef.class, new Visitor<LinkRef>()
                {
                    @Override
                    public void visit(LinkRef node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(WikiLink.class, new Visitor<WikiLink>()
                {
                    @Override
                    public void visit(WikiLink node)
                    {
                        visitor.visit(node);
                    }
                })
        };
    }

    private ResourceReferenceParser linkResourceReferenceParser;

    public LinkNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners,
        ResourceReferenceParser linkResourceReferenceParser, StreamParser plainTextStreamParser)
    {
        super(visitor, listeners, null, plainTextStreamParser);
        this.linkResourceReferenceParser = linkResourceReferenceParser;
    }

    public void visit(AutoLink node)
    {
        // This is an autolink to a URL. Autolinks to emails are calling visit(MailLink).
        ResourceReference reference = new ResourceReference(String.valueOf(node.getText()), ResourceType.URL);
        reference.setTyped(false);

        getListener().beginLink(reference, true, Collections.EMPTY_MAP);
        getListener().endLink(reference, true, Collections.EMPTY_MAP);
    }

    public void visit(MailLink node)
    {
        // This is an autolink to an email address.
        ResourceReference reference = new ResourceReference(String.valueOf(node.getText()), ResourceType.MAILTO);

        getListener().beginLink(reference, true, Collections.EMPTY_MAP);
        getListener().endLink(reference, true, Collections.EMPTY_MAP);
    }

    public void visit(Link node)
    {
        // This can be a link to a URL or a link to an email address but since links to an email address will need to
        // be prefixed with "mailto:" we can consider them URL links. Also, there's always a label since otherwise it
        // would be an autolink.

        // We consider all links to be URLs.
        ResourceReference reference = new ResourceReference(String.valueOf(node.getUrl()), ResourceType.URL);
        reference.setTyped(false);

        Map<String, String> parameters = new HashMap<>();

        // Handle optional title
        addTitle(parameters, String.valueOf(node.getTitle()));

        getListener().beginLink(reference, false, parameters);
        getVisitor().visitChildren(node);
        getListener().endLink(reference, false, parameters);
    }

    public void visit(LinkRef node)
    {
        if (!node.isDefined()) {
            // Non-existing reference, output the link reference as is
            parseInline(node.getChars().unescape());
        } else {
            // Since XWiki doesn't support reference links, we generate a standard link instead.
            // We consider all reference links to be URL links (ie not wikilinks).
            Reference reference = node.getReferenceNode(getReferenceRepository());
            ResourceReference resourceReference =
                new ResourceReference(String.valueOf(reference.getUrl()), ResourceType.URL);
            resourceReference.setTyped(false);

            // Handle an optional link title
            Map<String, String> parameters = Collections.EMPTY_MAP;
            if (StringUtils.isNotEmpty(reference.getTitle())) {
                parameters = Collections.singletonMap(TITLE_ATTRIBUTE, String.valueOf(reference.getTitle()));
            }

            getListener().beginLink(resourceReference, false, parameters);
            getVisitor().visitChildren(node);
            getListener().endLink(resourceReference, false, parameters);
        }
    }

    public void visit(WikiLink node)
    {
        ResourceReference reference = this.linkResourceReferenceParser.parse(String.valueOf(node.getLink()));
        getListener().beginLink(reference, false, Collections.EMPTY_MAP);
        if (node.getText() != null) {
            String label = String.valueOf(node.getText());
            parseInline(label);
        }
        getListener().endLink(reference, false, Collections.EMPTY_MAP);
    }
}
