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
package org.xwiki.rendering.internal.renderer.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xwiki.rendering.internal.renderer.xwiki20.XWikiSyntaxListenerChain;
import org.xwiki.rendering.listener.chaining.BlockStateChainingListener;

/**
 * Escape characters that would be confused for Markdown syntax if they were not escaped.
 *
 * @version $Id: 44a804272066150623d4b5ebbc8cd5980bd6f34b $
 * @since 8.1RC1
 */
public class MarkdownEscapeHandler
{
    private static final Pattern LIST_PATTERN = Pattern.compile(
        "\\p{Blank}*((\\*)|(\\-)|(\\+)|(\\p{Digit}\\.))\\p{Blank}+");

    private static final Pattern QUOTE_PATTERN = Pattern.compile("(\\>+)");

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\p{Blank}*((=+)|(-+))");

    /**
     * Note that we take care to not match if the first character is preceded by an escape (i.e. '\').
     */
    private static final Pattern RESERVED_CHARS_PATTERN = Pattern.compile(
        "(?<!\\\\)\\*|(?<!\\\\)\\*\\*|(?<!\\\\)__|(?<!\\\\)_|(?<!\\\\)`");

    public static final String ESCAPE_CHAR = "\\";

    private boolean beforeLink = false;

    private boolean onNewLine = true;

    public void setOnNewLine(boolean onNewLine)
    {
        this.onNewLine = onNewLine;
    }

    public boolean isOnNewLine()
    {
        return this.onNewLine;
    }

    public void escape(StringBuffer accumulatedBuffer, XWikiSyntaxListenerChain listenerChain)
    {
        BlockStateChainingListener blockStateListener = listenerChain.getBlockStateChainingListener();

        // Escape escape symbol.
        // Note: This needs to be the first replacement since other replacements below also use the escape symbol
        replaceAll(accumulatedBuffer, ESCAPE_CHAR, ESCAPE_CHAR + ESCAPE_CHAR);

        // When in a paragraph we need to escape symbols that are at beginning of lines and that could be confused
        // with list items, headers or tables.
        if (blockStateListener.isInLine() && isOnNewLine()) {

            // Look for list pattern at beginning of line and escape the first character only (it's enough)
            escapeFirstMatchedCharacter(LIST_PATTERN, accumulatedBuffer);

            // Look for header pattern at beginning of line and escape the first character only (it's enough)
            escapeFirstMatchedCharacter(HEADER_PATTERN, accumulatedBuffer);

            // Look for quote pattern at beginning of line and escape the first character only (it's enough)
            escapeFirstMatchedCharacter(QUOTE_PATTERN, accumulatedBuffer);
        }

        // Escape reserved keywords
        Matcher matcher = RESERVED_CHARS_PATTERN.matcher(accumulatedBuffer.toString());
        for (int i = 0; matcher.find(); i = i + matcher.end() - matcher.start() + 1) {
            accumulatedBuffer.replace(matcher.start() + i, matcher.end() + i, ESCAPE_CHAR + matcher.group().charAt(0)
                + ESCAPE_CHAR + matcher.group().charAt(1));
        }

        // TODO: Handle escaping link syntax, i.e. |(?<!\)[.*]\(.*\)
    }

    private void replaceAll(StringBuffer accumulatedBuffer, String match, String replacement)
    {
        int pos = -replacement.length();
        while ((pos + replacement.length() < accumulatedBuffer.length())
            && ((pos = accumulatedBuffer.indexOf(match, pos + replacement.length())) != -1)) {
            accumulatedBuffer.replace(pos, pos + match.length(), replacement);
        }
    }

    private void escapeFirstMatchedCharacter(Pattern pattern, StringBuffer accumulatedBuffer)
    {
        Matcher matcher = pattern.matcher(accumulatedBuffer);
        if (matcher.lookingAt()) {
            // Escape the first character
            accumulatedBuffer.replace(matcher.start(1), matcher.start(1) + 1, ESCAPE_CHAR + matcher.group(1).charAt(0));
        }
    }
}
