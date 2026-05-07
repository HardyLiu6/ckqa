#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinerU PDF解析脚本 (v2.0)
========================
将课程PDF文件上传到MinerU云端进行解析，获取结构化JSON结果。
支持MinIO存储和MySQL元数据管理，通过MD5校验避免重复上传。
当前版本支持同一课程下管理多份 PDF，后续操作可通过 `--file-id`
或 `--file-name` 精确指定目标文件。

目录结构:
    project_root/
    ├── data/
    │   └── {课程id}/
    │       ├── book.pdf          # 本地上传源（可选）
    │       └── slides.pdf
    ├── scripts/
    │   └── pdf_processor/
    │       ├── mineru_parser.py  # 主脚本
    │       ├── storage_service.py
    │       └── db_service.py
    ├── sql/
    │   └── init.sql              # 数据库初始化脚本
    ├── .env
    └── .temp/                    # 临时文件目录

使用方法 (在项目根目录运行):
    # 上传PDF文件
    python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf

    # 同一课程可继续上传其他PDF
    python scripts/pdf_processor/mineru_parser.py upload os -f data/os/slides.pdf

    # 解析指定文件
    python scripts/pdf_processor/mineru_parser.py parse os --file-name book.pdf

    # 上传并立即解析
    python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse

    # 先列出课程下文件，再按 file_id 查看状态 / 导出 / 下载
    python scripts/pdf_processor/mineru_parser.py list
    python scripts/pdf_processor/mineru_parser.py status os --file-id 3
    python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section
    python scripts/pdf_processor/mineru_parser.py download os --file-id 3 -o ./output
