package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TSaltIodLineMapper;
import com.fc.v2.model.auto.TSaltIodLine;
import com.fc.v2.service.impl.TSaltIodLineServiceImpl;

/**
 * 碘含量分档判定线：执行时段（立线/让位）在所有入口同一把口径，
 * 停用/删除任意时刻都不生效，让位之日不含；等于上限取高一档。
 */
public class TSaltIodLineServiceTest {

    private SaltDbSupport db;
    private TSaltIodLineServiceImpl svc;
    private TSaltIodLineMapper mapper;

    private static Date at(int y, int mo, int d, int h, int mi) {
        return Timestamp.valueOf(LocalDateTime.of(y, mo, d, h, mi));
    }

    /** 一条 10/20/30 的在场线，默认生效区间 2026-01-01..2026-12-31（不含） */
    private long line(long id, String code, int priority, int status,
                      java.util.Date start, java.util.Date end) {
        TSaltIodLine r = new TSaltIodLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "线");
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("20"));
        r.setTh3Max(new BigDecimal("30"));
        r.setEffStart(start);
        r.setEffEnd(end);
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        mapper.insert(r);
        return id;
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltIodLineServiceImpl());
        mapper = db.mapper(TSaltIodLineMapper.class);
    }

    @Test
    public void onlyEffectiveWithinWindow_listUsableCountAgree() {
        java.util.Date s = at(2026, 1, 1, 0, 0);
        java.util.Date e = at(2026, 12, 31, 0, 0); // 不含
        line(1, "A", 5, 0, s, e);
        line(2, "B", 5, 1, s, e); // 停用：任何时刻不生效
        // 删除的线
        TSaltIodLine del = new TSaltIodLine();
        del.setId(3L);
        del.setRuleCode("C");
        del.setTh1Max(new BigDecimal("10"));
        del.setTh2Max(new BigDecimal("20"));
        del.setTh3Max(new BigDecimal("30"));
        del.setEffStart(s);
        del.setEffEnd(e);
        del.setPriority(5);
        del.setStatus(0);
        del.setDelFlag(1);
        mapper.insert(del);

        // 窗口外（立线之前）：一条都不生效——不是任意时刻生效
        assertEquals(0, svc.listAvailable(at(2025, 12, 31, 23, 59)).size());
        assertEquals(0, svc.countAvailable(at(2025, 12, 31, 23, 59)));
        assertFalse(svc.usable(1L, at(2025, 12, 31, 23, 59)));

        // 窗口内
        assertEquals(1, svc.listAvailable(at(2026, 6, 1, 0, 0)).size());
        assertEquals(1, svc.countAvailable(at(2026, 6, 1, 0, 0)));
        assertTrue(svc.usable(1L, at(2026, 6, 1, 0, 0)));
        assertFalse(svc.usable(2L, at(2026, 6, 1, 0, 0)), "停用线不可用");
        assertFalse(svc.usable(3L, at(2026, 6, 1, 0, 0)), "删除线不可用");

        // 让位之日正点：不含，已失效（等号边界）
        assertFalse(svc.usable(1L, e));
        assertEquals(0, svc.listAvailable(e).size());
    }

    @Test
    public void evaluate_singleAndTop_shareWindowAndGrading() {
        java.util.Date s = at(2026, 1, 1, 0, 0);
        java.util.Date e = at(2026, 12, 31, 0, 0);
        line(1, "A", 5, 0, s, e);

        // 窗口外：两条路径都给 0（口径一致，不是一处生效一处不生效）
        assertEquals(0, svc.evaluate("A", new BigDecimal("15"), at(2025, 1, 1, 0, 0)));
        assertEquals(0, svc.evaluateTop(new BigDecimal("15"), at(2025, 1, 1, 0, 0)));

        java.util.Date within = at(2026, 6, 1, 0, 0);
        // 等于上限取高一档：10 取二档，20 取三档，30 取四档
        assertEquals(2, svc.evaluate("A", new BigDecimal("10"), within));
        assertEquals(3, svc.evaluate("A", new BigDecimal("20"), within));
        assertEquals(4, svc.evaluate("A", new BigDecimal("30"), within));
        // 严格低于上限留本档
        assertEquals(1, svc.evaluate("A", new BigDecimal("9.999"), within));
        assertEquals(2, svc.evaluate("A", new BigDecimal("10.001"), within));
        assertEquals(4, svc.evaluate("A", new BigDecimal("30.001"), within));
        // 单规则与多规则两条路径同档
        assertEquals(3, svc.evaluateTop(new BigDecimal("25"), within));
    }

    @Test
    public void priorityPicksTopRule_whenMultipleEffective() {
        java.util.Date s = at(2026, 1, 1, 0, 0);
        java.util.Date e = at(2026, 12, 31, 0, 0);
        line(1, "LOW", 1, 0, s, e);
        TSaltIodLine high = new TSaltIodLine();
        high.setId(2L);
        high.setRuleCode("HIGH");
        high.setTh1Max(new BigDecimal("8"));
        high.setTh2Max(new BigDecimal("16"));
        high.setTh3Max(new BigDecimal("24"));
        high.setEffStart(s);
        high.setEffEnd(e);
        high.setPriority(9);
        high.setStatus(0);
        high.setDelFlag(0);
        mapper.insert(high);

        java.util.Date within = at(2026, 6, 1, 0, 0);
        // 输入 18：LOW(10/20/30)→二档，HIGH(8/16/24)→三档；取优先级高的 HIGH → 三档
        assertEquals(3, svc.evaluateTop(new BigDecimal("18"), within));
        assertEquals("HIGH", svc.listAvailable(within).get(0).getRuleCode());
    }

    @Test
    public void missingRuleOrThresholdsOrOutOfRange_returnsZero() {
        java.util.Date within = at(2026, 6, 1, 0, 0);
        assertEquals(0, svc.evaluate("NOPE", new BigDecimal("5"), within));
        assertEquals(0, svc.evaluate(null, new BigDecimal("5"), within));
        assertEquals(0, svc.evaluate("A", null, within));
        assertEquals(0, svc.evaluateTop(new BigDecimal("-1"), within));
        assertEquals(0, svc.evaluateTop(new BigDecimal("100.01"), within));
        assertEquals(0, svc.evaluateTop(new BigDecimal("5"), within), "无可用规则给 0");
        assertFalse(svc.usable(null, within));
        assertFalse(svc.usable(999L, within));
    }
}
