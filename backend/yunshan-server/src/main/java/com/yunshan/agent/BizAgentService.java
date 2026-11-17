package com.yunshan.agent;

import com.yunshan.vo.BusinessDataVO;
import com.yunshan.vo.OrderOverViewVO;
import com.yunshan.vo.DishOverViewVO;
import com.yunshan.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云膳到家 · 经营助手 Agent
 *
 * 数据驱动的规则智能体（不依赖外部 LLM）：
 * 1. 经营诊断：聚合今日营业数据 + 订单状态 + 菜品在售，输出画像 / 发现 / 建议
 * 2. 环比分析：今日 vs 昨日营业额与订单量，自动判断趋势
 * 3. 菜品洞察：销量 TOP / 长期零销菜品识别
 * 4. 关键词问答：营业额、订单、菜品、完成率等主题的智能应答
 */
@Service
@Slf4j
public class BizAgentService {

    private final WorkspaceService workspaceService;
    private final JdbcTemplate jdbc;

    /** 问答主题关键词表 */
    private static final Map<String, List<String>> TOPICS = Map.of(
            "营业额", List.of("营业额", "收入", "流水", "turnover"),
            "订单", List.of("订单", "单量", "valid"),
            "完成率", List.of("完成率", "取消", "completion"),
            "客单价", List.of("客单价", "均价", "unitPrice"),
            "用户", List.of("用户", "新增", "会员", "newUsers"),
            "菜品", List.of("菜品", "在售", "停售", "dish"),
            "趋势", List.of("趋势", "环比", "昨天", "对比", "增长")
    );

    public BizAgentService(WorkspaceService workspaceService, JdbcTemplate jdbc) {
        this.workspaceService = workspaceService;
        this.jdbc = jdbc;
    }

    /**
     * 经营诊断：汇总核心指标并生成发现与建议
     */
    public Map<String, Object> diagnose() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        // 今日核心指标
        BusinessDataVO todayData = workspaceService.getBusinessData(dayStart, dayEnd);
        OrderOverViewVO orderView = workspaceService.getOrderOverView();
        DishOverViewVO dishView = workspaceService.getDishOverView();

