/**
 * 意图理解引擎
 *
 * 流程：
 *   1. 文本标准化 + ASR 纠错
 *   2. 模糊匹配（intentAliases → similarity）
 *   3. 精确正则匹配（兜底）
 *   4. AI_CHAT（最终兜底）
 *
 * 只有 AI_CHAT 类型才交给 DeepSeek
 */

import type { AssistantContext, UserIntent } from './types'
import {
  matchIntentByFuzzy,
  runSpeechPipeline,
} from './speechFuzzy'

// ── 调试信息（供 UI 面板读取） ──
export let lastPipelineResult: ReturnType<typeof runSpeechPipeline> | null = null
export let lastAiFallback = false

export function getLastPipelineDebug() {
  return lastPipelineResult
    ? {
        rawText: lastPipelineResult.rawText,
        normalized: lastPipelineResult.normalized,
        corrected: lastPipelineResult.corrected,
        intent: lastPipelineResult.fuzzy.intent,
        action: lastPipelineResult.fuzzy.action,
        matchedAlias: lastPipelineResult.fuzzy.matchedAlias || '(无匹配)',
        score: Math.round(lastPipelineResult.fuzzy.score * 100),
        confidence: lastPipelineResult.confidence,
        needConfirm: lastPipelineResult.needConfirm,
        aiFallback: lastAiFallback,
      }
    : null
}

// ══════════════════════════════════════════════════════
// 主入口
// ══════════════════════════════════════════════════════
export function parseIntent(rawText: string, _context: AssistantContext): UserIntent {
  // ── Step 1: 流水线预处理 ──
  const pipeline = runSpeechPipeline(rawText)
  lastPipelineResult = pipeline
  lastAiFallback = false

  const { corrected, fuzzy, confidence } = pipeline

  // ── 优先级最高：关闭/撤防关键词 → DISARM（避免 ASR 误识别为开启）──
  const disarmPriority = /(布防|防护|警戒)/.test(corrected) && /(关闭|退出|撤销|撤防|解除|停止|取消)/.test(corrected)
  if (disarmPriority) {
    return { type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true, confidence: 0.98, rawText }
  }

  // ── Step 2: 模糊匹配 ──
  if (fuzzy.intent !== 'AI_CHAT' && fuzzy.score > 0) {
    // HIGH confidence → 直接执行
    if (confidence === 'HIGH') {
      return buildIntentFromFuzzy(fuzzy, rawText, fuzzy.score)
    }

    // MEDIUM confidence → 追问确认（非敏感操作）
    if (confidence === 'MEDIUM' && !fuzzy.sensitivity) {
      return buildIntentFromFuzzy(fuzzy, rawText, fuzzy.score, true)
    }
  }

  // ── Step 3: 精确正则匹配（兜底老逻辑） ──
  const regexIntent = parseIntentRegex(corrected, rawText)
  if (regexIntent.type !== 'AI_CHAT') {
    return regexIntent
  }

  // 如果模糊匹配有结果但置信度低，仍然尝试用
  if (fuzzy.intent !== 'AI_CHAT' && fuzzy.score >= 0.40) {
    return buildIntentFromFuzzy(fuzzy, rawText, fuzzy.score, true)
  }

  // ── Step 4: 最终兜底 → AI_CHAT ──
  return { type: 'AI_CHAT', action: 'AI_CHAT', confidence: 0.5, rawText }
}

