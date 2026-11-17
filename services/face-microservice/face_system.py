"""
============================================================
人脸识别系统 — 增强版
============================================================
检测:   MTCNN (facenet-pytorch) / UltraLightFace
识别:   Evolve IR50 Asia / InsightFace IR-SE50 / MobileFaceNet
活体:   MiniFASNetV2 (ONNX)
============================================================
"""
import torch
import numpy as np
import os
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

import argparse
import threading
import logging
from pathlib import Path
from PIL import Image, ImageDraw
import pickle

from facenet_pytorch import MTCNN

from config import (
    PROJECT_ROOT, MODEL_DIR,
    RECOGNITION_MODEL_NAME, RECOGNITION_MODEL_PATHS, RECOGNITION_MODEL_CONFIGS,
    DETECTION_METHOD, ULTRALIGHT_PATH, ULTRA_INPUT_SIZE,
    LIVENESS_MODEL_PATH, LIVENESS_INPUT_SIZE,
    RECOGNITION_THRESHOLD, ENABLE_LIVENESS,
    MTCNN_IMAGE_SIZE, MTCNN_MARGIN, MTCNN_MIN_FACE_SIZE, MTCNN_KEEP_ALL,
    EMBEDDINGS_FILE, FACE_DB_DIR,
    MSG_MODEL_NOT_FOUND,
)

logger = logging.getLogger(__name__)

# 活体检测 (ONNX) — 可选依赖
try:
    import onnxruntime as ort
    _HAS_ONNX = True
except ImportError:
    _HAS_ONNX = False


# ═══════════════════════════════════════════
#  Liveness Detector (ONNX)
# ═══════════════════════════════════════════
class LivenessDetector:
    """MiniFASNetV2 活体检测器 — 防止照片/视频攻击

    预处理要求：
      - BGR 格式（OpenCV 风格）
      - 原始像素 [0, 255]
      - 输入尺寸 80x80
      - 输出 3 类：class 1 = real
    """

    def __init__(self, device='cpu'):
        if not _HAS_ONNX:
            raise ImportError('需要安装 onnxruntime: pip install onnxruntime')

        if not LIVENESS_MODEL_PATH.exists():
            raise FileNotFoundError(f'活体模型不存在: {LIVENESS_MODEL_PATH}')

        providers = ['CPUExecutionProvider']
        try:
            self.session = ort.InferenceSession(
                str(LIVENESS_MODEL_PATH),
                providers=providers
            )
        except Exception as e:
            raise RuntimeError(f'活体模型加载失败 ({LIVENESS_MODEL_PATH.name}): {e}') from e

        self.input_name = self.session.get_inputs()[0].name
        self.device = device
        print(f'  Liveness: MiniFASNetV2 (ONNX) ready')

    def predict(self, face_image):
        """
        对单张人脸 crop 进行活体检测。
        face_image: PIL Image — 原始人脸裁剪（RGB任意尺寸）
        返回: (is_real: bool, score: float, detail: dict)
        异常时返回 (False, 0.0, {'error': str})
        """
        try:
            face_resized = face_image.resize((LIVENESS_INPUT_SIZE, LIVENESS_INPUT_SIZE))
            img_np = np.array(face_resized)[:, :, ::-1]  # RGB → BGR
            img_np = img_np.transpose(2, 0, 1).astype(np.float32)  # CHW
            img_np = img_np[np.newaxis, :, :, :]  # (1, 3, 80, 80)

            outputs = self.session.run(None, {self.input_name: img_np})
            logits = outputs[0][0]  # (3,)

            exp = np.exp(logits - np.max(logits))
            probs = exp / exp.sum()

            # 类别顺序: [spoof_replay, real, spoof_print], index 1 = real
            real_score = float(probs[1])
            is_real = real_score > 0.5

            return is_real, real_score, {
                'real': round(float(probs[1]), 4),
                'spoof_print': round(float(probs[2]), 4),
                'spoof_replay': round(float(probs[0]), 4),
            }
        except Exception as e:
            print(f'  [Liveness] predict error: {e}')
            return False, 0.0, {'error': str(e)}


