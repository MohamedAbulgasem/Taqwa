/* taqwa.world's prayer-time pages, made live.

   A city page is complete without this file: every day of the months it shows is in the markup
   (this month, and the next once every day of it is checked: ruling R116), and the row of the day
   the page was built is lit. This makes it follow the reader's clock in the city's own time zone,
   so someone in London looking at Jakarta sees Jakarta's today: it lights that row, turns the
   Today card into the app's countdown ring (the same next prayer and interval as TimelineBuilder,
   the same H:MM:SS in the same digits as CountdownFormatter, whose digit rule the generator reads
   from the app), and folds the days of the month already gone. It never shows a time the page
   does not carry: on the page's last evening the next prayer is on no day it holds, so the card
   counts down to nothing (see showLeaf). On the index it filters the cities as you type.

   It makes no request and stores nothing. */
(function () {
  "use strict";

  var OBLIGATORY = [0, 2, 3, 4, 5]; // Fajr, Dhuhr, Asr, Maghrib, Isha: Sunrise is never "next"
  var FAJR = 0;
  var SUNRISE = 1;
  var ASR = 3;
  var MAGHRIB = 4;
  var ISHA = 5;
  var CIRCUMFERENCE = 2 * Math.PI * 88;

  /* What the index compares: lowercase, without Latin accents or Arabic vowel marks, as
     site/timetables.py folds the names it writes into data-k. */
  function fold(text) {
    return text.toLowerCase().normalize("NFKD")
      .replace(/[\u0300-\u036f\u064b-\u065f\u0670]/g, "")
      .replace(/\u2019/g, "'");
  }

  var filter = document.getElementById("city-filter");
  if (filter) {
    indexFilter(filter);
    return;
  }
  var holder = document.getElementById("tt-data");
  if (!holder) return;
  detailedView();
  printButtons();
  cautiousLine();
  cityPage(JSON.parse(holder.textContent));

  /* The detailed view (spec §3.4): a switch that shows the second lines the tables already
     carry, the end of eating under Fajr and the other school's Asr under Asr, and the mark on
     a day set by rule, through one class on <main>. Off when the page opens, never stored. */
  function detailedView() {
    var toggle = document.querySelector(".dv-toggle");
    if (!toggle) return;
    var row = toggle.closest(".dv-switch");
    if (row) row.hidden = false;
    toggle.addEventListener("click", function () {
      var on = toggle.getAttribute("aria-checked") !== "true";
      toggle.setAttribute("aria-checked", String(on));
      document.querySelector("main").classList.toggle("dv", on);
    });
  }

  /* "Print or save as PDF", one button per month: the month is marked on <html> and its section,
     the print stylesheet then prints that month alone with every day and the Hijri column, and
     the marks go when the dialog closes (afterprint, or the print media query ending where a
     browser fires no afterprint). Without script the buttons stay hidden and the browser's own
     print gives every month on the page. */
  function printButtons() {
    var buttons = [].slice.call(document.querySelectorAll("button.print[data-month]"));
    if (!buttons.length) return;
    var root = document.documentElement;
    function clear() {
      root.removeAttribute("data-print");
      [].slice.call(document.querySelectorAll("[data-print-target]")).forEach(function (section) {
        section.removeAttribute("data-print-target");
      });
    }
    window.addEventListener("afterprint", clear);
    if (window.matchMedia) {
      var printing = window.matchMedia("print");
      var ended = function (event) { if (!event.matches) clear(); };
      if (printing.addEventListener) printing.addEventListener("change", ended);
      else if (printing.addListener) printing.addListener(ended);
    }
    buttons.forEach(function (button) {
      var section = document.getElementById(button.getAttribute("data-month"));
      if (!section) return;
      button.hidden = false;
      button.addEventListener("click", function () {
        clear();
        root.setAttribute("data-print", section.id);
        section.setAttribute("data-print-target", "");
        window.print();
      });
    });
  }

  /* A cautious page's "Cautious times ›" line under the Today card's list is a link to the
     explainer; it opens the element as well, so the tap lands on the working, not the fold. */
  function cautiousLine() {
    var line = document.querySelector(".cautious-line");
    var details = document.getElementById("about");
    if (!line || !details) return;
    line.addEventListener("click", function () { details.open = true; });
  }

  function indexFilter(input) {
    var main = document.querySelector(".index");
    var links = [].slice.call(main.querySelectorAll(".cities a[data-k]"));
    var countries = [].slice.call(main.querySelectorAll(".country"));
    var regions = [].slice.call(main.querySelectorAll(".region"));
    var none = main.querySelector(".no-match");
    input.closest(".search").hidden = false;

    function apply() {
      var query = fold(input.value.trim());
      main.classList.toggle("filtering", query !== "");
      var shown = [];
      links.forEach(function (a) {
        var hit = query === "" || a.getAttribute("data-k").indexOf(query) !== -1;
        a.parentNode.toggleAttribute("data-hidden", !hit);
        if (hit) shown.push(a);
      });
      countries.forEach(function (c) {
        c.toggleAttribute("data-hidden", !c.querySelector("li:not([data-hidden])"));
      });
      regions.forEach(function (r) {
        r.toggleAttribute("data-hidden", !r.querySelector(".country:not([data-hidden])"));
      });
      none.hidden = shown.length > 0;
      return shown;
    }

    input.addEventListener("input", apply);
    input.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") return;
      var shown = apply();
      if (shown.length === 1) window.location.href = shown[0].href;
    });
    if (input.value) apply();
  }

  function cityPage(data) {
    var days = data.days;
    var byDate = {};
    days.forEach(function (day, i) { byDate[day.d] = i; });

    var dateInCity;
    try {
      dateInCity = new Intl.DateTimeFormat("en-CA", {
        timeZone: data.tz, year: "numeric", month: "2-digit", day: "2-digit"
      });
    } catch (e) {
      return; // a browser that does not know the zone keeps the page as it was built
    }

    function cityDate(ms) {
      var parts = {};
      dateInCity.formatToParts(new Date(ms)).forEach(function (p) { parts[p.type] = p.value; });
      return parts.year + "-" + parts.month + "-" + parts.day;
    }

    function local(text) {
      return String(text).replace(/[0-9]/g, function (d) { return data.digits.charAt(+d); });
    }

    function two(n) {
      return local(n < 10 ? "0" + n : String(n));
    }

    var card = document.querySelector(".today");
    var label = card.querySelector('[data-tt="label"]');
    var count = card.querySelector('[data-tt="count"]');
    var at = card.querySelector('[data-tt="at"]');
    var arc = card.querySelector(".ring-arc");
    var items = [].slice.call(card.querySelectorAll(".tl li"));
    var jumuah = card.querySelector('[data-tt="jumuah"]');
    var rules = [].slice.call(card.querySelectorAll(".tl .tag[data-rule]"));
    var polar = card.querySelector('[data-tt="polar"]');
    var stale = card.querySelector(".tt-stale");
    var fullDate = document.querySelector('[data-tt="full"]');
    var hijriDate = document.querySelector('[data-tt="hijri"]');
    var rows = [].slice.call(document.querySelectorAll("tr[data-i]"));
    var shown = data.today;
    var cautious = data.mn ? cautiousParts() : null;

    /* The app's TimelineBuilder: "current" is the last obligatory prayer whose time has come;
       "next" the first still to come, or tomorrow's Fajr after Isha; the ring measures the
       interval between the two, which before Fajr began at yesterday's Isha. As on the app's
       Prayer screen, Fajr is over at sunrise and Asr at sunset, so nothing is current from
       sunrise to Dhuhr or from sunset to Maghrib; the ring's interval is unchanged. */
    function state(now) {
      var i = byDate[cityDate(now * 1000)];
      if (i === undefined) return null;
      var today = days[i];
      var current = null;
      var previous = null;
      var next = null;
      OBLIGATORY.forEach(function (p) {
        if (today.e[p] <= now) {
          current = p;
          previous = today.e[p];
        } else if (next === null) {
          next = { p: p, at: today.e[p], day: i };
        }
      });
      if (current === FAJR && now >= today.e[SUNRISE]) current = null;
      if (current === ASR && now >= today.s) current = null;
      if (next === null) {
        if (i + 1 >= days.length) return { i: i, current: current, next: null };
        next = { p: FAJR, at: days[i + 1].e[FAJR], day: i + 1 };
      }
      if (previous === null && i > 0) previous = days[i - 1].e[ISHA];
      var total = previous === null ? 0 : next.at - previous;
      var progress = total <= 0 ? 0 : Math.min(1, Math.max(0, (now - previous) / total));
      return { i: i, current: current, next: next, progress: progress };
    }

    function showDay(i) {
      if (i === shown) return;
      shown = i;
      var day = days[i];
      items.forEach(function (li) {
        li.querySelector(".t").textContent = day.t[+li.getAttribute("data-p")];
      });
      if (jumuah) jumuah.hidden = !day.f;
      /* The app's "Set by rule" pill after a prayer the high-latitude rule set today, and its
         line for a day the sun neither rises nor sets. */
      rules.forEach(function (tag) {
        tag.hidden = (day.r || []).indexOf(+tag.getAttribute("data-rule")) === -1;
      });
      if (polar) polar.hidden = !day.p;
      if (fullDate) fullDate.textContent = day.full;
      if (hijriDate) hijriDate.textContent = day.hijri;
      rows.forEach(function (row) {
        var r = +row.getAttribute("data-i");
        row.classList.toggle("past", r < i);
        row.classList.toggle("is-today", r === i);
        if (r === i) row.setAttribute("aria-current", "date");
        else row.removeAttribute("aria-current");
      });
      if (cautious) cautious.show(i);
    }

    /* A cautious place's explainer for the reader's today (spec §3.3, §9.3): which timetable
       decides each time, the Maghrib cap's sentence on a day it decided, when to stop eating,
       and the Fajr ruler drawn from the members' own minutes. Without script the built day's
       rows and the ruler's sentence stand. */
    function cautiousParts() {
      var names = data.mn;
      var words = data.ruler;
      var decideRows = [].slice.call(document.querySelectorAll(".decides li[data-p]"));
      var cap = document.querySelector('[data-tt="cap"]');
      var stop = document.querySelector('[data-tt="stop"]');
      var figure = document.querySelector("figure.ruler.fajr");
      var sentence = figure ? figure.querySelector(".ruler-text") : null;
      var SVG = "http://www.w3.org/2000/svg";
      var W = 342;
      var X0 = 24;
      var X1 = 318;

      /* The members whose own instant is the one shown — the latest to begin it, for sunrise
         the earliest, for a capped Maghrib the most-followed's — as site/timetables.py picks
         them for the built day; a repaired day falls back to the latest (earliest) member. */
      function deciders(day, p) {
        var on = [];
        day.me.forEach(function (m, k) { if (m[p] === day.e[p]) on.push(k); });
        if (!on.length) {
          var pick = null;
          day.me.forEach(function (m) {
            if (pick === null || (p === SUNRISE ? m[p] < pick : m[p] > pick)) pick = m[p];
          });
          day.me.forEach(function (m, k) { if (m[p] === pick) on.push(k); });
        }
        return on;
      }

      function chip(text, on) {
        var node = document.createElement(on ? "b" : "span");
        node.className = on ? "chip on" : "chip";
        node.textContent = text;
        return node;
      }

      /* A sentence's {name} and %1$s placeholders filled (a function, so a name holding "$" is
         never read as a replacement pattern). */
      function put(template, values) {
        return template.replace(/\{(\w+)\}|%(\d)\$s/g, function (match, name, index) {
          var key = name || index;
          return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match;
        });
      }

      /* The app's maghribCapToday: the member whose Maghrib is the one shown (else the most
         followed, the first) and those whose own is later. */
      function capSentence(day, i) {
        if (i === data.today) return data.capText;
        var followed = -1;
        var later = [];
        day.me.forEach(function (m, k) {
          if (m[MAGHRIB] > day.e[MAGHRIB]) later.push(names[k]);
          if (followed < 0 && m[MAGHRIB] === day.e[MAGHRIB]) followed = k;
        });
        if (followed < 0) followed = 0;
        return put(data.capT, { "1": names[followed], "2": later.join(data.comma) });
      }

      /* The members grouped by their Fajr minute, earliest first. */
      function fajrGroups(day) {
        var groups = [];
        day.me.forEach(function (m, k) {
          var group = null;
          groups.forEach(function (g) { if (g.at === m[FAJR]) group = g; });
          if (group) group.ks.push(k);
          else groups.push({ at: m[FAJR], ks: [k] });
        });
        groups.sort(function (a, b) { return a.at - b.at; });
        return groups;
      }

      /* The ruler as a sentence, as site/timetables.py writes it for the built day. */
      function rulerSentence(day, groups) {
        var list = groups.map(function (g, n) {
          return put(n === 0 ? words.beginsFirst : words.beginsAt, {
            names: g.ks.map(function (k) { return names[k]; }).join(data.comma),
            fajr: words.fajr,
            time: day.m[g.ks[0]][FAJR]
          });
        }).join(" · ");
        return put(words.text, { eat: day.x, list: list, fajr: words.fajr, shown: day.t[FAJR] });
      }

      /* Labels laid in rows so that none overlaps its neighbour: a label at a tick hangs off
         it towards the middle of the drawing (so it stays beside its minute however long it
         is), a label on the box is centred, and each takes the first row with room after the
         label before it; the widths are estimated from the text. Returns the number of rows. */
      function lay(labels) {
        var ends = [];
        labels.forEach(function (label) {
          var width = label.text.length * 6.2 + 4;
          var cx = label.x;
          if (!label.centred) cx += label.x < W / 2 ? width / 2 : -width / 2;
          label.cx = Math.max(width / 2, Math.min(W - width / 2, cx));
          label.left = label.cx - width / 2;
          label.right = label.cx + width / 2;
        });
        labels.sort(function (a, b) { return a.left - b.left; });
        labels.forEach(function (label) {
          var row = 0;
          while (row < ends.length && ends[row] + 6 > label.left) row++;
          ends[row] = label.right;
          label.row = row;
        });
        return ends.length;
      }

      function node(name, attrs, text) {
        var made = document.createElementNS(SVG, name);
        Object.keys(attrs).forEach(function (key) { made.setAttribute(key, attrs[key]); });
        if (text !== undefined) made.textContent = text;
        return made;
      }

      /* B's "Fajr today, minute by minute" (spec §3.3.1): a scale from the end of eating to the
         Fajr shown, a tick and a name per member minute, the stretch where not all have begun
         it hatched, the shown minute in amber. Mirrored on an RTL page; the text never flipped. */
      function drawRuler(day, i) {
        if (!figure || !day.me || !day.me.length) return;
        var groups = fajrGroups(day);
        var start = day.xe;
        var shownAt = day.e[FAJR];
        var span = Math.max(60, shownAt - start);
        function xOf(epoch) {
          var x = X0 + (Math.min(Math.max(epoch, start), shownAt) - start) / span * (X1 - X0);
          return data.rtl ? W - x : x;
        }
        var above = [{ x: xOf(start), text: words.stop, weight: 700 }];
        var below = [{ x: xOf(start), text: day.x, weight: 700, centred: true }];
        groups.forEach(function (g) {
          above.push({ x: xOf(g.at), text: g.ks.map(function (k) { return names[k]; }).join(" · "), weight: 700 });
          // A member minute that is the shown one, or the end of eating, has its time already.
          if (g.at !== shownAt && g.at !== start) below.push({ x: xOf(g.at), text: day.m[g.ks[0]][FAJR], weight: 700, centred: true });
        });
        above.push({ x: xOf(shownAt), text: words.shown, weight: 700, accent: true });
        below.push({ x: xOf(shownAt), text: day.t[FAJR], weight: 800, accent: true, centred: true });
        var boxFrom = xOf(groups[0].at);
        var boxTo = xOf(shownAt);
        var boxed = Math.abs(boxTo - boxFrom) >= 2;
        if (boxed) above.push({ x: (boxFrom + boxTo) / 2, text: words.notAll, weight: 400, quiet: true, centred: true });
        var lineY = 28 + 14 * lay(above);
        var height = lineY + 14 * lay(below) + 12;
        var svg = node("svg", { viewBox: "0 0 " + W + " " + height, role: "img", "aria-label": rulerSentence(day, groups) });
        var defs = node("defs", {});
        var hatch = node("pattern", { id: "fajr-hatch", width: "6", height: "6", patternUnits: "userSpaceOnUse", patternTransform: "rotate(45)" });
        hatch.appendChild(node("line", { x1: "0", y1: "0", x2: "0", y2: "6", stroke: "var(--hair)", "stroke-width": "2" }));
        defs.appendChild(hatch);
        svg.appendChild(defs);
        if (boxed) {
          svg.appendChild(node("rect", {
            x: Math.min(boxFrom, boxTo), y: lineY - 16, width: Math.abs(boxTo - boxFrom), height: "16", fill: "url(#fajr-hatch)"
          }));
        }
        svg.appendChild(node("line", {
          x1: data.rtl ? W - X1 : X0, y1: lineY, x2: data.rtl ? W - X0 : X1, y2: lineY, stroke: "var(--t3)", "stroke-width": "1"
        }));
        [xOf(start)].concat(groups.map(function (g) { return xOf(g.at); })).forEach(function (x) {
          svg.appendChild(node("line", { x1: x, y1: lineY - 5, x2: x, y2: lineY + 5, stroke: "var(--t3)", "stroke-width": "1" }));
        });
        svg.appendChild(node("line", {
          x1: xOf(shownAt), y1: lineY - 18, x2: xOf(shownAt), y2: lineY + 7, stroke: "var(--accent)", "stroke-width": "2"
        }));
        above.forEach(function (label) {
          svg.appendChild(node("text", {
            x: label.cx, y: lineY - 22 - 14 * label.row, "font-size": "10.5", "font-weight": label.weight,
            fill: label.accent ? "var(--accent-text)" : (label.quiet ? "var(--t2)" : "var(--t1)"), "text-anchor": "middle"
          }, label.text));
        });
        below.forEach(function (label) {
          svg.appendChild(node("text", {
            x: label.cx, y: lineY + 20 + 14 * label.row, "font-size": "11", "font-weight": label.weight,
            fill: label.accent ? "var(--accent-text)" : "var(--t1)", "text-anchor": "middle"
          }, label.text));
        });
        var old = figure.querySelector("svg");
        if (old) figure.removeChild(old);
        figure.insertBefore(svg, sentence);
        if (sentence) sentence.hidden = true;
      }

      function show(i) {
        var day = days[i];
        if (!day || !day.me) return;
        decideRows.forEach(function (row) {
          var p = +row.getAttribute("data-p");
          row.querySelector(".shown").textContent = day.t[p];
          var who = row.querySelector(".who");
          while (who.firstChild) who.removeChild(who.firstChild);
          var on = deciders(day, p);
          names.forEach(function (name, k) {
            var isOn = on.indexOf(k) !== -1;
            who.appendChild(chip(isOn ? name : name + " " + day.m[k][p], isOn));
          });
        });
        if (cap) {
          cap.hidden = !day.cap;
          if (day.cap) cap.textContent = capSentence(day, i);
        }
        if (stop) stop.textContent = put(data.stop, { time: day.x });
        drawRuler(day, i);
      }

      return { show: show };
    }

    /* The page's last evening: Isha has begun on the last day the page carries, and the next
       prayer, tomorrow's Fajr, is on no day it holds. On a page that shows one month (ruling R116)
       that is the last evening of every month. Today's times are still right, so the page is not
       out of date and says nothing of the kind; it counts down to nothing it cannot show. The
       card is the day's calendar leaf, as without script (weekday, day, month), with an empty
       ring, and the list keeps Isha current as the app does after Isha. */
    function showLeaf() {
      var leaf = data.end || { w: "", n: "–", m: "" }; // a page cached from before the leaf: a dash
      if (stale) stale.hidden = true;
      label.textContent = leaf.w;
      count.textContent = leaf.n;
      at.textContent = leaf.m;
      arc.setAttribute("stroke-dasharray", "0 " + CIRCUMFERENCE.toFixed(1));
    }

    /* The page is older than its data: the reader's day is past every day it carries. The ring
       says nothing rather than something wrong, and the notice points to the app. */
    function showStale() {
      if (stale) stale.hidden = false;
      label.textContent = "";
      count.textContent = "–";
      at.textContent = "";
      arc.setAttribute("stroke-dasharray", "0 " + CIRCUMFERENCE.toFixed(1));
      shown = -1;
      items.forEach(function (li) { li.classList.remove("now"); li.classList.remove("past"); });
      rows.forEach(function (row) {
        row.classList.add("past");
        row.classList.remove("is-today");
        row.removeAttribute("aria-current");
      });
    }

    function render() {
      var now = Math.floor(Date.now() / 1000);
      var s = state(now);
      if (!s) {
        showStale();
        return;
      }
      showDay(s.i);
      var today = days[s.i];
      items.forEach(function (li) {
        var p = +li.getAttribute("data-p");
        li.classList.toggle("now", p === s.current);
        li.classList.toggle("past", p !== s.current && today.e[p] <= now);
      });
      if (!s.next) {
        showLeaf();
        return;
      }
      if (stale) stale.hidden = true;
      var left = Math.max(0, s.next.at - now);
      label.textContent = data.next[s.next.p];
      count.textContent = local(Math.floor(left / 3600)) + ":" + two(Math.floor(left % 3600 / 60)) + ":" + two(left % 60);
      at.textContent = days[s.next.day].t[s.next.p];
      arc.setAttribute("stroke-dasharray", (s.progress * CIRCUMFERENCE).toFixed(1) + " " + CIRCUMFERENCE.toFixed(1));
    }

    /* At every width the days already gone this month fold behind one row once today is the
       4th or later (spec §3.4; `shown` counts from 0 on the 1st), so today is near the top of
       the table; a tap brings them back and puts focus on the first of them, where the row
       was. The print stylesheet prints every row whatever the fold. */
    function foldPast() {
      if (shown < 3 || shown >= data.first) return;
      var gone = rows.slice(0, shown);
      var first = gone[0].querySelector("th b").textContent;
      var last = gone[gone.length - 1].querySelector("th b").textContent;
      var row = document.createElement("tr");
      row.className = "fold";
      var cell = document.createElement("td");
      cell.colSpan = 8;
      var button = document.createElement("button");
      button.type = "button";
      button.setAttribute("aria-expanded", "false");
      // Its name is what it shows, with a pause: "Earlier this month, 1–27"; the "+" is drawn.
      button.setAttribute("aria-label", data.earlier + data.comma + first + "–" + last);
      var words = document.createElement("span");
      words.textContent = data.earlier;
      var range = document.createElement("span");
      range.textContent = first + "–" + last;
      var plus = document.createElement("b");
      plus.setAttribute("aria-hidden", "true");
      plus.textContent = "+";
      button.appendChild(words);
      button.appendChild(range);
      button.appendChild(plus);
      button.addEventListener("click", function () {
        button.setAttribute("aria-expanded", "true");
        gone.forEach(function (r) { r.hidden = false; });
        row.parentNode.removeChild(row);
        var head = gone[0].querySelector("th");
        if (head) {
          head.tabIndex = -1;
          head.focus();
        }
      });
      cell.appendChild(button);
      row.appendChild(cell);
      gone.forEach(function (r) { r.hidden = true; });
      gone[0].parentNode.insertBefore(row, gone[0]);
    }

    function tick() {
      render();
      window.setTimeout(tick, 1000 - (Date.now() % 1000) + 10);
    }

    if (cautious) cautious.show(shown); // the built day's ruler is drawn before the day can move
    tick();
    foldPast();
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) render();
    });
  }
})();
