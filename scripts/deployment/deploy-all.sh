#!/bin/bash

# RoutePickr 전체 배포 스크립트
# AWS 인프라 배포 및 애플리케이션 배포

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# 사용법 출력
usage() {
    echo "사용법: $0 [환경] [옵션]"
    echo ""
    echo "환경:"
    echo "  dev       개발 환경 배포"
    echo "  staging   스테이징 환경 배포"
    echo "  prod      운영 환경 배포"
    echo ""
    echo "옵션:"
    echo "  --infra-only    인프라만 배포"
    echo "  --app-only      애플리케이션만 배포"
    echo "  --plan          Terraform plan만 실행"
    echo "  --force         확인 없이 강제 배포"
    echo ""
    echo "예시:"
    echo "  $0 dev                    # 개발 환경 전체 배포"
    echo "  $0 prod --plan            # 운영 환경 계획 확인"
    echo "  $0 staging --infra-only   # 스테이징 인프라만 배포"
}

# 파라미터 검증
if [ $# -eq 0 ]; then
    usage
    exit 1
fi

ENVIRONMENT=$1
INFRA_ONLY=false
APP_ONLY=false
PLAN_ONLY=false
FORCE=false

# 환경 검증
case $ENVIRONMENT in
    dev|staging|prod)
        ;;
    *)
        log_error "잘못된 환경: $ENVIRONMENT"
        usage
        exit 1
        ;;
esac

# 옵션 파싱
shift
while [[ $# -gt 0 ]]; do
    case $1 in
        --infra-only)
            INFRA_ONLY=true
            shift
            ;;
        --app-only)
            APP_ONLY=true
            shift
            ;;
        --plan)
            PLAN_ONLY=true
            shift
            ;;
        --force)
            FORCE=true
            shift
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            usage
            exit 1
            ;;
    esac
done

# 프로젝트 루트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
cd "$PROJECT_ROOT"

log_info "RoutePickr $ENVIRONMENT 환경 배포 시작"
log_info "프로젝트 디렉토리: $PROJECT_ROOT"

# 1. 필수 도구 확인
log_info "필수 도구 확인 중..."

tools=("terraform" "aws" "docker" "kubectl")
for tool in "${tools[@]}"; do
    if ! command -v $tool &> /dev/null; then
        log_error "$tool이 설치되지 않았습니다."
        exit 1
    fi
done
log_success "필수 도구 확인 완료"

# 2. AWS 자격 증명 확인
log_info "AWS 자격 증명 확인 중..."
if ! aws sts get-caller-identity &> /dev/null; then
    log_error "AWS 자격 증명이 설정되지 않았습니다. 'aws configure'를 실행하세요."
    exit 1
fi
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
log_success "AWS 계정 확인: $AWS_ACCOUNT"

# 3. 배포 확인 (운영 환경의 경우)
if [ "$ENVIRONMENT" = "prod" ] && [ "$FORCE" = false ]; then
    log_warning "⚠️  운영 환경에 배포하려고 합니다!"
    echo "배포할 내용:"
    echo "  - 환경: $ENVIRONMENT"
    echo "  - AWS 계정: $AWS_ACCOUNT"
    echo "  - 인프라만: $INFRA_ONLY"
    echo "  - 앱만: $APP_ONLY"
    echo ""
    read -p "계속하시겠습니까? (yes/no): " -r
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        log_info "배포가 취소되었습니다."
        exit 0
    fi
fi

# 4. 인프라 배포 (Terraform)
if [ "$APP_ONLY" = false ]; then
    log_info "=== 인프라 배포 시작 ==="
    
    cd routepick-infrastructure/terraform
    
    # Terraform 초기화
    log_info "Terraform 초기화 중..."
    terraform init -backend-config="key=$ENVIRONMENT/terraform.tfstate"
    
    # Workspace 선택/생성
    if terraform workspace list | grep -q "$ENVIRONMENT"; then
        terraform workspace select "$ENVIRONMENT"
    else
        terraform workspace new "$ENVIRONMENT"
    fi
    
    # 변수 파일 설정
    TFVARS_FILE="environments/$ENVIRONMENT.tfvars"
    if [ ! -f "$TFVARS_FILE" ]; then
        log_error "환경 변수 파일이 없습니다: $TFVARS_FILE"
        exit 1
    fi
    
    # Plan 실행
    log_info "Terraform Plan 실행 중..."
    terraform plan -var-file="$TFVARS_FILE" -out="$ENVIRONMENT.tfplan"
    
    if [ "$PLAN_ONLY" = true ]; then
        log_success "Plan 완료. 실제 배포는 --plan 옵션 없이 실행하세요."
        exit 0
    fi
    
    # Apply 실행
    log_info "Terraform Apply 실행 중..."
    terraform apply "$ENVIRONMENT.tfplan"
    
    # 출력값 저장
    terraform output -json > "../outputs/$ENVIRONMENT.json"
    
    log_success "인프라 배포 완료"
    
    cd "$PROJECT_ROOT"
fi

