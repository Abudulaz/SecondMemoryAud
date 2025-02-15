# 语音识别模型安装说明

为了使用离线语音识别功能，需要安装中文语音模型。请按照以下步骤操作：

1. 下载模型文件：
   - 访问 https://alphacephei.com/vosk/models
   - 下载 `vosk-model-small-cn-0.22.zip` (约40MB)

2. 解压模型文件：
   - 解压下载的zip文件
   - 将解压出的文件夹重命名为 `vosk-model-cn`
   - 将整个 `vosk-model-cn` 文件夹复制到 `app/src/main/assets/` 目录下

3. 验证安装：
   确保 `app/src/main/assets/vosk-model-cn` 目录包含以下文件和文件夹：
   ```
   vosk-model-cn/
   ├── am/           (声学模型)
   ├── conf/         (配置文件)
   ├── graph/        (语言模型)
   ├── ivector/      (说话人适应)
   └── README
   ```

4. 重新构建应用：
   - 清理并重新构建项目
   - 安装到设备上测试

注意事项：
- 模型文件较大，请确保有足够的存储空间
- 首次启动时，应用会自动将模型复制到内部存储
- 识别过程完全离线，不需要网络连接
- 支持实时识别，会在通知栏显示进度

如果遇到问题：
1. 确认模型文件夹名称为 `vosk-model-cn`
2. 确认模型文件夹位置正确
3. 检查 logcat 输出中的错误信息
4. 清理应用数据重试
