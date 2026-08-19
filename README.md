# FileTransfer
<img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform"> <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language"> <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">

**一款轻量、极速的 Android 本地文件局域网传输工具。接收端无需安装任何客户端，打开浏览器即可秒速接收。**
## 「界面展示」
**主界面 ＆ 访问日志界面**

<img height="300" alt="Screenshot_2026-08-19-13-07-17-89_9e5b3fe7739a7b13690f6b880567caea" src="https://github.com/user-attachments/assets/78287816-962f-4661-9f39-ec7de42a30bc" />

**前端**

<img height="300" alt="Screenshot_2026-08-19-13-07-38-69_a252b927494330cdc2c8ba3b3f952e5e" src="https://github.com/user-attachments/assets/5a08ccfd-e0a5-468e-bbde-b459562925b2" />

## 「亮点」
- 接收者无需下载客户端，使用浏览器即可接收文件
- 接入 Android 系统分享/打开文件, 可快速传输指定文件
- 局域网传输，不消耗流量
- 支持多线程传输，速度更快

## 「快速上手」
**处于同一局域网**：确保发送端手机与接收设备连接至**同一个 Wi-Fi**（或接收端连接手机开启的**热点**）。

**选择文件**：
   - **方式 A**：打开本 App，点击添加要发送的文件；
   - **方式 B**：在手机任意 App（如相册、文件管理器）中选中文件，点击「分享」并选择 **FileTransfer**。

**开始传输**：点击"启动", App 将自动生成访问地址及二维码, 点击 **"服务状态"** 右侧的 **"二维码"** 图标即可查看。

**接收文件**：在电脑或平板浏览器中输入对应的地址（或扫码），点击即可极速下载文件。

## 「技术栈」
**语言** : `Kotlin`

**服务端** : `Ktor`

**UI** : `Jetpack Compose`

## 「参与进来」
欢迎提交 Issue 反馈 Bug 或提出新需求！如果你有兴趣改进代码，欢迎提交 Pull Request。
