package com.lester.routerwatch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class Dev(val host: String, val ip: String, val mac: String, val status: String, val ssid: Int = 1)
data class FilterEntry(val domain: String, val ssid: String, val name: String, val mac: String)

/** All colors for one theme. LIGHT and DARK live in the companion object at the bottom. */
class Pal(
    val dark: Boolean,
    val bg: Int, val surface: Int, val tile: Int, val tag: Int, val line: Int,
    val text: Int, val muted: Int,
    val header: Int, val statusBar: Int, val headerSub: Int,
    val accent: Int, val accentSoft: Int, val onFill: Int,
    val good: Int, val goodSoft: Int, val bad: Int, val badSoft: Int, val warn: Int
)

const val ICON_REFRESH = 0
const val ICON_DEVICES = 1
const val ICON_SHIELD = 2
const val ICON_GLOBE = 3
const val ICON_PERSON = 4

/** Small line icons drawn with Canvas (24x24 grid, same shapes as the design). */
class IconView(ctx: Context, private val kind: Int, tintColor: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        color = tintColor
    }

    fun tint(c: Int) { p.color = c; invalidate() }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height) / 24f
        cv.save()
        cv.scale(s, s)
        val path = Path()
        when (kind) {
            ICON_REFRESH -> {
                cv.drawArc(RectF(4f, 4f, 20f, 20f), 0f, 317f, false, p)
                path.moveTo(20f, 4f); path.lineTo(20f, 9f); path.lineTo(15f, 9f)
                cv.drawPath(path, p)
            }
            ICON_DEVICES -> {
                cv.drawRoundRect(RectF(7f, 3f, 17f, 21f), 2f, 2f, p)
                cv.drawLine(11f, 18f, 13f, 18f, p)
            }
            ICON_SHIELD -> {
                path.moveTo(12f, 3f); path.lineTo(20f, 6f); path.lineTo(20f, 12f)
                path.cubicTo(20f, 17f, 16.5f, 20f, 12f, 21f)
                path.cubicTo(7.5f, 20f, 4f, 17f, 4f, 12f)
                path.lineTo(4f, 6f); path.close()
                cv.drawPath(path, p)
            }
            ICON_GLOBE -> {
                cv.drawCircle(12f, 12f, 9f, p)
                cv.drawLine(3f, 12f, 21f, 12f, p)
                path.moveTo(12f, 3f); path.cubicTo(15f, 6f, 15f, 18f, 12f, 21f)
                path.moveTo(12f, 3f); path.cubicTo(9f, 6f, 9f, 18f, 12f, 21f)
                cv.drawPath(path, p)
            }
            else -> {
                cv.drawCircle(12f, 8f, 4f, p)
                path.moveTo(4f, 21f)
                path.cubicTo(4f, 17f, 8f, 15f, 12f, 15f)
                path.cubicTo(16f, 15f, 20f, 17f, 20f, 21f)
                cv.drawPath(path, p)
            }
        }
        cv.restore()
    }
}

class MainActivity : Activity() {
    private lateinit var subtitle: TextView
    private lateinit var statsCard: LinearLayout
    private lateinit var vSignal: TextView
    private lateinit var vSignalSub: TextView
    private lateinit var vLink: TextView
    private lateinit var vLinkSub: TextView
    private lateinit var vRouter: TextView
    private lateinit var vRouterSub: TextView
    private lateinit var vNet: TextView
    private lateinit var vNetSub: TextView
    private lateinit var vBars: BarsView
    private lateinit var userBox: EditText
    private lateinit var passBox: EditText
    private lateinit var status: TextView
    private lateinit var acctStatus: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var listPage: LinearLayout
    private lateinit var accountPage: ScrollView
    private lateinit var chipBtns: List<TextView>
    private lateinit var tabBtns: List<TextView>
    private lateinit var themeBtns: List<TextView>
    private val navIcons = ArrayList<IconView>()
    private val navPills = ArrayList<FrameLayout>()
    private val navLabels = ArrayList<TextView>()
    private val history = ArrayList<Int>()
    @Volatile private var client: RouterClient? = null
    private var devCache: List<Dev> = emptyList()
    private var filterCache: List<FilterEntry> = emptyList()
    private var page = 0           // 0 = devices, 1 = filter list, 2 = account
    private var tab = 0            // list shown: 0 = devices, 1 = filter list
    private var statusFilter = 0   // 0 = all, 1 = online, 2 = offline
    private var wlEnabled: Boolean? = null   // router WLAN filter on/off, null = unknown
    private var wlGuess = false              // true = value is only "last set by this app"
    private var wlError = ""                 // why the state could not be read
    private var themeMode = 0      // 0 = follow system, 1 = light, 2 = dark
    private var pal: Pal = LIGHT
    private var lastStatus = "Open Account, enter router login, then tap refresh."
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT
    @Volatile private var running = false
    @Volatile private var targetHost = "192.168.1.1"
    @Volatile private var targetPort = 443
    private val prefs by lazy { getSharedPreferences("rw", MODE_PRIVATE) }

    // ---------- theme ----------
    private fun isDarkNow(): Boolean = when (themeMode) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun setThemeMode(m: Int) {
        themeMode = m
        prefs.edit().putInt("theme", m).apply()
        buildUi()
    }

    private fun dlg() = AlertDialog.Builder(this,
        if (pal.dark) AlertDialog.THEME_DEVICE_DEFAULT_DARK else AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)

    // ---------- small UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun hair() = Math.round(1.5f * resources.displayMetrics.density)

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokePx: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun ripple(content: Drawable, radiusDp: Int): Drawable {
        val rc = (pal.accent and 0x00FFFFFF) or 0x33000000
        return RippleDrawable(ColorStateList.valueOf(rc), content, rounded(Color.WHITE, radiusDp))
    }

