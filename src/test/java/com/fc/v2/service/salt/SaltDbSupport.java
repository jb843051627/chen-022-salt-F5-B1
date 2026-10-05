package com.fc.v2.service.salt;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TSaltCalendarMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltIodLineMapper;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.mapper.auto.TSaltNotifyScriptMapper;
import com.fc.v2.mapper.auto.TSaltOfficerMapper;
import com.fc.v2.mapper.auto.TSaltOfficerOffMapper;
import com.fc.v2.mapper.auto.TSaltOfficerScopeMapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;

/**
 * 盐政服务层测试基座：H2（MySQL 模式）内存库 + MyBatis-Plus 真跑 SQL，
 * 不起 Spring 容器、不碰 Shiro（字段填充用测试版，避免无登录人抛异常）。
 *
 * <p>每个实例一个独立内存库，建表脚本与 doc/schema/salt.sql 列对齐。
 * 进程时区钉在 GMT+8，与生产"墙钟串"口径一致（见服务层 wall()）。
 */
public final class SaltDbSupport {

    private static final AtomicLong DB_SEQ = new AtomicLong();

    @SuppressWarnings("rawtypes")
    private static final List<Class<? extends BaseMapper>> MAPPERS = buildMapperList();

    private final SqlSession session;

    public SaltDbSupport() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
        try {
            String url = "jdbc:h2:mem:salt_" + DB_SEQ.incrementAndGet()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
            DataSource ds = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
            try (Connection conn = ds.getConnection()) {
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("sql/salt-h2.sql"));
            }

            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(ds);
            GlobalConfig global = new GlobalConfig().setMetaObjectHandler(new MetaObjectHandler() {
                @Override
                public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                    this.strictInsertFill(metaObject, "createBy", String.class, "tester");
                    this.strictInsertFill(metaObject, "createTime", java.util.Date.class, new java.util.Date());
                }

                @Override
                public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                    this.strictUpdateFill(metaObject, "updateBy", String.class, "tester");
                    this.strictUpdateFill(metaObject, "updateTime", java.util.Date.class, new java.util.Date());
                }
            });
            fb.setGlobalConfig(global);
            SqlSessionFactory factory = fb.getObject();
            MybatisConfiguration cfg = (MybatisConfiguration) factory.getConfiguration();
            for (Class<? extends BaseMapper> m : MAPPERS) {
                cfg.addMapper(m);
            }
            this.session = factory.openSession(true);
        } catch (Exception e) {
            throw new IllegalStateException("盐政测试库起不来", e);
        }
    }

    /** 取一个 Mapper 代理（真打 H2）。 */
    public <T> T mapper(Class<T> mapperClass) {
        return this.session.getMapper(mapperClass);
    }

    /**
     * 把服务里所有 {@link Resource} 注的 BaseMapper 字段，按类型用本库 Mapper 代理注入。
     * 服务无需 Spring 容器即可跑真 SQL。对 MyBatis-Plus ServiceImpl 的泛型字段
     * baseMapper（声明类型是类型变量 M），按具体子类的泛型父类解析出真实 Mapper。
     */
    public <T> T wired(T service) {
        Class<?> c = service.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                Class<?> type = mapperFieldType(service, f);
                if (type != null && BaseMapper.class.isAssignableFrom(type)) {
                    f.setAccessible(true);
                    try {
                        if (f.get(service) == null) {
                            f.set(service, mapper(type));
                        }
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("注入 Mapper 失败：" + f.getName(), e);
                    }
                }
            }
            c = c.getSuperclass();
        }
        return service;
    }

    /** 解析字段要注的 Mapper 类型；ServiceImpl.baseMapper 的 M 从泛型父类取。 */
    @SuppressWarnings("rawtypes")
    private static Class<?> mapperFieldType(Object service, Field f) {
        if (BaseMapper.class.isAssignableFrom(f.getType()) && f.getType() != BaseMapper.class) {
            return f.getType();
        }
        if ("baseMapper".equals(f.getName()) && f.getType() == BaseMapper.class) {
            java.lang.reflect.Type sup = service.getClass().getGenericSuperclass();
            if (sup instanceof java.lang.reflect.ParameterizedType) {
                for (java.lang.reflect.Type arg : ((java.lang.reflect.ParameterizedType) sup).getActualTypeArguments()) {
                    if (arg instanceof Class && BaseMapper.class.isAssignableFrom((Class<?>) arg)) {
                        return (Class<?>) arg;
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static List<Class<? extends BaseMapper>> buildMapperList() {
        List<Class<? extends BaseMapper>> list = new ArrayList<Class<? extends BaseMapper>>();
        list.add(TSaltEntMapper.class);
        list.add(TSaltNotifyScriptMapper.class);
        list.add(TSaltOfficerMapper.class);
        list.add(TSaltOfficerScopeMapper.class);
        list.add(TSaltOfficerOffMapper.class);
        list.add(TSaltCalendarMapper.class);
        list.add(TSaltIodLineMapper.class);
        list.add(TSaltLinkPageMapper.class);
        list.add(TSaltRecordFlowMapper.class);
        list.add(TSaltRenewBillMapper.class);
        list.add(TSaltSpotRowMapper.class);
        list.add(TSaltYearTaskMapper.class);
        return list;
    }
}
