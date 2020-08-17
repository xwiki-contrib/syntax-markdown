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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.wikimodel.WikiParameter;
import org.xwiki.rendering.wikimodel.impl.WikiScannerUtil;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ext.wikilink.WikiLink;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

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
                new VisitHandler<>(AutoLink.class, node -> visitor.visit(node)),
                new VisitHandler<>(MailLink.class, node -> visitor.visit(node)),
                new VisitHandler<>(Link.class, node -> visitor.visit(node)),
                new VisitHandler<>(LinkRef.class, node -> visitor.visit(node)),
                new VisitHandler<>(WikiLink.class, node -> visitor.visit(node))
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
        ResourceReference reference = new ResourceReference(node.getText().unescape(), ResourceType.URL);
        reference.setTyped(false);

        getListener().beginLink(reference, true, Collections.emptyMap());
        getListener().endLink(reference, true, Collections.emptyMap());
    }

    public void visit(MailLink node)
    {
        // This is an autolink to an email address.
        ResourceReference reference = new ResourceReference(node.getText().unescape(), ResourceType.MAILTO);

        getListener().beginLink(reference, true, Collections.emptyMap());
        getListener().endLink(reference, true, Collections.emptyMap());
    }

    public void visit(Link node)
    {
        // This can be a link to a URL or a link to an email address but since links to an email address will need to
        // be prefixed with "mailto:" we can consider them URL links. Also, there's always a label since otherwise it
        // would be an autolink.

        // We consider all links to be URLs.
        ResourceReference reference = new ResourceReference(node.getUrl().unescape(), ResourceType.URL);
        reference.setTyped(false);

        Map<String, String> parameters = new HashMap<>();

        // Handle optional title
        addTitle(parameters, node.getTitle().toString());

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
                new ResourceReference(reference.getUrl().toString(), ResourceType.URL);
            resourceReference.setTyped(false);

            // Handle an optional link title
            Map<String, String> parameters = Collections.emptyMap();
            if (StringUtils.isNotEmpty(reference.getTitle())) {
                parameters = Collections.singletonMap(TITLE_ATTRIBUTE, reference.getTitle().toString());
            }

            getListener().beginLink(resourceReference, false, parameters);
            getVisitor().visitChildren(node);
            getListener().endLink(resourceReference, false, parameters);
        }
    }

    public void visit(WikiLink node)
    {
        // Parse any parameters specified using the format "label|reference|a=b c=d".
        // Important: We don't unescape() the link content since we need to support escape characters in wiki link
        // references (e.g. a reference with dots, etc).
        String nodeRawReference = node.getLink().toString();
        String nodeReference = nodeRawReference;
        String queryString = null;
        String anchor = null;
        int pos = nodeRawReference.indexOf('|');
        if (pos > -1) {
            nodeReference = nodeRawReference.substring(0, pos);
            if (pos < nodeRawReference.length()) {
                String parameters = nodeRawReference.substring(pos + 1);
                List<WikiParameter> parameterList = new ArrayList<>();
                WikiScannerUtil.splitToPairs(parameters, parameterList, null, null, '\\');
                for (WikiParameter wikiParameter : parameterList) {
                    if (wikiParameter.getKey().equals("queryString")) {
                        queryString = wikiParameter.getValue();
                    } else if (wikiParameter.getKey().equals("anchor")) {
                        anchor = wikiParameter.getValue();
                    }
                }
            }
        }

        ResourceReference reference = this.linkResourceReferenceParser.parse(nodeReference);

        if (reference instanceof DocumentResourceReference) {
            DocumentResourceReference documentResourceReference = (DocumentResourceReference) reference;
            if (queryString != null) {
                documentResourceReference.setQueryString(queryString);
            }
            if (anchor != null) {
                documentResourceReference.setAnchor(anchor);
            }
        }

        getListener().beginLink(reference, false, Collections.emptyMap());
        if (node.getText() != null) {
            String label = node.getText().unescape();
            parseInline(label);
        }
        getListener().endLink(reference, false, Collections.emptyMap());
    }
}
