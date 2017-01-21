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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.rendering.markdown.markdown12.MarkdownConfiguration;
import org.xwiki.contrib.rendering.markdown10.internal.renderer.MarkdownEscapeWikiPrinter;
import org.xwiki.contrib.rendering.markdown11.internal.renderer.Markdown11ChainingRenderer;
import org.xwiki.contrib.rendering.markdown11.internal.renderer.MarkdownMacroRenderer;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

import com.vladsch.flexmark.Extension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.parser.Parser;

/**
 * Convert listener events to Markdown 1.2.
 *
 * @version $Id$
 * @since 8.4
 */
public class Markdown12ChainingRenderer extends Markdown11ChainingRenderer
{
    private static final String STRIKEDOUT_SYMBOL = "~~";

    private MarkdownConfiguration configuration;

    private boolean isStrikethroughSupported;

    /**
     * @param listenerChain the chain of listener filters used to compute various states
     * @param linkReferenceSerializer the component to use for converting {@link ResourceReference} links to strings
     * @param imageReferenceSerializer the component to use for converting {@link ResourceReference} images to strings
     */
    public Markdown12ChainingRenderer(ListenerChain listenerChain,
        ResourceReferenceSerializer linkReferenceSerializer, ResourceReferenceSerializer imageReferenceSerializer,
            MarkdownConfiguration configuration)
    {
        super(listenerChain, linkReferenceSerializer, imageReferenceSerializer);
        this.configuration = configuration;
        this.isStrikethroughSupported = isStrikethroughSupported();
    }

    @Override
    public void beginFormat(Format format, Map<String, String> parameters)
    {
        if (format.equals(Format.STRIKEDOUT) && this.isStrikethroughSupported) {
            print(STRIKEDOUT_SYMBOL);
        } else {
            // Override from Markdown11ChainingRenderer since there's no need to escape space characters with
            //flexmark-java.
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
        }
    }

    @Override
    public void endFormat(Format format, Map<String, String> parameters)
    {
        if (format.equals(Format.STRIKEDOUT) && this.isStrikethroughSupported) {
            print(STRIKEDOUT_SYMBOL);
        } else {
            // Override from Markdown11ChainingRenderer since there's no need to escape space characters with
            //flexmark-java.
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
        }
    }

    @Override
    public void endLink(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        // Overridden from Markdown11ChainingRenderer since in markdown/1.2 we've changed the link syntax to use
        // depending on the reference type:
        // - [...](...) and <...> for URL and mailto
        // - [[...]] for Document, UNC, DataURI and all other types or references

        MarkdownEscapeWikiPrinter linkBlocksPrinter = getMarkdownPrinter();
        linkBlocksPrinter.flush();
        String label = linkBlocksPrinter.toString();
        popPrinter();

        if (ResourceType.URL.equals(reference.getType()) || ResourceType.MAILTO.equals(reference.getType())) {
            // Now decide if we should use an autolink or not
            // TODO: Handle escapes (for example if the label contains a "]")
            if (StringUtils.isEmpty(label)) {
                // Don't output the type prefix.
                printAutoLink(reference.getReference());
            } else {
                printLink(label, this.linkReferenceSerializer.serialize(reference));
            }
        } else {
            // TODO: Handle escapes (for example if the label contains a "|")
            printWikiLink(label, this.linkReferenceSerializer.serialize(reference));
        }
    }

    @Override
    public void onImage(ResourceReference reference, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        // Overridden from Markdown11ChainingRenderer since in markdown/1.2 we've changed the image syntax to use
        // depending on the reference type:
        // - [...](...) and <...> for URL
        // - [[...]] for Attach and all other types or references

        String alt = parameters.get("alt");
        if (StringUtils.isBlank(alt)) {
            alt = reference.getReference();
        }

        // TODO: Handle escapes
        if (ResourceType.URL.equals(reference.getType())) {
            print(String.format("![%s](%s)", alt, reference.getReference()));
        } else {
            print(String.format("![[%s|%s]]", alt, reference.getReference()));
        }
    }


    protected void printWikiLink(String label, String serializedReference)
    {
        // TODO: Handle escapes (for example if the label contains a "|")
        if (StringUtils.isEmpty(label)) {
            printWikiLink(serializedReference);
        } else {
            print(String.format("[[%s|%s]]", label, serializedReference));
        }
    }

    @Override
    protected MarkdownMacroRenderer createMacroPrinter()
    {
        return new Markdown12MacroRenderer();
    }

    private boolean isStrikethroughSupported()
    {
        for (Extension extension : this.configuration.getOptions().get(Parser.EXTENSIONS)) {
             if (extension instanceof StrikethroughExtension || extension instanceof StrikethroughSubscriptExtension) {
                 return true;
             }
        }
        return false;
    }
}
