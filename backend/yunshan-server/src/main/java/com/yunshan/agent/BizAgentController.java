package com.yunshan.agent;

import com.yunshan.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 云膳经营助手 Agent 接口
 */
@RestController
@RequestMapping("/admin/agent")
public class BizAgentController {

    private final BizAgentService bizAgentService;

    public BizAgentController(BizAgentService bizAgentService) {
        this.bizAgentService = bizAgentService;
    }

    /** 经营诊断 */
    @GetMapping("/diagnose")
    public Result<Map<String, Object>> diagnose() {
        return Result.success(bizAgentService.diagnose());
    }

    /** 关键词问答 */
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        return Result.success(bizAgentService.ask(body.get("question")));
    }
}