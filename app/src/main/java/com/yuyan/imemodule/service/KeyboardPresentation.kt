package com.yuyan.imemodule.service

import android.app.Presentation
import android.content.Context
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
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.singleton.EnvironmentSingleton

class KeyboardPresentation(
    ctx: Context,
    display: Display,
    private val service: ImeService
) : Presentation(ContextThemeWrapper(ctx, R.style.Theme_AppTheme), display) {
    lateinit var inputView: InputView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在副屏创建时，先用副屏的 DisplayMetrics 刷新一次环境，避免首次构建读主屏尺寸导致裁切
        EnvironmentSingleton.instance.initDataWithDisplayMetrics(context.resources.displayMetrics)
        // 避免副屏窗口抢占焦点导致输入连接中断，仅保留不获取焦点标记
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        // 去除后面内容暗化
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val forceFull = EnvironmentSingleton.instance.forceFullOnSecondary
        // 全屏：整块副屏铺黑，键盘仍贴底；非全屏：透明窗口，只占键盘那么高
        window?.setBackgroundDrawable(ColorDrawable(if (forceFull) Color.BLACK else Color.TRANSPARENT))
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
            setBackgroundColor(if (forceFull) Color.BLACK else Color.TRANSPARENT)
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