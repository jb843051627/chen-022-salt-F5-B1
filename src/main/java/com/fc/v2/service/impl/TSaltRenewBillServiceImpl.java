package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>档口（node_no）与情形（status）每次签批同源一处 UPDATE：
 * 同档算齐按 sign_mode——OR 一名即齐、AND 须 need_count 名同判（缺省按 1）；
 * 本档齐了才进下一档、落印清零，走到省核准档（node=2）齐了才办结。
 * 否决真落"已否决"；退回上一档清本档落印、情形回在核；办结/否决后不再动。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRenewBillServiceImpl implements ITSaltRenewBillService {

    /** 0县初核 1市复审 2省核准（末档） */
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        return isGone(r) ? null : r;
    }

    @Override
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (isGone(r) || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null; // 已办结/已否决不再签
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        if (node > MAX_NODE) {
            return null;
        }
        int signed = r.getSignCount() == null ? 0 : r.getSignCount().intValue();
        int required = requiredSigns(r);
        int now = signed + 1;

        if (now < required) {
            // 本档还没算齐：只落印，档口与情形不动
            return update(id, node, now, STATUS_RUNNING);
        }
        // 本档齐了：末档办结，否则进下一档并把落印清零
        if (node >= MAX_NODE) {
            return update(id, MAX_NODE, now, STATUS_PASS);
        }
        return update(id, node + 1, 0, STATUS_RUNNING);
    }

    @Override
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (isGone(r) || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        // 否决真落"已否决"（旧实现空更新，情形不跟）
        UpdateWrapper<TSaltRenewBill> uw = new UpdateWrapper<TSaltRenewBill>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", STATUS_VETO);
        return this.saltRenewBillMapper.update(null, uw) == 1
                ? this.saltRenewBillMapper.selectById(id) : null;
    }

    @Override
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (isGone(r)) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null; // 办结/否决不退
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        if (node <= 0) {
            return null; // 头档退不动
        }
        // 回上一档：档口 -1、本档落印清零、情形回在核（与档口同源）
        return update(id, node - 1, 0, STATUS_RUNNING);
    }

    /** 本档算齐要落几印：OR 一名；AND 取 need_count（缺省/非正按 1） */
    private int requiredSigns(TSaltRenewBill r) {
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode().intValue();
        if (mode != MODE_AND) {
            return 1;
        }
        int need = r.getNeedCount() == null ? 1 : r.getNeedCount().intValue();
        return need > 0 ? need : 1;
    }

    private TSaltRenewBill update(Long id, int node, int signCount, int status) {
        boolean ok = this.saltRenewBillMapper.update(null, new UpdateWrapper<TSaltRenewBill>()
                .eq("id", id).eq("del_flag", 0)
                .set("node_no", node)
                .set("sign_count", signCount)
                .set("status", status)) == 1;
        return ok ? this.saltRenewBillMapper.selectById(id) : null;
    }

    private boolean isGone(TSaltRenewBill r) {
        return r == null || (r.getDelFlag() != null && r.getDelFlag() == 1);
    }
}