        // 昨日数据（用于环比）
        BusinessDataVO yesterdayData = workspaceService.getBusinessData(
                dayStart.minusDays(1), dayStart);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("date", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        profile.put("turnover", round(todayData.getTurnover()));
        profile.put("validOrderCount", nz(todayData.getValidOrderCount()));
        profile.put("orderCompletionRate", pct(todayData.getOrderCompletionRate()));
        profile.put("unitPrice", round(todayData.getUnitPrice()));
        profile.put("newUsers", nz(todayData.getNewUsers()));
        profile.put("waitingOrders", nz(orderView.getWaitingOrders()));
        profile.put("deliveredOrders", nz(orderView.getDeliveredOrders()));
        profile.put("completedOrders", nz(orderView.getCompletedOrders()));
        profile.put("cancelledOrders", nz(orderView.getCancelledOrders()));
        profile.put("dishesOnSale", nz(dishView.getSold()));
        profile.put("dishesOffSale", nz(dishView.getDiscontinued()));

        // 环比
        double turnoverDelta = 0;
        int orderDelta = 0;
        if (yesterdayData.getTurnover() > 0) {
            turnoverDelta = (todayData.getTurnover() - yesterdayData.getTurnover())
                    / yesterdayData.getTurnover() * 100;
        }
        if (yesterdayData.getValidOrderCount() != null && yesterdayData.getValidOrderCount() > 0) {
            orderDelta = (int) Math.round(
                    (nz(todayData.getValidOrderCount()) - yesterdayData.getValidOrderCount())
                            * 100.0 / yesterdayData.getValidOrderCount());
        }
        profile.put("turnoverDeltaPct", round(turnoverDelta));
        profile.put("orderDeltaPct", orderDelta);

        // 发现与建议
        List<String> findings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        int validOrders = nz(todayData.getValidOrderCount());
        double turnover = todayData.getTurnover() == null ? 0 : todayData.getTurnover();
        double completion = todayData.getOrderCompletionRate() == null ? 0 : todayData.getOrderCompletionRate();

        if (validOrders == 0) {
            findings.add("今日还没有有效订单，可能是营业初期或店铺处于打烊状态");
            suggestions.add("检查店铺营业状态是否为「营业中」，并确认菜品至少有 3 道在售");
        } else {
            findings.add("今日营业额 " + round(turnover) + " 元，有效订单 " + validOrders + " 单");
            if (yesterdayData.getTurnover() > 0) {
                if (turnoverDelta >= 10) {
                    findings.add("营业额比昨日提升 " + round(turnoverDelta) + "%，增长势头良好");
                    suggestions.add("趁热度推出限时组合套餐，进一步拉高客单价");
                } else if (turnoverDelta <= -10) {
                    findings.add("营业额比昨日下降 " + round(Math.abs(turnoverDelta)) + "%，需要关注");
                    suggestions.add("检查热销菜品是否停售，或考虑发放满减券拉动下单");
                } else {
                    findings.add("营业额与昨日基本持平（" + round(turnoverDelta) + "%）");
                }
            }
        }

        int waiting = nz(orderView.getWaitingOrders());
        if (waiting > 0) {
            findings.add("有 " + waiting + " 笔订单等待接单");
            suggestions.add("尽快接单处理，超时未接单会影响店铺评分");
        }
        int cancelled = nz(orderView.getCancelledOrders());
        if (cancelled > 0 && validOrders > 0 && cancelled * 100.0 / (validOrders + cancelled) > 20) {
            findings.add("取消订单占比偏高（" + cancelled + " 笔）");
            suggestions.add("排查取消原因：出餐慢、缺货或配送问题，优先解决高频取消因素");
        }
        if (completion > 0 && completion < 0.8) {
            findings.add("订单完成率 " + pct(completion) + "，低于 80% 健康线");
            suggestions.add("关注出餐速度与骑手交接效率，完成率是平台流量的重要权重");
        }
        int offSale = nz(dishView.getDiscontinued());
        if (offSale > 0) {
            findings.add("有 " + offSale + " 个菜品处于停售状态");
            suggestions.add("确认停售是否为原料短缺导致，及时补货恢复在售");
        }
        int onSale = nz(dishView.getSold());
        if (onSale < 5) {
            findings.add("在售菜品仅 " + onSale + " 个，菜单偏单薄");
            suggestions.add("丰富菜单至 10 个以上菜品，可提升下单转化率");
        }
        int newUsers = nz(todayData.getNewUsers());
        if (newUsers == 0 && validOrders > 0) {
            findings.add("今日有订单但没有新增用户");
            suggestions.add("考虑设置「新人无门槛券」吸引新客，配合下单返积分");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("findings", findings);
        result.put("suggestions", suggestions);
        return result;
    }

    /**
     * 关键词问答
     */
    public Map<String, Object> ask(String question) {
        String q = question == null ? "" : question.toLowerCase();
        List<String> keywords = null;
        String topic = null;
        for (Map.Entry<String, List<String>> e : TOPICS.entrySet()) {
            for (String kw : e.getValue()) {
                if (q.contains(kw.toLowerCase())) {
                    keywords = e.getValue();
                    topic = e.getKey();
                    break;
                }
            }
            if (keywords != null) break;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (keywords == null) {
            result.put("answer", "我是云膳经营助手 🤖 试试问我这些主题：" +
                    "营业额、订单、完成率、客单价、新增用户、菜品、环比趋势。也可以直接让我「做一次经营诊断」。");
            result.put("data", Map.of());
            return result;
        }

        LocalDate today = LocalDate.now();
        BusinessDataVO todayData = workspaceService.getBusinessData(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BusinessDataVO yesterday = workspaceService.getBusinessData(
                today.minusDays(1).atStartOfDay(), today.atStartOfDay());
        DishOverViewVO dishView = workspaceService.getDishOverView();

        String answer;
        Map<String, Object> data = new LinkedHashMap<>();
        switch (topic) {
            case "营业额":
                answer = String.format("今日营业额 %.2f 元，昨日 %.2f 元，环比 %s。今日有效订单 %d 单，平均客单价 %.2f 元。",
                        nz(todayData.getTurnover()), nz(yesterday.getTurnover()),
                        deltaText(todayData.getTurnover(), yesterday.getTurnover()),
                        nz(todayData.getValidOrderCount()), nz(todayData.getUnitPrice()));
                data.put("today", round(todayData.getTurnover()));
                data.put("yesterday", round(yesterday.getTurnover()));
                break;
            case "订单":
                answer = String.format("今日有效订单 %d 单，环比 %s；当前待接单 %d、待配送 %d、已完成 %d、已取消 %d。",
                        nz(todayData.getValidOrderCount()),
                        deltaText(todayData.getValidOrderCount() == null ? 0 : todayData.getValidOrderCount(),
                                yesterday.getValidOrderCount() == null ? 0 : yesterday.getValidOrderCount()),
                        nz(workspaceService.getOrderOverView().getWaitingOrders()),
                        nz(workspaceService.getOrderOverView().getDeliveredOrders()),
                        nz(workspaceService.getOrderOverView().getCompletedOrders()),
                        nz(workspaceService.getOrderOverView().getCancelledOrders()));
                break;
            case "完成率":
                answer = String.format("今日订单完成率 %s。%s", pct(todayData.getOrderCompletionRate()),
                        (todayData.getOrderCompletionRate() == null || todayData.getOrderCompletionRate() < 0.8)
                                ? "低于 80% 健康线，建议优先排查出餐速度与配送交接。"
                                : "处于健康区间。");
                data.put("completionRate", pct(todayData.getOrderCompletionRate()));
                break;
            case "客单价":
                answer = String.format("今日平均客单价 %.2f 元。提升客单价的常用手段：设置满减阶梯（满30减5 / 满50减12）、搭配套餐、加价购小食。",
                        nz(todayData.getUnitPrice()));
                data.put("unitPrice", round(todayData.getUnitPrice()));
                break;
            case "用户":
                answer = String.format("今日新增用户 %d 人。%s", nz(todayData.getNewUsers()),
                        nz(todayData.getNewUsers()) == 0
                                ? "拉新建议：发放新人专享无门槛券，并在门店招牌位做首单立减活动。"
                                : "保持当前拉新节奏即可。");
                data.put("newUsers", nz(todayData.getNewUsers()));
                break;
            case "菜品":
                answer = String.format("当前在售菜品 %d 个，停售 %d 个。%s",
                        nz(dishView.getSold()), nz(dishView.getDiscontinued()),
                        nz(dishView.getDiscontinued()) > 0
                                ? "存在停售菜品，若为原料短缺建议尽快补货恢复。"
                                : "菜单结构健康。");
                data.put("onSale", nz(dishView.getSold()));
                data.put("offSale", nz(dishView.getDiscontinued()));
                break;
            case "趋势":
                answer = String.format("营业额环比：%s（今日 %.2f / 昨日 %.2f）。订单量环比：%s。",
                        deltaText(todayData.getTurnover(), yesterday.getTurnover()),
                        nz(todayData.getTurnover()), nz(yesterday.getTurnover()),
                        deltaText(todayData.getValidOrderCount() == null ? 0 : todayData.getValidOrderCount(),
                                yesterday.getValidOrderCount() == null ? 0 : yesterday.getValidOrderCount()));
                data.put("turnoverDeltaPct", todayData.getTurnover() > 0 && yesterday.getTurnover() > 0
                        ? round((todayData.getTurnover() - yesterday.getTurnover()) / yesterday.getTurnover() * 100)
                        : 0);
                break;
            default:
                answer = "暂不支持该主题，试试：营业额 / 订单 / 完成率 / 客单价 / 用户 / 菜品 / 趋势。";
        }
        result.put("answer", answer);
        result.put("data", data);
        return result;
    }

    /* ---------- 工具方法 ---------- */

    private String deltaText(Number today, Number yesterday) {
        double t = today == null ? 0 : today.doubleValue();
        double y = yesterday == null ? 0 : yesterday.doubleValue();
        if (y <= 0) return t > 0 ? "无昨日数据（今日 " + round(t) + "）" : "无数据";
        double delta = (t - y) / y * 100;
        if (delta >= 10) return "↑ 上升 " + round(delta) + "%";
        if (delta <= -10) return "↓ 下降 " + round(Math.abs(delta)) + "%";
        return "基本持平（" + round(delta) + "%）";
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private double nz(Double v) {
        return v == null ? 0 : v;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String pct(Double v) {
        return v == null ? "0%" : Math.round(v * 100) + "%";
    }
}