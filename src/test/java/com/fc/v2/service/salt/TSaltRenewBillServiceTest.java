package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.impl.TSaltRenewBillServiceImpl;

/**
 * 延续换证核签单：同档算齐才进档（OR 一名 / AND need_count 名），
 * 三档（县初核/市复审/省核准）齐了才办结；否决真落状态；退回清落印、回在核；
 * 档口 node 与情形 status 每次同源一处 UPDATE。
 */
public class TSaltRenewBillServiceTest {

    private SaltDbSupport db;
    private TSaltRenewBillServiceImpl svc;
    private TSaltRenewBillMapper mapper;

    /** 0在核 1已办结 2已否决(挪回)；mode 0一名即可 1两名同判 */
    private TSaltRenewBill seed(long id, int node, int mode, Integer need, Integer sign, int status) {
        TSaltRenewBill b = new TSaltRenewBill();
        b.setId(id);
        b.setBillNo("HX" + id);
        b.setNodeNo(node);
        b.setSignMode(mode);
        b.setNeedCount(need);
        b.setSignCount(sign);
        b.setStatus(status);
        b.setDelFlag(0);
        mapper.insert(b);
        return b;
    }

    private TSaltRenewBill get(long id) {
        return mapper.selectById(id);
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltRenewBillServiceImpl());
        mapper = db.mapper(TSaltRenewBillMapper.class);
    }

    // OR：每档一票即进；三档走完才办结，不许跳过省核准
    @Test
    public void orMode_oneSignPerNode_walksThreeNodesThenPass() {
        seed(1, 0, 0, 1, 0, 0);
        assertEquals(1, svc.approve(1L, "县员", "过").getNodeNo().intValue());
        assertEquals(0, get(1).getStatus().intValue());
        assertEquals(0, get(1).getSignCount().intValue(), "进档后落印清零，新档从头数");
        assertEquals(2, svc.approve(1L, "市员", "过").getNodeNo().intValue());
        assertEquals(0, get(1).getStatus().intValue());
        // 第三档（省核准，node=2）签齐：node 停在 2，情形办结
        TSaltRenewBill done = svc.approve(1L, "省员", "过");
        assertEquals(2, done.getNodeNo().intValue());
        assertEquals(1, done.getStatus().intValue(), "三档齐才办结");
        // 办结后拒签/再签/退回都不动
        assertNull(svc.approve(1L, "省员", "再过"));
        assertNull(svc.reject(1L, "省员", "翻案"));
        assertNull(svc.rollback(1L, "退回"));
        assertEquals(1, get(1).getStatus().intValue());
    }

    // AND：本档须 need_count 名同判，一票只落印不进档
    @Test
    public void andMode_needsBothSignaturesBeforeAdvancing() {
        seed(2, 0, 1, 2, 0, 0);
        TSaltRenewBill first = svc.approve(2L, "县员甲", "同意");
        assertEquals(0, first.getNodeNo().intValue(), "只来一名，留在县初核");
        assertEquals(1, first.getSignCount().intValue());
        assertEquals(0, first.getStatus().intValue());
        TSaltRenewBill second = svc.approve(2L, "县员乙", "同意");
        assertEquals(1, second.getNodeNo().intValue(), "两名同判齐了才进市复审");
        assertEquals(0, second.getSignCount().intValue());
    }

    // AND 下 need_count 缺省按 1 兜底，不致永远卡在本档
    @Test
    public void andMode_missingNeedCount_defaultsOne() {
        seed(3, 0, 1, null, 0, 0);
        assertEquals(1, svc.approve(3L, "甲", "x").getNodeNo().intValue());
    }

    // 否决：真把情形落成已否决，不是空操作
    @Test
    public void reject_marksVeto() {
        seed(4, 1, 0, 1, 0, 0);
        TSaltRenewBill r = svc.reject(4L, "市员", "材料不实");
        assertEquals(2, r.getStatus().intValue());
        assertEquals(2, get(4).getStatus().intValue());
        // 已否决不再签/退
        assertNull(svc.approve(4L, "甲", "x"));
    }

    // 退回上一环节：档口 -1、本档落印清零、情形回在核；0 档退不动
    @Test
    public void rollback_clearsSignsAndReturnsActive() {
        seed(5, 2, 1, 2, 2, 0);
        TSaltRenewBill r = svc.rollback(5L, "省厅退回复审");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue(), "退回清掉本档落印");
        assertEquals(0, r.getStatus().intValue(), "情形回在核，与档口同源");
        assertEquals(0, svc.rollback(5L, "x").getNodeNo().intValue());
        assertNull(svc.rollback(5L, "头档退不动"));
    }

    @Test
    public void approverRequired_andMissingBill() {
        seed(6, 0, 0, 1, 0, 0);
        assertNull(svc.approve(6L, null, "x"));
        assertNull(svc.approve(6L, "  ", "x"));
        assertNull(svc.approve(999L, "甲", "x"));
        assertNull(svc.reject(999L, "甲", "x"));
        assertNull(svc.rollback(999L, "x"));
        assertEquals(0, get(6).getStatus().intValue());
    }

    @Test
    public void deletedBill_untouchable() {
        seed(7, 1, 0, 1, 0, 0);
        get(7).setDelFlag(1);
        mapper.updateById(get(7));
        assertNull(svc.approve(7L, "甲", "x"));
        assertNull(svc.reject(7L, "甲", "x"));
        assertNull(svc.rollback(7L, "x"));
    }
}
