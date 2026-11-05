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

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.vladsch.flexmark.ext.definition.DefinitionItem;
import com.vladsch.flexmark.ext.definition.internal.DefinitionItemBlockParser;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.block.AbstractBlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockParser;
import com.vladsch.flexmark.parser.block.BlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockStart;
import com.vladsch.flexmark.parser.block.CustomBlockParserFactory;
import com.vladsch.flexmark.parser.block.MatchedBlockParser;
import com.vladsch.flexmark.parser.block.ParserState;
import com.vladsch.flexmark.parser.core.DocumentBlockParser;
import com.vladsch.flexmark.parser.core.ParagraphParser;
import com.vladsch.flexmark.parser.internal.MatchedBlockParserImpl;
import com.vladsch.flexmark.util.ast.BlankLine;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataHolder;

/**
 * Parses the definition descriptions that follow another definition description in a container block (e.g. a list
 * item or a quotation), as done at the top level of the document. The flexmark definition extension only starts a
 * definition description after a paragraph (the terms), after another definition description located at the top
 * level of the document, or inside a definition description, so the following descriptions of a term located in a
 * list item or a quotation would be read as text otherwise. Like the definition extension, it's one of the default
 * extensions, used by all the Markdown syntaxes supporting definition lists (e.g. markdown+github/1.0 too).
 *
 * @version $Id$
 * @since 8.10.0
 */
public class DefinitionItemExtension implements Parser.ParserExtension
{
    @Override
    public void parserOptions(MutableDataHolder mutableDataHolder)
    {
        // This parser extension doesn't have any configurable options.
    }

    @Override
    public void extend(Parser.Builder builder)
    {
        builder.customBlockParserFactory(new Factory());
    }

    /**
     * @return a new instance of this extension
     */
    public static DefinitionItemExtension create()
    {
        return new DefinitionItemExtension();
    }

    /**
     * Factory class for the parser of the definition descriptions following another definition description in a
     * container block.
     */
    private static final class Factory implements CustomBlockParserFactory
    {
        @Override
        public Set<Class<?>> getAfterDependents()
        {
            // No dependency: flexmark expects null in this case (as for its own block parser factories).
            return null;
        }

        @Override
        public Set<Class<?>> getBeforeDependents()
        {
            // No dependency: flexmark expects null in this case (as for its own block parser factories).
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

    private static final class BlockFactory extends AbstractBlockParserFactory
    {
        private final BlockParserFactory definitionItemFactory;

        BlockFactory(DataHolder options)
        {
            super(options);
            this.definitionItemFactory = new DefinitionItemBlockParser.Factory().apply(options);
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser)
        {
            BlockParser blockParser = matchedBlockParser.getBlockParser();
            if (blockParser.isContainer() && !(blockParser instanceof DocumentBlockParser)
                && !(blockParser instanceof DefinitionItemBlockParser)
                && blockParser.getBlock().getLastChildAnyNot(BlankLine.class) instanceof DefinitionItem) {
                // The definition extension starts a definition description in a container block only when the matched
                // block is a paragraph (the terms) or a definition description. Here the container ends with a
                // definition description, which is enough at the top level of the document, so we let the definition
                // extension start the definition description as if the matched block was an (empty) paragraph.
                // This relies on the following behavior of DefinitionItemBlockParser in flexmark 0.64.8:
                // - its tryStart() only checks the type of the matched block parser (instanceof ParagraphParser),
                //   it doesn't use the paragraph itself (its content and lines)
                // - the check of DefinitionExtension.DOUBLE_BLANK_LINE_BREAKS_LIST (two empty lines ending the
                //   definition list), which is done only for the definition descriptions located at the top level of
                //   the document, is skipped, which doesn't matter since this option isn't enabled
                // The definition description is then added to the definition list of the previous one by
                // DefinitionListItemBlockPreProcessor, as at the top level of the document.
                return this.definitionItemFactory.tryStart(state, new MatchedBlockParserImpl(new ParagraphParser()));
            }
            return BlockStart.none();
        }
    }
}
