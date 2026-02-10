# 局域网文件传输助手
## 「特性」
- 接收者无需下载客户端，使用浏览器即可接收文件
- 局域网传输，不消耗流量
- 支持多线程传输，速度更快
- 支持将服务器地址以 `loc=地址` `POST` 请求形式上传(需要Fork本项目，修改`app/src/main/java/com/xjyzs/filetransfer/MainActivity.kt`中形如`"main", ip, 1145, "http://IP地址", dict`的代码)