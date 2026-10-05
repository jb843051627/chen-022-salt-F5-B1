package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.service.ITSaltLinkPageService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 企业品种挂接簿页Service业务层处理
 *
 * <p>几处口径定死：
 * <ul>
 *   <li>列表与详情同一本未删台账：列表尊重调用方条件、只补 del_flag=0，
 *       不另起一套、不强塞分页；详情按 id 也只给未删页（列表那条与点进去是同一张）；</li>
 *   <li>site_id 与企业总名录 bigint 主键同型，不截断；</li>
 *   <li>欠挂（结存）lack 一律 = max(应挂-已挂, 0)，新增/修改同源重算，不许外填；</li>
 *   <li>推进 0→1→2 有守卫、null 安全，退回 1→0 有界；核验串 content 永不被推进/退回抹掉；</li>
 *   <li>删除一律逻辑删（del_flag=1），已删页各入口不再露、不再动。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSaltLinkPageServiceImpl extends ServiceImpl<TSaltLinkPageMapper, TSaltLinkPage> implements ITSaltLinkPageService {

    /** 挂接进展 0待挂 1已挂讫 2压页 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_HELD = 2;

    @Autowired
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltLinkPage selectTSaltLinkPageById(Long id) {
        if (id == null) {
            return null;
        }
        // 详情也只给未删页：列表上的那条与点进详情看到的是同一张
        return this.baseMapper.selectOne(new QueryWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper) {
        // 尊重调用方条件，只在其上补"未删"；分页交给调用方统一 startPage，不在此强塞。
        QueryWrapper<TSaltLinkPage> wrapper = queryWrapper == null
                ? new QueryWrapper<TSaltLinkPage>()
                : (QueryWrapper<TSaltLinkPage>) queryWrapper;
        return this.baseMapper.selectList(wrapper.eq("del_flag", 0));
    }

    @Override
    public int insertTSaltLinkPage(TSaltLinkPage record) {
        if (record == null) {
            return 0;
        }

        // 与企业总名录联动：查无名录或已摘牌，不开页
        TSaltEnt refArch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() != 0) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setLackCount(deriveLack(record.getShouldCount(), record.getDoneCount()));
        record.setDelFlag(0);
        if (record.getStatus() == null) {
            record.setStatus(STATUS_PENDING);
        }
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSaltLinkPage(TSaltLinkPage record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(record.getId());
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag() == 1)) {
            return 0;
        }
        if (record.getSiteId() != null && !record.getSiteId().equals(cur.getSiteId())) {
            TSaltEnt ref = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                    .eq("id", record.getSiteId()).eq("del_flag", 0));
            if (ref == null || (ref.getStatus() != null && ref.getStatus() != 0)) {
                return 0;
            }
            record.setSiteNo(ref.getSiteNo());
        }
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        // 欠挂结存同源：以合并后的应挂/已挂重算，外部给的 lack 不认
        Integer should = record.getShouldCount() != null ? record.getShouldCount() : cur.getShouldCount();
        Integer done = record.getDoneCount() != null ? record.getDoneCount() : cur.getDoneCount();
        record.setLackCount(deriveLack(should, done));

        return this.baseMapper.update(record, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag() == 1)) {
            return 0;
        }
        // 守卫：只许 0待挂→1已挂讫→2压页，末态不跳；null 存量从待挂起
        int from = cur.getStatus() == null ? STATUS_PENDING : cur.getStatus().intValue();
        if (from < STATUS_PENDING || from >= STATUS_HELD) {
            return 0;
        }
        int next = from + 1;
        // 只落状态与时刻，核验串 content 原样保留（不 set null）
        return this.baseMapper.update(null, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", next)
                .set("update_time", new Date()));
    }

    @Override
    public int revertTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag() == 1)) {
            return 0;
        }
        if (cur.getStatus() == null) {
            return 0;
        }
        int st = cur.getStatus().intValue();
        if (st <= STATUS_PENDING || st > STATUS_HELD) {
            return 0; // 头态退不动，越界态不退
        }
        int next = st - 1;
        // 退回只落状态；核验串 content 保留，不清除
        return this.baseMapper.update(null, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", next)
                .set("update_time", new Date()));
    }

    @Override
    public int deleteTSaltLinkPageByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        // 逻辑删除批量：留痕，台账不再列
        return this.baseMapper.update(null, new UpdateWrapper<TSaltLinkPage>()
                .in("id", Arrays.asList(idArr))
                .eq("del_flag", 0)
                .set("del_flag", 1));
    }

    @Override
    public int deleteTSaltLinkPageById(Long id) {
        if (id == null) {
            return 0;
        }
        return this.baseMapper.update(null, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("del_flag", 1));
    }

    /** 欠挂（结存）唯一算法：应挂 - 已挂，不足 0 记 0；任一缺失按 0 计 */
    private static Integer deriveLack(Integer should, Integer done) {
        int s = should == null ? 0 : should.intValue();
        int d = done == null ? 0 : done.intValue();
        return Integer.valueOf(Math.max(0, s - d));
    }
}
