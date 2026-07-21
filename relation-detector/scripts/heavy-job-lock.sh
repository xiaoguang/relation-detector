#!/usr/bin/env bash

# CN: 为发布、correctness 与 sample-data 入口提供同一进程级互斥原语。
# 不完整的 owner 元数据按占用处理，避免并发创建者被误判为 stale。
# EN: Provides one process-level mutex for release, correctness, and sample-data
# entry points. Incomplete owner metadata fails closed so a concurrent creator is
# never mistaken for a stale lock.

HEAVY_JOB_LOCK_OWNED=false
HEAVY_JOB_LOCK_BORROWED=false
HEAVY_JOB_LOCK_PATH=""
HEAVY_JOB_LOCK_CURRENT_TOKEN=""

_heavy_job_lock_read() {
  local file="$1"
  [[ -r "$file" ]] || return 1
  cat "$file"
}

_heavy_job_lock_write_metadata() {
  local lock_dir="$1"
  local job="$2"
  local token="$3"
  local pid_tmp="$lock_dir/.pid.$$"
  local job_tmp="$lock_dir/.job.$$"
  local token_tmp="$lock_dir/.token.$$"

  if ! printf '%s\n' "$$" >"$pid_tmp" \
      || ! mv "$pid_tmp" "$lock_dir/pid" \
      || ! printf '%s\n' "$job" >"$job_tmp" \
      || ! mv "$job_tmp" "$lock_dir/job" \
      || ! printf '%s\n' "$token" >"$token_tmp" \
      || ! mv "$token_tmp" "$lock_dir/token"; then
    rm -f "$pid_tmp" "$job_tmp" "$token_tmp" \
      "$lock_dir/pid" "$lock_dir/job" "$lock_dir/token"
    rmdir "$lock_dir" 2>/dev/null || true
    echo "failed to publish relation-detector heavy-job lock ownership" >&2
    return 1
  fi
}

_heavy_job_lock_publish_owner() {
  local lock_dir="$1"
  local job="$2"
  local token="$$-$(date +%s)-${RANDOM:-0}"

  HEAVY_JOB_LOCK_OWNED=true
  HEAVY_JOB_LOCK_BORROWED=false
  HEAVY_JOB_LOCK_PATH="$lock_dir"
  HEAVY_JOB_LOCK_CURRENT_TOKEN="$token"
  if ! _heavy_job_lock_write_metadata "$lock_dir" "$job" "$token"; then
    HEAVY_JOB_LOCK_OWNED=false
    HEAVY_JOB_LOCK_PATH=""
    HEAVY_JOB_LOCK_CURRENT_TOKEN=""
    return 1
  fi

  RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$lock_dir"
  RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN="$token"
  export RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR
  export RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN
}

_heavy_job_lock_validate_borrower() {
  local lock_dir="$1"
  local inherited_dir="${RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR:-}"
  local inherited_token="${RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN:-}"
  local owner_pid=""
  local owner_token=""

  if [[ -z "$inherited_dir" || "$inherited_dir" != "$lock_dir" ]]; then
    echo "inherited relation-detector heavy-job lock path does not match the requested lock" >&2
    return 1
  fi
  owner_pid="$(_heavy_job_lock_read "$lock_dir/pid" 2>/dev/null || true)"
  owner_token="$(_heavy_job_lock_read "$lock_dir/token" 2>/dev/null || true)"
  if ! [[ "$owner_pid" =~ ^[0-9]+$ ]] \
      || [[ -z "$owner_token" ]] \
      || [[ "$owner_token" != "$inherited_token" ]] \
      || ! kill -0 "$owner_pid" 2>/dev/null; then
    echo "inherited relation-detector heavy-job lock ownership is invalid" >&2
    return 1
  fi

  HEAVY_JOB_LOCK_OWNED=false
  HEAVY_JOB_LOCK_BORROWED=true
  HEAVY_JOB_LOCK_PATH="$lock_dir"
  HEAVY_JOB_LOCK_CURRENT_TOKEN="$inherited_token"
}

_heavy_job_lock_remove_quarantine() {
  local quarantine="$1"
  rm -f "$quarantine/pid" "$quarantine/job" "$quarantine/token" \
    "$quarantine"/.pid.* "$quarantine"/.job.* "$quarantine"/.token.*
  if ! rmdir "$quarantine" 2>/dev/null; then
    echo "stale relation-detector heavy-job lock contains unexpected files: $quarantine" >&2
    return 1
  fi
}

heavy_job_lock_acquire() {
  local lock_dir="$1"
  local job="$2"
  local owner_pid=""
  local owner_job=""
  local owner_token=""
  local quarantine=""

  if [[ -n "${RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN:-}" ]]; then
    _heavy_job_lock_validate_borrower "$lock_dir"
    return
  fi

  mkdir -p "$(dirname "$lock_dir")"
  if mkdir "$lock_dir" 2>/dev/null; then
    _heavy_job_lock_publish_owner "$lock_dir" "$job"
    return
  fi

  owner_pid="$(_heavy_job_lock_read "$lock_dir/pid" 2>/dev/null || true)"
  owner_job="$(_heavy_job_lock_read "$lock_dir/job" 2>/dev/null || true)"
  owner_token="$(_heavy_job_lock_read "$lock_dir/token" 2>/dev/null || true)"
  if ! [[ "$owner_pid" =~ ^[0-9]+$ ]] || [[ -z "$owner_job" ]] || [[ -z "$owner_token" ]]; then
    echo "relation-detector heavy-job lock ownership metadata is incomplete; manual cleanup required" >&2
    return 1
  fi
  if kill -0 "$owner_pid" 2>/dev/null; then
    echo "relation-detector heavy job is already running: job=$owner_job pid=$owner_pid" >&2
    return 1
  fi

  quarantine="$lock_dir.stale.$$-${RANDOM:-0}"
  if [[ -e "$quarantine" ]] || ! mv "$lock_dir" "$quarantine" 2>/dev/null; then
    echo "relation-detector heavy-job lock changed during stale recovery" >&2
    return 1
  fi
  if ! _heavy_job_lock_remove_quarantine "$quarantine"; then
    return 1
  fi
  if ! mkdir "$lock_dir" 2>/dev/null; then
    echo "relation-detector heavy job was acquired during stale recovery" >&2
    return 1
  fi
  _heavy_job_lock_publish_owner "$lock_dir" "$job"
}

heavy_job_lock_release() {
  local current_token=""

  if [[ "$HEAVY_JOB_LOCK_BORROWED" == true ]]; then
    return 0
  fi
  [[ "$HEAVY_JOB_LOCK_OWNED" == true ]] || return 0

  current_token="$(_heavy_job_lock_read "$HEAVY_JOB_LOCK_PATH/token" 2>/dev/null || true)"
  if [[ -z "$current_token" || "$current_token" != "$HEAVY_JOB_LOCK_CURRENT_TOKEN" ]]; then
    echo "relation-detector heavy-job lock token changed before release" >&2
    return 1
  fi

  rm -f "$HEAVY_JOB_LOCK_PATH/token" "$HEAVY_JOB_LOCK_PATH/job" "$HEAVY_JOB_LOCK_PATH/pid"
  if ! rmdir "$HEAVY_JOB_LOCK_PATH" 2>/dev/null; then
    echo "relation-detector heavy-job lock could not be removed cleanly" >&2
    return 1
  fi

  HEAVY_JOB_LOCK_OWNED=false
  HEAVY_JOB_LOCK_PATH=""
  HEAVY_JOB_LOCK_CURRENT_TOKEN=""
  unset RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN
}
