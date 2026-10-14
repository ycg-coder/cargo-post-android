#!/usr/bin/env bash
# 「兜底/演示数据必须说出来」的结构判据（配 tools/demo_disclosure_negative_controls.sh）
#
# 为什么要有这一本：2026-10-15 真机第一轮发现 `DemoNote` 整个函数体被一句裸 `return` 短路掉了，
# 全站 27 处各自写好的诚实文案一起失声、177 处 `demo` 标志没人读 —— 接口不通回落本地演示集时，
# 界面把演示班次当成真实班次念给用户。这条规矩**当时只活在注释里，没有任何判据**，
# 所以它能被安静地删掉而全批判据照样全绿。这一本就是那个洞的补丁。
# 顺带管住同轮的另外三格：明文图升到 https（正式版禁明文，不升就永远是占位图）、
# 换手机号要作废上一位的验证码状态与判决、司机"我的"要念排班档案名而不是只念账号昵称。
#
# 用法：bash tools/check_demo_disclosure_probes.sh        退出码非 0 = 这一格被拆了
# 写法沿用本仓规矩：按内容过滤不按行号；每个"目标 0"配同文件正对照；下限一律等于现读。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
BITS=$M/ui/common/Bits.kt
MD=$M/data/model/Models.kt
LVM=$M/ui/login/LoginViewModel.kt
LSC=$M/ui/login/LoginScreen.kt
DMINE=$M/ui/driver/DriverMineScreen.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$BITS" "$MD" "$LVM" "$LSC" "$DMINE"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
body() { sed -n "/^fun $2/,/^}/p" "$1"; }

FAIL=0
say() { # say <id> <现读> <下限> <说明>
  if [ "${2:-0}" -ge "${3:-1}" ]; then
    echo "✔ $1 = $2（下限 $3）$4"
  else
    echo "× $1 = $2（下限 $3）$4"
    FAIL=1
  fi
}

# 恰好 N / 目标 0 的每一格都必须**等值**判断。
# 这里曾经全部写成 `-ge`：目标 0 的下限是 0 ⇒ `-ge 0` 恒真，那一格无论怎么注入都不会红
# （负控 NC-3 当场把它抓出来了 —— 与 D-082 那批"下限偏离实测"是同一类空转）。
sayx() { # sayx <id> <现读> <恰好> <说明>
  if [ "${2:-x}" = "${3:-1}" ]; then
    echo "✔ $1 = $2（恰好 $3）$4"
  else
    echo "× $1 = $2（恰好 $3）$4"
    FAIL=1
  fi
}

# DD-a DemoNote 的函数体不许被无条件 return 短路：`if (!demo) return` 恰好 1 处是守卫，
#      裸 `return` 必须为 0。正对照就是同一段里那一处带条件的守卫 —— 数的是同一种形状，
#      所以"目标 0"不是空集，是真判据。
DD_GUARD=$(nc_count "$BITS" 'if (!demo) return')
DD_BARE=$(body "$BITS" 'DemoNote(' | grep -cE '^[[:space:]]+return$' || true)
sayx DD-a "$DD_GUARD" 1 "DemoNote 只许带条件守卫"
sayx DD-a2 "$DD_BARE" 0 "函数体开头不许有裸 return（有=整条诚实标注被静音）"

# DD-b 调用点不许缩水：现读 27 处各自带文案，删一处=那一屏重新变成静默兜底
DD_CALL=$(grep -rn "DemoNote(" $M --include=*.kt | grep -v "fun DemoNote" | grep -cvE "$NC" || true)
say DD-b "$DD_CALL" 27 "全站 DemoNote 调用点"

# DD-c 不许有人把 demo 写死成 false（那等于逐屏关掉标注）；
#      正对照：WorkbenchScreen 那一处刻意写 `DemoNote(true, ...)`，现读恰好 1
DD_FALSE=$(grep -rn "DemoNote(false" $M --include=*.kt | grep -cvE "$NC" || true)
DD_TRUE=$(grep -rn "DemoNote(true" $M --include=*.kt | wc -l | tr -d ' ')
sayx DD-c "$DD_FALSE" 0 "不许写死 false"
sayx DD-c2 "$DD_TRUE" 1 "写死 true 的那一处（工作台演示班次）仍要在"

# DD-d demo 标志仍有人算（下限=现读，掉了说明某屏把它丢了）
DD_FLAG=$(grep -rn "\.demo" $M --include=*.kt | grep -cvE "$NC" || true)
say DD-d "$DD_FLAG" 177 "全站 .demo 读数"

# DD-e 明文图升 https：判定函数与调用点各恰好一份，且退回分支保住本机 http
DD_DEF=$(nc_count "$MD" 'internal fun isPrivateHost')
DD_USE=$(nc_count "$MD" 'if (isPrivateHost(host))')
DD_HTTPS=$(nc_count "$MD" '"https://" + s.substringAfter')
sayx DD-e "$DD_DEF" 1 "isPrivateHost 定义"
sayx DD-e2 "$DD_USE" 1 "imageUrlOrEmpty 里的调用"
sayx DD-e3 "$DD_HTTPS" 1 "公网 http 升 https"

# DD-f 换手机号作废上一位的验证码状态：VM 一处定义、页面一处引用，
#      且页面不许再绕过它直接写 vm.mobile（现读 0）
DD_VM=$(nc_count "$LVM" 'fun onMobileChange')
DD_SC=$(nc_count "$LSC" 'vm.onMobileChange')
DD_RAW=$(nc_count "$LSC" 'vm.mobile = ')
sayx DD-f "$DD_VM" 1 "LoginViewModel.onMobileChange"
sayx DD-f2 "$DD_SC" 1 "LoginScreen 走这个口子"
sayx DD-f3 "$DD_RAW" 0 "页面不许直接改 vm.mobile（正对照：上一行 =1）"

# DD-g 司机"我的"以排班档案名为主：driverName 至少被读 1 次，账号昵称仍要留着标第二行
DD_ROSTER=$(nc_count "$DMINE" 'Prefs.driverName')
DD_ACCT=$(nc_count "$DMINE" 'Prefs.nickname')
sayx DD-g "$DD_ROSTER" 1 "档案名上屏"
sayx DD-g2 "$DD_ACCT" 1 "账号名仍标得出来（两个都是后端给的真值，不许混成一个）"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ demo_disclosure：$DD_CALL 处标注在岗，DD-a…DD-g 全过"
else
  echo "❌ demo_disclosure：有格被拆，见上面 ×"
fi
exit $FAIL
