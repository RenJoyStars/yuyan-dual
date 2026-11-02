package com.yuyan.imemodule.service

import android.app.Presentation
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.container.ClipBoardContainer
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.data.theme.Theme
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.service.DecodingInfo
import com.yuyan.imemodule.data.emojicon.YuyanEmojiCompat
import android.view.inputmethod.EditorInfo
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.data.theme.ThemeManager.removeOnChangedListener
import com.yuyan.imemodule.view.preference.ManagedPreference
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.imemodule.keyboard.container.CandidatesContainer
import android.view.inputmethod.InputConnection
import splitties.bitflags.hasFlag
import android.hardware.display.DisplayManager
import android.view.Display
import com.yuyan.imemodule.keyboard.container.T9TextContainer
import com.yuyan.imemodule.keyboard.container.HandwritingContainer
import kotlinx.coroutines.*
import com.yuyan.imemodule.data.theme.ThemeManager.OnThemeChangeListener
import com.yuyan.imemodule.prefs.AppPrefs.Companion.getInstance
import android.content.Context
import android.widget.Toast

class ImeService : InputMethodService() {
    private var isWindowShown = false // 键盘窗口是否已显示
    private lateinit var mInputView: InputView
    private var secondaryPresentation: KeyboardPresentation? = null
    private var secondaryInputView: InputView? = null
    private var placeholderView: View? = null
    private var displayManager: DisplayManager? = null
    private val logTag = "YuyanImeDual"

    private var lastDisplayState: EnvironmentSingleton.DisplayState? = null
    
    // 需要「键盘和输入框在同一块屏幕」的应用。
    // 双屏逻辑默认是「主屏横屏 → 键盘去副屏」。原先把 UU 远程 放进这个集合，
    // 是为了修「点了没键盘」，但实测那并不是屏选错了 —— 是这个输入法在
    // 横屏全屏应用里窗口根本没出来（切 Gboard 就正常）。
    // 现在按用户要求把远程桌面类应用放回「送副屏」的默认路径，
    // 主屏留给远程画面，键盘落到 Thor 下半屏。
    private val sameScreenPackages = emptySet<String>()

    // ── 临时诊断（排查完删除）────────────────────────────────────────
    // 目的：当输入法窗口「该出现却完全没出现」时，事后用 Toast 把流程写到哪一步抖出来。
    // Toast 是系统窗口，不受 IME 窗口可见性影响，所以键盘不出来它也照样能显示。
    // 只在「失败」时才弹，正常使用完全无感，也不用提前知道是哪个包名。
    private val diagHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var diagRunnable: Runnable? = null
    private val diagSteps = ArrayList<String>()

    private fun diag(msg: String) {
        Log.e(logTag, "DIAG $msg")
        diagSteps.add(msg)
        if (diagSteps.size > 6) diagSteps.removeAt(0)
    }

