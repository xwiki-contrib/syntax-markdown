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
package org.xwiki.rendering.internal.renderer.markdown11;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.internal.renderer.markdown.MarkdownChainingRenderer;
import org.xwiki.rendering.internal.renderer.markdown.MarkdownEscapeHandler;
import org.xwiki.rendering.internal.renderer.markdown.MarkdownEscapeWikiPrinter;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

/**
 * Convert listener events to Markdown 1.1.
 *
 * @version $Id: 7e81a3f18a0ac30eed9501b432a6a99b0e0e12b4 $
 * @since 8.1RC1
 */
public class Markdown11ChainingRenderer extends MarkdownChainingRenderer
{
    private static final String SUPERSCRIPT_SYMBOL = "^";

    private static final String SUBSCRIPT_SYMBOL = "~";

    private MarkdownMacroRenderer macroPrinter;

    /**
     * @param listenerChain the chain of listener filters used to compute various states
     * @param linkReferenceSerializer the component to use for converting {@link ResourceReference} links to strings
     * @param imageReferenceSerializer the component to use for converting {@link ResourceReference} images to strings
     */
    public Markdown11ChainingRenderer(ListenerChain listenerChain,
        ResourceReferenceSerializer linkReferenceSerializer, ResourceReferenceSerializer imageReferenceSerializer)
    {
        super(listenerChain, linkReferenceSerializer, imageReferenceSerializer);
        this.macroPrinter = new MarkdownMacroRenderer();
    }

    @Override
    public void beginFormat(Format format, Map<String, String> parameters)
    {
        switch (format) {
            case SUPERSCRIPT:
                print(SUPERSCRIPT_SYMBOL);
                break;
            case SUBSCRIPT:
                print(SUBSCRIPT_SYMBOL);
                break;
            default:
                super.beginFormat(format, parameters);
        }

        // Accumulate all content thereafter so that we can escape any space inside superscript or subscriptin since
        // MD Extra doesn't support this.
        switch (format) {
            case SUPERSCRIPT:
            case SUBSCRIPT:
                pushPrinter(createMarkdownPrinter(new DefaultWikiPrinter()));
                break;
            default:
        }
    }

    @Override
    public void endFormat(Format format, Map<String, String> parameters)
    {
        switch (format) {
            case SUPERSCRIPT:
                print(SUPERSCRIPT_SYMBOL);
                break;
            case SUBSCRIPT:
                print(SUBSCRIPT_SYMBOL);
                break;
            default:
                super.endFormat(format, parameters);
        }

        switch (format) {
            case SUPERSCRIPT:
            case SUBSCRIPT:
                // Escape any space in the accumulated text, see #beginFormat()
                MarkdownEscapeWikiPrinter formatPrinter = getMarkdownPrinter();
                formatPrinter.flush();
                String text = formatPrinter.toString();
                popPrinter();
                print(StringUtils.replace(text, " ", MarkdownEscapeHandler.ESCAPE_CHAR + " "));
                break;
            default:
        }
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        // Handle Code macro in a specific way
        if (handleCodeMacro(id, parameters, content, isInline)) {
            return;
        }

        // Handle standard macros
        if (!isInline) {
            printEmptyLine();
        }
        print(getMacroPrinter().renderMacro(id, parameters, content, isInline));
    }

    private MarkdownMacroRenderer getMacroPrinter()
    {
        return this.macroPrinter;
    }
}
