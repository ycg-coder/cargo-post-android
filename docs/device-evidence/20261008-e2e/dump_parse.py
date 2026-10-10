import re,json,sys
t=open(sys.argv[1],encoding='utf-8',errors='replace').read()
out=[]
for m in re.finditer(r'(<node[^>]*?/>)',t):
    s=m.group(1)
    txt=re.search(r'text="([^"]*)"',s); desc=re.search(r'content-desc="([^"]*)"',s)
    b=re.search(r'bounds="(\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\])"',s)
    cl=re.search(r'class="([^"]*)"',s); clk=re.search(r'clickable="true"',s)
    label=(txt.group(1) if txt and txt.group(1) else (desc.group(1) if desc else ''))
    if label or clk: out.append({'label':label,'cls':cl.group(1).split('.')[-1] if cl else '','bounds':b.group(1) if b else '','click':bool(clk)})
json.dump(out,open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,indent=1)
print("n=",len(out))
