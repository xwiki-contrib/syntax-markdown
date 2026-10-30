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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Escape characters that would be confused for Markdown syntax if they were not escaped.
 *
 * @version $Id: 44a804272066150623d4b5ebbc8cd5980bd6f34b $
 * @since 8.1RC1
 */
public class MarkdownEscapeHandler
{
    public static final String ESCAPE_CHAR = "\\";

    private static final char ESCAPE = '\\';

    private static final char NEW_LINE = '\n';

    /**
     * Characters that are always escaped since they can start or end some inline Markdown syntax (emphasis, code,
     * strikethrough, subscript, superscript, links, wiki links and images).
     */
    private static final String RESERVED_CHARS = "\\*_`~^[";

    private static final Pattern ENTITY_PATTERN =
        Pattern.compile("&(?:#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{0,31});");

    /**
     * Syntaxes that are only recognized at the beginning of a line (the first group is the character to escape).
     * Note that '*' is not listed since it's always escaped.
     */
    private static final List<Pattern> LINE_START_PATTERNS = Arrays.asList(
        // ATX headings
        Pattern.compile("\\p{Blank}*(#)#{0,5}(?:\\p{Blank}|$)"),
        // Setext headings, thematic breaks and bulleted lists using '-'
        Pattern.compile("\\p{Blank}*([=-])"),
        // Bulleted lists using '+'
        Pattern.compile("\\p{Blank}*(\\+)(?:\\p{Blank}|$)"),
        // Numbered lists (note that the escape needs to be on the delimiter, digits can't be escaped)
        Pattern.compile("\\p{Blank}*\\p{Digit}{1,9}([.)])(?:\\p{Blank}|$)"),
        // Quotes
        Pattern.compile("\\p{Blank}*(>)"),
        // Definition descriptions
        Pattern.compile("\\p{Blank}*(:)(?:\\p{Blank}|$)"));

    // Note that the HTML syntax recognized by the Markdown parser (flexmark-java) excludes the curly brackets from the
    // unquoted attribute values.
    private static final String HTML_ATTRIBUTE =
        "\\s+[a-zA-Z_:][a-zA-Z0-9_.:-]*(?:\\s*=\\s*(?:[^\\x00-\\x20\"'=<>`{}]+|'[^']*'|\"[^\"]*\"))?";

    // The repetition of the attributes is possessive to avoid a stack overflow on large content (it doesn't change the
    // matched strings since backtracking into it can't lead to a match).
    private static final String HTML_OPEN_TAG = "<[a-zA-Z][a-zA-Z0-9-]*(?:" + HTML_ATTRIBUTE + ")*+\\s*/?>";

    private static final String HTML_CLOSING_TAG = "</[a-zA-Z][a-zA-Z0-9-]*\\s*>";

    /**
     * HTML tags, comments, processing instructions, declarations and CDATA sections, which are kept verbatim. They
     * must match exactly the inline HTML recognized by the Markdown parser (see
     * https://spec.commonmark.org/0.29/#raw-html), otherwise some text that is read back as text would not be escaped
     * (e.g. a pipe splitting a table cell). The repetition in the comments is possessive to avoid a stack overflow on
     * large content.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(HTML_OPEN_TAG + '|' + HTML_CLOSING_TAG
        + "|<!---->|<!--(?:-?[^>-])(?:-?[^-])*+-->|<\\?.*?\\?>|<![a-zA-Z]+\\s+[^>]*>|<!\\[CDATA\\[[\\s\\S]*?\\]\\]>");

    private static final String HTML_BLOCK_INDENTATION = "^ {0,3}";

    /**
     * Start conditions of the HTML blocks whose content is not parsed as Markdown until an end condition is met,
     * associated with that end condition (see https://spec.commonmark.org/0.29/#html-blocks).
     */
    private static final List<Pattern[]> HTML_BLOCK_CONDITIONS = Arrays.asList(
        new Pattern[] {
            Pattern.compile(HTML_BLOCK_INDENTATION + "<(?:script|pre|style|textarea)(?:\\s|>|$)",
                Pattern.CASE_INSENSITIVE),
            Pattern.compile("</(?:script|pre|style|textarea)>", Pattern.CASE_INSENSITIVE) },
        new Pattern[] { Pattern.compile(HTML_BLOCK_INDENTATION + "<!--"), Pattern.compile("-->") },
        new Pattern[] { Pattern.compile(HTML_BLOCK_INDENTATION + "<\\?"), Pattern.compile("\\?>") },
        new Pattern[] { Pattern.compile(HTML_BLOCK_INDENTATION + "<![a-zA-Z]"), Pattern.compile(">") },
        new Pattern[] { Pattern.compile(HTML_BLOCK_INDENTATION + "<!\\[CDATA\\["), Pattern.compile("\\]\\]>") });

