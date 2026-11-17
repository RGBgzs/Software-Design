# -*- coding: utf-8 -*-
"""
人脸识别网页服务 — REST API + WebSocket 实时检测
"""
import io
import base64
import os
import sys
import uuid
import time
import re
import logging
import threading

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

from pathlib import Path
os.chdir(Path(__file__).parent)

from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO, emit
from PIL import Image, ImageDraw
from face_system import FaceRecognitionSystem
from tts_engine import get_engine
from spring_sync import get_sync_manager

from config import (
    PROJECT_ROOT, FACE_DB_DIR,
    RECOGNITION_THRESHOLD, RECOGNITION_MODEL_NAME, ENABLE_LIVENESS,
    EMBEDDINGS_FILE,
    SERVER_HOST, SERVER_PORT, CORS_ORIGINS, WS_MAX_BUFFER_SIZE,
    MAX_UPLOAD_SIZE_BYTES, MAX_UPLOAD_SIZE_MB,
    VALID_USERNAME_PATTERN,
    MSG_NAME_EMPTY, MSG_NO_IMAGE, MSG_NO_FILE, MSG_IMAGE_DECODE_FAIL,
    MSG_NO_FACE_DETECTED, MSG_USER_NOT_FOUND, MSG_DB_EMPTY,
    MSG_FILE_TOO_LARGE, MSG_INVALID_NAME, MSG_SERVER_ERROR, MSG_DECODE_FAIL,
)

