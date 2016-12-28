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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.extension.macro;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ast.Block;
import com.vladsch.flexmark.ast.BlockContent;
import com.vladsch.flexmark.internal.BlockQuoteParser;
import com.vladsch.flexmark.internal.FencedCodeBlockParser;
import com.vladsch.flexmark.internal.HeadingParser;
import com.vladsch.flexmark.internal.HtmlBlockParser;
import com.vladsch.flexmark.internal.IndentedCodeBlockParser;
import com.vladsch.flexmark.internal.ListBlockParser;
import com.vladsch.flexmark.internal.ThematicBreakParser;
import com.vladsch.flexmark.parser.block.AbstractBlockParser;
import com.vladsch.flexmark.parser.block.AbstractBlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockContinue;
import com.vladsch.flexmark.parser.block.BlockParser;
import com.vladsch.flexmark.parser.block.BlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockStart;
import com.vladsch.flexmark.parser.block.CustomBlockParserFactory;
import com.vladsch.flexmark.parser.block.MatchedBlockParser;
import com.vladsch.flexmark.parser.block.ParserState;
import com.vladsch.flexmark.util.options.DataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

public class MacroBlockParser extends AbstractBlockParser
{
    private static final Pattern OPENING_MACRO = Pattern.compile("^\\{\\{");

    private static final Pattern CLOSING_MACRO = Pattern.compile("\\/+\\}\\}");

    private final MacroBlock block = new MacroBlock();

    private BlockContent content = new BlockContent();

    public MacroBlockParser()
    {
    }

    @Override
    public Block getBlock()
    {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state)
    {
        BasedSequence line = state.getLine();
        BasedSequence trySequence = line.subSequence(0, line.length());
        Matcher matcher = CLOSING_MACRO.matcher(trySequence);
        if (matcher.find()) {
            int foundMacroLength = matcher.group(0).length();
            // We're at end of line, so we can finalize now
            this.block.setClosingMarker(trySequence.subSequence(0, foundMacroLength));
            return BlockContinue.finished();
        }
        return BlockContinue.atIndex(state.getIndex());
    }

    @Override
    public void addLine(ParserState state, BasedSequence line)
    {
        this.content.add(line, state.getIndent());
    }

    @Override
    public boolean isPropagatingLastBlankLine(BlockParser lastMatchedBlockParser)
    {
        return false;
    }

    @Override
    public void closeBlock(ParserState state)
    {
        // first line has the info string (ie macro parameters)
        List<BasedSequence> lines = this.content.getLines();
        if (lines.size() > 0) {
            BasedSequence info = lines.get(0);
            if (!info.isBlank()) {
                block.setInfo(info.trim());
            }

            BasedSequence chars = content.getSpanningChars();
            BasedSequence spanningChars = chars.baseSubSequence(chars.getStartOffset(), lines.get(0).getEndOffset());

            if (lines.size() > 1) {
                // have more lines
                block.setContent(spanningChars, lines.subList(1, lines.size()));
            } else {
                block.setContent(spanningChars, BasedSequence.EMPTY_LIST);
            }
        } else {
            block.setContent(content);
        }

        block.setCharsFromContent();
        content = null;
    }

    public static class Factory implements CustomBlockParserFactory
    {
        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getAfterDependents()
        {
            return new HashSet<>(Arrays.asList(
                    //BlockQuoteParser.Factory.class,
                    //HeadingParser.Factory.class,
                    //FencedCodeBlockParser.Factory.class
                    //HtmlBlockParser.Factory.class,
                    //ThematicBreakParser.Factory.class,
                    //ListBlockParser.Factory.class,
                    //IndentedCodeBlockParser.Factory.class
            ));
        }

        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getBeforeDependents()
        {
            return new HashSet<>(Arrays.asList(
                    BlockQuoteParser.Factory.class,
                    HeadingParser.Factory.class,
                    FencedCodeBlockParser.Factory.class,
                    HtmlBlockParser.Factory.class,
                    ThematicBreakParser.Factory.class,
                    ListBlockParser.Factory.class,
                    IndentedCodeBlockParser.Factory.class
            ));
        }

        @Override
        public boolean affectsGlobalScope()
        {
            return false;
        }

        @Override
        public BlockParserFactory create(DataHolder options)
        {
            return new BlockFactory(options);
        }
    }

    private static class BlockFactory extends AbstractBlockParserFactory
    {
        private BlockFactory(DataHolder options)
        {
            super(options);
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser)
        {
            BasedSequence line = state.getLine();
            Matcher matcher = OPENING_MACRO.matcher(line);
            if (matcher.find()) {
                int macroDeclarationLength = matcher.group(0).length();
                MacroBlockParser blockParser = new MacroBlockParser();
                blockParser.block.setOpeningMarker(line.subSequence(0, macroDeclarationLength));
                return BlockStart.of(blockParser).atIndex(macroDeclarationLength);
            }
            return BlockStart.none();
        }
    }
}
