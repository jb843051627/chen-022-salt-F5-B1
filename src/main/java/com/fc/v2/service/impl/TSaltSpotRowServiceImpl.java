package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * <p>口径定死：
 * <ul>
 *   <li>空批（null/无行）回 0，不许空着就崩；超过单批上限回 -1 且整批一行不落；</li>
 *   <li>一趟逐行核收：合法的收下（status=1）、非法的盖退状态留痕（status=2）并保留原报行次，
 *       一行走岔（含整行为空）不撂倒其余；返回收下条数；</li>
 *   <li>不合格件数不得多于送检件数（结存非负），否则该行退回；</li>
 *   <li>同一批次重复提交幂等：已报过的批次不重复入库，回原收下条数。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltSpotRowServiceImpl implements ITSaltSpotRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltSpotRowMapper saltSpotRowMapper;

    @Override
    public TSaltSpotRow selectTSaltSpotRowById(Long id) {
        return this.saltSpotRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        // 空批：正常回 0，绝不解引用崩掉整次
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        // 批次号以入参为准（入参空着才退回看头一行自带的）；整批无号不收
        String no = (batchNo != null && !batchNo.trim().isEmpty())
                ? batchNo.trim()
                : firstOwnBatchNo(rows);
        if (no == null) {
            return 0;
        }

        // 同批重复提交幂等：已报过就不重复入库，回该批已收下条数
        Integer existed = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer okNow = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                    .eq("batch_no", no).eq("del_flag", 0).eq("status", STATUS_OK));
            return okNow == null ? 0 : okNow.intValue();
        }

        int ok = 0;
        for (TSaltSpotRow r : rows) {
            // 整行为空也只跳过，不撂下后面的行
            if (r == null) {
                continue;
            }
            boolean valid = isValid(r);
            r.setBatchNo(no);
            r.setStatus(valid ? STATUS_OK : STATUS_FAIL);
            r.setDelFlag(0);
            // 原报行次原样保留，非法行不另起自增序号（r.rowNo 已带来，缺着就留空）
            this.saltSpotRowMapper.insert(r);
            if (valid) {
                ok++;
            }
        }
        return ok;
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", STATUS_FAIL)
                .orderByAsc("row_no")
                .orderByAsc("id"));
    }

    /** 行内核对：企业代号在、不合格件数为正、送检件数为正、且不合格不多于送检（结存非负） */
    private boolean isValid(TSaltSpotRow r) {
        if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()) {
            return false;
        }
        if (r.getQty() == null || r.getQty().signum() <= 0) {
            return false;
        }
        if (r.getHoursNum() == null || r.getHoursNum().signum() <= 0) {
            return false;
        }
        return r.getQty().compareTo(r.getHoursNum()) <= 0;
    }

    private String firstOwnBatchNo(List<TSaltSpotRow> rows) {
        for (TSaltSpotRow r : rows) {
            if (r != null && r.getBatchNo() != null && !r.getBatchNo().trim().isEmpty()) {
                return r.getBatchNo().trim();
            }
        }
        return null;
    }
}