    /**
     * Start condition of the HTML blocks that end with a blank line and that can interrupt a paragraph.
     */
    private static final Pattern HTML_BLOCK_START_PATTERN = Pattern.compile(HTML_BLOCK_INDENTATION
        + "</?(?:address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog"
        + "|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|frame|frameset|h1|h2|h3|h4|h5|h6|head|header|hr"
        + "|html|iframe|legend|li|link|main|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|section|source"
        + "|summary|table|tbody|td|tfoot|th|thead|title|tr|track|ul)(?:\\s|/?>|$)", Pattern.CASE_INSENSITIVE);

    /**
     * Start condition of the HTML blocks that end with a blank line and that cannot interrupt a paragraph.
     */
    private static final Pattern HTML_BLOCK_TAG_PATTERN =
        Pattern.compile(HTML_BLOCK_INDENTATION + "(?:" + HTML_OPEN_TAG + '|' + HTML_CLOSING_TAG + ")\\s*$");

    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("\\s*");

    private boolean onNewLine = true;

    private boolean inLinkLabel;

    private boolean inTableCell;

    public void setOnNewLine(boolean onNewLine)
    {
        this.onNewLine = onNewLine;
    }

    public boolean isOnNewLine()
    {
        return this.onNewLine;
    }

    /**
     * @param inLinkLabel true if the escaped text is located inside a link label, in which case the characters that
     *     would end the label are escaped too
     * @since 8.10.0
     */
    public void setInLinkLabel(boolean inLinkLabel)
    {
        this.inLinkLabel = inLinkLabel;
    }

    /**
     * @param inTableCell true if the escaped text is located inside a table cell, in which case the pipes (that would
     *     be considered as cell separators) are escaped too
     * @since 8.10.0
     */
    public void setInTableCell(boolean inTableCell)
    {
        this.inTableCell = inTableCell;
    }

    /**
     * Escape the passed text so that it's not interpreted as Markdown syntax.
     *
     * @param accumulatedBuffer the text to escape, modified in place
     * @param nextText the content that will be printed right after the text, or {@code null} if it's not known
     * @since 8.10.0
     */
    public void escape(StringBuffer accumulatedBuffer, String nextText)
    {
        char next = (nextText == null || nextText.isEmpty()) ? 0 : nextText.charAt(0);
        String escaped = escapeText(accumulatedBuffer.toString(), isOnNewLine(), next, true);
        accumulatedBuffer.setLength(0);
        accumulatedBuffer.append(escaped);
    }

    /**
     * Escape the text located between the HTML tags of some inline HTML content (in which Markdown syntax is
     * interpreted), keeping the HTML tags verbatim. HTML entities are not escaped since they are part of the HTML.
     *
     * @param html the inline HTML content to escape
     * @return the escaped HTML content
     * @since 8.10.0
     */
    public String escapeInlineHTML(String html)
    {
        return escapeHTML(html, isOnNewLine());
    }

