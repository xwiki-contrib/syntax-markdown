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
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.WikiLinkReferenceParameters;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.InterWikiResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.wikimodel.WikiParameter;
import org.xwiki.rendering.wikimodel.impl.WikiScannerUtil;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.HtmlInlineComment;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ext.wikilink.WikiLink;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.xwiki.macros.Macro;
import com.vladsch.flexmark.ext.xwiki.macros.MacroClose;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.sequence.BasedSequence;

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

    /**
     * The nodes of the wiki link labels that are kept as text (see convertLabelToText()).
     */
    private static final List<Class<? extends Node>> LABEL_TEXT_CLASSES = Arrays.asList(HtmlEntity.class,
        HtmlInline.class, HtmlInlineComment.class, Macro.class, MacroClose.class, AutoLink.class, MailLink.class,
        Link.class, LinkRef.class, WikiLink.class);

    private static final String MAILTO_PREFIX = ResourceType.MAILTO.getScheme() + ':';

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
        ResourceReference reference = createURIReference(node.getText().unescape());

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
        // This can be a link to a URL or a link to an email address (prefixed with "mailto:"). Also, there's always a
        // label since otherwise it would be an autolink.
        ResourceReference reference = createURIReference(node.getUrl().unescape());

        Map<String, String> parameters = new HashMap<>();

        // Handle optional title
        addTitle(parameters, node.getTitle().unescape());

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
            // We consider all reference links to be URL or mailto links (ie not wikilinks).
            Reference reference = node.getReferenceNode(getReferenceRepository());
            ResourceReference resourceReference = createURIReference(reference.getUrl().toString());

            // Handle an optional link title
            Map<String, String> parameters = Collections.emptyMap();
            if (StringUtils.isNotEmpty(reference.getTitle())) {
                parameters = Collections.singletonMap(TITLE_ATTRIBUTE, reference.getTitle().unescape());
            }

            getListener().beginLink(resourceReference, false, parameters);
            getVisitor().visitChildren(node);
            getListener().endLink(resourceReference, false, parameters);
        }
    }

    /**
     * @param uri the destination of a Markdown link
     * @return a reference to the email address when the URI uses the mailto scheme (same as the XWiki Syntax), a
     *     reference to the URL otherwise
     */
    private ResourceReference createURIReference(String uri)
    {
        ResourceReference reference;
        if (StringUtils.startsWithIgnoreCase(uri, MAILTO_PREFIX)) {
            reference = new ResourceReference(uri.substring(MAILTO_PREFIX.length()), ResourceType.MAILTO);
        } else {
            reference = new ResourceReference(uri, ResourceType.URL);
            reference.setTyped(false);
        }
        return reference;
    }

    /**
     * Same as the XWiki Syntax: the anchor and the query string are parameters of the document references, the query
     * string is a parameter of the attachment references, the other parameters are parameters of the link. The
     * interwiki alias parameter, which is part of the interwiki reference syntax, is also accepted as a parameter.
     *
     * @param type the type of the link reference
     * @param key the name of a parameter written in the wiki link syntax
     * @return {@code true} if the parameter is a parameter of the link reference, {@code false} if it's a parameter of
     *     the link
     */
    private static boolean isReferenceParameter(ResourceType type, String key)
    {
        return WikiLinkReferenceParameters.isReferenceParameter(type, key)
            || (ResourceType.INTERWIKI.equals(type) && InterWikiResourceReference.INTERWIKI_ALIAS.equals(key));
    }

    public void visit(WikiLink node)
    {
        // Parse any parameters specified using the format "label|reference|a=b c=d".
        // Important: We don't unescape() the link content since we need to support escape characters in wiki link
        // references (e.g. a reference with dots, etc).
        boolean isInTableCell = isInTableCell(node);
        String nodeRawReference = getRawLink(node);
        if (isInTableCell) {
            nodeRawReference = unescapeTableCellPipes(nodeRawReference);
        }
        String nodeReference = nodeRawReference;
        List<WikiParameter> parameterList = new ArrayList<>();
        int pos = nodeRawReference.indexOf('|');
        if (pos > -1) {
            nodeReference = nodeRawReference.substring(0, pos);
            WikiScannerUtil.splitToPairs(nodeRawReference.substring(pos + 1), parameterList, null, null, '\\');
        }

        ResourceReference reference = this.linkResourceReferenceParser.parse(decodeWikiLinkText(nodeReference));

        Map<String, String> parameters = new LinkedHashMap<>();
        for (WikiParameter wikiParameter : parameterList) {
            String key = wikiParameter.getKey();
            if (isReferenceParameter(reference.getType(), key)) {
                reference.setParameter(key, wikiParameter.getValue());
            } else {
                parameters.put(key, wikiParameter.getValue());
            }
        }

        getListener().beginLink(reference, false, parameters);
        BasedSequence label = node.getText();
        if (label.isNotNull()) {
            // In a table cell, a backslash located before the label separator escapes it (see
            // unescapeTableCellPipes()).
            boolean isSeparatorEscaped = isInTableCell && endsWithEscape(label);
            if (WikiLinkExtension.ALLOW_INLINES.get(node.getDocument())) {
                // The label has been parsed as inline Markdown content.
                convertLabelToText(node);
                if (isSeparatorEscaped) {
                    removeLabelEnd(node);
                }
                getVisitor().visitChildren(node);
            } else {
                if (isSeparatorEscaped) {
                    label = label.subSequence(0, label.length() - 1);
                }
                parseInline(label.unescape());
            }
        }
        getListener().endLink(reference, false, parameters);
    }

    /**
     * @param node the wiki link
     * @return the reference part of the wiki link syntax, as it's written (when there's no label and the labels are
     *     parsed as inline Markdown content, the link of the node is the text of the parsed content, e.g. without
     *     the escapes and the macros)
     */
    private String getRawLink(WikiLink node)
    {
        if (node.getText().isNull()) {
            BasedSequence chars = node.getChars();
            return chars.subSequence(node.getOpeningMarker().length(),
                chars.length() - node.getClosingMarker().length()).toString();
        }
        return node.getLink().toString();
    }

    /**
     * Remove the last character of the label, from the parsed content of the label.
     */
    private void removeLabelEnd(WikiLink node)
    {
        int end = node.getText().getEndOffset();
        for (Node descendant : node.getDescendants()) {
            if (descendant instanceof Text && descendant.getEndOffset() == end) {
                BasedSequence chars = descendant.getChars();
                descendant.setChars(chars.subSequence(0, chars.length() - 1));
            }
        }
    }

    /**
     * Convert into text the content of the wiki link label that is kept as text: the HTML entities, so that they are
     * decoded (e.g. the pipes and the new lines of the wiki link labels are written as HTML entities, since a pipe
     * ends the label and a blank line ends the paragraph), and the macros, the HTML and the links, which were not
     * parsed in the wiki link labels written by older versions (the formatting and the images are kept).
     *
     * @param node the node whose descendants to convert
     */
    private void convertLabelToText(Node node)
    {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            if (isTextInLabel(child)) {
                // The text node visitor unescapes the text, which decodes the HTML entities.
                child.insertBefore(new Text(child.getChars()));
                child.unlink();
            } else {
                convertLabelToText(child);
            }
            child = next;
        }
    }

    private boolean isTextInLabel(Node node)
    {
        for (Class<? extends Node> textClass : LABEL_TEXT_CLASSES) {
            if (textClass.isInstance(node)) {
                return true;
            }
        }
        return false;
    }
}
