# WakeBridge

一个极小的 Android 辅助 App，用于在**无 root、无 Shizuku** 场景下，通过显式广播、`WakeLock`、全屏通知和 `AlarmClock` 兜底来尝试点亮屏幕，并与 Termux / OpenClaw 做最小链路集成。

这个项目的目标不是“做一个全能自动化框架”，而是只解决一件事：

- 在当前这台安卓设备上，尽量稳定地把屏幕点亮
- 保持一小段时间常亮
- 然后自动退出，不做长期常驻
- 不引入 root、Shizuku、无线 ADB、Accessibility 常驻、watchdog 常驻这类额外复杂链路

## 为什么会有这个项目

这个项目不是凭空设计出来的，而是基于一轮轮排查和试错后收敛出来的最小方案。

在落到这个实现之前，已经实际排查过多类路径，包括：

- 纯 Termux / Termux:API 命令
- `termux-wake-lock`
- `cmd power wakeup`
- `input keyevent KEYCODE_WAKEUP`
- Termux `am` / `termux-open-url`
- 更高权限链路
- 需要额外后台能力的自动化方案

最后收敛到这条链路：

```text
Termux / OpenClaw
  -> am broadcast -n com.shizy.wakebridge/.WakeReceiver
  -> WakeReceiver
  -> WakeLock / AlarmClock / Full-screen notification / WakeActivity
  -> 点亮屏幕
```

原因很直接：

- 它不依赖系统签名权限
- 它不要求 root
- 它不依赖 Shizuku 的会话存活
- 它不需要长期后台轮询
- 它和 Termux / OpenClaw 的耦合点仍然很小，只需要一次显式广播

## 设计目标

- 最小权限
- 最少常驻组件
- 最低维护复杂度
- 最容易和 Termux / OpenClaw 集成
- 最小化“重复拉起、互相打架、后台长期耗电”

## 使用前提

在当前这类 MIUI / Android 设备上，如果希望后台触发尽量稳定，除了安装 APK，本机还需要额外打开这些系统设置：

- `WakeBridge` 允许自启动
- `WakeBridge` 电量策略设置为“无限制”
- 通知权限开启
- 如果系统提供“全屏通知 / 锁屏显示 / 后台弹出界面 / 显示在其他应用上层”等选项，也建议开启

如果这些前置条件没开，常见现象就是：

- 广播已经分发成功，但屏幕没有真的亮
- 偶尔能亮，偶尔失败
- 亮屏后不一定能稳定进入后续解锁动作

所以这个项目的正确预期是：

- 它依赖系统愿意放行后台唤醒
- 在部分 ROM 上，**自启动 + 电量无限制** 基本属于必要条件，不是可有可无的“优化项”

## 非目标

当前版本**明确不做**这些事：

- 不绕过系统锁屏密码
- 不注入全局按键
- 不模拟完整解锁流程
- 不做 Accessibility 自动化
- 不做后台常驻守护
- 不做成功回执服务端
- 不承诺适配所有 ROM

## 包名

`com.shizy.wakebridge`

## 当前方案的优势

### 1. 链路非常短

只有一个极小 App + 一次显式触发，没有额外的常驻守护进程、轮询线程、健康检查器、保活代理。

这对实际稳定性很重要：

- 出问题时更容易排查
- 不容易出现多头拉起
- 不容易和现有 OpenClaw 链路打架

### 2. 对 Termux / OpenClaw 友好

这个项目没有要求 OpenClaw 深度接入 Android 权限模型，只要能执行：

```bash
am broadcast -n com.shizy.wakebridge/.WakeReceiver \
  -a com.shizy.wakebridge.ACTION_WAKE \
  --ei hold_ms 12000
```

就可以触发。

这比依赖复杂 Android 自动化栈更容易维护。

### 3. 空闲耗电理论上更低

这是这个方案相对很多“自动化大而全方案”的一个现实优势。

原因不是它“点亮时更省电”，而是它在**不触发时几乎不做事**：

- 没有后台轮询
- 没有 watchdog
- 没有定时健康检查
- 没有长期持有 WakeLock
- 没有常驻 Accessibility 服务
- 没有 Shizuku / 无线调试会话维护成本

换句话说：

- **空闲态功耗**，它应该优于需要后台常驻的方案
- **触发瞬时功耗**，差异通常不在 App 本体，而在“屏幕被点亮且保持了多久”

### 4. 不依赖高权限会话

很多方案的问题不是第一次能跑，而是长期稳定性差：

- Shizuku 会话可能失效
- 无线 ADB 会话可能掉
- 更高权限链路一旦断开，后续恢复复杂

WakeBridge 没有这层依赖，维护面更小。

## 当前方案的劣势

### 1. 成功回执不强

现在的实现是“已触发”，不是“系统层明确确认已亮屏”。

也就是说：

- 用户肉眼能看到成功
- 但脚本未必能稳定拿到一个完全可信的成功状态

这是无 root 场景里很常见的现实限制。

### 2. 不保证所有 ROM 一致

不同厂商 ROM 对这些行为的限制差异很大。  
同样的 `Activity` 唤醒链，在一台机子能用，不代表所有设备都一样。

### 3. 不绕过安全锁

它只能 best-effort 点亮屏幕、尝试展示在锁屏上层。

它不会也不应该：

- 绕过密码
- 绕过图案锁
- 绕过指纹 / 人脸等系统安全锁

### 4. 成功回执仍然依赖现场验证

某些设备上，普通应用拿不到完整系统日志，或者系统会放行“亮屏”但不放行“真正顶到前台”。  
所以当前推荐把脚本输出视为“已分发 / 已尝试”，最终仍以手机是否实际亮屏、是否进入桌面为准。

