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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeTracker;
import com.vladsch.flexmark.util.sequence.Escaping;

/**
 * Replaces an HTML block containing only a {@code <div>} opening tag that carries the Group marker attribute, its
 * matching HTML block containing only a {@code </div>} closing tag (at the same nesting level), and all the nodes
 * located between them, with a {@link GroupBlock} containing these nodes. Any other div is left as is (i.e. as raw
 * HTML), so that existing content doesn't change meaning.
 *
 * @version $Id$
 * @since 8.9.2
 */
public class GroupPostProcessor extends NodePostProcessor
{
    /**
     * The attribute that marks a div as denoting a Group.
     */
    public static final String GROUP_MARKER = "data-xwiki-group";

    private static final String OPENING_TAG_PREFIX = "<div";

    private static final String TAG_SUFFIX = ">";

    /**
     * A single HTML attribute preceded by white spaces, with groups for its name and for its double-quoted,
     * single-quoted or unquoted value. Attributes are matched one by one (and not with a repeated group) to avoid a
     * possible stack overflow on large inputs.
     */
    private static final Pattern ATTRIBUTE =
        Pattern.compile("\\s+([^\\s\"'>/=]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");

    private static final Pattern CLOSING_TAG = Pattern.compile("</div\\s*>", Pattern.CASE_INSENSITIVE);

    /**
     * Factory class for {@link GroupPostProcessor}.
     */
    public static class Factory extends NodePostProcessorFactory
    {
        /**
         * Default constructor.
         */
        public Factory()
        {
            super(false);
            addNodes(HtmlBlock.class);
        }

        @NotNull
        @Override
        public NodePostProcessor apply(@NotNull Document document)
        {
            return new GroupPostProcessor();
        }
    }

    @Override
    public void process(@NotNull NodeTracker state, @NotNull Node node)
    {
        // A node already removed as the closing tag of another Group has no parent anymore.
        if (node.getParent() == null) {
            return;
        }

        Map<String, String> attributes = parseOpeningTag(node);
        if (attributes == null || !attributes.containsKey(GROUP_MARKER)) {
            return;
        }

        Node closingNode = findClosingNode(node);
        if (closingNode == null) {
            return;
        }

        attributes.remove(GROUP_MARKER);
        GroupBlock group = new GroupBlock(attributes);
        Node current = node.getNext();
        while (current != closingNode) {
            Node next = current.getNext();
            current.unlink();
            group.appendChild(current);
            current = next;
        }
        node.insertBefore(group);
        node.unlink();
        state.nodeRemoved(node);
        closingNode.unlink();
        state.nodeRemoved(closingNode);
        state.nodeAdded(group);
    }

    /**
     * @param node the node to check
     * @return the attributes of the div if the node is an HTML block made only of a div opening tag, {@code null}
     *     otherwise
     */
    private static Map<String, String> parseOpeningTag(Node node)
    {
        Map<String, String> attributes = null;
        if (node instanceof HtmlBlock) {
            String html = node.getChars().toString().trim();
            if (StringUtils.startsWithIgnoreCase(html, OPENING_TAG_PREFIX) && html.endsWith(TAG_SUFFIX)) {
                attributes = parseAttributes(
                    StringUtils.stripEnd(html.substring(OPENING_TAG_PREFIX.length(), html.length() - 1), null));
            }
        }
        return attributes;
    }

    private static boolean isClosingTag(Node node)
    {
        return node instanceof HtmlBlock && CLOSING_TAG.matcher(node.getChars().toString().trim()).matches();
    }

    private static Node findClosingNode(Node openingNode)
    {
        // Count the nested div opening tags to find the matching closing tag.
        int depth = 1;
        Node current = openingNode.getNext();
        while (current != null) {
            if (parseOpeningTag(current) != null) {
                depth++;
            } else if (isClosingTag(current)) {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
            current = current.getNext();
        }
        return current;
    }

    /**
     * @param attributesText the text located between the tag name and the end of the tag
     * @return the attributes, or {@code null} if the text is not a list of attributes (e.g. {@code "x"} for
     *     {@code <divx>})
     */
    private static Map<String, String> parseAttributes(String attributesText)
    {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(attributesText);
        int position = 0;
        // Each attribute must start where the previous one ended, and the attributes must cover the whole text.
        while (position < attributesText.length() && matcher.find(position) && matcher.start() == position) {
            attributes.put(matcher.group(1), getAttributeValue(matcher));
            position = matcher.end();
        }
        return position == attributesText.length() ? attributes : null;
    }

    private static String getAttributeValue(Matcher matcher)
    {
        String value = "";
        for (int i = 2; i <= 4; i++) {
            if (matcher.group(i) != null) {
                value = Escaping.unescapeHtml(matcher.group(i));
                break;
            }
        }
        return value;
    }
}
