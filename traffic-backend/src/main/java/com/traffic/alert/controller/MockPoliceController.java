package com.traffic.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Tag(name = "交警系统Mock")
@RestController
@RequestMapping("/api/mock/police")
public class MockPoliceController {

    private static final int MAX_HISTORY = 100;
    private final Deque<Map<String, Object>> receivedRequests = new ConcurrentLinkedDeque<>();

    @Operation(summary = "Mock 交警系统 webhook 接收端(沙箱验证)")
    @PostMapping("/webhook")
    public Map<String, Object> receiveEvent(@RequestBody(required = false) Map<String, Object> body,
                                            @RequestHeader(required = false) Map<String, String> headers,
                                            HttpServletRequest request) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("receivedAt", LocalDateTime.now().toString());
        record.put("method", request.getMethod());
        record.put("headers", headers);
        record.put("body", body);
        record.put("remoteAddr", request.getRemoteAddr());

        receivedRequests.addFirst(record);
        while (receivedRequests.size() > MAX_HISTORY) {
            receivedRequests.removeLast();
        }

        String plateNumber = body != null && body.get("plateNumber") != null
                ? body.get("plateNumber").toString() : "unknown";

        log.info("[交警沙箱] 已接收事件推送: plate={}, eventType={}, eventNo={}",
                plateNumber,
                body != null ? body.get("eventType") : null,
                body != null ? body.get("eventNo") : null);

        if (body != null && "FAIL_AFTER_2".equals(body.get("plateNumber")) && receivedRequests.size() <= 2) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("code", 500);
            fail.put("message", "服务器内部错误(模拟)");
            fail.put("success", false);
            return fail;
        }

        Map<String, Object> success = new LinkedHashMap<>();
        success.put("code", 200);
        success.put("errcode", 0);
        success.put("message", "success");
        success.put("success", true);
        success.put("receiptId", "POLICE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        success.put("receivedAt", LocalDateTime.now().toString());
        success.put("plateNumber", plateNumber);
        return success;
    }

    @Operation(summary = "查询沙箱接收到的最近请求(调试用)")
    @GetMapping("/webhook/history")
    public List<Map<String, Object>> getHistory(@RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Iterator<Map<String, Object>> it = receivedRequests.iterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            result.add(it.next());
            count++;
        }
        return result;
    }

    @Operation(summary = "清空沙箱请求历史")
    @DeleteMapping("/webhook/history")
    public String clearHistory() {
        receivedRequests.clear();
        return "OK";
    }
}
