package com.alertapp.pp

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleAdapter(private val onClick: (Article) -> Unit) :
    RecyclerView.Adapter<ArticleAdapter.VH>() {

    data class Row(val article: Article, val keyword: String?)

    private var rows: List<Row> = emptyList()
    private val absFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val strip: View = view.findViewById(R.id.strip)
        val meta: TextView = view.findViewById(R.id.tvMeta)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val keyword: TextView = view.findViewById(R.id.tvKeyword)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_article, parent, false)
        val vh = VH(v)
        v.setOnClickListener {
            val pos = vh.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(rows[pos].article)
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        val ctx = h.itemView.context
        val hit = row.keyword != null

        h.title.text = row.article.title
        val ms = row.article.date
        val rel = DateUtils.getRelativeTimeSpanString(
            ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        )
        h.meta.text = "$rel · ${absFormat.format(Date(ms))}"

        h.strip.visibility = if (hit) View.VISIBLE else View.GONE
        h.keyword.visibility = if (hit) View.VISIBLE else View.GONE
        if (hit) h.keyword.text = ctx.getString(R.string.keyword_hit, row.keyword)
        h.card.setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (hit) R.color.highlight_bg else R.color.card_bg)
        )
    }

    override fun getItemCount() = rows.size
}

/** Mostra uma única View pré-inflada (usada como cabeçalho da lista). */
class SingleViewAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(view) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
    override fun getItemCount() = 1
}