    private fun txt(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) addView(v)
    }

    private fun tile(title: String): Triple<LinearLayout, TextView, TextView> {
        val value = txt("--", 17f, pal.text, true)
        val sub = txt("", 11f, pal.muted, true)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pal.tile, 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(txt(title, 11f, pal.muted))
            addView(value)
            addView(sub)
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
        return Triple(box, value, sub)
    }

    private fun field(hint: String, password: Boolean) = EditText(this).apply {
        setSingleLine()
        this.hint = hint
        textSize = 14f
        setTextColor(pal.text)
        setHintTextColor(pal.muted)
        background = rounded(pal.tile, 14, pal.line, dp(1))
        setPadding(dp(16), 0, dp(16), 0)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(MP, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    // round button: filled or outlined
    private fun pillButton(title: String, color: Int, filled: Boolean, onClick: () -> Unit) =
        TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (filled) pal.onFill else color)
            background = ripple(rounded(if (filled) color else pal.surface, 22, color, hair()), 22)
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(WC, dp(44))
            setOnClickListener { onClick() }
        }

    // ---------- tabs, chips, nav ----------
    private fun tabBtn(title: String, onClick: () -> Unit) = TextView(this).apply {
        text = title
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        setOnClickListener { onClick() }
    }

    private fun chip(onClick: () -> Unit) = TextView(this).apply {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
        setOnClickListener { onClick() }
    }

    private fun paintTab(b: TextView, selected: Boolean) {
        b.background = ripple(rounded(if (selected) pal.accent else Color.TRANSPARENT, 22), 22)
        b.setTextColor(if (selected) pal.onFill else pal.muted)
    }

    private fun paintChip(b: TextView, selected: Boolean, color: Int) {
        b.background = ripple(rounded(if (selected) color else pal.surface, 22, color, hair()), 22)
        b.setTextColor(if (selected) pal.onFill else color)
    }

    private fun navItem(title: String, icon: Int, onClick: () -> Unit): LinearLayout {
        val ic = IconView(this, icon, pal.muted)
        val pill = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(30))
            addView(ic, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        val lab = txt(title, 11f, pal.muted)
        lab.setPadding(0, dp(2), 0, 0)
        lab.gravity = Gravity.CENTER
        lab.layoutParams = LinearLayout.LayoutParams(MP, WC)
        navIcons.add(ic); navPills.add(pill); navLabels.add(lab)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f)
            addView(pill)
            addView(lab)
            setOnClickListener { onClick() }
        }
    }

    private fun paintNav() {
        for (i in navIcons.indices) {
            val sel = when (i) { 0 -> page == 0; 1 -> page == 1; 3 -> page == 2; else -> false }
            navPills[i].background = rounded(if (sel) pal.accentSoft else Color.TRANSPARENT, 15)
            navIcons[i].tint(if (sel) pal.accent else pal.muted)
            navLabels[i].setTextColor(if (sel) pal.accent else pal.muted)
            navLabels[i].typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun paintThemeBtns() {
        themeBtns.forEachIndexed { i, b -> paintTab(b, themeMode == i) }
    }

    private fun showPage(p: Int) {
        page = p
        if (p < 2) tab = p
        listPage.visibility = if (p < 2) View.VISIBLE else View.GONE
        accountPage.visibility = if (p == 2) View.VISIBLE else View.GONE
        statsCard.visibility = if (p < 2) View.VISIBLE else View.GONE
        paintNav()
        if (p < 2) refresh()
    }

    private fun openRouterPage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$targetHost")))
    }

    // 1 = online, 2 = offline, 0 = unknown
    private fun stateOfDev(d: Dev) = if (d.status.equals("offline", true)) 2 else 1

    private fun stateOfMac(mac: String): Int {
        val d = devCache.firstOrNull { it.mac.equals(mac, true) }
            ?: return if (devCache.isEmpty()) 0 else 2
        return stateOfDev(d)
    }

    private fun passes(state: Int) = statusFilter == 0 || state == statusFilter

    private fun updateChips(all: Int, on: Int, off: Int) {
        val labels = listOf("All ($all)", "Online ($on)", "Offline ($off)")
        val colors = listOf(pal.accent, pal.good, pal.bad)
        chipBtns.forEachIndexed { i, b -> b.text = labels[i]; paintChip(b, statusFilter == i, colors[i]) }
        tabBtns.forEachIndexed { i, b -> paintTab(b, tab == i) }
    }

    private fun refresh() {
        if (tab == 0) renderDevices(devCache) else renderFilter(filterCache)
    }

    private fun showRaw() {
        val t = client?.devSample ?: return
        if (t.isNotBlank()) showDebug("Raw device data - hide private info, then copy", t)
    }

    private fun say(s: String) {
        lastStatus = s
        if (::status.isInitialized) status.text = s
        if (::acctStatus.isInitialized) acctStatus.text = s
    }

    // ---------- cards ----------
    private fun cardBg() = rounded(pal.surface, 16,
        if (pal.dark) pal.line else 0, if (pal.dark) dp(1) else 0)

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg()
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun itemCard(
        name: String, sub: String, state: Int, ssid: String,
        action: String, actionColor: Int, onAction: () -> Unit, onTap: (() -> Unit)?
    ): LinearLayout {
        val st = when (state) { 1 -> pal.good; 2 -> pal.bad; else -> pal.muted }
        val stSoft = when (state) { 1 -> pal.goodSoft; 2 -> pal.badSoft; else -> pal.tag }
        val stText = when (state) { 1 -> "Online"; 2 -> "Offline"; else -> "Unknown" }
        val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

        val avatar = txt(initial, 16f, pal.accent, true).apply {
            gravity = Gravity.CENTER
            background = oval(pal.accentSoft)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f).apply { setMargins(dp(12), 0, 0, 0) }
            addView(txt(name, 15f, if (state == 2) pal.muted else pal.text, true).apply {
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            })
            addView(txt(sub, 12f, pal.muted).apply {
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            })
        }
        val dot = View(this).apply {
            background = oval(st)
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { setMargins(0, 0, dp(5), 0) }
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(stSoft, 12)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            addView(dot)
            addView(txt(stText, 12f, st, true))
            layoutParams = LinearLayout.LayoutParams(WC, WC).apply { setMargins(dp(8), 0, 0, 0) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(avatar); addView(names); addView(pill)
        }
        val tag = txt(ssid, 12f, pal.muted).apply {
            background = rounded(pal.tag, 8)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tag); addView(spacer)
            addView(pillButton(action, actionColor, false) { onAction() })
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, dp(8), 0, 0) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (onTap != null) ripple(cardBg(), 16) else cardBg()
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(8)) }
            addView(top); addView(bottom)
            if (onTap != null) setOnClickListener { onTap() }
        }
    }

    private fun emptyNote(noData: Boolean) = txt(
        if (noData) "Nothing here yet. Tap the refresh button." else "Nothing matches this filter.",
        12f, pal.muted).apply { setPadding(dp(4), dp(12), dp(4), 0) }

    private fun ssidLabel(s: String) =
        if (s.isBlank()) "SSID --" else if (s.startsWith("SSID", true)) s else "SSID-$s"

    private fun copyMac(mac: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mac", mac))
        Toast.makeText(this, "MAC copied", Toast.LENGTH_SHORT).show()
    }

    // ---------- build the whole screen (called again when theme changes) ----------
    @Suppress("DEPRECATION")
    private fun buildUi() {
        val keepUser = if (::userBox.isInitialized) userBox.text.toString()
            else (prefs.getString("user", "") ?: "")
        val keepPass = if (::passBox.isInitialized) passBox.text.toString()
            else (prefs.getString("pass", null)?.let { decrypt(it) } ?: "")

        pal = if (isDarkNow()) DARK else LIGHT
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        window.statusBarColor = pal.statusBar
        window.navigationBarColor = pal.surface
        window.decorView.systemUiVisibility =
            if (!pal.dark && Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        navIcons.clear(); navPills.clear(); navLabels.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }

        // ----- header -----
        subtitle = txt("Starting...", 12f, pal.headerSub)
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            addView(txt("RouterWatch", 20f, Color.WHITE, true))
            addView(subtitle)
        }
        val refreshBtn = FrameLayout(this).apply {
            contentDescription = "Get devices"
            background = ripple(oval(Color.parseColor("#33FFFFFF")), 22)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            addView(IconView(this@MainActivity, ICON_REFRESH, Color.WHITE),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { fetchDevices() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.header)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(titleCol)
            addView(refreshBtn)
        }

        // ----- stats card -----
        val (tSignal, sv, ss) = tile("Signal"); vSignal = sv; vSignalSub = ss
        val (tLink, lv, ls) = tile("Link speed"); vLink = lv; vLinkSub = ls
        val (tRouter, rv, rs) = tile("Router ping"); vRouter = rv; vRouterSub = rs
        val (tNet, nv, ns) = tile("Internet ping"); vNet = nv; vNetSub = ns
        vBars = BarsView(this)
        statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            elevation = dp(2).toFloat()
            setPadding(dp(6), dp(6), dp(6), dp(10))
            addView(row(tSignal, tLink))
            addView(row(tRouter, tNet))
            addView(vBars, LinearLayout.LayoutParams(MP, dp(24)).apply { setMargins(dp(8), dp(8), dp(8), 0) })
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(dp(12), dp(12), dp(12), 0) }
        }

        // ----- list page: tabs, chips, status, list -----
        tabBtns = listOf(
            tabBtn("Devices") { showPage(0) },
            tabBtn("Filter list") { openFilter() })
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.surface, 26, if (pal.dark) pal.line else 0, if (pal.dark) dp(1) else 0)
            elevation = dp(2).toFloat()
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(tabBtns[0]); addView(tabBtns[1])
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(dp(12), dp(10), dp(12), 0) }
        }
        chipBtns = listOf(0, 1, 2).map { i -> chip { statusFilter = i; refresh() } }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chipBtns[0]); addView(chipBtns[1]); addView(chipBtns[2])
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(dp(8), dp(8), dp(8), 0) }
        }
        status = txt(lastStatus, 12f, pal.muted).apply {
            maxLines = 2
            setOnClickListener { showRaw() }
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(dp(16), dp(8), dp(16), 0) }
        }
        deviceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(deviceList)
        }
        listPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabBar); addView(chipRow); addView(status)
            addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
        }

        // ----- account page: login + appearance -----
        userBox = field("Router account", false).apply { setText(keepUser) }
        passBox = field("Router password", true).apply { setText(keepPass) }
        acctStatus = txt(lastStatus, 12f, pal.muted).apply {
            setPadding(dp(2), dp(8), dp(2), 0)
            setOnClickListener { showRaw() }
        }
        val getBtn = pillButton("Get devices", pal.accent, true) { fetchDevices() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 2f).apply { setMargins(0, dp(8), dp(4), 0) }
        }
        val forgetBtn = pillButton("Forget login", pal.bad, false) { forgetLogin() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(8), 0, 0) }
        }
        val loginCard = panel().apply {
            addView(txt("Router login", 15f, pal.text, true))
            addView(txt("Used to read devices and edit the WLAN MAC filter.", 12f, pal.muted))
            addView(userBox)
            addView(passBox)
            addView(row(getBtn, forgetBtn))
            addView(acctStatus)
        }
        themeBtns = listOf("System", "Light", "Dark").mapIndexed { i, t -> tabBtn(t) { setThemeMode(i) } }
        val themeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.tag, 26)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            for (b in themeBtns) addView(b)
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, dp(10), 0, 0) }
        }
        paintThemeBtns()
        val themeCard = panel().apply {
            addView(txt("Appearance", 15f, pal.text, true))
            addView(txt("System follows your phone's dark mode.", 12f, pal.muted))
            addView(themeBar)
        }
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val aboutCard = panel().apply {
            addView(txt("About", 15f, pal.text, true))
            addView(txt("RouterWatch v$version", 12f, pal.muted).apply { setPadding(0, dp(4), 0, 0) })
            addView(txt("Developer: $DEVELOPER", 13f, pal.text).apply { setPadding(0, dp(6), 0, 0) })
            addView(txt("GitHub: $GITHUB_URL", 13f, pal.accent).apply {
                setPadding(0, dp(6), 0, dp(4))
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }
            })
        }
        val acctCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(loginCard)
            addView(themeCard)
            addView(aboutCard)
        }
        accountPage = ScrollView(this).apply {
            addView(acctCol)
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
        }

        // ----- bottom nav -----
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(pal.surface)
            addView(navItem("Devices", ICON_DEVICES) { showPage(0) })
            addView(navItem("Filter list", ICON_SHIELD) { openFilter() })
            addView(navItem("Router page", ICON_GLOBE) { openRouterPage() })
            addView(navItem("Account", ICON_PERSON) { showPage(2) })
        }

        root.addView(header)
        root.addView(statsCard)
        root.addView(listPage)
        root.addView(accountPage)
        root.addView(View(this).apply { setBackgroundColor(pal.line) }, LinearLayout.LayoutParams(MP, dp(1)))
        root.addView(nav)
        setContentView(root)

        updateChips(0, 0, 0)
        showPage(page)
    }

    // ---------- saved login (password encrypted with Android Keystore) ----------
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("rw_key", null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder("rw_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return g.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String? = try {
        val raw = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    } catch (e: Exception) { null }

    private fun saveLogin(user: String, pass: String) {
        try {
            prefs.edit().putString("user", user).putString("pass", encrypt(pass)).apply()
        } catch (e: Exception) {
            prefs.edit().putString("user", user).remove("pass").apply()
        }
    }

    private fun forgetLogin() {
        prefs.edit().remove("user").remove("pass").apply()
        userBox.setText("")
        passBox.setText("")
        client = null
        say("Saved login removed.")
    }

    private fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r: Result<T> = try { Result.success(work()) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread { done(r) }
        }.start()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        themeMode = prefs.getInt("theme", 0)
        buildUi()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        targetHost = gatewayIp() ?: "192.168.1.1"
    }

    // phone dark mode switched while app open: redraw if theme follows system
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (themeMode == 0) buildUi()
    }

    override fun onResume() { super.onResume(); startMonitor() }
    override fun onPause() { running = false; super.onPause() }

    // ---------- device fetch ----------
    private fun fetchDevices() {
        val user = userBox.text.toString().trim()
        val pass = passBox.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            say("Fill account + password.")
            showPage(2)
            return
        }
        val host = gatewayIp() ?: targetHost
        targetHost = host
        say("Logging in to $host ...")
        Thread {
            val result = try {
                val c = RouterClient(host)
                c.login(user, pass)
                client = c
                Result.success(c.devices())
            } catch (e: Exception) {
                Result.failure(e)
            }
            runOnUiThread {
                result.onSuccess { list ->
                    devCache = list
                    saveLogin(user, pass)
                    say(if (list.isEmpty())
                        "Logged in, but no devices parsed. Page format may differ."
                    else "${list.size} device(s). Tap card = copy MAC. Tap this text = raw router data.")
                    showPage(0)
                }.onFailure { e ->
                    say("Failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun renderDevices(list: List<Dev>) {
        deviceList.removeAllViews()
        val on = list.count { stateOfDev(it) == 1 }
        updateChips(list.size, on, list.size - on)
        for (d in list.filter { passes(stateOfDev(it)) }) {
            val sub = listOf(d.ip, d.mac).filter { it.isNotBlank() }.joinToString(", ")
            deviceList.addView(itemCard(
                d.host.ifBlank { "(unnamed device)" }, sub, stateOfDev(d), "SSID-${d.ssid}",
                "Add to filter list", pal.accent, { addToFilter(d) }, { copyMac(d.mac) }))
        }
        if (deviceList.childCount == 0) deviceList.addView(emptyNote(list.isEmpty()))
    }

    // ---------- router MAC filter (whitelist / blacklist per router mode) ----------
    private fun showDebug(title: String, text: String) {
        dlg().setTitle(title).setMessage(text)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("debug", text))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun needLogin() {
        say("Login first (Account tab, then tap refresh).")
        showPage(2)
    }

    private fun addToFilter(d: Dev) {
        val c = client ?: run { needLogin(); return }
        val name = d.host.ifBlank { "device" }.replace(Regex("[^A-Za-z0-9_.-]"), "-").take(31)
        dlg()
            .setTitle("Add to router filter?")
            .setMessage("$name\n${d.mac}  (SSID${d.ssid})\n\n" +
                "Router mode decides meaning: whitelist = allowed, blacklist = blocked.")
            .setPositiveButton("Add") { _, _ ->
                say("Adding ${d.mac} ...")
                bg({
                    c.addFilter(d.ssid, name, d.mac)
                    c.filterList()
                }) { r ->
                    r.onSuccess { list ->
                        filterCache = list
                        val ok = list.any { it.mac.equals(d.mac, true) }
                        say(if (ok) "Added. Router lists ${d.mac}." else "Router did not add ${d.mac}.")
                        if (!ok) showDebug("Add failed - copy and send this",
                            c.log.joinToString("\n") + "\n\nentries read: ${list.size}\n\n" + c.jsSample)
                        if (tab == 1) refresh()
                    }.onFailure {
                        say("Failed: ${it.message}")
                        showDebug("Add failed - copy and send this", c.lastDebug + "\n" + (it.message ?: ""))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // open Filter list tab, then load it from the router
    private fun openFilter() {
        if (client == null) { needLogin(); return }
        showPage(1)
        loadFilter()
    }

    private fun loadFilter() {
        val c = client ?: run { needLogin(); return }
        say("Loading filter list ...")
        bg({
            val d = try { c.devices() } catch (e: Exception) { devCache }
            var err = ""
            val en = try { c.filterEnabled() } catch (e: Exception) { err = e.message ?: "read error"; null }
            Triple(d, c.filterList(), Pair(en, err))
        }) { r ->
            r.onSuccess { (d, list, st) ->
                if (d.isNotEmpty()) devCache = d
                setWlState(st.first, st.second)
                filterCache = list
                say(if (list.isEmpty()) "Filter list empty (or page format differs)."
                else "${list.size} filter entr${if (list.size == 1) "y" else "ies"}.")
                refresh()
            }.onFailure { say("Failed: ${it.message}") }
        }
    }

    private fun renderFilter(list: List<FilterEntry>) {
        deviceList.removeAllViews()
        val st = list.map { stateOfMac(it.mac) }
        updateChips(list.size, st.count { it == 1 }, st.count { it == 2 })
        list.forEachIndexed { i, e ->
            if (!passes(st[i])) return@forEachIndexed
            deviceList.addView(itemCard(
                e.name.ifBlank { "(no name)" }, e.mac, st[i], ssidLabel(e.ssid),
                "Remove", pal.bad, { removeFromFilter(e) }, null))
        }
        if (deviceList.childCount == 0) deviceList.addView(emptyNote(list.isEmpty()))
        deviceList.addView(
            txt("Router mode decides meaning: whitelist = allowed, blacklist = blocked.", 12f, pal.muted)
                .apply { setPadding(dp(4), 0, dp(4), dp(4)) }, 0)
        deviceList.addView(whitelistCard(), 0)
    }

    // ---------- whitelist on/off switch ----------
    private fun whitelistCard(): LinearLayout {
        val known = wlEnabled != null
        val sw = Switch(this).apply {
            isChecked = wlEnabled == true
            isEnabled = known
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(pal.accent, pal.muted))
            trackTintList = ColorStateList(states,
                intArrayOf((pal.accent and 0x00FFFFFF) or 0x55000000, pal.line))
            setOnClickListener { toggleWhitelist(isChecked) }
        }
        val badgeText = when {
            wlEnabled == null -> "UNKNOWN"
            wlGuess -> if (wlEnabled == true) "ON (last set)" else "OFF (last set)"
            wlEnabled == true -> "ON"
            else -> "OFF"
        }
        val badgeColor = when {
            wlEnabled == null || wlGuess -> pal.warn
            wlEnabled == true -> pal.good
            else -> pal.bad
        }
        val sub = when {
            wlEnabled == null -> "Cannot read router state. Tap Check, or use Debug and send me the text."
            wlGuess -> "Router state unreadable. Showing what this app last set."
            wlEnabled == true -> "Whitelist active. Only devices in the list can connect."
            else -> "Filter disabled. Any device can connect."
        }
        val title = row(
            txt("Whitelist filter", 15f, pal.text, true),
            txt(badgeText, 11f, badgeColor, true).apply {
                background = rounded(pal.tag, 8)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                layoutParams = LinearLayout.LayoutParams(WC, WC).apply { setMargins(dp(8), 0, 0, 0) }
            }
        ).apply { gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            addView(title)
            addView(txt(sub, 12f, pal.muted))
            if (!known || wlGuess) {
                val btns = row(
                    pillButton("Check", pal.accent, false) { loadFilter() },
                    pillButton("Debug", pal.muted, false) {
                        val c = client
                        showDebug("Whitelist state - copy and send this",
                            "read error: ${wlError.ifBlank { "none (pattern not found)" }}\n\n" +
                                (c?.modeSample?.ifBlank { "(no page sample yet, tap Check first)" } ?: "(not logged in)"))
                    }.apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(8), 0, 0, 0) }
                )
                btns.layoutParams = LinearLayout.LayoutParams(WC, WC).apply { setMargins(0, dp(8), 0, 0) }
                addView(btns)
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg()
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(8)) }
            addView(col)
            addView(sw)
        }
    }

    // router value wins; if unreadable, show last value this app set (marked as a guess)
    private fun setWlState(read: Boolean?, err: String = "") {
        wlError = err
        if (read != null) {
            wlEnabled = read; wlGuess = false
            prefs.edit().putBoolean("wl_last", read).apply()
        } else if (prefs.contains("wl_last")) {
            wlEnabled = prefs.getBoolean("wl_last", false); wlGuess = true
        } else {
            wlEnabled = null; wlGuess = false
        }
    }

    private fun toggleWhitelist(on: Boolean) {
        val c = client ?: run { needLogin(); return }
        if (!on) { applyWhitelist(c, false); return }
        dlg()
            .setTitle("Turn whitelist ON?")
            .setMessage("Only devices in the filter list can connect to Wi-Fi. " +
                "Make sure this phone is in the list first, or you lose access.")
            .setPositiveButton("Turn on") { _, _ -> applyWhitelist(c, true) }
            .setNegativeButton("Cancel") { _, _ -> refresh() }
            .setOnCancelListener { refresh() }
            .show()
    }

    private fun applyWhitelist(c: RouterClient, on: Boolean) {
        say(if (on) "Turning whitelist ON ..." else "Turning whitelist OFF ...")
        bg({
            c.setWhitelist(on)
            Pair(c.filterEnabled(), c.filterList())
        }) { r ->
            r.onSuccess { (st, list) ->
                if (st == null) {
                    // router state unreadable: trust what we just sent
                    prefs.edit().putBoolean("wl_last", on).apply()
                    setWlState(null)
                } else setWlState(st)
                filterCache = list
                say(when {
                    st == on -> "Whitelist is now ${if (on) "ON" else "OFF"}."
                    st == null -> "Sent, but cannot read switch state from router."
                    else -> "Router did not change the switch."
                })
                if (st != on) showDebug("Whitelist switch - copy and send this",
                    c.log.joinToString("\n") + "\n\n" + c.lastDebug + "\n\n" + c.modeSample)
                refresh()
            }.onFailure {
                say("Failed: ${it.message}")
                showDebug("Whitelist switch - copy and send this", c.lastDebug + "\n" + (it.message ?: ""))
                refresh()
            }
        }
    }

    private fun removeFromFilter(e: FilterEntry) {
        val c = client ?: run { needLogin(); return }
        dlg()
            .setTitle("Remove from filter?")
            .setMessage("${e.name}\n${e.mac}\n\nIn whitelist mode this device loses Wi-Fi access.")
            .setPositiveButton("Remove") { _, _ ->
                say("Removing ${e.mac} ...")
                bg({ c.deleteFilter(e.domain); c.filterList() }) { r ->
                    r.onSuccess { list ->
                        filterCache = list
                        val gone = list.none { it.domain == e.domain }
                        say(if (gone) "Removed. ${list.size} left." else "Router did not remove it.")
                        if (!gone) showDebug("Remove failed - copy and send this",
                            c.log.joinToString("\n") + "\n\nentry: ${e.domain}\n\n" + c.jsSample)
                        refresh()
                    }.onFailure {
                        say("Failed: ${it.message}")
                        showDebug("Remove failed - copy and send this", c.lastDebug + "\n" + (it.message ?: ""))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- talks to Huawei HG8145V5 web login (your own router) ----------
    private inner class RouterClient(private val host: String) {
        private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        private var cookie = "Cookie=body:Language:english:id=-1;path=/"
        var lastDebug = ""
        var pageSample = ""
        var jsSample = ""
        val log = ArrayList<String>()
        private var addAction: String? = null
        private var delAction: String? = null
        private var learned = false

        private fun open(path: String, post: Boolean): HttpURLConnection {
            if (!isPrivate(host)) throw Exception("Not a LAN address: $host")
            val c = URL("https://$host$path").openConnection() as HttpsURLConnection
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, trustAll, SecureRandom())
            c.sslSocketFactory = ctx.socketFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
            c.connectTimeout = 5000
            c.readTimeout = 8000
            c.instanceFollowRedirects = false
            c.requestMethod = if (post) "POST" else "GET"
            c.setRequestProperty("Cookie", cookie)
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            // Same headers the router's own web page sends (copied from the browser capture)
            c.setRequestProperty("Referer", "https://$host/html/bbsp/wlanmacfilter/set.cgi?" +
                "x=InternetGatewayDevice.X_HW_Security&RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp")
            c.setRequestProperty("Origin", "https://$host")
            return c
        }

        private fun read(c: HttpURLConnection): String =
            (if (c.responseCode in 200..399) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

        fun login(user: String, pass: String) {
            // 1) one-time token
            val tc = open("/asp/GetRandCount.asp", true)
            val token = read(tc).replace("\uFEFF", "").trim()
            if (token.isEmpty()) throw Exception("No login token. Model/firmware differs?")

            // 2) login (password is base64 in this firmware)
            val pw = Base64.encodeToString(pass.toByteArray(), Base64.NO_WRAP)
            val body = "UserName=" + URLEncoder.encode(user, "UTF-8") +
                "&PassWord=" + URLEncoder.encode(pw, "UTF-8") +
                "&Language=english&x.X_HW_Token=" + URLEncoder.encode(token, "UTF-8")
            val lc = open("/login.cgi", true)
            lc.doOutput = true
            lc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            lc.outputStream.use { it.write(body.toByteArray()) }
            val loginBody = read(lc)
            val sc = lc.headerFields["Set-Cookie"] ?: lc.headerFields["set-cookie"]
            // HG8145V5 sends e.g. "Cookie=sid=abc123:Language:english:id=1;path=/"
            val session = sc?.map { it.substringBefore(';') }
                ?.firstOrNull { it.contains("sid=", true) && !it.contains("id=-1") }
            if (session == null) {
                val dbg = "HTTP ${lc.responseCode}, cookies=${sc ?: "none"}, body=" +
                    loginBody.replace(Regex("\\s+"), " ").take(120)
                throw Exception("Login rejected. $dbg")
            }
            cookie = session
            if (loginBody.isEmpty() && lc.responseCode >= 400) throw Exception("Login HTTP ${lc.responseCode}")
        }

        var devSample = ""

        fun devices(): List<Dev> {
            val paths = listOf(
                "/html/bbsp/common/GetLanUserDevInfo.asp",
                "/html/bbsp/common/GetLanUserInfo.asp"
            )
            val sb = StringBuilder()
            for (p in paths) {
                try {
                    val txt = read(open(p, false))
                    val classes = Regex("new\\s+(\\w+)\\s*\\(").findAll(txt)
                        .groupingBy { it.groupValues[1] }.eachCount()
                    val recs = Regex("new\\s+\\w+\\s*\\(([^)]{0,500})\\)").findAll(txt).take(2)
                        .joinToString("\n") { unescape(it.value) }
                    sb.append("$p | len ${txt.length} | classes $classes\n$recs\n\n")
                    val list = parse(txt)
                    if (list.isNotEmpty()) { devSample = sb.toString(); return list }
                } catch (e: Exception) { sb.append("$p failed: ${e.message}\n\n") }
            }
            devSample = sb.toString()
            return emptyList()
        }

        // ---- config writes need a fresh token per request ----
        private fun cfgToken(): String =
            read(open("/html/ssmp/common/getRandString.asp", true)).replace("\uFEFF", "").trim()

        private fun post(path: String, body: String): String {
            val c = open(path, true)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val txt = read(c)
            val loc = c.getHeaderField("Location") ?: ""
            lastDebug = "POST $path\nbody: " + body.replace(Regex("X_HW_Token=[^&]*"), "X_HW_Token=***") +
                "\nHTTP $code" + (if (loc.isNotEmpty()) "\nLocation: $loc" else "") +
                "\nreply: " + txt.replace(Regex("\\s+"), " ").take(300)
            val note = Regex("""alert\(['"]([^'"]{0,120})""").find(txt)?.groupValues?.get(1)
                ?: Regex("""errorcode[^;<]{0,60}""", RegexOption.IGNORE_CASE).find(txt)?.value ?: ""
            log.add(path.substringAfterLast('/').take(90) + " | body keys: " +
                body.split('&').joinToString(",") { it.substringBefore('=').takeLast(40) } +
                " | token len ${Regex("X_HW_Token=([^&]*)").find(body)?.groupValues?.get(1)?.length ?: 0}" +
                " | HTTP $code | len ${txt.length} | $note")
            if (code in 300..399 && loc.contains("login", true)) throw Exception("Session expired. Tap Get devices.")
            if (code >= 400) throw Exception("HTTP $code")
            return txt
        }

        // Read the router's own filter page and copy the add/delete addresses it uses.
        private fun learn() {
            if (learned) return
            learned = true
            try {
                val html = read(open(FILTER_PAGE, false))
                fun act(kind: String): String? {
                    val m = Regex("""['"]([^'"?]*$kind\.cgi\?[^'"]*)['"]""").find(html) ?: return null
                    var u = m.groupValues[1]
                    if (!u.startsWith("/")) u = FILTER_PAGE.substringBeforeLast('/') + "/" + u
                    return u
                }
                addAction = act("add")
                delAction = act("del")
            } catch (e: Exception) { }
        }

        fun filterList(): List<FilterEntry> {
            val html = read(open(FILTER_PAGE, false))
            val i = html.indexOf("WLANMacFilter")
            pageSample = if (i >= 0)
                html.substring(maxOf(0, i - 100), minOf(html.length, i + 400)).replace(Regex("\\s+"), " ")
            else "(no WLANMacFilter text in page, length ${html.length}) " +
                html.take(200).replace(Regex("\\s+"), " ")
            fun around(key: String): String {
                val j = html.indexOf(key)
                return if (j < 0) "(no $key in page)"
                else html.substring(maxOf(0, j - 450), minOf(html.length, j + 250)).replace(Regex("\\s+"), " ")
            }
            val params = Regex("addParameter\\([^)]{0,80}\\)").findAll(html).map { it.value }.distinct().take(14).joinToString("\n")
            val toks = Regex("<input[^>]*token[^>]*>", RegexOption.IGNORE_CASE).findAll(html).take(3)
                .joinToString("\n") { it.value.replace(Regex("value=\"[^\"]*\""), "value=\"***\"") }
            jsSample = "DEL: " + around("del.cgi") + "\n\nADD: " + around("add.cgi") +
                "\n\nparams:\n" + params + "\n\ntoken inputs:\n" + toks
            return parseFilter(html)
        }

        // Some Huawei firmware uses the token printed inside the page itself.
        private fun pageToken(): String? = try {
            val html = read(open(FILTER_PAGE, false))
            val tag = Regex("<input[^>]*token[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
                .mapNotNull { Regex("value\\s*=\\s*[\"']([0-9a-fA-F]{16,})").find(it.value)?.groupValues?.get(1) }
                .firstOrNull()
            tag ?: Regex("token[\"']?\\s*[:=,]\\s*[\"']([0-9a-fA-F]{32,64})[\"']", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
        } catch (e: Exception) { null }

        fun addFilter(ssid: Int, name: String, mac: String) {
            learn()
            log.clear()
            val objUrl = "/html/bbsp/wlanmacfilter/add.cgi?x=$FILTER_OBJ&RequestFile=${FILTER_PAGE.trimStart('/')}"
            for (u in listOfNotNull(objUrl, addAction).distinct()) for (kind in 0..1) {
                try {
                    val t = (if (kind == 0) cfgToken() else pageToken()) ?: continue
                    // same fields and order as the router's own web page
                    post(u, "x.SourceMACAddress=${enc(mac.lowercase())}&x.SSIDName=${enc("SSID-$ssid")}" +
                        "&x.DeviceName=${enc(name)}&x.Enable=1&x.X_HW_Token=${enc(t)}")
                    if (filterList().any { it.mac.equals(mac, true) }) return
                } catch (e: Exception) { }
            }
        }

        fun deleteFilter(domain: String) {
            learn()
            log.clear()
            val page = "RequestFile=${FILTER_PAGE.trimStart('/')}"
            // Address copied from the router's own web page: x= is the FILTER OBJECT, entry goes in the body.
            val objUrl = "/html/bbsp/wlanmacfilter/del.cgi?x=$FILTER_OBJ&$page"
            val bodies = listOf(
                { t: String -> "${enc(domain)}=&x.X_HW_Token=${enc(t)}" },
                { t: String -> "x.X_HW_Token=${enc(t)}&${enc(domain)}=" },
                { t: String -> "x=${enc(domain)}&x.X_HW_Token=${enc(t)}" })
            for (mk in bodies) for (kind in 0..1) {
                try {
                    val t = (if (kind == 0) cfgToken() else pageToken()) ?: continue
                    post(objUrl, mk(t))
                    if (filterList().none { it.domain == domain }) return
                } catch (e: Exception) { }
            }
            try {
                post("/html/bbsp/wlanmacfilter/del.cgi?x=$domain&$page", "x.X_HW_Token=${enc(cfgToken())}")
            } catch (e: Exception) { }
        }

        // ---- WLAN filter on/off (whitelist switch) ----
        // Router page is read to find the real parameter name and set address.
        var modeSample = ""
        private val enableRe = Regex(
            """(?:WlanMacFilterRight)\W{1,8}([01])""",
            RegexOption.IGNORE_CASE)
        private val stateRe = Regex("""(?:var\s+)?\benableFilter\s*=\s*['"]?([01])\b""")
        private val stateAlt = listOf(
            Regex("""setCheck\(\s*['"]EnableMacFilter['"]\s*,\s*['"]?([01])"""),
            Regex("""EnableMacFilter['"]?\s*\)?\.checked\s*=\s*(true|false|1|0)"""),
            Regex("""\bEnableMacFilter\s*=\s*['"]?([01]|true|false)\b"""),
            Regex("""WlanMacFilterRight['"]?\s*[,:=]\s*['"]?([01])"""),
            Regex("""x\.WlanMacFilterRight[^0-9]{0,40}([01])""")
        )
        private val bindRe = Regex("""BindField\s*=\s*['"](x\.[^'"]+)['"]""")
        private val enableParamRe = Regex("""addParameter\(\s*['"]([^'"]+)['"]\s*,\s*EnableMacFilter\b""")
        private val setUrlRe = Regex("""['"]([^'"?]*set\.cgi\?[^'"]*)['"]""")

        private fun squash(s: String) = s.replace(Regex("\\s+"), " ")

        fun filterEnabled(): Boolean? {
            val html = read(open(FILTER_PAGE, false))
            val i = html.indexOf("EnableMacFilter")
            val ctx = if (i >= 0) squash(html.substring(maxOf(0, i - 500), minOf(html.length, i + 500)))
                else "(no EnableMacFilter in page)"
            val ap = Regex("addParameter\\([^)]{0,80}\\)").findAll(html).map { it.value }.distinct()
                .take(14).joinToString("\n")
            val su = setUrlRe.findAll(html).map { it.groupValues[1] }.distinct().take(6).joinToString("\n")
            val chk = Regex("""[^\n]{0,60}checked[^\n]{0,60}""", RegexOption.IGNORE_CASE)
                .findAll(html).take(5).joinToString("\n") { squash(it.value) }
            val sf = html.indexOf("function SubmitForm")
            val sub = if (sf >= 0) squash(html.substring(sf, minOf(html.length, sf + 900))) else "(no SubmitForm)"
            val bf = bindRe.findAll(html).map { it.groupValues[1] }.distinct().take(20).joinToString(", ")
            val vars = Regex("""(?:var\s+)?(?:enableFilter|Mode)\s*=[^;\n]{0,60}""").findAll(html)
                .take(6).joinToString("\n") { squash(it.value) }
            modeSample = "SubmitForm:\n$sub\n\nBindFields: $bf\n\nvars:\n$vars\n\nAROUND EnableMacFilter:\n$ctx\n\n" +
                "addParameter:\n$ap\n\nset.cgi urls:\n$su"
            val m = stateRe.find(html)
            if (m != null) return m.groupValues[1] == "1"
            val m2 = enableRe.find(html)
            if (m2 != null) return m2.groupValues[1] == "1"
            for (re in stateAlt) {
                val v = re.find(html)?.groupValues?.get(1) ?: continue
                return v == "1" || v == "true"
            }
            return null
        }

        fun setWhitelist(on: Boolean) {
            log.clear()
            // Same request the router's own page sends (browser capture):
            // Right = "Enable WLAN MAC Filter" checkbox (1 on, 0 off); Policy = mode, 1 = whitelist
            val url = "/html/bbsp/wlanmacfilter/set.cgi?x=InternetGatewayDevice.X_HW_Security" +
                "&RequestFile=${FILTER_PAGE.trimStart('/')}"
            val v = if (on) "1" else "0"
            for (kind in 0..1) {
                try {
                    val t = (if (kind == 0) cfgToken() else pageToken()) ?: continue
                    post(url, "x.WlanMacFilterPolicy=1&x.WlanMacFilterRight=$v&x.X_HW_Token=${enc(t)}")
                    val s = filterEnabled()
                    if (s == null || s == on) return
                } catch (e: Exception) { }
            }
        }
    }

    private fun parseFilter(text: String): List<FilterEntry> {
        val out = ArrayList<FilterEntry>()
        val ssidRe = Regex("^(SSID)?[-_ ]?\\d+$", RegexOption.IGNORE_CASE)
        for (m in Regex("new\\s+\\w+\\s*\\(([^)]*)\\)").findAll(text)) {
            val strs = Regex("\"([^\"]*)\"").findAll(m.groupValues[1]).map { unescape(it.groupValues[1]) }.toList()
            val domain = strs.firstOrNull { it.startsWith("InternetGatewayDevice.") } ?: continue
            val mac = strs.firstOrNull { macRe.matches(it) } ?: continue
            val ssid = strs.firstOrNull { it != domain && ssidRe.matches(it) } ?: ""
            val name = strs.firstOrNull {
                it.isNotBlank() && it != domain && !macRe.matches(it) && !ssidRe.matches(it)
            } ?: ""
            out.add(FilterEntry(domain, ssid, name, mac.lowercase()))
        }
        return out
    }

    private fun unescape(s: String) =
        Regex("\\\\x([0-9a-fA-F]{2})").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }

    private val macRe = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")
    private val ipRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val skipRe = Regex(
        "^(InternetGatewayDevice.*|LAN\\d*|SSID[-_ ]?\\d*|WIFI.*|Ethernet.*|2\\.4G|5G|DHCP|Static|" +
            "Online|Offline|Active|Inactive|--|N/A|IPv4|IPv6|\\d+|[\\d:]+)$", RegexOption.IGNORE_CASE)

    private fun parse(text: String): List<Dev> {
        val out = ArrayList<Dev>()
        val seen = HashSet<String>()
        val rec = Regex("new\\s+USERDevice[A-Za-z]*\\s*\\(([^)]*)\\)")
        for (m in rec.findAll(text)) {
            val strs = Regex("\"([^\"]*)\"").findAll(m.groupValues[1]).map { unescape(it.groupValues[1]) }.toList()
            val mac = strs.firstOrNull { macRe.matches(it) } ?: continue
            if (!seen.add(mac.lowercase())) continue
            val ip = strs.firstOrNull { ipRe.matches(it) } ?: ""
            val st = strs.firstOrNull { it.equals("online", true) || it.equals("offline", true) } ?: ""
            val host = strs.firstOrNull {
                it.isNotBlank() && !macRe.matches(it) && !ipRe.matches(it) && !skipRe.matches(it)
            } ?: strs.getOrNull(8)?.takeIf { it.isNotBlank() && !macRe.matches(it) && !ipRe.matches(it) } ?: ""
            val ssid = strs.firstNotNullOfOrNull {
                Regex("^SSID[-_ ]?(\\d+)$", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull()
            } ?: 1
            out.add(Dev(host, ip, mac.uppercase(), st, ssid))
        }
        return out
    }

    private fun isPrivate(h: String) =
        Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)").containsMatchIn(h)

    private fun ipStr(i: Int) =
        "${i and 255}.${(i shr 8) and 255}.${(i shr 16) and 255}.${(i shr 24) and 255}"

    @Suppress("DEPRECATION")
    private fun gatewayIp(): String? {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val g = wm.dhcpInfo?.gateway ?: 0
        return if (g == 0) null else ipStr(g)
    }

    private fun ping(host: String, port: Int): Int {
        val t = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            ((System.nanoTime() - t) / 1_000_000).toInt()
        } catch (e: Exception) { -1 }
    }

    private fun pingColor(ms: Int) = when {
        ms < 0 -> pal.bad
        ms < 80 -> pal.good
        ms < 200 -> pal.warn
        else -> pal.bad
    }

    private fun pingWord(ms: Int) = when {
        ms < 0 -> "No reply"
        ms < 80 -> "Good"
        ms < 200 -> "Slow"
        else -> "Poor"
    }

    private fun ms(v: Int) = if (v < 0) "--" else "$v ms"

    private fun showStats(
        connected: Boolean, ssid: String, rssi: Int, freq: Int, linkSpeed: Int,
        router: Int, net: Int, hist: List<Int>, gw: String
    ) {
        if (!connected) {
            subtitle.text = "Not connected to Wi-Fi"
            vSignal.text = "--"; vSignalSub.text = ""
            vLink.text = "--"; vLinkSub.text = ""
        } else {
            val band = when { freq < 3000 -> "2.4 GHz"; freq >= 5925 -> "6 GHz"; else -> "5 GHz" }
            val q = when { rssi >= -55 -> "Excellent"; rssi >= -67 -> "Good"; rssi >= -75 -> "Fair"; else -> "Weak" }
            val qc = when { rssi >= -67 -> pal.good; rssi >= -75 -> pal.warn; else -> pal.bad }
            subtitle.text = "$ssid, $band, router $gw"
            vSignal.text = "$rssi dBm"
            vSignalSub.text = q
            vSignalSub.setTextColor(qc)
            vLink.text = "$linkSpeed Mbps"
            vLinkSub.text = band
            vLinkSub.setTextColor(pal.muted)
        }
        vRouter.text = ms(router)
        vRouterSub.text = pingWord(router)
        vRouterSub.setTextColor(pingColor(router))
        vNet.text = ms(net)
        vNetSub.text = pingWord(net)
        vNetSub.setTextColor(pingColor(net))
        vBars.submit(hist)
    }

    @Suppress("DEPRECATION")
    private fun startMonitor() {
        if (running) return
        running = true
        Thread {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            while (running) {
                val w = wm.connectionInfo
                val d = wm.dhcpInfo
                val router = ping(targetHost, targetPort)
                val net = ping("1.1.1.1", 443)
                val hist: List<Int>
                synchronized(history) {
                    history.add(net)
                    if (history.size > 30) history.removeAt(0)
                    hist = ArrayList(history)
                }
                // networkId is -1 on new Android without Location access, so test signal instead
                val connected = w != null && w.rssi > -127
                val ssid = if (connected) w.ssid.trim('"').let {
                    if (it == "<unknown ssid>") "(allow Location to see name)" else it
                } else ""
                val rssi = if (connected) w.rssi else 0
                val freq = if (connected) w.frequency else 0
                val linkSpeed = if (connected) w.linkSpeed else 0
                val gw = ipStr(d?.gateway ?: 0)
                runOnUiThread {
                    showStats(connected, ssid, rssi, freq, linkSpeed, router, net, hist, gw)
                }
                try { Thread.sleep(2000) } catch (e: InterruptedException) { break }
            }
        }.start()
    }

    // ping history as small bars (newest on the right)
    private inner class BarsView(ctx: Context) : View(ctx) {
        private var data: List<Int> = emptyList()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun submit(h: List<Int>) { data = h; invalidate() }

        override fun onDraw(cv: Canvas) {
            val n = 30
            if (width == 0 || height == 0) return
            val gap = dp(3).toFloat()
            val bw = (width - gap * (n - 1)) / n
            val r = dp(2).toFloat()
            val shown = data.takeLast(n)
            val pad = n - shown.size
            for (i in 0 until n) {
                val x = i * (bw + gap)
                val v = if (i < pad) -2 else shown[i - pad]
                val frac: Float
                when {
                    v == -2 -> { p.color = pal.line; frac = 0.15f }
                    v < 0 -> { p.color = pal.bad; frac = 0.4f }
                    else -> {
                        p.color = if (v < 80) pal.accent else if (v < 200) pal.warn else pal.bad
                        frac = (v / 120f).coerceIn(0.2f, 1f)
                    }
                }
                val h = height * frac
                cv.drawRoundRect(RectF(x, height - h, x + bw, height.toFloat()), r, r, p)
            }
        }
    }

    companion object {
        private const val DEVELOPER = "Lestrrr233"
        private const val GITHUB_URL = "https://github.com/j4eh1/RouterWatch"
        // Router endpoints for WLAN MAC filter. If add/remove fail, confirm in browser F12 > Network and edit here.
        private const val FILTER_PAGE = "/html/bbsp/wlanmacfilter/wlanmacfilter.asp"
        private const val FILTER_OBJ = "InternetGatewayDevice.X_HW_Security.WLANMacFilter"

        private fun hex(s: String) = Color.parseColor(s)

        private val LIGHT = Pal(
            dark = false,
            bg = hex("#F2F4F8"), surface = hex("#FFFFFF"), tile = hex("#F5F7FB"), tag = hex("#F2F4F8"),
            line = hex("#DDE2EA"), text = hex("#1B1F27"), muted = hex("#5B6472"),
            header = hex("#1565C0"), statusBar = hex("#0D47A1"), headerSub = hex("#DCEBFF"),
            accent = hex("#1565C0"), accentSoft = hex("#E3EEFB"), onFill = hex("#FFFFFF"),
            good = hex("#2E7D32"), goodSoft = hex("#E6F4E7"), bad = hex("#C62828"), badSoft = hex("#FBE9E9"),
            warn = hex("#E09B00"))

        private val DARK = Pal(
            dark = true,
            bg = hex("#0E1217"), surface = hex("#181D25"), tile = hex("#212832"), tag = hex("#252C37"),
            line = hex("#2C3440"), text = hex("#E8EBF0"), muted = hex("#98A2B3"),
            header = hex("#102B4E"), statusBar = hex("#0A1F3A"), headerSub = hex("#B7CCE8"),
            accent = hex("#7AB4F5"), accentSoft = hex("#1F3858"), onFill = hex("#0B1626"),
            good = hex("#6FCF73"), goodSoft = hex("#1D3524"), bad = hex("#F07272"), badSoft = hex("#3B2226"),
            warn = hex("#FFC247"))
    }
}
