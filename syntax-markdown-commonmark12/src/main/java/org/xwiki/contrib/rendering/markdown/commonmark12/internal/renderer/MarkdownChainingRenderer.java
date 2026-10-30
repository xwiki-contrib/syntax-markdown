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
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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

    private static final String SPACE = " ";

    private static final String EMPTY_LINE = NEW_LINE + NEW_LINE;

    private static final String LINE_ENDINGS = "\r\n|\r|\n";

    private static final Pattern HEADING_CLOSING_SEQUENCE = Pattern.compile("(^|\\p{Blank})(#+\\p{Blank}*)$");

    /**
     * A line break HTML tag, which is parsed back as a new line, used where a Markdown hard line break can't be used.
     */
    private static final String LINE_BREAK_TAG = "<br/>";

    private static final Pattern HTML_ATTRIBUTE_NAME = Pattern.compile("[^\\s\"'>/=\\p{Cntrl}]+");

    /**
     * Line breaks (and the spaces around them) in a table cell, which would otherwise end the table row. Only character
     * classes are repeated, to avoid a stack overflow on large content.
     */
    private static final Pattern TABLE_CELL_LINE_BREAKS = Pattern.compile("[ \\t]*[\\r\\n][ \\t\\r\\n]*");

    /**
     * Several line breaks, i.e. at least one empty line (e.g. between two paragraphs).
     */
    private static final Pattern TABLE_CELL_EMPTY_LINE =
        Pattern.compile("(\\r\\n|\\r(?!\\n)|\\n)[ \\t]*(\\r\\n|\\r(?!\\n)|\\n)");

    private static final String TABLE_CELL_LINE_BREAK = "<br/>";

    private static final Pattern TABLE_CELL_NEW_LINES = Pattern.compile(LINE_ENDINGS);

    private static final String TABLE_ALIGN_PARAMETER = "align";

    private static final String TABLE_ALIGN_LEFT = "left";

    private static final String TABLE_ALIGN_RIGHT = "right";

    private static final String TABLE_ALIGN_CENTER = "center";

    /**
     * The minimum cell size, to account for the header separator "---".
     */
    private static final int TABLE_MIN_CELL_SIZE = 3;

    /**
     * The maximum size up to which the table cells are padded so that the columns are aligned, which bounds the size
     * of the padding.
     */
    private static final int TABLE_MAX_ALIGNED_CELL_SIZE = 50;

    private static final String TABLE_COLSPAN_PARAMETER = "colspan";

    /**
     * The maximum column span of a cell, as defined by the HTML specification. It avoids generating a huge table for
     * an invalid column span.
     */
    private static final int TABLE_MAX_COLSPAN = 1000;

    private static final String TABLE_ROWSPAN_PARAMETER = "rowspan";

    /**
     * A table cell to render, collected until the end of the table since the size of the columns must be known before
     * printing the table.
     */
    private static final class TableCellState
    {
        private final String text;

        /**
         * The cell alignment, {@code null} when the cell has no alignment.
         */
        private final String alignment;

        /**
         * The number of columns the cell spans.
         */
        private final int colspan;

        /**
         * The number of rows the cell spans.
         */
        private final int rowspan;

        /**
         * Whether the cell is a head cell.
         */
        private final boolean isHead;

        /**
         * The parameters of the cell.
         */
        private final Map<String, String> parameters;

        TableCellState(String text, String alignment, int colspan)
        {
            this(text, alignment, colspan, 1, false, Collections.emptyMap());
        }

        TableCellState(String text, String alignment, int colspan, int rowspan, boolean isHead,
            Map<String, String> parameters)
        {
            this.text = text;
            this.alignment = alignment;
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.isHead = isHead;
            this.parameters = parameters;
        }
    }

    /**
     * A table cell spanning several rows, whose columns must be kept free in the following rows that it spans.
     */
    private static final class TableRowSpanState
    {
        /**
         * The number of columns the cell spans.
         */
        private final int colspan;

        /**
         * The index of the last row the cell spans.
         */
        private final int lastRow;

        TableRowSpanState(int colspan, int lastRow)
        {
            this.colspan = colspan;
            this.lastRow = lastRow;
        }
    }

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

        private boolean isInDefinitionDescription;

        /**
         * The printer in which the Group content is rendered.
         */
        private MarkdownEscapeWikiPrinter printer;

        /**
         * Whether the first element of the Group is a paragraph.
         */
        private boolean isFirstElementParagraph;

        /**
         * Length of the rendered Group content just after rendering its first element when this element is a
         * paragraph, -1 otherwise.
         */
        private int paragraphEndIndex = -1;

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

        /**
         * Whether the Group directly follows an indented code block or a list (in which case an indented code block
         * can't be used).
         */
        private boolean isAfterIndentedCodeBlock;
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

    /**
     * For each row of each table, its cells.
     */
    private Stack<List<List<TableCellState>>> tableCells = new Stack<>();

    /**
     * For each row of each table, whether the row is made only of head cells.
     */
    private Stack<List<Boolean>> tableHeadRows = new Stack<>();

    /**
     * For each table, whether one of its cells is being rendered.
     */
    private Stack<Boolean> tableCellStarted = new Stack<>();

    /**
     * For each table, whether it contains content that can only be written using the wiki syntax (e.g. macros, wiki
     * links and images), which isn't supported inside inline HTML.
     */
    private Stack<Boolean> tableWikiSyntax = new Stack<>();

    /**
     * For each table, whether an element was rendered before it (and thus whether an empty line must separate them).
     */
    private Stack<Boolean> tableAfterRenderedElement = new Stack<>();

    /**
     * For each table, the separator that was pending before it (e.g. after a Group or a heading), which must be
     * printed before the table and not inside its first cell.
     */
    private Stack<String> tablePendingSeparators = new Stack<>();

    /**
     * For each table, whether it's the first content of its container (e.g. list item).
     */
    private Stack<Boolean> tableAtContainerStart = new Stack<>();

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
     * Whether the current events are located in a definition term (which must fit on a single line).
     */
    private boolean isInDefinitionTerm;

    /**
     * Line prefix of the container of the last rendered Group, set until something is printed after it, so that
     * any content following the Group in the same container is separated from it by an empty line (otherwise it
     * would be considered part of the HTML block of the Group closing tag).
     */
    private String pendingSeparator;

    private int monospaceDepth;

    /**
     * The printer in which the last indented code block was printed.
     */
    private MarkdownEscapeWikiPrinter indentedCodeBlockPrinter;

    /**
     * Length of the content of {@link #indentedCodeBlockPrinter} just after the last indented code block.
     */
    private int indentedCodeBlockEndIndex;

    /**
     * The printer in which the last list (or definition list) was printed.
     */
    private MarkdownEscapeWikiPrinter listEndPrinter;

    /**
     * Length of the content of {@link #listEndPrinter} just after the last list (or definition list).
     */
    private int listEndIndex;

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
                    printCodeSpan(code);
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
        if (isInTableCell()) {
            state.isFlattened = true;
            return;
        }

        state.isInListItem = isInList();
        state.isInDefinitionDescription = !state.isInListItem && getDefinitionListDepth() > 0
            && !this.isInDefinitionTerm;
        state.isTransparent = parameters.isEmpty() && isDirectlyInQuotation();
        state.isAfterIndentedCodeBlock = isAfterIndentedCodeBlock() || isAfterList();

        // The Group content is an independent document which is rendered separately so that it can be wrapped.
        beginBufferedContainer(state);
        state.printer = getMarkdownPrinter();
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

        if (parameters.isEmpty() && state.isInListItem && state.indentedCodeEndIndex == content.length()
            && !state.isAfterIndentedCodeBlock) {
            // A parameterless Group whose only content is an indented code block, located in a list item, is the
            // Group that the Markdown parser creates around indented code blocks in list items (since a code macro
            // is standalone), and thus doesn't need to be rendered. It's rendered when it directly follows another
            // indented code block or a list though, since the code block would be merged with them otherwise. When
            // it's the first content of the list item, the code block directly follows the list item marker (a list
            // item starting with an empty line would be empty).
            if (state.wasAtContainerStart) {
                this.isFirstElementRendered = true;
            } else {
                printEmptyLine();
            }
            print(prefixLines(content, state.linePrefix, state.wasAtContainerStart));
            markIndentedCodeBlockEnd();
            return;
        }

        printGroupSeparator(state);

        if (parameters.isEmpty() && (state.isInListItem || state.isInDefinitionDescription)
            && !state.wasAtContainerStart && state.paragraphEndIndex == content.length()) {
            // A parameterless Group whose only content is a paragraph, located in a list item (or definition
            // description) after some other content, is the Group that the Markdown parser creates around the
            // paragraphs following the first content of a list item, and thus doesn't need to be rendered.
            print(prefixLines(content, state.linePrefix, true));
            this.pendingSeparator = state.linePrefix;
            return;
        }

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
        // New lines are escaped too since an empty line would end the HTML block of the div opening tag. A parameter
        // without value (e.g. "(% name %)" in XWiki Syntax) has a null value.
        return StringUtils.defaultString(value).replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
            .replace(">", "&gt;").replace(NEW_LINE, "&#10;").replace("\r", "&#13;");
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
        GroupState groupState = this.groupStates.peek();
        if (groupState != null && !groupState.isFlattened && isNothingPrintedIn(groupState.printer)) {
            groupState.isFirstElementParagraph = true;
        }
        printBlockSeparator(getContainerLinePrefix());
    }

    @Override
    public void endParagraph(Map<String, String> parameters)
    {
        GroupState groupState = this.groupStates.peek();
        if (groupState != null && groupState.isFirstElementParagraph && groupState.paragraphEndIndex < 0
            && getMarkdownPrinter() == groupState.printer) {
            // Remember where the first paragraph of the Group ends, to know if it's the only content of the Group.
            groupState.printer.flush();
            groupState.paragraphEndIndex = groupState.printer.toString().length();
        }
    }

    private boolean isNothingPrintedIn(MarkdownEscapeWikiPrinter printer)
    {
        return getMarkdownPrinter() == printer && printer.toString().isEmpty() && printer.getBuffer().length() == 0;
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
        if (!isInTableCell()) {
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
        markListEnd();
    }

    @Override
    public void endDefinitionList(Map<String, String> parameters)
    {
        super.endDefinitionList(parameters);
        markListEnd();
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
        this.isInDefinitionTerm = true;
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
        this.isInDefinitionTerm = false;
    }

    @Override
    public void beginQuotation(Map<String, String> parameters)
    {
        QuotationState state = new QuotationState();
        // Markdown tables can't contain blocks, thus only the quotation content is rendered.
        if (isInTableCell()) {
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
        MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();
        if (getBlockState().isInHeader() || this.isInDefinitionTerm || getBlockState().isInLink()) {
            // Headings and definition terms must fit on a single line, and a blank line would end the link label.
            print(LINE_BREAK_TAG);
        } else if (!isInTableCell() && printer.isOnNewLine()
            && StringUtils.isBlank(printer.getBuffer()))
        {
            // The line is empty or contains only spaces (e.g. consecutive new lines or new line at the beginning of a
            // paragraph): a line containing only spaces and the 2 spaces of a hard line break would be a blank line,
            // which would end the paragraph.
            // Use the backslash hard line break instead. Note that the new lines of a table cell are converted to line
            // break tags when the table cell ends.
            print("\\\n");
        } else {
            print("  ");
            print(NEW_LINE);
        }
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
        // The separating empty line is printed when the table ends, only if the table is not empty. Remember whether
        // it's needed, before the cell contents modify the state.
        this.tableAfterRenderedElement.push(this.isFirstElementRendered);
        this.isFirstElementRendered = true;
        this.tablePendingSeparators.push(this.pendingSeparator);
        this.pendingSeparator = null;
        this.tableAtContainerStart.push(this.isAtContainerStart);
        this.tableCells.push(new ArrayList<>());
        this.tableHeadRows.push(new ArrayList<>());
        this.tableCellStarted.push(false);
        this.tableWikiSyntax.push(false);
    }

    @Override
    public void endTable(Map<String, String> parameters)
    {
        List<List<TableCellState>> rows = this.tableCells.pop();
        List<Boolean> headRows = this.tableHeadRows.pop();
        this.tableCellStarted.pop();
        boolean hasWikiSyntax = this.tableWikiSyntax.pop();
        this.isFirstElementRendered = this.tableAfterRenderedElement.pop();
        this.pendingSeparator = this.tablePendingSeparators.pop();
        this.isAtContainerStart = this.tableAtContainerStart.pop();

        if (isInTableCell()) {
            // Markdown tables can't be nested since a table row must be on a single line. The table is written as
            // inline HTML, unless it contains wiki syntax: it wouldn't be parsed inside inline HTML (the table being
            // read back as an HTML macro), thus its rows are written as text instead.
            if (hasWikiSyntax) {
                printTextTable(rows);
            } else {
                printHTMLTable(rows, parameters);
            }
            return;
        }

        fillTableRowSpannedColumns(rows);
        // Find the max number of columns, taking into account the cells spanning several columns.
        int maxColumns = removeTableSpannedOnlyColumns(rows);
        // A table without any cell cannot be represented in Markdown.
        if (maxColumns == 0) {
            return;
        }
        // The table rows must be indented to stay inside the container (e.g. list item) in which the table is located.
        String linePrefix = getContainerLinePrefix();
        printBlockSeparator(linePrefix);

        // A Markdown table must start with a single header row, followed by the delimiter row. Only the first row can
        // be the header row, and only if it's made of head cells only. Otherwise an empty header row is printed (which
        // the parser ignores). Head cells located in the other rows are printed as normal cells since Markdown cannot
        // express them.
        List<TableCellState> headerRow;
        List<List<TableCellState>> bodyRows;
        if (headRows.get(0)) {
            headerRow = rows.get(0);
            bodyRows = rows.subList(1, rows.size());
        } else {
            headerRow = new ArrayList<>();
            bodyRows = rows;
        }
        // The header row defines the number of columns of the table (the extra cells of the other rows are dropped by
        // the parser), thus it's padded with empty cells. The body rows having less cells are not padded since the
        // parser accepts them as they are: padding them would make the output size quadratic (e.g. a row of many
        // cells followed by many rows of a single cell). A body row without any cell gets a single empty cell though,
        // since it couldn't be written otherwise.
        for (int i = getTableRowColumnCount(headerRow); i < maxColumns; i++) {
            headerRow.add(new TableCellState("", null, 1));
        }
        for (List<TableCellState> rowCells : bodyRows) {
            if (rowCells.isEmpty()) {
                rowCells.add(new TableCellState("", null, 1));
            }
        }

        List<Integer> columnSizes = getTableColumnSizes(rows, maxColumns);
        List<String> columnAlignments = getTableColumnAlignments(rows, maxColumns);

        printTableRow(headerRow, columnAlignments, columnSizes);
        print(NEW_LINE + linePrefix);
        printTableDelimiterRow(columnAlignments, columnSizes);
        for (List<TableCellState> rowCells : bodyRows) {
            print(NEW_LINE + linePrefix);
            printTableRow(rowCells, columnAlignments, columnSizes);
        }
    }

    /**
     * Print a table located in a table cell as inline HTML on a single line, since Markdown tables can't be nested.
     * The cell contents are kept as Markdown since inline HTML can contain Markdown inline content, and HTML supports
     * the cell parameters (e.g. row spans).
     *
     * @param rows the cells of each row of the table
     * @param parameters the table parameters
     */
    private void printHTMLTable(List<List<TableCellState>> rows, Map<String, String> parameters)
    {
        // A table without any cell is not rendered, as in Markdown.
        if (rows.stream().allMatch(List::isEmpty)) {
            return;
        }

        printBlockSeparator(getContainerLinePrefix());
        StringBuilder builder = new StringBuilder();
        appendHTMLStartTag(builder, "table", parameters);
        for (List<TableCellState> rowCells : rows) {
            builder.append("<tr>");
            for (TableCellState cell : rowCells) {
                String tagName = cell.isHead ? "th" : "td";
                appendHTMLStartTag(builder, tagName, cell.parameters);
                builder.append(cell.text).append("</").append(tagName).append('>');
            }
            builder.append("</tr>");
        }
        builder.append("</table>");
        print(builder.toString());
    }

    /**
     * Print a table located in a table cell as text, each row on its own line (which is rendered as a line break) and
     * its cells separated by pipes.
     *
     * @param rows the cells of each row of the table
     */
    private void printTextTable(List<List<TableCellState>> rows)
    {
        List<String> rowTexts = new ArrayList<>(rows.size());
        for (List<TableCellState> rowCells : rows) {
            if (!rowCells.isEmpty()) {
                rowTexts.add(rowCells.stream().map(cell -> cell.text).collect(Collectors.joining(" \\| ")));
            }
        }
        // A table without any cell is not rendered, as in Markdown.
        if (!rowTexts.isEmpty()) {
            printBlockSeparator(getContainerLinePrefix());
            print(String.join(NEW_LINE, rowTexts));
        }
    }

    /**
     * Indicate that content that can only be written using the wiki syntax (e.g. macros, wiki links and images) is
     * being rendered, so that the tables containing it are not written as inline HTML when located in a table cell.
     */
    protected void onWikiSyntax()
    {
        Collections.fill(this.tableWikiSyntax, true);
    }

    private static void appendHTMLStartTag(StringBuilder builder, String tagName, Map<String, String> parameters)
    {
        builder.append('<').append(tagName);
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (isValidGroupParameterName(parameter.getKey())) {
                builder.append(' ').append(parameter.getKey()).append("=\"")
                    .append(escapeHTMLAttributeValue(parameter.getValue())).append('"');
            }
        }
        builder.append('>');
    }

    /**
     * Markdown doesn't support row spans: the columns of a cell spanning several rows are filled with an empty cell in
     * the following rows it spans (spanning the same columns), otherwise the cells of these rows would be shifted to
     * the left. The columns spanned at the end of a row don't need to be filled. The empty cells added this way never
     * span more columns than the cells of the table, so that a few cells spanning many rows can't generate a huge
     * table: the remaining row spans are ignored.
     *
     * @param rows the cells of each row of the table, to which the empty cells are added
     */
    private void fillTableRowSpannedColumns(List<List<TableCellState>> rows)
    {
        int remainingColumns = 0;
        for (List<TableCellState> rowCells : rows) {
            remainingColumns += getTableRowColumnCount(rowCells);
        }

        // The cells spanning several rows, indexed by their first column. Only the last cell starting in a column is
        // kept, the previous ones having ended.
        Map<Integer, TableRowSpanState> rowSpans = new HashMap<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<TableCellState> rowCells = rows.get(rowIndex);
            List<TableCellState> filledRowCells = new ArrayList<>(rowCells.size());
            int column = 0;
            for (TableCellState cell : rowCells) {
                TableRowSpanState rowSpan = rowSpans.get(column);
                while (rowSpan != null && rowSpan.lastRow >= rowIndex && rowSpan.colspan <= remainingColumns) {
                    filledRowCells.add(new TableCellState("", null, rowSpan.colspan));
                    remainingColumns -= rowSpan.colspan;
                    column += rowSpan.colspan;
                    rowSpan = rowSpans.get(column);
                }
                filledRowCells.add(cell);
                if (cell.rowspan > 1) {
                    rowSpans.put(column, new TableRowSpanState(cell.colspan, rowIndex + cell.rowspan - 1));
                }
                column += cell.colspan;
            }
            rows.set(rowIndex, filledRowCells);
        }
    }

    /**
     * Remove the columns in which no cell starts, i.e. the columns that exist only because some cells span over them
     * (e.g. a cell spanning more columns than the other rows have). Such columns hold no content, and keeping them
     * would allow a few cells with a large column span to generate a huge table (every row being padded up to the
     * number of columns). The column spans are reduced accordingly. This way the table never has more columns than
     * cells.
     *
     * @param rows the cells of each row of the table, modified to remove the columns in which no cell starts
     * @return the number of columns of the table
     */
    private int removeTableSpannedOnlyColumns(List<List<TableCellState>> rows)
    {
        SortedSet<Integer> startColumnSet = new TreeSet<>();
        for (List<TableCellState> rowCells : rows) {
            int column = 0;
            for (TableCellState cell : rowCells) {
                startColumnSet.add(column);
                column += cell.colspan;
            }
        }
        List<Integer> startColumns = new ArrayList<>(startColumnSet);
        for (List<TableCellState> rowCells : rows) {
            int column = 0;
            for (int i = 0; i < rowCells.size(); i++) {
                TableCellState cell = rowCells.get(i);
                int nextColumn = column + cell.colspan;
                // The cell now spans from the index of its start column to the index of the first start column after
                // it.
                int newColumn = Collections.binarySearch(startColumns, column);
                int newNextColumn = Collections.binarySearch(startColumns, nextColumn);
                if (newNextColumn < 0) {
                    newNextColumn = -newNextColumn - 1;
                }
                int newColspan = newNextColumn - newColumn;
                if (newColspan != cell.colspan) {
                    rowCells.set(i, new TableCellState(cell.text, cell.alignment, newColspan));
                }
                column = nextColumn;
            }
        }
        return startColumns.size();
    }

    private int getTableRowColumnCount(List<TableCellState> rowCells)
    {
        int columnCount = 0;
        for (TableCellState cell : rowCells) {
            columnCount += cell.colspan;
        }
        return columnCount;
    }

    /**
     * The size of a column is the size of its largest cell. A cell spanning several columns is printed over the space
     * of these columns (including the space of the pipes separating them), and thus doesn't make a single column
     * larger: when it's larger than this space, the missing size is distributed over the columns it spans.
     *
     * @param rows the cells of each row of the table
     * @param maxColumns the number of columns of the table
     * @return the size of each column
     */
    private List<Integer> getTableColumnSizes(List<List<TableCellState>> rows, int maxColumns)
    {
        List<Integer> columnSizes = new ArrayList<>(Collections.nCopies(maxColumns, TABLE_MIN_CELL_SIZE));
        for (List<TableCellState> rowCells : rows) {
            int column = 0;
            for (TableCellState cell : rowCells) {
                if (cell.colspan == 1) {
                    columnSizes.set(column, Math.max(columnSizes.get(column), cell.text.length()));
                }
                column += cell.colspan;
            }
        }
        for (List<TableCellState> rowCells : rows) {
            int column = 0;
            for (TableCellState cell : rowCells) {
                int missingSize = cell.text.length() - getTableCellSize(columnSizes, column, cell.colspan);
                for (int i = 0; i < cell.colspan && missingSize > 0; i++) {
                    int remainingColumns = cell.colspan - i;
                    int extraSize = (missingSize + remainingColumns - 1) / remainingColumns;
                    columnSizes.set(column + i, columnSizes.get(column + i) + extraSize);
                    missingSize -= extraSize;
                }
                column += cell.colspan;
            }
        }
        return columnSizes;
    }

    /**
     * @param columnSizes the size of each column
     * @param column the first column of the cell
     * @param colspan the number of columns spanned by the cell
     * @return the size available to the cell content, i.e. the size of the columns it spans, plus the space taken by
     *     the separators of these columns in a row without span (" | ") minus the extra pipes closing the cell
     */
    private int getTableCellSize(List<Integer> columnSizes, int column, int colspan)
    {
        int size = 2 * (colspan - 1);
        for (int i = column; i < column + colspan; i++) {
            size += columnSizes.get(i);
        }
        return size;
    }

    /**
     * Markdown only supports an alignment per column (set in the delimiter row), while the XWiki model has an
     * alignment per cell. A column gets the alignment specified by its cells, ignoring the cells without alignment
     * (e.g. it's common to align only the body cells, or only the header cell). When some cells specify different
     * alignments, there's no right choice and thus the column gets no alignment. A cell spanning several columns
     * specifies the alignment of its first column only, since that's the alignment it gets when parsed back.
     *
     * @param rows the cells of each row of the table
     * @param maxColumns the number of columns of the table
     * @return the alignment of each column ({@code null} when the column has no alignment)
     */
    private List<String> getTableColumnAlignments(List<List<TableCellState>> rows, int maxColumns)
    {
        List<String> columnAlignments = new ArrayList<>(Collections.nCopies(maxColumns, null));
        List<Boolean> conflicts = new ArrayList<>(Collections.nCopies(maxColumns, false));
        for (List<TableCellState> rowCells : rows) {
            int column = 0;
            for (TableCellState cell : rowCells) {
                if (cell.alignment != null) {
                    String columnAlignment = columnAlignments.get(column);
                    if (columnAlignment == null) {
                        columnAlignments.set(column, cell.alignment);
                    } else if (!columnAlignment.equals(cell.alignment)) {
                        conflicts.set(column, true);
                    }
                }
                column += cell.colspan;
            }
        }
        for (int i = 0; i < maxColumns; i++) {
            if (conflicts.get(i)) {
                columnAlignments.set(i, null);
            }
        }
        return columnAlignments;
    }

    private void printTableRow(List<TableCellState> rowCells, List<String> columnAlignments,
        List<Integer> columnSizes)
    {
        print("|");
        int column = 0;
        for (TableCellState cell : rowCells) {
            // The cells are aligned only up to a maximum size, otherwise a single large cell would make all the cells
            // of its column (or of the columns it spans) padded to its size, making the output size quadratic.
            int cellSize = getTableCellSize(columnSizes, column, cell.colspan);
            int paddingSize = cellSize <= TABLE_MAX_ALIGNED_CELL_SIZE ? cellSize - cell.text.length() : 0;
            // Pad the cell text according to the alignment of its (first) column, centering it by default.
            int leftPaddingSize;
            String alignment = columnAlignments.get(column);
            if (TABLE_ALIGN_LEFT.equals(alignment)) {
                leftPaddingSize = 0;
            } else if (TABLE_ALIGN_RIGHT.equals(alignment)) {
                leftPaddingSize = paddingSize;
            } else {
                leftPaddingSize = paddingSize / 2;
            }
            print(" ");
            print(StringUtils.repeat(' ', leftPaddingSize));
            print(cell.text);
            print(StringUtils.repeat(' ', paddingSize - leftPaddingSize));
            print(" ");
            // A cell spanning several columns is closed by as many adjacent pipes (whitespace between them would
            // create empty cells instead).
            print(StringUtils.repeat('|', cell.colspan));
            column += cell.colspan;
        }
    }

    private void printTableDelimiterRow(List<String> columnAlignments, List<Integer> columnSizes)
    {
        print("|");
        for (int j = 0; j < columnAlignments.size(); j++) {
            String alignment = columnAlignments.get(j);
            // The column size is at least 3, which leaves room for at least one dash between colons. A column larger
            // than the maximum aligned cell size isn't aligned (see printTableRow).
            int columnSize = columnSizes.get(j);
            int dashCount = columnSize <= TABLE_MAX_ALIGNED_CELL_SIZE ? columnSize : TABLE_MIN_CELL_SIZE;
            boolean colonOnLeft = TABLE_ALIGN_LEFT.equals(alignment) || TABLE_ALIGN_CENTER.equals(alignment);
            boolean colonOnRight = TABLE_ALIGN_RIGHT.equals(alignment) || TABLE_ALIGN_CENTER.equals(alignment);
            print(" ");
            if (colonOnLeft) {
                print(":");
                dashCount--;
            }
            if (colonOnRight) {
                dashCount--;
            }
            print(StringUtils.repeat('-', dashCount));
            if (colonOnRight) {
                print(":");
            }
            print(" |");
        }
    }

    @Override
    public void beginTableCell(Map<String, String> parameters)
    {
        // The current row contains a normal cell and thus cannot be the header row.
        List<Boolean> headRows = this.tableHeadRows.peek();
        headRows.set(headRows.size() - 1, false);
        beginTableCellContent();
    }

    @Override
    public void endTableCell(Map<String, String> parameters)
    {
        endTableCellContent(parameters, false);
    }

    private void endTableCellContent(Map<String, String> parameters, boolean isHead)
    {
        this.tableCellStarted.set(this.tableCellStarted.size() - 1, false);
        MarkdownEscapeWikiPrinter cellPrinter = getMarkdownPrinter();
        cellPrinter.flush();
        String cellText = cellPrinter.toString();
        popPrinter();

        // A table row must be on a single line. Blocks separated by an empty line (e.g. paragraphs) are separated by
        // 2 line breaks to keep them visually separated. Note that the pipes of the text, which would be considered
        // as cell separators, have been escaped when printed (see createMarkdownPrinter).
        cellText = TABLE_CELL_LINE_BREAKS.matcher(cellText.trim()).replaceAll(
            result -> TABLE_CELL_EMPTY_LINE.matcher(result.group()).find()
                ? TABLE_CELL_LINE_BREAK + TABLE_CELL_LINE_BREAK : TABLE_CELL_LINE_BREAK);

        List<TableCellState> cellsInLastRow = this.tableCells.peek().get(this.tableCells.peek().size() - 1);
        cellsInLastRow.add(new TableCellState(cellText, getTableCellAlignment(parameters),
            getTableCellColspan(parameters), getTableCellRowspan(parameters), isHead, parameters));
    }

    private int getTableCellRowspan(Map<String, String> parameters)
    {
        // Invalid row spans (not a number, lower than 1) are ignored.
        return NumberUtils.toInt(StringUtils.trim(parameters.get(TABLE_ROWSPAN_PARAMETER)), 1);
    }

    private String getTableCellAlignment(Map<String, String> parameters)
    {
        String alignment = StringUtils.lowerCase(StringUtils.trim(parameters.get(TABLE_ALIGN_PARAMETER)));
        if (TABLE_ALIGN_LEFT.equals(alignment) || TABLE_ALIGN_RIGHT.equals(alignment)
            || TABLE_ALIGN_CENTER.equals(alignment))
        {
            return alignment;
        }
        // Other alignments (e.g. "justify") cannot be expressed in Markdown.
        return null;
    }

    private int getTableCellColspan(Map<String, String> parameters)
    {
        // Invalid column spans (not a number, lower than 1) are ignored.
        int colspan = NumberUtils.toInt(StringUtils.trim(parameters.get(TABLE_COLSPAN_PARAMETER)), 1);
        return Math.min(Math.max(colspan, 1), TABLE_MAX_COLSPAN);
    }

    @Override
    public void beginTableHeadCell(Map<String, String> parameters)
    {
        beginTableCellContent();
    }

    @Override
    public void endTableHeadCell(Map<String, String> parameters)
    {
        endTableCellContent(parameters, true);
    }

    private void beginTableCellContent()
    {
        this.tableCellStarted.set(this.tableCellStarted.size() - 1, true);
        pushPrinter(createInlineMarkdownPrinter());
    }

    @Override
    public void beginTableRow(Map<String, String> parameters)
    {
        this.tableCells.peek().add(new ArrayList<>());
        this.tableHeadRows.peek().add(true);
    }

    @Override
    public void endTableRow(Map<String, String> parameters)
    {
        // A row without any cell cannot be the header row.
        List<List<TableCellState>> rows = this.tableCells.peek();
        if (rows.get(rows.size() - 1).isEmpty()) {
            List<Boolean> headRows = this.tableHeadRows.peek();
            headRows.set(headRows.size() - 1, false);
        }
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
            String rawText = text;
            if (isInTableCell()) {
                // Markdown table cells can only contain inline content on a single line, thus the line endings are
                // replaced by spaces (they would be rendered as line breaks otherwise, even inside an HTML tag), and
                // the pipes located outside the HTML tags, which would be considered as cell separators, are escaped.
                rawText = getMarkdownPrinter().escapeRawPipes(TABLE_CELL_NEW_LINES.matcher(text).replaceAll(" "));
            }
            print(rawText);
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
                    print(getMarkdownPrinter().escapeRawPipes(key));
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
        MarkdownEscapeWikiPrinter markdownPrinter =
            new MarkdownEscapeWikiPrinter(printer, (XWikiSyntaxListenerChain) getListenerChain());
        // The pipes of the text located in a table cell (including in the content of the elements located in the
        // cell, e.g. a heading) must be escaped since they would be considered as cell separators. This is done
        // when printing the text, and not on the whole cell content, since the pipes located inside some syntax
        // (e.g. a link, a macro, an HTML tag or a code span) must not be escaped: the cells are split after parsing
        // the inline content of the row, and a backslash would be kept in their content.
        markdownPrinter.setInTableCell(isInTableCell());
        return markdownPrinter;
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
                printCodeSpan(StringUtils.defaultString(content));
            } else if (isInTableCell()) {
                // Markdown table cells can only contain inline content, thus, as for verbatim, we generate a code span
                // (the language is lost since Markdown has no syntax to specify the language of a code span).
                printTableCellCodeSpan(StringUtils.defaultString(content));
            } else {
                printCodeBlockSeparator();
                // Standalone should generate fenced blocks or triple backticks with language if a language was
                // specified (and no language if language is none).
                String language = parameters.get("language");
                if (language != null && !"none".equals(language)) {
                    printFencedCodeBlock(content, language);
                } else if (this.isAtContainerStart || isAfterIndentedCodeBlock() || isAfterList()) {
                    // An indented code block can't be used when:
                    // - it's the first content of a container (e.g. list item), since it would start after the
                    //   container marker
                    // - it directly follows another indented code block, since both would be merged when parsed back
                    //   (the empty line between them being part of the code)
                    // - it directly follows a list or a definition list, since it would be considered as part of the
                    //   last list item (and thus not as code)
                    // Thus we generate a fenced code block without info string instead (which is read back as a code
                    // macro without language).
                    printFencedCodeBlock(content, "");
                } else {
                    GroupState groupState = this.groupStates.peek();
                    boolean isFirstGroupElement = groupState != null && !groupState.isFlattened
                        && getMarkdownPrinter().toString().isEmpty() && getMarkdownPrinter().getBuffer().length() == 0;
                    String lines[] = content.split(LINE_ENDINGS);
                    // The code must be indented relatively to the container (e.g. list item, definition description)
                    // in which it's located.
                    String linePrefix = getContainerLinePrefix() + "    ";
                    for (int i = 0; i < lines.length; i++) {
                        print(linePrefix + lines[i]);
                        if (i < lines.length - 1) {
                            print("\n");
                        }
                    }
                    markIndentedCodeBlockEnd();
                    if (isFirstGroupElement) {
                        groupState.indentedCodeEndIndex = this.indentedCodeBlockEndIndex;
                    }
                }
            }
        } else {
            isHandled = false;
        }

        return isHandled;
    }

    @Override
    public void onVerbatim(String content, boolean isInline, Map<String, String> parameters)
    {
        // Markdown has no verbatim syntax: the closest constructs are code spans and code blocks, whose content is
        // displayed as is (in monospace, like XWiki verbatim). Note that the parameters are dropped since Markdown
        // has no syntax to specify attributes on code spans or code blocks.
        if (isInline) {
            printCodeSpan(content);
        } else {
            // The content of a standalone verbatim written on its own lines starts and ends with a line ending (e.g.
            // "{{{\ncontent\n}}}" in XWiki Syntax 2.1), which isn't part of the content to display.
            String text = StringUtils.chomp(content.replaceFirst("^(\r\n|\r|\n)", ""));
            if (isInTableCell()) {
                // Markdown table cells can only contain inline content.
                printTableCellCodeSpan(text);
            } else {
                printCodeBlockSeparator();
                printFencedCodeBlock(text, "");
            }
        }
    }

    /**
     * Print the content of a standalone element (e.g. verbatim or code macro) located in a table cell as a code span.
     *
     * @param content the content to print
     */
    private void printTableCellCodeSpan(String content)
    {
        // Separate the code span from the previous content of the cell (e.g. another standalone code), otherwise
        // two consecutive code spans would be read back as a single one.
        MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();
        if (!content.isEmpty() && (!printer.toString().isEmpty() || printer.getBuffer().length() > 0)) {
            print(SPACE);
        }
        printCodeSpan(content);
    }

    /**
     * Remember where the indented code block that has just been printed ends, so that we can detect that another
     * indented code block directly follows it.
     */
    private void markIndentedCodeBlockEnd()
    {
        MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();
        printer.flush();
        this.indentedCodeBlockPrinter = printer;
        this.indentedCodeBlockEndIndex = printer.toString().length();
    }

    /**
     * @return {@code true} if only white spaces have been printed since the last indented code block, i.e. if an
     *         indented code block printed now would be merged with it
     */
    private boolean isAfterIndentedCodeBlock()
    {
        return isOnlyWhitespacePrintedSince(this.indentedCodeBlockPrinter, this.indentedCodeBlockEndIndex);
    }

    /**
     * Print some content as a code span, see {@link #formatCodeSpan(String)}. The content is printed verbatim (not
     * escaped).
     *
     * @param content the content to print
     */
    private void printCodeSpan(String content)
    {
        print(formatCodeSpan(content));
    }

    /**
     * @param content the content of the code span
     * @return the code span syntax for the passed content, using a backtick string that doesn't appear in the content
     *     (so that the content can't end the code span) and keeping it on a single line, or an empty string when the
     *     content is empty (code spans can't be empty), see
     *     <a href="https://spec.commonmark.org/0.29/#code-spans">code spans</a>
     */
    private String formatCodeSpan(String content)
    {
        if (content.isEmpty()) {
            return StringUtils.EMPTY;
        }

        // Line endings are converted to spaces in code spans, and they would break the enclosing block (e.g. a
        // table cell or a heading) or start a new block (e.g. a list item), so we replace them.
        String text = content.replaceAll(LINE_ENDINGS, SPACE);
        String fence = getBacktickFence(text, 1);
        // A single space is stripped from both sides of the content when it both begins and ends with a space, and
        // a space is needed to separate the content from the fence when it begins or ends with a backtick.
        boolean isPadded = text.startsWith(BACKTICK) || text.endsWith(BACKTICK)
            || (text.startsWith(SPACE) && text.endsWith(SPACE) && !StringUtils.containsOnly(text, ' '));
        String padding = isPadded ? SPACE : StringUtils.EMPTY;
        return fence + padding + text + padding + fence;
    }

    /**
     * Print some content as a fenced code block, using a fence longer than any backtick string located in the
     * content, see <a href="https://spec.commonmark.org/0.29/#fenced-code-blocks">fenced code blocks</a>.
     *
     * @param content the content to print
     * @param language the language of the content (the info string), empty if not specified
     */
    private void printFencedCodeBlock(String content, String language)
    {
        // The info string must stay on the opening fence line, and it can't contain backticks when the fence is made
        // of backticks (the line wouldn't be an opening fence otherwise), in which case a tilde fence is used.
        String info = language.replaceAll(LINE_ENDINGS, SPACE);
        String fence = info.indexOf('`') < 0 ? getBacktickFence(content, TRIPLE_BACKTICK.length())
            : getFence(content, '~', TRIPLE_BACKTICK.length());
        // All the line endings are normalized so that all the code block lines can be indented to stay inside the
        // container (e.g. list item) in which it's located, except the first line when the code block directly
        // follows the container marker.
        String code = content.replaceAll(LINE_ENDINGS, NEW_LINE);
        print(prefixLines(fence + info + NEW_LINE + code + NEW_LINE + fence, getContainerLinePrefix(),
            this.isAtContainerStart));
    }

    /**
     * Separate a code block from the previous block. A code block that is the first content of a container (e.g. a
     * list item) directly follows the container marker, since a container starting with an empty line ends there.
     */
    private void printCodeBlockSeparator()
    {
        if (this.isAtContainerStart) {
            this.isFirstElementRendered = true;
        } else {
            printEmptyLine();
        }
    }

    /**
     * Remember where the list (or definition list) that has just been printed ends, so that we can detect that an
     * indented code block directly follows it.
     */
    private void markListEnd()
    {
        MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();
        printer.flush();
        this.listEndPrinter = printer;
        this.listEndIndex = printer.toString().length();
    }

    /**
     * @return {@code true} if only white spaces have been printed since the end of the last list (or definition list),
     *     i.e. if an indented code block printed now would be considered as part of its last item
     */
    private boolean isAfterList()
    {
        return isOnlyWhitespacePrintedSince(this.listEndPrinter, this.listEndIndex);
    }

    private boolean isOnlyWhitespacePrintedSince(MarkdownEscapeWikiPrinter markPrinter, int markIndex)
    {
        MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();
        boolean result = false;
        if (printer == markPrinter) {
            // The delayed text is not flushed since its escaping depends on what follows it.
            String text = printer.toString();
            result = text.length() >= markIndex && StringUtils.isBlank(text.substring(markIndex))
                && StringUtils.isBlank(printer.getBuffer());
        }
        return result;
    }

    private static String getBacktickFence(String content, int minLength)
    {
        return getFence(content, '`', minLength);
    }

    /**
     * @param content the content to put between the fences
     * @param fenceCharacter the character the fence is made of
     * @param minLength the minimum length of the fence
     * @return a fence longer than any sequence of the fence character located in the content
     */
    private static String getFence(String content, char fenceCharacter, int minLength)
    {
        int longestRun = 0;
        int currentRun = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == fenceCharacter) {
                currentRun++;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        return StringUtils.repeat(fenceCharacter, Math.max(minLength, longestRun + 1));
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

    /**
     * @return {@code true} if the current element is located in a table cell, {@code false} otherwise
     */
    protected boolean isInTableCell()
    {
        // The block state can't be used since it doesn't support nested tables: it considers that we're not in a
        // table cell anymore after the end of a table located in a table cell.
        return !this.tableCellStarted.isEmpty() && this.tableCellStarted.peek();
    }

    private BlockStateChainingListener getBlockState()
    {
        return getXWikiSyntaxListenerChain().getBlockStateChainingListener();
    }
}
