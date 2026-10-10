"""#290 的**另一半**：notice 是"办成了"，error 是"这次没办成"。上一版取证只拍到 notice 那一路，
`error = "先填写 11 位手机号"` 这一支没留档就差点被写进台账当成已测 —— 这一支补上它。
不发 BACK（IME 没弹时 BACK 会把 App 退到桌面，那是 notice_probe.py 第一版的错法）。"""
import sys, time
sys.path.insert(0, r"D:\android-dev")
import dwalk20_login as D

LOG = open(r"D:\android-dev\dev\error-probe.log", "w", encoding="utf-8", newline="")


def say(*a):
    LOG.write(" ".join(str(x) for x in a) + "\n")
    LOG.flush()


def labels(xml):
    return [lab for lab, b, clk in D.texts(xml)]


def find(xml, label):
    for lab, b, clk in D.texts(xml):
        if lab == label:
            v = [int(t) for t in b.strip("[]").replace("][", ",").split(",")]
            return ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2, b)
    return None


xml = D.dump("e0")
if "手机号" not in labels(xml):
    D.dev("shell", "monkey", "-p", "com.shanxiang.buspost", "-c", "android.intent.category.LAUNCHER", "1")
    for _ in range(20):
        time.sleep(1.0)
        xml = D.dump("e0")
        if "手机号" in labels(xml):
            break
say("起手标签:", labels(xml))

sw = find(xml, "验证码登录")
if sw:
    D.dev("shell", "input", "tap", str(sw[0]), str(sw[1]))
    say("TAP 切到验证码登录 @(%d,%d)" % (sw[0], sw[1]))
    time.sleep(1.0)
    xml = D.dump("e1")

get = find(xml, "获取")
if not get:
    say("!! 没有「获取」这一格"); raise SystemExit(3)
D.dev("shell", "input", "tap", str(get[0]), str(get[1]))
say("TAP 空手机号直接按获取 @(%d,%d)" % (get[0], get[1]))
time.sleep(1.2)
xml = D.dump("e2")
labs = labels(xml)
say("按完之后标签:", labs)
err = find(xml, "先填写 11 位手机号")
say("error 分支读数:", err)
say("notice 读数（不该出现）:", [x for x in labs if "验证码已发送" in x])
D.shot("e2")
LOG.close()
print("done")
