"""#291 改前/改后同一 x/y 带的像素复测，读数落 contrast-L.log（stdout 是 GBK，中文不能直接打印）。

三格各自的量法都写死在这里，免得下次又要靠现场猜：
  边界   —— 沿 y=1342（手机号那一行）横扫 x=30..114，报每段的 (起点,宽度,颜色)，
            field 填充与 paper 底色的对比用 ratio() 直接算；
  禁用态 —— 登录按钮未勾选协议时是 disabled：板色取标签左侧 110px 的众数（避开字形），
            字色在标签 bounds 内取"最亮/最暗两个候选里离板色更远的那个"，两者一起进 ratio()；
  孤链   —— 只报 bounds 与中心 x，dp 由 1dp=2.625px（density 420）换算。
"""
import sys
from collections import Counter

sys.path.insert(0, r"D:\android-dev")
import dwalk20_login as D

PRE, POST = "screen-L-pre.png", "screen-L-post.png"
LOG = "contrast-L.log"
out = []


def say(*a):
    out.append(" ".join(str(x) for x in a))


def runlog(img, x0, x1, y):
    """横向扫一行，把连续同色像素并成段：段起点、段宽、颜色。"""
    segs = []
    cur = D.px(img, x0, y)
    start = x0
    for x in range(x0 + 1, x1 + 1):
        c = D.px(img, x, y)
        if c != cur:
            segs.append((start, x - start, cur))
            cur, start = c, x
    segs.append((start, x1 + 1 - start, cur))
    return segs


def mode_color(img, x0, x1, y0, y1):
    c = Counter(D.px(img, x, y) for x in range(x0, x1) for y in range(y0, y1))
    col, n = c.most_common(1)[0]
    return col, n, (x1 - x0) * (y1 - y0)


def darkest(img, x0, x1, y0, y1):
    best = None
    for x in range(x0, x1):
        for y in range(y0, y1):
            p = D.px(img, x, y)
            if best is None or D.lum(p) < D.lum(best):
                best = p
    return best


def brightest(img, x0, x1, y0, y1):
    best = None
    for x in range(x0, x1):
        for y in range(y0, y1):
            p = D.px(img, x, y)
            if best is None or D.lum(p) > D.lum(best):
                best = p
    return best


imgs = {k: D.read_png("D:/android-dev/dev/" + v) for k, v in (("pre", PRE), ("post", POST))}
for k, v in (("pre", PRE), ("post", POST)):
    w, h, ch, _ = imgs[k]
    say("IMG %s %s %dx%d" % (k, v, w, h))

# ① 输入框可视边界：同一条 y=1342 横带
say("== 边界 y=1342 ==")
for k in ("pre", "post"):
    segs = runlog(imgs[k], 30, 114, 1342)
    say("  %s: %s" % (k, segs))
    paper = D.px(imgs[k], 30, 1342)
    fill = D.px(imgs[k], 70, 1342)
    say("     paper=%s field=%s ratio=%.2f" % (paper, fill, D.ratio(paper, fill)))
    # 只认「左边框那一条」：从 x=53（20dp 边距处）起、既不是底色也不是填充色的第一段。
    # 早先用"非底非填充"粗筛会把 x≈90 那几列字形边缘也算进来，读数就不干净了。
    line = next(((s, n, c) for (s, n, c) in segs if s >= 53 and c not in (paper, fill)), None)
    say("     边框段：%s" % ((line if line else "无（改前就是没有这一条）"),))

# ② 禁用按钮：板色 / 字色 / 对比
# ⚠ 改前那一版是"白字压淡蓝板"，字比板**亮** ⇒ 只取最暗像素会量到板、ratio 恒 1.00（第一版就栽在这）。
#    正确读法：标签 bounds 内取最亮与最暗各一个，谁离板色远谁就是字色，两个 ratio 一起报出来。
say("== 禁用「登录」按钮 ==")
box = {"pre": (1958, 2021), "post": (2006, 2069)}
for k, (y0, y1) in box.items():
    board, n, tot = mode_color(imgs[k], 380, 490, y0 - 30, y1 + 30)
    li, dk = brightest(imgs[k], 497, 585, y0, y1), darkest(imgs[k], 497, 585, y0, y1)
    say("  %s label y=[%d,%d] 板=%s(众数 %d/%d)" % (k, y0, y1, board, n, tot))
    say("     最亮=%s ratio=%.2f / 最暗=%s ratio=%.2f ⇒ 字取 %s，字与板 %.2f:1" %
        (li, D.ratio(board, li), dk, D.ratio(board, dk),
         (li if D.ratio(board, li) >= D.ratio(board, dk) else dk),
         max(D.ratio(board, li), D.ratio(board, dk))))

# ③ 切换登录方式那一格：节点 bounds 与中心
say("== 孤链「验证码登录」==")
for k, f in (("pre", "ui-L-pre.xml"), ("post", "ui-L-post.xml")):
    for lab, bs, clk in D.texts(open("D:/android-dev/dev/" + f, encoding="utf-8", errors="replace").read()):
        if lab != "验证码登录":
            continue
        x1, y1, x2, y2 = [int(v) for v in bs.replace("][", ",").strip("[]").split(",")]
        say("  %s bounds=%s w=%.1fdp h=%.1fdp 中心x=%d 屏宽/2=%d clk=%s" %
            (k, bs, (x2 - x1) / 2.625, (y2 - y1) / 2.625, (x1 + x2) // 2, 1080 // 2, clk))

open("D:/android-dev/dev/" + LOG, "w", encoding="utf-8", newline="").write("\n".join(out) + "\n")
print("WROTE", LOG, len(out), "lines")