# ── 日志 ──
logging.basicConfig(level=logging.INFO, format='[%(asctime)s] %(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
app.config['SEND_FILE_MAX_AGE_DEFAULT'] = 0
app.config['MAX_CONTENT_LENGTH'] = MAX_UPLOAD_SIZE_BYTES
socketio = SocketIO(app, cors_allowed_origins=CORS_ORIGINS,
                    max_http_buffer_size=WS_MAX_BUFFER_SIZE,
                    async_mode='threading')

print("Loading models...")
system = FaceRecognitionSystem(
    embeddings_file=EMBEDDINGS_FILE,
    recognition_threshold=RECOGNITION_THRESHOLD,
    recognition_model=RECOGNITION_MODEL_NAME,
    enable_liveness=ENABLE_LIVENESS
)
print("Ready.\n")

# ── TTS 引擎（懒加载，首次调用时初始化）──
tts = get_engine()

# ── Spring Boot 同步管理器 ──
sync_mgr = get_sync_manager()
if sync_mgr.get_status()['enabled']:
    print(f"Spring Sync: enabled → {sync_mgr.get_status()['spring_boot_url']}")
else:
    print("Spring Sync: disabled")
print()


# ═══════════════════════════════════════════
#  工具函数
# ═══════════════════════════════════════════

def _ok(data=None, **kwargs):
    """统一成功响应"""
    body = {'ok': True}
    if data is not None:
        body['data'] = data
    body.update(kwargs)
    return jsonify(body)

def _err(error, code='BAD_REQUEST', status=400):
    """统一错误响应"""
    return jsonify({'ok': False, 'error': error, 'code': code}), status

def _validate_name(name):
    """校验用户名合法性，防止路径穿越。返回 (is_valid, sanitized_name)"""
    if not name or not name.strip():
        return False, ''
    name = name.strip()
    if not VALID_USERNAME_PATTERN.match(name):
        return False, ''
    return True, name

def _validate_image_file(uploaded_file):
    """校验上传的图片文件。返回 (is_valid, error_message)"""
    if not uploaded_file or uploaded_file.filename == '':
        return False, MSG_NO_FILE
    # 检查文件大小
    uploaded_file.seek(0, os.SEEK_END)
    size = uploaded_file.tell()
    uploaded_file.seek(0)
    if size > MAX_UPLOAD_SIZE_BYTES:
        return False, MSG_FILE_TOO_LARGE
    return True, ''

def _safe_open_image(file_obj):
    """安全打开上传的图片文件。返回 (PIL.Image, error_message)"""
    try:
        img = Image.open(file_obj).convert('RGB')
        return img, ''
    except Exception as e:
        return None, f'{MSG_IMAGE_DECODE_FAIL}: {str(e)}'

def _validate_save_path(name):
    """
    校验注册照片的保存目录，确保在 PROJECT_ROOT 内。
    返回: (safe_dir: Path, error: str)
    """
    safe_dir = (FACE_DB_DIR / name).resolve()
    try:
        safe_dir.relative_to(PROJECT_ROOT)
    except ValueError:
        return None, f'非法用户名（路径穿越检测）: {name}'
    return safe_dir, ''


# ═══════════════════════════════════════════
#  TTS 自动播报 — 防抖 + 子线程 + WebSocket 推送
# ═══════════════════════════════════════════

# TTS 播报冷却时间（秒）— 同一 key 在此时间内只播报一次
TTS_ANNOUNCE_COOLDOWN = float(os.environ.get('FACE_TTS_COOLDOWN', '5.0'))
# TTS 合成超时（秒）— 超过此时间丢弃本次合成
TTS_SYNTHESIS_TIMEOUT = float(os.environ.get('FACE_TTS_TIMEOUT', '1.5'))


class TTSPolicy:
    """TTS 自动播报的防抖策略 — 线程安全"""

    def __init__(self, cooldown: float = 5.0):
        self._cooldown = cooldown
        self._last_announced: dict[str, float] = {}  # key → 上次播报时间戳
        self._lock = threading.Lock()

    def should_announce(self, key: str) -> bool:
        """
        检查并更新防抖状态。返回 True 表示可以播报。
        调用此方法即视为"已播报"（更新冷却时间戳）。
        """
        with self._lock:
            now = time.time()
            last = self._last_announced.get(key, 0.0)
            if now - last < self._cooldown:
                return False
            self._last_announced[key] = now
            # 清理过期条目，防止字典无限增长
            expired = [k for k, v in self._last_announced.items()
                       if now - v > self._cooldown * 10]
            for k in expired:
                del self._last_announced[k]
            return True

    @staticmethod
    def get_key(text: str) -> str:
        """
        从播报文本提取防抖 key。
        "欢迎 魏宸希 到来" → "known:魏宸希"
        "不好意思，你不能进入" → "stranger"
        """
        if '欢迎' in text and '到来' in text:
            # 提取姓名
            name = text.replace('欢迎 ', '').replace(' 到来', '').strip()
            return f'known:{name}'
        return 'stranger'


# 全局播报策略实例
_tts_policy = TTSPolicy(cooldown=TTS_ANNOUNCE_COOLDOWN)


def _get_tts_announcement_text(faces: list) -> str | None:
    """
    分析一帧的检测结果，判断是否需要自动播报。
    优先级：已知人员 > 陌生人（真人）。
    返回播报文本；不需要播报时返回 None。

    规则：
      - 假体/照片 → 不播报
      - 已知人员（matched=True）→ "欢迎 XX 到来"
      - 陌生人（matched=False 且活体为真人）→ "不好意思，你不能进入"
    """
    if not faces:
        return None

    for face in faces:
        # 假体攻击：不播报任何内容
        if face.get('blocked'):
            continue

        # 已知人员（匹配成功）
        if face.get('matched') and face.get('name'):
            name = face['name']
            if name and name not in ('陌生人', 'Unknown', '假体攻击'):
                return f'欢迎 {name} 到来'

        # 陌生人（未匹配 + 活体为真人）
        liveness = face.get('liveness')
        if liveness and liveness.get('is_real'):
            return '不好意思，你不能进入'

    return None


def _tts_worker(text: str, sid: str):
    """
    在子线程中执行 TTS 合成并通过 WebSocket 推送给客户端。
    超时自动丢弃，不阻塞主线程。
    """
    import traceback

    t0 = time.time()
    try:
        # 如果 TTS 引擎不可用，静默放弃
        if not tts.is_available:
            if not tts._ensure_loaded():
                logger.warning(f'TTS worker: 引擎不可用 — {tts.model_error}')
                return

        # 调用合成（可能耗时 ~0.2s）
        result = tts.synthesize(text, speed=1.0)

        elapsed = time.time() - t0
        if elapsed > TTS_SYNTHESIS_TIMEOUT:
            logger.warning(f'TTS worker: 合成超时 ({elapsed:.1f}s > {TTS_SYNTHESIS_TIMEOUT}s)，丢弃')
            return

        if result.get('ok'):
            # 通过 WebSocket 推送音频给指定客户端
            socketio.emit('tts_audio', {
                'audio_base64': result['audio_base64'],
                'sample_rate': result['sample_rate'],
                'text': text,
                'duration': result.get('duration', 0),
            }, room=sid)
            logger.info(f'TTS worker: 已推送 "{text}" → sid={sid[:8]}... '
                        f'({elapsed:.2f}s, {result.get("duration", 0):.1f}s 音频)')
        else:
            logger.warning(f'TTS worker: 合成失败 — {result.get("error")}')

    except Exception:
        logger.error(f'TTS worker 异常:\n{traceback.format_exc()}')


def _maybe_trigger_tts(faces: list, sid: str):
    """
    检查是否需要自动播报，通过防抖后启动子线程合成。
    此函数调用后立即返回，不阻塞检测流程。
    """
    text = _get_tts_announcement_text(faces)
    if text is None:
        return

    key = TTSPolicy.get_key(text)
    if not _tts_policy.should_announce(key):
        return

    logger.info(f'TTS trigger: "{text}" (key={key}) → 启动子线程')
    t = threading.Thread(target=_tts_worker, args=(text, sid), daemon=True,
                         name=f'tts-{key}')
    t.start()


# ═══════════════════════════════════════════
#  Static pages
# ═══════════════════════════════════════════

@app.route('/')
def index():
    return app.send_static_file('frontend.html')

@app.route('/frontend')
def frontend():
    return app.send_static_file('frontend.html')


# ═══════════════════════════════════════════
#  REST API — 系统状态
# ═══════════════════════════════════════════

@app.route('/api/status')
def api_status():
    try:
        tts_available = tts.is_available or tts._ensure_loaded()
        return _ok({
            'registered_users': system.get_user_count(),
            'user_names': system.get_user_names(),
            'threshold': system.threshold,
            'device': str(system.device),
            'recognition_model': system.model_config['name'],
            'detection_method': system.detection_method,
            'liveness_enabled': system.liveness_detector is not None,
            'tts_available': tts_available,
            'tts_model': 'vits-icefall-zh-aishell3',
        })
    except Exception as e:
        logger.exception('api_status')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


# ═══════════════════════════════════════════
#  REST API — 用户管理
# ═══════════════════════════════════════════

@app.route('/api/users', methods=['GET'])
def api_list_users():
    try:
        snapshot = system.get_users_snapshot()
        users = [{'name': name, 'face_count': len(embs)}
                 for name, embs in snapshot.items()]
        return _ok({
            'total': len(users),
            'users': sorted(users, key=lambda u: u['name'])
        })
    except Exception as e:
        logger.exception('api_list_users')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


@app.route('/api/users/register', methods=['POST'])
def api_register_user():
    """注册新用户（上传照片）"""
    # ── 参数校验 ──
    name = request.form.get('name', '').strip()
    is_valid, name = _validate_name(name)
    if not is_valid:
        return _err(MSG_INVALID_NAME if name else MSG_NAME_EMPTY)

    if 'image' not in request.files:
        return _err(MSG_NO_IMAGE)

    file = request.files['image']
    valid, err_msg = _validate_image_file(file)
    if not valid:
        return _err(err_msg)

    # ── 打开图片 ──
    img, err_msg = _safe_open_image(file)
    if img is None:
        return _err(err_msg)

    # ── 检测人脸 ──
    try:
        face_tensor = system.extract_face(img)
    except Exception as e:
        logger.exception('extract_face')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)

    if face_tensor is None:
        return _err(MSG_NO_FACE_DETECTED)

    # ── 提取特征 ──
    try:
        embedding = system.extract_embedding(face_tensor)
    except Exception as e:
        logger.exception('extract_embedding')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)

    if embedding is None:
        return _err('特征提取失败', 'EXTRACTION_FAILED', 500)

    # ── 保存到人脸库 ──
    try:
        with system._db_lock:
            if name not in system.face_db:
                system.face_db[name] = []
            system.face_db[name].append(embedding)
            try:
                system._save_embeddings()
            except Exception:
                system.face_db[name].pop()
                if not system.face_db[name]:
                    del system.face_db[name]
                raise
    except Exception as e:
        logger.exception('save_embeddings')
        return _err(f'保存人脸数据失败: {str(e)}', 'SAVE_FAILED', 500)

    # ── 保存注册照片 ──
    try:
        safe_dir, err = _validate_save_path(name)
        if safe_dir is None:
            return _err(err, 'INVALID_NAME', 400)

        safe_dir.mkdir(parents=True, exist_ok=True)
        timestamp = time.strftime('%Y%m%d_%H%M%S')
        short_uid = uuid.uuid4().hex[:8]
        save_path = safe_dir / f'{timestamp}_{short_uid}.jpg'
        img.save(str(save_path), 'JPEG', quality=90)
    except Exception as e:
        logger.exception('save_photo')
        return _err(f'保存照片失败: {str(e)}', 'SAVE_PHOTO_FAILED', 500)

    total_faces = len(system.face_db[name]) if name in system.face_db else 1
    return _ok({
        'message': f'用户 "{name}" 注册成功',
        'name': name,
        'total_faces': total_faces,
        'saved_to': str(save_path.relative_to(PROJECT_ROOT))
    })


