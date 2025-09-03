# E2E 테스트 데이터 생성 유틸리티

## 개요
E2E 테스트를 효율적으로 수행하기 위한 헬퍼 클래스와 유틸리티 모음입니다. 테스트 데이터 생성, 공통 작업 자동화, 검증 로직 등을 제공합니다.

## 테스트 데이터 생성 유틸리티

```java
package com.routepick.e2e.utils;

import com.routepick.auth.dto.request.SignupRequestDto;
import com.routepick.auth.dto.request.LoginRequestDto;
import com.routepick.gym.dto.request.GymRegistrationRequestDto;
import com.routepick.route.dto.request.RouteCreateRequestDto;
import com.routepick.user.dto.request.UserProfileUpdateRequestDto;
import com.routepick.climbing.dto.request.ClimbingRecordRequestDto;
import com.routepick.community.dto.request.PostCreateRequestDto;
import com.routepick.community.dto.request.CommentCreateRequestDto;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * E2E 테스트용 데이터 생성 유틸리티
 * 
 * 실제 사용자 시나리오와 유사한 테스트 데이터를 자동 생성하여
 * 테스트의 현실성과 신뢰성을 높입니다.
 */
@Component
public class TestDataGenerator {
    
    private static final String[] KOREAN_SURNAMES = {
        "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"
    };
    
    private static final String[] KOREAN_GIVEN_NAMES = {
        "민준", "서윤", "도윤", "예은", "시우", "하은", "주원", "지유", "건우", "채원"
    };
    
    private static final String[] CLIMBING_ADJECTIVES = {
        "강력한", "우아한", "도전적인", "재미있는", "기술적인", "파워풀한", "섬세한", "창의적인"
    };
    
    private static final String[] CLIMBING_NOUNS = {
        "크림퍼", "다이나마이트", "오버행마스터", "볼더러", "클라이머", "루트세터", "홀드킹", "스타일리스트"
    };
    
    private static final String[] GYM_NAMES = {
        "클라임존", "더클라이밍", "보울더월드", "락스터", "클라이머스", "정상클럽", "암벽마을", "볼더파크"
    };
    
    private static final String[] SEOUL_DISTRICTS = {
        "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
        "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구"
    };
    
    private final Random random = new Random();
    
    /**
     * 랜덤한 사용자 회원가입 요청 생성
     */
    public SignupRequestDto createRandomSignupRequest() {
        String nickname = generateRandomNickname();
        String email = generateRandomEmail(nickname);
        
        return SignupRequestDto.builder()
                .email(email)
                .password("TestPass123!")
                .nickName(nickname)
                .phone(generateRandomPhoneNumber())
                .birthDate(generateRandomBirthDate())
                .gender(random.nextBoolean() ? "MALE" : "FEMALE")
                .agreeToTerms(true)
                .agreeToPrivacy(true)
                .agreeToMarketing(random.nextBoolean())
                .build();
    }
    
    /**
     * 특정 정보를 가진 사용자 회원가입 요청 생성
     */
    public SignupRequestDto createSignupRequest(String email, String nickname) {
        return SignupRequestDto.builder()
                .email(email)
                .password("TestPass123!")
                .nickName(nickname)
                .phone(generateRandomPhoneNumber())
                .birthDate(generateRandomBirthDate())
                .gender("MALE")
                .agreeToTerms(true)
                .agreeToPrivacy(true)
                .agreeToMarketing(false)
                .build();
    }
    
    /**
     * 로그인 요청 생성
     */
    public LoginRequestDto createLoginRequest(String email, String password) {
        return LoginRequestDto.builder()
                .email(email)
                .password(password)
                .build();
    }
    
    /**
     * 사용자 프로필 업데이트 요청 생성
     */
    public UserProfileUpdateRequestDto createProfileUpdateRequest() {
        return UserProfileUpdateRequestDto.builder()
                .height(ThreadLocalRandom.current().nextInt(150, 190))
                .weight(ThreadLocalRandom.current().nextInt(45, 100))
                .climbingExperience(getRandomClimbingExperience())
                .preferredDifficulties(getRandomPreferredDifficulties())
                .bio(generateRandomBio())
                .build();
    }
    
    /**
     * 체육관 등록 요청 생성
     */
    public GymRegistrationRequestDto createGymRegistrationRequest() {
        String district = SEOUL_DISTRICTS[random.nextInt(SEOUL_DISTRICTS.length)];
        String gymName = GYM_NAMES[random.nextInt(GYM_NAMES.length)] + " " + district + "점";
        
        return GymRegistrationRequestDto.builder()
                .name(gymName)
                .address("서울특별시 " + district + " " + generateRandomAddress())
                .phone(generateRandomGymPhoneNumber())
                .latitude(generateSeoulLatitude())
                .longitude(generateSeoulLongitude())
                .description(generateRandomGymDescription(gymName))
                .build();
    }
    
    /**
     * 루트 생성 요청 생성
     */
    public RouteCreateRequestDto createRouteCreateRequest(Long branchId) {
        String routeName = generateRandomRouteName();
        String difficulty = getRandomDifficulty();
        
        return RouteCreateRequestDto.builder()
                .branchId(branchId)
                .routeName(routeName)
                .difficulty(difficulty)
                .color(getRandomRouteColor())
                .description(generateRandomRouteDescription(difficulty))
                .tags(getRandomTags())
                .build();
    }
    
    /**
     * 클라이밍 기록 요청 생성
     */
    public ClimbingRecordRequestDto createClimbingRecordRequest(Long userId, Long routeId) {
        boolean isCompleted = random.nextDouble() > 0.3; // 70% 완등률
        int attempts = isCompleted ? 
                ThreadLocalRandom.current().nextInt(1, 6) : 
                ThreadLocalRandom.current().nextInt(3, 12);
        
        return ClimbingRecordRequestDto.builder()
                .userId(userId)
                .routeId(routeId)
                .isCompleted(isCompleted)
                .attempts(attempts)
                .climbingDate(generateRandomClimbingDate())
                .notes(generateRandomClimbingNotes(isCompleted))
                .difficulty(getRandomDifficulty())
                .build();
    }
    
    /**
     * 커뮤니티 포스트 생성 요청 생성
     */
    public PostCreateRequestDto createPostCreateRequest(Long userId) {
        return PostCreateRequestDto.builder()
                .userId(userId)
                .title(generateRandomPostTitle())
                .content(generateRandomPostContent())
                .isPublic(random.nextDouble() > 0.1) // 90% 공개
                .build();
    }
    
    /**
     * 루트 관련 포스트 생성 요청 생성
     */
    public PostCreateRequestDto createRoutePostCreateRequest(Long userId, Long routeId) {
        return PostCreateRequestDto.builder()
                .userId(userId)
                .routeId(routeId)
                .title(generateRandomRoutePostTitle())
                .content(generateRandomRoutePostContent())
                .isPublic(true)
                .build();
    }
    
    /**
     * 댓글 생성 요청 생성
     */
    public CommentCreateRequestDto createCommentCreateRequest(Long postId, Long userId) {
        return CommentCreateRequestDto.builder()
                .postId(postId)
                .userId(userId)
                .content(generateRandomCommentContent())
                .build();
    }
    
    /**
     * 대댓글 생성 요청 생성
     */
    public CommentCreateRequestDto createReplyCreateRequest(Long postId, Long parentId, Long userId) {
        return CommentCreateRequestDto.builder()
                .postId(postId)
                .parentId(parentId)
                .userId(userId)
                .content(generateRandomReplyContent())
                .build();
    }
    
    // ================================================================================================
    // Private Helper Methods
    // ================================================================================================
    
    private String generateRandomNickname() {
        String adjective = CLIMBING_ADJECTIVES[random.nextInt(CLIMBING_ADJECTIVES.length)];
        String noun = CLIMBING_NOUNS[random.nextInt(CLIMBING_NOUNS.length)];
        int number = random.nextInt(1000);
        return adjective + noun + number;
    }
    
    private String generateRandomEmail(String nickname) {
        String[] domains = {"gmail.com", "naver.com", "kakao.com", "daum.net"};
        String domain = domains[random.nextInt(domains.length)];
        return nickname.toLowerCase() + "@" + domain;
    }
    
    private String generateRandomPhoneNumber() {
        return String.format("010-%04d-%04d", 
                random.nextInt(10000), random.nextInt(10000));
    }
    
    private String generateRandomBirthDate() {
        int year = ThreadLocalRandom.current().nextInt(1980, 2005);
        int month = ThreadLocalRandom.current().nextInt(1, 13);
        int day = ThreadLocalRandom.current().nextInt(1, 29); // 간단화
        return String.format("%04d-%02d-%02d", year, month, day);
    }
    
    private String generateRandomGymPhoneNumber() {
        String[] areaCodes = {"02", "031", "032", "033", "041", "042", "043", "051"};
        String areaCode = areaCodes[random.nextInt(areaCodes.length)];
        return String.format("%s-%04d-%04d", areaCode, 
                random.nextInt(1000, 10000), random.nextInt(1000, 10000));
    }
    
    private String generateRandomAddress() {
        String[] roadNames = {"테헤란로", "강남대로", "논현로", "선릉로", "봉은사로", "영동대로"};
        String roadName = roadNames[random.nextInt(roadNames.length)];
        int number = random.nextInt(1, 200);
        return roadName + " " + number + "번길 " + random.nextInt(1, 50);
    }
    
    private double generateSeoulLatitude() {
        // 서울 위도 범위: 37.4~ ~ 37.7~
        return 37.4 + random.nextDouble() * 0.3;
    }
    
    private double generateSeoulLongitude() {
        // 서울 경도 범위: 126.8~ ~ 127.2~
        return 126.8 + random.nextDouble() * 0.4;
    }
    
    private String getRandomClimbingExperience() {
        String[] experiences = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};
        return experiences[random.nextInt(experiences.length)];
    }
    
    private List<String> getRandomPreferredDifficulties() {
        String[][] difficultyGroups = {
            {"V0", "V1", "V2"},
            {"V1", "V2", "V3"},
            {"V2", "V3", "V4"},
            {"V3", "V4", "V5"},
            {"V4", "V5", "V6"}
        };
        String[] group = difficultyGroups[random.nextInt(difficultyGroups.length)];
        return Arrays.asList(group);
    }
    
    private String generateRandomBio() {
        String[] templates = {
            "클라이밍을 시작한지 %d개월째, %s 난이도에 도전 중입니다!",
            "%s를 좋아하는 클라이머입니다. 함께 클라이밍 하실분 연락주세요~",
            "주말마다 클라이밍장을 다니는 %s 레벨 클라이머입니다.",
            "클라이밍으로 건강한 라이프스타일을 만들어가는 중이에요 ⛰️"
        };
        
        String template = templates[random.nextInt(templates.length)];
        if (template.contains("%d")) {
            return String.format(template, random.nextInt(1, 36));
        } else if (template.contains("%s")) {
            return String.format(template, getRandomDifficulty());
        }
        return template;
    }
    
    private String generateRandomGymDescription(String gymName) {
        String[] templates = {
            "%s은 최신 시설과 다양한 루트를 제공하는 클라이밍 전문 체육관입니다.",
            "%s에서 초보자부터 고수까지 모든 레벨의 클라이머를 환영합니다!",
            "%s은 안전하고 깨끗한 환경에서 클라이밍을 즐길 수 있는 곳입니다.",
            "%s - 전문 루트세터가 매주 새로운 문제를 업데이트합니다."
        };
        
        String template = templates[random.nextInt(templates.length)];
        return String.format(template, gymName);
    }
    
    private String generateRandomRouteName() {
        String[] prefixes = {"레드", "블루", "그린", "옐로우", "퍼플", "오렌지", "화이트", "블랙"};
        String[] suffixes = {"워리어", "드래곤", "피닉스", "타이거", "이글", "울프", "라이온", "팬더"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        
        return prefix + " " + suffix;
    }
    
    private String getRandomDifficulty() {
        String[] difficulties = {"V0", "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8"};
        return difficulties[random.nextInt(difficulties.length)];
    }
    
    private String getRandomRouteColor() {
        String[] colors = {"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE", "WHITE", "BLACK"};
        return colors[random.nextInt(colors.length)];
    }
    
    private String generateRandomRouteDescription(String difficulty) {
        String[] templates = {
            "%s 난이도의 기술적인 루트입니다. 균형감각이 중요합니다.",
            "파워풀한 %s 루트! 상체 힘이 필요해요.",
            "%s 레벨의 재미있는 볼더 문제입니다.",
            "섬세한 풋워크가 요구되는 %s 루트입니다."
        };
        
        String template = templates[random.nextInt(templates.length)];
        return String.format(template, difficulty);
    }
    
    private List<String> getRandomTags() {
        String[] allTags = {
            "CRIMPING", "PINCHING", "SLOPERS", "JUGS", "POCKETS",
            "MANTLING", "DYNO", "STATIC", "COMPRESSION", "COORDINATION",
            "VERTICAL", "OVERHANG", "SLAB", "ROOF", "CORNER"
        };
        
        int tagCount = random.nextInt(2, 6); // 2~5개 태그
        List<String> selectedTags = new java.util.ArrayList<>();
        
        for (int i = 0; i < tagCount; i++) {
            String tag = allTags[random.nextInt(allTags.length)];
            if (!selectedTags.contains(tag)) {
                selectedTags.add(tag);
            }
        }
        
        return selectedTags;
    }
    
    private LocalDateTime generateRandomClimbingDate() {
        // 최근 30일 내 랜덤 날짜
        LocalDateTime now = LocalDateTime.now();
        int daysAgo = random.nextInt(30);
        int hour = ThreadLocalRandom.current().nextInt(10, 22); // 오전 10시~오후 10시
        int minute = random.nextInt(60);
        
        return now.minusDays(daysAgo).withHour(hour).withMinute(minute);
    }
    
    private String generateRandomClimbingNotes(boolean isCompleted) {
        if (isCompleted) {
            String[] completedNotes = {
                "드디어 완등! 정말 만족스러운 루트였어요.",
                "생각보다 쉽게 완등했네요. 재미있었습니다!",
                "몇 번 시도 끝에 완등 성공! 성취감이 대단해요.",
                "기술적으로 까다로웠지만 완등할 수 있어서 뿌듯합니다.",
                "완등! 다음엔 더 어려운 난이도에 도전해보겠습니다."
            };
            return completedNotes[random.nextInt(completedNotes.length)];
        } else {
            String[] failedNotes = {
                "아직 완등하지 못했지만 재미있는 루트네요. 다음에 다시 도전!",
                "마지막 무브가 어려워서 못했어요. 연습 더 하고 올게요.",
                "오늘은 컨디션이 안 좋아서... 다음번엔 꼭!",
                "거의 다 왔는데 아쉽게 떨어졌네요. 곧 완등할 수 있을 것 같아요.",
                "어려운 루트지만 포기하지 않고 계속 도전하겠습니다!"
            };
            return failedNotes[random.nextInt(failedNotes.length)];
        }
    }
    
    private String generateRandomPostTitle() {
        String[] templates = {
            "오늘 클라이밍 세션 후기",
            "%s 완등 성공! 🎉",
            "새로운 암장 다녀왔어요",
            "클라이밍 실력 향상을 위한 팁",
            "추천 루트 공유합니다",
            "클라이밍 장비 리뷰",
            "초보자 질문 있어요!",
            "함께 클라이밍 하실분 구해요"
        };
        
        String template = templates[random.nextInt(templates.length)];
        if (template.contains("%s")) {
            return String.format(template, getRandomDifficulty());
        }
        return template;
    }
    
    private String generateRandomPostContent() {
        String[] contents = {
            "오늘 정말 좋은 클라이밍 세션이었어요! 새로운 루트들을 시도해보면서 많이 배웠습니다. 특히 홀딩 기술이 많이 늘었다고 느꼈어요.",
            "드디어 도전하던 루트를 완등했습니다! 몇 주간 연습한 보람이 있네요. 다음 목표는 한 단계 더 어려운 난이도에 도전하는 것입니다.",
            "새로 개점한 클라이밍장에 다녀왔는데 시설도 좋고 루트도 다양해서 만족스러웠어요. 다들 한 번씩 가보시길 추천합니다!",
            "클라이밍을 시작한지 6개월이 되었는데 처음보다 많이 늘었다고 느껴요. 꾸준히 하는 것이 정말 중요한 것 같습니다.",
            "오늘은 테크닉 위주로 연습했어요. 발 위치와 무게 중심 이동에 집중하니 확실히 효율이 좋아지는 것 같습니다."
        };
        return contents[random.nextInt(contents.length)];
    }
    
    private String generateRandomRoutePostTitle() {
        String[] templates = {
            "이 루트 진짜 재미있어요!",
            "추천 루트 발견!",
            "도전적인 루트 공유",
            "오늘의 완등 루트 ⭐",
            "숨은 보석 같은 루트"
        };
        return templates[random.nextInt(templates.length)];
    }
    
    private String generateRandomRoutePostContent() {
        String[] contents = {
            "이 루트 정말 재미있게 설계되어 있어요! 기술적인 무브들이 잘 조합되어 있고, 완등했을 때의 성취감이 대단합니다.",
            "처음엔 어려워 보였는데 몇 번 시도하니 패턴이 보이더라고요. 크림핑과 풋워크가 중요한 루트입니다.",
            "이 루트는 정말 창의적이에요! 루트세터가 독특한 아이디어로 만든 것 같습니다. 추천드려요!",
            "파워보다는 기술이 중요한 루트네요. 균형감각과 바디 포지션이 핵심인 것 같습니다.",
            "오랜만에 정말 만족스러운 루트를 만났어요. 난이도도 적당하고 재미도 있고!"
        };
        return contents[random.nextInt(contents.length)];
    }
    
    private String generateRandomCommentContent() {
        String[] comments = {
            "정말 좋은 후기네요! 저도 한번 가봐야겠어요.",
            "완전 공감해요! 그 루트 저도 완등했는데 정말 재미있더라고요.",
            "아직 그 난이도는 어려워서... 더 연습하고 도전해보겠습니다!",
            "좋은 정보 감사합니다. 다음에 가서 꼭 해볼게요!",
            "와! 축하드려요! 🎉 저도 곧 도전해보겠습니다.",
            "팁 공유해주셔서 감사해요. 참고해서 연습해볼게요.",
            "사진도 올려주시면 좋을 것 같아요!",
            "그 암장 저도 자주 가는데 다음에 만나면 인사해요!"
        };
        return comments[random.nextInt(comments.length)];
    }
    
    private String generateRandomReplyContent() {
        String[] replies = {
            "네, 꼭 한번 가보세요! 후회하지 않으실거에요.",
            "감사합니다! 함께 클라이밍 하게 되면 좋겠어요.",
            "충분히 하실 수 있을거에요! 화이팅!",
            "다음에 만나면 자세히 알려드릴게요.",
            "저도 그렇게 생각해요. 사진 찍어서 올려볼게요!",
            "네! 만나면 인사드려요. 같이 클라이밍해요!"
        };
        return replies[random.nextInt(replies.length)];
    }
}
```

---

*분할된 파일: step9-6d3_e2e_helper_utils.md → step9-6d3a_test_data_generator.md*  
*내용: E2E 테스트용 데이터 생성 유틸리티 (TestDataGenerator)*  
*라인 수: 487줄*