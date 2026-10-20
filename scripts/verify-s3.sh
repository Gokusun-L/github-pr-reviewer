#!/usr/bin/env bash
# S3 验收脚本（证据制）—— 只读核对，不修改任何项目文件。
# 用法：bash scripts/verify-s3.sh          # 静态核对
#       bash scripts/verify-s3.sh --with-run  # 额外实跑一次启动，抓 .env 被加载的日志证据
#
# 本脚本回答的是：密钥有没有真的离开仓库、模板在不在、配置导入链在不在。
# 它不回答"值有没有被业务代码消费"——那要到 S4 用 GITHUB_TOKEN 打真实请求时才见分晓。

set -u
cd "$(dirname "$0")/.." || exit 1

PASS=0; FAIL=0; WARN=0
WITH_RUN=0
[ "${1:-}" = "--with-run" ] && WITH_RUN=1

sec() { printf '\n── %s ──\n' "$1"; }
ok()  { printf '  [PASS] %s\n' "$1"; PASS=$((PASS+1)); }
no()  { printf '  [FAIL] %s\n' "$1"; shift || true; for m in "$@"; do printf '         %s\n' "$m"; done; FAIL=$((FAIL+1)); }
warn(){ printf '  [WARN] %s\n' "$1"; WARN=$((WARN+1)); }
info(){ printf '  [INFO] %s\n' "$1"; }

echo "========================================"
echo " S3 验收：配置外部化 / 密钥不进仓库"
echo " 仓库：$(pwd)"
echo " HEAD：$(git rev-parse --short HEAD 2>/dev/null || echo '?')"
echo " 时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

sec "0. 环境（只报告，不判分）"
if [ -f .env ]; then
  info ".env 行数：$(wc -l < .env | tr -d ' ')（键名：$(grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' .env | tr -d '=' | tr '\n' ' '))"
else
  info "本机无 .env（干净环境，正常）"
fi

sec "1. .env 落在忽略区，且没有被 git 跟踪"
if git ls-files --error-unmatch .env >/dev/null 2>&1; then
  no ".env 已被 git 跟踪 —— 密钥进了仓库" "git rm --cached .env 后确认 .gitignore 生效"
else
  ok ".env 未被 git 跟踪"
fi
if git check-ignore -q .env 2>/dev/null; then
  ok ".gitignore 命中 .env（$(git check-ignore -v .env | cut -d: -f1-2)）"
else
  no ".gitignore 未忽略 .env" "补一行 .env"
fi

sec "2. 仓库里有模板 .env.example，且不含真实值"
if [ -f .env.example ]; then
  ok "存在 .env.example"
  if git ls-files --error-unmatch .env.example >/dev/null 2>&1; then
    ok ".env.example 已被 git 跟踪（clone 的人能拿到模板）"
  else
    no ".env.example 未入库" "git add .env.example 后提交"
  fi
  REAL=$(grep -vE '^\s*(#|$)' .env.example 2>/dev/null \
         | grep -viE '(your[_-]|changeme|change_me|placeholder|xxx|<[^>]*>|=\s*$)' || true)
  if [ -z "$REAL" ]; then
    ok ".env.example 里没有真实值形状的条目"
  else
    no ".env.example 疑似含真实值" "$(printf '%s' "$REAL" | sed -E 's/=(.{0,4}).*/=\1***(脱敏)/' | head -4)"
  fi
else
  no "缺少 .env.example" "模板要公开：键名给全、值留空或写占位；否则 clone 的人不知道该配什么"
fi

sec "3. 当前 HEAD 的受跟踪文件里搜不到本机真实口令"
# 判据只认「赋值形态」：KEY: value / KEY=value / "KEY": "value"
# 不做裸子串匹配 —— 否则密码若恰好等于库名（本项目就是这样），会被 pom.xml 里的包名误伤
LEAKED=0
CHECKED=0
for KEY in POSTGRES_PASSWORD GITHUB_TOKEN; do
  if [ -f .env ] && grep -qE "^${KEY}=" .env 2>/dev/null; then
    VAL=$(grep -E "^${KEY}=" .env | head -1 | cut -d= -f2- | tr -d '\r')
    if [ -n "${VAL}" ]; then
      CHECKED=1
      HIT=$(git grep -nE "[\"']?${KEY}[\"']?[[:space:]]*[:=][[:space:]]*[\"']?${VAL}[\"']?[[:space:]]*$" HEAD 2>/dev/null || true)
      if [ -n "$HIT" ]; then
        no "$KEY 的真实值以赋值形态出现在 HEAD 里" "$(printf '%s' "$HIT" | cut -d: -f2- | head -3)"
        LEAKED=1
      else
        ok "$KEY 的真实值没有以赋值形态出现在 HEAD 里"
      fi
    fi
  fi
done
[ "$CHECKED" = "1" ] || info "本机无 .env，本项跳过（不算通过也不算失败）"
if [ "$LEAKED" = "0" ] && [ -f .env ]; then
  DBN=$(grep -E '^POSTGRES_DB=' .env 2>/dev/null | cut -d= -f2- | tr -d '\r')
  DBP=$(grep -E '^POSTGRES_PASSWORD=' .env 2>/dev/null | cut -d= -f2- | tr -d '\r')
  if [ -n "$DBN" ] && [ "$DBN" = "$DBP" ]; then
    warn "口令 == 库名（$DBN）→ 熵为零，字典库里第一个就命中；轮换时顺手改掉"
  fi
fi

