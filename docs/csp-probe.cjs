// Local browser regression fixture. No credentials or production services.
const http = require('node:http');
http.createServer((req, res) => {
  const origin = process.argv.includes('--baseline') ? '' : ' http://127.0.0.1:18790';
  res.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'" + origin + "; frame-ancestors 'none'; base-uri 'none'");
  if (req.url.startsWith('/start')) {
    res.writeHead(302, {Location: 'http://127.0.0.1:18790/login'});
    res.end();
  } else {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end('<form action="/start" method="get"><input name="code" value="TESTONLY"><button>Start</button></form>');
  }
}).listen(18789, '127.0.0.1');
http.createServer((req, res) => {
  res.setHeader('Content-Type', 'text/html');
  res.end('<h1>External provider reached</h1>');
}).listen(18790, '127.0.0.1');
