package com.shanxiang.buspost.ui.driver

/**
 * 手写签名的**纯层**（不含 Compose / Android.graphics）：手势折点的采集规则与"这算不算签了"的判定。
 *
 * 为什么要单独一层：位图编码只能在真机上做（`Bitmap`/`Canvas`），如果连"签没签"都留在那一侧，
 * 就没有任何 JVM 用例能钉住"误触一下就算签了名"这种事故 —— 而那一格是要写进回执、写进库的。
 */

/** 一笔手势：从按下到抬起经过的折点（画的时候连成折线，这里只存点） */
data class InkPoint(val x: Float, val y: Float)

/**
 * 判"签了没签"的唯一口径。两个数各自钉一件事：
 * · [MIN_POINTS_PER_STROKE] —— 一笔画至少要有 3 个折点才算"一划"，两个点只是按下与抬起之间的抖动；
 * · [MIN_STROKES] —— 至少两划才算签名，一划（比如随手点一下再滑走）不够。
 * 阈值放低一档就会把"手掌压在屏幕上"当成签名，放高一档会把快签的人挡在门外；
 * 这两格由用例逐条钉（`HandoverSignatureContractTest`），不许在 UI 里各写一套。
 */
object HandoverSignature {
    const val MIN_POINTS_PER_STROKE = 3
    const val MIN_STROKES = 2

    /** 这一组折点够不够算"签过了" */
    fun hasInk(strokes: List<List<InkPoint>>): Boolean =
        strokes.count { it.size >= MIN_POINTS_PER_STROKE } >= MIN_STROKES

    /** 有效笔数：只数达到单划门槛的那些（用于"还差几划"的提示，不数毛刺） */
    fun strokeCount(strokes: List<List<InkPoint>>): Int =
        strokes.count { it.size >= MIN_POINTS_PER_STROKE }

    /**
     * 上传口的拦停决定（与凭证照片同一条纪律）：
     * 后端 `HandoverServiceImpl.confirmHandover` 对**已完成**的交接直接 return（幂等），
     * 先落"交接完成"就再也补不进签名 URL ⇒ 签了名但没拿到 URL 时**不提交**，
     * 而不是"签名丢了但交接记上了"。
     */
    fun shouldBlockSubmit(signed: Boolean, signUrl: String): Boolean = signed && signUrl.isEmpty()

    /**
     * 回执照 Which：只按**后端回读到的**两个 URL 说话。
     * 四种组合各一句，任何一句都不许把"缺"念成"核对过、确实没签"。
     */
    fun receiptFor(photoStored: Boolean, signStored: Boolean): String = when {
        photoStored && signStored -> "交接已提交，凭证照片与签名均已入库"
        photoStored -> "交接已提交，凭证照片已入库；签名那一格回读里没有"
        signStored -> "交接已提交，签名已入库；这一单没附凭证照片"
        else -> "交接已提交，照片与签名两格后端都回读为空"
    }

    /** 位图尺寸口径：签名板按固定像素导出，缩放/密度都不进签名图（同一台机器上签两次要能对上） */
    const val EXPORT_WIDTH_PX = 1024
    const val EXPORT_HEIGHT_PX = 480
}
