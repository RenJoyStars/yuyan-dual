package com.yuyan.imemodule.singleton

import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.data.theme.ThemeManager.prefs
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.DevicesUtils
import kotlin.math.max
import kotlin.math.min
import android.util.DisplayMetrics

class EnvironmentSingleton private constructor() {
    var systemNavbarWindowsBottom = 0 // 导航栏高度
    var mScreenWidth = 0 // 屏幕的宽度
    var mScreenHeight = 0 // 屏幕的高度
    var inputAreaHeight = 0 // 键盘区域高度
    var inputAreaWidth = 0 // 键盘区域宽度
    var skbWidth = 0 // 键盘按键区域、候选词区域宽度
        private set
    var skbHeight = 0 // 键盘按键区高度
        private set
    var holderWidth = 0 // 单手模式下键盘占位区域宽度
        private set
    var heightForCandidatesArea = 0 // 候选词区域的高度
    var heightForcomposing = 0 // 候选词拼音区域的高度
    var heightForCandidates = 0 // 候选词区域的高度
    var heightForFullDisplayBar = 0 // 智能导航栏高度
    var heightForKeyboardMove = 0 // 悬浮键盘移动条高度
    var keyTextSize = 0 // 正常按键中文本的大小
    var keyTextSmallSize = 0 // 正常按键中文本的大小,小值
    var candidateTextSize = 0f // 候选词字体大小
    var composingTextSize = 0f // 候选词字体大小
    var isLandscape = false //键盘是否横屏
    var keyXMargin = 0 //键盘按键水平间距
    var keyYMargin = 0 //键盘按键垂直间距
    private var keyboardHeightRatio = 0f
    var forceFullOnSecondary = false
    private var lastDisplayMetrics: DisplayMetrics? = null
    // 四种显示状态枚举与当前状态
    enum class DisplayState { PrimaryPortrait, SecondaryPortrait, SecondaryLandscape, SecondaryFullscreen }
    var currentDisplayState: DisplayState = DisplayState.PrimaryPortrait

    init {
        initData()
    }

    fun initData() {
        val resources = Launcher.instance.context.resources
        val dm = lastDisplayMetrics ?: resources.displayMetrics
        reinitWithDisplayMetrics(dm)
    }

    fun initDataWithDisplayMetrics(dm: DisplayMetrics) {
        lastDisplayMetrics = dm
        reinitWithDisplayMetrics(dm)
    }

    fun clearDisplayMetricsOverride() {
        lastDisplayMetrics = null
    }

    private fun reinitWithDisplayMetrics(dm: DisplayMetrics) {
        // 副屏刚创建/切换时 metrics 偶尔是 0，按 0 算会得到 0 宽高的键盘（一屏空白）。
        // 直接沿用上一次的尺寸，等下一次有效回调再更新。
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) return

        mScreenWidth = dm.widthPixels
        mScreenHeight = dm.heightPixels
        isLandscape = mScreenHeight <= mScreenWidth

        // 键盘宽度：竖屏取窄边；横屏（含副屏横屏/全屏）取实际宽度
        if (isLandscape) {
            inputAreaWidth = mScreenWidth
            skbWidth = inputAreaWidth
        } else {
            inputAreaWidth = min(dm.widthPixels, dm.heightPixels)
            skbWidth = inputAreaWidth
        }
        holderWidth = 0

        // 根据显示状态选择独立的高度比例
        val prefsInternal = AppPrefs.getInstance().internal
        val configured = when (currentDisplayState) {
            DisplayState.PrimaryPortrait -> prefsInternal.heightRatioPrimaryPortrait.getValue()
            DisplayState.SecondaryPortrait -> prefsInternal.heightRatioSecondaryPortrait.getValue()
            DisplayState.SecondaryLandscape -> prefsInternal.heightRatioSecondaryLandscape.getValue()
            DisplayState.SecondaryFullscreen -> prefsInternal.heightRatioSecondaryFullscreen.getValue()
        }
        // 历史数据里可能存了 0 / 负数（旧版「重置」会写 0.3，拖到边界还可能越界），回落到默认值。
        // 副屏（下屏）允许到 1.0 —— 就是「铺满整块下屏」；主屏仍限制在 0.9 以内，别把应用遮没。
        val isSecondaryDisplay = currentDisplayState != DisplayState.PrimaryPortrait
        // 旧版副屏全屏的默认值是 0.7，等于「没调过」。直接沿用会让老用户永远差一截，
        // 所以把恰好等于旧默认值的当成未设置，迁移到新默认（1.0 铺满）。
        val migrated = if (isSecondaryDisplay && configured == 0.7f) 1.0f else configured
        val validRange = if (isSecondaryDisplay) 0.4f..1.0f else 0.2f..0.9f
        val ratio = if (migrated in validRange) migrated else defaultHeightRatio(currentDisplayState)
        keyboardHeightRatio = ratio

        // 高度基数 = 屏幕实际高度。
        // 旧版用 max(width,height)：竖屏时它就是高度（一致），但横屏 1920×1080 会按 1920 算，
        // 0.5 的比例直接得到 960px（占屏幕 89%），0.7 更是 1344px —— 比屏幕还高、顶部被裁。
        // 比例就是「占屏幕高度的几分之几」，这样横竖屏语义才一致，拖动也不会出现
        // 「拉到某个高度之后怎么拖都不动」的死区。
        val minSkb = (96 * dm.density).toInt()

