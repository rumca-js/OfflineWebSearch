package io.github.rumcajs.offlinewebsearch.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class RssPage(val link: String, val contents: String) {
    private var feedTitle: String? = null
    private var feedDescription: String? = null
    private val entries = mutableListOf<RssEntry>()

    init {
        if (contents.isNotBlank()) {
            try {
                val factory = XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = true
                }
                val parser = factory.newPullParser()
                parser.setInput(StringReader(contents))

                var isAtom = false
                var inEntry = false
                var entryLink: String? = null
                var entryDescription: String? = null
                var currentText = java.lang.StringBuilder()

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name ?: ""
                            if (tag == "feed") {
                                isAtom = true
                            }

                            val isItemTag = tag == "item" || (isAtom && tag == "entry")
                            if (isItemTag) {
                                inEntry = true
                                entryLink = null
                                entryDescription = null
                            }

                            // Atom: link is an attribute
                            if (isAtom && tag == "link" && inEntry) {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null && entryLink == null) {
                                    entryLink = href
                                }
                            }

                            currentText = java.lang.StringBuilder()
                        }

                        XmlPullParser.TEXT -> {
                            currentText.append(parser.text ?: "")
                        }

                        XmlPullParser.END_TAG -> {
                            val tag = parser.name ?: ""
                            val text = currentText.toString().trim()

                            if (inEntry) {
                                when (tag) {
                                    "link" -> {
                                        if (!isAtom && entryLink == null) {
                                            entryLink = text.ifEmpty { null }
                                        }
                                    }
                                    "description", "summary", "content", "content:encoded" -> {
                                        if (entryDescription == null) {
                                            entryDescription = text.ifEmpty { null }
                                        }
                                    }
                                }

                                val isEndItemTag = tag == "item" || (isAtom && tag == "entry")
                                if (isEndItemTag) {
                                    entries.add(RssEntry(entryLink, entryDescription))
                                    inEntry = false
                                }
                            } else {
                                // Channel/Feed level metadata
                                when (tag) {
                                    "title" -> {
                                        if (feedTitle == null) {
                                            feedTitle = text.ifEmpty { null }
                                        }
                                    }
                                    "description", "subtitle" -> {
                                        if (feedDescription == null) {
                                            feedDescription = text.ifEmpty { null }
                                        }
                                    }
                                }
                            }

                            currentText = java.lang.StringBuilder()
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getTitle(): String? = feedTitle
    fun getDescription(): String? = feedDescription
    fun getEntries(): List<RssEntry> = entries
}

class RssEntry(
    val link: String?,
    val description: String?
)
