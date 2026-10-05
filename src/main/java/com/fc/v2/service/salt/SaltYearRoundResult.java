package com.fc.v2.service.salt;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 跑一轮的出口回话：四样（本轮挑到哪几条、够不上日子的动不动、窗口日走不走、派出那一刻落没落）
 * 与收口三数（还欠开口、本轮派出、分不出），全部只出自这一趟；屏上照此回话誊，不许另处手填。
 *
 * @author fuce
 * @date 2026-10-04
 */
public class SaltYearRoundResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 一轮出口所凭时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ranAt;

    /** 本轮是否撞上全省集中办案窗口（撞上：已到档也压着，一律不走） */
    private boolean windowDay;

    /** 本轮挑到的待派条目（开口点已到、原本该动手的） */
    private List<Long> pickedItemIds = new ArrayList<Long>();

    /** 够不上日子的待派条目：本轮压着不动，也不点名 */
    private List<Long> heldNotDueItemIds = new ArrayList<Long>();

    /** 窗口日压下的已到档条目：本轮不动，等下一轮开口 */
    private List<Long> pressedItemIds = new ArrayList<Long>();

    /** 本轮派出条目逐条两处痕迹（回库点出） */
    private List<DispatchTrace> dispatched = new ArrayList<DispatchTrace>();

    /** 本轮分不出条目逐条原委（回库点出） */
    private List<FailTrace> failed = new ArrayList<FailTrace>();

    /** 收口三数之一：还欠开口（已到档却仍是待派，本轮没开成口的） */
    private int owingCount;

    /** 收口三数之二：本轮派出 */
    private int dispatchedCount;

    /** 收口三数之三：分不出 */
    private int failedCount;

    /** 收口话：整轮一条也捞不着时为"本轮无到期"，正常收口 */
    private String message;

    public static class DispatchTrace implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long itemId;
        private String itemNo;
        private Long officerId;
        private String officerName;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date dispatchAt;

        public DispatchTrace() {
        }

        public DispatchTrace(Long itemId, String itemNo, Long officerId, String officerName, Date dispatchAt) {
            this.itemId = itemId;
            this.itemNo = itemNo;
            this.officerId = officerId;
            this.officerName = officerName;
            this.dispatchAt = dispatchAt;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getItemNo() {
            return itemNo;
        }

        public void setItemNo(String itemNo) {
            this.itemNo = itemNo;
        }

        public Long getOfficerId() {
            return officerId;
        }

        public void setOfficerId(Long officerId) {
            this.officerId = officerId;
        }

        public String getOfficerName() {
            return officerName;
        }

        public void setOfficerName(String officerName) {
            this.officerName = officerName;
        }

        public Date getDispatchAt() {
            return dispatchAt;
        }

        public void setDispatchAt(Date dispatchAt) {
            this.dispatchAt = dispatchAt;
        }
    }

    public static class FailTrace implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long itemId;
        private String itemNo;
        private String failReason;

        public FailTrace() {
        }

        public FailTrace(Long itemId, String itemNo, String failReason) {
            this.itemId = itemId;
            this.itemNo = itemNo;
            this.failReason = failReason;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getItemNo() {
            return itemNo;
        }

        public void setItemNo(String itemNo) {
            this.itemNo = itemNo;
        }

        public String getFailReason() {
            return failReason;
        }

        public void setFailReason(String failReason) {
            this.failReason = failReason;
        }
    }

    public Date getRanAt() {
        return ranAt;
    }

    public void setRanAt(Date ranAt) {
        this.ranAt = ranAt;
    }

    public boolean isWindowDay() {
        return windowDay;
    }

    public void setWindowDay(boolean windowDay) {
        this.windowDay = windowDay;
    }

    public List<Long> getPickedItemIds() {
        return pickedItemIds;
    }

    public void setPickedItemIds(List<Long> pickedItemIds) {
        this.pickedItemIds = pickedItemIds;
    }

    public List<Long> getHeldNotDueItemIds() {
        return heldNotDueItemIds;
    }

    public void setHeldNotDueItemIds(List<Long> heldNotDueItemIds) {
        this.heldNotDueItemIds = heldNotDueItemIds;
    }

    public List<Long> getPressedItemIds() {
        return pressedItemIds;
    }

    public void setPressedItemIds(List<Long> pressedItemIds) {
        this.pressedItemIds = pressedItemIds;
    }

    public List<DispatchTrace> getDispatched() {
        return dispatched;
    }

    public void setDispatched(List<DispatchTrace> dispatched) {
        this.dispatched = dispatched;
    }

    public List<FailTrace> getFailed() {
        return failed;
    }

    public void setFailed(List<FailTrace> failed) {
        this.failed = failed;
    }

    public int getOwingCount() {
        return owingCount;
    }

    public void setOwingCount(int owingCount) {
        this.owingCount = owingCount;
    }

    public int getDispatchedCount() {
        return dispatchedCount;
    }

    public void setDispatchedCount(int dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
