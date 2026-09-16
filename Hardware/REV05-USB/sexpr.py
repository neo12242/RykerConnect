"""Small lossless-atom KiCad S-expression reader/writer for project maintenance."""
import re,json
class Q(str):pass
def loads(s):
    stack=[];root=[];cur=root
    for t in re.findall(r'"(?:\\.|[^"\\])*"|[()]|[^\s()]+',s):
        if t=='(': n=[];cur.append(n);stack.append(cur);cur=n
        elif t==')':cur=stack.pop()
        else:cur.append(Q(json.loads(t)) if t.startswith('"') else t)
    assert not stack and len(root)==1
    return root[0]
def read(p):return loads(p.read_text(encoding='utf-8-sig'))
def fields(n,k):return [x for x in n if isinstance(x,list) and x and x[0]==k]
def field(n,k):return next(iter(fields(n,k)),None)
def prop(n,k):return next((x for x in fields(n,'property') if x[1]==k),None)
def dump(n,level=0):
    if not isinstance(n,list):return json.dumps(str(n),ensure_ascii=False) if isinstance(n,Q) else str(n)
    if not any(isinstance(x,list) for x in n):return '('+' '.join(dump(x) for x in n)+')'
    return '('+''.join(('\n'+'\t'*(level+1) if isinstance(x,list) else (' ' if i else ''))+dump(x,level+1) for i,x in enumerate(n))+'\n'+'\t'*level+')'
def write(p,n):p.write_text(dump(n)+'\n',encoding='utf-8')
