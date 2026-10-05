package com.fc.v2.service.salt;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 全省一把尺：到期档口怎么数日子，全处只此一把（挑单、回写同一把）。
 *
 * 数法（按条文案）：
 * 1）跨夜那一段不数——按"日"数，不按钟点小时数；止点时刻原样保留；
 * 2）国家放的假不数、省里通令不办公的那几日不数（offDays），周末也不数；
 * 3）剩下的按工作日一天一天数到头；
 * 4）集中办案窗口那几日（caseWindows）日子照过、不算假，只是年检单这几日不许开口，
 *    由调用方凭 {@link #caseWindow(LocalDate)} 另压一层。
 *
 * 本类不沾 Spring/JDBC，单元可直接验。
 *
 * @author fuce
 * @date 2026-10-04
 */
public final class SaltWorkdayRuler {

    /** 不办公的日子：国家法定假 + 省通令不办公（周末由星期序另判） */
    private final Set<LocalDate> offDays;

    /** 全省集中办案窗口：手上真有已到档的也不许动 */
    private final Set<LocalDate> caseWindows;

    public SaltWorkdayRuler(Set<LocalDate> offDays, Set<LocalDate> caseWindows) {
        this.offDays = offDays == null ? Collections.<LocalDate>emptySet()
                : Collections.unmodifiableSet(new HashSet<LocalDate>(offDays));
        this.caseWindows = caseWindows == null ? Collections.<LocalDate>emptySet()
                : Collections.unmodifiableSet(new HashSet<LocalDate>(caseWindows));
    }

    /** 工作日：周一至周五且不在国家假/省令不办公名册内（窗口日仍是工作日，只是不许动单） */
    public boolean workday(LocalDate day) {
        if (day == null) {
            return false;
        }
        DayOfWeek w = day.getDayOfWeek();
        boolean weekend = w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
        return !weekend && !this.offDays.contains(day);
    }

    /** 集中办案窗口日：档口再到也压着，等下一轮开口 */
    public boolean caseWindow(LocalDate day) {
        return day != null && this.caseWindows.contains(day);
    }

    /**
     * 由止点时刻倒推 lead 个工作日，得到最可提前的开口时刻。
     * 跨夜不数：只挪日历日、保留止点的时分秒；倒推路上的周末/假日一律跳过不计数。
     * lead 为 0（或缺省）时开口点即止点本身。
     */
    public LocalDateTime openPoint(LocalDateTime due, int lead) {
        if (due == null) {
            return null;
        }
        int n = lead < 0 ? 0 : lead;
        LocalDate d = due.toLocalDate();
        while (n > 0) {
            d = d.minusDays(1);
            if (workday(d)) {
                n--;
            }
        }
        return LocalDateTime.of(d, due.toLocalTime());
    }

    /**
     * 该时刻是否已可开口：开口点 ≤ now（止点正压在 now 上算已到，不许顺延到下一轮）。
     */
    public boolean openable(LocalDateTime due, int lead, LocalDateTime now) {
        LocalDateTime point = openPoint(due, lead);
        return point != null && now != null && !point.isAfter(now);
    }
}
