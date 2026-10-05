package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 全省一把尺 SaltWorkdayRuler 单测：跨夜不数、周末不数、国家假/省令不数、
 * 止点正压算已到且不顺延、窗口日另压。
 *
 * @author fuce
 * @date 2026-10-04
 */
public class SaltWorkdayRulerTest {

    private static SaltWorkdayRuler rulerWith(Set<LocalDate> off, Set<LocalDate> win) {
        return new SaltWorkdayRuler(off, win);
    }

    @Test
    public void zeroLead_dueInstantCountsAsDue_noDefer() {
        SaltWorkdayRuler ruler = rulerWith(null, null);
        LocalDateTime due = LocalDateTime.of(2026, 10, 16, 17, 0, 0); // 周五止点
        // 档口正压在当日止点上：算已到，不许顺延到下一轮（等号边界）
        assertTrue(ruler.openable(due, 0, due));
        assertFalse(ruler.openable(due, 0, due.minusSeconds(1)));
        assertTrue(ruler.openable(due, 0, due.plusDays(3)));
    }

    @Test
    public void leadOneAcrossWeekend_keepsDeadlineClock() {
        SaltWorkdayRuler ruler = rulerWith(null, null);
        LocalDateTime mondayDeadline = LocalDateTime.of(2026, 10, 19, 17, 30, 0); // 周一
        // 提前 1 个工作日：跨夜不数，周六周日不数，开口点落在上周五，时分秒原样保留
        LocalDateTime point = ruler.openPoint(mondayDeadline, 1);
        assertEquals(LocalDateTime.of(2026, 10, 16, 17, 30, 0), point);
        // 上周五止点已到 -> 可开口；周四同一钟点 -> 还没到
        assertTrue(ruler.openable(mondayDeadline, 1, LocalDateTime.of(2026, 10, 16, 17, 30, 0)));
        assertFalse(ruler.openable(mondayDeadline, 1, LocalDateTime.of(2026, 10, 15, 17, 30, 0)));
    }

    @Test
    public void nationalHolidaysAndProvinceOrders_areNotCounted() {
        // 国庆 10-01..10-07 国家假；10-08 省通令不办公
        Set<LocalDate> off = new HashSet<>();
        for (int d = 1; d <= 7; d++) {
            off.add(LocalDate.of(2026, 10, d));
        }
        off.add(LocalDate.of(2026, 10, 8));
        SaltWorkdayRuler ruler = rulerWith(off, null);

        // 10-09（周五）为止点；倒推 2 个工作日：
        // 10-08 省令不办公跳过，10-07..10-01 假跳过，09-30（周三）数第 1 个，09-29（周二）数第 2 个
        LocalDateTime due = LocalDateTime.of(2026, 10, 9, 17, 0, 0);
        assertEquals(LocalDateTime.of(2026, 9, 29, 17, 0, 0), ruler.openPoint(due, 2));

        assertFalse(ruler.workday(LocalDate.of(2026, 10, 1)));
        assertFalse(ruler.workday(LocalDate.of(2026, 10, 8)));
        assertFalse(ruler.workday(LocalDate.of(2026, 10, 10))); // 周六
        assertTrue(ruler.workday(LocalDate.of(2026, 9, 30)));
    }

    @Test
    public void caseWindowDay_isWorkdayButFrozen() {
        LocalDate winDay = LocalDate.of(2026, 10, 15); // 周四
        SaltWorkdayRuler ruler = rulerWith(null, java.util.Collections.singleton(winDay));
        // 窗口日仍是工作日（不进尺的假日册），只是由调用方另压一层、已到档也不许动
        assertTrue(ruler.workday(winDay));
        assertTrue(ruler.caseWindow(winDay));
        assertFalse(ruler.caseWindow(winDay.plusDays(1)));
        // 尺仍判档口已到——压不压在跑一轮那一层决定，尺不越权
        LocalDateTime due = LocalDateTime.of(2026, 10, 15, 9, 0, 0);
        assertTrue(ruler.openable(due, 0, LocalDateTime.of(2026, 10, 15, 10, 0, 0)));
    }

    @Test
    public void nullsAndNegativeLead_areSafe() {
        SaltWorkdayRuler ruler = rulerWith(null, null);
        assertFalse(ruler.openable(null, 0, LocalDateTime.now()));
        LocalDateTime due = LocalDateTime.of(2026, 10, 16, 17, 0, 0);
        // lead 为负按 0 处理，不回退
        assertEquals(due, ruler.openPoint(due, -3));
    }
}
