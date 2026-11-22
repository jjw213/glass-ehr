package com.glassehr.message;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.message.MessageDtos.MessagePage;
import com.glassehr.message.MessageDtos.MessageView;
import com.glassehr.message.MessageDtos.SendRequest;
import com.glassehr.message.MessageDtos.ThreadSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사내 메시지 API(#80). 로그인한 사용자면 누구나 부를 수 있고, 대화방 참여 여부는 서비스가 검사한다(아니면 403).
 */
@RestController
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/api/threads")
    @RequiresPermission
    public List<ThreadSummary> threads(AuthUser user) {
        return service.threads(user);
    }

    @GetMapping("/api/threads/{threadId}/messages")
    @RequiresPermission
    public MessagePage messages(AuthUser user, @PathVariable long threadId,
            @RequestParam(required = false) Long beforeId, @RequestParam(required = false) Integer size) {
        return service.messages(user, threadId, beforeId, size);
    }

    @PostMapping("/api/threads/{threadId}/messages")
    @RequiresPermission
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(AuthUser user, @PathVariable long threadId, @RequestBody SendRequest req) {
        return service.send(user, threadId, req.text());
    }

    @PostMapping("/api/threads/{threadId}/read")
    @RequiresPermission
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(AuthUser user, @PathVariable long threadId) {
        service.markRead(user, threadId);
    }
}
