"""
============================================================
语音合成 (TTS) 引擎 — 基于 sherpa-onnx + VITS
============================================================
单例模式 · 懒加载 · 优雅降级
============================================================
"""
import io
import base64
import wave
import threading
import logging
from pathlib import Path

import numpy as np

from config import PROJECT_ROOT, MODEL_DIR

logger = logging.getLogger(__name__)

# ═══════════════════════════════════════════
#  TTS 配置 — 可通过环境变量覆盖
# ═══════════════════════════════════════════
TTS_MODEL_DIR = Path(MODEL_DIR) / 'tts'
TTS_MODEL_ONNX = str(TTS_MODEL_DIR / 'model.onnx')
TTS_LEXICON = str(TTS_MODEL_DIR / 'lexicon.txt')
TTS_TOKENS = str(TTS_MODEL_DIR / 'tokens.txt')
TTS_RULE_FSTS = ','.join([
    str(TTS_MODEL_DIR / 'phone.fst'),
    str(TTS_MODEL_DIR / 'date.fst'),
    str(TTS_MODEL_DIR / 'number.fst'),
])

# 默认说话人 SID (0-173)
TTS_DEFAULT_SID = 0

# 音频采样率 (vits-icefall-zh-aishell3 为 8000 Hz)
TTS_SAMPLE_RATE = 8000


