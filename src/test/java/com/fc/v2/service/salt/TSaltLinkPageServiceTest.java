package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.service.impl.TSaltLinkPageServiceImpl;

/**
 * 企业品种挂接簿页：列表与详情同一本未删台账（不丢条件、不漏删标记、不强分页）；
 * site_id 与企业 bigint 主键同型不截断；推进/退回有界且核验串不丢；
 * 欠挂数 lack 始终同源 = 应挂 - 已挂（不为负）；删除走逻辑删。
 */
public class TSaltLinkPageServiceTest {

    private SaltDbSupport db;
    private TSaltLinkPageServiceImpl svc;
    private TSaltLinkPageMapper mapper;

    private long bigEntId = 30000000001L; // 超过 int 上界：int 会截断成另一张证

    private void ent(long id, int status) {
        TSaltEnt e = new TSaltEnt();
        e.setId(id);
        e.setSiteNo("JY" + id);
        e.setSiteName("厂" + id);
        e.setStatus(status);
        e.setDelFlag(0);
        db.mapper(TSaltEntMapper.class).insert(e);
    }

    private TSaltLinkPage page(long id, String billNo, Long siteId, Integer status) {
        TSaltLinkPage p = new TSaltLinkPage();
        p.setId(id);
        p.setBillNo(billNo);
        p.setSiteId(siteId);
        p.setShouldCount(10);
        p.setDoneCount(4);
        p.setStatus(status);
        p.setDelFlag(0);
        return p;
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltLinkPageServiceImpl());
        mapper = db.mapper(TSaltLinkPageMapper.class);
        ent(bigEntId, 0);
        ent(20L, 0);
        ent(21L, 1); // 摘牌
    }

    // —— 列表那条证和点进详情是同一张：bigint 企业号不被 int 截断 ——
    @Test
    public void largeSiteId_isNotTruncated_listAndDetailShowSameSite() {
        TSaltLinkPage p = page(1L, "B1", bigEntId, 0);
        assertEquals(1, svc.insertTSaltLinkPage(p));
        List<TSaltLinkPage> list = svc.selectTSaltLinkPageList(new QueryWrapper<TSaltLinkPage>());
        assertEquals(1, list.size());
        assertEquals(bigEntId, list.get(0).getSiteId());
        assertEquals(bigEntId, svc.selectTSaltLinkPageById(1L).getSiteId(), "列表与详情必须是同一家企业");
    }

    // —— 待处理集合筛干净：已删的不进列表，尊重调用方条件，不被强分页截断 ——
    @Test
    public void list_respectsCallerFilter_excludesDeleted_noForcedPaging() {
        for (int i = 1; i <= 15; i++) {
            mapper.insert(page(100 + i, "P" + i, 20L, 0));
        }
        TSaltLinkPage deleted = page(200L, "PDEL", 20L, 0);
        deleted.setDelFlag(1);
        mapper.insert(deleted);

        QueryWrapper<TSaltLinkPage> all = new QueryWrapper<TSaltLinkPage>();
        List<TSaltLinkPage> list = svc.selectTSaltLinkPageList(all);
        assertEquals(15, list.size(), "不强制每页 10 条，调用方未分页就给全量");
        assertTrue(list.stream().noneMatch(x -> x.getId() == 200L), "已删的不混进列表");

        List<TSaltLinkPage> one = svc.selectTSaltLinkPageList(
                new QueryWrapper<TSaltLinkPage>().eq("bill_no", "P5"));
        assertEquals(1, one.size(), "调用方的查询条件必须生效");
        assertNull(svc.selectTSaltLinkPageById(200L), "已删详情不露面");
    }

    // —— 欠挂（结存）同源：lack = should - done，非负 ——
    @Test
    public void lackCount_isDerivedFromShouldMinusDone() {
        TSaltLinkPage p = page(1L, "B1", 20L, 0);
        p.setLackCount(999); // 外部乱填不认
        svc.insertTSaltLinkPage(p);
        assertEquals(Integer.valueOf(6), mapper.selectById(1L).getLackCount());

        TSaltLinkPage upd = new TSaltLinkPage();
        upd.setId(1L);
        upd.setDoneCount(10);
        upd.setShouldCount(10);
        svc.updateTSaltLinkPage(upd);
        assertEquals(Integer.valueOf(0), mapper.selectById(1L).getLackCount(), "挂齐了结存为 0");

        TSaltLinkPage over = new TSaltLinkPage();
        over.setId(1L);
        over.setDoneCount(12); // 多挂不产生负结存
        over.setShouldCount(10);
        svc.updateTSaltLinkPage(over);
        assertEquals(Integer.valueOf(0), mapper.selectById(1L).getLackCount());
    }

    // —— 推进有界、null 状态不崩、核验串保留 ——
    @Test
    public void advance_isBounded_nullSafe_andKeepsVerifyToken() {
        TSaltLinkPage p = page(1L, "B1", 20L, null); // 存量 status 为空
        p.setContent("核验串XYZ");
        mapper.insert(p);
        assertEquals(1, svc.advanceTSaltLinkPage(1L));
        assertEquals(1, mapper.selectById(1L).getStatus().intValue());
        assertEquals(1, svc.advanceTSaltLinkPage(1L));
        assertEquals(2, mapper.selectById(1L).getStatus().intValue());
        assertEquals(0, svc.advanceTSaltLinkPage(1L), "已到末态不许再往前跳");
        assertEquals(2, mapper.selectById(1L).getStatus().intValue());
        assertEquals("核验串XYZ", mapper.selectById(1L).getContent(), "推进不得抹掉核验串");
    }

    // —— 退回有界、不清核验串 ——
    @Test
    public void revert_isBounded_andKeepsVerifyToken() {
        TSaltLinkPage p = page(1L, "B1", 20L, 1);
        p.setContent("核验串ABC");
        mapper.insert(p);
        assertEquals(1, svc.revertTSaltLinkPage(1L));
        assertEquals(0, mapper.selectById(1L).getStatus().intValue());
        assertEquals("核验串ABC", mapper.selectById(1L).getContent(), "退回不得清掉核验串");
        assertEquals(0, svc.revertTSaltLinkPage(1L), "头态退不动");
    }

    @Test
    public void insert_rejectsUnknownOrDelistedEnterprise_andDuplicateBillNo() {
        assertEquals(0, svc.insertTSaltLinkPage(page(1L, "BX", 999L, 0)), "无名录企业不开页");
        assertEquals(0, svc.insertTSaltLinkPage(page(2L, "BY", 21L, 0)), "摘牌企业不开页");
        assertEquals(1, svc.insertTSaltLinkPage(page(3L, "DUP", 20L, 0)));
        assertEquals(0, svc.insertTSaltLinkPage(page(4L, "DUP", 20L, 0)), "同号不重复落");
    }

    @Test
    public void delete_isLogical_andRemovesFromBoard() {
        mapper.insert(page(1L, "B1", 20L, 0));
        assertEquals(1, svc.deleteTSaltLinkPageById(1L));
        assertEquals(1, mapper.selectById(1L).getDelFlag().intValue(), "库里留痕、翻删除标记");
        assertNull(svc.selectTSaltLinkPageById(1L));
        assertEquals(0, svc.advanceTSaltLinkPage(1L), "已删不许再推进");
        assertEquals(0, svc.revertTSaltLinkPage(1L));
    }

    @Test
    public void batchDelete_isLogical() {
        mapper.insert(page(1L, "B1", 20L, 0));
        mapper.insert(page(2L, "B2", 20L, 0));
        assertTrue(svc.deleteTSaltLinkPageByIds("1,2") >= 2);
        assertEquals(1, mapper.selectById(1L).getDelFlag().intValue());
        assertEquals(1, mapper.selectById(2L).getDelFlag().intValue());
    }

    @Test
    public void missingRecord_isGuarded() {
        assertNull(svc.selectTSaltLinkPageById(999L));
        assertEquals(0, svc.advanceTSaltLinkPage(999L));
        assertEquals(0, svc.revertTSaltLinkPage(999L));
        assertEquals(0, svc.updateTSaltLinkPage(new TSaltLinkPage()));
        assertFalse(svc.deleteTSaltLinkPageByIds("999") > 0);
    }
}
