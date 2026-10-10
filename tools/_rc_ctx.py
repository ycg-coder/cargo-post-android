import io, os, re

ROOT = r"C:\Users\袁\Desktop\测试\cargo-post-android\app\src\main\java"
out = []
for d, _, fs in os.walk(ROOT):
    for f in sorted(fs):
        if not f.endswith(".kt"):
            continue
        p = os.path.join(d, f)
        lines = io.open(p, encoding="utf-8", newline="").read().split("\n")
        hits = [i for i, ln in enumerate(lines)
                if "runCatching" in ln and not ln.strip().startswith(("//", "*", "/*"))]
        if not hits:
            continue
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        out.append("\n\n######## " + rel)
        for i in hits:
            lo = max(0, i - 26)
            out.append("\n----- site L%d -----" % (i + 1))
            for k in range(lo, min(len(lines), i + 12)):
                mark = ">>" if k == i else "  "
                out.append("%s %5d| %s" % (mark, k + 1, lines[k]))
io.open(r"C:\Users\袁\AppData\Local\Temp\rc_context.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("sites=%d lines=%d" % (sum(1 for x in out if x.startswith("----- site")), len("\n".join(out).split("\n"))))
