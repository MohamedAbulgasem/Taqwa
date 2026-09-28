package world.taqwa.app.prayer.engine.registry.data

/**
 * Rabita Helsinki's Fajr start curve (Task 7g, as IRN's under ruling R48): its calendar keeps 18° from
 * autumn to early spring, then from April to August prints Fajr near the middle of the night (about 0.4
 * of it before sunrise), a rule no angle or fraction here reproduces day by day; so its own Fajr is its
 * curve. Rabita prints no imsak: the fast begins at this Fajr, and its end curve is
 * [EndOfEatingDawns.rabitaHelsinki].
 *
 * Derived parameters, not times: the sun's depression (this engine's NOAA formulas) at each printed Fajr
 * of its 2026 Helsinki calendar at 60.1699, 24.9384, placed on ruling R28's slots (the month and day of
 * 2028-01-01 + i; 29 February and the one day left out from their neighbours), each the smallest of the
 * day before, the day and the day after, rounded down to a hundredth of a degree (a later Fajr).
 * Provenance (ruling R42): Rabita Helsinki's 2026 Mawaqit calendar (restricted); only these depressions
 * are kept, the calendar is not committed.
 */
internal object RabitaCurves {
    private fun decode(encoded: String): DoubleArray =
        encoded.trim().split(' ').map { it.toInt() / 100.0 }.toDoubleArray().also { require(it.size == 366) }

    val fajr: DoubleArray = decode(
        "1794 1794 1794 1797 1797 1797 1799 1798 1798 1795 1795 1795 1798 1801 1802 1804 1803 1802 1800 1797 " +
        "1793 1793 1793 1796 1796 1797 1795 1795 1795 1794 1794 1794 1797 1799 1800 1801 1800 1799 1797 1794 " +
        "1794 1794 1796 1796 1796 1798 1798 1799 1798 1798 1794 1794 1794 1798 1794 1794 1794 1796 1797 1798 " +
        "1798 1798 1797 1796 1794 1794 1794 1798 1794 1794 1794 1797 1797 1798 1797 1797 1794 1794 1794 1797 " +
        "1800 1794 1794 1794 1794 1794 1800 1797 1797 1795 1795 1795 1795 1798 1800 1802 1800 1798 1798 1796 " +
        "1796 1796 1796 1796 1799 1799 1797 1797 1771 1746 1718 1686 1656 1622 1589 1559 1527 1495 1466 1435 " +
        "1404 1374 1347 1318 1289 1260 1235 1208 1181 1154 1131 1106 1081 1056 1033 1012 989 967 945 927 " +
        "907 889 870 854 836 821 804 790 774 762 747 736 723 712 701 692 681 673 664 658 " +
        "650 645 638 634 629 627 623 621 621 619 618 618 618 618 618 618 619 620 624 627 " +
        "630 635 641 646 654 660 668 676 686 695 706 716 728 741 755 767 782 798 814 830 " +
        "846 864 882 901 921 939 959 980 1002 1022 1045 1068 1092 1116 1139 1164 1189 1213 1240 1267 " +
        "1292 1320 1348 1374 1403 1432 1460 1490 1520 1548 1579 1611 1640 1672 1701 1729 1756 1783 1798 1798 " +
        "1799 1797 1797 1797 1800 1797 1796 1796 1796 1798 1796 1796 1796 1797 1796 1796 1796 1797 1797 1797 " +
        "1797 1795 1795 1795 1798 1800 1802 1794 1794 1794 1794 1794 1801 1800 1797 1794 1794 1794 1796 1796 " +
        "1796 1794 1794 1794 1796 1796 1796 1796 1796 1795 1795 1795 1797 1797 1794 1794 1794 1793 1793 1793 " +
        "1797 1794 1794 1794 1797 1799 1793 1793 1793 1795 1797 1798 1800 1801 1801 1802 1802 1802 1801 1800 " +
        "1799 1797 1795 1793 1793 1793 1797 1794 1794 1794 1793 1793 1793 1795 1795 1795 1793 1793 1793 1793 " +
        "1793 1793 1796 1799 1801 1802 1803 1802 1801 1799 1796 1796 1796 1796 1796 1796 1797 1795 1795 1795 " +
        "1799 1795 1795 1795 1796 1796",
    )
}
