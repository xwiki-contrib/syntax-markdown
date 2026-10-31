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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.renderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.MarkdownConfiguration;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.parser.MacroBlockParser;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.DelimitedLinkNode;
import com.vladsch.flexmark.ast.HtmlInlineBase;
import com.vladsch.flexmark.ast.InlineLinkNode;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.RefNode;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.wikilink.WikiNode;
import com.vladsch.flexmark.ext.xwiki.macros.Macro;
import com.vladsch.flexmark.ext.xwiki.macros.MacroAttribute;
import com.vladsch.flexmark.ext.xwiki.macros.MacroClose;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.misc.Extension;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * Convert listener events to Markdown 1.2.
 *
 * @version $Id$
 * @since 8.4
 */
public class Markdown12ChainingRenderer extends Markdown11ChainingRenderer
{
    private static final String STRIKEDOUT_SYMBOL = "~~";

    private static final char QUOTE = '"';

    private static final String FORMAT_TWO_PARAMS = "[[%s|%s]]";

    private static final String FORMAT_THREE_PARAMS = "[[%s|%s|%s]]";

    private static final String WIKI_LINK_LABEL_PIPE = "&#124;";

    private static final String ALT_PARAMETER = "alt";

    private static final String PERCENT_ENCODING = "%%%02X";

    private static final char DEL = 127;

    private static final String CODE_MACRO_ID = "code";

    private static final String CODE_LANGUAGE_PARAMETER = "language";

    private static final String NEW_LINE_ENTITY = "&#10;";

    /**
     * The macro identifiers supported by the Markdown macro syntax (the same as the HTML tag names).
     */
    private static final Pattern MACRO_ID = Pattern.compile("[A-Za-z][A-Za-z0-9-]*");

