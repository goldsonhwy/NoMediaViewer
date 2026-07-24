# Yellow-gallery

黄黑配色的 Android 隐藏图片/相册浏览器，面向 `.nomedia` 与普通子目录中的图片浏览、已读标记、收藏筛选和本地/WebDAV/SMB 备份。

## 功能

- 设置页添加手机根目录，递归扫描所有含图片的子目录
- 文件夹页以 9:16 缩略图网格显示相册，左上角绿色三角表示整夹已读
- 浏览页支持单列/双列瀑布流，单击收藏，双击全屏
- 浏览中左滑切换到下一个文件夹，并显示 2 秒提示
- 已读图片左上角显示绿色小三角，不再隐藏
- 收藏页 2 列原比例瀑布流，全屏支持单击收藏/取消、双击缩放、下拉退出
- 收藏可备份到本地目录、WebDAV 或 SMB（SMB 实验性）

## 构建

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="/opt/gradle/gradle-8.2/bin:$PATH"
gradle assembleDebug --no-daemon
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

- Kotlin
- Jetpack Compose
- Coil
- SharedPreferences
- Android File API + MANAGE_EXTERNAL_STORAGE

## 仓库

https://github.com/goldsonhwy/Yellow-gallery
