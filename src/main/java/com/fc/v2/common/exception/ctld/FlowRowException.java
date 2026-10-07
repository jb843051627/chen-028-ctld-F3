package com.fc.v2.common.exception.ctld;

/**
 * 核收流水办事规矩被拒时抛出。
 * code 给固定口径，msg 必须点到具体拨/具体行/具体栏，不允许只回「信息不全」。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class FlowRowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 4xx：来件本身不成立（拨头缺件、切法不明等），整拨留在门外 */
    public static final int CODE_REJECT = 460;
    /** 4xx：同一批次码重递，头一拨已落定，照头一拨回话 */
    public static final int CODE_IDEMPOTENT = 461;

    private final int code;

    public FlowRowException(String message) {
        this(CODE_REJECT, message);
    }

    public FlowRowException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