class TTSEngine:
    """
    语音合成引擎 — 单例、线程安全、懒加载。

    用法:
        engine = TTSEngine()
        result = engine.synthesize("你好，欢迎使用人脸识别系统。")
        # result = {"ok": True, "audio_base64": "...", "sample_rate": 8000, "duration": 3.9}
    """

    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        # __init__ may be called multiple times in singleton, guard it
        if hasattr(self, '_initialized'):
            return
        self._initialized = False
        self._tts = None
        self._error = None
        self._load_lock = threading.Lock()

    # ═══════════════════════════════════════════
    #  模型加载（懒加载，线程安全）
    # ═══════════════════════════════════════════

    def _ensure_loaded(self) -> bool:
        """确保模型已加载。已加载返回 True；加载失败返回 False（可通过 error 属性获取原因）。"""
        if self._initialized and self._tts is not None:
            return True

        if self._error:
            return False

        with self._load_lock:
            # 双重检查
            if self._initialized and self._tts is not None:
                return True
            if self._error:
                return False

            try:
                self._load_model()
                self._initialized = True
                return True
            except Exception as e:
                self._error = str(e)
                logger.error(f'TTSEngine 初始化失败: {e}')
                return False

    def _load_model(self):
        """实际加载模型 — 仅在 _load_lock 内调用"""
        import sherpa_onnx

        # ── 检查必需文件 ──
        model_path = Path(TTS_MODEL_ONNX)
        if not model_path.exists():
            raise FileNotFoundError(
                f'TTS 模型文件不存在: {model_path}\n'
                f'请将 sherpa-onnx TTS 模型（vits-icefall-zh-aishell3）解压到 {TTS_MODEL_DIR}/ 目录。\n'
                f'模型下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2'
            )

        lexicon_path = Path(TTS_LEXICON)
        if not lexicon_path.exists():
            raise FileNotFoundError(f'TTS 发音词典不存在: {lexicon_path}')

        tokens_path = Path(TTS_TOKENS)
        if not tokens_path.exists():
            raise FileNotFoundError(f'TTS token 文件不存在: {tokens_path}')

        # ── 构建配置 ──
        vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=TTS_MODEL_ONNX,
            lexicon=TTS_LEXICON,
            tokens=TTS_TOKENS,
        )

        model_config = sherpa_onnx.OfflineTtsModelConfig(vits=vits_config)

        # 检查 FST 文件是否存在
        rule_fsts = []
        for fst_path in [str(TTS_MODEL_DIR / 'phone.fst'),
                         str(TTS_MODEL_DIR / 'date.fst'),
                         str(TTS_MODEL_DIR / 'number.fst')]:
            if Path(fst_path).exists():
                rule_fsts.append(fst_path)

        tts_config = sherpa_onnx.OfflineTtsConfig(
            model=model_config,
            rule_fsts=','.join(rule_fsts) if rule_fsts else '',
        )

        logger.info('TTSEngine: 加载模型...')
        self._tts = sherpa_onnx.OfflineTts(tts_config)
        logger.info('TTSEngine: 模型就绪')

    # ═══════════════════════════════════════════
    #  语音合成
    # ═══════════════════════════════════════════

    @property
    def is_available(self) -> bool:
        """模型是否已加载可用"""
        return self._initialized and self._tts is not None

    @property
    def model_error(self) -> str | None:
        """模型加载失败的原因（None 表示无错误）"""
        return self._error

    @property
    def model_loaded(self) -> bool:
        """模型是否已成功加载（同 is_available）"""
        return self.is_available

    def synthesize(self, text: str, speed: float = 1.0, pitch: float = 1.0,
                   sid: int = None) -> dict:
        """
        将文本合成为语音，返回 Base64 编码的 WAV 音频。

        Args:
            text: 要合成的文本（中文）
            speed: 语速倍率。1.0 = 正常，> 1.0 更快，< 1.0 更慢
            pitch: 音高倍率。**注意：VITS 模型不支持音高调节，此参数保留无实际效果。**
            sid: 说话人 ID (0-173)。默认使用 SID 0。

        Returns:
            {"ok": True,  "audio_base64": "...", "sample_rate": 8000, "duration": 3.9}
            {"ok": False, "error": "错误信息"}
        """
        import sherpa_onnx

        if sid is None:
            sid = TTS_DEFAULT_SID

        # ── 确保模型已加载 ──
        if not self._ensure_loaded():
            return {
                'ok': False,
                'error': f'TTS 引擎不可用: {self._error or "模型加载失败"}',
                'code': 'TTS_NOT_READY',
            }

        # ── 参数校验 ──
        text = text.strip()
        if not text:
            return {
                'ok': False,
                'error': '文本不能为空',
                'code': 'EMPTY_TEXT',
            }

        if len(text) > 300:
            return {
                'ok': False,
                'error': f'文本过长 ({len(text)} 字符)，最多支持 300 字符',
                'code': 'TEXT_TOO_LONG',
            }

        # ── 生成音频 ──
        try:
            logger.info(f'TTSEngine: 合成中 "{text[:30]}{"..." if len(text)>30 else ""}" (speed={speed})')
            result = self._tts.generate(text, sid=sid, speed=speed)
        except Exception as e:
            logger.exception('TTSEngine: 合成失败')
            return {
                'ok': False,
                'error': f'语音合成失败: {str(e)}',
                'code': 'SYNTHESIS_FAILED',
            }

        # ── 转换为 Base64 WAV ──
        try:
            audio_b64 = self._samples_to_base64_wav(result.samples, result.sample_rate)
            duration = len(result.samples) / result.sample_rate
            logger.info(f'TTSEngine: 合成完成 — {duration:.1f}s 音频')

            return {
                'ok': True,
                'audio_base64': audio_b64,
                'sample_rate': result.sample_rate,
                'duration': round(duration, 2),
            }
        except Exception as e:
            logger.exception('TTSEngine: WAV 编码失败')
            return {
                'ok': False,
                'error': f'音频编码失败: {str(e)}',
                'code': 'ENCODING_FAILED',
            }

    # ═══════════════════════════════════════════
    #  工具：samples → WAV bytes → Base64
    # ═══════════════════════════════════════════

    @staticmethod
    def _samples_to_base64_wav(samples: np.ndarray, sample_rate: int) -> str:
        """将 float32 采样数组转换为 Base64 编码的 WAV 字符串"""
        # float32 → int16
        samples_int16 = (np.clip(samples, -1.0, 1.0) * 32767).astype('<i2')

        # 写入内存 WAV
        buf = io.BytesIO()
        with wave.open(buf, 'wb') as wf:
            wf.setnchannels(1)          # 单声道
            wf.setsampwidth(2)           # 16-bit
            wf.setframerate(sample_rate)
            wf.writeframes(samples_int16.tobytes())

        buf.seek(0)
        wav_bytes = buf.read()
        return base64.b64encode(wav_bytes).decode('utf-8')

    # ═══════════════════════════════════════════
    #  重新加载（用于模型切换或恢复）
    # ═══════════════════════════════════════════

    def reload(self):
        """强制重新加载模型"""
        with self._load_lock:
            self._initialized = False
            self._tts = None
            self._error = None
        self._ensure_loaded()


# ═══════════════════════════════════════════
#  模块级便捷函数（保证线程安全复用）
# ═══════════════════════════════════════════

_engine: TTSEngine | None = None


def get_engine() -> TTSEngine:
    """获取全局唯一 TTSEngine 实例"""
    global _engine
    if _engine is None:
        _engine = TTSEngine()
    return _engine
