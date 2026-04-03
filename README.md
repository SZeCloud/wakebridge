# WakeBridge

一个极小的 Android 辅助 App，用于在无 root 场景下通过前台 `Activity` 尝试点亮屏幕。

当前版本目标很单一：

- 允许通过显式组件拉起 `WakeActivity`
- 允许通过 `wakebridge://wake?hold_ms=12000` 这样的自定义协议拉起
- 点亮后短暂保持常亮，再自动退出
- 不尝试绕过系统锁屏密码

## 包名

`com.shizy.wakebridge`

## 触发方式

### 1. 显式组件

```bash
am start -n com.shizy.wakebridge/.WakeActivity --ei hold_ms 12000
```

### 2. 自定义协议

```bash
termux-open-url 'wakebridge://wake?hold_ms=12000'
```

`hold_ms` 单位是毫秒，当前代码会把它限制在 `1000` 到 `60000` 之间。

## 构建

需要：

- JDK 17
- Android SDK Platform 34
- Android Build Tools 34.0.0

构建命令：

```bash
./gradlew assembleDebug
```

产物路径：

```bash
app/build/outputs/apk/debug/app-debug.apk
```

## 关键实现

- [`WakeActivity.java`](app/src/main/java/com/shizy/wakebridge/WakeActivity.java)
- [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)

实现重点：

- `setShowWhenLocked(true)`
- `setTurnScreenOn(true)`
- `FLAG_KEEP_SCREEN_ON`
- `FLAG_SHOW_WHEN_LOCKED`
- `FLAG_TURN_SCREEN_ON`
- `requestDismissKeyguard(...)`

## 限制

- 不保证所有 ROM 都允许后台无感亮屏
- 不绕过密码、图案、指纹等系统安全锁
- 某些设备上从 Termux 调起 Activity 时，`am` 兼容层可能会报噪音异常，此时应以实际是否亮屏为准
