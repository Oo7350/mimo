package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.ChatMessageDTO.*;
import com.mimo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public Result<ChatMessageVO> send(@Valid @RequestBody SendRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.send(request, userId));
    }

    /**
     * 获取团队聊天历史
     */
    @GetMapping("/history/{teamId}")
    public Result<List<ChatMessageVO>> history(@PathVariable Long teamId) {
        return Result.success(chatService.getHistory(teamId));
    }
}
