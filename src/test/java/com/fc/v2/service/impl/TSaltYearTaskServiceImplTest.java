package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltNotifyScriptMapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltNotifyScript;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.salt.SaltYearCreateResult;

/**
 * 手动新建一条的单测：同批幂等（两回只落一回、册内既有版本为准）、
 * 缺省口径（提前几日 0、唯一在场话本）、入门拒收、库层唯一键兜底。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TSaltYearTaskServiceImplTest {

    @Mock
    private TSaltYearTaskMapper yearTaskMapper;
    @Mock
    private TSaltEntMapper entMapper;
    @Mock
    private TSaltNotifyScriptMapper scriptMapper;

    private TSaltYearTaskServiceImpl service;

    @BeforeEach
    public void setUp() {
        service = new TSaltYearTaskServiceImpl();
        ReflectionTestUtils.setField(service, "saltYearTaskMapper", yearTaskMapper);
        ReflectionTestUtils.setField(service, "saltEntMapper", entMapper);
        ReflectionTestUtils.setField(service, "saltNotifyScriptMapper", scriptMapper);
    }

    private TSaltYearTask newRecord() {
        TSaltYearTask r = new TSaltYearTask();
        r.setSiteId(Long.valueOf(0L));
        r.setBizType(Integer.valueOf(0));
        r.setDueAt(new Date());
        return r;
    }

    private TSaltEnt listedEnt() {
        TSaltEnt ent = new TSaltEnt();
        ent.setId(Long.valueOf(0L));
        ent.setStatus(Integer.valueOf(0));
        ent.setContactPerson("卤延年");
        return ent;
    }

    private TSaltNotifyScript onScript(String no, Integer bizType) {
        TSaltNotifyScript s = new TSaltNotifyScript();
        s.setScriptNo(no);
        s.setBizType(bizType);
        s.setStatus(Integer.valueOf(0));
        s.setDelFlag(Integer.valueOf(0));
        return s;
    }

    /** 在册企业 + 无同批 + 话本册一本在场（NB-NJ 对事由0） */
    private void stubHappyPath() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        when(yearTaskMapper.selectList(any())).thenReturn(new ArrayList<TSaltYearTask>());
        when(yearTaskMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        List<TSaltNotifyScript> scripts = new ArrayList<TSaltNotifyScript>();
        scripts.add(onScript("NB-NJ", Integer.valueOf(0)));
        when(scriptMapper.selectList(any())).thenReturn(scripts);
        when(yearTaskMapper.insert(any(TSaltYearTask.class))).thenAnswer(inv -> {
            TSaltYearTask t = inv.getArgument(0);
            t.setId(Long.valueOf(99L));
            return Integer.valueOf(1);
        });
        when(yearTaskMapper.update(isNull(), any())).thenReturn(Integer.valueOf(1));
    }

    @Test
    public void created_defaultsFilled_perConvention() {
        stubHappyPath();
        TSaltYearTask record = newRecord();
        record.setAmount(null);   // 提前几日没填 → 缺省 0
        record.setScriptNo(null); // 话本没填 → 联动取该事由唯一在场的一本

        SaltYearCreateResult r = service.createManual(record);

        assertEquals(SaltYearCreateResult.OUT_CREATED, r.getOutcome());
        ArgumentCaptor<TSaltYearTask> cap = ArgumentCaptor.forClass(TSaltYearTask.class);
        verify(yearTaskMapper).insert(cap.capture());
        TSaltYearTask row = cap.getValue();
        assertEquals(Integer.valueOf(0), row.getStatus());          // 落册即待派
        assertEquals(Integer.valueOf(0), row.getDelFlag());
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getAmount())); // 缺省 0：开口点即止点本身
        assertEquals("NB-NJ", row.getScriptNo());                    // 联动话本册取唯一在场
        assertTrue(row.getItemNo().startsWith("NJ"));                // 代号没填照规起一个
        assertNull(row.getOfficerId());
        assertNull(row.getDispatchAt());
        // 止点走墙钟串绑定落库（JDBC 时区不对称，Date 不直绑）
        verify(yearTaskMapper).update(isNull(), any());
    }

    @Test
    public void sameBatchTwice_secondIsNoop_existingVersionWins() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        // 册内既有同批条目：提前几日=5
        TSaltYearTask existing = newRecord();
        existing.setId(Long.valueOf(7L));
        existing.setItemNo("NJ-OLD");
        existing.setAmount(new BigDecimal("5"));
        when(yearTaskMapper.selectList(any()))
                .thenReturn(new ArrayList<TSaltYearTask>(Collections.singletonList(existing)));

        // 第二回并着建：同一批（企业+事由+止点），提前几日填了 2 —— 不覆盖、不另建
        TSaltYearTask again = newRecord();
        again.setAmount(new BigDecimal("2"));
        SaltYearCreateResult r = service.createManual(again);

        assertEquals(SaltYearCreateResult.OUT_EXISTING, r.getOutcome());
        assertEquals(Long.valueOf(7L), r.getItem().getId());
        assertEquals(0, new BigDecimal("5").compareTo(r.getItem().getAmount())); // 册内既有版本为准
        verify(yearTaskMapper, never()).insert(any());
    }

    @Test
    public void delistedEnt_rejected_atTheDoor() {
        TSaltEnt delisted = listedEnt();
        delisted.setStatus(Integer.valueOf(1));
        when(entMapper.selectOne(any())).thenReturn(delisted);

        SaltYearCreateResult r = service.createManual(newRecord());

        assertEquals(SaltYearCreateResult.OUT_REJECTED, r.getOutcome());
        verify(yearTaskMapper, never()).insert(any());
    }

    @Test
    public void missingPieces_rejected() {
        TSaltYearTask noDue = newRecord();
        noDue.setDueAt(null);
        assertEquals(SaltYearCreateResult.OUT_REJECTED, service.createManual(noDue).getOutcome());

        TSaltYearTask noSite = newRecord();
        noSite.setSiteId(null);
        assertEquals(SaltYearCreateResult.OUT_REJECTED, service.createManual(noSite).getOutcome());

        TSaltYearTask negLead = newRecord();
        negLead.setAmount(new BigDecimal("-1"));
        assertEquals(SaltYearCreateResult.OUT_REJECTED, service.createManual(negLead).getOutcome());

        TSaltYearTask fracLead = newRecord();
        fracLead.setAmount(new BigDecimal("2.5"));
        assertEquals(SaltYearCreateResult.OUT_REJECTED, service.createManual(fracLead).getOutcome());

        verify(yearTaskMapper, never()).insert(any());
    }

    @Test
    public void itemNoCollision_rejected() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        when(yearTaskMapper.selectList(any())).thenReturn(new ArrayList<TSaltYearTask>());
        when(yearTaskMapper.selectCount(any())).thenReturn(Integer.valueOf(1)); // 代号册内已有

        TSaltYearTask record = newRecord();
        record.setItemNo("NJ-TAKEN");
        SaltYearCreateResult r = service.createManual(record);

        assertEquals(SaltYearCreateResult.OUT_REJECTED, r.getOutcome());
        verify(yearTaskMapper, never()).insert(any());
    }

    @Test
    public void duplicateKeyOnInsert_fallsBackToExisting() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        TSaltYearTask existing = newRecord();
        existing.setId(Long.valueOf(8L));
        existing.setItemNo("NJ-WON");
        // 第一回查同批为空；撞键后再查，册内已有（并着建那回先落）
        when(yearTaskMapper.selectList(any()))
                .thenReturn(new ArrayList<TSaltYearTask>())
                .thenReturn(new ArrayList<TSaltYearTask>(Collections.singletonList(existing)));
        when(yearTaskMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        List<TSaltNotifyScript> scripts = new ArrayList<TSaltNotifyScript>();
        scripts.add(onScript("NB-NJ", Integer.valueOf(0)));
        when(scriptMapper.selectList(any())).thenReturn(scripts);
        when(yearTaskMapper.insert(any(TSaltYearTask.class)))
                .thenThrow(new DuplicateKeyException("uk_salt_year_task_batch"));

        SaltYearCreateResult r = service.createManual(newRecord());

        assertEquals(SaltYearCreateResult.OUT_EXISTING, r.getOutcome());
        assertEquals("NJ-WON", r.getItem().getItemNo());
    }

    @Test
    public void duplicateKeyOnDueAtWrite_halfRowCleaned() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        TSaltYearTask existing = newRecord();
        existing.setId(Long.valueOf(9L));
        existing.setItemNo("NJ-FIRST");
        when(yearTaskMapper.selectList(any()))
                .thenReturn(new ArrayList<TSaltYearTask>())
                .thenReturn(new ArrayList<TSaltYearTask>(Collections.singletonList(existing)));
        when(yearTaskMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        List<TSaltNotifyScript> scripts = new ArrayList<TSaltNotifyScript>();
        scripts.add(onScript("NB-NJ", Integer.valueOf(0)));
        when(scriptMapper.selectList(any())).thenReturn(scripts);
        // 落库成功、止点回写时才撞键：本趟的半截条（止点未写上）必须清掉，不许留册
        when(yearTaskMapper.insert(any(TSaltYearTask.class))).thenAnswer(inv -> {
            TSaltYearTask t = inv.getArgument(0);
            t.setId(Long.valueOf(101L));
            return Integer.valueOf(1);
        });
        when(yearTaskMapper.update(isNull(), any()))
                .thenThrow(new DuplicateKeyException("uk_salt_year_task_batch"));

        SaltYearCreateResult r = service.createManual(newRecord());

        assertEquals(SaltYearCreateResult.OUT_EXISTING, r.getOutcome());
        assertEquals("NJ-FIRST", r.getItem().getItemNo());
        verify(yearTaskMapper).deleteById(Long.valueOf(101L));
    }

    @Test
    public void scriptGivenButOffDuty_rejected() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        when(yearTaskMapper.selectList(any())).thenReturn(new ArrayList<TSaltYearTask>());
        when(scriptMapper.selectOne(any())).thenReturn(null); // 填了的话本不在场

        TSaltYearTask record = newRecord();
        record.setScriptNo("NB-GONE");
        SaltYearCreateResult r = service.createManual(record);

        assertEquals(SaltYearCreateResult.OUT_REJECTED, r.getOutcome());
        verify(yearTaskMapper, never()).insert(any());
    }

    @Test
    public void noUniqueScript_leavesBlank_forRoundToHold() {
        when(entMapper.selectOne(any())).thenReturn(listedEnt());
        when(yearTaskMapper.selectList(any())).thenReturn(new ArrayList<TSaltYearTask>());
        when(yearTaskMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        // 两本在场都对事由0：取不到唯一，留空，跑一轮按"无本可依"挂起
        List<TSaltNotifyScript> scripts = new ArrayList<TSaltNotifyScript>();
        scripts.add(onScript("NB-NJ", Integer.valueOf(0)));
        scripts.add(onScript("NB-NJ2", null));
        when(scriptMapper.selectList(any())).thenReturn(scripts);
        when(yearTaskMapper.insert(any(TSaltYearTask.class))).thenAnswer(inv -> {
            TSaltYearTask t = inv.getArgument(0);
            t.setId(Long.valueOf(100L));
            return Integer.valueOf(1);
        });
        when(yearTaskMapper.update(isNull(), any())).thenReturn(Integer.valueOf(1));

        SaltYearCreateResult r = service.createManual(newRecord());

        assertEquals(SaltYearCreateResult.OUT_CREATED, r.getOutcome());
        assertNotNull(r.getItem());
        assertNull(r.getItem().getScriptNo());
    }
}
