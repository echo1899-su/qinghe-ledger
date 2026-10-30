#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""青禾账本 · 构建脚本

不需要 Gradle / Android Studio，只用 JDK + Android SDK 命令行工具。

环境变量：
    QINGHE_TOOLCHAIN   工具链目录（内含 jdk/、build-tools/、platform/），默认 ./toolchain
    QINGHE_OUT         输出目录，默认 ./dist

会做：aapt2 编译资源 → 链接清单 → javac → d8 → 打包 → zipalign → apksigner 签名 → 校验
"""

import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

BUILD_DIR = Path(__file__).resolve().parent
ROOT = BUILD_DIR.parent
APP = ROOT / "qinghe"
OUT = Path(os.environ.get("QINGHE_OUT") or ROOT / "dist")
TOOL = Path(os.environ.get("QINGHE_TOOLCHAIN") or ROOT / "toolchain")
BUILD = ROOT / ".build"      # 中间产物，已在 .gitignore 中忽略
SIGN = ROOT / "signing"      # 签名材料，绝不提交

def pick(sub: str) -> Path:
    """Accept an extracted tool directly or exactly one matching version folder."""
    required = {"jdk": ["bin/java.exe", "bin/javac.exe", "bin/keytool.exe"],
                "build-tools": ["aapt2.exe", "zipalign.exe", "lib/d8.jar", "lib/apksigner.jar"],
                "platform": ["android.jar"]}[sub]
    base = TOOL / sub
    candidates = [base]
    if base.is_dir():
        candidates += sorted(p for p in base.iterdir() if p.is_dir())
    matches = [p for p in candidates if all((p / name).is_file() for name in required)]
    if len(matches) != 1:
        raise SystemExit(f"工具链 {base} 必须包含且仅包含一套有效工具；找到 {len(matches)} 套。需要：{', '.join(required)}")
    return matches[0]


if os.name != "nt":
    raise SystemExit("当前构建脚本仅支持 Windows，请使用 Windows 的 JDK 和 Android SDK。")
JDK = pick("jdk")
BT = pick("build-tools")
PLATFORM = pick("platform")
for d in (OUT, BUILD, SIGN):
    d.mkdir(parents=True, exist_ok=True)

env = os.environ.copy()
env["JAVA_HOME"] = str(JDK)
env["PATH"] = str(JDK / "bin") + os.pathsep + env["PATH"]
env["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US"


def run(args):
    r = subprocess.run(
        [str(a) for a in args],
        env=env,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    if r.returncode:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit(r.returncode)
    if r.stdout.strip():
        print(r.stdout.strip())
    if r.stderr.strip():
        print(r.stderr.strip())


# 版本号直接从 AndroidManifest.xml 读，避免两处不一致
manifest_text = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
m = re.search(r'android:versionName="([^"]+)"', manifest_text)
if not m:
    raise SystemExit("AndroidManifest.xml 里没找到 android:versionName")
VERSION = m.group(1)

# 1. 资源
run([BT / "aapt2.exe", "compile", "--dir", APP / "res", "-o", BUILD / "resources.zip"])
(BUILD / "gen").mkdir(exist_ok=True)
run([
    BT / "aapt2.exe", "link", "-o", BUILD / "resources.apk",
    "--manifest", APP / "AndroidManifest.xml",
    "-I", PLATFORM / "android.jar",
    "-A", APP / "assets",
    "--java", BUILD / "gen",
    BUILD / "resources.zip",
])

# 2. Java
(BUILD / "classes").mkdir(exist_ok=True)
sources = list((APP / "src").rglob("*.java")) + list((BUILD / "gen").rglob("*.java"))
argfile = BUILD / "javac.args"
argfile.write_text(
    "\n".join('"' + str(p).replace("\\", "/") + '"' for p in sources), encoding="utf-8"
)
run([
    JDK / "bin/javac.exe", "-encoding", "UTF-8", "-source", "8", "-target", "8",
    "-classpath", PLATFORM / "android.jar",
    "-d", BUILD / "classes", "@" + str(argfile),
])

jar = BUILD / "classes.jar"
with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
    for f in (BUILD / "classes").rglob("*.class"):
        z.write(f, f.relative_to(BUILD / "classes").as_posix())

# 3. dex
(BUILD / "dex").mkdir(exist_ok=True)
run([
    JDK / "bin/java.exe", "-cp", BT / "lib/d8.jar", "com.android.tools.r8.D8",
    "--release", "--min-api", "24", "--lib", PLATFORM / "android.jar",
    "--output", BUILD / "dex", jar,
])

# 4. 打包 + 对齐
unsigned = BUILD / "unsigned.apk"
shutil.copy2(BUILD / "resources.apk", unsigned)
with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
    for f in (BUILD / "dex").glob("*.dex"):
        z.write(f, f.name)
run([BT / "zipalign.exe", "-P", "16", "-f", "4", unsigned, BUILD / "aligned.apk"])

# 5. 签名（首次自动生成密钥；signing/ 已被 .gitignore 忽略）
password = SIGN / "password.txt"
key = SIGN / "qinghe-private.jks"
if not password.exists():
    password.write_text(secrets.token_urlsafe(32), encoding="ascii")
if not key.exists():
    run([
        JDK / "bin/keytool.exe", "-genkeypair",
        "-keystore", key, "-storepass:file", password, "-keypass:file", password,
        "-alias", "qinghe", "-keyalg", "RSA", "-keysize", "3072",
        "-validity", "10000",
        "-dname", "CN=Qinghe Offline Ledger, OU=Personal, O=Local, C=CN",
        "-storetype", "JKS",
    ])

apk = OUT / f"青禾账本-{VERSION}.apk"
run([
    JDK / "bin/java.exe", "-jar", BT / "lib/apksigner.jar", "sign",
    "--ks", key, "--ks-pass", "file:" + str(password),
    "--ks-key-alias", "qinghe", "--out", apk, BUILD / "aligned.apk",
])

# 6. 校验
run([JDK / "bin/java.exe", "-jar", BT / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk])
run([BT / "aapt2.exe", "dump", "badging", apk])

print(json.dumps({
    "apk": str(apk),
    "size": apk.stat().st_size,
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
}, ensure_ascii=False))
