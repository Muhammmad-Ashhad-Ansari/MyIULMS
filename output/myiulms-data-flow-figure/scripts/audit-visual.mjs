// Visual audit driver: loads the demo in headless Edge and reports what the
// eye would complain about — contrast, tiny type, clipped or truncated text,
// overflow, the round-trip wiring, and a top-down map of the layout.
//
//   node --experimental-websocket scripts\audit-visual.mjs <file.html> [--width 1200] [--height 1400] [--tabs] [--play] [--map]
//
// --tabs  audits the phone and the portal once per section (Schedule, ...)
// --play  plays the round trip for every section and audits the finished screen
// --map   prints the ASCII layout map

import { chromePort } from "./chrome-port.mjs";
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

if (typeof WebSocket === "undefined" && !process.execArgv.includes("--experimental-websocket")) {
  const child = spawn(process.execPath, ["--experimental-websocket", ...process.argv.slice(2)], { stdio: "inherit" });
  child.on("exit", (code) => process.exit(code ?? 0));
} else {
  await main();
}

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    ...(process.env.LOCALAPPDATA ? [join(process.env.LOCALAPPDATA, "Google\\Chrome\\Application\\chrome.exe")] : []),
  ].filter(Boolean);
  return candidates.find((p) => existsSync(p));
}

function arg(name, fallback) {
  const i = process.argv.indexOf(name);
  return i > -1 ? process.argv[i + 1] : fallback;
}

