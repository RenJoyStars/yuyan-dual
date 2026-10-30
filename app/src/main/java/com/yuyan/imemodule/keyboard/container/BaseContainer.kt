package com.yuyan.imemodule.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.KeyboardLoaderUtil
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.view.preference.ManagedPreference
import splitties.dimensions.dp
import splitties.views.bottomPadding
import splitties.views.rightPadding
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * 软键盘View集装箱
 * 所有软键盘（输入、符号、设置等）父容器View。
 */
@SuppressLint("ViewConstructor")
open class BaseContainer(@JvmField var mContext: Context, inputView: InputView) : ConstraintLayout(mContext) {
    //输入法服务
    @JvmField
    protected var inputView: InputView
    private lateinit var mRightPaddingKey: ManagedPreference.PInt
    private lateinit var mBottomPaddingKey: ManagedPreference.PInt

    /**
     * 更新软键盘布局
     */
    open fun updateSkbLayout(){
    }

    init {
        this.inputView = inputView
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = EnvironmentSingleton.instance.skbWidth
        val measuredHeight = EnvironmentSingleton.instance.skbHeight
        val widthMeasure = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val heightMeasure = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasure, heightMeasure)
    }

    /**
     * 设定键盘高度后重建环境与键盘，返回键盘高度是否真的变了。
     *
     * 注意用 mContext（当前这块屏幕的 Context）的 metrics：副屏上拖高度时，
     * 原来调 initData()，取的是 Launcher（主屏）的尺寸 —— 主副屏分辨率不同时，
     * 键盘会按主屏重算，宽高和实际窗口对不上，键位被拉变形。
     */
    private fun refreshKeyboardMetrics(): Boolean {
        val before = EnvironmentSingleton.instance.skbHeight
        EnvironmentSingleton.instance.initDataWithDisplayMetrics(mContext.resources.displayMetrics)
        // 高度没变（拖动不足 1px / 已到上限）就别拆了重建，能少一次闪烁
        if (EnvironmentSingleton.instance.skbHeight == before) return false
        KeyboardLoaderUtil.instance.clearKeyboardMap()
        KeyboardManager.instance.clearKeyboard()
        updateSkbLayout()
        return true
    }

    /**
     * 设置键盘高度
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboardHeight() {
        val rootView = LayoutInflater.from(context).inflate(R.layout.layout_ime_keyboard_height_shadow, this, false)
        this.addView(rootView)
        rootView.findViewById<View>(R.id.ll_keyboard_height_reset).setOnClickListener { _: View? ->
            // 恢复到「当前这块屏幕 + 当前显示状态」的默认高度，
            // 而不是统一写 0.3（副屏全屏默认是 0.7，写 0.3 会突然矮一半）
            EnvironmentSingleton.instance.keyBoardHeightRatio =
                EnvironmentSingleton.defaultHeightRatio(EnvironmentSingleton.instance.currentDisplayState)
            refreshKeyboardMetrics()
        }
        rootView.findViewById<View>(R.id.ll_keyboard_height_sure).setOnClickListener { removeView(rootView) }
        rootView.findViewById<View>(R.id.iv_keyboard_height_Top).setOnTouchListener { v12: View, event -> onModifyKeyboardHeightEvent(v12, event) }
        
        // 根据当前显示状态选择对应的 padding 偏好
        val prefsInternal = AppPrefs.getInstance().internal
        when (EnvironmentSingleton.instance.currentDisplayState) {
            EnvironmentSingleton.DisplayState.PrimaryPortrait -> {
                mBottomPaddingKey = prefsInternal.bottomPaddingPrimaryPortrait
                mRightPaddingKey = prefsInternal.rightPaddingPrimaryPortrait
            }
            EnvironmentSingleton.DisplayState.SecondaryPortrait -> {
                mBottomPaddingKey = prefsInternal.bottomPaddingSecondaryPortrait
                mRightPaddingKey = prefsInternal.rightPaddingSecondaryPortrait
            }
            EnvironmentSingleton.DisplayState.SecondaryLandscape -> {
                mBottomPaddingKey = prefsInternal.bottomPaddingSecondaryLandscape
                mRightPaddingKey = prefsInternal.rightPaddingSecondaryLandscape
            }
            EnvironmentSingleton.DisplayState.SecondaryFullscreen -> {
                mBottomPaddingKey = prefsInternal.bottomPaddingSecondaryFullscreen
                mRightPaddingKey = prefsInternal.rightPaddingSecondaryFullscreen
            }
        }
        
        rootView.findViewById<View>(R.id.iv_keyboard_move).setOnTouchListener { _, event -> onMoveKeyboardEvent(event) }
    }

    private val lastY = floatArrayOf(0f)
    var isHandling = false
    private fun onModifyKeyboardHeightEvent(v12: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastY[0] = event.y
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                if (!isHandling && abs((y - lastY[0]).toDouble()) > dp(10)) {
                    isHandling = true
                    // 比例限制在 0.25–0.85：拖到 0 以下会让键盘高度变负（MeasureSpec 直接崩），
                    // 拖太高则会把候选栏顶出屏幕。夹住之后怎么拖都不会变形。
                    val next = (EnvironmentSingleton.instance.keyBoardHeightRatio +
                            if (y < lastY[0]) 0.01f else -0.01f).coerceIn(0.25f, 0.85f)
                    lastY[0] = y
                    EnvironmentSingleton.instance.keyBoardHeightRatio = next
                    // 不用再手动 setLayoutParams：容器的 onMeasure 按 skbHeight 量，
                    // 窗口是 wrap_content，会自己跟着变。（原来这里改的是 rootView，
                    // 也就是整个视图树的根，等于把 IME 窗口强行改成只有键盘那么高，
                    // 候选栏被挤掉、拖动时一闪一闪，就是这个原因。）
                    refreshKeyboardMetrics()
                    isHandling = false
                }
            }
            MotionEvent.ACTION_UP -> v12.performClick()
        }
        return true
    }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var rightPaddingValue = 0  // 右侧边距
    private var bottomPaddingValue = 0  // 底部边距
    private fun onMoveKeyboardEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                bottomPaddingValue = mBottomPaddingKey.getValue()
                rightPaddingValue = mRightPaddingKey.getValue()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx: Float = event.rawX - initialTouchX
                val dy: Float = event.rawY - initialTouchY
                if(dx.absoluteValue > 10) {
                    rightPaddingValue -= dx.toInt()
                    rightPaddingValue = if(rightPaddingValue < 0) 0
                    else if(rightPaddingValue > EnvironmentSingleton.instance.mScreenWidth - inputView.mSkbRoot.width) {
                        EnvironmentSingleton.instance.mScreenWidth - inputView.mSkbRoot.width
                    } else rightPaddingValue
                    initialTouchX = event.rawX
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        inputView.rightPadding = rightPaddingValue
                    } else {
                        inputView.mSkbRoot.rightPadding = rightPaddingValue
                    }
                }
                if(dy.absoluteValue > 10 ) {
                    bottomPaddingValue -= dy.toInt()
                    bottomPaddingValue = if(bottomPaddingValue < 0) 0
                    else if(bottomPaddingValue > EnvironmentSingleton.instance.mScreenHeight - inputView.mSkbRoot.height) {
                        EnvironmentSingleton.instance.mScreenHeight - inputView.mSkbRoot.height
                    } else bottomPaddingValue
                    initialTouchY = event.rawY
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        inputView.bottomPadding = bottomPaddingValue
                    } else {
                        inputView.mSkbRoot.bottomPadding = bottomPaddingValue
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mRightPaddingKey.setValue(rightPaddingValue)
                mBottomPaddingKey.setValue(bottomPaddingValue)
            }
        }
        return false
    }
}