sec "4. 历史污染：旧明文是否仍与当前口令相同"
if [ -f .env ] && grep -qE '^POSTGRES_PASSWORD=' .env 2>/dev/null; then
  PW=$(grep -E '^POSTGRES_PASSWORD=' .env | head -1 | cut -d= -f2- | tr -d '\r')
  HCOMMITS=$(git log --all --oneline -S"$PW" -- '*.yml' '*.yaml' '*.properties' 2>/dev/null || true)
  if [ -n "$HCOMMITS" ]; then
    warn "当前口令在 git 历史里出现过 → 改文件只是搬家，这个口令仍然有效"
    printf '         命中提交：%s\n' "$(printf '%s' "$HCOMMITS" | head -3 | awk '{print $1}' | tr '\n' ' ')"
    printf '         判据：轮换值之后 `git log -S <新值>` 应为空；旧值留在历史里可接受（已失效）\n'
  else
    ok "当前口令不在任何历史提交里（已轮换）"
  fi
fi

sec "5. application.yml 里有 spring.config.import，且指向 .env"
APP=src/main/resources/application.yml
if [ -f "$APP" ]; then
  ok "存在 $APP"
  if grep -qE '^\s*import:.*\.env' "$APP"; then
    ok "含 spring.config.import → $(grep -oE 'optional:file:\.env.*' "$APP" | head -1)"
    if grep -qE '\.env\[\.properties\]' "$APP"; then
      ok "带 [.properties] 格式标签（无后缀文件必须贴标签）"
    else
      no "缺少 [.properties] 格式标签" ".env 没有标准后缀，Spring 不知道按哪种格式解析"
    fi
    if grep -qE 'optional:file:\.env' "$APP"; then
      ok "用 optional: 前缀（.env 缺失时不炸启动）"
    else
      warn "未用 optional: → 换机器/CI 上缺 .env 会直接启动失败"
    fi
  else
    no "application.yml 里没有指向 .env 的 import" "S3 的核心机制缺失"
  fi
else
  no "缺少 $APP"
fi

sec "6. docker-compose.yml 不再硬编码口令"
if [ -f docker-compose.yml ]; then
  if grep -qE 'POSTGRES_PASSWORD:\s*\$\{' docker-compose.yml; then
    ok "POSTGRES_PASSWORD 是 \${...} 占位：$(grep -oE 'POSTGRES_PASSWORD:.*' docker-compose.yml | head -1)"
  elif grep -qE '^\s*POSTGRES_PASSWORD:\s*\S' docker-compose.yml; then
    no "POSTGRES_PASSWORD 仍是字面值" "改成 \${POSTGRES_PASSWORD}，值放到 .env"
  fi
else
  no "缺少 docker-compose.yml"
fi

sec "7. 两份「读 .env 的引擎」解析规则是否被混用"
if grep -qE '^\s*(spring\.|server\.|github\.)' .env 2>/dev/null; then
  warn ".env 里出现了 Spring 的键名 → Compose 会把它们当插值变量一起读，两套引擎读同一个文件"
else
  info ".env 只放单纯的环境变量（Compose 插值与 Spring 导入互不干扰）"
fi
CRLF_ENV=$(C:/Users/aa/.workbuddy/binaries/python/versions/3.13.12/python.exe -c "
import sys
b=open('.env','rb').read()
print(b.count(b'\r\n'))" 2>/dev/null || echo 0)
if [ "${CRLF_ENV:-0}" -gt 0 ] 2>/dev/null; then
  warn ".env 是 CRLF（$CRLF_ENV 行）→ Compose 的 dotenv 解析可能把 \\r 留在值尾，报错会伪装成'密码错'"
else
  ok ".env 是 LF 行尾"
fi

sec "8. 事实对账：本地 HEAD 是否就是远程 main"
LOCAL=$(git rev-parse HEAD 2>/dev/null || echo '?')
REMOTE=$(git ls-remote --heads origin main 2>/dev/null | awk '{print $1}')
if [ -z "$REMOTE" ]; then
  warn "读不到远程 main（通道问题或未配置 remote），本项未能对账"
elif [ "$LOCAL" = "$REMOTE" ]; then
  ok "本地 = 远程 = ${LOCAL:0:7}"
else
  no "本地与远程不一致" "本地 ${LOCAL:0:7} / 远程 ${REMOTE:0:7}"
fi

if [ "$WITH_RUN" = "1" ]; then
  sec "9. 实跑：抓 .env 被 Spring 加载的日志证据"
  if command -v mvn >/dev/null 2>&1; then
    LOG=$(mktemp 2>/dev/null || echo /tmp/s3-run.log)
    printf '  （启动中，最多等 70s …）\n'
    env -u SERVER__PORT -u SERVER__HOST timeout 70 mvn -q spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Dlogging.level.org.springframework.boot.context.config=TRACE" \
      > "$LOG" 2>&1 || true
    EV=$(grep -E "Loaded resource file \[\.env\]|Adding imported property source.*\.env" "$LOG" | head -2)
    if [ -n "$EV" ]; then
      ok ".env 确实被 Spring 加载"
      printf '%s\n' "$EV" | sed -E 's/^.*: //' | sed 's/^/         /'
    else
      warn "日志里没抓到 .env 加载证据（看 $LOG）"
    fi
    grep -E "Tomcat started on port" "$LOG" | head -1 | sed 's/^/         /'
  else
    warn "mvn 不可用，跳过实跑"
  fi
else
  sec "9. 实跑（已跳过）"
  info "需要启动证据时加参数：bash scripts/verify-s3.sh --with-run"
fi

echo
echo "========================================"
printf ' 结果：PASS %d ｜ FAIL %d ｜ WARN %d\n' "$PASS" "$FAIL" "$WARN"
if [ "$FAIL" -eq 0 ]; then
  echo " → 静态项全过。别忘了补启动日志与反向输出，两段齐了才算放行。"
else
  echo " → 有 FAIL，S3 未通过。"
fi
echo "========================================"
[ "$FAIL" -eq 0 ] || exit 1