    /**
     * Escape the pipes located between the HTML tags of some raw content located in a table cell, which would be
     * considered as cell separators, keeping the rest of the content verbatim. The content is kept unchanged when not
     * located in a table cell.
     *
     * @param raw the raw content to escape
     * @return the raw content with the pipes located between the HTML tags escaped
     * @since 8.10.0
     */
    public String escapeRawPipes(String raw)
    {
        if (!this.inTableCell) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw.length());
        Matcher matcher = HTML_TAG_PATTERN.matcher(raw);
        int position = 0;
        while (matcher.find()) {
            appendEscapedPipes(builder, raw, position, matcher.start());
            builder.append(matcher.group());
            position = matcher.end();
        }
        appendEscapedPipes(builder, raw, position, raw.length());
        return builder.toString();
    }

    private void appendEscapedPipes(StringBuilder builder, String text, int start, int end)
    {
        boolean isEscaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '|' && !isEscaped) {
                builder.append(ESCAPE_CHAR);
            }
            builder.append(c);
            isEscaped = c == '\\' && !isEscaped;
        }
    }

    /**
     * Escape some HTML content that is located at the beginning of a block. Markdown syntax is not interpreted inside
     * HTML blocks and thus their content is kept verbatim, while the text located between the HTML tags of the parts
     * that are parsed as paragraphs is escaped.
     *
     * @param html the HTML content to escape
     * @return the escaped HTML content
     * @since 8.10.0
     */
    public String escapeBlockHTML(String html)
    {
        StringBuilder builder = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        // The end condition of the current HTML block, if any.
        Pattern htmlBlockEnd = null;
        boolean isInHTMLBlockUntilBlankLine = false;
        String[] lines = html.split(String.valueOf(NEW_LINE), -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isLineVerbatim = true;
            boolean isBlankLine = BLANK_LINE_PATTERN.matcher(line).matches();
            if (htmlBlockEnd != null) {
                if (htmlBlockEnd.matcher(line).find()) {
                    htmlBlockEnd = null;
                }
            } else if (isInHTMLBlockUntilBlankLine) {
                isInHTMLBlockUntilBlankLine = !isBlankLine;
            } else if (!isBlankLine) {
                Pattern[] conditions = getHTMLBlockConditions(line, paragraph.length() > 0);
                if (conditions == null) {
                    isLineVerbatim = false;
                } else if (conditions.length > 1) {
                    htmlBlockEnd = conditions[1].matcher(line).find() ? null : conditions[1];
                } else {
                    isInHTMLBlockUntilBlankLine = true;
                }
            }

            if (isLineVerbatim) {
                appendParagraph(builder, paragraph);
                builder.append(line);
            } else {
                paragraph.append(line);
            }
            if (i < lines.length - 1) {
                (isLineVerbatim ? builder : paragraph).append(NEW_LINE);
            }
        }
        appendParagraph(builder, paragraph);

        return builder.toString();
    }

    private void appendParagraph(StringBuilder builder, StringBuilder paragraph)
    {
        if (paragraph.length() > 0) {
            builder.append(escapeHTML(paragraph.toString(), true));
            paragraph.setLength(0);
        }
    }

    /**
     * @return the start and end conditions of the HTML block that starts with the passed line, a single start
     *     condition for HTML blocks that end with a blank line, or {@code null} if the line doesn't start an HTML block
     */
    private Pattern[] getHTMLBlockConditions(String line, boolean isInParagraph)
    {
        for (Pattern[] conditions : HTML_BLOCK_CONDITIONS) {
            if (conditions[0].matcher(line).find()) {
                return conditions;
            }
        }
        if (HTML_BLOCK_START_PATTERN.matcher(line).find()
            || (!isInParagraph && HTML_BLOCK_TAG_PATTERN.matcher(line).find()))
        {
            return new Pattern[] { HTML_BLOCK_TAG_PATTERN };
        }
        return null;
    }

    private String escapeHTML(String html, boolean lineStart)
    {
        StringBuilder builder = new StringBuilder(html.length());
        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        boolean isOnLineStart = lineStart;
        int position = 0;
        while (matcher.find()) {
            builder.append(escapeText(html.substring(position, matcher.start()), isOnLineStart, '<', false));
            builder.append(matcher.group());
            isOnLineStart = false;
            position = matcher.end();
        }
        builder.append(escapeText(html.substring(position), isOnLineStart, (char) 0, false));
        return builder.toString();
    }

    private String escapeText(String text, boolean lineStart, char next, boolean escapeEntities)
    {
        boolean[] escapes = new boolean[text.length()];
        if (lineStart) {
            markLineStartEscapes(text, 0, next, escapes);
        }
        for (int i = text.indexOf(NEW_LINE); i > -1; i = text.indexOf(NEW_LINE, i + 1)) {
            markLineStartEscapes(text, i + 1, next, escapes);
        }

        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char nextChar = i + 1 < text.length() ? text.charAt(i + 1) : next;
            if (escapes[i] || needsEscape(c, text, i, nextChar, escapeEntities)) {
                builder.append(ESCAPE);
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private boolean needsEscape(char c, String text, int index, char next, boolean escapeEntities)
    {
        boolean result;
        if (RESERVED_CHARS.indexOf(c) > -1) {
            result = true;
        } else {
            switch (c) {
                case ']':
                    result = this.inLinkLabel;
                    break;
                case '{':
                    // XWiki macro syntax
                    result = next == '{';
                    break;
                case '<':
                    // HTML tags, HTML comments, processing instructions and autolinks
                    result = Character.isLetter(next) || next == '/' || next == '!' || next == '?';
                    break;
                case '&':
                    // HTML entities
                    result = escapeEntities && ENTITY_PATTERN.matcher(text).region(index, text.length()).lookingAt();
                    break;
                case '|':
                    // Table cell separator. Pipes located inside link labels are escaped too: the link would be broken
                    // by a pipe located inside some emphasis of its label. Note that the wiki link labels encode them
                    // as HTML entities instead.
                    result = this.inTableCell;
                    break;
                case '!':
                    // Image syntax when the text is followed by some link syntax
                    result = index == text.length() - 1 && next == '[';
                    break;
                default:
                    result = false;
            }
        }
        return result;
    }

    private void markLineStartEscapes(String text, int start, char next, boolean[] escapes)
    {
        int end = text.indexOf(NEW_LINE, start);
        String line;
        if (end < 0) {
            end = text.length();
            line = next == 0 ? text.substring(start) : text.substring(start) + next;
        } else {
            line = text.substring(start, end);
        }
        for (Pattern pattern : LINE_START_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.lookingAt() && start + matcher.start(1) < end) {
                escapes[start + matcher.start(1)] = true;
            }
        }
    }
}
