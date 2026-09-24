package com.alertapp.pp

import android.content.Context

object Checker {
    private const val DAY = 24L * 60 * 60 * 1000

    class Outcome(val ok: Boolean, val newCount: Int, val error: String?)

    /**
     * Busca as matérias, guarda as novas e notifica as que contêm palavra-chave.
     * @param deep true = percorre várias páginas do feed para cobrir todo o período configurado.
     */
    @Synchronized
    fun run(context: Context, deep: Boolean): Outcome {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        val cutoff = now - prefs.days * DAY

        return try {
            val firstRun = !prefs.initialized
            val pages = if (deep || firstRun) 10 else 2
            val result = Fetcher.fetch(prefs.url, cutoff, pages)

            val keywords = prefs.keywords
            val known = HashMap<String, Article>()
            prefs.articles.forEach { known[it.link] = it }

            val toNotify = ArrayList<Pair<Article, String>>()
            var newCount = 0
            for (a in result.articles) {
                if (known.containsKey(a.link)) continue
                val art = if (a.date <= 0L) a.copy(date = now) else a
                known[art.link] = art
                newCount++
                if (!firstRun && art.date >= cutoff) {
                    val kw = KeywordMatcher.firstMatch(art.title, keywords)
                    if (kw != null) toNotify.add(art to kw)
                }
            }

            val keepFrom = now - maxOf(30, prefs.days + 2) * DAY
            prefs.articles = known.values
                .filter { it.date >= keepFrom }
                .sortedByDescending { it.date }
                .take(500)
            prefs.initialized = true
            prefs.lastCheck = now
            prefs.lastError = null
            prefs.lastSource = result.source

            Notifier.notify(ctx, toNotify)
            Outcome(true, newCount, null)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            prefs.lastError = msg
            Outcome(false, 0, msg)
        }
    }
}
