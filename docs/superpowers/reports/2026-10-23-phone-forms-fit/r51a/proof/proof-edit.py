"""Scratch (Ruling 51a's first step): in a scratch copy's watchface.xml only, Arch's right-to-left
Gregorian Groups take month rule A through subText()/== with an Arabic literal:
greg_arch_rtl_13 (31.5 px, the 14-unit box) for 18 or fewer characters not ending in August,
greg_arch_rtl_18 (29.25 px, the 13-unit box) for 18 or fewer ending in August, and
greg_arch_rtl_more (25.3125 px) as it is (more than 18)."""
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
T = "[COMPLICATION.TEXT]"
n = "textLength(%s)" % T
month = 'subText(%s, clamp(%s - 5, 0, 99), %s)' % (T, n, n)
old13 = '<Transform target="alpha" value="(%s &lt;= 13) ? 255 : 0" />' % n
old18 = '<Transform target="alpha" value="(%s &gt; 13 &amp;&amp; %s &lt;= 18) ? 255 : 0" />' % (n, n)
new13 = '<Transform target="alpha" value=\'(%s &lt;= 18 &amp;&amp; %s != "أغسطس") ? 255 : 0\' />' % (n, month)
new18 = '<Transform target="alpha" value=\'(%s &lt;= 18 &amp;&amp; %s == "أغسطس") ? 255 : 0\' />' % (n, month)
assert s.count(old13) == 1 and s.count(old18) == 1, (s.count(old13), s.count(old18))
s = s.replace(old13, new13).replace(old18, new18)
open(p, "w", encoding="utf-8").write(s)
print("edited", p)
