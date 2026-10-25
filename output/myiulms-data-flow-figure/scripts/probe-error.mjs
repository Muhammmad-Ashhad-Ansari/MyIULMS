// Probe: report the exact parse error position of a page's inline script.
import { chromePort } from "./chrome-port.mjs";
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const target = resolve(process.argv[2]);
const html = readFileSync(target, "utf8");
const src = html.match(/<script>([\s\S]*?)<\/script>/)[1];

const binary = [
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
].find((p) => existsSync(p));
const profile = mkdtempSync(join(tmpdir(), "probe-"));
const chrome = spawn(binary, [
  "--headless=new",
  "--remote-debugging-port=0",
  `--user-data-dir=${profile}`,
  "--no-first-run",
  "--no-default-browser-check",
  "about:blank",
]);
const port = await chromePort(chrome, profile);
const page = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
const socket = new WebSocket(page.webSocketDebuggerUrl);
let id = 0;
const pending = new Map();
const send = (method, params = {}) =>
  new Promise((done) => {
    const mid = ++id;
    pending.set(mid, done);
    socket.send(JSON.stringify({ id: mid, method, params }));
  });
socket.onmessage = (m) => {
  const data = JSON.parse(m.data);
  if (data.id && pending.has(data.id)) {
    pending.get(data.id)(data);
    pending.delete(data.id);
  }
};
await new Promise((r) => (socket.onopen = r));
await send("Runtime.enable");

const reply = await send("Runtime.compileScript", { expression: src, sourceURL: "probe.js", persistScript: false });
const det = reply.result?.exceptionDetails;
if (det) {
  const lines = src.split("\n");
  console.log("PARSE ERROR at line", det.lineNumber + 1, "col", det.columnNumber);
  console.log("   >> " + (lines[det.lineNumber] ?? ""));
  console.log("   text: " + JSON.stringify(det.exception?.description ?? det.text));
} else {
  console.log("inline script parses OK (" + src.length + " chars)");
}
chrome.kill("SIGKILL");
try {
  rmSync(profile, { recursive: true, force: true, maxRetries: 3 });
} catch {}
process.exit(0);
