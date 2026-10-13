#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把启动图标的两份 VectorDrawable 渲成商店用的 1024x1024 位图（PRODUCTIZATION_COMPLETE.md §6.1 那一格）。

为什么要有这个脚本：`mipmap-anydpi-v26/ic_launcher.xml` 只有矢量层，商店后台还要一张**无透明通道**的
1024x1024 位图。本机没有 Android 渲染器，所以这里**直接读同一份 pathData** 按 Android 的口径
（viewport 108、group scale 0.86 / pivot 54、evenOdd 挖洞、strokeWidth + round cap）展平绘制
—— 几何与矢量层同源，不是"另画一个像的"。像素级不保证与系统渲染器完全一致（抗锯齿/渐变插值不同）。

自查是硬闸（不合格就 exit 1，不留"看着差不多"的产物）：
  1. 输出必须是 1024x1024 且 **RGB（无 alpha）**；
  2. 前景任何一点到画心的距离必须落在自适应图标的安全圆 r=33dp 内（越界=会被机型遮罩切掉）；
  3. 风挡那个洞必须真的透（取色是背景渐变，不是白色）——这条证 evenOdd 没被画成"涂成背景色"；
  4. 前景像素数必须 >0（路径展平失败时 PIL 会安静地什么都不画）。

用法：python tools/render_launcher_store_png.py [输出路径]
      默认写 docs/design/ic_launcher_store_1024.png
