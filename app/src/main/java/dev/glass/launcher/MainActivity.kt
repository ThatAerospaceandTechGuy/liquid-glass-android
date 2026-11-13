package dev.glass.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast

/**
 * A CATEGORY_HOME activity with a transparent, wallpaper-showing window.
 *
 * View budget at rest: 1 root FrameLayout, 1 GridView (<= 20 recycled cells),
 * 1 dock shell + 1 GPU shadow view + 1 LinearLayout holding 4 recycled cells,
 * and a GestureDetector. The drawer and the settings sheet are built on first
 * use and removed again on close/stop, so the idle home screen stays tiny.
 */
class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())

    private lateinit var prefs: GlassPrefs
    private lateinit var icons: IconStore
    private lateinit var root: FrameLayout
    private lateinit var grid: GridView
    private lateinit var dockShell: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var dockShadow: GlassShadow
    private lateinit var homeAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter

    private var drawerLayer: FrameLayout? = null
    private var drawerPanel: LinearLayout? = null
    private var drawerGrid: GridView? = null
    private var searchBox: EditText? = null

    private var sheetLayer: FrameLayout? = null
    private var sheetView: View? = null

    private var allApps: List<AppEntry> = emptyList()
    private var gestures: GestureDetector? = null
    private var loadThread: Thread? = null
    private var drawerOpen = false
    private var sheetOpen = false

    private val density: Float get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = GlassPrefs(this)
        icons = IconStore(this)
        applyWindowFlags()
        buildHome()
        loadApps()
    }

    // ---------------------------------------------------------------- window

    private fun applyWindowFlags() {
        val window = window
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setStatusBarColor(Color.TRANSPARENT)
        window.setNavigationBarColor(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            window.attributes = params
        }
        applyWindowBlur()
    }

    /**
     * API 31+: ask the system compositor to blur whatever is behind this
     * window, which is the live wallpaper. Zero app-side bitmaps.
     */
    private fun applyWindowBlur() {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            window.setBackgroundBlurRadius(if (prefs.blurRadius > 0) dp(prefs.blurRadius) else 0)
        } catch (t: Throwable) {
            // Launcher windows on some OEM builds refuse cross window blur.
        }
    }

    // ------------------------------------------------------------------ home

    private fun buildHome() {
        root = FrameLayout(this)

        homeAdapter = AppAdapter(this, icons, prefs, showLabels = true, cellHeightDp = 92) {
            launch(it)
        }
        drawerAdapter = AppAdapter(this, icons, prefs, showLabels = true, cellHeightDp = 84) {
            launch(it)
        }

        grid = GridView(this).apply {
            id = View.generateViewId()
            numColumns = 4
            isVerticalScrollBarEnabled = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(4)
            horizontalSpacing = 0
            setPadding(dp(14), dp(10), dp(14), dp(10))
            clipToPadding = false
            setSelector(ColorDrawable(Color.TRANSPARENT))
            adapter = homeAdapter
            // Long-press on empty grid space opens the glass settings sheet.
            setOnLongClickListener {
                openSheet()
                true
            }
        }
        val gridParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        gridParams.bottomMargin = dp(118)
        root.addView(grid, gridParams)

        dockShell = FrameLayout(this)
        dockShadow = GlassShadow(this).apply {
            sideInsetDp = 22f
            bottomInsetDp = 8f
            shapeHeightDp = 84f
            cornerDp = 42f
            setBlurDp(prefs.blurRadius.toFloat())
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = LiquidGlassDrawable(density, prefs).apply {
                pill = true
                shadowDp = 0f
                tintAlpha = 80
            }
            setOnLongClickListener {
                openSheet()
                true
            }
        }
        dockShell.addView(
            dockShadow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val dockParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(84),
            Gravity.BOTTOM
        )
        dockParams.setMargins(dp(22), 0, dp(22), dp(8))
        dockShell.addView(dock, dockParams)

        val shellParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(112),
            Gravity.BOTTOM
        )
        shellParams.bottomMargin = dp(4)
        root.addView(dockShell, shellParams)

        applyInsets()
        gestures = buildGestures()
        setContentView(root)
        GlassEffects.attachPanel(dock, prefs)
    }

    private fun buildGestures(): GestureDetector =
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || sheetOpen) return false
                val dy = e2.y - e1.y
                if (!drawerOpen && dy < -dp(32) && velocityY < -600f) {
                    if (!grid.canScrollVertically(1)) {
                        openDrawer()
                        return true
                    }
                }
                if (drawerOpen && dy > dp(32) && velocityY > 800f) {
                    val list = drawerGrid
                    if (list == null || !list.canScrollVertically(-1)) {
                        closeDrawer()
                        return true
                    }
                }
                return false
            }
        })

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            grid.setPadding(dp(14), top + dp(10), dp(14), dp(10))
            (dockShell.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.bottomMargin = bottom + dp(4)
                dockShell.layoutParams = params
            }
            insets
        }
        root.requestApplyInsets()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ------------------------------------------------------------------ apps

    private fun loadApps() {
        loadThread = Thread({
            val loaded = try {
                AppLoader.load(this)
            } catch (t: Throwable) {
                emptyList<AppEntry>()
            }
            ui.post { onAppsLoaded(loaded) }
        }, "glass-apps")
        loadThread?.start()
    }

    private fun onAppsLoaded(apps: List<AppEntry>) {
        if (isFinishing || isDestroyed || apps.isEmpty()) return
        allApps = apps
        val dockApps = chooseDock(apps)
        bindDock(dockApps)

        val dockKeys = HashSet<String>(dockApps.size)
        for (app in dockApps) dockKeys.add(app.key)

        val home = ArrayList<AppEntry>(HOME_LIMIT)
        for (app in apps) {
            if (home.size >= HOME_LIMIT) break
            if (!dockKeys.contains(app.key)) home.add(app)
        }
        homeAdapter.setItems(home)
        drawerAdapter.setItems(apps)
    }

    private fun bindDock(apps: List<AppEntry>) {
        dock.removeAllViews()
        for (slot in 0 until DOCK_SLOTS) {
            val cell = GlassIconView(this, icons, prefs, showLabel = false)
            cell.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            if (slot < apps.size) {
                val app = apps[slot]
                cell.bind(app)
                cell.setOnClickListener { launch(app) }
            } else {
                cell.visibility = View.INVISIBLE
            }
            dock.addView(cell)
        }
    }

    private fun chooseDock(apps: List<AppEntry>): List<AppEntry> {
        val preferred = arrayOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.chrome",
            "com.android.camera",
            "com.google.android.GoogleCamera",
            "com.android.settings"
        )
        val chosen = ArrayList<AppEntry>(DOCK_SLOTS)
        for (pkg in preferred) {
            if (chosen.size >= DOCK_SLOTS) break
            for (app in apps) {
                if (app.pkg == pkg && chosen.none { it.key == app.key }) {
                    chosen.add(app)
                    break
                }
            }
        }
        for (app in apps) {
            if (chosen.size >= DOCK_SLOTS) break
            if (chosen.none { it.key == app.key }) chosen.add(app)
        }
        return chosen
    }

    private fun launch(app: AppEntry) {
        closeDrawer(instant = true)
        try {
            startActivity(app.intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- drawer

    private fun ensureDrawer(): FrameLayout {
        drawerLayer?.let { return it }

        val layer = FrameLayout(this).apply {
            setBackgroundColor(0xB306080B.toInt())
            isClickable = true
            visibility = View.GONE
            alpha = 0f
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), 0)
            background = LiquidGlassDrawable(density, prefs).apply {
                cornerRadiusDp = 30f
                shadowDp = 0f
                tintAlpha = 54
            }
        }

        val box = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = LiquidGlassDrawable(density, prefs).apply {
                pill = true
                shadowDp = 0f
                tintAlpha = 40
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
            })
        }

        val list = GridView(this).apply {
            numColumns = 5
            isVerticalScrollBarEnabled = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(2)
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(20))
            setSelector(ColorDrawable(Color.TRANSPARENT))
            adapter = drawerAdapter
        }

        val boxParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }
        panel.addView(box, boxParams)
        panel.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        panelParams.setMargins(dp(10), dp(14), dp(10), dp(10))
        layer.addView(panel, panelParams)
        root.addView(
            layer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        drawerLayer = layer
        drawerPanel = panel
        drawerGrid = list
        searchBox = box
        GlassEffects.attachPanel(panel, prefs)
        return layer
    }

    private fun openDrawer() {
        if (drawerOpen) return
        val layer = ensureDrawer()
        drawerOpen = true
        layer.visibility = View.VISIBLE
        layer.animate().alpha(1f).setDuration(150).start()
        drawerPanel?.let { panel ->
            panel.translationY = dp(26).toFloat()
            panel.animate().translationY(0f).setDuration(190).start()
        }
        drawerGrid?.setSelection(0)
    }

    private fun closeDrawer(instant: Boolean = false) {
        if (!drawerOpen) return
        drawerOpen = false
        searchBox?.let { box ->
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(box.windowToken, 0)
            box.setText("")
            box.clearFocus()
        }
        val layer = drawerLayer ?: return
        if (instant) {
            layer.visibility = View.GONE
            layer.alpha = 0f
            return
        }
        layer.animate()
            .alpha(0f)
            .setDuration(130)
            .withEndAction { layer.visibility = View.GONE }
            .start()
    }

    private fun applyFilter(query: String) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            drawerAdapter.setItems(allApps)
            return
        }
        val matches = ArrayList<AppEntry>(16)
        for (app in allApps) {
            if (app.label.lowercase().contains(needle) || app.pkg.lowercase().contains(needle)) {
                matches.add(app)
            }
        }
        drawerAdapter.setItems(matches)
    }

    // --------------------------------------------------------------- settings

    private fun openSheet() {
        if (sheetOpen) return
        sheetOpen = true

        val layer = FrameLayout(this).apply {
            setBackgroundColor(0x73000000)
            isClickable = true
            alpha = 0f
            setOnClickListener { closeSheet() }
        }

        val view = layoutInflater.inflate(R.layout.sheet_glass_settings, layer, false)
        view.background = LiquidGlassDrawable(density, prefs).apply {
            cornerRadiusDp = 30f
            shadowDp = 0f
            tintAlpha = 74
        }
        view.setOnClickListener { }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        params.setMargins(dp(8), 0, dp(8), dp(10))
        layer.addView(view, params)
        root.addView(
            layer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        sheetLayer = layer
        sheetView = view

        bindSlider(view, R.id.seek_blur, prefs.blurRadius, GlassPrefs.MAX_BLUR) { value ->
            prefs.blurRadius = value
            applyWindowBlur()
            dockShadow.setBlurDp(value.toFloat())
        }
        bindSlider(view, R.id.seek_tint, prefs.tintPercent, GlassPrefs.MAX_TINT) { value ->
            prefs.tintPercent = value
            refreshGlass()
        }
        bindSlider(view, R.id.seek_corner, prefs.cornerRadius, GlassPrefs.MAX_CORNER) { value ->
            prefs.cornerRadius = value
            refreshGlass()
        }

        GlassEffects.attachPanel(view, prefs)

        layer.animate().alpha(1f).setDuration(140).start()
        view.translationY = (view.height.takeIf { it > 0 } ?: dp(300)).toFloat()
        view.animate().translationY(0f).setDuration(210).start()
    }

    private fun closeSheet(instant: Boolean = false) {
        val layer = sheetLayer ?: return
        sheetOpen = false
        sheetLayer = null
        sheetView = null
        if (instant) {
            root.removeView(layer)
            return
        }
        layer.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction { root.removeView(layer) }
            .start()
    }

    private fun bindSlider(
        parent: View,
        id: Int,
        initial: Int,
        max: Int,
        onChange: (Int) -> Unit
    ) {
        val bar = parent.findViewById<SeekBar>(id) ?: return
        bar.max = max
        bar.progress = initial
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    /** Restyles every live glass surface without rebuilding a single view. */
    private fun refreshGlass() {
        for (i in 0 until grid.childCount) grid.getChildAt(i).invalidate()
        for (i in 0 until dock.childCount) dock.getChildAt(i).invalidate()
        dock.invalidate()
        drawerGrid?.let { list ->
            for (i in 0 until list.childCount) list.getChildAt(i).invalidate()
        }
        drawerPanel?.invalidate()
        sheetView?.invalidate()
    }

    // ------------------------------------------------------------- lifecycle

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeDrawer()
        closeSheet()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            sheetOpen -> closeSheet()
            drawerOpen -> closeDrawer()
            // Home: swallow back instead of leaving the launcher.
        }
    }

    override fun onStop() {
        super.onStop()
        closeDrawer(instant = true)
        closeSheet(instant = true)
        // Drop every pixel this app holds the moment we are not on screen.
        icons.trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        repaintIcons()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        icons.trim(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            repaintIcons()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        icons.clear()
        repaintIcons()
    }

    override fun onDestroy() {
        loadThread?.interrupt()
        loadThread = null
        icons.destroy()
        super.onDestroy()
    }

    private fun repaintIcons() {
        for (i in 0 until grid.childCount) grid.getChildAt(i).invalidate()
        for (i in 0 until dock.childCount) dock.getChildAt(i).invalidate()
        drawerGrid?.let { list ->
            for (i in 0 until list.childCount) list.getChildAt(i).invalidate()
        }
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private companion object {
        const val DOCK_SLOTS = 4
        const val HOME_LIMIT = 20
    }
}