# ═══════════════════════════════════════════
#  Face Recognition System
# ═══════════════════════════════════════════
class FaceRecognitionSystem:

    def __init__(self,
                 embeddings_file=None,
                 device=None,
                 recognition_threshold=None,
                 recognition_model=None,
                 enable_liveness=None,
                 detection_method=None):
        """
        recognition_model: 'evolve_asia' | 'ir_se50' | 'mobilefacenet'
        detection_method:  'mtcnn' | 'ultralight'
        enable_liveness:   是否启用人脸活体检测
        """
        # ── 应用配置默认值 ──
        if device is None:
            self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        else:
            self.device = torch.device(device)

        self.recognition_model_name = recognition_model or RECOGNITION_MODEL_NAME
        self.detection_method = detection_method or DETECTION_METHOD
        self.threshold = recognition_threshold if recognition_threshold is not None else RECOGNITION_THRESHOLD
        self.embeddings_file = str(Path(embeddings_file or EMBEDDINGS_FILE).resolve())
        _enable_liveness = enable_liveness if enable_liveness is not None else ENABLE_LIVENESS

        print(f'Device: {self.device}')

        # ── 模型配置校验 ──
        if self.recognition_model_name not in RECOGNITION_MODEL_CONFIGS:
            raise ValueError(
                f'未知识别模型: {self.recognition_model_name}. '
                f'可选: {list(RECOGNITION_MODEL_CONFIGS.keys())}'
            )
        self.model_config = RECOGNITION_MODEL_CONFIGS[self.recognition_model_name]
        self.input_size = self.model_config['input_size']

        print(f'Recognition: {self.model_config["name"]}')
        print(f'Detection:   {"UltraLightFace" if self.detection_method == "ultralight" else "MTCNN"}')

        # ── 人脸数据库并发锁 ──
        self._db_lock = threading.Lock()

        # ── 加载模型 ──
        self._load_detection_model()
        self._load_recognition_model()
        self._load_liveness_model(_enable_liveness)

        print('Models loaded\n')

        # ── 加载人脸库 ──
        self.face_db = {}
        self._load_embeddings()

    # ═══════════════════════════════════════════
    #  Model loaders (with error handling)
    # ═══════════════════════════════════════════

    def _load_detection_model(self):
        if self.detection_method == 'ultralight':
            self._load_ultralight()
        else:
            self._load_mtcnn()

    def _load_mtcnn(self):
        print('Loading MTCNN...')
        try:
            self.mtcnn = MTCNN(
                image_size=MTCNN_IMAGE_SIZE, margin=MTCNN_MARGIN,
                keep_all=MTCNN_KEEP_ALL, min_face_size=MTCNN_MIN_FACE_SIZE,
                device=self.device
            )
        except Exception as e:
            raise RuntimeError(f'MTCNN 加载失败: {e}') from e
        self._detector = 'mtcnn'

    def _load_ultralight(self):
        print('Loading UltraLightFace...')
        if not ULTRALIGHT_PATH.exists():
            print(f'  UltraLightFace not found ({ULTRALIGHT_PATH}), falling back to MTCNN')
            self._load_mtcnn()
            return
        try:
            self.ultranet = torch.jit.load(str(ULTRALIGHT_PATH), map_location=self.device)
            self.ultranet.eval()
        except Exception as e:
            print(f'  UltraLightFace 加载失败 ({e}), falling back to MTCNN')
            ULTRALIGHT_PATH.unlink(missing_ok=True)  # 删除损坏文件
            self._load_mtcnn()
            return
        self._detector = 'ultralight'
        self._ultra_input_size = ULTRA_INPUT_SIZE

    def _load_recognition_model(self):
        recog_path = RECOGNITION_MODEL_PATHS[self.recognition_model_name]
        if not recog_path.exists():
            raise FileNotFoundError(f'{MSG_MODEL_NOT_FOUND}: {recog_path}')
        print(f'Loading {recog_path.name}...')
        try:
            self.recognizer = torch.jit.load(str(recog_path), map_location=self.device)
            self.recognizer.eval()
        except Exception as e:
            raise RuntimeError(f'识别模型加载失败 ({recog_path.name}): {e}') from e

    def _load_liveness_model(self, enable_liveness):
        self.liveness_detector = None
        if enable_liveness:
            try:
                self.liveness_detector = LivenessDetector(device=str(self.device))
            except (ImportError, FileNotFoundError, RuntimeError) as e:
                print(f'  ⚠️  活体检测不可用: {e}')

    # ═══════════════════════════════════════════
    #  Face DB — 线程安全读写
    # ═══════════════════════════════════════════

    def _load_embeddings(self):
        path = self.embeddings_file
        if not os.path.exists(path):
            print('Face DB empty\n')
            self.face_db = {}
            return

        try:
            with open(path, 'rb') as f:
                data = pickle.load(f)
        except Exception as e:
            print(f'Load failed ({e}), starting with empty DB')
            self.face_db = {}
            return

        # ── 结构校验 ──
        if not isinstance(data, dict):
            print(f'Load failed: data is not dict (got {type(data).__name__}), new DB')
            self.face_db = {}
            return

        validated = {}
        dropped = 0
        for name, embeddings in data.items():
            if not isinstance(name, str) or not name.strip():
                dropped += 1
                continue
            if not isinstance(embeddings, list):
                dropped += 1
                continue
            valid_embs = [e for e in embeddings if isinstance(e, torch.Tensor)]
            if len(valid_embs) < len(embeddings):
                dropped += len(embeddings) - len(valid_embs)
            if valid_embs:
                validated[name] = valid_embs

        self.face_db = validated
        total = sum(len(v) for v in self.face_db.values())
        print(f'Face DB: {len(self.face_db)} people, {total} embeddings')
        if dropped:
            print(f'  (dropped {dropped} invalid entries)')
        for name, embs in self.face_db.items():
            print(f'  {name}: {len(embs)}')
        print()

    def _save_embeddings(self):
        """保存人脸特征库（调用方需持有 _db_lock）"""
        try:
            with open(self.embeddings_file, 'wb') as f:
                pickle.dump(self.face_db, f)
            print(f'Saved: {self.embeddings_file}')
        except (OSError, IOError, pickle.PickleError) as e:
            print(f'ERROR: 保存人脸库失败: {e}')
            raise

    # ═══════════════════════════════════════════
    #  Detection
    # ═══════════════════════════════════════════

    def _open_image(self, source):
        """将路径或 PIL Image 统一转为 RGB Image。返回 None 表示失败。"""
        if isinstance(source, (str, Path)):
            try:
                return Image.open(source).convert('RGB')
            except Exception as e:
                print(f'Image open error ({source}): {e}')
                return None
        return source  # 已经是 PIL Image

    def extract_face(self, image):
        """MTCNN 检测单张人脸，返回 face tensor 或 None"""
        img = self._open_image(image)
        if img is None:
            return None
        try:
            return self.mtcnn(img)
        except Exception as e:
            print(f'Detection error: {e}')
            return None

    def detect_faces(self, image, with_landmarks=False):
        """检测所有人脸框，可选返回5点关键点。失败返回空。"""
        img = self._open_image(image)
        if img is None:
            return (None, None) if with_landmarks else None
        try:
            if with_landmarks:
                boxes, probs, landmarks = self.mtcnn.detect(img, landmarks=True)
                return boxes, landmarks
            boxes, _ = self.mtcnn.detect(img)
            return boxes
        except Exception as e:
            print(f'detect_faces error: {e}')
            return (None, None) if with_landmarks else None

    def detect_with_landmarks(self, image):
        """
        检测人脸 + 5点关键点，返回结构化列表。
        返回: [(box, landmarks, score), ...]
          box:       [x1, y1, x2, y2]
          landmarks: [[x,y]*5]  左眼/右眼/鼻子/左嘴角/右嘴角
          score:     置信度
        """
        img = self._open_image(image)
        if img is None:
            return []
        try:
            boxes, probs, landmarks = self.mtcnn.detect(img, landmarks=True)
        except Exception as e:
            print(f'detect_with_landmarks error: {e}')
            return []

        if boxes is None:
            return []
        results = []
        for i, box in enumerate(boxes):
            pts = landmarks[i] if landmarks is not None else None
            score = float(probs[i]) if probs is not None else 0.0
            results.append((box, pts, score))
        return results

    def crop_and_align(self, image, box):
        """裁剪人脸区域 + MTCNN 对齐。失败返回 None。"""
        try:
            x1, y1, x2, y2 = [max(0, int(v)) for v in box]
            w_img, h_img = image.size
            mw = int((x2 - x1) * 0.3)
            mh = int((y2 - y1) * 0.3)
            x1, y1 = max(0, x1 - mw), max(0, y1 - mh)
            x2, y2 = min(w_img, x2 + mw), min(h_img, y2 + mh)
            if x2 <= x1 or y2 <= y1:
                return None
            face = image.crop((x1, y1, x2, y2))
            return self.extract_face(face)
        except Exception as e:
            print(f'crop_and_align error: {e}')
            return None

    # ═══════════════════════════════════════════
    #  Recognition
    # ═══════════════════════════════════════════

    def extract_embedding(self, face_tensor):
        """
        MTCNN 输出 (3,160,160) → resize to model input size → recognizer → embedding
        失败返回 None。
        """
        if face_tensor is None:
            return None
        try:
            if face_tensor.dim() == 3:
                face_tensor = face_tensor.unsqueeze(0)

            if face_tensor.shape[-1] != self.input_size:
                face_tensor = torch.nn.functional.interpolate(
                    face_tensor, size=(self.input_size, self.input_size),
                    mode='bilinear', align_corners=False)

            face_tensor = face_tensor.to(self.device)
            with torch.no_grad():
                embedding = self.recognizer(face_tensor)
            return embedding.cpu()
        except Exception as e:
            print(f'extract_embedding error: {e}')
            return None

    def _find_best_match(self, embedding):
        """在 face_db 中查找最佳匹配。返回 (name, similarity)"""
        if embedding is None:
            return None, 0
        with self._db_lock:
            if not self.face_db:
                return None, 0
            best_name, best_sim = None, 0
            for name, embeddings in self.face_db.items():
                for emb in embeddings:
                    try:
                        sim = float(torch.nn.functional.cosine_similarity(
                            embedding, emb).item())
                    except Exception:
                        continue
                    if sim > best_sim:
                        best_sim = sim
                        best_name = name
        return best_name, best_sim

    # ═══════════════════════════════════════════
    #  Register
    # ═══════════════════════════════════════════

    def register(self, name, image_path):
        """注册一个人脸。返回 (success: bool, message: str)"""
        print(f'\nRegister: {name} ({image_path})')
        path = Path(image_path)

        # ── 路径安全校验 ──
        try:
            resolved = path.resolve()
        except (OSError, ValueError) as e:
            msg = f'路径无效: {e}'
            print(f'  {msg}')
            return False, msg

        if not resolved.exists():
            msg = f'文件不存在: {image_path}'
            print(f'  {msg}')
            return False, msg

        # ── 检查是否在允许目录内（仅对 face_db 目录内的文件做限制） ──
        try:
            resolved.relative_to(FACE_DB_DIR)
        except ValueError:
            # 不在 face_db 内也允许，CLI 可能从任意位置注册
            pass

        # ── 打开图片 ──
        try:
            img = Image.open(str(resolved)).convert('RGB')
        except Exception as e:
            msg = f'图片读取失败: {e}'
            print(f'  {msg}')
            return False, msg

        # ── 检测 + 提取特征 ──
        face = self.extract_face(img)
        if face is None:
            msg = '未检测到人脸'
            print(f'  {msg}')
            return False, msg

        embedding = self.extract_embedding(face)
        if embedding is None:
            msg = '特征提取失败'
            print(f'  {msg}')
            return False, msg

        # ── 写入人脸库 ──
        with self._db_lock:
            if name not in self.face_db:
                self.face_db[name] = []
            self.face_db[name].append(embedding)
            try:
                self._save_embeddings()
            except Exception as e:
                # 回滚
                self.face_db[name].pop()
                if not self.face_db[name]:
                    del self.face_db[name]
                msg = f'保存失败: {e}'
                print(f'  {msg}')
                return False, msg

        print(f'  SUCCESS ({len(self.face_db[name])} total)')
        return True, f'SUCCESS ({len(self.face_db[name])} total)'

    def register_folder(self, folder_path):
        """批量注册文件夹中的人脸"""
        folder = Path(folder_path)
        try:
            folder = folder.resolve()
        except (OSError, ValueError):
            print(f'路径无效: {folder_path}')
            return

        if not folder.exists():
            print(f'Not found: {folder_path}')
            return

        if not folder.is_dir():
            print(f'Not a directory: {folder_path}')
            return

        print(f'\nBatch: {folder}')
        count = 0
        for person_dir in sorted(folder.iterdir()):
            if not person_dir.is_dir():
                continue
            for img_file in person_dir.iterdir():
                if img_file.suffix.lower() in ('.jpg', '.jpeg', '.png', '.bmp'):
                    ok, _ = self.register(person_dir.name, str(img_file))
                    if ok:
                        count += 1
        print(f'Done: {count} faces')

    # ═══════════════════════════════════════════
    #  Recognize
    # ═══════════════════════════════════════════

    def recognize(self, image_path, show_result=True):
        """识别图片中的人脸。返回结果列表。"""
        print(f'\nRecognize: {image_path}')
        path = Path(image_path)
        if not path.exists():
            print('File not found')
            return []

        with self._db_lock:
            if not self.face_db:
                print('DB empty!')
                return []

        try:
            img = Image.open(str(path)).convert('RGB')
        except Exception as e:
            print(f'Image open error: {e}')
            return []

        boxes = self.detect_faces(img)
        if boxes is None or len(boxes) == 0:
            print('No face detected')
            return []

        results = []
        for i, box in enumerate(boxes):
            face_tensor = self.crop_and_align(img, box)
            if face_tensor is None:
                results.append({
                    'face_index': i + 1, 'best_match': 'Unknown',
                    'similarity': 0, 'is_match': False, 'all_candidates': []
                })
                continue

            embedding = self.extract_embedding(face_tensor)
            if embedding is None:
                results.append({
                    'face_index': i + 1, 'best_match': 'Unknown',
                    'similarity': 0, 'is_match': False, 'all_candidates': []
                })
                continue

            # 计算与所有注册用户的相似度
            candidates = []
            with self._db_lock:
                for name, embs in self.face_db.items():
                    for emb in embs:
                        try:
                            s = float(torch.nn.functional.cosine_similarity(
                                embedding, emb).item())
                            candidates.append((name, s))
                        except Exception:
                            continue

            candidates.sort(key=lambda x: x[1], reverse=True)
            best_name, best_sim = candidates[0] if candidates else ('Unknown', 0)
            matched = best_sim >= self.threshold

            results.append({
                'face_index': i + 1,
                'bbox': [max(0, int(v)) for v in box],
                'best_match': best_name,
                'similarity': best_sim,
                'is_match': matched,
                'all_candidates': candidates[:5]
            })

            if show_result:
                label = f'[MATCH] {best_name}' if matched else '[STRANGER]'
                print(f'  Face#{i + 1}: {label} {best_sim:.2%}')
                for n, s in candidates[:3]:
                    print(f'    {n}: {s:.2%}')

        return results

    def recognize_with_liveness(self, image_path):
        """识别 + 活体检测"""
        results = self.recognize(image_path, show_result=False)
        if not results:
            return results

        if self.liveness_detector is None:
            for r in results:
                r['liveness'] = None
            return results

        img = self._open_image(image_path)
        if img is None:
            return results

        boxes = self.detect_faces(img)
        if boxes is None:
            return results

        for i, (result, box) in enumerate(zip(results, boxes)):
            if box is None:
                result['liveness'] = None
                continue

            try:
                x1, y1, x2, y2 = [max(0, int(v)) for v in box]
                mw = int((x2 - x1) * 0.3)
                mh = int((y2 - y1) * 0.3)
                cx1 = max(0, x1 - mw)
                cy1 = max(0, y1 - mh)
                cx2 = min(img.width, x2 + mw)
                cy2 = min(img.height, y2 + mh)
                if cx2 > cx1 and cy2 > cy1:
                    face_crop = img.crop((cx1, cy1, cx2, cy2))
                    is_real, score, detail = self.liveness_detector.predict(face_crop)
                    result['liveness'] = {
                        'is_real': is_real,
                        'score': round(score * 100, 2),
                        'detail': detail
                    }
                else:
                    result['liveness'] = None
            except Exception as e:
                result['liveness'] = {'error': str(e)}

        return results

    # ═══════════════════════════════════════════
    #  Recognize & Draw
    # ═══════════════════════════════════════════

    def recognize_and_draw(self, image_path, output_path=None):
        """识别并在图片上画框标注"""
        path = Path(image_path)
        if output_path is None:
            output_path = str(path.parent / f'{path.stem}_recognized{path.suffix}')

        try:
            img = Image.open(str(path)).convert('RGB')
        except Exception as e:
            print(f'Image open error: {e}')
            return

        draw = ImageDraw.Draw(img)
        boxes = self.detect_faces(img)
        if boxes is None or len(boxes) == 0:
            print('No face detected')
            try:
                img.save(output_path)
            except Exception as e:
                print(f'Save error: {e}')
            return

        for box in boxes:
            x1, y1, x2, y2 = [max(0, int(v)) for v in box]
            face_tensor = self.crop_and_align(img, box)
            if face_tensor is None:
                name, sim, color = '?', 0, 'red'
            else:
                embedding = self.extract_embedding(face_tensor)
                if embedding is not None:
                    name, sim = self._find_best_match(embedding)
                else:
                    name, sim = '?', 0
                color = 'green' if sim >= self.threshold else 'orange'

            draw.rectangle([x1, y1, x2, y2], outline=color, width=3)
            draw.text((x1, max(0, y1 - 18)), f'{name} ({sim:.1%})', fill=color)

        try:
            img.save(output_path)
            print(f'Saved: {output_path}')
        except Exception as e:
            print(f'Save error: {e}')

    # ═══════════════════════════════════════════
    #  User Management
    # ═══════════════════════════════════════════

    def list_users(self):
        with self._db_lock:
            if not self.face_db:
                print('DB empty')
                return
            print(f'\nRegistered ({len(self.face_db)} people):')
            for name, embs in sorted(self.face_db.items()):
                print(f'  {name}: {len(embs)} embeddings')
            print(f'  Threshold: {self.threshold:.0%}')

    def get_users_snapshot(self):
        """返回 face_db 的快照（线程安全）"""
        with self._db_lock:
            return {name: list(embs) for name, embs in self.face_db.items()}

    def delete_user(self, name):
        with self._db_lock:
            if name not in self.face_db:
                print(f'Not found: {name}')
                return False
            count = len(self.face_db[name])
            del self.face_db[name]
            try:
                self._save_embeddings()
            except Exception as e:
                print(f'ERROR: 删除后保存失败: {e}')
                return False
            print(f'Deleted: {name} ({count} embeddings)')
            return True

    def get_user_count(self):
        """返回已注册用户数量（线程安全）"""
        with self._db_lock:
            return len(self.face_db)

    def get_user_names(self):
        """返回已注册用户名列表（线程安全）"""
        with self._db_lock:
            return list(self.face_db.keys())

    def info(self):
        print(f'\n{"=" * 50}')
        print(f'  Face Recognition System (Enhanced)')
        print(f'  Detection:    {"UltraLightFace" if self._detector == "ultralight" else "MTCNN"}')
        print(f'  Recognition:  {self.model_config["name"]}')
        print(f'  Liveness:     {"MiniFASNetV2 ONNX" if self.liveness_detector else "Disabled"}')
        print(f'  Device:       {self.device}')
        with self._db_lock:
            print(f'  Registered:   {len(self.face_db)} people')
        print(f'  Threshold:    {self.threshold:.0%}')
        print(f'{"=" * 50}\n')


