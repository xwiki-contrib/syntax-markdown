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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeTracker;
import com.vladsch.flexmark.util.data.DataHolder;

/**
 * Detects and merges nodes that are content of inline HTML open and close tags.
 * Opening tags without any corresponding closing tag in the same paragraph (including void elements such as
 * {@code <br>}) are left untouched so that the rest of the paragraph content is still parsed as Markdown. The content
 * of a {@code <code>} tag without attributes isn't merged either: it's parsed as Markdown and put in a
 * {@link MonospaceNode} instead, since it's read as monospace.
 *
 * @version $Id$
 * @since 8.9
 */
public class DeepInlineHTMLPostProcessor extends NodePostProcessor
{
    private static final String HTML_SELF_SUFFIX = "/>";

    private static final String TAG_NAME = "([a-zA-Z][a-zA-Z0-9-]*)";

    private static final Pattern OPENING_TAG_REGEX = Pattern.compile("<" + TAG_NAME + "[\\s/>]");

    private static final Pattern CLOSING_TAG_REGEX = Pattern.compile("</" + TAG_NAME + "[\\s>]");

    /**
     * A {@code <code>} opening tag without attributes, read as the start of a monospace content.
     */
    private static final Pattern MONOSPACE_OPENING_TAG = Pattern.compile("<code\\s*+>", Pattern.CASE_INSENSITIVE);

    private static final String CODE_TAG_NAME = "code";

    /**
     * HTML elements that cannot have any content and thus never have a closing tag. Includes obsolete void elements.
     *
     * @see <a href="https://html.spec.whatwg.org/multipage/syntax.html#void-elements">Void elements</a>
     */
    private static final Set<String> VOID_ELEMENTS = new HashSet<>(Arrays.asList("area", "base", "basefont",
        "bgsound", "br", "col", "command", "embed", "frame", "hr", "image", "img", "input", "keygen", "link", "meta",
        "param", "source", "track", "wbr"));

    /**
     * The matching closing tag node of each opening tag node, for the parents of the already processed nodes. A post
     * processor instance is created for each parsed document.
     */
    private final Map<Node, Node> closingNodes = new IdentityHashMap<>();

    /**
     * The parents of the already processed nodes, for which the opening and closing tags have been matched.
     */
    private final Set<Node> matchedParents = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Factory class for DeepInlineHTMLPostProcessor.
     */
    public static class Factory extends NodePostProcessorFactory
    {
        /**
         * Factory constructor.
         *
         * @param options parser configuration
         */
        public Factory(DataHolder options)
        {
            super(false);
            addNodes(HtmlInline.class);
        }

        @NotNull
        @Override
        public NodePostProcessor apply(@NotNull Document document)
        {
            return new DeepInlineHTMLPostProcessor();
        }
    }

    @Override
    public void process(@NotNull NodeTracker nodeTracker, @NotNull Node node)
    {
        String inlineStartText = node.getChars().toString();

        // We only merge the content of opening tags that have a matching closing tag (in the same paragraph). Thus we
        // return early for closing tags, self-closing tags, void elements (which never have a closing tag), and for
        // comments, processing instructions, declarations and CDATA sections.
        String inlineStartTag = getOpeningTagName(inlineStartText);
        if (inlineStartTag == null || VOID_ELEMENTS.contains(inlineStartTag)) {
            return;
        }

        // If there's no matching closing tag we don't merge anything, so that the content following the opening tag
        // is parsed as Markdown.
        Node closingNode = getClosingNode(node);
        if (closingNode == null) {
            return;
        }

        if (CODE_TAG_NAME.equals(inlineStartTag) && MONOSPACE_OPENING_TAG.matcher(inlineStartText).matches()) {
            wrapMonospaceContent(nodeTracker, node, closingNode);
            return;
        }

        Node nextNode = node.getNext();
        Node currentNode;
        do {
            currentNode = nextNode;
            nextNode = nextNode.getNext();

            // We store the content of the processed nodes in the first HtmlInline node.
            node.setChars(node.getChars().append(currentNode.getChars()));

            // We get rid of now redundant nodes.
            currentNode.unlink();
            nodeTracker.nodeRemoved(currentNode);
        } while (currentNode != closingNode);
    }

    /**
     * Replace the {@code <code>} and {@code </code>} tags by a {@link MonospaceNode} holding the nodes located between
     * them, which are thus still parsed as Markdown.
     */
    private void wrapMonospaceContent(NodeTracker nodeTracker, Node openingNode, Node closingNode)
    {
        MonospaceNode monospace = new MonospaceNode();
        Node child = openingNode.getNext();
        while (child != closingNode) {
            Node next = child.getNext();
            // The moved nodes are still processed (e.g. the HTML tags located inside the monospace content).
            monospace.appendChild(child);
            child = next;
        }
        openingNode.insertBefore(monospace);
        openingNode.unlink();
        nodeTracker.nodeRemoved(openingNode);
        closingNode.unlink();
        nodeTracker.nodeRemoved(closingNode);
    }

    /**
     * @param node the HtmlInline node holding the opening tag
     * @return the sibling HtmlInline node holding the matching closing tag or {@code null} if there's none
     */
    private Node getClosingNode(Node node)
    {
        // We match all the opening and closing tags of a parent at once (when processing its first opening tag) to
        // avoid looking ahead in the siblings for each opening tag, which would be quadratic in the number of tags.
        Node parent = node.getParent();
        if (this.matchedParents.add(parent)) {
            matchTags(parent);
        }
        Node closingNode = this.closingNodes.get(node);
        // The tags matched before some of them were moved in a monospace content (e.g. an opening tag located in the
        // monospace content and its closing tag located after it) are not siblings anymore.
        return closingNode != null && closingNode.getParent() == parent ? closingNode : null;
    }

    /**
     * Match the opening and closing tags found in the children of the passed node. An opening tag is matched with the
     * first following closing tag with the same name that is not matching a nested opening tag with the same name.
     *
     * @param parent the node whose children to match
     */
    private void matchTags(Node parent)
    {
        // The opening tags waiting for their closing tag, by tag name.
        Map<String, Deque<Node>> openingNodes = new HashMap<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof HtmlInline) {
                String text = child.getChars().toString();
                String openingTagName = getOpeningTagName(text);
                if (openingTagName != null) {
                    openingNodes.computeIfAbsent(openingTagName, key -> new ArrayDeque<>()).push(child);
                } else {
                    Deque<Node> candidates = openingNodes.get(getClosingTagName(text));
                    if (candidates != null && !candidates.isEmpty()) {
                        this.closingNodes.put(candidates.pop(), child);
                    }
                }
            }
        }
    }

    /**
     * @param text the inline HTML
     * @return the lower case name of the tag if the passed inline HTML is an opening tag that is not self-closing,
     *     {@code null} otherwise
     */
    private static String getOpeningTagName(String text)
    {
        if (text.endsWith(HTML_SELF_SUFFIX)) {
            return null;
        }
        return getTagName(OPENING_TAG_REGEX, text);
    }

    /**
     * @param text the inline HTML
     * @return the lower case name of the tag if the passed inline HTML is a closing tag, {@code null} otherwise
     */
    private static String getClosingTagName(String text)
    {
        return getTagName(CLOSING_TAG_REGEX, text);
    }

    private static String getTagName(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        // HTML tag names are case-insensitive.
        return matcher.lookingAt() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }
}
