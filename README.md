# 天机 (Tianji)

> 把 Agent 时代的玄学能力，带给普通手机用户的 Android AI 应用。

开源社区里的八字、塔罗、风水等玄学 AI 能力，大多以"装进 Claude Code 的技能"形态存在，普通用户用不上；现有网页竞品又只做单点。**天机把「今日 · 八字 · 姻缘 · 塔罗 · 风水」五件套做成一个原生 Android App**——本地引擎保证排盘确定性，LLM 只负责解读，10 秒出结果，有氛围感、值得截图分享。

## 功能模块

| 模块 | 输入 | 本地确定性计算 | LLM 解读 |
|---|---|---|---|
| 今日 | 无 | 每日签句 + 黄历摘要 | 每日一言 |
| 八字 | 阳历/农历生日 + 时辰 + 性别 | 四柱、五行、十神、大运流年 | 日主强弱、格局、事业感情建议 |
| 姻缘 | 双方信息 / 摇签筒 | 八字合婚打分 / 抽 100 签 | 合婚报告 / 签诗解签 |
| 塔罗 | 问题 + 牌阵（单张/三张/凯尔特十字） | 抽牌 + 正逆位 | 牌义 + 牌阵叙事 |
| 风水 | 朝向 + 入住年份 + 户型要点 | 玄空飞星盘 + 八宅吉凶位 | 旺位凶位与布局建议 |

## 核心设计

1. **确定性计算外包**——排盘/抽牌/飞星全部由本地 Kotlin 引擎计算，LLM 只做解读，根治"同一生日问六次、日柱出五种答案"的幻觉问题
2. **先追问，再输出**——信息不全（如缺时辰）时先引导补全，不强行排盘
3. **口径透明**——显示所用流派与换日规则
4. **只给结构化参考**——不替代医疗/法律/财务建议，不制造焦虑；结果页统一"排盘可视化 → 结论金句 → 分维度解读 → 仅供娱乐参考"结构
5. **密钥不出设备**——LLM API Key 由用户在设置页自行配置，仅存本机 DataStore，代码与仓库中不含任何密钥

## 应用下载

- **APK 下载（v0.1.0）**：[GitHub Releases](https://github.com/yuxing28/tianji/releases/latest)
- 直链：[tianji-v0.1.0-debug.apk](https://github.com/yuxing28/tianji/releases/download/v0.1.0/tianji-v0.1.0-debug.apk)（约 17MB）
- 系统要求：Android 8.0+（minSdk 26）；debug 签名版本，安装时需允许"未知来源"
- 无 Key 也能用：可完成排盘/抽牌看到结果，解读区引导去设置页配置 LLM

## 技术栈

- **语言**：Kotlin（100%），JDK 17
- **UI**：Jetpack Compose + Material 3，单 Activity + Navigation Compose
- **存储**：DataStore Preferences（LLM 配置、历史记录）
- **网络**：OkHttp + kotlinx.serialization，支持 OpenAI 兼容与 Anthropic 两种协议
- **测试**：JUnit 4，四大引擎单元测试
- **构建**：Gradle Kotlin DSL，compileSdk 35 / targetSdk 35 / minSdk 26

## 项目结构

```
app/src/main/java/com/tianji/app/
├── engine/          # 本地确定性引擎：BaziEngine / MarriageEngine / TarotEngine / FengshuiEngine
├── llm/             # LlmClient：OpenAI 兼容 / Anthropic 双协议客户端
├── data/            # LlmConfigStore / HistoryStore / LlmPresets（7 家厂商预设）
└── ui/              # Compose 屏幕、通用组件、主题
app/src/test/        # 四大引擎单元测试
docs/                # PRD 与排盘参考资料
design-system/       # 视觉设计系统（MASTER.md）
```

## 本地构建

```bash
git clone https://github.com/yuxing28/tianji.git
cd tianji
./gradlew assembleDebug   # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # 运行引擎单元测试
```

## LLM 接入

设置页内置 7 家预设：讯飞星火、DeepSeek、通义千问、Kimi、智谱、OpenAI 兼容、Anthropic。也可自定义任意 OpenAI 兼容端点 + API Key + 模型名。解读请求 = 模块知识库 + 排盘结构化 JSON 作为上下文。

## 文档

- [产品需求文档 PRD](docs/PRD.md)
- [设计系统 MASTER](design-system/tianji/MASTER.md)
- [排盘参考资料](docs/reference/)（八字 / 姻缘 / 塔罗 / 风水知识库与算法参考）

## 致谢与参考来源

排盘算法与知识库参考了以下开源技能仓库，在此致谢：

- [jinchenma94/bazi-skill](https://github.com/jinchenma94/bazi-skill)（MIT License）
- [voidforall/fengshui.skill](https://github.com/voidforall/fengshui.skill)
- [daman-ovo-0404/tarot-skill](https://github.com/daman-ovo-0404/tarot-skill)
- [Ming-H/yinyuan-skills](https://github.com/Ming-H/yinyuan-skills)

## 免责声明

本应用仅供娱乐参考，不构成医疗、法律、财务等任何专业建议。