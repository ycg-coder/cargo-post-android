"""#290 验证码回执出口实测（第二版）：不再盲发 BACK——BACK 在 IME 没弹出时会把 App 退出到桌面。"""
import sys, time, re
sys.path.insert(0, r"D:\android-dev")
import dwalk20_login as D

LOG = open(r"D:\android-dev\dev\notice-probe2.log", "w", encoding="utf-8")


def say(*a):
    msg = " ".join(str(x) for x in a)
    LOG.write(msg + "\n")
    LOG.flush()


def tap(cx, cy, why):
    D.dev("shell", "input", "tap", str(cx), str(cy))
    say("TAP %s @(%d,%d)" % (why, cx, cy))
    time.sleep(0.9)


def find(xml, label):
    for lab, b, clk in D.texts(xml):
        if lab == label:
            v = [int(t) for t in re.findall(r"\d+", b)]
            return ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2, b, v)
    return None


def ime_shown():
    return "mInputShown=true" in D.dev("shell", "dumpsys", "input_method")


def labels(xml):
    return [l for l, _, _ in D.texts(xml)]


D.dev("shell", "am", "force-stop", D.PKG)
D.dev("shell", "am", "start", "-n", D.PKG + "/.MainActivity")
for i in range(20):
    time.sleep(1.0)
    xml = D.dump("m%d" % i)
    if find(xml, "验证码登录"):
        say("登录页就绪（等了 %d 秒）" % (i + 1))
        break
else:
    say("!! 20 秒没等到登录页"); raise SystemExit(3)

x, y, b, _ = find(xml, "验证码登录")
tap(x, y, "切到验证码登录")
xml = D.dump("m1")
say("切换后标签:", labels(xml))

# 手机号输入框：标签在第一行、正文在第二行，点在标签那一行不会聚焦（上一版就是这么错的）
ph = find(xml, "请输入手机号")
if not ph:
    say("!! 没有手机号正文节点"); raise SystemExit(3)
tap(ph[0], ph[1], "手机号正文")
say("聚焦后 IME =", ime_shown())
D.dev("shell", "input", "text", "13800000001")
time.sleep(0.8)
say("输入后 IME =", ime_shown())
xml = D.dump("m2")
say("输入后标签:", labels(xml))

if ime_shown():
    D.dev("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.8)
    say("收键盘后 IME =", ime_shown())
else:
    say("没弹 IME，不发 BACK（发出去就是退出 App）")

xml = D.dump("m3")
say("收键盘后标签:", labels(xml))
g = find(xml, "获取")
say("获取 node =", g[2] if g else None)
if g:
    tap(g[0], g[1], "获取验证码")
    for k in (1, 2, 4):
        time.sleep(k)
        xml = D.dump("m4")
        say("第 %s 秒标签:" % k, labels(xml))
        if any("验证码已发送" in l or "发送失败" in l for l in labels(xml)):
            break
    open(r"D:\android-dev\dev\notice-xml.txt", "w", encoding="utf-8").write(xml)
    for l, b, c in D.texts(xml):
        say("   |%-26s| %s clk=%s" % (l[:26], b, c))
D.shot("notice")
say("done")