# ═══════════════════════════════════════════
#  CLI
# ═══════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description='Face Recognition System (Enhanced)')
    sub = parser.add_subparsers(dest='command')

    model_choices = list(RECOGNITION_MODEL_CONFIGS.keys())

    p = sub.add_parser('register')
    p.add_argument('--name', required=True)
    p.add_argument('--image', required=True)
    p.add_argument('--threshold', type=float, default=RECOGNITION_THRESHOLD)
    p.add_argument('--model', default=RECOGNITION_MODEL_NAME, choices=model_choices)

    p = sub.add_parser('register-folder')
    p.add_argument('--folder', required=True)
    p.add_argument('--threshold', type=float, default=RECOGNITION_THRESHOLD)
    p.add_argument('--model', default=RECOGNITION_MODEL_NAME, choices=model_choices)

    p = sub.add_parser('recognize')
    p.add_argument('--image', required=True)
    p.add_argument('--draw', action='store_true')
    p.add_argument('--output', default=None)
    p.add_argument('--threshold', type=float, default=RECOGNITION_THRESHOLD)
    p.add_argument('--model', default=RECOGNITION_MODEL_NAME, choices=model_choices)
    p.add_argument('--liveness', action='store_true', help='启用活体检测')

    sub.add_parser('list')
    p = sub.add_parser('delete')
    p.add_argument('--name', required=True)
    sub.add_parser('info')

    args = parser.parse_args()
    if args.command is None:
        parser.print_help()
        return

    model = getattr(args, 'model', RECOGNITION_MODEL_NAME)
    threshold = getattr(args, 'threshold', RECOGNITION_THRESHOLD)
    use_liveness = getattr(args, 'liveness', False)

    try:
        system = FaceRecognitionSystem(
            recognition_threshold=threshold,
            recognition_model=model,
            enable_liveness=use_liveness
        )
    except Exception as e:
        print(f'FATAL: 系统初始化失败: {e}')
        sys.exit(1)

    if args.command == 'register':
        system.register(args.name, args.image)
    elif args.command == 'register-folder':
        system.register_folder(args.folder)
    elif args.command == 'recognize':
        if args.draw:
            system.recognize_and_draw(args.image, args.output)
        elif use_liveness:
            results = system.recognize_with_liveness(args.image)
            for r in results:
                l = r.get('liveness', {})
                if l and 'is_real' in l:
                    tag = 'REAL' if l['is_real'] else 'SPOOF'
                    print(f'  Face#{r["face_index"]}: Liveness [{tag}] ({l["score"]:.1f}%)')
        else:
            system.recognize(args.image)
    elif args.command == 'list':
        system.list_users()
    elif args.command == 'delete':
        system.delete_user(args.name)
    elif args.command == 'info':
        system.info()


if __name__ == '__main__':
    main()
