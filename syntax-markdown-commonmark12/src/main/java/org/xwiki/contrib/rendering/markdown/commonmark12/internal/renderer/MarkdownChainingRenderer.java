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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.xwiki.rendering.internal.renderer.xwiki20.XWikiSyntaxListenerChain;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.chaining.BlockStateChainingListener;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.AbstractChainingPrintRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;

/**
 * Convert listener events to Markdown 1.0.
 *
 * @version $Id: 5c0886a29259563b388d2b38eff9ad027e1d75f8 $
 * @since 8.1RC1
 */
public class MarkdownChainingRenderer extends AbstractChainingPrintRenderer
{
    private static final String BACKTICK = "`";

    private static final String TRIPLE_BACKTICK = BACKTICK + BACKTICK + BACKTICK;

    private static final String NEW_LINE = "\n";

    private static final String EMPTY_LINE = NEW_LINE + NEW_LINE;

    private static final Pattern HEADING_CLOSING_SEQUENCE = Pattern.compile("(^|\\p{Blank})(#+\\p{Blank}*)$");

    private static final Pattern HTML_ATTRIBUTE_NAME = Pattern.compile("[^\\s\"'>/=\\p{Cntrl}]+");

    /**
     * State of a block container (Group, Quotation) being rendered, whose content is rendered separately so that it
     * can be wrapped.
     */
    private static class ContainerState
    {
        /**
         * Whether the container is not rendered as a block (e.g. in a table cell), but only its content is rendered.
         */
        protected boolean isFlattened;

        /**
         * The prefix to put in front of each line of the container so that it stays inside the container in which
         * it's located (list item, definition description).
         */
        protected String linePrefix;

        protected boolean wasFirstElementRendered;

        protected boolean wasAtContainerStart;

        protected String previousPendingSeparator;
    }

    /**
     * State of a Group being rendered.
     */
    private static final class GroupState extends ContainerState
    {
        private boolean isInListItem;

        /**
         * Whether the Group is a parameterless Group located directly in a quotation, which is rendered without div
         * since a Markdown quotation can contain any block.
         */
        private boolean isTransparent;

        /**
         * The states of the quotations in which the Group is located, restored at the end of the Group since the
         * Group content is an independent document.
         */
        private Deque<QuotationState> previousQuotationStates;

        /**
         * Length of the rendered Group content just after rendering its first element when this element is a
         * standalone code macro rendered as an indented code block, -1 otherwise.
         */
        private int indentedCodeEndIndex = -1;
    }

    /**
     * State of a Quotation being rendered.
     */
    private static final class QuotationState extends ContainerState
    {
        /**
         * The list depth at the start of the quotation, so that the lists located inside the quotation are rendered
         * relatively to it.
         */
        private int listDepth;

        /**
         * The definition list depth at the start of the quotation.
         */
        private int definitionListDepth;
    }

    protected ResourceReferenceSerializer linkReferenceSerializer;

    protected ResourceReferenceSerializer imageReferenceSerializer;

    // Custom States

    private boolean isFirstElementRendered;

    private Stack<String> listStyle = new Stack<>();

    private Stack<List<List<String>>> tableCells = new Stack<>();

    /**
     * How many head rows each table has.
     */
    private Stack<Integer> tableHeadRowsCount = new Stack<>();

    private Stack<Boolean> isOnFirstHeadCellInTableRow = new Stack<>();

    private Stack<Map<String, String>> abbreviations = new Stack<>();

    private Deque<GroupState> groupStates = new ArrayDeque<>();

    /**
     * The states of the quotations being rendered, in the current Group (or document).
     */
    private Deque<QuotationState> quotationStates = new ArrayDeque<>();

    /**
     * Whether nothing has been printed yet in the current container (list item, definition description).
     */
    private boolean isAtContainerStart;

    /**
     * Line prefix of the container of the last rendered Group, set until something is printed after it, so that
     * any content following the Group in the same container is separated from it by an empty line (otherwise it
     * would be considered part of the HTML block of the Group closing tag).
     */
    private String pendingSeparator;

    private int monospaceDepth;

    /**
     * @param listenerChain the chain of listener filters used to compute various states
     * @param linkReferenceSerializer the component to use for converting {@link ResourceReference} links to strings
     * @param imageReferenceSerializer the component to use for converting {@link ResourceReference} images to strings
     */
    public MarkdownChainingRenderer(ListenerChain listenerChain,
        ResourceReferenceSerializer linkReferenceSerializer, ResourceReferenceSerializer imageReferenceSerializer)
    {
        setListenerChain(listenerChain);

        this.linkReferenceSerializer = linkReferenceSerializer;
        this.imageReferenceSerializer = imageReferenceSerializer;
    }