@app.route('/api/users/<path:name>', methods=['DELETE'])
def api_delete_user(name):
    """删除用户"""
    is_valid, name = _validate_name(name)
    if not is_valid:
        return _err(MSG_INVALID_NAME if name else MSG_USER_NOT_FOUND, 'INVALID_NAME', 400)

    try:
        with system._db_lock:
            if name not in system.face_db:
                return _err(f'{MSG_USER_NOT_FOUND}: "{name}"', 'NOT_FOUND', 404)
            count = len(system.face_db[name])
            del system.face_db[name]
            try:
                system._save_embeddings()
            except Exception:
                raise
    except Exception as e:
        logger.exception('api_delete_user')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)

    return _ok({
        'message': f'已删除用户 "{name}"（{count} 张人脸）',
        'name': name,
        'deleted_faces': count
    })


@app.route('/api/users/register-batch', methods=['POST'])
def api_register_batch():
    """批量注册 face_db 目录中的所有人脸"""
    if not FACE_DB_DIR.exists():
        return _err(MSG_DB_EMPTY)

    try:
        before_count = sum(len(v) for v in system.face_db.values())
        system.register_folder(str(FACE_DB_DIR))
        after_count = sum(len(v) for v in system.face_db.values())
        new_faces = after_count - before_count

        return _ok({
            'message': '批量注册完成',
            'total_users': len(system.face_db),
            'total_faces': after_count,
            'new_faces_registered': max(0, new_faces),
            'users': list(system.face_db.keys())
        })
    except Exception as e:
        logger.exception('api_register_batch')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


