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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * The parameters of the link references that are written in the parameters part of the wiki link syntax, same as the
 * XWiki Syntax: the anchor and the query string of the document references, the query string of the attachment
 * references. The other parameters of the link references are part of the serialized references.
 *
 * @version $Id$
 * @since 8.10.0
 */
public final class WikiLinkReferenceParameters
{
    /**
     * The parameters of the link references written in the parameters part of the wiki link syntax, in this order.
     */
    public static final List<String> NAMES = Collections.unmodifiableList(
        Arrays.asList(DocumentResourceReference.QUERY_STRING, DocumentResourceReference.ANCHOR));

    private WikiLinkReferenceParameters()
    {
        // Utility class.
    }

    /**
     * @param type the type of a link reference
     * @param name the name of a parameter
     * @return {@code true} if the parameter is a parameter of the link reference written in the parameters part of
     *     the wiki link syntax, {@code false} otherwise
     */
    public static boolean isReferenceParameter(ResourceType type, String name)
    {
        boolean result;
        if (ResourceType.DOCUMENT.equals(type) || ResourceType.SPACE.equals(type) || ResourceType.PAGE.equals(type)) {
            result = DocumentResourceReference.ANCHOR.equals(name)
                || DocumentResourceReference.QUERY_STRING.equals(name);
        } else if (ResourceType.ATTACHMENT.equals(type) || ResourceType.PAGE_ATTACHMENT.equals(type)) {
            result = AttachmentResourceReference.QUERY_STRING.equals(name);
        } else {
            result = false;
        }
        return result;
    }
}
