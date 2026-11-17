### Task 1: OfferEntry 状态 + 时序对齐 + 模型测试（TDD）

**Files:**
- Modify: `common/src/main/java/com/reclizer/csgobox/terminal/NegotiationModel.java`
- Create: `common/src/test/java/com/reclizer/csgobox/terminal/NegotiationModelTest.java`

**决策（用户已确认）：** 拒绝→下一轮 450ms（对齐 HTML）；接受→立即成交；卡片带状态标签。

- [ ] **Step 1: 先写失败测试**（TDD 红）— 测试文件内容：
```java
package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NegotiationModelTest {

    private static NegotiationModel fresh() {
        NegotiationModel m = new NegotiationModel();
        m.start(100_000L);
        return m;
    }

    @Test
    @DisplayName("start -> TYPING round 1, tick -> PENDING + OfferEntry")
    void startAndPending() {
        NegotiationModel m = fresh();
        assertEquals(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertInstanceOf(NegotiationModel.LineEntry.class, m.history().get(0));
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertInstanceOf(NegotiationModel.OfferEntry.class, m.history().get(1));
        NegotiationModel.OfferEntry oe = (NegotiationModel.OfferEntry) m.history().get(1);
        assertEquals(NegotiationModel.OFFER_PENDING, oe.status());
        assertEquals(0.1139F, oe.offer().wearVal(), 1e-6F);
    }

    @Test
    @DisplayName("accept: PENDING -> ACCEPT_BUSY -> CLOSED next tick, card tagged ACCEPTED")
    void acceptFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.acceptNow(200_000L);
        assertEquals(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_ACCEPTED, lastOfferEntry(m).status());
        m.tick(200_001L);
        assertEquals(NegotiationModel.Status.CLOSED, m.status());
    }

    @Test
    @DisplayName("accept during TYPING is ignored")
    void acceptWhileTypingIgnored() {
        NegotiationModel m = fresh();
        m.acceptNow(150_000L);
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("reject: card tagged REJECTED, next round after 450ms")
    void rejectFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.rejectNow(200_000L);
        assertEquals(NegotiationModel.Status.REJECT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_REJECTED, lastOfferEntry(m).status());
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS);
        assertEquals(2, m.round());
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("round 5 reject -> FAILED + failed system entry")
    void finalRejectFails() {
        NegotiationModel m = fresh();
        for (int r = 1; r <= 5; r++) {
            m.tick(100_000L + r * 500_000L + NegotiationModel.TYPING_MS);
            m.rejectNow(100_000L + r * 500_000L + 1L);
            m.tick(100_000L + r * 500_000L + 1L + NegotiationModel.REJECT_BUSY_MS);
        }
        assertEquals(NegotiationModel.Status.FAILED, m.status());
        assertInstanceOf(NegotiationModel.SystemEntry.class,
                m.history().get(m.history().size() - 1));
    }

    @Test
    @DisplayName("timing constants aligned with HTML")
    void timings() {
        assertEquals(450L, NegotiationModel.REJECT_BUSY_MS);
        assertEquals(0L, NegotiationModel.ACCEPT_BUSY_MS);
    }

    @Test
    @DisplayName("countdown ticks down per second")
    void countdown() {
        NegotiationModel m = fresh();
        m.tick(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownMs());
    }

    private static NegotiationModel.OfferEntry lastOfferEntry(NegotiationModel m) {
        for (int i = m.history().size() - 1; i >= 0; i--) {
            if (m.history().get(i) instanceof NegotiationModel.OfferEntry oe) {
                return oe;
            }
        }
        throw new AssertionError("no offer entry");
    }
}
```
- [ ] **Step 2: 运行验证红**：`./gradlew :common:test -Pactive_versions=26.1.2`（编译失败即可）
- [ ] **Step 3: 实现模型**（`NegotiationModel.java`）：
  - 新增：`public static final int OFFER_PENDING = 0, OFFER_REJECTED = 1, OFFER_ACCEPTED = 2;`
  - `OfferEntry` 加第三字段：`public record OfferEntry(Offer offer, long atMs, int status) {}`
  - `becomePending`：`history.add(new OfferEntry(offer, nowMs, OFFER_PENDING));`
  - 新增私有方法 + 两处调用：
```java
private void markOfferStatus(int status) {
    for (int i = history.size() - 1; i >= 0; i--) {
        if (history.get(i) instanceof OfferEntry oe) {
            history.set(i, new OfferEntry(oe.offer(), oe.atMs(), status));
            return;
        }
    }
}
// acceptNow() 内、状态改完后：markOfferStatus(OFFER_ACCEPTED);
// rejectNow() 内、状态改完后：markOfferStatus(OFFER_REJECTED);
```
  - 时序：`REJECT_BUSY_MS = 450L;`、`ACCEPT_BUSY_MS = 0L;`；删除无引用的 `NEXT_ROUND_MS`（已确认全仓无使用）。
  - 更新类 javadoc 状态机（acceptNow → CLOSED 下一 tick；rejectNow → REJECT_BUSY --(450ms)--> round<5 ? TYPING : FAILED）。
- [ ] **Step 4: 运行验证绿**：`./gradlew :common:test -Pactive_versions=26.1.2`
- [ ] **Step 5: 提交**：`feat(terminal): offer card status + HTML-parity timings (reject 450ms, accept instant)`

