package com.yuyan.imemodule.ui.fragment.theme

import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.SkbStyleMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import com.yuyan.imemodule.utils.KeyboardLoaderUtil
import com.yuyan.imemodule.view.preference.ManagedPreference


private val skbStyleMode = ThemeManager.prefs.skbStyleMode

private val skbStyleModeListener = ManagedPreference.OnChangeListener<SkbStyleMode> { _, value ->
   when(value){
       // 三星风格的字母区上方本来就带一排数字键，切换过去时把开关一并打开，
       // 免得设置页显示「关」但键盘上却有数字行。
       // 微信风格不在这里动开关：它的全键盘固定带数字行、九键不带，
       // 是风格本身的一部分，不依赖这个全局开关。
       SkbStyleMode.Samsung ->{
           AppPrefs.getInstance().keyboardSetting.abcNumberLine.setValue(true)
       }
       else ->{
           AppPrefs.getInstance().keyboardSetting.abcNumberLine.setValue(false)
       }
   }
    EnvironmentSingleton.instance.initData()
    KeyboardLoaderUtil.instance.clearKeyboardMap()
    KeyboardManager.instance.clearKeyboard()
}

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs){

    override fun onStart() {
        super.onStart()
        skbStyleMode.registerOnChangeListener(skbStyleModeListener)
    }

    override fun onStop() {
        super.onStop()
        skbStyleMode.unregisterOnChangeListener(skbStyleModeListener)
    }
}