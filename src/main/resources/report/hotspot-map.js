/*
 * Hotspot map: package > file > class > method, drawn as nested circles.
 * Reads the JSON block #hotspot-map-data written by HtmlOutputWriter; the only
 * library used is d3-hierarchy (circle packing). Everything derived here is a
 * pure function of that JSON, so the same report always renders the same map.
 *
 * Every data-derived string goes through esc() before it reaches innerHTML.
 */
(function () {
  "use strict";
  var host = document.getElementById("hm");
  var dataEl = document.getElementById("hotspot-map-data");
  if (!host || !dataEl) return;

  var TEXT = {
    en: {
      sizeBy: "Circle size", colorBy: "Circle colour", optCc: "Complexity (CC)", optScore: "Composite score", optLoc: "Lines of code",
      optRisk: "Risk score (composite)", optDecay: "Recent change pressure", optGap: "Test gap",
      legendColor: "Colour", legendSize: "Size", legendHint: "Click to go package › file › class › method",
      low: "low", highLog: "high (log)", gapLo: "100% covered", gapHi: "50% or less",
      floatOn: "Drift on", floatOff: "Drift off", langBtn: "한국어",
      commits: "commits", files: "files", methods: "methods", ccTotal: "total complexity", lineCov: "line coverage",
      truncFiles: "Map shows the top {0} of {1} files by score (output.topN).", truncMethods: "Only the top {0} of {1} methods are included.",
      overview: "At a glance", headline: "<em>{0}</em> of {1} files hold <em>{2}</em> of the risk",
      headlineNone: "No file has a risk score yet", share80: "80% of the risk sits in the top {0} files ({1}). They are the large red circles.",
      tabRank: "Fix first", tabGap: "Test gap", rankNote: "Ordered by composite score.", gapNote: "Files with complexity, lowest line coverage first. The value is the coverage.",
      drillNote: "Click a circle to go package › file › class › method.",
      scoreNote: "Risk score = complexity (CC) × recent change pressure × coverage multiplier, after Adam Tornhill's Your Code as a Crime Scene.",
      scoreNoteNoCov: "Risk score = complexity (CC) × recent change pressure. Coverage is shown for reference only (scoring.excludeCoverage).",
      fileRank: "File · risk rank {0} of {1}", classOf: "Class · {0}", methodRank: "Method · risk rank {0} of {1}",
      openFile: "Open on source host", openClass: "Open class", openLines: "Open L{0}–{1}", toFile: "File", toClass: "Class", home: "Overview",
      why: "Why the score is high", hoverHint: "Hover a box to see how it was calculated.",
      cc: "Complexity", decay: "Change pressure", coverage: "Coverage", coverageRef: "Coverage (not scored)", score: "Risk score",
      ccTitle: "Complexity (CC)", decayTitle: "Recent change pressure", covTitle: "Line coverage", scoreTitle: "Risk score (composite)",
      ccFile: "Sum of the Cognitive Complexity of every method in the file.", ccFileZero: "No method has branches or loops, so it is 0.",
      ccOthers: "{0} others {1}", ccMethod: "Cognitive Complexity of this method body (SonarQube rules): branches, loops and nesting.",
      ccNesting: "Deeper nesting adds more per branch.", ccBody: "L{0}–{1} body = {2}",
      decayRule: "Each commit weighs <code>0.5^(days ago ÷ {0})</code> and the weights are summed: today 1, {0} days ago 0.5, {1} days ago 0.25.",
      decaySum: "weights of {0} commits = {1}", decayAvg: "On average {0} per commit, i.e. like a change about {1} days ago.",
      decayMethod: "For a method, only commits whose changed lines overlap its range count.",
      covRule: "Line coverage = lines run by tests ÷ executable lines (JaCoCo). Branch coverage is not used.",
      covLines: "{0} ÷ {1} lines = {2}", covRange: "L{0}–{1} range = {2}",
      covMulRule: "It enters the score as a multiplier: ×0.91 at 100%, ×10 at 0%.", covZero: "There are executable lines, but tests ran none of them.",
      covUnknown: "No executable lines here (interface or abstract declaration, or absent from the JaCoCo report), so coverage is unknown and the multiplier is ×1.00.",
      covNoReport: "No JaCoCo report was supplied, so coverage does not affect the score.", covExcluded: "Coverage is excluded from the score by configuration.",
      scoreRule: "complexity × change pressure × coverage multiplier", scoreRuleNoCov: "complexity × change pressure",
      mCc: "Complexity (CC)", mDecay: "Recent change pressure", mCov: "Line coverage", maxOf: "max {0}", methodMax: "method max {0}",
      commitsN: "{0} commits", linesN: "{0} lines", noData: "no data", hotMethods: "Hottest methods", classesN: "{0} classes",
      noMethods: "No methods in this file (record, interface, or outside output.topN).",
      nMethods: "methods", ccSum: "total complexity", methodScoreSum: "method score total", methodsByScore: "Methods by risk score",
      tipRisk: "risk", tipCc: "CC", tipChange: "change", tipCov: "line cov", tipFiles: "{0} files", tipRiskSum: "risk total", tipCcSum: "CC total", tipMethods: "{0} methods",
      vTest: "Add tests first", vTestWhy: "Line coverage is {0}. Pin the current behaviour with tests before changing it.",
      vFix: "Fix first", vFixWhy: "Complex ({0}) and still changing ({1} commits). This is where defects are most likely.",
      vBusy: "Change magnet", vBusyWhy: "Not complex, but changed often. Reviews and regression tests are enough.",
      vSleep: "Sleeping complexity", vSleepWhy: "Complex but rarely touched lately. Tidy it up next time it changes.",
      vOk: "Low risk", vOkWhy: "Nothing to do for now.",
      error: "The hotspot map could not be drawn. The tables in the other tabs carry the same data."
    },
    ko: {
      sizeBy: "원 크기", colorBy: "원 색", optCc: "복잡도 (CC)", optScore: "Composite Score", optLoc: "코드 줄 수 (LOC)",
      optRisk: "위험 점수 (Composite)", optDecay: "최근 변경 압력", optGap: "테스트 공백",
      legendColor: "색", legendSize: "크기", legendHint: "누르면 패키지 › 파일 › 클래스 › 메서드로 들어갑니다",
      low: "낮음", highLog: "높음 (log)", gapLo: "커버리지 100%", gapHi: "50% 이하",
      floatOn: "두둥실 켜짐", floatOff: "두둥실 꺼짐", langBtn: "English",
      commits: "커밋", files: "파일", methods: "메서드", ccTotal: "복잡도 합계", lineCov: "전체 라인 커버리지",
      truncFiles: "점수 상위 {0}개 파일만 지도에 있습니다 (전체 {1}개, output.topN).", truncMethods: "메서드는 상위 {0}개만 포함됩니다 (전체 {1}개).",
      overview: "한눈에 보기", headline: "파일 {1}개 중 <em>{0}개</em>가 위험의 <em>{2}</em>",
      headlineNone: "위험 점수가 있는 파일이 아직 없습니다", share80: "위험의 80%가 상위 {0}개 파일({1})에 몰려 있습니다. 지도에서 크고 붉은 원이 그 파일들입니다.",
      tabRank: "먼저 손볼 곳", tabGap: "테스트 공백", rankNote: "Composite Score 순입니다.", gapNote: "복잡도가 있는 파일 중 라인 커버리지가 낮은 순입니다. 오른쪽 값은 커버리지입니다.",
      drillNote: "원을 누르면 패키지 › 파일 › 클래스 › 메서드 순으로 들어갑니다.",
      scoreNote: "위험 점수 = 복잡도(CC) × 최근 변경 압력 × 커버리지 배수. Adam Tornhill의 Your Code as a Crime Scene 방식입니다.",
      scoreNoteNoCov: "위험 점수 = 복잡도(CC) × 최근 변경 압력. 커버리지는 참고용으로만 표시합니다 (scoring.excludeCoverage).",
      fileRank: "파일 · 위험 순위 {0}위 / {1}개", classOf: "클래스 · {0}", methodRank: "메서드 · 위험 순위 {0}위 / {1}개",
      openFile: "소스 저장소에서 코드 보기", openClass: "클래스 코드 보기", openLines: "L{0}–{1} 코드 보기", toFile: "파일로", toClass: "클래스로", home: "전체 보기",
      why: "점수가 높은 이유", hoverHint: "칸에 마우스를 올리면 계산 과정이 나옵니다.",
      cc: "복잡도", decay: "변경 압력", coverage: "커버리지", coverageRef: "커버리지 (점수 미반영)", score: "위험 점수",
      ccTitle: "복잡도 (CC)", decayTitle: "최근 변경 압력", covTitle: "라인 커버리지", scoreTitle: "위험 점수 (Composite)",
      ccFile: "파일 안 모든 메서드의 Cognitive Complexity를 더한 값입니다.", ccFileZero: "분기나 반복이 있는 메서드가 없어 0입니다.",
      ccOthers: "그 외 {0}개 {1}", ccMethod: "이 메서드 본문의 분기, 반복, 중첩을 센 Cognitive Complexity(SonarQube 방식)입니다.",
      ccNesting: "중첩이 깊을수록 분기 하나에 점수가 더 붙습니다.", ccBody: "L{0}–{1} 본문 = {2}",
      decayRule: "커밋마다 가중치 <code>0.5^(경과일 ÷ {0})</code>를 주고 모두 더합니다. 오늘 커밋은 1, {0}일 전은 0.5, {1}일 전은 0.25입니다.",
      decaySum: "커밋 {0}회의 가중치 합 = {1}", decayAvg: "커밋 1회당 평균 {0}이므로, 평균 약 {1}일 전 변경에 해당합니다.",
      decayMethod: "메서드는 변경된 줄이 이 메서드 범위와 겹치는 커밋만 셉니다.",
      covRule: "라인 커버리지 = 테스트가 실행한 줄 ÷ 실행 가능한 줄 (JaCoCo). 브랜치 커버리지는 쓰지 않습니다.",
      covLines: "{0}줄 ÷ {1}줄 = {2}", covRange: "L{0}–{1} 범위 = {2}",
      covMulRule: "점수에는 배수로 바꿔 곱합니다. 100%면 ×0.91, 0%면 ×10입니다.", covZero: "실행 가능한 줄은 있지만 테스트가 한 줄도 실행하지 않았습니다.",
      covUnknown: "실행 가능한 줄이 없어(인터페이스, 추상 선언, 또는 JaCoCo 리포트에 없음) 커버리지를 알 수 없습니다. 배수는 ×1.00입니다.",
      covNoReport: "JaCoCo 리포트가 없어 커버리지를 점수에 반영하지 않았습니다.", covExcluded: "설정에 따라 커버리지를 점수에서 제외했습니다.",
      scoreRule: "복잡도 × 변경 압력 × 커버리지 배수", scoreRuleNoCov: "복잡도 × 변경 압력",
      mCc: "복잡도 (CC)", mDecay: "최근 변경 압력", mCov: "라인 커버리지", maxOf: "최고 {0}", methodMax: "메서드 최고 {0}",
      commitsN: "커밋 {0}회", linesN: "{0}줄", noData: "데이터 없음", hotMethods: "가장 뜨거운 메서드", classesN: "클래스 {0}개",
      noMethods: "이 파일에는 표시할 메서드가 없습니다 (record, 인터페이스, 또는 output.topN 밖).",
      nMethods: "메서드", ccSum: "복잡도 합계", methodScoreSum: "메서드 점수 합계", methodsByScore: "메서드 (위험 점수 순)",
      tipRisk: "위험", tipCc: "복잡도", tipChange: "변경", tipCov: "라인 커버리지", tipFiles: "파일 {0}개", tipRiskSum: "위험 합계", tipCcSum: "복잡도 합계", tipMethods: "메서드 {0}개",
      vTest: "테스트부터 채우기", vTestWhy: "라인 커버리지가 {0}입니다. 고치기 전에 현재 동작을 고정하는 테스트가 먼저 필요합니다.",
      vFix: "먼저 손볼 곳", vFixWhy: "복잡한데({0}) 최근에도 계속 바뀌고 있습니다({1}회). 결함이 생길 확률이 가장 높은 유형입니다.",
      vBusy: "변경이 몰리는 곳", vBusyWhy: "복잡하지는 않지만 변경이 잦습니다. 리뷰와 회귀 테스트를 챙기면 충분합니다.",
      vSleep: "잠든 복잡도", vSleepWhy: "복잡하지만 최근엔 거의 안 바뀝니다. 다음에 건드릴 때 함께 정리하면 됩니다.",
      vOk: "저위험", vOkWhy: "지금은 신경 쓰지 않아도 됩니다.",
      error: "핫스팟 지도를 그리지 못했습니다. 다른 탭의 표에 같은 데이터가 있습니다."
    }
  };

  var lang = /^ko/i.test((window.navigator && navigator.language) || "") ? "ko" : "en";
  function t(key) {
    var s = TEXT[lang][key], args = arguments;
    return s.replace(/\{(\d)\}/g, function (_, i) { return args[+i + 1]; });
  }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function byId(id) { return document.getElementById(id); }
  function num(n) { return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ","); }
  function fmt(n) { var p = n.toFixed(1).split("."); return p[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",") + "." + p[1]; }
  function pct(v) { return Math.round(v * 100) + "%"; }
  function pct1(v) { return (v * 100).toFixed(1) + "%"; }
  function sum(list, fn) { var s = 0; list.forEach(function (x) { s += fn(x); }); return s; }
  function maxOf(list, fn) { var m = 0; list.forEach(function (x) { m = Math.max(m, fn(x)); }); return m; }
  function quantile(sorted, p) {
    if (!sorted.length) return 0;
    var i = (sorted.length - 1) * p, lo = Math.floor(i), hi = Math.ceil(i);
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (i - lo);
  }

  try {
    boot(JSON.parse(dataEl.textContent));
  } catch (e) {
    host.innerHTML = '<p class="hm-error">' + esc(TEXT[lang].error) + "</p>";
    if (window.console && console.error) console.error(e);
  }

  function boot(data) {
    var meta = data.meta, files = data.files, methods = data.methods;
    var reduceMotion = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
    var state = { sizeBy: "cc", colorBy: "score", tab: "rank", drift: !reduceMotion };
    var HEAT = [[219, 228, 240], [246, 198, 106], [238, 106, 59], [176, 19, 47]];
    var INK = "#1c2433", MUTED = "#627084", REACH = 170;
    var svg = byId("hm-map"), stage = byId("hm-stage"), side = byId("hm-side"), tip = byId("hm-tip");
    var SVG_NS = svg.namespaceURI;
    var hasCov = files.some(function (f) { return f.cov != null; });
    var root, all, focus, selected, view, anim = null, dirty = true, pointer = null, w = 0, h = 0, scale, fileNode, methodNode;

    /* ---------- paths, links ---------- */
    var prefix = commonDir(files.map(function (f) { return f.path; }));
    files.forEach(function (f) { f.rel = f.path.slice(prefix.length); f.base = f.rel.split("/").pop(); });
    var fileByPath = {};
    files.forEach(function (f) { fileByPath[f.path] = f; });
    function commonDir(paths) {
      if (!paths.length) return "";
      var parts = paths[0].split("/").slice(0, -1);
      paths.forEach(function (p) {
        var q = p.split("/").slice(0, -1), i = 0;
        while (i < parts.length && i < q.length && parts[i] === q[i]) i++;
        parts = parts.slice(0, i);
      });
      return parts.length ? parts.join("/") + "/" : "";
    }
    function link(path, start, end) {
      if (!meta.blobBase || meta.blobBase.indexOf("https://") !== 0) return null;
      return meta.blobBase + encodeURI(path).replace(/#/g, "%23").replace(/\?/g, "%3F") + (start ? "#L" + start + "-L" + end : "");
    }

    /* ---------- colour ---------- */
    function heat(v) {
      var x = Math.max(0, Math.min(1, v)) * (HEAT.length - 1), i = Math.min(HEAT.length - 2, Math.floor(x)), f = x - i;
      return [0, 1, 2].map(function (c) { return Math.round(HEAT[i][c] + (HEAT[i + 1][c] - HEAT[i][c]) * f); });
    }
    function rgb(c) { return "rgb(" + c[0] + "," + c[1] + "," + c[2] + ")"; }
    function logScale(items, key) {
      var max = maxOf(items, function (x) { return x[key]; }) || 1, floor = max / 2000, span = Math.log(max / floor);
      return function (x) { return heat(Math.log(Math.max(floor, x[key]) / floor) / span); };
    }
    function makeScale() {
      if (state.colorBy === "gap") {
        var gap = function (x) { return x.cov == null ? [238, 241, 246] : heat((1 - x.cov) / 0.5); };
        return { file: gap, method: gap, lo: t("gapLo"), hi: t("gapHi"), label: t("optGap") };
      }
      return { file: logScale(files, state.colorBy), method: logScale(methods, state.colorBy),
        lo: t("low"), hi: t("highLog"), label: state.colorBy === "score" ? t("optRisk") : t("optDecay") };
    }

    /* ---------- model: package > file > class > method ---------- */
    function sizeOf(x) { return Math.max({ cc: 0.6, score: 0.3, loc: 6 }[state.sizeBy], x[state.sizeBy]); }
    function buildTree() {
      var top = { kind: "root", name: meta.label, children: [] };
      files.forEach(function (f) {
        var parts = f.rel.split("/"), node = top;
        parts.slice(0, -1).forEach(function (p) {
          var next = null;
          node.children.forEach(function (c) { if (c.kind === "pkg" && c.name === p) next = c; });
          if (!next) { next = { kind: "pkg", name: p, children: [] }; node.children.push(next); }
          node = next;
        });
        node.children.push({ kind: "file", name: f.base, file: f });
      });
      return top;
    }
    function bySize(a, b) { return b.value - a.value; }
    // Breadth-first walk over the (grafted) tree: parents always come before their children.
    // Written out rather than using node.descendants(), which needs generator-based iteration.
    function walk(node) {
      var out = [node];
      for (var i = 0; i < out.length; i++) {
        if (out[i].children) Array.prototype.push.apply(out, out[i].children);
      }
      return out;
    }
    function layout() {
      root = d3.pack().size([w, h]).padding(function (d) { return d.depth === 0 ? 22 : 6; })(
        d3.hierarchy(buildTree()).sum(function (d) { return d.file && !d.children ? sizeOf(d.file) : 0; }).sort(bySize));
      var byFile = {};
      methods.forEach(function (m) { (byFile[m.file] = byFile[m.file] || []).push(m); });
      // A file keeps the radius its own value earned; its classes and methods are packed inside it.
      root.leaves().forEach(function (leaf) {
        var ms = leaf.data.file ? byFile[leaf.data.file.path] : null;   // an empty report has only the root
        if (!ms) return;
        var classes = [], index = {};
        ms.forEach(function (m) {
          var c = index[m.fqcn];
          if (!c) { c = index[m.fqcn] = { kind: "class", name: m.cls, fqcn: m.fqcn, file: leaf.data.file, methods: [], children: [] }; classes.push(c); }
          c.methods.push(m);
          c.children.push({ kind: "method", name: m.name, m: m, file: leaf.data.file });
        });
        var sub = d3.pack().size([2 * leaf.r, 2 * leaf.r]).padding(leaf.r * 0.045)(
          d3.hierarchy({ children: classes }).sum(function (d) { return d.m ? sizeOf(d.m) : 0; }).sort(bySize));
        walk(sub).forEach(function (n) {
          if (n === sub) return;
          n.x += leaf.x - leaf.r; n.y += leaf.y - leaf.r; n.depth += leaf.depth;
        });
        sub.children.forEach(function (c) { c.parent = leaf; });
        leaf.children = sub.children;
      });
      all = walk(root);
      fileNode = {}; methodNode = [];
      all.forEach(function (d, i) {
        if (d.data.kind === "file") fileNode[d.data.file.path] = d;
        if (d.data.kind === "method") methodNode.push(d);
        // Deterministic drift: each bubble bobs on its own slow rhythm.
        var r1 = Math.sin(i * 12.9898) * 43758.5453, r2 = Math.sin(i * 78.233) * 12543.123;
        r1 -= Math.floor(r1); r2 -= Math.floor(r2);
        d.p1 = r1 * 6.283; d.p2 = r2 * 6.283; d.w1 = 0.55 + r2 * 0.5; d.w2 = 0.45 + r1 * 0.5;
        d.ox = 0; d.oy = 0; d.px = 0; d.py = 0;
      });
    }
    function keyOf(d) { return d.ancestors().map(function (a) { return a.data.kind + ":" + a.data.name; }).reverse().join("/"); }
    function findByKey(key) { var hit = null; all.forEach(function (d) { if (!hit && keyOf(d) === key) hit = d; }); return hit; }
    function filesUnder(d) { return walk(d).filter(function (n) { return n.data.kind === "file"; }).map(function (n) { return n.data.file; }); }
    function openFile() { var hit = null; focus.ancestors().forEach(function (a) { if (!hit && a.data.kind === "file") hit = a; }); return hit; }
    function isInner(d) { return d.data.kind === "class" || d.data.kind === "method"; }
    function isVisible(d, open) { return !isInner(d) || (open !== null && d.ancestors().indexOf(open) >= 0); }
    function fillOf(d, open) {
      var k = d.data.kind;
      if (k === "root") return null;
      if (k === "pkg") return [255, 255, 255];
      if (k === "file") return d === open ? [255, 255, 255] : scale.file(d.data.file);
      if (k === "class") return [236, 240, 247];
      return scale.method(d.data.m);
    }
    function labelText(d) {
      var k = d.data.kind;
      return k === "pkg" ? d.data.name + "/" : k === "method" ? d.data.name + "()" : d.data.name.replace(/\.java$/, "");
    }
    function fileQuantile(key, p) { return quantile(files.map(function (x) { return x[key]; }).sort(function (a, b) { return a - b; }), p); }
    function verdict(f) {
      var hiCc = f.cc > 0 && f.cc >= fileQuantile("cc", 0.8), hiD = f.decay >= fileQuantile("decay", 0.8);
      if (f.cov != null && f.cov < 0.6 && f.cc > 0) return [t("vTest"), t("vTestWhy", pct(f.cov)), "var(--hm-burn)"];
      if (hiCc && hiD) return [t("vFix"), t("vFixWhy", f.cc, f.rev), "var(--hm-burn)"];
      if (hiD) return [t("vBusy"), t("vBusyWhy"), "var(--hm-hot)"];
      if (hiCc) return [t("vSleep"), t("vSleepWhy"), "var(--hm-accent)"];
      return [t("vOk"), t("vOkWhy"), "var(--hm-ok)"];
    }

    /* ---------- side panel ---------- */
    function row(i, name, sub, value) {
      return '<li><button type="button" data-i="' + i + '"><span class="hm-n">' + (i < 9 ? "0" : "") + (i + 1) + '</span><span class="hm-nm" title="'
        + esc(name) + '">' + esc(name) + " <small>" + esc(sub) + '</small></span><span class="hm-s">' + esc(value) + "</span></button></li>";
    }
    function wireRows(targets) {
      Array.prototype.forEach.call(side.querySelectorAll(".hm-list button"), function (b) {
        b.onclick = function () { activate(targets[+b.getAttribute("data-i")]); };
      });
    }
    function pop(title, lines) {
      return '<div class="hm-pop" role="tooltip"><strong>' + title + "</strong>"
        + lines.filter(function (l) { return !!l; }).map(function (l) { return "<div>" + l + "</div>"; }).join("") + "</div>";
    }
    function code(s) { return "<code>" + s + "</code>"; }
    function box(big, label, popHtml, extra) {
      return '<div class="hm-f' + (extra || "") + '" tabindex="0"><b>' + big + "</b><span>" + label + "</span>" + popHtml + "</div>";
    }
    // Each factor box explains, on hover or focus, how its number was produced.
    function formula(x, d) {
      var isFile = d.data.kind === "file", H = meta.halfLifeDays, ccLines, covLines, covBig = "–";
      if (isFile) {
        var ms = [];
        (d.children || []).forEach(function (c) { c.children.forEach(function (n) { if (n.data.m.cc > 0) ms.push(n.data.m); }); });
        ms.sort(function (a, b) { return b.cc - a.cc; });
        var top = ms.slice(0, 4), restCc = x.cc - sum(top, function (m) { return m.cc; }), restN = ms.length - top.length;
        var terms = top.map(function (m) { return esc(m.name) + " " + m.cc; });
        if (restCc > 0) terms.push(restN > 0 ? t("ccOthers", restN, restCc) : String(restCc));
        ccLines = [t("ccFile"), x.cc > 0 ? code(terms.join(" + ") + " = " + x.cc) : t("ccFileZero")];
      } else {
        ccLines = [t("ccMethod"), t("ccNesting"), code(t("ccBody", x.start, x.end, x.cc))];
      }
      var avg = x.rev ? x.decay / x.rev : 0;
      var decayLines = [t("decayRule", H, 2 * H), code(t("decaySum", x.rev, x.decay.toFixed(4))),
        avg > 0 && avg <= 1 ? t("decayAvg", avg.toFixed(2), Math.round(H * Math.log(1 / avg) / Math.LN2)) : "",
        isFile ? "" : t("decayMethod")];
      if (x.cov == null) {
        covLines = [hasCov ? t("covUnknown") : t("covNoReport")];
      } else {
        covBig = pct(x.cov);
        covLines = [t("covRule"),
          code(isFile && x.exec != null ? t("covLines", num(x.covered), num(x.exec), pct1(x.cov)) : t("covRange", x.start, x.end, pct1(x.cov))),
          x.cov === 0 ? t("covZero") : ""];
        if (meta.excludeCoverage) covLines.push(t("covExcluded"));
        else covLines.push(t("covMulRule"), code("1 ÷ (" + x.cov.toFixed(4) + " + 0.1) = ×" + x.covMul.toFixed(2)));
      }
      var scoreLine = meta.excludeCoverage
        ? code(x.cc + " × " + x.decay.toFixed(4) + " = " + x.score.toFixed(4))
        : code(x.cc + " × " + x.decay.toFixed(4) + " × " + x.covMul.toFixed(4) + " = " + x.score.toFixed(4));
      return '<div><div class="hm-eyebrow" style="margin-bottom:8px">' + t("why") + '</div><div class="hm-formula">'
        + box(x.cc, t("cc"), pop(t("ccTitle"), ccLines)) + '<span class="hm-op">×</span>'
        + box(x.decay.toFixed(2), t("decay"), pop(t("decayTitle"), decayLines)) + '<span class="hm-op">' + (meta.excludeCoverage ? "·" : "×") + "</span>"
        + box(covBig, meta.excludeCoverage ? t("coverageRef") : t("coverage") + " ×" + x.covMul.toFixed(2), pop(t("covTitle"), covLines))
        + '<span class="hm-op">=</span>'
        + box(fmt(x.score), t("score"), pop(t("scoreTitle"), [scoreLine, meta.excludeCoverage ? t("scoreRuleNoCov") : t("scoreRule")]), " hm-total")
        + '</div><p class="hm-note" style="margin-top:6px;font-size:12px">' + t("hoverHint") + "</p></div>";
    }
    function meter(label, right, ratio, color) {
      return '<div class="hm-meter"><div class="hm-meter-row"><span>' + label + "</span><span>" + right + '</span></div><div class="hm-track"><div class="hm-fill" style="width:'
        + Math.max(0, Math.min(100, 100 * ratio)) + "%;" + (color ? "background:" + color : "") + '"></div></div></div>';
    }
    function actions(url, label, upLabel) {
      return '<div class="hm-actions">' + (url ? '<a class="hm-btn hm-primary" href="' + esc(url) + '" target="_blank" rel="noopener noreferrer">' + label + " ↗</a>" : "")
        + (upLabel ? '<button class="hm-btn" id="hm-up" type="button">← ' + upLabel + "</button>" : "")
        + '<button class="hm-btn" id="hm-home" type="button">' + t("home") + "</button></div>";
    }
    function wireActions(upNode) {
      var up = byId("hm-up");
      if (up) up.onclick = function () { activate(upNode); };
      byId("hm-home").onclick = function () { activate(root); };
    }
    function byScore(a, b) { return b.score - a.score; }
    function renderOverview() {
      var sorted = files.slice().sort(byScore);
      var total = meta.totalFileScore > 0 ? meta.totalFileScore : sum(sorted, function (x) { return x.score; });
      var topN = Math.min(3, sorted.length), acc = 0, n80 = 0;
      var topShare = total > 0 ? sum(sorted.slice(0, topN), function (x) { return x.score; }) / total : 0;
      for (var i = 0; i < sorted.length && total > 0; i++) { acc += sorted[i].score; if (acc / total >= 0.8) { n80 = i + 1; break; } }
      var gapTab = state.tab === "gap" && hasCov;
      var list = gapTab
        ? files.filter(function (f) { return f.cov != null && f.cc > 0; }).sort(function (a, b) { return a.cov - b.cov; }).slice(0, 10)
        : sorted.slice(0, 10);
      side.innerHTML = '<div><div class="hm-eyebrow">' + t("overview") + "</div><h2>"
        + (total > 0 ? t("headline", topN, num(meta.totalFiles), pct(topShare)) : t("headlineNone")) + "</h2>"
        + (n80 ? '<p class="hm-note" style="margin-top:8px">' + t("share80", n80, pct(n80 / meta.totalFiles)) + "</p>" : "") + "</div>"
        + '<div class="hm-tabs" role="tablist"><button role="tab" data-tab="rank" aria-selected="' + !gapTab + '">' + t("tabRank") + "</button>"
        + '<button role="tab" data-tab="gap" aria-selected="' + gapTab + '"' + (hasCov ? "" : " disabled") + ">" + t("tabGap") + "</button></div>"
        + '<ol class="hm-list">' + list.map(function (f, i) {
          return row(i, f.base, gapTab ? "CC " + f.cc : verdict(f)[0], gapTab ? pct(f.cov) : fmt(f.score));
        }).join("") + "</ol>"
        + '<p class="hm-note">' + (gapTab ? t("gapNote") : t("rankNote")) + " " + t("drillNote") + "</p>"
        + '<p class="hm-note">' + (meta.excludeCoverage ? t("scoreNoteNoCov") : t("scoreNote")) + "</p>";
      Array.prototype.forEach.call(side.querySelectorAll(".hm-tabs button"), function (b) {
        b.onclick = function () { state.tab = b.getAttribute("data-tab"); renderOverview(); };
      });
      wireRows(list.map(function (f) { return fileNode[f.path]; }));
    }
    function renderFile(d) {
      var f = d.data.file, v = verdict(f), classes = d.children || [], ms = [];
      var maxCc = maxOf(files, function (x) { return x.cc; }) || 1, maxD = maxOf(files, function (x) { return x.decay; }) || 1;
      classes.forEach(function (c) { c.children.forEach(function (n) { ms.push(n); }); });
      ms.sort(function (a, b) { return b.data.m.score - a.data.m.score; });
      ms = ms.slice(0, 6);
      side.innerHTML = '<div><div class="hm-eyebrow">' + t("fileRank", f.rank, num(meta.totalFiles)) + "</div><h2>" + esc(d.data.name)
        + '</h2><div class="hm-path">' + esc(f.path) + "</div></div>"
        + '<div class="hm-verdict" style="--v:' + v[2] + '"><b>' + v[0] + "</b><span>" + v[1] + "</span></div>"
        + actions(link(f.path), t("openFile"), null) + formula(f, d)
        + meter(t("mCc"), f.cc + " · " + t("maxOf", maxCc), f.cc / maxCc)
        + meter(t("mDecay"), t("commitsN", f.rev) + " · " + f.decay.toFixed(2), f.decay / maxD)
        + meter(t("mCov"), (f.cov == null ? t("noData") : pct(f.cov)) + " · " + t("linesN", num(f.loc)), f.cov == null ? 0 : f.cov, "var(--hm-ok)")
        + '<div><div class="hm-eyebrow" style="margin-bottom:8px">' + t("hotMethods") + (classes.length > 1 ? " · " + t("classesN", classes.length) : "") + "</div>"
        + (ms.length ? '<ol class="hm-list">' + ms.map(function (n, i) {
          return row(i, n.data.name + "()", n.parent.data.name + " · CC " + n.data.m.cc, fmt(n.data.m.score));
        }).join("") + "</ol>" : '<p class="hm-note">' + t("noMethods") + "</p>") + "</div>";
      wireActions(null); wireRows(ms);
    }
    function renderClass(d) {
      var c = d.data, ms = d.children.slice().sort(function (a, b) { return b.data.m.score - a.data.m.score; });
      var start = Infinity, end = 0;
      c.methods.forEach(function (m) { start = Math.min(start, m.start); end = Math.max(end, m.end); });
      side.innerHTML = '<div><div class="hm-eyebrow">' + t("classOf", esc(c.file.base)) + "</div><h2>" + esc(c.name) + '</h2><div class="hm-path">' + esc(c.fqcn) + "</div></div>"
        + actions(link(c.file.path, start, end), t("openClass"), t("toFile"))
        + '<div class="hm-formula hm-three"><div class="hm-f"><b>' + c.methods.length + "</b><span>" + t("nMethods") + "</span></div>"
        + '<div class="hm-f"><b>' + sum(c.methods, function (m) { return m.cc; }) + "</b><span>" + t("ccSum") + "</span></div>"
        + '<div class="hm-f"><b>' + fmt(sum(c.methods, function (m) { return m.score; })) + "</b><span>" + t("methodScoreSum") + "</span></div></div>"
        + '<div><div class="hm-eyebrow" style="margin-bottom:8px">' + t("methodsByScore") + '</div><ol class="hm-list">' + ms.map(function (n, i) {
          return row(i, n.data.name + "()", "L" + n.data.m.start + "–" + n.data.m.end + " · CC " + n.data.m.cc, fmt(n.data.m.score));
        }).join("") + "</ol></div>";
      wireActions(d.parent); wireRows(ms);
    }
    function renderMethod(d) {
      var m = d.data.m, maxCc = maxOf(methods, function (x) { return x.cc; }) || 1, maxD = maxOf(methods, function (x) { return x.decay; }) || 1;
      side.innerHTML = '<div><div class="hm-eyebrow">' + t("methodRank", m.rank, num(meta.totalMethods)) + "</div><h2>" + esc(m.name) + "()</h2>"
        + '<div class="hm-path">' + esc(m.cls) + " · " + esc(m.file) + " · L" + m.start + "–" + m.end + "</div>"
        + (m.params.length ? '<div class="hm-path">(' + esc(m.params.join(", ")) + ")</div>" : "") + "</div>"
        + actions(link(m.file, m.start, m.end), t("openLines", m.start, m.end), t("toClass")) + formula(m, d)
        + meter(t("mCc"), m.cc + " · " + t("methodMax", maxCc), m.cc / maxCc)
        + meter(t("mDecay"), t("commitsN", m.rev) + " · " + m.decay.toFixed(2), m.decay / maxD)
        + meter(t("mCov"), (m.cov == null ? t("noData") : pct(m.cov)) + " · " + t("linesN", num(m.loc)), m.cov == null ? 0 : m.cov, "var(--hm-ok)");
      wireActions(d.parent);
    }
    function renderPanel() {
      var k = selected ? selected.data.kind : null;
      if (k === "file") renderFile(selected); else if (k === "class") renderClass(selected); else if (k === "method") renderMethod(selected); else renderOverview();
    }

    /* ---------- map ---------- */
    function tipHtml(d) {
      var k = d.data.kind;
      if (k === "file") {
        var f = d.data.file;
        return "<b>" + esc(f.rel) + "</b><br>" + t("tipRisk") + " " + fmt(f.score) + " · " + t("tipCc") + " " + f.cc + " · " + t("tipChange") + " " + f.decay.toFixed(2)
          + "<br>" + t("linesN", num(f.loc)) + " · " + t("commitsN", f.rev) + " · " + t("tipCov") + " " + (f.cov == null ? "–" : pct(f.cov));
      }
      if (k === "method") {
        var m = d.data.m;
        return "<b>" + esc(m.cls) + "." + esc(m.name) + "()</b><br>" + t("tipRisk") + " " + fmt(m.score) + " · " + t("tipCc") + " " + m.cc + " · " + t("tipChange") + " "
          + m.decay.toFixed(2) + "<br>L" + m.start + "–" + m.end + " · " + t("tipCov") + " " + (m.cov == null ? "–" : pct(m.cov));
      }
      if (k === "class") return "<b>" + esc(d.data.name) + "</b><br>" + t("tipMethods", d.data.methods.length) + " · " + t("tipCcSum") + " " + sum(d.data.methods, function (x) { return x.cc; });
      var fs = filesUnder(d);
      return "<b>" + esc(d.data.name) + "/</b><br>" + t("tipFiles", fs.length) + " · " + t("tipRiskSum") + " " + fmt(sum(fs, function (x) { return x.score; }))
        + " · " + t("tipCcSum") + " " + num(sum(fs, function (x) { return x.cc; }));
    }
    // One click goes one level deeper: package > file > class > method.
    function activate(d) {
      if (!d || d.depth === 0) { selected = null; zoom(root); renderPanel(); return; }
      var k = d.data.kind;
      if (k === "pkg") { zoom(d); return; }
      selected = d; renderPanel();
      if (k === "file") zoom(d.children ? (d.children.length === 1 ? d.children[0] : d) : d.parent);
      else if (k === "class") zoom(d);
      else zoom(d.parent);
    }
    // Leave room around a zoomed circle so its rim label clears the breadcrumbs.
    function frame(d) { return [d.x, d.y, d.r * (d.depth === 0 ? 2 : 2.3)]; }
    function build(keep) {
      w = stage.clientWidth || 900; h = svg.clientHeight || 620;
      var prevFocus = keep && focus ? keyOf(focus) : null, prevSel = keep && selected ? keyOf(selected) : null;
      layout(); scale = makeScale();
      focus = (prevFocus && findByKey(prevFocus)) || root;
      selected = (prevSel && findByKey(prevSel)) || null;
      svg.setAttribute("viewBox", (-w / 2) + " " + (-h / 2) + " " + w + " " + h);
      while (svg.firstChild) svg.removeChild(svg.firstChild);
      var g = document.createElementNS(SVG_NS, "g");
      all.forEach(function (d) {
        var c = document.createElementNS(SVG_NS, "circle");
        c.onmousemove = function (e) {
          if (d.depth === 0) { tip.hidden = true; return; }
          tip.hidden = false; tip.innerHTML = tipHtml(d);
          tip.style.left = Math.max(8, Math.min(e.clientX + 14, window.innerWidth - 340)) + "px"; tip.style.top = (e.clientY + 14) + "px";
        };
        c.onmouseleave = function () { tip.hidden = true; };
        c.onclick = function (e) { e.stopPropagation(); activate(d); };
        d.el = c; g.appendChild(c);
      });
      all.forEach(function (d) {
        if (d.depth === 0) { d.lab = null; return; }
        var tx = document.createElementNS(SVG_NS, "text");
        tx.textContent = labelText(d); d.lab = tx; g.appendChild(tx);
      });
      svg.appendChild(g);
      view = frame(focus); anim = null;
      restyle(); crumbs();
      byId("hm-legend-label").textContent = scale.label;
      byId("hm-lo").textContent = scale.lo; byId("hm-hi").textContent = scale.hi;
      var stops = []; for (var i = 0; i <= 10; i++) stops.push(rgb(heat(i / 10)));
      byId("hm-legend-bar").style.background = "linear-gradient(90deg," + stops.join(",") + ")";
      byId("hm-size-label").textContent = t({ cc: "optCc", score: "optScore", loc: "optLoc" }[state.sizeBy]);
      paint(0);
    }
    // Everything that changes only when focus or selection changes.
    function restyle() {
      var open = openFile();
      all.forEach(function (d) {
        var k = d.data.kind, leafLike = k === "file" || k === "method", fill = fillOf(d, open);
        d.shown = isVisible(d, open);
        d.top = k === "pkg" || k === "class" || d === open;      // container label sits on the rim
        d.el.style.display = d.shown ? "" : "none";
        d.el.setAttribute("fill", fill ? rgb(fill) : "none");
        d.el.setAttribute("fill-opacity", k === "pkg" ? "0.55" : k === "class" ? "0.85" : "1");
        d.el.setAttribute("pointer-events", "all");
        d.el.setAttribute("stroke", d === selected ? INK : d === open ? rgb(scale.file(d.data.file)) : leafLike ? "rgba(28,36,51,.2)" : "rgba(98,112,132,.55)");
        d.el.setAttribute("stroke-width", d === selected ? 3.5 : d === open ? 2.5 : 1);
        if (d === selected || d === open || leafLike) d.el.removeAttribute("stroke-dasharray"); else d.el.setAttribute("stroke-dasharray", "4 4");
        if (d.lab) {
          var dark = d.top || !fill || (0.299 * fill[0] + 0.587 * fill[1] + 0.114 * fill[2]) > 150;
          d.lab.setAttribute("fill", d.top ? MUTED : dark ? INK : "#ffffff");
          d.lab.setAttribute("font-size", d.top ? 12 : 11);
        }
      });
      dirty = true;
    }
    function labelShown(d) {
      if (!d.shown) return false;
      var k = d.data.kind;
      if (k === "pkg") return (d.parent === focus || d === focus || d.depth === 1) && d.sr > 38;
      if (k === "file" && d.children && d.top) return focus === d;
      if (k === "class") return d.sr > 34;
      return d.sr * 2 > labelText(d).length * 7 + 10;
    }
    function paint(now) {
      var k = Math.min(w, h) / view[2], time = now / 1000, cx = view[0], cy = view[1];
      all.forEach(function (d) {      // breadth-first: a parent's drift is ready before its children add theirs
        var sr = d.r * k;
        if (state.drift && d.parent) {
          var a = Math.min(isInner(d) || d.data.kind === "file" ? 2.6 : 1.6, sr * 0.12), tx = 0, ty = 0;
          // Bubbles near the pointer ease away from it; the one under the pointer stays put so it can be clicked.
          if (pointer && d.shown) {
            var dx = (d.x - cx) * k + d.parent.ox - pointer.x, dy = (d.y - cy) * k + d.parent.oy - pointer.y;
            var dist = Math.sqrt(dx * dx + dy * dy) || 1, gap = dist - sr;
            if (gap > 0 && gap < REACH) {
              var q = 1 - gap / REACH, push = q * q * Math.min(d.children ? 6 : 16, sr * 0.7);
              tx = dx / dist * push; ty = dy / dist * push;
            }
          }
          d.px += (tx - d.px) * 0.1; d.py += (ty - d.py) * 0.1;
          d.ox = d.parent.ox + a * Math.sin(time * d.w1 + d.p1) + d.px; d.oy = d.parent.oy + a * Math.cos(time * d.w2 + d.p2) + d.py;
        } else { d.ox = 0; d.oy = 0; d.px = 0; d.py = 0; }
        d.sx = (d.x - cx) * k + d.ox; d.sy = (d.y - cy) * k + d.oy; d.sr = sr;
        d.el.setAttribute("cx", d.sx.toFixed(2)); d.el.setAttribute("cy", d.sy.toFixed(2));
        d.el.setAttribute("r", Math.max(d.children ? 0 : 2.5, sr).toFixed(2));
        if (d.lab) {
          var on = labelShown(d);
          d.lab.style.display = on ? "" : "none";
          if (on) { d.lab.setAttribute("x", d.sx.toFixed(2)); d.lab.setAttribute("y", (d.top ? d.sy - sr + 15 : d.sy + 4).toFixed(2)); }
        }
      });
    }
    function zoom(d) {
      focus = d; restyle(); crumbs();
      var to = frame(d);
      if (reduceMotion) { view = to; anim = null; dirty = true; return; }
      anim = { from: view.slice(), to: to, start: null, dur: 650 };
    }
    function tick(now) {
      if (anim) {
        if (anim.start === null) anim.start = now;
        var p = Math.min(1, (now - anim.start) / anim.dur), e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
        view = [anim.from[0] + (anim.to[0] - anim.from[0]) * e, anim.from[1] + (anim.to[1] - anim.from[1]) * e,
          anim.from[2] * Math.pow(anim.to[2] / anim.from[2], e)];
        if (p >= 1) anim = null;
        dirty = true;
      }
      // Skip work while another tab of the report is showing.
      if ((state.drift || dirty) && host.offsetParent !== null) { paint(now); dirty = false; }
      schedule(tick);
    }
    var schedule = window.requestAnimationFrame
      ? function (fn) { window.requestAnimationFrame(fn); }
      : function (fn) { window.setTimeout(function () { fn(Date.now()); }, 33); };
    function crumbs() {
      var el = byId("hm-crumbs");
      el.innerHTML = "";
      var chain = focus.ancestors().reverse();
      chain.forEach(function (a, i) {
        var b = document.createElement("button");
        b.type = "button"; b.className = "hm-btn";
        b.textContent = a.data.kind === "pkg" ? a.data.name + "/" : a.data.name;
        b.onclick = function (e) {
          e.stopPropagation();
          if (a.depth === 0 || a.data.kind === "pkg") { if (a.depth === 0) selected = null; zoom(a); renderPanel(); }
          else { selected = a; renderPanel(); zoom(a); }
        };
        el.appendChild(b);
        if (i < chain.length - 1) el.appendChild(document.createTextNode("›"));
      });
    }

    /* ---------- chrome ---------- */
    function renderChrome() {
      Array.prototype.forEach.call(host.querySelectorAll("[data-hm-i18n]"), function (el) { el.textContent = t(el.getAttribute("data-hm-i18n")); });
      var stat = function (value, label) { return "<span><b>" + value + "</b>" + label + "</span>"; };
      byId("hm-stats").innerHTML = stat(num(meta.commits), t("commits"))
        + stat(files.length < meta.totalFiles ? files.length + " / " + num(meta.totalFiles) : num(meta.totalFiles), t("files"))
        + stat(methods.length < meta.totalMethods ? methods.length + " / " + num(meta.totalMethods) : num(meta.totalMethods), t("methods"))
        + stat(num(sum(files, function (f) { return f.cc; })), t("ccTotal"))
        + (meta.executableLines > 0 ? stat(pct(meta.coveredLines / meta.executableLines), t("lineCov")) : "");
      var trunc = [];
      if (files.length < meta.totalFiles) trunc.push(t("truncFiles", files.length, num(meta.totalFiles)));
      if (methods.length < meta.totalMethods) trunc.push(t("truncMethods", methods.length, num(meta.totalMethods)));
      byId("hm-trunc").textContent = trunc.join(" ");
      byId("hm-float").textContent = state.drift ? t("floatOn") : t("floatOff");
      byId("hm-float").setAttribute("aria-pressed", state.drift);
      byId("hm-lang").textContent = t("langBtn");
      var gapOpt = byId("hm-color").querySelector('option[value="gap"]');
      gapOpt.disabled = !hasCov;
    }
    byId("hm-float").onclick = function () { state.drift = !state.drift; dirty = true; renderChrome(); };
    byId("hm-lang").onclick = function () { lang = lang === "ko" ? "en" : "ko"; renderChrome(); build(true); renderPanel(); };
    byId("hm-size").onchange = function (e) { state.sizeBy = e.target.value; build(true); };
    byId("hm-color").onchange = function (e) { state.colorBy = e.target.value; build(true); };
    svg.onclick = function () { activate(root); };
    stage.onmousemove = function (e) {
      var r = svg.getBoundingClientRect();
      pointer = { x: e.clientX - r.left - r.width / 2, y: e.clientY - r.top - r.height / 2 };
    };
    stage.onmouseleave = function () { pointer = null; };
    var resizeTimer = null;
    function rebuildIfResized() {
      if (host.offsetParent === null) return;
      if ((stage.clientWidth || 900) !== w || (svg.clientHeight || 620) !== h) build(true);
    }
    window.addEventListener("resize", function () { window.clearTimeout(resizeTimer); resizeTimer = window.setTimeout(rebuildIfResized, 150); });
    // The map is laid out for its on-screen size, so re-measure when its tab is shown again.
    Array.prototype.forEach.call(document.querySelectorAll(".tab-button"), function (b) {
      b.addEventListener("click", function () { window.setTimeout(rebuildIfResized, 0); });
    });

    renderChrome(); build(false); renderPanel();
    host.setAttribute("data-hm-ready", all.length);
    schedule(tick);
  }
})();
