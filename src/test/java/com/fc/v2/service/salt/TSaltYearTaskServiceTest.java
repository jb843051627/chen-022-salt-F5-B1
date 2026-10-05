package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltCalendarMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltNotifyScriptMapper;
import com.fc.v2.mapper.auto.TSaltOfficerMapper;
import com.fc.v2.mapper.auto.TSaltOfficerScopeMapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltCalendar;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltNotifyScript;
import com.fc.v2.model.auto.TSaltOfficer;
import com.fc.v2.model.auto.TSaltOfficerScope;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.impl.TSaltYearTaskServiceImpl;

/**
 * 年检催办跑一轮 + 手动建条目的服务端口径测试（H2 真打库）。
 *
 * 覆盖本处被点过名的毛病：空轮不崩、止点正压算已到、窗口日压条、处理过的不回捞、
 * 逐条走岔不撂整轮、摘牌企业出待办、同一家企业一轮只绑一个执法员、
 * 手动建条目默认提前量/幂等/提前量定版/两册联动。
 */
public class TSaltYearTaskServiceTest {

    private SaltDbSupport db;
    private TSaltYearTaskServiceImpl svc;
    private TSaltYearTaskMapper taskMapper;

    private static Date at(int y, int mo, int d, int h, int mi) {
        return Timestamp.valueOf(LocalDateTime.of(y, mo, d, h, mi));
    }

    private static Date at(int y, int mo, int d, int h, int mi, int s) {
        return Timestamp.valueOf(LocalDateTime.of(y, mo, d, h, mi, s));
    }

    private TSaltEnt ent(long id, String no, int status, String contact) {
        TSaltEnt e = new TSaltEnt();
        e.setId(id);
        e.setSiteNo(no);
        e.setSiteName(no + "厂");
        e.setStatus(status);
        e.setContactPerson(contact);
        e.setDelFlag(0);
        return e;
    }

    private void officer(long id, String name, Integer limit, int status) {
        TSaltOfficer o = new TSaltOfficer();
        o.setId(id);
        o.setOfficerNo("O" + id);
        o.setOfficerName(name);
        o.setDailyLimit(limit);
        o.setStatus(status);
        o.setDelFlag(0);
        db.mapper(TSaltOfficerMapper.class).insert(o);
        TSaltOfficerScope s = new TSaltOfficerScope();
        s.setId(id * 10 + 1);
        s.setOfficerId(id);
        s.setBizType(0);
        s.setDelFlag(0);
        db.mapper(TSaltOfficerScopeMapper.class).insert(s);
    }

    private void script() {
        TSaltNotifyScript s = new TSaltNotifyScript();
        s.setId(1L);
        s.setScriptNo("NB-NJ");
        s.setScriptName("年检话本");
        s.setBizType(0);
        s.setStatus(0);
        s.setDelFlag(0);
        db.mapper(TSaltNotifyScriptMapper.class).insert(s);
    }

