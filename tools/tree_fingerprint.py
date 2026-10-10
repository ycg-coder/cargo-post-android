#!/usr/bin/env python3
"""TREEV2 整树指纹：app/src + tools 两棵树，逐文件 path\\0字节数\\0sha256，按路径排序后合摘要。

排除 build/ 与 .gradle/。D-061 只在台账里留了口径与终值、没把命令本身归档，
所以那一串 5647d505… 无法再被任何人事后复算 —— 本脚本就是那个归档。
"""
import hashlib
import os
import sys

ROOTS = ("app/src", "tools")
EXCLUDE_DIRS = {"build", ".gradle"}


def entries(root_dir):
    out = []
    for base in ROOTS:
        top = os.path.join(root_dir, base)
        for dp, dn, fn in os.walk(top):
            dn[:] = [d for d in dn if d not in EXCLUDE_DIRS]
            for f in fn:
                p = os.path.join(dp, f)
                rel = os.path.relpath(p, root_dir).replace("\\", "/")
                with open(p, "rb") as fh:
                    b = fh.read()
                out.append(rel.encode("utf-8") + b"\0" + str(len(b)).encode() + b"\0"
                           + hashlib.sha256(b).hexdigest().encode() + b"\n")
    return sorted(out)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    ents = entries(root)
    digest = hashlib.sha256(b"".join(ents)).hexdigest()
    print("TREEV2 files=%d sha256=%s" % (len(ents), digest))
    return 0


if __name__ == "__main__":
    sys.exit(main())
