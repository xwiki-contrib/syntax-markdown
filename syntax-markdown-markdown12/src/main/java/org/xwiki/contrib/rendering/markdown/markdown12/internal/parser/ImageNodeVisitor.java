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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.reference.link.URILabelGenerator;

import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ext.wikilink.WikiImage;

public class ImageNodeVisitor extends AbstractNodeVisitor
{
    private ResourceReferenceParser imageResourceReferenceParser;

    private ComponentManager componentManager;

    public ImageNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners,
            ResourceReferenceParser imageResourceReferenceParser, ComponentManager componentManager,
            PrintRendererFactory plainRendererFactory)
    {
        super(visitor, listeners, plainRendererFactory);
        this.imageResourceReferenceParser = imageResourceReferenceParser;
        this.componentManager = componentManager;
    }

    public void visit(Image node)
    {
        ResourceReference reference = this.imageResourceReferenceParser.parse(String.valueOf(node.getUrl()));
        Map<String, String> parameters = new HashMap<>();

        // Handle alt text. Note that in order to have the same behavior as the XWiki Syntax 2.0+ we don't add the alt
        // parameter if its content is the same as the one that would be automatically generated by the XHTML Renderer.
        String computedAltValue = computeAltAttributeValue(reference);
        String extractedAltValue = extractText(node);
        if (StringUtils.isNotEmpty(extractedAltValue) && !extractedAltValue.equals(computedAltValue)) {
            parameters.put("alt", extractedAltValue);
        }

        // Handle optional title
        addTitle(parameters, String.valueOf(node.getTitle()));

        getListener().onImage(reference, false, parameters);
    }

    public void visit(ImageRef node)
    {
        if (!node.isDefined()) {
            // Non-existing reference, output the image reference as is, as plain text, e.g. "![image.png][invalidref]".
            getListener().onVerbatim(node.getChars().unescape(), true, Collections.emptyMap());
        } else {
            // Since XWiki doesn't support reference images, we generate a standard image instead
            Reference reference = node.getReferenceNode(getReferenceRepository());
            ResourceReference resourceReference = this.imageResourceReferenceParser.parse(
                String.valueOf(reference.getUrl()));

            // Handle an optional image title
            Map<String, String> parameters = Collections.EMPTY_MAP;
            if (StringUtils.isNotEmpty(reference.getTitle())) {
                parameters = Collections.singletonMap(TITLE_ATTRIBUTE, String.valueOf(reference.getTitle()));
            }

            getListener().onImage(resourceReference, false, parameters);
        }
    }

    public void visit(WikiImage node)
    {
        ResourceReference reference = this.imageResourceReferenceParser.parse(String.valueOf(node.getLink()));
        Map<String, String> parameters = new HashMap<>();

        // Handle alt text. Note that in order to have the same behavior as the XWiki Syntax 2.0+ we don't add the alt
        // parameter if its content is the same as the one that would be automatically generated by the XHTML Renderer.
        String computedAltValue = computeAltAttributeValue(reference);
        if(node.getText() != null) {
            String extractedAltValue = String.valueOf(node.getText());
            if (StringUtils.isNotEmpty(extractedAltValue) && !extractedAltValue.equals(computedAltValue)) {
                parameters.put("alt", extractedAltValue);
            }
        }

        getListener().onImage(reference, false, parameters);
    }

    /**
     * @param reference the reference for which to compute the alt attribute value
     * @return the alt attribute value that would get generated if not specified by the user
     */
    private String computeAltAttributeValue(ResourceReference reference)
    {
        String label;
        try {
            URILabelGenerator uriLabelGenerator = this.componentManager.getInstance(URILabelGenerator.class,
                reference.getType().getScheme());
            label = uriLabelGenerator.generateLabel(reference);
        } catch (ComponentLookupException e) {
            label = reference.getReference();
        }
        return label;
    }
}
