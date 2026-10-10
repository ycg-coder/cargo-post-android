# GitHub 仓库与发布交接（方案 A：私有库 → 转移进 cgp-team）

## 现状（全部为实测，不是计划）
- App 的家：`https://github.com/ycg-coder/cargo-post-android`（**私有**，分支 `main`）。
  org 内建库被 403 挡（`ycg-coder` 在 `cgp-team` 无 admin），所以先落个人私有库，等管理员转移。
- CI：`.github/workflows/build-and-release.yml`
  - `verify`（每次 push）：`testDebugUnitTest` + `tools/check_*.sh` 全批判据 + `tools/check_ledger_tables.py` + 出 debug 包。
  - `release`（**只能手动触发**）：缺任一签名项或 baseUrl 不是 https 就**显式失败**；出包后**扫 dex 取证**——
    包里必须找得到给定 baseUrl，且残留 `127.0.0.1 / localhost / 10.0.2.2 / 1.15.29.107` 任一即红。
- 已登记的 Secrets（只写名字，值不进仓库、不进日志）：
  `SIGNING_KEYSTORE_B64`、`SIGNING_STOREPASSWORD`、`SIGNING_KEYALIAS`、`SIGNING_KEYPASSWORD`、`AMAP_ANDROID_KEY`、`AMAP_WEB_KEY`。
- 红线仍然成立：签名材料在工程之外（`../buspost-signing-backup/`），源码树内 `find . -name '*.jks'` = **0**；
  `local.properties`（含高德 key 与 baseUrl）不入库。

## 给管理员的两步
1. 我在个人侧发起转移（`gh api -X POST repos/ycg-coder/cargo-post-android/transfer -f owner=cgp-team`），你在 org 侧接受邀请。
2. 转移后请确认 org 的 Actions 允许私有库运行 workflow；Secrets 随仓库一起过去，但如果你们改用 **org-level secrets**，需要在 org 设置里建同名项后把 repo 级的删掉。

## 出生产包（转移前后同一条命令，换仓库名即可）
```bash
gh workflow run build-and-release.yml -R ycg-coder/cargo-post-android   -f baseUrl=https://api.qwer2333.com/api
```
产物在 run 的 artifacts 里（`release-apk`）。私有库的 artifact/Release 下载需要登录或 token；
要给司机村民"点开就下"的公网链接，得把库转公开（**先做台账脱敏**：仓内 `1.15.29.107` 出现 46 处、
另有本机开发库口令线索），或另挂分发页。

## 后端侧已实测的事实（写包之前要知道）
- 云上后端可用且是 https：`https://api.qwer2333.com/api/app-api/...`（nginx `location /api/ → 127.0.0.1:48080`），
  未带 token 正确回 `{"code":401,"msg":"账号未登录"}`。
- **云上账号未解决**：源仓 `CHANGELOG.md`/`docs/DEMO_GUIDE.md` 记的 `13800138001~9`、`13900001001~3` + 口令 `123456`
  在云上**逐个实测全部 `1004003000`**；`/auth/register` 回 401（不许自助注册）；管理端 `admin/admin123` 被图形验证码挡（`400 验证码不能为空`）。
  ⇒ 云库口令与仓库种子 SQL 已分叉，**`DEMO_GUIDE.md` 那份现在是错的**；需要队友在云上给一个绑了 `transport_driver` 的会员设可用口令。
- 本机后端副本落后：本地 `cargo-post-platform` HEAD `3633e32d`（09-26，分支 `perf/bigscreen-fast-firstload`），
  真远端 master = `c83bf6be`（10-10 03:38 合 PR #183，含 `V027__idempotency_unique_keys.sql`）⇒ App 侧"后端无可复用幂等键"的前提要重验。
