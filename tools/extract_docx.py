#!/usr/bin/env python3
"""把 .docx 正文抽成 UTF-8 文本，供读取。

本机控制台是 GBK，直接 print 中文正文会乱码，所以一律**落文件再看文件**。
产物是临时文件，不属于工程，读完即删。

用法：
    python tools/extract_docx.py "C:/Users/.../新建 DOCX 文档 (3).docx" _docx_dump.txt

按 CONTEXT_LOCK.md §十：引用指令段号必须从抽取结果里现查现抄，不许凭记忆。
"""
import re
import sys
import zipfile
import io


def paragraphs(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read('word/document.xml').decode('utf-8', 'ignore')
    out = []
    for p in re.findall(r'<w:p[ >].*?</w:p>', xml, re.S):
        text = ''.join(re.findall(r'<w:t[^>]*>(.*?)</w:t>', p, re.S))
        text = (text.replace('&lt;', '<').replace('&gt;', '>')
                    .replace('&amp;', '&').replace('&quot;', '"').replace('&apos;', "'"))
        out.append(re.sub(r'<[^>]+>', '', text))
    return out


def main():
    if len(sys.argv) < 3:
        sys.stderr.write('usage: extract_docx.py <src.docx> <out.txt>\n')
        return 2
    paras = paragraphs(sys.argv[1])
    with io.open(sys.argv[2], 'w', encoding='utf-8') as f:
        f.write('\n'.join(paras))
    chars = sum(len(p) for p in paras)
    sys.stderr.write('paragraphs=%d chars=%d -> %s\n' % (len(paras), chars, sys.argv[2]))
    return 0


if __name__ == '__main__':
    sys.exit(main())
