/* taqwa.world's prayer-time pages, made live.

   A city page is complete without this file: every day of both months is in the markup, and the
   row of the day the page was built is lit. This makes it follow the reader's clock in the
   city's own time zone, so someone in London looking at Jakarta sees Jakarta's today: it lights
   that row, turns the Today card into the app's countdown ring (the same next prayer and interval
   as TimelineBuilder, the same H:MM:SS in the same digits as CountdownFormatter, whose digit rule
   the generator reads from the app), and on a phone folds the days of the month already gone. On
   the index it filters the cities as you type.

   It makes no request and stores nothing. */
(function () {
  "use strict";

  var OBLIGATORY = [0, 2, 3, 4, 5]; // Fajr, Dhuhr, Asr, Maghrib, Isha: Sunrise is never "next"
  var FAJR = 0;
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
  cityPage(JSON.parse(holder.textContent));

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
    var stale = card.querySelector(".tt-stale");
    var fullDate = document.querySelector('[data-tt="full"]');
    var hijriDate = document.querySelector('[data-tt="hijri"]');
    var rows = [].slice.call(document.querySelectorAll("tr[data-i]"));
    var shown = data.today;

    /* The app's TimelineBuilder: "current" is the last obligatory prayer whose time has come;
       "next" the first still to come, or tomorrow's Fajr after Isha; the ring measures the
       interval between the two, which before Fajr began at yesterday's Isha. */
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
      if (fullDate) fullDate.textContent = day.full;
      if (hijriDate) hijriDate.textContent = day.hijri;
      rows.forEach(function (row) {
        var r = +row.getAttribute("data-i");
        row.classList.toggle("past", r < i);
        row.classList.toggle("is-today", r === i);
        if (r === i) row.setAttribute("aria-current", "date");
        else row.removeAttribute("aria-current");
      });
    }

    /* The page is older than its data: after Isha on its last day, or past it altogether. The
       ring says nothing rather than something wrong, and the notice points to the app. */
    function showStale(pastEverything) {
      if (stale) stale.hidden = false;
      label.textContent = "";
      count.textContent = "–";
      at.textContent = "";
      arc.setAttribute("stroke-dasharray", "0 " + CIRCUMFERENCE.toFixed(1));
      if (pastEverything) {
        shown = -1;
        items.forEach(function (li) { li.classList.remove("now"); li.classList.remove("past"); });
        rows.forEach(function (row) {
          row.classList.add("past");
          row.classList.remove("is-today");
          row.removeAttribute("aria-current");
        });
      }
    }

    function render() {
      var now = Math.floor(Date.now() / 1000);
      var s = state(now);
      if (!s) {
        showStale(true);
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
        showStale(false);
        return;
      }
      var left = Math.max(0, s.next.at - now);
      label.textContent = data.next[s.next.p];
      count.textContent = local(Math.floor(left / 3600)) + ":" + two(Math.floor(left % 3600 / 60)) + ":" + two(left % 60);
      at.textContent = days[s.next.day].t[s.next.p];
      arc.setAttribute("stroke-dasharray", (s.progress * CIRCUMFERENCE).toFixed(1) + " " + CIRCUMFERENCE.toFixed(1));
    }

    /* On a phone the days already gone this month fold behind one row, so today is near the
       top of the table; a tap brings them back. */
    function foldPast() {
      if (!window.matchMedia("(max-width: 720px)").matches) return;
      if (shown < 4 || shown >= data.first) return;
      var gone = rows.slice(0, shown);
      var first = gone[0].querySelector("th b").textContent;
      var last = gone[gone.length - 1].querySelector("th b").textContent;
      var row = document.createElement("tr");
      row.className = "fold";
      var cell = document.createElement("td");
      cell.colSpan = 8;
      var button = document.createElement("button");
      button.type = "button";
      var words = document.createElement("span");
      words.textContent = data.earlier;
      var range = document.createElement("span");
      range.textContent = first + "–" + last;
      var plus = document.createElement("b");
      plus.textContent = "+";
      button.appendChild(words);
      button.appendChild(range);
      button.appendChild(plus);
      button.addEventListener("click", function () {
        gone.forEach(function (r) { r.hidden = false; });
        row.parentNode.removeChild(row);
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

    tick();
    foldPast();
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) render();
    });
  }
})();
