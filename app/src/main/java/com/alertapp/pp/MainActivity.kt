package com.alertapp.pp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: ArticleAdapter
    private lateinit var header: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tvStatus: TextView
    private lateinit var settingsCard: View
    private lateinit var etUrl: EditText
    private lateinit var etInterval: EditText
    private lateinit var etDays: EditText
    private lateinit var etKeyword: EditText
    private lateinit var chipGroup: ChipGroup

    private val keywords = mutableListOf<String>()
    private val executor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val list = findViewById<RecyclerView>(R.id.list)
        swipe = findViewById(R.id.swipe)
        header = layoutInflater.inflate(R.layout.header, list, false)
        adapter = ArticleAdapter { openLink(it.link) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ConcatAdapter(SingleViewAdapter(header), adapter)

        bindHeader()
        swipe.setOnRefreshListener { refresh(deep = true) }

        Notifier.ensureChannel(this)
        requestNotificationPermission()
        Scheduler.schedule(this, replace = false)

        render()
        refresh(deep = false)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) render()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun bindHeader() {
        tvStatus = header.findViewById(R.id.tvStatus)
        settingsCard = header.findViewById(R.id.settingsCard)
        etUrl = header.findViewById(R.id.etUrl)
        etInterval = header.findViewById(R.id.etInterval)
        etDays = header.findViewById(R.id.etDays)
        etKeyword = header.findViewById(R.id.etKeyword)
        chipGroup = header.findViewById(R.id.chipGroup)

        etUrl.setText(prefs.url)
        etInterval.setText(prefs.intervalMinutes.toString())
        etDays.setText(prefs.days.toString())
        keywords.clear()
        keywords.addAll(prefs.keywords)
        keywords.forEach { addChip(it) }

        header.findViewById<View>(R.id.btnSettings).setOnClickListener {
            settingsCard.visibility = if (settingsCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        header.findViewById<View>(R.id.btnAdd).setOnClickListener { addKeyword() }
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addKeyword(); true } else false
        }
        header.findViewById<View>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun addKeyword() {
        val word = etKeyword.text.toString().trim()
        if (word.isEmpty()) return
        val exists = keywords.any { KeywordMatcher.norm(it) == KeywordMatcher.norm(word) }
        if (!exists) {
            keywords.add(word)
            addChip(word)
            prefs.keywords = keywords.toList()
            render()
        }
        etKeyword.setText("")
    }

    private fun addChip(word: String) {
        val chip = Chip(this)
        chip.text = word
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            chipGroup.removeView(chip)
            keywords.remove(word)
            prefs.keywords = keywords.toList()
            render()
        }
        chipGroup.addView(chip)
    }

    private fun save() {
        var url = etUrl.text.toString().trim()
        if (url.isEmpty()) url = Prefs.DEFAULT_URL
        if (!url.contains("://")) url = "https://$url"
        if (url.startsWith("http://", true)) url = "https://" + url.substring(7)

        val interval = (etInterval.text.toString().toIntOrNull() ?: 60).coerceAtLeast(15)
        val days = (etDays.text.toString().toIntOrNull() ?: 5).coerceIn(1, 60)

        if (url != prefs.url) {
            prefs.articles = emptyList()
            prefs.initialized = false
            prefs.lastError = null
        }
        prefs.url = url
        prefs.intervalMinutes = interval
        prefs.days = days
        prefs.keywords = keywords.toList()

        etUrl.setText(url)
        etInterval.setText(interval.toString())
        etDays.setText(days.toString())

        Scheduler.schedule(this, replace = true)
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(header.windowToken, 0)
        currentFocus?.clearFocus()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()

        render()
        refresh(deep = true)
    }

    private fun refresh(deep: Boolean) {
        swipe.post { swipe.isRefreshing = true }
        executor.execute {
            Checker.run(applicationContext, deep)
            runOnUiThread {
                if (!isDestroyed) {
                    swipe.isRefreshing = false
                    render()
                }
            }
        }
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val cutoff = now - prefs.days * 24L * 60 * 60 * 1000
        val kws = prefs.keywords
        val rows = prefs.articles
            .filter { it.date >= cutoff }
            .sortedByDescending { it.date }
            .map { ArticleAdapter.Row(it, KeywordMatcher.firstMatch(it.title, kws)) }
        adapter.submit(rows)

        val lines = mutableListOf<String>()
        val error = prefs.lastError
        when {
            error != null -> lines.add(getString(R.string.status_error, error))
            prefs.lastCheck == 0L -> lines.add(getString(R.string.status_never))
            else -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(prefs.lastCheck))
                lines.add(getString(R.string.status_ok, time, prefs.lastSource ?: "-", rows.size))
            }
        }
        if (rows.isEmpty() && error == null && prefs.lastCheck != 0L) {
            lines.add(getString(R.string.status_empty, prefs.days))
        }
        tvStatus.text = lines.joinToString("\n")
    }

    private fun openLink(link: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
