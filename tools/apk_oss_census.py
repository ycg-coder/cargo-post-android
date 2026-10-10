#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""F-2 第 15 步：把"Gradle 声明的依赖"与"APK 里真有的字节码"对起来数。

为什么不能只读 `gradle :app:dependencies`：那张表说的是 classpath **解析结果**，
不等于 classes.dex 里真有这段代码（只在 test 源集的 jar、被 merge 掉的资源、framework 自己提供的类都会骗人）。
为什么不能只看 APK 大小：那说不出"是哪家的代码"。
所以两头都读：
  1) DEX 头解析（不用正则猜）：
     - defined = class_defs_item[].class_idx 指向的类型 ⇒ 这个 APK **真打包**了这些类；
     - referenced = type_ids_item 全表 ⇒ 代码**引用**到的类型（framework 提供的也在里面）。
     两个数分开打：`org.json` 会 referenced>0 而 defined=0（系统提供，没打包）——只看一个数就会写错矩阵。
  2) 字面量侧：dex + resources.arsc 里搜给定串（用来实测 `© OpenStreetMap contributors` 在不在包里）。

用法：
  python tools/apk_oss_census.py <apk 路径>              # 前缀表：defined/referenced/判定
  python tools/apk_oss_census.py <apk 路径> --strings     # 再跑字面量表
  python tools/apk_oss_census.py <apk 路径> --families    # 列出打包类的前三段包族（盘点用）

