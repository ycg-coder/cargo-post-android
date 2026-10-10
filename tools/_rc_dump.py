import io, os, re, sys

ROOT = r"C:\Users\袁\Desktop\测试\cargo-post-android\app\src\main\java"

def kt_files(root):
    for d, _, fs in os.walk(root):
        for f in sorted(fs):
            if f.endswith(".kt"):
                yield os.path.join(d, f)

def read(p):
    return io.open(p, encoding="utf-8", newline="").read()

# 1) every identifier declared as a suspend function (any modifier order, extensions, generics)
sus = set()
decl_lines = 0
for p in kt_files(ROOT):
    for ln in read(p).split("\n"):
        if re.search(r'\bsuspend\b', ln) and re.search(r'\bfun\b', ln):
            decl_lines += 1
            m = re.search(r'\bfun\b\s*(?:<[^>]*>\s*)?(?:[\w<>,?.\s]+\.)?(\w+)', ln)
            if m:
                sus.add(m.group(1))
        # suspend-typed lambda parameters: block: suspend (...) -> R  => invoking that param suspends
        for m in re.finditer(r'(\w+)\s*:\s*suspend\b', ln):
            sus.add(m.group(1))
print("SUSPEND_DECL_LINES=%d  NAMES=%d" % (decl_lines, len(sus)))

# 2) stdlib / coroutine suspend entry points that are not declared in this project
KNOWN = {"delay", "withContext", "withTimeout", "withTimeoutOrNull", "coroutineScope",
         "supervisorScope", "await", "awaitAll", "awaitOrNull", "join", "joinAll",
         "next", "shot", "receive", "send", "collect", "collectLatest", "single",
         "first", "suspendCancellableCoroutine", "getCancellationException", "yield"}

def strip_lit_comment(s):
    s = re.sub(r'"(?:[^"\\]|\\.)*"', '""', s)
    s = re.sub(r"'(?:[^'\\]|\\.)*'", "''", s)
    j = s.find("//")
    if j >= 0:
        s = s[:j]
    return s

def block_span(lines, i):
    depth, seen = 0, False
    for k in range(i, min(i + 500, len(lines))):
        for ch in strip_lit_comment(lines[k]):
            if ch == "{":
                depth += 1
                seen = True
            elif ch == "}":
                depth -= 1
                if seen and depth == 0:
                    return i, k
    return i, -1

rows = []
for p in kt_files(ROOT):
    lines = read(p).split("\n")
    for i, ln in enumerate(lines):
        t = ln.strip()
        if "runCatching" not in t:
            continue
        if t.startswith("//") or t.startswith("*") or t.startswith("/*"):
            continue
        a, b = block_span(lines, i)
        body = "\n".join(strip_lit_comment(x) for x in lines[a:b + 1])
        calls = set(re.findall(r'([A-Za-z_]\w*)\s*[\(\{\.]', body))
        proj = sorted(calls & sus)
        kn = sorted(calls & KNOWN)
        # enclosing: walk back for the nearest suspend fun / coroutine builder
        enc_susp = ""
        for k in range(i, -1, -1):
            s = lines[k]
            if re.search(r'\bfun\b', s) and re.search(r'\bsuspend\b', s):
                m = re.search(r'\bfun\b\s*(?:<[^>]*>\s*)?(?:[\w<>,?.\s]+\.)?(\w+)', s)
                enc_susp = "suspend fun " + (m.group(1) if m else "?")
                break
            if re.search(r'\b(LaunchedEffect|launch|async|withContext|collect|flow)\b\s*[\(\{]', s):
                enc_susp = "coroutine-context @" + str(k + 1) + ": " + s.strip()[:60]
                break
        rows.append((os.path.relpath(p, ROOT).replace("\\", "/"), i + 1, ";".join(proj), ";".join(kn), enc_susp))

print("ACTIVE=%d FILES=%d" % (len(rows), len(set(r[0] for r in rows))))
bad = [r for r in rows if r[2] or r[3]]
print("SITES_WITH_SUSPEND_IN_BLOCK=%d" % len(bad))
for f, n, proj, kn, enc in rows:
    print("%s:%d | in_block: proj=[%s] known=[%s] | %s" % (f, n, proj, kn, enc))
