#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MySQL数据库服务模块
==================
提供数据库CRUD操作功能
"""

import os
from datetime import datetime
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from contextlib import contextmanager
from enum import Enum

import pymysql
from pymysql.cursors import DictCursor
from dbutils.pooled_db import PooledDB


class ParseStatus(Enum):
    """解析状态枚举"""
    PENDING = "pending"
    PROCESSING = "processing"
    DONE = "done"
    FAILED = "failed"


class ResultType(Enum):
    """结果类型枚举"""
    CONTENT_LIST_JSON = "content_list_json"
    MODEL_JSON = "model_json"
    LAYOUT_JSON = "layout_json"
    MARKDOWN = "markdown"
    IMAGE = "image"
    ORIGIN_PDF = "origin_pdf"
    OTHER = "other"


@dataclass
class MySQLConfig:
    """MySQL配置"""
    host: str
    port: int
    user: str
    password: str
    database: str
    pool_size: int
    pool_recycle: int
    timezone: str
    
    @classmethod
    def from_env(cls) -> "MySQLConfig":
        """从环境变量加载配置"""
        return cls(
            host=os.getenv("MYSQL_HOST", "localhost"),
            port=int(os.getenv("MYSQL_PORT", "3306")),
            user=os.getenv("MYSQL_USER", "root"),
            password=os.getenv("MYSQL_PASSWORD", ""),
            database=os.getenv("MYSQL_DATABASE", "mineru_parser"),
            pool_size=int(os.getenv("MYSQL_POOL_SIZE", "5")),
            pool_recycle=int(os.getenv("MYSQL_POOL_RECYCLE", "3600")),
            timezone=os.getenv("MYSQL_TIMEZONE", "+08:00"),
        )


class DatabaseService:
    """数据库服务类"""
    
    def __init__(self, config: MySQLConfig):
        """初始化数据库连接池"""
        self.config = config
        self.pool = PooledDB(
            creator=pymysql,
            maxconnections=config.pool_size,
            mincached=2,
            maxcached=config.pool_size,
            blocking=True,
            maxusage=None,
            setsession=[f"SET time_zone = '{config.timezone}'"],  # 设置会话时区
            ping=1,
            host=config.host,
            port=config.port,
            user=config.user,
            password=config.password,
            database=config.database,
            charset="utf8mb4",
            cursorclass=DictCursor
        )
    
    @contextmanager
    def get_connection(self):
        """获取数据库连接（上下文管理器）"""
        conn = self.pool.connection()
        try:
            yield conn
            conn.commit()
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    
    # ==================== 课程操作 ====================
    
    def create_course(self, course_id: str, course_name: Optional[str] = None,
                      description: Optional[str] = None) -> int:
        """
        创建课程
        
        Returns:
            课程记录ID
        """
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO courses (course_id, course_name, description)
                VALUES (%s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    course_name = COALESCE(VALUES(course_name), course_name),
                    description = COALESCE(VALUES(description), description)
            """, (course_id, course_name, description))
            
            # 获取ID
            cursor.execute("SELECT id FROM courses WHERE course_id = %s", (course_id,))
            result = cursor.fetchone()
            return result["id"]
    
    def get_course(self, course_id: str) -> Optional[Dict]:
        """获取课程信息"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM courses WHERE course_id = %s",
                (course_id,)
            )
            return cursor.fetchone()
    
    def course_exists(self, course_id: str) -> bool:
        """检查课程是否存在"""
        return self.get_course(course_id) is not None
    
    # ==================== 资料对象与课程资料关系操作 ====================

    @staticmethod
    def _select_course_material_sql(where_clause: str) -> str:
        """返回带历史 PDF 字段别名的课程资料查询 SQL。"""
        return f"""
            SELECT
                cm.*,
                cm.id AS pdf_file_id,
                cm.display_name AS file_name,
                mo.original_file_name,
                mo.file_md5,
                mo.file_size,
                mo.mime_type,
                mo.minio_bucket,
                mo.minio_object_key
            FROM course_materials cm
            JOIN material_objects mo ON mo.id = cm.material_object_id
            WHERE {where_clause}
        """

    def create_material_object(
        self,
        original_file_name: str,
        file_md5: str,
        file_size: int,
        minio_bucket: str,
        minio_object_key: str,
        mime_type: str = "application/pdf",
    ) -> int:
        """
        创建或复用资料对象记录。

        material_objects 以 file_md5 唯一，允许不同课程复用同一份物理文件。
        """
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO material_objects
                    (original_file_name, file_md5, file_size, mime_type,
                     minio_bucket, minio_object_key)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    original_file_name = VALUES(original_file_name),
                    file_size = VALUES(file_size),
                    mime_type = VALUES(mime_type),
                    minio_bucket = VALUES(minio_bucket),
                    minio_object_key = VALUES(minio_object_key)
            """, (
                original_file_name, file_md5, file_size, mime_type,
                minio_bucket, minio_object_key,
            ))
            return cursor.lastrowid

    def get_material_object_by_md5(self, file_md5: str) -> Optional[Dict]:
        """根据 MD5 获取资料对象。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM material_objects WHERE file_md5 = %s",
                (file_md5,)
            )
            return cursor.fetchone()

    def get_material_object_by_id(self, material_object_id: int) -> Optional[Dict]:
        """根据 ID 获取资料对象。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM material_objects WHERE id = %s",
                (material_object_id,)
            )
            return cursor.fetchone()

    def create_course_material(
        self,
        course_id: str,
        material_object_id: int,
        display_name: str,
        material_type: str = "textbook",
    ) -> int:
        """
        创建或复用课程资料关系。

        关系由 service 层维护，不依赖数据库级外键。
        """
        self.create_course(course_id)

        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO course_materials
                    (course_id, material_object_id, display_name, material_type)
                VALUES (%s, %s, %s, %s)
            """, (course_id, material_object_id, display_name, material_type))
            return cursor.lastrowid

    def get_course_material_by_id(self, course_material_id: int) -> Optional[Dict]:
        """根据关系 ID 获取课程资料，兼容返回历史 PDF 字段名。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                self._select_course_material_sql("cm.id = %s"),
                (course_material_id,)
            )
            return cursor.fetchone()

    def get_course_material_by_object(
        self, course_id: str, material_object_id: int
    ) -> Optional[Dict]:
        """根据课程与资料对象获取课程资料关系。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                self._select_course_material_sql(
                    "cm.course_id = %s AND cm.material_object_id = %s"
                ),
                (course_id, material_object_id)
            )
            return cursor.fetchone()

    def get_course_material_by_course(
        self, course_id: str, display_name: Optional[str] = None
    ) -> Optional[Dict]:
        """
        根据课程获取课程资料。

        - 传入 display_name 时精确匹配
        - 未传入时返回最新上传的一条
        """
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if display_name is not None:
                cursor.execute(
                    self._select_course_material_sql(
                        "cm.course_id = %s AND cm.display_name = %s"
                    ),
                    (course_id, display_name)
                )
            else:
                cursor.execute(
                    self._select_course_material_sql("cm.course_id = %s")
                    + " ORDER BY cm.upload_time DESC, cm.id DESC LIMIT 1",
                    (course_id,)
                )
            return cursor.fetchone()

    def get_course_materials_by_course(self, course_id: str) -> List[Dict]:
        """获取课程下所有资料关系，按最新上传排序。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                self._select_course_material_sql("cm.course_id = %s")
                + " ORDER BY cm.upload_time DESC, cm.id DESC",
                (course_id,)
            )
            return cursor.fetchall()

    def delete_course_material(self, course_material_id: int):
        """删除课程资料关系及其解析产物记录。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "DELETE FROM parse_results WHERE course_material_id = %s",
                (course_material_id,)
            )
            cursor.execute(
                "DELETE FROM parse_logs WHERE course_material_id = %s",
                (course_material_id,)
            )
            cursor.execute(
                "DELETE FROM course_materials WHERE id = %s",
                (course_material_id,)
            )

    # ==================== PDF文件操作（兼容入口） ====================
    
    def create_pdf_file(self, course_id: str, file_name: str, file_md5: str,
                        file_size: int, minio_bucket: str, 
                        minio_object_key: str) -> int:
        """
        创建PDF文件记录
        
        Returns:
            文件记录ID
        """
        # 确保课程存在
        self.create_course(course_id)
        
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO pdf_files 
                    (course_id, file_name, file_md5, file_size, minio_bucket, minio_object_key)
                VALUES (%s, %s, %s, %s, %s, %s)
            """, (course_id, file_name, file_md5, file_size, minio_bucket, minio_object_key))
            return cursor.lastrowid
    
    def get_pdf_file_by_id(self, file_id: int) -> Optional[Dict]:
        """根据ID获取PDF文件记录"""
        return self.get_course_material_by_id(file_id)
    
    def get_pdf_file_by_md5(self, file_md5: str) -> Optional[Dict]:
        """根据MD5获取PDF文件记录"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM pdf_files WHERE file_md5 = %s", (file_md5,))
            return cursor.fetchone()

    def get_pdf_files_by_course(self, course_id: str) -> List[Dict]:
        """获取课程下的所有PDF文件记录，按最新上传排序。"""
        return self.get_course_materials_by_course(course_id)
    
    def get_pdf_file_by_course(
        self, course_id: str, file_name: Optional[str] = None
    ) -> Optional[Dict]:
        """
        根据课程ID获取PDF文件记录。

        - 传入 file_name 时按课程ID+文件名精确匹配
        - 未传入时返回该课程最新上传的一条记录
        """
        if file_name is not None:
            return self.get_course_material_by_course(course_id, file_name)
        return self.get_course_material_by_course(course_id)
    
    def check_md5_exists(self, file_md5: str) -> Optional[Dict]:
        """
        检查MD5是否已存在
        
        Returns:
            如果存在返回文件记录，否则返回None
        """
        return self.get_material_object_by_md5(file_md5)
    
    def update_parse_status(self, file_id: int, status: ParseStatus,
                            error_msg: Optional[str] = None, batch_id: Optional[str] = None):
        """更新解析状态；file_id 参数兼容旧调用，语义为 course_material_id。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            
            updates = ["parse_status = %s"]
            params: List[Any] = [status.value]
            
            if status == ParseStatus.PROCESSING:
                updates.append("parse_started_at = NOW()")
            elif status in (ParseStatus.DONE, ParseStatus.FAILED):
                updates.append("parse_finished_at = NOW()")
            
            if error_msg is not None:
                updates.append("parse_error_msg = %s")
                params.append(error_msg)
            
            if batch_id is not None:
                updates.append("mineru_batch_id = %s")
                params.append(batch_id)
            
            params.append(file_id)
            
            cursor.execute(f"""
                UPDATE course_materials SET {', '.join(updates)} WHERE id = %s
            """, params)
    
    def delete_pdf_file(self, file_id: int):
        """删除PDF文件记录（会级联删除相关解析结果）"""
        self.delete_course_material(file_id)
    
    # ==================== 解析结果操作 ====================
    
    def create_parse_result(self, pdf_file_id: int, course_id: str,
                            result_type: ResultType, file_name: str,
                            minio_bucket: str, minio_object_key: str,
                            file_size: int = 0) -> int:
        """
        创建解析结果记录
        
        Returns:
            结果记录ID
        """
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO parse_results
                    (course_material_id, course_id, result_type, file_name,
                     minio_bucket, minio_object_key, file_size)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """, (pdf_file_id, course_id, result_type.value, file_name,
                  minio_bucket, minio_object_key, file_size))
            return cursor.lastrowid
    
    def get_parse_results(self, pdf_file_id: int) -> List[Dict]:
        """获取课程资料的所有解析结果；参数兼容旧名。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM parse_results WHERE course_material_id = %s",
                (pdf_file_id,)
            )
            return cursor.fetchall()
    
    def get_parse_results_by_course(self, course_id: str) -> List[Dict]:
        """获取课程的所有解析结果"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM parse_results WHERE course_id = %s",
                (course_id,)
            )
            return cursor.fetchall()
    
    def delete_parse_results(self, pdf_file_id: int):
        """删除解析结果记录；参数兼容旧名。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "DELETE FROM parse_results WHERE course_material_id = %s",
                (pdf_file_id,)
            )
    
    # ==================== 解析日志操作 ====================
    
    def add_log(self, pdf_file_id: int, message: str,
                level: str = "info"):
        """添加解析日志；pdf_file_id 参数兼容旧调用，语义为 course_material_id。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO parse_logs (course_material_id, log_level, log_message)
                VALUES (%s, %s, %s)
            """, (pdf_file_id, level, message))
    
    def get_logs(self, pdf_file_id: int) -> List[Dict]:
        """获取解析日志；参数兼容旧名。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM parse_logs WHERE course_material_id = %s ORDER BY created_at",
                (pdf_file_id,)
            )
            return cursor.fetchall()
    
    # ==================== 统计查询 ====================
    
    def get_course_overview(self, course_id: Optional[str] = None) -> List[Dict]:
        """获取课程解析概览"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if course_id:
                cursor.execute(
                    "SELECT * FROM v_course_parse_overview WHERE course_id = %s",
                    (course_id,)
                )
            else:
                cursor.execute("SELECT * FROM v_course_parse_overview")
            return cursor.fetchall()
    
    def get_pending_files(self) -> List[Dict]:
        """获取待解析的文件列表"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                self._select_course_material_sql("cm.parse_status = 'pending'")
            )
            return cursor.fetchall()
    
    def get_stats(self) -> Dict:
        """获取统计信息"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            
            stats = {}
            
            # 总课程数
            cursor.execute("SELECT COUNT(*) as count FROM courses")
            stats["total_courses"] = cursor.fetchone()["count"]
            
            # 总文件数
            cursor.execute("SELECT COUNT(*) as count FROM course_materials")
            stats["total_files"] = cursor.fetchone()["count"]
            
            # 各状态文件数
            cursor.execute("""
                SELECT parse_status, COUNT(*) as count 
                FROM course_materials GROUP BY parse_status
            """)
            stats["status_counts"] = {
                row["parse_status"]: row["count"] 
                for row in cursor.fetchall()
            }
            
            # 总解析结果数
            cursor.execute("SELECT COUNT(*) as count FROM parse_results")
            stats["total_results"] = cursor.fetchone()["count"]
            
            return stats


def infer_result_type(file_name: str) -> ResultType:
    """根据文件名推断结果类型"""
    file_name_lower = file_name.lower()
    
    if "content_list" in file_name_lower and file_name_lower.endswith(".json"):
        return ResultType.CONTENT_LIST_JSON
    elif "model" in file_name_lower and file_name_lower.endswith(".json"):
        return ResultType.MODEL_JSON
    elif "layout" in file_name_lower and file_name_lower.endswith(".json"):
        return ResultType.LAYOUT_JSON
    elif file_name_lower.endswith(".md"):
        return ResultType.MARKDOWN
    elif file_name_lower.endswith((".png", ".jpg", ".jpeg", ".gif")):
        return ResultType.IMAGE
    elif file_name_lower.endswith(".pdf") and "origin" in file_name_lower:
        return ResultType.ORIGIN_PDF
    else:
        return ResultType.OTHER
