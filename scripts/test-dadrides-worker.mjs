// Isolated local adapter for exercising the real DadRides Worker from Android tests.
// Node 24+, no external services. Never use production credentials or storage.
import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
const [workerPath, schemaPath, keyPath, sitePath] = process.argv.slice(2);
const port=Number(process.env.DADRIDES_TEST_PORT||8890);
if (!workerPath || !schemaPath || !keyPath) throw Error('Expected Worker module, schema and temporary test-key file');
const { handle, sha } = await import(pathToFileURL(workerPath));
const sqlite = new DatabaseSync(':memory:');
sqlite.exec(readFileSync(schemaPath, 'utf8'));
const objects = new Map();
const calls = {get:0,put:0,delete:0};
function statement(sql, params = []) {
  return {
    bind(...values) { return statement(sql, values); },
    first() { return sqlite.prepare(sql).get(...params) ?? null; },
    all() { return { results: sqlite.prepare(sql).all(...params) }; },
    run() { return sqlite.prepare(sql).run(...params); },
  };
}
const env = {
  OWNER_TOKEN_SHA256: await sha(new TextEncoder().encode(readFileSync(keyPath, 'utf8').trim())),
  DB: {
    prepare: statement,
    batch(statements) {
      sqlite.exec('BEGIN IMMEDIATE');
      try { const results = statements.map(s => s.run()); sqlite.exec('COMMIT'); return results; }
      catch (error) { sqlite.exec('ROLLBACK'); throw error; }
    },
  },
  PHOTOS: {
    put(key, bytes) { calls.put++;objects.set(key, new Uint8Array(bytes)); },
    get(key) { calls.get++;return objects.has(key) ? { body: objects.get(key) } : null; },
    delete(key) { calls.delete++;objects.delete(key); },
  },
};
const server = createServer(async (req, res) => {
  try {
    const pathname=new URL(req.url,'http://127.0.0.1:8890').pathname;
    if(req.method==='GET' && pathname==='/__test/metrics'){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify(calls));return}
    if(sitePath && req.method==='GET' && !pathname.startsWith('/api/')){
      const root=resolve(sitePath),file=resolve(root,pathname==='/'?'index.html':decodeURIComponent(pathname).slice(1));
      if(file.startsWith(root+sep) && existsSync(file) && statSync(file).isFile()){
        const mime={'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.json':'application/json'}[extname(file)]||'application/octet-stream';
        res.writeHead(200,{'content-type':mime,'cache-control':'no-store'});res.end(readFileSync(file));return
      }
    }
    const chunks = []; let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 4_000_000) throw Error('Local test request too large');
      chunks.push(chunk);
    }
    const request = new Request(`http://127.0.0.1:${port}${req.url}`, {
      method: req.method, headers: req.headers,
      ...(req.method === 'GET' || req.method === 'HEAD' ? {} : { body: Buffer.concat(chunks) }),
    });
    const response = await handle(request, env);
    res.writeHead(response.status, Object.fromEntries(response.headers));
    res.end(Buffer.from(await response.arrayBuffer()));
    console.log(JSON.stringify({ method: req.method, status: response.status }));
  } catch (error) { res.writeHead(500); res.end('Local test adapter failed'); console.error(error.message); }
});
server.listen(port, '127.0.0.1', () => console.log('Isolated DadRides Worker listening on 127.0.0.1:'+port));