async function main() {
  const target = process.argv[2];
  if (!target || target.startsWith("--")) {
    console.error("usage: audit-visual.mjs <file.html> [--width 1200] [--height 1400] [--tabs] [--map]");
    process.exit(1);
  }
  const width = Number(arg("--width", 1200));
  const height = Number(arg("--height", 1400));
  const withTabs = process.argv.includes("--tabs");
  const withPlay = process.argv.includes("--play");
  const withMap = process.argv.includes("--map");
  const binary = chromePath();
  if (!binary) {
    console.error("No Chrome/Edge found");
    process.exit(1);
  }
  const profile = mkdtempSync(join(tmpdir(), "myiulms-audit-"));
  const chrome = spawn(binary, ["--headless=new", "--do-not-de-elevate", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "--no-first-run", "--no-default-browser-check", "--hide-scrollbars", "--allow-file-access-from-files", "--disable-backgrounding-occluded-windows", "--disable-renderer-backgrounding", "--disable-background-timer-throttling", "--disable-features=CalculateNativeWinOcclusion", "about:blank"]);
  const port = await chromePort(chrome, profile);
  const finish = (code) => {
    chrome.kill("SIGKILL");
    try { rmSync(profile, { recursive: true, force: true }); } catch {}
    process.exit(code);
  };
  try {
    const page = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
    const socket = new WebSocket(page.webSocketDebuggerUrl);
    let id = 0;
    const pending = new Map();
    const problems = [];
    socket.onmessage = (m) => {
      const d = JSON.parse(m.data);
      if (d.id && pending.has(d.id)) { pending.get(d.id)(d); pending.delete(d.id); }
      else if (d.method === "Runtime.consoleAPICalled" && (d.params.type === "error" || d.params.type === "warning")) problems.push("console." + d.params.type + ": " + d.params.args.map((a) => a.value ?? a.description ?? "").join(" "));
      else if (d.method === "Runtime.exceptionThrown") problems.push("exception: " + (d.params.exceptionDetails.exception?.description ?? d.params.exceptionDetails.text));
    };
    await new Promise((done) => (socket.onopen = done));
    const send = (method, params = {}) => new Promise((done) => { const call = ++id; pending.set(call, done); socket.send(JSON.stringify({ id: call, method, params })); });
    const evaluate = async (expression) => {
      const reply = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
      if (reply.error) throw new Error(reply.error.message);
      if (reply.result?.exceptionDetails) throw new Error(reply.result.exceptionDetails.exception?.description ?? reply.result.exceptionDetails.text);
      return reply.result?.result?.value;
    };
    const sleep = (ms) => new Promise((done) => setTimeout(done, ms));
    await send("Runtime.enable");
    await send("Page.enable");
    await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: width < 600 });
    const auditSource = readFileSync(join(resolve("scripts"), "audit-page.js"), "utf8");
    await send("Page.addScriptToEvaluateOnNewDocument", { source: auditSource });
    await send("Page.navigate", { url: pathToFileURL(resolve(target)).href });
    await send("Page.bringToFront").catch(() => {});
    await sleep(1400);

    const show = (name, r) => {
      console.log("\n===== " + name + " =====");
      console.log("viewport:", JSON.stringify(r.viewport));
      if (r.overflowingElements?.length) {
        console.log("OVERFLOWING ELEMENTS:");
        for (const o of r.overflowingElements) console.log("  ", o.path, "left", o.left, "right", o.right);
      }
      console.log("text nodes:", r.textCount, "· font sizes:", JSON.stringify(r.fontSizes));
      if (r.lowContrast.length) {
        console.log("LOW CONTRAST:");
        for (const t of r.lowContrast) console.log("  ", t.ratio, "need", t.need, "|", t.size + "px", "|", t.color, "on", t.bg, "|", t.text, "|", t.path);
      } else console.log("low contrast: none");
      console.log("smallest text:");
      for (const t of r.smallestText) console.log("  ", t.size + "px", "|", t.text.slice(0, 46), "|", t.path);
      if (r.truncated.length) {
        console.log("TRUNCATED (text cut off):");
        for (const t of r.truncated) console.log("  ", t.scrollW + ">" + t.clientW, "|", t.text, "|", t.path);
      } else console.log("truncated: none");
      if (r.clipped.length) {
        console.log("CLIPPED (content taller than the box):");
        for (const t of r.clipped) console.log("  ", t.scrollH + ">" + t.clientH, "|", t.text, "|", t.path);
      } else console.log("clipped: none");
      console.log("rects:", JSON.stringify(r.rects, null, 0));
      console.log("wire:", JSON.stringify(r.wire));
      console.log("phone items:", JSON.stringify(r.phoneItems));
      console.log("portal cards:", JSON.stringify(r.portalCards));
      if (withMap && r.map) console.log("\n" + r.map);
    };

    show("initial view @ " + width + "px", await evaluate("__audit({map:" + withMap + "})"));

    if (withTabs) {
      const tabs = await evaluate("[...document.querySelectorAll('#p-tabs button')].map(b=>b.dataset.tab)");
      for (const tab of tabs) {
        await send("Page.bringToFront").catch(() => {});
        await evaluate("(document.querySelector('#p-tabs button[data-tab=" + tab + "]')).click()");
        await sleep(900);
        const r = await evaluate("__audit({map:false})");
        console.log("\n----- section: " + tab + " -----");
        console.log("portal cards:", JSON.stringify(r.portalCards));
        console.log("phone items:", JSON.stringify(r.phoneItems));
        const phoneProblems = r.lowContrast.filter((t) => t.path.includes("p-screen"));
        if (phoneProblems.length) { console.log("phone low contrast:"); for (const t of phoneProblems) console.log("  ", t.ratio, "|", t.size + "px", "|", t.color, "on", t.bg, "|", t.text); }
        const clippedPhone = r.clipped.filter((t) => t.path.includes("p-screen"));
        if (clippedPhone.length) { console.log("phone clipped:"); for (const t of clippedPhone) console.log("  ", t.scrollH + ">" + t.clientH, "|", t.text, "|", t.path); }
        const truncPhone = r.truncated.filter((t) => t.path.includes("p-screen"));
        if (truncPhone.length) { console.log("phone truncated:"); for (const t of truncPhone) console.log("  ", t.scrollW + ">" + t.clientW, "|", t.text); }
      }
    }

    if (withPlay) {
      const tabs = await evaluate("[...document.querySelectorAll('#p-tabs button')].map(b=>b.dataset.tab)");
      for (const tab of tabs) {
        await send("Page.bringToFront").catch(() => {});
        await evaluate("(document.querySelector('#p-tabs button[data-tab=" + tab + "]')).click()");
        await sleep(400);
        // tapping a tab already starts the round trip; only press play when it did not
        await evaluate("if (!state.playing) document.querySelector('#btn-play').click()");
        // wait for the phone to reach its populated state
        let ready = false;
        for (let i = 0; i < 40; i++) {
          const s = await evaluate("document.querySelector('#p-body').dataset.state");
          if (process.env.AUDIT_DEBUG) console.log("   poll", tab, i, s, await evaluate("document.visibilityState"));
          if (s === "ready") { ready = true; break; }
          await sleep(300);
        }
        if (!ready) {
          console.log("!! " + tab + ": the phone never reached the populated state (page visibility: " + (await evaluate("document.visibilityState")) + ") — rendering the final state directly");
          await evaluate("goTo(3)");
          await sleep(400);
        }
        await sleep(700);
        const r = await evaluate("__audit({map:false})");
        console.log("\n===== played: " + tab + " =====");
        console.log("phone items:", JSON.stringify(r.phoneItems));
        const phoneProblems = r.lowContrast.filter((t) => t.path.includes("p-screen"));
        if (phoneProblems.length) { console.log("phone low contrast:"); for (const t of phoneProblems) console.log("  ", t.ratio, "|", t.size + "px", "|", t.color, "on", t.bg, "|", t.text); }
        const clippedPhone = r.clipped.filter((t) => t.path.includes("p-screen"));
        if (clippedPhone.length) { console.log("phone clipped:"); for (const t of clippedPhone) console.log("  ", t.scrollH + ">" + t.clientH, "|", t.text, "|", t.path); }
        const truncPhone = r.truncated.filter((t) => t.path.includes("p-screen"));
        if (truncPhone.length) { console.log("phone truncated:"); for (const t of truncPhone) console.log("  ", t.scrollW + ">" + t.clientW, "|", t.text); }
        const portalTrunc = r.truncated.filter((t) => t.path.includes("b-page"));
        if (portalTrunc.length) { console.log("portal truncated:"); for (const t of portalTrunc) console.log("  ", t.scrollW + ">" + t.clientW, "|", t.text); }
        const portalClip = r.clipped.filter((t) => t.path.includes("b-page"));
        if (portalClip.length) { console.log("portal clipped:"); for (const t of portalClip) console.log("  ", t.scrollH + ">" + t.clientH, "|", t.text, "|", t.path); }
      }
    }

    console.log("\nconsole:", problems.length ? problems.join("\n") : "clean");
    socket.close();
    finish(0);
  } catch (error) {
    console.error(error);
    finish(1);
  }
}