只读，不写。退出码 0=读到了；2=APK 打不开 / 没有 dex / 头解析不出一个数
（"打不开"绝不允许读成"里面没有"）。
"""
import os
import re
import struct
import sys
import zipfile
from collections import Counter

# 候选包前缀：左边给人看，右边是 DEX 类型描述符里的包前缀。
# ⚠ 表里每一条都要在 docs/android/THIRD_PARTY_RUNTIME_MATRIX.md 落一行；加/删前缀必须同时改那份矩阵。
# "期望 0"的那几条是反证位：它们一旦非 0，就是矩阵把"参考过"写成了"内置"。
PREFIXES = [
    ("androidx.core:core-ktx", "androidx/core/", None),
    ("androidx.activity:activity-compose", "androidx/activity/", None),
    ("androidx.appcompat:appcompat", "androidx/appcompat/", None),
    ("compose ui（foundation 等共用 androidx/compose）", "androidx/compose/ui/", None),
    ("compose material3", "androidx/compose/material3/", None),
    ("compose material-icons-extended", "androidx/compose/material/icons/", None),
    ("androidx.lifecycle", "androidx/lifecycle/", None),
    ("androidx.navigation:navigation-compose", "androidx/navigation/", None),
    ("androidx.work:work-runtime-ktx", "androidx/work/", None),
    ("kotlinx-coroutines-android", "kotlinx/coroutines/", None),
    ("okhttp3", "okhttp3/", None),
    ("okio（okhttp 传递）", "okio/", None),
    ("coil-compose", "coil/", None),
    ("kotlinx-serialization-core（传递依赖）", "kotlinx/serialization/", None),
    ("com.google.zxing:core", "com/google/zxing/", None),
    ("journeyapps:zxing-android-embedded", "com/journeyapps/", None),
    ("amap navi-3dmap", "com/amap/api/", None),
    ("amap 构件自带的另一棵包树", "com/autonavi/", None),
    ("kotlin-stdlib", "kotlin/", None),
    # 以下**期望 defined=0**：进了 APK 就是矩阵写错了
    ("junit（test only，期望 0）", "org/junit/", None),
    ("org.json 独立 jar（期望 0，框架已提供）", "org/json/", None),
    ("mockwebserver（test only，期望 0）", "okhttp3/mockwebserver/", None),
    ("kotlinx-coroutines-test（test only，期望 0）", "kotlinx/coroutines/test/", None),
    ("graphhopper（服务端，期望 0）", "com/graphhopper/", None),
    ("osmdroid（未内置，期望 0）", "org/osmdroid/", None),
    ("gdal/proj（未内置，期望 0）", "org/gdal/", None),
    ("maplibre（计划复用未引入，期望 0）", "org/maplibre/", None),
    ("springframework（后端，期望 0）", "org/springframework/", None),
]

# APK 里应当出现的署名字面量：⑭ 补最小入口之前实测 0，补完必须非 0（方向由判据簿钉）
STRINGS = [
    "© OpenStreetMap contributors",
    "Open Database License",
    "ODbL",
    "OpenStreetMap",
]


def _uleb128(buf, off):
    r = 0
    shift = 0
    while True:
        b = buf[off]
        off += 1
        r |= (b & 0x7F) << shift
        if not (b & 0x80):
            return r, off
        shift += 7


def parse_dex(data, name):
    """返回 (defined_descriptors, referenced_descriptors)。字段偏移见 DEX 格式头 header_item。"""
    if not re.match(rb"^dex\n0\d\d\0", data[:8]):
        raise ValueError("%s 不是 dex 头：%r" % (name, data[:8]))
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 0x40)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)
    if not (string_ids_size and type_ids_size):
        raise ValueError("%s 头解析出 0（string_ids=%d type_ids=%d）" % (name, string_ids_size, type_ids_size))

    strings = []
    # string_id_item 的宽度不是恒定的：dex 035…037 是 {utf16_size, data_off} 8 字节，
    # 这个 APK 是 dex 038（D8 新版），实测 (type_ids_off - string_ids_off) / string_ids_size = 4，
    # 即尺寸字段被去掉了。所以这里**用两个段的间距反推宽度**，而不是照抄文档里的 8。
    span = type_ids_off - string_ids_off
    width = span // string_ids_size if span % string_ids_size == 0 else 0
    if width not in (4, 8):
        raise ValueError("%s string_id_item 宽度推不出（span=%d size=%d ⇒ %s）" % (name, span, string_ids_size, width))
    for i in range(string_ids_size):
        if width == 8:
            data_off = struct.unpack_from("<II", data, string_ids_off + 8 * i)[1]
        else:
            data_off = struct.unpack_from("<I", data, string_ids_off + 4 * i)[0]
        _, s = _uleb128(data, data_off)                      # 跳过 uleb128 的 utf16_size
        end = data.index(b"\x00", s)
        strings.append(data[s:end].decode("utf-8", "replace"))
    types = [strings[struct.unpack_from("<I", data, type_ids_off + 4 * i)[0]] for i in range(type_ids_size)]
    defined = set()
    for i in range(class_defs_size):
        defined.add(types[struct.unpack_from("<I", data, class_defs_off + 32 * i)[0]])
    return defined, types


def main():
    # Windows 控制台默认 GBK：输出里的 `©` 会直接把脚本砸成 UnicodeEncodeError，
    # 退出码 1 会被调用方读成"反证位红了"，而真相是"这一行印不出来"（2026-10-01 实测）。
    # 所以读数一律按 utf-8 打，让调用方的 grep 拿到确定字节。
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    if len(sys.argv) < 2:
        print("用法：python tools/apk_oss_census.py <apk 路径> [--strings] [--families]")
        return 2
    path = sys.argv[1]
    try:
        apk = zipfile.ZipFile(path)
    except Exception as e:
        print("EXIT2 打不开 APK（不许把'打不开'读成'里面没有'）：%s : %s" % (path, e))
        return 2
    names = [n for n in apk.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
    if not names:
        print("EXIT2 APK 里没有 classes*.dex：%s（条目共 %d 个）" % (path, len(apk.namelist())))
        return 2

    defined = set()
    referenced = []
    for n in sorted(names):
        try:
            d, t = parse_dex(apk.read(n), n)
        except Exception as e:
            print("EXIT2 dex 头解析失败：%s" % e)
            return 2
        defined |= d
        referenced += t
    print("APK = %s" % path)
    print("APK 字节数 = %d  dex 文件数 = %d  打包类(defined) = %d  引用类型(referenced) = %d（去重 %d）"
          % (os.path.getsize(path), len(names), len(defined), len(referenced), len(set(referenced))))

    def count(pfx, pool):
        return sum(1 for d in pool if d[1:].startswith(pfx) or d[1:] == pfx.rstrip("/"))

    bad = 0
    print("PREFIX|name|prefix|defined|referenced|verdict")
    for label, pfx, alt in PREFIXES:
        prefixes = [pfx] + ([alt] if alt else [])
        dn = sum(count(p, defined) for p in prefixes)
        rf = sum(count(p, referenced) for p in prefixes)
        verdict = "INCLUDED_IN_APP" if dn else "NOT_IN_APK"
        # 反证位靠 label 里的"期望 0"来认，不靠硬编码包名清单：加了新前缀忘了更新清单，
        # 那个"期望 0"就永远不会红（2026-10-01 加 kotlinx/coroutines/test 时差点踩中）。
        if "期望 0" in label and dn:
            print("× 反证位 %s 打包类不是 0（实测 %d）" % (label, dn))
            bad = 1
        print("PREFIX|%s|%s|%d|%d|%s" % (label, pfx, dn, rf, verdict))

    if "--families" in sys.argv:
        fam = Counter("/".join(d[1:].split("/")[:3]) for d in defined if "/" in d)
        print("FAMILIES（打包类的前三段包族，全部列出，共 %d 族）" % len(fam))
        for k, v in sorted(fam.items(), key=lambda kv: -kv[1]):
            print("  %-52s %d" % (k, v))

    if "--strings" in sys.argv:
        hay = b"".join(apk.read(n) for n in sorted(names))
        res = apk.read("resources.arsc") if "resources.arsc" in apk.namelist() else b""
        print("STRING|needle|dex|resources.arsc")
        for s in STRINGS:
            print("STRING|%s|%d|%d" % (s, hay.count(s.encode("utf-8")), res.count(s.encode("utf-8"))))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
