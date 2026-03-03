#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinerU PDF解析测试脚本
=======================
该脚本用于将本地PDF文件上传到MinerU云端进行解析，并获取JSON结果。

使用前准备:
1. 访问 https://mineru.net/ 注册账号
2. 登录后在 https://mineru.net/apiManage 申请API权限
3. 获取API Token

使用方法:
    python test_mineru.py -f your_file.pdf -t YOUR_API_TOKEN
    
或者直接修改脚本中的 API_TOKEN 变量
"""

import requests
import json
import time
import argparse
import os
import zipfile
import io
from pathlib import Path


# ===================== 配置区 =====================
API_TOKEN = "eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFM1MTIifQ.eyJqdGkiOiIzNTQwMDEzNCIsInJvbCI6IlJPTEVfUkVHSVNURVIiLCJpc3MiOiJPcGVuWExhYiIsImlhdCI6MTc2ODgyMjQ0OSwiY2xpZW50SWQiOiJsa3pkeDU3bnZ5MjJqa3BxOXgydyIsInBob25lIjoiIiwib3BlbklkIjpudWxsLCJ1dWlkIjoiYmEzNTZhODQtOWIyNi00ODUxLWJmMGMtY2M4YTQ4MjcyY2E4IiwiZW1haWwiOiIiLCJleHAiOjE3NzAwMzIwNDl9.jBI71Dq3JZ3YFJb8mJGRCfJUurREtzsXP9gq-rkV_orJrIGKwCdlKS_SUW_ci2LLqX1KD3nAwGjHjsD3BsQIXw"  # 在这里填写你的API Token，或通过命令行参数传入
API_BASE_URL = "https://mineru.net/api/v4"
# ==================================================


class MinerUParser:
    """MinerU PDF解析器类"""
    
    def __init__(self, token: str):
        """
        初始化解析器
        
        Args:
            token: MinerU API Token
        """
        self.token = token
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}"
        }
    
    def apply_upload_url(self, file_names: list, model_version: str = "vlm", 
                         enable_formula: bool = True, enable_table: bool = True,
                         language: str = "ch") -> dict:
        """
        申请文件上传链接
        
        Args:
            file_names: 文件名列表
            model_version: 模型版本，可选 "vlm"（视觉语言模型，精度高）或 "pipeline"
            enable_formula: 是否启用公式识别
            enable_table: 是否启用表格识别
            language: 语言设置，"ch"为中文，"en"为英文
            
        Returns:
            包含batch_id和上传URL的字典
        """
        url = f"{API_BASE_URL}/file-urls/batch"
        
        # 构建文件信息列表
        files = []
        for i, name in enumerate(file_names):
            files.append({
                "name": name,
                "is_ocr": True,  # 启用OCR
                "data_id": f"file_{i}_{int(time.time())}"  # 自定义ID用于追踪
            })
        
        data = {
            "files": files,
            "model_version": model_version,
            "enable_formula": enable_formula,
            "enable_table": enable_table,
            "language": language
        }
        
        print(f"[1/4] 正在申请上传链接...")
        print(f"      模型版本: {model_version}")
        print(f"      公式识别: {'启用' if enable_formula else '禁用'}")
        print(f"      表格识别: {'启用' if enable_table else '禁用'}")
        print(f"      语言: {language}")
        
        response = requests.post(url, headers=self.headers, json=data)
        
        if response.status_code != 200:
            raise Exception(f"申请上传链接失败: HTTP {response.status_code}, {response.text}")
        
        result = response.json()
        
        if result.get("code") != 0:
            raise Exception(f"申请上传链接失败: {result.get('msg', '未知错误')}")
        
        batch_id = result["data"]["batch_id"]
        file_urls = result["data"]["file_urls"]
        
        print(f"      ✓ 获取成功! Batch ID: {batch_id}")
        
        return {
            "batch_id": batch_id,
            "file_urls": file_urls
        }
    
    def upload_file(self, file_path: str, upload_url: str) -> bool:
        """
        上传文件到预签名URL
        
        Args:
            file_path: 本地文件路径
            upload_url: 预签名上传URL
            
        Returns:
            是否上传成功
        """
        print(f"[2/4] 正在上传文件: {os.path.basename(file_path)}...")
        
        with open(file_path, 'rb') as f:
            response = requests.put(upload_url, data=f)
        
        if response.status_code == 200:
            print(f"      ✓ 上传成功!")
            return True
        else:
            print(f"      ✗ 上传失败: HTTP {response.status_code}")
            return False
    
    def get_batch_results(self, batch_id: str, timeout: int = 600, 
                          poll_interval: int = 5) -> dict:
        """
        轮询获取批量解析结果
        
        Args:
            batch_id: 批次ID
            timeout: 超时时间（秒）
            poll_interval: 轮询间隔（秒）
            
        Returns:
            解析结果字典
        """
        url = f"{API_BASE_URL}/extract-results/batch/{batch_id}"
        
        print(f"[3/4] 正在等待解析完成...")
        print(f"      Batch ID: {batch_id}")
        print(f"      超时时间: {timeout}秒")
        
        start_time = time.time()
        
        while True:
            elapsed = time.time() - start_time
            
            if elapsed > timeout:
                raise TimeoutError(f"解析超时（已等待{timeout}秒）")
            
            response = requests.get(url, headers=self.headers)
            
            if response.status_code != 200:
                raise Exception(f"查询结果失败: HTTP {response.status_code}")
            
            result = response.json()
            
            if result.get("code") != 0:
                raise Exception(f"查询结果失败: {result.get('msg', '未知错误')}")
            
            extract_results = result["data"]["extract_result"]
            
            # 检查所有文件的状态
            all_done = True
            has_error = False
            
            for file_result in extract_results:
                state = file_result.get("state", "")
                file_name = file_result.get("file_name", "未知")
                
                if state == "done":
                    continue
                elif state == "failed":
                    has_error = True
                    print(f"      ✗ {file_name} 解析失败: {file_result.get('err_msg', '未知错误')}")
                elif state == "running":
                    all_done = False
                    progress = file_result.get("extract_progress", {})
                    extracted = progress.get("extracted_pages", 0)
                    total = progress.get("total_pages", "?")
                    print(f"      ⏳ {file_name} 解析中: {extracted}/{total} 页 (已等待 {int(elapsed)}秒)")
                else:
                    all_done = False
                    print(f"      ⏳ {file_name} 状态: {state} (已等待 {int(elapsed)}秒)")
            
            if all_done:
                if has_error:
                    print(f"      ⚠ 部分文件解析失败")
                else:
                    print(f"      ✓ 所有文件解析完成!")
                return result["data"]
            
            time.sleep(poll_interval)
    
    def download_and_extract_results(self, extract_result: dict, 
                                     output_dir: str = "./mineru_output") -> list:
        """
        下载并解压解析结果
        
        Args:
            extract_result: 解析结果数据
            output_dir: 输出目录
            
        Returns:
            解析后的JSON内容列表
        """
        print(f"[4/4] 正在下载解析结果...")
        
        os.makedirs(output_dir, exist_ok=True)
        
        results = []
        
        for file_result in extract_result.get("extract_result", []):
            file_name = file_result.get("file_name", "unknown")
            state = file_result.get("state", "")
            
            if state != "done":
                print(f"      ⏭ 跳过 {file_name} (状态: {state})")
                continue
            
            zip_url = file_result.get("full_zip_url", "")
            
            if not zip_url:
                print(f"      ⚠ {file_name} 没有下载链接")
                continue
            
            print(f"      📥 下载: {file_name}")
            
            # 下载ZIP文件
            response = requests.get(zip_url)
            
            if response.status_code != 200:
                print(f"      ✗ 下载失败: HTTP {response.status_code}")
                continue
            
            # 创建文件专属目录
            file_output_dir = os.path.join(output_dir, Path(file_name).stem)
            os.makedirs(file_output_dir, exist_ok=True)
            
            # 解压ZIP
            with zipfile.ZipFile(io.BytesIO(response.content)) as zf:
                zf.extractall(file_output_dir)
                print(f"      ✓ 已解压到: {file_output_dir}")
                
                # 查找并读取JSON文件
                json_content = None
                for name in zf.namelist():
                    if name.endswith('.json') and 'content_list' in name:
                        json_path = os.path.join(file_output_dir, name)
                        with open(json_path, 'r', encoding='utf-8') as f:
                            json_content = json.load(f)
                        print(f"      📄 找到JSON: {name}")
                        break
                
                results.append({
                    "file_name": file_name,
                    "output_dir": file_output_dir,
                    "json_content": json_content,
                    "files": zf.namelist()
                })
        
        return results
    
    def parse_pdf(self, pdf_path: str, output_dir: str = "./mineru_output",
                  model_version: str = "vlm", enable_formula: bool = True,
                  enable_table: bool = True, language: str = "ch",
                  timeout: int = 600) -> dict | None:
        """
        解析单个PDF文件的完整流程
        
        Args:
            pdf_path: PDF文件路径
            output_dir: 输出目录
            model_version: 模型版本
            enable_formula: 是否启用公式识别
            enable_table: 是否启用表格识别
            language: 语言
            timeout: 超时时间
            
        Returns:
            解析结果
        """
        # 检查文件是否存在
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"文件不存在: {pdf_path}")
        
        file_name = os.path.basename(pdf_path)
        print(f"\n{'='*60}")
        print(f"MinerU PDF解析")
        print(f"{'='*60}")
        print(f"文件: {pdf_path}")
        print(f"大小: {os.path.getsize(pdf_path) / 1024 / 1024:.2f} MB")
        print(f"{'='*60}\n")
        
        # 步骤1: 申请上传链接
        upload_info = self.apply_upload_url(
            [file_name], 
            model_version=model_version,
            enable_formula=enable_formula,
            enable_table=enable_table,
            language=language
        )
        
        # 步骤2: 上传文件
        success = self.upload_file(pdf_path, upload_info["file_urls"][0])
        if not success:
            raise Exception("文件上传失败")
        
        # 步骤3: 等待解析完成
        batch_result = self.get_batch_results(upload_info["batch_id"], timeout=timeout)
        
        # 步骤4: 下载结果
        results = self.download_and_extract_results(batch_result, output_dir)
        
        print(f"\n{'='*60}")
        print(f"解析完成!")
        print(f"{'='*60}")
        
        return results[0] if results else None


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description="MinerU PDF解析工具 - 将本地PDF上传到MinerU云端解析",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python mineru_pdf_parser.py -f document.pdf -t YOUR_TOKEN
  python mineru_pdf_parser.py -f document.pdf -t YOUR_TOKEN -o ./output
  python mineru_pdf_parser.py -f document.pdf -t YOUR_TOKEN -m pipeline -l en

获取Token:
  1. 访问 https://mineru.net/ 注册账号
  2. 在 https://mineru.net/apiManage 申请API权限
  3. 审核通过后获取Token
        """
    )
    
    parser.add_argument("-f", "--file", required=True, help="PDF文件路径")
    parser.add_argument("-t", "--token", default=API_TOKEN, help="MinerU API Token")
    parser.add_argument("-o", "--output", default="./mineru_output", help="输出目录")
    parser.add_argument("-m", "--model", default="vlm", choices=["vlm", "pipeline"],
                        help="模型版本: vlm(高精度) 或 pipeline(快速)")
    parser.add_argument("-l", "--language", default="ch", choices=["ch", "en"],
                        help="语言: ch(中文) 或 en(英文)")
    parser.add_argument("--no-formula", action="store_true", help="禁用公式识别")
    parser.add_argument("--no-table", action="store_true", help="禁用表格识别")
    parser.add_argument("--timeout", type=int, default=600, help="超时时间(秒)")
    parser.add_argument("--show-json", action="store_true", help="显示解析后的JSON内容")
    
    args = parser.parse_args()
    
    # 检查Token
    if args.token == "YOUR_API_TOKEN":
        print("错误: 请提供有效的API Token!")
        print("使用 -t 参数传入Token，或修改脚本中的 API_TOKEN 变量")
        print("\n获取Token方法:")
        print("1. 访问 https://mineru.net/ 注册账号")
        print("2. 在 https://mineru.net/apiManage 申请API权限")
        return 1
    
    try:
        # 创建解析器实例
        mineru = MinerUParser(args.token)
        
        # 执行解析
        result = mineru.parse_pdf(
            pdf_path=args.file,
            output_dir=args.output,
            model_version=args.model,
            enable_formula=not args.no_formula,
            enable_table=not args.no_table,
            language=args.language,
            timeout=args.timeout
        )
        
        if result:
            print(f"\n输出目录: {result['output_dir']}")
            print(f"包含文件:")
            for f in result['files']:
                print(f"  - {f}")
            
            # 显示JSON内容
            if args.show_json and result.get('json_content'):
                print(f"\n{'='*60}")
                print("JSON内容预览 (前3个元素):")
                print(f"{'='*60}")
                json_preview = result['json_content'][:3] if isinstance(result['json_content'], list) else result['json_content']
                print(json.dumps(json_preview, ensure_ascii=False, indent=2))
        else:
            print("解析失败，未获取到结果")
            return 1
            
    except FileNotFoundError as e:
        print(f"错误: {e}")
        return 1
    except TimeoutError as e:
        print(f"超时: {e}")
        return 1
    except Exception as e:
        print(f"错误: {e}")
        return 1
    
    return 0


if __name__ == "__main__":
    exit(main())