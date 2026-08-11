# r2 脚本与证据时点

`revise.py`、`finalize.py`、`validate_final.py` 是此前修订阶段脚本；其中 revise 的末尾说明针对草稿，并不意味着现有最终几何结论尚未整合。旧 validate 会覆盖原 Evidence，恢复时没有运行它。

本次恢复使用 `resume_guard.py`、`resume_finalize.py`、`resume_validate.py`。恢复开始快照不可覆盖；原 Reports 已另存 Evidence/Resume/reports-before.zip。业务仓库只读，只有当前 r2 目录允许写入。

当前交付以 Reports 下不带 draft 的最终文件及 README 为准。geometry-corrections、compat-state-groups 和 history-decisions 是保留输入；最终对应 geometry-final、compat-workpackages-final、history-effective。不要单独重跑旧脚本将最终裁决回退到草稿。
