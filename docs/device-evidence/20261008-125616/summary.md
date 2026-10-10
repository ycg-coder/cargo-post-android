# DEVICE_STATUS: DEVICE_UNAVAILABLE

分类依据：adb 的真实报错原文（下面这份），不是「没有设备」这句转述。
```
adb.exe: no devices/emulators found
```

出路：接设备并开 USB 调试；unauthorized 先 adb kill-server && adb start-server。
在此之前，本工程一律记 NOT VERIFIED ON DEVICE，JVM 用例不冒充设备证据。
