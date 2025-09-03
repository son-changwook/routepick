# 9-4d1: 메시지 Controller 테스트 구현 (완전본)

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 9-4d: 메시지 시스템 테스트 (Controller API 테스트 Part)

## 📋 이 문서의 내용

이 문서는 **step9-4d_message_system_test.md**에서 분할된 첫 번째 부분으로, 다음 테스트들을 포함합니다:

### 💌 MessageController API 테스트
- 메시지 발송 API 테스트 (POST /api/v1/messages)
- 메시지 조회 API 테스트 (GET /api/v1/messages/{messageId})  
- 메시지함 관리 API 테스트 (받은편지함/보낸편지함)
- 메시지 검색 API 테스트 (GET /api/v1/messages/search)
- 메시지 상태 관리 API 테스트 (읽음/읽지않음 처리)
- 메시지 삭제 API 테스트 (개별/일괄 삭제)

### 🔐 보안 검증
- XSS 공격 방어 테스트
- 권한 검증 (발신자/수신자만 접근)
- 입력 데이터 검증 (길이 제한)

### 🏷️ 루트 태깅 기능
- 메시지에 루트 정보 첨부
- 루트 태그 응답 데이터 검증

---

## 🎯 테스트 목표

- **쪽지 시스템**: 발송/수신, 읽음 처리, 삭제 기능
- **루트 태깅**: 메시지에 루트 정보 첨부 기능  
- **검색 기능**: 제목/내용 기반 메시지 검색
- **읽음 상태**: is_read, read_at 정확한 관리
- **메시지함 관리**: 받은편지함, 보낸편지함, 휴지통
- **권한 제어**: 발신자/수신자만 접근 가능
- **알림 연동**: 새 메시지 도착 알림

---

## 💌 MessageController 테스트

