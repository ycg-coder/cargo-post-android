import io, os, re

ROOT = r"C:\Users\袁\Desktop\测试\cargo-post-android\app\src\main\java"

def strip_lit_comment(s):
    s = re.sub(r'"(?:[^"\\]|\\.)*"', '""', s)
    s = re.sub(r"'(?:[^'\\]|\\.)*'", "''", s)
    j = s.find("//")
    if j >= 0:
        s = s[:j]
    return s

def block_end(lines, i):
    depth, seen = 0, False
    for k in range(i, min(i + 500, len(lines))):
        for ch in strip_lit_comment(lines[k]):
            if ch == "{":
                depth += 1; seen = True
            elif ch == "}":
                depth -= 1
                if seen and depth == 0:
                    return k
    return -1

for d, _, fs in os.walk(ROOT):
    for f in sorted(fs):
        if not f.endswith(".kt"):
            continue
        p = os.path.join(d, f)
        lines = io.open(p, encoding="utf-8", newline="").read().split("\n")
        hits = []
        for i, ln in enumerate(lines):
            t = ln.strip()
            if "runCatching" not in t or t.startswith(("//", "*", "/*")):
                continue
            hits.append(i)
        if not hits:
            continue
        print("\n#### " + os.path.relpath(p, ROOT).replace("\\", "/"))
        for i in hits:
            e = block_end(lines, i)
            tail = " / ".join(x.strip() for x in lines[e:e + 3] if x.strip()) if e >= 0 else "?"
            print("  L%-4d %s" % (i + 1, lines[i].strip()[:70]))
            print("        end L%-4d %s" % (e + 1, tail[:120]))
