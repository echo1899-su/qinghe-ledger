# 青禾账本

一款**完全离线**的安卓记账应用。不联网、不注册、不收集、不上传任何数据。

包名 `local.qinghe.ledger` · Android 7.0+ · 轻量安装包

---

## 它是什么

打开就能记账。没有注册页，没有登录框，没有广告，没有内购，没有云同步。

数据只存在你自己的手机里——这个 App **连 `INTERNET` 权限都没有申请**，物理上无法联网。

## 功能

- **记一笔**：数字键盘、分类、备注，三步完成
- **多账本 / 多账户**：现金、微信、支付宝、银行卡，可自定义
- **预算**、定时记账、搜索筛选、收支日历
- **统计**：月度 / 年度总览、分类占比、六个月趋势、年度收支
- **钱的河流**：桑基图看钱从哪来、流到哪去
- **奶茶指数**：把金额换算成「≈ 3 杯奶茶」
- **导出**：完整备份 JSON、CSV / XLSX、月报 PDF
- **安全**：密码锁屏、桌面小组件、每日提醒
- **外观**：晴日 / 青禾 / 夜色 三套主题

## 隐私

- 不声明 `INTERNET` 权限，没有任何网络请求
- 数据存于应用私有目录，卸载即删除
- 备份文件是**明文 JSON**，请自行妥善保管

## 构建

当前构建脚本支持 **Windows**。需要 Python 3.10+、JDK 17 和 Android SDK 命令行工具（Build-Tools 35、Platform android-35）；不需要 Gradle 或 Android Studio。

### 1. 准备工具链

在仓库根目录建一个 `toolchain/`（已在 `.gitignore` 中忽略），放三个子目录：

```
toolchain/
├── jdk/            JDK 17 或更高
├── build-tools/    Android SDK Build-Tools（含 aapt2、zipalign、lib/d8.jar、lib/apksigner.jar）
└── platform/       Android SDK Platform（android-35，含 android.jar）
```

每类工具支持直接解压到对应目录，也支持额外一层版本目录，例如 `jdk/jdk-17/bin/javac.exe`、`build-tools/35.0.0/aapt2.exe`、`platform/android-35/android.jar`。每类目录中只能放一套完整工具，缺失或多套都会明确报错。

也可以放在任意位置，用环境变量指定：

```powershell
$env:QINGHE_TOOLCHAIN = "D:\AndroidTools\toolchain"
```

### 2. 编译

```powershell
python build/build_qinghe.py
```

产物在 `dist/青禾账本-<版本>.apk`，脚本会打印 SHA-256。

### 3. 签名

首次运行脚本会在 `signing/` 里**自动生成**一套新密钥。

> `signing/` 与 `*.jks` 都在 `.gitignore` 中——**签名密钥绝不提交，也绝不发给别人**。
>
> 发布更新时必须一直使用同一套签名；换签名后，旧版本无法覆盖安装。

## 目录结构

```
qinghe-ledger/
├── LICENSE                GPL-3.0 全文
├── README.md
├── qinghe/                应用源码
│   ├── AndroidManifest.xml
│   ├── assets/            HTML / CSS / JS 界面（WebView 加载）
│   ├── res/               图标、开屏、小组件布局
│   └── src/               Java 容器（WebView、存储、导出、锁屏）
└── build/
    └── build_qinghe.py    构建脚本
```

## 验证

安装 Node.js 18+ 后，在仓库根目录运行 `node --test tests/*.test.js`。构建工具链查找测试运行 `python -m unittest discover -s tests -p "test_*.py"`。测试使用模拟数据，不连接手机。

## 数据格式

- 金额以**整数分**存储，避免浮点误差
- 保存时先写临时文件、再原子替换，并保留上一份内部副本
- 读写统一限制为 25,000,000 个 UTF-8 字节，超限会在写入前拒绝。
- 读取失败页支持导出原始文件（包括超限或损坏文件）、验证并恢复上一份内部副本。恢复前的首份原文件另存为 `ledger.before-recovery.json`，后续恢复不覆盖该留底；失败页可导出它。恢复不会自动丢弃坏记录。
- 表格导入缺少收支类型时默认跳过并报告；可明确选择正数收入/负数支出、全部支出或全部收入。
- 内部副本**不能**替代外部备份——换机或卸载前请手动导出

## 说明

- 定时账目在打开应用时补记，不宣称后台准时执行
- 每日提醒可能被系统省电策略延后，重启后需打开一次应用恢复

## 许可证

[GNU General Public License v3.0](LICENSE)

你可以自由使用、修改、分发；但修改后分发，也必须同样以 GPL v3 开源。
