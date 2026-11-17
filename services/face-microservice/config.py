"""
============================================================
人脸识别系统 — 全局配置
============================================================
所有可调参数集中管理，支持环境变量覆盖。
本地开发直接修改此文件；生产部署通过环境变量注入。
============================================================
"""
import os
import re
from pathlib import Path

# ═══════════════════════════════════════════
#  项目根目录
# ═══════════════════════════════════════════
PROJECT_ROOT = Path(__file__).parent.resolve()

# ═══════════════════════════════════════════
#  模型路径
# ═══════════════════════════════════════════
MODEL_DIR = Path(os.environ.get('FACE_MODEL_DIR', PROJECT_ROOT / 'models'))

# ── 识别模型 ──
RECOGNITION_MODEL_NAME = os.environ.get('FACE_RECOGNITION_MODEL', 'evolve_asia')

RECOGNITION_MODEL_PATHS = {
    'evolve_asia':    MODEL_DIR / 'evolve_ir50_asia.pt',
    'ir_se50':        MODEL_DIR / 'model_ir_se50.pt',
    'mobilefacenet':  MODEL_DIR / 'model_mobilefacenet.pt',
}

RECOGNITION_MODEL_CONFIGS = {
    'evolve_asia':    {'name': 'Evolve IR50 (Asia)', 'input_size': 112, 'embedding_dim': 512},
    'ir_se50':        {'name': 'InsightFace IR-SE50', 'input_size': 112, 'embedding_dim': 512},
    'mobilefacenet':  {'name': 'MobileFaceNet', 'input_size': 112, 'embedding_dim': 512},
}

# ── 检测模型 ──
DETECTION_METHOD = os.environ.get('FACE_DETECTION_METHOD', 'mtcnn')
ULTRALIGHT_PATH = MODEL_DIR / 'ultranet.pt'

# ── 活体检测模型 ──
LIVENESS_MODEL_PATH = MODEL_DIR / '2.7_80x80_MiniFASNetV2.onnx'
LIVENESS_INPUT_SIZE = 80

# ═══════════════════════════════════════════
#  推理参数
# ═══════════════════════════════════════════
RECOGNITION_THRESHOLD = float(os.environ.get('FACE_RECOGNITION_THRESHOLD', '0.4'))
ENABLE_LIVENESS = os.environ.get('FACE_ENABLE_LIVENESS', '1') == '1'

# MTCNN 参数
MTCNN_IMAGE_SIZE = int(os.environ.get('FACE_MTCNN_IMAGE_SIZE', '160'))
MTCNN_MARGIN = int(os.environ.get('FACE_MTCNN_MARGIN', '20'))
MTCNN_MIN_FACE_SIZE = int(os.environ.get('FACE_MTCNN_MIN_FACE', '30'))
MTCNN_KEEP_ALL = True

# UltraLightFace 参数
ULTRA_INPUT_SIZE = int(os.environ.get('FACE_ULTRA_INPUT_SIZE', '320'))

# ═══════════════════════════════════════════
#  人脸数据库
# ═══════════════════════════════════════════
EMBEDDINGS_FILE = str(
    Path(os.environ.get('FACE_EMBEDDINGS_FILE', PROJECT_ROOT / 'face_embeddings.pkl')).resolve()
)
FACE_DB_DIR = Path(os.environ.get('FACE_DB_DIR', PROJECT_ROOT / 'face_db')).resolve()

# ═══════════════════════════════════════════
#  服务器
# ═══════════════════════════════════════════
SERVER_HOST = os.environ.get('FACE_SERVER_HOST', '0.0.0.0')
SERVER_PORT = int(os.environ.get('FACE_SERVER_PORT', '5000'))
CORS_ORIGINS = os.environ.get('FACE_CORS_ORIGINS', '*')
WS_MAX_BUFFER_SIZE = int(os.environ.get('FACE_WS_MAX_BUFFER', str(5 * 1024 * 1024)))

# ═══════════════════════════════════════════
#  安全限制
# ═══════════════════════════════════════════
MAX_UPLOAD_SIZE_MB = int(os.environ.get('FACE_MAX_UPLOAD_MB', '20'))
MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024

# 用户名合法性：中文、英文、数字、短横线、下划线、空格，1-50 字符
VALID_USERNAME_PATTERN = re.compile(r'^[\w一-鿿\-\ ]{1,50}$')

# ═══════════════════════════════════════════
#  输出消息模板
# ═══════════════════════════════════════════
MSG_NAME_EMPTY = '姓名不能为空'
MSG_NO_IMAGE = '未上传图片'
MSG_NO_FILE = '未选择文件'
MSG_IMAGE_DECODE_FAIL = '图片读取失败'
MSG_NO_FACE_DETECTED = '未检测到人脸，请使用正面清晰照片'
MSG_USER_NOT_FOUND = '用户不存在'
MSG_DB_EMPTY = 'face_db 文件夹不存在'
MSG_FILE_TOO_LARGE = f'图片大小不能超过 {MAX_UPLOAD_SIZE_MB}MB'
MSG_INVALID_NAME = '用户名只能包含中文、英文、数字、短横线和空格，长度 1-50 字符'
MSG_MODEL_NOT_FOUND = '模型文件不存在'
MSG_SERVER_ERROR = '服务器内部错误'
MSG_DECODE_FAIL = '解码失败'

# ═══════════════════════════════════════════
#  Spring Boot 智慧安防集成配置
# ═══════════════════════════════════════════
# 同学 Spring Boot 项目地址
SPRING_BOOT_URL = os.environ.get('FACE_SPRING_BOOT_URL', 'http://localhost:8080')

# 是否启用向 Spring Boot 同步识别结果（可随时关闭，方便单独调试人脸识别）
ENABLE_SPRING_SYNC = os.environ.get('FACE_ENABLE_SPRING_SYNC', '1') == '1'

# Spring Boot API 超时（秒），超时则忽略，不阻塞主流程
SPRING_API_TIMEOUT = float(os.environ.get('FACE_SPRING_API_TIMEOUT', '2.0'))

# 是否启用陌生人告警自动创建（等同学实现 /api/face/alarm/create 后开启）
ENABLE_ALARM_CREATE = os.environ.get('FACE_ENABLE_ALARM_CREATE', '0') == '1'

# 陌生人截图保存目录
CAPTURES_DIR = PROJECT_ROOT / 'captures'
CAPTURES_DIR.mkdir(parents=True, exist_ok=True)

# Flask 设备编号（用于向 Spring Boot 标识自己）
FLASK_DEVICE_CODE = os.environ.get('FACE_DEVICE_CODE', 'FLASK_CAM_001')
FLASK_LOCATION = os.environ.get('FACE_LOCATION', '监控区域')
