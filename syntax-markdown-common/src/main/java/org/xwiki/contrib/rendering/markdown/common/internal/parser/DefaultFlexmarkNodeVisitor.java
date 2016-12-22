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
package org.xwiki.contrib.rendering.markdown.common.internal.parser;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.listener.InlineFilterListener;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.BulletListItem;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlCommentBlock;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.HtmlInlineComment;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.OrderedListItem;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ast.VisitHandler;
import com.vladsch.flexmark.ext.definition.DefinitionItem;
import com.vladsch.flexmark.ext.definition.DefinitionList;
import com.vladsch.flexmark.ext.definition.DefinitionTerm;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCaption;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.ext.wikilink.WikiLink;
import com.vladsch.flexmark.parser.Parser;

@Component
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DefaultFlexmarkNodeVisitor implements FlexmarkNodeVisitor
{
    /**
     * A plain text parser used to convert Flexmark's Text node elements to various Block elements since Text node's
     * values contain several words and special characters which we thus need to break down into individual tokens.
     */
    @Inject
    @Named("plain/1.0")
    private StreamParser plainTextStreamParser;

    /**
     * We parse image references with the default reference parser (i.e. the same one used by XWiki Syntax 2.1).
     */
    @Inject
    @Named("image")
    private ResourceReferenceParser imageResourceReferenceParser;

    /**
     * We parse link references with the default reference parser (i.e. the same one used by XWiki Syntax 2.1).
     */
    @Inject
    @Named("link")
    private ResourceReferenceParser linkResourceReferenceParser;

    /**
     * Used to extract text from nodes.
     */
    @Inject
    @Named("plain/1.0")
    protected PrintRendererFactory plainRendererFactory;

    /**
     * Used to find out at runtime a link label generator matching the link reference type.
     */
    @Inject
    private ComponentManager componentManager;

    /**
     * Listener(s) for the generated XWiki Events. Organized as a stack so that a buffering listener can hijack all
     * events for a while, for example. All generated events are sent to the top of the stack.
     */
    private Deque<Listener> listeners = new ArrayDeque<>();

    private NodeVisitor visitor;

    private ImageNodeVisitor imageNodeVisitor;

    private LinkNodeVisitor linkNodeVisitor;

    public void visit(Node node, Listener listener, Syntax syntax)
    {
        SectionListener sectionListener = new SectionListener();
        sectionListener.setWrappedListener(listener);
        this.listeners.push(sectionListener);

        MetaData metaData = new MetaData(Collections.singletonMap(MetaData.SYNTAX, syntax));
        getListener().beginDocument(metaData);

        this.visitor = new NodeVisitor(new VisitHandler<>(Document.class, this::visit));

        // Handle formatting nodes
        FormatNodeVisitor formatNodeVisitor = new FormatNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(
            new VisitHandler<>(Emphasis.class, formatNodeVisitor::visit),
            new VisitHandler<>(StrongEmphasis.class, formatNodeVisitor::visit)
        );

        // Handle Paragraph nodes
        ParagraphNodeVisitor paragraphNodeVisitor = new ParagraphNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(
            new VisitHandler<>(Paragraph.class, paragraphNodeVisitor::visit)
        );

        // Handle Image nodes
        this.imageNodeVisitor = new ImageNodeVisitor(this.visitor, this.listeners, this.imageResourceReferenceParser,
            this.componentManager, this.plainRendererFactory);
        this.visitor.addHandlers(
            new VisitHandler<>(Image.class, this.imageNodeVisitor::visit),
            new VisitHandler<>(ImageRef.class, this.imageNodeVisitor::visit)
        );

        // Handle Link nodes
        this.linkNodeVisitor = new LinkNodeVisitor(this.visitor, this.listeners, this.linkResourceReferenceParser);
        this.visitor.addHandlers(
            new VisitHandler<>(Link.class, this.linkNodeVisitor::visit),
            new VisitHandler<>(LinkRef.class, this.linkNodeVisitor::visit),
            new VisitHandler<>(AutoLink.class, this.linkNodeVisitor::visit),
            new VisitHandler<>(MailLink.class, this.linkNodeVisitor::visit),
            new VisitHandler<>(WikiLink.class, this.linkNodeVisitor::visit)
        );

        // Handle list nodes
        ListNodeVisitor listNodeVisitor = new ListNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(
            new VisitHandler<>(BulletList.class, listNodeVisitor::visit),
            new VisitHandler<>(BulletListItem.class, listNodeVisitor::visit),
            new VisitHandler<>(OrderedList.class, listNodeVisitor::visit),
            new VisitHandler<>(OrderedListItem.class, listNodeVisitor::visit),
            new VisitHandler<>(DefinitionList.class, listNodeVisitor::visit),
            new VisitHandler<>(DefinitionItem.class, listNodeVisitor::visit),
            new VisitHandler<>(DefinitionTerm.class, listNodeVisitor::visit)
        );

        // Handle quote nodes
        QuoteNodeVisitor quoteNodeVisitor = new QuoteNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(
            new VisitHandler<>(BlockQuote.class, quoteNodeVisitor::visit)
        );

        // Handle Heading nodes
        HeadingNodeVisitor headingNodeVisitor = new HeadingNodeVisitor(this.visitor, this.listeners,
            this.plainRendererFactory);
        this.visitor.addHandlers(
            new VisitHandler<>(Heading.class, headingNodeVisitor::visit)
        );

        // Handle Table nodes
        TableNodeVisitor tableNodeVisitor = new TableNodeVisitor(this.visitor, this.listeners,
            this.plainRendererFactory);
        this.visitor.addHandlers(
            new VisitHandler<>(TableBlock.class, tableNodeVisitor::visit),
            new VisitHandler<>(TableHead.class, tableNodeVisitor::visit),
            new VisitHandler<>(TableRow.class, tableNodeVisitor::visit),
            new VisitHandler<>(TableCell.class, tableNodeVisitor::visit),
            new VisitHandler<>(TableCaption.class, tableNodeVisitor::visit),
            new VisitHandler<>(TableSeparator.class, tableNodeVisitor::visit)
        );

        // Handle other node types
        this.visitor.addHandlers(
            new VisitHandler<>(Document.class, this::visit),
            new VisitHandler<>(FencedCodeBlock.class, this::visit),
            new VisitHandler<>(HtmlBlock.class, this::visit),
            new VisitHandler<>(HtmlCommentBlock.class, this::visit),
            new VisitHandler<>(IndentedCodeBlock.class, this::visit),
            new VisitHandler<>(ThematicBreak.class, this::visit),
            new VisitHandler<>(Code.class, this::visit),
            new VisitHandler<>(HardLineBreak.class, this::visit),
            new VisitHandler<>(HtmlEntity.class, this::visit),
            new VisitHandler<>(HtmlInline.class, this::visit),
            new VisitHandler<>(HtmlInlineComment.class, this::visit),
            new VisitHandler<>(SoftLineBreak.class, this::visit),
            new VisitHandler<>(Text.class, this::visit)
        );

        this.visitor.visit(node);
        getListener().endDocument(metaData);
    }

    public void visit(Text node)
    {
        String content = node.getChars().toString();
        try {
            WrappingListener inlineListener = new InlineFilterListener();
            inlineListener.setWrappedListener(getListener());
            this.plainTextStreamParser.parse(new StringReader(content), inlineListener);
        } catch (ParseException e) {
            throw new RuntimeException(String.format("Error parsing content [%s]", content), e);
        }

        // Descend into children (could be omitted in this case because Text nodes don't have children).
        this.visitor.visitChildren(node);
    }

    public void visit(HtmlEntity node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(SoftLineBreak node)
    {
        getListener().onNewLine();
    }

    public void visit(HardLineBreak node)
    {
        getListener().onNewLine();
    }

    public void visit(BlockQuote node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(Document node)
    {
        this.imageNodeVisitor.setReferenceRepository(node.get(Parser.REFERENCES));
        this.linkNodeVisitor.setReferenceRepository(node.get(Parser.REFERENCES));
        this.visitor.visitChildren(node);
    }

    public void visit(FencedCodeBlock node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(Heading node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(HtmlBlock node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(HtmlCommentBlock node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(IndentedCodeBlock node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(ThematicBreak node)
    {
        getListener().onHorizontalLine(Collections.EMPTY_MAP);
    }

    public void visit(Code node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(HtmlInline node)
    {
        this.visitor.visitChildren(node);
    }

    public void visit(HtmlInlineComment node)
    {
        this.visitor.visitChildren(node);
    }

    /**
     * @return the top listener on the stack
     */
    private Listener getListener()
    {
        return this.listeners.peek();
    }
}
