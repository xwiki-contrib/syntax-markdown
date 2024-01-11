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
 * In a situation where a paragraph contains an open tag without any corresponding closing tag, the rest of the
 * paragraph content will be treated as a single HtmlInline node.
 *
 * @version $Id$
 * @since 8.9
 */
public class DeepInlineHTMLPostProcessor extends NodePostProcessor
{
    private static final String HTML_OPEN_PREFIX = "<";
    private static final String HTML_CLOSE_PREFIX = "</";
    private static final String HTML_SELF_SUFFIX = "/>";
    private static final String HTML_REGULAR_SUFFIX = ">";

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
        // We store the number of nested open tags found during the processing and match it to the number of closing
        // tags.
        int toFind = 1;
        int nFound = 0;

        Node nextNode = node.getNext();
        String inlineStartText = node.getChars().toString();

        // We return early if we found a single closing tag, or a self-closing one.
        if (inlineStartText.startsWith(HTML_CLOSE_PREFIX) || inlineStartText.endsWith(HTML_SELF_SUFFIX)) {
            return;
        }

        // We keep the name of the tag until the first whitespace character and remove the angle brackets.
        String inlineStartTag = inlineStartText.split("\\s+|" + HTML_REGULAR_SUFFIX)[0].substring(1);

        while (nextNode != null && nFound < toFind) {
            Node currentNode = nextNode;
            nextNode = nextNode.getNext();

            // We store the content of the processed nodes in the first HtmlInline node.
            node.setChars(node.getChars().append(currentNode.getChars()));

            if (currentNode instanceof HtmlInline) {
                // We keep the name of the tag until the first whitespace character or closing angle bracket.
                String currentNodeTag = currentNode.getChars().toString().split("\\s+|" + HTML_REGULAR_SUFFIX)[0];
                if (currentNodeTag.equals(HTML_OPEN_PREFIX + inlineStartTag)) {
                    toFind++;
                } else if (currentNodeTag.equals(HTML_CLOSE_PREFIX + inlineStartTag)) {
                    nFound++;
                }
            }

            // We get rid of now redundant nodes.
            currentNode.unlink();
            nodeTracker.nodeRemoved(currentNode);
        }
    }
}
