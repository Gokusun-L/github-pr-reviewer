#!/usr/bin/env bash
# S2 验收脚本（证据制）—— 只读核对，不修改任何项目文件，不删除容器与卷。
# 用法：bash scripts/verify-s2.sh
# 说明：本脚本只回答"声明的东西真的在跑吗"，不回答"是从零起来的吗"。
#       后者只能靠 `docker compose down -v && docker compose up -d` 人工验证（见第 8 项）。

set -u
cd "$(dirname "$0")/.." || exit 1

PASS=0; FAIL=0; WARN=0

sec() { printf '\n── %s ──\n' "$1"; }
ok()  { printf '  [PASS] %s\n' "$1"; PASS=$((PASS+1)); }
no()  { printf '  [FAIL] %s\n' "$1"; shift || true; for m in "$@"; do printf '         %s\n' "$m"; done; FAIL=$((FAIL+1)); }
warn(){ printf '  [WARN] %s\n' "$1"; WARN=$((WARN+1)); }

echo "========================================"
echo " S2 验收：Docker Compose 起 PostgreSQL"
echo " 仓库：$(pwd)"
echo " 时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

sec "0. 环境（只报告，不判分）"
if command -v docker >/dev/null 2>&1; then
  docker version --format '  client {{.Client.Version}} / server {{.Server.Version}}' 2>&1 | head -2
  printf '  compose：%s\n' "$(docker compose version --short 2>/dev/null || echo '不可用')"
else
  printf '  [WARN] docker 不可用，后续全部会判 FAIL\n'
fi

sec "1. docker-compose.yml 在仓库根并被 git 跟踪"
if [ -f docker-compose.yml ]; then
  ok "根目录存在 docker-compose.yml"
  if git ls-files --error-unmatch docker-compose.yml >/dev/null 2>&1; then
    ok "docker-compose.yml 已被 git 跟踪"
  else
    no "docker-compose.yml 未被 git 跟踪" "git add docker-compose.yml 后再提交"
  fi
else
  no "根目录没有 docker-compose.yml"
fi

sec "2. compose 文件本身合法（docker compose config）"
if [ -f docker-compose.yml ] && docker compose config >/dev/null 2>&1; then
  ok "compose 文件通过校验"
  printf '  声明了这些服务：\n'
  docker compose config --services 2>/dev/null | sed 's/^/    /'
else
  no "compose 文件不合法" "先跑 docker compose config 看报错"
fi

sec "3. 声明的服务处于 running"
RUNNING=$(docker compose ps --services --filter status=running 2>/dev/null | sort | tr '\n' ' ')
DECLARED=$(docker compose config --services 2>/dev/null | sort | tr '\n' ' ')
printf '  声明：%s\n  运行：%s\n' "${DECLARED:-（无）}" "${RUNNING:-（无）}"
if [ -n "$RUNNING" ] && [ "$RUNNING" = "$DECLARED" ]; then
  ok "声明的服务全部在运行"
else
  no "有服务未运行" "docker compose up -d 后再看 docker compose ps"
fi

sec "4. PostgreSQL 真能连（用 compose 里声明的用户与库）"
if docker compose ps --services --filter status=running 2>/dev/null | grep -qx postgres; then
  PGU=$(docker compose exec -T postgres printenv POSTGRES_USER 2>/dev/null | tr -d '\r')
  PGD=$(docker compose exec -T postgres printenv POSTGRES_DB 2>/dev/null | tr -d '\r')
  printf '  POSTGRES_USER=%s  POSTGRES_DB=%s\n' "${PGU:-（未声明）}" "${PGD:-（未声明）}"
  VER=$(docker compose exec -T postgres psql -U "${PGU:-postgres}" -d "${PGD:-postgres}" -tAc "select version()" 2>&1 | tr -d '\r')
  if printf '%s' "$VER" | grep -qi 'PostgreSQL'; then
    ok "psql 连通：$(printf '%s' "$VER" | cut -c1-46)…"
  else
    no "psql 连不上" "$(printf '%s' "$VER" | head -2)"
  fi
else
  no "postgres 服务未运行，无法验证连接"
fi