# ═══════════════════════════════════════════
#  REST API — 图片识别（单次上传）
# ═══════════════════════════════════════════

@app.route('/api/recognize', methods=['POST'])
def api_recognize():
    """单张图片上传识别"""
    # ── 参数校验 ──
    if 'image' not in request.files:
        return _err(MSG_NO_IMAGE)
    file = request.files['image']
    valid, err_msg = _validate_image_file(file)
    if not valid:
        return _err(err_msg)

    # ── 打开图片 ──
    img, err_msg = _safe_open_image(file)
    if img is None:
        return _err(err_msg)

    # ── 检测人脸 ──
    try:
        boxes = system.detect_faces(img)
    except Exception as e:
        logger.exception('detect_faces')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)

    results = []
    annotated_img = img.copy()

    if boxes is not None and len(boxes) > 0:
        try:
            draw = ImageDraw.Draw(annotated_img)
        except Exception:
            draw = None

        for i, box in enumerate(boxes):
            try:
                x1, y1, x2, y2 = [max(0, int(v)) for v in box]
                x2, y2 = min(img.width, x2), min(img.height, y2)
                if x2 <= x1 or y2 <= y1:
                    continue

                face_tensor = system.crop_and_align(img, box)
                if face_tensor is None:
                    results.append({
                        'face_index': i + 1, 'name': 'Unknown',
                        'similarity': 0, 'matched': False,
                        'bbox': [x1, y1, x2, y2], 'liveness': None
                    })
                    if draw:
                        draw.rectangle([x1, y1, x2, y2], outline='red', width=3)
                        draw.text((x1, max(0, y1 - 18)), 'Unknown', fill='red')
                    continue

                # ── 活体检测 ──
                is_spoof = False
                liveness = None
                if system.liveness_detector is not None:
                    try:
                        mw = int((x2 - x1) * 0.3)
                        mh = int((y2 - y1) * 0.3)
                        cx1 = max(0, x1 - mw)
                        cy1 = max(0, y1 - mh)
                        cx2 = min(img.width, x2 + mw)
                        cy2 = min(img.height, y2 + mh)
                        if cx2 > cx1 and cy2 > cy1:
                            face_crop = img.crop((cx1, cy1, cx2, cy2))
                            is_real, lv_score, lv_detail = system.liveness_detector.predict(face_crop)
                            liveness = {
                                'is_real': is_real,
                                'score': round(lv_score * 100, 2),
                                'detail': lv_detail
                            }
                            if not is_real:
                                is_spoof = True
                    except Exception as e:
                        liveness = {'error': str(e)}

                if is_spoof:
                    results.append({
                        'face_index': i + 1, 'name': '假体攻击',
                        'similarity': 0, 'matched': False,
                        'bbox': [x1, y1, x2, y2], 'liveness': liveness,
                        'blocked': True, 'block_reason': 'liveness_spoof'
                    })
                    if draw:
                        draw.rectangle([x1, y1, x2, y2], outline='red', width=3)
                        draw.text((x1, max(0, y1 - 18)), 'SPOOF! 假体攻击', fill='red')
                        draw.text((x1, y2 + 4), '[BLOCKED] 非活体', fill='red')
                    continue

                # ── 特征提取 + 匹配 ──
                embedding = system.extract_embedding(face_tensor)
                if embedding is None:
                    name, sim = 'Unknown', 0
                    matched = False
                else:
                    name, sim = system._find_best_match(embedding)
                    matched = sim >= system.threshold

                color = 'green' if matched else 'orange'
                label = f'{name} ({sim:.1%})' if matched else f'Stranger ({sim:.1%})'
                if draw:
                    draw.rectangle([x1, y1, x2, y2], outline=color, width=3)
                    draw.text((x1, max(0, y1 - 18)), label, fill=color)
                    if liveness and liveness.get('is_real'):
                        draw.text((x1, y2 + 4), f'[LIVE] {liveness["score"]:.0f}%', fill='green')

                results.append({
                    'face_index': i + 1, 'name': name,
                    'similarity': round(sim * 100, 2),
                    'matched': matched,
                    'bbox': [x1, y1, x2, y2],
                    'liveness': liveness,
                    'blocked': False
                })

            except Exception as e:
                logger.exception(f'process_face_{i}')
                results.append({
                    'face_index': i + 1, 'name': 'Error',
                    'similarity': 0, 'matched': False,
                    'bbox': [x1, y1, x2, y2] if 'x1' in dir() else [0, 0, 0, 0],
                    'liveness': {'error': str(e)},
                    'blocked': False
                })

    # ── 生成标注图片 ──
    try:
        buf = io.BytesIO()
        annotated_img.save(buf, format='JPEG', quality=85)
        buf.seek(0)
        img_b64 = base64.b64encode(buf.read()).decode('utf-8')
        annotated_image = f'data:image/jpeg;base64,{img_b64}'
    except Exception as e:
        logger.exception('annotated_save')
        annotated_image = ''

    return _ok({
        'total_faces': len(results),
        'results': results,
        'annotated_image': annotated_image
    })


