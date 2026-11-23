-- KNOWN-006：审批执行补偿
-- 审批通过后要真正执行动作（建项目 / 删项目）。以前执行失败只 printStackTrace，
-- 审批状态停在 APPROVED、动作却没发生，也没有任何痕迹可查、无法重试。
-- 这里把「审批结果」和「执行结果」拆成两个状态，执行失败可重试。

ALTER TABLE `approval_requests`
    ADD COLUMN `exec_status` VARCHAR(16) DEFAULT NULL
        COMMENT '执行结果：SUCCESS / FAILED；NULL = 未跟踪（历史数据）';

ALTER TABLE `approval_requests`
    ADD COLUMN `exec_error` VARCHAR(500) DEFAULT NULL
        COMMENT '最近一次执行失败的原因（截断到 500 字）';

ALTER TABLE `approval_requests`
    ADD COLUMN `exec_attempts` INT NOT NULL DEFAULT 0
        COMMENT '执行尝试次数（含首次）';

ALTER TABLE `approval_requests`
    ADD COLUMN `last_exec_at` DATETIME DEFAULT NULL
        COMMENT '最近一次执行时间';
