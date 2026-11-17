/**
 * 语音容错理解层
 *
 * 流程：
 *   rawText → normalizeSpeechText → correctCommonAsrErrors → matchIntentByFuzzy → intent
 *
 * 分层信心：
 *   HIGH (>=0.72)  → 直接执行
 *   MEDIUM (>=0.55) → 追问确认
 *   LOW (<0.55)    → DeepSeek 意图分类兜底 / 本地提示
 */

// ══════════════════════════════════════════════════════
// 一、文本标准化
// ══════════════════════════════════════════════════════
export function normalizeSpeechText(text: string): string {
  return (text || '')
    .toLowerCase()
    .replace(/\s+/g, '')
    .replace(/[，,。.!！?？；;：:、~～'"]/g, '')
    .replace(/^(额|嗯|啊|呃|那个|就是|请问|小安啊|小安呀|小安小安|你好小安)/g, '')
    .replace(/[?？!！~～]$/g, '')
    .trim()
}

// ══════════════════════════════════════════════════════
// 二、常见 ASR 错词纠正
// ══════════════════════════════════════════════════════

const asrCorrectionMap: [string, string][] = [
  // ── 设备相关 ──
  ['设别', '设备'],
  ['社备', '设备'],
  ['舍备', '设备'],
  ['摄备', '设备'],
  ['设备壮态', '设备状态'],
  ['设备状况', '设备状态'],
  ['设备情况', '设备状态'],
  ['设备妆态', '设备状态'],

  // ── 实时监测 / 监控 ──
  ['实事监测', '实时监测'],
  ['时实监测', '实时监测'],
  ['实时检测', '实时监测'],
  ['实时监控', '实时监测'],
  ['实施监测', '实时监测'],
  ['事实监测', '实时监测'],
  ['时时监测', '实时监测'],
  ['打开监控', '打开实时监测'],
  ['打开摄像', '打开摄像头'],

  // ── 告警 / 报警 ──
  ['报警', '告警'],
  ['警报', '告警'],
  ['警告', '告警'],
  ['预警', '告警'],
  ['报敬', '告警'],
  ['告敬', '告警'],
  ['异常事件', '告警异常'],
  ['安全事件', '告警'],
  ['看一下告警', '查看告警'],
  ['看下告警', '查看告警'],
  ['看一下警告', '查看告警'],
  ['看一下报警', '查看告警'],
  ['有没有报警', '有没有告警'],
  ['有没有警告', '有没有告警'],
  ['有没告警', '有没有告警'],
  ['警告有多少', '告警有多少'],
  ['报警有多少', '告警有多少'],
  ['警告多不多', '告警多不多'],
  ['报警多不多', '告警多不多'],

  // ── PIR 相关 ──
  ['派ir', 'pir'],
  ['派IR', 'pir'],
  ['派艾儿', 'pir'],
  ['pi r', 'pir'],
  ['p i r', 'pir'],
  ['pirr', 'pir'],
  ['per', 'pir'],
  ['人体检测', '人体感应'],
  ['红外感应', 'pir'],
  ['人体传感', 'pir'],
  ['红外检测', 'pir'],
  ['热释电', '人体感应'],

  // ── 人经过 ──
  ['人路过', '人经过'],
  ['有人进来', '有人经过'],
  ['有人进入', '有人经过'],
  ['有人来过', '有人经过'],
  ['有没有人来', '有没有人经过'],
  ['有人来过吗', '有没有人经过'],
  ['有人来过么', '有没有人经过'],

  // ── 安全相关 ──
  ['安不安全', '安全吗'],
  ['安全嘛', '安全吗'],
  ['安全么', '安全吗'],
  ['安全法', '安全吗'],
  ['安全码', '安全吗'],
  ['安全部', '安全吗'],

  // ── 唤醒词纠错 ──
  ['小岸', '小安'],
  ['小按', '小安'],
  ['小暗', '小安'],
  ['小安安', '小安'],
  ['小安同学', '小安'],

  // ── 其他 ──
  ['有没', '有没有'],
  ['壮态', '状态'],
  ['妆态', '状态'],
  ['状太', '状态'],
]

export function correctCommonAsrErrors(text: string): string {
  let result = text
  for (const [wrong, right] of asrCorrectionMap) {
    if (result.includes(wrong)) {
      result = result.replaceAll(wrong, right)
    }
  }
  return result
}

// ══════════════════════════════════════════════════════
// 三、意图同义别名表
// ══════════════════════════════════════════════════════

export interface IntentAliasEntry {
  intent: string
  action: string
  module?: string
  sensitivity?: boolean
}

export const intentAliases: Record<string, IntentAliasEntry> = {
  // ── OPEN_MODULE ──
  '打开实时监测':    { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '进入实时监测':    { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '打开监控':        { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '进入监控':        { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '打开摄像头页面':  { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '切换到实时监测':  { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '打开实时监控':    { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  // 连续对话中省略动词的短说法（语音高频）
  '实时监测':        { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '实时监控':        { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '监控':            { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },
  '摄像头':          { intent: 'OPEN_MODULE', action: 'OPEN_MONITOR', module: 'monitor' },

  '打开pir':        { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '进入pir':        { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '打开人体感应':    { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '进入人体感应':    { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '打开红外感应':    { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  // 连续对话短说法
  'pir':            { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '人体感应':        { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },
  '红外感应':        { intent: 'OPEN_MODULE', action: 'OPEN_PIR', module: 'pir' },

  '打开设备管理':    { intent: 'OPEN_MODULE', action: 'OPEN_DEVICE', module: 'device' },
  '进入设备管理':    { intent: 'OPEN_MODULE', action: 'OPEN_DEVICE', module: 'device' },
  '切换到设备管理':  { intent: 'OPEN_MODULE', action: 'OPEN_DEVICE', module: 'device' },
  '设备管理':        { intent: 'OPEN_MODULE', action: 'OPEN_DEVICE', module: 'device' },

  '打开告警管理':    { intent: 'OPEN_MODULE', action: 'OPEN_ALARM', module: 'alarm' },
  '进入告警管理':    { intent: 'OPEN_MODULE', action: 'OPEN_ALARM', module: 'alarm' },
  '切换到告警管理':  { intent: 'OPEN_MODULE', action: 'OPEN_ALARM', module: 'alarm' },

  '打开首页':        { intent: 'OPEN_MODULE', action: 'OPEN_DASHBOARD', module: 'dashboard' },
  '回到首页':        { intent: 'OPEN_MODULE', action: 'OPEN_DASHBOARD', module: 'dashboard' },
  '进入首页':        { intent: 'OPEN_MODULE', action: 'OPEN_DASHBOARD', module: 'dashboard' },
  '打开总控台':      { intent: 'OPEN_MODULE', action: 'OPEN_DASHBOARD', module: 'dashboard' },
  '首页':            { intent: 'OPEN_MODULE', action: 'OPEN_DASHBOARD', module: 'dashboard' },

  '打开一键报警':    { intent: 'OPEN_MODULE', action: 'OPEN_SOS', module: 'sos' },
  '一键报警':        { intent: 'OPEN_MODULE', action: 'OPEN_SOS', module: 'sos' },
  '紧急求助':        { intent: 'OPEN_MODULE', action: 'OPEN_SOS', module: 'sos' },
  '发送求助':        { intent: 'OPEN_MODULE', action: 'OPEN_SOS', module: 'sos' },

  // ── QUERY_DEVICE_STATUS ──
  '设备状态':        { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '查看设备状态':    { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '设备情况':        { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '设备怎么样':      { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '有哪些设备在线':  { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '有没有离线设备':  { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '设备是否正常':    { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '设备在线情况':    { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },
  '设备运转情况':    { intent: 'QUERY_DATA', action: 'QUERY_DEVICE_STATUS' },

  // ── QUERY_ALARMS ──
  '查看告警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '有没有告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '有没有报警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },  // corrected
  '有没有警告':      { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '告警情况':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '报警情况':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '警告情况':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '最近告警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '朗读告警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '读一下告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '有什么告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  // 连续对话短说法
  '告警':            { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '报警':            { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '警告':            { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '查看报警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },
  '查看警告':        { intent: 'QUERY_DATA', action: 'QUERY_ALARMS' },

  // ── QUERY_ALARM_TODAY (今天 + 告警/警告/报警 + 数量) ──
  '今天有多少告警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天多少告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今日告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天有多少警告':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天多少警告':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天警告':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天有多少报警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天报警数量':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今日报警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天告警情况':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天报警情况':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '今天警告情况':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '当天告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },
  '本日告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_TODAY' },

  // ── QUERY_ALARM_WEEK (一周/本周 + 告警/警告/报警) ──
  '本周告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '这一周告警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '最近一周告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '近七天告警':        { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '最近七天告警':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '一周告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '这周告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '本周有多少告警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '一周有多少告警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '最近七天有多少告警': { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '这周多少条告警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '本周警告':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '本周报警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '最近一周警告':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },
  '近7天告警':         { intent: 'QUERY_DATA', action: 'QUERY_ALARM_WEEK' },

  // ── QUERY_ALARM_COUNT (数量关键词 + 告警) ──
  '告警有多少条':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '告警有多少':        { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '几条告警':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '告警数量':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '警告有多少条':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '报警有多少条':      { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '一共有多少告警':    { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },
  '告警总数':          { intent: 'QUERY_DATA', action: 'QUERY_ALARM_COUNT' },

  // ── QUERY_UNHANDLED_ALARMS ──
  '未处理告警':        { intent: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS' },
  '有没有未处理告警':  { intent: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS' },
  '还有多少警告没处理': { intent: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS' },
  '还有告警没处理':    { intent: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS' },
  '未处理警告':        { intent: 'QUERY_DATA', action: 'QUERY_UNHANDLED_ALARMS' },

  // ── READ_LATEST_ALARM ──
  '最近一条告警':      { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '最新告警':          { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '最新警告':          { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '最后一条告警':      { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '最近一次告警':      { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '读一下最新告警':    { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '最近一次警告':      { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },
  '读一下最新警告':    { intent: 'QUERY_DATA', action: 'READ_LATEST_ALARM' },

  // ── QUERY_PIR_TODAY ──
  '今天有没有人经过':    { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '有没有人经过':        { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '今天有人吗':          { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '有没有人进来':        { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '人体感应情况':        { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  'pir情况':             { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  'pir状态':             { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '红外感应情况':        { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '今天有人来过吗':      { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '今天检测到几次':      { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  '几次人体感应':        { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },
  'pir触发了多少次':     { intent: 'QUERY_DATA', action: 'QUERY_PIR_TODAY' },

  // ── QUERY_SECURITY_STATUS ──
  '我现在安全吗':        { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '当前安全吗':          { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '家里安全吗':          { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '安防状态':            { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '当前安防状态':        { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '有没有异常':          { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '现在有没有危险':      { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '家里有没有异常':      { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },
  '现在安不安全':        { intent: 'QUERY_DATA', action: 'QUERY_SECURITY_STATUS' },

  // ── QUERY_CONTACTS ──
  '查看联系人':          { intent: 'QUERY_DATA', action: 'QUERY_CONTACTS' },
  '联系人':              { intent: 'QUERY_DATA', action: 'QUERY_CONTACTS' },
  '紧急联系人':          { intent: 'QUERY_DATA', action: 'QUERY_CONTACTS' },
  '我有几个联系人':      { intent: 'QUERY_DATA', action: 'QUERY_CONTACTS' },
  '我的联系人':          { intent: 'QUERY_DATA', action: 'QUERY_CONTACTS' },

  // ── QUERY_REGISTERED_PERSONS ──
  '查看已注册人员':      { intent: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS' },
  '已注册人员':          { intent: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS' },
  '有哪些注册人员':      { intent: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS' },
  '注册了哪些人':        { intent: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS' },
  '人员列表':            { intent: 'QUERY_DATA', action: 'QUERY_REGISTERED_PERSONS' },

  // ── SEND_HELP ──
  '发送求助':            { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  '帮我求助':            { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  '我需要帮助':          { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  '有危险':              { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  '紧急求助':            { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  '帮我报警':            { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },
  // 连续对话短说法（语音常用，需要二次确认）
  '求助':                { intent: 'SENSITIVE_ACTION', action: 'SEND_HELP', sensitivity: true },

  // ── SEND_MESSAGE ──
  '发送消息':            { intent: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true },
  '发消息':              { intent: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true },
  '发送信息':            { intent: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true },
  '发信息':              { intent: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true },
  '发送通知':            { intent: 'SENSITIVE_ACTION', action: 'SEND_MESSAGE', sensitivity: true },

  // ── STOP_SPEAKING ──
  '停止播报':            { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '别说了':              { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '停一下':              { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '不要说了':            { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '安静':                { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '停止说话':            { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '别播了':              { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },
  '闭嘴':                 { intent: 'STOP_SPEAKING', action: 'STOP_SPEAKING' },

  // ── OTHER ──
  '开启布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '打开布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '进入布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '启动布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '帮我布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '我要布防':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '帮我开启布防':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '帮我打开布防':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '开启布防模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '打开布防模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '进入布防模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '启动布防模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '帮我打开布防模式':    { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '帮我开启布防模式':    { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '进入防护模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '打开防护模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '开启防护模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '启动防护模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '打开警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '开启警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '进入警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '启动警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  // 连续对话短说法
  '布防':                { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },
  '防护模式':            { intent: 'SENSITIVE_ACTION', action: 'ARM_DEVICE', sensitivity: true },

  '撤防':                { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '撤销布防':            { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '关闭布防':            { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '退出布防':            { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '关闭布防模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '退出布防模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '撤销布防模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '关闭防护模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '退出防护模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '关闭警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '退出警戒模式':        { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '解除布防':            { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '解除防护':            { intent: 'SENSITIVE_ACTION', action: 'DISARM_DEVICE', sensitivity: true },
  '打开摄像头':          { intent: 'CONTROL_ACTION', action: 'OPEN_CAMERA', module: 'monitor' },
  '关闭摄像头':          { intent: 'CONTROL_ACTION', action: 'CLOSE_CAMERA', module: 'monitor' },
  '打开人员管理':        { intent: 'CONTROL_ACTION', action: 'OPEN_PERSON_MANAGER', module: 'monitor' },
  '人员管理':            { intent: 'CONTROL_ACTION', action: 'OPEN_PERSON_MANAGER', module: 'monitor' },
  '刷新':                { intent: 'CONTROL_ACTION', action: 'REFRESH_CURRENT' },
}

// 从 alias 表中提取 alias 列表供匹配使用
export const allAliases = Object.keys(intentAliases)

// ══════════════════════════════════════════════════════
// 四、相似度计算
// ══════════════════════════════════════════════════════

/**
 * 计算两个字符串的相似度 (0~1)
 * 先做精确匹配和包含匹配，再做字符级 Jaccard
 */
export function similarity(a: string, b: string): number {
  if (!a || !b) return 0
  if (a === b) return 1
  if (a.includes(b) || b.includes(a)) {
    // 包含关系：按长度比加成
    const ratio = Math.min(a.length, b.length) / Math.max(a.length, b.length)
    return 0.7 + ratio * 0.25
  }

  // 字符集 Jaccard（双向加权防短文本误匹配）
  const setA = new Set(a.split(''))
  const setB = new Set(b.split(''))
  let intersection = 0
  setA.forEach(ch => { if (setB.has(ch)) intersection++ })
  const union = setA.size + setB.size - intersection
  if (union === 0) return 0

  const jaccard = intersection / union

  // 额外加权：连续 2-gram 重叠
  const bigramsA = new Set<string>()
  for (let i = 0; i < a.length - 1; i++) bigramsA.add(a.slice(i, i + 2))
  const bigramsB = new Set<string>()
  for (let i = 0; i < b.length - 1; i++) bigramsB.add(b.slice(i, i + 2))
  let biInter = 0
  bigramsA.forEach(bg => { if (bigramsB.has(bg)) biInter++ })
  const biUnion = bigramsA.size + bigramsB.size - biInter
  const bigramScore = biUnion > 0 ? biInter / biUnion : 0

  // 综合得分（Jaccard 60% + bigram 40%）
  return jaccard * 0.6 + bigramScore * 0.4
}

// ══════════════════════════════════════════════════════
// 五、模糊意图匹配
// ══════════════════════════════════════════════════════

export interface FuzzyMatchResult {
  intent: string
  action: string
  module?: string
  sensitivity?: boolean
  score: number
  matchedAlias: string
  /** 是否通过模糊匹配 */
  fuzzy: boolean
}

export function matchIntentByFuzzy(text: string): FuzzyMatchResult {
  let best: FuzzyMatchResult = {
    intent: 'AI_CHAT',
    action: 'AI_CHAT',
    score: 0,
    matchedAlias: '',
    fuzzy: false,
  }

  for (const [alias, entry] of Object.entries(intentAliases)) {
    const normalizedAlias = normalizeSpeechText(alias)
    const score = similarity(text, normalizedAlias)
    if (score > best.score) {
      best = {
        intent: entry.intent,
        action: entry.action,
        module: entry.module,
        sensitivity: entry.sensitivity,
        score,
        matchedAlias: alias,
        fuzzy: score < 1.0, // 只有不完全等于时才标记 fuzzy
      }
    }
  }

  return best
}

// ══════════════════════════════════════════════════════
// 六、置信度判断
// ══════════════════════════════════════════════════════

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export function getConfidenceLevel(score: number): ConfidenceLevel {
  if (score >= 0.72) return 'HIGH'
  if (score >= 0.55) return 'MEDIUM'
  return 'LOW'
}

// ══════════════════════════════════════════════════════
// 七、完整流水线：rawText → FuzzyMatchResult
// ══════════════════════════════════════════════════════

export interface SpeechPipelineResult {
  rawText: string
  normalized: string
  corrected: string
  fuzzy: FuzzyMatchResult
  confidence: ConfidenceLevel
  needConfirm: boolean
}

export function runSpeechPipeline(rawText: string): SpeechPipelineResult {
  const normalized = normalizeSpeechText(rawText)
  const corrected = correctCommonAsrErrors(normalized)
  const fuzzy = matchIntentByFuzzy(corrected)
  const confidence = getConfidenceLevel(fuzzy.score)
  const needConfirm = confidence === 'MEDIUM' && !fuzzy.sensitivity

  return {
    rawText,
    normalized,
    corrected,
    fuzzy,
    confidence,
    needConfirm,
  }
}

// ══════════════════════════════════════════════════════
// 八、DeepSeek 意图分类兜底（导出供 actionExecutor 使用）
// ══════════════════════════════════════════════════════

export async function classifyIntentByAi(text: string): Promise<FuzzyMatchResult | null> {
  try {
    const res = await fetch('/api/ai/agent-chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 1,
        message: text,
        instruction: `你是意图分类器。只返回 JSON，不要解释。
可选 intent: OPEN_MONITOR, OPEN_PIR, OPEN_DEVICE, OPEN_ALARM, OPEN_DASHBOARD, QUERY_SECURITY_STATUS, QUERY_DEVICE_STATUS, QUERY_ALARMS, QUERY_PIR_TODAY, SEND_HELP, STOP_SPEAKING, AI_CHAT, UNKNOWN
用户语音识别文本: "${text}"
返回格式: {"intent":"xxx","action":"xxx","confidence":0.9}`,
      }),
    })

    if (!res.ok) return null

    const json = await res.json()
    const data = json?.data || json
    const aiIntent = data?.intent || data?.answer || ''

    // 解析 AI 返回的意图
    if (typeof aiIntent === 'string') {
      try {
        const parsed = JSON.parse(aiIntent)
        if (parsed.intent && parsed.intent !== 'UNKNOWN') {
          return {
            intent: parsed.intent.startsWith('OPEN_') ? 'OPEN_MODULE'
                 : parsed.intent.startsWith('QUERY_') ? 'QUERY_DATA'
                 : parsed.intent === 'SEND_HELP' ? 'SENSITIVE_ACTION'
                 : parsed.intent === 'STOP_SPEAKING' ? 'STOP_SPEAKING'
                 : 'AI_CHAT',
            action: parsed.intent,
            score: Math.min(parsed.confidence || 0.65, 0.71), // cap at 0.71 to mark as AI-inferred
            matchedAlias: `AI 分类: ${parsed.intent}`,
            fuzzy: true,
          }
        }
      } catch {
        // AI 返回的不是 JSON，尝试匹配
      }
    }
    return null
  } catch {
    return null
  }
}

// ══════════════════════════════════════════════════════
// 九、调试信息（供 UI 面板显示）
// ══════════════════════════════════════════════════════

export interface SpeechDebugInfo {
  rawText: string
  normalized: string
  corrected: string
  intent: string
  action: string
  matchedAlias: string
  score: number
  confidence: ConfidenceLevel
  needConfirm: boolean
  aiFallback: boolean
}

export function getSpeechDebugInfo(
  rawText: string,
  pipeline: SpeechPipelineResult,
  aiFallback = false,
): SpeechDebugInfo {
  return {
    rawText,
    normalized: pipeline.normalized,
    corrected: pipeline.corrected,
    intent: pipeline.fuzzy.intent,
    action: pipeline.fuzzy.action,
    matchedAlias: pipeline.fuzzy.matchedAlias || '(无匹配)',
    score: Math.round(pipeline.fuzzy.score * 100),
    confidence: pipeline.confidence,
    needConfirm: pipeline.needConfirm,
    aiFallback,
  }
}
