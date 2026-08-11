# Relay：Claude ↔ GPT 文档中转站

用途：Claude（Opus，额度有限，负责决策与指导）与 GPT-6.1 Sol（负责执行与大范围阅读）之间的书面交流。
用户负责转交和拍板。本目录是工作区，不是权威记录。

## 规则

1. **文件命名**：`YYYY-MM-DD-NN-<发起方>-to-<接收方>-<主题>.md`。
   - 发起方和接收方取 `claude`、`gpt`、`user` 之一。
   - `NN` 是当天的序号。
2. **文件头**（必填）：

   ```
   From: claude | gpt | user
   To: ...
   Type: REQUEST | REPORT | MEMO | DECISION
   Status: queued | open | answered | closed
   Replies-to: <文件名或 none>
   ```

   - `DECISION` 只记录用户已经确认的决定，并写明确认日期。
3. **报告格式**（写给 Claude 的 REPORT）：
   - 开头先给不超过 30 行的摘要，包括结论、风险、需要的决定。
   - 细节放在后面，用 `路径:行号` 引用，不要大段粘贴代码。
   - Claude 默认只读摘要。
4. **留痕**：
   - 不改写他人的原文。
   - 批注写成 `> [作者 YYYY-MM-DD] …`，插在相关段落之后。
   - 确需修订他人内容时，用删除线保留原文，再写批注说明。
   - 状态变化写在文末的 `## History`，每次一行。
5. **转为权威记录**：用户确认的决定和实施结果，由 GPT 写入 `docs/DEVELOPMENT_LEDGER.md`、`docs/ROADMAP.md` 或 `docs/research/`，并在中转文件里写上链接。之后把该中转文件标为 `closed`，不删除。
6. **索引**：`INDEX.md` 列出所有未关闭的文件。新建或关闭文件的一方负责更新索引。
7. **语言**：中文或英文都可以。转入权威文档时，遵守 `docs/DOCUMENTATION_POLICY.md`（以英文为准）。
8. **既有约定仍然有效**：
   - 未经授权不提交、不推送；
   - 自动化客户端只在隔离实例中运行；
   - Prism 操作遵守 `AGENT_GUIDE.md`；
   - 不针对特定几何做特化。
