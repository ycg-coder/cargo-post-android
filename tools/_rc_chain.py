import io, os, re

ROOT = r"C:\Users\袁\Desktop\测试\cargo-post-android\app\src\main\java"

def strip_lit_comment(s):
    s = re.sub(r'"(?:[^"\\]|\\.)*"', '""', s)
    s = re.sub(r"'(?:[^'\\]|\\.)*'", "''", s)
    j = s.find("//")
    if j >= 0:
        s = s[:j]
    return s

BOUND = re.compile(r'\b(fun|LaunchedEffect|DisposableEffect|callbackFlow|flowOf|Runnable|onClick|onDismiss|object|launch|async|withContext|snapshotFlow|CompositionLocalProvider|Column|Row|Box|Button|Scaffold|let|run|use|forEach|mapNotNull|onFailure|getOrDefault|class)\b')

for d, _, fs in os.walk(ROOT):
    for f in sorted(fs):
        if not f.endswith(".kt"):
            continue
        p = os.path.join(d, f)
        lines = io.open(p, encoding="utf-8", newline="").read().split("\n")
        openers = []  # (idx, depth_after)
        depth = 0
        for i, ln in enumerate(lines):
            code = strip_lit_comment(ln)
            before = depth
            for ch in code:
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
            if depth > before and BOUND.search(code):
                openers.append((i, depth))
            elif depth > before:
                openers.append((i, depth))
        site_idx = [i for i, ln in enumerate(lines)
                    if "runCatching" in ln and not ln.strip().startswith(("//", "*", "/*"))]
        if not site_idx:
            continue
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        print("\n#### " + rel)
        for si in site_idx:
            sd = depth_at = None
            dd = 0
            for i in range(si + 1):
                code = strip_lit_comment(lines[i])
                dd += code.count("{") - code.count("}")
                if i == si:
                    sd = dd
            chain = []
            for idx, da in openers:
                if idx >= si:
                    break
                if da <= sd:
                    chain.append((idx, da))
            # keep innermost opener per depth level
            by_depth = {}
            for idx, da in chain:
                by_depth[da] = idx
            picks = [by_depth[k] for k in sorted(by_depth, reverse=True)][:7]
            print("  L%d (depth %s)" % (si + 1, sd))
            for idx in picks:
                s = lines[idx].strip()
                tag = "SUSPEND" if re.search(r'\bsuspend\b', s) else ""
                print("      @%-4d d=%d %s%s" % (idx + 1, by_depth[[k for k, v in by_depth.items() if v == idx][0]], tag, s[:95]))
