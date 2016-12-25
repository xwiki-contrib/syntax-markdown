# Markdown Syntaxes

Parsers and Renderers for Markdown syntaxes.

* Project Lead: [Vincent Massol](http://www.xwiki.org/xwiki/bin/view/XWiki/VincentMassol)
* [Documentation & Downloads](http://extensions.xwiki.org/xwiki/bin/view/Extension/Markdown+Syntaxes)
* [Issue Tracker](http://jira.xwiki.org/browse/MARKDOWN)
* Communication: [Mailing List](http://dev.xwiki.org/xwiki/bin/view/Community/MailingLists), [IRC](http://dev.xwiki.org/xwiki/bin/view/Community/IRC)
* [Development Practices](http://dev.xwiki.org)
* Minimal XWiki version supported: XWiki 8.4
* License: LGPL 2.1
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](http://ci.xwiki.org/buildStatus/icon?job=Contrib%20-%20Markdown)](http://ci.xwiki.org/job/Contrib%20-%20Markdown/)

## Changes in `markdown/1.2` vs `markdown/1.1`

* Based on [Common Mark 0.27](http://spec.commonmark.org/0.27/)
* With Extensions configurable and some configured by default:
  * WikiLinkExtension (also in `markdown/1.1`)
  * AutolinkExtension (also in `markdown/1.1`)
  * DefinitionExtension.class (also in `markdown/1.1`)
  * TablesExtension.class (also in `markdown/1.1`)
  * StrikethroughSubscriptExtension.class (also in `markdown/1.1` for the subscript part only)
  * SuperscriptExtension.class (also in `markdown/1.1`)
* Added support for strikethrough
* Spaces are allowed without neededing to be escaped in superscript/subscript
* Newlines in (X)HTML tags is supported and considered valid (X)HTML.