# ═══════════════════════════════════════════
#  REST API — 实时检测轮询 (HTTP fallback)
# ═══════════════════════════════════════════

@app.route('/api/detect-live', methods=['POST'])
def api_detect_live():
    """HTTP POST 轮询 — WebSocket 不可用时的降级方案"""
    data = request.get_data(as_text=True)
    try:
        if ',' in data and data.startswith('data:image'):
            _, b64 = data.split(',', 1)
            img = Image.open(io.BytesIO(base64.b64decode(b64))).convert('RGB')
        else:
            img = Image.open(io.BytesIO(base64.b64decode(data))).convert('RGB')
    except Exception as e:
        logger.warning(f'detect-live decode: {e}')
        return _err(f'{MSG_DECODE_FAIL}: {str(e)}')

    try:
        result = _process_frame(img)
        # HTTP 降级模式：附带 tts_text，前端可用 SpeechSynthesis 播报
        tts_text = _get_tts_announcement_text(result.get('faces', []))
        if tts_text and _tts_policy.should_announce(TTSPolicy.get_key(tts_text)):
            result['tts_text'] = tts_text
        # 异步推送识别结果到 Spring Boot
        _trigger_spring_sync(result.get('faces', []), img)
        return jsonify(result)
    except Exception as e:
        logger.exception('detect-live process')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


