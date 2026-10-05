package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 年检到期催办单对象 t_salt_year_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_salt_year_task")
@ApiModel(value = "TSaltYearTask", description = "年检到期催办单")
public class TSaltYearTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 年检催办条目代号 */
    @TableField("item_no")
    @ApiModelProperty(value = "年检催办条目代号")
    private String itemNo;

    /** 拴着哪家企业(t_salt_ent.id) */
    @TableField("site_id")
    @ApiModelProperty(value = "拴着哪家企业")
    private Long siteId;

    /** 事由 0年检催办 1换证提醒 */
    @TableField("biz_type")
    @ApiModelProperty(value = "事由 0年检催办 1换证提醒")
    private Integer bizType;

    /** 应开口那一日的止点时刻 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "应开口那一日的止点时刻")
    private Date dueAt;

    /** 最可提前几日开口 */
    @TableField("amount")
    @ApiModelProperty(value = "最可提前几日开口")
    private BigDecimal amount;

    /** 照哪一份话本去发 */
    @TableField("script_no")
    @ApiModelProperty(value = "照哪一份话本去发")
    private String scriptNo;

    /** 事由与所对企业的记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与所对企业的记要")
    private String content;

    /** 条目情形 0待派 1已派出 2分不出 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0待派 1已派出 2分不出")
    private Integer status;

    /** 派给了哪位执法员(t_salt_officer.id) */
    @TableField("officer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "派给了哪位执法员")
    private Long officerId;

    /** 派出那一刻 */
    @TableField("dispatch_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "派出那一刻")
    private Date dispatchAt;

    /** 分不出原委（卡在哪一环，日后按原委挑得出一批） */
    @TableField("fail_reason")
    @ApiModelProperty(value = "分不出原委")
    private String failReason;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Integer getBizType() {
        return bizType;
    }

    public void setBizType(Integer bizType) {
        this.bizType = bizType;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getScriptNo() {
        return scriptNo;
    }

    public void setScriptNo(String scriptNo) {
        this.scriptNo = scriptNo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getOfficerId() {
        return officerId;
    }

    public void setOfficerId(Long officerId) {
        this.officerId = officerId;
    }

    public Date getDispatchAt() {
        return dispatchAt;
    }

    public void setDispatchAt(Date dispatchAt) {
        this.dispatchAt = dispatchAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
