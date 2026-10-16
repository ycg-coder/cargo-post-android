#!/usr/bin/env bash
# 保证"某个测试类的结果 XML 存在"（队列 #96 / 台账 D-136 第八格）。
#
# 要修的那件事不是判据坏，是**批自己把证据删了**：任何 `*_test_negative_controls.sh` 跑
# `testDebugUnitTest --tests <单个类>` 时，Gradle 会用**那一个类**的结果**覆盖整个**
# `app/build/test-results/testDebugUnitTest/` 目录 ⇒ 排在后面、要读别的类 XML 的书（实读两本：
# check_notify_result_probes.sh 与 check_oss_credits_probes.sh）必定看不见产物 ⇒ 按当前次序
# 全批永远不可能全绿。上一轮就是这么红的，恢复证据后要人工重跑两本才过。
#
# 这一格**禁止**用快照/复制旧产物来"还原"——那等于拿陈旧证据冒充新鲜读数（用户立的规矩：
# 只用实测输出充当完成凭证）。唯一诚实的做法是**真的再跑一次**，而且必须**不带 --tests 过滤**，
# 因为过滤正是删产物的那只手。
#
# ⚠ 本机跑全量必须走 ASCII 盘符那条路（tools/run-unit-tests.sh）：工程路径与用户名都含中文，
#   Gradle 把测试 worker 的 classpath 写成 UTF-8 的 @argfile，而 java 启动器按 GBK 读，
#   中文路径整条失效 ⇒ 满屏 NoClassDefFoundError（kotlin/*、okhttp3/*）。**那不是断言失败，是环境假失败**，
#   直接 ./gradlew 拿到的 XML 会带一串 errors，判据就会把环境红念成代码红。
#   所以：有 subst 的 Y:/Z: 就用 run-unit-tests.sh；没有就明写"这一轮的读数不可信"并原样把 gradlew 的失败转出去。
#
# 用法：ensure_test_xml.sh <XML 相对路径>
# 退出码：0 = XML 就位（本来就有，或刚跑出来）；2 = 跑过了还是没有（用例本身没建对/类名变了）；
#         3 = gradle 自己红，把它的读数原样转出去，不粉饰。
set -u

XML="${1:-}"
if [ -z "$XML" ]; then
    echo "× 用法：ensure_test_xml.sh <XML 相对路径>"; exit 2
fi
if [ -f "$XML" ]; then
    exit 0
fi

echo "! 预检缺产物：$XML —— 触发一次**无过滤全量** testDebugUnitTest（绝不用 --tests：那只手就是删产物的）"
if [ -d "/y/.gradle" ] && [ -d "/z/cargo-post-android" ]; then
    echo "  走 ASCII 盘符那条 runner（Y:/Z: 在）：tools/run-unit-tests.sh"
    bash tools/run-unit-tests.sh > tmp/ensure_test_xml.log 2>&1
    RC=$?
else
    echo "  ⚠ Y:/Z: 没 subst，退回裸 gradlew —— 中文路径下这轮读数可能是**环境假失败**（NoClassDefFoundError），不是代码红"
    ./gradlew :app:testDebugUnitTest --console=plain > tmp/ensure_test_xml.log 2>&1
    RC=$?
fi
if [ "$RC" -ne 0 ]; then
    echo "× gradle 红（rc=$RC），最后 15 行原样贴出，不改写："
    tail -15 tmp/ensure_test_xml.log
    exit 3
fi
if [ ! -f "$XML" ]; then
    echo "× 全量跑完了仍然没有 $XML —— 不是产物被删，是这个类根本没被执行（类名/包名与判据不一致）"
    echo "  现读同目录 XML：$(ls app/build/test-results/testDebugUnitTest/ 2>/dev/null | wc -l | tr -d ' ') 个"
    exit 2
fi
N=$(ls app/build/test-results/testDebugUnitTest/*.xml 2>/dev/null | wc -l | tr -d ' ')
echo "✔ 产物已就位：$XML（同目录现读 $N 个 XML）"
exit 0
