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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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

    protected ResourceReferenceSerializer linkReferenceSerializer;

    protected ResourceReferenceSerializer imageReferenceSerializer;

    // Custom States

    private boolean isFirstElementRendered;

    private Stack<String> listStyle = new Stack<>();

    private int previousQuoteDepth;

    private Stack<List<List<String>>> tableCells = new Stack<>();

    /**
     * How many head rows each table has.
     */
    private Stack<Integer> tableHeadRowsCount = new Stack<>();

    private Stack<Boolean> isOnFirstHeadCellInTableRow = new Stack<>();

    private Stack<Map<String, String>> abbreviations = new Stack<>();

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
                print("`");
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
                print("`");
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    public void onHorizontalLine(Map<String, String> parameters)
    {
        printEmptyLine();
        print("---");
    }

    @Override
    public void beginParagraph(Map<String, String> parameters)
    {
        printEmptyLine();
    }

    @Override
    public void beginHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        printEmptyLine();
        print(StringUtils.repeat("#", level.getAsInt()) + " ");
        pushPrinter(createMarkdownPrinter(new DefaultWikiPrinter()));
    }

    @Override
    public void endHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        MarkdownEscapeWikiPrinter headingPrinter = getMarkdownPrinter();
        headingPrinter.flush();
        String heading = headingPrinter.toString();
        popPrinter();
        print(heading);
    }

    @Override
    public void beginList(ListType listType, Map<String, String> parameters)
    {
        if (getBlockState().getListDepth() == 1) {
            printEmptyLine();
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

        print(StringUtils.repeat(" ", (getBlockState().getListDepth() - 1) * 4));
        print(this.listStyle.peek());
        if (StringUtils.contains(this.listStyle.peek(), '1')) {
            print(".");
        }
        print(" ");
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

        print(StringUtils.repeat(' ', 4 * (getBlockState().getDefinitionListDepth() - 1)) + ":   ");
    }

    @Override
    public void beginDefinitionTerm()
    {
        printEmptyLine();
        print(StringUtils.repeat(' ', 4 * (getBlockState().getDefinitionListDepth() - 1)));
    }

    @Override
    public void endDefinitionDescription()
    {
        getMarkdownPrinter().flush();
    }

    @Override
    public void endDefinitionTerm()
    {
        getMarkdownPrinter().flush();
    }

    @Override
    public void beginQuotation(Map<String, String> parameters)
    {
        if (!getBlockState().isInQuotationLine()) {
            printEmptyLine();
        }
        this.previousQuoteDepth = getBlockState().getQuotationDepth();
    }

    @Override
    public void beginQuotationLine()
    {
        if (getBlockState().getQuotationLineIndex() > 0) {
            print("\n");
            print(">");
            if (this.previousQuoteDepth > 2) {
                print(StringUtils.repeat(" >", this.previousQuoteDepth - 2));
            }
            print("\n");
        }

        print(StringUtils.repeat("> ", getBlockState().getQuotationDepth()));
    }

    @Override
    public void endQuotationLine()
    {
        getMarkdownPrinter().flush();
    }

    @Override
    public void beginLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        MarkdownEscapeWikiPrinter linkLabelPrinter = createMarkdownPrinter(new DefaultWikiPrinter());

        // Make sure the escape handler knows there is already characters before
        linkLabelPrinter.setOnNewLine(getMarkdownPrinter().isOnNewLine());

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
        print(" ");
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
        print(word);
    }

    @Override
    public void onSpecialSymbol(char symbol)
    {
        // - Handle pegdown QUOTES extension for << and >>
        // - Handle --- as em-dash
        // - Handle ... as ellipsis
        // - Handle -- as range
        if (symbol == 8220) {
            print("<<");
        } else if (symbol == 8221) {
            print(">>");
        } else if (symbol == 8212) {
            print("---");
        } else if (symbol == 8230) {
            print("...");
        } else if (symbol == 8211) {
            print("--");
        } else {
            print("" + symbol);
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
        pushPrinter(createMarkdownPrinter(new DefaultWikiPrinter()));
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
                print(BACKTICK + content + BACKTICK);
            } else {
                // Standalone should generate fenced blocks or triple backticks with language if a language was
                // specified (and no language if language is none).
                String language = parameters.get("language");
                if (language != null && !"none".equals(language)) {
                    println(TRIPLE_BACKTICK + language);
                    print(content);
                    print("\n" + TRIPLE_BACKTICK);
                } else {
                    String lines[] = content.split("\\r?\\n");
                    int spaces = getBlockState().isInList() ? (getBlockState().getListDepth() - 1) * 4 + 2 : 0;
                    for (int i = 0; i < lines.length; i++) {
                        // If we're in a list, for each list item level, we add 4 + 2 more spaces, see spec at
                        // https://spec.commonmark.org/0.28/#list-items
                        print(String.format("    %s%s", StringUtils.repeat(' ', spaces), lines[i]));
                        if (i < lines.length - 1) {
                            print("\n");
                        }
                    }
                }
            }
        } else {
            isHandled = false;
        }

        return isHandled;
    }

    protected void print(String text)
    {
        getPrinter().print(text);
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

    private BlockStateChainingListener getBlockState()
    {
        return getXWikiSyntaxListenerChain().getBlockStateChainingListener();
    }
}
