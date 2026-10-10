#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
runCatching 取消形状检测器（F-2 ⑨，配套 tools/check_runcatching_probes.sh 的 RC-b）。

要钉的契约：`runCatching` 捕获 Throwable，其中包含 CancellationException —— 一旦块体里出现
挂起调用，"页面已离开 / Worker 已停 / 外层超时"就会被收进 Result，被下游读成一次普通失败
（null、Result.success()、往已离场的 StateFlow 上写）。本项目里这类点必须为 0。

为什么不能只数 `grep -c runCatching`：在册 30 点里绝大多数块体是纯同步的（JSON 解析、
SDK 句柄、Handler/Intent），它们合法且必要；把 30 个删光才得到 0 属于"为了 0 而误删合法
runCatching"，指令明确禁止。所以这里做**闭包判定**：
  1) 从扫描根收全部 `suspend fun` 名（含 `x: suspend (...) -> T` 形参名），记录其声明所在
     的 object/class；再加一组无歧义的协程/stdlib 挂起入口（delay/withContext/await/join/…）。
  2) 对每个 runCatching 的**块体**（`runCatching` 之后第一个 `{` 到配对 `}`；字符串内容与行注释
     先按等长空格抹掉，所以花括号配对与列偏移都不会被字面量里的花括号骗到）取调用名。
     注意块体从 `{` 之后开始 ⇒ 同行前缀（`get() = runCatching`、`main.post { runCatching`）
     不会被当成块内调用，这正是朴素名字交集曾经误报的两处。
  3) 裸名命中挂起集 ⇒ VIOLATION；`Recv.name(` 且 Recv 末段等于该挂起函数的声明者 ⇒ VIOLATION；
     `Recv.name(` 而 Recv 不匹配 ⇒ CANDIDATE（只打数，人工复核）。实测依据：`main.post(...)`
     是 Handler、`sp.getString(...)` 是 SharedPreferences，而 `Api.get(...)` 才是真挂起。
  4) `first/single/last` 这类 Flow 与 List 同名的终端操作符只进 CANDIDATE，不进 VIOLATION。

输出：
  SUMMARY|sites=N|violations=N|candidates=N|suspend_names=N|suspend_decl_lines=N|run_blocking=N|scc=N
  之后每行一条 VIOLATION/CANDIDATE：文件:行号 命中名