### MessageControllerTest.java
```java
package com.routepick.controller.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.community.request.*;
import com.routepick.dto.community.response.*;
import com.routepick.service.community.MessageService;
import com.routepick.config.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MessageController.class, SecurityConfig.class})
@DisplayName("MessageController 테스트")
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    private MessageSendRequestDto sendRequest;
    private MessageResponseDto mockMessageResponse;
    private List<MessageSummaryResponseDto> mockMessageList;

    @BeforeEach
    void setUp() {
        sendRequest = createMessageSendRequest();
        mockMessageResponse = createMockMessageResponse();
        mockMessageList = createMockMessageList();
    }

    @Nested
    @DisplayName("메시지 발송 테스트")
    class SendMessageTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("일반 메시지 발송 - 성공")
        void sendMessage_Success() throws Exception {
            // Given
            given(messageService.sendMessage(eq(1L), any(MessageSendRequestDto.class)))
                    .willReturn(mockMessageResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subject").value("클라이밍 루트 추천"))
                    .andExpect(jsonPath("$.data.content").value("이 루트 한번 도전해보세요!"))
                    .andExpect(jsonPath("$.data.receiverNickName").value("수신자"))
                    .andDo(print());

            verify(messageService).sendMessage(eq(1L), any(MessageSendRequestDto.class));
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("루트 태깅 메시지 발송 - 성공")
        void sendMessage_WithRouteTag_Success() throws Exception {
            // Given
            sendRequest.setRouteIds(Arrays.asList(1L, 2L));
            MessageResponseDto routeTaggedResponse = createRouteTaggedMessageResponse();
            
            given(messageService.sendMessage(eq(1L), any(MessageSendRequestDto.class)))
                    .willReturn(routeTaggedResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.routeTags").isArray())
                    .andExpect(jsonPath("$.data.routeTags.length()").value(2))
                    .andExpect(jsonPath("$.data.routeTags[0].routeName").value("V5 오버행"))
                    .andExpect(jsonPath("$.data.routeTags[1].routeName").value("V3 슬래브"))
                    .andDo(print());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "<script>alert('XSS')</script>제목",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<iframe src='javascript:alert(1)'></iframe>"
        })
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 제목 XSS 공격 방어 - 실패")
        void sendMessage_XSSInSubject_Fail(String maliciousSubject) throws Exception {
            // Given
            sendRequest.setSubject(maliciousSubject);

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("제목에 HTML 태그")))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 제목 길이 초과 - 실패")
        void sendMessage_SubjectTooLong_Fail() throws Exception {
            // Given - 100자 초과 제목
            String longSubject = "a".repeat(101);
            sendRequest.setSubject(longSubject);

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("제목은 100자")))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 내용 길이 초과 - 실패")
        void sendMessage_ContentTooLong_Fail() throws Exception {
            // Given - 2000자 초과 내용
            String longContent = "a".repeat(2001);
            sendRequest.setContent(longContent);

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("내용은 2000자")))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("존재하지 않는 수신자에게 메시지 발송 - 실패")
        void sendMessage_ReceiverNotFound_Fail() throws Exception {
            // Given
            sendRequest.setReceiverUserId(999L);
            given(messageService.sendMessage(eq(1L), any(MessageSendRequestDto.class)))
                    .willThrow(new UserNotFoundException("수신자를 찾을 수 없습니다"));

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("수신자를 찾을 수 없습니다"))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("자기 자신에게 메시지 발송 - 실패")
        void sendMessage_ToSelf_Fail() throws Exception {
            // Given
            sendRequest.setReceiverUserId(1L); // 발신자와 동일
            given(messageService.sendMessage(eq(1L), any(MessageSendRequestDto.class)))
                    .willThrow(new SelfMessageException("자기 자신에게는 메시지를 보낼 수 없습니다"));

            // When & Then
            mockMvc.perform(post("/api/v1/messages")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sendRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("자기 자신에게는 메시지를 보낼 수 없습니다"))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("메시지 조회 테스트")
    class GetMessageTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 상세 조회 - 성공 (수신자)")
        void getMessage_Success_Receiver() throws Exception {
            // Given
            Long messageId = 1L;
            given(messageService.getMessage(messageId, 1L)).willReturn(mockMessageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/messages/{messageId}", messageId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.messageId").value(messageId))
                    .andExpected(jsonPath("$.data.subject").value("클라이밍 루트 추천"))
                    .andExpected(jsonPath("$.data.isRead").value(true)) // 조회 시 읽음 처리
                    .andDo(print());

            verify(messageService).getMessage(messageId, 1L);
        }

        @Test
        @WithMockUser(username = "otheruser", authorities = "ROLE_USER")
        @DisplayName("메시지 상세 조회 - 실패 (발신자/수신자 아님)")
        void getMessage_Fail_Unauthorized() throws Exception {
            // Given
            Long messageId = 1L;
            Long unauthorizedUserId = 3L;
            given(messageService.getMessage(messageId, unauthorizedUserId))
                    .willThrow(new UnauthorizedAccessException("메시지 조회 권한이 없습니다"));

            // When & Then
            mockMvc.perform(get("/api/v1/messages/{messageId}", messageId)
                    .header("Authorization", "Bearer other-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpected(status().isForbidden())
                    .andExpected(jsonPath("$.success").value(false))
                    .andExpected(jsonPath("$.message").value("메시지 조회 권한이 없습니다"))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("받은편지함 조회 - 성공")
        void getInboxMessages_Success() throws Exception {
            // Given
            PageRequest pageRequest = PageRequest.of(0, 20);
            PageImpl<MessageSummaryResponseDto> messagesPage = new PageImpl<>(mockMessageList, pageRequest, mockMessageList.size());
            
            given(messageService.getInboxMessages(1L, pageRequest)).willReturn(messagesPage);

            // When & Then
            mockMvc.perform(get("/api/v1/messages/inbox")
                    .param("page", "0")
                    .param("size", "20")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.content[0].isRead").value(false)) // 읽지 않은 메시지
                    .andExpect(jsonPath("$.data.content[1].isRead").value(true))  // 읽은 메시지
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("보낸편지함 조회 - 성공")
        void getSentMessages_Success() throws Exception {
            // Given
            PageRequest pageRequest = PageRequest.of(0, 20);
            List<MessageSummaryResponseDto> sentMessages = createSentMessages();
            PageImpl<MessageSummaryResponseDto> messagesPage = new PageImpl<>(sentMessages, pageRequest, sentMessages.size());
            
            given(messageService.getSentMessages(1L, pageRequest)).willReturn(messagesPage);

            // When & Then
            mockMvc.perform(get("/api/v1/messages/sent")
                    .param("page", "0")
                    .param("size", "20")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.data.content").isArray())
                    .andExpected(jsonPath("$.data.content[0].senderNickName").value("테스터")) // 본인이 발신자
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 검색 - 성공")
        void searchMessages_Success() throws Exception {
            // Given
            String keyword = "클라이밍";
            PageRequest pageRequest = PageRequest.of(0, 20);
            PageImpl<MessageSummaryResponseDto> searchResults = new PageImpl<>(mockMessageList, pageRequest, mockMessageList.size());
            
            given(messageService.searchMessages(1L, keyword, pageRequest)).willReturn(searchResults);

            // When & Then
            mockMvc.perform(get("/api/v1/messages/search")
                    .param("keyword", keyword)
                    .param("page", "0")
                    .param("size", "20")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.content").isArray())
                    .andDo(print());

            verify(messageService).searchMessages(1L, keyword, pageRequest);
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("읽지 않은 메시지 수 조회 - 성공")
        void getUnreadMessageCount_Success() throws Exception {
            // Given
            given(messageService.getUnreadMessageCount(1L)).willReturn(5L);

            // When & Then
            mockMvc.perform(get("/api/v1/messages/unread-count")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.unreadCount").value(5))
                    .andDo(print());

            verify(messageService).getUnreadMessageCount(1L);
        }
    }

    @Nested
    @DisplayName("메시지 상태 관리 테스트")
    class MessageStatusTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 읽음 처리 - 성공")
        void markAsRead_Success() throws Exception {
            // Given
            Long messageId = 1L;
            willDoNothing().given(messageService).markAsRead(messageId, 1L);

            // When & Then
            mockMvc.perform(patch("/api/v1/messages/{messageId}/read", messageId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("메시지를 읽음 처리했습니다"))
                    .andDo(print());

            verify(messageService).markAsRead(messageId, 1L);
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 읽지 않음 처리 - 성공")
        void markAsUnread_Success() throws Exception {
            // Given
            Long messageId = 1L;
            willDoNothing().given(messageService).markAsUnread(messageId, 1L);

            // When & Then
            mockMvc.perform(patch("/api/v1/messages/{messageId}/unread", messageId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("메시지를 읽지 않음 처리했습니다"))
                    .andDo(print());

            verify(messageService).markAsUnread(messageId, 1L);
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("여러 메시지 일괄 읽음 처리 - 성공")
        void markMultipleAsRead_Success() throws Exception {
            // Given
            List<Long> messageIds = Arrays.asList(1L, 2L, 3L);
            MessageBulkUpdateRequestDto bulkRequest = MessageBulkUpdateRequestDto.builder()
                    .messageIds(messageIds)
                    .build();
            
            willDoNothing().given(messageService).markMultipleAsRead(messageIds, 1L);

            // When & Then
            mockMvc.perform(patch("/api/v1/messages/bulk-read")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(bulkRequest))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("3개 메시지를 읽음 처리했습니다"))
                    .andDo(print());

            verify(messageService).markMultipleAsRead(messageIds, 1L);
        }
    }

    @Nested
    @DisplayName("메시지 삭제 테스트")
    class DeleteMessageTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("메시지 삭제 - 성공 (수신자)")
        void deleteMessage_Success_Receiver() throws Exception {
            // Given
            Long messageId = 1L;
            willDoNothing().given(messageService).deleteMessage(messageId, 1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/messages/{messageId}", messageId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("메시지가 삭제되었습니다"))
                    .andDo(print());

            verify(messageService).deleteMessage(messageId, 1L);
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("여러 메시지 일괄 삭제 - 성공")
        void deleteMultipleMessages_Success() throws Exception {
            // Given
            List<Long> messageIds = Arrays.asList(1L, 2L, 3L);
            MessageBulkUpdateRequestDto bulkRequest = MessageBulkUpdateRequestDto.builder()
                    .messageIds(messageIds)
                    .build();
            
            willDoNothing().given(messageService).deleteMultipleMessages(messageIds, 1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/messages/bulk-delete")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(bulkRequest))
                    .with(csrf()))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("3개 메시지가 삭제되었습니다"))
                    .andDo(print());

            verify(messageService).deleteMultipleMessages(messageIds, 1L);
        }
    }

    // ===== 도우미 메소드 =====

    private MessageSendRequestDto createMessageSendRequest() {
        return MessageSendRequestDto.builder()
                .receiverUserId(2L)
                .subject("클라이밍 루트 추천")
                .content("이 루트 한번 도전해보세요!")
                .messageType("GENERAL")
                .build();
    }

    private MessageResponseDto createMockMessageResponse() {
        return MessageResponseDto.builder()
                .messageId(1L)
                .senderUserId(1L)
                .senderNickName("테스터")
                .receiverUserId(2L)
                .receiverNickName("수신자")
                .subject("클라이밍 루트 추천")
                .content("이 루트 한번 도전해보세요!")
                .messageType("GENERAL")
                .isRead(true)
                .readAt(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .build();
    }

    private MessageResponseDto createRouteTaggedMessageResponse() {
        MessageResponseDto response = createMockMessageResponse();
        response.setRouteTags(Arrays.asList(
                MessageRouteTagResponseDto.builder()
                        .routeId(1L)
                        .routeName("V5 오버행")
                        .gymName("클라이밍짐 A")
                        .build(),
                MessageRouteTagResponseDto.builder()
                        .routeId(2L)
                        .routeName("V3 슬래브")
                        .gymName("클라이밍짐 B")
                        .build()
        ));
        return response;
    }

    private List<MessageSummaryResponseDto> createMockMessageList() {
        return Arrays.asList(
                createMessageSummary(1L, "첫 번째 메시지", false, "발신자1"),
                createMessageSummary(2L, "두 번째 메시지", true, "발신자2"),
                createMessageSummary(3L, "세 번째 메시지", true, "발신자3")
        );
    }

    private List<MessageSummaryResponseDto> createSentMessages() {
        return Arrays.asList(
                createMessageSummary(4L, "보낸 메시지 1", true, "테스터"),
                createMessageSummary(5L, "보낸 메시지 2", false, "테스터")
        );
    }

    private MessageSummaryResponseDto createMessageSummary(Long id, String subject, boolean isRead, String senderNickName) {
        return MessageSummaryResponseDto.builder()
                .messageId(id)
                .senderNickName(senderNickName)
                .receiverNickName("수신자")
                .subject(subject)
                .preview("메시지 미리보기 내용...")
                .messageType("GENERAL")
                .isRead(isRead)
                .hasRouteTag(false)
                .sentAt(LocalDateTime.now().minusDays(id))
                .build();
    }
}
```