    private TSaltYearTask waitTask(long id, Long siteId, Date due, Integer lead) {
        TSaltYearTask t = new TSaltYearTask();
        t.setId(id);
        t.setItemNo("NJ" + id);
        t.setSiteId(siteId);
        t.setBizType(0);
        t.setDueAt(due);
        t.setAmount(lead == null ? null : new BigDecimal(lead));
        t.setScriptNo("NB-NJ");
        t.setStatus(0);
        t.setDelFlag(0);
        taskMapper.insert(t);
        return t;
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltYearTaskServiceImpl());
        taskMapper = db.mapper(TSaltYearTaskMapper.class);
        script();
        officer(1L, "严甲", 100, 0);
        officer(2L, "严乙", 100, 0);
        db.mapper(TSaltEntMapper.class).insert(ent(10L, "JY10", 0, "卤延年"));
        db.mapper(TSaltEntMapper.class).insert(ent(11L, "JY11", 0, "盐有余"));
        db.mapper(TSaltEntMapper.class).insert(ent(12L, "JY12", 1, null)); // 已摘牌
    }

    private TSaltYearTask reload(long id) {
        return taskMapper.selectById(id);
    }

    // —— 这次触发没有一条到期，程序却崩了 ——
    @Test
    public void emptyRound_doesNotCrash_andSaysNothingDue() {
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 15, 9, 0));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(0, r.getFailedCount());
        assertEquals(0, r.getOwingCount());
        assertTrue(r.getPickedItemIds().isEmpty());
        assertNotNull(r.getMessage());
    }

    // —— 恰好到期的条目没进去处理：止点正压当刻算已到（lead=0 等号边界，端到端）——
    @Test
    public void deadlineExactlyNow_isPickedAndDispatched() {
        Date due = at(2026, 10, 16, 17, 0);
        waitTask(1L, 10L, due, 0);
        SaltYearRoundResult r = svc.runRound(due);
        assertEquals(1, r.getDispatchedCount());
        assertEquals(1, reload(1L).getStatus().intValue());
        assertNotNull(reload(1L).getDispatchAt());
        assertNotNull(reload(1L).getOfficerId());
    }

    // —— 差一秒没到档：压着不点名，仍是待派 ——
    @Test
    public void oneSecondBeforeDeadline_isHeld() {
        Date due = at(2026, 10, 16, 17, 0);
        waitTask(1L, 10L, due, 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 16, 59, 59));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(0, reload(1L).getStatus().intValue());
        assertTrue(r.getHeldNotDueItemIds().contains(1L));
        assertTrue(r.getPickedItemIds().isEmpty());
    }

    // —— 执行窗口：窗口日已到档也整批压下，状态不动，等下一轮 ——
    @Test
    public void caseWindowDay_pressesDueItems() {
        TSaltCalendar c = new TSaltCalendar();
        c.setId(1L);
        c.setCalDate(at(2026, 10, 15, 0, 0));
        c.setDayKind(TSaltCalendar.KIND_CASE_WINDOW);
        c.setDelFlag(0);
        db.mapper(TSaltCalendarMapper.class).insert(c);
        waitTask(1L, 10L, at(2026, 10, 15, 9, 0), 0);

        SaltYearRoundResult r = svc.runRound(at(2026, 10, 15, 10, 0));
        assertTrue(r.isWindowDay());
        assertEquals(0, r.getDispatchedCount());
        assertEquals(1, r.getPressedItemIds().size());
        assertEquals(0, reload(1L).getStatus().intValue());
        assertNull(reload(1L).getOfficerId());

        // 次日（非窗口）再跑：压着的条目正常派出
        SaltYearRoundResult r2 = svc.runRound(at(2026, 10, 16, 9, 0));
        assertEquals(1, r2.getDispatchedCount());
        assertEquals(1, reload(1L).getStatus().intValue());
    }

    // —— 状态跟着处理走：派出/分不出的，下轮不再捞回 ——
    @Test
    public void processedItems_areNotPickedNextRound() {
        waitTask(1L, 10L, at(2026, 10, 16, 17, 0), 0);
        waitTask(2L, 12L, at(2026, 10, 16, 17, 0), 0); // 摘牌 → 分不出
        svc.runRound(at(2026, 10, 16, 17, 0));
        assertEquals(1, reload(1L).getStatus().intValue());
        assertEquals(2, reload(2L).getStatus().intValue());

        SaltYearRoundResult second = svc.runRound(at(2026, 10, 17, 9, 0));
        assertTrue(second.getPickedItemIds().isEmpty(), "处理过的不许混进待处理集合");
        assertEquals(0, second.getDispatchedCount());
        assertEquals(0, second.getFailedCount());
    }

    // —— 逐条处理时缺值解引用：一条走岔不撂下其余 ——
    @Test
    public void oneBrokenRecord_doesNotAbortWholeRound() {
        waitTask(1L, 10L, at(2026, 10, 16, 17, 0), 0);
        waitTask(2L, null, at(2026, 10, 16, 17, 0), 0); // site_id 缺失
        waitTask(3L, 11L, at(2026, 10, 16, 17, 0), 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 17, 0));
        assertEquals(2, r.getDispatchedCount());
        assertEquals(1, r.getFailedCount());
        assertEquals(2, reload(2L).getStatus().intValue());
        assertEquals(1, reload(1L).getStatus().intValue());
        assertEquals(1, reload(3L).getStatus().intValue());
    }

    // —— 停用食盐定点证的企业：一轮过后摘牌的出待办（转分不出，挂摘牌原委）——
    @Test
    public void delistedEnterprise_leavesWaitingBoard_afterRound() {
        waitTask(1L, 12L, at(2026, 10, 16, 17, 0), 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 17, 0));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(2, reload(1L).getStatus().intValue());
        assertEquals(TSaltYearTaskServiceImpl.REASON_DELISTED, reload(1L).getFailReason());
        long waiting = taskMapper.selectCount(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0).eq("status", 0).eq("site_id", 12));
        assertEquals(0, waiting);
    }

    // —— 两条分派路径口径统一：同一家企业同一轮只绑一个执法员，不派两个人 ——
    @Test
    public void sameEnterprise_inOneRound_bindsToOneOfficer() {
        waitTask(1L, 10L, at(2026, 10, 16, 9, 0), 0);
        waitTask(2L, 10L, at(2026, 10, 16, 10, 0), 0);
        waitTask(3L, 10L, at(2026, 10, 16, 11, 0), 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 12, 0));
        assertEquals(3, r.getDispatchedCount());
        Long officer = reload(1L).getOfficerId();
        assertNotNull(officer);
        assertEquals(officer, reload(2L).getOfficerId());
        assertEquals(officer, reload(3L).getOfficerId());
    }

    // —— 定额：一位接满后，别的企业派给另一位（负载均衡仍成立）——
    @Test
    public void quotaFull_spillsToOtherOfficer_forDifferentEnterprises() {
        db.mapper(TSaltOfficerMapper.class).update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSaltOfficer>()
                        .eq("id", 1L).set("daily_limit", 1));
        waitTask(1L, 10L, at(2026, 10, 16, 9, 0), 0);
        waitTask(2L, 11L, at(2026, 10, 16, 9, 0), 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 12, 0));
        assertEquals(2, r.getDispatchedCount());
        assertEquals(Long.valueOf(1L), reload(1L).getOfficerId());
        assertEquals(Long.valueOf(2L), reload(2L).getOfficerId(), "不同企业可溢派到在岗未满额的另一位");
    }

    // —— 同企业绑定的执法员当日满额：不拆给第二个人，按定额满挂分不出 ——
    @Test
    public void affinityOfficerFull_sameEnterpriseNotSplitToSecond() {
        // 企业10 已有一条当日派给执法员1（定额1，已满）
        TSaltYearTask prior = waitTask(9L, 10L, at(2026, 10, 16, 8, 0), 0);
        prior.setStatus(1);
        prior.setOfficerId(1L);
        prior.setDispatchAt(at(2026, 10, 16, 8, 30));
        taskMapper.updateById(prior);
        db.mapper(TSaltOfficerMapper.class).update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSaltOfficer>()
                        .eq("id", 1L).set("daily_limit", 1));

        waitTask(1L, 10L, at(2026, 10, 16, 17, 0), 0);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 16, 17, 0));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(1, r.getFailedCount());
        assertEquals(2, reload(1L).getStatus().intValue());
        assertEquals(TSaltYearTaskServiceImpl.REASON_QUOTA_FULL, reload(1L).getFailReason());
        assertNotEqualsOfficer2(reload(1L));
    }

    private void assertNotEqualsOfficer2(TSaltYearTask t) {
        assertFalse(Long.valueOf(2L).equals(t.getOfficerId()), "同企业不得拆派给第二位执法员");
        assertNull(t.getOfficerId());
    }

    // ================= 手动新建催办条目 =================

    // 默认提前量按常规填 3 个工作日；建出来即挂现行屏待办 ——
    @Test
    public void createManual_defaultsLeadAndAppearsOnBoard() {
        Date due = at(2026, 10, 19, 17, 0); // 周一
        TSaltYearTask t = svc.createManual(10L, 0, due, null, "NB-NJ", "窗口手工建");
        assertNotNull(t.getId());
        assertEquals(0, t.getStatus().intValue());
        assertEquals(new BigDecimal("3"), t.getAmount(), "最可提前几日按常规填 3 个工作日");
        assertTrue(svc.listBoard().stream().anyMatch(x -> x.getId().equals(t.getId())));
    }

    // 再往下推一步就看到了：开口点（止点前3工作日=上周三 10-14 17:00）已过，本轮即派出 ——
    @Test
    public void createdItem_isPickedOnNextRound() {
        Date due = at(2026, 10, 19, 17, 0); // 周一；提前3工作日 = 10-14（周三）17:00
        TSaltYearTask t = svc.createManual(10L, 0, due, null, "NB-NJ", null);
        SaltYearRoundResult r = svc.runRound(at(2026, 10, 15, 10, 0));
        assertTrue(r.getPickedItemIds().contains(t.getId()));
        assertEquals(1, reload(t.getId()).getStatus().intValue());
    }

    // 提前几日以建条当时那版为准：建后不改、跑轮只认行内定版值 ——
    @Test
    public void leadDaysAreSnapshottedAtCreation_andUsedAsFrozen() {
        Date due = at(2026, 10, 19, 17, 0); // 提前2工作日 = 10-15（周四）17:00
        TSaltYearTask t = svc.createManual(10L, 0, due, 2, "NB-NJ", null);
        assertEquals(new BigDecimal("2"), t.getAmount());
        // 10-15 16:00 尚未到开口点 → 压着
        SaltYearRoundResult early = svc.runRound(at(2026, 10, 15, 16, 0));
        assertFalse(early.getPickedItemIds().contains(t.getId()));
        // 10-15 17:00 正压 → 派出；用的是行内 2，不是常规默认 3
        SaltYearRoundResult onTime = svc.runRound(at(2026, 10, 15, 17, 0));
        assertTrue(onTime.getPickedItemIds().contains(t.getId()));
        assertEquals(0, reload(t.getId()).getAmount().compareTo(new BigDecimal("2")),
                "跑轮不得改写提前量（decimal 回读按数值比）");
    }

    // 同一批并着建两回要幂等：同企业+事由+止点只落一条，第二次回同一条 ——
    @Test
    public void createManual_isIdempotentForSameBatch() {
        Date due = at(2026, 10, 20, 17, 0);
        TSaltYearTask a = svc.createManual(10L, 0, due, null, "NB-NJ", "头一回");
        TSaltYearTask b = svc.createManual(10L, 0, due, null, "NB-NJ", "并着又建一回");
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.getId(), b.getId(), "同企业+事由+止点并着建两回，回的是同一条");
        long cnt = taskMapper.selectCount(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0).eq("site_id", 10).eq("biz_type", 0).eq("due_at", due));
        assertEquals(1, cnt);
    }

    // 催办册与年检岗那本联动：摘牌企业建不出来 ——
    @Test
    public void createManual_rejectsDelistedEnterprise() {
        assertNull(svc.createManual(12L, 0, at(2026, 10, 20, 17, 0), null, "NB-NJ", null));
    }

    // 联动：名录里查无此企业建不出来 ——
    @Test
    public void createManual_rejectsUnknownEnterprise() {
        assertNull(svc.createManual(999L, 0, at(2026, 10, 20, 17, 0), null, "NB-NJ", null));
    }

    // 话本缺失/停用/事由不对：建不出来（与跑轮同一把口径）——
    @Test
    public void createManual_rejectsBadScript() {
        Date due = at(2026, 10, 20, 17, 0);
        assertNull(svc.createManual(10L, 0, due, null, "NO-SUCH", null));
        assertNull(svc.createManual(10L, 0, due, null, null, null));
    }

    // 止点缺失建不出来 ——
    @Test
    public void createManual_rejectsMissingDeadline() {
        assertNull(svc.createManual(10L, 0, null, null, "NB-NJ", null));
    }

    // 不同止点是两批，各落一条 ——
    @Test
    public void differentDeadlines_areDistinctItems() {
        TSaltYearTask a = svc.createManual(10L, 0, at(2026, 10, 20, 17, 0), null, "NB-NJ", null);
        TSaltYearTask b = svc.createManual(10L, 0, at(2026, 10, 21, 17, 0), null, "NB-NJ", null);
        assertNotNull(a);
        assertNotNull(b);
        assertFalse(a.getId().equals(b.getId()));
    }
}
