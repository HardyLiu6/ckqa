#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinIO存储服务模块
================
提供文件上传、下载、删除等MinIO操作功能
"""

import os
import hashlib
from pathlib import Path
from typing import BinaryIO, Any
from dataclasses import dataclass

from minio import Minio
from minio.error import S3Error


@dataclass
class MinIOConfig:
    """MinIO配置"""
    endpoint: str
    access_key: str
    secret_key: str
    secure: bool
    bucket_pdf: str
    bucket_artifacts: str
    
    @classmethod
    def from_env(cls) -> "MinIOConfig":
        """从环境变量加载配置"""
        def str_to_bool(value: str) -> bool:
            return value.lower() in ("true", "1", "yes", "on")
        
        return cls(
            endpoint=os.getenv("MINIO_ENDPOINT", "localhost:9000"),
            access_key=os.getenv("MINIO_ACCESS_KEY", "admin"),
            secret_key=os.getenv("MINIO_SECRET_KEY", "12345678"),
            secure=str_to_bool(os.getenv("MINIO_SECURE", "false")),
            bucket_pdf=os.getenv("MINIO_BUCKET_PDF", "course-pdfs"),
            bucket_artifacts=os.getenv("MINIO_BUCKET_ARTIFACTS", "course-artifacts"),
        )


class MinIOService:
    """MinIO存储服务"""
    
    def __init__(self, config: MinIOConfig):
        """初始化MinIO客户端"""
        self.config = config
        self.client = Minio(
            endpoint=config.endpoint,
            access_key=config.access_key,
            secret_key=config.secret_key,
            secure=config.secure
        )
        self._ensure_buckets()
    
    def _ensure_buckets(self):
        """确保存储桶存在"""
        for bucket in [self.config.bucket_pdf, self.config.bucket_artifacts]:
            if not self.client.bucket_exists(bucket):
                self.client.make_bucket(bucket)
                print(f"[MinIO] 创建存储桶: {bucket}")
    
    @staticmethod
    def calculate_md5(file_path: str) -> str:
        """计算文件MD5值"""
        hash_md5 = hashlib.md5()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(8192), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    
    @staticmethod
    def calculate_md5_from_stream(stream: BinaryIO) -> str:
        """从流计算MD5值"""
        hash_md5 = hashlib.md5()
        for chunk in iter(lambda: stream.read(8192), b""):
            hash_md5.update(chunk)
        stream.seek(0)  # 重置流位置
        return hash_md5.hexdigest()
    
    def upload_pdf(self, course_id: str, file_path: str, file_name: str) -> dict:
        """
        上传PDF文件到MinIO
        
        Args:
            course_id: 课程ID
            file_path: 本地文件路径
            file_name: 存储的文件名
            
        Returns:
            包含object_key, md5, size的字典
        """
        object_key = f"{course_id}/{file_name}"
        file_size = os.path.getsize(file_path)
        file_md5 = self.calculate_md5(file_path)
        
        with open(file_path, "rb") as f:
            self.client.put_object(
                bucket_name=self.config.bucket_pdf,
                object_name=object_key,
                data=f,
                length=file_size,
                content_type="application/pdf"
            )
        
        return {
            "bucket": self.config.bucket_pdf,
            "object_key": object_key,
            "md5": file_md5,
            "size": file_size
        }
    
    def upload_pdf_stream(
        self, course_id: str, stream: BinaryIO, file_size: int, file_name: str
    ) -> dict:
        """
        从流上传PDF文件到MinIO
        
        Args:
            course_id: 课程ID
            stream: 文件流
            file_size: 文件大小
            file_name: 存储的文件名
            
        Returns:
            包含object_key, md5, size的字典
        """
        object_key = f"{course_id}/{file_name}"
        file_md5 = self.calculate_md5_from_stream(stream)
        
        self.client.put_object(
            bucket_name=self.config.bucket_pdf,
            object_name=object_key,
            data=stream,
            length=file_size,
            content_type="application/pdf"
        )
        
        return {
            "bucket": self.config.bucket_pdf,
            "object_key": object_key,
            "md5": file_md5,
            "size": file_size
        }
    
    def download_pdf(self, course_id: str, local_path: str, file_name: str) -> str:
        """
        从MinIO下载PDF文件
        
        Args:
            course_id: 课程ID
            local_path: 本地保存路径
            file_name: MinIO中的文件名
            
        Returns:
            本地文件路径
        """
        object_key = f"{course_id}/{file_name}"
        
        # 确保目录存在
        Path(local_path).parent.mkdir(parents=True, exist_ok=True)
        
        self.client.fget_object(
            bucket_name=self.config.bucket_pdf,
            object_name=object_key,
            file_path=local_path
        )
        
        return local_path
    
    def get_pdf_stream(self, course_id: str, file_name: str) -> Any:
        """
        获取PDF文件流
        
        Args:
            course_id: 课程ID
            file_name: MinIO中的文件名
            
        Returns:
            文件流
        """
        object_key = f"{course_id}/{file_name}"
        
        response = self.client.get_object(
            bucket_name=self.config.bucket_pdf,
            object_name=object_key
        )
        
        return response
    
    def upload_artifact(
        self, course_id: str, local_path: str, relative_path: str
    ) -> dict:
        """
        上传解析结果文件到MinIO
        
        Args:
            course_id: 课程ID
            local_path: 本地文件路径
            relative_path: 相对路径（在课程目录下）
            
        Returns:
            包含object_key和size的字典
        """
        object_key = f"{course_id}/{relative_path}"
        file_size = os.path.getsize(local_path)
        
        # 根据文件扩展名确定content_type
        ext = Path(local_path).suffix.lower()
        content_types = {
            ".json": "application/json",
            ".md": "text/markdown",
            ".pdf": "application/pdf",
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".gif": "image/gif",
        }
        content_type = content_types.get(ext, "application/octet-stream")
        
        with open(local_path, "rb") as f:
            self.client.put_object(
                bucket_name=self.config.bucket_artifacts,
                object_name=object_key,
                data=f,
                length=file_size,
                content_type=content_type
            )
        
        return {
            "bucket": self.config.bucket_artifacts,
            "object_key": object_key,
            "size": file_size
        }
    
    def upload_artifacts_dir(
        self, course_id: str, local_dir: str, base_prefix: str = ""
    ) -> list[dict]:
        """
        上传整个解析结果目录到MinIO
        
        Args:
            course_id: 课程ID
            local_dir: 本地目录路径
            
        Returns:
            上传结果列表
        """
        results = []
        local_dir_path = Path(local_dir)
        
        for file_path in local_dir_path.rglob("*"):
            if file_path.is_file():
                relative_path = str(file_path.relative_to(local_dir_path))
                if base_prefix:
                    relative_path = f"{base_prefix}/{relative_path}"
                result = self.upload_artifact(course_id, str(file_path), relative_path)
                result["file_name"] = file_path.name
                result["relative_path"] = relative_path
                results.append(result)
        
        return results
    
    def download_artifact(self, course_id: str, relative_path: str,
                          local_path: str) -> str:
        """
        下载解析结果文件
        
        Args:
            course_id: 课程ID
            relative_path: 相对路径
            local_path: 本地保存路径
            
        Returns:
            本地文件路径
        """
        object_key = f"{course_id}/{relative_path}"
        
        Path(local_path).parent.mkdir(parents=True, exist_ok=True)
        
        self.client.fget_object(
            bucket_name=self.config.bucket_artifacts,
            object_name=object_key,
            file_path=local_path
        )
        
        return local_path

    def download_object(self, bucket: str, object_key: str, local_path: str) -> str:
        """按 bucket + object_key 直接下载对象。"""
        Path(local_path).parent.mkdir(parents=True, exist_ok=True)
        self.client.fget_object(
            bucket_name=bucket,
            object_name=object_key,
            file_path=local_path,
        )
        return local_path
    
    def download_artifacts_dir(self, course_id: str, 
                               local_dir: str) -> list[str]:
        """
        下载课程的所有解析结果
        
        Args:
            course_id: 课程ID
            local_dir: 本地目录路径
            
        Returns:
            下载的文件列表
        """
        downloaded = []
        prefix = f"{course_id}/"
        
        objects = self.client.list_objects(
            bucket_name=self.config.bucket_artifacts,
            prefix=prefix,
            recursive=True
        )
        
        for obj in objects:
            if obj.object_name is None:
                continue
            relative_path = obj.object_name[len(prefix):]
            local_path = os.path.join(local_dir, relative_path)
            
            self.download_artifact(course_id, relative_path, local_path)
            downloaded.append(local_path)
        
        return downloaded
    
    def list_artifacts(self, course_id: str) -> list[dict]:
        """
        列出课程的所有解析结果
        
        Args:
            course_id: 课程ID
            
        Returns:
            文件信息列表
        """
        prefix = f"{course_id}/"
        files = []
        
        objects = self.client.list_objects(
            bucket_name=self.config.bucket_artifacts,
            prefix=prefix,
            recursive=True
        )
        
        for obj in objects:
            if obj.object_name is None:
                continue
            relative_path = obj.object_name[len(prefix):]
            files.append({
                "object_key": obj.object_name,
                "relative_path": relative_path,
                "size": obj.size,
                "last_modified": obj.last_modified
            })
        
        return files
    
    def pdf_exists(self, course_id: str, file_name: str) -> bool:
        """检查PDF文件是否存在"""
        object_key = f"{course_id}/{file_name}"
        try:
            self.client.stat_object(self.config.bucket_pdf, object_key)
            return True
        except S3Error:
            return False
    
    def delete_pdf(self, course_id: str, file_name: str):
        """删除PDF文件"""
        object_key = f"{course_id}/{file_name}"
        self.client.remove_object(self.config.bucket_pdf, object_key)
    
    def delete_artifacts(self, course_id: str, relative_prefix: str = ""):
        """删除课程下指定前缀的解析结果；默认删除整个课程目录。"""
        prefix = f"{course_id}/"
        if relative_prefix:
            prefix = f"{prefix}{relative_prefix.rstrip('/')}/"
        
        objects = self.client.list_objects(
            bucket_name=self.config.bucket_artifacts,
            prefix=prefix,
            recursive=True
        )
        
        for obj in objects:
            if obj.object_name is not None:
                self.client.remove_object(self.config.bucket_artifacts, obj.object_name)
    
    def get_presigned_url(self, bucket: str, object_key: str, 
                          expires_hours: int = 24) -> str:
        """
        获取预签名URL（用于临时访问）
        
        Args:
            bucket: 存储桶名称
            object_key: 对象键
            expires_hours: 过期时间（小时）
            
        Returns:
            预签名URL
        """
        from datetime import timedelta
        
        return self.client.presigned_get_object(
            bucket_name=bucket,
            object_name=object_key,
            expires=timedelta(hours=expires_hours)
        )
