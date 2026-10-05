package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.service.impl.TSaltRecordFlowServiceImpl;

/**
 * 跨省经营备案单：环节一档档走（+1），到顶封卷，情形与环节同源；
 * 封卷后不改不删不回退；列表只看未删；删除走逻辑删。
 */
public class TSaltRecordFlowServiceTest {

    private SaltDbSupport db;
    private TSaltRecordFlowServiceImpl svc;
    private TSaltRecordFlowMapper mapper;

    private TSaltRecordFlow seed(long id, Integer stage, Integer status, int del) {
        TSaltRecordFlow r = new TSaltRecordFlow();
        r.setId(id);
        r.setBizNo("BA" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(del);
        mapper.insert(r);
        return r;
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltRecordFlowServiceImpl());
        mapper = db.mapper(TSaltRecordFlowMapper.class);
    }

    @Test
    public void advanceWalksOneStageAtATime_andSealsAtTop() {
        seed(1, 0, 1, 0);
        // 0→1
        TSaltRecordFlow r1 = svc.advance(1L, "材料核验");
        assertEquals(1, r1.getStage().intValue());
        assertEquals(1, r1.getStatus().intValue(), "未到顶仍是在办");
        assertEquals("材料核验", r1.getLastAction());
        // 1→2
        assertEquals(2, svc.advance(1L, "生效确认").getStage().intValue());
        // 2→3 到顶：环节与情形同源落封卷
        TSaltRecordFlow sealed = svc.advance(1L, "缴回封卷");
        assertEquals(3, sealed.getStage().intValue());
        assertEquals(2, sealed.getStatus().intValue(), "到顶必须封卷，不能赖在在办被下轮再捞");
    }

    @Test
    public void advanceNullStage_startsFromZero() {
        seed(2, null, null, 0);
        assertEquals(1, svc.advance(2L, "受理").getStage().intValue());
        assertEquals(1, svc.advance(2L, "x").getStatus().intValue());
    }

    @Test
    public void sealedBill_cannotAdvanceRollbackEditOrDelete() {
        seed(3, 3, 2, 0);
        assertNull(svc.advance(3L, "再推"));
        assertNull(svc.rollback(3L, "退回"));
        assertFalse(svc.updateContent(3L, "改内容"));
        assertFalse(svc.remove(3L));
        assertEquals(3, mapper.selectById(3L).getStage().intValue());
        assertEquals(0, mapper.selectById(3L).getDelFlag().intValue());
    }

    @Test
    public void rollbackGoesBackExactlyOneStage() {
        seed(4, 2, 1, 0);
        TSaltRecordFlow r = svc.rollback(4L, "补材料");
        assertEquals(1, r.getStage().intValue(), "只退一档，不是一把归零");
        assertEquals(1, r.getStatus().intValue());
        assertEquals(0, svc.rollback(4L, "x").getStage().intValue());
        assertNull(svc.rollback(4L, "没档可退"), "0 档退不动");
    }

    @Test
    public void deletedBills_areInvisibleAndImmutable() {
        seed(5, 1, 1, 0);
        seed(6, 1, 1, 1); // 已删
        List<TSaltRecordFlow> list = svc.selectTSaltRecordFlowList(new QueryWrapper<TSaltRecordFlow>());
        assertEquals(1, list.size());
        assertEquals(Long.valueOf(5L), list.get(0).getId());

        // 逻辑删除：库里还在，只是 del_flag 翻 1
        assertTrue(svc.remove(5L));
        assertEquals(1, mapper.selectById(5L).getDelFlag().intValue());
        assertNull(svc.selectTSaltRecordFlowById(5L), "按主键回查也不该再露已删单据");
        // 已删的不许再挪步
        assertNull(svc.advance(5L, "x"));
        assertNull(svc.rollback(5L, "x"));
        assertFalse(svc.updateContent(5L, "y"));
    }

    @Test
    public void updateContent_worksWhileActive_only() {
        seed(7, 1, 1, 0);
        assertTrue(svc.updateContent(7L, "一段一记"));
        assertEquals("一段一记", mapper.selectById(7L).getContent());
        assertNotNull(svc.selectTSaltRecordFlowById(7L));
    }

    @Test
    public void missingBill_returnsNullOrFalse() {
        assertNull(svc.advance(99L, "x"));
        assertNull(svc.rollback(99L, "x"));
        assertFalse(svc.updateContent(99L, "x"));
        assertFalse(svc.remove(99L));
    }
}
