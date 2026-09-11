#!/usr/bin/env bash
#
# 生成 Release 更新日志：把上一个 tag 到当前 tag 之间的提交按 conventional commit
# 前缀分组，输出 Markdown 到标准输出。
#
# 用法：changelog.sh <当前 tag> [上一个 tag 或提交]
#
# 在 GitHub Actions 中运行时读取 GITHUB_SERVER_URL / GITHUB_REPOSITORY 生成链接，
# 本地运行时回退到 origin remote；两者都取不到则只输出纯文本条目。
set -euo pipefail

current="${1:-}"
if [ -z "$current" ]; then
    echo "用法：changelog.sh <当前 tag> [上一个 tag 或提交]" >&2
    exit 2
fi
previous="${2:-}"

server="${GITHUB_SERVER_URL:-https://github.com}"
slug="${GITHUB_REPOSITORY:-}"
if [ -z "$slug" ]; then
    slug="$(git remote get-url origin 2>/dev/null | sed -E 's#^(git@[^:]+:|[a-z]+://[^/]+/)##; s#\.git$##')" || true
fi
repo_url=""
if [ -n "$slug" ]; then
    repo_url="${server}/${slug}"
else
    echo "提示：未识别仓库地址，更新日志不生成超链接。" >&2
fi

# 手动触发 Release 时 tag 还不存在，此时以 HEAD 作为提交区间终点。
if git rev-parse -q --verify "refs/tags/${current}" >/dev/null 2>&1; then
    end="$current"
else
    end="HEAD"
fi

# 默认取区间终点上最新的、且不是本次发布的 tag 作为基线。
if [ -z "$previous" ]; then
    previous="$(git tag --sort=-v:refname --merged "$end" 2>/dev/null | grep -v -F -x -- "$current" | head -n 1 || true)"
fi

if [ -n "$previous" ]; then
    range="${previous}..${end}"
    range_label="${previous} → ${current}"
else
    range="$end"
    range_label="首次发布"
fi

categories="feat|✨ 新增功能
fix|🐛 问题修复
perf|⚡ 性能优化
refactor|♻️ 代码重构
docs|📝 文档
build|📦 构建
ci|🤖 持续集成
test|✅ 测试
style|💄 代码风格
revert|⏪ 回滚"
fallback_title="🔧 其他改动"
fallback_index="$(printf '%s\n' "$categories" | wc -l | tr -d ' ')"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

render_item() { # <短哈希> <完整哈希> <标题>
    local short="$1" full="$2" text="$3"
    if [ -n "$repo_url" ]; then
        text="$(printf '%s' "$text" | sed -E "s~\(#([0-9]+)\)~([#\1](${repo_url}/pull/\1))~g")"
        printf -- '- %s ([`%s`](%s/commit/%s))\n' "$text" "$short" "$repo_url" "$full"
    else
        printf -- '- %s (%s)\n' "$text" "$short"
    fi
}

conventional_re='^([A-Za-z]+)(\(([^)]*)\))?!?:[[:space:]]*(.*)$'

count=0
while IFS=$'\t' read -r short full subject; do
    [ -n "$short" ] || continue
    count=$((count + 1))

    prefix=""
    scope=""
    clean="$subject"
    if [[ "$subject" =~ $conventional_re ]]; then
        prefix="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
        scope="${BASH_REMATCH[3]}"
        clean="${BASH_REMATCH[4]}"
    fi

    index=""
    i=0
    while IFS= read -r line; do
        if [ "${line%%|*}" = "$prefix" ]; then
            index="$i"
            break
        fi
        i=$((i + 1))
    done <<< "$categories"

    if [ -n "$scope" ]; then
        clean="**${scope}**：${clean}"
    fi

    render_item "$short" "$full" "$clean" >> "${tmpdir}/${index:-$fallback_index}"
done <<< "$(git log --no-merges --pretty=format:'%h%x09%H%x09%s' "$range")"

printf '## 📋 更新日志\n\n'
if [ -n "$repo_url" ] && [ -n "$previous" ]; then
    printf '**完整变更**：[%s](%s/compare/%s...%s)\n\n' "$range_label" "$repo_url" "$previous" "$current"
elif [ -n "$previous" ]; then
    printf '**完整变更**：%s\n\n' "$range_label"
else
    printf '**首次发布**：这是本仓库的第一个 Release。\n\n'
fi
if [ "$count" -eq 0 ]; then
    printf '_本次发布没有新的提交。_\n\n'
fi

i=0
while IFS= read -r line; do
    if [ -s "${tmpdir}/${i}" ]; then
        printf '### %s\n\n' "${line#*|}"
        cat "${tmpdir}/${i}"
        printf '\n'
    fi
    i=$((i + 1))
done <<< "$categories"

if [ -s "${tmpdir}/${fallback_index}" ]; then
    printf '### %s\n\n' "$fallback_title"
    cat "${tmpdir}/${fallback_index}"
    printf '\n'
fi
