# 第三方模组 Reference 结果

生成时间：2026-09-14T14:11:06.4765146+08:00

资格结论：**PASS**；目标 10，复用 4，新发布 6，失败 0。

| Project | Entry | 结果 | Version | Commit | Source kind / scope | Tree SHA-256 | 实际路径 |
|---|---|---|---|---|---|---|---|
| Create | create-6.0.10 | reused-existing | 6.0.10 | ac0c444d9828da3453ae8cc65338e8de063286fb | upstream-source; java-only-upstream-source-subset | 9aab72bccb3687d3654f7abecd3e4350c85538e4f6e831fe38404ba33e329695 | D:\Workspaces\References\minecraft-references\create-6.0.10 |
| Sable | sable-2.0.5 | published | 2.0.5 | 6966d2928340de7631abcecf8549904b877df0a8 | upstream-repository-archive; complete-upstream-repository-archive | 98de13f6b60681c5245ce2e69895b31b93444c063f8d53c64a49c56317aceb0a | D:\Workspaces\References\minecraft-references\sable-2.0.5 |
| Veil latest applicable | veil-4.5.0 | published | 4.5.0 | 8af4e37925febb7309745c5d381f9b8807fd55b8 | upstream-repository-archive; complete-upstream-repository-archive | 478c12e9f16cdfa52eeb112e72782d5ab98bef0b9a326809f8628de39d4a6649 | D:\Workspaces\References\minecraft-references\veil-4.5.0 |
| Veil audit actual | veil-4.3.2 | published | 4.3.2 | 540ad3778c1a5dafe905420adb21cb05e459b5c4 | upstream-repository-archive; complete-upstream-repository-archive | f6dfd403085fdf4137a7490d1062c8d4dab1e978acf4a7de19257f8c9b2ed7e4 | D:\Workspaces\References\minecraft-references\veil-4.3.2 |
| Aeronautics / Simulated / Offroad | aeronautics_bundled-1.3.2 | published | 1.3.2 | e720946bb5e84d618d0f1aa47e1a19ab2adb5bd7 | upstream-development-branch-archive; complete-upstream-repository-archive | 4e53142e738a270ec32c10a845fdecb81cf960b3ba075a3cfc6a4e297f1c83fb | D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2 |
| Flywheel | flywheel-1.0.6-neoforge | reused-existing | 1.0.6 | 未证明；02af394 为内容等价候选 | exact-source-jars; mechanically-extracted-exact-source-jar-java-files | ddda1264ff7c07d9a2f48eed771d133295a9609aa7caf16c61974ab2475e2734 | D:\Workspaces\References\minecraft-references\flywheel-1.0.6-neoforge |
| Ponder audit actual | ponder-1.0.82+mc1.21.1 | published | 1.0.82+mc1.21.1 | 355b488f3d5c5756b33de190d2dd4f7e483480e0 | exact-source-jars; complete-exact-source-jar-extraction | b4336b36855feea54432040de6174115da9f296313dea28297c6337a2e6aa0ee | D:\Workspaces\References\minecraft-references\ponder-1.0.82+mc1.21.1 |
| Ponder latest applicable | ponder-1.0.87+mc1.21.1 | published | 1.0.87+mc1.21.1 | 89819291b726708d119b118dea01667eae0b64c9 | exact-source-jars; complete-exact-source-jar-extraction | 37d34d6d0ed870bf4244b82413918285de24b8c6851056b1566659134d5e669a | D:\Workspaces\References\minecraft-references\ponder-1.0.87+mc1.21.1 |
| Sable Companion | sablecompanion-1.6.0 | reused-existing | 1.6.0 | 652ecf0e051846f3a433dd20126dd4d64ec3793a | upstream-source; java-only-upstream-source-subset | 7592000ddfd15fb0e77c2f2ed690c99d3ccff3a6ca397f8012ce4e5ae3ba63b8 | D:\Workspaces\References\minecraft-references\sablecompanion-1.6.0 |
| Zume | zume-1.2.2 | reused-existing | 1.2.2 | 0bc4e9b0f67be27bcb036439bda09a33a942c5d1 | upstream-source; java-only-upstream-source-subset | 42d012801d325cdf746194c6bffd5fd92c9abb026719f77e3b5fbba4da184284 | D:\Workspaces\References\minecraft-references\zume-1.2.2 |

## 关键来源结论

- Create 6.0.10、Flywheel 1.0.6、Sable Companion 1.6.0、Zume 1.2.2 复用既有 entry；报告明确其 Java-only 或 exact-source-JAR 范围。
- Ponder 1.0.82 与 1.0.87 的同版本二进制 MANIFEST 分别嵌入 355b488 与 8981929；Gradle module 同时固定 source/binary hash，源码与对应 commit archive 逐文件相关通过。
- Flywheel 1.0.6 没有上游 tag，source/binary MANIFEST 也未嵌入 commit。02af394 与 423 个 authored Java 文件一致，14 个 package-info.java 由构建规则生成，因此仅记录为内容等价候选。
- Sable Companion 当前 main 仍为 652ecf0，声明版本 1.6.0，既有 12 个 Java 文件与该 archive 全部一致；上游为 common + Fabric，common 内有 NeoForge descriptor，没有独立 NeoForge module。
- Create 与 Zume 还从各自精确 commit 的 gradle.properties 复核了 Minecraft 1.21.1 及 NeoForge 字段；不是只根据 tag 名称判定。
- Aeronautics / Simulated / Offroad 来自同一 main commit e720946，声明 1.3.2、Minecraft 1.21.1，并明确无发行 tag。

## 旧目录处置候选

- aeronautics_bundled-1.3.0 -> D:\Workspaces\References\minecraft-references\aeronautics_bundled-1.3.2；旧 seal fb10ccd3a4a1ca1d1ab06d61d97490ca64258df5c9bd038dfe5bc43ec53400ef。仅供根任务备份、验证和回收，本 worker 未删除或移动。
- ponder-1.0 -> D:\Workspaces\References\minecraft-references\ponder-1.0.82+mc1.21.1, D:\Workspaces\References\minecraft-references\ponder-1.0.87+mc1.21.1；旧 seal 4af3d3b8a03fe921d22eaaee8e9241052d85df0a35082dfc606aad82a6c0f239。仅供根任务备份、验证和回收，本 worker 未删除或移动。
- sable-2.0.3 -> D:\Workspaces\References\minecraft-references\sable-2.0.5；旧 seal 837c54a6b5756141684a0f833d76ef4a59d0258de64c27c795925450ab19c126。仅供根任务备份、验证和回收，本 worker 未删除或移动。
- veil-4.1.4 -> D:\Workspaces\References\minecraft-references\veil-4.3.2, D:\Workspaces\References\minecraft-references\veil-4.5.0；旧 seal a940af8fa62a3a20b926f31e8ae8137a1f499435c191e5f0c7f858100dcf3278。仅供根任务备份、验证和回收，本 worker 未删除或移动。

未修改 Radiance/MCVR，未启动 Prism、GUI 或游戏；没有删除动作。
