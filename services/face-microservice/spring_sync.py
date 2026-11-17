"""
============================================================
Spring Boot 智慧安防集成 — 异步同步管理器
============================================================
功能：
  1. 将人脸识别结果异步推送给同学的 Spring Boot 后端
  2. 保存陌生人截图到本地 captures/ 目录
  3. 预留告警创建接口（环境变量开关控制）
============================================================
"""
import io
import base64
import json
import threading
import logging
import time
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime

from config import (
    SPRING_BOOT_URL, ENABLE_SPRING_SYNC, SPRING_API_TIMEOUT,
    ENABLE_ALARM_CREATE, CAPTURES_DIR,
    FLASK_DEVICE_CODE, FLASK_LOCATION,
)

logger = logging.getLogger(__name__)


class ApiSyncManager:
    """
    向 Spring Boot 同步识别结果的异步管理器。

    所有 HTTP 请求均在子线程中执行，不阻塞主检测流程。
    Spring Boot 不可用时自动降级，只记录日志不抛异常。

    用法:
        sync = ApiSyncManager()
        sync.push_identify_result(name="魏宸希", matched=True, similarity=0.92,
                                   img=None, is_spoof=False)
    """

    def __init__(self):
        self._last_result: dict | None = None  # 最近一次同步结果（供前端轮询）
        self._last_sync_time: float = 0.0
        self._last_sync_success: bool = False
        self._last_sync_error: str | None = None
        self._lock = threading.Lock()

    # ═══════════════════════════════════════════
    #  对外接口：推送识别结果
    # ═══════════════════════════════════════════

    def push_identify_result(self, name: str, matched: bool, similarity: float,
                              img=None, is_spoof: bool = False):
        """
        异步推送识别结果到 Spring Boot 的 /api/face/identify/base64。

        Args:
            name: 识别到的人名（"陌生人" 表示未匹配）
            matched: 是否匹配成功
            similarity: 相似度 (0.0 ~ 1.0)
            img: PIL Image 对象（当前帧），用于转 Base64 发送
            is_spoof: 是否为假体攻击
        """
        if not ENABLE_SPRING_SYNC:
            logger.debug('Spring Sync: 已禁用 (ENABLE_SPRING_SYNC=0)')
            return

        # ── 假体攻击不推送（没有意义）──
        if is_spoof:
            return

        # 启动子线程执行 HTTP 请求
        t = threading.Thread(
            target=self._do_push_identify,
            args=(name, matched, similarity, img),
            daemon=True,
            name=f'sync-{name}',
        )
        t.start()

    # ═══════════════════════════════════════════
    #  内部：实际 HTTP 请求
    # ═══════════════════════════════════════════

    def _do_push_identify(self, name: str, matched: bool, similarity: float,
                           img=None):
        """在子线程中执行 Base64 人脸识别推送"""
        t0 = time.time()
        try:
            # ── 构造 imageBase64 ──
            image_base64 = ''
            if img is not None:
                try:
                    buf = io.BytesIO()
                    img.save(buf, format='JPEG', quality=70)
                    buf.seek(0)
                    img_bytes = buf.read()
                    image_base64 = 'data:image/jpeg;base64,' + base64.b64encode(
                        img_bytes).decode('utf-8')
                except Exception as e:
                    logger.warning(f'Spring Sync: 帧编码失败 — {e}')

            # ── 构造请求体 ──
            payload = {
                'imageBase64': image_base64,
                'deviceCode': FLASK_DEVICE_CODE,
                'location': FLASK_LOCATION,
                'threshold': 0.4,  # 与我们的人脸识别阈值一致
            }

            # ── 发送 HTTP POST ──
            url = f'{SPRING_BOOT_URL}/api/face/identify/base64'
            data = json.dumps(payload).encode('utf-8')
            req = urllib.request.Request(
                url, data=data,
                headers={'Content-Type': 'application/json'},
                method='POST',
            )

            try:
                with urllib.request.urlopen(req, timeout=SPRING_API_TIMEOUT) as resp:
                    body = resp.read().decode('utf-8')
                    elapsed = time.time() - t0
                    resp_data = json.loads(body)
                    logger.info(
                        f'Spring Sync OK [{elapsed:.2f}s]: name={name}, '
                        f'matched={matched}, sim={similarity:.2f}, '
                        f'code={resp_data.get("code")}'
                    )

                # ── 记录同步状态 ──
                with self._lock:
                    self._last_sync_time = time.time()
                    self._last_sync_success = True
                    self._last_sync_error = None
                    self._last_result = {
                        'name': name, 'matched': matched,
                        'similarity': round(similarity, 4),
                        'time': datetime.now().strftime('%H:%M:%S'),
                        'success': True,
                    }

                # ── 陌生人：保存截图 + 可选的告警创建 ──
                if not matched and img is not None and name in ('陌生人', 'Unknown', ''):
                    self._save_stranger_capture(img, similarity)

                return

            except urllib.error.HTTPError as e:
                elapsed = time.time() - t0
                status = e.code
                logger.warning(
                    f'Spring Sync HTTP {status} [{elapsed:.2f}s]: '
                    f'{e.reason} — Spring Boot 可能未启动或接口未实现'
                )
                with self._lock:
                    self._last_sync_time = time.time()
                    self._last_sync_success = False
                    self._last_sync_error = f'HTTP {status}: {e.reason}'
                    self._last_result = {
                        'name': name, 'success': False,
                        'error': f'HTTP {status}',
                        'time': datetime.now().strftime('%H:%M:%S'),
                    }

            except urllib.error.URLError as e:
                elapsed = time.time() - t0
                logger.warning(
                    f'Spring Sync 连接失败 [{elapsed:.2f}s]: {e.reason} '
                    f'— 确认 Spring Boot 已启动 ({SPRING_BOOT_URL})'
                )
                with self._lock:
                    self._last_sync_time = time.time()
                    self._last_sync_success = False
                    self._last_sync_error = str(e.reason)
                    self._last_result = {
                        'name': name, 'success': False,
                        'error': '连接失败',
                        'time': datetime.now().strftime('%H:%M:%S'),
                    }

            except Exception as e:
                logger.warning(f'Spring Sync 异常: {e}')

        except Exception as e:
            logger.error(f'Spring Sync 线程异常: {e}')

    # ═══════════════════════════════════════════
    #  陌生人截图保存
    # ═══════════════════════════════════════════

    def _save_stranger_capture(self, img, similarity: float):
        """保存陌生人截图到 captures/ 目录"""
        try:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f'stranger_{timestamp}.jpg'
            filepath = CAPTURES_DIR / filename
            img.save(str(filepath), 'JPEG', quality=85)
            logger.info(f'陌生人截图已保存: {filepath} (sim={similarity:.2f})')
        except Exception as e:
            logger.warning(f'陌生人截图保存失败: {e}')

    # ═══════════════════════════════════════════
    #  告警创建（预留，等同学实现接口后开启）
    # ═══════════════════════════════════════════

    def create_alarm_if_needed(self, record_id: int = None):
        """
        预留：向 Spring Boot 创建陌生人告警。
        等同学实现 POST /api/face/alarm/create 后，设置
        环境变量 FACE_ENABLE_ALARM_CREATE=1 即可启用。
        """
        if not ENABLE_ALARM_CREATE:
            return

        try:
            payload = {
                'recordId': record_id or 0,
                'deviceCode': FLASK_DEVICE_CODE,
                'deviceName': 'Flask 摄像头',
                'location': FLASK_LOCATION,
                'alarmLevel': 'HIGH',
                'alarmContent': f'检测到陌生人进入{FLASK_LOCATION}',
                'autoCreate': True,
            }
            url = f'{SPRING_BOOT_URL}/api/face/alarm/create'
            data = json.dumps(payload).encode('utf-8')
            req = urllib.request.Request(
                url, data=data,
                headers={'Content-Type': 'application/json'},
                method='POST',
            )
            with urllib.request.urlopen(req, timeout=SPRING_API_TIMEOUT) as resp:
                body = json.loads(resp.read().decode('utf-8'))
                logger.info(f'告警创建成功: {body}')
        except urllib.error.HTTPError as e:
            logger.warning(f'告警创建 HTTP {e.code}: {e.reason}')
        except Exception as e:
            logger.warning(f'告警创建失败: {e}')

    # ═══════════════════════════════════════════
    #  状态查询（供前端 /api/sync/status 使用）
    # ═══════════════════════════════════════════

    def get_status(self) -> dict:
        """返回最近一次同步状态（线程安全）"""
        with self._lock:
            now = time.time()
            is_recent = (now - self._last_sync_time) < 30.0 if self._last_sync_time > 0 else False
            return {
                'enabled': ENABLE_SPRING_SYNC,
                'spring_boot_url': SPRING_BOOT_URL,
                'last_sync': {
                    'success': self._last_sync_success,
                    'error': self._last_sync_error,
                    'time': datetime.fromtimestamp(self._last_sync_time).strftime('%H:%M:%S')
                    if self._last_sync_time > 0 else None,
                    'recent': is_recent,  # 30 秒内有同步记录
                },
                'last_result': self._last_result,
                'device_code': FLASK_DEVICE_CODE,
                'alarm_create_enabled': ENABLE_ALARM_CREATE,
            }


# ═══════════════════════════════════════════
#  全局单例
# ═══════════════════════════════════════════

_sync_manager: ApiSyncManager | None = None


def get_sync_manager() -> ApiSyncManager:
    """获取全局唯一 ApiSyncManager 实例"""
    global _sync_manager
    if _sync_manager is None:
        _sync_manager = ApiSyncManager()
    return _sync_manager
