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

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataHolder;

/**
 * This extension provides DeepInlineHTMLPostProcessor as a parsing post processor.
 *
 * @version $Id$
 * @since 8.9
 */
public class DeepInlineHTMLExtension implements Parser.ParserExtension
{
    @Override
    public void parserOptions(MutableDataHolder mutableDataHolder)
    {

    }

    @Override
    public void extend(Parser.Builder builder)
    {
        builder.postProcessorFactory(new DeepInlineHTMLPostProcessor.Factory(builder));
    }

    /**
     * Creates an instance of DeepInlineHTMLExtension.
     *
     * @return a new instance of this extension
     */
    public static DeepInlineHTMLExtension create()
    {
        return new DeepInlineHTMLExtension();
    }
}
