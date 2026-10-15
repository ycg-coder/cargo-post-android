#!/usr/bin/env bash
# 四条"同屏口径"判据（缺陷 #47，配 tools/caliber_negative_controls.sh）
# 用法：bash tools/check_caliber_probes.sh      退出码非 0 = 这四格被拆了
#
# 这四格都不是"功能没做"，而是**界面在替后端宣布一件后端没说过的事**：
#   ① 商城排序只拿着已加载的那几页排，却念成"全店最便宜的是这件"；
#   ② 空态把"清单还没取回来/本机按名字分的类没匹配上"说成"这类山货暂时没货"（库存是每件商品另一个字段）；
#   ③ 未读数同屏两个来源（后端 `/unread-count` 与本页自己算的），翻页就跳数；
#   ④ 公交页搜索框只覆盖半径内那几条线路，却写成"搜索线路名称"，词一打错整块列表静默消失。
# 写法沿用既有教训：先验文件存在；按内容过滤注释行（注释里引用旧病句是在**解释为什么改**，不是把病句写回产品）；
# 每个"目标 0"都配一条同机制反证；计数钉的是生效代码的确切字面量，不是关键字出现次数。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

A=app/src/main/java/com/shanxiang/buspost
GS=$A/ui/user/GoodsScreen.kt
DM=$A/ui/driver/DriverMessagesScreen.kt
NS=$A/ui/user/NotificationScreen.kt
BS=$A/ui/user/BusScreen.kt
MP=$A/ui/user/MinePages.kt
T=app/src/test/java/com/shanxiang/buspost/ui/user/GoodsScopeContractTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$GS" "$DM" "$NS" "$BS" "$MP" "$T"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（扫描面塌了，本轮所有数不采信）"; exit 2; }
done

noncomment() { grep -vE "$NC"; }
# 非注释行里某串的出现次数
cnt() { grep -n "$2" "$1" | noncomment | wc -l | tr -d ' '; }

# KL-a 排序/筛选启用时必须走全店入口（`/product/list`），且列表源是"全量 ?: 分页"的兜底形状
KLA_POOL=$(cnt "$GS" 'catalog ?: allProducts')
KLA_FETCH=$(cnt "$GS" 'Repo.products()')
KLA_NEED=$(cnt "$GS" 'goodsNeedsCatalog')
KLA_TRIGGER=$(cnt "$GS" 'if (vm.needsCatalog) vm.ensureCatalog()')
# 反证：旧写法"直接拿已加载的分页排"必须为 0（`allProducts.filter` 是那一形的指纹）
KLA_OLD=$(cnt "$GS" 'allProducts.filter')

# KL-b 空态那句必须三分（正在取 / 没有这一类 / 没搜到），且"没货"这句只许存在于注释里
KLB_BRANCH=$(cnt "$GS" 'goodsEmptyCopy')
KLB_TITLE=$(cnt "$GS" '"在架清单里没有这一类"')
KLB_NOSTOCK=$(cnt "$GS" '没货')
# 反证：同一模式在注释那侧必须数得出（本轮实测 2 处是在解释为什么改），否则那个 0 是"整句被删了"
KLB_NOSTOCK_ALL=$(grep -c '没货' "$GS")

# KL-c 未读只有一个出处：司机消息页头读 AppState，本页自己算的那个不再出现在"未读"字样里
KLC_HEAD=$(cnt "$DM" '"\$unread 条未读"')
KLC_LOCAL=$(cnt "$DM" 'vm.shown.count { !it.read }} 条未读')
# 用户侧分段器那两个数必须带"本页"限定词（与顶栏那个后端数区分开）
KLC_PAGE_QUAL=$(cnt "$NS" '"本页未读 \$')
KLC_BARE=$(cnt "$NS" '"未读 \$')

# KL-d 搜索框与未命中提示都说清"只在附近搜"
KLD_HINT=$(cnt "$BS" '"在附近线路里搜名称"')
KLD_MISS=$(cnt "$BS" '附近线路里没搜到')
KLD_OLD=$(cnt "$BS" '"搜索线路名称"')

# KL-e 用例条数（纯函数判据的底盘，删用例必须让这里变红）
KLE=$(grep -cE '^[[:space:]]*@Test' "$T")

# KL-g 反馈提交的回执：Ack 不许被丢在地上，演示那一次必须说"没发给站点"（缺陷 #76）
#     反证是"真实提交"那句还在（=1）：只剩一句反而说明两分支塌回了一句谎话。
KLG_ACK=$(cnt "$MP" 'val ack = Repo.submitFeedback')
KLG_FLAG=$(cnt "$MP" 'sentDemo = ack.demo')
KLG_DEMO=$(cnt "$MP" '这条只记在演示数据里，没有发给站点。')
KLG_REAL=$(cnt "$MP" '反馈已提交，站点会在班次调整时统一回复。')

