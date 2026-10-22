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
const val ICON_PULSE = 5

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
            ICON_PULSE -> {
                path.moveTo(3f, 12f); path.lineTo(8f, 12f); path.lineTo(10.5f, 5f)
                path.lineTo(13.5f, 19f); path.lineTo(16f, 12f); path.lineTo(21f, 12f)
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
    private lateinit var vDown: TextView
    private lateinit var vUp: TextView
    private lateinit var speedStatus: TextView
    private lateinit var monitorPage: ScrollView
    @Volatile private var testing = false
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
    private var page = 0           // 0 = devices, 1 = filter list, 2 = account, 3 = monitor
    private var tab = 0            // list shown: 0 = devices, 1 = filter list
    private var statusFilter = 0   // 0 = all, 1 = online, 2 = offline
    private var wlEnabled: Boolean? = null   // router WLAN filter on/off, null = unknown
    private var wlGuess = false              // true = value is only "last set by this app"
    private var wlError = ""                 // why the state could not be read
    private var themeMode = 0      // 0 = follow system, 1 = light, 2 = dark
    private var role = 0           // 0 = normal admin, 1 = super admin (own saved login each)
    private lateinit var roleBtns: List<TextView>
    private fun userKey() = if (role == 1) "user_su" else "user"
    private fun passKey() = if (role == 1) "pass_su" else "pass"
    private var pal: Pal = LIGHT
    private var lastStatus = "Open Account, enter router login, then tap refresh."
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT
    @Volatile private var running = false
    @Volatile private var monitor: Thread? = null
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
            background = rounded(pal.tile, 18, pal.line, hair())
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
        val thumb = if (pal.dark) Color.parseColor("#4DFFFFFF") else Color.WHITE
        b.background = ripple(rounded(if (selected) thumb else Color.TRANSPARENT, 22,
            if (selected) pal.line else 0, if (selected) hair() else 0), 22)
        b.setTextColor(if (selected) pal.text else pal.muted)
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
            val sel = when (i) { 0 -> page == 0; 1 -> page == 1; 2 -> page == 3; 3 -> page == 2; else -> false }
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
        monitorPage.visibility = if (p == 3) View.VISIBLE else View.GONE
        paintNav()
        if (p < 2) refresh()
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

    private fun say(s: String) {
        lastStatus = s
        if (::status.isInitialized) status.text = s
        if (::acctStatus.isInitialized) acctStatus.text = s
    }

    // ---------- cards ----------
    private fun cardBg() = rounded(pal.surface, 24, pal.line, hair())

    private fun bgGrad() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        if (pal.dark) GRAD_DARK else GRAD_LIGHT)

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg()
        elevation = 0f
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun itemCard(
        name: String, sub: String, state: Int, ssid: String,
        action: String, actionColor: Int, onAction: () -> Unit, onTap: (() -> Unit)?,
        onLong: (() -> Unit)? = null
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
            background = if (onTap != null || onLong != null) ripple(cardBg(), 24) else cardBg()
            elevation = 0f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(8)) }
            addView(top); addView(bottom)
            if (onTap != null) setOnClickListener { onTap() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
    }

    private fun emptyNote(noData: Boolean) = txt(
        if (noData) "Nothing here yet. Tap the refresh button." else "Nothing matches this filter.",
        12f, pal.muted).apply { setPadding(dp(4), dp(12), dp(4), 0) }

    private fun ssidLabel(s: String) =
        if (s.isBlank()) "SSID --" else if (s.startsWith("SSID", true)) s else "SSID-$s"

    // ---------- own device names (saved on this phone only) ----------
    private fun nick(mac: String): String = prefs.getString("nick_" + mac.lowercase(), "") ?: ""
    private fun showName(mac: String, routerName: String) = nick(mac).ifBlank { routerName }

    private fun renameDialog(mac: String, routerName: String) {
        val box = EditText(this).apply {
            setSingleLine()
            setText(nick(mac))
            hint = routerName.ifBlank { "Device name" }
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(box)
        }
        dlg().setTitle("Name this device").setMessage(mac).setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("nick_" + mac.lowercase(), box.text.toString().trim()).apply()
                refresh()
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.edit().remove("nick_" + mac.lowercase()).apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- change Wi-Fi name / password (band steering: SSID1 drives 2.4G + 5G) ----------
    // Password blank = change the name only, current password is kept.
    private fun wifiBlock(label: String): LinearLayout {
        val name = field("Wi-Fi name", false)
        val pass = field("New password (blank = keep current)", true)
        val btn = pillButton("Apply to $label", pal.accent, true) {
            val n = name.text.toString().trim()
            val p = pass.text.toString()
            when {
                n.isEmpty() -> Toast.makeText(this, "Enter the Wi-Fi name", Toast.LENGTH_SHORT).show()
                n.length > 32 -> Toast.makeText(this, "Name can be 32 characters at most", Toast.LENGTH_SHORT).show()
                p.isNotEmpty() && p.length !in 8..63 ->
                    Toast.makeText(this, "Password must be 8 to 63 characters", Toast.LENGTH_SHORT).show()
                else -> confirmWifi(label, n, p)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MP, dp(48)).apply { setMargins(0, dp(8), 0, dp(8)) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt(label, 13f, pal.text, true).apply { setPadding(0, dp(8), 0, 0) })
            addView(name); addView(pass); addView(btn)
        }
    }

    private fun confirmWifi(label: String, name: String, pass: String) {
        val c = client ?: run { needLogin(); return }
        val same = pass.isEmpty()
        dlg()
            .setTitle("Change $label?")
            .setMessage("New name: $name\n" + (if (same) "Password: stays the same\n" else "") +
                    "\nDevices on this Wi-Fi disconnect, including this phone if it uses it. " +
                    (if (same) "Reconnect with the new name." else "Reconnect with the new name and password."))
            .setPositiveButton("Apply") { _, _ ->
                say("Changing $label ...")
                bg({ c.setWifi(name, pass) }) { r ->
                    r.onSuccess { say("Sent. Reconnect to \"$name\" if Wi-Fi dropped.") }
                    r.onFailure {
                        say("Failed: ${it.message}. Wi-Fi may have restarted anyway.")
                        showDebug("Wi-Fi change - copy and send this", c.lastDebug + "\n" + (it.message ?: ""))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyMac(mac: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mac", mac))
        Toast.makeText(this, "MAC copied", Toast.LENGTH_SHORT).show()
    }

    // ---------- build the whole screen (called again when theme changes) ----------
    @Suppress("DEPRECATION")
    private fun buildUi() {
        val keepUser = if (::userBox.isInitialized) userBox.text.toString()
        else (prefs.getString(userKey(), "") ?: "")
        val keepPass = if (::passBox.isInitialized) passBox.text.toString()
        else (prefs.getString(passKey(), null)?.let { decrypt(it) } ?: "")

        pal = if (isDarkNow()) DARK else LIGHT
        window.setBackgroundDrawable(bgGrad())
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            if (!pal.dark) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            else 0
        navIcons.clear(); navPills.clear(); navLabels.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ----- header -----
        subtitle = txt("Starting...", 12f, pal.headerSub)
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            addView(txt("RouterWatch", 26f, pal.text, true))
            addView(subtitle)
        }
        val refreshBtn = FrameLayout(this).apply {
            contentDescription = "Get devices"
            background = ripple(oval(pal.surface), 22)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            addView(IconView(this@MainActivity, ICON_REFRESH, pal.accent),
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
            elevation = 0f
            setPadding(dp(6), dp(6), dp(6), dp(10))
            addView(row(tSignal, tLink))
            addView(row(tRouter, tNet))
            addView(vBars, LinearLayout.LayoutParams(MP, dp(24)).apply { setMargins(dp(8), dp(8), dp(8), 0) })
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, 0, 0, dp(10)) }
        }

        // ----- list page: tabs, chips, status, list -----
        tabBtns = listOf(
            tabBtn("Devices") { showPage(0) },
            tabBtn("Filter list") { openFilter() })
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.tile, 26, pal.line, hair())
            elevation = 0f
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
        acctStatus = txt(lastStatus, 12f, pal.muted).apply { setPadding(dp(2), dp(8), dp(2), 0) }
        val getBtn = pillButton("Get devices", pal.accent, true) { fetchDevices() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 2f).apply { setMargins(0, dp(8), dp(4), 0) }
        }
        val forgetBtn = pillButton("Forget login", pal.bad, false) { forgetLogin() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(8), 0, 0) }
        }
        roleBtns = listOf("Normal admin", "Super admin").mapIndexed { i, t -> tabBtn(t) { setRole(i) } }
        val roleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.tag, 26)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            for (b in roleBtns) addView(b)
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, dp(8), 0, dp(4)) }
        }
        roleBtns.forEachIndexed { i, b -> paintTab(b, role == i) }
        val loginCard = panel().apply {
            addView(txt("Router login", 15f, pal.text, true))
            addView(txt("Used to read devices and edit the WLAN MAC filter.", 12f, pal.muted))
            addView(roleBar)
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
        val wifiCard = panel().apply {
            addView(txt("Wi-Fi name and password", 15f, pal.text, true))
            addView(txt("Needs router login. Leave password blank to change only the name. " +
                    "Band steering: one name and password for 2.4G and 5G.", 12f, pal.muted))
            addView(wifiBlock("Wi-Fi (2.4G + 5G)"))
        }
        val acctCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(loginCard)
            addView(wifiCard)
            addView(themeCard)
            addView(aboutCard)
        }
        accountPage = ScrollView(this).apply {
            addView(acctCol)
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
        }

        // ----- monitor page: live stats + speed test -----
        val (tDown, dv, _) = tile("Download"); vDown = dv
        val (tUp, uv, _) = tile("Upload"); vUp = uv
        speedStatus = txt("Tap Start to test your internet speed.", 12f, pal.muted)
            .apply { setPadding(dp(2), dp(8), dp(2), 0) }
        val startBtn = pillButton("Start speed test", pal.accent, true) { runSpeedTest() }.apply {
            layoutParams = LinearLayout.LayoutParams(MP, dp(48)).apply { setMargins(0, dp(8), 0, 0) }
        }
        val speedCard = panel().apply {
            addView(txt("Speed test", 15f, pal.text, true))
            addView(txt("Uses Cloudflare. About 28 MB of data per test.", 12f, pal.muted))
            addView(row(tDown, tUp))
            addView(startBtn)
            addView(speedStatus)
        }
        val monCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(statsCard)
            addView(speedCard)
        }
        monitorPage = ScrollView(this).apply {
            addView(monCol)
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
        }

        // ----- bottom nav -----
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.surface, 32, pal.line, hair())
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(dp(16), dp(4), dp(16), dp(12)) }
            addView(navItem("Devices", ICON_DEVICES) { showPage(0) })
            addView(navItem("Filter list", ICON_SHIELD) { openFilter() })
            addView(navItem("Monitor", ICON_PULSE) { showPage(3) })
            addView(navItem("Account", ICON_PERSON) { showPage(2) })
        }

        root.addView(header)
        root.addView(listPage)
        root.addView(accountPage)
        root.addView(monitorPage)
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
            prefs.edit().putString(userKey(), user).putString(passKey(), encrypt(pass)).apply()
        } catch (e: Exception) {
            prefs.edit().putString(userKey(), user).remove(passKey()).apply()
        }
    }

    private fun setRole(r: Int) {
        role = r
        prefs.edit().putInt("role", r).apply()
        userBox.setText(prefs.getString(userKey(), "") ?: "")
        passBox.setText(prefs.getString(passKey(), null)?.let { decrypt(it) } ?: "")
        roleBtns.forEachIndexed { i, b -> paintTab(b, role == i) }
        say("${if (r == 1) "Super admin" else "Normal admin"}: tap Get devices to log in.")
    }

    private fun forgetLogin() {
        prefs.edit().remove(userKey()).remove(passKey()).apply()
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
        role = prefs.getInt("role", 0)
        page = 2   // start on the login page
        // QR feature removed: drop the Wi-Fi name/password it saved earlier
        prefs.edit().remove("wifi_ssid").remove("wifi_pass").apply()
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
    // interrupt so the old thread leaves its 2 s sleep instead of surviving a quick resume
    override fun onPause() { running = false; monitor?.interrupt(); super.onPause() }

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
                    else "${list.size} device(s). Tap = copy MAC. Long-press = rename. Button = add to filter.")
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
                showName(d.mac, d.host).ifBlank { "(unnamed device)" }, sub, stateOfDev(d), "SSID-${d.ssid}",
                "Add to filter list", pal.accent, { addToFilter(d) }, { copyMac(d.mac) },
                { renameDialog(d.mac, d.host) }))
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
        val name = showName(d.mac, d.host).ifBlank { "device" }.replace(Regex("[^A-Za-z0-9_.-]"), "-").take(31)
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
                showName(e.mac, e.name).ifBlank { "(no name)" }, e.mac, st[i], ssidLabel(e.ssid),
                "Remove", pal.bad, { removeFromFilter(e) }, null, { renameDialog(e.mac, e.name) }))
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
            elevation = 0f
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
        private var referer: String? = null

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
            c.setRequestProperty("Referer", referer ?: ("https://$host/html/bbsp/wlanmacfilter/set.cgi?" +
                    "x=InternetGatewayDevice.X_HW_Security&RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp"))
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
                if (loginBody.contains("upper threshold", true) || loginBody.contains("already logged", true))
                    throw Exception("Router says another admin session is open (browser or earlier app login). " +
                            "Close/log out there, wait ~5 min, then retry.")
                val dbg = "HTTP ${lc.responseCode}, cookies=${sc ?: "none"}, body=" +
                        loginBody.replace(Regex("\\s+"), " ").take(120)
                throw Exception("Login rejected. $dbg")
            }
            cookie = session
            if (loginBody.isEmpty() && lc.responseCode >= 400) throw Exception("Login HTTP ${lc.responseCode}")
        }

        fun devices(): List<Dev> {
            val paths = listOf(
                "/html/bbsp/common/GetLanUserDevInfo.asp",
                "/html/bbsp/common/GetLanUserInfo.asp"
            )
            for (p in paths) {
                try {
                    val list = parse(read(open(p, false)))
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) { }
            }
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
        private fun pageToken(page: String = FILTER_PAGE): String? = try {
            val html = read(open(page, false))
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

        // Current 2.4G (SSID1) Wi-Fi password. The router's WlanBasic page prints it in plain text
        // (wifiPasswordMask = 0) and its own Apply button resubmits it with every save, so we do the same.
        private fun currentWifiKey(): String {
            val html = read(open(WLAN_PAGE, false))
            val m = Regex("""WLANConfiguration\\x2e1\\x2ePreSharedKey\\x2e1"\s*,\s*"([^"]*)""").find(html)
            val key = m?.let { unescape(it.groupValues[1]) }.orEmpty()
            if (key.isEmpty()) throw Exception("Cannot read the current Wi-Fi password from the router page.")
            return key
        }

        // SSID1 (2.4G) name + password. Fields copied from a browser capture of the router's own
        // page: it resubmits the whole form, so only SSID and key change, the rest stays as captured.
        // Band steering shares one SSID/password, so this single request covers both bands.
        // pass blank = name-only change: the current key is read from the router and sent back unchanged.
        fun setWifi(ssid: String, pass: String) {
            log.clear()
            val y = "$WLAN_OBJ.1"
            val url = "$WLAN_SET?w=$WLAN_COVER&y=$y&z=$y.WPS&k=$y.PreSharedKey.1" +
                    "&RequestFile=${WLAN_PAGE.trimStart('/')}"
            referer = "https://$host$WLAN_PAGE"
            try {
                val n = enc(ssid)
                val p = enc(pass.ifEmpty { currentWifiKey() })
                // This page's own Apply button sends the token printed in the page (hidden "onttoken").
                // Try that first, then the ajax token. A wrong token is ignored silently by the router,
                // so after each try read the name back and only stop when it really changed.
                for (kind in 0..1) {
                    val t = (if (kind == 0) pageToken(WLAN_PAGE) else cfgToken()) ?: continue
                    post(url, wifiBody(n, p, t))
                    Thread.sleep(2000)
                    // read fails when Wi-Fi restarted and the phone dropped off: treat as applied
                    val now = try { currentSsid() } catch (e: Exception) { return }
                    if (now == ssid) return
                }
                throw Exception("Router ignored the change (name is still \"${currentSsid()}\")")
            } finally { referer = null }
        }

        private fun currentSsid(): String {
            val html = read(open(WLAN_PAGE, false))
            val m = Regex("""new stWlan\("[^"]*WLANConfiguration\\x2e1","[^"]*","[^"]*","([^"]*)""").find(html)
                ?: throw Exception("Cannot read the current Wi-Fi name from the router page.")
            return unescape(m.groupValues[1])
        }

        private fun wifiBody(n: String, p: String, token: String): String =
            "y.Enable=1&y.SSIDAdvertisementEnabled=1&y.SSID=$n&y.X_HW_AssociateNum=10" +
                        "&y.BeaconType=11i&y.IEEE11iAuthenticationMode=PSKAuthentication" +
                        "&y.IEEE11iEncryptionModes=AESEncryption&k.PreSharedKey=$p&y.X_HW_GroupRekey=3600" +
                        "&z.Enable=0&z.X_HW_ConfigMethod=PushButton&w.SsidInst=1&w.SSID=$n&w.Enable=1" +
                        "&w.Standard=11bgn&w.BasicAuthenticationMode=None&w.BasicEncryptionModes=AESEncryption" +
                        "&w.WPAAuthenticationMode=EAPAuthentication&w.WPAEncryptionModes=AESEncryption" +
                        "&w.IEEE11iAuthenticationMode=PSKAuthentication&w.IEEE11iEncryptionModes=AESEncryption" +
                        "&w.MixAuthenticationMode=EAPAuthentication&w.MixEncryptionModes=AESEncryption" +
                        "&w.SSIDAdvertisementEnabled=1&w.WMMEnable=1&w.MaxAssociateNum=10&w.BeaconType=11i" +
                        "&w.WEPEncryptionLevel=104-bit&w.WEPKeyIndex=1&w.Key=$p&x.X_HW_Token=${enc(token)}"
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
            // Router JS args are positional. HostName = 10th arg (11th for USERDeviceNew),
            // UserDevAlias = 15th (16th). Old guess picked DevType ("android-dhcp-11") instead.
            val off = if (m.value.contains("USERDeviceNew")) 1 else 0
            val alias = strs.getOrNull(14 + off)?.trim().orEmpty()
            val hostName = strs.getOrNull(9 + off)?.trim().orEmpty()
            val host = when {
                alias.isNotBlank() && alias != "--" -> alias
                hostName.isNotBlank() && hostName != "--" -> hostName
                strs.size <= 9 + off -> strs.firstOrNull {
                    it.isNotBlank() && !macRe.matches(it) && !ipRe.matches(it) && !skipRe.matches(it)
                } ?: ""
                else -> ""
            }
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
        monitor = Thread {
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
        }.also { it.start() }
    }

    // ---------- speed test (Cloudflare) ----------
    private fun mbps(bytes: Long, nanos: Long) = bytes * 8.0 * 1000.0 / nanos
    private fun fmtMbps(v: Double) = String.format(java.util.Locale.US, "%.1f Mbps", v)

    private fun speedDown(onTick: (Double) -> Unit): Double {
        val c = URL("https://speed.cloudflare.com/__down?bytes=20000000").openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        val buf = ByteArray(65536)
        var total = 0L
        var last = 0L
        val t0 = System.nanoTime()
        try {
            c.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    val now = System.nanoTime()
                    if (now - last > 250_000_000L) { last = now; onTick(mbps(total, now - t0)) }
                    if (now - t0 > 8_000_000_000L) break
                }
            }
        } finally { c.disconnect() }
        return mbps(total, System.nanoTime() - t0)
    }

    private fun speedUp(): Double {
        val size = 8_000_000
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 5000
        c.readTimeout = 15000
        c.setFixedLengthStreamingMode(size)
        val chunk = ByteArray(65536)
        val t0 = System.nanoTime()
        try {
            c.outputStream.use { os ->
                var sent = 0
                while (sent < size) {
                    val n = minOf(chunk.size, size - sent)
                    os.write(chunk, 0, n)
                    sent += n
                }
            }
            c.responseCode
        } finally { c.disconnect() }
        return mbps(size.toLong(), System.nanoTime() - t0)
    }

    private fun runSpeedTest() {
        if (testing) return
        testing = true
        vDown.text = "--"
        vUp.text = "--"
        speedStatus.text = "Testing download ..."
        Thread {
            var err = ""
            try {
                val d = speedDown { v -> runOnUiThread { vDown.text = fmtMbps(v) } }
                runOnUiThread { vDown.text = fmtMbps(d); speedStatus.text = "Testing upload ..." }
                val u = speedUp()
                runOnUiThread { vUp.text = fmtMbps(u) }
            } catch (e: Exception) { err = e.message ?: "error" }
            runOnUiThread {
                testing = false
                speedStatus.text = if (err.isEmpty()) "Done." else "Failed: $err"
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
        // Router endpoints for Wi-Fi name/password. Query copied from F12; the set.cgi path itself is assumed.
        private const val WLAN_PAGE = "/html/amp/wlanbasic/WlanBasic.asp"
        private const val WLAN_COVER = "InternetGatewayDevice.X_HW_DEBUG.AMP.WifiCoverSetWlanBasic"
        private const val WLAN_SET = "/html/amp/wlanbasic/set.cgi"
        private const val WLAN_OBJ = "InternetGatewayDevice.LANDevice.1.WLANConfiguration"

        private fun hex(s: String) = Color.parseColor(s)

        private val GRAD_LIGHT = intArrayOf(hex("#BCD7FF"), hex("#DCCBFF"), hex("#FFD3E6"))
        private val GRAD_DARK = intArrayOf(hex("#0B1226"), hex("#1E1547"), hex("#33143F"))

        private val LIGHT = Pal(
            dark = false,
            bg = hex("#DCCBFF"), surface = hex("#B3FFFFFF"), tile = hex("#80FFFFFF"), tag = hex("#66FFFFFF"),
            line = hex("#99FFFFFF"), text = hex("#1C1C1E"), muted = hex("#5A5A60"),
            header = Color.TRANSPARENT, statusBar = Color.TRANSPARENT, headerSub = hex("#5A5A60"),
            accent = hex("#0A74E0"), accentSoft = hex("#260A84FF"), onFill = hex("#FFFFFF"),
            good = hex("#1E8E3E"), goodSoft = hex("#2630D158"), bad = hex("#D70015"), badSoft = hex("#26FF3B30"),
            warn = hex("#C77700"))

        private val DARK = Pal(
            dark = true,
            bg = hex("#1E1547"), surface = hex("#26FFFFFF"), tile = hex("#1FFFFFFF"), tag = hex("#1AFFFFFF"),
            line = hex("#40FFFFFF"), text = hex("#F2F2F7"), muted = hex("#AEAEB8"),
            header = Color.TRANSPARENT, statusBar = Color.TRANSPARENT, headerSub = hex("#AEAEB8"),
            accent = hex("#5AA9FF"), accentSoft = hex("#3340A0FF"), onFill = hex("#06121F"),
            good = hex("#4ADE80"), goodSoft = hex("#2630D158"), bad = hex("#FF6B6B"), badSoft = hex("#26FF453A"),
            warn = hex("#FFC247"))
    }
}
