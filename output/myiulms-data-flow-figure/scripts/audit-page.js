// Page-side audit used by scripts/audit-visual.mjs.
// Runs inside the rendered page and reports what the eye would complain about:
// low-contrast text, clipped or truncated text, tiny type, overflow, and a
// top-down map of the box model so the composition can be judged without eyes.
window.__audit = function (opts) {
  opts = opts || {};
  const report = {};

  /* ---------- helpers ---------- */
  const parseColor = (c) => {
    const m = String(c).match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(",").map((s) => s.trim());
    return { r: +p[0], g: +p[1], b: +p[2], a: p[3] === undefined ? 1 : +p[3] };
  };
  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  });
  const bgOf = (el) => {
    const stack = [];
    let node = el;
    while (node && node.nodeType === 1) {
      const c = parseColor(getComputedStyle(node).backgroundColor);
      if (c && c.a > 0) {
        stack.push(c);
        if (c.a >= 1) break;
      }
      node = node.parentElement;
    }
    let base = { r: 255, g: 255, b: 255, a: 1 };
    for (let i = stack.length - 1; i >= 0; i--) base = over(stack[i], base);
    return base;
  };
  const lum = (c) => {
    const f = (v) => {
      v /= 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  };
  const ratio = (a, b) => {
    const l1 = lum(a);
    const l2 = lum(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  };
  const hex = (c) =>
    "#" + [c.r, c.g, c.b].map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const visible = (el) => {
    const s = getComputedStyle(el);
    if (s.display === "none" || s.visibility === "hidden") return false;
    if (parseFloat(s.opacity) < 0.05) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const label = (el) => {
    const t = (el.textContent || "").trim().replace(/\s+/g, " ");
    return t.length > 54 ? t.slice(0, 54) + "…" : t;
  };
  const pathOf = (el) => {
    const bits = [];
    let n = el;
    while (n && n.nodeType === 1 && bits.length < 4) {
      bits.unshift(n.tagName.toLowerCase() + (n.className && typeof n.className === "string" ? "." + n.className.trim().split(/\s+/).join(".") : ""));
      n = n.parentElement;
    }
    return bits.join(">");
  };

  /* ---------- document overflow ---------- */
  const de = document.documentElement;
  report.viewport = {
    scrollWidth: de.scrollWidth,
    clientWidth: de.clientWidth,
    scrollHeight: de.scrollHeight,
    horizontalOverflow: de.scrollWidth - de.clientWidth,
  };
  const wide = [];
  for (const el of document.querySelectorAll("body *")) {
    if (!visible(el)) continue;
    const r = el.getBoundingClientRect();
    if (r.right > de.clientWidth + 1 || r.left < -1) {
      wide.push({ path: pathOf(el), left: Math.round(r.left), right: Math.round(r.right) });
    }
  }
  report.overflowingElements = wide.slice(0, 12);

  /* ---------- text: contrast, size, clipping ---------- */
  const texts = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) {
    const n = walker.currentNode;
    if (n.nodeValue && n.nodeValue.trim()) nodes.push(n);
  }
  for (const n of nodes) {
    const el = n.parentElement;
    if (!el || !visible(el)) continue;
    const s = getComputedStyle(el);
    const size = parseFloat(s.fontSize);
    const weight = parseInt(s.fontWeight, 10) || 400;
    const large = size >= 24 || (size >= 18.66 && weight >= 700);
    const fg = parseColor(s.color);
    if (!fg) continue;
    const bg = bgOf(el);
    const r = ratio(fg.a < 1 ? over(fg, bg) : fg, bg);
    texts.push({
      path: pathOf(el),
      text: label(el),
      size: Math.round(size * 10) / 10,
      weight,
      color: s.color,
      bg: hex(bg),
      ratio: Math.round(r * 100) / 100,
      need: large ? 3 : 4.5,
      pass: r >= (large ? 3 : 4.5),
    });
  }
  report.textCount = texts.length;
  report.lowContrast = texts.filter((t) => !t.pass).sort((a, b) => a.ratio - b.ratio).slice(0, 18);
  report.smallestText = texts.slice().sort((a, b) => a.size - b.size).slice(0, 12);
  report.fontSizes = {};
  for (const t of texts) report.fontSizes[t.size] = (report.fontSizes[t.size] || 0) + 1;

  /* ---------- clipped / truncated text ---------- */
  const clipped = [];
  const truncated = [];
  for (const el of document.querySelectorAll("body *")) {
    if (!visible(el)) continue;
    const s = getComputedStyle(el);
    const ov = s.overflowX + " " + s.overflowY;
    if (/auto|scroll/.test(ov)) continue;
    const r = el.getBoundingClientRect();
    if (el.scrollWidth > el.clientWidth + 1 && /hidden|clip/.test(s.overflowX)) {
      truncated.push({ path: pathOf(el), text: label(el), scrollW: el.scrollWidth, clientW: el.clientWidth });
    }
    if (el.scrollHeight > el.clientHeight + 1 && /hidden|clip/.test(s.overflowY)) {
      clipped.push({ path: pathOf(el), text: label(el), scrollH: el.scrollHeight, clientH: el.clientHeight });
    }
  }
  report.truncated = truncated.slice(0, 12);
  report.clipped = clipped.slice(0, 12);

  /* ---------- key geometry ---------- */
  const rect = (sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return {
      x: Math.round(r.left), y: Math.round(r.top),
      w: Math.round(r.width), h: Math.round(r.height),
      right: Math.round(r.right), bottom: Math.round(r.bottom),
    };
  };
  report.rects = {
    stage: rect(".stage"),
    phonePanel: rect(".phone-panel"),
    journeyPanel: rect(".journey-panel"),
    portalPanel: rect(".portal-panel"),
    phone: rect(".phone"),
    screen: rect(".p-screen"),
    browser: rect(".browser"),
    browserPage: rect(".b-page"),
    journeyWrap: rect(".journey-wrap"),
    requestRail: rect("#rail-req"),
    responseRail: rect("#rail-res"),
    steps: rect("#jp-steps"),
    status: rect("#jp-status"),
    stepTitle: rect("#jp-title"),
    controls: rect(".controls"),
    hood: rect(".hood"),
  };
  const sp = report.rects;
  if (sp.phonePanel && sp.journeyPanel) {
    sp.gapPhoneJourney = sp.journeyPanel.x - sp.phonePanel.right;
    sp.gapJourneyPortal = sp.portalPanel.x - sp.journeyPanel.right;
  }
  if (sp.phonePanel && sp.portalPanel) {
    sp.panelHeightDelta = sp.portalPanel.h - sp.phonePanel.h;
    sp.portalSlackBelowBrowser = sp.portalPanel.bottom - sp.browser.bottom;
  }

  /* ---------- the round trip: does the wire reach the panels? ---------- */
  const rail = document.querySelector("#rail-req");
  if (rail) {
    const stub = parseFloat(getComputedStyle(document.querySelector(".stage")).getPropertyValue("--stub")) || 0;
    const rr = rail.getBoundingClientRect();
    const jr = document.querySelector(".journey-panel").getBoundingClientRect();
    const pr = document.querySelector(".phone-panel").getBoundingClientRect();
    const tr = document.querySelector(".portal-panel").getBoundingClientRect();
    report.wire = {
      stub: stub,
      railLeft: Math.round(rr.left),
      railRight: Math.round(rr.right),
      leftStubEnd: Math.round(rr.left - stub),
      rightStubEnd: Math.round(rr.right + stub),
      journeyLeft: Math.round(jr.left),
      journeyRight: Math.round(jr.right),
      phoneRight: Math.round(pr.right),
      portalLeft: Math.round(tr.left),
      reachesPhone: Math.abs(rr.left - stub - pr.right) <= 2,
      reachesPortal: Math.abs(rr.right + stub - tr.left) <= 2,
    };
  }

  /* ---------- phone content: does everything fit on the screen? ---------- */
  const screen = document.querySelector(".p-screen");
  if (screen) {
    const sr = screen.getBoundingClientRect();
    const items = [...screen.querySelectorAll(".p-card,.p-hero,.p-choose,.p-load,.p-tabs,.p-disclaimer,.p-head")];
    const out = [];
    for (const el of items) {
      if (!visible(el)) continue;
      const r = el.getBoundingClientRect();
      out.push({
        path: pathOf(el).split(">").slice(-1)[0],
        top: Math.round(r.top - sr.top),
        bottom: Math.round(r.bottom - sr.top),
        h: Math.round(r.height),
        overflowsBottom: r.bottom - sr.bottom,
        overflowsRight: r.right - sr.right,
      });
    }
    report.phoneItems = out;
    report.phoneScreen = { w: Math.round(sr.width), h: Math.round(sr.height) };
  }

  /* ---------- portal content ---------- */
  const cards = [...document.querySelectorAll("#b-page .b-card")];
  report.portalCards = cards.map((c) => {
    const r = c.getBoundingClientRect();
    const t = c.querySelector(".b-card-title");
    const p = c.querySelector(".b-card-path");
    return {
      title: t ? t.textContent : "",
      path: p ? p.textContent : "",
      h: Math.round(r.height),
      rows: c.querySelectorAll("tbody tr").length,
      visibleRows: [...c.querySelectorAll("tbody tr")].filter((tr) => parseFloat(getComputedStyle(tr.querySelector("td")).opacity) > 0.5).length,
    };
  });

  /* ---------- top-down box map (a stand-in for looking at it) ---------- */
  if (opts.map !== false) {
    const stage = document.querySelector(".stage") || document.body;
    const sr = stage.getBoundingClientRect();
    const cols = opts.cols || 104;
    const cw = sr.width / cols;
    const rows = opts.rows || 46;
    const rh = sr.height / rows;
    const grid = [];
    for (let y = 0; y < rows; y++) grid.push(new Array(cols).fill(" "));
    const tags = [
      [".p-choose", "?"], [".p-load", "~"], [".p-list", ":"], [".p-card", ":"], [".p-hero", ":"],
      [".b-card", "="], [".b-table", "="], [".b-page", "="], [".browser", "B"],
      [".rail-track", "-"], [".token", "O"], [".rail-chip", "#"], [".rail-arrow", ">"],
      [".jp-status", "J"], [".jp-steps", "P"], [".jp-line", "-"], [".rail-note", "n"],
      [".phone", "P"], [".p-screen", "S"], [".p-tabs", "T"], [".p-head", "H"],
      [".panel-label", "L"], [".panel-note", "n"], [".panel", "|"],
    ];
    const paint = (el, ch) => {
      if (!visible(el)) return;
      const r = el.getBoundingClientRect();
      const x0 = Math.max(0, Math.floor((r.left - sr.left) / cw));
      const x1 = Math.min(cols - 1, Math.ceil((r.right - sr.left) / cw) - 1);
      const y0 = Math.max(0, Math.floor((r.top - sr.top) / rh));
      const y1 = Math.min(rows - 1, Math.ceil((r.bottom - sr.top) / rh) - 1);
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) grid[y][x] = ch;
    };
    const els = [...document.querySelectorAll(".stage *")];
    // paint biggest first; smaller boxes overwrite, so the deepest box wins
    els.sort((a, b) => b.getBoundingClientRect().width * b.getBoundingClientRect().height - a.getBoundingClientRect().width * a.getBoundingClientRect().height);
    for (const el of els) {
      let ch = null;
      for (const [sel, t] of tags) {
        if (el.matches(sel)) { ch = t; break; }
      }
      if (!ch) {
        const s = getComputedStyle(el);
        const hasText = [...el.childNodes].some((n) => n.nodeType === 3 && n.nodeValue.trim());
        if (!hasText) continue;
        ch = ".";
      }
      paint(el, ch);
    }
    report.map = grid.map((r) => r.join("")).join("\n");
  }

  return report;
};
