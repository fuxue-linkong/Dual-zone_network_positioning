# CW练习功能模块设计规格

## 概述

在APP主页的主导航菜单中添加"CW练习"功能模块入口，提供摩尔斯电码练习功能，包括自由练习和教程练习两种模式。

## 需求分析

### 功能需求

1. **主页入口**：在主页列表中添加"CW练习"入口，与"定位"、"卫星"并列
2. **自由练习**：用户自主选择练习内容和参数
   - 速度（WPM）
   - 字符集（字母/数字/符号）
   - 练习长度（字符数/时间）
   - 音调频率
   - 播放模式（连续/间隔）
3. **教程练习**：参考lcwo.net，提供结构化的分步练习指导
   - Koch方法课程（40个课程）
   - 字符组练习
   - 呼号训练
   - 文本训练
   - 进度跟踪和统计

### 技术需求

1. **音频实现**：使用第三方库生成摩尔斯电码音频
2. **输入方式**：支持文本输入框和虚拟键盘
3. **数据存储**：使用Room数据库存储设置、进度和成绩历史
4. **响应式布局**：在不同设备上均能正常显示和操作

## 架构设计

### 页面结构

```
CW练习主页 (CWPracticeScreen)
├── 自由练习入口
│   ├── 参数设置页面 (FreePracticeSettingsScreen)
│   └── 练习页面 (PracticeScreen)
└── 教程练习入口
    ├── 课程列表页面 (TutorialListScreen)
    ├── 课程详情页面 (TutorialDetailScreen)
    └── 练习页面 (PracticeScreen)
```

### 数据层

- **CWSettingsStore**：存储练习设置（速度、音调等）
- **CWProgressStore**：存储学习进度和成绩历史
- **MorseCodeGenerator**：摩尔斯电码生成器（使用第三方库）

### 核心组件

- **MorseCodePlayer**：音频播放组件
- **MorseCodeInput**：输入组件（文本框 + 虚拟键盘）
- **StatisticsChart**：统计图表组件

## 数据模型

### CWSettings
```kotlin
data class CWSettings(
    val wpm: Int = 15, // 速度
    val frequency: Int = 600, // 音调频率
    val characterSet:CharacterSet = CharacterSet.LETTERS, // 字符集
    val practiceLength: Int = 100, // 练习长度（字符数）
    val practiceDuration: Int = 5, // 练习时长（分钟）
    val playMode: PlayMode = PlayMode.CONTINUOUS // 播放模式
)
```

### CWProgress
```kotlin
data class CWProgress(
    val courseId: Int, // 课程ID
    val lessonId: Int, // 课程ID
    val completedAt: Long, // 完成时间
    val accuracy: Float, // 准确率
    val wpm: Int, // 速度
    val duration: Int // 时长
)
```

## UI设计

### CW练习主页
- 两个入口卡片：自由练习、教程练习
- 快速统计：总练习时长、平均准确率、当前速度

### 自由练习设置页面
- 速度滑块（5-50 WPM）
- 音调频率滑块（400-800 Hz）
- 字符集选择（字母/数字/符号/自定义）
- 练习长度输入（字符数或时间）
- 播放模式选择（连续/间隔）

### 教程练习列表页面
- 课程列表（40个Koch课程）
- 进度显示（已完成/进行中/未开始）
- 快速统计

### 练习页面
- 音频播放控制（播放/暂停/停止）
- 输入区域（文本框 + 虚拟键盘）
- 实时反馈（正确/错误指示）
- 进度条

## 实现计划

### 阶段1：基础架构
1. 添加依赖（Room数据库、第三方音频库）
2. 创建数据模型和数据库
3. 创建数据存储类

### 阶段2：核心功能
1. 实现CW练习主页
2. 实现自由练习设置页面
3. 实现练习页面

### 阶段3：教程功能
1. 实现教程练习列表页面
2. 实现课程详情页面
3. 实现进度跟踪

### 阶段4：完善功能
1. 实现统计图表
2. 优化UI/UX
3. 测试和调试

## 技术选型

### 第三方库
- **Room**：数据库存储
- **AudioTrack API**：摩尔斯电码音频生成（Android原生API）
- **MPAndroidChart**：统计图表

### 依赖关系
- 与现有项目架构保持一致
- 遵循MVVM架构模式
- 使用Jetpack Compose构建UI

## 测试策略

### 单元测试
- 数据模型测试
- 业务逻辑测试

### 集成测试
- 数据库操作测试
- 音频播放测试

### UI测试
- 页面导航测试
- 用户交互测试

## 风险评估

### 技术风险
- 第三方库兼容性问题
- 音频播放性能问题

### 时间风险
- 功能复杂度较高
- 需要较多测试时间

## 成功标准

1. 功能完整性：实现所有需求功能
2. 性能要求：音频播放流畅，UI响应及时
3. 用户体验：界面美观，操作简单
4. 代码质量：代码清晰，易于维护