退出码恒 0（判红留给判据簿）；扫描根不存在 ⇒ stderr 报错 + 退出 2。
"""
import io
import os
import re
import sys

KNOWN_SUSPEND = {
    "delay", "withContext", "withTimeout", "withTimeoutOrNull", "coroutineScope",
    "supervisorScope", "await", "awaitAll", "join", "joinAll",
    "receive", "send", "collect", "collectLatest", "emit", "yield",
    "suspendCancellableCoroutine",
}
AMBIGUOUS = {"first", "single", "last", "firstOrNull", "singleOrNull", "lastOrNull",
             "FirstOrDefault", "firstDescendant"}
DECL = re.compile(r'\bfun\b\s*(?:<[^>]*>\s*)?(?:[\w<>,?.\s]+\.)?(\w+)')
OWNER = re.compile(r'\b(?:object|class|interface)\s+(\w+)')
STR = re.compile(r'"(?:[^"\\\n]|\\.)*"|\'(?:[^\'\\\n]|\\.)*\'')
CALL = re.compile(r'\b([A-Za-z_]\w*)\s*\(')
KEYWORDS = {"if", "for", "while", "when", "catch", "return", "fun", "else", "do", "try"}


def kt_files(root):
    for dirpath, _, files in os.walk(root):
        for f in sorted(files):
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def read(p):
    return io.open(p, encoding="utf-8", newline="").read().replace("\r\n", "\n")


def blanked(line):
    """等长抹掉字符串字面量内容与行注释；保留标识符，便于后续按列配对与取名。"""
    out = list(line)
    j = line.find("//")
    tail = j if j >= 0 else len(line)
    for m in STR.finditer(line):
        if m.start() >= tail:
            break
        for k in range(m.start() + 1, min(m.end() - 1, tail)):
            if out[k] != "\\":
                out[k] = " "
    for k in range(tail, len(line)):
        out[k] = " "
    return "".join(out)


def collect_suspend(root):
    names, decl_lines = {}, 0
    for p in kt_files(root):
        owner = ""
        for ln in read(p).split("\n"):
            m = OWNER.search(ln)
            if m:
                owner = m.group(1)
            if re.search(r"\bsuspend\b", ln) and re.search(r"\bfun\b", ln):
                decl_lines += 1
                d = DECL.search(ln)
                if d:
                    names.setdefault(d.group(1), owner)
            for m2 in re.finditer(r'(\w+)\s*:\s*suspend\b', ln):
                names.setdefault(m2.group(1), owner)
    return names, decl_lines


def block_body(code_lines, li, col):
    """从 (li, col) 之后的第一个 '{' 起配对，返回块体字符串（不含外层花括号）与结束行。"""
    i, j = li, col
    while i < len(code_lines):
        k = code_lines[i].find("{", j)
        if k >= 0:
            break
        i += 1
        j = 0
    else:
        return "", li
    depth = 1
    body = []
    seg = code_lines[i][k + 1:]
    while i < len(code_lines):
        for c in seg:
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return "".join(body), i
            body.append(c)
        body.append("\n")
        i += 1
        seg = code_lines[i] if i < len(code_lines) else ""
    return "".join(body), i


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else None
    if not root:
        here = os.path.dirname(os.path.abspath(__file__))
        root = os.path.join(os.path.dirname(here), "app", "src", "main", "java")
    if not os.path.isdir(root):
        sys.stderr.write("扫描根不存在: %s\n" % root)
        return 2

    susp, decl_lines = collect_suspend(root)
    sites = violations = candidates = run_blocking = scc = 0
    lines_out = []
    for p in kt_files(root):
        text = read(p)
        raw = text.split("\n")
        code = [blanked(x) for x in raw]
        flat = "\n".join(code)
        run_blocking += len(re.findall(r"\brunBlocking\b", flat))
        scc += len(re.findall(r"\bsuspendCancellableCoroutine\b", flat))
        rel = os.path.relpath(p, root).replace("\\", "/")
        for m in re.finditer(r"\brunCatching\b", flat):
            upto = flat[:m.start()]
            li = upto.count("\n")
            if raw[li].strip().startswith(("//", "*", "/*")):
                continue
            sites += 1
            col = m.start() - (flat.rfind("\n", 0, m.start()) + 1)
            body, _ = block_body(code, li, col)
            names = set(CALL.findall(body))
            hit_known = sorted(n for n in names if n in KNOWN_SUSPEND)
            hit_proj = sorted(n for n in names if n in susp and n not in AMBIGUOUS)
            # 接收者核对：挂起函数是成员时，只有 `Owner.name(` 才是真调用
            real, cand = list(hit_known), []
            for n in hit_proj:
                owner = susp[n]
                recv = re.findall(r'([A-Za-z_]\w*)\.' + re.escape(n) + r'\s*\(', body)
                bare = re.findall(r'(?<![.\w])' + re.escape(n) + r'\s*\(', body)
                if bare or (owner and any(r == owner for r in recv)):
                    real.append(n)
                elif recv:
                    cand.append("%s.%s" % (recv[0], n))
            amb = sorted(n for n in names if n in AMBIGUOUS)
            if real:
                violations += 1
                lines_out.append("VIOLATION %s:%d %s" % (rel, li + 1, ",".join(sorted(set(real)))))
            if cand or amb:
                candidates += 1
                lines_out.append("CANDIDATE %s:%d %s" % (
                    rel, li + 1, ",".join(sorted(set(cand + amb)))))
    print("SUMMARY|sites=%d|violations=%d|candidates=%d|suspend_names=%d|"
          "suspend_decl_lines=%d|run_blocking=%d|scc=%d"
          % (sites, violations, candidates, len(susp), decl_lines, run_blocking, scc))
    for l in lines_out:
        print(l)
    return 0


if __name__ == "__main__":
    sys.exit(main())
