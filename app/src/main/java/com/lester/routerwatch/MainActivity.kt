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
import java.util.Locale
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
const val ICON_WIFI = 6
const val ICON_SLIDERS = 7

/** MAC Vendor Lookup helper for instant brand identification based on OUI prefix. */
object MacVendorLookup {
    private val knownVendors = mapOf(
        "00:03:93" to "Apple", "00:05:02" to "Apple", "00:0A:95" to "Apple",
        "00:11:24" to "Apple", "00:14:08" to "Apple", "00:1C:B3" to "Apple",
        "00:21:E9" to "Apple", "00:23:12" to "Apple", "00:25:00" to "Apple",
        "00:00:F0" to "Samsung", "00:02:78" to "Samsung", "00:07:AB" to "Samsung",
        "00:12:FB" to "Samsung", "00:15:B9" to "Samsung", "00:1A:8A" to "Samsung",
        "00:08:22" to "Infinix/Tecno", "00:18:01" to "LG Electronics",
        "00:0E:C6" to "ASUSTek", "00:13:D4" to "ASUSTek",
        "00:04:23" to "Intel", "00:0E:0C" to "Intel", "00:13:E8" to "Intel",
        "00:09:5B" to "Netgear", "00:14:6C" to "Netgear",
        "00:0A:EB" to "TP-Link", "00:1D:0F" to "TP-Link", "00:27:19" to "TP-Link",
        "00:0B:6B" to "Wistron", "00:1E:10" to "Huawei", "00:25:9E" to "Huawei",
        "00:1A:11" to "Google", "00:1A:73" to "Sony", "00:1E:C2" to "Apple",
        "3C:22:FB" to "Xiaomi", "64:09:80" to "Xiaomi", "DC:21:5E" to "Xiaomi"
    )

    fun cleanMac(mac: String): String {
        val clean = mac.replace("[^a-fA-F0-9]".toRegex(), "").uppercase(Locale.ROOT)
        if (clean.length < 6) return mac
        return clean.chunked(2).joinToString(":")
    }

