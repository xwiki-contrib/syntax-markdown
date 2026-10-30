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
import java.util.regex.Pattern;
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
import com.vladsch.flexmark.util.ast.Document;
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

    /**
     * A line break HTML tag without any attribute (e.g. {@code <br>}, {@code <br/>} or {@code <br />}), in any case.
     */
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);

    /**
     * The document for which {@link #inlineHTMLParser} and {@link #inlineHTMLRenderer} have been created.
     */
    private Document inlineHTMLDocument;

    /**
     * The parser used to parse the Markdown embedded in inline HTML, created once per document.
     */
    private Parser inlineHTMLParser;

    /**
     * The renderer used to render as HTML the Markdown embedded in inline HTML, created once per document.
     */
    private HtmlRenderer inlineHTMLRenderer;

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

    /**
     * @param node the node to check
     * @return {@code true} if the passed node is an inline line break HTML tag without attributes (which is equivalent
     *     to a new line), {@code false} otherwise
     */
    static boolean isLineBreak(Node node)
    {
        return node instanceof HtmlInline && LINE_BREAK_PATTERN.matcher(node.getChars()).matches();
    }

    public void visit(HtmlInline node)
    {
        // A line break HTML tag without attributes is equivalent to a new line, which we generate instead of an HTML
        // macro so that it can be edited and rendered back as Markdown (e.g. the Markdown renderer uses such tag for
        // new lines in table cells since table rows have to be on a single line). Note that a line break tag found
        // inside some HTML element has been merged with it and is thus kept in the generated HTML macro.
        if (isLineBreak(node)) {
            getListener().onNewLine();
            return;
        }

        String html = node.getChars().toString();

        // When we have an inline HTML macro with its raw content, we need to parse it and render it as HTML to
        // support possibly embedded Markdown content.
        initializeInlineHTMLParser(node.getDocument());
        Node parsedNode = this.inlineHTMLParser.parse(html);
        String outputHtml = this.inlineHTMLRenderer.render(parsedNode);

        // Parsing the node on its own will put in a paragraph, so we remove <p></p> tags and linebreak from the output.
        generateHTMLMacro(StringUtils.removeEnd(StringUtils.removeStart(outputHtml, "<p>"), "</p>\n"), true);
    }

    private void initializeInlineHTMLParser(Document document)
    {
        // We use the same options (emulation profile, extensions, reference repository) as the ones used to parse the
        // whole document so that the embedded Markdown is parsed the same way. Since these options depend only on the
        // document, the parser and renderer are created only once per document.
        if (this.inlineHTMLDocument != document) {
            MutableDataSet options = new MutableDataSet(document);
            // We already know we are in an inline context, so we disable block parsing.
            options.set(Parser.HTML_BLOCK_PARSER, false);
            options.set(Parser.EXTENSIONS, getInlineHTMLExtensions(Parser.EXTENSIONS.get(options)));
            this.inlineHTMLParser = Parser.builder(options).build();
            this.inlineHTMLRenderer = HtmlRenderer.builder(options).build();
            this.inlineHTMLDocument = document;
        }
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