        // 候选栏高度约占键盘高度的 29%（原公式 0.1×2.9，随候选字号缩放）
        val candidateScale = AppPrefs.getInstance().keyboardSetting.candidateTextSize.getValue() / 100f
        val candidateFactor = 0.1f * 2.9f * candidateScale

        if (isSecondaryDisplay) {
            // 副屏：ratio 表示「整个输入法窗口占下屏高度的几分之几」。
            // 候选栏从这块高度里**扣**，而不是加在键盘上方 ——
            // 旧实现是 skb = 屏高×ratio、候选栏再额外加 29%，两者相加必然超过屏幕，
            // 于是只好把 skb 压到 74% 左右，怎么拖都铺不满。
            // 现在 ratio = 1.0 时，键盘 + 候选栏正好等于整块下屏。
            val total = (mScreenHeight * ratio).toInt().coerceAtLeast(minSkb)
            val candArea = (total * candidateFactor / (1f + candidateFactor)).toInt()
            skbHeight = (total - candArea).coerceAtLeast(minSkb)
            heightForCandidatesArea = (total - skbHeight).coerceAtLeast(0)
            heightForcomposing = (heightForCandidatesArea / 2.9f).toInt()
            heightForCandidates = (heightForcomposing * 1.9).toInt()
        } else {
            // 主屏：键盘只占屏幕下部一块，候选栏叠在应用区域上方，沿用原公式
            var skb = (mScreenHeight * ratio).toInt()
            if (skb < minSkb) skb = minSkb
            // 兜底：即使比例配得很大，也不让键盘顶穿屏幕
            val maxSkb = (mScreenHeight * 0.96f / (1f + candidateFactor)).toInt()
            if (skb > maxSkb) skb = maxSkb.coerceAtLeast(minSkb)
            skbHeight = skb
            heightForcomposing = (skbHeight * 0.1f * candidateScale).toInt()
            heightForCandidates = (heightForcomposing * 1.9).toInt()
            heightForCandidatesArea = (heightForcomposing * 2.9).toInt()
        }
        composingTextSize = DevicesUtils.px2sp (heightForcomposing)
        candidateTextSize = DevicesUtils.px2sp (heightForCandidates)
        heightForFullDisplayBar = 0
        heightForKeyboardMove = 0

        val keyboardFontSizeRatio = prefs.keyboardFontSize.getValue()/100f
        keyTextSize = (skbHeight * 0.06f * keyboardFontSizeRatio).toInt()
        keyTextSmallSize = (skbHeight * 0.04f * keyboardFontSizeRatio).toInt()
        keyXMargin = (prefs.keyXMargin.getValue() / 1000f * skbWidth).toInt()
        keyYMargin = (prefs.keyYMargin.getValue() / 1000f * skbHeight).toInt()
        inputAreaHeight = skbHeight + heightForCandidatesArea
    }

    var keyBoardHeightRatio: Float
        get() = keyboardHeightRatio
        set(keyBoardHeightRatio) {
            keyboardHeightRatio = keyBoardHeightRatio
            val internal = AppPrefs.getInstance().internal
            when (currentDisplayState) {
                DisplayState.PrimaryPortrait -> internal.heightRatioPrimaryPortrait.setValue(keyBoardHeightRatio)
                DisplayState.SecondaryPortrait -> internal.heightRatioSecondaryPortrait.setValue(keyBoardHeightRatio)
                DisplayState.SecondaryLandscape -> internal.heightRatioSecondaryLandscape.setValue(keyBoardHeightRatio)
                DisplayState.SecondaryFullscreen -> internal.heightRatioSecondaryFullscreen.setValue(keyBoardHeightRatio)
            }
        }

    var keyboardModeFloat:Boolean = false
        set(isFloatMode) {
            field = isFloatMode
        }

    val skbAreaHeight:Int
        get() = instance.inputAreaHeight + AppPrefs.getInstance().internal.keyboardBottomPadding.getValue() + instance.systemNavbarWindowsBottom

    val leftMarginWidth:Int
        get() = (instance.inputAreaWidth - instance.skbWidth)/2

    companion object {
        /**
         * 各显示状态的默认键盘高度占比。
         * 键盘里「重置高度」用它恢复，避免旧代码那样一律写 0.3（副屏全屏只有 30% 太矮）。
         */
        fun defaultHeightRatio(state: DisplayState): Float = when (state) {
            DisplayState.PrimaryPortrait -> 0.45f
            DisplayState.SecondaryPortrait -> 0.7f
            DisplayState.SecondaryLandscape -> 0.8f
            // 副屏全屏：整块下屏都给键盘，默认就铺满
            DisplayState.SecondaryFullscreen -> 1.0f
        }

        private var mInstance: EnvironmentSingleton? = null
        @JvmStatic
        val instance: EnvironmentSingleton
            get() {
                if (null == mInstance) {
                    mInstance = EnvironmentSingleton()
                }
                return mInstance!!
            }
    }
}