echo "KL-a 全量源/取数点/闸门/触发 · 旧分页直排 = $KLA_POOL/$KLA_FETCH/$KLA_NEED/$KLA_TRIGGER · $KLA_OLD （目标 1/≥1/≥2/1 · 0）"
echo "KL-b 三分说法调用/标题句 · 『没货』非注释/全部 = $KLB_BRANCH/$KLB_TITLE · $KLB_NOSTOCK/$KLB_NOSTOCK_ALL （目标 ≥2/1 · 0/≥1）"
echo "KL-c 页头读后端数 · 本页自算的未读句 = $KLC_HEAD · $KLC_LOCAL （目标 1 · 0）"
echo "KL-c2 用户侧『本页未读』限定 / 裸『未读 N』 = $KLC_PAGE_QUAL/$KLC_BARE （目标 1/0）"
echo "KL-d 附近限定/未命中提示 · 旧『搜索线路名称』 = $KLD_HINT/$KLD_MISS · $KLD_OLD （目标 1/≥1 · 0）"
echo "KL-e 口径用例条数                        = $KLE （目标 ≥5）"
echo "KL-g 反馈 Ack/演示标志/两句回执           = $KLG_ACK/$KLG_FLAG/$KLG_DEMO/$KLG_REAL （目标 1/1/1/1）"

fail=0
[ "$KLA_POOL" = 1 ] || { echo "× KL-a 列表源不再是『全量 ?: 分页』（现 $KLA_POOL）：排序又会只在那几页里比高低"; fail=1; }
[ "$KLA_FETCH" -ge 1 ] || { echo "× KL-a 商城不再取全店清单（现 $KLA_FETCH）：全量口径成了空话"; fail=1; }
[ "$KLA_NEED" -ge 2 ] || { echo "× KL-a 全店口径判定被拆（现 $KLA_NEED，定义+调用各 1）：页面会自己决定什么时候该取全量"; fail=1; }
[ "$KLA_TRIGGER" = 1 ] || { echo "× KL-a 筛选一开就取全量的触发点不在（现 $KLA_TRIGGER）：needsCatalog 成了摆设"; fail=1; }
[ "$KLA_OLD" = 0 ] || { echo "× KL-a 回来了『直接拿已加载分页排』那一直排写法（现 $KLA_OLD 处）：界面念出的第一名只是这一页的第一名"; fail=1; }
[ "$KLB_BRANCH" -ge 2 ] || { echo "× KL-b 空态说法不再走纯函数（现 $KLB_BRANCH）：话术会退回页面里各写一份"; fail=1; }
[ "$KLB_TITLE" = 1 ] || { echo "× KL-b『在架清单里没有这一类』这句没了（现 $KLB_TITLE）：三分说法塌回一句"; fail=1; }
[ "$KLB_NOSTOCK" = 0 ] || { echo "× KL-b 生效代码里出现『没货』（现 $KLB_NOSTOCK 处）：本机无权替后端的库存字段宣布卖完"; fail=1; }
[ "$KLB_NOSTOCK_ALL" -ge 1 ] || { echo "× KL-b 反证掉了（『没货』全书命中 $KLB_NOSTOCK_ALL）：上面那个 0 可能是因为整句被删了而不是改对了"; fail=1; }
[ "$KLC_HEAD" = 1 ] || { echo "× KL-c 司机消息页头不再读后端未读数（现 $KLC_HEAD）：与 tab 角标又会各说一套"; fail=1; }
[ "$KLC_LOCAL" = 0 ] || { echo "× KL-c 页头拿本页已加载的消息自己算未读（现 $KLC_LOCAL 处）：翻页时这个数字会跳"; fail=1; }
[ "$KLC_PAGE_QUAL" = 1 ] || { echo "× KL-c2 用户侧分段的『本页』限定词没了（现 $KLC_PAGE_QUAL）：同屏两个未读数又长得一模一样"; fail=1; }
[ "$KLC_BARE" = 0 ] || { echo "× KL-c2 又出现裸的『未读 N』（现 $KLC_BARE 处）：没说清是本页还是全店"; fail=1; }
[ "$KLD_HINT" = 1 ] || { echo "× KL-d 搜索框不再说明只搜附近（现 $KLD_HINT）：用户以为全网能点名查"; fail=1; }
[ "$KLD_MISS" -ge 1 ] || { echo "× KL-d 搜不到时没有那句『附近线路里没搜到』（现 $KLD_MISS）：词一打错整块列表静默消失"; fail=1; }
[ "$KLD_OLD" = 0 ] || { echo "× KL-d 旧『搜索线路名称』那句话回来了（现 $KLD_OLD 处）：它承诺的是全网搜索"; fail=1; }
[ "$KLE" -ge 5 ] || { echo "× KL-e 口径用例少于 5 条（现 $KLE）：这三句话的分工没人钉"; fail=1; }
[ "$KLG_ACK" = 1 ] || { echo "× KL-g 反馈提交又把 Ack 丢在地上（现 $KLG_ACK）：演示那次没发给任何站点，界面却说『站点会回复』"; fail=1; }
[ "$KLG_FLAG" = 1 ] || { echo "× KL-g 没把 ack.demo 记进回执（现 $KLG_FLAG）：分支条件没了，两句回执会塌成一句"; fail=1; }
[ "$KLG_DEMO" = 1 ] || { echo "× KL-g 演示那次那句真话没了（现 $KLG_DEMO）"; fail=1; }
[ "$KLG_REAL" = 1 ] || { echo "× KL-g 反证掉了（真实提交那句=$KLG_REAL，目标 1）：上面那个 1 可能是整块回执被删了"; fail=1; }

if [ "$fail" = 0 ]; then echo "四条口径判据通过"; else echo "四条口径判据失败"; fi
exit "$fail"
