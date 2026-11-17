<template>
  <div class="agent-page">
    <div class="agent-header">
      <div>
        <h2>🤖 云膳经营助手</h2>
        <p class="sub">数据驱动的经营诊断与智能问答</p>
      </div>
      <el-button type="primary" :loading="diagLoading" @click="loadDiagnose">
        立即诊断
      </el-button>
    </div>

    <!-- 诊断结果 -->
    <div v-if="diag" class="diag-panel">
      <div class="diag-grid">
        <div class="item">
          <b>¥ {{ diag.profile.turnover }}</b>
          <span>今日营业额</span>
          <em :class="deltaClass(diag.profile.turnoverDeltaPct)">
            {{ deltaText(diag.profile.turnoverDeltaPct) }}
          </em>
        </div>
        <div class="item">
          <b>{{ diag.profile.validOrderCount }}</b>
          <span>有效订单</span>
          <em :class="deltaClass(diag.profile.orderDeltaPct)">
            {{ deltaText(diag.profile.orderDeltaPct) }}
          </em>
        </div>
        <div class="item">
          <b>{{ diag.profile.orderCompletionRate }}%</b>
          <span>订单完成率</span>
        </div>
        <div class="item">
          <b>¥ {{ diag.profile.unitPrice }}</b>
          <span>平均客单价</span>
        </div>
        <div class="item">
          <b>{{ diag.profile.newUsers }}</b>
          <span>新增用户</span>
        </div>
        <div class="item">
          <b>{{ diag.profile.waitingOrders }}</b>
          <span>待接单</span>
        </div>
        <div class="item">
          <b>{{ diag.profile.dishesOnSale }}</b>
          <span>在售菜品</span>
        </div>
        <div class="item warn" v-if="diag.profile.cancelledOrders > 0">
          <b>{{ diag.profile.cancelledOrders }}</b>
          <span>已取消订单</span>
        </div>
      </div>

      <div class="diag-cols">
        <div class="diag-col">
          <h4>🔍 诊断发现</h4>
          <ul>
            <li v-for="(f, i) in diag.findings" :key="i">{{ f }}</li>
          </ul>
        </div>
        <div class="diag-col">
          <h4>💡 经营建议</h4>
          <ul class="sug">
            <li v-for="(s, i) in diag.suggestions" :key="i">{{ s }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 智能问答 -->
    <div class="qa-panel">
      <h3>💬 向经营助手提问</h3>
      <div class="chips">
        <span
          v-for="q in quickQuestions"
          :key="q"
          class="chip"
          @click="quickAsk(q)"
        >
          {{ q }}
        </span>
      </div>
      <div class="qa-input">
        <el-input
          v-model="question"
          placeholder="问我营业额、订单、完成率、菜品、环比趋势…"
          @keyup.enter.native="ask"
        />
        <el-button type="primary" :loading="askLoading" @click="ask">
          提问
        </el-button>
      </div>
      <div v-if="answer" class="answer">
        <div class="answer-text">{{ answer }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from "vue-property-decorator";
import { diagnose, ask } from "@/api/agent";

@Component({ name: "BizAgent" })
export default class extends Vue {
  private diag: any = null;
  private diagLoading = false;
  private question = "";
  private answer = "";
  private askLoading = false;

  private quickQuestions = [
    "今日营业额怎么样",
    "订单情况如何",
    "订单完成率健康吗",
    "客单价怎么提升",
    "今日新增用户",
    "菜品在售情况",
    "和昨天对比趋势",
  ];

  private async created() {
    this.loadDiagnose();
  }

  private async loadDiagnose() {
    this.diagLoading = true;
    try {
      const res = await diagnose();
      if (res.data.code === 1) this.diag = res.data.data;
    } finally {
      this.diagLoading = false;
    }
  }

  private async quickAsk(q: string) {
    this.question = q;
    this.ask();
  }

  private async ask() {
    if (!this.question.trim()) return;
    this.askLoading = true;
    try {
      const res = await ask({ question: this.question.trim() });
      if (res.data.code === 1) {
        this.answer = res.data.data.answer;
      } else {
        this.answer = "助手暂时无法回答，请稍后再试。";
      }
    } finally {
      this.askLoading = false;
    }
  }

  private deltaText(pct: number) {
    if (pct === undefined || pct === null) return "";
    if (pct >= 10) return `↑ ${pct}%`;
    if (pct <= -10) return `↓ ${Math.abs(pct)}%`;
    return `持平 ${pct}%`;
  }

  private deltaClass(pct: number) {
    if (pct >= 10) return "up";
    if (pct <= -10) return "down";
    return "flat";
  }
}
</script>

<style lang="scss" scoped>
.agent-page {
  padding: 20px;
}
.agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 {
    font-size: 20px;
    color: #1f2937;
    margin-bottom: 4px;
  }
  .sub {
    font-size: 13px;
    color: #6b7280;
  }
}
.diag-panel {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  margin-bottom: 20px;
}
.diag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 14px;
  margin-bottom: 22px;
  .item {
    background: #f2faf8;
    border: 1px solid #d5eae5;
    border-radius: 10px;
    padding: 16px 12px;
    text-align: center;
    b {
      display: block;
      font-size: 22px;
      color: #0e9384;
    }
    span {
      display: block;
      font-size: 12px;
      color: #6b7280;
      margin-top: 4px;
    }
    em {
      display: block;
      font-style: normal;
      font-size: 12px;
      margin-top: 6px;
      &.up { color: #17a673; }
      &.down { color: #e5484d; }
      &.flat { color: #9ca3af; }
    }
    &.warn b { color: #e5484d; }
  }
}
.diag-cols {
  display: flex;
  gap: 20px;
  .diag-col {
    flex: 1;
    background: #f7f9fa;
    border-radius: 10px;
    padding: 16px 20px;
    h4 {
      font-size: 14px;
      margin-bottom: 10px;
      color: #1f2937;
    }
    ul {
      padding-left: 4px;
      li {
        font-size: 13.5px;
        color: #374151;
        line-height: 1.8;
        margin-bottom: 6px;
        &::before {
          content: "·";
          color: #0e9384;
          font-weight: bold;
          margin-right: 8px;
        }
      }
      &.sug li::before {
        content: "→";
        color: #f0b429;
      }
    }
  }
}
.qa-panel {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  h3 {
    font-size: 16px;
    color: #1f2937;
    margin-bottom: 14px;
  }
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  .chip {
    font-size: 12px;
    padding: 6px 16px;
    border-radius: 16px;
    background: rgba(14, 147, 132, 0.08);
    color: #0e9384;
    cursor: pointer;
    transition: background 0.15s;
    &:hover {
      background: rgba(14, 147, 132, 0.18);
    }
  }
}
.qa-input {
  display: flex;
  gap: 12px;
}
.answer {
  margin-top: 16px;
  .answer-text {
    background: #f7f9fa;
    border-left: 3px solid #0e9384;
    padding: 14px 18px;
    border-radius: 0 8px 8px 0;
    font-size: 14px;
    line-height: 1.8;
    color: #374151;
    white-space: pre-wrap;
  }
}
</style>
