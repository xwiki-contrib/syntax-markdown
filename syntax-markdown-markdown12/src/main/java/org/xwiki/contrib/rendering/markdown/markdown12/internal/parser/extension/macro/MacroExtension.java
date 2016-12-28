package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.extension.macro;

import com.vladsch.flexmark.Extension;
import com.vladsch.flexmark.parser.Parser;

public class MacroExtension implements Parser.ParserExtension
{
    private MacroExtension()
    {
    }

    public static Extension create()
    {
        return new MacroExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder)
    {
        parserBuilder.customBlockParserFactory(new MacroBlockParser.Factory());
    }
}