# ═══════════════════════════════════════════
#  REST API — Spring Boot 兼容适配层
# ═══════════════════════════════════════════

@app.route('/api/face/status', methods=['GET'])
def api_face_status():
    try:
        tts_available = tts.is_available or tts._ensure_loaded()
        return jsonify({'code': 200, 'message': 'success', 'data': {
            'registered_users': system.get_user_count(),
            'user_names': system.get_user_names(),
            'threshold': system.threshold,
            'device': str(system.device),
            'recognition_model': system.model_config['name'],
            'detection_method': system.detection_method,
            'liveness_enabled': system.liveness_detector is not None,
            'tts_available': tts_available,
        }})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e), 'data': {}})

@app.route('/api/face/identify', methods=['POST'])
def api_face_identify():
    if 'image' not in request.files:
        return jsonify({'code': 400, 'message': MSG_NO_IMAGE, 'data': {}})
    try:
        file = request.files['image']
        img, err_msg = _safe_open_image(file)
        if img is None:
            return jsonify({'code': 400, 'message': err_msg, 'data': {}})
        boxes = system.detect_faces(img)
        if boxes is None or len(boxes) == 0:
            return jsonify({'code': 200, 'message': '未检测到人脸', 'data': {'matched': False, 'name': '无人脸', 'personType': 'UNKNOWN', 'similarity': 0}})
        box = boxes[0]
        x1, y1, x2, y2 = [max(0, int(v)) for v in box]
        face_tensor = system.crop_and_align(img, box)
        if face_tensor is None:
            return jsonify({'code': 200, 'message': '特征提取失败', 'data': {'matched': False, 'name': 'UNKNOWN', 'personType': 'UNKNOWN', 'similarity': 0}})
        embedding = system.extract_embedding(face_tensor)
        if embedding is None:
            return jsonify({'code': 200, 'message': '特征提取失败', 'data': {'matched': False, 'name': 'UNKNOWN', 'personType': 'UNKNOWN', 'similarity': 0}})
        name, sim = system._find_best_match(embedding)
        matched = sim >= system.threshold
        pt = 'REGISTERED' if matched else 'UNKNOWN'
        return jsonify({'code': 200, 'message': 'success', 'data': {
            'matched': matched, 'name': name, 'personType': pt,
            'similarity': round(sim * 100, 2),
            'threshold': system.threshold,
            'deviceCode': request.form.get('deviceCode', 'CAMERA_001'),
            'location': request.form.get('location', ''),
        }})
    except Exception as e:
        logger.exception('api_face_identify')
        return jsonify({'code': 500, 'message': str(e), 'data': {}})

@app.route('/api/face/register', methods=['POST'])
def api_face_register():
    name = request.form.get('personId', '') or request.form.get('name', '')
    if not name or not name.strip():
        return jsonify({'code': 400, 'message': MSG_NAME_EMPTY, 'data': {}})
    if 'image' not in request.files:
        return jsonify({'code': 400, 'message': MSG_NO_IMAGE, 'data': {}})
    try:
        file = request.files['image']
        img, err_msg = _safe_open_image(file)
        if img is None:
            return jsonify({'code': 400, 'message': err_msg, 'data': {}})
        face_tensor = system.extract_face(img)
        if face_tensor is None:
            return jsonify({'code': 400, 'message': MSG_NO_FACE_DETECTED, 'data': {}})
        embedding = system.extract_embedding(face_tensor)
        if embedding is None:
            return jsonify({'code': 500, 'message': '特征提取失败', 'data': {}})
        with system._db_lock:
            if name not in system.face_db:
                system.face_db[name] = []
            system.face_db[name].append(embedding)
        system._save_embeddings()
        return jsonify({'code': 200, 'message': '注册成功', 'data': {'name': name, 'face_count': len(system.face_db[name])}})
    except Exception as e:
        logger.exception('api_face_register')
        return jsonify({'code': 500, 'message': str(e), 'data': {}})


