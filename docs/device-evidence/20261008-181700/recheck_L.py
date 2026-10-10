"""#291 改后复拍：同一 x/y 带做像素测量，并与改前基线对照。"""
import os, sys, json
sys.path.insert(0, r"D:\android-dev")
import dwalk20_login as D

tag = sys.argv[1] if len(sys.argv) > 1 else "post"
xml = D.dump(tag)
path, size = D.shot(tag)
img = D.read_png(path)
print("SHOT", path, size, "w", img[0], "h", img[1] if isinstance(img, tuple) else "")
nodes = D.texts(xml)
print("NODES", len(nodes))
for lab, b, clk in nodes:
    print("  %-22s %-34s clk=%s" % (lab[:22], b, clk))
clickable = [n for n in nodes if n[2]]
print("CLICKABLE", len(clickable))
