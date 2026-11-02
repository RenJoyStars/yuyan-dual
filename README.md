# 语燕输入法双屏版（RenJoy 维护版）

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/RenJoyStars/yuyan-dual)](https://github.com/RenJoyStars/yuyan-dual/releases)
![Platform](https://img.shields.io/badge/Android-8.0%2B%20%C2%B7%20arm64-green)
![Size](https://img.shields.io/badge/APK-47MB-lightgrey)

本项目是 [语燕双屏版](https://github.com/Maditer/YuyanDual) 的衍生维护版本，面向 **AYN Thor 这类双屏安卓掌机**。
在原版基础上做了这些改动：

- **移除离线语音输入**（sherpa-ncnn）：连录音权限、权限请求页、语音设置页一并去掉，安装包从 135MB 降到 47MB
- **修复 UU远程里点输入框不弹键盘**：远程桌面类应用强制「键盘与输入框同屏」
- **双屏界面整理**：发送键改成胶囊形（不再被拉成椭圆）、键盘高度按屏幕实际高度计算、
  主副屏输入视图重新绑定（副屏用完回主屏不再失灵）、切输入框不再闪、副屏旋转后自动重排
- **补回 Rime 词库与预编译方案**：原仓库 `.gitignore` 把 `*.yaml/*.bin/*.txt` 全忽略了，
  直接 clone 编出来的包**一个字也打不出来**，这里已补全

## 下载安装

到 [Releases](https://github.com/RenJoyStars/yuyan-dual/releases/latest) 下载最新版
（**v0.3.1**，arm64-v8a，约 47MB）。

1. 安装 APK
2. 「设置 → 系统 → 语言和输入法」中启用「语燕输入法」并设为默认
3. 首次打开应用会复制词库（约 65MB，等十几秒）

> ⚠️ 包名是 `com.yuyan.pinyin.online.release`，与官方版**不同，不能覆盖安装**。
> 已装官方版请先卸载（卸载会清掉个人习惯记录，词库在安装包里不受影响）。

## 界面一览

| 九键拼音 | 全键拼音 |
|---|---|
| ![九键](images/t9_pinyin.jpg) | ![全键](images/qwerty_pinyin.jpg) |

| 双拼 | 手写 |
|---|---|
| ![双拼](images/double_pinyin.jpg) | ![手写](images/writing_pinyin.jpg) |

| 表情 | 悬浮键盘 |
|---|---|
| ![表情](images/emoji.jpg) | ![悬浮](images/float.jpg) |

| 单手模式 | 深色主题 |
|---|---|
| ![单手](images/onehand.jpg) | ![深色](images/dark.jpg) |

<sub>截图为上游版本的界面，本版除「发送键改为胶囊形」「移除语音输入」外基本一致。</sub>

## 协议与来源

- 本仓库遵循 **GPL-3.0**（见 [LICENSE](LICENSE)），与上游一致；分发或修改同样需以 GPL-3.0 开源
- 上游链路：[Maditer/YuyanDual](https://github.com/Maditer/YuyanDual) → [gurecn/YuyanIme](https://github.com/gurecn/YuyanIme) → [Rime / librime](https://github.com/rime/librime)
- `app/src/main/assets/rime/` 下的词库与预编译方案取自原作者发布的安装包，随本项目以 GPL-3.0 一并分发
- 输入法内核（`libyuyanime.so` 等）与手写模型版权归原作者，本仓库仅做界面与行为层面的修改

### 原作者说明（保留）

1. [语燕双屏版](https://github.com/Maditer/YuyanDual)以[语燕输入法](https://github.com/gurecn/YuyanIme)为基础，增加了对双屏安卓设备（目前只有 AYN Thor）的支持，并对一些功能做了个性化调整
2. 如果使用当中遇到问题，欢迎提交反馈，但我不保证能解决所有问题，因为我一行代码也不会写，全是 AI 写的 ^_^
3. 对项目感兴趣的大佬欢迎自取源码并进行优化

### 项目参考与鸣谢
1. [语燕输入法](https://github.com/gurecn/YuyanIme)
2. [Rime引擎](https://github.com/rime/librime)
3. [sherpa-ncnn](https://github.com/k2-fsa/sherpa-ncnn)

## 构建安装包

```sh
./gradlew :app:assembleDualscreenRelease
```

- **包名**：`com.yuyan.pinyin.online.release`（**不能改成 `com.yuyan.pinyin.online`**）
- **签名**：`keystore/keystore.properties` + `keystore/yuyan-release.jks`（都在 `.gitignore` 里，丢了就发不出升级包，务必单独备份）

> ⚠️ **包名白名单（踩过的坑）**
> `libyuyanime.so` 里硬编码了可用包名：`com.yuyan.pinyin.online.release` /
> `com.yuyan.pinyin.online.debug` / `com.yuyan.pinyin.offline.release` / `com.yuyan.pinyin.offline.debug`。
> 改成白名单以外的包名（例如为了覆盖官方版而用 `com.yuyan.pinyin.online`），Rime 引擎会在
> `startupRime()` 里**静默拒绝初始化**：键盘能弹出、按键有动画，但中英文都上不了屏、候选栏永远空，
> logcat 里连 `rime.yuyan` 日志都没有。这是闭源 SDK 的授权校验，不是 bug。

### 词库

`app/src/main/assets/rime/build/` 是预编译的 Rime 方案与词库（`pinyin.table.bin` 65MB 为词条表、
`t9_pinyin.prism.bin` 为九键音节索引）。这套文件上游仓库里没有（被 `.gitignore` 漏掉），
缺失时编出来的包完全打不出中文。换成熟词库需要用 librime 重新编译 table + 重建九键 prism。

---

## 更新日志

## 0.3（私有维护版）
### 移除
1. 删除离线语音输入（sherpa-ncnn）：按住空格说话、语音设置页、语音相关权限一并去掉
   - 同时移除 `RECORD_AUDIO` / `MODIFY_AUDIO_SETTINGS` 权限与权限请求 Activity
   - 仓库里只留了 `.param`，语音模型 `.bin` 本来就不在版本库内

### 修复
1. **UU远程（`com.netease.uuremote`）里点输入框不弹键盘**：该应用强制「键盘与输入框同屏」，
   不再按「主屏横屏 → 键盘去副屏」的规则送走
2. 副屏处于熄屏状态（`Display.STATE_OFF`）时不再把键盘送过去，回落到主屏显示
3. **发送/回车键被拉成椭圆**：旧代码用 `GradientDrawable.OVAL`，椭圆的宽高实际是
   「横向边距+半径 × 纵向边距+半径」，切换布局（回车占两行）或拖高度改变单元格比例后
   就会从圆变成明显的扁椭圆（键盘调矮时最明显）。改成圆角恒取短边一半的胶囊形。
4. **调键盘高度时闪烁、候选栏被挤掉**：拖动时原来会 `rootView.setLayoutParams(...)`，
   改的是整棵视图树的根（等于把 IME 窗口强行压成只有键盘那么高）。去掉该调用，交给
   `onMeasure` 自然跟随；同时高度没变时不再重建键盘，少一次闪烁。
5. **副屏调整高度按主屏尺寸重算**：拖动/重置调的是 `initData()`（Launcher 的主屏 metrics），
   主副屏分辨率不同时键盘宽高与实际窗口对不上、键位被拉变形。改用当前屏幕 Context 的 metrics。
6. **横屏时键盘高度算成 `max(宽,高) × 比例`**：1920×1080 的副屏按 1920 算，0.5 的比例就是
   960px（占屏幕 89%），0.7 直接 1344px —— 比屏幕还高、顶部被裁。改为按屏幕实际高度算
   （比例＝占屏幕高度的几分之几），并把「键盘+候选栏」限制在屏幕 96% 内。

### 双屏体验优化
1. **单屏横屏不再当成副屏全屏**：`宽度≥1920` 原来会直接判定为副屏全屏（键盘 70% 高），
   单屏横屏手机/平板会因此被键盘遮掉大半屏。现在只有真的存在可用的副屏才走这条路。
2. **副屏起不来时回退本屏比例**：该送副屏但创建失败（被占用/熄屏）时，状态退回本屏，
   避免键盘按全屏比例铺在主屏上。
3. **主副屏输入视图的绑定修正**：`KeyboardManager` 和按键气泡层都是单例，原来谁最后被构造
   就挂在谁身上 —— 副屏用完回到主屏后，切布局/长按气泡会作用到已经消失的那块屏。现在每次
   开始输入都会重新绑定到当前屏幕的视图。
4. **切输入框不再闪**：已经显示在同一块副屏且显示状态没变时复用原窗口，不再 dismiss 重建。
5. **副屏旋转/改分辨率后自动重排**：`onDisplayChanged` 里按新尺寸重算环境并重建键盘。
6. **拖动键盘高度留白越界修正**：悬浮模式往上拖会把键盘整块推出屏幕，加了上限。
7. **「重置高度」按当前状态恢复**：原来统一写 0.3（副屏全屏默认 0.7，重置后突然矮一半），
   现在按 PrimaryPortrait/SecondaryPortrait/SecondaryLandscape/SecondaryFullscreen 各自的默认值恢复。
8. **高度比例做了取值保护**：历史偏好里 0/负数会回落到默认值；拖动范围限制在 0.25–0.85，
   避免负高度（MeasureSpec 会崩）和候选栏被顶出屏幕。
9. 删掉遗留的调试覆盖层代码（`buildDebugOverlay` / `refreshDebugLabel` 等，本来全是注释），
   `KeyboardPresentation` 的重复分支合并成一套。

## 2025-11-07
### 新增：
支持语音输入，按住空格说话即可输入
  - 基于[sherpa-ncnn](https://github.com/k2-fsa/sherpa-ncnn)语音识别引擎
  - 纯离线，不消耗流量；边说话边识别；支持中英双语
### 优化：
1. 优化：重写了键盘类型判断逻辑，更稳定。现在在两块屏幕间快速切换键盘，不容易出现键盘显示错位的问题
2. 优化：emoji 键盘底部的一行控制图标放大，更容易点击
3. 优化：菜单栏的图标大小优化，更容易点击，尤其是副屏竖屏状态下
### 修复：
1. 修复：在任务管理器“全部清除”导致默认输入法失效的问题
2. 修复：imeOptions 失效问题。现在在夸克里打完字，点右下角的按钮可以直接搜索，而不是换行了
3. 修复：副屏键盘出现时，原应用输入框不抬起的问题。现在输入法会正常给应用传递键盘高度，但实测部分应用无效，可能是因为副屏尺寸太小，应用不兼容
### 已知问题：
1. 部分应用在横屏状态下，需要点击两次输入框才出现键盘
2. 调节键盘高度时，拖拽不是很顺滑，有点闪烁
3. 长按删除键，出现“上滑清空”的按钮，在不同键盘高度下位置和大小有一点怪
4. 某些极端情况下还是有键盘错位的情况，需要收起再展开

## 2025-10-31
### 新增与优化：
1. 增加了对双屏设备的支持：当主屏处在横屏状态时，调用的输入法会在副屏出现，方便使用
2. 区分了“主屏横屏、主屏竖屏、副屏横屏、副屏竖屏”四种键盘显示状态，可以单独调整高度，互不影响
3. [设置-键盘-主题]页面适配了横屏
### 删减功能：
为了减少兼容性问题，提高可维护性，删除了部分（个人）不常用的功能
1. 悬浮键盘
2. 单手模式
3. 花漾字
4. 全面屏优化设置
### 已知问题：
1. ~~在任务管理器“全部清除”会导致默认输入法的状态被清空，需要重新选择。这个问题原版也存在，可以在任务管理器中下拉输入法窗口，锁定应用即可~~
2. ~~当一块屏幕已经呼出输入法时，在另一块屏幕重复呼出，有概率键盘状态不会刷新。只需先把之前的键盘收起再呼出即可~~
2. 在副屏上运行竖屏应用时，主屏横屏呼出键盘，会导致键盘以竖向显示，UI错位。需要先退出副屏的竖屏应用再使用
3. ~~emoji界面底部切tab的按钮偏小；副屏竖屏键盘的菜单栏按钮偏小。~~
