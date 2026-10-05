package com.fc.v2.service.salt;

import java.io.Serializable;

import com.fc.v2.model.auto.TSaltYearTask;

/**
 * 手动新建一条的回话：三样落点（新建落册 / 同批已在册未重复建 / 拒收）与册内条目，
 * 只出自新建这一趟；屏上照此回话誊，不许另处手填。
 *
 * 同批认定：企业 + 事由 + 止点时刻三者全同（且未删）即同一批。并着建两回只落一回，
 * 第二回原样回话册内既有条目——册内既有版本为准（含最可提前几日），不覆盖、不另建。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class SaltYearCreateResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落点：新建落册 */
    public static final int OUT_CREATED = 1;
    /** 落点：同批已在册，未重复建（册内既有版本为准） */
    public static final int OUT_EXISTING = 0;
    /** 落点：拒收（原委见回话） */
    public static final int OUT_REJECTED = -1;

    /** 三样落点之一 */
    private int outcome;

    /** 收口话：落在哪、因何拒，一句话说清 */
    private String message;

    /** 册内条目：新建的或同批既有的（拒收时为空） */
    private TSaltYearTask item;

    public SaltYearCreateResult() {
    }

    public SaltYearCreateResult(int outcome, String message, TSaltYearTask item) {
        this.outcome = outcome;
        this.message = message;
        this.item = item;
    }

    public static SaltYearCreateResult created(String message, TSaltYearTask item) {
        return new SaltYearCreateResult(OUT_CREATED, message, item);
    }

    public static SaltYearCreateResult existing(String message, TSaltYearTask item) {
        return new SaltYearCreateResult(OUT_EXISTING, message, item);
    }

    public static SaltYearCreateResult rejected(String message) {
        return new SaltYearCreateResult(OUT_REJECTED, message, null);
    }

    public int getOutcome() {
        return outcome;
    }

    public void setOutcome(int outcome) {
        this.outcome = outcome;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TSaltYearTask getItem() {
        return item;
    }

    public void setItem(TSaltYearTask item) {
        this.item = item;
    }
}
