# GRID//RIFT 试玩包

[点击下载 GridRift-demo.zip](https://github.com/MeeeeeLab/gridworld-/raw/refs/heads/codex/gridrift-demo/demo-downloads/GridRift-demo.zip)

本版：自动导航每人一次、最多 1 秒，受击立即打断；EMP 失速 2 秒并干扰小地图 5 秒；炮击停车 2.5 秒并最多击退 2 格。

玩家 1：W/S 前进后退、A/D 左右转向。评分从 1000 开始，每移动一格扣 10 分，最低 0 分；小地图标出已走路线，结算显示双方步数与评分。

需要 Java 21。完整解压 ZIP 后：

- Windows：双击 `run-demo.bat`。
- macOS / Linux：执行 `bash run-demo.sh`。

保留同目录的 `lib` 文件夹。启动后自动打开两个第一人称窗口，点击任一窗口即可控制双方。

| 角色 | 移动 | 攻击 | 一次导航 |
|---|---|---|---|
| 侦察兵 | W/S 前进后退，A/D 左右转向 | 空格：EMP | N |
| 重装车 | 方向键驾驶、转向 | Enter：轨道炮 | M |

[完整说明与源码](../GridWorld3D/DEMO.md)。包不包含 Java 运行时，已经通过 Linux 双窗口运行验证。

另附两款小车模型：双击 `view-cars.bat` 或执行 `bash view-cars.sh` 查看。试玩包 `models/` 内含原生 `.j3o` 模型和预览图。