// ══════════════════════════════════════════════════════
// 精确正则匹配（保留原有逻辑作兜底）
// ══════════════════════════════════════════════════════
function parseIntentRegex(c: string, rawText: string): UserIntent {
  // === PRIORITY 1: STOP SPEAKING ===
  if (/停止播报|别说了|停一下|安静|不用说了|打断|停止|别播了|闭嘴/.test(c)) {
    return { type: 'STOP_SPEAKING', action: 'STOP_SPEAKING', confidence: 1, rawText }
  }

  // === PRIORITY 2: CONFIRM / CANCEL (pendingAction resolution) ===
  if (/^(确认执行|确认|确定|执行吧|可以|好的|行|对|是|嗯嗯|没错)$/.test(c) ||
      /^确认/.test(c) && c.length <= 4) {
    return { type: 'CONFIRM_ACTION', action: 'CONFIRM', confidence: 0.95, rawText }
  }
  if (/^(取消|算了|不用了|不要|不了|别了|不执行)$/.test(c)) {
    return { type: 'CANCEL_ACTION', action: 'CANCEL', confidence: 0.95, rawText }
  }

  // === PRIORITY 3: SENSITIVE ACTIONS — 必须在 PAGE SWITCH 之前 ===
  // 先检测撤防（关闭/退出 + 布防），再检测布防（避免 ASR 把"关闭"听成"开启"）
  if (/(布防|防护模式|警戒模式|设防|防护|警戒)/.test(c) &&
      /(关闭|退出|撤销|撤防|解除|停止|取消)/.test(c)) {
    return { type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true, confidence: 0.95, rawText }
  }

  // ARM_DEVICE: 布防/防护模式/警戒模式
  if (/(布防|防护模式|警戒模式|设防)/.test(c) &&
      /(打开|开启|启动|进入|帮我|我要|打开|启动|开始|设)/.test(c) &&
      !/(关闭|退出|撤销|撤防|解除|停止|取消)/.test(c)) {
    return { type: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true, confidence: 0.95, rawText }
  }

  // DISARM_DEVICE: 撤防/关闭布防/退出防护
  if (/(布防|防护模式|警戒模式|设防)/.test(c) &&
      /(关闭|退出|撤销|撤防|解除|停止|取消)/.test(c)) {
    return { type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true, confidence: 0.95, rawText }
  }
  if (/^撤防$|撤销布防/.test(c)) {
    return { type: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true, confidence: 0.95, rawText }
  }

  // SEND_HELP
  if (/发送求助|紧急求助|帮我求助|有危险|帮我报警|一键报警|报警/.test(c))
    return { type: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true, confidence: 0.95, rawText }

  // SEND_MESSAGE
  if (/发送消息|发消息|发送信息|发信息|发送通知/.test(c))
    return { type: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true, confidence: 0.95, rawText }

  // HANDLE_ALARM
  if (/处理告警|标记.*处理/.test(c))
    return { type: 'SENSITIVE_ACTION', action: 'HANDLE_ALARM', sensitivity: true, confidence: 0.9, rawText }

  // CLEAR_CHAT
  if (/清空.*对话|清除.*记录/.test(c))
    return { type: 'SENSITIVE_ACTION', action: 'CLEAR_CHAT', sensitivity: true, confidence: 0.9, rawText }

  // === PRIORITY 4: PAGE SWITCH (only explicit open/navigate, AFTER sensitive checks) ===
  if (/打开|进入|切换|帮我打开|带我去/.test(c)) {
    if (/首页|总控台/.test(c))
      return { type: 'OPEN_MODULE', module: 'dashboard', action: 'OPEN_DASHBOARD', confidence: 0.95, rawText }
    if (/实时监测|实时监控|监控|摄像头/.test(c))
      return { type: 'OPEN_MODULE', module: 'monitor', action: 'OPEN_MONITOR', confidence: 0.95, rawText }
    if (/pir|PIR|人体感应|红外感应/.test(c))
      return { type: 'OPEN_MODULE', module: 'pir', action: 'OPEN_PIR', confidence: 0.95, rawText }
    if (/设备管理/.test(c))
      return { type: 'OPEN_MODULE', module: 'device', action: 'OPEN_DEVICE', confidence: 0.95, rawText }
    if (/告警管理|告警页面/.test(c))
      return { type: 'OPEN_MODULE', module: 'alarm', action: 'OPEN_ALARM', confidence: 0.95, rawText }
    if (/消息中心|紧急求助|求助页面|一键报警/.test(c))
      return { type: 'OPEN_MODULE', module: 'sos', action: 'OPEN_SOS', confidence: 0.95, rawText }
    // 不匹配任何页面 → 不是 OPEN_MODULE
    return { type: 'AI_CHAT', action: 'AI_CHAT', confidence: 0.5, rawText }
  }

  // === CONTROL ACTIONS ===
  // 监测启动/停止 — 必须放在 OPEN_CAMERA 前面（"打开摄像头...监测"优先匹配监测）
  if (/打开.*监测|开启.*监测|开始.*监测|启动.*监测/.test(c) && !/停止|关闭/.test(c))
    return { type: 'CONTROL_ACTION', action: 'START_LIVE_MONITOR', module: 'monitor', confidence: 0.9, rawText }
  if (/停止.*监测|关闭.*监测/.test(c))
    return { type: 'CONTROL_ACTION', action: 'STOP_LIVE_MONITOR', module: 'monitor', confidence: 0.9, rawText }
  if (/打开摄像头|开启摄像头/.test(c))
    return { type: 'CONTROL_ACTION', action: 'OPEN_CAMERA', module: 'monitor', confidence: 0.9, rawText }
  if (/关闭摄像头/.test(c))
    return { type: 'CONTROL_ACTION', action: 'CLOSE_CAMERA', module: 'monitor', confidence: 0.9, rawText }
  if (/人脸识别.*一下|识别.*人脸|人脸.*识别/.test(c))
    return { type: 'CONTROL_ACTION', action: 'FACE_RECOGNIZE_ONCE', module: 'monitor', confidence: 0.9, rawText }
  if (/人员管理|管理.*人员|注册.*人脸/.test(c))
    return { type: 'CONTROL_ACTION', action: 'OPEN_PERSON_MANAGER', module: 'monitor', confidence: 0.85, rawText }
  if (/刷新/.test(c))
    return { type: 'CONTROL_ACTION', action: 'REFRESH_CURRENT', confidence: 0.85, rawText }

  // === QUERY DATA ===
  if (/我现在安全吗|安防状态|安全.*状态|有没有.*危险|有没有.*异常|安全.*情况|安全.*评估|现在安不安全|安全吗/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS', confidence: 0.9, rawText }
  if (/今天.*人|人.*经过|人体感应|pir.*触发|pir.*记录|人体.*检测|红外.*感应/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_PIR_TODAY', confidence: 0.9, rawText }
  if (/设备.*在线|设备.*状态|哪些.*设备|设备.*离线|在线.*设备|设备.*情况|设备.*怎么样/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS', confidence: 0.9, rawText }

  // ── 告警时间维度查询 ──
  // 今天/今日/当天/本日 + 告警/警告/报警/异常
  if (/(今天|今日|当天|本日).*(告警|警告|报警|预警|异常|安全事件)/.test(c) ||
      /(告警|警告|报警|预警).*(今天|今日|当天|本日)/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY', confidence: 0.9, rawText }

  // 本周/一周/最近一周/近七天/这个星期 + 告警/警告/报警/异常
  if (/(一周|本周|最近一周|近七天|近7天|这个星期|最近7天|七天内).*(告警|警告|报警|预警|异常|情况)/.test(c) ||
      /(告警|警告|报警|预警).*(一周|本周|最近一周|近七天|近7天)/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK', confidence: 0.9, rawText }

  // 未处理/没处理/待处理 + 告警/警告/报警
  if (/(未处理|没处理|待处理|还没处理).*(告警|警告|报警|预警)/.test(c) ||
      /(告警|警告|报警).*(未处理|没处理|待处理)/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS', confidence: 0.9, rawText }

  // 最近一条/最新/最后一条 + 告警/警告/报警
  if (/(最近一条|最新|最后一条|最近一次|最近的|读一下最新).*(告警|警告|报警|预警)/.test(c) ||
      /(朗读|读一下|读).*(最新|最近|最后).*(告警|警告|报警)/.test(c))
    return { type: 'QUERY_DATA', action: 'READ_LATEST_ALARM', confidence: 0.9, rawText }

  // 多少/几条/数量 + 告警/警告/报警
  if (/(多少|几条|几个|数量|总数|一共有|有几个).*(告警|警告|报警|预警)/.test(c) ||
      /(告警|警告|报警).*(多少|几条|几个|数量|总数)/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT', confidence: 0.9, rawText }

  // 通用告警查询（兜底）
  if (/未处理.*告警|告警.*未|有没有.*告警|有没有.*报警|有没有.*警告|最近.*告警|最新.*告警|告警.*列表|查看.*告警|朗读.*告警|读.*告警|告警|警告|报警|预警/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_ALARMS', confidence: 0.9, rawText }

  if (/最近.*告警.*朗读|朗读.*告警|读.*告警/.test(c))
    return { type: 'QUERY_DATA', action: 'READ_LATEST_ALARM', confidence: 0.85, rawText }
  if (/已注册.*人员|注册.*人员.*有哪些|人员.*列表/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS', confidence: 0.85, rawText }
  if (/布防.*状态|哪些.*布防/.test(c))
    return { type: 'QUERY_DATA', action: 'QUERY_DEFENSE', confidence: 0.85, rawText }

  // === DEFAULT ===
  return { type: 'AI_CHAT', action: 'AI_CHAT', confidence: 0.7, rawText }
}

// ══════════════════════════════════════════════════════
// 从 FuzzyMatchResult 构建 UserIntent
// ══════════════════════════════════════════════════════
function buildIntentFromFuzzy(
  fuzzy: ReturnType<typeof matchIntentByFuzzy>,
  rawText: string,
  confidence: number,
  needConfirm = false,
): UserIntent {
  return {
    type: fuzzy.intent as UserIntent['type'],
    action: fuzzy.action,
    module: fuzzy.module,
    sensitivity: fuzzy.sensitivity,
    confidence,
    rawText,
    needConfirm,
  }
}
