package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.impl.TSaltSpotRowServiceImpl;

/**
 * 抽检结果报送核收：空批不崩、超限整批不落、逐行核收不互撂、
 * 不合格件数不得多于送检件数（结存非负）、保留原报行次、同批重复提交幂等。
 */
public class TSaltSpotRowServiceTest {

    private SaltDbSupport db;
    private TSaltSpotRowServiceImpl svc;
    private TSaltSpotRowMapper mapper;

    private static final int MAX_ROWS = 500;

    private TSaltSpotRow row(String code, String qty, String hours, Integer rowNo) {
        TSaltSpotRow r = new TSaltSpotRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setHoursNum(hours == null ? null : new BigDecimal(hours));
        r.setRowNo(rowNo);
        r.setDelFlag(0);
        return r;
    }

    private long count(String batchNo) {
        return mapper.selectCount(new QueryWrapper<TSaltSpotRow>().eq("batch_no", batchNo));
    }

    @BeforeEach
    public void setUp() {
        db = new SaltDbSupport();
        svc = db.wired(new TSaltSpotRowServiceImpl());
        mapper = db.mapper(TSaltSpotRowMapper.class);
    }

    @Test
    public void emptyOrNullBatch_returnsZero_doesNotCrash() {
        assertEquals(0, svc.submitBatch("B0", null));
        assertEquals(0, svc.submitBatch("B0", new ArrayList<TSaltSpotRow>()));
        assertEquals(0, count("B0"));
    }

    @Test
    public void overLimit_returnsMinusOne_andNothingLands() {
        List<TSaltSpotRow> rows = new ArrayList<TSaltSpotRow>();
        for (int i = 1; i <= MAX_ROWS + 1; i++) {
            rows.add(row("JY" + i, "1", "10", i));
        }
        assertEquals(-1, svc.submitBatch("BIG", rows));
        assertEquals(0, count("BIG"), "超限整批不入库");
    }

    @Test
    public void exactlyAtLimit_isAccepted() {
        List<TSaltSpotRow> rows = new ArrayList<TSaltSpotRow>();
        for (int i = 1; i <= MAX_ROWS; i++) {
            rows.add(row("JY" + i, "1", "10", i));
        }
        assertEquals(MAX_ROWS, svc.submitBatch("OK500", rows));
        assertEquals(MAX_ROWS, count("OK500"));
    }

    @Test
    public void mixedBatch_validLanded_invalidMarkedKeepingOriginalRowNo() {
        List<TSaltSpotRow> rows = new ArrayList<TSaltSpotRow>();
        rows.add(row("JY1", "3", "10", 11));     // 合法（原报行次 11）
        rows.add(row(null, "1", "10", 12));      // 企业代号空 → 退
        rows.add(row("JY3", null, "10", 13));    // 不合格数空 → 退
        rows.add(row("JY4", "0", "10", 14));     // 不合格数 0/负无意义 → 退
        rows.add(row("JY5", "8", "5", 15));      // 不合格 8 > 送检 5，结存为负 → 退
        rows.add(row("JY6", "5", "5", 16));      // 恰好相等，结存 0 → 合法
        rows.add(row("JY7", "2", null, 17));     // 送检件数空，无从核结存 → 退
        rows.add(null);                          // 整行为空也不许撂倒整次：跳过

        int ok = svc.submitBatch("MIX", rows);
        assertEquals(2, ok, "合法两条收下，其余逐条退回，整次不中断");
        assertEquals(7, count("MIX"), "收下2与退回5都留痕；空元素跳过");

        List<TSaltSpotRow> errors = svc.listErrors("MIX");
        assertEquals(5, errors.size());
        // 失败明细按原报行次升序，不是自增序号
        assertEquals(Integer.valueOf(12), errors.get(0).getRowNo());
        assertEquals(Integer.valueOf(13), errors.get(1).getRowNo());
        assertEquals(Integer.valueOf(14), errors.get(2).getRowNo());
        assertEquals(Integer.valueOf(15), errors.get(3).getRowNo());
        assertEquals(Integer.valueOf(17), errors.get(4).getRowNo());
        // 退回行都盖了批次号、落了退状态
        for (TSaltSpotRow e : errors) {
            assertEquals("MIX", e.getBatchNo());
            assertEquals(2, e.getStatus().intValue());
        }
    }

    @Test
    public void validRowsGetIdAndOkStatus() {
        List<TSaltSpotRow> rows = new ArrayList<TSaltSpotRow>();
        rows.add(row("JY1", "2", "10", 1));
        svc.submitBatch("ID", rows);
        TSaltSpotRow saved = mapper.selectOne(new QueryWrapper<TSaltSpotRow>().eq("batch_no", "ID"));
        assertNotNull(saved.getId());
        assertEquals(1, saved.getStatus().intValue());
        assertEquals("ID", saved.getBatchNo(), "批次号以入参为准盖到每行");
    }

    @Test
    public void duplicateResubmit_isIdempotent() {
        List<TSaltSpotRow> rows = new ArrayList<TSaltSpotRow>();
        rows.add(row("JY1", "2", "10", 1));
        rows.add(row("JY2", "3", "10", 2));
        assertEquals(2, svc.submitBatch("DUP", rows));
        // 同批次再报一遍：不重复入库，回的仍是那两条成功
        int second = svc.submitBatch("DUP", rows);
        assertEquals(2, second);
        assertEquals(2, count("DUP"), "重复提交不得重复入库");
    }
}
