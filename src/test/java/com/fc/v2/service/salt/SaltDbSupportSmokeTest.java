package com.fc.v2.service.salt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.model.auto.TSaltEnt;

/** 基座自检：H2 真跑 SQL + 字段填充。 */
public class SaltDbSupportSmokeTest {

    @Test
    public void insertAndQueryRoundTrip() {
        SaltDbSupport db = new SaltDbSupport();
        TSaltEntMapper mapper = db.mapper(TSaltEntMapper.class);
        TSaltEnt ent = new TSaltEnt();
        ent.setId(777L);
        ent.setSiteNo("JY777");
        ent.setSiteName("基座自检厂");
        ent.setStatus(0);
        ent.setDelFlag(0);
        assertEquals(1, mapper.insert(ent));

        TSaltEnt back = mapper.selectOne(new QueryWrapper<TSaltEnt>().eq("site_no", "JY777"));
        assertNotNull(back);
        assertEquals("基座自检厂", back.getSiteName());
        assertNotNull(back.getCreateTime(), "字段填充应补 createTime");
        assertEquals(new Date().getYear(), back.getCreateTime().getYear());
    }
}
