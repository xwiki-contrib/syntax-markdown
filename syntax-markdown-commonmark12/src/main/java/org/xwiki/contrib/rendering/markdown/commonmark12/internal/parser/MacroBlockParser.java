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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.util.Parsing;
import com.vladsch.flexmark.ext.xwiki.macros.Macro;
import com.vladsch.flexmark.ext.xwiki.macros.MacroAttribute;
import com.vladsch.flexmark.ext.xwiki.macros.MacroBlock;
import com.vladsch.flexmark.ext.xwiki.macros.MacroClose;
import com.vladsch.flexmark.parser.block.AbstractBlockParser;
import com.vladsch.flexmark.parser.block.AbstractBlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockContinue;
import com.vladsch.flexmark.parser.block.BlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockStart;
import com.vladsch.flexmark.parser.block.CustomBlockParserFactory;
import com.vladsch.flexmark.parser.block.MatchedBlockParser;
import com.vladsch.flexmark.parser.block.ParserState;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * Parses the macros written on their own lines (standalone macros), e.g. {@code {{info}}}, followed by the macro
 * content lines and {@code {{/info}}}, into a {@link MacroBlock} whose content lines are the macro content.
 * <p>
 * This replaces the block parser of the flexmark XWiki macro extension, which looks for the macro name and for the
 * closing tag at the beginning of the whole line instead of after the prefixes of the containers in which the macro is
 * located (e.g. the {@code > } of a quotation or the indentation of a list item), and which parses the macro content
 * as Markdown, whose characters then include these prefixes. The macro content lines are kept here as is, without the
 * container prefixes, since the macro content is passed as is to the macro. Not parsing the macro content as Markdown
 * also ensures that it can't affect the rest of the document (e.g. through abbreviation definitions) and that it
 * doesn't take lines located outside of the container of the macro (lazy continuation lines).
 *
 * @version $Id$
 * @since 8.10.0
 */
public class MacroBlockParser extends AbstractBlockParser
{
    private static final String CLOSE_MARKER = "}}";

    private static final String CLOSED_TAG_END = "/}}";

    private final MacroBlock block = new MacroBlock();

    private final MacroParsing parsing;

    private final String macroName;

    private final boolean isOneLine;

    private final List<BasedSequence> lines = new ArrayList<>();

    private boolean hadClose;

    /**
     * The number of nested macros having the same name, which are not closed yet.
     */
    private int nestedMacroDepth;

    MacroBlockParser(MacroParsing parsing, String macroName, boolean isOneLine)
    {
        this.parsing = parsing;
        this.macroName = macroName;
        this.isOneLine = isOneLine;
        this.hadClose = isOneLine;
    }

    @Override
    public Block getBlock()
    {
        return this.block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state)
    {
        if (this.hadClose) {
            return BlockContinue.none();
        }

        // The line content located after the prefixes of the containers in which the macro is located.
        BasedSequence line = state.getLine().subSequence(state.getIndex());
        Matcher closeMatcher = this.parsing.matchClosingLine(this.macroName, line);
        if (closeMatcher != null) {
            if (this.nestedMacroDepth > 0) {
                this.nestedMacroDepth--;
            } else {
                this.hadClose = true;
                this.block.appendChild(createMacroClose(line, closeMatcher));
                return BlockContinue.atIndex(state.getLineEndIndex());
            }
        } else if (this.parsing.isOpeningLine(this.macroName, line)) {
            this.nestedMacroDepth++;
        }

        return BlockContinue.atIndex(state.getIndex());
    }

    private static MacroClose createMacroClose(BasedSequence text, Matcher closeMatcher)
    {
        // The closing marker is the first one after the macro name (the matched text may end with white spaces).
        int closeStart = text.indexOf(CLOSE_MARKER, closeMatcher.end(1));
        MacroClose macroClose = new MacroClose(text.subSequence(closeMatcher.start(), closeMatcher.start() + 3),
            text.subSequence(closeMatcher.start(1), closeMatcher.end(1)),
            text.subSequence(closeStart, closeStart + CLOSE_MARKER.length()));
        macroClose.setCharsFromContent();
        return macroClose;
    }

    @Override
    public void addLine(ParserState state, BasedSequence line)
    {
        this.lines.add(line);
    }

    @Override
    public void closeBlock(ParserState state)
    {
        Macro macro = (Macro) this.block.getFirstChild();
        List<BasedSequence> contentLines = new ArrayList<>();
        if (this.isOneLine) {
            if (this.block.getLastChild() instanceof MacroClose) {
                contentLines.add(macro.baseSubSequence(macro.getEndOffset(),
                    this.block.getLastChild().getStartOffset()));
            }
        } else if (!this.lines.isEmpty()) {
            // The first line is the end of the opening line and the last line is the end of the closing line, if any.
            contentLines.addAll(this.lines.subList(1, this.hadClose ? this.lines.size() - 1 : this.lines.size()));
        }
        this.block.setContent(contentLines);
        this.block.setCharsFromContent();
    }