# ═══════════════════════════════════════════
#  REST API — 语音合成 (TTS)
# ═══════════════════════════════════════════

@app.route('/api/tts/status', methods=['GET'])
def api_tts_status():
    """TTS 引擎状态查询"""
    try:
        available = tts.is_available
        if not available:
            available = tts._ensure_loaded()
        return _ok({
            'available': available,
            'model': 'vits-icefall-zh-aishell3',
            'sample_rate': 8000,
            'error': tts.model_error if not available else None,
        })
    except Exception as e:
        logger.exception('api_tts_status')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


@app.route('/api/tts/synthesize', methods=['POST'])
def api_tts_synthesize():
    """语音合成 — 文本转语音"""
    try:
        body = request.get_json(silent=True)
        if not body:
            return _err('请求体必须是 JSON 格式')
    except Exception:
        return _err('请求体解析失败')

    text = (body.get('text', '') or '').strip()
    if not text:
        return _err('缺少 text 参数', 'EMPTY_TEXT')

    if len(text) > 300:
        return _err(f'文本过长 ({len(text)} 字符)，最多支持 300 字符', 'TEXT_TOO_LONG')

    speed = float(body.get('speed', 1.0))
    sid = int(body.get('sid', 0))

    # 参数范围校验
    speed = max(0.3, min(3.0, speed))
    sid = max(0, min(173, sid))

    # ── 调用引擎 ──
    try:
        result = tts.synthesize(text, speed=speed, sid=sid)
    except Exception as e:
        logger.exception('tts_synthesize')
        return _err(f'语音合成异常: {str(e)}', 'SYNTHESIS_FAILED', 500)

    if not result.get('ok'):
        code = result.get('code', 'SYNTHESIS_FAILED')
        return _err(result.get('error', '语音合成失败'), code,
                    400 if code == 'EMPTY_TEXT' else 500)

    return _ok({
        'audio_base64': result['audio_base64'],
        'sample_rate': result['sample_rate'],
        'duration': result['duration'],
        'text': text,
        'speed': speed,
        'format': 'wav',
    })


# ═══════════════════════════════════════════
#  REST API — Spring Boot 同步状态查询
# ═══════════════════════════════════════════

@app.route('/api/sync/status', methods=['GET'])
def api_sync_status():
    """查询向 Spring Boot 同步的最新状态"""
    try:
        status = sync_mgr.get_status()
        return _ok(status)
    except Exception as e:
        logger.exception('api_sync_status')
        return _err(f'{MSG_SERVER_ERROR}: {str(e)}', 'INTERNAL_ERROR', 500)


# ═══════════════════════════════════════════
#  WebSocket — 实时检测帧处理
# ═══════════════════════════════════════════

def _trigger_spring_sync(faces: list, img=None):
    """
    从检测结果中提取数据，异步推送给 Spring Boot。
    每帧只推送第一个有效人脸（避免频繁请求）。
    """
    for face in faces:
        if face.get('blocked'):
            continue  # 假体不推送
        name = face.get('name', '陌生人')
        matched = face.get('matched', False)
        similarity = face.get('similarity', 0) / 100.0  # 转回 0~1
        # 异步推送（不阻塞）
        sync_mgr.push_identify_result(
            name=name, matched=matched, similarity=similarity,
            img=img, is_spoof=False,
        )
        return  # 每帧只推送第一个有效人脸

