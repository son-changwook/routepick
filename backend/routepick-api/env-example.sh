#!/bin/bash

# RoutePick API 프로덕션 환경변수 설정 예시
# 이 파일을 복사하여 실제 환경변수를 설정하세요

# 데이터베이스 설정
export DB_URL="jdbc:mysql://your-db-host:3306/routepick?useSSL=true&allowPublicKeyRetrieval=false&serverTimezone=Asia/Seoul"
export DB_USERNAME="routepick"
export DB_PASSWORD="your-secure-database-password"

# JWT 시크릿 키 (최소 256비트 이상의 강력한 키 사용)
export JWT_SECRET="your-super-secure-jwt-secret-key-with-at-least-256-bits-minimum-length-for-production-security"

# 이메일 설정
export MAIL_HOST="smtp.gmail.com"
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-app-password"
export MAIL_FROM="noreply@routepick.com"

# CORS 설정
export CORS_ALLOWED_ORIGINS="https://routepick.com,https://www.routepick.com"

# 서버 포트
export SERVER_PORT="8080"

# Spring Profile
export SPRING_PROFILES_ACTIVE="prod"

# 로깅 레벨
export LOGGING_LEVEL_ROOT="WARN"
export LOGGING_LEVEL_COM_ROUTEPICK_API="INFO"

echo "환경변수 설정이 완료되었습니다."
echo "애플리케이션을 시작하려면: ./gradlew :routepick-api:bootRun" 