# 5. 애플리케이션 빌드 및 배포
if [ "$INFRA_ONLY" = false ]; then
    log_info "=== 애플리케이션 배포 시작 ==="
    
    # ECR 로그인
    log_info "ECR 로그인 중..."
    ECR_REGISTRY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.ap-northeast-2.amazonaws.com
    aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin $ECR_REGISTRY
    
    # Backend 이미지 빌드 및 푸시
    log_info "Backend 이미지 빌드 중..."
    cd routepick-backend
    docker build -t routepick-backend:$ENVIRONMENT .
    docker tag routepick-backend:$ENVIRONMENT $ECR_REGISTRY/routepick-backend:$ENVIRONMENT
    docker tag routepick-backend:$ENVIRONMENT $ECR_REGISTRY/routepick-backend:latest
    docker push $ECR_REGISTRY/routepick-backend:$ENVIRONMENT
    docker push $ECR_REGISTRY/routepick-backend:latest
    
    # Admin Web 이미지 빌드 및 푸시
    log_info "Admin Web 이미지 빌드 중..."
    cd ../routepick-admin
    docker build -t routepick-admin:$ENVIRONMENT .
    docker tag routepick-admin:$ENVIRONMENT $ECR_REGISTRY/routepick-admin:$ENVIRONMENT
    docker tag routepick-admin:$ENVIRONMENT $ECR_REGISTRY/routepick-admin:latest
    docker push $ECR_REGISTRY/routepick-admin:$ENVIRONMENT
    docker push $ECR_REGISTRY/routepick-admin:latest
    
    cd "$PROJECT_ROOT"
    
    # ECS 서비스 업데이트
    log_info "ECS 서비스 업데이트 중..."
    aws ecs update-service \
        --cluster "routepick-$ENVIRONMENT" \
        --service "routepick-backend-$ENVIRONMENT" \
        --force-new-deployment \
        --region ap-northeast-2
    
    aws ecs update-service \
        --cluster "routepick-$ENVIRONMENT" \
        --service "routepick-admin-$ENVIRONMENT" \
        --force-new-deployment \
        --region ap-northeast-2
    
    # 배포 완료 대기
    log_info "배포 완료 대기 중..."
    aws ecs wait services-stable \
        --cluster "routepick-$ENVIRONMENT" \
        --services "routepick-backend-$ENVIRONMENT" \
        --region ap-northeast-2
    
    aws ecs wait services-stable \
        --cluster "routepick-$ENVIRONMENT" \
        --services "routepick-admin-$ENVIRONMENT" \
        --region ap-northeast-2
    
    log_success "애플리케이션 배포 완료"
fi

# 6. 배포 후 검증
log_info "=== 배포 검증 시작 ==="

# 로드 밸런서 엔드포인트 확인
if [ -f "routepick-infrastructure/outputs/$ENVIRONMENT.json" ]; then
    ALB_DNS=$(cat "routepick-infrastructure/outputs/$ENVIRONMENT.json" | jq -r '.alb_dns_name.value')
    
    if [ "$ALB_DNS" != "null" ]; then
        log_info "로드 밸런서 DNS: $ALB_DNS"
        
        # Health Check
        log_info "Health Check 수행 중..."
        for i in {1..10}; do
            if curl -f "http://$ALB_DNS/actuator/health" > /dev/null 2>&1; then
                log_success "Health Check 성공"
                break
            else
                log_warning "Health Check 실패 (시도 $i/10)"
                sleep 30
            fi
        done
    fi
fi

# 7. 배포 완료 리포트
log_success "=== 배포 완료 ==="
echo ""
echo "🚀 배포 정보:"
echo "  환경: $ENVIRONMENT"
echo "  AWS 계정: $AWS_ACCOUNT"
echo "  리전: ap-northeast-2"
echo "  배포 시간: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

if [ -f "routepick-infrastructure/outputs/$ENVIRONMENT.json" ]; then
    echo "🌐 서비스 엔드포인트:"
    ALB_DNS=$(cat "routepick-infrastructure/outputs/$ENVIRONMENT.json" | jq -r '.alb_dns_name.value // "N/A"')
    CLOUDFRONT_DOMAIN=$(cat "routepick-infrastructure/outputs/$ENVIRONMENT.json" | jq -r '.cloudfront_domain_name.value // "N/A"')
    
    echo "  Backend API: http://$ALB_DNS/api/v1"
    echo "  Admin Web: http://$ALB_DNS"
    echo "  CDN: https://$CLOUDFRONT_DOMAIN"
    echo "  API 문서: http://$ALB_DNS/swagger-ui/index.html"
fi

echo ""
echo "📊 모니터링:"
echo "  CloudWatch: https://console.aws.amazon.com/cloudwatch/"
echo "  ECS: https://console.aws.amazon.com/ecs/"
echo "  RDS: https://console.aws.amazon.com/rds/"
echo ""

log_info "배포 롤백 시: ./scripts/deployment/rollback.sh $ENVIRONMENT"
log_info "로그 확인 시: ./scripts/deployment/show-logs.sh $ENVIRONMENT"

log_success "🎉 RoutePickr $ENVIRONMENT 환경 배포가 완료되었습니다!"