    @Override
    public void beginDocument(MetaData metaData)
    {
        this.abbreviations.push(new LinkedHashMap<>());
    }

    @Override
    public void endDocument(MetaData metaData)
    {
        // Display abbreviations
        if (!this.abbreviations.peek().isEmpty()) {
            printEmptyLine();
            Iterator<Map.Entry<String, String>> it = this.abbreviations.peek().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                print("*[" + entry.getKey() + "]: " + entry.getValue());
                if (it.hasNext()) {
                    print("\n");
                }
            }
        }
        this.abbreviations.pop();

        // Make sure that any delayed text is printed.
        getMarkdownPrinter().flush();
    }

    @Override
    public void beginFormat(Format format, Map<String, String> parameters)
    {
        switch (format) {
            case BOLD:
                print("**");
                break;
            case ITALIC:
                print("_");
                break;
            case UNDERLINED:
                print("<ins>");
                break;
            case STRIKEDOUT:
                print("<del>");
                break;
            case SUPERSCRIPT:
                print("<sup>");
                break;
            case SUBSCRIPT:
                print("<sub>");
                break;
            case MONOSPACE:
                if (this.monospaceDepth == 0) {
                    // Accumulate the content since the code span delimiters depend on it.
                    printPendingSeparator(BACKTICK);
                    this.isAtContainerStart = false;
                    pushPrinter(createInlineMarkdownPrinter());
                }
                this.monospaceDepth++;
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    public void endFormat(Format format, Map<String, String> parameters)
    {
        switch (format) {
            case BOLD:
                print("**");
                break;
            case ITALIC:
                print("_");
                break;
            case UNDERLINED:
                print("</ins>");
                break;
            case STRIKEDOUT:
                print("</del>");
                break;
            case SUPERSCRIPT:
                print("</sup>");
                break;
            case SUBSCRIPT:
                print("</sub>");
                break;
            case MONOSPACE:
                this.monospaceDepth--;
                if (this.monospaceDepth == 0) {
                    MarkdownEscapeWikiPrinter monospacePrinter = getMarkdownPrinter();
                    monospacePrinter.flush();
                    String code = monospacePrinter.toString();
                    popPrinter();
                    print(formatCodeSpan(code));
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    public void beginGroup(Map<String, String> parameters)
    {
        GroupState state = new GroupState();
        this.groupStates.push(state);

        // Markdown tables can't contain blocks, thus only the Group content is rendered.
        if (getBlockState().isInTableCell()) {
            state.isFlattened = true;
            return;
        }

        state.isInListItem = isInList();
        state.isTransparent = parameters.isEmpty() && isDirectlyInQuotation();

        // The Group content is an independent document which is rendered separately so that it can be wrapped.
        beginBufferedContainer(state);
        state.previousQuotationStates = this.quotationStates;
        this.quotationStates = new ArrayDeque<>();
        getListenerChain().pushAllStackableListeners();
    }

    @Override
    public void endGroup(Map<String, String> parameters)
    {
        GroupState state = this.groupStates.pop();
        if (state.isFlattened) {
            return;
        }

        getMarkdownPrinter().flush();
        getListenerChain().popAllStackableListeners();
        this.quotationStates = state.previousQuotationStates;
        String content = endBufferedContainer(state);

        if (parameters.isEmpty() && state.isInListItem && state.indentedCodeEndIndex == content.length()) {
            // A parameterless Group whose only content is an indented code block, located in a list item, is the
            // Group that the Markdown parser creates around indented code blocks in list items (since a code macro
            // is standalone), and thus doesn't need to be rendered.
            printEmptyLine();
            print(prefixLines(content, state.linePrefix, false));
            return;
        }

        printGroupSeparator(state);

        if (state.isTransparent) {
            // A parameterless Group located directly in a quotation is the Group that the Markdown and XHTML parsers
            // create around the blocks of a quotation (since a quotation line can only contain inline content), and
            // thus doesn't need to be rendered.
            print(prefixLines(content, state.linePrefix, true));
            this.pendingSeparator = state.linePrefix;
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<div");
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (isValidGroupParameterName(parameter.getKey())) {
                builder.append(' ').append(parameter.getKey()).append("=\"")
                    .append(escapeHTMLAttributeValue(parameter.getValue())).append('"');
            }
        }
        builder.append('>').append(EMPTY_LINE);
        if (!content.isEmpty()) {
            builder.append(content).append(EMPTY_LINE);
        }
        builder.append("</div>");
        print(prefixLines(builder.toString(), state.linePrefix, true));

        this.pendingSeparator = state.linePrefix;
    }

    private void printGroupSeparator(ContainerState state)
    {
        if (!state.wasAtContainerStart) {
            if (this.isFirstElementRendered) {
                print(EMPTY_LINE + state.linePrefix);
            } else {
                this.isFirstElementRendered = true;
            }
        }
    }

    /**
     * Start rendering the content of a block container separately, so that it can be wrapped once it's ended.
     *
     * @param state the state of the container, in which the current rendering state is saved
     */
    private void beginBufferedContainer(ContainerState state)
    {
        state.linePrefix = getContainerLinePrefix();
        state.wasFirstElementRendered = this.isFirstElementRendered;
        state.wasAtContainerStart = this.isAtContainerStart;
        state.previousPendingSeparator = this.pendingSeparator;

        pushPrinter(createMarkdownPrinter(new DefaultWikiPrinter()));
        this.isFirstElementRendered = false;
        this.isAtContainerStart = false;
        this.pendingSeparator = null;
    }

    /**
     * Stop rendering the content of a block container separately, and restore the rendering state saved when it
     * started.
     *
     * @param state the state of the container
     * @return the rendered content of the container
     */
    private String endBufferedContainer(ContainerState state)
    {
        MarkdownEscapeWikiPrinter containerPrinter = getMarkdownPrinter();
        containerPrinter.flush();
        String content = containerPrinter.toString();
        popPrinter();

        this.isFirstElementRendered = state.wasFirstElementRendered;
        this.isAtContainerStart = state.wasAtContainerStart;
        this.pendingSeparator = state.previousPendingSeparator;

        return content;
    }

    private String getContainerLinePrefix()
    {
        String prefix;
        if (isInList()) {
            // List item content must be aligned with the first character following the list item marker.
            int markerLength = StringUtils.contains(this.listStyle.peek(), '1') ? 3 : 2;
            prefix = StringUtils.repeat(' ', (getListDepth() - 1) * 4 + markerLength);
        } else {
            prefix = getDefinitionListLinePrefix();
        }
        return prefix;
    }

    private String getDefinitionListLinePrefix()
    {
        return StringUtils.repeat(' ', 4 * getDefinitionListDepth());
    }

    /**
     * @return the list depth, relative to the quotation in which the current block is located (since the content of
     *     a quotation is rendered separately)
     */
    private int getListDepth()
    {
        QuotationState quotationState = this.quotationStates.peek();
        return getBlockState().getListDepth() - (quotationState != null ? quotationState.listDepth : 0);
    }

    private boolean isInList()
    {
        return getListDepth() > 0;
    }

    /**
     * @return the definition list depth, relative to the quotation in which the current block is located
     */
    private int getDefinitionListDepth()
    {
        QuotationState quotationState = this.quotationStates.peek();
        return getBlockState().getDefinitionListDepth()
            - (quotationState != null ? quotationState.definitionListDepth : 0);
    }

    /**
     * @return {@code true} if the current block is located directly in a quotation (or a quotation line), i.e. not in
     *     a list or definition list located inside the quotation
     */
    private boolean isDirectlyInQuotation()
    {
        return !this.quotationStates.isEmpty() && !this.quotationStates.peek().isFlattened && !isInList()
            && getDefinitionListDepth() == 0;
    }

    private static String prefixLines(String text, String prefix, boolean skipFirstLine)
    {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split(NEW_LINE, -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append(NEW_LINE);
            }
            // Don't generate trailing spaces on empty lines.
            if ((i > 0 || !skipFirstLine) && !lines[i].isEmpty()) {
                builder.append(prefix);
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static boolean isValidGroupParameterName(String name)
    {
        return HTML_ATTRIBUTE_NAME.matcher(name).matches();
    }

    private static String escapeHTMLAttributeValue(String value)
    {
        // New lines are escaped too since an empty line would end the HTML block of the div opening tag.
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            .replace(NEW_LINE, "&#10;").replace("\r", "&#13;");
    }

    @Override
    public void onHorizontalLine(Map<String, String> parameters)
    {
        printBlockSeparator(getContainerLinePrefix());
        print("---");
    }

    @Override
    public void beginParagraph(Map<String, String> parameters)
    {
        printBlockSeparator(getContainerLinePrefix());
    }

    @Override
    public void beginHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        printBlockSeparator(getContainerLinePrefix());
        print(StringUtils.repeat("#", level.getAsInt()) + " ");
        pushPrinter(createInlineMarkdownPrinter());
    }

    @Override
    public void endHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        MarkdownEscapeWikiPrinter headingPrinter = getMarkdownPrinter();
        headingPrinter.flush();
        String heading = headingPrinter.toString();
        popPrinter();
        // Make sure that the heading content doesn't end with what would be considered a closing sequence.
        print(HEADING_CLOSING_SEQUENCE.matcher(heading).replaceFirst("$1\\\\$2"));

        // Any inline content following the heading in the same container (e.g. a list item) must be separated from
        // it by an empty line, otherwise it would be part of the heading.
        if (!getBlockState().isInTableCell()) {
            this.pendingSeparator = getContainerLinePrefix();
        }
    }

    @Override
    public void beginList(ListType listType, Map<String, String> parameters)
    {
        if (getListDepth() == 1) {
            // The list is not located in a list item (its container line prefix is not the one of its items).
            printBlockSeparator(getDefinitionListLinePrefix());
        } else {
            print("\n");
        }

        if (listType == ListType.BULLETED) {
            this.listStyle.push("*");
        } else {
            this.listStyle.push("1");
        }
    }

    @Override
    public void endList(ListType listType, Map<String, String> parameters)
    {
        this.listStyle.pop();
        // Ensure that any not printed characters are flushed.
        // TODO: Fix this better by introducing a state listener to handle escapes
        getMarkdownPrinter().flush();
    }

    @Override
    public void beginListItem()
    {
        if (getBlockState().getListItemIndex() > 0) {
            print("\n");
        }

        print(StringUtils.repeat(" ", (getListDepth() - 1) * 4));
        print(this.listStyle.peek());
        if (StringUtils.contains(this.listStyle.peek(), '1')) {
            print(".");
        }
        print(" ");
        markBlockContentStart();
        this.isAtContainerStart = true;
    }

    @Override
    public void endListItem()
    {
        // The next list item doesn't need to be separated from a Group ending the current one.
        this.pendingSeparator = null;
        // The list item may be empty: the blocks following it must not be considered as its first content.
        this.isAtContainerStart = false;
        super.endListItem();
    }

    /**
     * Start of a list item.
     *
     * @param parameters a generic list of parameters for the list item. Example: "style"/"background-color: blue"
     * @since 8.5.4
     */
    public void beginListItem(Map<String, String> parameters)
    {
        // XWiki Rendering had a regression between versions 10.0 and 10.11.11/11.3.7/11.10.2/12.0 (excluded).
        // This is to make this extension work when used on an XWiki version that has the regression.
        // See also https://jira.xwiki.org/browse/XRENDERING-581
        // More specifically:
        // - Before XWiki 10.0, the Listener interface doesn't have such signature so this method will be ignored and
        //   beginListItem() will be called.
        // - After XWiki 10.0, this method will be called.
        beginListItem();
    }

    /**
     * End of a list item.
     *
     * @param parameters a generic list of parameters for the list item. Example: "style"/"background-color: blue"
     * @since 8.5.4
     */
    public void endListItem(Map<String, String> parameters)
    {
        // See explanations in {@link #beginListItem}.
        endListItem();
    }

    @Override
    public void beginDefinitionDescription()
    {
        if (getBlockState().getDefinitionListItemIndex() > 0) {
            print("\n");
        }

        print(StringUtils.repeat(' ', 4 * (getDefinitionListDepth() - 1)) + ":   ");
        markBlockContentStart();
        this.isAtContainerStart = true;
    }

    @Override
    public void beginDefinitionTerm()
    {
        printEmptyLine();
        print(StringUtils.repeat(' ', 4 * (getDefinitionListDepth() - 1)));
        markBlockContentStart();
    }

    @Override
    public void endDefinitionDescription()
    {
        getMarkdownPrinter().flush();
        // The definition description may be empty: the blocks following it must not be considered as its first
        // content.
        this.isAtContainerStart = false;
    }

    @Override
    public void endDefinitionTerm()
    {
        getMarkdownPrinter().flush();
    }

    @Override
    public void beginQuotation(Map<String, String> parameters)
    {
        QuotationState state = new QuotationState();
        // Markdown tables can't contain blocks, thus only the quotation content is rendered.
        if (getBlockState().isInTableCell()) {
            state.isFlattened = true;
        } else {
            // The quotation content is rendered separately so that all its lines can be prefixed once it's ended.
            beginBufferedContainer(state);
        }
        state.listDepth = getBlockState().getListDepth();
        state.definitionListDepth = getBlockState().getDefinitionListDepth();
        this.quotationStates.push(state);
    }

    @Override
    public void endQuotation(Map<String, String> parameters)
    {
        QuotationState state = this.quotationStates.pop();
        if (state.isFlattened) {
            return;
        }

        String content = endBufferedContainer(state);

        printGroupSeparator(state);
        StringBuilder builder = new StringBuilder();
        String[] lines = content.split(NEW_LINE, -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append(NEW_LINE);
            }
            // Don't generate trailing spaces on empty lines.
            builder.append(lines[i].isEmpty() ? ">" : "> ").append(lines[i]);
        }
        print(prefixLines(builder.toString(), state.linePrefix, true));

        // Any content following the quotation in the same container must be separated from it by an empty line,
        // otherwise it would be considered part of the quotation (lazy continuation line).
        this.pendingSeparator = state.linePrefix;
    }

    @Override
    public void beginQuotationLine()
    {
        if (this.quotationStates.peek().isFlattened) {
            if (getBlockState().getQuotationLineIndex() > 0) {
                print(" ");
            }
        } else {
            // Each quotation line is rendered as a paragraph of the quotation.
            this.isAtContainerStart = false;
            printEmptyLine();
            this.isAtContainerStart = true;
            markBlockContentStart();
        }
    }

    @Override
    public void endQuotationLine()
    {
        getMarkdownPrinter().flush();
        // The blocks following the quotation line (even if it's empty) must be separated from it.
        this.isAtContainerStart = false;
    }

    @Override
    public void beginLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        // The separator needed after a Group must be printed before the link, and not in its label.
        printPendingSeparator("[");
        this.isAtContainerStart = false;

        MarkdownEscapeWikiPrinter linkLabelPrinter = createMarkdownPrinter(new DefaultWikiPrinter());

        // Make sure the escape handler knows there is already characters before
        linkLabelPrinter.setOnNewLine(getMarkdownPrinter().isOnNewLine());
        linkLabelPrinter.setInLinkLabel(true);

        // Defer printing the link content since we need to gather all nested elements
        pushPrinter(linkLabelPrinter);
    }

    @Override
    public void endLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        MarkdownEscapeWikiPrinter linkBlocksPrinter = getMarkdownPrinter();
        linkBlocksPrinter.flush();
        String label = linkBlocksPrinter.toString();
        popPrinter();

        String serializedReference = this.linkReferenceSerializer.serialize(reference);

        // When the label is empty and the reference is a URL or an email address then use an autolink syntax,
        // otherwise use the wikilink syntax.
        // Note that we don't use the autolink syntax for mailto, UNC or Data URIs since that's not supported by the
        // MD spec and by pegdown.
        if (StringUtils.isEmpty(label)) {
            // Don't output the type prefix.
            if (ResourceType.MAILTO.equals(reference.getType()) || ResourceType.URL.equals(reference.getType())) {
                printAutoLink(reference.getReference());
            } else {
                printWikiLink(serializedReference);
            }
        } else {
            // Note: if the reference contains a space it'll generate an invalid link syntax. This is fixed with
            // markdown/1.2.
            printLink(label, serializedReference);
        }
    }

    protected void printLink(String label, String serializedReference)
    {
        print("[" + label + "](" + serializedReference + ")");
    }

    protected void printWikiLink(String serializedReference)
    {
        print("[[" + serializedReference + "]]");
    }

    protected void printAutoLink(String serializedReference)
    {
        print("<" + serializedReference + ">");
    }

    private boolean isExternalReference(ResourceReference reference)
    {
        return ResourceType.URL.equals(reference.getType()) || ResourceType.MAILTO.equals(reference.getType())
                || ResourceType.DATA.equals(reference.getType()) || ResourceType.UNC.equals(reference.getType());
    }

    @Override
    public void onImage(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        String alt = parameters.get("alt");
        String serializedReference = this.imageReferenceSerializer.serialize(reference);
        if (StringUtils.isBlank(alt)) {
            alt = serializedReference;
        }
        print("![" + alt + "](" + serializedReference + ")");
    }

    @Override
    public void onSpace()
    {
        printText(" ");
    }

    @Override
    public void onNewLine()
    {
        print("  ");
        print("\n");
    }

    @Override
    public void onWord(String word)
    {
        printText(word);
    }

    @Override
    public void onSpecialSymbol(char symbol)
    {
        // - Handle pegdown QUOTES extension for << and >>
        // - Handle --- as em-dash
        // - Handle ... as ellipsis
        // - Handle -- as range
        if (symbol == 8220) {
            printText("<<");
        } else if (symbol == 8221) {
            printText(">>");
        } else if (symbol == 8212) {
            printText("---");
        } else if (symbol == 8230) {
            printText("...");
        } else if (symbol == 8211) {
            printText("--");
        } else {
            printText(String.valueOf(symbol));
        }
    }

    @Override
    public void beginTable(Map<String, String> parameters)
    {
        printEmptyLine();
        this.tableCells.push(new ArrayList<>());
        this.tableHeadRowsCount.push(0);
    }

    @Override
    public void endTable(Map<String, String> parameters)
    {
        // Display the full table

        // First, find the max cell size per row

        List<Integer> maxColumnSizes = new ArrayList<>();
        // Find the max number of columns
        int maxColumns = -1;
        for (List<String> columnCells : this.tableCells.peek()) {
            if (columnCells.size() > maxColumns) {
                maxColumns = columnCells.size();
            }
        }
        // Initialize max column sizes
        for (int i = 0; i < maxColumns; i++) {
            maxColumnSizes.add(-1);
        }
        // Set the max column sizes
        for (int rows = 0; rows < this.tableCells.peek().size(); rows++) {
            List<String> columnCells = this.tableCells.peek().get(rows);
            for (int i = 0; i < columnCells.size(); i++) {
                int currentCellSize = columnCells.get(i).length();
                // The minimum cell size is 3 to account for the header separator "---"
                if (currentCellSize < 3) {
                    currentCellSize = 3;
                }
                if (currentCellSize > maxColumnSizes.get(i)) {
                    maxColumnSizes.set(i, currentCellSize);
                }
            }
        }

        // First, print header rows
        if (this.tableHeadRowsCount.peek() > 0) {
            for (int i = 0; i < this.tableHeadRowsCount.peek(); i++) {
                List<String> columnCells = this.tableCells.peek().get(i);
                printTableRow(columnCells, ' ', true, maxColumnSizes);
            }
            print("\n");
        }
        printTableRow(this.tableCells.peek().get(0), '-', false, maxColumnSizes);
        print("\n");

        // Now print the body rows
        for (int i = this.tableHeadRowsCount.peek(); i < this.tableCells.peek().size(); i++) {
            List<String> columnCells = this.tableCells.peek().get(i);
            printTableRow(columnCells, ' ', true, maxColumnSizes);
            if (i < this.tableCells.peek().size() - 1) {
                print("\n");
            }
        }

        this.tableCells.pop();
        this.tableHeadRowsCount.pop();
    }

    private void printTableRow(List<String> columnCells, char separator, boolean printCellText,
        List<Integer> maxColumnSizes)
    {
        print("|");
        for (int j = 0; j < columnCells.size(); j++) {
            String cell = columnCells.get(j);
            int spaceSize = (maxColumnSizes.get(j) - cell.length()) / 2;
            print(" ");
            print(StringUtils.repeat(separator, spaceSize));
            if (printCellText) {
                print(cell);
            } else {
                print(StringUtils.repeat(separator, cell.length()));
            }
            print(
                StringUtils.repeat(separator, maxColumnSizes.get(j) - cell.length() - spaceSize));
            print(" ");
            print("|");
        }
    }

    @Override
    public void beginTableCell(Map<String, String> parameters)
    {
        pushPrinter(createInlineMarkdownPrinter());
    }

    @Override
    public void endTableCell(Map<String, String> parameters)
    {
        MarkdownEscapeWikiPrinter headingPrinter = getMarkdownPrinter();
        headingPrinter.flush();
        String cellText = headingPrinter.toString();
        popPrinter();
        List<String> cellsInLastRow = this.tableCells.peek().get(this.tableCells.peek().size() - 1);
        cellsInLastRow.add(cellText);
    }

    @Override
    public void beginTableHeadCell(Map<String, String> parameters)
    {
        if (this.isOnFirstHeadCellInTableRow.peek()) {
            this.isOnFirstHeadCellInTableRow.pop();
            this.isOnFirstHeadCellInTableRow.push(false);
            this.tableHeadRowsCount.push(this.tableHeadRowsCount.pop() + 1);
        }
        beginTableCell(parameters);
    }

    @Override
    public void endTableHeadCell(Map<String, String> parameters)
    {
        endTableCell(parameters);
    }

    @Override
    public void beginTableRow(Map<String, String> parameters)
    {
        this.tableCells.peek().add(new ArrayList<>());
        this.isOnFirstHeadCellInTableRow.push(true);
    }

    @Override
    public void endTableRow(Map<String, String> parameters)
    {
        this.isOnFirstHeadCellInTableRow.pop();
    }

    @Override
    public void onRawText(String text, Syntax syntax)
    {
        // Handle some HTML construct that have no representation in XWiki in special ways. For example, the use of
        // abbreviations
        if (!handleAbbreviations(text)) {
            if (!getBlockState().isInLine()) {
                printEmptyLine();
            }
            print(text);
        }
    }

    private boolean handleAbbreviations(String text)
    {
        boolean isHandled = false;
        if (text.startsWith("<abbr ")) {
            try {
                SAXBuilder builder = new SAXBuilder();
                // Disable entity processing since we don't need that feature, and it could lead to an XXE attack.
                builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
                builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                builder.setExpandEntities(false);
                Document document = builder.build(new StringReader(text));
                Element abbrElement = document.getRootElement();
                if (abbrElement.getAttributes().size() == 1) {
                    String key = abbrElement.getText();
                    String value = abbrElement.getAttributeValue("title");
                    this.abbreviations.peek().put(key, value);
                    print(key);
                    isHandled = true;
                }
            } catch (Exception e) {
                // Failure to parse HTML, send HTML as is!
            }
        }
        return isHandled;
    }

    public MarkdownEscapeWikiPrinter createMarkdownPrinter(WikiPrinter printer)
    {
        return new MarkdownEscapeWikiPrinter(printer, (XWikiSyntaxListenerChain) getListenerChain());
    }

    /**
     * @return a new printer for some content that is located in the middle of a line (e.g. heading or table cell
     *     content), where the syntaxes recognized only at the beginning of lines don't apply
     */
    private MarkdownEscapeWikiPrinter createInlineMarkdownPrinter()
    {
        MarkdownEscapeWikiPrinter printer = createMarkdownPrinter(new DefaultWikiPrinter());
        printer.setOnNewLine(false);
        return printer;
    }

    /**
     * Indicate that what follows is at the beginning of the content of a block (e.g. after a list item marker), where
     * the syntaxes recognized at the beginning of lines apply.
     */
    private void markBlockContentStart()
    {
        getMarkdownPrinter().setOnNewLine(true);
    }

    @Override
    public void setPrinter(WikiPrinter printer)
    {
        // If the printer is already a Markdown Syntax Escape printer don't wrap it again. This case happens when
        // the createChainingListenerInstance() method is called, ie when this renderer's state is stacked
        // (for example when a Group event is being handled).
        if (printer instanceof MarkdownEscapeWikiPrinter) {
            super.setPrinter(printer);
        } else {
            super.setPrinter(createMarkdownPrinter(printer));
        }
    }

    /**
     * Allows exposing the additional methods of {@link MarkdownEscapeWikiPrinter}, namely the ability to delay
     * printing some text and the ability to escape characters that would otherwise have a meaning in Markdown syntax.
     */
    public MarkdownEscapeWikiPrinter getMarkdownPrinter()
    {
        return (MarkdownEscapeWikiPrinter) super.getPrinter();
    }

    @Override
    protected void popPrinter()
    {
        // Ensure that any not printed characters are flushed
        getMarkdownPrinter().flush();

        super.popPrinter();
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        handleCodeMacro(id, parameters, content, isInline);
    }

    protected boolean handleCodeMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        boolean isHandled = true;

        // Special handling to support Markdown code
        if (id.equals("code")) {
            if (isInline) {
                // Inline should generate back ticks
                print(formatCodeSpan(StringUtils.defaultString(content)));
            } else {
                // Standalone should generate fenced blocks or triple backticks with language if a language was
                // specified (and no language if language is none).
                String language = parameters.get("language");
                if (language != null && !"none".equals(language)) {
                    println(TRIPLE_BACKTICK + language);
                    print(content);
                    print("\n" + TRIPLE_BACKTICK);
                } else {
                    GroupState groupState = this.groupStates.peek();
                    boolean isFirstGroupElement = groupState != null && !groupState.isFlattened
                        && getMarkdownPrinter().toString().isEmpty() && getMarkdownPrinter().getBuffer().length() == 0;
                    String lines[] = content.split("\\r?\\n");
                    int spaces = isInList() ? (getListDepth() - 1) * 4 + 2 : 0;
                    for (int i = 0; i < lines.length; i++) {
                        // If we're in a list, for each list item level, we add 4 + 2 more spaces, see spec at
                        // https://spec.commonmark.org/0.28/#list-items
                        print(String.format("    %s%s", StringUtils.repeat(' ', spaces), lines[i]));
                        if (i < lines.length - 1) {
                            print("\n");
                        }
                    }
                    if (isFirstGroupElement) {
                        getMarkdownPrinter().flush();
                        groupState.indentedCodeEndIndex = getMarkdownPrinter().toString().length();
                    }
                }
            }
        } else {
            isHandled = false;
        }

        return isHandled;
    }

    /**
     * @param code the content of the code span
     * @return the code span syntax for the passed content, using backtick strings that can't be found in the content
     *     (so that the content can't end the code span) and keeping it on a single line (so that block syntaxes are
     *     not recognized inside it)
     */
    private String formatCodeSpan(String code)
    {
        String singleLineCode = code.replaceAll("\\r?\\n", " ");
        int longestBacktickString = 0;
        int backtickString = 0;
        for (int i = 0; i < singleLineCode.length(); i++) {
            backtickString = singleLineCode.charAt(i) == '`' ? backtickString + 1 : 0;
            longestBacktickString = Math.max(longestBacktickString, backtickString);
        }
        String delimiter = StringUtils.repeat(BACKTICK, longestBacktickString + 1);
        // A space is needed between the delimiter and a backtick located at the beginning or end of the content.
        String padding =
            singleLineCode.startsWith(BACKTICK) || singleLineCode.endsWith(BACKTICK) ? " " : StringUtils.EMPTY;
        return delimiter + padding + singleLineCode + padding + delimiter;
    }

    protected void print(String text)
    {
        if (!text.isEmpty()) {
            printPendingSeparator(text);
            this.isAtContainerStart = false;
        }
        getPrinter().print(text);
    }

    /**
     * Print some text, which is escaped so that it's not interpreted as Markdown syntax (except inside monospace
     * content, which is verbatim).
     *
     * @param text the text to print
     * @since 8.10.0
     */
    protected void printText(String text)
    {
        if (this.monospaceDepth > 0) {
            print(text);
        } else {
            if (!text.isEmpty()) {
                printPendingSeparator(text);
                this.isAtContainerStart = false;
            }
            // The text is escaped when the printer is flushed, i.e. when some markup is printed after it.
            getMarkdownPrinter().printDelayed(text);
        }
    }

    /**
     * Print the separator that is needed between a Group and the content that follows it, if any.
     *
     * @param text the text that is going to be printed after the separator
     * @since 8.10.0
     */
    protected void printPendingSeparator(String text)
    {
        if (this.pendingSeparator != null) {
            String separator = this.pendingSeparator;
            this.pendingSeparator = null;
            // Nothing to do if the text starts with an empty line already.
            if (!text.startsWith(EMPTY_LINE)) {
                if (text.startsWith(NEW_LINE)) {
                    // For example a nested list: the text contains the new line and the line prefix.
                    getPrinter().print(NEW_LINE);
                } else {
                    getPrinter().print(EMPTY_LINE + separator);
                    markBlockContentStart();
                }
            }
        }
    }

    protected void println(String text)
    {
        print(text + "\n");
    }

    private XWikiSyntaxListenerChain getXWikiSyntaxListenerChain()
    {
        return (XWikiSyntaxListenerChain) getListenerChain();
    }

    protected void printEmptyLine()
    {
        if (this.isFirstElementRendered) {
            print("\n\n");
        } else {
            this.isFirstElementRendered = true;
        }
    }

    /**
     * Separate a block from the previous one, keeping it inside the container (e.g. list item, definition
     * description) in which it's located.
     *
     * @param linePrefix the line prefix of the container in which the block is located
     */
    private void printBlockSeparator(String linePrefix)
    {
        if (this.isAtContainerStart) {
            // The block is the first content of the container: it follows the container marker (e.g. "* # Title").
            this.isFirstElementRendered = true;
        } else if (this.isFirstElementRendered) {
            print(EMPTY_LINE + linePrefix);
            // The block content starts after the container line prefix.
            markBlockContentStart();
        } else {
            this.isFirstElementRendered = true;
        }
    }

    private BlockStateChainingListener getBlockState()
    {
        return getXWikiSyntaxListenerChain().getBlockStateChainingListener();
    }
}
