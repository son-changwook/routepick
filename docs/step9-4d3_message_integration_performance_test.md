# 메시지 시스템 통합 성능 테스트

## 개요
메시지 시스템의 통합 기능과 성능을 검증하는 테스트입니다. 개인 메시지, 루트 태그 메시지, 알림 연동, 대량 발송 성능을 종합적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.message.integration;

import com.routepick.message.service.MessageService;
import com.routepick.notification.service.NotificationService;
import com.routepick.user.service.UserService;
import com.routepick.community.service.PostService;
import com.routepick.common.service.CacheService;
import com.routepick.message.dto.request.MessageCreateRequestDto;
import com.routepick.message.dto.request.MessageBulkSendRequestDto;
import com.routepick.message.dto.response.MessageResponseDto;
import com.routepick.message.dto.response.MessageStatsResponseDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * 메시지 시스템 통합 성능 테스트
 * 
 * 성능 검증 영역:
 * - 개인 메시지 대량 처리 성능 (10,000건)
 * - 루트 태그 메시지 성능
 * - 알림 연동 성능
 * - 메시지 검색 성능
 * - 동시 접속자 메시지 처리
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MessageIntegrationPerformanceTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_message_perf_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MessageService messageService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PostService postService;
    
    @Autowired
    private CacheService cacheService;
    
    private List<Long> testUserIds;
    private List<Long> testRouteIds;
    
    @BeforeEach
    void setUp() {
        // 성능 테스트용 대량 데이터 준비
        testUserIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        testRouteIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        
        // 캐시 초기화
        cacheService.clearAll();
    }
    
    @Nested
    @DisplayName("개인 메시지 성능 테스트")
    class PersonalMessagePerformanceTest {
        
        @Test
        @Timeout(30)
        @DisplayName("[성능] 개인 메시지 1만건 생성 성능")
        void createPersonalMessages_10000_Performance() throws Exception {
            // given
            int messageCount = 10000;
            long startTime = System.currentTimeMillis();
            
            // when - 1만건의 개인 메시지 생성
            ExecutorService executor = Executors.newFixedThreadPool(20);
            List<CompletableFuture<MessageResponseDto>> futures = new ArrayList<>();
            
            for (int i = 0; i < messageCount; i++) {
                final int messageNum = i;
                Long senderId = testUserIds.get(messageNum % testUserIds.size());
                Long receiverId = testUserIds.get((messageNum + 1) % testUserIds.size());
                
                CompletableFuture<MessageResponseDto> future = CompletableFuture.supplyAsync(() -> {
                    MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                            .senderId(senderId)
                            .receiverId(receiverId)
                            .messageType("PERSONAL")
                            .title("성능 테스트 메시지 #" + messageNum)
                            .content("이것은 성능 테스트를 위한 " + messageNum + "번째 메시지입니다.")
                            .isRead(false)
                            .build();
                    
                    try {
                        return messageService.sendMessage(request);
                    } catch (Exception e) {
                        throw new RuntimeException("메시지 발송 실패: " + e.getMessage(), e);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 메시지 생성 완료 대기
            List<MessageResponseDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList());
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 성능 검증
            assertThat(results).hasSize(messageCount);
            assertThat(executionTime).isLessThan(25000); // 25초 이내
            
            double messagesPerSecond = (double) messageCount / (executionTime / 1000.0);
            assertThat(messagesPerSecond).isGreaterThan(400); // 초당 400건 이상
            
            System.out.printf("✅ 개인 메시지 %d건 생성 완료%n", messageCount);
            System.out.printf("⏱️ 실행 시간: %d ms (%.2f 메시지/초)%n", 
                    executionTime, messagesPerSecond);
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        
        @Test
        @Timeout(15)
        @DisplayName("[성능] 메시지 목록 조회 성능 (페이징)")
        void getMessageList_Paging_Performance() {
            // given - 사전에 1000개 메시지 생성
            Long userId = testUserIds.get(0);
            int totalMessages = 1000;
            
            for (int i = 0; i < totalMessages; i++) {
                MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                        .senderId(testUserIds.get(1))
                        .receiverId(userId)
                        .messageType("PERSONAL")
                        .title("페이징 테스트 메시지 #" + i)
                        .content("페이징 성능 테스트용 메시지입니다.")
                        .build();
                messageService.sendMessage(request);
            }
            
            // when - 페이징된 메시지 목록 조회 성능 측정
            long startTime = System.currentTimeMillis();
            
            List<MessageResponseDto> allMessages = new ArrayList<>();
            int pageSize = 50;
            int currentPage = 0;
            
            while (true) {
                List<MessageResponseDto> pageMessages = messageService
                        .getUserMessages(userId, currentPage, pageSize);
                
                if (pageMessages.isEmpty()) {
                    break;
                }
                
                allMessages.addAll(pageMessages);
                currentPage++;
                
                // 무한 루프 방지
                if (currentPage > 25) { // 최대 25페이지
                    break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 성능 검증
            assertThat(allMessages).hasSizeGreaterThanOrEqualTo(totalMessages);
            assertThat(executionTime).isLessThan(5000); // 5초 이내
            
            double pagesPerSecond = (double) currentPage / (executionTime / 1000.0);
            
            System.out.printf("✅ 메시지 목록 %d페이지 조회 완료%n", currentPage);
            System.out.printf("⏱️ 실행 시간: %d ms (%.2f 페이지/초)%n", 
                    executionTime, pagesPerSecond);
        }
    }
    
    @Nested
    @DisplayName("루트 태그 메시지 성능 테스트")
    class RouteTagMessagePerformanceTest {
        
        @Test
        @Timeout(20)
        @DisplayName("[성능] 루트 태그 메시지 대량 발송")
        void sendRouteTagMessages_Bulk_Performance() throws Exception {
            // given
            int routeTagMessageCount = 5000;
            long startTime = System.currentTimeMillis();
            
            // when - 5000건의 루트 태그 메시지 발송
            ExecutorService executor = Executors.newFixedThreadPool(15);
            List<CompletableFuture<MessageResponseDto>> futures = new ArrayList<>();
            
            for (int i = 0; i < routeTagMessageCount; i++) {
                final int messageNum = i;
                Long senderId = testUserIds.get(messageNum % testUserIds.size());
                Long receiverId = testUserIds.get((messageNum + 1) % testUserIds.size());
                Long routeId = testRouteIds.get(messageNum % testRouteIds.size());
                
                CompletableFuture<MessageResponseDto> future = CompletableFuture.supplyAsync(() -> {
                    MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                            .senderId(senderId)
                            .receiverId(receiverId)
                            .messageType("ROUTE_TAG")
                            .title("루트 태그: 추천 루트 #" + messageNum)
                            .content("이 루트 한번 도전해보세요! 정말 재미있어요.")
                            .routeId(routeId)
                            .isRead(false)
                            .build();
                    
                    try {
                        return messageService.sendMessage(request);
                    } catch (Exception e) {
                        throw new RuntimeException("루트 태그 메시지 발송 실패: " + e.getMessage(), e);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 메시지 발송 완료 대기
            List<MessageResponseDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList());
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 성능 검증
            assertThat(results).hasSize(routeTagMessageCount);
            assertThat(executionTime).isLessThan(18000); // 18초 이내
            
            double messagesPerSecond = (double) routeTagMessageCount / (executionTime / 1000.0);
            assertThat(messagesPerSecond).isGreaterThan(250); // 초당 250건 이상
            
            // 루트 연결 정보 검증
            long routeTaggedMessages = results.stream()
                    .filter(msg -> msg.getRouteId() != null)
                    .count();
            assertThat(routeTaggedMessages).isEqualTo(routeTagMessageCount);
            
            System.out.printf("✅ 루트 태그 메시지 %d건 발송 완료%n", routeTagMessageCount);
            System.out.printf("⏱️ 실행 시간: %d ms (%.2f 메시지/초)%n", 
                    executionTime, messagesPerSecond);
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        
        @Test
        @Timeout(10)
        @DisplayName("[성능] 루트별 태그 메시지 조회 성능")
        void getRouteTagMessages_ByRoute_Performance() {
            // given - 루트별로 태그 메시지 생성
            Long targetRouteId = testRouteIds.get(0);
            int messagesPerRoute = 500;
            
            for (int i = 0; i < messagesPerRoute; i++) {
                MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                        .senderId(testUserIds.get(i % testUserIds.size()))
                        .receiverId(testUserIds.get((i + 1) % testUserIds.size()))
                        .messageType("ROUTE_TAG")
                        .title("루트 태그 메시지 #" + i)
                        .content("루트 추천 메시지입니다.")
                        .routeId(targetRouteId)
                        .build();
                messageService.sendMessage(request);
            }
            
            // when - 루트별 태그 메시지 조회 성능 측정
            long startTime = System.currentTimeMillis();
            
            List<MessageResponseDto> routeMessages = messageService
                    .getMessagesByRoute(targetRouteId, 0, messagesPerRoute);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 성능 검증
            assertThat(routeMessages).hasSizeGreaterThanOrEqualTo(messagesPerRoute);
            assertThat(executionTime).isLessThan(2000); // 2초 이내
            
            // 루트 정보 정확성 검증
            routeMessages.forEach(msg -> 
                    assertThat(msg.getRouteId()).isEqualTo(targetRouteId));
            
            System.out.printf("✅ 루트 태그 메시지 %d건 조회 완료%n", routeMessages.size());
            System.out.printf("⏱️ 실행 시간: %d ms%n", executionTime);
        }
    }
    
    @Nested
    @DisplayName("메시지-알림 통합 성능")
    class MessageNotificationIntegrationTest {
        
        @Test
        @Timeout(25)
        @DisplayName("[성능] 메시지 발송 + 알림 연동 성능")
        void sendMessage_WithNotification_Performance() throws Exception {
            // given
            int messageWithNotificationCount = 3000;
            long startTime = System.currentTimeMillis();
            
            // when - 메시지 발송과 동시에 알림 발송
            ExecutorService executor = Executors.newFixedThreadPool(12);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < messageWithNotificationCount; i++) {
                final int messageNum = i;
                Long senderId = testUserIds.get(messageNum % testUserIds.size());
                Long receiverId = testUserIds.get((messageNum + 1) % testUserIds.size());
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // 1. 메시지 발송
                        MessageCreateRequestDto messageRequest = MessageCreateRequestDto.builder()
                                .senderId(senderId)
                                .receiverId(receiverId)
                                .messageType("PERSONAL")
                                .title("알림 연동 테스트 #" + messageNum)
                                .content("메시지 알림 통합 성능 테스트입니다.")
                                .build();
                        
                        MessageResponseDto message = messageService.sendMessage(messageRequest);
                        
                        // 2. 메시지 발송 후 자동 알림이 생성되는지 검증
                        // (실제 구현에서는 MessageService 내부에서 자동 처리)
                        assertThat(message).isNotNull();
                        assertThat(message.getMessageId()).isNotNull();
                        
                    } catch (Exception e) {
                        throw new RuntimeException("메시지-알림 연동 실패: " + e.getMessage(), e);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 작업 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(20, TimeUnit.SECONDS);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 통합 성능 검증
            assertThat(executionTime).isLessThan(22000); // 22초 이내
            
            double integrationsPerSecond = (double) messageWithNotificationCount / (executionTime / 1000.0);
            assertThat(integrationsPerSecond).isGreaterThan(120); // 초당 120건 이상
            
            // 알림 발송 확인 (샘플링)
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Long sampleUserId = testUserIds.get(1);
                List<?> notifications = notificationService.getUserNotifications(sampleUserId, 0, 100);
                assertThat(notifications).isNotEmpty(); // 알림이 발송되었음
            });
            
            System.out.printf("✅ 메시지-알림 통합 %d건 완료%n", messageWithNotificationCount);
            System.out.printf("⏱️ 실행 시간: %d ms (%.2f 통합/초)%n", 
                    executionTime, integrationsPerSecond);
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    @Nested
    @DisplayName("메시지 검색 성능")
    class MessageSearchPerformanceTest {
        
        @Test
        @Timeout(15)
        @DisplayName("[성능] 메시지 내용 검색 성능")
        void searchMessages_ByContent_Performance() {
            // given - 검색용 메시지 데이터 준비
            String[] keywords = {"클라이밍", "루트", "추천", "도전", "성공", "실패", "기술", "팁"};
            int messagesPerKeyword = 200;
            
            for (String keyword : keywords) {
                for (int i = 0; i < messagesPerKeyword; i++) {
                    MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                            .senderId(testUserIds.get(i % testUserIds.size()))
                            .receiverId(testUserIds.get((i + 1) % testUserIds.size()))
                            .messageType("PERSONAL")
                            .title(keyword + " 관련 메시지 #" + i)
                            .content("이것은 " + keyword + "에 대한 내용을 포함한 메시지입니다. " +
                                    "추가 내용으로 검색 성능을 테스트합니다.")
                            .build();
                    messageService.sendMessage(request);
                }
            }
            
            // when - 키워드별 검색 성능 측정
            long totalStartTime = System.currentTimeMillis();
            
            for (String keyword : keywords) {
                long startTime = System.currentTimeMillis();
                
                List<MessageResponseDto> searchResults = messageService
                        .searchMessages(testUserIds.get(0), keyword, 0, 100);
                
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                // then - 개별 검색 성능 검증
                assertThat(executionTime).isLessThan(1500); // 1.5초 이내
                assertThat(searchResults).isNotEmpty();
                
                // 검색 결과 정확성 검증
                searchResults.forEach(msg -> {
                    boolean containsKeyword = msg.getTitle().contains(keyword) || 
                                            msg.getContent().contains(keyword);
                    assertThat(containsKeyword).isTrue();
                });
                
                System.out.printf("🔍 '%s' 검색: %d건 결과, %d ms%n", 
                        keyword, searchResults.size(), executionTime);
            }
            
            long totalEndTime = System.currentTimeMillis();
            long totalExecutionTime = totalEndTime - totalStartTime;
            
            assertThat(totalExecutionTime).isLessThan(12000); // 전체 12초 이내
            
            System.out.printf("✅ 전체 검색 테스트 완료: %d ms%n", totalExecutionTime);
        }
    }
    
    @Nested
    @DisplayName("동시 접속자 메시지 성능")
    class ConcurrentMessagePerformanceTest {
        
        @Test
        @Timeout(30)
        @DisplayName("[성능] 100명 동시 접속자 메시지 처리")
        void concurrentUsers_MessageHandling_Performance() throws Exception {
            // given
            int concurrentUsers = 100;
            int messagesPerUser = 20;
            long startTime = System.currentTimeMillis();
            
            // when - 100명이 동시에 각각 20개씩 메시지 발송
            ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
            List<CompletableFuture<List<MessageResponseDto>>> userFutures = new ArrayList<>();
            
            for (int userId = 0; userId < concurrentUsers; userId++) {
                final Long senderId = (long) (userId % testUserIds.size() + 1);
                final int userNumber = userId;
                
                CompletableFuture<List<MessageResponseDto>> userFuture = CompletableFuture.supplyAsync(() -> {
                    List<MessageResponseDto> userMessages = new ArrayList<>();
                    
                    for (int msgNum = 0; msgNum < messagesPerUser; msgNum++) {
                        Long receiverId = testUserIds.get((userNumber + msgNum + 1) % testUserIds.size());
                        
                        MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                                .senderId(senderId)
                                .receiverId(receiverId)
                                .messageType("PERSONAL")
                                .title("동시접속 테스트 - 사용자" + userNumber + " 메시지" + msgNum)
                                .content("동시 접속자 성능 테스트용 메시지입니다.")
                                .build();
                        
                        try {
                            MessageResponseDto message = messageService.sendMessage(request);
                            userMessages.add(message);
                        } catch (Exception e) {
                            throw new RuntimeException("동시 메시지 발송 실패: " + e.getMessage(), e);
                        }
                    }
                    
                    return userMessages;
                }, executor);
                
                userFutures.add(userFuture);
            }
            
            // 모든 사용자의 메시지 발송 완료 대기
            List<List<MessageResponseDto>> allUserMessages = userFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList());
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // then - 동시 접속 성능 검증
            int totalMessages = concurrentUsers * messagesPerUser;
            int actualTotalMessages = allUserMessages.stream()
                    .mapToInt(List::size)
                    .sum();
            
            assertThat(actualTotalMessages).isEqualTo(totalMessages);
            assertThat(executionTime).isLessThan(25000); // 25초 이내
            
            double messagesPerSecond = (double) totalMessages / (executionTime / 1000.0);
            assertThat(messagesPerSecond).isGreaterThan(80); // 초당 80건 이상
            
            // 데이터 무결성 검증
            Set<Long> allMessageIds = allUserMessages.stream()
                    .flatMap(List::stream)
                    .map(MessageResponseDto::getMessageId)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(allMessageIds).hasSize(totalMessages); // 중복 없음
            
            System.out.printf("✅ 동시 접속 성능 테스트 완료%n");
            System.out.printf("👥 동시 사용자: %d명%n", concurrentUsers);
            System.out.printf("📨 총 메시지: %d건%n", totalMessages);
            System.out.printf("⏱️ 실행 시간: %d ms (%.2f 메시지/초)%n", 
                    executionTime, messagesPerSecond);
            
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
    
    @Test
    @Timeout(45)
    @DisplayName("[종합] 메시지 시스템 전체 성능 벤치마크")
    void comprehensive_MessageSystemPerformanceBenchmark() throws Exception {
        System.out.println("=== 메시지 시스템 전체 성능 벤치마크 시작 ===");
        
        long totalStartTime = System.currentTimeMillis();
        Map<String, Long> benchmarkResults = new HashMap<>();
        
        // 1. 개인 메시지 성능
        System.out.println("📝 1. 개인 메시지 성능 테스트");
        long personalMessageStart = System.currentTimeMillis();
        
        ExecutorService personalExecutor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<MessageResponseDto>> personalFutures = new ArrayList<>();
        
        for (int i = 0; i < 2000; i++) {
            final int msgNum = i;
            CompletableFuture<MessageResponseDto> future = CompletableFuture.supplyAsync(() -> {
                MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                        .senderId(testUserIds.get(msgNum % testUserIds.size()))
                        .receiverId(testUserIds.get((msgNum + 1) % testUserIds.size()))
                        .messageType("PERSONAL")
                        .title("벤치마크 개인 메시지 #" + msgNum)
                        .content("성능 벤치마크용 개인 메시지입니다.")
                        .build();
                return messageService.sendMessage(request);
            }, personalExecutor);
            personalFutures.add(future);
        }
        
        CompletableFuture.allOf(personalFutures.toArray(new CompletableFuture[0])).get();
        long personalMessageTime = System.currentTimeMillis() - personalMessageStart;
        benchmarkResults.put("개인메시지_2000건", personalMessageTime);
        personalExecutor.shutdown();
        
        // 2. 루트 태그 메시지 성능
        System.out.println("🏃 2. 루트 태그 메시지 성능 테스트");
        long routeTagStart = System.currentTimeMillis();
        
        ExecutorService routeExecutor = Executors.newFixedThreadPool(8);
        List<CompletableFuture<MessageResponseDto>> routeFutures = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            final int msgNum = i;
            CompletableFuture<MessageResponseDto> future = CompletableFuture.supplyAsync(() -> {
                MessageCreateRequestDto request = MessageCreateRequestDto.builder()
                        .senderId(testUserIds.get(msgNum % testUserIds.size()))
                        .receiverId(testUserIds.get((msgNum + 1) % testUserIds.size()))
                        .messageType("ROUTE_TAG")
                        .title("벤치마크 루트 태그 #" + msgNum)
                        .content("성능 벤치마크용 루트 태그 메시지입니다.")
                        .routeId(testRouteIds.get(msgNum % testRouteIds.size()))
                        .build();
                return messageService.sendMessage(request);
            }, routeExecutor);
            routeFutures.add(future);
        }
        
        CompletableFuture.allOf(routeFutures.toArray(new CompletableFuture[0])).get();
        long routeTagTime = System.currentTimeMillis() - routeTagStart;
        benchmarkResults.put("루트태그_1000건", routeTagTime);
        routeExecutor.shutdown();
        
        // 3. 메시지 조회 성능
        System.out.println("🔍 3. 메시지 조회 성능 테스트");
        long queryStart = System.currentTimeMillis();
        
        for (Long userId : testUserIds) {
            List<MessageResponseDto> messages = messageService.getUserMessages(userId, 0, 100);
            assertThat(messages).isNotNull();
        }
        
        long queryTime = System.currentTimeMillis() - queryStart;
        benchmarkResults.put("조회_10명x100건", queryTime);
        
        // 4. 검색 성능
        System.out.println("🔎 4. 메시지 검색 성능 테스트");
        long searchStart = System.currentTimeMillis();
        
        String[] searchKeywords = {"벤치마크", "성능", "테스트", "메시지", "루트"};
        for (String keyword : searchKeywords) {
            List<MessageResponseDto> searchResults = messageService
                    .searchMessages(testUserIds.get(0), keyword, 0, 50);
            assertThat(searchResults).isNotNull();
        }
        
        long searchTime = System.currentTimeMillis() - searchStart;
        benchmarkResults.put("검색_5개키워드", searchTime);
        
        // 5. 통계 조회 성능
        System.out.println("📊 5. 메시지 통계 조회 성능 테스트");
        long statsStart = System.currentTimeMillis();
        
        for (Long userId : testUserIds) {
            MessageStatsResponseDto stats = messageService.getUserMessageStats(userId);
            assertThat(stats).isNotNull();
        }
        
        long statsTime = System.currentTimeMillis() - statsStart;
        benchmarkResults.put("통계조회_10명", statsTime);
        
        long totalEndTime = System.currentTimeMillis();
        long totalTime = totalEndTime - totalStartTime;
        
        // 결과 출력 및 검증
        System.out.println("\n=== 📊 성능 벤치마크 결과 ===");
        benchmarkResults.forEach((test, time) -> 
                System.out.printf("%-20s: %,6d ms%n", test, time));
        System.out.printf("%-20s: %,6d ms%n", "전체_실행시간", totalTime);
        
        // 성능 기준 검증
        assertThat(benchmarkResults.get("개인메시지_2000건")).isLessThan(15000);
        assertThat(benchmarkResults.get("루트태그_1000건")).isLessThan(8000);
        assertThat(benchmarkResults.get("조회_10명x100건")).isLessThan(3000);
        assertThat(benchmarkResults.get("검색_5개키워드")).isLessThan(5000);
        assertThat(benchmarkResults.get("통계조회_10명")).isLessThan(2000);
        assertThat(totalTime).isLessThan(40000); // 전체 40초 이내
        
        System.out.println("\n✅ 모든 성능 벤치마크 기준 통과!");
        System.out.println("=== 메시지 시스템 전체 성능 벤치마크 완료 ===");
    }
}
```

## 성능 기준 및 목표

### 처리량 목표
- **개인 메시지**: 초당 400건 이상
- **루트 태그 메시지**: 초당 250건 이상  
- **메시지-알림 통합**: 초당 120건 이상
- **동시 접속**: 100명 동시 처리

### 응답 시간 목표
- **메시지 발송**: 평균 50ms 이하
- **메시지 조회**: 페이지당 100ms 이하
- **메시지 검색**: 1.5초 이하
- **통계 조회**: 200ms 이하

### 동시성 목표
- **동시 발송**: 스레드풀 20개로 처리
- **데이터베이스**: 커넥션 풀 최적화
- **캐시**: Redis 동시성 보장
- **메모리**: 힙 메모리 효율적 사용

## 실행 및 모니터링

### 실행 명령어
```bash
# 전체 성능 테스트 실행
./gradlew test --tests="*MessageIntegrationPerformanceTest"

# 특정 성능 테스트만 실행
./gradlew test --tests="MessageIntegrationPerformanceTest.PersonalMessagePerformanceTest"

# 메모리 사용량 모니터링과 함께 실행
./gradlew test --tests="*MessageIntegrationPerformanceTest" -Xms2g -Xmx4g
```

### 성능 모니터링
1. **실행 시간**: 각 테스트별 소요 시간 측정
2. **처리량**: 초당 처리 건수 계산
3. **메모리**: 힙 사용량 모니터링
4. **데이터베이스**: 커넥션 풀 상태 확인
5. **캐시**: Redis 메모리 사용률 확인

### 성능 최적화 포인트
- 배치 처리로 데이터베이스 호출 최소화
- Redis 캐싱으로 조회 성능 향상
- 비동기 처리로 알림 발송 최적화
- 인덱스 최적화로 검색 성능 향상
- 커넥션 풀 튜닝으로 동시성 개선

이 성능 테스트는 메시지 시스템이 대용량 트래픽 환경에서도 안정적으로 동작함을 보장합니다.