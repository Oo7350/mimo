package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ChatMessageDTO.*;
import com.mimo.service.ChatService;
import com.mimo.service.TeamService;
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
    private final TeamService teamService;

    @PostMapping("/send")
    public Result<ChatMessageVO> send(@Valid @RequestBody SendRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        // 校验是团队成员才能发消息
        if (!teamService.isTeamMember(request.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非团队成员无法发送消息");
        }
        return Result.success(chatService.send(request, userId));
    }

    @PutMapping("/recall/{messageId}")
    public Result<ChatMessageVO> recall(@PathVariable Long messageId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(chatService.recallMessage(messageId, userId));
    }

    @GetMapping("/history/{teamId}")
    public Result<List<ChatMessageVO>> history(@PathVariable Long teamId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        // 校验是团队成员才能查看聊天记录
        if (!teamService.isTeamMember(teamId, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非团队成员无法查看聊天记录");
        }
        return Result.success(chatService.getHistory(teamId));
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
