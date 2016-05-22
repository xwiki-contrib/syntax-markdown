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
package org.xwiki.rendering.internal.renderer.markdown.reference;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.xwiki.rendering.internal.renderer.ParametersPrinter;
import org.xwiki.rendering.internal.renderer.markdown.MarkdownEscapeWikiPrinter;
import org.xwiki.rendering.internal.renderer.xwiki20.XWikiSyntaxListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

/**
 * Logic to render a Resource Reference (image or link) into Markdown 1.0.
 *
 * @version $Id: c830e0a738e6301eac4b73fb62334e28701e7df3 $
 * @since 8.1RC1
 */
public class MarkdownResourceRenderer
{
    protected ParametersPrinter parametersPrinter = new ParametersPrinter();

    private Deque<Boolean> forceFullSyntax = new ArrayDeque<Boolean>();

    private XWikiSyntaxListenerChain listenerChain;

    private ResourceReferenceSerializer referenceSerializer;

    public MarkdownResourceRenderer(XWikiSyntaxListenerChain listenerChain,
        ResourceReferenceSerializer referenceSerializer)
    {
        this.listenerChain = listenerChain;
        this.referenceSerializer = referenceSerializer;
        this.forceFullSyntax.push(false);
    }

    public String serialize(ResourceReference reference, boolean isFreeStanding)
    {
        String result = this.referenceSerializer.serialize(reference);

        if (!isFreeStanding) {
            result = result.replace("\\", "\\\\");
        }

        return result;
    }

    public void beginRenderLink(MarkdownEscapeWikiPrinter printer, ResourceReference reference,
        boolean isFreeStandingURI, Map<String, String> parameters)
    {
        printer.print("[");


    }

    public void endRenderLink(MarkdownEscapeWikiPrinter printer, ResourceReference reference,
        boolean isFreeStandingURI, Map<String, String> parameters)
    {
        printer.print(serialize(reference, isFreeStandingURI));

        // If there were parameters specified, print them
//        printParameters(printer, reference, parameters);

        if (this.forceFullSyntax.peek() || !isFreeStandingURI) {
            printer.print("]]");
        }

        this.forceFullSyntax.pop();
    }

    /*
    protected void printParameters(MarkdownEscapeWikiPrinter printer, ResourceReference resourceReference,
        Map<String, String> parameters)
    {
        // If there were parameters specified, output them separated by the "||" characters
        if (!parameters.isEmpty()) {
            printer.print(PARAMETER_SEPARATOR);
            printer.print(this.parametersPrinter.print(parameters, '~'));
        }
    }
    */
}
