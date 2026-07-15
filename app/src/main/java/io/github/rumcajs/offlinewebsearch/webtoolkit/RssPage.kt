package io.github.rumcajs.offlinewebsearch.webtoolkit

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.lang.StringBuilder
import io.github.rumcajs.offlinewebsearch.data.Entry

class RssPage(val link: String, val contents: String) : Page {
    private var feedTitle: String? = null
    private var feedDescription: String? = null
    private val entries = mutableListOf<RssEntry>()
    // TODO read from channel -> thumbnails
    private val thumbnails = mutableListOf<String>()


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
                var entryTitle: String? = null
                var entryDescription: String? = null
                var entryThumbnail: String? = null
                var entryDatePublished: String? = null
                var currentText = StringBuilder()

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
                                entryTitle = null
                                entryDescription = null
                                entryThumbnail = null
                                entryDatePublished = null
                            }

                            // Atom: link is an attribute
                            if (isAtom && tag == "link" && inEntry) {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null && entryLink == null) {
                                    entryLink = href
                                }
                            }

                            // media:thumbnail or enclosure url attribute
                            if ((tag == "thumbnail" || tag == "enclosure") && inEntry) {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null && entryThumbnail == null) {
                                    entryThumbnail = url
                                }
                            }

                            currentText = StringBuilder()
                        }

                        XmlPullParser.TEXT -> {
                            currentText.append(parser.text ?: "")
                        }

                        XmlPullParser.END_TAG -> {
                            val tag = parser.name ?: ""
                            val text = currentText.toString().trim()

                            if (inEntry) {
                                when (tag) {
                                    "title" -> {
                                        if (entryTitle == null) {
                                            entryTitle = text.ifEmpty { null }
                                        }
                                    }
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
                                    "url" -> {
                                        if (entryThumbnail == null) {
                                            entryThumbnail = text.ifEmpty { null }
                                        }
                                    }
                                    "pubDate", "published", "updated", "dc:date" -> {
                                        if (entryDatePublished == null) {
                                            entryDatePublished = text.ifEmpty { null }
                                        }
                                    }
                                }

                                val isEndItemTag = tag == "item" || (isAtom && tag == "entry")
                                if (isEndItemTag) {
                                    entries.add(RssEntry(entryLink, entryTitle, entryDescription, entryThumbnail, entryDatePublished))
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

                            currentText = StringBuilder()
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getTitle(): String? = feedTitle
    override fun getDescription(): String? = feedDescription
    override fun getDatePublished(): String? = null
    override fun getThumbnails(): List<String> = thumbnails
    override fun getEntries(): List<Entry> {
        return entries.map {
            Entry(
                link = it.link,
                title = it.title,
                description = it.description,
                thumbnail = it.thumbnail,
                date_published = it.datePublished
            )
        }
    }
}

class RssEntry(
    val link: String?,
    val title: String? = null,
    val description: String?,
    val thumbnail: String? = null,
    val datePublished: String? = null
)
