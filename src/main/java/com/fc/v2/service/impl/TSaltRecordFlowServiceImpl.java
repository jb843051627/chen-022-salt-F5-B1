package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.service.ITSaltRecordFlowService;

/**
 * 跨省经营备案单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>环节（stage）与情形（status）只同源出自每次挪步这一处 UPDATE：
 * 一档档走（+1），走到第 3 档即封卷（status=2）；封卷后不改、不删、不挪、不退。
 * 台账只列未删；删除为逻辑删（del_flag=1），已删单据任何入口都不再露、不再动。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRecordFlowServiceImpl implements ITSaltRecordFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TSaltRecordFlowMapper saltRecordFlowMapper;

    @Override
    public TSaltRecordFlow selectTSaltRecordFlowById(Long id) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        // 已删的不再露（列表与详情同一张台账）
        return isGone(r) ? null : r;
    }

    @Override
    public List<TSaltRecordFlow> selectTSaltRecordFlowList(QueryWrapper<TSaltRecordFlow> queryWrapper) {
        QueryWrapper<TSaltRecordFlow> w = queryWrapper == null ? new QueryWrapper<TSaltRecordFlow>() : queryWrapper;
        // 待处理集合必须筛干净：已删的不混进台账
        return this.saltRecordFlowMapper.selectList(w.eq("del_flag", 0));
    }

    @Override
    public TSaltRecordFlow advance(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (isGone(r) || r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int stage = r.getStage() == null ? 0 : r.getStage().intValue();
        if (stage >= MAX_STAGE) {
            return null;
        }
        int next = stage + 1;
        // 情形跟着环节走、同源一处：到顶即封卷，其余在办
        int status = next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE;
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("stage", next)
                .set("status", status)
                .set("last_action", remark);
        return this.saltRecordFlowMapper.update(null, uw) == 1 ? load(id) : null;
    }

    @Override
    public TSaltRecordFlow rollback(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (isGone(r) || (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL)) {
            return null;
        }
        int stage = r.getStage() == null ? 0 : r.getStage().intValue();
        if (stage <= 0) {
            return null; // 没档可退
        }
        int prev = stage - 1;
        int status = prev <= 0 ? STATUS_NOT_STARTED : STATUS_ACTIVE;
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("stage", prev)
                .set("status", status)
                .set("last_action", remark);
        return this.saltRecordFlowMapper.update(null, uw) == 1 ? load(id) : null;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (isGone(r) || (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL)) {
            return false; // 封卷、已删不得再改
        }
        return this.saltRecordFlowMapper.update(null, new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("content", remark)) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (isGone(r) || (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL)) {
            return false; // 封卷不得删；已删幂等返回 false
        }
        // 逻辑删除：留痕，台账不再列
        return this.saltRecordFlowMapper.update(null, new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("del_flag", 1)) > 0;
    }

    private TSaltRecordFlow load(Long id) {
        return this.saltRecordFlowMapper.selectById(id);
    }

    /** 查无或已删：所有入口一律当作不存在 */
    private boolean isGone(TSaltRecordFlow r) {
        return r == null || (r.getDelFlag() != null && r.getDelFlag() == 1);
    }
}