    /**
     * The macro parameter names supported by the Markdown macro syntax (the same as the HTML attribute names).
     */
    private static final Pattern MACRO_PARAMETER_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9:._-]*");

    private static final Pattern NEW_LINES = Pattern.compile("\\r\\n|\\r|\\n");

    /**
     * See https://spec.commonmark.org/0.29/#autolinks.
     */
    private static final Pattern URI_AUTOLINK = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]{1,31}:[^\\s<>\\p{Cntrl}]*");

    // The repetition of the domain labels is possessive to avoid a stack overflow on large content (it doesn't change
    // the matched strings since backtracking into it can't lead to a match).
    private static final Pattern EMAIL_AUTOLINK = Pattern.compile("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]"
        + "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*+");

    /**
     * The characters starting or ending the inline syntaxes that could hide the closing tag of an inline macro: a code
     * span, a link or a wiki link (an opening angle bracket can also start an HTML tag or an autolink, see
     * {@link #HTML_START}).
     */
    private static final String TAG_HIDING_CHARACTERS = "`[]";

    /**
     * The start of an HTML tag, comment, processing instruction or declaration, or of an autolink.
     */
    private static final Pattern HTML_START = Pattern.compile("<[a-zA-Z/!?]");

    /**
     * The text written before an inline macro when parsing it to check it, so that it isn't parsed as a standalone
     * macro.
     */
    private static final String INLINE_MACRO_CHECK_PREFIX = "a ";

    private MarkdownConfiguration configuration;

    private boolean isStrikethroughSupported;

    private final Markdown12MacroRenderer macroSyntaxPrinter = new Markdown12MacroRenderer();

    /**
     * The parser used to check the inline macros, created when needed.
     */
    private Parser macroCheckParser;

    /**
     * The number of emphasis (e.g. bold) currently open, which are written using delimiters (e.g. "**").
     */
    private int emphasisDepth;

    /**
     * The number of emphasis open outside the tables being rendered.
     */
    private Deque<Integer> tableEmphasisDepths = new ArrayDeque<>();

    /**
     * Whether the current events are located in the label of a link that is rendered using the wiki link syntax.
     */
    private boolean isInWikiLink;

    /**
     * @param listenerChain the chain of listener filters used to compute various states
     * @param linkReferenceSerializer the component to use for converting {@link ResourceReference} links to strings
     * @param imageReferenceSerializer the component to use for converting {@link ResourceReference} images to strings
     */
    public Markdown12ChainingRenderer(ListenerChain listenerChain,
        ResourceReferenceSerializer linkReferenceSerializer, ResourceReferenceSerializer imageReferenceSerializer,
            MarkdownConfiguration configuration)
    {
        super(listenerChain, linkReferenceSerializer, imageReferenceSerializer);
        this.configuration = configuration;
        this.isStrikethroughSupported = isStrikethroughSupported();
    }

    @Override
    public void beginFormat(Format format, Map<String, String> parameters)
    {
        if (isEmphasis(format)) {
            this.emphasisDepth++;
        }
        if (format.equals(Format.STRIKEDOUT) && this.isStrikethroughSupported) {
            print(STRIKEDOUT_SYMBOL);
        } else {
            // Override from Markdown11ChainingRenderer since there's no need to escape space characters with
            //flexmark-java.
            switch (format) {
                case SUPERSCRIPT:
                    print(SUPERSCRIPT_SYMBOL);
                    break;
                case SUBSCRIPT:
                    print(SUBSCRIPT_SYMBOL);
                    break;
                default:
                    super.beginFormat(format, parameters);
            }
        }
    }

    @Override
    public void endFormat(Format format, Map<String, String> parameters)
    {
        if (isEmphasis(format)) {
            this.emphasisDepth--;
        }
        if (format.equals(Format.STRIKEDOUT) && this.isStrikethroughSupported) {
            print(STRIKEDOUT_SYMBOL);
        } else {
            // Override from Markdown11ChainingRenderer since there's no need to escape space characters with
            //flexmark-java.
            switch (format) {
                case SUPERSCRIPT:
                    print(SUPERSCRIPT_SYMBOL);
                    break;
                case SUBSCRIPT:
                    print(SUBSCRIPT_SYMBOL);
                    break;
                default:
                    super.endFormat(format, parameters);
            }
        }
    }

    /**
     * @param format a format
     * @return {@code true} if the format is written using delimiters that are processed as emphasis by the Markdown
     *     parser, {@code false} otherwise
     */
    private boolean isEmphasis(Format format)
    {
        switch (format) {
            case BOLD:
            case ITALIC:
            case SUPERSCRIPT:
            case SUBSCRIPT:
                return true;
            case STRIKEDOUT:
                return this.isStrikethroughSupported;
            default:
                return false;
        }
    }

    @Override
    public void beginTable(Map<String, String> parameters)
    {
        // The emphasis opened outside a table don't apply to its cells, unless the table is written inside the cell of
        // another table.
        this.tableEmphasisDepths.push(this.emphasisDepth);
        if (!isInTableCell()) {
            this.emphasisDepth = 0;
        }
        super.beginTable(parameters);
    }

    @Override
    public void endTable(Map<String, String> parameters)
    {
        super.endTable(parameters);
        this.emphasisDepth = this.tableEmphasisDepths.pop();
    }

    @Override
    public void beginLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        this.isInWikiLink = !isMarkdownLink(reference);
        super.beginLink(reference, isFreeStandingURI, parameters);
    }

    @Override
    public void onNewLine()
    {
        if (this.isInWikiLink) {
            // The wiki link label is plain text, in which HTML entities are supported (and a blank line would end the
            // wiki link).
            print(NEW_LINE_ENTITY);
        } else {
            super.onNewLine();
        }
    }

    @Override
    public void endLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        // Overridden from Markdown11ChainingRenderer since in markdown/1.2 we've changed the link syntax to use
        // depending on the reference type:
        // - [...](...) and <...> for URL and mailto
        // - [[...]] for Document, UNC, DataURI and all other types or references

        this.isInWikiLink = false;
        MarkdownEscapeWikiPrinter linkBlocksPrinter = getMarkdownPrinter();
        linkBlocksPrinter.flush();
        String label = linkBlocksPrinter.toString();
        popPrinter();

        if (isMarkdownLink(reference)) {
            String rawReference = reference.getReference();
            // Now decide if we should use an autolink or not
            if (StringUtils.isNotEmpty(label)) {
                printLink(label, escapeLinkReference(rawReference));
            } else if (isAutoLink(reference)) {
                // Don't output the type prefix.
                printAutoLink(rawReference);
            } else {
                // The reference can't be written using the autolink syntax.
                String linkReference = ResourceType.MAILTO.equals(reference.getType())
                    ? ResourceType.MAILTO.getScheme() + ':' + rawReference : rawReference;
                printLink(escapeText(rawReference), escapeLinkReference(linkReference));
            }
        } else {
            onWikiSyntax();
            printWikiLink(label, escapeWikiLinkReference(this.linkReferenceSerializer.serialize(reference)),
                reference.getParameters());
        }
    }

    /**
     * @return {@code true} if the link is rendered using the Markdown link syntax, {@code false} if it's rendered using
     *     the wiki link syntax
     */
    private boolean isMarkdownLink(ResourceReference reference)
    {
        return ResourceType.URL.equals(reference.getType()) || ResourceType.MAILTO.equals(reference.getType());
    }

    private boolean isAutoLink(ResourceReference reference)
    {
        Pattern pattern = ResourceType.MAILTO.equals(reference.getType()) ? EMAIL_AUTOLINK : URI_AUTOLINK;
        return pattern.matcher(reference.getReference()).matches();
    }

    private String escapeText(String text)
    {
        MarkdownEscapeHandler escapeHandler = new MarkdownEscapeHandler();
        escapeHandler.setOnNewLine(false);
        escapeHandler.setInLinkLabel(true);
        escapeHandler.setInTableCell(isInTableCell());
        StringBuffer buffer = new StringBuffer(removeNewLines(text));
        escapeHandler.escape(buffer, null);
        return buffer.toString();
    }

    private String escapeLinkReference(String rawReference)
    {
        // Escape the characters that would end the link destination or that would make it invalid.
        // e.g. [label](https://en.wikipedia.org/Some_Subject_\(With_Title\))
        // See https://spec.commonmark.org/0.29/#link-destination
        StringBuilder builder = new StringBuilder(rawReference.length());
        for (int i = 0; i < rawReference.length(); i++) {
            char c = rawReference.charAt(i);
            // Note that a link destination starting with '<' ends with the first '>'.
            if (c == '(' || c == ')' || (i == 0 && c == '<')) {
                builder.append(MarkdownEscapeHandler.ESCAPE_CHAR).append(c);
            } else {
                appendURICharacter(builder, c);
            }
        }
        return builder.toString();
    }

    private String encodeImageReference(String rawReference)
    {
        // The image destination is not unescaped by the parser so we use percent-encoding instead.
        StringBuilder builder = new StringBuilder(rawReference.length());
        for (int i = 0; i < rawReference.length(); i++) {
            char c = rawReference.charAt(i);
            if (c == '(' || c == ')' || (i == 0 && c == '<')) {
                builder.append(String.format(PERCENT_ENCODING, (int) c));
            } else {
                appendURICharacter(builder, c);
            }
        }
        return builder.toString();
    }

    private void appendURICharacter(StringBuilder builder, char c)
    {
        // Spaces and control characters are not allowed in link destinations.
        if (c <= ' ' || c == DEL) {
            builder.append(String.format(PERCENT_ENCODING, (int) c));
        } else {
            builder.append(c);
        }
    }

    /**
     * Escape the closing brackets that could end the wiki link (the content of wiki links is not unescaped by the
     * parser but backslash escapes are taken into account when looking for the end of the wiki link).
     */
    private String escapeWikiLinkReference(String reference)
    {
        return StringUtils.replace(removeNewLines(reference), "]", MarkdownEscapeHandler.ESCAPE_CHAR + ']');
    }

    /**
     * Replace the new lines since a blank line would end the paragraph and thus the link or image syntax.
     */
    private String removeNewLines(String text)
    {
        return NEW_LINES.matcher(text).replaceAll(" ");
    }

    @Override
    public void onImage(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        // Overridden from Markdown11ChainingRenderer since in markdown/1.2 we've changed the image syntax to use
        // depending on the reference type:
        // - [...](...) and <...> for URL
        // - [[...]] for Attach and all other types or references

        String alt = parameters.get(ALT_PARAMETER);
        if (StringUtils.isBlank(alt)) {
            alt = reference.getReference();
        }

        if (ResourceType.URL.equals(reference.getType())) {
            print(String.format("![%s](%s)", escapeText(alt), encodeImageReference(reference.getReference())));
        } else {
            onWikiSyntax();
            String escapedReference = escapeWikiLinkReference(reference.getReference());
            if (isWikiSyntaxSeparatorEscaped() && StringUtils.isBlank(parameters.get(ALT_PARAMETER))) {
                // Avoid the separator, which would be kept at the end of the alternative text.
                print("![[" + escapedReference + "]]");
            } else {
                print("![[" + escapeWikiLinkReference(alt) + getWikiSyntaxSeparator() + escapedReference + "]]");
            }
        }
    }

    protected void printWikiLink(String label, String serializedReference, Map<String, String> parameters)
    {
        if (StringUtils.isEmpty(label)) {
            if (parameters.isEmpty()) {
                printWikiLink(serializedReference);
            } else {
                print(String.format(FORMAT_TWO_PARAMS, serializedReference, serializeParameters(parameters)));
            }
        } else {
            // Only the separator located after the label can be escaped (the backslash being kept in the label).
            String wikiLinkLabel = escapeWikiLinkLabel(label)
                + (isWikiSyntaxSeparatorEscaped() ? MarkdownEscapeHandler.ESCAPE_CHAR : "");
            if (parameters.isEmpty()) {
                print(String.format(FORMAT_TWO_PARAMS, wikiLinkLabel, serializedReference));
            } else {
                print(String.format(FORMAT_THREE_PARAMS, wikiLinkLabel, serializedReference,
                    serializeParameters(parameters)));
            }
        }
    }

    /**
     * @return {@code true} if the pipe separating the parts of the wiki link and wiki image syntaxes must be escaped,
     *     {@code false} otherwise
     */
    private boolean isWikiSyntaxSeparatorEscaped()
    {
        // In a table cell, the table parser ends the pending emphasis (e.g. bold) at each pipe that is not located
        // inside a code span, an HTML tag or an autolink, even if the pipe doesn't split the cell, e.g. a wiki link
        // separator. The emphasis and the wiki link or image syntaxes are then broken, splitting the cell. An escaped
        // separator doesn't split the cell, but the backslash is kept at the end of the wiki link label or of the
        // image alternative text (the wiki link syntax doesn't support escaping its separator).
        return isInTableCell() && this.emphasisDepth > 0;
    }

    private String getWikiSyntaxSeparator()
    {
        return isWikiSyntaxSeparatorEscaped() ? MarkdownEscapeHandler.ESCAPE_CHAR + '|' : "|";
    }

    private String escapeWikiLinkLabel(String label)
    {
        // The wiki link label ends at the first pipe character (which can't be escaped with a backslash), but HTML
        // entities are supported. Note that the pipes of a label located in a table cell are already escaped with a
        // backslash.
        StringBuilder builder = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c == '|') {
                builder.append(WIKI_LINK_LABEL_PIPE);
            } else if (c == MarkdownEscapeHandler.ESCAPE_CHAR.charAt(0) && i + 1 < label.length()) {
                // Escaped character.
                i++;
                char escaped = label.charAt(i);
                if (escaped == '|') {
                    builder.append(WIKI_LINK_LABEL_PIPE);
                } else {
                    builder.append(c).append(escaped);
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String serializeParameters(Map<String, String> parameters)
    {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = parameters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(entry.getKey()).append('=').append(QUOTE)
                .append(escapeWikiLinkReference(entry.getValue())).append(QUOTE);
            if (iterator.hasNext()) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    @Override
    protected MarkdownMacroRenderer createMacroPrinter()
    {
        return new Markdown12MacroRenderer();
    }

    private boolean isStrikethroughSupported()
    {
        for (Extension extension : Parser.EXTENSIONS.get(this.configuration.getOptions())) {
             if (extension instanceof StrikethroughExtension || extension instanceof StrikethroughSubscriptExtension) {
                 return true;
             }
        }
        return false;
    }

    /**
     * @return {@code true} if the macro identifier and parameter names are supported by the Markdown macro syntax,
     *     {@code false} otherwise
     */
    private boolean isMacroSyntaxSupported(String id, Map<String, String> parameters)
    {
        if (!MACRO_ID.matcher(id).matches()) {
            return false;
        }
        for (String parameterName : parameters.keySet()) {
            if (!MACRO_PARAMETER_NAME.matcher(parameterName).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} if the macro content is read back as is when the macro is written using the Markdown macro
     *     syntax, {@code false} otherwise (e.g. if the content contains the closing tag of the macro)
     */
    private boolean isMacroContentSupported(String id, Map<String, String> parameters, String content,
        boolean isInline)
    {
        if (content == null) {
            return true;
        } else if (isInTableCell() && !CODE_MACRO_ID.equals(id)) {
            // The macros located in table cells are written as inline macros on the line of the table row, the line
            // endings of their content being written as line break tags.
            return isInlineMacroSupported(
                replaceTableCellLineBreaks(this.macroSyntaxPrinter.renderTableCellMacro(id, parameters, content)));
        } else if (isInline || isInTableCell()) {
            return isInlineMacroSupported(this.macroSyntaxPrinter.renderInlineMacro(id, parameters, content));
        } else {
            return MacroBlockParser.isStandaloneMacroContent(id, content);
        }
    }

    /**
     * Indicates if an inline macro is read back as the same macro, whatever the text written after it. The macro is
     * parsed on its own: it must be closed by its closing tag (and thus the tags having the same name located in its
     * content must be balanced and not hidden by another syntax, e.g. a code span), and its content must not contain
     * the start of an inline syntax that isn't ended in the macro (e.g. a backtick that doesn't start a code span on
     * its own), since the text written after the macro could end it, hiding the closing tag of the macro.
     *
     * @param macro the inline macro, as written (e.g. {@code {{info}}content{{/info}}})
     * @return {@code true} if the macro is read back as the same macro, {@code false} otherwise
     */
    private boolean isInlineMacroSupported(String macro)
    {
        if (this.macroCheckParser == null) {
            this.macroCheckParser = Parser.builder(this.configuration.getOptions()).build();
        }
        int macroStart = INLINE_MACRO_CHECK_PREFIX.length();
        Node paragraph = this.macroCheckParser.parse(INLINE_MACRO_CHECK_PREFIX + macro).getFirstChild();
        Node macroNode =
            paragraph instanceof Paragraph && paragraph.getNext() == null ? paragraph.getLastChild() : null;
        return macroNode instanceof Macro && macroNode.getStartOffset() == macroStart
            && macroNode.getLastChild() instanceof MacroClose
            && macroNode.getLastChild().getEndOffset() == macroStart + macro.length()
            && !hasUnendedInlineSyntax(macroNode);
    }

    /**
     * @return {@code true} if the node contains a character starting or ending an inline syntax that could hide the
     *     closing tag of the macro in which the node is located (see {@link #TAG_HIDING_CHARACTERS}) outside of the
     *     syntaxes ended in the node, {@code false} otherwise
     */
    private boolean hasUnendedInlineSyntax(Node node)
    {
        if (node instanceof Code || node instanceof HtmlInlineBase
            || (node instanceof DelimitedLinkNode && ((DelimitedLinkNode) node).getOpeningMarker().isNotNull()))
        {
            // The code spans, the inline HTML and the autolinks written between angle brackets are ended in the node,
            // and their content can't be read as another syntax.
            return false;
        }

        // The characters of the macro tags, and the brackets, destinations and titles of the links and images, are
        // part of syntaxes ended in the node. The text of a wiki link is located between its markers, and the content
        // of a macro after its opening tag.
        boolean isTextOwner = !(node instanceof MacroClose || node instanceof MacroAttribute
            || node instanceof InlineLinkNode || node instanceof RefNode);
        int offset = node.getStartOffset();
        int end = node.getEndOffset();
        if (node instanceof Macro) {
            offset = ((Macro) node).getClosingMarker().getEndOffset();
        } else if (node instanceof WikiNode) {
            offset = ((WikiNode) node).getOpeningMarker().getEndOffset();
            end = ((WikiNode) node).getClosingMarker().getStartOffset();
        }
        for (Node child : node.getChildren()) {
            if ((isTextOwner && hasTagHidingCharacter(node, offset, child.getStartOffset()))
                || hasUnendedInlineSyntax(child))
            {
                return true;
            }
            offset = Math.max(offset, child.getEndOffset());
        }
        return isTextOwner && hasTagHidingCharacter(node, offset, end);
    }

    private boolean hasTagHidingCharacter(Node node, int start, int end)
    {
        if (start >= end) {
            return false;
        }
        BasedSequence text = node.getChars().baseSubSequence(start, end);
        // The character following an opening angle bracket can be located after the checked text.
        BasedSequence htmlText =
            node.getChars().baseSubSequence(start, Math.min(end + 1, node.getChars().getBaseSequence().length()));
        return StringUtils.containsAny(text, TAG_HIDING_CHARACTERS) || HTML_START.matcher(htmlText).find();
    }

    /**
     * Write the macro as text, keeping its line endings as new lines (a line ending in the text would be read back as
     * a space, and a blank line would end the paragraph).
     */
    private void printMacroAsText(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        if (!isInline) {
            // A standalone macro is separated from the previous blocks, including in a table cell.
            printEmptyLine();
        }
        String[] lines = NEW_LINES.split(createMacroPrinter().renderMacro(id, parameters, content, isInline), -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                onNewLine();
            }
            printText(lines[i]);
        }
    }

    protected boolean handleHtmlMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        boolean isHandled = false;

        if (id.equals("html")) {
            String html = StringUtils.defaultString(createMacroPrinter().renderMacro(id, parameters, content,
                isInline));
            MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();

            boolean isInTableCell = isInTableCell();
            if (isInTableCell) {
                // Markdown table cells can only contain inline content on a single line, thus the HTML is rendered
                // as inline HTML, with its line endings replaced by spaces (which is equivalent in HTML, except in
                // preformatted elements) since they would be rendered as line breaks otherwise. Standalone HTML is
                // separated from the previous blocks of the cell by an empty line, as other blocks.
                html = NEW_LINES.matcher(html).replaceAll(" ");
                if (!isInline) {
                    printEmptyLine();
                }
            }

            // Keep the HTML tags verbatim and only escape the text that would be parsed as Markdown.
            if (isInline || isInTableCell) {
                // Print what comes before so that the escaping knows if the HTML is located at the beginning of a
                // line.
                if (!html.isEmpty()) {
                    printPendingSeparator(html);
                }
                printer.flush(html);
                print(printer.escapeInlineHTML(html));
            } else {
                printEmptyLine();
                print(printer.escapeBlockHTML(html));
            }
            isHandled = true;
        }

        return isHandled;
    }

    /**
     * Prepare the writing of an inline macro using the macro syntax, so that it's not read back as a standalone macro
     * when it's the only content of its line. This is the case when the macro is the first content of a paragraph or
     * of a quotation line: it's then preceded by a space, which isn't part of the paragraph content and which prevents
     * the line from being read as a standalone macro. This isn't possible for the first content of a list item or
     * definition description (the space would be part of the item marker).
     *
     * @return {@code true} if the inline macro can be written using the macro syntax, {@code false} otherwise
     */
    private boolean prepareInlineMacroSyntax()
    {
        if (isInTableCell() || !isAtBlockStart()) {
            return true;
        } else if (isAtItemStart()) {
            return false;
        }
        print(" ");
        return true;
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        if (handleHtmlMacro(id, parameters, content, isInline) || handleCodeMacro(id, parameters, content, isInline)) {
            return;
        }

        boolean isCode = CODE_MACRO_ID.equals(id);
        if (!isMacroSyntaxSupported(id, parameters) || !isMacroContentSupported(id, parameters, content, isInline)
            || (isCode && isInline && !StringUtils.isEmpty(content) && !prepareInlineMacroSyntax()))
        {
            if (isCode && !StringUtils.isEmpty(content)) {
                // The code macro is written using the Markdown code syntax instead, which is read back as a code
                // macro having the same content (but possibly not the same parameters).
                printCode(content, parameters.get(CODE_LANGUAGE_PARAMETER), isInline);
            } else {
                // The macro can't be written using the Markdown macro syntax, which would then be read back as
                // Markdown or as other macros. Write it as text instead, so that it's at least read back as the same
                // text.
                printMacroAsText(id, parameters, content, isInline);
            }
            return;
        }

        if (isInTableCell()) {
            // Markdown table cells can only contain inline content on a single line, thus a standalone macro located
            // in a table cell is rendered as an inline macro (the line endings around its content would be rendered
            // as line breaks, and thus added to its content, otherwise), separated from the previous blocks of the
            // cell by an empty line, as other blocks. The line endings of the content of the macros located in table
            // cells are kept, and written as line break tags when the table cell ends, except for the code macro
            // whose content is displayed as is (the line break tags would be displayed): they are replaced by spaces,
            // as for the inline macros.
            if (!isInline) {
                printEmptyLine();
            }
            onWikiSyntax();
            print(isCode ? this.macroSyntaxPrinter.renderInlineMacro(id, parameters, content)
                : this.macroSyntaxPrinter.renderTableCellMacro(id, parameters, content));
            return;
        }

        super.onMacro(id, parameters, content, isInline);
    }
}