    private fun diagStart() {
        diagSteps.clear()
        diagRunnable?.let { diagHandler.removeCallbacks(it) }
        val r = Runnable {
            val visible = isWindowShown || secondaryPresentation?.isShowing == true
            if (!visible) {
                val text = "输入法没显示：" + diagSteps.joinToString("；")
                Log.e(logTag, "DIAG FAIL $text")
                try {
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                } catch (_: Throwable) {
                }
            }
        }
        diagRunnable = r
        diagHandler.postDelayed(r, 1500)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            // Log.d(logTag, "displayListener onDisplayAdded id=$displayId") // 调试标签已隐藏
        }
        override fun onDisplayRemoved(displayId: Int) {
            val secId = secondaryPresentation?.display?.displayId
            // Log.d(logTag, "displayListener onDisplayRemoved id=$displayId secId=$secId") // 调试标签已隐藏
            if (secId != null && secId == displayId) {
                // 副屏被移除，确保窗口关闭
                dismissSecondary()
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            val secId = secondaryPresentation?.display?.displayId
            // Log.d(logTag, "displayListener onDisplayChanged id=$displayId secId=$secId") // 调试标签已隐藏
            if (secId != null && secId == displayId) {
                // 旋转或尺寸变化：先按新尺寸重算环境，再让副屏键盘按新尺寸重建，
                // 否则键盘还是旧的宽高，键位会被拉伸（回车键最容易看出来）
                val dmSec = secondaryPresentation?.context?.resources?.displayMetrics
                if (dmSec != null) {
                    EnvironmentSingleton.instance.initDataWithDisplayMetrics(dmSec)
                    val view = secondaryInputView
                    if (view != null) {
                        view.post { view.relayoutKeyboard() }
                    }
                }
            }
        }
    }
    private val onThemeChangeListener = OnThemeChangeListener { _: Theme? -> if (getActiveInputView() != null) getActiveInputView()!!.updateTheme() }
    private val clipboardUpdateContent = getInstance().internal.clipboardUpdateContent
    private val clipboardUpdateContentListener = ManagedPreference.OnChangeListener<String> { _, value ->
        if(getInstance().clipboard.clipboardSuggestion.getValue()){
            if(value.isNotBlank()) {
                val activeView = getActiveInputView()
                if (activeView != null && activeView.isShown) {
                    if(KeyboardManager.instance.currentContainer is ClipBoardContainer
                        && (KeyboardManager.instance.currentContainer as ClipBoardContainer).getMenuMode() == SkbMenuMode.ClipBoard ){
                        (KeyboardManager.instance.currentContainer as ClipBoardContainer).showClipBoardView(SkbMenuMode.ClipBoard)
                    } else {
                        activeView.showSymbols(arrayOf(value))
                    }
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        placeholderView = View(this)
        return placeholderView!!
    }

    override fun setInputView(view: View) {
        // 包一层 FrameLayout，保证视图能被安全地换父容器
        val container = FrameLayout(this)
        container.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (view is InputView) {
            mInputView = view
            // 修复：避免重复添加已有父视图导致崩溃
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(view)
            super.setInputView(container)
        } else {
            super.setInputView(view)
        }
    }

    private fun getActiveInputView(): InputView? = secondaryInputView ?: if (::mInputView.isInitialized) mInputView else null

    /** 当前是否真的存在一块可用于 Presentation 的副屏（合盖/单屏时为 false） */
    private fun hasPresentationDisplay(): Boolean = try {
        val dm = displayManager ?: getSystemService(DisplayManager::class.java)
        dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.isNotEmpty() == true
    } catch (_: Throwable) {
        false
    }

    /** 当前输入法窗口所在的屏幕 id（拿不到就当主屏 0） */
    private fun currentWindowDisplayId(): Int = try {
        window?.window?.decorView?.display?.displayId ?: Display.DEFAULT_DISPLAY
    } catch (_: Throwable) {
        Display.DEFAULT_DISPLAY
    }

    @Synchronized
    private fun tryShowSecondary(editorInfo: EditorInfo, restarting: Boolean): Boolean {
        val dm = displayManager ?: getSystemService(DisplayManager::class.java)
        val displays = dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        // 有多块可选时，优先挑「不是当前输入法窗口所在」的那一块
        val selfDisplayId = currentWindowDisplayId()
        val second = displays?.firstOrNull { it.displayId != selfDisplayId }
            ?: displays?.firstOrNull()
        // Log.d(logTag, "tryShowSecondary: displays=${displays?.size ?: 0} second=${second}") // 调试标签已隐藏
        if (second == null) {
            diag("副屏：没有可用的 Presentation 屏幕")
            return false
        }
        // 屏没亮（合盖/单屏模式）就别把键盘往副屏送，否则等于没有键盘
        if (second.state == android.view.Display.STATE_OFF) {
            diag("副屏：屏幕处于熄屏状态 id=${second.displayId}")
            return false
        }

        // 若已经在同一副屏显示，则避免重复 show，仅刷新输入视图
        val alreadyShowingSame = secondaryPresentation?.isShowing == true &&
                (secondaryPresentation?.display?.displayId == second.displayId)
        if (alreadyShowingSame) {
            // Log.d(logTag, "tryShowSecondary: already showing on displayId=${second.displayId}, skip re-show") // 调试标签已隐藏
            try {
                secondaryInputView?.onStartInputView(editorInfo, restarting)
                return true
            } catch (t: Throwable) {
                Log.e(logTag, "tryShowSecondary: refresh existing failed, will recreate", t)
                // 失败则先关闭再重建
                dismissSecondary()
            }
        }

        val ctx = createDisplayContext(second)
        secondaryPresentation = KeyboardPresentation(ctx, second, this)
        try {
            secondaryPresentation!!.show()
            secondaryInputView = secondaryPresentation!!.inputView
            secondaryInputView!!.onStartInputView(editorInfo, restarting)
            // Log.d(logTag, "tryShowSecondary: shown on ${second}") // 调试标签已隐藏
            return true
        } catch (t: Throwable) {
            Log.e(logTag, "tryShowSecondary: failed", t)
            diag("副屏创建失败 ${t.javaClass.simpleName}: ${t.message}")
            secondaryPresentation = null
            secondaryInputView = null
            return false
        }
    }

    private fun dismissSecondary() {
        // Log.d(logTag, "dismissSecondary") // 调试标签已隐藏
        secondaryInputView = null
        try { secondaryPresentation?.dismiss() } catch (t: Throwable) { Log.e(logTag, "dismissSecondary error", t) }
        secondaryPresentation = null
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        // Log.d(logTag, "onStartInputView restarting=$restarting") // 调试标签已隐藏
        YuyanEmojiCompat.setEditorInfo(editorInfo)

        // 先获取主屏的显示尺寸与方向，作为副屏使用判断依据
        val currentWindowDisplay = try { window?.window?.decorView?.display } catch (_: Throwable) { null }
        val currentWindowMetrics = try {
            currentWindowDisplay?.let { createDisplayContext(it).resources.displayMetrics }
        } catch (_: Throwable) { null } ?: baseContext.resources.displayMetrics
        // 先关掉旧副屏窗口再决定要不要重建（下面会判断能否复用）
        val editorPkg = try { editorInfo.packageName } catch (_: Throwable) { null }
        // Log.d(logTag, "debug: editorPkg=$editorPkg rawMetrics=${currentWindowMetrics.widthPixels}x${currentWindowMetrics.heightPixels}") // 调试标签已隐藏

        val widthPx = currentWindowMetrics.widthPixels
        val heightPx = currentWindowMetrics.heightPixels
        val hasSecondaryDisplay = hasPresentationDisplay()

        // 状态推断补两条：
        // ① 「宽度 ≥1920」不再直接等于副屏全屏 —— 单屏横屏手机（1920×1080）也会命中，
        //    那样键盘会按全屏状态撑到 70% 高，把应用挤没。没有副屏一律按本屏处理。
        // ② 横屏判断加上「宽 > 高」，避免 1200×2000 这种竖屏平板被误判成副屏横屏。
        val inferredState = when {
            widthPx >= 1920 && hasSecondaryDisplay ->
                EnvironmentSingleton.DisplayState.SecondaryFullscreen
            widthPx >= 1081 && widthPx > heightPx ->
                EnvironmentSingleton.DisplayState.SecondaryLandscape
            heightPx > 1600 ->
                EnvironmentSingleton.DisplayState.PrimaryPortrait
            else ->
                EnvironmentSingleton.DisplayState.SecondaryPortrait
        }
        EnvironmentSingleton.instance.currentDisplayState = inferredState
        EnvironmentSingleton.instance.forceFullOnSecondary = inferredState == EnvironmentSingleton.DisplayState.SecondaryFullscreen

        val forceSameScreen = editorPkg != null && sameScreenPackages.contains(editorPkg)
        val shouldUsePresentation = !forceSameScreen &&
                inferredState == EnvironmentSingleton.DisplayState.SecondaryFullscreen
        Log.d(logTag, "pkg=$editorPkg state=$inferredState sameScreen=$forceSameScreen secondary=$hasSecondaryDisplay")
        diagStart()
        diag("1/3 收到输入 ${widthPx}x$heightPx 副屏=$hasSecondaryDisplay 状态=$inferredState 送副屏=$shouldUsePresentation")

        // 已经在同一块副屏上显示、且显示状态没变 → 直接复用，不要再 dismiss + 重建。
        // 原来每次都先关再开，切个输入框就闪一下，而且重建期间候选词、布局状态会丢。
        val stateChanged = lastDisplayState != null && lastDisplayState != inferredState
        val reuseExisting = shouldUsePresentation && !stateChanged &&
                secondaryPresentation?.isShowing == true
        if (!reuseExisting) dismissSecondary()

        val shownOnSecondary = if (shouldUsePresentation) {
            tryShowSecondary(editorInfo, restarting)
        } else {
            false
        }
        diag("2/3 副屏窗口=${if (shownOnSecondary) "已显示" else "未显示"}")

        // 该送副屏但没送成（副屏被占用 / 熄屏 / 创建失败）：状态退回本屏，
        // 否则会按「全屏 70%」的比例把键盘铺在主屏上，一开键盘就遮掉大半个屏幕。
        if (shouldUsePresentation && !shownOnSecondary) {
            EnvironmentSingleton.instance.currentDisplayState =
                if (widthPx > heightPx) EnvironmentSingleton.DisplayState.SecondaryLandscape
                else EnvironmentSingleton.DisplayState.PrimaryPortrait
            EnvironmentSingleton.instance.forceFullOnSecondary = false
        }
        // Log.d(logTag, "shownOnSecondary=$shownOnSecondary") // 调试标签已隐藏

        // 设置输入视图（正常 IME 窗口）
        if (!shownOnSecondary) {
            if (!::mInputView.isInitialized) {
                mInputView = InputView(baseContext, this)
            }
            setInputView(mInputView)
            // Log.d(logTag, "setInputView normal with ${mInputView.javaClass.simpleName}") // 调试标签已隐藏
            mInputView.onStartInputView(editorInfo, restarting)
            diag("3/3 已装载主屏键盘 键高=${EnvironmentSingleton.instance.skbHeight}")
        } else {
            diag("3/3 已交给副屏")
        }

        // 根据当前实际显示设备刷新环境尺寸与显示状态
        val targetMetrics = if (shownOnSecondary) {
            val dmSec2 = secondaryPresentation?.context?.resources?.displayMetrics
            // Log.d(logTag, "init with secondary metrics=${dmSec2?.widthPixels}x${dmSec2?.heightPixels}") // 调试标签已隐藏
            dmSec2 ?: currentWindowMetrics
        } else {
            currentWindowMetrics
        }
        EnvironmentSingleton.instance.initDataWithDisplayMetrics(targetMetrics)

        // 如果显示状态发生变化，执行一次“切到另一个键盘再切回”的快速重排
        applyRelayoutIfDisplayChanged()

        // 最后刷新调试标签，确保当前场景被正确显示
    }

    private fun applyRelayoutIfDisplayChanged() {
        val current = EnvironmentSingleton.instance.currentDisplayState
        if (lastDisplayState != current) {
            // Log.d(logTag, "display state changed: ${lastDisplayState} -> ${current}, force relayout by toggle") // 调试标签已隐藏
            try {
                KeyboardManager.instance.forceRelayoutByToggle()
            } catch (t: Throwable) {
                Log.e(logTag, "forceRelayoutByToggle failed", t)
            }
            lastDisplayState = current
        } else {
            // 同一状态下不重排，避免不必要闪烁
            // Log.d(logTag, "display state unchanged: ${current}") // 调试标签已隐藏
        }
    }

    override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        val pkg = try { editorInfo.packageName } catch (_: Throwable) { null }
        // Log.d(logTag, "onStartInput: pkg=$pkg restarting=$restarting") // 调试标签已隐藏
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return super.onShowInputRequested(flags, configChange)
    }

    /**
     * 永远不进「全屏编辑（extract）模式」。
     *
     * 横屏应用（远程桌面、游戏）默认会触发 InputMethodService 的全屏模式：
     * 框架会把我们的输入视图塞进它自带的 extract 布局里，外面套一个全屏编辑框，
     * 尺寸和 insets 全按那套布局算 —— 结果是键盘窗口要么被撑到整屏，
     * 要么因为 insets 算错而整块不可见（Gboard 自己处理了这种情况，所以它正常）。
     * 我们本来就有候选栏和自绘键盘，不需要系统那层全屏编辑界面，直接关掉。
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onBindInput() {
        super.onBindInput()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        isWindowShown = true
        diag("4/4 输入法窗口已显示")
        getActiveInputView()?.onWindowShown()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        getActiveInputView()?.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesEnd)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        isWindowShown = false
        getActiveInputView()?.onWindowHidden()
        dismissSecondary()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        getActiveInputView()?.resetToIdleState()
        dismissSecondary()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        diagRunnable?.let { diagHandler.removeCallbacks(it) }   // 会话结束就别再弹诊断了
        getActiveInputView()?.resetToIdleState()
        dismissSecondary()
    }

    override fun onUnbindInput() {
        super.onUnbindInput()
        getActiveInputView()?.resetToIdleState()
        dismissSecondary()
    }

    fun sendDeleteKeyEvent()
    {
        val eventTime = SystemClock.uptimeMillis()
        sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_DEL)
        sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_DEL)
    }

    private fun sendKeyUpEvent(keyEventCode: Int, eventTime: Long){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    fun sendEnterKeyEvent(){
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        if (info == null || ic == null) {
            val t = SystemClock.uptimeMillis()
            sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            return
        }
        if ((info.inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_NULL
            || info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            val t = SystemClock.uptimeMillis()
            sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
        } else if (!info.actionLabel.isNullOrEmpty() && info.actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(info.actionId)
        } else {
            when (val action = info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> {
                    val t = SystemClock.uptimeMillis()
                    sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
                    sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
                }
                else -> ic.performEditorAction(action)
            }
        }
    }

    private fun sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    private fun sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    fun sendCombinationKeyEvents(keyCode: Int, shift: Boolean = false) {
        val eventTime = SystemClock.uptimeMillis()
        if (shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDownKeyEvent(eventTime, keyCode)
        sendUpKeyEvent(eventTime, keyCode)
        if (shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    fun commitText(text: String) {
        val ic: InputConnection? = currentInputConnection
        ic?.commitText(text, 1)
    }

    fun setComposingText(text: CharSequence) {
        currentInputConnection?.setComposingText(text, 1)
    }

    fun finishComposingText() {
        currentInputConnection?.finishComposingText()
    }

    fun forceHideKeyboard() {
        // 收起系统输入法并清理副屏
        try { requestHideSelf(0) } catch (_: Throwable) {}
        isWindowShown = false
        dismissSecondary()
    }

    fun getTextBeforeCursor(length:Int) : String{
        val ic: InputConnection? = currentInputConnection
        return StringUtils.fromCSDString(ic?.getTextBeforeCursor(length, 0)?.toString())
    }

    fun commitTextEditMenu(id:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.performContextMenuAction(id)
    }

    fun performEditorAction(editorAction:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.performEditorAction(editorAction)
    }

    fun deleteSurroundingText(length:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.deleteSurroundingText(length, 0)
    }

    fun setSelection(start: Int, end: Int){
        val ic: InputConnection? = currentInputConnection
        ic?.setSelection(start, end)
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        val activeView = getActiveInputView() ?: return
        // 副屏场景：主屏不受输入法影响，不调整内容区域（避免主屏被全屏输入框覆盖）
        if (secondaryInputView != null) {
            outInsets.apply {
                contentTopInsets = EnvironmentSingleton.instance.mScreenHeight
                visibleTopInsets = EnvironmentSingleton.instance.mScreenHeight
                touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
                touchableRegion.setEmpty()
            }
            // Log.d(logTag, "onComputeInsets: secondary active, primary unaffected") // 调试标签已隐藏
            return
        }
        val (x, y) = intArrayOf(0, 0).also { if(activeView.isAddPhrases) activeView.mAddPhrasesLayout.getLocationInWindow(it) else activeView.mSkbRoot.getLocationInWindow(it) }
        outInsets.apply {
            if(EnvironmentSingleton.instance.keyboardModeFloat) {
                contentTopInsets = EnvironmentSingleton.instance.mScreenHeight
                visibleTopInsets = EnvironmentSingleton.instance.mScreenHeight
                touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
                touchableRegion.set(x, y, x + activeView.mSkbRoot.width, y + activeView.mSkbRoot.height)
            } else {
                contentTopInsets = y
                touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
                touchableRegion.setEmpty()
                visibleTopInsets = y
            }
        }
    }
    
    /**
     * 初始化语音识别
     */
    companion object {
        @Volatile
        private var currentInstance: ImeService? = null
        
        fun getCurrentInstance(): ImeService? = currentInstance
    }
    
    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, null)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        clipboardUpdateContent.registerOnChangeListener(clipboardUpdateContentListener)
        
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentInstance = null
        
        // 取消显示监听器
        displayManager?.unregisterDisplayListener(displayListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        clipboardUpdateContent.unregisterOnChangeListener(clipboardUpdateContentListener)
    }
}