"""
import math
import os
import re
import sys
from PIL import Image, ImageDraw

SELF = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(SELF)
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
N = 1024
VP = 108.0
S = N / VP
SAFE_R = 33.0 / VP * N   # 自适应图标的安全圆半径（dp→px）
TOKENS = re.compile(r"[MmHhVvLlAaCcSsTtQqZz]|[-+]?\d*\.?\d+(?:e[-+]?\d+)?")


def flatten(d, scale=1.0, pivot=None):
    """pathData → 子路径折线列表。只实现本图标用到的指令：M/L/H/V/A/C/Z + 小写相对形式。"""
    t = TOKENS.findall(d)
    subs, cur = [], []
    p = start = (0.0, 0.0)
    i = 0

    def put(x, y):
        if pivot:
            x = pivot[0] + (x - pivot[0]) * scale
            y = pivot[1] + (y - pivot[1]) * scale
        cur.append((x * S, y * S))

    while i < len(t):
        tok = t[i]
        if not tok[0].isalpha():
            i += 1
            continue
        cmd = tok
        i += 1
        rel = cmd.islower()
        C = cmd.upper()
        if C == "M":
            if cur:
                subs.append(cur)
                cur = []
            x, y = float(t[i]), float(t[i + 1])
            i += 2
            if rel:
                x += p[0]
                y += p[1]
            p = start = (x, y)
            put(*p)
            C = "L"
        if C == "L":
            while i < len(t) and not t[i][0].isalpha():
                x, y = float(t[i]), float(t[i + 1])
                i += 2
                if rel:
                    x += p[0]
                    y += p[1]
                p = (x, y)
                put(*p)
        elif C == "H":
            while i < len(t) and not t[i][0].isalpha():
                x = float(t[i])
                i += 1
                p = (p[0] + x if rel else x, p[1])
                put(*p)
        elif C == "V":
            while i < len(t) and not t[i][0].isalpha():
                y = float(t[i])
                i += 1
                p = (p[0], p[1] + y if rel else y)
                put(*p)
        elif C == "C":
            while i + 5 < len(t) and not t[i][0].isalpha():
                c1 = [float(t[i]), float(t[i + 1])]
                c2 = [float(t[i + 2]), float(t[i + 3])]
                en = [float(t[i + 4]), float(t[i + 5])]
                i += 6
                if rel:
                    for arr in (c1, c2, en):
                        arr[0] += p[0]
                        arr[1] += p[1]
                x0, y0 = p
                for k in range(1, 25):
                    u = k / 24.0
                    m1 = 1 - u
                    put(m1 ** 3 * x0 + 3 * m1 * m1 * u * c1[0] + 3 * m1 * u * u * c2[0] + u ** 3 * en[0],
                        m1 ** 3 * y0 + 3 * m1 * m1 * u * c1[1] + 3 * m1 * u * u * c2[1] + u ** 3 * en[1])
                p = (en[0], en[1])
        elif C == "A":
            while i + 6 < len(t) and not t[i][0].isalpha():
                rx, ry = abs(float(t[i])), abs(float(t[i + 1]))
                rot = math.radians(float(t[i + 2]))
                laf, sf = int(float(t[i + 3])), int(float(t[i + 4]))
                x, y = float(t[i + 5]), float(t[i + 6])
                i += 7
                if rel:
                    x += p[0]
                    y += p[1]
                x0, y0 = p
                if not rx or not ry or (x0, y0) == (x, y):
                    put(x, y)
                    p = (x, y)
                    continue
                cosr, sinr = math.cos(rot), math.sin(rot)
                x1p = cosr * (x0 - x) / 2 + sinr * (y0 - y) / 2
                y1p = -sinr * (x0 - x) / 2 + cosr * (y0 - y) / 2
                lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
                if lam > 1:
                    rx *= math.sqrt(lam)
                    ry *= math.sqrt(lam)
                num = max(rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2, 0.0)
                den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
                co = (math.sqrt(num / den) if den else 0.0) * (-1 if laf == sf else 1)
                cx = cosr * (co * rx * y1p / ry) - sinr * (-co * ry * x1p / rx) + (x0 + x) / 2
                cy = sinr * (co * rx * y1p / ry) + cosr * (-co * ry * x1p / rx) + (y0 + y) / 2
                ang = lambda ux, uy: math.atan2(uy / ry, ux / rx)
                th1 = ang(x0 - cx, y0 - cy)
                dth = ang(x - cx, y - cy) - th1
                if sf == 0 and dth > 0:
                    dth -= 2 * math.pi
                if sf == 1 and dth < 0:
                    dth += 2 * math.pi
                steps = max(8, int(abs(dth) / (math.pi / 36)))
                for k in range(1, steps + 1):
                    a = th1 + dth * k / steps
                    put(cosr * rx * math.cos(a) - sinr * ry * math.sin(a) + cx,
                        sinr * rx * math.cos(a) + cosr * ry * math.sin(a) + cy)
                p = (x, y)
        elif C == "Z":
            put(*start)
            if cur:
                subs.append(cur)
                cur = []
            p = start
    if cur:
        subs.append(cur)
    return subs


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "design", "ic_launcher_store_1024.png")
    fg = open(os.path.join(RES, "ic_launcher_foreground.xml"), encoding="utf-8").read()
    bg = open(os.path.join(RES, "ic_launcher_background.xml"), encoding="utf-8").read()
    paths = re.findall(r"<path[^>]*?/?>", fg, re.S)
    fills = [p for p in paths if "fillColor" in p and "strokeColor" not in p]
    strokes = [p for p in paths if "strokeColor" in p]
    if not (fills and strokes):
        print("× 前景层没读到 fill/stroke 路径（XML 形状变了，本脚本的口径不再成立）")
        return 1
    data_of = lambda p: re.search(r'pathData="([^"]*)"', p).group(1)
    # 缩放与轴心从 group 上读，不写死：矢量层一改，这里就跟着改，否则位图与矢量层会静默分叉
    g = re.search(r"<group[^>]*?>", fg, re.S)
    if not g:
        print("× 前景层没有 <group>（缩放口径变了，脚本要跟着改）")
        return 1
    attr = lambda k: float(re.search(r'android:%s="([-.\d]+)"' % k, g.group(0)).group(1))
    SCALE, PIV = attr("scaleX"), (attr("pivotX"), attr("pivotY"))
    if abs(attr("scaleY") - SCALE) > 1e-6:
        print("× scaleX 与 scaleY 不相等，本脚本按等比缩放写死了")
        return 1

    m = re.search(r'startColor="#FF([0-9A-Fa-f]{6})"[\s\S]*?endColor="#FF([0-9A-Fa-f]{6})"', bg)
    if not m:
        print("× 背景层不是一份 startColor/endColor 线性渐变，脚本要跟着改")
        return 1
    rgb = lambda hx: tuple(int(hx[i:i + 2], 16) for i in (0, 2, 4))
    c0, c1 = rgb(m.group(1)), rgb(m.group(2))
    img = Image.new("RGB", (N, N))
    for y in range(N):
        f = y / (N - 1)
        img.paste(tuple(round(c0[k] + (c1[k] - c0[k]) * f) for k in range(3)), (0, y, N, y + 1))

    ov = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(ov)
    for p in fills:
        subs = flatten(data_of(p), SCALE, PIV)
        evenodd = 'fillType="evenOdd"' in p
        for idx, sp in enumerate(subs):
            if len(sp) < 3:
                continue
            d.polygon(sp, fill=0 if (evenodd and idx > 0) else 255)
    for p in strokes:
        sw = float(re.search(r'strokeWidth="([\d.]+)"', p).group(1)) * S
        for sp in flatten(data_of(p), SCALE, PIV):
            d.line(sp, fill=255, width=max(1, int(round(sw))), joint="curve")
            for (x, y) in (sp[0], sp[-1]):
                d.ellipse([x - sw / 2, y - sw / 2, x + sw / 2, y + sw / 2], fill=255)
    img = Image.composite(Image.new("RGB", (N, N), (255, 255, 255)), img, ov)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)

    # ---- 四条硬闸 ----
    bad = []
    chk = Image.open(out)
    if chk.size != (N, N) or chk.mode != "RGB":
        bad.append("尺寸/模式 = %s %s（要 1024x1024 RGB 无 alpha）" % (chk.size, chk.mode))
    px = chk.load()
    pts = [(x, y) for y in range(0, N, 2) for x in range(0, N, 2) if min(px[x, y]) > 240]
    area = len(pts) * 4.0 / (N * N)
    if area < 0.02:
        bad.append("前景只占画面 %.2f%%（下限 2%%）⇒ 路径展平失败或 pathData 退化，产物不是那张标" % (area * 100))
    rmax = max(math.hypot(x - N / 2.0, y - N / 2.0) for x, y in pts) if pts else 0.0
    if rmax > SAFE_R:
        bad.append("前景最远点 %.1fpx 越出安全圆 r=33dp=%.1fpx ⇒ 会被机型遮罩切掉" % (rmax, SAFE_R))
    wx, wy = PIV[0] * S, (PIV[1] + (44.0 - PIV[1]) * SCALE) * S
    hole = px[int(wx), int(wy)]
    if min(hole) >= 200:
        bad.append("风挡中心取色 %s 是白的 ⇒ evenOdd 那个洞没挖开（monochrome 层会跟着错）" % (hole,))
    print("产物=%s 尺寸=%s 模式=%s 前景采样=%d 最远半径=%.1fpx(安全圆 %.1f) 风挡取色=%s 字节=%d" % (
        os.path.relpath(out, ROOT).replace("\\", "/"), chk.size, chk.mode, len(pts), rmax, SAFE_R, hole,
        os.path.getsize(out)))
    for b in bad:
        print("× " + b)
    if bad:
        return 1
    print("RENDER=OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