    /**
     * The patterns used to parse the macros.
     */
    static class MacroParsing
    {
        private final Pattern open;

        private final Pattern close;

        private final Pattern closeEnd;

        private final Pattern attribute;

        /**
         * The opening or closing macro tags.
         */
        private final Pattern tag;

        MacroParsing(DataHolder options)
        {
            Parsing htmlParsing = new Parsing(options);
            // The repetition of the parameters is possessive to avoid a stack overflow on large content.
            String openTag = "\\{\\{(" + htmlParsing.TAGNAME + ")" + htmlParsing.ATTRIBUTE + "*+\\s*/?\\}\\}";
            String closeTag = "\\{\\{/(" + htmlParsing.TAGNAME + ")\\s*\\}\\}";
            String closeTagEnd = closeTag + "\\s*$";
            this.open = Pattern.compile('^' + openTag, Pattern.CASE_INSENSITIVE);
            this.close = Pattern.compile('^' + closeTagEnd, Pattern.CASE_INSENSITIVE);
            this.closeEnd = Pattern.compile(closeTagEnd, Pattern.CASE_INSENSITIVE);
            this.attribute = Pattern.compile("\\s*(" + htmlParsing.ATTRIBUTENAME + ")\\s*(?:=\\s*("
                + htmlParsing.ATTRIBUTEVALUE + ")?)?");
            this.tag = Pattern.compile(String.format("(?:%s)|(?:%s)", openTag, closeTag));
        }

        /**
         * @return the matcher of the closing tag if the line contains only the closing tag of the macro, {@code null}
         *     otherwise
         */
        private Matcher matchClosingLine(String macroName, CharSequence line)
        {
            Matcher closeMatcher = this.close.matcher(line);
            return closeMatcher.find() && macroName.equals(closeMatcher.group(1)) ? closeMatcher : null;
        }

        /**
         * @return the matcher of the closing tag if the text ends with the closing tag of the macro, {@code null}
         *     otherwise
         */
        private Matcher matchClosingEnd(String macroName, CharSequence text)
        {
            Matcher closeMatcher = this.closeEnd.matcher(text);
            return closeMatcher.find() && macroName.equals(closeMatcher.group(1)) ? closeMatcher : null;
        }

        /**
         * Indicates if a line, located between the opening and closing tags of a standalone macro, opens a nested macro
         * having the same name, whose closing tag must not close the macro. This is the case when the line starts with
         * an opening tag (not a closed tag) of a macro having the same name, which isn't closed at the end of the line,
         * even if the opening tag is followed by some text (in case of doubt, the following lines are kept in the
         * macro).
         *
         * @return {@code true} if the line opens a nested macro having the given name, {@code false} otherwise
         */
        private boolean isOpeningLine(String macroName, CharSequence line)
        {
            Matcher openMatcher = this.open.matcher(line);
            return openMatcher.find() && macroName.equals(openMatcher.group(1))
                && !openMatcher.group().endsWith(CLOSED_TAG_END)
                && matchClosingEnd(macroName, line.subSequence(openMatcher.end(), line.length())) == null;
        }

        /**
         * Indicates if the text located between the opening tag of a macro and the closing tag ending the line is the
         * content of a macro written on a single line. Otherwise the line is read as a paragraph, in which the inline
         * parser closes the macro on the first closing tag having the same name that doesn't close a nested macro
         * (e.g. a line made of 2 inline macros having the same name, or of an inline macro followed by some text
         * ending with an escaped closing tag, is not a single macro). The tags having the same name located in the
         * content must thus be balanced, and the closing tag ending the line must not be escaped.
         *
         * @return {@code true} if the text is the content of the macro, {@code false} otherwise
         */
        private boolean isSingleLineContent(String macroName, CharSequence content)
        {
            int backslashCount = 0;
            while (backslashCount < content.length()
                && content.charAt(content.length() - 1 - backslashCount) == '\\')
            {
                backslashCount++;
            }
            if (backslashCount % 2 != 0) {
                return false;
            }

            int depth = 0;
            Matcher matcher = this.tag.matcher(content);
            while (matcher.find()) {
                boolean isClosingTag = matcher.group(1) == null;
                if (macroName.equals(isClosingTag ? matcher.group(2) : matcher.group(1))) {
                    if (!isClosingTag) {
                        depth += matcher.group().endsWith(CLOSED_TAG_END) ? 0 : 1;
                    } else if (depth == 0) {
                        return false;
                    } else {
                        depth--;
                    }
                }
            }
            return depth == 0;
        }
    }