    fun getBrandLocal(mac: String): String {
        val formatted = cleanMac(mac)
        if (formatted.length < 8) return "Unknown Brand"
        val prefix = formatted.substring(0, 8)
        return knownVendors[prefix] ?: "Unknown Brand"
    }
}

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
            ICON_WIFI -> {
                cv.drawCircle(12f, 19f, 0.8f, p)
                for (r in floatArrayOf(3.5f, 7f, 10.5f))
                    cv.drawArc(RectF(12f - r, 19f - r, 12f + r, 19f + r), 225f, 90f, false, p)
            }
            ICON_SLIDERS -> {
                cv.drawLine(4f, 7f, 6.8f, 7f, p); cv.drawLine(11.2f, 7f, 20f, 7f, p)
                cv.drawCircle(9f, 7f, 2.2f, p)
                cv.drawLine(4f, 17f, 12.8f, 17f, p); cv.drawLine(17.2f, 17f, 20f, 17f, p)
                cv.drawCircle(15f, 17f, 2.2f, p)
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
    private lateinit var vBars: View
    private lateinit var vDown: TextView
    private lateinit var vUp: TextView
    private lateinit var speedStatus: TextView
    private lateinit var monitorPage: LinearLayout
    private lateinit var wifiPage: LinearLayout
    private lateinit var wifiStatus: TextView
    private lateinit var titleView: TextView
    private lateinit var refreshBtn: FrameLayout
    @Volatile private var testing = false
    private lateinit var userBox: EditText
    private lateinit var passBox: EditText
    private lateinit var status: TextView
    private lateinit var acctStatus: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var listPage: LinearLayout
    private lateinit var accountPage: LinearLayout
    private lateinit var chipBtns: List<TextView>
    private lateinit var tabBtns: List<TextView>
    private lateinit var themeBtns: List<TextView>
    private val navIcons = ArrayList<IconView>()
    private val navPills = ArrayList<FrameLayout>()
    private val navLabels = ArrayList<TextView>()
    private val history = ArrayList<Int>()
    private var devCache: List<Dev> = emptyList()
    private var filterCache: List<FilterEntry> = emptyList()
    private var page = 0           // 0 = devices, 1 = filter list, 2 = settings, 3 = network, 4 = wi-fi
    private var netTab = 0         // network sub tab
    private var wifiTab = 0        // wi-fi sub tab
    private var setTab = 0         // settings sub tab
    private var pickLogin: (Int) -> Unit = {}
    private var tab = 0            // list shown
    private var statusFilter = 0   // 0 = all, 1 = online, 2 = offline
    private var themeMode = 0      // 0 = follow system, 1 = light, 2 = dark
    private var role = 0           // 0 = normal admin, 1 = super admin
    private var pal: Pal = LIGHT
    private var lastStatus = "Open Settings, enter router login, then tap refresh."
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT
    private val prefs by lazy { getSharedPreferences("rw", MODE_PRIVATE) }

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
            val sel = when (i) { 0 -> page < 2; 1 -> page == 3; 2 -> page == 4; 3 -> page == 2; else -> false }
            navPills[i].background = rounded(if (sel) pal.accentSoft else Color.TRANSPARENT, 15)
            navIcons[i].tint(if (sel) pal.accent else pal.muted)
            navLabels[i].setTextColor(if (sel) pal.accent else pal.muted)
            navLabels[i].typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun showPage(p: Int) {
        page = p
        if (p < 2) tab = p
        listPage.visibility = if (p < 2) View.VISIBLE else View.GONE
        accountPage.visibility = if (p == 2) View.VISIBLE else View.GONE
        monitorPage.visibility = if (p == 3) View.VISIBLE else View.GONE
        wifiPage.visibility = if (p == 4) View.VISIBLE else View.GONE
        titleView.text = when (p) { 2 -> "Settings"; 3 -> "Network"; 4 -> "Wi-Fi"; else -> "Devices" }
        refreshBtn.visibility = if (p < 2) View.VISIBLE else View.INVISIBLE
        paintNav()
        if (p < 2) refresh()
    }

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

    private fun renderDevices(devList: List<Dev>) {
        deviceList.removeAllViews()
        var countAll = 0; var countOn = 0; var countOff = 0
        for (dev in devList) {
            val st = stateOfDev(dev)
            if (st == 1) countOn++ else if (st == 2) countOff++
            countAll++
            if (!passes(st)) continue

            val nickname = nick(dev.mac)
            val brand = MacVendorLookup.getBrandLocal(dev.mac)
            val displayName = nickname.ifBlank {
                if (dev.host.isNotBlank() && dev.host != "Unknown") dev.host else brand
            }
            val subText = "${dev.ip}  •  ${dev.mac}  •  $brand"

            val card = itemCard(
                name = displayName,
                sub = subText,
                state = st,
                ssid = ssidLabel(dev.ssid.toString()),
                action = "Block",
                actionColor = pal.bad,
                onAction = { },
                onTap = { copyMac(dev.mac) },
                onRename = { renameDialog(dev.mac, dev.host) }
            )
            deviceList.addView(card)
        }
        updateChips(countAll, countOn, countOff)
        if (deviceList.childCount == 0) deviceList.addView(emptyNote(devList.isEmpty()))
    }

    private fun renderFilter(filterList: List<FilterEntry>) {
        deviceList.removeAllViews()
        var countAll = 0; var countOn = 0; var countOff = 0
        for (f in filterList) {
            val st = stateOfMac(f.mac)
            if (st == 1) countOn++ else if (st == 2) countOff++
            countAll++
            if (!passes(st)) continue

            val nickname = nick(f.mac)
            val brand = MacVendorLookup.getBrandLocal(f.mac)
            val displayName = nickname.ifBlank {
                if (f.name.isNotBlank() && f.name != "Unknown") f.name else brand
            }
            val subText = "${f.mac}  •  $brand"

            val card = itemCard(
                name = displayName,
                sub = subText,
                state = st,
                ssid = ssidLabel(f.ssid),
                action = "Unblock",
                actionColor = pal.good,
                onAction = { },
                onTap = { copyMac(f.mac) },
                onRename = { renameDialog(f.mac, f.name) }
            )
            deviceList.addView(card)
        }
        updateChips(countAll, countOn, countOff)
        if (deviceList.childCount == 0) deviceList.addView(emptyNote(filterList.isEmpty()))
    }

    private fun refresh() {
        if (tab == 0) renderDevices(devCache) else renderFilter(filterCache)
    }

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
        onRename: (() -> Unit)? = null
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
            if (onRename != null) addView(pillButton("Rename", pal.muted, false) { onRename() }.apply {
                layoutParams = LinearLayout.LayoutParams(WC, dp(44)).apply { setMargins(0, 0, dp(6), 0) }
            })
            addView(pillButton(action, actionColor, false) { onAction() })
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, dp(8), 0, 0) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (onTap != null) ripple(cardBg(), 24) else cardBg()
            elevation = 0f
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

    private fun copyMac(mac: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mac", mac))
        Toast.makeText(this, "MAC copied", Toast.LENGTH_SHORT).show()
    }

    private fun buildUi() {
        pal = if (isDarkNow()) DARK else LIGHT
        window.setBackgroundDrawable(bgGrad())
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            if (!pal.dark) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            else 0
        navIcons.clear(); navPills.clear(); navLabels.clear()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        subtitle = txt("Starting...", 12f, pal.headerSub)
        titleView = txt("Devices", 26f, pal.text, true)
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            addView(titleView)
            addView(subtitle)
        }
        refreshBtn = FrameLayout(this).apply {
            contentDescription = "Get devices"
            background = ripple(oval(pal.surface), 22)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            addView(IconView(this@MainActivity, ICON_REFRESH, pal.accent),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.header)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(titleCol)
            addView(refreshBtn)
        }

        tabBtns = listOf(
            tabBtn("Devices") { showPage(0) },
            tabBtn("Filter list") { showPage(1) })
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.tile, 26, pal.line, hair())
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

        userBox = field("Router account", false)
        passBox = field("Router password", true)
        acctStatus = txt(lastStatus, 12f, pal.muted).apply { setPadding(dp(2), dp(8), dp(2), 0) }

        val acctBox = panel().apply {
            addView(txt("Router Login", 16f, pal.text, true))
            addView(userBox)
            addView(passBox)
            addView(acctStatus)
        }

        themeBtns = listOf(
            tabBtn("System") { setThemeMode(0) },
            tabBtn("Light") { setThemeMode(1) },
            tabBtn("Dark") { setThemeMode(2) }
        )
        val themeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pal.tile, 26, pal.line, hair())
            setPadding(dp(3), dp(3), dp(3), dp(3))
            for (b in themeBtns) addView(b)
        }

        val appearanceBox = panel().apply {
            addView(txt("Appearance", 16f, pal.text, true).apply { setPadding(0, 0, 0, dp(8)) })
            addView(themeBar)
        }

        accountPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(acctBox)
            addView(appearanceBox)
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
            visibility = View.GONE
        }

        monitorPage = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
            visibility = View.GONE
        }

        wifiPage = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MP, 0, 1f)
            visibility = View.GONE
        }

        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(pal.surface)
            elevation = dp(8).toFloat()
            addView(navItem("Devices", ICON_DEVICES) { showPage(0) })
            addView(navItem("Network", ICON_PULSE) { showPage(3) })
            addView(navItem("Wi-Fi", ICON_WIFI) { showPage(4) })
            addView(navItem("Settings", ICON_SLIDERS) { showPage(2) })
        }

        root.addView(header)
        root.addView(listPage)
        root.addView(accountPage)
        root.addView(monitorPage)
        root.addView(wifiPage)
        root.addView(navBar)

        setContentView(root)
        showPage(page)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = prefs.getInt("theme", 0)
        buildUi()
    }

    companion object {
        val GRAD_LIGHT = intArrayOf(Color.parseColor("#F8FAFC"), Color.parseColor("#EEF2F6"))
        val GRAD_DARK = intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#020617"))

        val LIGHT = Pal(
            dark = false,
            bg = Color.parseColor("#F8FAFC"),
            surface = Color.parseColor("#FFFFFF"),
            tile = Color.parseColor("#F1F5F9"),
            tag = Color.parseColor("#E2E8F0"),
            line = Color.parseColor("#E2E8F0"),
            text = Color.parseColor("#0F172A"),
            muted = Color.parseColor("#64748B"),
            header = Color.parseColor("#FFFFFF"),
            statusBar = Color.parseColor("#F8FAFC"),
            headerSub = Color.parseColor("#64748B"),
            accent = Color.parseColor("#2563EB"),
            accentSoft = Color.parseColor("#DBEAFE"),
            onFill = Color.parseColor("#FFFFFF"),
            good = Color.parseColor("#16A34A"),
            goodSoft = Color.parseColor("#DCFCE7"),
            bad = Color.parseColor("#DC2626"),
            badSoft = Color.parseColor("#FEE2E2"),
            warn = Color.parseColor("#D97706")
        )

        val DARK = Pal(
            dark = true,
            bg = Color.parseColor("#0F172A"),
            surface = Color.parseColor("#1E293B"),
            tile = Color.parseColor("#334155"),
            tag = Color.parseColor("#334155"),
            line = Color.parseColor("#334155"),
            text = Color.parseColor("#F8FAFC"),
            muted = Color.parseColor("#94A3B8"),
            header = Color.parseColor("#1E293B"),
            statusBar = Color.parseColor("#0F172A"),
            headerSub = Color.parseColor("#94A3B8"),
            accent = Color.parseColor("#3B82F6"),
            accentSoft = Color.parseColor("#1E3A8A"),
            onFill = Color.parseColor("#FFFFFF"),
            good = Color.parseColor("#22C55E"),
            goodSoft = Color.parseColor("#14532D"),
            bad = Color.parseColor("#EF4444"),
            badSoft = Color.parseColor("#7F1D1D"),
            warn = Color.parseColor("#F59E0B")
        )
    }
}