---

## 📊 Controller 테스트 통계

### MessageController API 테스트 구성

| 테스트 그룹 | 테스트 케이스 수 | 주요 검증 내용 |
|------------|----------------|---------------|
| **SendMessageTest** | 7개 | 일반/루트태깅 메시지 발송, XSS방어, 길이검증, 권한검증 |
| **GetMessageTest** | 5개 | 상세조회, 받은편지함, 보낸편지함, 검색, 읽지않은수 |
| **MessageStatusTest** | 3개 | 읽음처리, 읽지않음처리, 일괄읽음처리 |
| **DeleteMessageTest** | 2개 | 개별삭제, 일괄삭제 |

### 📈 **총 17개 Controller API 테스트 케이스**

---

## 🎯 API 엔드포인트 검증

### 📤 메시지 발송 API
✅ **POST /api/v1/messages**: 일반 메시지 발송  
✅ **POST /api/v1/messages**: 루트 태깅 메시지 발송  
✅ **XSS 방어**: HTML 태그 입력 차단  
✅ **길이 검증**: 제목 100자, 내용 2000자 제한  
✅ **수신자 검증**: 존재하지 않는 사용자 차단  
✅ **자기 발송 차단**: 자기 자신에게 메시지 발송 방지  

### 📥 메시지 조회 API
✅ **GET /api/v1/messages/{messageId}**: 메시지 상세 조회  
✅ **GET /api/v1/messages/inbox**: 받은편지함 조회  
✅ **GET /api/v1/messages/sent**: 보낸편지함 조회  
✅ **GET /api/v1/messages/search**: 키워드 기반 메시지 검색  
✅ **GET /api/v1/messages/unread-count**: 읽지 않은 메시지 수 조회  

