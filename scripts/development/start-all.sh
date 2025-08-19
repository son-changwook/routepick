#!/bin/bash

# RoutePickr 개발 환경 전체 시작 스크립트
# 모든 서비스를 순차적으로 시작하고 상태 확인

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 프로젝트 루트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
cd "$PROJECT_ROOT"

log_info "RoutePickr 개발 환경 시작 중..."
log_info "프로젝트 디렉토리: $PROJECT_ROOT"

# 1. Docker 상태 확인
log_info "Docker 상태 확인 중..."
if ! docker info > /dev/null 2>&1; then
    log_error "Docker가 실행되지 않았습니다. Docker를 시작해주세요."
    exit 1
fi
log_success "Docker 확인 완료"

# 2. 기존 컨테이너 정리 (선택사항)
if [ "$1" = "--clean" ]; then
    log_warning "기존 컨테이너 정리 중..."
    docker-compose down -v
    docker system prune -f
    log_success "정리 완료"
fi

# 3. 환경 변수 파일 확인
if [ ! -f ".env" ]; then
    log_warning ".env 파일이 없습니다. 기본 설정을 생성합니다."
    cp .env.example .env
fi

# 4. 네트워크 및 볼륨 생성
log_info "Docker 네트워크 및 볼륨 확인 중..."
docker network create routepick-network 2>/dev/null || true

# 5. 데이터베이스 및 캐시 서비스 먼저 시작
log_info "인프라 서비스 시작 중 (MySQL, Redis)..."
docker-compose up -d mysql redis

# 6. 데이터베이스 준비 대기
log_info "MySQL 서비스 준비 대기 중..."
timeout=60
counter=0
while ! docker-compose exec -T mysql mysqladmin ping -h"mysql" -u"routepick" -p"routepick2024!" --silent; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -gt $timeout ]; then
        log_error "MySQL 시작 타임아웃 (${timeout}초)"
        exit 1
    fi
    echo -n "."
done
echo ""
log_success "MySQL 준비 완료"

# 7. Redis 준비 대기
log_info "Redis 서비스 준비 대기 중..."
timeout=30
counter=0
while ! docker-compose exec -T redis redis-cli -a "routepick2024!" ping > /dev/null 2>&1; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -gt $timeout ]; then
        log_error "Redis 시작 타임아웃 (${timeout}초)"
        exit 1
    fi
    echo -n "."
done
echo ""
log_success "Redis 준비 완료"

# 8. Backend 애플리케이션 시작
log_info "Backend 애플리케이션 시작 중..."
docker-compose up -d backend

# 9. Backend 준비 대기
log_info "Backend 애플리케이션 준비 대기 중..."
timeout=120
counter=0
while ! curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; do
    sleep 5
    counter=$((counter + 5))
    if [ $counter -gt $timeout ]; then
        log_error "Backend 시작 타임아웃 (${timeout}초)"
        log_info "Backend 로그 확인:"
        docker-compose logs backend | tail -20
        exit 1
    fi
    echo -n "."
done
echo ""
log_success "Backend 준비 완료"

# 10. Frontend 애플리케이션들 시작
log_info "Frontend 애플리케이션들 시작 중..."
docker-compose up -d admin-web metro

# 11. 나머지 서비스들 시작
log_info "지원 서비스들 시작 중 (Nginx, MinIO, 모니터링 도구)..."
docker-compose up -d nginx minio elasticsearch kibana prometheus grafana mailhog

# 12. 모든 서비스 상태 확인
log_info "서비스 상태 확인 중..."
sleep 10

# 서비스별 상태 확인
services=("mysql" "redis" "backend" "admin-web" "nginx" "minio")
for service in "${services[@]}"; do
    if docker-compose ps | grep "$service" | grep -q "Up"; then
        log_success "$service: 실행 중"
    else
        log_error "$service: 실행 실패"
    fi
done

# 13. 서비스 URL 정보 출력
echo ""
log_success "=== RoutePickr 개발 환경 준비 완료 ==="
echo ""
echo "🚀 서비스 접속 URL:"
echo "  📱 React Native Metro Bundler: http://localhost:8081"
echo "  💻 관리자 웹: http://localhost:3000 (또는 http://admin.routepick.local)"
echo "  🔧 Backend API: http://localhost:8080/api/v1"
echo "  📚 API 문서: http://localhost:8080/swagger-ui/index.html"
echo "  ⚡ API Health Check: http://localhost:8080/actuator/health"
echo ""
echo "🛠️ 개발 도구:"
echo "  📊 Grafana 대시보드: http://localhost:3001 (admin/routepick2024!)"
echo "  📈 Prometheus: http://localhost:9090"
echo "  🔍 Kibana: http://localhost:5601"
echo "  📧 MailHog: http://localhost:8025"
echo "  💾 MinIO Console: http://localhost:9001 (routepick/routepick2024!)"
echo ""
echo "💾 데이터베이스 접속 정보:"
echo "  🗄️ MySQL: localhost:3306 (routepick/routepick2024!)"
echo "  ⚡ Redis: localhost:6379 (routepick2024!)"
echo ""

# 14. 로그 모니터링 안내
log_info "실시간 로그 확인:"
echo "  전체 로그: docker-compose logs -f"
echo "  Backend 로그: docker-compose logs -f backend"
echo "  Admin 웹 로그: docker-compose logs -f admin-web"
echo ""

# 15. hosts 파일 설정 안내
log_warning "로컬 도메인 사용을 위해 /etc/hosts 파일에 다음을 추가하세요:"
echo "127.0.0.1 api.routepick.local"
echo "127.0.0.1 admin.routepick.local"
echo "127.0.0.1 monitor.routepick.local"
echo ""

# 16. 종료 안내
log_info "개발 환경 종료 시: ./scripts/development/stop-all.sh"
log_info "전체 재시작 시: ./scripts/development/restart-all.sh"
log_info "로그 확인 시: ./scripts/development/show-logs.sh"

log_success "🎉 RoutePickr 개발 환경이 성공적으로 시작되었습니다!"