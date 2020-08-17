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
import java.util.Collections;
import java.util.Deque;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

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
     * Handle Document nodes.
     */
    static <V extends DefaultFlexmarkNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(Document.class, node -> visitor.visit(node)),
                new VisitHandler<>(ThematicBreak.class, node -> visitor.visit(node)),
                new VisitHandler<>(HardLineBreak.class, node -> visitor.visit(node)),
                new VisitHandler<>(SoftLineBreak.class, node -> visitor.visit(node))
        };
    }

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

        // Handle nodes not handled by a specific visitor
        this.visitor = new NodeVisitor(VISIT_HANDLERS(this));

        // Handle Text nodes
        TextNodeVisitor textNodeVisitor = new TextNodeVisitor(this.visitor, this.listeners, this.plainTextStreamParser);
        this.visitor.addHandlers(TextNodeVisitor.VISIT_HANDLERS(textNodeVisitor));

        // Handle Emphasis nodes
        EmphasisNodeVisitor emphasisNodeVisitor = new EmphasisNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(EmphasisNodeVisitor.VISIT_HANDLERS(emphasisNodeVisitor));

        // Handle Paragraph nodes
        ParagraphNodeVisitor paragraphNodeVisitor = new ParagraphNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(ParagraphNodeVisitor.VISIT_HANDLERS(paragraphNodeVisitor));

        // Handle Image nodes
        this.imageNodeVisitor = new ImageNodeVisitor(this.visitor, this.listeners, this.imageResourceReferenceParser,
            this.componentManager, this.plainRendererFactory);
        this.visitor.addHandlers(ImageNodeVisitor.VISIT_HANDLERS(this.imageNodeVisitor));

        // Handle Link nodes
        this.linkNodeVisitor = new LinkNodeVisitor(this.visitor, this.listeners, this.linkResourceReferenceParser,
            this.plainTextStreamParser);
        this.visitor.addHandlers(LinkNodeVisitor.VISIT_HANDLERS(this.linkNodeVisitor));

        // Handle list nodes
        ListNodeVisitor listNodeVisitor = new ListNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(ListNodeVisitor.VISIT_HANDLERS(listNodeVisitor));

        // Handle quote nodes
        QuoteNodeVisitor quoteNodeVisitor = new QuoteNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(QuoteNodeVisitor.VISIT_HANDLERS(quoteNodeVisitor));

        // Handle Heading nodes
        HeadingNodeVisitor headingNodeVisitor = new HeadingNodeVisitor(this.visitor, this.listeners,
            this.plainRendererFactory);
        this.visitor.addHandlers(HeadingNodeVisitor.VISIT_HANDLERS(headingNodeVisitor));

        // Handle Table nodes
        TableNodeVisitor tableNodeVisitor = new TableNodeVisitor(this.visitor, this.listeners,
            this.plainRendererFactory);
        this.visitor.addHandlers(TableNodeVisitor.VISIT_HANDLERS(tableNodeVisitor));

        // Handle strikethrough nodes
        StrikethroughNodeVisitor strikethroughNodeVisitor =
            new StrikethroughNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(StrikethroughNodeVisitor.VISIT_HANDLERS(strikethroughNodeVisitor));

        // Handle superscript and subscript nodes
        SubSuperscriptNodeVisitor subSuperscriptNodeVisitor =
            new SubSuperscriptNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(SubSuperscriptNodeVisitor.VISIT_HANDLERS(subSuperscriptNodeVisitor));

        // Handle HTML nodes
        HTMLNodeVisitor htmlNodeVisitor = new HTMLNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(HTMLNodeVisitor.VISIT_HANDLERS(htmlNodeVisitor));

        // Handle Code nodes
        CodeNodeVisitor codeNodeVisitor = new CodeNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(CodeNodeVisitor.VISIT_HANDLERS(codeNodeVisitor));

        // Handle Abbreviation nodes
        AbbreviationNodeVisitor abbreviationNodeVisitor = new AbbreviationNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(AbbreviationNodeVisitor.VISIT_HANDLERS(abbreviationNodeVisitor));

        // Handle Macro nodes
        MacroNodeVisitor macroNodeVisitor = new MacroNodeVisitor(this.visitor, this.listeners);
        this.visitor.addHandlers(MacroNodeVisitor.VISIT_HANDLERS(macroNodeVisitor));

        this.visitor.visit(node);
        getListener().endDocument(metaData);
    }

    public void visit(SoftLineBreak node)
    {
        // XWiki doesn't have a softlinkebreak block. Thus we consider a softlinebreak as a space.
        getListener().onSpace();
    }

    public void visit(HardLineBreak node)
    {
        getListener().onNewLine();
    }

    public void visit(Document node)
    {
        this.imageNodeVisitor.setReferenceRepository(Parser.REFERENCES.get(node));
        this.linkNodeVisitor.setReferenceRepository(Parser.REFERENCES.get(node));
        this.visitor.visitChildren(node);
    }

    public void visit(ThematicBreak node)
    {
        getListener().onHorizontalLine(Collections.emptyMap());
    }

    /**
     * @return the top listener on the stack
     */
    private Listener getListener()
    {
        return this.listeners.peek();
    }
}
