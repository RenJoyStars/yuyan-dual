package com.yuyan.imemodule.service

import android.app.Presentation
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.singleton.EnvironmentSingleton

class KeyboardPresentation(
    ctx: Context,
    display: Display,
    private val service: ImeService
) : Presentation(ContextThemeWrapper(ctx, R.style.Theme_AppTheme), display) {
    lateinit var inputView: InputView

    /**
     * 副屏的真实尺寸。
     *
     * `resources.displayMetrics` 给的是「应用可用区域」，可能已经扣掉系统栏；
     * 而本窗口是 MATCH_PARENT 铺满整块副屏的。两个高度差多少，键盘顶部就露多宽的边。
     * `currentWindowMetrics.bounds` 才是这块屏幕的真实边界，用它来算才能严丝合缝。
     */
    private fun realDisplayMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        dm.setTo(context.resources.displayMetrics)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm?.currentWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    dm.widthPixels = bounds.width()
                    dm.heightPixels = bounds.height()
                }
            } catch (_: Throwable) {
                // 拿不到就退回资源里的尺寸，不影响可用性
            }
        }
        return dm
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在副屏创建时，先用副屏的 DisplayMetrics 刷新一次环境，避免首次构建读主屏尺寸导致裁切。
        // 注意必须用 realDisplayMetrics()：resources.displayMetrics 是「应用可用区域」，
        // 往往比铺满整块副屏的窗口矮一条（系统栏），差多少键盘顶上就露多宽的黑边。
        EnvironmentSingleton.instance.initDataWithDisplayMetrics(realDisplayMetrics())
        // 避免副屏窗口抢占焦点导致输入连接中断，仅保留不获取焦点标记
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        // 去除后面内容暗化
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val forceFull = EnvironmentSingleton.instance.forceFullOnSecondary
        // 全屏时铺满整块副屏。底色用「键盘背景色」而不是纯黑：
        // 万一还差几像素，露出来的是同色，不会形成一条刺眼的黑边。
        val fullBg = try { ThemeManager.activeTheme.keyboardColor } catch (_: Throwable) { Color.BLACK }
        window?.setBackgroundDrawable(ColorDrawable(if (forceFull) fullBg else Color.TRANSPARENT))
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (forceFull) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.BOTTOM)

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (forceFull) ViewGroup.LayoutParams.MATCH_PARENT
                else ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(if (forceFull) fullBg else Color.TRANSPARENT)
        }
        inputView = InputView(context, service).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        root.addView(inputView)
        setContentView(root)
    }
}