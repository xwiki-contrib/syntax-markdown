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

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.rendering.markdown.commonmark12.internal.MarkdownConfiguration;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.misc.Extension;

/**
 * Convert listener events to Markdown 1.2.
 *
 * @version $Id$
 * @since 8.4
 */
public class Markdown12ChainingRenderer extends Markdown11ChainingRenderer
{
    private static final String STRIKEDOUT_SYMBOL = "~~";

    private static final char QUOTE = '"';

    private static final String FORMAT_TWO_PARAMS = "[[%s|%s]]";

    private static final String FORMAT_THREE_PARAMS = "[[%s|%s|%s]]";

    private static final String PERCENT_ENCODING = "%%%02X";

    private static final char DEL = 127;

    private static final Pattern NEW_LINES = Pattern.compile("\\r\\n|\\r|\\n");

    /**
     * See https://spec.commonmark.org/0.29/#autolinks.
     */
    private static final Pattern URI_AUTOLINK = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]{1,31}:[^\\s<>\\p{Cntrl}]*");

    // The repetition of the domain labels is possessive to avoid a stack overflow on large content (it doesn't change
    // the matched strings since backtracking into it can't lead to a match).
    private static final Pattern EMAIL_AUTOLINK = Pattern.compile("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]"
        + "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*+");

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
            String rawReference = reference.getReference();
            // Now decide if we should use an autolink or not
            if (StringUtils.isNotEmpty(label)) {
                printLink(label, escapeLinkReference(rawReference));
            } else if (isAutoLink(reference)) {
                // Don't output the type prefix.
                printAutoLink(rawReference);
            } else {
                // The reference can't be written using the autolink syntax.
                String linkReference = ResourceType.MAILTO.equals(reference.getType())
                    ? ResourceType.MAILTO.getScheme() + ':' + rawReference : rawReference;
                printLink(escapeText(rawReference), escapeLinkReference(linkReference));
            }
        } else {
            printWikiLink(label, escapeWikiLinkReference(this.linkReferenceSerializer.serialize(reference)),
                reference.getParameters());
        }
    }

    private boolean isAutoLink(ResourceReference reference)
    {
        Pattern pattern = ResourceType.MAILTO.equals(reference.getType()) ? EMAIL_AUTOLINK : URI_AUTOLINK;
        return pattern.matcher(reference.getReference()).matches();
    }

    private String escapeText(String text)
    {
        MarkdownEscapeHandler escapeHandler = new MarkdownEscapeHandler();
        escapeHandler.setOnNewLine(false);
        escapeHandler.setInLinkLabel(true);
        StringBuffer buffer = new StringBuffer(removeNewLines(text));
        escapeHandler.escape(buffer, null);
        return buffer.toString();
    }

    private String escapeLinkReference(String rawReference)
    {
        // Escape the characters that would end the link destination or that would make it invalid.
        // e.g. [label](https://en.wikipedia.org/Some_Subject_\(With_Title\))
        // See https://spec.commonmark.org/0.29/#link-destination
        StringBuilder builder = new StringBuilder(rawReference.length());
        for (int i = 0; i < rawReference.length(); i++) {
            char c = rawReference.charAt(i);
            // Note that a link destination starting with '<' ends with the first '>'.
            if (c == '(' || c == ')' || (i == 0 && c == '<')) {
                builder.append(MarkdownEscapeHandler.ESCAPE_CHAR).append(c);
            } else {
                appendURICharacter(builder, c);
            }
        }
        return builder.toString();
    }

    private String encodeImageReference(String rawReference)
    {
        // The image destination is not unescaped by the parser so we use percent-encoding instead.
        StringBuilder builder = new StringBuilder(rawReference.length());
        for (int i = 0; i < rawReference.length(); i++) {
            char c = rawReference.charAt(i);
            if (c == '(' || c == ')' || (i == 0 && c == '<')) {
                builder.append(String.format(PERCENT_ENCODING, (int) c));
            } else {
                appendURICharacter(builder, c);
            }
        }
        return builder.toString();
    }

    private void appendURICharacter(StringBuilder builder, char c)
    {
        // Spaces and control characters are not allowed in link destinations.
        if (c <= ' ' || c == DEL) {
            builder.append(String.format(PERCENT_ENCODING, (int) c));
        } else {
            builder.append(c);
        }
    }

    /**
     * Escape the closing brackets that could end the wiki link (the content of wiki links is not unescaped by the
     * parser but backslash escapes are taken into account when looking for the end of the wiki link).
     */
    private String escapeWikiLinkReference(String reference)
    {
        return StringUtils.replace(removeNewLines(reference), "]", MarkdownEscapeHandler.ESCAPE_CHAR + ']');
    }

    /**
     * Replace the new lines since a blank line would end the paragraph and thus the link or image syntax.
     */
    private String removeNewLines(String text)
    {
        return NEW_LINES.matcher(text).replaceAll(" ");
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

        if (ResourceType.URL.equals(reference.getType())) {
            print(String.format("![%s](%s)", escapeText(alt), encodeImageReference(reference.getReference())));
        } else {
            print(String.format("![[%s|%s]]", escapeWikiLinkReference(alt),
                escapeWikiLinkReference(reference.getReference())));
        }
    }

    protected void printWikiLink(String label, String serializedReference, Map<String, String> parameters)
    {
        if (StringUtils.isEmpty(label)) {
            if (parameters.isEmpty()) {
                printWikiLink(serializedReference);
            } else {
                print(String.format(FORMAT_TWO_PARAMS, serializedReference, serializeParameters(parameters)));
            }
        } else {
            String wikiLinkLabel = escapeWikiLinkLabel(label);
            if (parameters.isEmpty()) {
                print(String.format(FORMAT_TWO_PARAMS, wikiLinkLabel, serializedReference));
            } else {
                print(String.format(FORMAT_THREE_PARAMS, wikiLinkLabel, serializedReference,
                    serializeParameters(parameters)));
            }
        }
    }

    private String escapeWikiLinkLabel(String label)
    {
        // The wiki link label ends at the first pipe character (which can't be escaped with a backslash), but HTML
        // entities are supported.
        return label.replace("|", "&#124;");
    }

    private String serializeParameters(Map<String, String> parameters)
    {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = parameters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(entry.getKey()).append('=').append(QUOTE)
                .append(escapeWikiLinkReference(entry.getValue())).append(QUOTE);
            if (iterator.hasNext()) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    @Override
    protected MarkdownMacroRenderer createMacroPrinter()
    {
        return new Markdown12MacroRenderer();
    }

    private boolean isStrikethroughSupported()
    {
        for (Extension extension : Parser.EXTENSIONS.get(this.configuration.getOptions())) {
             if (extension instanceof StrikethroughExtension || extension instanceof StrikethroughSubscriptExtension) {
                 return true;
             }
        }
        return false;
    }

    protected boolean handleHtmlMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        boolean isHandled = false;

        if (id.equals("html")) {
            String html = StringUtils.defaultString(createMacroPrinter().renderMacro(id, parameters, content,
                isInline));
            MarkdownEscapeWikiPrinter printer = getMarkdownPrinter();

            // Keep the HTML tags verbatim and only escape the text that would be parsed as Markdown.
            if (isInline) {
                // Print what comes before so that the escaping knows if the HTML is located at the beginning of a
                // line.
                if (!html.isEmpty()) {
                    printPendingSeparator(html);
                }
                printer.flush(html);
                print(printer.escapeInlineHTML(html));
            } else {
                printEmptyLine();
                print(printer.escapeBlockHTML(html));
            }
            isHandled = true;
        }

        return isHandled;
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        if (handleHtmlMacro(id, parameters, content, isInline)) {
            return;
        }

        super.onMacro(id, parameters, content, isInline);
    }
}