def _process_frame(img):
    """处理一帧图像，返回检测结果 dict。单帧异常不崩溃，返回空 faces。"""
    t0 = time.time()
    try:
        detections = system.detect_with_landmarks(img)
    except Exception as e:
        logger.exception('_process_frame detect')
        return {'faces': [], 'ts': time.time(), 'ms': 0, 'error': str(e)}

    t_det = (time.time() - t0) * 1000

    if not detections:
        return {'faces': [], 'ts': time.time(), 'ms': round(t_det, 1)}

    results = []
    for box, landmarks, score in detections:
        try:
            x1, y1, x2, y2 = [max(0, int(v)) for v in box]
            x2, y2 = min(img.width, x2), min(img.height, y2)
            if x2 <= x1 or y2 <= y1:
                continue

            # 活体检测
            is_spoof = False
            liveness = None
            if system.liveness_detector is not None:
                try:
                    mw = int((x2 - x1) * 0.3)
                    mh = int((y2 - y1) * 0.3)
                    cx1 = max(0, x1 - mw)
                    cy1 = max(0, y1 - mh)
                    cx2 = min(img.width, x2 + mw)
                    cy2 = min(img.height, y2 + mh)
                    face_crop = img.crop((cx1, cy1, cx2, cy2))
                    is_real, lv_score, lv_detail = system.liveness_detector.predict(face_crop)
                    liveness = {
                        'is_real': is_real,
                        'score': round(lv_score * 100, 2),
                        'detail': lv_detail
                    }
                    if not is_real:
                        is_spoof = True
                except Exception as e:
                    liveness = {'error': str(e)}

            if is_spoof:
                results.append({
                    'bbox': [x1, y1, x2, y2],
                    'name': '假体攻击', 'similarity': 0, 'matched': False,
                    'liveness': liveness, 'blocked': True, 'block_reason': 'liveness_spoof'
                })
                continue

            # 特征提取 + 匹配
            face_tensor = system.crop_and_align(img, box)
            name, sim = '陌生人', 0.0
            matched = False
            if face_tensor is not None:
                embedding = system.extract_embedding(face_tensor)
                if embedding is not None:
                    name, sim = system._find_best_match(embedding)
                    matched = sim >= system.threshold

            results.append({
                'bbox': [x1, y1, x2, y2],
                'name': name,
                'similarity': round(sim * 100, 2),
                'matched': matched,
                'liveness': liveness,
                'blocked': False
            })

        except Exception as e:
            logger.exception('_process_frame face')
            continue

    return {'faces': results, 'ts': time.time(), 'ms': round(t_det, 1)}


@socketio.on('detect')
def handle_detect(data):
    """WebSocket 实时检测帧处理"""
    try:
        data_url = data.get('image', '')
        if ',' in data_url and data_url.startswith('data:image'):
            _, b64 = data_url.split(',', 1)
            img = Image.open(io.BytesIO(base64.b64decode(b64))).convert('RGB')
        else:
            img = Image.open(io.BytesIO(base64.b64decode(data_url))).convert('RGB')
    except Exception as e:
        logger.warning(f'WS decode: {e}')
        emit('detect_result', {'error': f'{MSG_DECODE_FAIL}: {str(e)}', 'faces': []})
        return

    try:
        result = _process_frame(img)
        emit('detect_result', result)

        # 异步 TTS 自动播报（不阻塞 detect 事件循环）
        _maybe_trigger_tts(result.get('faces', []), request.sid)

        # 异步推送识别结果到 Spring Boot（不阻塞 detect 事件循环）
        _trigger_spring_sync(result.get('faces', []), img)
    except Exception as e:
        logger.exception('WS handle_detect')
        emit('detect_result', {'error': f'{MSG_SERVER_ERROR}: {str(e)}', 'faces': []})


@socketio.on('connect')
def handle_connect():
    logger.info('WS client connected')


@socketio.on('disconnect')
def handle_disconnect():
    logger.info('WS client disconnected')


# ═══════════════════════════════════════════
#  Error handlers
# ═══════════════════════════════════════════

@app.errorhandler(413)
def too_large(e):
    """文件超过 MAX_CONTENT_LENGTH"""
    return _err(MSG_FILE_TOO_LARGE, 'FILE_TOO_LARGE', 413)


@app.errorhandler(404)
def not_found(e):
    return _err('Not Found', 'NOT_FOUND', 404)


@app.errorhandler(500)
def internal_error(e):
    return _err(MSG_SERVER_ERROR, 'INTERNAL_ERROR', 500)


# ═══════════════════════════════════════════
#  Entry point
# ═══════════════════════════════════════════

if __name__ == '__main__':
    print(f"\n  Frontend : http://localhost:{SERVER_PORT}/frontend")
    print(f"  REST API : http://localhost:{SERVER_PORT}/api/")
    print(f"  WebSocket: ws://localhost:{SERVER_PORT} (Socket.IO)\n")
    socketio.run(app, host=SERVER_HOST, port=SERVER_PORT, debug=False,
                 allow_unsafe_werkzeug=True)
