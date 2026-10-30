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

import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlCommentBlock;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.HtmlInlineComment;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.xwiki.macros.MacroExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

/**
 * Handle HTML events.
 *
 * @version $Id$
 * @since 8.4
 */
public class HTMLNodeVisitor extends AbstractNodeVisitor
{
    /**
     * Extensions not used when parsing the Markdown content embedded in some inline HTML: the XWiki specific ones
     * (wiki links, macros, groups) that cannot be rendered as HTML, the block level ones, and the one merging inline
     * HTML nodes (since the merged content would then be rendered as is, ignoring the embedded Markdown).
     */
    private static final Set<Class<?>> EXCLUDED_INLINE_HTML_EXTENSIONS = new HashSet<>(Arrays.asList(
        DeepInlineHTMLExtension.class,
        WikiLinkExtension.class,
        MacroExtension.class,
        GroupExtension.class,
        TablesExtension.class,
        DefinitionExtension.class
    ));

    static <V extends HTMLNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(HtmlInline.class, node -> visitor.visit(node)),
                new VisitHandler<>(HtmlBlock.class, node -> visitor.visit(node)),
                new VisitHandler<>(HtmlCommentBlock.class, node -> visitor.visit(node)),
                new VisitHandler<>(HtmlEntity.class, node -> visitor.visit(node)),
                new VisitHandler<>(HtmlInlineComment.class, node -> visitor.visit(node))
        };
    }

    public HTMLNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public void visit(HtmlInline node)
    {
        // When we have an inline HTML macro with its raw content, we need to parse it and render it as HTML to
        // support possibly embedded Markdown content. We use the same options (emulation profile, extensions) as the
        // ones used to parse the whole document so that the embedded Markdown is parsed the same way.
        MutableDataSet options = new MutableDataSet(node.getDocument());
        // We already know we are in an inline context, so we disable block parsing.
        options.set(Parser.HTML_BLOCK_PARSER, false);
        options.set(Parser.EXTENSIONS, getInlineHTMLExtensions(Parser.EXTENSIONS.get(options)));
        Parser parser = Parser.builder(options).build();
        Node parsedNode = parser.parse(node.getChars().toString());

        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        String outputHtml = renderer.render(parsedNode);

        // Parsing the node on its own will put in a paragraph, so we remove <p></p> tags and linebreak from the output.
        generateHTMLMacro(StringUtils.removeEnd(StringUtils.removeStart(outputHtml, "<p>"), "</p>\n"), true);
    }

    private List<Extension> getInlineHTMLExtensions(Collection<Extension> extensions)
    {
        // Keep only the extensions for which the HTML rendering makes sense inside some inline HTML.
        return extensions.stream()
            .filter(extension -> !EXCLUDED_INLINE_HTML_EXTENSIONS.contains(extension.getClass()))
            .collect(Collectors.toList());
    }

    public void visit(HtmlBlock node)
    {
        // Flexmark puts trailing newline in the HTML block so we need to remove it.
        String html = node.getChars().toString().trim();
        generateHTMLMacro(html, false);
    }

    public void visit(HtmlCommentBlock node)
    {
        visit((Node) node);
    }

    public void visit(HtmlEntity node)
    {
        visit((Node) node);
    }

    public void visit(HtmlInlineComment node)
    {
        visit((Node) node);
    }

    private void visit(Node node)
    {
        generateHTMLMacro(node.getChars().toString(), true);
    }
}