    /**
     * Factory class for {@link MacroBlockParser}.
     */
    public static class Factory implements CustomBlockParserFactory
    {
        @Override
        public Set<Class<?>> getAfterDependents()
        {
            return null;
        }

        @Override
        public Set<Class<?>> getBeforeDependents()
        {
            return null;
        }

        @Override
        public boolean affectsGlobalScope()
        {
            return false;
        }

        @NotNull
        @Override
        public BlockParserFactory apply(@NotNull DataHolder options)
        {
            return new BlockFactory(options);
        }
    }

    private static class BlockFactory extends AbstractBlockParserFactory
    {
        private final MacroParsing parsing;

        BlockFactory(DataHolder options)
        {
            super(options);
            this.parsing = new MacroParsing(options);
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser)
        {
            // A standalone macro can't interrupt a paragraph.
            if (state.getIndent() != 0 || matchedBlockParser.getBlockParser().getBlock() instanceof Paragraph) {
                return BlockStart.none();
            }

            // The line content located after the prefixes of the containers in which the macro is located.
            BasedSequence line = state.getLine().subSequence(state.getIndex());
            Matcher matcher = this.parsing.open.matcher(line);
            if (!matcher.find()) {
                return BlockStart.none();
            }

            BasedSequence name = line.subSequence(matcher.start(1), matcher.end(1));
            BasedSequence macroOpen = line.subSequence(0, matcher.end());
            BasedSequence afterOpen = line.subSequence(matcher.end());
            boolean isClosedTag = macroOpen.endsWith(CLOSED_TAG_END);
            MacroClose macroClose = null;
            if (!afterOpen.isBlank()) {
                // The macro is standalone only if it's closed at the end of the line, by its own closing tag.
                macroClose = isClosedTag ? null : getMacroClose(name, afterOpen);
                if (macroClose == null) {
                    return BlockStart.none();
                }
            }

            Macro macro = new Macro(macroOpen.subSequence(0, 2), name, macroOpen.endSequence(isClosedTag ? 3 : 2));
            macro.setCharsFromContent();
            addAttributes(macro, macroOpen.baseSubSequence(name.getEndOffset(),
                macro.getClosingMarker().getStartOffset()).trim());

            MacroBlockParser parser =
                new MacroBlockParser(this.parsing, name.toString(), isClosedTag || macroClose != null);
            parser.block.appendChild(macro);
            if (macroClose != null) {
                parser.block.appendChild(macroClose);
            }

            return BlockStart.of(parser).atIndex(state.getLineEndIndex());
        }

        private MacroClose getMacroClose(BasedSequence name, BasedSequence afterOpen)
        {
            Matcher closeMatcher = this.parsing.matchClosingEnd(name.toString(), afterOpen);
            return closeMatcher != null
                && this.parsing.isSingleLineContent(name.toString(), afterOpen.subSequence(0, closeMatcher.start()))
                    ? createMacroClose(afterOpen, closeMatcher) : null;
        }

        private void addAttributes(Macro macro, BasedSequence attributeText)
        {
            if (attributeText.isEmpty()) {
                return;
            }

            macro.setAttributeText(attributeText);
            Matcher attributeMatcher = this.parsing.attribute.matcher(attributeText);
            while (attributeMatcher.find()) {
                BasedSequence attributeName =
                    attributeText.subSequence(attributeMatcher.start(1), attributeMatcher.end(1));
                BasedSequence separator = BasedSequence.NULL;
                BasedSequence value = BasedSequence.NULL;
                if (attributeMatcher.start(2) != -1) {
                    separator = attributeText.subSequence(attributeMatcher.end(1), attributeMatcher.start(2)).trim();
                    value = attributeText.subSequence(attributeMatcher.start(2), attributeMatcher.end(2));
                }
                BasedSequence openingMarker = BasedSequence.NULL;
                BasedSequence closingMarker = BasedSequence.NULL;
                if (isQuoted(value)) {
                    openingMarker = value.subSequence(0, 1);
                    closingMarker = value.endSequence(1, 0);
                    value = value.midSequence(1, -1);
                }
                macro.appendChild(new MacroAttribute(attributeName, separator, openingMarker, value, closingMarker));
            }
        }

        private boolean isQuoted(BasedSequence value)
        {
            return value.length() >= 2 && isQuote(value.charAt(0)) && value.endCharAt(1) == value.charAt(0);
        }

        private boolean isQuote(char c)
        {
            return c == '"' || c == '\'';
        }
    }
}
