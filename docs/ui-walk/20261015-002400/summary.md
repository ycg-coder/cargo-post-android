# UI 遍历汇总 2026-10-14T16:28:54Z

- 模式：**深链扫路由**（am start VIEW，不注入点击）
- 扫过：**27** 条路由
- 抓取失败：0
- 屏幕 1080x2400，密度 440，48dp=132px，设备 1f71ee9b
- 路由分母（源码注册的 composable）：**27**（带 {参数} 的路由这里不进，需要真实 id 的交给点击遍历）
- 每屏 `layout.txt` = 几何自检（越界 / 兄弟文字重叠 / 热区<132px）

## 逐屏清单（目录 ← 到达路径）
01-route_login	d=1	route/login	可点=26	布局告警=0	自检跑过=1	ef4d5ff9302b
02-route_userShell	d=1	route/userShell	可点=26	布局告警=0	自检跑过=1	ef4d5ff9302b
03-route_driverShell	d=1	route/driverShell	可点=26	布局告警=0	自检跑过=1	ef4d5ff9302b
04-route_send	d=1	route/send	可点=44	布局告警=0	自检跑过=1	f94f49b7200a
05-route_bus	d=1	route/bus	可点=26	布局告警=0	自检跑过=1	1343829ccf67
06-route_orders	d=1	route/orders	可点=32	布局告警=0	自检跑过=1	0148bb78b335
07-route_parcel	d=1	route/parcel	可点=32	布局告警=0	自检跑过=1	2160cf3aff50
08-route_arrangements	d=1	route/arrangements	可点=10	布局告警=0	自检跑过=1	b686863648ac
09-route_wallet	d=1	route/wallet	可点=17	布局告警=0	自检跑过=1	04a2fab38d46
10-route_notifications	d=1	route/notifications	可点=19	布局告警=0	自检跑过=1	df043212278e
11-route_profile	d=1	route/profile	可点=55	布局告警=0	自检跑过=1	84dea0ddc895
12-route_address	d=1	route/address	可点=13	布局告警=0	自检跑过=1	3518f1d732d8
13-route_addressSearch	d=1	route/addressSearch	可点=17	布局告警=0	自检跑过=1	4958779f04f2
14-route_addressSelect	d=1	route/addressSelect	可点=13	布局告警=0	自检跑过=1	3518f1d732d8
15-route_mapPointPicker	d=1	route/mapPointPicker	可点=22	布局告警=0	自检跑过=1	9f1ac6068af2
16-route_sendAddressPicker	d=1	route/sendAddressPicker	可点=38	布局告警=0	自检跑过=1	9ab8b9d3b830
17-route_busQr	d=1	route/busQr	可点=20	布局告警=0	自检跑过=1	3cb42be1817d
18-route_feedback	d=1	route/feedback	可点=32	布局告警=0	自检跑过=1	95fde8d1995b
19-route_settings	d=1	route/settings	可点=68	布局告警=0	自检跑过=1	161fd2fd33cc
20-route_ossLicenses	d=1	route/ossLicenses	可点=37	布局告警=0	自检跑过=1	a2d3f6744020
21-route_privacy	d=1	route/privacy	可点=29	布局告警=0	自检跑过=1	78b0c019f543
22-route_terms	d=1	route/terms	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
23-route_driverNav	d=1	route/driverNav	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
24-route_driverGuide	d=1	route/driverGuide	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
25-route_driverMessages	d=1	route/driverMessages	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
26-route_handover	d=1	route/handover	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
27-route_driverException	d=1	route/driverException	可点=29	布局告警=0	自检跑过=1	1f3e42987b24
- **未产生导航的路由**（闸门拒绝 / 已在该页，不算验过）：7 条

```
userShell	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
driverShell	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
driverNav	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
driverGuide	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
driverMessages	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
handover	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
driverException	与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）
```

## 告警类型去重计数（坐标与像素已归一）

- 清理：设备侧 /sdcard/w.xml 已删；本机全量 logcat 副本已删（每屏切片保留在 `*/logcat-slice.txt`）
- 已删 27 张截图（判据来自 dump 的几何数字与 logcat 切片，不依赖图）；需要人眼复核用 --keep-shots
- 汇总日志线索：`logfindings.txt`（1 行）
- 证据目录占用：33893 KB（删图后）
