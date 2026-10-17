#!/usr/bin/env bash
# S1 验收脚本（证据制）—— 只读核对，不修改任何项目文件（仅 mvn package 会生成 target/，已在 .gitignore 内）。
# 用法：bash scripts/verify-s1.sh
# 第 3 项（能否启动并监听 8080）不自动执行：Windows 上后台起 mvn 再杀进程不可靠，
# 脚本给出命令与判据，由本人跑一次并贴输出。

set -u
cd "$(dirname "$0")/.." || exit 1

PASS=0; FAIL=0; MANUAL=0

sec() { printf '\n── %s ──\n' "$1"; }
ok()  { printf '  [PASS]   %s\n' "$1"; PASS=$((PASS+1)); }
no()  { printf '  [FAIL]   %s\n' "$1"; shift || true; for m in "$@"; do printf '           %s\n' "$m"; done; FAIL=$((FAIL+1)); }
man() { printf '  [MANUAL] %s\n' "$1"; if [ $# -gt 1 ]; then printf '           %s\n' "$2"; fi; MANUAL=$((MANUAL+1)); }

echo "========================================"
echo " S1 验收：Spring Boot 骨架"
echo " 仓库：$(pwd)"
echo " 时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

sec "0. 环境（只报告，不判分）"
if command -v mvn >/dev/null 2>&1; then
  mvn -v 2>&1 | head -3 | sed 's/^/  /'
else
  printf '  [WARN] mvn 不可用，后续构建项会直接判 FAIL\n'
fi

sec "1. pom.xml 在仓库根并被 git 跟踪"
if [ -f pom.xml ]; then
  ok "根目录存在 pom.xml"
  if git ls-files --error-unmatch pom.xml >/dev/null 2>&1; then
    ok "pom.xml 已被 git 跟踪"
  else
    no "pom.xml 未被 git 跟踪" "git add pom.xml 后再提交"
  fi
else
  no "根目录没有 pom.xml" "S1 第一件事就是把 pom.xml 放到仓库根"
fi

sec "2. mvn -DskipTests package 成功并产出 jar"
if [ -f pom.xml ] && command -v mvn >/dev/null 2>&1; then
  mkdir -p target
  if mvn -q -DskipTests package > target/s1-build.log 2>&1; then
    ok "构建成功（mvn -q -DskipTests package）"
  else
    no "构建失败" "见 target/s1-build.log 末尾："
    tail -6 target/s1-build.log 2>/dev/null | sed 's/^/           /'
  fi
else
  no "跳过构建：pom.xml 或 mvn 缺失"
fi
if ls target/*.jar >/dev/null 2>&1; then
  ok "target/ 下有 jar：$(ls target/*.jar | tr '\n' ' ')"
else
  no "target/ 下没有 jar"
fi

sec "3. 能启动并监听 8080（手动，必做）"
man "另开一个终端执行：" "mvn spring-boot:run"
man "判据：日志出现 Tomcat started on port 8080"

sec "4. 包结构是 com.gokusun.prreviewer，且没有 demo 包"
if [ -d src/main/java/com/gokusun/prreviewer ]; then
  ok "存在目录 src/main/java/com/gokusun/prreviewer"
else
  no "缺少目录 src/main/java/com/gokusun/prreviewer" "包名要换成自己的，不要留模板包"
fi
if [ -d src/main/java/demo ]; then
  no "仍存在 src/main/java/demo" "S1 要求删掉 demo 包（判据见 学习开发记录 Q5）"
else
  ok "src/main/java 下已无 demo 包"
fi
printf '  当前 java 源文件：\n'
find src/main/java -name '*.java' 2>/dev/null | sed 's/^/    /'

sec "5. 存在 @SpringBootApplication 启动类"
HITS=$(grep -rl "@SpringBootApplication" src/main/java 2>/dev/null | tr '\n' ' ')
if [ -n "$HITS" ]; then
  ok "找到启动类：$HITS"
else
  no "没有找到 @SpringBootApplication"
fi

sec "6. 依赖只有 web + test 两类 starter"
if [ -f pom.xml ]; then
  STARTERS=$(grep -o 'spring-boot-starter-[a-z0-9-]*' pom.xml | sort -u)
  printf '  pom 中出现的 starter：\n'
  printf '%s\n' "$STARTERS" | sed 's/^/    /'
  EXTRA=$(printf '%s\n' "$STARTERS" | grep -v -E '^spring-boot-starter-(web|test|parent)$' || true)
  if [ -z "$EXTRA" ]; then
    ok "只有 web 与 test 两类 starter"
  else
    no "出现额外 starter" "$(printf '%s' "$EXTRA" | tr '\n' ' ')"
  fi
else
  no "没有 pom.xml，无法检查依赖"
fi

sec "7. 有一次提交，且 message 只描述变更"
if git rev-parse HEAD >/dev/null 2>&1; then
  SUBJ=$(git log -1 --pretty=%s)
  printf '  最近提交：%s\n' "$SUBJ"
  printf '  提交时间：%s\n' "$(git log -1 --format=%ad --date=iso)"
  if git show --stat --name-only --format="" HEAD | grep -qE '(^|/)pom\.xml|^src/'; then
    ok "最近提交涉及 pom.xml 或 src/"
  else
    no "最近提交没有涉及 pom.xml / src/" "把 .gitignore 修正和骨架放在同一次提交里"
  fi
  if printf '%s' "$SUBJ" | grep -qE '因为|为了|所以|考虑|避免|之前|原因是'; then
    no "commit message 出现动机类措辞" "只描述变更，不解释动机"
  else
    ok "commit message 未出现动机类措辞"
  fi
else
  no "仓库还没有任何提交"
fi

sec "8. 事实对账（本地 vs 远程）"
if git rev-parse --abbrev-ref '@{u}' >/dev/null 2>&1; then
  if [ -z "$(git status --porcelain)" ]; then
    ok "工作区干净"
  else
    no "工作区有未提交改动" "$(git status --porcelain | head -5 | tr '\n' ' ')"
  fi
  LOCAL=$(git rev-parse HEAD)
  REMOTE=$(git rev-parse '@{u}' 2>/dev/null || echo none)
  if [ "$LOCAL" = "$REMOTE" ]; then
    ok "本地与远程一致（$LOCAL）"
  else
    no "本地与远程不一致" "本地 $LOCAL / 远程 $REMOTE → 需要 push"
  fi
else
  no "当前分支没有上游，远程状态未知" "先建立 tracking，或走 REST API 核对"
fi

echo
echo "========================================"
printf " PASS %d ｜ FAIL %d ｜ MANUAL %d\n" "$PASS" "$FAIL" "$MANUAL"
if [ "$FAIL" -eq 0 ] && [ "$MANUAL" -eq 0 ]; then
  echo " 结论：自动项全过。"
elif [ "$FAIL" -eq 0 ]; then
  echo " 结论：自动项全过，仍有 $MANUAL 项需手动确认后 S1 才算完成。"
else
  echo " 结论：未通过。先把 FAIL 项清掉。"
fi
echo "========================================"