"""

import requests
import json
import time
import argparse
import os
import sys
import zipfile
import io
import shutil
import logging
from pathlib import Path
from dataclasses import dataclass
from typing import Callable, Optional, Dict, Any

# 尝试导入依赖
try:
    from dotenv import load_dotenv
except ImportError:
    print("错误: 请先安装依赖库")
    print("运行: pip install -r requirements.txt")
    sys.exit(1)

# 导入自定义模块
from storage_service import MinIOService, MinIOConfig
from db_service import (
    DatabaseService, MySQLConfig, ParseStatus, ResultType, infer_result_type
)
from graphrag_exporter import GraphRAGExporter, ExportOptions


# ===================== 配置类 =====================

@dataclass
class Config:
    """主配置类"""
    # MinerU API配置
    api_token: str
    api_base_url: str
    
    # MinIO配置
    minio: MinIOConfig
    
    # MySQL配置
    mysql: MySQLConfig
    
    # 路径配置
    project_root: Path
    temp_dir: str
    
    # 解析配置
    model_version: str
    language: str
    enable_formula: bool
    enable_table: bool
    enable_ocr: bool
    
    # 运行配置
    timeout: int
    poll_interval: int
    progress_poll_interval: int
    log_level: str
    
    @classmethod
    def from_env(cls, env_path: Optional[str] = None) -> "Config":
        """从环境变量加载配置"""
        # 计算项目根目录
        script_dir = Path(__file__).resolve().parent
        default_project_root = script_dir.parent.parent
        
        # 加载.env文件
        if env_path:
            load_dotenv(env_path)
        else:
            possible_paths = [
                default_project_root / ".env",
                Path.cwd() / ".env",
                script_dir / ".env",
            ]
            for path in possible_paths:
                if path.exists():
                    load_dotenv(path)
                    break
        
        def str_to_bool(value: str) -> bool:
            return value.lower() in ("true", "1", "yes", "on")
        
        project_root_str = os.getenv("PROJECT_ROOT", "")
        if project_root_str and project_root_str != ".":
            project_root = Path(project_root_str)
        else:
            project_root = default_project_root
        
        poll_interval = int(os.getenv("POLL_INTERVAL", "5"))
        progress_poll_interval = int(os.getenv(
            "MINERU_PROGRESS_POLL_INTERVAL",
            str(min(poll_interval, 2)),
        ))

        return cls(
            api_token=os.getenv("MINERU_API_TOKEN", ""),
            api_base_url=os.getenv("MINERU_API_BASE_URL", "https://mineru.net/api/v4"),
            minio=MinIOConfig.from_env(),
            mysql=MySQLConfig.from_env(),
            project_root=project_root,
            temp_dir=os.getenv("TEMP_DIR", ".temp"),
            model_version=os.getenv("MODEL_VERSION", "vlm"),
            language=os.getenv("LANGUAGE", "ch"),
            enable_formula=str_to_bool(os.getenv("ENABLE_FORMULA", "true")),
            enable_table=str_to_bool(os.getenv("ENABLE_TABLE", "true")),
            enable_ocr=str_to_bool(os.getenv("ENABLE_OCR", "true")),
            timeout=int(os.getenv("TIMEOUT", "600")),
            poll_interval=poll_interval,
            progress_poll_interval=max(1, progress_poll_interval),
            log_level=os.getenv("LOG_LEVEL", "INFO"),
        )
    
    def get_temp_path(self, *parts) -> Path:
        """获取临时文件路径"""
        path = self.project_root / self.temp_dir
        for part in parts:
            path = path / part
        path.parent.mkdir(parents=True, exist_ok=True)
        return path
    
    def validate(self) -> list[str]:
        """验证配置"""
        errors = []
        
        if not self.api_token or self.api_token == "your_api_token_here":
            errors.append("MINERU_API_TOKEN 未配置或无效")
        
        if self.model_version not in ("vlm", "pipeline"):
            errors.append(f"MODEL_VERSION 无效: {self.model_version}")
        
        if self.language not in ("ch", "en"):
            errors.append(f"LANGUAGE 无效: {self.language}")
        
        return errors


# ===================== MinerU解析器 =====================

class MinerUParser:
    """MinerU PDF解析器"""
    
    def __init__(self, config: Config):
        self.config = config
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {config.api_token}"
        }
        self.logger = logging.getLogger("MinerUParser")
    
    def apply_upload_url(self, file_names: list) -> dict:
        """申请文件上传链接"""
        url = f"{self.config.api_base_url}/file-urls/batch"
        
        files = []
        for i, name in enumerate(file_names):
            files.append({
                "name": name,
                "is_ocr": self.config.enable_ocr,
                "data_id": f"file_{i}_{int(time.time())}"
            })
        
        data = {
            "files": files,
            "model_version": self.config.model_version,
            "enable_formula": self.config.enable_formula,
            "enable_table": self.config.enable_table,
            "language": self.config.language
        }
        
        self.logger.info("申请上传链接...")
        response = requests.post(url, headers=self.headers, json=data)
        
        if response.status_code != 200:
            raise Exception(f"申请上传链接失败: HTTP {response.status_code}")
        
        result = response.json()
        if result.get("code") != 0:
            raise Exception(f"申请上传链接失败: {result.get('msg')}")
        
        return {
            "batch_id": result["data"]["batch_id"],
            "file_urls": result["data"]["file_urls"]
        }
    
    def upload_file(self, file_path: Path, upload_url: str) -> bool:
        """上传文件到预签名URL"""
        self.logger.info(f"上传文件: {file_path.name}...")
        
        with open(file_path, 'rb') as f:
            response = requests.put(upload_url, data=f)
        
        return response.status_code == 200
    
    def get_batch_results(
        self,
        batch_id: str,
        on_progress: Optional[Callable[[Dict[str, Any]], None]] = None,
    ) -> dict:
        """轮询获取解析结果"""
        url = f"{self.config.api_base_url}/extract-results/batch/{batch_id}"
        
        self.logger.info(f"等待解析完成 (Batch: {batch_id})...")
        start_time = time.time()
        
        while True:
            elapsed = time.time() - start_time
            
            if elapsed > self.config.timeout:
                raise TimeoutError(f"解析超时（{self.config.timeout}秒）")
            
            response = requests.get(url, headers=self.headers)
            
            if response.status_code != 200:
                raise Exception(f"查询结果失败: HTTP {response.status_code}")
            
            result = response.json()
            if result.get("code") != 0:
                raise Exception(f"查询结果失败: {result.get('msg')}")
            
            extract_results = result["data"]["extract_result"]
            
            all_done = True
            has_error = False
            
            for file_result in extract_results:
                state = file_result.get("state", "")
                
                if state == "done":
                    continue
                elif state == "failed":
                    has_error = True
                    self.logger.error(f"解析失败: {file_result.get('err_msg')}")
                else:
                    all_done = False
                    progress = file_result.get("extract_progress", {})
                    if on_progress and progress:
                        on_progress(progress)
                    self.logger.info(
                        f"解析中: {progress.get('extracted_pages', 0)}/"
                        f"{progress.get('total_pages', '?')} 页 ({int(elapsed)}秒)"
                    )
            
            if all_done:
                if has_error:
                    raise Exception("解析失败")
                self.logger.info("解析完成!")
                return result["data"]
            
            time.sleep(self._next_poll_interval(extract_results))

    def _next_poll_interval(self, extract_results: list[dict]) -> int:
        """MinerU 只有 running 时暴露页级进度，运行中使用更短轮询间隔。"""
        has_running_file = any(
            file_result.get("state") == "running"
            for file_result in extract_results
        )
        if has_running_file:
            return getattr(self.config, "progress_poll_interval", self.config.poll_interval)
        return self.config.poll_interval
    
    def download_results(self, extract_result: dict, output_dir: Path) -> list:
        """下载解析结果"""
        self.logger.info("下载解析结果...")
        output_dir.mkdir(parents=True, exist_ok=True)
        
        results = []
        
        for file_result in extract_result.get("extract_result", []):
            if file_result.get("state") != "done":
                continue
            
            zip_url = file_result.get("full_zip_url", "")
            if not zip_url:
                continue
            
            response = requests.get(zip_url)
            if response.status_code != 200:
                continue
            
            with zipfile.ZipFile(io.BytesIO(response.content)) as zf:
                zf.extractall(output_dir)
                
                json_content = None
                for name in zf.namelist():
                    if name.endswith('.json') and 'content_list' in name:
                        with open(output_dir / name, 'r', encoding='utf-8') as f:
                            json_content = json.load(f)
                        break
                
                results.append({
                    "file_name": file_result.get("file_name"),
                    "output_dir": str(output_dir),
                    "json_content": json_content,
                    "files": zf.namelist()
                })
        
        return results


# ===================== 主应用类 =====================

class PDFParserApp:
    """PDF解析应用主类"""
    
    def __init__(self, config: Config):
        self.config = config
        self.storage = MinIOService(config.minio)
        self.db = DatabaseService(config.mysql)
        self.parser = MinerUParser(config)
        self.logger = logging.getLogger("PDFParserApp")

    def _resolve_pdf_file(
        self,
        course_id: str,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        解析课程下要操作的具体资料。

        规则：
        - 指定 material_id/file_id：按课程资料关系 ID 精确定位，并校验 course_id
        - 指定 material_name/file_name：按课程ID+展示名定位
        - 都未指定：若课程下只有一份文件则自动选择，否则报错要求显式指定
        """
        resolved_material_id = material_id if material_id is not None else file_id
        resolved_material_name = material_name if material_name else file_name

        if resolved_material_id is not None:
            if hasattr(self.db, "get_course_material_by_id"):
                pdf_file = self.db.get_course_material_by_id(resolved_material_id)
            else:
                pdf_file = self.db.get_pdf_file_by_id(resolved_material_id)
            if not pdf_file or pdf_file["course_id"] != course_id:
                raise Exception(
                    f"课程 {course_id} 下不存在 material_id={resolved_material_id} 的资料"
                )
            return pdf_file

        if resolved_material_name:
            if hasattr(self.db, "get_course_material_by_course"):
                pdf_file = self.db.get_course_material_by_course(
                    course_id, resolved_material_name
                )
            else:
                pdf_file = self.db.get_pdf_file_by_course(course_id, resolved_material_name)
            if not pdf_file:
                raise Exception(f"课程 {course_id} 下不存在资料: {resolved_material_name}")
            return pdf_file

        if hasattr(self.db, "get_course_materials_by_course"):
            pdf_files = self.db.get_course_materials_by_course(course_id)
        else:
            pdf_files = self.db.get_pdf_files_by_course(course_id)
        if not pdf_files:
            raise Exception(f"课程 {course_id} 没有上传的资料")
        if len(pdf_files) == 1:
            return pdf_files[0]

        choices = ", ".join(
            f"{row['id']}:{row.get('display_name') or row.get('file_name')}"
            for row in pdf_files[:10]
        )
        raise Exception(
            "课程下存在多份资料，请使用 --material-id 或 --material-name 指定；"
            "兼容参数 --file-id / --file-name 仍可使用。"
            f" 可选资料: {choices}"
        )

    def _delete_material_related_data(self, course_material: Dict[str, Any]) -> None:
        """删除某一份课程资料关系相关的解析产物和 GraphRAG 导出。"""
        course_id = course_material["course_id"]
        material_id = course_material["id"]
        self.storage.delete_artifacts(course_id, f"pdf_{material_id}")
        self.storage.delete_artifacts(course_id, f"graphrag/pdf_{material_id}")
        self.storage.delete_artifacts(course_id, f"material_{material_id}")
        self.storage.delete_artifacts(course_id, f"graphrag/material_{material_id}")
        self.db.delete_course_material(material_id)

    def _delete_pdf_related_data(self, pdf_file: Dict[str, Any]) -> None:
        """兼容旧调用；file_id 现在等价于 course_material_id。"""
        self._delete_material_related_data(pdf_file)

    def _cleanup_parse_temp_files(
        self,
        course_id: str,
        temp_pdf: Optional[Path] = None,
        artifact_prefix: Optional[str] = None,
    ) -> None:
        """清理解析过程中的本地临时文件。"""
        if temp_pdf:
            temp_pdf.unlink(missing_ok=True)

        if artifact_prefix:
            shutil.rmtree(
                self.config.get_temp_path(course_id, artifact_prefix),
                ignore_errors=True,
            )

        course_temp_dir = self.config.get_temp_path(course_id)
        if course_temp_dir.exists():
            try:
                course_temp_dir.rmdir()
            except OSError:
                pass
    
    def upload(self, course_id: str, file_path: str, 
               force: bool = False) -> Dict[str, Any]:
        """
        上传PDF文件
        
        Args:
            course_id: 课程ID
            file_path: 本地文件路径
            force: 是否强制覆盖
            
        Returns:
            上传结果
        """
        file_path_obj = Path(file_path)
        upload_file_name = file_path_obj.name
        
        if not file_path_obj.exists():
            raise FileNotFoundError(f"文件不存在: {file_path}")
        
        # 计算MD5
        file_md5 = self.storage.calculate_md5(str(file_path))
        file_size = file_path_obj.stat().st_size
        
        self.logger.info(f"文件: {file_path_obj.name}")
        self.logger.info(f"大小: {file_size / 1024 / 1024:.2f} MB")
        self.logger.info(f"MD5: {file_md5}")
        
        material_object = self.db.get_material_object_by_md5(file_md5)
        if material_object:
            material_object_id = int(material_object["id"])
            upload_result = {
                "bucket": material_object["minio_bucket"],
                "object_key": material_object["minio_object_key"],
                "md5": file_md5,
                "size": material_object.get("file_size", file_size),
            }
        else:
            self.logger.info("上传资料对象到MinIO...")
            upload_result = self.storage.upload_material_object(
                str(file_path), file_md5, upload_file_name
            )
            material_object_id = self.db.create_material_object(
                original_file_name=upload_file_name,
                file_md5=file_md5,
                file_size=file_size,
                minio_bucket=upload_result["bucket"],
                minio_object_key=upload_result["object_key"],
            )

        existing_relation = self.db.get_course_material_by_object(
            course_id, material_object_id
        )
        if existing_relation:
            if not force:
                self.logger.warning(f"资料已存在 (课程: {course_id})")
                display_name = existing_relation.get("display_name") or existing_relation.get("file_name")
                return {
                    "status": "duplicate",
                    "message": "资料已存在",
                    "course_id": course_id,
                    "course_material_id": existing_relation["id"],
                    "file_id": existing_relation["id"],
                    "material_object_id": material_object_id,
                    "display_name": display_name,
                    "file_name": display_name,
                    "parse_status": existing_relation["parse_status"],
                }
            self.logger.info("强制替换已存在的课程资料关系")
            self._delete_material_related_data(existing_relation)

        same_name_material = self.db.get_course_material_by_course(
            course_id, upload_file_name
        )
        if (
            same_name_material
            and int(same_name_material["material_object_id"]) != material_object_id
        ):
            if not force:
                raise Exception(
                    f"课程 {course_id} 已存在同名资料 {upload_file_name}，"
                    "但内容不同；请使用 --force 替换该课程资料关系"
                )
            else:
                self._delete_material_related_data(same_name_material)

        course_material_id = self.db.create_course_material(
            course_id=course_id,
            material_object_id=material_object_id,
            display_name=upload_file_name,
        )
        
        self.db.add_log(course_material_id, f"文件上传成功: {file_path_obj.name}")
        
        self.logger.info(f"上传成功! 课程资料ID: {course_material_id}")
        
        return {
            "status": "success",
            "course_material_id": course_material_id,
            "file_id": course_material_id,
            "material_object_id": material_object_id,
            "course_id": course_id,
            "display_name": upload_file_name,
            "file_name": upload_file_name,
            "md5": file_md5,
            "size": file_size
        }
    
    def parse(
        self,
        course_id: str,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        解析已上传的PDF文件
        
        Args:
            course_id: 课程ID
            
        Returns:
            解析结果
        """
        pdf_file = self._resolve_pdf_file(
            course_id,
            file_id=file_id,
            file_name=file_name,
            material_id=material_id,
            material_name=material_name,
        )
        
        resolved_file_id: int = int(pdf_file["id"])
        source_file_name = pdf_file.get("display_name") or pdf_file.get("file_name")
        
        # 检查状态
        if pdf_file["parse_status"] == "done":
            self.logger.warning("文件已解析完成")
            return {
                "status": "already_done",
                "course_material_id": resolved_file_id,
                "file_id": resolved_file_id,
                "display_name": source_file_name,
                "file_name": source_file_name,
                "message": "文件已解析完成"
            }
        
        if pdf_file["parse_status"] == "processing":
            raise Exception("文件正在解析中，请稍后重试")
        
        # 更新状态为处理中
        self.db.update_parse_status(resolved_file_id, ParseStatus.PROCESSING)
        self.db.add_log(resolved_file_id, "开始解析")

        temp_pdf: Optional[Path] = None
        artifact_prefix: Optional[str] = None
        
        try:
            # 从MinIO下载文件到临时目录
            temp_pdf = self.config.get_temp_path(course_id, source_file_name)
            self.logger.info(f"从MinIO下载文件...")
            self.storage.download_pdf_object(
                pdf_file["minio_bucket"],
                pdf_file["minio_object_key"],
                str(temp_pdf),
            )
            
            # 申请上传链接
            upload_info = self.parser.apply_upload_url([source_file_name])
            batch_id = upload_info["batch_id"]
            
            self.db.update_parse_status(resolved_file_id, ParseStatus.PROCESSING, batch_id=batch_id)
            self.db.add_log(resolved_file_id, f"MinerU Batch ID: {batch_id}")
            
            # 上传到MinerU
            if not self.parser.upload_file(temp_pdf, upload_info["file_urls"][0]):
                raise Exception("上传到MinerU失败")
            
            self.db.add_log(resolved_file_id, "文件已上传到MinerU")
            
            # 等待解析完成
            batch_result = self.parser.get_batch_results(
                batch_id,
                on_progress=lambda progress: self._record_parse_progress(resolved_file_id, progress),
            )
            
            # 下载结果到临时目录
            artifact_prefix = f"material_{resolved_file_id}"
            temp_output = self.config.get_temp_path(course_id, artifact_prefix, "output")
            results = self.parser.download_results(batch_result, temp_output)
            
            if not results:
                raise Exception("未获取到解析结果")
            
            self.db.add_log(resolved_file_id, f"下载解析结果: {len(results[0]['files'])} 个文件")
            
            # 上传结果到MinIO
            self.logger.info("上传解析结果到MinIO...")
            uploaded_files = self.storage.upload_artifacts_dir(
                course_id, str(temp_output), base_prefix=artifact_prefix
            )
            
            # 记录结果到数据库
            for uf in uploaded_files:
                result_type = infer_result_type(uf["file_name"])
                self.db.create_parse_result(
                    pdf_file_id=resolved_file_id,
                    course_id=course_id,
                    result_type=result_type,
                    file_name=uf["file_name"],
                    minio_bucket=uf["bucket"],
                    minio_object_key=uf["object_key"],
                    file_size=uf["size"]
                )
            
            # 更新状态为完成
            self.db.update_parse_status(resolved_file_id, ParseStatus.DONE)
            self.db.add_log(resolved_file_id, f"解析完成，共 {len(uploaded_files)} 个结果文件")
            self.logger.info(f"解析完成! 共 {len(uploaded_files)} 个文件")
            
            return {
                "status": "success",
                "course_material_id": resolved_file_id,
                "file_id": resolved_file_id,
                "course_id": course_id,
                "display_name": source_file_name,
                "file_name": source_file_name,
                "result_count": len(uploaded_files),
                "files": [f["relative_path"] for f in uploaded_files]
            }
            
        except Exception as e:
            self.db.update_parse_status(resolved_file_id, ParseStatus.FAILED, error_msg=str(e))
            self.db.add_log(resolved_file_id, f"解析失败: {e}", level="error")
            raise
        finally:
            self._cleanup_parse_temp_files(
                course_id,
                temp_pdf=temp_pdf,
                artifact_prefix=artifact_prefix,
            )
    
    def status(
        self,
        course_id: str,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """获取课程状态"""
        try:
            pdf_file = self._resolve_pdf_file(
                course_id,
                file_id=file_id,
                file_name=file_name,
                material_id=material_id,
                material_name=material_name,
            )
        except Exception as e:
            return {
                "status": "not_found",
                "message": str(e),
            }

        results = self.db.get_parse_results(pdf_file["id"])
        logs = self.db.get_logs(pdf_file["id"])
        
        return {
            "course_id": course_id,
            "course_material_id": pdf_file["id"],
            "file_id": pdf_file["id"],
            "material_object_id": pdf_file.get("material_object_id"),
            "display_name": pdf_file.get("display_name") or pdf_file.get("file_name"),
            "file_name": pdf_file.get("display_name") or pdf_file.get("file_name"),
            "file_md5": pdf_file["file_md5"],
            "file_size": pdf_file["file_size"],
            "parse_status": pdf_file["parse_status"],
            "parse_progress_percent": pdf_file.get("parse_progress_percent"),
            "parse_progress_extracted_pages": pdf_file.get("parse_progress_extracted_pages"),
            "parse_progress_total_pages": pdf_file.get("parse_progress_total_pages"),
            "parse_progress_updated_at": (
                str(pdf_file["parse_progress_updated_at"])
                if pdf_file.get("parse_progress_updated_at")
                else None
            ),
            "upload_time": str(pdf_file["upload_time"]),
            "parse_started_at": str(pdf_file["parse_started_at"]) if pdf_file["parse_started_at"] else None,
            "parse_finished_at": str(pdf_file["parse_finished_at"]) if pdf_file["parse_finished_at"] else None,
            "result_count": len(results),
            "results": [
                {"type": r["result_type"], "file": r["file_name"]} 
                for r in results
            ],
            "recent_logs": [
                {"level": l["log_level"], "message": l["log_message"], "time": str(l["created_at"])}
                for l in logs[-5:]
            ]
        }

    def _record_parse_progress(self, course_material_id: int, progress: Dict[str, Any]):
        try:
            self.db.update_parse_progress(course_material_id, progress)
        except Exception as exc:
            self.logger.warning(f"写入 MinerU 页级进度失败，将继续等待解析结果: {exc}")
    
    def download(
        self,
        course_id: str,
        output_dir: str,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """下载解析结果"""
        pdf_file = self._resolve_pdf_file(
            course_id,
            file_id=file_id,
            file_name=file_name,
            material_id=material_id,
            material_name=material_name,
        )
        
        if pdf_file["parse_status"] != "done":
            raise Exception(f"文件尚未解析完成 (状态: {pdf_file['parse_status']})")
        
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)
        
        self.logger.info(f"下载解析结果到: {output_path}")
        
        results = self.db.get_parse_results(pdf_file["id"])
        downloaded = []
        prefix = f"{course_id}/"
        for record in results:
            object_key = record["minio_object_key"]
            if not object_key.startswith(prefix):
                relative_path = record["file_name"]
            else:
                relative_path = object_key[len(prefix):]

            local_path = output_path / relative_path
            self.storage.download_object(
                record["minio_bucket"], object_key, str(local_path)
            )
            downloaded.append(str(local_path))
        
        self.logger.info(f"下载完成! 共 {len(downloaded)} 个文件")
        
        return {
            "status": "success",
            "course_id": course_id,
            "course_material_id": pdf_file["id"],
            "file_id": pdf_file["id"],
            "display_name": pdf_file.get("display_name") or pdf_file.get("file_name"),
            "file_name": pdf_file.get("display_name") or pdf_file.get("file_name"),
            "output_dir": str(output_path),
            "file_count": len(downloaded),
            "files": downloaded
        }
    
    def list_all(self) -> Dict[str, Any]:
        """列出所有课程状态"""
        stats = self.db.get_stats()
        overview = self.db.get_course_overview()

        return {
            "stats": stats,
            "courses": [
                {
                    "course_id": o["course_id"],
                    "course_material_id": o["pdf_file_id"],
                    "file_id": o["pdf_file_id"],
                    "file_name": o["file_name"],
                    "display_name": o["file_name"],
                    "parse_status": o["parse_status"],
                    "result_count": o["result_file_count"]
                }
                for o in overview if o["pdf_file_id"]
            ]
        }

    def export_graphrag(
        self,
        course_id: str,
        options: ExportOptions,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        导出 GraphRAG 输入文件

        Args:
            course_id: 课程ID
            options: 导出选项

        Returns:
            导出结果
        """
        exporter = GraphRAGExporter(
            db=self.db, storage=self.storage, config=self.config
        )
        pdf_file = self._resolve_pdf_file(
            course_id,
            file_id=file_id,
            file_name=file_name,
            material_id=material_id,
            material_name=material_name,
        )
        return exporter.export(pdf_file, options)


# ===================== 命令行入口 =====================

def setup_logging(level: str):
    """配置日志"""
    logging.basicConfig(
        level=getattr(logging, level.upper()),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )


def main():
    parser = argparse.ArgumentParser(
        description="MinerU PDF解析工具 v2.0",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例 (在项目根目录运行):
  # 上传PDF文件
  python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf

  # 上传并立即解析
  python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse

  # 解析已上传的文件
  python scripts/pdf_processor/mineru_parser.py parse os --file-name book.pdf

  # 查看状态
  python scripts/pdf_processor/mineru_parser.py status os --file-id 3

  # 下载解析结果
  python scripts/pdf_processor/mineru_parser.py download os --file-name slides.pdf -o ./output

  # 列出所有课程
  python scripts/pdf_processor/mineru_parser.py list

  # 导出 GraphRAG 输入 (章节模式，默认开启表格语义化)
  python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section

  # 关闭表格语义化，使用原始 TSV 格式
  python scripts/pdf_processor/mineru_parser.py export-graphrag os --no-semantic-table

  # 导出 GraphRAG 输入 (页面模式，强制覆盖)
  python scripts/pdf_processor/mineru_parser.py export-graphrag os --mode page --force
        """
    )
    
    parser.add_argument("--env", help="指定.env文件路径")
    
    subparsers = parser.add_subparsers(dest="command", help="命令")
    
    # upload 命令
    upload_parser = subparsers.add_parser("upload", help="上传PDF文件")
    upload_parser.add_argument("course_id", help="课程ID")
    upload_parser.add_argument("-f", "--file", required=True, help="PDF文件路径")
    upload_parser.add_argument("--force", action="store_true", help="强制覆盖已存在的文件")
    upload_parser.add_argument("--parse", action="store_true", help="上传后立即解析")
    
    # parse 命令
    parse_parser = subparsers.add_parser("parse", help="解析已上传的PDF")
    parse_parser.add_argument("course_id", help="课程ID")
    parse_parser.add_argument("--material-id", type=int, help="指定要解析的课程资料ID")
    parse_parser.add_argument("--material-name", help="指定要解析的课程资料展示名")
    parse_parser.add_argument("--file-id", type=int, help="指定要解析的PDF文件ID")
    parse_parser.add_argument("--file-name", help="指定要解析的PDF文件名")
    
    # status 命令
    status_parser = subparsers.add_parser("status", help="查看课程状态")
    status_parser.add_argument("course_id", help="课程ID")
    status_parser.add_argument("--material-id", type=int, help="指定课程资料ID")
    status_parser.add_argument("--material-name", help="指定课程资料展示名")
    status_parser.add_argument("--file-id", type=int, help="指定PDF文件ID")
    status_parser.add_argument("--file-name", help="指定PDF文件名")
    
    # download 命令
    download_parser = subparsers.add_parser("download", help="下载解析结果")
    download_parser.add_argument("course_id", help="课程ID")
    download_parser.add_argument("--material-id", type=int, help="指定课程资料ID")
    download_parser.add_argument("--material-name", help="指定课程资料展示名")
    download_parser.add_argument("--file-id", type=int, help="指定PDF文件ID")
    download_parser.add_argument("--file-name", help="指定PDF文件名")
    download_parser.add_argument("-o", "--output", default="./output", help="输出目录")
    
    # list 命令
    subparsers.add_parser("list", help="列出所有课程")

    # export-graphrag 命令
    export_parser = subparsers.add_parser(
        "export-graphrag", help="导出 GraphRAG 输入文件"
    )
    export_parser.add_argument("course_id", help="课程ID")
    export_parser.add_argument("--material-id", type=int, help="指定课程资料ID")
    export_parser.add_argument("--material-name", help="指定课程资料展示名")
    export_parser.add_argument("--file-id", type=int, help="指定PDF文件ID")
    export_parser.add_argument("--file-name", help="指定PDF文件名")
    export_parser.add_argument(
        "--mode", choices=["section", "page"], default="section",
        help="聚合模式: section(章节) / page(页面)，默认 section"
    )
    export_parser.add_argument(
        "--force", action="store_true",
        help="强制覆盖已有的 GraphRAG 导出"
    )
    export_parser.add_argument(
        "--semantic-table", action="store_true", default=True,
        help="启用表格语义化 (列名=值 描述，默认开启)"
    )
    export_parser.add_argument(
        "--no-semantic-table", dest="semantic_table", action="store_false",
        help="关闭表格语义化，保留原始 TSV 格式"
    )
    export_parser.add_argument(
        "--with-page-docs", action="store_true",
        help="同时生成 page 模式文档 (仅在 --mode=section 时有效)"
    )
    export_parser.add_argument(
        "--max-chars", type=int, default=None,
        help="每个文档最大字符数，超出按段落拆分"
    )
    export_parser.add_argument(
        "--output-prefix", default="graphrag",
        help="MinIO 输出子目录前缀 (默认: graphrag)"
    )
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return 1
    
    # 加载配置
    config = Config.from_env(args.env)
    
    # 验证配置
    errors = config.validate()
    if errors:
        print("配置错误:")
        for e in errors:
            print(f"  ✗ {e}")
        return 1
    
    # 配置日志
    setup_logging(config.log_level)
    
    try:
        app = PDFParserApp(config)
        
        if args.command == "upload":
            result = app.upload(args.course_id, args.file, args.force)
            print(json.dumps(result, ensure_ascii=False, indent=2))
            
            if args.parse and result["status"] == "success":
                print("\n开始解析...")
                parse_result = app.parse(args.course_id, file_id=result["file_id"])
                print(json.dumps(parse_result, ensure_ascii=False, indent=2))
        
        elif args.command == "parse":
            result = app.parse(
                args.course_id,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )
            print(json.dumps(result, ensure_ascii=False, indent=2))
        
        elif args.command == "status":
            result = app.status(
                args.course_id,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )
            print(json.dumps(result, ensure_ascii=False, indent=2))
        
        elif args.command == "download":
            result = app.download(
                args.course_id,
                args.output,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )
            print(json.dumps(result, ensure_ascii=False, indent=2))
        
        elif args.command == "list":
            result = app.list_all()
            print(json.dumps(result, ensure_ascii=False, indent=2))

        elif args.command == "export-graphrag":
            options = ExportOptions(
                mode=args.mode,
                force=args.force,
                semantic_table=args.semantic_table,
                with_page_docs=args.with_page_docs,
                max_chars=args.max_chars,
                output_prefix=args.output_prefix,
            )
            result = app.export_graphrag(
                args.course_id,
                options,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )
            print(json.dumps(result, ensure_ascii=False, indent=2))
        
        return 0
        
    except Exception as e:
        logging.error(f"错误: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
