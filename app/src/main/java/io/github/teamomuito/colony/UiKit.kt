package io.github.teamomuito.colony

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small helpers for building the dark, parchment-and-amber UI programmatically. */
class UiKit(val a: Activity) {
    val density = a.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()
    fun dpf(v: Float) = (v * density).toInt()

    val text = 0xFFF2E8D5.toInt()
    val dim = 0xFFB8AD98.toInt()
    val accent = 0xFFE8B04A.toInt()
    val panel = 0xEE1E1B17.toInt()
    val button = 0xFF3A332A.toInt()
    val good = 0xFF8FD18A.toInt()
    val warn = 0xFFFFD27A.toInt()
    val bad = 0xFFFF8A80.toInt()

    fun bg(color: Int, radius: Int = 10, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    fun label(t: String, size: Float = 13f, color: Int = text, bold: Boolean = false): TextView =
        TextView(a).apply {
            this.text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun mono(t: String, size: Float = 11f): TextView = label(t, size).apply { typeface = Typeface.MONOSPACE }

    fun button(t: String, size: Float = 13f, selected: Boolean = false, onClick: () -> Unit): TextView =
        TextView(a).apply {
            this.text = t; textSize = size; setTextColor(if (selected) Color.WHITE else this@UiKit.text); gravity = Gravity.CENTER
            background = bg(if (selected) 0xFF7A5A1E.toInt() else button, 10, if (selected) accent else 0x33FFFFFF)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { guard(onClick) }
        }

    /** Runs a UI action; a bug in it shows a message and is logged instead of crashing the app. */
    fun guard(f: () -> Unit) {
        try { f() } catch (e: Throwable) { (a as? MainActivity)?.reportError(e) }
    }

    fun chip(t: String, color: Int, size: Float = 11f): TextView =
        TextView(a).apply {
            this.text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = bg(color, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }

    fun row(vararg views: View, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; this.gravity = gravity
            for (v in views) addView(v)
        }

    fun column(): LinearLayout = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

    fun scroll(child: View): ScrollView = ScrollView(a).apply { addView(child); isVerticalScrollBarEnabled = false }

    fun hscroll(child: View): HorizontalScrollView = HorizontalScrollView(a).apply { addView(child); isHorizontalScrollBarEnabled = false }

    fun lin(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    fun fl(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    /** A thin horizontal bar, 0..1. */
    fun bar(v: Float, color: Int, width: Int = 90): View {
        val box = FrameLayout(a)
        val back = View(a).apply { background = bg(0xFF2A2620.toInt(), 4) }
        val front = View(a).apply { background = bg(color, 4) }
        box.addView(back, FrameLayout.LayoutParams(dp(width), dp(8)))
        box.addView(front, FrameLayout.LayoutParams((dp(width) * v.coerceIn(0f, 1f)).toInt().coerceAtLeast(1), dp(8)))
        return box
    }

    fun divider(): View = View(a).apply { setBackgroundColor(0x22FFFFFF) }
    fun spacer(h: Int = 6): View = View(a).apply { minimumHeight = dp(h) }

    fun moodColor(m: Float) = when {
        m < 0.2f -> 0xFFE05050.toInt()
        m < 0.35f -> 0xFFE0A030.toInt()
        m < 0.6f -> 0xFFD8D070.toInt()
        else -> 0xFF8FD18A.toInt()
    }
}