### 5. 仍然受系统电池与自启动策略影响

这不是一个完全脱离系统策略的“万能后台唤醒器”。

在当前设备的实测里：

- 没开自启动、没设电量无限制时，成功率明显不稳定
- 打开自启动、设为电量无限制后，亮屏和后续上滑成功率会明显改善

所以仓库不应该把这件事写成“可选建议”，而应该明确写成“实机上经常是必要前提”。

## 和其他方案相比

| 方案 | 优势 | 劣势 | 当前结论 |
| --- | --- | --- | --- |
| 纯 `termux-wake-lock` | 简单 | 只能防 CPU 睡眠，不等于亮屏 | 不满足需求 |
| `cmd power wakeup` / `input keyevent` | 看起来直接 | 普通 App 用户态经常被权限拦截 | 当前设备不可行 |
| Shizuku / 无线 ADB | 能力更强 | 会话管理复杂，长期稳定性一般 | 不适合当前目标 |
| 自动化大框架 | 功能多 | 常驻组件多，链路重，维护成本高 | 过重 |
| WakeBridge | 链路短、最小权限、易集成 | 回执弱，ROM 兼容性仍有限 | 当前最平衡 |

## 关于耗电

### 这个方案是否有优势

从工程结构上看，**有空闲态优势**，但不应夸大为“点亮时本身特别省电”。

更准确的说法是：

- 在**不触发**时，它几乎没有后台持续成本
- 在**触发**时，主要耗电来自屏幕本身，而不是这个 App 的业务逻辑

### 为什么说它在空闲态更有优势

因为它默认不做这些事：

- 不常驻前台服务
- 不定时自检
- 不后台轮询接口
- 不持续持有 WakeLock
- 不维持外部高权限会话

对于“偶尔唤醒一下屏幕”的场景，这比常驻型自动化方案更合理。

### 需要实事求是的地方

当前仓库没有做严格的电量基准测试，例如：

- 同一设备、同一 ROM、同一亮屏时长
- 与 Tasker / MacroDroid / Shizuku / 其他桥接方案做毫安时对照

所以这里的结论是：

- **这是基于架构和运行方式的工程判断**
- **不是实验室级别的电量 benchmark**

### 真正影响耗电的关键变量

不是 WakeBridge 本身的代码复杂度，而是：

- 屏幕点亮次数
- 每次 `hold_ms` 保持多久
- 当前屏幕亮度
- 是否叠加了其他后台链路

因此在实际使用中，最重要的优化不是继续改 App，而是：

- 尽量缩短 `hold_ms`
- 只在需要时触发
- 不叠加额外 watchdog / 轮询守护

## 触发方式

### 1. 显式广播到 `WakeReceiver`

```bash
am broadcast -n com.shizy.wakebridge/.WakeReceiver \
  -a com.shizy.wakebridge.ACTION_WAKE \
  --ei hold_ms 12000
```

### 2. 直接启动 `WakeActivity`

```bash
am start -n com.shizy.wakebridge/.WakeActivity --ei hold_ms 12000
```

`hold_ms` 单位是毫秒，当前代码会把它限制在 `1000` 到 `60000` 之间。

### 3. 可选的 Orb Eye 上滑解锁

如果设备已经安装并启用 [Orb Eye](https://github.com/KarryViber/orb-eye)，则可以在亮屏后再追加一次上滑：

```bash
curl -sS -X POST http://127.0.0.1:7333/swipe \
  -H 'Content-Type: application/json' \
  -d '{"x1":540,"y1":2200,"x2":540,"y2":220,"duration":520}'
```

这个能力只适合“无密码，仅需上滑”的锁屏场景，不绕过密码、图案、指纹等系统认证。

## 建议的系统设置

为了让后台触发更接近可用状态，建议在安装后立刻检查：

1. `设置 -> 应用 -> WakeBridge -> 自启动`：开启
2. `设置 -> 电量与性能 / 应用电量管理 -> WakeBridge`：改为“无限制”
3. `设置 -> 通知管理 -> WakeBridge`：允许通知
4. 如系统存在额外开关，再开启：
   - 全屏通知
   - 锁屏显示
   - 后台弹出界面
   - 显示在其他应用上层

如果缺少这些设置，仓库中的示例命令仍然可能“返回成功”，但手机现场不一定真的亮屏。

## 与 Termux / OpenClaw 集成

推荐从 Termux 侧这样触发：

```bash
am broadcast -n com.shizy.wakebridge/.WakeReceiver \
  -a com.shizy.wakebridge.ACTION_WAKE \
  --ei hold_ms 12000 >/dev/null 2>&1 || true
```

在 OpenClaw 中，当前推荐封成两个桥接命令：

```bash
phone_wake_screen
phone_wake_unlock
```

前者只负责亮屏，后者会在亮屏后通过 Orb Eye 再做一次上滑。

## 当前设备上的实践结论

在当前设备上，已经验证过以下事实：

- WakeBridge 可以实际点亮屏幕
- 与隐式广播相比，显式广播到 `WakeReceiver` 更稳定
- 只亮屏和“亮屏后上滑”都已经在当前设备上跑通
- 直接依赖日志或 `am start` 返回码都不可靠
- `自启动 + 电量无限制` 会明显影响后台唤醒成功率
- “是否成功”的最终判断，应以**手机是否真的亮屏 / 是否真的进入桌面**为准

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
- 当前版本没有强成功回执

## 后续可以继续做的方向

- 增加“写回执文件”能力，方便脚本侧确认最近一次触发时间
- 增加更细的触发日志
- 如果后续确有必要，再评估是否扩展为“点亮后辅助上滑”，但这会引入新的复杂度
