package com.alertapp.pp

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object Fetcher {
    class Result(val articles: List<Article>, val source: String)

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val TAGS = Regex("<[^>]+>")
    private val WS = Regex("[\\s\\u00A0]+")
    private val ARTICLE_BLOCK = Regex("<article\\b[\\s\\S]*?</article>", RegexOption.IGNORE_CASE)
    private val HEADING_LINK = Regex(
        """<h[1-4][^>]*>\s*(?:<(?!a\b)[^>]+>\s*)*<a\s[^>]*?href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_ATTR = Regex("""<time[^>]*datetime=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * Tenta o feed RSS (com paginação até cobrir o período); se falhar, lê o HTML da página.
     */
    fun fetch(pageUrl: String, cutoff: Long, maxPages: Int): Result {
        var rssError: Exception? = null

        for (feed in feedCandidates(pageUrl)) {
            try {
                val all = ArrayList<Article>()
                var page = 1
                while (page <= maxPages) {
                    val url = if (page == 1) feed else withParam(feed, "paged", page)
                    val items = try {
                        parseRss(download(url))
                    } catch (e: Exception) {
                        if (page == 1) throw e else break
                    }
                    if (items.isEmpty()) break
                    all.addAll(items)
                    val dated = items.filter { it.date > 0 }
                    if (dated.isNotEmpty() && dated.minOf { it.date } < cutoff) break
                    page++
                }
                if (all.isNotEmpty()) return Result(all.distinctBy { it.link }, "RSS")
            } catch (e: Exception) {
                rssError = e
            }
        }

        // Plano B: HTML
        val base = pageUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        val all = ArrayList<Article>()
        var page = 1
        while (page <= maxPages) {
            val url = if (page == 1) pageUrl else "$base/page/$page/"
            val items = try {
                parseHtml(download(url), url)
            } catch (e: Exception) {
                if (page == 1) throw IOException(describe(e) + (rssError?.let { " (RSS: ${describe(it)})" } ?: ""))
                else break
            }
            if (items.isEmpty()) break
            all.addAll(items)
            val dated = items.filter { it.date > 0 }
            if (dated.isEmpty() || dated.minOf { it.date } < cutoff) break
            page++
        }
        if (all.isEmpty()) throw IOException("Nenhuma matéria encontrada no endereço informado")
        return Result(all.distinctBy { it.link }, "HTML")
    }

    private fun describe(e: Exception) = e.message ?: e.javaClass.simpleName

    private fun feedCandidates(pageUrl: String): List<String> {
        val base = pageUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        return listOf("$base/feed/", "$base?feed=rss2")
    }

    private fun withParam(url: String, key: String, value: Int) =
        url + (if (url.contains("?")) "&" else "?") + "$key=$value"

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/html;q=0.9, */*;q=0.8")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- RSS / Atom ----------
    private fun parseRss(xml: String): List<Article> {
        if (!xml.contains("<rss", true) && !xml.contains("<feed", true)) {
            throw IOException("Resposta não é um feed RSS")
        }
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))

        val out = ArrayList<Article>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var date: String? = null

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item", "entry" -> { inItem = true; title = null; link = null; date = null }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem) {
                        val href = p.getAttributeValue(null, "href")
                        val text = p.nextText()
                        link = if (!href.isNullOrBlank()) href else text
                    }
                    "pubDate", "published", "updated" -> if (inItem) {
                        val t = p.nextText()
                        if (date == null) date = t
                    }
                }
            } else if (event == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
                val t = title?.let { clean(it) }
                val l = link?.trim()
                if (!t.isNullOrEmpty() && !l.isNullOrEmpty()) out.add(Article(t, l, parseDate(date)))
                inItem = false
            }
            event = p.next()
        }
        return out
    }

    // ---------- HTML ----------
    private fun parseHtml(html: String, baseUrl: String): List<Article> {
        val out = LinkedHashMap<String, Article>()

        fun add(m: MatchResult, dateStr: String?) {
            val link = absolute(baseUrl, m.groupValues[1]) ?: return
            val title = clean(m.groupValues[2])
            if (title.length < 8 || out.containsKey(link)) return
            out[link] = Article(title, link, parseDate(dateStr))
        }

        val blocks = ARTICLE_BLOCK.findAll(html).map { it.value }.toList()
        if (blocks.isNotEmpty()) {
            for (b in blocks) {
                val m = HEADING_LINK.find(b) ?: continue
                add(m, TIME_ATTR.find(b)?.groupValues?.get(1))
            }
        } else {
            for (m in HEADING_LINK.findAll(html)) add(m, null)
        }
        return out.values.toList()
    }

    private fun absolute(base: String, href: String): String? = try {
        val u = URL(URL(base), href.trim()).toString()
        if (u.startsWith("http")) u else null
    } catch (e: Exception) {
        null
    }

    // ---------- utilidades ----------
    private fun clean(s: String): String {
        val noTags = s.replace(TAGS, " ")
        return Html.fromHtml(noTags, Html.FROM_HTML_MODE_LEGACY).toString().replace(WS, " ").trim()
    }

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    private fun parseDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val t = s.trim()
        for (f in DATE_FORMATS) {
            try {
                val d = SimpleDateFormat(f, Locale.US).parse(t)
                if (d != null) return d.time
            } catch (e: Exception) {
                // tenta o próximo formato
            }
        }
        return 0L
    }
}
