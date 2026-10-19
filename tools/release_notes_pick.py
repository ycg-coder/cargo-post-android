"""从 RELEASE_NOTES.md 里取出**本次发布那一个 tag** 的更新公告，写成 GitHub Release 的 notes。

为什么要有这么一支脚本：App 里那两处升级弹窗显示的公告，来源就是 GitHub Release 的正文；
而正文以前是 CI 里写死的一句"自动发布：本次运行 NNN"—— 于是"更新公告"这一格永远没有内容可读。
现在公告的原件放在仓库里，发版时按 tag 取，取不到**不编**：落一份明说没写的占位并退 1，
让 CI 打一条 warning、发布照走。宁可空着，也不许把上一版的公告顶到这一版上。

用法：
    python3 tools/release_notes_pick.py --tag v214 --file RELEASE_NOTES.md --out .release-notes.md
退出码：
    0 命中该 tag，正文已写出
    1 没命中（或那一节是空的）—— 写出的是"这一版没写公告"的占位，发布可以继续
    2 用法/文件本身不对（文件不存在、读不出来）—— 不写任何输出，让 CI 红
"""
import argparse
import io
import re
import sys

# 公告后面固定补的那一句：说的是"装完之后在哪儿能自己更新"，与改了什么无关，所以每版都有。
TAIL = "\n\n装了这个包之后，App 里「设置 → 关于 → 检查更新」就能就地更新。"

MISSING = "这一版没有写更新公告。\n\n仓库里 RELEASE_NOTES.md 按版本记公告，发这一版时没人给它写正文。"


def headings(text):
    """返回 [(行号, 标题原文)]，只认二级标题（一级是文件说明，三级是节内小标题）。"""
    out = []
    for i, line in enumerate(text.splitlines()):
        if re.match(r"^##\s", line):
            out.append((i, line[2:].strip()))
    return out


def section_for(text, tag):
    """取标题里出现这个 tag 的那一节的正文；没有就返回 None。

    tag 要按**整词**认：`v21` 不许命中 `v214`（标题里常见 `## v214 · v215` 这种一节多版）。
    """
    hs = headings(text)
    lines = text.splitlines()
    # 词边界用"不是数字字母"来划：tag 里带点号时 \b 的行为不直观，这里要的是"这一段就是个版本号"
    want = re.compile(r"(?<![0-9A-Za-z])" + re.escape(tag) + r"(?![0-9A-Za-z])")
    for idx, (start, title) in enumerate(hs):
        if not want.search(title):
            continue
        end = hs[idx + 1][0] if idx + 1 < len(hs) else len(lines)
        body = "\n".join(lines[start + 1:end]).strip()
        return body
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="本次发布的 tag，例如 v214")
    ap.add_argument("--file", default="RELEASE_NOTES.md")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    try:
        with io.open(a.file, "r", encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        sys.stderr.write("CANNOT-READ file=%s err=%s\n" % (a.file, e))
        return 2

    body = section_for(text, a.tag)
    if body:
        with io.open(a.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(body + TAIL + "\n")
        # 这一行刻意只用 ASCII：本机 Git Bash 的 stdout 编码不是 UTF-8 时，
        # 打印中文会抛 UnicodeEncodeError —— 文件已经写对了，退出码却变成"没命中"，白丢一格证据。
        print("HIT tag=%s chars=%d" % (a.tag, len(body)))
        return 0

    # 没命中 —— 占位话要说清"是没人写"，不能写成"没有更新"（那是另一件事）
    with io.open(a.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(MISSING + TAIL + "\n")
    print("MISS tag=%s no-section-in=%s -> placeholder written" % (a.tag, a.file))
    return 1


if __name__ == "__main__":
    sys.exit(main())