sec "5. Redis 真能连（PING → PONG）"
if docker compose ps --services --filter status=running 2>/dev/null | grep -qx redis; then
  R=$(docker compose exec -T redis redis-cli ping 2>&1 | tr -d '\r')
  if [ "$R" = "PONG" ]; then
    ok "redis-cli PING → PONG"
  else
    no "redis PING 未返回 PONG" "$R"
  fi
else
  printf '  [WARN] 本步没有 redis 服务（S2 只要求 PostgreSQL）\n'
  WARN=$((WARN+1))
fi

sec "6. 端口映射到宿主机"
for S in postgres redis; do
  if docker compose ps --services --filter status=running 2>/dev/null | grep -qx "$S"; then
    P=$(docker compose port "$S" 5432 2>/dev/null | head -1)
    [ -z "$P" ] && P=$(docker compose port "$S" 6379 2>/dev/null | head -1)
    if [ -n "$P" ]; then
      ok "$S 已发布端口：$P"
      HOSTPORT=${P##*:}
      case "$P" in
        0.0.0.0:*|\[::\]:*|\[::\]:*) warn "$S 绑定到 0.0.0.0（局域网可访问）→ 本机开发建议 127.0.0.1:${HOSTPORT}" ;;
      esac
    else
      no "$S 没有端口映射" "ports: 段里没写，或容器未运行"
    fi
  fi
done

sec "7. 卷与网络（重启后数据是否还在的前提）"
docker volume ls 2>/dev/null | grep -E "$(basename "$(pwd)" | tr 'A-Z' 'a-z')_" | sed 's/^/  /' || true
if docker volume ls 2>/dev/null | grep -q "$(basename "$(pwd)" | tr 'A-Z' 'a-z')_"; then
  ok "存在命名卷（数据不随容器删除而丢）"
else
  no "没有命名卷" "volumes: 段里声明并在服务里挂载"
fi

sec "8. 从零复现（只能人工做，这是唯一能证明 compose 文件自己够用的方式）"
printf '  %s\n' "docker compose down -v && docker compose up -d"
printf '  %s\n' "判据：容器 Created 时间是刚才；docker compose ps 全 Up"

sec "9. 事实对账（本地 vs 远程）"
if [ -z "$(git status --porcelain)" ]; then
  ok "工作区干净"
else
  no "工作区有未提交改动" "$(git status --porcelain | head -5 | tr '\n' ' ')"
fi
LOCAL=$(git rev-parse HEAD 2>/dev/null || echo none)
REMOTE=$(git ls-remote origin refs/heads/main 2>/dev/null | cut -f1)
if [ -n "$REMOTE" ] && [ "$LOCAL" = "$REMOTE" ]; then
  ok "本地与远程一致（${LOCAL:0:7}）"
elif [ -z "$REMOTE" ]; then
  no "取不到远程 main（网络或凭据问题）" "重试，或走 REST API 核对"
else
  no "本地与远程不一致" "本地 ${LOCAL:0:7} / 远程 ${REMOTE:0:7} → 需要 push"
fi

sec "10. 机密是否被挡在仓库外"
for F in .env; do
  if [ -f "$F" ] && ! git ls-files --error-unmatch "$F" >/dev/null 2>&1; then
    ok "$F 存在且未被跟踪"
  elif [ -f "$F" ]; then
    no "$F 被 git 跟踪了" "立刻 git rm --cached $F 并写进 .gitignore"
  fi
done
if grep -qE 'password|PASSWORD' docker-compose.yml 2>/dev/null; then
  warn "compose 里有明文口令 → S3 配置外部化的正题（现在先记着，不要急着改）"
fi
if ! grep -q 'healthcheck' docker-compose.yml 2>/dev/null; then
  warn "没有 healthcheck → S8 端到端时 app 可能比 DB 先就绪，连接被拒"
fi

echo
echo "========================================"
printf " PASS %d ｜ FAIL %d ｜ WARN %d\n" "$PASS" "$FAIL" "$WARN"
if [ "$FAIL" -eq 0 ]; then
  echo " 结论：S2 硬性项全过（WARN 不阻塞，但要知道自己踩着什么）。"
else
  echo " 结论：未通过。先把 FAIL 项清掉。"
fi
echo "========================================"