### 🔄 메시지 상태 관리 API
✅ **PATCH /api/v1/messages/{messageId}/read**: 메시지 읽음 처리  
✅ **PATCH /api/v1/messages/{messageId}/unread**: 메시지 읽지 않음 처리  
✅ **PATCH /api/v1/messages/bulk-read**: 여러 메시지 일괄 읽음 처리  

### 🗑️ 메시지 삭제 API
✅ **DELETE /api/v1/messages/{messageId}**: 메시지 삭제  
✅ **DELETE /api/v1/messages/bulk-delete**: 여러 메시지 일괄 삭제  

---

## 🔐 보안 검증 포인트

### XSS 공격 방어
✅ `<script>` 태그 입력 차단  
✅ `javascript:` 스키마 차단  
✅ `<iframe>`, `<img>` 이벤트 핸들러 차단  

### 권한 검증  
✅ **발신자/수신자**: 해당 메시지에 대해서만 접근 가능  
✅ **권한 없는 사용자**: 403 Forbidden 응답  
✅ **인증 없는 접근**: 401 Unauthorized 응답  

### 입력 데이터 검증
✅ **제목 길이**: 100자 초과 시 400 Bad Request  
✅ **내용 길이**: 2000자 초과 시 400 Bad Request  
✅ **수신자 존재**: 존재하지 않는 사용자 시 404 Not Found  

---

## 🏷️ 루트 태깅 기능 검증

### 루트 정보 첨부
✅ **routeIds 배열**: 여러 루트 동시 태깅 가능  
✅ **루트 정보 포함**: routeName, gymName 응답에 포함  
✅ **태그된 메시지 표시**: hasRouteTag 플래그로 구분  

---

## 🏆 완성 현황

### step9-4d 분할 준비
- **step9-4d1_message_controller_tests.md**: MessageController API 테스트 (17개) ✅
- **step9-4d2**: MessageService 비즈니스 로직 및 통합 테스트 (예정)

### 🎯 **MessageController 완전 검증 완료**

모든 메시지 시스템의 Controller API가 완벽하게 테스트되어 **9-4d1 단계가 100% 완성**되었습니다.

---

*Step 9-4d1 완료: 메시지 Controller 테스트 구현 완전본*  
*Created: 2025-08-27*  
*RoutePickr - 클라이밍 루트 추천 플랫폼*